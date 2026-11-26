package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.AdMobManager
import com.example.auth.GoogleSignInManager
import com.example.model.InspirationImage
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.notifications.PromptLensDeepLinkManager
import com.example.ui.LumiereViewModel
import com.example.ui.components.pressScaleClickable
import com.example.ui.screens.AiToolsScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageDetailScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.IndigoSurfaceTop
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.LumiereTheme
import com.example.ui.theme.PromptAccentGradientBrush
import com.example.ui.theme.PromptCardSurfaceBrush
import com.example.ui.theme.PromptVerticalBackgroundBrush
import com.example.ui.theme.TextLavenderGray
import com.example.ui.theme.TextPureWhite
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PromptLensNavTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    CATEGORIES(R.string.nav_categories, Icons.Filled.GridView, Icons.Outlined.GridView, "nav_tab_categories"),
    SAVED(R.string.nav_saved, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "nav_tab_saved"),
    AI_TOOLS(R.string.nav_ai_tools, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_tab_ai_tools"),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_profile")
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdMobManager.purgeCorruptedWebViewVariationsSeeds(this)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        // Handle deep link intent extras immediately without network calls
        PromptLensDeepLinkManager.handleIncomingIntent(intent)

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

            // Initialize Firebase anonymous session, AdMob, UMP consent, and FCM in the background
            // AFTER the first frame so startup is instant and never blocked.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                // 1. Background FCM channel & topic init
                withContext(Dispatchers.IO) {
                    runCatching { PromptLensDeepLinkManager.initialize(applicationContext) }
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
                    activity = this@MainActivity,
                    viewModel = viewModel,
                    firebaseUser = currentUser,
                    isBangla = isBangla,
                    onSignInRequest = { onComplete ->
                        GoogleSignInManager.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                viewModel.updateCurrentUserId(
                                    runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                                )
                                viewModel.onUserSignedIn()
                                viewModel.emitMessage(context.getString(R.string.signed_in_message))
                                onComplete(true)
                            },
                            onAuthError = { errMessage ->
                                viewModel.emitMessage(errMessage)
                                onComplete(false)
                            },
                            scope = coroutineScope,
                            onAuthCancelled = {
                                onComplete(false)
                            }
                        )
                    },
                    onSignOut = {
                        viewModel.onUserSignedOut()
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
        PromptLensDeepLinkManager.handleIncomingIntent(intent)
    }
}

@Composable
fun PromptLensAppRoot(
    activity: AppCompatActivity,
    viewModel: LumiereViewModel,
    firebaseUser: FirebaseUser?,
    isBangla: Boolean,
    onSignInRequest: (onComplete: (Boolean) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imagesUiState by viewModel.imagesUiState.collectAsStateWithLifecycle()
    val categories by viewModel.categoriesState.collectAsStateWithLifecycle()
    val aiTools by viewModel.aiToolsState.collectAsStateWithLifecycle()
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
    val isInitialCacheLoaded by viewModel.isInitialCacheLoaded.collectAsStateWithLifecycle()
    val pendingDeepLinkImageId by PromptLensDeepLinkManager.pendingImageDeepLink.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(PromptLensNavTab.HOME) }
    var activeDetailImageId by rememberSaveable { mutableStateOf<String?>(null) }
    var isNotificationsScreenOpen by rememberSaveable { mutableStateOf(false) }
    var deepLinkedFallbackImage by remember { mutableStateOf<InspirationImage?>(null) }
    var isStartupLogoShowing by rememberSaveable { mutableStateOf(true) }
    val latestAdSettings by rememberUpdatedState(advertisementSettings)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            coroutineScope {
                val showJob = launch {
                    snackbarHostState.showSnackbar(
                        message = msg,
                        duration = SnackbarDuration.Indefinite
                    )
                }
                delay(2000L)
                snackbarHostState.currentSnackbarData?.dismiss()
                showJob.cancel()
            }
        }
    }

    val canRequestAds by AdMobManager.canRequestAdsFlow.collectAsStateWithLifecycle()

    // Sync rewarded, interstitial, and App Open ad preload/clear state immediately whenever advertisementSettings or UMP consent changes live
    LaunchedEffect(advertisementSettings, canRequestAds) {
        AdMobManager.preloadRewardedAdIfNeeded(context, advertisementSettings)
        AdMobManager.preloadInterstitialIfNeeded(context, advertisementSettings)
        AdMobManager.preloadAppOpenAdIfNeeded(context, advertisementSettings)
    }

    // App start: show the PromptLens logo on the dark background for 2.5 seconds while loading the App Open ad.
    // After 2.5s, if the App Open ad is ready and eligible, show it and then open Home; otherwise go directly to Home.
    LaunchedEffect(Unit) {
        if (isStartupLogoShowing) {
            AdMobManager.preloadAppOpenAdIfNeeded(context, latestAdSettings)
            delay(2500L)
            AdMobManager.showAppOpenAdIfEligible(
                activity = activity,
                settings = latestAdSettings,
                onComplete = {
                    isStartupLogoShowing = false
                }
            )
        }
    }

    // Show App Open Ad when app returns to foreground (ON_START) after initial startup if already loaded & eligible (30-min cooldown, not first install launch)
    DisposableEffect(lifecycleOwner, advertisementSettings, isStartupLogoShowing) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !isStartupLogoShowing) {
                AdMobManager.showAppOpenAdIfEligible(activity, advertisementSettings)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    LaunchedEffect(allImages.isNotEmpty(), notificationPermissionAsked, isStartupLogoShowing) {
        if (!isStartupLogoShowing && allImages.isNotEmpty() && !notificationPermissionAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    // Handle deep links from FCM push notifications or promptlens://image/{imageId}
    LaunchedEffect(pendingDeepLinkImageId, imagesUiState, isInitialCacheLoaded) {
        val targetId = pendingDeepLinkImageId ?: return@LaunchedEffect
        if (!isInitialCacheLoaded && imagesUiState is UiState.Loading) return@LaunchedEffect

        val resolved = viewModel.resolveDeepLinkImage(targetId, allImages)
        if (resolved != null) {
            deepLinkedFallbackImage = resolved
            isNotificationsScreenOpen = false
            activeDetailImageId = resolved.id
            viewModel.onImageDetailOpened(resolved)
            viewModel.observeCommentsForImage(resolved.id)
            PromptLensDeepLinkManager.consumeDeepLink()
        } else if (imagesUiState !is UiState.Loading) {
            Toast.makeText(
                context,
                context.getString(R.string.post_no_longer_available),
                Toast.LENGTH_SHORT
            ).show()
            activeDetailImageId = null
            isNotificationsScreenOpen = false
            currentTab = PromptLensNavTab.HOME
            PromptLensDeepLinkManager.consumeDeepLink()
        }
    }

    val activeDetailImage = remember(activeDetailImageId, allImages, deepLinkedFallbackImage) {
        if (activeDetailImageId == null) {
            null
        } else {
            allImages.firstOrNull { it.id == activeDetailImageId }
                ?: deepLinkedFallbackImage?.takeIf { it.id == activeDetailImageId }
        }
    }

    // Re-attach comments stream after process death or rotation when a detail image is active,
    // and cancel the Firestore listener immediately when leaving the detail screen.
    LaunchedEffect(activeDetailImageId) {
        val id = activeDetailImageId
        if (!id.isNullOrBlank()) {
            viewModel.observeCommentsForImage(id)
        } else {
            viewModel.stopObservingComments()
        }
    }

    // Handle process-death restoration or server-side deletion of the active detail post
    LaunchedEffect(activeDetailImageId, imagesUiState, isInitialCacheLoaded) {
        val id = activeDetailImageId ?: return@LaunchedEffect
        if (!isInitialCacheLoaded && imagesUiState is UiState.Loading) return@LaunchedEffect
        if (allImages.none { it.id == id } && deepLinkedFallbackImage?.id != id) {
            val resolved = viewModel.resolveDeepLinkImage(id, allImages)
            if (resolved != null) {
                deepLinkedFallbackImage = resolved
            } else if (imagesUiState !is UiState.Loading) {
                activeDetailImageId = null
            }
        }
    }

    // System back from any non-Home tab returns to Home first
    BackHandler(
        enabled = activeDetailImage == null && !isNotificationsScreenOpen && currentTab != PromptLensNavTab.HOME
    ) {
        currentTab = PromptLensNavTab.HOME
    }

    // Helper that opens the Google Sign-In sheet directly and executes the pending action if sign-in succeeds
    val requestGoogleSignInAndContinue: ((() -> Unit) -> Unit) = { afterSignInAction ->
        onSignInRequest { success ->
            if (success) {
                afterSignInAction()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PromptVerticalBackgroundBrush)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = IndigoSurfaceTop,
                        contentColor = TextPureWhite,
                        border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.7f)),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = snackbarData.visuals.message,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPureWhite,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                        )
                    }
                }
            },
            bottomBar = {
                if (activeDetailImage == null && !isNotificationsScreenOpen) {
                    PromptLensFloatingBottomNavigationBar(
                        currentTab = currentTab,
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
                    .padding(top = innerPadding.calculateTopPadding())
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
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(50))
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
                            aiTools = aiTools,
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
                                viewModel.stopObservingComments()
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
                    } else if (isNotificationsScreenOpen) {
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
                                    PromptLensDeepLinkManager.dispatchDeepLinkImageId(targetImageId)
                                }
                            },
                            onBack = {
                                isNotificationsScreenOpen = false
                            }
                        )
                    } else {
                        val openDetail: (InspirationImage) -> Unit = { image ->
                            deepLinkedFallbackImage = image
                            activeDetailImageId = image.id
                            viewModel.onImageDetailOpened(image)
                            viewModel.observeCommentsForImage(image.id)
                        }

                        // Smooth screen transitions (fade + slight slide)
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) +
                                    slideInVertically(animationSpec = tween(220)) { fullHeight -> fullHeight / 28 })
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(160)) +
                                            slideOutVertically(animationSpec = tween(160)) { fullHeight -> -fullHeight / 36 }
                                    )
                            },
                            label = "main_tab_animated_content"
                        ) { tab ->
                            when (tab) {
                                PromptLensNavTab.HOME -> {
                                    HomeScreen(
                                        imagesUiState = imagesUiState,
                                        categories = categories,
                                        likedIds = likedIds,
                                        advertisementSettings = advertisementSettings,
                                        hasUnreadNotifications = hasUnreadNotifications,
                                        onOpenNotifications = {
                                            isNotificationsScreenOpen = true
                                        },
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

                                PromptLensNavTab.AI_TOOLS -> {
                                    AiToolsScreen(
                                        aiTools = aiTools
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
                                        },
                                        onOpenNotifications = {
                                            isNotificationsScreenOpen = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2.5-second startup screen showing the PromptLens logo on the dark background
        if (isStartupLogoShowing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PromptVerticalBackgroundBrush)
                    .testTag("startup_logo_screen"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_promptlens_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(116.dp)
                        .clip(RoundedCornerShape(28.dp))
                )
            }
        }
    }
}

/**
 * BOTTOM NAVIGATION (Requirement 2):
 * - Floating rounded bar (28dp radius, 12dp margin from the edges and bottom),
 *   dark indigo surface, soft border and shadow.
 * - 5 tabs: Home, Categories, Saved, AI Tools, Profile.
 * - The selected tab = filled violet pill with icon + label on ONE line; unselected = icon only.
 * - Labels never wrap.
 */
@Composable
private fun PromptLensFloatingBottomNavigationBar(
    currentTab: PromptLensNavTab,
    onTabSelected: (PromptLensNavTab) -> Unit
) {
    val navBarShape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = navBarShape,
                    ambientColor = Color.Black.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(navBarShape)
                .background(IndigoSurfaceTop, navBarShape)
                .border(1.dp, IndigoSoftBorder, navBarShape)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("bottom_navigation_bar"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val density = LocalDensity.current
            val cappedLabelSizeSp = with(density) {
                val effectiveScale = fontScale.coerceAtMost(1.15f)
                (13f / fontScale * effectiveScale).sp
            }

            PromptLensNavTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val label = stringResource(tab.titleRes)

                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .then(
                            if (selected) {
                                Modifier.background(PromptAccentGradientBrush, RoundedCornerShape(26.dp))
                            } else {
                                Modifier
                            }
                        )
                        .pressScaleClickable { onTabSelected(tab) }
                        .animateContentSize(animationSpec = tween(durationMillis = 220))
                        .padding(horizontal = if (selected) 14.dp else 11.dp)
                        .testTag(tab.testTag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (tab == PromptLensNavTab.AI_TOOLS) {
                        AiToolsNavBadgeIcon(selected = selected)
                    } else {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = label,
                            tint = if (selected) Color.White else TextLavenderGray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (selected) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = cappedLabelSizeSp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiToolsNavBadgeIcon(selected: Boolean) {
    val scaleAnim = remember { Animatable(1f) }
    var previousSelected by remember { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        if (selected && !previousSelected) {
            scaleAnim.snapTo(1f)
            scaleAnim.animateTo(
                targetValue = 1.12f,
                animationSpec = tween(durationMillis = 110)
            )
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 130)
            )
        } else if (!selected) {
            scaleAnim.snapTo(1f)
        }
        previousSelected = selected
    }

    val badgeShape = RoundedCornerShape(8.dp)
    val inactiveColor = Color(0xFFA8ACD1)

    Box(
        modifier = Modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(badgeShape)
                .then(
                    if (selected) {
                        Modifier
                            .background(PromptAccentGradientBrush, badgeShape)
                            .border(1.dp, Color.White.copy(alpha = 0.38f), badgeShape)
                    } else {
                        Modifier.border(1.5.dp, inactiveColor, badgeShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 0.3.sp
                ),
                color = if (selected) Color.White else inactiveColor,
                maxLines = 1,
                softWrap = false
            )
        }

        // Tiny 4-point sparkle at the top-right corner when selected
        if (selected) {
            Canvas(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val inner = w * 0.18f
                val path = Path().apply {
                    moveTo(cx, 0f)
                    quadraticTo(cx + inner * 0.4f, cy - inner * 0.4f, w, cy)
                    quadraticTo(cx + inner * 0.4f, cy + inner * 0.4f, cx, h)
                    quadraticTo(cx - inner * 0.4f, cy + inner * 0.4f, 0f, cy)
                    quadraticTo(cx - inner * 0.4f, cy - inner * 0.4f, cx, 0f)
                    close()
                }
                drawPath(path = path, color = Color.White)
            }
        }
    }
}

