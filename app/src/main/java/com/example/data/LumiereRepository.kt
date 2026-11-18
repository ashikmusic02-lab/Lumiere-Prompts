package com.example.data

import android.content.Context
import android.os.Bundle
import com.example.R
import com.example.auth.GoogleSignInManager
import com.example.model.AdvertisementSettings
import com.example.model.AppConfigSettings
import com.example.model.AppNotification
import com.example.model.CategoryItem
import com.example.model.FavoriteRecord
import com.example.model.ImageComment
import com.example.model.ImagePromptDocument
import com.example.model.InspirationImage
import com.example.model.LikeRecord
import com.example.model.PromptUnlock
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Cursor-paginated result page for `images` queries.
 */
data class PaginatedImagesResult(
    val images: List<InspirationImage>,
    val lastSnapshot: DocumentSnapshot?,
    val hasMore: Boolean
)

/**
 * Plug-in hook for server-side reward verification.
 *
 * SECURITY NOTE (AdMob Server-Side Verification — SSV):
 * On the Firebase Spark (free) plan, `promptUnlocks/{uid}_{imageId}` is created by the client
 * after `OnUserEarnedRewardListener` fires and is validated by strict Firestore Security Rules.
 * Full protection against forged client unlock writes requires AdMob Server-Side Verification (SSV)
 * via a Cloud Function (which requires the Firebase Blaze plan) that verifies Google's cryptographic
 * signature and writes `promptUnlocks/{uid}_{imageId}` using the Firebase Admin SDK.
 * When upgrading to the Blaze plan, supply a custom [RewardUnlockVerifier] implementation that
 * awaits the Cloud Function SSV receipt before reading `imagePrompts/{imageId}`.
 */
fun interface RewardUnlockVerifier {
    suspend fun recordUnlockReceipt(
        db: FirebaseFirestore,
        uid: String,
        image: InspirationImage,
        adNetwork: String
    )
}

class LumiereRepository(
    private val db: FirebaseFirestore,
    private val analytics: FirebaseAnalytics? = null,
    private val rewardUnlockVerifier: RewardUnlockVerifier = DefaultClientRewardUnlockVerifier
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        kotlin.runCatching { FirebaseAnalytics.getInstance(context.applicationContext) }.getOrNull()
    )

    private val auth = Firebase.auth

    /**
     * Returns the current user's UID (works for both anonymous guest users and Google-authenticated users).
     */
    private fun requireSignedInUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Session is initializing. Please try again in a moment.")
    }

    /**
     * Ensures a signed-in UID exists (creating an anonymous Firebase Auth session if `currentUser` is null)
     * before executing Firestore reads/writes that require a UID (views, favorites, prompt unlocks).
     */
    suspend fun ensureSignedInUserId(): String {
        auth.currentUser?.uid?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user?.uid
            ?: throw IllegalStateException("Unable to initialize anonymous guest session.")
    }

    private fun requireRealUser(): FirebaseUser {
        val user = auth.currentUser
        if (!GoogleSignInManager.isRealAuthenticatedUser(user)) {
            throw IllegalStateException("Please sign in with Google to use likes, comments, and reports.")
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
    // PAGINATED & LIMITED HOME / EXPLORE / RELATED QUERIES (No Full-Collection Listeners)
    // =========================================================================

    /**
     * Fetches a cursor-paginated page of published images (default 20 per page),
     * ordered by `displayOrder` ascending then `createdAt` descending.
     */
    suspend fun fetchImagesPage(
        pageSize: Long = 20L,
        lastSnapshot: DocumentSnapshot? = null,
        categoryIdOrName: String? = null
    ): Result<PaginatedImagesResult> {
        val path = "images"
        return try {
            var query: Query = db.collection(path)
                .whereEqualTo("status", "published")

            if (!categoryIdOrName.isNullOrBlank() && !categoryIdOrName.equals("All", ignoreCase = true)) {
                query = query.whereEqualTo("categoryName", categoryIdOrName)
            }

            query = query
                .orderBy("displayOrder", Query.Direction.ASCENDING)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize)

            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }

            val snapshot = try {
                query.get().await()
            } catch (_: FirebaseFirestoreException) {
                // Fallback if composite index is still building in user's Firebase project
                var fallbackQuery: Query = db.collection(path)
                    .whereEqualTo("status", "published")
                    .limit(pageSize)
                if (lastSnapshot != null) {
                    fallbackQuery = fallbackQuery.startAfter(lastSnapshot)
                }
                fallbackQuery.get().await()
            }

            val items = snapshot.toObjects(
                InspirationImage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val newCursor = snapshot.documents.lastOrNull()
            Result.success(
                PaginatedImagesResult(
                    images = items,
                    lastSnapshot = newCursor,
                    hasMore = snapshot.documents.size.toLong() >= pageSize
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Fetches a small limited list of Featured images for the Home screen.
     */
    suspend fun fetchHomeFeaturedImages(limit: Long = 8L): Result<List<InspirationImage>> {
        val path = "images"
        return try {
            val snapshot = try {
                db.collection(path)
                    .whereEqualTo("status", "published")
                    .whereEqualTo("featured", true)
                    .orderBy("displayOrder", Query.Direction.ASCENDING)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()
            } catch (_: FirebaseFirestoreException) {
                db.collection(path)
                    .whereEqualTo("status", "published")
                    .whereEqualTo("featured", true)
                    .limit(limit)
                    .get()
                    .await()
            }
            Result.success(
                snapshot.toObjects(
                    InspirationImage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Fetches a small limited list of Trending images for the Home screen.
     */
    suspend fun fetchHomeTrendingImages(limit: Long = 10L): Result<List<InspirationImage>> {
        val path = "images"
        return try {
            val snapshot = try {
                db.collection(path)
                    .whereEqualTo("status", "published")
                    .whereEqualTo("trending", true)
                    .orderBy("viewCount", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()
            } catch (_: FirebaseFirestoreException) {
                db.collection(path)
                    .whereEqualTo("status", "published")
                    .whereEqualTo("trending", true)
                    .limit(limit)
                    .get()
                    .await()
            }
            Result.success(
                snapshot.toObjects(
                    InspirationImage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Fetches a small limited list of Recommended images for the Home screen.
     */
    suspend fun fetchHomeRecommendedImages(limit: Long = 10L): Result<List<InspirationImage>> {
        val path = "images"
        return try {
            val snapshot = try {
                db.collection(path)
                    .whereEqualTo("status", "published")
                    .whereEqualTo("recommended", true)
                    .orderBy("displayOrder", Query.Direction.ASCENDING)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()
            } catch (_: FirebaseFirestoreException) {
                db.collection(path)
                    .whereEqualTo("status", "published")
                    .whereEqualTo("recommended", true)
                    .limit(limit)
                    .get()
                    .await()
            }
            Result.success(
                snapshot.toObjects(
                    InspirationImage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Fetches related images in the same `categoryId` (excluding `excludeImageId`), with cursor pagination.
     */
    suspend fun fetchRelatedImagesPage(
        categoryId: String,
        excludeImageId: String,
        pageSize: Long = 8L,
        lastSnapshot: DocumentSnapshot? = null
    ): Result<PaginatedImagesResult> {
        val path = "images"
        return try {
            var query: Query = db.collection(path)
                .whereEqualTo("status", "published")
                .whereEqualTo("categoryId", categoryId)
                .orderBy("displayOrder", Query.Direction.ASCENDING)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize + 1L)

            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }

            val snapshot = try {
                query.get().await()
            } catch (_: FirebaseFirestoreException) {
                var fallbackQuery: Query = db.collection(path)
                    .whereEqualTo("status", "published")
                    .whereEqualTo("categoryId", categoryId)
                    .limit(pageSize + 1L)
                if (lastSnapshot != null) {
                    fallbackQuery = fallbackQuery.startAfter(lastSnapshot)
                }
                fallbackQuery.get().await()
            }

            val allItems = snapshot.toObjects(
                InspirationImage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { it.id != excludeImageId }
                .take(pageSize.toInt())

            val newCursor = snapshot.documents.lastOrNull()
            Result.success(
                PaginatedImagesResult(
                    images = allItems,
                    lastSnapshot = newCursor,
                    hasMore = snapshot.documents.size.toLong() >= pageSize
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // PROMPT LOCK VAULT (`imagePrompts/{imageId}` & `promptUnlocks/{uid}_{imageId}`)
    // =========================================================================

    /**
     * Reads the locked prompt from `imagePrompts/{imageId}`.
     * Security rules allow this read ONLY if `promptUnlocks/{uid}_{imageId}` exists for the signed-in user
     * (or if the user is an active admin/owner in `adminUsers/{uid}`).
     */
    suspend fun fetchUnlockedPromptText(imageId: String): Result<String> {
        val cleanId = imageId.trim()
        val path = "imagePrompts/$cleanId"
        return try {
            ensureSignedInUserId()
            require(isValidDocId(cleanId)) { "Invalid image ID." }
            val snap = db.collection("imagePrompts").document(cleanId).get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Prompt text has not been published for this image yet."))
            }
            val doc = snap.toObject(
                ImagePromptDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val text = doc?.promptText?.trim().orEmpty()
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Prompt text is empty."))
            } else {
                Result.success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    /**
     * Records a verified rewarded-ad unlock in `promptUnlocks/{uid}_{imageId}`, increments `unlockCount`
     * by +1, and then reads the unlocked prompt text from `imagePrompts/{imageId}`.
     */
    suspend fun recordVerifiedPromptUnlock(
        image: InspirationImage,
        adNetwork: String = "AdMob"
    ): Result<String> {
        val uid = try {
            ensureSignedInUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val unlockDocId = "${uid}_${image.id}"
        val path = "promptUnlocks/$unlockDocId"
        return try {
            require(isValidDocId(image.id) && isValidDocId(unlockDocId)) { "Invalid unlock document ID" }
            rewardUnlockVerifier.recordUnlockReceipt(db, uid, image, adNetwork)

            logAnalyticsEvent(
                "prompt_unlocked_verified",
                mapOf("image_id" to image.id, "category" to image.categoryName, "ad_network" to adNetwork)
            )
            fetchUnlockedPromptText(image.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // OBSERVABLE STREAMS (Categories, User State, Comments, Settings)
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
                    )
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
        val uid = requireSignedInUserId()
        check(uid == userId) { "User ID mismatch" }
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
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserFavorites(userId: String): Flow<List<FavoriteRecord>> = flow {
        val uid = requireSignedInUserId()
        check(uid == userId) { "User ID mismatch" }
        val path = "favorites"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FavoriteRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
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

    fun observeUserPromptUnlocks(userId: String): Flow<List<PromptUnlock>> = flow {
        val uid = requireSignedInUserId()
        check(uid == userId) { "User ID mismatch" }
        val path = "promptUnlocks"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PromptUnlock::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
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
                    )
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
                    )
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

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireSignedInUserId()
        check(uid == userId) { "User ID mismatch" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
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

    fun observeAdvertisementSettings(): Flow<AdvertisementSettings> = flow {
        val uid = auth.currentUser?.uid
        val path = "advertisementSettings/global"
        emitAll(
            db.collection("advertisementSettings").document("global")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        AdvertisementSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: AdvertisementSettings()
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

    // Fetch single image by ID (for deep linking & notification tap verification)
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
                )
            } else null
            Result.success(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    // Ensure User Profile Document Exists on Sign-In
    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            require(isValidDocId(uid)) { "Invalid user ID" }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val displayName = (user.displayName ?: "Lumiere Creator").trim().take(80).ifBlank { "Lumiere Creator" }
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
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // SAFE COUNTERS (+1 / -1 Atomic Increments & Batched Writes)
    // NOTE: Fully trustworthy server-only counters require Cloud Functions (Firebase Blaze plan).
    // =========================================================================

    /**
     * Increments `viewCount` by +1 (called at most once per image per app session).
     */
    suspend fun recordImageView(imageId: String): Result<Unit> {
        val path = "images/$imageId"
        return try {
            ensureSignedInUserId()
            require(isValidDocId(imageId)) { "Invalid image ID" }
            db.collection("images").document(imageId).update(
                mapOf(
                    "viewCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAnalyticsEvent("image_view", mapOf("image_id" to imageId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Toggles a Like using a batched write (`db.batch()`) combining `likes/{uid}_{imageId}` create/delete
     * with `FieldValue.increment(1L)` / `FieldValue.increment(-1L)` on `images/{imageId}`.
     * Requires a real (non-anonymous) account.
     */
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
                logAnalyticsEvent("image_unliked", mapOf("image_id" to image.id))
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
                logAnalyticsEvent("image_liked", mapOf("image_id" to image.id, "category" to image.categoryName))
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (currentlyLiked) OperationType.DELETE else OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // Toggle Favorite with Deterministic ID `${userId}_${imageId}` (works for anonymous guests and Google users)
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
            val favRef = db.collection("favorites").document(favDocId)
            if (currentlyFavorited) {
                favRef.delete().await()
                logAnalyticsEvent("image_unfavorited", mapOf("image_id" to image.id))
                Result.success(false)
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "imageId" to image.id,
                    "imageTitle" to image.title.trim().take(150).ifBlank { "Untitled Inspiration" },
                    "imageUrl" to image.imageUrl.trim().take(1000),
                    "categoryName" to image.categoryName.trim().take(100).ifBlank { "Photography" },
                    "aspectRatio" to image.aspectRatio.coerceIn(0.2, 5.0),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                favRef.set(payload).await()
                logAnalyticsEvent("image_favorited", mapOf("image_id" to image.id, "category" to image.categoryName))
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (currentlyFavorited) OperationType.DELETE else OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // Record Share Event (+1 safe counter step)
    suspend fun recordPromptShare(imageId: String, categoryName: String): Result<Unit> {
        val path = "images/$imageId"
        return try {
            ensureSignedInUserId()
            require(isValidDocId(imageId)) { "Invalid image ID" }
            db.collection("images").document(imageId).update(
                mapOf(
                    "shareCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAnalyticsEvent("prompt_shared", mapOf("image_id" to imageId, "category" to categoryName))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // Post Comment on an Image (requires real non-anonymous account; batched +1 commentCount)
    suspend fun postComment(imageId: String, rawText: String): Result<Unit> {
        val user = try {
            requireRealUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val uid = user.uid
        val trimmedText = rawText.trim()
        require(trimmedText.isNotEmpty()) { "Comment cannot be empty." }
        require(trimmedText.length <= 500) { "Comment must be 500 characters or fewer." }
        require(isValidDocId(imageId)) { "Invalid image ID." }

        val commentRef = db.collection("comments").document()
        val imageRef = db.collection("images").document(imageId)
        val path = commentRef.path
        return try {
            val userName = (user.displayName ?: "Lumiere Member").trim().take(80).ifBlank { "Lumiere Member" }
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
            logAnalyticsEvent("comment_posted", mapOf("image_id" to imageId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // Delete Own Comment (batched -1 commentCount)
    suspend fun deleteComment(commentId: String, imageId: String): Result<Unit> {
        val path = "comments/$commentId"
        return try {
            requireRealUser()
            require(isValidDocId(commentId) && isValidDocId(imageId)) { "Invalid comment or image ID." }
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
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Reports an inappropriate comment by creating a document in `reports/{reportId}`
     * (`{ commentId, imageId, reporterId, reason, status: "open", createdAt }`)
     * and flagging the comment document in a single batched write.
     */
    suspend fun reportComment(commentId: String, imageId: String, reason: String): Result<Unit> {
        val user = try {
            requireRealUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val reportRef = db.collection("reports").document()
        val path = reportRef.path
        return try {
            require(isValidDocId(commentId) && isValidDocId(imageId)) { "Invalid comment or image ID." }
            val cleanReason = reason.trim().take(300).ifBlank { "Inappropriate content" }
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
            logAnalyticsEvent("comment_reported", mapOf("comment_id" to commentId, "image_id" to imageId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // Delete User Account Data from Firestore
    suspend fun deleteUserAccountData(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireSignedInUserId()
            check(uid == userId) { "Unauthorized account deletion request." }
            db.collection("users").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        private val DefaultClientRewardUnlockVerifier = RewardUnlockVerifier { db, uid, image, adNetwork ->
            val unlockDocId = "${uid}_${image.id}"
            val unlockRef = db.collection("promptUnlocks").document(unlockDocId)
            val existing = unlockRef.get().await()
            if (!existing.exists()) {
                val batch = db.batch()
                val payload = mapOf(
                    "userId" to uid,
                    "imageId" to image.id,
                    "imageTitle" to image.title.trim().take(150).ifBlank { "Inspiration" },
                    "rewardVerified" to true,
                    "adNetwork" to adNetwork.trim().take(50).ifBlank { "AdMob" },
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(unlockRef, payload)
                batch.update(
                    db.collection("images").document(image.id),
                    mapOf(
                        "unlockCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
        }
    }
}
