package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.LumiereBannerAdSlot
import com.example.model.AdvertisementSettings
import com.example.model.CategoryItem
import com.example.model.InspirationImage
import com.example.model.UiState
import com.example.ui.components.InspirationGalleryCard
import com.example.ui.components.ShimmerSkeletonCard

@Composable
fun HomeScreen(
    imagesUiState: UiState<List<InspirationImage>>,
    categories: List<CategoryItem>,
    likedIds: Set<String>,
    advertisementSettings: AdvertisementSettings,
    onRetrySync: () -> Unit,
    onImageClick: (InspirationImage) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf("all") }

    val publishedCategories = remember(categories) {
        categories.filter { it.status == "published" }.sortedBy { it.sortOrder }
    }

    val allPublishedImages = remember(imagesUiState) {
        (imagesUiState as? UiState.Success)?.data
            ?.filter { it.status == "published" }
            ?.sortedWith(
                compareByDescending<InspirationImage> { it.createdAt?.seconds ?: 0L }
                    .thenBy { it.displayOrder }
            )
            .orEmpty()
    }

    val filteredImages = remember(allPublishedImages, searchQuery, selectedCategoryId) {
        val query = searchQuery.trim().lowercase()
        allPublishedImages.filter { img ->
            val matchesCategory = selectedCategoryId == "all" ||
                img.categoryId == selectedCategoryId
            val matchesQuery = query.isEmpty() ||
                img.title.lowercase().contains(query) ||
                img.shortName.lowercase().contains(query) ||
                img.categoryName.lowercase().contains(query) ||
                img.description.lowercase().contains(query) ||
                img.keywords.lowercase().contains(query)
            matchesCategory && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_container")
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 20.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp
        ) {
            // 1. Clean Top Bar: Logo + App Name (NO manual refresh / reload icon)
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_promptlens_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 2. Search Field
            item(span = StaggeredGridItemSpan.FullLine) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("clear_home_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            }

            // 3. Category Chips Row: ONLY category names as text chips (plus "All" first)
            item(span = StaggeredGridItemSpan.FullLine) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    modifier = Modifier.testTag("home_category_row")
                ) {
                    item(key = "chip_all") {
                        val isAllSelected = selectedCategoryId == "all"
                        FilterChip(
                            selected = isAllSelected,
                            onClick = { selectedCategoryId = "all" },
                            label = {
                                Text(
                                    text = stringResource(R.string.category_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isAllSelected,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("home_chip_all")
                        )
                    }
                    items(publishedCategories, key = { it.id }) { category ->
                        val isSelected = selectedCategoryId == category.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategoryId = if (isSelected) "all" else category.id
                            },
                            label = {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("home_chip_${category.id}")
                        )
                    }
                }
            }

            // 4. ONE Uniform Staggered 2-Column Grid containing ALL images (newest first)
            // Shimmer skeleton cards are shown ONLY on the very first launch with an empty cache.
            when (imagesUiState) {
                is UiState.Loading -> {
                    items(6) { index ->
                        ShimmerSkeletonCard(
                            aspectRatio = if (index % 2 == 0) 0.75f else 0.9f
                        )
                    }
                }

                is UiState.Error -> {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        HomeErrorState(
                            message = imagesUiState.message,
                            onRetry = onRetrySync
                        )
                    }
                }

                is UiState.Success -> {
                    if (allPublishedImages.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            HomeEmptyState(
                                title = stringResource(R.string.empty_inspirations)
                            )
                        }
                    } else if (filteredImages.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            HomeEmptyState(
                                title = stringResource(R.string.empty_search_results)
                            )
                        }
                    } else {
                        items(filteredImages, key = { it.id }) { image ->
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

        // One Banner Ad at the bottom of the Home content area above the tab bar
        LumiereBannerAdSlot(
            settings = advertisementSettings,
            enabledForPlacement = advertisementSettings.homeBannerEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
                .testTag("home_bottom_banner_ad")
        )
    }
}

@Composable
private fun HomeEmptyState(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ImageSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("home_retry_button")
        ) {
            Text(text = stringResource(R.string.retry_action))
        }
    }
}
