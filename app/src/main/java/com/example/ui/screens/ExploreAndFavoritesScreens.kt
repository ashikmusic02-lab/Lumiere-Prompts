package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AiToolItem
import com.example.model.CategoryItem
import com.example.model.InspirationImage
import com.example.ui.components.InspirationGalleryCard
import com.example.ui.components.LumiereExternalImage
import com.example.ui.components.openInAppCustomTab
import com.example.ui.components.pressScaleClickable
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.IndigoSurfaceDark
import com.example.ui.theme.PromptAccentGradientBrush
import com.example.ui.theme.PromptCardSurfaceBrush

enum class CategorySortOption {
    LATEST,
    TRENDING,
    MOST_LIKED
}

/**
 * CATEGORIES SCREEN (Requirement 4):
 * - Rounded 28dp category cards (name only, vertical accent bar on the left).
 * - Tapping filters the posts into the 2-column staggered grid.
 */
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
        categories.filter { it.status == "published" && it.name.isNotBlank() }
            .sortedBy { it.sortOrder }
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
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.categories_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (publishedCategories.isEmpty()) {
                FriendlyRoundedEmptyCard(
                    icon = Icons.Outlined.Category,
                    message = stringResource(R.string.empty_categories)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("categories_name_grid")
                ) {
                    items(publishedCategories, key = { it.id }) { category ->
                        val cardShape = RoundedCornerShape(28.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(PromptCardSurfaceBrush, cardShape)
                                .border(1.dp, IndigoSoftBorder, cardShape)
                                .pressScaleClickable { selectedCategoryId = category.id }
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                                .testTag("category_card_${category.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Accent bar on the left
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(26.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(PromptAccentGradientBrush)
                                    )
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            // Rounded header bar with back button, category name, and pill sort button
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = IndigoSurfaceDark,
                border = BorderStroke(1.dp, IndigoSoftBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PromptCardSurfaceBrush, CircleShape)
                                .border(1.dp, IndigoSoftBorder, CircleShape)
                                .pressScaleClickable { selectedCategoryId = null }
                                .testTag("category_back_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_action),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = selectedCategory.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        val pillShape = RoundedCornerShape(50)
                        Row(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(PromptCardSurfaceBrush, pillShape)
                                .border(1.dp, IndigoSoftBorder, pillShape)
                                .pressScaleClickable { showSortMenu = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("category_sort_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_label),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when (sortOption) {
                                    CategorySortOption.LATEST -> stringResource(R.string.sort_latest)
                                    CategorySortOption.TRENDING -> stringResource(R.string.sort_trending)
                                    CategorySortOption.MOST_LIKED -> stringResource(R.string.sort_most_liked)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = IndigoSurfaceDark,
                            border = BorderStroke(1.dp, IndigoSoftBorder)
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
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                FriendlyRoundedEmptyCard(
                    icon = Icons.Outlined.ImageSearch,
                    message = stringResource(R.string.empty_inspirations)
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp,
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
 * SAVED SCREEN (Requirement 5):
 * - Same 2-column 28dp rounded grid as Home.
 * - Friendly rounded empty state with one icon and one short line.
 */
@Composable
fun SavedScreen(
    allImages: List<InspirationImage>,
    favoritedIds: Set<String>,
    likedIds: Set<String>,
    onImageClick: (InspirationImage) -> Unit,
    onGoToHome: () -> Unit = {},
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
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (savedImages.isEmpty()) {
            FriendlyRoundedEmptyCard(
                icon = Icons.Outlined.BookmarkBorder,
                message = stringResource(R.string.empty_saved)
            )
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalItemSpacing = 16.dp,
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

/**
 * AI TOOLS TAB (Requirement 6):
 * - Title "AI Tools" and a rounded pill search bar.
 * - Below, a rounded 28dp section card containing a 4-column grid of tools:
 *   round logo (56dp) with the tool name underneath.
 * - Tools come from Firestore collection `aiTools` where `enabled == true`, ordered by `sortOrder`,
 *   cached locally so it shows instantly and offline.
 * - If `logoUrl` is empty, uses `https://www.google.com/s2/favicons?domain=<host of url>&sz=128`.
 * - Tapping a tool opens its `url` INSIDE the app using a Chrome Custom Tab (dark violet toolbar).
 * - If the list is empty, shows a friendly rounded empty state.
 */
@Composable
fun AiToolsScreen(
    aiTools: List<AiToolItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredTools = remember(aiTools, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val base = aiTools.filter { it.enabled && it.name.isNotBlank() && it.url.isNotBlank() }
            .sortedBy { it.sortOrder }
        if (q.isEmpty()) {
            base
        } else {
            base.filter { tool ->
                tool.name.lowercase().contains(q) || tool.url.lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("ai_tools_screen_container"),
        contentPadding = PaddingValues(top = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Title "AI Tools"
        item {
            Text(
                text = stringResource(R.string.ai_tools_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 2. Pill-shaped Search Bar inside a 28dp rounded header container
        item {
            val searchCardShape = RoundedCornerShape(28.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(searchCardShape)
                    .background(PromptCardSurfaceBrush, searchCardShape)
                    .border(1.dp, IndigoSoftBorder, searchCardShape)
                    .padding(14.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.ai_tools_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("clear_ai_tools_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        .testTag("ai_tools_search_input")
                )
            }
        }

        // 3. 28dp Rounded Section Card containing a 4-column grid of tools (or friendly rounded empty state)
        item {
            if (filteredTools.isEmpty()) {
                FriendlyRoundedEmptyCard(
                    icon = Icons.Outlined.AutoAwesome,
                    message = if (searchQuery.isNotBlank()) {
                        stringResource(R.string.empty_search_results)
                    } else {
                        stringResource(R.string.empty_ai_tools)
                    }
                )
            } else {
                val sectionShape = RoundedCornerShape(28.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sectionShape)
                        .background(PromptCardSurfaceBrush, sectionShape)
                        .border(1.dp, IndigoSoftBorder, sectionShape)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .testTag("ai_tools_section_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Section header with left vertical accent bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(PromptAccentGradientBrush)
                            )
                            Text(
                                text = stringResource(R.string.ai_tools_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 4-column grid of tools
                        val rows = remember(filteredTools) { filteredTools.chunked(4) }
                        rows.forEach { rowTools ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowTools.forEach { tool ->
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        AiToolGridItem(
                                            tool = tool,
                                            onClick = {
                                                openInAppCustomTab(context, tool.url)
                                            }
                                        )
                                    }
                                }
                                repeat(4 - rowTools.size) {
                                    Spacer(modifier = Modifier.weight(1f))
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
private fun AiToolGridItem(
    tool: AiToolItem,
    onClick: () -> Unit
) {
    val logoUrl = remember(tool.logoUrl, tool.url) { tool.resolvedLogoUrl() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pressScaleClickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("ai_tool_item_${tool.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 56dp round logo with clean white circular background and subtle border ring
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .border(1.5.dp, IndigoSoftBorder, CircleShape)
                .padding(6.dp),
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
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Text(
            text = tool.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun FriendlyRoundedEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clip(shape)
            .background(PromptCardSurfaceBrush, shape)
            .border(1.dp, IndigoSoftBorder, shape)
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
