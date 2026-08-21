@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.ramzes.visavinet.network.DownFileItem
import com.ramzes.visavinet.network.DownItem
import com.ramzes.visavinet.network.NewsCommentItem
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassFileCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.VideoFullscreenDialog
import com.ramzes.visavinet.ui.components.VideoPlaceholder
import com.ramzes.visavinet.ui.components.VideoPlayerView
import com.ramzes.visavinet.ui.components.VoteDualButton
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

fun buildFinalDownCommentText(rawText: String, replyToUser: String?): String {
    val textWithUser = if (!replyToUser.isNullOrBlank()) {
        val userLink = "<a class=\"user\" href=\"/users/$replyToUser\">@$replyToUser</a> "
        if (rawText.startsWith(userLink)) rawText else "$userLink$rawText"
    } else {
        rawText
    }
    return ensureParagraphTags(textWithUser)
}

@Composable
fun DownsDetailScreen(
    viewModel: DownsViewModel,
    down: DownItem,
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
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            attachedFiles = (attachedFiles + uris).distinct().take(10)
        }
    }

    LaunchedEffect(viewModel.comments.size, viewModel.isLoadingMoreComments) {
        if (viewModel.comments.isEmpty() || viewModel.isLoadingMoreComments) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= viewModel.comments.size - 2) {
                    viewModel.loadMoreComments(down.id, context)
                }
            }
    }

    val displayDown = viewModel.currentDown ?: down
    val backCategoryName = remember(viewModel.currentCategory, displayDown.category) {
        viewModel.currentCategory?.name ?: displayDown.category?.name ?: "Загрузки"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя панель со стрелкой назад
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
                        text = backCategoryName,
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
                onRefresh = { viewModel.loadDownDetail(down.id, context, refresh = true) }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Главная карточка информации о загрузке
                    item {
                        DownsMainContentCard(
                            down = displayDown,
                            isDark = isDark,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onNewsClick = onNewsClick,
                            onDownClick = onDownClick,
                            onPhotoClick = onPhotoClick,
                            onVoteUp = {
                                viewModel.voteDown(
                                    downId = displayDown.id,
                                    vote = "+",
                                    context = context,
                                    type = displayDown.vote?.type ?: "downs",
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onVoteDown = {
                                viewModel.voteDown(
                                    downId = displayDown.id,
                                    vote = "-",
                                    context = context,
                                    type = displayDown.vote?.type ?: "downs",
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            isVoting = displayDown.id in viewModel.votingDownIds,
                            onImageClick = { url -> zoomImageUrl = url },
                            onFullscreenVideo = { url -> fullscreenVideoUrl = url }
                        )
                    }

                    // Заголовок комментариев
                    item {
                        Text(
                            text = "Комментарии (${displayDown.commentsCount})",
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
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
                            DownCommentCard(
                                comment = comment,
                                isDark = isDark,
                                isHighlighted = highlightedCommentId == comment.id,
                                onUserClick = onUserClick,
                                onTopicClick = onTopicClick,
                                onNewsClick = onNewsClick,
                                onDownClick = onDownClick,
                                onPhotoClick = onPhotoClick,
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

            // Нижняя панель комментирования
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.Transparent
            ) {
                GlassButton(
                    onClick = { isFullscreenModalOpen = true },
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
        }

        // Полноэкранный редактор комментария
        if (isFullscreenModalOpen) {
            val isCommentValid = commentText.trim().isNotEmpty()
            FullscreenInputModal(
                text = commentText,
                onTextChanged = { commentText = it },
                selectedFiles = attachedFiles,
                onFilesChanged = { attachedFiles = it },
                replyToUser = replyingToLogin,
                onRemoveReplyToUser = {
                    replyingToLogin = null
                    replyingToCommentId = null
                },
                textMin = 1,
                textMax = 5000,
                isSending = viewModel.isSubmittingComment,
                title = if (replyingToLogin != null) "Ответ для @$replyingToLogin" else "Комментарий",
                onSend = {
                    if (isCommentValid && !viewModel.isSubmittingComment) {
                        val formatted = buildFinalDownCommentText(
                            rawText = commentText.trim(),
                            replyToUser = replyingToLogin
                        )
                        viewModel.createComment(
                            context = context,
                            downId = down.id,
                            text = formatted,
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

        // Лайтбокс картинок
        zoomImageUrl?.let { url ->
            ImageLightboxDialog(
                imageUrl = url,
                onDismiss = { zoomImageUrl = null }
            )
        }

        // Полноэкранный видеоплеер
        fullscreenVideoUrl?.let { url ->
            VideoFullscreenDialog(
                videoUrl = url,
                onDismiss = { fullscreenVideoUrl = null }
            )
        }
    }
}

/**
 * Главный контент страницы загрузки
 */
@Composable
fun DownsMainContentCard(
    down: DownItem,
    isDark: Boolean,
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit = {},
    onDownClick: (downId: Int) -> Unit = {},
    onPhotoClick: (photoId: Int) -> Unit = {},
    onVoteUp: (() -> Unit)? = null,
    onVoteDown: (() -> Unit)? = null,
    isVoting: Boolean = false,
    onImageClick: (String) -> Unit,
    onFullscreenVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = getPrimaryAccentColor()

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        shape = RoundedCornerShape(10.dp),
        glowColor = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Название загрузки
            Text(
                text = down.title ?: "Без названия",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Автор слева, бейджик с датой и скачиваниями справа
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
                    if (!down.user?.avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(down.user.avatar)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    val authorLogin = down.user?.authorLogin
                    Text(
                        text = down.user?.displayName ?: "Автор",
                        color = authorColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = authorLogin != null) {
                            authorLogin?.let { onUserClick(it) }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Типовой бейджик: скачивания + дата добавления
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        // Кол-во скачиваний (акцентным цветом)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Скачивания",
                                tint = authorColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${down.downloads}",
                                color = authorColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        down.createdAt?.let { created ->
                            Text(
                                text = "•",
                                fontSize = 10.sp,
                                color = secondaryTextColor.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 1.dp)
                            )
                            val isRecent = isDateRecent(created)
                            val dateColor = if (isRecent) authorColor else secondaryTextColor
                            Text(
                                text = formatUnixTime(created),
                                fontSize = 10.5.sp,
                                color = dateColor,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Файлы дистрибутива
            if (down.safeFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Файлы для скачивания",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    down.safeFiles.forEach { file ->
                        DownDistributionFileCard(
                            file = file,
                            isDark = isDark,
                            canDownload = down.canDownload,
                            onDownloadClick = {
                                file.downloadUrl?.let { url ->
                                    DownloaderHelper.downloadFile(
                                        context = context,
                                        url = url,
                                        fileName = file.name,
                                        mimeType = file.mimeType
                                    )
                                } ?: Toast.makeText(context, "Скачивание недоступно", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Внешние ссылки
            if (down.safeLinks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    down.safeLinks.forEach { link ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0x33000000) else Color(0x1A000000),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    link.downloadUrl?.let { url ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = getSecondaryAccentColor(),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = link.name ?: "Внешняя ссылка",
                                    color = getSecondaryAccentColor(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Скриншоты и медиа
            if (down.safeMedia.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Скриншоты и материалы",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(down.safeMedia) { mediaItem ->
                        val isVideo = mediaItem.isVideo || mediaItem.extension?.lowercase() in listOf("mp4", "webm", "mkv", "mov", "avi", "3gp")
                        Box(
                            modifier = Modifier
                                .height(120.dp)
                                .widthIn(min = 120.dp, max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .clickable {
                                    if (isVideo && mediaItem.path != null) {
                                        onFullscreenVideo(mediaItem.path)
                                    } else if (mediaItem.path != null) {
                                        onImageClick(mediaItem.path)
                                    }
                                }
                        ) {
                            if (isVideo) {
                                VideoPlaceholder(
                                    modifier = Modifier.fillMaxSize(),
                                    isDark = isDark,
                                    iconSize = 22.dp
                                )
                            } else if (mediaItem.path != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(mediaItem.path)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // Описание
            down.text?.let { text ->
                if (text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
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
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Блок рейтинга и голосования
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                VoteDualButton(
                    vote = down.vote,
                    rating = down.rating,
                    onVoteUp = { onVoteUp?.invoke() },
                    onVoteDown = { onVoteDown?.invoke() },
                    isLoading = isVoting,
                    isDark = isDark,
                    isCompact = false
                )
            }
        }
    }
}

/**
 * Карточка файла дистрибутива с кнопкой скачивания
 */
@Composable
fun DownDistributionFileCard(
    file: DownFileItem,
    isDark: Boolean,
    canDownload: Boolean,
    onDownloadClick: () -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0x33000000) else Color(0x1A000000),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = getPrimaryAccentColor(),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name ?: "Файл",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(file.size),
                    color = secondaryTextColor,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onDownloadClick,
                enabled = canDownload && file.downloadUrl != null,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = getPrimaryAccentColor())
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Скачать",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Карточка комментария к загрузке
 */
@Composable
fun DownCommentCard(
    comment: NewsCommentItem,
    isDark: Boolean,
    isHighlighted: Boolean = false,
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit = {},
    onDownClick: (downId: Int) -> Unit = {},
    onPhotoClick: (photoId: Int) -> Unit = {},
    onParentCommentClick: ((parentId: Int) -> Unit)? = null,
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

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) {
                    Modifier.border(1.5.dp, getPrimaryAccentColor(), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            ),
        isDark = isDark,
        shape = RoundedCornerShape(6.dp),
        glowColor = if (isHighlighted) getPrimaryAccentColor().copy(alpha = 0.25f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Контекст ответа
            if (comment.parent != null && !comment.parent.login.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0x3338BDF8) else Color(0x1A0284C7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .then(
                            if (parentId != null && onParentCommentClick != null) {
                                Modifier.clickable { onParentCommentClick(parentId) }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = getSecondaryAccentColor(),
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 2.dp)
                        )
                        Text(
                            text = "В ответ @${comment.parent.login}: ${comment.parent.excerpt ?: ""}",
                            color = getSecondaryAccentColor(),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                // Шапка комментария: автор, аватар, и бейджик с временем и кнопкой ответа
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
                                contentDescription = null,
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // Бейджик: кнопка ответа + время (в стиле новостей и форума)
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
                            // Кнопка ответа
                            IconButton(
                                onClick = onReplyClick,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Ответить",
                                    tint = authorColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            comment.createdAt?.let { created ->
                                Text(
                                    text = "•",
                                    fontSize = 10.sp,
                                    color = secondaryTextColor.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )

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

                Spacer(modifier = Modifier.height(6.dp))

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
