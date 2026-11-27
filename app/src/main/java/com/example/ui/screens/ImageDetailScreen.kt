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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.ActionBookmarkContainer
import com.example.ui.theme.ActionBookmarkIcon
import com.example.ui.theme.ActionCommentContainer
import com.example.ui.theme.ActionLikeContainer
import com.example.ui.theme.ActionLikeIcon
import com.example.ui.theme.ActionShareContainer
import com.example.ui.theme.CardBackground
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HeartLikeRed
import com.example.ui.theme.InactiveChipText
import com.example.ui.theme.IndigoInnerBox
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.IndigoSurfaceDark
import com.example.ui.theme.IndigoSurfaceTop
import com.example.ui.theme.MainBackground
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PromptAccentGradientBrush
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryBlue
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextLavenderGray
import com.example.ui.theme.TextLavenderMuted
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

@OptIn(ExperimentalMaterial3Api::class)
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
    commentLikeCounts: Map<String, Int> = emptyMap(),
    likedCommentIds: Set<String> = emptySet(),
    onToggleCommentLike: (ImageComment) -> Unit = {},
    onCommentsSheetVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val commentsListState = rememberLazyListState()

    var promptRevealed by rememberSaveable(image.id) { mutableStateOf(false) }
    var isWaitingForAdToFinish by remember(image.id) { mutableStateOf(false) }
    var showCommentsSheet by rememberSaveable(image.id) { mutableStateOf(false) }
    var commentDraft by rememberSaveable(image.id) { mutableStateOf("") }
    var isCommentInputFocused by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<ImageComment?>(null) }
    var commentToReport by remember { mutableStateOf<ImageComment?>(null) }
    var reportReasonText by remember { mutableStateOf("") }

    val copiedMsg = stringResource(R.string.copied_message)
    val appName = stringResource(R.string.app_name)
    val defaultReportReason = stringResource(R.string.report_default_reason)

    // Springy like-button pop animation when isLiked transitions to true
    val likePopScale = remember { Animatable(1f) }
    var previousLiked by remember(image.id) { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (isLiked && !previousLiked) {
            likePopScale.snapTo(0.82f)
            likePopScale.animateTo(
                targetValue = 1.26f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            likePopScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        previousLiked = isLiked
    }

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
            isCommentInputActive = isCommentInputFocused || showCommentsSheet
        )
        onBack()
    }

    BackHandler(onBack = handleExit)

    DisposableEffect(image.id) {
        onDispose {
            onCommentsSheetVisibilityChanged(false)
            onClearPromptMemory()
        }
    }

    LaunchedEffect(showCommentsSheet, image.id) {
        onCommentsSheetVisibilityChanged(showCommentsSheet && appConfigSettings.allowComments)
    }

    val hasValidCategory = remember(image.categoryId, image.categoryName) {
        isRealCategory(image.categoryId) && isRealCategory(image.categoryName)
    }

    val enabledAiTools = remember(aiTools) {
        aiTools.filter { it.enabled && it.name.isNotBlank() && it.url.isNotBlank() }
            .sortedBy { it.sortOrder }
    }

    val commentCount = maxOf(image.commentCount, comments.size.toLong())

    // Delete Comment Confirmation Dialog
    commentToDelete?.let { targetComment ->
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            containerColor = IndigoSurfaceTop,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = stringResource(R.string.delete_comment_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteComment(targetComment)
                        commentToDelete = null
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_comment_button")
                ) {
                    Text(stringResource(R.string.delete_comment))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { commentToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_comment_button")
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = TextLavenderGray
                    )
                }
            }
        )
    }

    // Report Comment Dialog
    commentToReport?.let { targetComment ->
        AlertDialog(
            onDismissRequest = { commentToReport = null },
            containerColor = IndigoSurfaceTop,
            shape = RoundedCornerShape(24.dp),
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
                    label = {
                        Text(
                            text = stringResource(R.string.report_reason_label),
                            color = TextLavenderGray
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = IndigoSoftBorder,
                        focusedContainerColor = IndigoInnerBox,
                        unfocusedContainerColor = IndigoInnerBox,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_reason_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = reportReasonText.trim().ifBlank { defaultReportReason }
                        onReportComment(targetComment, reason)
                        commentToReport = null
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("submit_report_comment_button")
                ) {
                    Text(stringResource(R.string.submit_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { commentToReport = null },
                    modifier = Modifier.testTag("cancel_report_comment_button")
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = TextLavenderGray
                    )
                }
            }
        )
    }

    // ModalBottomSheet for Comments (YouTube-style fixed ~75% height, skipPartiallyExpanded = true, pinned input bar, keyboard-safe)
    if (showCommentsSheet && appConfigSettings.allowComments) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                isCommentInputFocused = false
                showCommentsSheet = false
            },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = IndigoSurfaceTop,
            contentColor = TextPureWhite,
            tonalElevation = 12.dp,
            scrimColor = Color(0xAA040210),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .navigationBarsPadding()
                    .imePadding()
                    .testTag("comments_bottom_sheet")
            ) {
                // Sheet Header: Title "Comments (N)" + Close button (stays on top)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.comments_header, commentCount.toInt()),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            color = TextPureWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.comment_respect_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLavenderMuted
                        )
                    }
                    IconButton(
                        onClick = {
                            isCommentInputFocused = false
                            showCommentsSheet = false
                        },
                        modifier = Modifier.testTag("close_comments_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.back_action),
                            tint = TextLavenderGray
                        )
                    }
                }

                HorizontalDivider(
                    color = IndigoSoftBorder,
                    thickness = 0.8.dp
                )

                // Middle area (weight(1f)): Scrollable List of Comments or Centered Empty State
                if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(AccentViolet.copy(alpha = 0.16f))
                                    .border(1.dp, IndigoSoftBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = null,
                                    tint = TextLavenderGray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.no_comments_yet),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextLavenderGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("empty_comments_text")
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = commentsListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("comments_sheet_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(
                            items = comments,
                            key = { comment -> comment.id }
                        ) { comment ->
                            val isOwn = !currentUserId.isNullOrBlank() && comment.userId == currentUserId
                            val isCommentLiked = likedCommentIds.contains(comment.id)
                            val likeCount = commentLikeCounts[comment.id] ?: 0
                            CommentRowItem(
                                comment = comment,
                                isOwnComment = isOwn,
                                isLiked = isCommentLiked,
                                likeCount = likeCount,
                                onToggleLike = { onToggleCommentLike(comment) },
                                onRequestDelete = { commentToDelete = comment },
                                onRequestReport = {
                                    reportReasonText = defaultReportReason
                                    commentToReport = comment
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = IndigoSoftBorder,
                    thickness = 0.8.dp
                )

                // Pinned Input Row at the bottom of the sheet (moves directly above the keyboard)
                Surface(
                    color = IndigoSurfaceDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isRealUser) {
                        // Guest user: show "Sign in to comment" button; tapping it starts Google sign-in
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(PromptAccentGradientBrush, RoundedCornerShape(50))
                                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(50))
                                    .pressScaleClickable {
                                        onRequireSignIn {}
                                    }
                                    .testTag("sign_in_to_comment_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.sign_in_to_comment),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
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
                                            coroutineScope.launch {
                                                commentsListState.animateScrollToItem(0)
                                            }
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
                    }
                }
            }
        }
    }

    val safeAspectRatio = remember(image.aspectRatio) {
        val raw = image.aspectRatio.toFloat()
        if (raw in 0.65f..1.35f) raw else 0.8f
    }
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
        // 1. Slim floating top bar pinned at the top (20dp rounded, 1dp subtle border) with one round back button.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SecondaryBackground,
            border = BorderStroke(1.dp, SubtleBorder),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardBackground, CircleShape)
                        .border(1.dp, SubtleBorder, CircleShape)
                        .pressScaleClickable(onClick = handleExit)
                        .testTag("detail_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_action),
                        tint = PrimaryText,
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
            contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. Image Preview: ~93% of screen width, displayed according to aspect ratio, 20dp corner radius, no overlay
            item {
                val imageShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.93f)
                            .heightIn(min = 260.dp, max = 460.dp)
                            .aspectRatio(safeAspectRatio)
                            .shadow(
                                elevation = 6.dp,
                                shape = imageShape,
                                ambientColor = Color.Black.copy(alpha = 0.25f),
                                spotColor = Color.Black.copy(alpha = 0.25f)
                            )
                            .clip(imageShape)
                            .background(CardBackground, imageShape)
                            .border(1.dp, SubtleBorder, imageShape)
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

            // 3. Details Card: Background #11164A, Border #252B69, Corner Radius 24dp, Padding 18dp
            item {
                val mainCardShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = mainCardShape,
                            ambientColor = Color.Black.copy(alpha = 0.2f),
                            spotColor = Color.Black.copy(alpha = 0.2f)
                        )
                        .clip(mainCardShape)
                        .background(CardBackground, mainCardShape)
                        .border(1.dp, SubtleBorder, mainCardShape)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .padding(18.dp)
                        .testTag("prompt_display_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Image Title: 22sp (21–23sp), SemiBold, #F5F6FF
                        if (image.title.isNotBlank()) {
                            Text(
                                text = image.title,
                                fontSize = 22.sp,
                                lineHeight = 29.sp,
                                color = PrimaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Category Pill ONLY when the post has a real category (hidden for blank/"uncategorized"/"অন্যান্য")
                        if (hasValidCategory) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = ElevatedSurface,
                                border = BorderStroke(1.dp, SubtleBorder)
                            ) {
                                Text(
                                    text = image.categoryName.trim(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PrimaryText,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Action Buttons Row: Like (Soft Pink), Comment (Aqua Cyan), Share (Blue), Bookmark (Lavender)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Like (Soft Pink Accent)
                            RoundActionIconButton(
                                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.like_action),
                                containerColor = if (isLiked) HeartLikeRed.copy(alpha = 0.22f) else ActionLikeContainer,
                                iconTint = if (isLiked) HeartLikeRed else ActionLikeIcon,
                                borderColor = if (isLiked) HeartLikeRed.copy(alpha = 0.65f) else ActionLikeIcon.copy(alpha = 0.32f),
                                countText = image.likeCount.takeIf { it > 0L }?.let(::formatCompactCount),
                                iconScale = likePopScale.value,
                                testTag = "detail_like_button",
                                onClick = onToggleLike
                            )

                            // 2. Comment (Aqua Cyan Accent) -> opens ModalBottomSheet
                            RoundActionIconButton(
                                icon = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = stringResource(R.string.comments_header, commentCount.toInt()),
                                containerColor = ActionCommentContainer,
                                iconTint = AccentCyan,
                                borderColor = AccentCyan.copy(alpha = 0.32f),
                                countText = commentCount.takeIf { it > 0L }?.let(::formatCompactCount),
                                testTag = "detail_comments_action_button",
                                onClick = {
                                    if (appConfigSettings.allowComments) {
                                        showCommentsSheet = true
                                    }
                                }
                            )

                            // 3. Share (Blue Accent)
                            RoundActionIconButton(
                                icon = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share_action),
                                containerColor = ActionShareContainer,
                                iconTint = SecondaryBlue,
                                borderColor = SecondaryBlue.copy(alpha = 0.32f),
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

                            // 4. Bookmark (Lavender Accent)
                            RoundActionIconButton(
                                icon = if (isFavorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(R.string.save_action),
                                containerColor = if (isFavorited) PrimaryPurple.copy(alpha = 0.25f) else ActionBookmarkContainer,
                                iconTint = if (isFavorited) PrimaryText else ActionBookmarkIcon,
                                borderColor = if (isFavorited) PrimaryPurple.copy(alpha = 0.68f) else ActionBookmarkIcon.copy(alpha = 0.34f),
                                countText = null,
                                testTag = "detail_save_button",
                                onClick = onToggleFavorite
                            )
                        }

                        // View Comments Row: Background #080B2A, Text #B8BCE0, Comment Icon #21D6E8, Radius 14dp, Height 44dp (42–46dp)
                        if (appConfigSettings.allowComments) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MainBackground, RoundedCornerShape(14.dp))
                                    .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
                                    .pressScaleClickable { showCommentsSheet = true }
                                    .padding(horizontal = 14.dp)
                                    .testTag("comments_collapse_header"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Comment,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.view_comments_count, commentCount.toInt()),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.5.sp
                                        ),
                                        color = InactiveChipText
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = InactiveChipText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Get Prompt Button: Full Width, Height 56dp (54–58dp), Gradient #713CFF → #258BFF, White 17sp SemiBold text, Sparkles icon, Radius 28dp, light purple-blue shadow
                        if (!isPromptUnlockedOrRevealed) {
                            val buttonShape = RoundedCornerShape(28.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = buttonShape,
                                        ambientColor = PrimaryPurple.copy(alpha = 0.25f),
                                        spotColor = SecondaryBlue.copy(alpha = 0.25f)
                                    )
                                    .clip(buttonShape)
                                    .background(PromptAccentGradientBrush, buttonShape)
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
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.get_prompt_action),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 6. AnimatedVisibility for the prompt reveal
                        AnimatedVisibility(
                            visible = isPromptUnlockedOrRevealed,
                            enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                            exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
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

                                // 7. Below the prompt box, row of rounded AI tool chips
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
            }

            // 8. Banner Ad in its own rounded 20dp container (HIDDEN while comments bottom sheet or keyboard is open)
            if (!showCommentsSheet && !isCommentInputFocused && hasBannerSlotConfigured) {
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
    iconScale: Float = 1f,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(25.dp)
    Box(
        modifier = Modifier
            .height(50.dp)
            .width(72.dp)
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
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            if (!countText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = iconTint,
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
    isLiked: Boolean,
    likeCount: Int,
    onToggleLike: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestReport: () -> Unit
) {
    val dateString = remember(comment.createdAt) {
        val seconds = comment.createdAt?.seconds
        if (seconds != null && seconds > 0L) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(seconds * 1000L))
        } else ""
    }

    val avatarInitial = remember(comment.userName) {
        comment.userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }

    var menuExpanded by remember { mutableStateOf(false) }

    // Quick heart scale animation when liked
    val heartScale = remember { Animatable(1f) }
    var prevLiked by remember(comment.id) { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (isLiked != prevLiked) {
            heartScale.snapTo(0.8f)
            heartScale.animateTo(
                targetValue = 1.24f,
                animationSpec = tween(durationMillis = 110)
            )
            heartScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 120)
            )
            prevLiked = isLiked
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("comment_item_${comment.id}"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Row 1: small circular avatar with first letter, bold user name, small muted date, and 3-dot icon (Delete for own, Report for others)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.26f), CircleShape)
                    .border(1.dp, AccentViolet.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarInitial,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Text(
                text = comment.userName,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (dateString.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = TextLavenderMuted,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("comment_more_menu_${comment.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = if (isOwnComment) {
                            stringResource(R.string.delete_comment)
                        } else {
                            stringResource(R.string.report_comment)
                        },
                        tint = TextLavenderMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = IndigoSurfaceDark
                ) {
                    if (isOwnComment) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.delete_comment),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRequestDelete()
                            },
                            modifier = Modifier.testTag("delete_comment_${comment.id}")
                        )
                    } else {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.report_comment),
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = TextLavenderGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRequestReport()
                            },
                            modifier = Modifier.testTag("report_comment_${comment.id}")
                        )
                    }
                }
            }
        }

        // Row 2: comment text, normal weight, comfortable line height
        Text(
            text = comment.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
                fontSize = 14.sp
            ),
            color = Color.White.copy(alpha = 0.93f),
            modifier = Modifier.padding(start = 37.dp, end = 4.dp)
        )

        // Row 3 (small, muted): heart icon button with like count next to it
        Row(
            modifier = Modifier
                .padding(start = 33.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleLike)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .testTag("like_comment_${comment.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.like_action),
                    tint = if (isLiked) PrimaryPurple else TextLavenderMuted,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                        }
                )
                Text(
                    text = likeCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = if (isLiked) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isLiked) PrimaryPurple else TextLavenderMuted
                )
            }
        }

        // Admin reply: indented, slightly highlighted block with small label and verified check icon
        if (comment.adminReply.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 37.dp, top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentViolet.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .border(0.8.dp, AccentViolet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reply_from_promptlens),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = TextLavenderGray
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentViolet,
                        modifier = Modifier.size(13.5.dp)
                    )
                }
                Text(
                    text = comment.adminReply,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                        fontSize = 13.sp
                    ),
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }

        HorizontalDivider(
            color = IndigoSoftBorder.copy(alpha = 0.42f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
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
