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
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.NetworkAndCacheManager
import com.example.model.InspirationImage
import com.example.ui.theme.IndigoBorderBase
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.IndigoSurfaceDark
import com.example.ui.theme.IndigoSurfaceLight
import java.util.Locale

/**
 * Press animation modifier that scales the component to [pressedScale] (default 0.97f) on press.
 */
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
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
 * Opens a URL inside the app using a Chrome Custom Tab with a dark violet toolbar (#120C4A).
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

    val toolbarColor = 0xFF120C4A.toInt()
    val colorParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .setSecondaryToolbarColor(toolbarColor)
        .setNavigationBarColor(0xFF07041A.toInt())
        .build()

    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorParams)
        .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .build()

    // Prefer a browser package that supports Custom Tabs so the link always opens in a Custom Tab
    // inside our app's task rather than opening another installed native app.
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
    val baseColor = IndigoSurfaceDark
    val highlightColor = IndigoSurfaceLight.copy(alpha = 0.85f)
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 400f, translateAnim - 400f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
fun ShimmerSkeletonCard(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.78f
) {
    val brush = rememberShimmerBrush()
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(brush)
            .border(1.5.dp, IndigoSoftBorder, shape)
    )
}

@Composable
fun LumiereExternalImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { NetworkAndCacheManager.getImageLoader(context) }
    var hasFailed by remember(imageUrl) { mutableStateOf(false) }

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(120)
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
                .background(IndigoSurfaceDark),
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
                        .background(IndigoSurfaceDark),
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
 * Uniform 2-column staggered gallery card:
 * - Image filling a 28.dp rounded card with a 1.5.dp soft indigo border
 * - Small translucent pill at bottom-left with an eye icon and view count (format 1.2k+)
 * - NO title or text on the image
 */
@Composable
fun InspirationGalleryCard(
    image: InspirationImage,
    isLiked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeAspectRatio = image.aspectRatio.toFloat().coerceIn(0.65f, 1.32f)
    val cardShape = RoundedCornerShape(28.dp)
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            IndigoBorderBase.copy(alpha = 0.85f),
            IndigoBorderBase.copy(alpha = 0.55f)
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(safeAspectRatio)
            .clip(cardShape)
            .border(width = 1.5.dp, brush = borderBrush, shape = cardShape)
            .pressScaleClickable(onClick = onClick)
            .testTag("gallery_card_${image.id}"),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = IndigoSurfaceDark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LumiereExternalImage(
                imageUrl = image.imageUrl,
                contentDescription = image.title.ifBlank { "PromptLens" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle bottom vignette so the bottom-left eye count pill is always legible
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x8807041A)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x9907041A),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = formatViewCountPlus(image.viewCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.SemiBold
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
