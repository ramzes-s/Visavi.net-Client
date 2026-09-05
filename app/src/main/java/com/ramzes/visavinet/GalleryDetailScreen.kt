@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
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
import androidx.media3.exoplayer.ExoPlayer
import com.ramzes.visavinet.network.FileData
import com.ramzes.visavinet.network.NewsCommentItem
import com.ramzes.visavinet.network.PhotoItem
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassFileCard
import com.ramzes.visavinet.ui.components.VideoPlaceholder
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.VideoFullscreenDialog
import com.ramzes.visavinet.ui.components.VideoPlayerView
import com.ramzes.visavinet.ui.components.VoteDualButton
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun GalleryDetailScreen(
    viewModel: GalleryViewModel,
    photo: PhotoItem,
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
    val accentColor = getSecondaryAccentColor()
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
    var activeFullscreenPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Состояние свернутых веток комментариев (по умолчанию пусто = все развернуты)
    var collapsedCommentIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Древовидное упорядочивание комментариев: каждый ответ идет строго под своим родителем
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

    LaunchedEffect(photo.id) {
        viewModel.selectPhoto(photo)
        viewModel.loadPhotoDetail(context, photo.id)
    }

    LaunchedEffect(viewModel.comments.size, viewModel.isLoadingMoreComments) {
        if (viewModel.comments.isEmpty() || viewModel.isLoadingMoreComments) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= viewModel.comments.size - 2) {
                    viewModel.loadMoreComments(context, photo.id)
                }
            }
    }

    val displayPhoto = viewModel.currentPhoto ?: photo

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя навигационная панель
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
                            tint = textColor
                        )
                    }
                    Text(
                        text = displayPhoto.title ?: "Медиафайл",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Основной скроллируемый контент
            SwipeRefresh(
                state = rememberSwipeRefreshState(viewModel.isLoadingDetail),
                onRefresh = { viewModel.loadPhotoDetail(context, photo.id) },
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Карточка записи
                    item {
                        GalleryMainContentCard(
                            photo = displayPhoto,
                            isDark = isDark,
                            isExternalFullscreenOpen = activeFullscreenPlayer != null,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onNewsClick = onNewsClick,
                            onDownClick = onDownClick,
                            onPhotoClick = onPhotoClick,
                            onVoteUp = {
                                viewModel.votePhoto(
                                    photoId = displayPhoto.id,
                                    vote = "+",
                                    context = context,
                                    type = displayPhoto.vote?.type ?: "photos",
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onVoteDown = {
                                viewModel.votePhoto(
                                    photoId = displayPhoto.id,
                                    vote = "-",
                                    context = context,
                                    type = displayPhoto.vote?.type ?: "photos",
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            isVoting = displayPhoto.id in viewModel.votingPhotoIds,
                            onImageClick = { url -> zoomImageUrl = url },
                            onFullscreenVideo = { player ->
                                activeFullscreenPlayer = player
                            }
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

                            GalleryCommentCard(
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

            // Индикатор закрытого комментирования (если запись закрыта)
            if (displayPhoto.closed) {
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
                            text = "Комментирование этой записи закрыто",
                            color = secondaryTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Плавающая круглая кнопка добавления комментария (если запись не закрыта)
        if (!displayPhoto.closed) {
            FloatingActionButton(
                onClick = {
                    replyingToCommentId = null
                    isFullscreenModalOpen = true
                },
                shape = CircleShape,
                containerColor = accentColor,
                contentColor = if (isDark) Color.Black else Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Добавить комментарий"
                )
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
                            photoId = photo.id,
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

        // Лайтбокс картинок
        zoomImageUrl?.let { url ->
            ImageLightboxDialog(
                imageUrl = url,
                onDismiss = { zoomImageUrl = null }
            )
        }

        // Полноэкранный видеоплеер
        activeFullscreenPlayer?.let { player ->
            VideoFullscreenDialog(
                player = player,
                onDismiss = { activeFullscreenPlayer = null }
            )
        }
    }
}

@Composable
fun GalleryMainContentCard(
    photo: PhotoItem,
    isDark: Boolean,
    isExternalFullscreenOpen: Boolean = false,
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit = {},
    onDownClick: (downId: Int) -> Unit = {},
    onPhotoClick: (photoId: Int) -> Unit = {},
    onVoteUp: (() -> Unit)? = null,
    onVoteDown: (() -> Unit)? = null,
    isVoting: Boolean = false,
    onImageClick: (String) -> Unit,
    onFullscreenVideo: (ExoPlayer) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = getPrimaryAccentColor()

    val mediaFiles = remember(photo.media, photo.files) {
        photo.allMedia
    }

    var selectedMediaIndex by remember { mutableIntStateOf(0) }
    val currentMedia = mediaFiles.getOrNull(selectedMediaIndex) ?: photo.primaryMedia

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        shape = RoundedCornerShape(10.dp),
        glowColor = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Медиа-контент: Видео или Картинка
            if (currentMedia != null) {
                val isVideo = currentMedia.isVideo || currentMedia.extension?.lowercase() in listOf("mp4", "webm", "mkv", "mov", "avi", "3gp")

                if (isVideo && currentMedia.path != null) {
                    VideoPlayerView(
                        videoUrl = currentMedia.path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp),
                        autoPlay = false,
                        isExternalFullscreenOpen = isExternalFullscreenOpen,
                        onFullscreenClick = { player -> onFullscreenVideo(player) }
                    )
                } else if (currentMedia.path != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 320.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .clickable { onImageClick(currentMedia.path) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentMedia.path)
                                .crossfade(true)
                                .build(),
                            contentDescription = photo.title,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Если файлов несколько — галерея миниатюр
                if (mediaFiles.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(mediaFiles.size) { index ->
                            val file = mediaFiles[index]
                            val isSelected = index == selectedMediaIndex
                            val isFileVideo = file.isVideo || file.extension?.lowercase() in listOf("mp4", "webm", "mkv", "mov", "avi", "3gp")

                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) getPrimaryAccentColor() else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .background(Color(0xFF1E293B))
                                    .clickable { selectedMediaIndex = index }
                            ) {
                                if (isFileVideo) {
                                    VideoPlaceholder(
                                        modifier = Modifier.fillMaxSize(),
                                        isDark = isDark,
                                        iconSize = 16.dp
                                    )
                                } else if (file.path != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(file.path)
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Заголовок записи
            Text(
                text = photo.title ?: "Медиа",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Автор и рейтинг в одной линии
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
                    val authorLogin = photo.user?.authorLogin
                    val avatarData: Any = if (!photo.user?.avatar.isNullOrBlank()) {
                        photo.user.avatar
                    } else {
                        R.drawable.ic_default_avatar
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarData)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Аватар автора",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(enabled = authorLogin != null) {
                                authorLogin?.let { onUserClick(it) }
                            },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = photo.user?.displayName ?: "Пользователь",
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

                // Блок рейтинга и голосования
                VoteDualButton(
                    vote = photo.vote,
                    rating = photo.rating,
                    onVoteUp = { onVoteUp?.invoke() },
                    onVoteDown = { onVoteDown?.invoke() },
                    isLoading = isVoting,
                    isDark = isDark,
                    isCompact = true
                )
            }

            // Текст/описание записи
            photo.text?.let { text ->
                if (text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
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
        }
    }
}

@Composable
fun GalleryCommentCard(
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
                            val authorLogin = comment.user?.authorLogin
                            val avatarData: Any = if (!comment.user?.avatar.isNullOrBlank()) {
                                comment.user.avatar
                            } else {
                                R.drawable.ic_default_avatar
                            }
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(avatarData)
                                    .placeholder(R.drawable.ic_default_avatar)
                                    .error(R.drawable.ic_default_avatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Аватар",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = authorLogin != null) {
                                        authorLogin?.let { onUserClick(it) }
                                    },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(6.dp))

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
}
