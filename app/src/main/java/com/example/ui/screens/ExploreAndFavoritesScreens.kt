package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CategoryItem
import com.example.model.InspirationImage
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SoftChampagneGold

enum class ExploreSortOrder(val label: String) {
    TRENDING("Trending"),
    LATEST("Latest"),
    MOST_LIKED("Most Liked"),
    MOST_UNLOCKED("Most Unlocked")
}

@Composable
fun ExploreScreen(
    allImages: List<InspirationImage>,
    categories: List<CategoryItem>,
    initialSelectedCategory: String?,
    hasMoreImages: Boolean = false,
    isLoadingMoreImages: Boolean = false,
    onLoadMoreImages: () -> Unit = {},
    likedIds: Set<String>,
    favoritedIds: Set<String>,
    unlockedIds: Set<String>,
    onClearInitialCategory: () -> Unit,
    onOpenImageDetail: (InspirationImage) -> Unit,
    onToggleLike: (InspirationImage) -> Unit,
    onToggleFavorite: (InspirationImage) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable(initialSelectedCategory) {
        mutableStateOf(initialSelectedCategory ?: "All")
    }
    var sortOrder by rememberSaveable { mutableStateOf(ExploreSortOrder.TRENDING) }

    val filteredImages = remember(allImages, searchQuery, selectedCategory, sortOrder) {
        val query = searchQuery.trim().lowercase()
        val matched = allImages.filter { img ->
            val categoryMatches = selectedCategory == "All" ||
                img.categoryName.equals(selectedCategory, ignoreCase = true) ||
                img.categoryId.equals(selectedCategory, ignoreCase = true)

            val queryMatches = query.isEmpty() ||
                img.title.lowercase().contains(query) ||
                img.shortName.lowercase().contains(query) ||
                img.categoryName.lowercase().contains(query) ||
                img.keywords.lowercase().contains(query) ||
                img.description.lowercase().contains(query) ||
                img.collectionName.lowercase().contains(query)

            categoryMatches && queryMatches
        }

        when (sortOrder) {
            ExploreSortOrder.TRENDING -> matched.sortedByDescending { it.viewCount + it.likeCount * 3 }
            ExploreSortOrder.LATEST -> matched.sortedWith(
                compareBy<InspirationImage> { it.displayOrder }
                    .thenByDescending { it.createdAt?.seconds ?: 0L }
            )
            ExploreSortOrder.MOST_LIKED -> matched.sortedByDescending { it.likeCount }
            ExploreSortOrder.MOST_UNLOCKED -> matched.sortedByDescending { it.unlockCount }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columnsCount = if (maxWidth >= 700.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("explore_screen_list"),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Explore Inspirations",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Search by image title, category, or lighting keywords",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search portraits, lenses, or styles…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explore_search_input")
                    )
                }
            }

            // Category Filter Chips
            if (categories.isNotEmpty()) {
                item {
                    val chipLabels = remember(categories) {
                        listOf("All") + categories.map { it.name }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chipLabels, key = { "chip_$it" }) { label ->
                            val isSelected = selectedCategory.equals(label, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategory = label
                                    onClearInitialCategory()
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ChampagneGold,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Sort Order Row
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExploreSortOrder.entries, key = { it.name }) { option ->
                        val active = sortOrder == option
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { sortOrder = option }
                                .border(
                                    width = 1.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            if (filteredImages.isEmpty()) {
                item {
                    EmptyGalleryStateCard(
                        title = if (allImages.isEmpty()) "Content is being prepared" else "No matching inspirations found",
                        subtitle = if (allImages.isEmpty()) {
                            "Photography inspirations and AI prompts will appear here as soon as they are published in Firestore."
                        } else {
                            "Try clearing your search filter or exploring another photography category."
                        },
                        actionLabel = "Reset Filters",
                        onAction = {
                            searchQuery = ""
                            selectedCategory = "All"
                        }
                    )
                }
            } else {
                item {
                    ResponsiveGalleryGrid(
                        images = filteredImages,
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
                                modifier = Modifier.testTag("explore_load_more_button")
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

@Composable
fun FavoritesScreen(
    allImages: List<InspirationImage>,
    unlockedPromptsMap: Map<String, String>,
    likedIds: Set<String>,
    favoritedIds: Set<String>,
    unlockedIds: Set<String>,
    onOpenImageDetail: (InspirationImage) -> Unit,
    onToggleLike: (InspirationImage) -> Unit,
    onToggleFavorite: (InspirationImage) -> Unit,
    onPromptCopied: () -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    val favoriteImages = remember(allImages, favoritedIds) {
        allImages.filter { favoritedIds.contains(it.id) }
    }
    val unlockedImages = remember(allImages, unlockedIds) {
        allImages.filter { unlockedIds.contains(it.id) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columnsCount = if (maxWidth >= 700.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("favorites_screen_list"),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Your Personal Vault",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Saved photography inspirations and verified unlocked prompts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Favorites (${favoriteImages.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Unlocked Prompts (${unlockedImages.size})") }
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                if (favoriteImages.isEmpty()) {
                    item {
                        EmptyGalleryStateCard(
                            title = "No saved favorites yet",
                            subtitle = "Tap the bookmark icon on any photography inspiration to save it to your collection.",
                            actionLabel = "Explore Inspirations",
                            onAction = onExploreClick
                        )
                    }
                } else {
                    item {
                        ResponsiveGalleryGrid(
                            images = favoriteImages,
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
            } else {
                if (unlockedImages.isEmpty()) {
                    item {
                        EmptyGalleryStateCard(
                            title = "No unlocked prompts yet",
                            subtitle = "Open any photography inspiration and tap Generate Text to unlock its complete AI prompt.",
                            actionLabel = "Browse Featured Prompts",
                            onAction = onExploreClick
                        )
                    }
                } else {
                    items(unlockedImages, key = { "unlocked_item_${it.id}" }) { image ->
                        val unlockedPrompt = unlockedPromptsMap[image.id]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clickable { onOpenImageDetail(image) },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = image.categoryName.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SoftChampagneGold
                                        )
                                        Text(
                                            text = image.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!unlockedPrompt.isNullOrBlank()) {
                                        IconButton(
                                            onClick = {
                                                copyPromptToClipboard(context, unlockedPrompt)
                                                onPromptCopied()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copy Prompt",
                                                tint = ChampagneGold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = unlockedPrompt ?: "Tap to view unlocked prompt in detail screen",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
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
fun EmptyGalleryStateCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
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
                        imageVector = Icons.Outlined.ImageSearch,
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
