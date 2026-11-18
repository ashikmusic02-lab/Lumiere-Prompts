package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.AdMobManager
import com.example.auth.GoogleSignInManager
import com.example.auth.configureAppCheck
import com.example.model.InspirationImage
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.notifications.OneSignalDeepLinkManager
import com.example.ui.LumiereViewModel
import com.example.ui.screens.AuthGateScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageDetailScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.LumiereTheme
import com.example.ui.theme.SoftChampagneGold
import kotlinx.coroutines.launch

enum class LumiereDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    EXPLORE("Explore", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_explore"),
    FAVORITES("Favorites", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_favorites"),
    NOTIFICATIONS("Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications, "nav_notifications"),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase App Check (Debug provider in debug builds, Play Integrity in release builds)
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        // Initialize AdMob & OneSignal Push/DeepLink Router
        AdMobManager.initialize(this)
        OneSignalDeepLinkManager.initialize(this)
        OneSignalDeepLinkManager.handleIncomingIntent(intent)

        setContent {
            LumiereAppRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        OneSignalDeepLinkManager.handleIncomingIntent(intent)
    }
}

@Composable
fun LumiereAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by GoogleSignInManager.authStateFlow(context)
        .collectAsStateWithLifecycle(initialValue = null)

    var guestBrowseAccepted by rememberSaveable { mutableStateOf(true) }

    // Attempt silent Google auto-sign-in on cold start when Firebase is configured
    LaunchedEffect(Unit) {
        val credentialManager = CredentialManager.create(context)
        GoogleSignInManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { },
            onUnauthenticated = { },
            scope = scope
        )
    }

    val activeUid = currentUser?.uid
    val viewModel: LumiereViewModel = viewModel(
        factory = LumiereViewModel.factory(initialUserId = activeUid)
    )

    LaunchedEffect(activeUid) {
        viewModel.updateCurrentUserId(activeUid)
    }

    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val isDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    LumiereTheme(darkTheme = isDark) {
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(viewModel) {
            viewModel.snackbarMessage.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        if (!GoogleSignInManager.isRealAuthenticatedUser(currentUser) && !guestBrowseAccepted) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    AuthGateScreen(
                        onAuthSuccess = { guestBrowseAccepted = true },
                        onContinueToGalleryPreview = { guestBrowseAccepted = true },
                        onFeedbackMessage = { viewModel.emitMessage(it) }
                    )
                }
            }
        } else {
            LumiereMainScaffold(
                viewModel = viewModel,
                currentUser = currentUser,
                snackbarHostState = snackbarHostState,
                onOpenSignInScreen = { guestBrowseAccepted = false }
            )
        }
    }
}

@Composable
private fun LumiereMainScaffold(
    viewModel: LumiereViewModel,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    snackbarHostState: SnackbarHostState,
    onOpenSignInScreen: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reactiveUserId by viewModel.currentUserIdFlow.collectAsStateWithLifecycle()
    val imagesUiState by viewModel.imagesUiState.collectAsStateWithLifecycle()
    val homeFeaturedImages by viewModel.homeFeaturedImages.collectAsStateWithLifecycle()
    val homeTrendingImages by viewModel.homeTrendingImages.collectAsStateWithLifecycle()
    val homeRecommendedImages by viewModel.homeRecommendedImages.collectAsStateWithLifecycle()
    val hasMoreImages by viewModel.hasMoreImages.collectAsStateWithLifecycle()
    val isLoadingMoreImages by viewModel.isLoadingMoreImages.collectAsStateWithLifecycle()
    val relatedImages by viewModel.relatedImages.collectAsStateWithLifecycle()
    val hasMoreRelatedImages by viewModel.hasMoreRelatedImages.collectAsStateWithLifecycle()
    val unlockedPromptsMap by viewModel.unlockedPromptsMap.collectAsStateWithLifecycle()
    val categories by viewModel.categoriesState.collectAsStateWithLifecycle()
    val recentlyViewedIds by viewModel.recentlyViewedIds.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedImageIds.collectAsStateWithLifecycle()
    val favoritedIds by viewModel.favoritedImageIds.collectAsStateWithLifecycle()
    val unlockedIds by viewModel.unlockedImageIds.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val appConfigSettings by viewModel.appConfigSettings.collectAsStateWithLifecycle()
    val advertisementSettings by viewModel.advertisementSettings.collectAsStateWithLifecycle()
    val activeComments by viewModel.activeComments.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val isOneSignalConfigured by OneSignalDeepLinkManager.isOneSignalConfigured.collectAsStateWithLifecycle()
    val pendingDeepLinkImageId by OneSignalDeepLinkManager.pendingImageDeepLink.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(LumiereDestination.HOME) }
    var selectedImageForDetail by remember { mutableStateOf<InspirationImage?>(null) }
    var exploreInitialCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailableDeepLinkImageId by remember { mutableStateOf<String?>(null) }

    val allImages = when (val state = imagesUiState) {
        is UiState.Success -> state.data
        else -> emptyList()
    }

    val collections = remember(allImages) {
        viewModel.buildCollections(allImages)
    }

    // Handle incoming OneSignal / URI Deep Link -> Verify image exists -> Navigate or Show Friendly Fallback
    LaunchedEffect(pendingDeepLinkImageId, allImages) {
        val targetId = pendingDeepLinkImageId ?: return@LaunchedEffect
        val resolved = viewModel.resolveDeepLinkImage(targetId, allImages)
        OneSignalDeepLinkManager.consumeDeepLink()
        if (resolved != null) {
            selectedImageForDetail = resolved
            viewModel.onImageDetailOpened(resolved)
            viewModel.observeCommentsForImage(resolved.id)
        } else {
            unavailableDeepLinkImageId = targetId
        }
    }

    val openDetail: (InspirationImage) -> Unit = { image ->
        selectedImageForDetail = image
        viewModel.onImageDetailOpened(image)
        viewModel.observeCommentsForImage(image.id)
        AdMobManager.onDetailScreenOpened(context, advertisementSettings)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectedImageForDetail == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    LumiereDestination.entries.forEach { dest ->
                        val selected = currentDestination == dest
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentDestination = dest },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ChampagneGold,
                                selectedTextColor = SoftChampagneGold,
                                indicatorColor = ChampagneGold.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val detailImage = selectedImageForDetail
            if (detailImage != null) {
                val liveImage = allImages.firstOrNull { it.id == detailImage.id } ?: detailImage
                ImageDetailScreen(
                    image = liveImage,
                    relatedImages = relatedImages.ifEmpty {
                        allImages.filter { it.id != liveImage.id && it.categoryId == liveImage.categoryId }
                    },
                    hasMoreRelatedImages = hasMoreRelatedImages,
                    onLoadMoreRelated = {
                        viewModel.loadRelatedImagesFor(liveImage, loadMore = true)
                    },
                    comments = activeComments,
                    currentUserId = reactiveUserId,
                    unlockedPromptText = unlockedPromptsMap[liveImage.id],
                    isLiked = likedIds.contains(liveImage.id),
                    isFavorited = favoritedIds.contains(liveImage.id),
                    isAlreadyUnlockedByUser = unlockedIds.contains(liveImage.id),
                    appConfigSettings = appConfigSettings,
                    advertisementSettings = advertisementSettings,
                    onBack = { selectedImageForDetail = null },
                    onSelectRelatedImage = { related -> openDetail(related) },
                    onToggleLike = {
                        viewModel.toggleLike(
                            image = liveImage,
                            onRequireSignIn = {
                                viewModel.emitMessage("Please sign in with Google to like images.")
                                selectedImageForDetail = null
                                currentDestination = LumiereDestination.PROFILE
                            }
                        )
                    },
                    onToggleFavorite = {
                        viewModel.toggleFavorite(
                            image = liveImage,
                            onRequireSignIn = {
                                viewModel.emitMessage("Please sign in with Google to sync favorites.")
                                selectedImageForDetail = null
                                currentDestination = LumiereDestination.PROFILE
                            }
                        )
                    },
                    onVerifiedRewardUnlock = {
                        viewModel.onVerifiedRewardPromptUnlock(liveImage)
                    },
                    onRequestLoadUnlockedPrompt = {
                        viewModel.ensurePromptLoadedIfUnlocked(liveImage.id)
                    },
                    onPromptCopied = {
                        viewModel.emitMessage("Complete prompt copied to clipboard!")
                    },
                    onPromptShared = {
                        viewModel.onPromptShared(liveImage)
                    },
                    onPostComment = { text ->
                        viewModel.postComment(
                            imageId = liveImage.id,
                            rawText = text,
                            onRequireSignIn = {
                                viewModel.emitMessage("Please sign in with Google to post comments.")
                                selectedImageForDetail = null
                                currentDestination = LumiereDestination.PROFILE
                            }
                        )
                    },
                    onDeleteComment = { comment ->
                        viewModel.deleteComment(comment)
                    },
                    onReportComment = { comment, reason ->
                        viewModel.reportComment(
                            comment = comment,
                            reason = reason,
                            onRequireSignIn = {
                                viewModel.emitMessage("Please sign in with Google to report comments.")
                                selectedImageForDetail = null
                                currentDestination = LumiereDestination.PROFILE
                            }
                        )
                    },
                    onRequireSignIn = {
                        viewModel.emitMessage("Please sign in with Google to continue.")
                        selectedImageForDetail = null
                        currentDestination = LumiereDestination.PROFILE
                    }
                )
            } else {
                when (currentDestination) {
                    LumiereDestination.HOME -> {
                        HomeScreen(
                            imagesUiState = imagesUiState,
                            featuredImagesOverride = homeFeaturedImages,
                            trendingImagesOverride = homeTrendingImages,
                            recommendedImagesOverride = homeRecommendedImages,
                            hasMoreImages = hasMoreImages,
                            isLoadingMoreImages = isLoadingMoreImages,
                            onLoadMoreImages = { viewModel.loadNextImagesPage() },
                            onRefreshCatalog = { viewModel.refreshCatalog() },
                            categories = categories,
                            collections = collections,
                            recentlyViewedIds = recentlyViewedIds,
                            likedIds = likedIds,
                            favoritedIds = favoritedIds,
                            unlockedIds = unlockedIds,
                            appConfigSettings = appConfigSettings,
                            advertisementSettings = advertisementSettings,
                            onOpenImageDetail = openDetail,
                            onSelectCategory = { catName ->
                                exploreInitialCategory = catName
                                currentDestination = LumiereDestination.EXPLORE
                            },
                            onOpenSearch = {
                                currentDestination = LumiereDestination.EXPLORE
                            },
                            onToggleLike = { img ->
                                viewModel.toggleLike(
                                    image = img,
                                    onRequireSignIn = {
                                        viewModel.emitMessage("Sign in with Google on the Profile tab to like images.")
                                        currentDestination = LumiereDestination.PROFILE
                                    }
                                )
                            },
                            onToggleFavorite = { img ->
                                viewModel.toggleFavorite(
                                    image = img,
                                    onRequireSignIn = {
                                        viewModel.emitMessage("Sign in with Google on the Profile tab to sync favorites.")
                                        currentDestination = LumiereDestination.PROFILE
                                    }
                                )
                            }
                        )
                    }
                    LumiereDestination.EXPLORE -> {
                        ExploreScreen(
                            allImages = allImages,
                            categories = categories,
                            initialSelectedCategory = exploreInitialCategory,
                            hasMoreImages = hasMoreImages,
                            isLoadingMoreImages = isLoadingMoreImages,
                            onLoadMoreImages = { viewModel.loadNextImagesPage() },
                            likedIds = likedIds,
                            favoritedIds = favoritedIds,
                            unlockedIds = unlockedIds,
                            onClearInitialCategory = { exploreInitialCategory = null },
                            onOpenImageDetail = openDetail,
                            onToggleLike = { img ->
                                viewModel.toggleLike(
                                    image = img,
                                    onRequireSignIn = {
                                        viewModel.emitMessage("Sign in with Google on the Profile tab to like images.")
                                        currentDestination = LumiereDestination.PROFILE
                                    }
                                )
                            },
                            onToggleFavorite = { img ->
                                viewModel.toggleFavorite(
                                    image = img,
                                    onRequireSignIn = {
                                        viewModel.emitMessage("Sign in with Google on the Profile tab to sync favorites.")
                                        currentDestination = LumiereDestination.PROFILE
                                    }
                                )
                            }
                        )
                    }
                    LumiereDestination.FAVORITES -> {
                        FavoritesScreen(
                            allImages = allImages,
                            unlockedPromptsMap = unlockedPromptsMap,
                            likedIds = likedIds,
                            favoritedIds = favoritedIds,
                            unlockedIds = unlockedIds,
                            onOpenImageDetail = openDetail,
                            onToggleLike = { img ->
                                viewModel.toggleLike(
                                    image = img,
                                    onRequireSignIn = {
                                        viewModel.emitMessage("Sign in with Google on the Profile tab to like images.")
                                        currentDestination = LumiereDestination.PROFILE
                                    }
                                )
                            },
                            onToggleFavorite = { img ->
                                viewModel.toggleFavorite(
                                    image = img,
                                    onRequireSignIn = {
                                        viewModel.emitMessage("Sign in with Google on the Profile tab to sync favorites.")
                                        currentDestination = LumiereDestination.PROFILE
                                    }
                                )
                            },
                            onPromptCopied = {
                                viewModel.emitMessage("Prompt copied to clipboard!")
                            },
                            onExploreClick = {
                                currentDestination = LumiereDestination.EXPLORE
                            }
                        )
                    }
                    LumiereDestination.NOTIFICATIONS -> {
                        NotificationsScreen(
                            notifications = notifications,
                            isOneSignalConfigured = isOneSignalConfigured,
                            onNotificationTapped = { targetImageId ->
                                scope.launch {
                                    val resolved = viewModel.resolveDeepLinkImage(targetImageId, allImages)
                                    if (resolved != null) {
                                        openDetail(resolved)
                                    } else {
                                        unavailableDeepLinkImageId = targetImageId
                                    }
                                }
                            },
                            onFeedbackMessage = { viewModel.emitMessage(it) }
                        )
                    }
                    LumiereDestination.PROFILE -> {
                        ProfileScreen(
                            currentUser = currentUser,
                            likedCount = likedIds.size,
                            favoritedCount = favoritedIds.size,
                            unlockedCount = unlockedIds.size,
                            themePreference = themePreference,
                            onChangeTheme = { viewModel.setThemePreference(it) },
                            onSignInSuccess = { },
                            onSignOut = {
                                viewModel.emitMessage("Signed out.")
                            },
                            onDeleteAccount = {
                                viewModel.deleteCurrentAccount {
                                    onOpenSignInScreen()
                                }
                            },
                            onFeedbackMessage = { viewModel.emitMessage(it) }
                        )
                    }
                }
            }
        }
    }

    // Friendly Fallback Dialog when a Notification or Deep Link points to an invalid or deleted Image ID
    val missingId = unavailableDeepLinkImageId
    if (missingId != null) {
        AlertDialog(
            onDismissRequest = { unavailableDeepLinkImageId = null },
            title = { Text("Inspiration Unavailable") },
            text = {
                Text(
                    "The photography inspiration linked in this notification (ID: $missingId) is no longer available or has been archived by the curator. Explore other trending prompts in the gallery!"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        unavailableDeepLinkImageId = null
                        selectedImageForDetail = null
                        currentDestination = LumiereDestination.HOME
                    }
                ) {
                    Text("Explore Featured Prompts")
                }
            }
        )
    }
}
