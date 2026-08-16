@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassFileCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun NewsDetailScreen(
    viewModel: NewsViewModel,
    news: NewsItem,
    currentLogin: String? = null,
    onBackClick: () -> Unit,
    onUserClick: (String) -> Unit = {},
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit = { _, _, _ -> }
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
                            Icons.AutoMirrored.Filled.ArrowBack,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Карточка новости
                    item {
                        NewsMainContentCard(
                            news = displayNews,
                            isDark = isDark,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onImageClick = { url -> zoomImageUrl = url }
                        )
                    }

                    // Заголовок комментариев
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Комментарии (${displayNews.commentsCount})",
                                color = textColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Комментариев пока нет. Будьте первыми!",
                                    color = secondaryTextColor,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(viewModel.comments, key = { "comment_${it.id}" }) { comment ->
                            NewsCommentCard(
                                comment = comment,
                                isDark = isDark,
                                isHighlighted = highlightedCommentId == comment.id,
                                onUserClick = onUserClick,
                                onTopicClick = onTopicClick,
                                onParentCommentClick = { parentId ->
                                    val targetIndex = viewModel.comments.indexOfFirst { it.id == parentId }
                                    if (targetIndex != -1) {
                                        coroutineScope.launch {
                                            highlightedCommentId = parentId
                                            listState.animateScrollToItem(index = 2 + targetIndex)
                                            kotlinx.coroutines.delay(2000)
                                            if (highlightedCommentId == parentId) {
                                                highlightedCommentId = null
                                            }
                                        }
                                    }
                                },
                                onReplyClick = {
                                    replyingToCommentId = comment.id
                                    replyingToLogin = comment.user?.login
                                    commentText = "@${comment.user?.login ?: "пользователь"}, "
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
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDark) Color(0x33000000) else Color(0x1A000000)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Индикатор ответа на комментарий
                        if (replyingToLogin != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ответ для @$replyingToLogin",
                                    color = getPrimaryAccentColor(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        replyingToCommentId = null
                                        replyingToLogin = null
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Отмена ответа",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Прикрепленные файлы
                        if (attachedFiles.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(attachedFiles) { uri ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) Color(0x33000000) else Color(0x1A000000),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                tint = getPrimaryAccentColor(),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Файл",
                                                color = textColor,
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Удалить",
                                                tint = secondaryTextColor,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable {
                                                        attachedFiles = attachedFiles.filter { it != uri }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = "Прикрепить файл",
                                    tint = if (attachedFiles.isNotEmpty()) getPrimaryAccentColor() else secondaryTextColor
                                )
                            }

                            IconButton(onClick = { isFullscreenModalOpen = true }) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "Полноэкранный ввод",
                                    tint = secondaryTextColor
                                )
                            }

                            GlassTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                placeholderText = "Ваш комментарий...",
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (commentText.isNotBlank()) {
                                        viewModel.createComment(
                                            context = context,
                                            newsId = news.id,
                                            text = commentText.trim(),
                                            parentId = replyingToCommentId,
                                            fileUris = attachedFiles,
                                            onSuccess = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                commentText = ""
                                                replyingToCommentId = null
                                                replyingToLogin = null
                                                attachedFiles = emptyList()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                },
                                enabled = commentText.isNotBlank() && !viewModel.isSubmittingComment
                            ) {
                                if (viewModel.isSubmittingComment) {
                                    CircularProgressIndicator(
                                        color = getPrimaryAccentColor(),
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Отправить",
                                        tint = if (commentText.isNotBlank()) getPrimaryAccentColor() else secondaryTextColor.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
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
                textMin = 1,
                textMax = 5000,
                isSending = viewModel.isSubmittingComment,
                title = "Комментарий",
                onSend = {
                    if (isCommentValid && !viewModel.isSubmittingComment) {
                        viewModel.createComment(
                            context = context,
                            newsId = news.id,
                            text = commentText.trim(),
                            parentId = replyingToCommentId,
                            fileUris = attachedFiles,
                            onSuccess = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                onDismiss = { isFullscreenModalOpen = false }
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
    onImageClick: (String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = remember(news.user?.color) {
        news.user?.color?.let { parseColorString(it) } ?: textColor
    }

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

                val authorLogin = news.user?.login
                Text(
                    text = news.user?.name ?: authorLogin ?: "Администратор",
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
                    onTopicClick = onTopicClick
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
        }
    }
}

@Composable
fun NewsCommentCard(
    comment: NewsCommentItem,
    isDark: Boolean,
    isHighlighted: Boolean = false,
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onParentCommentClick: ((parentId: Int) -> Unit)? = null,
    onReplyClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = remember(comment.user?.color) {
        comment.user?.color?.let { parseColorString(it) } ?: textColor
    }

    val allFiles = remember(comment.media, comment.files) {
        (comment.safeMedia + comment.safeFiles).distinctBy { it.id }
    }

    val parentId = comment.parent?.id?.takeIf { it > 0 } ?: comment.parentId

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        shape = RoundedCornerShape(6.dp),
        glowColor = if (isHighlighted) getPrimaryAccentColor().copy(alpha = 0.6f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Контекст ответа родителю
            if (comment.parent != null && !comment.parent.login.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0x3338BDF8) else Color(0x1A0284C7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .then(
                            if (parentId != null && parentId > 0 && onParentCommentClick != null) {
                                Modifier.clickable { onParentCommentClick(parentId) }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = getSecondaryAccentColor(),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "В ответ @${comment.parent.login}: ${comment.parent.excerpt ?: ""}",
                            color = getSecondaryAccentColor(),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (comment.deleted) {
                Text(
                    text = "Комментарий удален",
                    color = secondaryTextColor.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // Шапка комментария: автор, аватар, дата, кнопка ответа
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
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

                        val authorLogin = comment.user?.login
                        Text(
                            text = comment.user?.name ?: authorLogin ?: "Пользователь",
                            color = authorColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = authorLogin != null) {
                                authorLogin?.let { onUserClick(it) }
                            }
                        )

                        comment.createdAt?.let { created ->
                            Text(
                                text = " • ${formatUnixTime(created)}",
                                color = secondaryTextColor,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Кнопка ответа
                    IconButton(
                        onClick = onReplyClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Ответить",
                            tint = getSecondaryAccentColor(),
                            modifier = Modifier.size(16.dp)
                        )
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
                        onTopicClick = onTopicClick
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
            }
        }
    }
}
