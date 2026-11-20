package com.example.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.auth.GoogleSignInManager
import com.example.data.LocalPreferencesRepository
import com.example.data.LumiereRepository
import com.example.data.NetworkAndCacheManager
import com.example.data.ReauthRequiredForDeleteException
import com.example.model.AdvertisementSettings
import com.example.model.AppConfigSettings
import com.example.model.AppNotification
import com.example.model.CategoryItem
import com.example.model.FavoriteRecord
import com.example.model.ImageComment
import com.example.model.InspirationImage
import com.example.model.LikeRecord
import com.example.model.MergedNotificationItem
import com.example.model.ThemePreference
import com.example.model.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LumiereViewModel(
    private val appContext: Context,
    private val repository: LumiereRepository?,
    private val localPrefs: LocalPreferencesRepository,
    initialUserId: String?
) : ViewModel() {

    companion object {
        private const val SIX_HOURS_MS = 6L * 60L * 60L * 1000L

        fun factory(initialUserId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val context = application.applicationContext
                NetworkAndCacheManager.getImageLoader(context)
                val firebaseReady = GoogleSignInManager.isFirebaseConfigured(context)
                val repo = if (firebaseReady) {
                    runCatching { LumiereRepository(context) }.getOrNull()
                } else null
                return LumiereViewModel(
                    appContext = context,
                    repository = repo,
                    localPrefs = LocalPreferencesRepository(context),
                    initialUserId = initialUserId
                ) as T
            }
        }
    }

    private val _currentUserId = MutableStateFlow(initialUserId)
    val currentUserIdFlow: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _isRealUser = MutableStateFlow(false)
    val isRealUserFlow: StateFlow<Boolean> = _isRealUser.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _isRealUser.value = GoogleSignInManager.isRealAuthenticatedUser(user)
        updateCurrentUserId(user?.uid)
    }

    val isOnline: StateFlow<Boolean> = NetworkAndCacheManager.observeConnectivity(appContext)
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkAndCacheManager.isOnlineNow(appContext))

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun emitMessage(message: String) {
        if (message.isNotBlank()) {
            _snackbarMessage.tryEmit(message)
        }
    }

    private fun emitOfflineMessage() {
        emitMessage(appContext.getString(R.string.turn_on_internet_message))
    }

    // =========================================================================
    // CATALOG STATE (Images & Categories with Offline Cache + Reconciliation)
    // =========================================================================

    private val _imagesUiState = MutableStateFlow<UiState<List<InspirationImage>>>(UiState.Loading)
    val imagesUiState: StateFlow<UiState<List<InspirationImage>>> = _imagesUiState.asStateFlow()

    private val _categoriesState = MutableStateFlow<List<CategoryItem>>(emptyList())
    val categoriesState: StateFlow<List<CategoryItem>> = _categoriesState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val viewedImageIdsInSession = mutableSetOf<String>()
    private val pendingUnsyncedViewImageIds = mutableSetOf<String>()

    // =========================================================================
    // MEMORY-ONLY PROMPT STATE (Never persisted to disk, Room, DataStore, or logs)
    // =========================================================================

    private val _activeDetailPromptText = MutableStateFlow<String?>(null)
    val activeDetailPromptText: StateFlow<String?> = _activeDetailPromptText.asStateFlow()

    private val _isLoadingDetailPrompt = MutableStateFlow(false)
    val isLoadingDetailPrompt: StateFlow<Boolean> = _isLoadingDetailPrompt.asStateFlow()

    fun clearActiveDetailPrompt() {
        _activeDetailPromptText.value = null
        _isLoadingDetailPrompt.value = false
    }

    // =========================================================================
    // COMMENTS & BLOCKED USERS
    // =========================================================================

    val blockedUserIds: StateFlow<Set<String>> = localPrefs.blockedUserIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _rawActiveComments = MutableStateFlow<List<ImageComment>>(emptyList())
    val activeComments: StateFlow<List<ImageComment>> = combine(
        _rawActiveComments,
        blockedUserIds
    ) { comments, blocked ->
        comments.filter { it.userId !in blocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var commentsJob: Job? = null

    // =========================================================================
    // USER LIKES & SAVED FAVORITES
    // =========================================================================

    private val firestoreLikesState: StateFlow<Set<String>> = _currentUserId
        .flatMapLatest { uid ->
            if (repository != null && !uid.isNullOrBlank()) {
                repository.observeUserLikes(uid)
                    .map { list -> list.map(LikeRecord::imageId).toSet() }
                    .catch { emit(emptySet()) }
            } else {
                flowOf(emptySet())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val firestoreFavoritesListState: StateFlow<List<FavoriteRecord>> = _currentUserId
        .flatMapLatest { uid ->
            if (repository != null && !uid.isNullOrBlank()) {
                repository.observeUserFavorites(uid)
                    .catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedImageIds: StateFlow<Set<String>> = firestoreLikesState

    val favoritedImageIds: StateFlow<Set<String>> = firestoreFavoritesListState
        .map { list -> list.map(FavoriteRecord::imageId).toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // =========================================================================
    // MERGED NOTIFICATIONS & UNREAD DOT
    // =========================================================================

    private val broadcastNotificationsState: StateFlow<List<AppNotification>> = _currentUserId
        .flatMapLatest {
            repository?.observeNotifications()
                ?.catch { emit(emptyList()) }
                ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val commentRepliesState: StateFlow<List<ImageComment>> = combine(
        _currentUserId,
        _isRealUser
    ) { uid, isReal -> uid to isReal }
        .flatMapLatest { (uid, isReal) ->
            if (repository != null && isReal && !uid.isNullOrBlank()) {
                repository.observeUserCommentReplies(uid)
                    .catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val fetchedPostCache = MutableStateFlow<Map<String, InspirationImage>>(emptyMap())

    val mergedNotificationsState: StateFlow<List<MergedNotificationItem>> = combine(
        broadcastNotificationsState,
        commentRepliesState,
        _imagesUiState,
        fetchedPostCache
    ) { broadcasts, replies, imagesState, extraPosts ->
        val catalogMap = buildMap<String, InspirationImage> {
            putAll(extraPosts)
            if (imagesState is UiState.Success) {
                imagesState.data.forEach { put(it.id, it) }
            }
        }

        val items = mutableListOf<MergedNotificationItem>()
        broadcasts.forEach { notif ->
            val ts = (notif.createdAt?.seconds ?: 0L) * 1000L
            items.add(
                MergedNotificationItem.BroadcastItem(
                    notification = notif,
                    timestampMs = ts
                )
            )
        }
        replies.forEach { comment ->
            val post = catalogMap[comment.imageId]
            if (post == null && comment.imageId.isNotBlank()) {
                ensurePostFetchedForReply(comment.imageId)
            }
            val ts = ((comment.updatedAt ?: comment.createdAt)?.seconds ?: 0L) * 1000L
            items.add(
                MergedNotificationItem.CommentReplyItem(
                    comment = comment,
                    postTitle = post?.title.orEmpty(),
                    postThumbnailUrl = post?.imageUrl.orEmpty(),
                    timestampMs = ts
                )
            )
        }
        items.sortedByDescending { it.timestampMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasUnreadNotifications: StateFlow<Boolean> = combine(
        mergedNotificationsState,
        localPrefs.lastSeenNotificationTimestampMs
    ) { items, lastSeenMs ->
        val newestTs = items.maxOfOrNull { it.timestampMs } ?: 0L
        newestTs > 0L && newestTs > lastSeenMs
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun markNotificationsTabOpened() {
        viewModelScope.launch {
            val newestTs = mergedNotificationsState.value.maxOfOrNull { it.timestampMs } ?: 0L
            val targetTs = maxOf(System.currentTimeMillis(), newestTs)
            localPrefs.markNotificationsSeen(targetTs)
        }
    }

    private fun ensurePostFetchedForReply(imageId: String) {
        if (repository == null || imageId.isBlank()) return
        if (fetchedPostCache.value.containsKey(imageId)) return
        viewModelScope.launch {
            val img = repository.fetchImageById(imageId).getOrNull() ?: return@launch
            fetchedPostCache.value = fetchedPostCache.value + (imageId to img)
        }
    }

    // =========================================================================
    // GLOBAL SETTINGS & AD SETTINGS (Locked on Initial Load per App Open)
    // =========================================================================

    val appConfigSettings: StateFlow<AppConfigSettings> = _currentUserId
        .flatMapLatest {
            repository?.observeAppConfigSettings()
                ?.catch { emit(AppConfigSettings()) }
                ?: flowOf(AppConfigSettings())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppConfigSettings()
        )

    private val _advertisementSettings = MutableStateFlow(
        AdvertisementSettings.defaultWhenDocumentMissing()
    )
    val advertisementSettings: StateFlow<AdvertisementSettings> = _advertisementSettings.asStateFlow()
    private var initialAdSettingsLockedForSession = false

    val themePreference: StateFlow<ThemePreference> = localPrefs.themePreference
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.DARK
        )

    val languageTag: StateFlow<String> = localPrefs.languageTag
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "en"
        )

    val notificationPermissionAsked: StateFlow<Boolean> = localPrefs.notificationPermissionAsked
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    init {
        if (repository != null) {
            runCatching { Firebase.auth.addAuthStateListener(authStateListener) }
        }

        // 1. Load cached catalog immediately for instant startup & offline viewing
        viewModelScope.launch {
            val cachedImgs = localPrefs.loadCachedImages()
            val cachedCats = localPrefs.loadCachedCategories()
            if (cachedCats.isNotEmpty()) {
                _categoriesState.value = cachedCats
            }
            if (cachedImgs.isNotEmpty()) {
                _imagesUiState.value = UiState.Success(cachedImgs)
            }

            // Ensure anonymous session if needed, then sync with server
            if (repository != null && isOnline.value) {
                val uid = runCatching { repository.ensureSignedInUserId() }.getOrNull()
                if (!uid.isNullOrBlank()) {
                    updateCurrentUserId(uid)
                }
            }
            syncAndReconcileCatalog(force = true)
        }

        // 2. Listen to categories realtime stream when online
        viewModelScope.launch {
            _currentUserId.flatMapLatest {
                repository?.observePublishedCategories()
                    ?.catch { emit(emptyList()) }
                    ?: flowOf(emptyList())
            }.collect { liveCategories ->
                if (liveCategories.isNotEmpty() || isOnline.value) {
                    _categoriesState.value = liveCategories
                }
            }
        }

        // 3. Realtime listener on advertisementSettings/global (locked on first emission for current session; updates cache for next open)
        viewModelScope.launch {
            _currentUserId.flatMapLatest {
                repository?.observeAdvertisementSettings()
                    ?.catch { emit(AdvertisementSettings.defaultWhenDocumentMissing()) }
                    ?: flowOf(AdvertisementSettings.defaultWhenDocumentMissing())
            }.collect { latestSettings ->
                if (!initialAdSettingsLockedForSession) {
                    _advertisementSettings.value = latestSettings
                    initialAdSettingsLockedForSession = true
                }
            }
        }

        // 4. Sync and reconcile automatically when connectivity returns (or if > 6 hours old)
        viewModelScope.launch {
            var wasOnline = isOnline.value
            isOnline.collect { onlineNow ->
                if (onlineNow && !wasOnline) {
                    val lastSync = localPrefs.getLastCatalogSyncTimestampMs()
                    val currentImages = (_imagesUiState.value as? UiState.Success)?.data.orEmpty()
                    val needsSync = currentImages.isEmpty() ||
                        (System.currentTimeMillis() - lastSync) >= SIX_HOURS_MS
                    if (needsSync) {
                        syncAndReconcileCatalog(force = true)
                    }
                }
                wasOnline = onlineNow
            }
        }
    }

    fun updateCurrentUserId(newUid: String?) {
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        _isRealUser.value = GoogleSignInManager.isRealAuthenticatedUser(currentUser)

        if (_currentUserId.value != newUid) {
            _currentUserId.value = newUid
        }
        if (!newUid.isNullOrBlank() && repository != null && pendingUnsyncedViewImageIds.isNotEmpty() && isOnline.value) {
            val toFlush = pendingUnsyncedViewImageIds.toList()
            pendingUnsyncedViewImageIds.clear()
            toFlush.forEach { imageId ->
                viewModelScope.launch {
                    repository.recordImageView(imageId)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (repository != null) {
            runCatching { Firebase.auth.removeAuthStateListener(authStateListener) }
        }
    }

    /**
     * Fetches current published images & categories from Firestore and reconciles the local cache:
     * - Anything no longer published (deleted or unlisted in admin) is removed from local cache,
     *   lists, and saved screen, and its cached image file is evicted from Coil.
     */
    fun syncAndReconcileCatalog(force: Boolean = false) {
        viewModelScope.launch {
            val cachedImages = localPrefs.loadCachedImages()
            val cachedCategories = localPrefs.loadCachedCategories()

            if (repository == null) {
                _categoriesState.value = cachedCategories
                _imagesUiState.value = UiState.Success(cachedImages)
                _isRefreshing.value = false
                return@launch
            }

            if (!isOnline.value) {
                if (cachedCategories.isNotEmpty()) {
                    _categoriesState.value = cachedCategories
                }
                _imagesUiState.value = UiState.Success(cachedImages)
                _isRefreshing.value = false
                return@launch
            }

            val lastSync = localPrefs.getLastCatalogSyncTimestampMs()
            val withinSixHours = (System.currentTimeMillis() - lastSync) < SIX_HOURS_MS
            if (!force && withinSixHours && cachedImages.isNotEmpty()) {
                _imagesUiState.value = UiState.Success(cachedImages)
                _isRefreshing.value = false
                return@launch
            }

            if (cachedImages.isEmpty()) {
                _imagesUiState.value = UiState.Loading
            }

            runCatching { repository.ensureSignedInUserId() }.getOrNull()?.let { uid ->
                updateCurrentUserId(uid)
            }

            val imagesResult = repository.fetchAllPublishedImages(Source.SERVER)
            val categoriesResult = repository.fetchAllPublishedCategories(Source.SERVER)

            imagesResult.fold(
                onSuccess = { freshImages ->
                    val freshCategories = categoriesResult.getOrNull() ?: _categoriesState.value
                    _categoriesState.value = freshCategories
                    _imagesUiState.value = UiState.Success(freshImages)

                    val removedUrls = localPrefs.reconcileAndSaveCatalog(
                        freshImages = freshImages,
                        freshCategories = freshCategories
                    )
                    if (removedUrls.isNotEmpty()) {
                        NetworkAndCacheManager.evictImageUrls(appContext, removedUrls)
                    }

                    // Prefetch priority (~20) images + background Wi-Fi prefetch for remaining
                    val allUrls = freshImages.map { it.imageUrl }.filter { it.isNotBlank() }
                    NetworkAndCacheManager.prefetchCatalogImages(
                        context = appContext,
                        scope = viewModelScope,
                        priorityUrls = allUrls.take(20),
                        remainingUrls = allUrls.drop(20)
                    )
                },
                onFailure = { error ->
                    if (cachedImages.isNotEmpty()) {
                        _imagesUiState.value = UiState.Success(cachedImages)
                    } else {
                        _imagesUiState.value = UiState.Error(
                            error.localizedMessage ?: appContext.getString(R.string.error_loading_content)
                        )
                    }
                }
            )
            _isRefreshing.value = false
        }
    }

    fun onPullToRefresh() {
        if (!isOnline.value) {
            emitOfflineMessage()
            _isRefreshing.value = false
            return
        }
        _isRefreshing.value = true
        syncAndReconcileCatalog(force = true)
    }

    fun observeCommentsForImage(imageId: String) {
        commentsJob?.cancel()
        _rawActiveComments.value = emptyList()
        if (repository == null || imageId.isBlank()) return
        commentsJob = viewModelScope.launch {
            repository.observeImageComments(imageId)
                .catch { emit(emptyList()) }
                .collect { list ->
                    _rawActiveComments.value = list
                }
        }
    }

    fun onImageDetailOpened(image: InspirationImage) {
        clearActiveDetailPrompt()

        // Prefetch viewed image + next ~20 images in the catalog
        val currentCatalog = (_imagesUiState.value as? UiState.Success)?.data.orEmpty()
        val idx = currentCatalog.indexOfFirst { it.id == image.id }.coerceAtLeast(0)
        val priorityUrls = (listOf(image.imageUrl) + currentCatalog.drop(idx + 1).take(20).map { it.imageUrl })
            .filter { it.isNotBlank() }
        val remainingUrls = currentCatalog.map { it.imageUrl }.filter { it !in priorityUrls }
        NetworkAndCacheManager.prefetchCatalogImages(
            context = appContext,
            scope = viewModelScope,
            priorityUrls = priorityUrls,
            remainingUrls = remainingUrls
        )

        // Increment viewCount by +1 at most once per image per app session
        if (viewedImageIdsInSession.add(image.id)) {
            if (!_currentUserId.value.isNullOrBlank() && isOnline.value) {
                viewModelScope.launch {
                    repository?.recordImageView(image.id)
                }
            } else {
                pendingUnsyncedViewImageIds.add(image.id)
            }
        }

        // If reusePreviousUnlocks == true and online, check if already unlocked by this UID in Firestore
        if (_advertisementSettings.value.reusePreviousUnlocks && isOnline.value && repository != null) {
            viewModelScope.launch {
                val previousPrompt = repository.readPreviouslyUnlockedPromptIfAvailable(image.id).getOrNull()
                if (!previousPrompt.isNullOrBlank()) {
                    _activeDetailPromptText.value = previousPrompt
                }
            }
        }
    }

    /**
     * Executes step (d) of the Prompt flow:
     * Ensures `promptUnlocks/{uid}_{imageId}` exists, then reads `imagePrompts/{imageId}`
     * into memory-only state [_activeDetailPromptText].
     */
    fun unlockAndLoadPromptForCurrentVisit(image: InspirationImage, adNetwork: String) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val repo = repository ?: run {
            emitMessage(appContext.getString(R.string.prompt_unavailable))
            return
        }
        viewModelScope.launch {
            _isLoadingDetailPrompt.value = true
            val result = repo.ensureUnlockAndReadPrompt(image, adNetwork)
            _isLoadingDetailPrompt.value = false
            result.fold(
                onSuccess = { promptText ->
                    _activeDetailPromptText.value = promptText
                },
                onFailure = {
                    emitMessage(appContext.getString(R.string.prompt_unavailable))
                }
            )
        }
    }

    fun toggleLike(image: InspirationImage, onRequireSignIn: () -> Unit) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn()
            return
        }
        val repo = repository ?: return
        val currentlyLiked = likedImageIds.value.contains(image.id)
        viewModelScope.launch {
            val result = repo.toggleLike(image, currentlyLiked)
            result.onSuccess { nowLiked ->
                val delta = if (nowLiked) 1L else -1L
                updateLocalCatalogImageCount(image.id) { img ->
                    img.copy(likeCount = (img.likeCount + delta).coerceAtLeast(0L))
                }
            }.onFailure {
                emitMessage(appContext.getString(R.string.error_loading_content))
            }
        }
    }

    fun toggleFavorite(image: InspirationImage) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val repo = repository ?: return
        val currentlyFavorited = favoritedImageIds.value.contains(image.id)
        viewModelScope.launch {
            val result = repo.toggleFavorite(image, currentlyFavorited)
            result.onFailure {
                emitMessage(appContext.getString(R.string.error_loading_content))
            }
        }
    }

    fun onPostShared(image: InspirationImage) {
        if (!isOnline.value) return
        viewModelScope.launch {
            repository?.recordPromptShare(image.id)
        }
    }

    fun postComment(imageId: String, rawText: String, onRequireSignIn: () -> Unit) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn()
            return
        }
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return
        val repo = repository ?: return
        viewModelScope.launch {
            val res = repo.postComment(imageId, trimmed)
            res.onSuccess {
                updateLocalCatalogImageCount(imageId) { img ->
                    img.copy(commentCount = img.commentCount + 1L)
                }
            }.onFailure {
                emitMessage(appContext.getString(R.string.error_loading_content))
            }
        }
    }

    fun deleteComment(comment: ImageComment) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val repo = repository ?: return
        viewModelScope.launch {
            val res = repo.deleteComment(comment.id, comment.imageId)
            res.onSuccess {
                updateLocalCatalogImageCount(comment.imageId) { img ->
                    img.copy(commentCount = (img.commentCount - 1L).coerceAtLeast(0L))
                }
            }.onFailure {
                emitMessage(appContext.getString(R.string.error_loading_content))
            }
        }
    }

    fun reportComment(comment: ImageComment, reason: String, onRequireSignIn: () -> Unit) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn()
            return
        }
        val repo = repository ?: return
        viewModelScope.launch {
            val res = repo.reportComment(comment.id, comment.imageId, reason)
            res.onSuccess {
                emitMessage(appContext.getString(R.string.report_submitted_message))
            }.onFailure {
                emitMessage(appContext.getString(R.string.error_loading_content))
            }
        }
    }

    fun blockUserLocally(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            localPrefs.blockUser(userId)
            emitMessage(appContext.getString(R.string.user_blocked_message))
        }
    }

    private fun updateLocalCatalogImageCount(
        imageId: String,
        transform: (InspirationImage) -> InspirationImage
    ) {
        val current = (_imagesUiState.value as? UiState.Success)?.data ?: return
        _imagesUiState.value = UiState.Success(
            current.map { if (it.id == imageId) transform(it) else it }
        )
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            localPrefs.setThemePreference(preference)
        }
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            localPrefs.setLanguageTag(tag)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    fun markNotificationPermissionPromptShown() {
        viewModelScope.launch {
            localPrefs.markNotificationPermissionAsked()
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            NetworkAndCacheManager.clearAllImageCache(appContext)
            emitMessage(appContext.getString(R.string.image_cache_cleared))
        }
    }

    suspend fun resolveDeepLinkImage(imageId: String, currentList: List<InspirationImage>): InspirationImage? {
        val cleaned = imageId.trim()
        if (cleaned.isBlank()) return null
        currentList.firstOrNull { it.id == cleaned }?.let { return it }
        return repository?.fetchImageById(cleaned)?.getOrNull()
    }

    fun deleteCurrentAccount(
        onSuccess: () -> Unit,
        onReauthRequired: () -> Unit
    ) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val repo = repository ?: return
        viewModelScope.launch {
            val result = repo.deleteUserAccountAndData()
            result.fold(
                onSuccess = {
                    emitMessage(appContext.getString(R.string.account_deleted_message))
                    onSuccess()
                },
                onFailure = { err ->
                    if (err is ReauthRequiredForDeleteException) {
                        emitMessage(appContext.getString(R.string.delete_account_reauth_message))
                        onReauthRequired()
                    } else {
                        emitMessage(appContext.getString(R.string.error_loading_content))
                    }
                }
            )
        }
    }
}
