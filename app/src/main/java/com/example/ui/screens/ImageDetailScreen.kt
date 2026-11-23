package com.example.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ads.AdMobManager
import com.example.ads.LumiereBannerAdSlot
import com.example.model.AdvertisementSettings
import com.example.model.AiToolItem
import com.example.model.AppConfigSettings
import com.example.model.ImageComment
import com.example.model.InspirationImage
import com.example.ui.components.LumiereExternalImage
import com.example.ui.components.formatCompactCount
import com.example.ui.components.openInAppCustomTab
import com.example.ui.components.pressScaleClickable
import com.example.ui.components.rememberShimmerBrush
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.HeartLikeRed
import com.example.ui.theme.IndigoBorderBase
import com.example.ui.theme.IndigoInnerBox
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.IndigoSurfaceDark
import com.example.ui.theme.PromptAccentGradientBrush
import com.example.ui.theme.PromptCardSurfaceBrush
import com.example.ui.theme.PromptGoldAccent
import com.example.ui.theme.TextLavenderGray
import com.example.ui.theme.TextPureWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private fun isRealCategory(raw: String): Boolean {
    val cleaned = raw.trim()
    return cleaned.isNotBlank() &&
        !cleaned.equals("uncategorized", ignoreCase = true) &&
        !cleaned.equals("অন্যান্য", ignoreCase = true)
}

@Composable
fun ImageDetailScreen(
    image: InspirationImage,
    aiTools: List<AiToolItem>,
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
    var isWaitingForAdToFinish by remember(image.id) { mutableStateOf(false) }
    var commentsExpanded by rememberSaveable(image.id) { mutableStateOf(false) }
    var commentDraft by rememberSaveable(image.id) { mutableStateOf("") }
    var isCommentInputFocused by remember { mutableStateOf(false) }
    var commentToReport by remember { mutableStateOf<ImageComment?>(null) }
    val defaultReportReason = stringResource(R.string.report_default_reason)
    var reportReasonText by remember { mutableStateOf(defaultReportReason) }

    val copiedMsg = stringResource(R.string.copied_message)
    val appName = stringResource(R.string.app_name)

    LaunchedEffect(image.id) {
        promptRevealed = false
        isWaitingForAdToFinish = false
        listState.scrollToItem(0)
    }

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

    val hasValidCategory = remember(image.categoryId, image.categoryName) {
        isRealCategory(image.categoryId) && isRealCategory(image.categoryName)
    }

    val enabledAiTools = remember(aiTools) {
        aiTools.filter { it.enabled && it.name.isNotBlank() && it.url.isNotBlank() }
            .sortedBy { it.sortOrder }
    }

    // Report Comment Dialog
    commentToReport?.let { targetComment ->
        AlertDialog(
            onDismissRequest = { commentToReport = null },
            containerColor = IndigoSurfaceDark,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.report_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = reportReasonText,
                    onValueChange = { reportReasonText = it },
                    label = { Text(stringResource(R.string.report_reason_label)) },
                    shape = RoundedCornerShape(18.dp),
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
                    shape = RoundedCornerShape(50),
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

    val safeAspectRatio = image.aspectRatio.toFloat().coerceIn(0.68f, 1.35f)
    val hasBannerSlotConfigured = remember(advertisementSettings) {
        advertisementSettings.allAdsEnabled &&
            advertisementSettings.detailBannerEnabled &&
            AdMobManager.resolveBannerUnitId(advertisementSettings).isNotBlank()
    }

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
                    promptRevealed = true
                    onUnlockAndLoadPrompt(adNetwork)
                }
            )
        }
    }

    val handleGetPromptOrSignIn: () -> Unit = {
        if (appConfigSettings.requireLoginForPrompts && !isRealUser) {
            onRequireSignIn {
                triggerGetPromptFlow()
            }
        } else {
            triggerGetPromptFlow()
        }
    }

    val isPromptUnlockedOrRevealed = promptRevealed || !activePromptText.isNullOrBlank() || isPromptComingSoon

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("image_detail_screen")
    ) {
        // 1. Slim floating top bar pinned at the top (20dp rounded, 1dp soft border) with one round back button. It never scrolls away.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = IndigoSurfaceDark,
            border = BorderStroke(1.dp, IndigoSoftBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PromptCardSurfaceBrush, CircleShape)
                        .border(1.dp, IndigoSoftBorder, CircleShape)
                        .pressScaleClickable(onClick = handleExit)
                        .testTag("detail_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_action),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. Image centered, full width minus 16dp, 28dp rounded, soft glow. Nothing drawn on top of the image.
            item {
                val imageShape = RoundedCornerShape(28.dp)
                val glowRimBrush = Brush.linearGradient(
                    colors = listOf(
                        AccentViolet.copy(alpha = 0.55f),
                        IndigoBorderBase.copy(alpha = 0.65f),
                        AccentBlue.copy(alpha = 0.55f)
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 380.dp)
                            .aspectRatio(safeAspectRatio)
                            .shadow(
                                elevation = 16.dp,
                                shape = imageShape,
                                ambientColor = AccentViolet.copy(alpha = 0.35f),
                                spotColor = AccentBlue.copy(alpha = 0.35f)
                            )
                            .clip(imageShape)
                            .border(1.5.dp, glowRimBrush, imageShape)
                    ) {
                        LumiereExternalImage(
                            imageUrl = image.imageUrl,
                            contentDescription = image.title.ifBlank { appName },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 3. Large 28dp rounded card below the image:
            //    - Post title (large, semibold, 22sp+)
            //    - Category pill ONLY when the post has a real category (hide for blank/"uncategorized"/"অন্যান্য")
            //    - Action row: exactly 4 round buttons in order left to right: Like, Comment, Share, Save (NO copy button in this row)
            //    - "Get Prompt" button (or, after reveal, darker inner rounded prompt box with "Prompt" left label + top-right light "Copy" chip + ~3-line scrollable text, and AI tool chips below it)
            item {
                val mainCardShape = RoundedCornerShape(28.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = mainCardShape,
                            ambientColor = AccentViolet.copy(alpha = 0.22f),
                            spotColor = AccentBlue.copy(alpha = 0.22f)
                        )
                        .clip(mainCardShape)
                        .background(PromptCardSurfaceBrush, mainCardShape)
                        .border(1.dp, IndigoSoftBorder, mainCardShape)
                        .padding(20.dp)
                        .testTag("prompt_display_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Post Title (large, semibold, 22sp+)
                        if (image.title.isNotBlank()) {
                            Text(
                                text = image.title,
                                fontSize = 22.sp,
                                lineHeight = 29.sp,
                                color = TextPureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Category Pill ONLY when the post has a real category (hidden for blank/"uncategorized"/"অন্যান্য")
                        if (hasValidCategory) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = AccentViolet.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = image.categoryName.trim(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Action row: EXACTLY 4 round buttons in this order left to right:
                        // 1. Like (heart, solid red #FF3B5C when liked)
                        // 2. Comment
                        // 3. Share
                        // 4. Save
                        // NO copy button in this row.
                        val commentCount = maxOf(image.commentCount, comments.size.toLong())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Like
                            RoundActionIconButton(
                                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.like_action),
                                containerColor = if (isLiked) HeartLikeRed.copy(alpha = 0.24f) else Color(0xFF361539),
                                iconTint = if (isLiked) HeartLikeRed else Color.White,
                                borderColor = if (isLiked) HeartLikeRed.copy(alpha = 0.75f) else HeartLikeRed.copy(alpha = 0.35f),
                                countText = image.likeCount.takeIf { it > 0L }?.let(::formatCompactCount),
                                testTag = "detail_like_button",
                                onClick = onToggleLike
                            )

                            // 2. Comment
                            RoundActionIconButton(
                                icon = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = stringResource(R.string.comments_header, commentCount.toInt()),
                                containerColor = Color(0xFF123652),
                                iconTint = Color(0xFF2DD4BF),
                                borderColor = Color(0xFF2DD4BF).copy(alpha = 0.38f),
                                countText = commentCount.takeIf { it > 0L }?.let(::formatCompactCount),
                                testTag = "detail_comments_action_button",
                                onClick = {
                                    if (appConfigSettings.allowComments) {
                                        commentsExpanded = !commentsExpanded
                                        if (commentsExpanded) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(if (hasBannerSlotConfigured) 3 else 2)
                                            }
                                        }
                                    }
                                }
                            )

                            // 3. Share
                            RoundActionIconButton(
                                icon = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share_action),
                                containerColor = Color(0xFF14355E),
                                iconTint = Color(0xFF60A5FA),
                                borderColor = Color(0xFF60A5FA).copy(alpha = 0.38f),
                                countText = null,
                                testTag = "detail_share_post_button",
                                onClick = {
                                    val playStoreUrl = appConfigSettings.playStoreUrl.ifBlank {
                                        BuildConfig.OFFICIAL_PLAY_STORE_URL
                                    }
                                    val textToShare = if (!activePromptText.isNullOrBlank()) {
                                        activePromptText
                                    } else {
                                        "$appName\n$playStoreUrl"
                                    }
                                    sharePlainText(context, textToShare)
                                    onPostShared()
                                }
                            )

                            // 4. Save
                            RoundActionIconButton(
                                icon = if (isFavorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(R.string.save_action),
                                containerColor = if (isFavorited) AccentViolet.copy(alpha = 0.28f) else Color(0xFF23185B),
                                iconTint = if (isFavorited) AccentViolet else TextLavenderGray,
                                borderColor = if (isFavorited) AccentViolet.copy(alpha = 0.75f) else AccentViolet.copy(alpha = 0.38f),
                                countText = null,
                                testTag = "detail_save_button",
                                onClick = onToggleFavorite
                            )
                        }

                        // 4 & 5. Before Get Prompt is tapped: show ONLY the "Get Prompt" button.
                        //        NO copy control and NO AI tool chips are visible anywhere before reveal.
                        if (!isPromptUnlockedOrRevealed) {
                            val pillShape = RoundedCornerShape(50)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .shadow(
                                        elevation = 14.dp,
                                        shape = pillShape,
                                        ambientColor = AccentViolet.copy(alpha = 0.45f),
                                        spotColor = AccentBlue.copy(alpha = 0.45f)
                                    )
                                    .clip(pillShape)
                                    .background(PromptAccentGradientBrush, pillShape)
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.28f),
                                        pillShape
                                    )
                                    .pressScaleClickable(onClick = handleGetPromptOrSignIn)
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
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            // 6. After the prompt is revealed:
                            //    - Darker inner rounded prompt box with a header showing ONLY a clean styled text label
                            //      "Prompt" (Bangla "প্রম্পট", soft lavender, semibold, NO icon) on the left and a small
                            //      rounded light "Copy" chip (copy icon + dark text "Copy"/"কপি") in the TOP-RIGHT corner.
                            //      No view-count in this header.
                            //    - The box shows about 3 lines and scrolls inside.
                            //    - Tapping the Copy chip or the text copies the FULL prompt with sensitive ClipData and shows
                            //      ONLY a small themed in-app snackbar "Copied" ("কপি হয়েছে") for about 2 seconds.
                            val innerShape = RoundedCornerShape(22.dp)
                            val copyFullPromptToClipboard: () -> Unit = {
                                if (!activePromptText.isNullOrBlank()) {
                                    copyToClipboard(context, activePromptText)
                                    onEmitMessage(copiedMsg)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(innerShape)
                                    .background(IndigoInnerBox, innerShape)
                                    .border(1.dp, IndigoSoftBorder, innerShape)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Header: "Prompt" ("প্রম্পট", soft lavender, semibold, NO icon) on the left,
                                // small rounded light "Copy" chip (copy icon + dark text "Copy"/"কপি") on the top-right.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.prompt_heading),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        ),
                                        color = TextLavenderGray
                                    )

                                    if (!activePromptText.isNullOrBlank()) {
                                        val copyChipShape = RoundedCornerShape(50)
                                        Row(
                                            modifier = Modifier
                                                .clip(copyChipShape)
                                                .background(Color(0xFFF3E8FF), copyChipShape)
                                                .border(1.dp, Color.White, copyChipShape)
                                                .pressScaleClickable(onClick = copyFullPromptToClipboard)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .testTag("copy_prompt_button"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = stringResource(R.string.copy_action),
                                                tint = Color(0xFF120C4A),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.copy_action),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp
                                                ),
                                                color = Color(0xFF120C4A),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                when {
                                    !activePromptText.isNullOrBlank() -> {
                                        val promptScrollState = rememberScrollState()
                                        // Compact ~3-line scrollable prompt box; tapping copies the FULL prompt
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(74.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { copyFullPromptToClipboard() }
                                                .verticalScroll(promptScrollState)
                                                .padding(vertical = 2.dp)
                                                .testTag("prompt_selectable_text")
                                        ) {
                                            Text(
                                                text = activePromptText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 22.sp,
                                                color = Color.White.copy(alpha = 0.94f)
                                            )
                                        }
                                    }

                                    isPromptComingSoon -> {
                                        Text(
                                            text = stringResource(R.string.prompt_coming_soon),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = TextLavenderGray,
                                            modifier = Modifier.testTag("prompt_coming_soon_text")
                                        )
                                    }

                                    else -> {
                                        val shimmerBrush = rememberShimmerBrush()
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(16.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(shimmerBrush)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.85f)
                                                    .height(16.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(shimmerBrush)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.65f)
                                                    .height(16.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(shimmerBrush)
                                            )
                                        }
                                    }
                                }
                            }

                            // 7. Below the prompt box, a row of rounded AI tool chips (round logo + name)
                            //    loaded from Firestore collection `aiTools`.
                            //    Shown ONLY after the prompt is revealed and if the list is non-empty.
                            if (!activePromptText.isNullOrBlank() && enabledAiTools.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(top = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("detail_ai_tools_chip_row")
                                ) {
                                    items(enabledAiTools, key = { it.id }) { tool ->
                                        AiToolPromptChip(
                                            tool = tool,
                                            onClick = {
                                                copyToClipboard(context, activePromptText)
                                                onEmitMessage(copiedMsg)
                                                openInAppCustomTab(context, tool.url)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Banner Ad in its own rounded 20dp container with a fixed reserved height and even spacing
            if (!isCommentInputFocused && hasBannerSlotConfigured) {
                item {
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
                }
            }

            // 9. Comments in rounded 28dp cards (NO "More like this" section)
            if (appConfigSettings.allowComments) {
                item {
                    val commentsShape = RoundedCornerShape(28.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(commentsShape)
                            .background(PromptCardSurfaceBrush, commentsShape)
                            .border(1.dp, IndigoSoftBorder, commentsShape)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { commentsExpanded = !commentsExpanded }
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
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
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (commentsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextLavenderGray
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
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = commentDraft,
                                            onValueChange = { commentDraft = it },
                                            placeholder = {
                                                Text(
                                                    text = stringResource(R.string.comment_placeholder),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextLavenderGray
                                                )
                                            },
                                            shape = RoundedCornerShape(50),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AccentViolet,
                                                unfocusedBorderColor = IndigoSoftBorder,
                                                focusedContainerColor = IndigoInnerBox,
                                                unfocusedContainerColor = IndigoInnerBox,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .onFocusChanged { focusState ->
                                                    isCommentInputFocused = focusState.isFocused
                                                }
                                                .testTag("comment_input_field")
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(PromptAccentGradientBrush, CircleShape)
                                                .pressScaleClickable {
                                                    val textToSend = commentDraft.trim()
                                                    if (textToSend.isNotEmpty()) {
                                                        onPostComment(textToSend)
                                                        commentDraft = ""
                                                    }
                                                }
                                                .testTag("send_comment_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = stringResource(R.string.submit_action),
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = stringResource(R.string.comment_respect_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextLavenderGray
                                    )

                                    if (comments.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.no_comments_yet),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextLavenderGray,
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
}

@Composable
private fun AiToolPromptChip(
    tool: AiToolItem,
    onClick: () -> Unit
) {
    val logoUrl = remember(tool.logoUrl, tool.url) { tool.resolvedLogoUrl() }
    val pillShape = RoundedCornerShape(50)

    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(Color(0xFF281870), pillShape)
            .border(1.dp, AccentViolet.copy(alpha = 0.45f), pillShape)
            .pressScaleClickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
            .testTag("detail_ai_tool_chip_${tool.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (logoUrl.isNotBlank()) {
                LumiereExternalImage(
                    imageUrl = logoUrl,
                    contentDescription = tool.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = tool.name,
                    tint = AccentViolet,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = tool.name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoundActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color,
    borderColor: Color,
    countText: String?,
    testTag: String,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .height(48.dp)
            .width(66.dp)
            .clip(pillShape)
            .background(containerColor, pillShape)
            .border(1.dp, borderColor, pillShape)
            .pressScaleClickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
            if (!countText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTint,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
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
        shape = RoundedCornerShape(22.dp),
        color = IndigoInnerBox,
        border = BorderStroke(1.dp, IndigoSoftBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comment_item_${comment.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                if (dateString.isNotBlank()) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLavenderGray
                    )
                }
            }

            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )

            if (comment.adminReply.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentViolet.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reply_from_promptlens),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextLavenderGray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = comment.adminReply,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }

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
    val clip = ClipData.newPlainText("PromptLens Prompt", text)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard?.setPrimaryClip(clip)
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
