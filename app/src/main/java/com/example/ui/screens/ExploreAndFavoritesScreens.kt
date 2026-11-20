package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CategoryItem
import com.example.model.InspirationImage
import com.example.ui.components.InspirationGalleryCard

enum class CategorySortOption {
    LATEST,
    TRENDING,
    MOST_LIKED
}

@Composable
fun CategoriesScreen(
    allImages: List<InspirationImage>,
    categories: List<CategoryItem>,
    likedIds: Set<String>,
    onImageClick: (InspirationImage) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOption by rememberSaveable { mutableStateOf(CategorySortOption.LATEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    val publishedCategories = remember(categories) {
        categories.filter { it.status == "published" }.sortedBy { it.sortOrder }
    }

    val selectedCategory = remember(publishedCategories, selectedCategoryId) {
        publishedCategories.firstOrNull { it.id == selectedCategoryId }
    }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategoryId = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("categories_screen_container")
    ) {
        if (selectedCategory == null) {
            // Categories List/Grid (name only, no image)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.categories_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (publishedCategories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.empty_categories),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("categories_name_grid")
                ) {
                    items(publishedCategories, key = { it.id }) { category ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedCategoryId = category.id }
                                .testTag("category_card_${category.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Selected Category Detail Grid with Back button and Sort menu (Latest, Trending, Most Liked)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { selectedCategoryId = null },
                        modifier = Modifier.testTag("category_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = selectedCategory.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("category_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort_label),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = when (sortOption) {
                                CategorySortOption.LATEST -> stringResource(R.string.sort_latest)
                                CategorySortOption.TRENDING -> stringResource(R.string.sort_trending)
                                CategorySortOption.MOST_LIKED -> stringResource(R.string.sort_most_liked)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_latest)) },
                            onClick = {
                                sortOption = CategorySortOption.LATEST
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_option_latest")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_trending)) },
                            onClick = {
                                sortOption = CategorySortOption.TRENDING
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_option_trending")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_most_liked)) },
                            onClick = {
                                sortOption = CategorySortOption.MOST_LIKED
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_option_most_liked")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val categoryImages by remember(allImages, selectedCategory.id, sortOption) {
                derivedStateOf {
                    val base = allImages.filter {
                        it.status == "published" && it.categoryId == selectedCategory.id
                    }
                    when (sortOption) {
                        CategorySortOption.LATEST -> base.sortedByDescending { it.createdAt?.seconds ?: 0L }
                        CategorySortOption.TRENDING -> base.sortedWith(
                            compareByDescending<InspirationImage> { it.trending }
                                .thenByDescending { it.viewCount + (it.likeCount * 3L) }
                        )
                        CategorySortOption.MOST_LIKED -> base.sortedByDescending { it.likeCount }
                    }
                }
            }

            if (categoryImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ImageSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.empty_inspirations),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("category_images_grid")
                ) {
                    items(categoryImages, key = { it.id }) { image ->
                        InspirationGalleryCard(
                            image = image,
                            isLiked = likedIds.contains(image.id),
                            onClick = { onImageClick(image) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * SAVED SCREEN (Requirement 8):
 * - Shows ONLY saved posts (favorites collection).
 * - Same 2-column card grid.
 * - No prompt tab.
 * - Tapping opens the detail screen (where viewing the prompt requires the ad flow again).
 * - Empty state with a button to go to Home.
 */
@Composable
fun SavedScreen(
    allImages: List<InspirationImage>,
    favoritedIds: Set<String>,
    likedIds: Set<String>,
    onImageClick: (InspirationImage) -> Unit,
    onGoToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedImages = remember(allImages, favoritedIds) {
        allImages.filter { it.status == "published" && favoritedIds.contains(it.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("saved_screen_container")
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.saved_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (savedImages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.empty_saved),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onGoToHome,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("saved_go_to_home_button")
                    ) {
                        Text(text = stringResource(R.string.go_to_home))
                    }
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("saved_posts_grid")
            ) {
                items(savedImages, key = { it.id }) { image ->
                    InspirationGalleryCard(
                        image = image,
                        isLiked = likedIds.contains(image.id),
                        onClick = { onImageClick(image) }
                    )
                }
            }
        }
    }
}
