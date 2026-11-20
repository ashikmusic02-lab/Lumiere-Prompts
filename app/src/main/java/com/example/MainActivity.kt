package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        OneSignalDeepLinkManager.initialize(applicationContext)
        OneSignalDeepLinkManager.handleIncomingIntent(intent)

        // UMP Consent check at launch before initializing MobileAds
        AdMobManager.requestConsentAndInitializeAds(this)

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val credentialManager = remember { CredentialManager.create(context) }

            val initialUser = remember {
                if (GoogleSignInManager.isFirebaseConfigured(context)) {
                    runCatching { Firebase.auth.currentUser }.getOrNull()
                } else null
            }
            val currentUser by GoogleSignInManager.authStateFlow(context)
                .collectAsState(initial = initialUser)

            LaunchedEffect(Unit) {
                GoogleSignInManager.attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = coroutineScope
                )
            }

            val viewModel: LumiereViewModel = viewModel(
                factory = LumiereViewModel.factory(currentUser?.uid)
            )

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

            LumiereTheme(
                darkTheme = useDarkTheme,
                isBangla = languageTag.startsWith("bn", ignoreCase = true)
            ) {
                PromptLensAppRoot(
                    viewModel = viewModel,
                    firebaseUser = currentUser,
                    onSignInRequest = { onComplete ->
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
    val isLoadingDetailPrompt by viewModel.isLoadingDetailPrompt.collectAsStateWithLifecycle()
    val activeComments by viewModel.activeComments.collectAsStateWithLifecycle()
    val mergedNotifications by viewModel.mergedNotificationsState.collectAsStateWithLifecycle()
    val hasUnreadNotifications by viewModel.hasUnreadNotifications.collectAsStateWithLifecycle()
    val advertisementSettings by viewModel.advertisementSettings.collectAsStateWithLifecycle()
    val appConfigSettings by viewModel.appConfigSettings.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRealUser by viewModel.isRealUserFlow.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserIdFlow.collectAsStateWithLifecycle()
    val notificationPermissionAsked by viewModel.notificationPermissionAsked.collectAsStateWithLifecycle()
    val isAdPrivacyRequired by AdMobManager.isPrivacyOptionsRequiredFlow.collectAsStateWithLifecycle()
    val pendingDeepLinkImageId by OneSignalDeepLinkManager.pendingImageDeepLink.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(PromptLensNavTab.HOME) }
    var activeDetailImageId by rememberSaveable { mutableStateOf<String?>(null) }
    var deepLinkedFallbackImage by remember { mutableStateOf<InspirationImage?>(null) }
    var showSignInDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Preload interstitial ad when ad settings become available
    LaunchedEffect(advertisementSettings) {
        if (advertisementSettings.allAdsEnabled && advertisementSettings.interstitialAdsEnabled) {
            AdMobManager.preloadInterstitialIfNeeded(context, advertisementSettings)
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

    // Google Sign-In Dialog for guests attempting gated actions (Like, Comment, Report, or requireLoginForPrompts)
    if (showSignInDialog) {
        AlertDialog(
            onDismissRequest = { showSignInDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.sign_in_required_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.sign_in_required_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignInDialog = false
                        onSignInRequest {}
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("dialog_google_sign_in_button")
                ) {
                    Text(text = stringResource(R.string.sign_in_with_google))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignInDialog = false },
                    modifier = Modifier.testTag("dialog_cancel_sign_in_button")
                ) {
                    Text(text = stringResource(R.string.cancel_action))
                }
            }
        )
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
                    onTabSelected = { currentTab = it }
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
                        isLoadingPrompt = isLoadingDetailPrompt,
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
                                onRequireSignIn = { showSignInDialog = true }
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
                                onRequireSignIn = { showSignInDialog = true }
                            )
                        },
                        onDeleteComment = { comment ->
                            viewModel.deleteComment(comment)
                        },
                        onReportComment = { comment, reason ->
                            viewModel.reportComment(
                                comment = comment,
                                reason = reason,
                                onRequireSignIn = { showSignInDialog = true }
                            )
                        },
                        onBlockUser = { blockedUid ->
                            viewModel.blockUserLocally(blockedUid)
                        },
                        onRequireSignIn = {
                            showSignInDialog = true
                        },
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
                                    isRefreshing = isRefreshing,
                                    onRefresh = { viewModel.onPullToRefresh() },
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
                                    onGoToHome = { currentTab = PromptLensNavTab.HOME }
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
    onTabSelected: (PromptLensNavTab) -> Unit
) {
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
                        style = MaterialTheme.typography.labelSmall
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
