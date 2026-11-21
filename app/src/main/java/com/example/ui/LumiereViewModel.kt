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
import com.example.data.PromptComingSoonException
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
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class LumiereViewModel(
    private val appContext: Context,
    private val repository: LumiereRepository?,
    private val localPrefs: LocalPreferencesRepository,
    initialUserId: String?
) : ViewModel() {

    companion object {
        private const val SIX_HOURS_MS = 6L * 60L * 60L * 1000L
        private const val SYNC_TIMEOUT_MS = 12_000L

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

    private val _isInitialCacheLoaded = MutableStateFlow(false)
    val isInitialCacheLoaded: StateFlow<Boolean> = _isInitialCacheLoaded.asStateFlow()

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
    // CATALOG STATE (Images & Categories with Instant Local Cache First)
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

    private val _isPromptComingSoon = MutableStateFlow(false)
    val isPromptComingSoon: StateFlow<Boolean> = _isPromptComingSoon.asStateFlow()

    fun clearActiveDetailPrompt() {
        _activeDetailPromptText.value = null
        _isPromptComingSoon.value = false
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

        // 1. Read local cache FIRST and show immediately; dismiss splash as soon as cache is read.
        viewModelScope.launch(Dispatchers.IO) {
            var cachedImgs = localPrefs.loadCachedImages()
            var cachedCats = localPrefs.loadCachedCategories()

            // If DataStore JSON cache is empty, also check Firestore local disk cache (fast, local-only)
            if (cachedImgs.isEmpty() && repository != null) {
                cachedImgs = withTimeoutOrNull(300L) {
                    repository.fetchAllPublishedImages(Source.CACHE).getOrNull()
                }.orEmpty()
            }
            if (cachedCats.isEmpty() && repository != null) {
                cachedCats = withTimeoutOrNull(250L) {
                    repository.fetchAllPublishedCategories(Source.CACHE).getOrNull()
                }.orEmpty()
            }

            if (cachedCats.isNotEmpty()) {
                _categoriesState.value = cachedCats.sortedBy { it.sortOrder }
            }
            if (cachedImgs.isNotEmpty()) {
                _imagesUiState.value = UiState.Success(sortNewestFirst(cachedImgs))
            }

            // Dismiss splash immediately after local cache check!
            _isInitialCacheLoaded.value = true

            // 2. Silently sync from network in the background
            if (repository != null && isOnline.value) {
                val uid = withTimeoutOrNull(5_000L) {
                    runCatching { repository.ensureSignedInUserId() }.getOrNull()
                }
                if (!uid.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        updateCurrentUserId(uid)
                    }
                }
            }
            syncAndReconcileCatalog(force = true, showRefreshIndicator = false)
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

        // 3. Realtime listener on advertisementSettings/global
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

        // 4. Silently sync when connectivity returns
        viewModelScope.launch {
            var wasOnline = isOnline.value
            isOnline.collect { onlineNow ->
                if (onlineNow && !wasOnline) {
                    syncAndReconcileCatalog(force = true, showRefreshIndicator = false)
                }
                wasOnline = onlineNow
            }
        }
    }

    private fun sortNewestFirst(images: List<InspirationImage>): List<InspirationImage> {
        return images.sortedWith(
            compareByDescending<InspirationImage> { it.createdAt?.seconds ?: 0L }
                .thenBy { it.displayOrder }
        )
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
     * Silently syncs in the background when the Home tab is tapped.
     */
    fun onHomeTabSelected() {
        if (isOnline.value) {
            syncAndReconcileCatalog(force = true, showRefreshIndicator = false)
        }
    }

    /**
     * Fetches current published images & categories from Firestore in the background and
     * updates the UI silently without flickering or blocking spinners.
     */
    fun syncAndReconcileCatalog(
        force: Boolean = false,
        showRefreshIndicator: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedImages = localPrefs.loadCachedImages()
            val cachedCategories = localPrefs.loadCachedCategories()

            if (repository == null) {
                if (cachedCategories.isNotEmpty()) _categoriesState.value = cachedCategories
                _imagesUiState.value = UiState.Success(sortNewestFirst(cachedImages))
                _isRefreshing.value = false
                return@launch
            }

            if (!isOnline.value) {
                if (cachedCategories.isNotEmpty()) {
                    _categoriesState.value = cachedCategories
                }
                if (_imagesUiState.value !is UiState.Success) {
                    _imagesUiState.value = UiState.Success(sortNewestFirst(cachedImages))
                }
                _isRefreshing.value = false
                return@launch
            }

            val lastSync = localPrefs.getLastCatalogSyncTimestampMs()
            val withinSixHours = (System.currentTimeMillis() - lastSync) < SIX_HOURS_MS
            if (!force && withinSixHours && cachedImages.isNotEmpty()) {
                if (_imagesUiState.value !is UiState.Success) {
                    _imagesUiState.value = UiState.Success(sortNewestFirst(cachedImages))
                }
                _isRefreshing.value = false
                return@launch
            }

            // Only show skeleton loading if the cache is completely empty on very first launch
            val existingDisplayed = (_imagesUiState.value as? UiState.Success)?.data.orEmpty()
            if (existingDisplayed.isEmpty() && cachedImages.isEmpty()) {
                _imagesUiState.value = UiState.Loading
            } else if (existingDisplayed.isEmpty() && cachedImages.isNotEmpty()) {
                _imagesUiState.value = UiState.Success(sortNewestFirst(cachedImages))
            }

            withTimeoutOrNull(4_000L) {
                runCatching { repository.ensureSignedInUserId() }.getOrNull()
            }?.let { uid ->
                withContext(Dispatchers.Main) {
                    updateCurrentUserId(uid)
                }
            }

            val imagesResult = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                repository.fetchAllPublishedImages(Source.SERVER)
            } ?: Result.failure(IllegalStateException("Network timeout"))

            val categoriesResult = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                repository.fetchAllPublishedCategories(Source.SERVER)
            }

            imagesResult.fold(
                onSuccess = { freshImages ->
                    val sortedFresh = sortNewestFirst(freshImages)
                    val freshCategories = categoriesResult?.getOrNull() ?: _categoriesState.value
                    _categoriesState.value = freshCategories
                    _imagesUiState.value = UiState.Success(sortedFresh)

                    val removedUrls = localPrefs.reconcileAndSaveCatalog(
                        freshImages = sortedFresh,
                        freshCategories = freshCategories
                    )
                    if (removedUrls.isNotEmpty()) {
                        NetworkAndCacheManager.evictImageUrls(appContext, removedUrls)
                    }

                    // Prefetch priority (~20) images + background Wi-Fi prefetch for remaining
                    val allUrls = sortedFresh.map { it.imageUrl }.filter { it.isNotBlank() }
                    NetworkAndCacheManager.prefetchCatalogImages(
                        context = appContext,
                        scope = viewModelScope,
                        priorityUrls = allUrls.take(20),
                        remainingUrls = allUrls.drop(20)
                    )
                },
                onFailure = {
                    val fallback = existingDisplayed.ifEmpty { cachedImages }
                    _imagesUiState.value = UiState.Success(sortNewestFirst(fallback))
                }
            )
            if (showRefreshIndicator || _isRefreshing.value) {
                _isRefreshing.value = false
            }
        }
    }

    fun onPullToRefresh() {
        if (!isOnline.value) {
            emitOfflineMessage()
            _isRefreshing.value = false
            return
        }
        _isRefreshing.value = true
        syncAndReconcileCatalog(force = true, showRefreshIndicator = true)
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
    }

    /**
     * Reveals the prompt for the current detail visit:
     * Ensures `promptUnlocks/{uid}_{imageId}` exists, then reads `imagePrompts/{imageId}`
     * into memory-only state [_activeDetailPromptText].
     * If the prompt does not exist for this image, sets [_isPromptComingSoon] to true
     * so the UI shows "Prompt coming soon"; for network problems shows "Turn on internet and try again".
     */
    fun unlockAndLoadPromptForCurrentVisit(image: InspirationImage, adNetwork: String) {
        if (!isOnline.value) {
            _isPromptComingSoon.value = false
            _activeDetailPromptText.value = null
            emitMessage(appContext.getString(R.string.turn_on_internet_message))
            return
        }
        val repo = repository ?: run {
            _isPromptComingSoon.value = true
            return
        }
        viewModelScope.launch {
            val result = repo.ensureUnlockAndReadPrompt(image, adNetwork)
            result.fold(
                onSuccess = { promptText ->
                    _isPromptComingSoon.value = false
                    _activeDetailPromptText.value = promptText
                },
                onFailure = { err ->
                    android.util.Log.e(
                        "LumiereViewModel",
                        "unlockAndLoadPromptForCurrentVisit failed for imageId=${image.id}",
                        err
                    )
                    if (err is PromptComingSoonException) {
                        _isPromptComingSoon.value = true
                    } else {
                        _isPromptComingSoon.value = false
                        _activeDetailPromptText.value = null
                        emitMessage(appContext.getString(R.string.turn_on_internet_message))
                    }
                }
            )
        }
    }

    fun toggleLike(
        image: InspirationImage,
        onRequireSignIn: (afterSignInAction: () -> Unit) -> Unit
    ) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn {
                executeToggleLike(image)
            }
            return
        }
        executeToggleLike(image)
    }

    private fun executeToggleLike(image: InspirationImage) {
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

    fun postComment(
        imageId: String,
        rawText: String,
        onRequireSignIn: (afterSignInAction: () -> Unit) -> Unit
    ) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn {
                executePostComment(imageId, trimmed)
            }
            return
        }
        executePostComment(imageId, trimmed)
    }

    private fun executePostComment(imageId: String, trimmedText: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            val res = repo.postComment(imageId, trimmedText)
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

    fun reportComment(
        comment: ImageComment,
        reason: String,
        onRequireSignIn: (afterSignInAction: () -> Unit) -> Unit
    ) {
        if (!isOnline.value) {
            emitOfflineMessage()
            return
        }
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn {
                executeReportComment(comment, reason)
            }
            return
        }
        executeReportComment(comment, reason)
    }

    private fun executeReportComment(comment: ImageComment, reason: String) {
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
