package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.AdMobManager
import com.example.auth.GoogleSignInManager
import com.example.model.InspirationImage
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.notifications.OneSignalDeepLinkManager
import com.example.ui.LumiereViewModel
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageDetailScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.theme.LumiereTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PromptLensNavTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    CATEGORIES(R.string.nav_categories, Icons.Filled.Category, Icons.Outlined.Category, "nav_tab_categories"),
    SAVED(R.string.nav_saved, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_tab_saved"),
    NOTIFICATIONS(R.string.nav_notifications, Icons.Filled.Notifications, Icons.Outlined.Notifications, "nav_tab_notifications"),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_profile")
}

class MainActivity : AppCompatActivity() {

    @Volatile
    private var isCacheReadyForSplashDismiss = false
    private var splashStartTimeMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        splashStartTimeMs = System.currentTimeMillis()
        AdMobManager.purgeCorruptedWebViewVariationsSeeds(this)
        enableEdgeToEdge()

        // Hold native Android SplashScreen only until cached data is read from local storage
        // (with a strict <450ms cap so it never waits for network, auth, ads, UMP, or OneSignal).
        val contentView: View = findViewById(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val elapsed = System.currentTimeMillis() - splashStartTimeMs
                    return if (isCacheReadyForSplashDismiss || elapsed >= 450L) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        // Handle deep link intent extras immediately without network calls
        OneSignalDeepLinkManager.handleIncomingIntent(intent)

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val credentialManager = remember { CredentialManager.create(context) }

            // Silently restore any existing Firebase Auth user (Google or anonymous) without showing any popup
            val initialUser = remember {
                if (GoogleSignInManager.isFirebaseConfigured(context)) {
                    runCatching { Firebase.auth.currentUser }.getOrNull()
                } else null
            }
            val currentUser by GoogleSignInManager.authStateFlow(context)
                .collectAsState(initial = initialUser)

            val viewModel: LumiereViewModel = viewModel(
                factory = LumiereViewModel.factory(currentUser?.uid)
            )

            val isInitialCacheLoaded by viewModel.isInitialCacheLoaded.collectAsStateWithLifecycle()
            LaunchedEffect(isInitialCacheLoaded) {
                if (isInitialCacheLoaded) {
                    isCacheReadyForSplashDismiss = true
                }
            }

            // Initialize Firebase anonymous session, AdMob, UMP consent, and OneSignal in the background
            // AFTER the first frame so startup is instant and never blocked.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                // 1. Background OneSignal init
                withContext(Dispatchers.IO) {
                    runCatching { OneSignalDeepLinkManager.initialize(applicationContext) }
                }
                // 2. Background UMP & AdMob init
                runCatching { AdMobManager.requestConsentAndInitializeAds(this@MainActivity) }
                // 3. Silently ensure anonymous guest session if no user is currently signed in (NEVER shows Google popup)
                val user = GoogleSignInManager.ensureAnonymousSignInIfNeeded(context)
                if (user != null) {
                    viewModel.updateCurrentUserId(user.uid)
                }
            }

            LaunchedEffect(currentUser?.uid) {
                viewModel.updateCurrentUserId(currentUser?.uid)
            }

            val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
            val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
            val useDarkTheme = when (themePref) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            val isBangla = languageTag.startsWith("bn", ignoreCase = true)

            LumiereTheme(
                darkTheme = useDarkTheme,
                isBangla = isBangla
            ) {
                PromptLensAppRoot(
                    viewModel = viewModel,
                    firebaseUser = currentUser,
                    isBangla = isBangla,
                    onSignInRequest = { onComplete ->
                        // Directly invoke Google Sign-In sheet when explicitly requested by user
                        GoogleSignInManager.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                viewModel.updateCurrentUserId(
                                    runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                                )
                                viewModel.emitMessage(context.getString(R.string.signed_in_message))
                                onComplete(true)
                            },
                            onAuthError = { errMessage ->
                                viewModel.emitMessage(errMessage)
                                onComplete(false)
                            },
                            scope = coroutineScope,
                            onAuthCancelled = {
                                // User cancelled: return silently with no error message
                                onComplete(false)
                            }
                        )
                    },
                    onSignOut = {
                        GoogleSignInManager.signOut(
                            context = context,
                            credentialManager = credentialManager,
                            scope = coroutineScope,
                            onSignOutComplete = {
                                viewModel.updateCurrentUserId(
                                    runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                                )
                                viewModel.emitMessage(context.getString(R.string.signed_out_message))
                            }
                        )
                    },
                    onAccountDeleted = {
                        coroutineScope.launch {
                            val guestUser = GoogleSignInManager.ensureAnonymousSignInIfNeeded(context)
                            viewModel.updateCurrentUserId(guestUser?.uid)
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        OneSignalDeepLinkManager.handleIncomingIntent(intent)
    }
}

@Composable
fun PromptLensAppRoot(
    viewModel: LumiereViewModel,
    firebaseUser: FirebaseUser?,
    isBangla: Boolean,
    onSignInRequest: (onComplete: (Boolean) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    val imagesUiState by viewModel.imagesUiState.collectAsStateWithLifecycle()
    val categories by viewModel.categoriesState.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedImageIds.collectAsStateWithLifecycle()
    val favoritedIds by viewModel.favoritedImageIds.collectAsStateWithLifecycle()
    val activeDetailPromptText by viewModel.activeDetailPromptText.collectAsStateWithLifecycle()
    val isPromptComingSoon by viewModel.isPromptComingSoon.collectAsStateWithLifecycle()
    val activeComments by viewModel.activeComments.collectAsStateWithLifecycle()
    val mergedNotifications by viewModel.mergedNotificationsState.collectAsStateWithLifecycle()
    val hasUnreadNotifications by viewModel.hasUnreadNotifications.collectAsStateWithLifecycle()
    val advertisementSettings by viewModel.advertisementSettings.collectAsStateWithLifecycle()
    val appConfigSettings by viewModel.appConfigSettings.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isRealUser by viewModel.isRealUserFlow.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserIdFlow.collectAsStateWithLifecycle()
    val notificationPermissionAsked by viewModel.notificationPermissionAsked.collectAsStateWithLifecycle()
    val isAdPrivacyRequired by AdMobManager.isPrivacyOptionsRequiredFlow.collectAsStateWithLifecycle()
    val pendingDeepLinkImageId by OneSignalDeepLinkManager.pendingImageDeepLink.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(PromptLensNavTab.HOME) }
    var activeDetailImageId by rememberSaveable { mutableStateOf<String?>(null) }
    var deepLinkedFallbackImage by remember { mutableStateOf<InspirationImage?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Preload rewarded and interstitial ads in background when ad settings become available
    LaunchedEffect(advertisementSettings) {
        if (advertisementSettings.allAdsEnabled) {
            if (advertisementSettings.rewardedAdsEnabled) {
                AdMobManager.preloadRewardedAdIfNeeded(context, advertisementSettings)
            }
            if (advertisementSettings.interstitialAdsEnabled) {
                AdMobManager.preloadInterstitialIfNeeded(context, advertisementSettings)
            }
        }
    }

    val allImages = remember(imagesUiState) {
        when (val state = imagesUiState) {
            is UiState.Success -> state.data
            else -> emptyList()
        }
    }

    // Polite notification permission request after first content load on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.markNotificationPermissionPromptShown()
    }

    LaunchedEffect(allImages.isNotEmpty(), notificationPermissionAsked) {
        if (allImages.isNotEmpty() && !notificationPermissionAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.markNotificationPermissionPromptShown()
            if (!alreadyGranted) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Handle deep links from OneSignal push notifications or promptlens://image/{imageId}
    LaunchedEffect(pendingDeepLinkImageId, imagesUiState) {
        val targetId = pendingDeepLinkImageId ?: return@LaunchedEffect
        if (imagesUiState is UiState.Loading) return@LaunchedEffect

        val resolved = viewModel.resolveDeepLinkImage(targetId, allImages)
        if (resolved != null) {
            deepLinkedFallbackImage = resolved
            activeDetailImageId = resolved.id
            viewModel.onImageDetailOpened(resolved)
            viewModel.observeCommentsForImage(resolved.id)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.post_no_longer_available),
                Toast.LENGTH_SHORT
            ).show()
            activeDetailImageId = null
            currentTab = PromptLensNavTab.HOME
        }
        OneSignalDeepLinkManager.consumeDeepLink()
    }

    val activeDetailImage = remember(activeDetailImageId, allImages, deepLinkedFallbackImage) {
        if (activeDetailImageId == null) {
            null
        } else {
            allImages.firstOrNull { it.id == activeDetailImageId }
                ?: deepLinkedFallbackImage?.takeIf { it.id == activeDetailImageId }
        }
    }

    // Helper that opens the Google Sign-In sheet directly and executes the pending action if sign-in succeeds
    val requestGoogleSignInAndContinue: ((() -> Unit) -> Unit) = { afterSignInAction ->
        onSignInRequest { success ->
            if (success) {
                afterSignInAction()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (activeDetailImage == null) {
                PromptLensBottomNavigationBar(
                    currentTab = currentTab,
                    hasUnreadNotifications = hasUnreadNotifications,
                    isBangla = isBangla,
                    onTabSelected = { selectedTab ->
                        currentTab = selectedTab
                        if (selectedTab == PromptLensNavTab.HOME) {
                            viewModel.onHomeTabSelected()
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Slim Offline Banner at the top of the screen when offline
            AnimatedVisibility(
                visible = !isOnline,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("offline_top_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.offline_banner_message),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val detailImg = activeDetailImage
                if (detailImg != null) {
                    ImageDetailScreen(
                        image = detailImg,
                        isLiked = likedIds.contains(detailImg.id),
                        isFavorited = favoritedIds.contains(detailImg.id),
                        isOnline = isOnline,
                        isRealUser = isRealUser,
                        activePromptText = activeDetailPromptText,
                        isPromptComingSoon = isPromptComingSoon,
                        comments = activeComments,
                        currentUserId = currentUserId,
                        advertisementSettings = advertisementSettings,
                        appConfigSettings = appConfigSettings,
                        onBack = {
                            viewModel.clearActiveDetailPrompt()
                            activeDetailImageId = null
                        },
                        onToggleLike = {
                            viewModel.toggleLike(
                                image = detailImg,
                                onRequireSignIn = requestGoogleSignInAndContinue
                            )
                        },
                        onToggleFavorite = {
                            viewModel.toggleFavorite(detailImg)
                        },
                        onUnlockAndLoadPrompt = { adNetwork ->
                            viewModel.unlockAndLoadPromptForCurrentVisit(detailImg, adNetwork)
                        },
                        onClearPromptMemory = {
                            viewModel.clearActiveDetailPrompt()
                        },
                        onPostShared = {
                            viewModel.onPostShared(detailImg)
                        },
                        onPostComment = { text ->
                            viewModel.postComment(
                                imageId = detailImg.id,
                                rawText = text,
                                onRequireSignIn = requestGoogleSignInAndContinue
                            )
                        },
                        onDeleteComment = { comment ->
                            viewModel.deleteComment(comment)
                        },
                        onReportComment = { comment, reason ->
                            viewModel.reportComment(
                                comment = comment,
                                reason = reason,
                                onRequireSignIn = requestGoogleSignInAndContinue
                            )
                        },
                        onBlockUser = { blockedUid ->
                            viewModel.blockUserLocally(blockedUid)
                        },
                        onRequireSignIn = requestGoogleSignInAndContinue,
                        onEmitMessage = { msg ->
                            viewModel.emitMessage(msg)
                        }
                    )
                } else {
                    val openDetail: (InspirationImage) -> Unit = { image ->
                        deepLinkedFallbackImage = image
                        activeDetailImageId = image.id
                        viewModel.onImageDetailOpened(image)
                        viewModel.observeCommentsForImage(image.id)
                    }

                    Crossfade(
                        targetState = currentTab,
                        label = "main_tab_crossfade"
                    ) { tab ->
                        when (tab) {
                            PromptLensNavTab.HOME -> {
                                HomeScreen(
                                    imagesUiState = imagesUiState,
                                    categories = categories,
                                    likedIds = likedIds,
                                    advertisementSettings = advertisementSettings,
                                    onRetrySync = { viewModel.onHomeTabSelected() },
                                    onImageClick = openDetail
                                )
                            }

                            PromptLensNavTab.CATEGORIES -> {
                                CategoriesScreen(
                                    allImages = allImages,
                                    categories = categories,
                                    likedIds = likedIds,
                                    onImageClick = openDetail
                                )
                            }

                            PromptLensNavTab.SAVED -> {
                                SavedScreen(
                                    allImages = allImages,
                                    favoritedIds = favoritedIds,
                                    likedIds = likedIds,
                                    onImageClick = openDetail,
                                    onGoToHome = {
                                        currentTab = PromptLensNavTab.HOME
                                        viewModel.onHomeTabSelected()
                                    }
                                )
                            }

                            PromptLensNavTab.NOTIFICATIONS -> {
                                NotificationsScreen(
                                    mergedNotifications = mergedNotifications,
                                    notificationPermissionAsked = notificationPermissionAsked,
                                    onMarkNotificationPermissionAsked = {
                                        viewModel.markNotificationPermissionPromptShown()
                                    },
                                    onNotificationsTabOpened = {
                                        viewModel.markNotificationsTabOpened()
                                    },
                                    onOpenPostFromNotification = { targetImageId ->
                                        if (targetImageId.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.post_no_longer_available),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            OneSignalDeepLinkManager.dispatchDeepLinkImageId(targetImageId)
                                        }
                                    }
                                )
                            }

                            PromptLensNavTab.PROFILE -> {
                                ProfileScreen(
                                    firebaseUser = firebaseUser,
                                    themePreference = themePreference,
                                    languageTag = languageTag,
                                    isAdPrivacyOptionsRequired = isAdPrivacyRequired,
                                    appConfigSettings = appConfigSettings,
                                    onThemeChange = { viewModel.setThemePreference(it) },
                                    onLanguageChange = { viewModel.setLanguage(it) },
                                    onClearImageCache = { viewModel.clearImageCache() },
                                    onSignInClick = { onSignInRequest {} },
                                    onSignOutClick = onSignOut,
                                    onDeleteAccountClick = {
                                        viewModel.deleteCurrentAccount(
                                            onSuccess = onAccountDeleted,
                                            onReauthRequired = {
                                                onSignInRequest { success ->
                                                    if (success) {
                                                        viewModel.deleteCurrentAccount(
                                                            onSuccess = onAccountDeleted,
                                                            onReauthRequired = {}
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptLensBottomNavigationBar(
    currentTab: PromptLensNavTab,
    hasUnreadNotifications: Boolean,
    isBangla: Boolean,
    onTabSelected: (PromptLensNavTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_navigation_bar")
        ) {
            PromptLensNavTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val label = stringResource(tab.titleRes)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    alwaysShowLabel = true,
                    icon = {
                        if (tab == PromptLensNavTab.NOTIFICATIONS && hasUnreadNotifications) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = label
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = label
                            )
                        }
                    },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isBangla) 9.5.sp else 9.5.sp,
                                lineHeight = 11.sp,
                                letterSpacing = (-0.2).sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag(tab.testTag)
                )
            }
        }
    }
}
