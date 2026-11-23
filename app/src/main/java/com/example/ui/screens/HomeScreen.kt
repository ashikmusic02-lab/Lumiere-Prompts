package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.LumiereBannerAdSlot
import com.example.model.AdvertisementSettings
import com.example.model.CategoryItem
import com.example.model.InspirationImage
import com.example.model.UiState
import com.example.ui.components.InspirationGalleryCard
import com.example.ui.components.ShimmerSkeletonCard
import com.example.ui.components.pressScaleClickable
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.HeartLikeRed
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.IndigoSurfaceDark
import com.example.ui.theme.PromptAccentGradientBrush
import com.example.ui.theme.PromptCardSurfaceBrush

@Composable
fun HomeScreen(
    imagesUiState: UiState<List<InspirationImage>>,
    categories: List<CategoryItem>,
    likedIds: Set<String>,
    hasUnreadNotifications: Boolean,
    advertisementSettings: AdvertisementSettings,
    onOpenNotifications: () -> Unit,
    onRetrySync: () -> Unit,
    onImageClick: (InspirationImage) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf("all") }

    val publishedCategories = remember(categories) {
        categories.filter { it.status == "published" && it.name.isNotBlank() }
            .sortedBy { it.sortOrder }
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
                top = 14.dp,
                bottom = 104.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp
        ) {
            // 1. Top Row: App Logo + Name "PromptLens" on the left, Bell icon with unread red dot on the right
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_promptlens_logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Bell icon button with small red dot when unread notifications exist
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PromptCardSurfaceBrush, CircleShape)
                            .border(1.dp, IndigoSoftBorder, CircleShape)
                            .pressScaleClickable(onClick = onOpenNotifications)
                            .testTag("home_notifications_bell_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.notifications_title),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        if (hasUnreadNotifications) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-9).dp, y = 9.dp)
                                    .clip(CircleShape)
                                    .background(HeartLikeRed)
                                    .testTag("home_unread_bell_dot")
                            )
                        }
                    }
                }
            }

            // 2. Pill-shaped Search Bar (placeholder "Search")
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = IndigoSoftBorder,
                        focusedContainerColor = IndigoSurfaceDark,
                        unfocusedContainerColor = IndigoSurfaceDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            }

            // 3. Horizontally scrollable row of pill category chips (first chip "All"; selected = accent gradient; name only)
            item(span = StaggeredGridItemSpan.FullLine) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    modifier = Modifier.testTag("home_category_row")
                ) {
                    item(key = "chip_all") {
                        CategoryPillChip(
                            label = stringResource(R.string.category_all),
                            selected = selectedCategoryId == "all",
                            onClick = { selectedCategoryId = "all" },
                            testTag = "home_chip_all"
                        )
                    }
                    items(publishedCategories, key = { it.id }) { category ->
                        val isSelected = selectedCategoryId == category.id
                        CategoryPillChip(
                            label = category.name,
                            selected = isSelected,
                            onClick = {
                                selectedCategoryId = if (isSelected) "all" else category.id
                            },
                            testTag = "home_chip_${category.id}"
                        )
                    }
                }
            }

            // 4. ONE Uniform Staggered 2-Column Grid of all published posts, newest first
            when (imagesUiState) {
                is UiState.Loading -> {
                    items(6) { index ->
                        ShimmerSkeletonCard(
                            aspectRatio = if (index % 2 == 0) 0.74f else 0.88f
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

        // Optional Home Banner Ad in its own 20dp rounded container above the floating bottom bar
        LumiereBannerAdSlot(
            settings = advertisementSettings,
            enabledForPlacement = advertisementSettings.homeBannerEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp)
                .testTag("home_bottom_banner_ad")
        )
    }
}

@Composable
private fun CategoryPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .then(
                if (selected) {
                    Modifier.background(PromptAccentGradientBrush, pillShape)
                } else {
                    Modifier
                        .background(IndigoSurfaceDark, pillShape)
                        .border(1.dp, IndigoSoftBorder, pillShape)
                }
            )
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeEmptyState(
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = IndigoSurfaceDark,
        border = BorderStroke(1.dp, IndigoSoftBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 44.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ImageSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = IndigoSurfaceDark,
        border = BorderStroke(1.dp, IndigoSoftBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 44.dp, horizontal = 24.dp),
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
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("home_retry_button")
            ) {
                Text(
                    text = stringResource(R.string.retry_action),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
