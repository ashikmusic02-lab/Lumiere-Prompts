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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ads.LumiereBannerAdSlot
import com.example.model.AdvertisementSettings
import com.example.model.CategoryItem
import com.example.model.InspirationImage
import com.example.model.UiState
import com.example.ui.components.InspirationGalleryCard
import com.example.ui.components.ShimmerSkeletonCard
import com.example.ui.components.pressScaleClickable
import com.example.ui.theme.CardBackground
import com.example.ui.theme.HeartLikeRed
import com.example.ui.theme.InactiveChipText
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PromptAccentGradientBrush
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SubtleBorder

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

    val publishedCategories by remember(categories) {
        derivedStateOf {
            categories.filter { it.status == "published" && it.name.isNotBlank() }
                .sortedBy { it.sortOrder }
        }
    }

    val allPublishedImages by remember(imagesUiState) {
        derivedStateOf {
            (imagesUiState as? UiState.Success)?.data
                ?.filter { it.status == "published" }
                ?.sortedWith(
                    compareByDescending<InspirationImage> { it.createdAt?.seconds ?: 0L }
                        .thenBy { it.displayOrder }
                )
                .orEmpty()
        }
    }

    val filteredImages by remember(allPublishedImages, searchQuery, selectedCategoryId) {
        derivedStateOf {
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
                top = 10.dp,
                bottom = 100.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp
        ) {
            // 1. Compact App Header: Deep Navy Background (#0C1035), subtle border (#252B69), no extra glow
            item(
                key = "header_row",
                span = StaggeredGridItemSpan.FullLine,
                contentType = "header"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SecondaryBackground, RoundedCornerShape(18.dp))
                        .border(1.dp, SubtleBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_promptlens_logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .border(0.8.dp, SubtleBorder, RoundedCornerShape(11.dp))
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 21.sp
                            ),
                            color = PrimaryText
                        )
                    }

                    // Notification Icon (White / soft Lavender) with unread red indicator
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CardBackground, CircleShape)
                            .border(1.dp, SubtleBorder, CircleShape)
                            .pressScaleClickable(onClick = onOpenNotifications)
                            .testTag("home_notifications_bell_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.notifications_title),
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                        if (hasUnreadNotifications) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-8).dp, y = 8.dp)
                                    .clip(CircleShape)
                                    .background(HeartLikeRed)
                                    .testTag("home_unread_bell_dot")
                            )
                        }
                    }
                }
            }

            // 2. Slim Search Bar: Height 54dp (52–56dp), Background #11164A, Border #252B69, Icon & Placeholder #A8ACD1, Radius 20dp
            item(
                key = "search_bar",
                span = StaggeredGridItemSpan.FullLine,
                contentType = "search"
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SecondaryText,
                            modifier = Modifier.size(20.dp)
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
                                    tint = SecondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = SubtleBorder,
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("home_search_input")
                )
            }

            // 3. Category Filter Row: Height 40dp (38–42dp), Radius 22dp, Active = #713CFF → #258BFF & White text, Inactive = #11164A & #B8BCE0
            item(
                key = "category_row",
                span = StaggeredGridItemSpan.FullLine,
                contentType = "categories"
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            // 4. Two Column Image Gallery (12dp gap, 20dp corner radius, 4:5 aspect ratio)
            when (imagesUiState) {
                is UiState.Loading -> {
                    items(6, key = { "shimmer_$it" }, contentType = { "shimmer" }) {
                        ShimmerSkeletonCard(aspectRatio = 0.8f)
                    }
                }

                is UiState.Error -> {
                    item(
                        key = "error_state",
                        span = StaggeredGridItemSpan.FullLine,
                        contentType = "error"
                    ) {
                        HomeErrorState(
                            message = imagesUiState.message,
                            onRetry = onRetrySync
                        )
                    }
                }

                is UiState.Success -> {
                    if (allPublishedImages.isEmpty()) {
                        item(
                            key = "empty_all",
                            span = StaggeredGridItemSpan.FullLine,
                            contentType = "empty"
                        ) {
                            HomeEmptyState(
                                title = stringResource(R.string.empty_inspirations)
                            )
                        }
                    } else if (filteredImages.isEmpty()) {
                        item(
                            key = "empty_filtered",
                            span = StaggeredGridItemSpan.FullLine,
                            contentType = "empty"
                        ) {
                            HomeEmptyState(
                                title = stringResource(R.string.empty_search_results)
                            )
                        }
                    } else {
                        items(
                            items = filteredImages,
                            key = { it.id },
                            contentType = { "image_card" }
                        ) { image ->
                            val isLiked by remember(likedIds, image.id) {
                                derivedStateOf { likedIds.contains(image.id) }
                            }
                            InspirationGalleryCard(
                                image = image,
                                isLiked = isLiked,
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
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp)
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
    val chipShape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(chipShape)
            .then(
                if (selected) {
                    Modifier.background(PromptAccentGradientBrush, chipShape)
                } else {
                    Modifier
                        .background(CardBackground, chipShape)
                        .border(1.dp, SubtleBorder, chipShape)
                }
            )
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) Color.White else InactiveChipText,
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
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
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
                tint = SecondaryText.copy(alpha = 0.8f),
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SecondaryText,
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
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
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
                color = SecondaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryPurple,
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
