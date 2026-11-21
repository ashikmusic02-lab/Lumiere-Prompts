package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.NetworkAndCacheManager
import com.example.model.InspirationImage
import com.example.ui.theme.HeartLikeRed

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
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        .background(MaterialTheme.colorScheme.surfaceVariant),
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
 * Premium uniform 2-column staggered gallery card:
 * - Smooth 20.dp rounded corners with a subtle luminous gradient border rim
 * - Image fills the entire card preserving aspectRatio
 * - Cinematic bottom gradient overlay with title and frosted glass heart pill badge
 * - Liked state: solid red heart (#FF3B5C) with red count; Not liked: outlined heart in neutral color
 */
@Composable
fun InspirationGalleryCard(
    image: InspirationImage,
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeAspectRatio = image.aspectRatio.toFloat().coerceIn(0.62f, 1.35f)
    val neutralHeartTint = Color(0xFFF1F5F9)
    val cardShape = RoundedCornerShape(20.dp)

    val rimBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(safeAspectRatio)
            .clip(cardShape)
            .border(width = 1.dp, brush = rimBrush, shape = cardShape)
            .clickable(onClick = onClick)
            .testTag("gallery_card_${image.id}"),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LumiereExternalImage(
                imageUrl = image.imageUrl,
                contentDescription = image.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Cinematic bottom gradient scrim for title overlay & frosted heart count pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x99090718),
                                Color(0xE6090718)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = image.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isLiked) {
                            HeartLikeRed.copy(alpha = 0.22f)
                        } else {
                            Color.White.copy(alpha = 0.14f)
                        },
                        border = BorderStroke(
                            0.7.dp,
                            if (isLiked) HeartLikeRed.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.24f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isLiked) HeartLikeRed else neutralHeartTint,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = formatCompactCount(image.likeCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLiked) HeartLikeRed else neutralHeartTint,
                                fontWeight = if (isLiked) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000L -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000L -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
