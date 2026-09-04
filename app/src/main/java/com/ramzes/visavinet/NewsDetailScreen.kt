@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.ramzes.visavinet.network.FileData
import com.ramzes.visavinet.network.NewsCommentItem
import com.ramzes.visavinet.network.NewsItem
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassFileCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.VoteButton
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.*
import kotlinx.coroutines.flow.distinctUntilChanged

fun buildFinalNewsCommentText(rawText: String, replyToUser: String?): String {
    val textWithUser = if (!replyToUser.isNullOrBlank()) {
        val userLink = "<a class=\"user\" href=\"/users/$replyToUser\">@$replyToUser</a> "
        if (rawText.startsWith(userLink)) rawText else "$userLink$rawText"
    } else {
        rawText
    }
    return ensureParagraphTags(textWithUser)
}

@Composable
fun NewsDetailScreen(
    viewModel: NewsViewModel,
    news: NewsItem,
    currentLogin: String? = null,
    onBackClick: () -> Unit,
    onUserClick: (String) -> Unit = {},
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit = { _, _, _ -> },
    onNewsClick: (newsId: Int) -> Unit = {},
    onDownClick: (downId: Int) -> Unit = {},
    onPhotoClick: (photoId: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val listState = rememberLazyListState()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoadingDetail)
    val coroutineScope = rememberCoroutineScope()

    var commentText by remember { mutableStateOf("") }
    var replyingToCommentId by remember { mutableStateOf<Int?>(null) }
    var replyingToLogin by remember { mutableStateOf<String?>(null) }
    var highlightedCommentId by remember { mutableStateOf<Int?>(null) }
    var attachedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isFullscreenModalOpen by remember { mutableStateOf(false) }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }

    // Состояние свернутых веток комментариев (по умолчанию пусто = все развернуты)
    var collapsedCommentIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Древовидное упорядочивание комментариев: каждый ответ (включая 3-й уровень) идет строго под своим родителем
    val treeComments = remember(viewModel.comments) {
        buildCommentTree(viewModel.comments)
    }

    // Карта количества всех потомков в ветке для каждого комментария
    val replyCountMap = remember(treeComments) {
        val byId = treeComments.associateBy { it.id }
        val getParentId: (NewsCommentItem) -> Int? = { c ->
            val pId = c.parent?.id?.takeIf { it > 0 } ?: c.parentId?.takeIf { it > 0 }
            if (pId != null && pId in byId) pId else null
        }
        val childrenMap = treeComments.groupBy { getParentId(it) }
        fun countDescendants(parentId: Int): Int {
            val children = childrenMap[parentId] ?: emptyList()
            return children.size + children.sumOf { countDescendants(it.id) }
        }
        treeComments.associate { it.id to countDescendants(it.id) }
    }

    // Отображаемый список комментариев с фильтрацией скрытых потомков свернутых веток
    val visibleComments = remember(treeComments, collapsedCommentIds) {
        if (collapsedCommentIds.isEmpty()) {
            treeComments
        } else {
            val byId = treeComments.associateBy { it.id }
            val getParentId: (NewsCommentItem) -> Int? = { c ->
                val pId = c.parent?.id?.takeIf { it > 0 } ?: c.parentId?.takeIf { it > 0 }
                if (pId != null && pId in byId) pId else null
            }
            val childrenMap = treeComments.groupBy { getParentId(it) }
            val hiddenIds = mutableSetOf<Int>()
            for (collapsedId in collapsedCommentIds) {
                fun collectDescendants(pId: Int) {
                    childrenMap[pId]?.forEach { child ->
                        if (hiddenIds.add(child.id)) {
                            collectDescendants(child.id)
                        }
                    }
                }
                collectDescendants(collapsedId)
            }
            treeComments.filter { it.id !in hiddenIds }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            attachedFiles = (attachedFiles + uris).distinct().take(10)
        }
    }

    LaunchedEffect(news.id) {
        viewModel.selectNews(news)
        viewModel.loadNewsDetail(context, news.id)
    }

    LaunchedEffect(viewModel.comments.size, viewModel.isLoadingMoreComments) {
        if (viewModel.comments.isEmpty() || viewModel.isLoadingMoreComments) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= viewModel.comments.size - 2) {
                    viewModel.loadMoreComments(context, news.id)
                }
            }
    }

    val displayNews = viewModel.currentNews ?: news

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя панель
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = getSecondaryAccentColor()
                        )
                    }
                    Text(
                        text = "Новости",
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
                onRefresh = { viewModel.loadNewsDetail(context, news.id, refresh = true) }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Карточка новости
                    item {
                        NewsMainContentCard(
                            news = displayNews,
                            isDark = isDark,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onNewsClick = onNewsClick,
                            onDownClick = onDownClick,
                            onPhotoClick = onPhotoClick,
                            onVoteUp = {
                                viewModel.voteNews(
                                    newsId = displayNews.id,
                                    context = context,
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            isVoting = displayNews.id in viewModel.votingNewsIds,
                            onImageClick = { url -> zoomImageUrl = url }
                        )
                    }

                    // Заголовок комментариев (отображается только если есть комментарии)
                    val totalComments = maxOf(displayNews.commentsCount, viewModel.comments.size)
                    if (totalComments > 0) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isDark) Color(0x0DFFFFFF) else Color(0x06000000),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = getPrimaryAccentColor(),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = formatCommentsCount(totalComments),
                                            color = textColor,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.isLoadingDetail && viewModel.comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = getPrimaryAccentColor(),
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    } else if (viewModel.comments.isEmpty()) {
                        item {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                isDark = isDark,
                                shape = RoundedCornerShape(12.dp),
                                glowColor = Color.Transparent
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = getPrimaryAccentColor().copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Комментариев пока нет. Будьте первыми!",
                                        color = secondaryTextColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(visibleComments, key = { _, comment -> "comment_${comment.id}" }) { index, comment ->
                            val replyCount = replyCountMap[comment.id] ?: 0
                            val isCollapsed = comment.id in collapsedCommentIds
                            val parentId = comment.parent?.id?.takeIf { it > 0 } ?: comment.parentId
                            val isReply = comment.depth > 0 || (parentId != null && parentId > 0)
                            val prevIsReply = if (index > 0) {
                                val prev = visibleComments[index - 1]
                                prev.depth > 0 || ((prev.parent?.id ?: prev.parentId ?: 0) > 0)
                            } else false
                            val nextIsReply = if (index + 1 < visibleComments.size) {
                                val next = visibleComments[index + 1]
                                next.depth > 0 || ((next.parent?.id ?: next.parentId ?: 0) > 0)
                            } else false
                            val isFirstReply = isReply && !prevIsReply
                            val continuesBelow = isReply && nextIsReply

                            NewsCommentCard(
                                comment = comment,
                                currentLogin = currentLogin,
                                isDark = isDark,
                                isHighlighted = highlightedCommentId == comment.id,
                                replyCount = replyCount,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = {
                                    collapsedCommentIds = if (isCollapsed) {
                                        collapsedCommentIds - comment.id
                                    } else {
                                        collapsedCommentIds + comment.id
                                    }
                                },
                                isFirstReply = isFirstReply,
                                continuesBelow = continuesBelow,
                                onUserClick = onUserClick,
                                onTopicClick = onTopicClick,
                                onNewsClick = onNewsClick,
                                onDownClick = onDownClick,
                                onPhotoClick = onPhotoClick,
                                onReplyClick = {
                                    replyingToCommentId = comment.id
                                    isFullscreenModalOpen = true
                                },
                                onImageClick = { url -> zoomImageUrl = url }
                            )
                        }

                        if (viewModel.isLoadingMoreComments) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = getPrimaryAccentColor(),
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Нижняя панель отправки комментария
            if (!displayNews.closed) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.Transparent
                ) {
                    GlassButton(
                        onClick = {
                            replyingToCommentId = null
                            isFullscreenModalOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isDark = isDark,
                        accentColor = getPrimaryAccentColor()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Написать комментарий",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDark) Color(0x33000000) else Color(0x1A000000)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Комментирование этой новости закрыто",
                            color = secondaryTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Полноэкранный редактор
        if (isFullscreenModalOpen) {
            val isCommentValid = commentText.trim().isNotEmpty()
            FullscreenInputModal(
                text = commentText,
                onTextChanged = { commentText = it },
                selectedFiles = attachedFiles,
                onFilesChanged = { attachedFiles = it },
                replyToUser = null,
                onRemoveReplyToUser = null,
                textMin = 1,
                textMax = 5000,
                isSending = viewModel.isSubmittingComment,
                title = if (replyingToCommentId != null) "Ответ на комментарий" else "Комментарий",
                onSend = {
                    if (isCommentValid && !viewModel.isSubmittingComment) {
                        val formatted = ensureParagraphTags(commentText.trim())
                        viewModel.createComment(
                            context = context,
                            newsId = news.id,
                            text = formatted,
                            parentId = replyingToCommentId,
                            fileUris = attachedFiles,
                            onSuccess = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                replyingToCommentId?.let { pId ->
                                    collapsedCommentIds = collapsedCommentIds - pId
                                }
                                commentText = ""
                                replyingToCommentId = null
                                replyingToLogin = null
                                attachedFiles = emptyList()
                                isFullscreenModalOpen = false
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                onDismiss = {
                    isFullscreenModalOpen = false
                    replyingToCommentId = null
                    replyingToLogin = null
                }
            )
        }

        // Модальный зум картинок
        zoomImageUrl?.let { url ->
            ImageLightboxDialog(
                imageUrl = url,
                onDismiss = { zoomImageUrl = null }
            )
        }
    }
}

@Composable
fun NewsMainContentCard(
    news: NewsItem,
    isDark: Boolean,
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit = {},
    onDownClick: (downId: Int) -> Unit = {},
    onPhotoClick: (photoId: Int) -> Unit = {},
    onVoteUp: (() -> Unit)? = null,
    isVoting: Boolean = false,
    onImageClick: (String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = getPrimaryAccentColor()

    val allFiles = remember(news.media, news.files) {
        (news.safeMedia + news.safeFiles).distinctBy { it.id }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        shape = RoundedCornerShape(8.dp),
        glowColor = if (news.top) getPrimaryAccentColor().copy(alpha = 0.2f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Закреплено
            if (news.top) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Закреплено",
                        tint = getPrimaryAccentColor(),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Закреплено",
                        color = getPrimaryAccentColor(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Заголовок новости
            Text(
                text = news.title ?: "Новость",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Автор и дата
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!news.user?.avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(news.user.avatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Аватар автора",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val authorLogin = news.user?.authorLogin
                Text(
                    text = news.user?.displayName ?: "Администратор",
                    color = authorColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = authorLogin != null) {
                        authorLogin?.let { onUserClick(it) }
                    }
                )

                news.createdAt?.let { created ->
                    Text(
                        text = " • ${formatUnixTime(created)}",
                        color = secondaryTextColor,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HTML текст новости
            news.text?.let { text ->
                val blocks = parseHtmlToBlocks(text)
                RenderContentBlocks(
                    blocks = blocks,
                    isDark = isDark,
                    onUserClick = onUserClick,
                    onTopicClick = onTopicClick,
                    onNewsClick = onNewsClick,
                    onDownClick = onDownClick,
                    onPhotoClick = onPhotoClick
                )
            }

            // Прикрепленные картинки и файлы
            if (allFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allFiles.filter { isImageFile(it) }.forEach { file ->
                        ImageFilePreview(file = file, onImageClick = onImageClick)
                    }
                    allFiles.filter { !isImageFile(it) }.forEach { file ->
                        GlassFileCard(file = file, isDark = isDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Блок рейтинга и голосования
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                VoteButton(
                    vote = news.vote,
                    rating = news.rating,
                    onVoteUp = { onVoteUp?.invoke() },
                    isLoading = isVoting,
                    isDark = isDark,
                    isCompact = false
                )
            }
        }
    }
}

fun buildCommentTree(comments: List<NewsCommentItem>): List<NewsCommentItem> {
    if (comments.isEmpty()) return emptyList()

    val byId = comments.associateBy { it.id }
    val getParentId: (NewsCommentItem) -> Int? = { c ->
        val pId = c.parent?.id?.takeIf { it > 0 } ?: c.parentId?.takeIf { it > 0 }
        if (pId != null && pId in byId) pId else null
    }

    val childrenMap = comments.groupBy { getParentId(it) }
    val result = mutableListOf<NewsCommentItem>()
    val visited = mutableSetOf<Int>()

    fun collectSubtree(comment: NewsCommentItem) {
        if (!visited.add(comment.id)) return
        result.add(comment)
        childrenMap[comment.id]?.forEach { child ->
            collectSubtree(child)
        }
    }

    val roots = comments.filter { getParentId(it) == null }
    for (root in roots) {
        collectSubtree(root)
    }

    for (c in comments) {
        if (c.id !in visited) {
            collectSubtree(c)
        }
    }

    return result
}

fun formatRepliesCount(count: Int): String {
    val rem100 = count % 100
    val rem10 = count % 10
    return when {
        rem100 in 11..19 -> "$count ответов"
        rem10 == 1 -> "$count ответ"
        rem10 in 2..4 -> "$count ответа"
        else -> "$count ответов"
    }
}

@Composable
fun BranchGuideCanvas(
    isFirstReply: Boolean,
    continuesBelow: Boolean,
    branchLineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val cornerRadius = 6.dp.toPx()
        val midY = 22.dp.toPx()
        val lineX = size.width / 2f

        // Линия сверху и L-отвод к карточке
        val path = Path().apply {
            moveTo(lineX, 0f)
            val turnStartY = (midY - cornerRadius).coerceAtLeast(0f)
            lineTo(lineX, turnStartY)
            quadraticBezierTo(lineX, midY, lineX + cornerRadius, midY)
            lineTo(size.width, midY)
        }

        drawPath(
            path = path,
            color = branchLineColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Продолжение вертикальной линии вниз, если ниже есть еще ответы ветки
        if (continuesBelow) {
            drawLine(
                color = branchLineColor,
                start = Offset(lineX, midY),
                end = Offset(lineX, size.height),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun NewsCommentCard(
    comment: NewsCommentItem,
    isDark: Boolean,
    isHighlighted: Boolean = false,
    replyCount: Int = 0,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    isFirstReply: Boolean = false,
    continuesBelow: Boolean = false,
    currentLogin: String? = null,
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit = {},
    onDownClick: (downId: Int) -> Unit = {},
    onPhotoClick: (photoId: Int) -> Unit = {},
    onReplyClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = getPrimaryAccentColor()

    val allFiles = remember(comment.media, comment.files) {
        (comment.safeMedia + comment.safeFiles).distinctBy { it.id }
    }

    val parentId = comment.parent?.id?.takeIf { it > 0 } ?: comment.parentId
    val isReply = comment.depth > 0 || (parentId != null && parentId > 0)
    val branchLineColor = if (isDark) {
        getPrimaryAccentColor().copy(alpha = 0.7f)
    } else {
        getPrimaryAccentColor().copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(top = if (!isReply) 8.dp else 0.dp)
    ) {
        // Отрисовка направляющей ветвления для ответов (все уровни ответов на одном уровне)
        if (isReply) {
            BranchGuideCanvas(
                isFirstReply = isFirstReply,
                continuesBelow = continuesBelow,
                branchLineColor = branchLineColor,
                modifier = Modifier
                    .width(20.dp)
                    .fillMaxHeight()
            )
        }

        GlassCard(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = if (isReply) 3.dp else 0.dp),
            isDark = isDark,
            shape = RoundedCornerShape(8.dp),
            glowColor = if (isHighlighted) getPrimaryAccentColor().copy(alpha = 0.6f) else Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isReply) 8.dp else 10.dp)
            ) {
                if (comment.deleted) {
                    Text(
                        text = "Комментарий удален",
                        color = secondaryTextColor.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    // Шапка комментария: автор, аватар, кнопка ветки, кнопка ответа и время
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Автор
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (!comment.user?.avatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(comment.user.avatar)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Аватар",
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            val authorLogin = comment.user?.authorLogin
                            Text(
                                text = comment.user?.displayName ?: "Пользователь",
                                color = authorColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(enabled = authorLogin != null) {
                                    authorLogin?.let { onUserClick(it) }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Правая часть: кнопка ветки (свернуть/развернуть) + кнопка ответа + время
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Кнопка сворачивания/разворачивания ветки, если у комментария есть ответы
                            if (replyCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isCollapsed) authorColor.copy(alpha = 0.16f) else (if (isDark) Color(0x0DFFFFFF) else Color(0x06000000)),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isCollapsed) authorColor.copy(alpha = 0.5f) else (if (isDark) Color(0x18FFFFFF) else Color(0x10000000))
                                    ),
                                    modifier = Modifier.clickable { onToggleCollapse() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (isCollapsed) "Развернуть ветку" else "Свернуть ветку",
                                            tint = if (isCollapsed) authorColor else secondaryTextColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "$replyCount",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCollapsed) authorColor else secondaryTextColor
                                        )
                                    }
                                }
                            }

                            // Бейджик: кнопка ответа + время (в стиле форума)
                            Surface(
                                shape = CircleShape,
                                color = if (isDark) Color(0x0DFFFFFF) else Color(0x06000000),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                                ),
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    val authorLogin = comment.user?.authorLogin ?: comment.user?.login
                                    val isMyComment = currentLogin != null && (
                                        comment.user?.login.equals(currentLogin, ignoreCase = true) ||
                                        comment.user?.authorLogin.equals(currentLogin, ignoreCase = true)
                                    )

                                    if (!isMyComment) {
                                        IconButton(
                                            onClick = onReplyClick,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                                contentDescription = "Ответить",
                                                tint = authorColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }

                                    comment.createdAt?.let { created ->
                                        if (!isMyComment) {
                                            Text(
                                                text = "•",
                                                fontSize = 10.sp,
                                                color = secondaryTextColor.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(horizontal = 1.dp)
                                            )
                                        }

                                        Text(
                                            text = formatUnixTime(created),
                                            fontSize = 10.sp,
                                            color = secondaryTextColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Текст комментария
                    comment.text?.let { text ->
                        val blocks = parseHtmlToBlocks(text)
                        RenderContentBlocks(
                            blocks = blocks,
                            isDark = isDark,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onNewsClick = onNewsClick,
                            onDownClick = onDownClick,
                            onPhotoClick = onPhotoClick
                        )
                    }

                    // Прикрепленные файлы
                    if (allFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allFiles.filter { isImageFile(it) }.forEach { file ->
                                ImageFilePreview(file = file, onImageClick = onImageClick)
                            }
                            allFiles.filter { !isImageFile(it) }.forEach { file ->
                                GlassFileCard(file = file, isDark = isDark)
                            }
                        }
                    }

                    // Если ветка свернута — плашка внизу для быстрого разворачивания
                    if (replyCount > 0 && isCollapsed) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) getPrimaryAccentColor().copy(alpha = 0.12f) else getPrimaryAccentColor().copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = getPrimaryAccentColor().copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCollapse() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = authorColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Показать ${formatRepliesCount(replyCount)}",
                                    color = authorColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

