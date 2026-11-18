package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ads.AdMobManager
import com.example.ads.LumiereBannerAdSlot
import com.example.ads.RewardedAdFlowState
import com.example.model.AdvertisementSettings
import com.example.model.AppConfigSettings
import com.example.model.ImageComment
import com.example.model.InspirationImage
import com.example.ui.components.LumiereExternalImage
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LuminousViolet
import com.example.ui.theme.SoftChampagneGold
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ImageDetailScreen(
    image: InspirationImage,
    relatedImages: List<InspirationImage>,
    hasMoreRelatedImages: Boolean,
    onLoadMoreRelated: () -> Unit,
    comments: List<ImageComment>,
    currentUserId: String?,
    unlockedPromptText: String?,
    isLiked: Boolean,
    isFavorited: Boolean,
    isAlreadyUnlockedByUser: Boolean,
    appConfigSettings: AppConfigSettings,
    advertisementSettings: AdvertisementSettings,
    onBack: () -> Unit,
    onSelectRelatedImage: (InspirationImage) -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onVerifiedRewardUnlock: () -> Unit,
    onRequestLoadUnlockedPrompt: () -> Unit,
    onPromptCopied: () -> Unit,
    onPromptShared: () -> Unit,
    onPostComment: (String) -> Unit,
    onDeleteComment: (ImageComment) -> Unit,
    onReportComment: (ImageComment, String) -> Unit,
    onRequireSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    // Determine whether this prompt is currently unlocked according to reward policy
    var unlockedInCurrentView by rememberSaveable(image.id) {
        mutableStateOf(isAlreadyUnlockedByUser && advertisementSettings.reusePreviousUnlocks)
    }
    LaunchedEffect(isAlreadyUnlockedByUser, advertisementSettings.reusePreviousUnlocks) {
        if (isAlreadyUnlockedByUser && advertisementSettings.reusePreviousUnlocks) {
            unlockedInCurrentView = true
            onRequestLoadUnlockedPrompt()
        }
    }

    var adFlowState by remember(image.id) { mutableStateOf<RewardedAdFlowState>(RewardedAdFlowState.Idle) }
    var commentDraft by rememberSaveable(image.id) { mutableStateOf("") }
    var commentToReport by remember { mutableStateOf<ImageComment?>(null) }
    var reportReasonDraft by remember { mutableStateOf("Spam or inappropriate comment") }

    val resolvedPromptText = unlockedPromptText?.takeIf { it.isNotBlank() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val gridColumns = if (maxWidth >= 700.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("image_detail_screen"),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Top Navigation & Full-Attention Image Presentation (Preserving Original Aspect Ratio)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("detail_back_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = ChampagneGold.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(50)
                            )
                        ) {
                            Text(
                                text = image.categoryName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftChampagneGold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("detail_favorite_top_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(
                                    imageVector = if (isFavorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (isFavorited) "Remove Favorite" else "Save Favorite",
                                    tint = if (isFavorited) ChampagneGold else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    // Main Full-Attention Image Frame (Original Aspect Ratio Preserved, No Distortion, No Browser Link)
                    val safeAspectRatio = image.aspectRatio.toFloat().coerceIn(0.55f, 1.85f)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .aspectRatio(safeAspectRatio)
                            .testTag("detail_main_image"),
                        shape = RoundedCornerShape(26.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                    ) {
                        LumiereExternalImage(
                            imageUrl = image.imageUrl,
                            contentDescription = image.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF09090D))
                        )
                    }
                }
            }

            // 2. Image Title, Category, Total View Count & Description
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = image.categoryName,
                            style = MaterialTheme.typography.labelLarge,
                            color = LuminousViolet
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = "Total Views",
                                tint = ChampagneGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${formatCompactCount(image.viewCount)} views",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = image.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (image.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = image.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Action Bar: Like, Comment Count, Share, Favorite
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Like Button + Count
                    ActionStatChip(
                        icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        label = formatCompactCount(image.likeCount),
                        active = isLiked,
                        activeColor = Color(0xFFF43F5E),
                        onClick = onToggleLike,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_like_button")
                    )

                    // Comment Count Indicator
                    ActionStatChip(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        label = formatCompactCount(maxOf(image.commentCount, comments.size.toLong())),
                        active = false,
                        activeColor = LuminousViolet,
                        onClick = { },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_comment_count_chip")
                    )

                    // Favorite Button
                    ActionStatChip(
                        icon = if (isFavorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = if (isFavorited) "Saved" else "Save",
                        active = isFavorited,
                        activeColor = ChampagneGold,
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_favorite_button")
                    )

                    // Share Button (Never shares original image URL)
                    ActionStatChip(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        active = false,
                        activeColor = LuminousViolet,
                        onClick = {
                            val textToShare = if (unlockedInCurrentView && resolvedPromptText != null) {
                                buildRequiredShareText(
                                    imageTitle = image.title,
                                    completePrompt = resolvedPromptText,
                                    playStoreUrl = appConfigSettings.playStoreUrl
                                )
                            } else {
                                buildString {
                                    append("${image.title} — ${image.categoryName}")
                                    if (appConfigSettings.playStoreUrl.isNotBlank()) {
                                        append("\n\nGet more amazing image prompts:\n${appConfigSettings.playStoreUrl}")
                                    }
                                }
                            }
                            shareTextViaNativeSheet(context, textToShare)
                            onPromptShared()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_share_button")
                    )
                }
            }

            // 4. Primary Monetization: GENERATE TEXT Button & Rewarded Ad Flow
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!unlockedInCurrentView) {
                        Button(
                            onClick = {
                                if (appConfigSettings.requireLoginForPrompts && currentUserId.isNullOrBlank()) {
                                    onRequireSignIn()
                                    return@Button
                                }
                                if (isAlreadyUnlockedByUser && advertisementSettings.reusePreviousUnlocks) {
                                    unlockedInCurrentView = true
                                    onRequestLoadUnlockedPrompt()
                                    return@Button
                                }
                                AdMobManager.showRewardedAdForPromptUnlock(
                                    context = context,
                                    settings = advertisementSettings,
                                    userId = currentUserId,
                                    imageId = image.id,
                                    onStateChanged = { state -> adFlowState = state },
                                    onVerifiedRewardEarned = {
                                        unlockedInCurrentView = true
                                        onVerifiedRewardUnlock()
                                    }
                                )
                            },
                            enabled = adFlowState !is RewardedAdFlowState.LoadingAd &&
                                adFlowState !is RewardedAdFlowState.ShowingAd,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampagneGold,
                                contentColor = Color(0xFF171204)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("generate_text_button")
                        ) {
                            when (adFlowState) {
                                is RewardedAdFlowState.LoadingAd,
                                is RewardedAdFlowState.ShowingAd -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFF171204),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Loading Rewarded Advertisement…",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Generate Text",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Graceful Ad Failure Fallback & Retry Mechanism
                        val failedState = adFlowState as? RewardedAdFlowState.Failed
                        if (failedState != null) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = failedState.fallbackMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            AdMobManager.showRewardedAdForPromptUnlock(
                                                context = context,
                                                settings = advertisementSettings,
                                                userId = currentUserId,
                                                imageId = image.id,
                                                onStateChanged = { state -> adFlowState = state },
                                                onVerifiedRewardEarned = {
                                                    unlockedInCurrentView = true
                                                    onVerifiedRewardUnlock()
                                                }
                                            )
                                        },
                                        modifier = Modifier.testTag("retry_rewarded_ad_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Retry Ad",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Retry Rewarded Advertisement")
                                    }
                                }
                            }
                        }
                    }

                    // 5. Small Banner Advertisement Area Directly Below Generate Text Button (Collapses to 0dp if unavailable)
                    LumiereBannerAdSlot(
                        settings = advertisementSettings,
                        enabledForPlacement = advertisementSettings.detailBannerEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "detail_banner_ad"
                    )

                    // 6. Unlocked Prompt Display, Copy Text & Share Text (Fetched from `imagePrompts/{imageId}`)
                    AnimatedVisibility(
                        visible = unlockedInCurrentView,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        UnlockedPromptCard(
                            promptText = resolvedPromptText ?: "Loading unlocked prompt from vault…",
                            isReadyToCopy = resolvedPromptText != null,
                            onCopyPrompt = {
                                if (resolvedPromptText != null) {
                                    copyPromptToClipboard(context, resolvedPromptText)
                                    onPromptCopied()
                                }
                            },
                            onSharePrompt = {
                                if (resolvedPromptText != null) {
                                    val formatted = buildRequiredShareText(
                                        imageTitle = image.title,
                                        completePrompt = resolvedPromptText,
                                        playStoreUrl = appConfigSettings.playStoreUrl
                                    )
                                    shareTextViaNativeSheet(context, formatted)
                                    onPromptShared()
                                }
                            }
                        )
                    }
                }
            }

            // 7. Comment System
            if (appConfigSettings.allowComments) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CommentSectionHeader(
                        commentCount = comments.size,
                        commentDraft = commentDraft,
                        onDraftChange = { commentDraft = it },
                        onSubmit = {
                            val textToSend = commentDraft
                            commentDraft = ""
                            onPostComment(textToSend)
                        }
                    )
                }

                if (comments.isEmpty()) {
                    item {
                        Text(
                            text = "No comments yet. Share your lighting or prompt notes on this inspiration!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    items(comments, key = { "comment_${it.id}" }) { comment ->
                        CommentItemCard(
                            comment = comment,
                            isOwner = !currentUserId.isNullOrBlank() && comment.userId == currentUserId,
                            onDelete = { onDeleteComment(comment) },
                            onReport = { commentToReport = comment },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            // 8. Related Images from the Same Category (Paginated)
            if (relatedImages.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "More in ${image.categoryName}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Related photography inspirations & prompts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ResponsiveGalleryGrid(
                        images = relatedImages,
                        columnsCount = gridColumns,
                        likedIds = emptySet(),
                        favoritedIds = emptySet(),
                        unlockedIds = emptySet(),
                        onOpenImageDetail = onSelectRelatedImage,
                        onToggleLike = { },
                        onToggleFavorite = { }
                    )

                    if (hasMoreRelatedImages) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = onLoadMoreRelated,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("load_more_related_button")
                            ) {
                                Text("Load More from ${image.categoryName}")
                            }
                        }
                    }
                }
            }
        }
    }

    // Report Inappropriate Comment Modal -> Writes to `reports/{reportId}`
    val targetComment = commentToReport
    if (targetComment != null) {
        AlertDialog(
            onDismissRequest = { commentToReport = null },
            title = { Text("Report Comment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Flag this comment by ${targetComment.userName} for moderator review:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = reportReasonDraft,
                        onValueChange = { reportReasonDraft = it },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportComment(targetComment, reportReasonDraft)
                        commentToReport = null
                    }
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToReport = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UnlockedPromptCard(
    promptText: String,
    isReadyToCopy: Boolean,
    onCopyPrompt: () -> Unit,
    onSharePrompt: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(listOf(ChampagneGold, LuminousViolet)),
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("unlocked_prompt_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = "Unlocked",
                        tint = ChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VERIFIED AI GENERATION PROMPT",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoftChampagneGold
                    )
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Reward Verified",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFamily
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCopyPrompt,
                    enabled = isReadyToCopy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("copy_prompt_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy Text",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy Text",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onSharePrompt,
                    enabled = isReadyToCopy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("share_prompt_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share Text",
                        tint = SoftChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share Text",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (active) activeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .height(48.dp)
            .border(
                width = 1.dp,
                color = if (active) activeColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) activeColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CommentSectionHeader(
    commentCount: Int,
    commentDraft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Community & Curator Notes ($commentCount)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = commentDraft,
                onValueChange = onDraftChange,
                placeholder = { Text("Write a comment on this prompt…") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("comment_input_field"),
                maxLines = 3
            )
            IconButton(
                onClick = onSubmit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("post_comment_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Post comment",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun CommentItemCard(
    comment: ImageComment,
    isOwner: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateLabel = remember(comment.createdAt) {
        comment.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
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
                Column {
                    Text(
                        text = comment.userName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    if (isOwner) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete comment",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onReport,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = "Report comment",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (comment.adminReply.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChampagneGold.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .border(
                            width = 1.dp,
                            color = ChampagneGold.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "Curator Reply",
                                tint = ChampagneGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CURATOR REPLY",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftChampagneGold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = comment.adminReply,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Copies ONLY the clean prompt text to the Android clipboard (never adds promotional text or image URLs).
 */
fun copyPromptToClipboard(context: Context, promptText: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Lumiere AI Prompt", promptText.trim())
    clipboard?.setPrimaryClip(clip)
}

/**
 * Formats shared prompt text according to the exact specification without ever exposing external image URLs.
 */
fun buildRequiredShareText(
    imageTitle: String,
    completePrompt: String,
    playStoreUrl: String
): String {
    return buildString {
        append(imageTitle.trim())
        append("\n\n")
        append("Prompt:\n")
        append(completePrompt.trim())
        if (playStoreUrl.isNotBlank()) {
            append("\n\nGet more amazing image prompts:\n")
            append(playStoreUrl.trim())
        }
    }
}

fun shareTextViaNativeSheet(context: Context, shareBody: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    val chooser = Intent.createChooser(sendIntent, "Share AI Prompt")
    runCatching {
        context.startActivity(chooser)
    }
}
