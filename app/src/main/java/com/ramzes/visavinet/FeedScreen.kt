@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.ramzes.visavinet.network.FeedItem
import com.ramzes.visavinet.network.FileData
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.RenderContentBlocks
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.parseHtmlToBlocks
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit,
    onPhotoClick: (photoId: Int) -> Unit,
    onDownClick: (downId: Int) -> Unit,
    onUserClick: (login: String) -> Unit,
    onImageClick: (imageUrl: String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val primaryAccent = getPrimaryAccentColor()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.scrollItemIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoading)

    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.scrollItemIndex = index
                viewModel.scrollOffset = offset
            }
    }

    LaunchedEffect(Unit) {
        if (viewModel.feedItems.isEmpty()) {
            viewModel.loadFeed(context, 1)
        }
    }

    LaunchedEffect(viewModel.feedItems.size, viewModel.isLoadingMore) {
        if (viewModel.feedItems.isEmpty() || viewModel.isLoadingMore) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= viewModel.feedItems.size - 3) {
                    viewModel.loadMore(context)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхний заголовок "Лента событий"
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
                        text = "Лента событий",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                }
            }

            when {
                viewModel.isLoading && viewModel.feedItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryAccent)
                    }
                }

                viewModel.errorMessage != null && viewModel.feedItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = viewModel.errorMessage ?: "Ошибка загрузки",
                                color = Color(0xFFCF6679),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh(context) }) {
                                Text("Повторить")
                            }
                        }
                    }
                }

                else -> {
                    SwipeRefresh(
                        state = swipeRefreshState,
                        onRefresh = { viewModel.refresh(context) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(viewModel.feedItems, key = { "${it.type}_${it.id}_${it.createdAtRaw ?: ""}" }) { item ->
                                FeedCardItem(
                                    item = item,
                                    isDark = isDark,
                                    onTopicClick = onTopicClick,
                                    onNewsClick = onNewsClick,
                                    onPhotoClick = onPhotoClick,
                                    onDownClick = onDownClick,
                                    onUserClick = onUserClick,
                                    onImageClick = onImageClick
                                )
                            }

                            if (viewModel.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = primaryAccent,
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

        // Floating Action Button для прокрутки наверх
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                shape = CircleShape,
                containerColor = primaryAccent,
                contentColor = if (isDark) Color.Black else Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Наверх"
                )
            }
        }
    }
}

@Composable
fun FeedCardItem(
    item: FeedItem,
    isDark: Boolean,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit,
    onPhotoClick: (photoId: Int) -> Unit,
    onDownClick: (downId: Int) -> Unit,
    onUserClick: (login: String) -> Unit,
    onImageClick: (imageUrl: String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    val (badgeText, badgeColor, badgeIcon) = when (item.type) {
        "topics" -> Triple("ФОРУМ", NeonCyan, Icons.Default.Forum)
        "news" -> Triple("НОВОСТЬ", FieryRed, Icons.Default.Article)
        "photos" -> Triple("ГАЛЕРЕЯ", Color(0xFFC084FC), Icons.Default.PhotoCamera)
        "comments" -> {
            when (item.relate?.type) {
                "news" -> Triple("КОММЕНТАРИЙ: НОВОСТИ", EmeraldGreen, Icons.Default.ChatBubbleOutline)
                "photos" -> Triple("КОММЕНТАРИЙ: ГАЛЕРЕЯ", Color(0xFFE879F9), Icons.Default.ChatBubbleOutline)
                "downs" -> Triple("КОММЕНТАРИЙ: ЗАГРУЗКИ", AmberGold, Icons.Default.ChatBubbleOutline)
                else -> Triple("КОММЕНТАРИЙ", primaryAccent, Icons.Default.ChatBubbleOutline)
            }
        }
        else -> Triple(item.type.uppercase(), primaryAccent, Icons.Default.Info)
    }

    val onItemClick: () -> Unit = {
        when (item.type) {
            "topics" -> onTopicClick(item.topicId, null, item.postId)
            "news" -> onNewsClick(item.id.toInt())
            "photos" -> onPhotoClick(item.id.toInt())
            "comments" -> {
                when (item.relate?.type) {
                    "news" -> onNewsClick(item.relate.id.toInt())
                    "photos" -> onPhotoClick(item.relate.id.toInt())
                    "downs" -> onDownClick(item.relate.id.toInt())
                }
            }
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick),
        isDark = isDark,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Верхняя строка: Бейдж типа события + Хлебные крошки/Раздел
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val subBreadcrumbs = if (item.breadcrumbs.size > 1) {
                    item.breadcrumbs.drop(1)
                } else {
                    emptyList()
                }

                val breadcrumbText = if (subBreadcrumbs.isNotEmpty()) {
                    subBreadcrumbs.joinToString(" / ") { it.title }
                } else {
                    val rawSection = item.section ?: ""
                    if (rawSection !in listOf("Форум", "Темы", "Новости", "Галерея", "Загрузки", "Разделы")) {
                        rawSection
                    } else {
                        ""
                    }
                }

                if (breadcrumbText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = breadcrumbText,
                        fontSize = 11.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Заголовок темы / материала
            val titleText = item.title ?: item.relate?.title ?: ""
            if (titleText.isNotBlank()) {
                Text(
                    text = stripHtml(titleText),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Автор и дата
            item.user?.let { user ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(user.avatar)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f), CircleShape)
                            .clickable { onUserClick(user.login) },
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val authorColor = try {
                                if (!user.color.isNullOrBlank()) Color(android.graphics.Color.parseColor(user.color)) else textColor
                            } catch (e: Exception) {
                                textColor
                            }
                            Text(
                                text = user.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = authorColor,
                                modifier = Modifier.clickable { onUserClick(user.login) }
                            )
                        }

                        item.createdAt?.let { timeMs ->
                            Text(
                                text = formatUnixTime(timeMs),
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Превью медиа для раздела "Галерея" (фото/видео)
            if (item.type == "photos" && item.media.isNotEmpty()) {
                val mediaItem = item.media.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable { onPhotoClick(item.id.toInt()) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(mediaItem.path)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (mediaItem.isVideo) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Видео",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Текстовое содержимое (превью не более 300 символов)
            val previewText = remember(item.text) {
                formatFeedPreviewText(item.text, 300)
            }
            if (previewText.isNotBlank()) {
                Text(
                    text = previewText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = textColor.copy(alpha = 0.88f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Прикрепленные медиа (для комментариев / тем)
            if (item.type != "photos" && item.media.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(item.media) { media ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(media.path)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .clickable { media.path?.let { onImageClick(it) } },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Футер: Рейтинг и Счетчик комментариев/ответов
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Рейтинг
                val ratingColor = when {
                    item.rating > 0 -> Color(0xFF10B981)
                    item.rating < 0 -> Color(0xFFEF4444)
                    else -> secondaryTextColor
                }
                Surface(
                    color = ratingColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = ratingColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (item.rating > 0) "+${item.rating}" else "${item.rating}",
                            color = ratingColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Комментарии
                if (item.commentsCount != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${item.commentsCount}",
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Очищает HTML от тегов и сокращает до maxLength символов для превью в ленте
 */
fun formatFeedPreviewText(html: String?, maxLength: Int = 300): String {
    if (html.isNullOrBlank()) return ""
    val withoutTags = html
        .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?(?:p|div|br|li|tr|h[1-6])[^>]*>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]*>"), "")

    val decoded = withoutTags
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&#60;", "<")
        .replace("&#62;", ">")
        .replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else match.value
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else match.value
        }

    val singleSpaced = decoded.replace(Regex("\\s+"), " ").trim()

    return if (singleSpaced.length > maxLength) {
        singleSpaced.take(maxLength).trimEnd() + "…"
    } else {
        singleSpaced
    }
}
