package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.GoogleSignInManager
import com.example.data.LocalPreferencesRepository
import com.example.data.LumiereRepository
import com.example.model.AdvertisementSettings
import com.example.model.AppConfigSettings
import com.example.model.AppNotification
import com.example.model.CategoryItem
import com.example.model.FavoriteRecord
import com.example.model.ImageCollectionSummary
import com.example.model.ImageComment
import com.example.model.InspirationImage
import com.example.model.LikeRecord
import com.example.model.PromptUnlock
import com.example.model.ThemePreference
import com.example.model.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    private val repository: LumiereRepository?,
    private val localPrefs: LocalPreferencesRepository,
    initialUserId: String?
) : ViewModel() {

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Reactive currentUserId StateFlow so flows that depend on UID (likes, favorites, unlocks, views)
    // automatically re-initialize as soon as signInAnonymously() or Google Sign-In completes.
    private val _currentUserId = MutableStateFlow<String?>(initialUserId)
    val currentUserIdFlow: StateFlow<String?> = _currentUserId.asStateFlow()
    val currentUserId: String?
        get() = _currentUserId.value

    // Session-level view tracking to ensure each image view is counted at most once per app session
    private val viewedImageIdsInSession = mutableSetOf<String>()
    private val pendingUnsyncedViewImageIds = mutableSetOf<String>()
    private val localViewDeltas = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val localLikeDeltas = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val localUnlockDeltas = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Anti-spam comment state
    private var lastCommentTimeMs: Long = 0L
    private var lastCommentText: String = ""

    // Paginated images state (limit 20 per page, startAfter cursor)
    private val _rawImagesState = MutableStateFlow<UiState<List<InspirationImage>>>(UiState.Loading)
    private var lastImagesCursor: DocumentSnapshot? = null
    private val _hasMoreImages = MutableStateFlow(false)
    val hasMoreImages: StateFlow<Boolean> = _hasMoreImages.asStateFlow()
    private val _isLoadingMoreImages = MutableStateFlow(false)
    val isLoadingMoreImages: StateFlow<Boolean> = _isLoadingMoreImages.asStateFlow()

    // Small limited Home section queries
    private val _homeFeaturedImages = MutableStateFlow<List<InspirationImage>>(emptyList())
    val homeFeaturedImages: StateFlow<List<InspirationImage>> = _homeFeaturedImages.asStateFlow()

    private val _homeTrendingImages = MutableStateFlow<List<InspirationImage>>(emptyList())
    val homeTrendingImages: StateFlow<List<InspirationImage>> = _homeTrendingImages.asStateFlow()

    private val _homeRecommendedImages = MutableStateFlow<List<InspirationImage>>(emptyList())
    val homeRecommendedImages: StateFlow<List<InspirationImage>> = _homeRecommendedImages.asStateFlow()

    // Paginated related images for the active ImageDetailScreen
    private val _relatedImages = MutableStateFlow<List<InspirationImage>>(emptyList())
    val relatedImages: StateFlow<List<InspirationImage>> = _relatedImages.asStateFlow()
    private var lastRelatedCursor: DocumentSnapshot? = null
    private val _hasMoreRelatedImages = MutableStateFlow(false)
    val hasMoreRelatedImages: StateFlow<Boolean> = _hasMoreRelatedImages.asStateFlow()

    // Unlocked prompt texts loaded from `imagePrompts/{imageId}` (readable only after `promptUnlocks/{uid}_{imageId}` exists)
    private val _unlockedPromptsMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val unlockedPromptsMap: StateFlow<Map<String, String>> = _unlockedPromptsMap.asStateFlow()

    val themePreference: StateFlow<ThemePreference> = localPrefs.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ThemePreference.DARK)

    val recentlyViewedIds: StateFlow<List<String>> = localPrefs.recentlyViewedIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val localUnlockedIds: StateFlow<Set<String>> = localPrefs.localUnlockedIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val localLikedIds: StateFlow<Set<String>> = localPrefs.localLikedIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val localFavoritedIds: StateFlow<Set<String>> = localPrefs.localFavoritedIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    // Combined effective images (strictly from Firestore — never fake data)
    val imagesUiState: StateFlow<UiState<List<InspirationImage>>> = combine(
        _rawImagesState,
        localViewDeltas,
        localLikeDeltas,
        localUnlockDeltas
    ) { state, viewDeltas, likeDeltas, unlockDeltas ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(state.message)
            is UiState.Success -> {
                val enriched = state.data.map { img ->
                    img.copy(
                        viewCount = (img.viewCount + (viewDeltas[img.id] ?: 0L)).coerceAtLeast(0L),
                        likeCount = (img.likeCount + (likeDeltas[img.id] ?: 0L)).coerceAtLeast(0L),
                        unlockCount = (img.unlockCount + (unlockDeltas[img.id] ?: 0L)).coerceAtLeast(0L)
                    )
                }
                UiState.Success(enriched)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    // Categories (Re-subscribes when uid becomes available)
    val categoriesState: StateFlow<List<CategoryItem>> = currentUserIdFlow
        .flatMapLatest { _ ->
            if (repository != null) {
                repository.observePublishedCategories()
                    .map { list -> list.sortedBy { it.sortOrder } }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // User Likes from Firestore (Re-initializes reactively whenever currentUserIdFlow changes)
    private val firestoreLikesState: StateFlow<List<LikeRecord>> = currentUserIdFlow
        .flatMapLatest { uid ->
            if (repository != null && !uid.isNullOrBlank()) {
                repository.observeUserLikes(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val likedImageIds: StateFlow<Set<String>> = combine(
        firestoreLikesState,
        localLikedIds
    ) { remoteLikes, localLikes ->
        remoteLikes.map { it.imageId }.toSet() + localLikes
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    // User Favorites from Firestore (Re-initializes reactively whenever currentUserIdFlow changes)
    private val firestoreFavoritesState: StateFlow<List<FavoriteRecord>> = currentUserIdFlow
        .flatMapLatest { uid ->
            if (repository != null && !uid.isNullOrBlank()) {
                repository.observeUserFavorites(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val favoritedImageIds: StateFlow<Set<String>> = combine(
        firestoreFavoritesState,
        localFavoritedIds
    ) { remoteFavs, localFavs ->
        remoteFavs.map { it.imageId }.toSet() + localFavs
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    // User Unlocked Prompts from Firestore (Re-initializes reactively whenever currentUserIdFlow changes)
    private val firestorePromptUnlocksState: StateFlow<List<PromptUnlock>> = currentUserIdFlow
        .flatMapLatest { uid ->
            if (repository != null && !uid.isNullOrBlank()) {
                repository.observeUserPromptUnlocks(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unlockedImageIds: StateFlow<Set<String>> = combine(
        firestorePromptUnlocksState,
        localUnlockedIds
    ) { remoteUnlocks, localUnlocks ->
        remoteUnlocks.map { it.imageId }.toSet() + localUnlocks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    // Notifications Feed (Strictly from Firestore; no fake notifications)
    val notificationsState: StateFlow<List<AppNotification>> = currentUserIdFlow
        .flatMapLatest { _ ->
            if (repository != null) {
                repository.observeNotifications()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Global App Config Settings
    val appConfigSettings: StateFlow<AppConfigSettings> = currentUserIdFlow
        .flatMapLatest { _ ->
            if (repository != null) {
                repository.observeAppConfigSettings()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(AppConfigSettings())
                    }
            } else {
                flowOf(AppConfigSettings())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppConfigSettings())

    // Global Advertisement Settings
    val advertisementSettings: StateFlow<AdvertisementSettings> = currentUserIdFlow
        .flatMapLatest { _ ->
            if (repository != null) {
                repository.observeAdvertisementSettings()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(AdvertisementSettings())
                    }
            } else {
                flowOf(AdvertisementSettings())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdvertisementSettings())

    // Active Image Comments Observation (Strictly from Firestore)
    private val _activeComments = MutableStateFlow<List<ImageComment>>(emptyList())
    val activeComments: StateFlow<List<ImageComment>> = _activeComments.asStateFlow()
    private var commentsJob: Job? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        updateCurrentUserId(user?.uid)
    }

    init {
        if (repository != null) {
            runCatching { Firebase.auth.addAuthStateListener(authStateListener) }
            viewModelScope.launch {
                // 1. Ensure automatic Firebase Anonymous Authentication before any Firestore work
                val ensuredUid = runCatching { repository.ensureSignedInUserId() }.getOrNull()
                if (!ensuredUid.isNullOrBlank()) {
                    updateCurrentUserId(ensuredUid)
                }
                // 2. Load initial catalog after auth session is ready
                refreshCatalog()
            }
        } else {
            refreshCatalog()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (repository != null) {
            runCatching { Firebase.auth.removeAuthStateListener(authStateListener) }
        }
    }

    /**
     * Updates the active UID reactively when `signInAnonymously()` or Google `linkWithCredential` completes,
     * flushing any pending view increments and syncing user profile if non-anonymous.
     */
    fun updateCurrentUserId(newUid: String?) {
        val previous = _currentUserId.value
        _currentUserId.value = newUid
        if (!newUid.isNullOrBlank() && repository != null) {
            viewModelScope.launch {
                // Flush any image views opened while anonymous auth was still initializing
                val pendingViews = pendingUnsyncedViewImageIds.toList()
                pendingUnsyncedViewImageIds.clear()
                pendingViews.forEach { imageId ->
                    repository.recordImageView(imageId)
                }

                // If upgraded to a real Google account, ensure user profile document exists
                val user = Firebase.auth.currentUser
                if (user != null && !user.isAnonymous) {
                    repository.ensureUserProfile(user)
                }

                // If UID just became available and catalog was empty or errored, refresh
                val currentCatalog = _rawImagesState.value
                if (previous.isNullOrBlank() &&
                    (currentCatalog is UiState.Error || (currentCatalog is UiState.Success && currentCatalog.data.isEmpty()))
                ) {
                    refreshCatalog()
                }
            }
        }
    }

    /**
     * Loads the initial paginated page of images (limit 20) and small limited queries
     * for Home screen sections (Featured, Trending, Recommended).
     */
    fun refreshCatalog() {
        if (repository == null) {
            _rawImagesState.value = UiState.Success(emptyList())
            _homeFeaturedImages.value = emptyList()
            _homeTrendingImages.value = emptyList()
            _homeRecommendedImages.value = emptyList()
            _hasMoreImages.value = false
            return
        }
        viewModelScope.launch {
            _rawImagesState.value = UiState.Loading
            lastImagesCursor = null

            // Ensure anonymous session if currentUser is still null before running Firestore queries
            runCatching {
                val uid = repository.ensureSignedInUserId()
                if (_currentUserId.value != uid) {
                    _currentUserId.value = uid
                }
            }

            val pageDeferred = async { repository.fetchImagesPage(pageSize = 20L, lastSnapshot = null) }
            val featuredDeferred = async { repository.fetchHomeFeaturedImages(limit = 8L) }
            val trendingDeferred = async { repository.fetchHomeTrendingImages(limit = 10L) }
            val recommendedDeferred = async { repository.fetchHomeRecommendedImages(limit = 10L) }

            val pageResult = pageDeferred.await()
            val featuredResult = featuredDeferred.await().getOrDefault(emptyList())
            val trendingResult = trendingDeferred.await().getOrDefault(emptyList())
            val recommendedResult = recommendedDeferred.await().getOrDefault(emptyList())

            _homeFeaturedImages.value = featuredResult
            _homeTrendingImages.value = trendingResult
            _homeRecommendedImages.value = recommendedResult

            pageResult.onSuccess { page ->
                lastImagesCursor = page.lastSnapshot
                _hasMoreImages.value = page.hasMore
                val combinedUnique = (page.images + featuredResult + trendingResult + recommendedResult)
                    .distinctBy { it.id }
                _rawImagesState.value = UiState.Success(combinedUnique)
            }.onFailure { err ->
                _rawImagesState.value = UiState.Error(
                    err.localizedMessage ?: "Unable to load photography inspirations from Firestore."
                )
            }
        }
    }

    /**
     * Loads the next cursor-paginated page (limit 20, `startAfter` cursor) of published images.
     */
    fun loadNextImagesPage() {
        val repo = repository ?: return
        if (_isLoadingMoreImages.value || !_hasMoreImages.value) return
        val cursor = lastImagesCursor ?: return

        viewModelScope.launch {
            _isLoadingMoreImages.value = true
            val result = repo.fetchImagesPage(pageSize = 20L, lastSnapshot = cursor)
            result.onSuccess { nextPage ->
                lastImagesCursor = nextPage.lastSnapshot
                _hasMoreImages.value = nextPage.hasMore
                val currentList = (_rawImagesState.value as? UiState.Success)?.data.orEmpty()
                val merged = (currentList + nextPage.images).distinctBy { it.id }
                _rawImagesState.value = UiState.Success(merged)
            }
            _isLoadingMoreImages.value = false
        }
    }

    /**
     * Loads related images from the same `categoryId` (excluding the current image), with cursor pagination.
     */
    fun loadRelatedImagesFor(image: InspirationImage, loadMore: Boolean = false) {
        val repo = repository
        if (repo == null) {
            _relatedImages.value = emptyList()
            _hasMoreRelatedImages.value = false
            return
        }
        viewModelScope.launch {
            if (!loadMore) {
                lastRelatedCursor = null
                _relatedImages.value = emptyList()
            }
            val result = repo.fetchRelatedImagesPage(
                categoryId = image.categoryId,
                excludeImageId = image.id,
                pageSize = 8L,
                lastSnapshot = if (loadMore) lastRelatedCursor else null
            )
            result.onSuccess { page ->
                lastRelatedCursor = page.lastSnapshot
                _hasMoreRelatedImages.value = page.hasMore
                _relatedImages.value = if (loadMore) {
                    (_relatedImages.value + page.images).distinctBy { it.id }
                } else {
                    page.images
                }
            }
        }
    }

    /**
     * Reads `imagePrompts/{imageId}` if the user (anonymous guest or Google user) already has a verified unlock receipt.
     */
    fun ensurePromptLoadedIfUnlocked(imageId: String) {
        if (repository == null) return
        if (_unlockedPromptsMap.value.containsKey(imageId)) return
        viewModelScope.launch {
            repository.fetchUnlockedPromptText(imageId).onSuccess { promptText ->
                _unlockedPromptsMap.value = _unlockedPromptsMap.value + (imageId to promptText)
            }
        }
    }

    fun observeCommentsForImage(imageId: String) {
        commentsJob?.cancel()
        if (repository != null) {
            commentsJob = viewModelScope.launch {
                repository.observeImageComments(imageId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
                    .map { comments ->
                        comments.sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }
                    .collect { sorted ->
                        _activeComments.value = sorted
                    }
            }
        } else {
            _activeComments.value = emptyList()
        }
    }

    /**
     * Counts a view at most once per image per app session (works for anonymous guests and Google users).
     */
    fun onImageDetailOpened(image: InspirationImage) {
        viewModelScope.launch {
            localPrefs.recordRecentlyViewed(image.id)
            loadRelatedImagesFor(image, loadMore = false)
            if (unlockedImageIds.value.contains(image.id)) {
                ensurePromptLoadedIfUnlocked(image.id)
            }
            if (viewedImageIdsInSession.add(image.id)) {
                localViewDeltas.value = localViewDeltas.value +
                    (image.id to ((localViewDeltas.value[image.id] ?: 0L) + 1L))
                if (repository != null) {
                    val res = repository.recordImageView(image.id)
                    if (res.isFailure) {
                        pendingUnsyncedViewImageIds.add(image.id)
                    }
                }
            }
        }
    }

    /**
     * Likes require a real Google account (`sign_in_provider != 'anonymous'`).
     */
    fun toggleLike(image: InspirationImage, onRequireSignIn: () -> Unit) {
        val currentUser = Firebase.auth.currentUser
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn()
            return
        }
        val currentlyLiked = likedImageIds.value.contains(image.id)
        viewModelScope.launch {
            localPrefs.toggleLocalLike(image.id)
            val delta = if (currentlyLiked) -1L else 1L
            localLikeDeltas.value = localLikeDeltas.value +
                (image.id to ((localLikeDeltas.value[image.id] ?: 0L) + delta))

            if (repository != null) {
                val res = repository.toggleLike(image, currentlyLiked)
                res.onFailure { err ->
                    _snackbarMessage.tryEmit(err.localizedMessage ?: "Unable to sync like right now.")
                }
            }
        }
    }

    /**
     * Favorites work for both anonymous guests and Google-authenticated users.
     */
    fun toggleFavorite(image: InspirationImage, onRequireSignIn: () -> Unit = {}) {
        val currentlyFav = favoritedImageIds.value.contains(image.id)
        viewModelScope.launch {
            localPrefs.toggleLocalFavorite(image.id)
            if (repository != null) {
                val uid = runCatching { repository.ensureSignedInUserId() }.getOrNull()
                if (!uid.isNullOrBlank() && _currentUserId.value != uid) {
                    updateCurrentUserId(uid)
                }
                repository.toggleFavorite(image, currentlyFav)
            }
            _snackbarMessage.tryEmit(
                if (currentlyFav) "Removed from your Favorites" else "Saved to your Favorites"
            )
        }
    }

    /**
     * Rewarded-ad prompt unlock works for both anonymous guests and Google-authenticated users:
     * writes `promptUnlocks/{uid}_{imageId}`, increments `unlockCount` by +1, and reads `imagePrompts/{imageId}`.
     */
    fun onVerifiedRewardPromptUnlock(image: InspirationImage) {
        viewModelScope.launch {
            localPrefs.markPromptUnlockedLocally(image.id)
            localUnlockDeltas.value = localUnlockDeltas.value +
                (image.id to ((localUnlockDeltas.value[image.id] ?: 0L) + 1L))
            if (repository != null) {
                val uid = runCatching { repository.ensureSignedInUserId() }.getOrNull()
                if (!uid.isNullOrBlank() && _currentUserId.value != uid) {
                    updateCurrentUserId(uid)
                }
                val result = repository.recordVerifiedPromptUnlock(image, adNetwork = "AdMob")
                result.onSuccess { unlockedText ->
                    _unlockedPromptsMap.value = _unlockedPromptsMap.value + (image.id to unlockedText)
                    _snackbarMessage.tryEmit("Prompt unlocked! You can now copy or share the complete prompt.")
                }.onFailure { err ->
                    _snackbarMessage.tryEmit(
                        err.localizedMessage ?: "Reward verified; prompt will load once Firestore is configured."
                    )
                }
            } else {
                _snackbarMessage.tryEmit("Connect google-services.json to load locked prompts from Firestore.")
            }
        }
    }

    fun onPromptShared(image: InspirationImage) {
        viewModelScope.launch {
            if (repository != null) {
                repository.recordPromptShare(image.id, image.categoryName)
            }
        }
    }

    /**
     * Posting comments requires a real Google account (`sign_in_provider != 'anonymous'`).
     */
    fun postComment(imageId: String, rawText: String, onRequireSignIn: () -> Unit) {
        val currentUser = Firebase.auth.currentUser
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn()
            return
        }
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            _snackbarMessage.tryEmit("Please write a comment before posting.")
            return
        }
        if (trimmed.length > 500) {
            _snackbarMessage.tryEmit("Comments must be 500 characters or fewer.")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCommentTimeMs < 4000L || trimmed.equals(lastCommentText, ignoreCase = true)) {
            _snackbarMessage.tryEmit("Please wait a moment before posting another comment.")
            return
        }
        lastCommentTimeMs = now
        lastCommentText = trimmed

        viewModelScope.launch {
            if (repository != null) {
                val result = repository.postComment(imageId, trimmed)
                result.onSuccess {
                    _snackbarMessage.tryEmit("Comment posted.")
                }.onFailure { err ->
                    _snackbarMessage.tryEmit(err.localizedMessage ?: "Could not post comment.")
                }
            }
        }
    }

    fun deleteComment(comment: ImageComment) {
        val currentUser = Firebase.auth.currentUser
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser) || comment.userId != currentUser?.uid) return
        viewModelScope.launch {
            if (repository != null) {
                repository.deleteComment(comment.id, comment.imageId)
                    .onSuccess { _snackbarMessage.tryEmit("Comment deleted.") }
                    .onFailure { _snackbarMessage.tryEmit("Failed to delete comment.") }
            }
        }
    }

    /**
     * Reporting comments requires a real Google account (`sign_in_provider != 'anonymous'`).
     */
    fun reportComment(comment: ImageComment, reason: String, onRequireSignIn: () -> Unit) {
        val currentUser = Firebase.auth.currentUser
        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser)) {
            onRequireSignIn()
            return
        }
        viewModelScope.launch {
            if (repository != null) {
                repository.reportComment(comment.id, comment.imageId, reason)
                    .onSuccess { _snackbarMessage.tryEmit("Comment reported to moderators.") }
                    .onFailure { err ->
                        _snackbarMessage.tryEmit(err.localizedMessage ?: "Could not submit report.")
                    }
            }
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            localPrefs.setThemePreference(preference)
        }
    }

    fun deleteCurrentAccount(onDeleted: () -> Unit) {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            repository?.deleteUserAccountData(uid)
            runCatching {
                Firebase.auth.currentUser?.delete()
            }
            _snackbarMessage.tryEmit("Your account data has been deleted.")
            onDeleted()
        }
    }

    fun emitMessage(message: String) {
        _snackbarMessage.tryEmit(message)
    }

    fun buildCollections(images: List<InspirationImage>): List<ImageCollectionSummary> {
        return images
            .filter { it.collectionName.isNotBlank() }
            .groupBy { it.collectionName }
            .map { (name, items) ->
                ImageCollectionSummary(
                    name = name,
                    subtitle = "${items.size} curated prompts • ${items.first().categoryName}",
                    coverImageUrl = items.first().imageUrl,
                    itemCount = items.size,
                    images = items
                )
            }
    }

    suspend fun resolveDeepLinkImage(imageId: String, currentImages: List<InspirationImage>): InspirationImage? {
        val cleanId = imageId.trim()
        if (cleanId.isBlank()) return null
        currentImages.firstOrNull { it.id == cleanId }?.let { return it }
        if (repository != null) {
            return repository.fetchImageById(cleanId).getOrNull()
        }
        return null
    }

    companion object {
        fun factory(initialUserId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localPrefs = LocalPreferencesRepository(app)
                val repo = if (GoogleSignInManager.isFirebaseConfigured(app)) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    LumiereRepository(db)
                } else {
                    null
                }
                LumiereViewModel(
                    repository = repo,
                    localPrefs = localPrefs,
                    initialUserId = initialUserId
                )
            }
        }
    }
}
