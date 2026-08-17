@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.ramzes.visavinet.network.CategoryItem
import com.ramzes.visavinet.network.DownItem
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.sanitizeHtml
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun DownsScreen(
    viewModel: DownsViewModel,
    onDownClick: (DownItem) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary

    LaunchedEffect(Unit) {
        if (viewModel.categories.isEmpty()) {
            viewModel.loadCategories(context)
        }
    }

    when (viewModel.navigationLevel) {
        DownsNavigationLevel.CATEGORIES -> {
            DownsCategoriesView(
                viewModel = viewModel,
                isDark = isDark,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                onCategoryClick = { category ->
                    viewModel.openCategory(category, context)
                },
                onAllNewClick = {
                    viewModel.openAllNewDowns(context)
                }
            )
        }
        DownsNavigationLevel.DOWNS_LIST -> {
            DownsListView(
                viewModel = viewModel,
                isDark = isDark,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                onBackClick = { viewModel.navigateBack(context) },
                onDownClick = onDownClick,
                onUserClick = onUserClick,
                onSubcategoryClick = { subcategory ->
                    viewModel.openCategory(subcategory, context)
                }
            )
        }
        DownsNavigationLevel.DETAIL -> {
            // Детальный экран вызывается отдельно через DownsDetailScreen
        }
    }
}

/**
 * Уровень 1: Список категорий загрузок
 */
@Composable
fun DownsCategoriesView(
    viewModel: DownsViewModel,
    isDark: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    onCategoryClick: (CategoryItem) -> Unit,
    onAllNewClick: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.categoriesScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.categoriesScrollOffset
    )
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoadingCategories)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.categoriesScrollIndex = index
                viewModel.categoriesScrollOffset = offset
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя строка
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Все категории",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            SwipeRefresh(
                state = swipeRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                onRefresh = { viewModel.loadCategories(context, refresh = true) }
            ) {
                when {
                    viewModel.isLoadingCategories && viewModel.categories.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = getPrimaryAccentColor(),
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    viewModel.categoriesErrorMessage != null && viewModel.categories.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = viewModel.categoriesErrorMessage ?: "Ошибка",
                                    color = Color(0xFFEF4444),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.loadCategories(context, refresh = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = getPrimaryAccentColor())
                                ) {
                                    Text("Повторить", color = Color.Black)
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Специальная карточка «Все новые загрузки»
                            item {
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onAllNewClick),
                                    isDark = isDark,
                                    shape = RoundedCornerShape(6.dp),
                                    glowColor = getPrimaryAccentColor().copy(alpha = 0.25f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = getPrimaryAccentColor(),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Все новые загрузки",
                                                color = textColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                            Text(
                                                text = "Лента последних добавленных файлов",
                                                color = secondaryTextColor,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = getSecondaryAccentColor(),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Список категорий
                            items(viewModel.categories, key = { "cat_${it.id}" }) { category ->
                                DownsCategoryCard(
                                    category = category,
                                    isDark = isDark,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onClick = { onCategoryClick(category) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка категории в списке
 */
@Composable
fun DownsCategoryCard(
    category: CategoryItem,
    isDark: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        isDark = isDark,
        shape = RoundedCornerShape(6.dp),
        glowColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = getSecondaryAccentColor(),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name ?: "Категория",
                        color = textColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Файлов: ${category.totalDownsCount}",
                            color = secondaryTextColor,
                            fontSize = 11.sp
                        )
                        if (category.subcategories.isNotEmpty()) {
                            Text(
                                text = "• Подкатегорий: ${category.subcategories.size}",
                                color = getPrimaryAccentColor(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = secondaryTextColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Быстрые чипсы подкатегорий
            if (category.subcategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(category.subcategories) { sub ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0x33000000) else Color(0x1A000000),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = getSecondaryAccentColor(),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${sub.name ?: "Подраздел"} (${sub.downsCount})",
                                    color = textColor,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Уровень 2: Список файлов категории (с блоком подкатегорий)
 */
@Composable
fun DownsListView(
    viewModel: DownsViewModel,
    isDark: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    onBackClick: () -> Unit,
    onDownClick: (DownItem) -> Unit,
    onUserClick: (String) -> Unit,
    onSubcategoryClick: (CategoryItem) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.downsListScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.downsListScrollOffset
    )
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoadingDowns)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.downsListScrollIndex = index
                viewModel.downsListScrollOffset = offset
            }
    }

    val backTitle = remember(viewModel.categoryStack, viewModel.currentCategory) {
        if (viewModel.categoryStack.isNotEmpty()) {
            viewModel.categoryStack.last().name ?: "Назад"
        } else {
            "Все категории"
        }
    }

    LaunchedEffect(viewModel.downsList.size, viewModel.isLoadingMoreDowns) {
        if (viewModel.downsList.isEmpty() || viewModel.isLoadingMoreDowns) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= viewModel.downsList.size - 3) {
                    viewModel.loadMoreDowns(context)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя навигационная панель со стрелкой назад
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = getSecondaryAccentColor()
                        )
                    }
                    Text(
                        text = backTitle,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBackClick() }
                    )
                }
            }

            SwipeRefresh(
                state = swipeRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                onRefresh = { viewModel.loadDownsForCurrentCategory(context, refresh = true) }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Блок подкатегорий текущей категории
                    val currentCat = viewModel.currentCategory
                    if (currentCat != null && currentCat.subcategories.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                                Text(
                                    text = "Подкатегории",
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(currentCat.subcategories) { subcat ->
                                        GlassCard(
                                            modifier = Modifier.clickable { onSubcategoryClick(subcat) },
                                            isDark = isDark,
                                            shape = RoundedCornerShape(6.dp),
                                            glowColor = Color.Transparent
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = getPrimaryAccentColor(),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${subcat.name ?: "Подраздел"} (${subcat.totalDownsCount})",
                                                    color = textColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Список файлов
                    if (viewModel.isLoadingDowns && viewModel.downsList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = getPrimaryAccentColor(),
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    } else if (viewModel.downsErrorMessage != null && viewModel.downsList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = viewModel.downsErrorMessage ?: "Ошибка",
                                        color = Color(0xFFEF4444),
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.loadDownsForCurrentCategory(context, refresh = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = getPrimaryAccentColor())
                                    ) {
                                        Text("Повторить", color = Color.Black)
                                    }
                                }
                            }
                        }
                    } else if (viewModel.downsList.isEmpty() && !viewModel.isLoadingDowns) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "В этой категории пока нет файлов",
                                    color = secondaryTextColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(viewModel.downsList, key = { "down_${it.id}" }) { down ->
                            DownItemCard(
                                down = down,
                                isDark = isDark,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { onDownClick(down) },
                                onUserClick = onUserClick
                            )
                        }

                        if (viewModel.isLoadingMoreDowns) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = getPrimaryAccentColor(),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка загрузки в списке
 */
@Composable
fun DownItemCard(
    down: DownItem,
    isDark: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val authorColor = getPrimaryAccentColor()

    val previewText = remember(down.text) {
        down.text?.let { sanitizeHtml(it).replace(Regex("\\s+"), " ").trim() } ?: ""
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        isDark = isDark,
        shape = RoundedCornerShape(6.dp),
        glowColor = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Превью скриншота или иконка файла
            val screenshot = down.primaryMedia
            if (screenshot?.path != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(screenshot.path)
                            .crossfade(true)
                            .build(),
                        contentDescription = down.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0x33000000) else Color(0x1A000000),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = getPrimaryAccentColor(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Текстовая информация
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = down.title ?: "Без названия",
                    color = textColor,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (previewText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = previewText,
                        color = secondaryTextColor,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Автор и дата
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!down.user?.avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(down.user.avatar)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }

                    val authorLogin = down.user?.login
                    Text(
                        text = down.user?.name ?: authorLogin ?: "Автор",
                        color = authorColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = authorLogin != null) {
                            authorLogin?.let { onUserClick(it) }
                        }
                    )

                    down.createdAt?.let { created ->
                        Text(
                            text = " • ${formatUnixTime(created)}",
                            color = secondaryTextColor,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Метрики: скачивания, комментарии, рейтинг
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Скачиваний",
                            tint = getPrimaryAccentColor(),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${down.downloads}",
                            color = textColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = "Комментариев",
                            tint = getSecondaryAccentColor(),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${down.commentsCount}",
                            color = getSecondaryAccentColor(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (down.rating != 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Рейтинг",
                                tint = AmberGold,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${if (down.rating > 0) "+" else ""}${down.rating}",
                                color = if (down.rating > 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
