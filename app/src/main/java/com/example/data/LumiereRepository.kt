package com.example.data

import android.content.Context
import android.os.Bundle
import com.example.R
import com.example.auth.GoogleSignInManager
import com.example.model.AdvertisementSettings
import com.example.model.AiToolItem
import com.example.model.AppConfigSettings
import com.example.model.AppNotification
import com.example.model.CategoryItem
import com.example.model.FavoriteRecord
import com.example.model.ImageComment
import com.example.model.ImagePromptDocument
import com.example.model.InspirationImage
import com.example.model.LikeRecord
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class ReauthRequiredForDeleteException : Exception("Re-authentication required")
class PromptComingSoonException(message: String = "Prompt coming soon") : Exception(message)
class PostDeletedException(message: String = "This post is no longer available") : Exception(message)

class LumiereRepository(
    private val db: FirebaseFirestore,
    private val analytics: FirebaseAnalytics? = null
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context),
        kotlin.runCatching { FirebaseAnalytics.getInstance(context.applicationContext) }.getOrNull()
    )

    private val auth = Firebase.auth

    private fun requireSignedInUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Session is initializing. Please try again.")
    }

    suspend fun ensureSignedInUserId(): String {
        auth.currentUser?.uid?.let { return it }
        val result = withTimeout(WRITE_TIMEOUT_MS) {
            auth.signInAnonymously().await()
        }
        return result.user?.uid
            ?: throw IllegalStateException("Unable to initialize guest session.")
    }

    private fun requireRealUser(): FirebaseUser {
        val user = auth.currentUser
        if (!GoogleSignInManager.isRealAuthenticatedUser(user)) {
            throw IllegalStateException("Sign in with Google to continue.")
        }
        return user!!
    }

    private fun isValidDocId(id: String): Boolean {
        return id.isNotBlank() && id.length <= 128 && id.matches(Regex("^[a-zA-Z0-9_\\-]+$"))
    }

    fun logAnalyticsEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        runCatching {
            val bundle = Bundle().apply {
                params.forEach { (k, v) -> putString(k, v.take(100)) }
            }
            analytics?.logEvent(eventName, bundle)
        }
    }

    // =========================================================================
    // CATALOG FETCH & RECONCILIATION (Equality-only queries; NO composite indexes needed)
    // =========================================================================

    /**
     * Fetches all currently published images using a single equality filter (`status == "published"`)
     * and sorts them newest first (`createdAt DESC`) on the client so no composite index is required.
     */
    suspend fun fetchAllPublishedImages(source: Source = Source.DEFAULT): Result<List<InspirationImage>> {
        val path = "images"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("status", "published")
                .get(source)
                .await()

            val items = snapshot.toObjects(
                InspirationImage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedWith(
                compareByDescending<InspirationImage> { it.createdAt?.seconds ?: 0L }
                    .thenBy { it.displayOrder }
            )
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Fetches all currently published categories using a single equality filter (`status == "published"`)
     * and sorts them on the client by `sortOrder ASC` so no composite index is required.
     */
    suspend fun fetchAllPublishedCategories(source: Source = Source.DEFAULT): Result<List<CategoryItem>> {
        val path = "categories"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("status", "published")
                .get(source)
                .await()

            val items = snapshot.toObjects(
                CategoryItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedBy { it.sortOrder }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Fetches all enabled AI tools from `aiTools` (`enabled == true`) and sorts them by `sortOrder ASC` on the client.
     */
    suspend fun fetchAllEnabledAiTools(source: Source = Source.DEFAULT): Result<List<AiToolItem>> {
        val path = "aiTools"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("enabled", true)
                .get(source)
                .await()

            val items = snapshot.toObjects(
                AiToolItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedBy { it.sortOrder }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.success(emptyList())
            } else {
                handleFirestoreError(e, OperationType.LIST, path)
                Result.failure(e)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun observeEnabledAiTools(): Flow<List<AiToolItem>> = flow {
        val uid = auth.currentUser?.uid
        val path = "aiTools"
        emitAll(
            db.collection(path)
                .whereEqualTo("enabled", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiToolItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sortOrder }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(emptyList())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // =========================================================================
    // PROMPT ACCESS (`promptUnlocks/{uid}_{imageId}` -> `imagePrompts/{imageId}`)
    // Prompt text is NEVER cached to disk, DataStore, Room, or logged.
    // =========================================================================

    /**
     * Ensures `promptUnlocks/{uid}_{imageId}` exists (gets first; creates only if missing with
     * exact schema fields, `rewardVerified = true`, `adNetwork = "AdMob"` or `"none"`, never updates),
     * then reads `imagePrompts/{imageId}` from the server and returns the prompt text in memory only.
     */
    suspend fun ensureUnlockAndReadPrompt(
        image: InspirationImage,
        adNetwork: String
    ): Result<String> {
        val cleanImageId = image.id.trim()
        val uid = try {
            ensureSignedInUserId()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("LumiereRepository", "ensureSignedInUserId failed for imageId=$cleanImageId", e)
            return Result.failure(e)
        }
        val unlockDocId = "${uid}_$cleanImageId"
        val unlockPath = "promptUnlocks/$unlockDocId"
        val promptPath = "imagePrompts/$cleanImageId"

        return try {
            require(isValidDocId(cleanImageId) && isValidDocId(unlockDocId)) { "Invalid ID" }

            withTimeout(WRITE_TIMEOUT_MS) {
                val unlockRef = db.collection("promptUnlocks").document(unlockDocId)
                // Note: firestore.rules `allow get` on promptUnlocks checks `existing().userId == request.auth.uid`.
                // When the document does not exist yet, `existing()` (resource.data) is null, so Firestore throws
                // PERMISSION_DENIED on `get()`. Catch PERMISSION_DENIED on `get()` as `unlockExists = false`.
                val unlockExists = try {
                    unlockRef.get(Source.SERVER).await().exists()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        false
                    } else {
                        throw e
                    }
                }

                if (!unlockExists) {
                    val safeTitle = image.title.trim().take(150).ifBlank { "Inspiration" }
                    val safeNetwork = adNetwork.trim().take(50).ifBlank { "none" }
                    val payload = mapOf(
                        "userId" to uid,
                        "imageId" to cleanImageId,
                        "imageTitle" to safeTitle,
                        "rewardVerified" to true,
                        "adNetwork" to safeNetwork,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    unlockRef.set(payload).await()
                }
            }

            val promptSnap = withTimeout(WRITE_TIMEOUT_MS) {
                db.collection("imagePrompts").document(cleanImageId).get(Source.SERVER).await()
            }
            if (!promptSnap.exists()) {
                val postStillExists = try {
                    val imgSnap = withTimeout(WRITE_TIMEOUT_MS) {
                        db.collection("images").document(cleanImageId).get(Source.SERVER).await()
                    }
                    imgSnap.exists() && imgSnap.getString("status") == "published"
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                        e.code == FirebaseFirestoreException.Code.NOT_FOUND
                    ) {
                        false
                    } else {
                        true
                    }
                } catch (_: Exception) {
                    true
                }
                if (!postStillExists) {
                    return Result.failure(PostDeletedException())
                }
                return Result.failure(PromptComingSoonException())
            }

            val doc = promptSnap.toObject(
                ImagePromptDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val text = doc?.promptText?.trim().orEmpty()
            if (text.isEmpty()) {
                Result.failure(PromptComingSoonException())
            } else {
                Result.success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("LumiereRepository", "ensureUnlockAndReadPrompt failed for $unlockPath -> $promptPath", e)
            handleFirestoreError(e, OperationType.GET, "$unlockPath -> $promptPath")
            Result.failure(e)
        }
    }

    /**
     * Checks if `promptUnlocks/{uid}_{imageId}` already exists for this user and reads
     * `imagePrompts/{imageId}` when `reusePreviousUnlocks == true`.
     */
    suspend fun readPreviouslyUnlockedPromptIfAvailable(imageId: String): Result<String?> {
        val uid = try {
            ensureSignedInUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanImageId = imageId.trim()
        val unlockDocId = "${uid}_$cleanImageId"
        return try {
            require(isValidDocId(cleanImageId) && isValidDocId(unlockDocId)) { "Invalid ID" }
            val unlockExists = withTimeout(WRITE_TIMEOUT_MS) {
                try {
                    db.collection("promptUnlocks").document(unlockDocId).get(Source.SERVER).await().exists()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        false
                    } else {
                        throw e
                    }
                }
            }
            if (!unlockExists) {
                return Result.success(null)
            }
            val promptSnap = withTimeout(WRITE_TIMEOUT_MS) {
                db.collection("imagePrompts").document(cleanImageId).get(Source.SERVER).await()
            }
            val text = promptSnap.toObject(
                ImagePromptDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.promptText?.trim()
            Result.success(text?.takeIf { it.isNotEmpty() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // OBSERVABLE STREAMS (Equality-only queries; client-side sorting)
    // =========================================================================

    fun observePublishedCategories(): Flow<List<CategoryItem>> = flow {
        val uid = auth.currentUser?.uid
        val path = "categories"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "published")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CategoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sortOrder }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserLikes(userId: String): Flow<List<LikeRecord>> = flow {
        val currentUser = auth.currentUser
        if (currentUser == null || !GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            emit(emptyList())
            return@flow
        }
        val uid = currentUser.uid
        if (uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "likes"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LikeRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(emptyList())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserFavorites(userId: String): Flow<List<FavoriteRecord>> = flow {
        val currentUser = auth.currentUser
        if (currentUser == null || !GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            emit(emptyList())
            return@flow
        }
        val uid = currentUser.uid
        if (uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "favorites"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FavoriteRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(emptyList())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Observes visible comments for an image using equality-only filters (`imageId` and `status == "visible"`)
     * and sorts newest-first on the client so NO composite index is required.
     */
    fun observeImageComments(imageId: String): Flow<List<ImageComment>> = flow {
        val uid = auth.currentUser?.uid
        val path = "comments"
        emitAll(
            db.collection(path)
                .whereEqualTo("imageId", imageId)
                .whereEqualTo("status", "visible")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ImageComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Observes published broadcast notifications using an equality-only filter (`status == "published"`)
     * and sorts newest-first on the client so NO composite index is required.
     */
    fun observeNotifications(): Flow<List<AppNotification>> = flow {
        val uid = auth.currentUser?.uid
        val path = "notifications"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "published")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AppNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Observes comment replies for a signed-in real user:
     * Queries `comments` with equality-only filters (`userId == uid` and `status == "visible"`),
     * then filters `adminReply.isNotBlank()` and sorts newest-first on the client (NO composite index needed).
     */
    fun observeUserCommentReplies(userId: String): Flow<List<ImageComment>> = flow {
        val currentUser = auth.currentUser
        if (currentUser == null || !GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            emit(emptyList())
            return@flow
        }
        val uid = currentUser.uid
        if (uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "comments"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "visible")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ImageComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.adminReply.isNotBlank() }
                        .sortedByDescending { (it.updatedAt ?: it.createdAt)?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(emptyList())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAppConfigSettings(): Flow<AppConfigSettings> = flow {
        val uid = auth.currentUser?.uid
        val path = "appSettings/global"
        emitAll(
            db.collection("appSettings").document("global")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        AppConfigSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: AppConfigSettings()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Observes `advertisementSettings/global`:
     * - If the document does NOT exist in Firestore, returns [AdvertisementSettings.defaultWhenDocumentMissing]
     *   (which uses the built-in BuildConfig ad unit IDs).
     * - If the document DOES exist in Firestore, uses its exact values (so any blank unit ID turns that ad format off).
     */
    fun observeAdvertisementSettings(): Flow<AdvertisementSettings> = flow {
        val uid = auth.currentUser?.uid
        val path = "advertisementSettings/global"
        emitAll(
            db.collection("advertisementSettings").document("global")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        AdvertisementSettings.defaultWhenDocumentMissing()
                    } else {
                        snapshot.toObject(
                            AdvertisementSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AdvertisementSettings.defaultWhenDocumentMissing()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchImageById(imageId: String): Result<InspirationImage?> {
        val cleanId = imageId.trim()
        val path = "images/$cleanId"
        return try {
            require(isValidDocId(cleanId)) { "Invalid image identifier." }
            val snapshot = db.collection("images").document(cleanId).get().await()
            val image = if (snapshot.exists()) {
                snapshot.toObject(
                    InspirationImage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.takeIf { it.status == "published" }
            } else null
            Result.success(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                e.code == FirebaseFirestoreException.Code.NOT_FOUND
            ) {
                Result.success(null)
            } else {
                handleFirestoreError(e, OperationType.GET, path)
                Result.failure(e)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            require(isValidDocId(uid)) { "Invalid user ID" }
            withTimeout(WRITE_TIMEOUT_MS) {
                val docRef = db.collection("users").document(uid)
                val existing = docRef.get(Source.SERVER).await()
                val displayName = (user.displayName ?: "PromptLens Creator").trim().take(80).ifBlank { "PromptLens Creator" }
                val email = (user.email ?: "").trim().take(150)
                val photoUrl = (user.photoUrl?.toString() ?: "").trim().take(1000)

                if (!existing.exists()) {
                    val payload = mapOf(
                        "userId" to uid,
                        "displayName" to displayName,
                        "email" to email,
                        "photoUrl" to photoUrl,
                        "likedCount" to 0L,
                        "favoriteCount" to 0L,
                        "unlockedCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(payload).await()
                } else {
                    docRef.update(
                        mapOf(
                            "displayName" to displayName,
                            "email" to email,
                            "photoUrl" to photoUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // ONLINE-ONLY SERVER-ACKNOWLEDGED WRITES (~15s timeout, no offline queueing)
    // =========================================================================

    suspend fun recordImageView(imageId: String): Result<Unit> {
        val path = "images/$imageId"
        return try {
            ensureSignedInUserId()
            require(isValidDocId(imageId)) { "Invalid image ID" }
            withTimeout(WRITE_TIMEOUT_MS) {
                db.collection("images").document(imageId).update(
                    mapOf(
                        "viewCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                e.code != FirebaseFirestoreException.Code.NOT_FOUND
            ) {
                handleFirestoreError(e, OperationType.UPDATE, path)
            }
            Result.failure(e)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleLike(image: InspirationImage, currentlyLiked: Boolean): Result<Boolean> {
        val user = try {
            requireRealUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val uid = user.uid
        val likeDocId = "${uid}_${image.id}"
        val path = "likes/$likeDocId"
        return try {
            require(isValidDocId(image.id) && isValidDocId(likeDocId)) { "Invalid like ID" }
            withTimeout(WRITE_TIMEOUT_MS) {
                val likeRef = db.collection("likes").document(likeDocId)
                val imageRef = db.collection("images").document(image.id)
                val batch = db.batch()

                if (currentlyLiked) {
                    batch.delete(likeRef)
                    batch.update(
                        imageRef,
                        mapOf(
                            "likeCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                    Result.success(false)
                } else {
                    val payload = mapOf(
                        "userId" to uid,
                        "imageId" to image.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(likeRef, payload)
                    batch.update(
                        imageRef,
                        mapOf(
                            "likeCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                    Result.success(true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (currentlyLiked) OperationType.DELETE else OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(image: InspirationImage, currentlyFavorited: Boolean): Result<Boolean> {
        val uid = try {
            ensureSignedInUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val favDocId = "${uid}_${image.id}"
        val path = "favorites/$favDocId"
        return try {
            require(isValidDocId(image.id) && isValidDocId(favDocId)) { "Invalid favorite ID" }
            withTimeout(WRITE_TIMEOUT_MS) {
                val favRef = db.collection("favorites").document(favDocId)
                if (currentlyFavorited) {
                    favRef.delete().await()
                    Result.success(false)
                } else {
                    val payload = mapOf(
                        "userId" to uid,
                        "imageId" to image.id,
                        "imageTitle" to image.title.trim().take(150).ifBlank { "Inspiration" },
                        "imageUrl" to image.imageUrl.trim().take(1000),
                        "categoryName" to image.categoryName.trim().take(100).ifBlank { "Photography" },
                        "aspectRatio" to image.aspectRatio.coerceIn(0.2, 5.0),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    favRef.set(payload).await()
                    Result.success(true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (currentlyFavorited) OperationType.DELETE else OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordPromptShare(imageId: String): Result<Unit> {
        val path = "images/$imageId"
        return try {
            ensureSignedInUserId()
            require(isValidDocId(imageId)) { "Invalid image ID" }
            withTimeout(WRITE_TIMEOUT_MS) {
                db.collection("images").document(imageId).update(
                    mapOf(
                        "shareCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                e.code != FirebaseFirestoreException.Code.NOT_FOUND
            ) {
                handleFirestoreError(e, OperationType.UPDATE, path)
            }
            Result.failure(e)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun postComment(imageId: String, rawText: String): Result<Unit> {
        val user = try {
            requireRealUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val uid = user.uid
        val trimmedText = rawText.trim()
        if (trimmedText.isEmpty() || trimmedText.length > 500 || !isValidDocId(imageId)) {
            return Result.failure(IllegalArgumentException("Invalid comment."))
        }

        val commentRef = db.collection("comments").document()
        val imageRef = db.collection("images").document(imageId)
        val path = commentRef.path
        return try {
            withTimeout(WRITE_TIMEOUT_MS) {
                val userName = (user.displayName ?: "PromptLens Member").trim().take(80).ifBlank { "PromptLens Member" }
                val payload = mapOf(
                    "imageId" to imageId,
                    "userId" to uid,
                    "userName" to userName,
                    "text" to trimmedText,
                    "reported" to false,
                    "reportReason" to "",
                    "adminReply" to "",
                    "status" to "visible",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val batch = db.batch()
                batch.set(commentRef, payload)
                batch.update(
                    imageRef,
                    mapOf(
                        "commentCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(commentId: String, imageId: String): Result<Unit> {
        val path = "comments/$commentId"
        return try {
            requireRealUser()
            require(isValidDocId(commentId) && isValidDocId(imageId)) { "Invalid ID" }
            withTimeout(WRITE_TIMEOUT_MS) {
                val batch = db.batch()
                batch.delete(db.collection("comments").document(commentId))
                batch.update(
                    db.collection("images").document(imageId),
                    mapOf(
                        "commentCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun reportComment(commentId: String, imageId: String, reason: String): Result<Unit> {
        val user = try {
            requireRealUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val reportRef = db.collection("reports").document()
        val path = reportRef.path
        return try {
            require(isValidDocId(commentId) && isValidDocId(imageId)) { "Invalid ID" }
            val cleanReason = reason.trim().take(300).ifBlank { "Inappropriate comment" }
            withTimeout(WRITE_TIMEOUT_MS) {
                val reportPayload = mapOf(
                    "commentId" to commentId,
                    "imageId" to imageId,
                    "reporterId" to user.uid,
                    "reason" to cleanReason,
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val batch = db.batch()
                batch.set(reportRef, reportPayload)
                batch.update(
                    db.collection("comments").document(commentId),
                    mapOf(
                        "reported" to true,
                        "reportReason" to cleanReason.take(200),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Deletes the user's own documents allowed by security rules (`favorites`, `likes`, `comments`
     * authored by them, and `users/{uid}`), then deletes the Firebase Auth user.
     * If Firebase Auth throws [FirebaseAuthRecentLoginRequiredException], returns a failure wrapping
     * [ReauthRequiredForDeleteException] so the UI can ask the user to sign in with Google again and retry.
     */
    suspend fun deleteUserAccountAndData(): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = currentUser.uid
        val isReal = GoogleSignInManager.isRealAuthenticatedUser(currentUser)

        return try {
            withTimeout(WRITE_TIMEOUT_MS) {
                // 1. Delete user's favorites
                val favSnap = db.collection("favorites")
                    .whereEqualTo("userId", uid)
                    .get(Source.SERVER)
                    .await()
                favSnap.documents.forEach { doc ->
                    runCatching { doc.reference.delete().await() }
                }

                // 2. Delete user's likes & authored visible comments (allowed for real users)
                if (isReal) {
                    val likesSnap = db.collection("likes")
                        .whereEqualTo("userId", uid)
                        .get(Source.SERVER)
                        .await()
                    likesSnap.documents.forEach { doc ->
                        runCatching { doc.reference.delete().await() }
                    }

                    val commentsSnap = db.collection("comments")
                        .whereEqualTo("userId", uid)
                        .whereEqualTo("status", "visible")
                        .get(Source.SERVER)
                        .await()
                    commentsSnap.documents.forEach { doc ->
                        runCatching { doc.reference.delete().await() }
                    }
                }

                // 3. Delete users/{uid} profile doc
                runCatching {
                    db.collection("users").document(uid).delete().await()
                }

                // 4. Delete Firebase Auth account
                currentUser.delete().await()
            }

            // Re-establish clean anonymous guest session after account deletion
            runCatching { auth.signInAnonymously().await() }
            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(ReauthRequiredForDeleteException())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 15_000L
        private const val FIRESTORE_CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB cap

        private fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(dbId)
            runCatching {
                val settings = FirebaseFirestoreSettings.Builder(firestore.firestoreSettings)
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FIRESTORE_CACHE_SIZE_BYTES)
                            .build()
                    )
                    .build()
                firestore.firestoreSettings = settings
            }
            return firestore
        }
    }
}
