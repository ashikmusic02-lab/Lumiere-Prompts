package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.data.NetworkAndCacheManager
import com.example.model.InspirationImage
import com.example.ui.theme.CardBackground
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.SubtleBorder
import java.util.Locale

/**
 * Press animation modifier that scales the component to [pressedScale] (default 0.96f) on press.
 */
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Opens a URL inside the app using a Chrome Custom Tab with a deep navy toolbar (#11164A).
 * Resolves a Custom Tabs browser package so Android never hijacks the link into an external native app
 * and never uses an in-app WebView.
 */
fun openInAppCustomTab(context: Context, rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return
    val normalized = if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return

    val toolbarColor = 0xFF11164A.toInt()
    val colorParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .setSecondaryToolbarColor(toolbarColor)
        .setNavigationBarColor(0xFF080B2A.toInt())
        .build()

    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorParams)
        .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .build()

    val browserPackage = resolveCustomTabsBrowserPackage(context, uri)
    if (!browserPackage.isNullOrBlank()) {
        customTabsIntent.intent.setPackage(browserPackage)
    }

    runCatching {
        customTabsIntent.launchUrl(context, uri)
    }.onFailure {
        runCatching {
            val fallbackTab = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorParams)
                .setShowTitle(true)
                .build()
            fallbackTab.launchUrl(context, uri)
        }
    }
}

private fun resolveCustomTabsBrowserPackage(context: Context, uri: Uri): String? {
    return runCatching {
        val pm = context.packageManager
        val activityIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val resolvedActivities = pm.queryIntentActivities(activityIntent, 0)
        val candidatePackages = mutableListOf<String>()

        for (info in resolvedActivities) {
            val pkg = info.activityInfo?.packageName ?: continue
            val serviceIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)
                .setPackage(pkg)
            if (pm.resolveService(serviceIntent, 0) != null) {
                candidatePackages.add(pkg)
            }
        }

        when {
            "com.android.chrome" in candidatePackages -> "com.android.chrome"
            candidatePackages.isNotEmpty() -> candidatePackages.first()
            else -> null
        }
    }.getOrNull()
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val baseColor = CardBackground
    val highlightColor = ElevatedSurface.copy(alpha = 0.85f)
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 400f, translateAnim - 400f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
fun ShimmerSkeletonCard(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.8f
) {
    val brush = rememberShimmerBrush()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(brush)
            .border(0.8.dp, SubtleBorder.copy(alpha = 0.7f), shape)
    )
}

@Composable
fun LumiereExternalImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    isThumbnail: Boolean = false
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { NetworkAndCacheManager.getImageLoader(context) }
    var hasFailed by remember(imageUrl) { mutableStateOf(false) }

    val imageRequest = remember(imageUrl, isThumbnail) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(150)
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .apply {
                if (isThumbnail) {
                    size(640, 800)
                }
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .listener(
                onError = { _, _ -> hasFailed = true }
            )
            .build()
    }

    if (imageUrl.isBlank() || hasFailed) {
        Box(
            modifier = modifier
                .background(CardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
        }
    } else {
        SubcomposeAsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rememberShimmerBrush())
                )
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            }
        )
    }
}

/**
 * Two-column gallery card refined per Premium UI Design Guideline:
 * - Aspect Ratio: 4:5 (0.8f) or image's natural aspect ratio gently bounded so images look larger and balanced
 * - Image Corner Radius: 20dp
 * - Image Border: very subtle (0.8dp, #252B69 at 65% alpha)
 * - No excessive shadow or glow on the image
 * - Small, clean view count badge with semi-transparent #080B2A background and White text
 */
@Composable
fun InspirationGalleryCard(
    image: InspirationImage,
    isLiked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeAspectRatio = remember(image.aspectRatio) {
        val raw = image.aspectRatio.toFloat()
        if (raw in 0.72f..0.92f) raw else 0.8f // 4:5 default aspect ratio
    }
    val formattedViews = remember(image.viewCount) {
        formatViewCountPlus(image.viewCount)
    }
    val cardShape = RoundedCornerShape(20.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(safeAspectRatio)
            .clip(cardShape)
            .border(width = 0.8.dp, color = SubtleBorder.copy(alpha = 0.65f), shape = cardShape)
            .pressScaleClickable(onClick = onClick)
            .testTag("gallery_card_${image.id}"),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LumiereExternalImage(
                imageUrl = image.imageUrl,
                contentDescription = image.title.ifBlank { "PromptLens" },
                contentScale = ContentScale.Crop,
                isThumbnail = true,
                modifier = Modifier.fillMaxSize()
            )

            // Compact, clean view count badge at bottom-left with semi-transparent #080B2A background
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xB8080B2A), // Semi-transparent #080B2A
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formattedViews,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

fun formatViewCountPlus(count: Long): String {
    val safe = count.coerceAtLeast(0L)
    return when {
        safe >= 1_000_000L -> String.format(Locale.US, "%.1fM+", safe / 1_000_000.0)
        safe >= 1_000L -> String.format(Locale.US, "%.1fk+", safe / 1_000.0)
        safe > 0L -> "$safe+"
        else -> "0"
    }
}

fun formatCompactCount(count: Long): String {
    val safe = count.coerceAtLeast(0L)
    return when {
        safe >= 1_000_000L -> String.format(Locale.US, "%.1fM", safe / 1_000_000.0)
        safe >= 1_000L -> String.format(Locale.US, "%.1fk", safe / 1_000.0)
        else -> safe.toString()
    }
}
