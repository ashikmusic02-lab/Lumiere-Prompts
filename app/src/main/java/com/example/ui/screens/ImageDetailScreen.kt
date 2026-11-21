package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.ads.AdMobManager
import com.example.ads.LumiereBannerAdSlot
import com.example.model.AdvertisementSettings
import com.example.model.AppConfigSettings
import com.example.model.ImageComment
import com.example.model.InspirationImage
import com.example.ui.components.LumiereExternalImage
import com.example.ui.components.formatCompactCount
import com.example.ui.components.rememberShimmerBrush
import com.example.ui.theme.HeartLikeRed
import com.example.ui.theme.PromptCyanSecondary
import com.example.ui.theme.PromptGoldAccent
import com.example.ui.theme.PromptVioletDark
import com.example.ui.theme.PromptVioletPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImageDetailScreen(
    image: InspirationImage,
    isLiked: Boolean,
    isFavorited: Boolean,
    isOnline: Boolean,
    isRealUser: Boolean,
    activePromptText: String?,
    isPromptComingSoon: Boolean,
    comments: List<ImageComment>,
    currentUserId: String?,
    advertisementSettings: AdvertisementSettings,
    appConfigSettings: AppConfigSettings,
    onBack: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onUnlockAndLoadPrompt: (String) -> Unit,
    onClearPromptMemory: () -> Unit,
    onPostShared: () -> Unit,
    onPostComment: (String) -> Unit,
    onDeleteComment: (ImageComment) -> Unit,
    onReportComment: (ImageComment, String) -> Unit,
    onBlockUser: (String) -> Unit,
    onRequireSignIn: (afterSignInAction: () -> Unit) -> Unit,
    onEmitMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var promptRevealed by rememberSaveable(image.id) { mutableStateOf(false) }
    var isWaitingForAdToFinish by remember { mutableStateOf(false) }
    var commentsExpanded by rememberSaveable { mutableStateOf(false) }
    var commentDraft by rememberSaveable { mutableStateOf("") }
    var isCommentInputFocused by remember { mutableStateOf(false) }
    var commentToReport by remember { mutableStateOf<ImageComment?>(null) }
    val defaultReportReason = stringResource(R.string.report_default_reason)
    var reportReasonText by remember { mutableStateOf(defaultReportReason) }

    val turnOnInternetMsg = stringResource(R.string.turn_on_internet_message)
    val copiedMsg = stringResource(R.string.copied_message)
    val appName = stringResource(R.string.app_name)

    // Immediately start preloading the banner ad, rewarded ad, and interstitial ad when the screen opens
    LaunchedEffect(image.id, advertisementSettings) {
        AdMobManager.onDetailScreenEntered(context, advertisementSettings)
    }

    LaunchedEffect(activePromptText, isPromptComingSoon) {
        if (!activePromptText.isNullOrBlank() || isPromptComingSoon) {
            promptRevealed = true
            isWaitingForAdToFinish = false
        }
    }

    val handleExit: () -> Unit = {
        onClearPromptMemory()
        AdMobManager.onDetailScreenExited(
            context = context,
            settings = advertisementSettings,
            isCommentInputActive = isCommentInputFocused
        )
        onBack()
    }

    BackHandler(onBack = handleExit)

    DisposableEffect(image.id) {
        onDispose {
            onClearPromptMemory()
        }
    }

    // Report Comment Dialog
    commentToReport?.let { targetComment ->
        AlertDialog(
            onDismissRequest = { commentToReport = null },
            title = {
                Text(
                    text = stringResource(R.string.report_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = reportReasonText,
                    onValueChange = { reportReasonText = it },
                    label = { Text(stringResource(R.string.report_reason_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_reason_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportComment(targetComment, reportReasonText.ifBlank { defaultReportReason })
                        commentToReport = null
                    },
                    modifier = Modifier.testTag("confirm_report_button")
                ) {
                    Text(stringResource(R.string.submit_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToReport = null }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    val safeAspectRatio = image.aspectRatio.toFloat().coerceIn(0.62f, 1.45f)
    val hasBannerSlotConfigured = remember(advertisementSettings) {
        advertisementSettings.allAdsEnabled &&
            advertisementSettings.detailBannerEnabled &&
            AdMobManager.resolveBannerUnitId(advertisementSettings).isNotBlank()
    }

    // Clicking "Get Prompt" ("প্রম্পট নিন") shows a Rewarded or Interstitial Ad and then reveals the prompt
    val triggerGetPromptFlow: () -> Unit = {
        if (!isWaitingForAdToFinish) {
            isWaitingForAdToFinish = true
            AdMobManager.showRewardedOrInterstitialForPrompt(
                context = context,
                settings = advertisementSettings,
                userId = currentUserId,
                imageId = image.id,
                onProceedToPrompt = { adNetwork ->
                    isWaitingForAdToFinish = false
                    onUnlockAndLoadPrompt(adNetwork)
                }
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("image_detail_screen"),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 1. Top Back Bar with Category Pill
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        IconButton(
                            onClick = handleExit,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_action),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (image.categoryName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                            )
                        ) {
                            Text(
                                text = image.categoryName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Large Framed Hero Image (constrained height so Banner Ad & Get Prompt button are immediately visible on open)
        item {
            val imageShape = RoundedCornerShape(24.dp)
            val rimBrush = Brush.linearGradient(
                colors = listOf(
                    PromptVioletPrimary.copy(alpha = 0.45f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    PromptCyanSecondary.copy(alpha = 0.35f)
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 220.dp, max = 330.dp)
                    .aspectRatio(safeAspectRatio.coerceIn(0.88f, 1.45f))
                    .clip(imageShape)
                    .border(1.2.dp, rimBrush, imageShape)
            ) {
                LumiereExternalImage(
                    imageUrl = image.imageUrl,
                    contentDescription = image.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Title & Description (if present)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = image.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                if (image.description.isNotBlank()) {
                    Text(
                        text = image.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4. Premium Action Row: Like (solid red #FF3B5C when liked), Comment count, Save, Share
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like (solid red heart #FF3B5C + red count when liked; outlined neutral when not liked)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isLiked) {
                        HeartLikeRed.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isLiked) HeartLikeRed.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleLike)
                        .testTag("detail_like_button")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.like_action),
                            tint = if (isLiked) HeartLikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatCompactCount(image.likeCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isLiked) HeartLikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Comment count (tapping expands/scrolls to comments)
                if (appConfigSettings.allowComments) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                commentsExpanded = true
                                coroutineScope.launch {
                                    listState.animateScrollToItem(5)
                                }
                            }
                            .testTag("detail_comments_action_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatCompactCount(maxOf(image.commentCount, comments.size.toLong())),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Save
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isFavorited) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleFavorite)
                        .testTag("detail_save_button")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.save_action),
                            tint = if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFavorited) stringResource(R.string.saved_action) else stringResource(R.string.save_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Share (shares app name + Play Store link, NOT the prompt text)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val playStoreUrl = appConfigSettings.playStoreUrl.ifBlank {
                                BuildConfig.OFFICIAL_PLAY_STORE_URL
                            }
                            sharePlainText(
                                context = context,
                                text = "$appName\n$playStoreUrl"
                            )
                            onPostShared()
                        }
                        .testTag("detail_share_post_button")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.share_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 5. Native-Styled Banner Ad Slot shown immediately as the post opens, with >= 22dp vertical spacing
        item {
            Spacer(modifier = Modifier.height(22.dp))
            if (!isCommentInputFocused && hasBannerSlotConfigured) {
                LumiereBannerAdSlot(
                    settings = advertisementSettings,
                    enabledForPlacement = advertisementSettings.detailBannerEnabled,
                    reserveFixedHeight = true,
                    useLargeNativeBanner = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("detail_banner_ad")
                )
                Spacer(modifier = Modifier.height(22.dp))
            }
        }

        // 6. Luxury "Get Prompt" ("প্রম্পট নিন") Gradient CTA Button OR Revealed Prompt Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (!promptRevealed && activePromptText == null && !isPromptComingSoon) {
                    val ctaGradient = Brush.horizontalGradient(
                        colors = listOf(
                            PromptVioletDark,
                            PromptVioletPrimary,
                            Color(0xFF9333EA)
                        )
                    )
                    val borderBrush = Brush.horizontalGradient(
                        colors = listOf(
                            PromptGoldAccent.copy(alpha = 0.65f),
                            PromptCyanSecondary.copy(alpha = 0.65f)
                        )
                    )
                    val buttonShape = RoundedCornerShape(18.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(buttonShape)
                            .background(ctaGradient)
                            .border(1.2.dp, borderBrush, buttonShape)
                            .clickable {
                                if (!isOnline) {
                                    onEmitMessage(turnOnInternetMsg)
                                    return@clickable
                                }
                                if (appConfigSettings.requireLoginForPrompts && !isRealUser) {
                                    onRequireSignIn {
                                        triggerGetPromptFlow()
                                    }
                                    return@clickable
                                }
                                triggerGetPromptFlow()
                            }
                            .testTag("get_prompt_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PromptGoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.get_prompt_action),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Luxury Prompt Card (selectable prompt + Copy/Share, or calm "Prompt coming soon")
                    val cardShape = RoundedCornerShape(22.dp)
                    val cardBorderBrush = Brush.linearGradient(
                        colors = listOf(
                            PromptVioletPrimary.copy(alpha = 0.6f),
                            PromptCyanSecondary.copy(alpha = 0.45f)
                        )
                    )

                    Card(
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.2.dp, cardBorderBrush, cardShape)
                            .testTag("prompt_display_card"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PromptGoldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.prompt_heading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            when {
                                !activePromptText.isNullOrBlank() -> {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = activePromptText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier
                                                    .padding(14.dp)
                                                    .testTag("prompt_selectable_text")
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                copyToClipboard(context, activePromptText)
                                                onEmitMessage(copiedMsg)
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("copy_prompt_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.copy_action),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                sharePlainText(context, activePromptText)
                                                onPostShared()
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("share_prompt_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.share_action),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                isPromptComingSoon -> {
                                    Text(
                                        text = stringResource(R.string.prompt_coming_soon),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("prompt_coming_soon_text")
                                    )
                                }

                                else -> {
                                    // Subtle skeleton lines while prompt doc resolves from Firestore (no spinning circle)
                                    val shimmerBrush = rememberShimmerBrush()
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(16.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(shimmerBrush)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.75f)
                                                .height(16.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(shimmerBrush)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Comments Section (collapsed by default, hidden if allowComments == false)
        if (appConfigSettings.allowComments) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { commentsExpanded = !commentsExpanded }
                                .padding(horizontal = 16.dp, vertical = 15.dp)
                                .testTag("comments_collapse_header"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.comments_header,
                                    maxOf(image.commentCount.toInt(), comments.size)
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (commentsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = commentsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Comment Input Box (does NOT auto-focus)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = commentDraft,
                                        onValueChange = { commentDraft = it },
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.comment_placeholder),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .onFocusChanged { focusState ->
                                                isCommentInputFocused = focusState.isFocused
                                            }
                                            .testTag("comment_input_field")
                                    )
                                    IconButton(
                                        onClick = {
                                            val textToSend = commentDraft.trim()
                                            if (textToSend.isNotEmpty()) {
                                                onPostComment(textToSend)
                                                commentDraft = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                            .size(46.dp)
                                            .testTag("send_comment_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = stringResource(R.string.submit_action),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Respectful note below input
                                Text(
                                    text = stringResource(R.string.comment_respect_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (comments.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.no_comments_yet),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        comments.forEach { comment ->
                                            CommentRowItem(
                                                comment = comment,
                                                isOwnComment = !currentUserId.isNullOrBlank() && comment.userId == currentUserId,
                                                onDelete = { onDeleteComment(comment) },
                                                onReport = {
                                                    reportReasonText = defaultReportReason
                                                    commentToReport = comment
                                                },
                                                onBlockUser = { onBlockUser(comment.userId) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRowItem(
    comment: ImageComment,
    isOwnComment: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlockUser: () -> Unit
) {
    val dateString = remember(comment.createdAt) {
        val seconds = comment.createdAt?.seconds
        if (seconds != null && seconds > 0L) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(seconds * 1000L))
        } else ""
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comment_item_${comment.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.userName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (dateString.isNotBlank()) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Indented Reply from PromptLens if adminReply is non-empty
            if (comment.adminReply.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reply_from_promptlens),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = comment.adminReply,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Actions: Own comment -> Delete; Others -> Report & Block user
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOwnComment) {
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("delete_comment_${comment.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.delete_comment),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    TextButton(
                        onClick = onReport,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("report_comment_${comment.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.report_comment),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (comment.userId.isNotBlank()) {
                        TextButton(
                            onClick = onBlockUser,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("block_user_${comment.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.block_user),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("PromptLens Prompt", text))
}

private fun sharePlainText(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}
