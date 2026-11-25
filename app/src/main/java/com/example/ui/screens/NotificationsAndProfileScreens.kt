package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.ads.AdMobManager
import com.example.model.AppConfigSettings
import com.example.model.AppLinks
import com.example.model.MergedNotificationItem
import com.example.model.ThemePreference
import com.example.ui.components.LumiereExternalImage
import com.example.ui.components.openInAppCustomTab
import com.example.ui.components.pressScaleClickable
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.BadgeAmberOrange
import com.example.ui.theme.BadgeElectricBlue
import com.example.ui.theme.BadgeEmeraldGreen
import com.example.ui.theme.BadgeShieldGreen
import com.example.ui.theme.BadgeSkyBlue
import com.example.ui.theme.BadgeTealCyan
import com.example.ui.theme.BadgeVioletPurple
import com.example.ui.theme.IndigoInnerBox
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.IndigoSurfaceTop
import com.example.ui.theme.PromptAccentGradientBrush
import com.example.ui.theme.PromptCardSurfaceBrush
import com.example.ui.theme.TextLavenderGray
import com.example.ui.theme.TextLavenderMuted
import com.example.ui.theme.TextPureWhite
import com.google.firebase.auth.FirebaseUser
import kotlin.math.max

/**
 * NOTIFICATIONS SCREEN (Requirement 8):
 * - Opened from the Home top-right bell and from Profile.
 * - Slim floating top bar with a 40dp round back button and "Notifications" title.
 * - 28dp rounded cards: small rounded post thumbnail on the left, title, short body and time on the right;
 *   comment replies show the user's comment and the admin reply in a slightly highlighted rounded block.
 * - Tapping opens the post inside the app.
 */
@Composable
fun NotificationsScreen(
    mergedNotifications: List<MergedNotificationItem>,
    notificationPermissionAsked: Boolean,
    onMarkNotificationPermissionAsked: () -> Unit,
    onNotificationsTabOpened: () -> Unit,
    onOpenPostFromNotification: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onMarkNotificationPermissionAsked()
    }

    LaunchedEffect(Unit) {
        onNotificationsTabOpened()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionAsked) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                onMarkNotificationPermissionAsked()
                runCatching {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("notifications_screen_container")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Slim floating top bar with round 40dp back button + Notifications title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PromptCardSurfaceBrush)
                .border(1.dp, IndigoSoftBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(IndigoInnerBox)
                            .border(1.dp, IndigoSoftBorder, CircleShape)
                            .pressScaleClickable(onClick = onBack)
                            .testTag("notifications_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                            tint = TextPureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.notifications_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    ),
                    color = TextPureWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (mergedNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(PromptCardSurfaceBrush)
                        .border(1.dp, IndigoSoftBorder, RoundedCornerShape(28.dp))
                        .padding(horizontal = 24.dp, vertical = 44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AccentViolet.copy(alpha = 0.16f))
                                .border(1.dp, IndigoSoftBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = TextLavenderGray,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.empty_notifications),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextLavenderGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("notifications_list")
            ) {
                items(
                    items = mergedNotifications,
                    key = { item -> item.uniqueKey }
                ) { item ->
                    when (item) {
                        is MergedNotificationItem.BroadcastItem -> {
                            BroadcastNotificationCard(
                                item = item,
                                onClick = {
                                    onOpenPostFromNotification(item.notification.imageId)
                                }
                            )
                        }

                        is MergedNotificationItem.CommentReplyItem -> {
                            CommentReplyNotificationCard(
                                item = item,
                                onClick = {
                                    onOpenPostFromNotification(item.comment.imageId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastNotificationCard(
    item: MergedNotificationItem.BroadcastItem,
    onClick: () -> Unit
) {
    val notif = item.notification
    val timeAgo = rememberRelativeTimeText(item.timestampMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(PromptCardSurfaceBrush)
            .border(1.dp, IndigoSoftBorder, RoundedCornerShape(24.dp))
            .pressScaleClickable(onClick = onClick)
            .padding(16.dp)
            .testTag("notification_broadcast_${notif.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (notif.imageUrl.isNotBlank()) {
                LumiereExternalImage(
                    imageUrl = notif.imageUrl,
                    contentDescription = notif.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, IndigoSoftBorder, RoundedCornerShape(18.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(AccentViolet.copy(alpha = 0.18f))
                        .border(1.dp, IndigoSoftBorder, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = TextPureWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notif.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = TextPureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = timeAgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLavenderMuted
                    )
                }

                if (notif.body.isNotBlank()) {
                    Text(
                        text = notif.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLavenderGray,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentReplyNotificationCard(
    item: MergedNotificationItem.CommentReplyItem,
    onClick: () -> Unit
) {
    val comment = item.comment
    val timeAgo = rememberRelativeTimeText(item.timestampMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(PromptCardSurfaceBrush)
            .border(1.dp, IndigoSoftBorder, RoundedCornerShape(24.dp))
            .pressScaleClickable(onClick = onClick)
            .padding(16.dp)
            .testTag("notification_reply_${comment.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row: Small rounded post thumbnail + post title + time ago
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (item.postThumbnailUrl.isNotBlank()) {
                    LumiereExternalImage(
                        imageUrl = item.postThumbnailUrl,
                        contentDescription = item.postTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, IndigoSoftBorder, RoundedCornerShape(14.dp))
                    )
                }
                Text(
                    text = item.postTitle.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = TextPureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextLavenderMuted
                )
            }

            // User's own comment text
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextLavenderGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Admin reply in a slightly highlighted rounded block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AccentViolet.copy(alpha = 0.16f))
                    .border(1.dp, AccentViolet.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reply_from_promptlens),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextLavenderGray,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = comment.adminReply,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPureWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberRelativeTimeText(timestampMs: Long): String {
    if (timestampMs <= 0L) return stringResource(R.string.just_now)
    val diffSec = max(0L, (System.currentTimeMillis() - timestampMs) / 1000L)
    val minutes = diffSec / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> stringResource(R.string.just_now)
        minutes < 60L -> stringResource(R.string.minutes_ago, minutes.toInt())
        hours < 24L -> stringResource(R.string.hours_ago, hours.toInt())
        else -> stringResource(R.string.days_ago, days.toInt())
    }
}

/**
 * PROFILE SCREEN (Requirement 9):
 * - Top card (28dp rounded): round avatar, name (or "Guest"), and a "Sign in with Google" pill button when not signed in.
 * - Below, grouped rounded list cards (28dp) with colored circular icon badges and chevrons, rows 64dp high:
 *   Notifications, Language, Share app, Rate us, Privacy policy, Terms of use, About.
 *   Default language English; other languages selectable here.
 */
@Composable
fun ProfileScreen(
    firebaseUser: FirebaseUser?,
    themePreference: ThemePreference,
    languageTag: String,
    isAdPrivacyOptionsRequired: Boolean,
    appConfigSettings: AppConfigSettings,
    onThemeChange: (ThemePreference) -> Unit,
    onLanguageChange: (String) -> Unit,
    onClearImageCache: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSignedInWithGoogle = firebaseUser != null && !firebaseUser.isAnonymous

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val appName = stringResource(R.string.app_name)
    val playStoreUrl = appConfigSettings.playStoreUrl.ifBlank {
        BuildConfig.OFFICIAL_PLAY_STORE_URL
    }
    val isBangla = languageTag.startsWith("bn", ignoreCase = true)

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = IndigoSurfaceTop,
            titleContentColor = TextPureWhite,
            textContentColor = TextLavenderGray,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.language_label),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = !isBangla,
                            onClick = {
                                onLanguageChange("en")
                                showLanguageDialog = false
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.language_english),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentViolet,
                                selectedLabelColor = TextPureWhite,
                                containerColor = IndigoInnerBox,
                                labelColor = TextLavenderGray
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("language_chip_en")
                        )
                        FilterChip(
                            selected = isBangla,
                            onClick = {
                                onLanguageChange("bn")
                                showLanguageDialog = false
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.language_bangla),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentViolet,
                                selectedLabelColor = TextPureWhite,
                                containerColor = IndigoInnerBox,
                                labelColor = TextLavenderGray
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("language_chip_bn")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = TextLavenderGray
                    )
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = IndigoSurfaceTop,
            titleContentColor = TextPureWhite,
            textContentColor = TextLavenderGray,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.terms_of_use_label),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.terms_acceptable_use_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPureWhite
                    )
                    Text(
                        text = stringResource(R.string.terms_acceptable_use_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLavenderGray
                    )
                    Text(
                        text = stringResource(R.string.terms_comments_rules_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPureWhite
                    )
                    Text(
                        text = stringResource(R.string.terms_comments_rules_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLavenderGray
                    )
                    Text(
                        text = stringResource(R.string.terms_content_rights_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPureWhite
                    )
                    Text(
                        text = stringResource(R.string.terms_content_rights_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLavenderGray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text(
                        text = stringResource(R.string.back_action),
                        color = TextPureWhite
                    )
                }
            }
        )
    }

    val supportEmail = appConfigSettings.supportEmail.ifBlank {
        AppLinks.SUPPORT_EMAIL
    }
    val appVersionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = IndigoSurfaceTop,
            titleContentColor = TextPureWhite,
            textContentColor = TextLavenderGray,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.about_version_format, appVersionName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPureWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.about_support_email_format, supportEmail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLavenderGray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(
                        text = stringResource(R.string.back_action),
                        color = TextPureWhite
                    )
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = IndigoSurfaceTop,
            titleContentColor = TextPureWhite,
            textContentColor = TextLavenderGray,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.delete_account_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_account_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLavenderGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteAccountClick()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text(text = stringResource(R.string.delete_account_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = TextLavenderGray
                    )
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .testTag("profile_screen_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            ),
            color = TextPureWhite
        )

        // 1. Top Card (24dp rounded): Round avatar, name (or "Guest"), and a "Sign in with Google" pill button when not signed in
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.2f),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(PromptCardSurfaceBrush)
                .border(1.dp, IndigoSoftBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            val photoUrl = if (isSignedInWithGoogle) firebaseUser?.photoUrl?.toString().orEmpty() else ""
            val displayName = if (isSignedInWithGoogle) {
                firebaseUser?.displayName.orEmpty().ifBlank { appName }
            } else {
                stringResource(R.string.guest_user_title)
            }
            val email = if (isSignedInWithGoogle) firebaseUser?.email.orEmpty() else ""

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Round avatar with accent ring
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(PromptAccentGradientBrush)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(IndigoInnerBox),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotBlank()) {
                        LumiereExternalImage(
                            imageUrl = photoUrl,
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = displayName,
                            tint = TextLavenderGray,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 21.sp
                    ),
                    color = TextPureWhite,
                    textAlign = TextAlign.Center
                )

                if (email.isNotBlank()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLavenderGray,
                        textAlign = TextAlign.Center
                    )
                }

                if (!isSignedInWithGoogle) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = AccentViolet.copy(alpha = 0.25f),
                                spotColor = AccentBlue.copy(alpha = 0.25f)
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .background(PromptAccentGradientBrush)
                            .pressScaleClickable(onClick = onSignInClick)
                            .testTag("profile_google_sign_in_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.sign_in_with_google),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 2. First Grouped Rounded Card (24dp): Notifications, Language
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(PromptCardSurfaceBrush)
                .border(1.dp, IndigoSoftBorder, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ProfileMenuRow(
                    icon = Icons.Default.Notifications,
                    badgeColor = BadgeEmeraldGreen,
                    label = stringResource(R.string.notifications_title),
                    testTag = "profile_row_notifications",
                    onClick = onOpenNotifications
                )

                HorizontalDivider(
                    color = IndigoSoftBorder,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                ProfileMenuRow(
                    icon = Icons.Default.Language,
                    badgeColor = BadgeVioletPurple,
                    label = stringResource(R.string.language_label),
                    trailingText = if (isBangla) {
                        stringResource(R.string.language_bangla)
                    } else {
                        stringResource(R.string.language_english)
                    },
                    testTag = "profile_row_language",
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        // 3. Second Grouped Rounded Card (24dp): Share app, Rate us, Contact us, Privacy policy, Terms of use, About
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(PromptCardSurfaceBrush)
                .border(1.dp, IndigoSoftBorder, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Share app
                ProfileMenuRow(
                    icon = Icons.Default.Share,
                    badgeColor = BadgeElectricBlue,
                    label = stringResource(R.string.share_app_label),
                    testTag = "profile_row_share_app",
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$appName\n$playStoreUrl")
                        }
                        val chooser = Intent.createChooser(sendIntent, null).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(chooser) }
                    }
                )

                HorizontalDivider(
                    color = IndigoSoftBorder,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // Rate us
                ProfileMenuRow(
                    icon = Icons.Default.StarRate,
                    badgeColor = BadgeAmberOrange,
                    label = stringResource(R.string.rate_app_label),
                    testTag = "profile_row_rate_app",
                    onClick = { openExternalUrl(context, playStoreUrl) }
                )

                HorizontalDivider(
                    color = IndigoSoftBorder,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // Contact us
                ProfileMenuRow(
                    icon = Icons.Default.Email,
                    badgeColor = BadgeVioletPurple,
                    label = stringResource(R.string.contact_us_label),
                    testTag = "profile_row_contact_us",
                    onClick = {
                        val subject = Uri.encode("PromptLens support")
                        val mailtoUri = Uri.parse("mailto:${supportEmail.trim()}?subject=$subject")
                        val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(emailIntent)
                        }.onFailure {
                            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "message/rfc822"
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail.trim()))
                                putExtra(Intent.EXTRA_SUBJECT, "PromptLens support")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(fallbackIntent, null).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        }
                    }
                )

                // Privacy policy (hidden while PRIVACY_POLICY_URL is blank)
                if (AppLinks.PRIVACY_POLICY_URL.isNotBlank()) {
                    HorizontalDivider(
                        color = IndigoSoftBorder,
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Policy,
                        badgeColor = BadgeShieldGreen,
                        label = stringResource(R.string.privacy_policy_label),
                        testTag = "profile_row_privacy_policy",
                        onClick = {
                            openInAppCustomTab(context, AppLinks.PRIVACY_POLICY_URL)
                        }
                    )
                }

                HorizontalDivider(
                    color = IndigoSoftBorder,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // Terms of use
                ProfileMenuRow(
                    icon = Icons.Default.Lock,
                    badgeColor = BadgeTealCyan,
                    label = stringResource(R.string.terms_of_use_label),
                    testTag = "profile_row_terms_of_use",
                    onClick = { showTermsDialog = true }
                )

                HorizontalDivider(
                    color = IndigoSoftBorder,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // About
                ProfileMenuRow(
                    icon = Icons.Default.Favorite,
                    badgeColor = BadgeSkyBlue,
                    label = stringResource(R.string.about_label),
                    trailingText = appVersionName,
                    testTag = "profile_row_about",
                    onClick = { showAboutDialog = true }
                )

                if (isAdPrivacyOptionsRequired) {
                    HorizontalDivider(
                        color = IndigoSoftBorder,
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    ProfileMenuRow(
                        icon = Icons.Default.PrivacyTip,
                        badgeColor = BadgeVioletPurple,
                        label = stringResource(R.string.ad_privacy_settings_label),
                        testTag = "profile_row_ad_privacy",
                        onClick = {
                            findActivity(context)?.let { activity ->
                                AdMobManager.showPrivacyOptionsForm(activity)
                            }
                        }
                    )
                }

                if (isSignedInWithGoogle) {
                    HorizontalDivider(
                        color = IndigoSoftBorder,
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    ProfileMenuRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        badgeColor = BadgeAmberOrange,
                        label = stringResource(R.string.sign_out_label),
                        testTag = "profile_row_sign_out",
                        onClick = onSignOutClick
                    )
                }
            }
        }

        // Delete account in small red text at the very bottom (when signed in)
        if (isSignedInWithGoogle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.testTag("profile_delete_account_button")
                ) {
                    Text(
                        text = stringResource(R.string.delete_account_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(104.dp))
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    badgeColor: Color,
    label: String,
    testTag: String,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 18.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = TextPureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLavenderGray
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextLavenderGray,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun openExternalUrl(context: Context, rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun findActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
