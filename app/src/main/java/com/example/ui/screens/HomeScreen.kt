package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.LumiereBannerAdSlot
import com.example.model.AdvertisementSettings
import com.example.model.AppConfigSettings
import com.example.model.CategoryItem
import com.example.model.ImageCollectionSummary
import com.example.model.InspirationImage
import com.example.model.UiState
import com.example.ui.components.CategoryShowcaseCard
import com.example.ui.components.CollectionShowcaseCard
import com.example.ui.components.FeaturedHeroCard
import com.example.ui.components.InspirationGalleryCard
import com.example.ui.components.ShimmerPlaceholderBox
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.LuminousViolet
import com.example.ui.theme.SoftChampagneGold

@Composable
fun HomeScreen(
    imagesUiState: UiState<List<InspirationImage>>,
    featuredImagesOverride: List<InspirationImage> = emptyList(),
    trendingImagesOverride: List<InspirationImage> = emptyList(),
    recommendedImagesOverride: List<InspirationImage> = emptyList(),
    hasMoreImages: Boolean = false,
    isLoadingMoreImages: Boolean = false,
    onLoadMoreImages: () -> Unit = {},
    onRefreshCatalog: () -> Unit = {},
    categories: List<CategoryItem>,
    collections: List<ImageCollectionSummary>,
    recentlyViewedIds: List<String>,
    likedIds: Set<String>,
    favoritedIds: Set<String>,
    unlockedIds: Set<String>,
    appConfigSettings: AppConfigSettings,
    advertisementSettings: AdvertisementSettings,
    onOpenImageDetail: (InspirationImage) -> Unit,
    onSelectCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleLike: (InspirationImage) -> Unit,
    onToggleFavorite: (InspirationImage) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columnsCount = if (maxWidth >= 700.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Welcome Hero Section & Search Bar
            item {
                WelcomeHeroSection(
                    headline = appConfigSettings.welcomeHeadline,
                    subheadline = appConfigSettings.welcomeSubheadline,
                    onSearchClick = onOpenSearch
                )
            }

            when (imagesUiState) {
                is UiState.Loading -> {
                    item {
                        HomeLoadingSkeleton()
                    }
                }
                is UiState.Error -> {
                    item {
                        ContentPreparedEmptyCard(
                            title = "Content is being prepared",
                            subtitle = imagesUiState.message,
                            actionLabel = "Retry Connection",
                            onAction = onRefreshCatalog
                        )
                    }
                }
                is UiState.Success -> {
                    val allImages = imagesUiState.data
                    if (allImages.isEmpty()) {
                        item {
                            ContentPreparedEmptyCard(
                                title = "Content is being prepared",
                                subtitle = "Our curators are preparing photography inspirations and AI prompts in Firestore. Connect your google-services.json or publish items from your Admin Panel to populate the gallery.",
                                actionLabel = "Refresh Gallery",
                                onAction = onRefreshCatalog
                            )
                        }
                    } else {
                        val featuredImages = featuredImagesOverride.ifEmpty {
                            allImages.filter { it.featured }.ifEmpty { allImages.take(6) }
                        }
                        val trendingImages = trendingImagesOverride.ifEmpty {
                            allImages.filter { it.trending }
                                .sortedByDescending { it.viewCount + it.likeCount * 3 }
                                .ifEmpty { allImages.take(8) }
                        }
                        val recommendedImages = recommendedImagesOverride.ifEmpty {
                            allImages.filter { it.recommended }.ifEmpty { allImages.take(8) }
                        }
                        val latestImages = allImages.sortedWith(
                            compareBy<InspirationImage> { it.displayOrder }
                                .thenByDescending { it.createdAt?.seconds ?: 0L }
                        )
                        val recentlyViewedImages = recentlyViewedIds.mapNotNull { id ->
                            allImages.firstOrNull { it.id == id }
                        }

                        // 2. Featured Images Carousel
                        if (featuredImages.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Featured Inspirations",
                                    subtitle = "Hand-picked editorial compositions & master prompts",
                                    icon = Icons.Outlined.AutoAwesome,
                                    onActionClick = onOpenSearch
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(featuredImages, key = { "featured_${it.id}" }) { image ->
                                        FeaturedHeroCard(
                                            image = image,
                                            isUnlocked = unlockedIds.contains(image.id),
                                            onClick = { onOpenImageDetail(image) }
                                        )
                                    }
                                }
                            }
                        }

                        // Home Banner Ad Slot (Collapses to 0dp if unavailable)
                        item {
                            LumiereBannerAdSlot(
                                settings = advertisementSettings,
                                enabledForPlacement = advertisementSettings.homeBannerEnabled,
                                modifier = Modifier.padding(horizontal = 20.dp),
                                testTagName = "home_banner_ad"
                            )
                        }

                        // 3. Popular Categories
                        if (categories.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Popular Categories",
                                    subtitle = "Explore curated photography genres",
                                    onActionClick = onOpenSearch
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(categories, key = { "cat_${it.id}" }) { category ->
                                        CategoryShowcaseCard(
                                            category = category,
                                            onClick = { onSelectCategory(category.name) }
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Trending Images
                        if (trendingImages.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Trending Now",
                                    subtitle = "Most viewed & unlocked prompts this week",
                                    icon = Icons.Outlined.LocalFireDepartment,
                                    onActionClick = onOpenSearch
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(trendingImages, key = { "trending_${it.id}" }) { image ->
                                        Box(modifier = Modifier.width(210.dp)) {
                                            InspirationGalleryCard(
                                                image = image,
                                                isLiked = likedIds.contains(image.id),
                                                isFavorited = favoritedIds.contains(image.id),
                                                isUnlocked = unlockedIds.contains(image.id),
                                                onClick = { onOpenImageDetail(image) },
                                                onToggleLike = { onToggleLike(image) },
                                                onToggleFavorite = { onToggleFavorite(image) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Popular Image Collections
                        if (collections.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Popular Collections",
                                    subtitle = "Signature lighting & aesthetic series"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(collections, key = { "col_${it.name}" }) { collection ->
                                        CollectionShowcaseCard(
                                            collection = collection,
                                            onClick = {
                                                collection.images.firstOrNull()?.let(onOpenImageDetail)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 6. Recently Viewed Images
                        if (recentlyViewedImages.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Recently Viewed",
                                    subtitle = "Continue exploring your recent inspirations",
                                    icon = Icons.Outlined.History
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(recentlyViewedImages, key = { "recent_${it.id}" }) { image ->
                                        Box(modifier = Modifier.width(185.dp)) {
                                            InspirationGalleryCard(
                                                image = image,
                                                isLiked = likedIds.contains(image.id),
                                                isFavorited = favoritedIds.contains(image.id),
                                                isUnlocked = unlockedIds.contains(image.id),
                                                onClick = { onOpenImageDetail(image) },
                                                onToggleLike = { onToggleLike(image) },
                                                onToggleFavorite = { onToggleFavorite(image) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Recommended Images (Responsive Grid Rows)
                        if (recommendedImages.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Recommended For You",
                                    subtitle = "Curated lighting setups & high-detail prompts",
                                    icon = Icons.Outlined.AutoAwesome,
                                    onActionClick = onOpenSearch
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ResponsiveGalleryGrid(
                                    images = recommendedImages,
                                    columnsCount = columnsCount,
                                    likedIds = likedIds,
                                    favoritedIds = favoritedIds,
                                    unlockedIds = unlockedIds,
                                    onOpenImageDetail = onOpenImageDetail,
                                    onToggleLike = onToggleLike,
                                    onToggleFavorite = onToggleFavorite
                                )
                            }
                        }

                        // 8. Latest Images (Paginated)
                        item {
                            SectionHeader(
                                title = "Latest Releases",
                                subtitle = "Freshly added photography inspirations",
                                onActionClick = onOpenSearch
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ResponsiveGalleryGrid(
                                images = latestImages,
                                columnsCount = columnsCount,
                                likedIds = likedIds,
                                favoritedIds = favoritedIds,
                                unlockedIds = unlockedIds,
                                onOpenImageDetail = onOpenImageDetail,
                                onToggleLike = onToggleLike,
                                onToggleFavorite = onToggleFavorite
                            )

                            if (hasMoreImages) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = onLoadMoreImages,
                                        enabled = !isLoadingMoreImages,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.testTag("home_load_more_button")
                                    ) {
                                        if (isLoadingMoreImages) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text("Load More Inspirations")
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
private fun ContentPreparedEmptyCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("content_prepared_empty_state"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = ChampagneGold.copy(alpha = 0.16f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(50)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun WelcomeHeroSection(
    headline: String,
    subheadline: String,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(215.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner_1794954909272),
                    contentDescription = "Lumiere Editorial Studio Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ChampagneGold.copy(alpha = 0.2f),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = ChampagneGold.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(50)
                        )
                    ) {
                        Text(
                            text = "LUMIERE PROMPT VAULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftChampagneGold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subheadline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Search Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("home_search_bar")
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onSearchClick),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search inspirations",
                    tint = LuminousViolet,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Search portraits, cinematic lighting, luxury prompts…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "See all",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ResponsiveGalleryGrid(
    images: List<InspirationImage>,
    columnsCount: Int,
    likedIds: Set<String>,
    favoritedIds: Set<String>,
    unlockedIds: Set<String>,
    onOpenImageDetail: (InspirationImage) -> Unit,
    onToggleLike: (InspirationImage) -> Unit,
    onToggleFavorite: (InspirationImage) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(images, columnsCount) {
        images.chunked(columnsCount)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                rowItems.forEach { image ->
                    Box(modifier = Modifier.weight(1f)) {
                        InspirationGalleryCard(
                            image = image,
                            isLiked = likedIds.contains(image.id),
                            isFavorited = favoritedIds.contains(image.id),
                            isUnlocked = unlockedIds.contains(image.id),
                            onClick = { onOpenImageDetail(image) },
                            onToggleLike = { onToggleLike(image) },
                            onToggleFavorite = { onToggleFavorite(image) }
                        )
                    }
                }
                repeat(columnsCount - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShimmerPlaceholderBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ShimmerPlaceholderBox(
                modifier = Modifier
                    .weight(1f)
                    .height(210.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            ShimmerPlaceholderBox(
                modifier = Modifier
                    .weight(1f)
                    .height(210.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
    }
}
