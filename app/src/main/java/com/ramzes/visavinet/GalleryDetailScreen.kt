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
import com.ramzes.visavinet.network.PhotoItem
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassFileCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.VideoFullscreenDialog
import com.ramzes.visavinet.ui.components.VideoPlayerView
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun GalleryDetailScreen(
    viewModel: GalleryViewModel,
    photo: PhotoItem,
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

    var commentText by remember { mutableStateOf("") }
    var replyingToCommentId by remember { mutableStateOf<Int?>(null) }
    var replyingToLogin by remember { mutableStateOf<String?>(null) }
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
                            tint = getSecondaryAccentColor()
                        )
                    }
                    Text(
                        text = "Галерея",
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
                onRefresh = { viewModel.loadPhotoDetail(context, photo.id, refresh = true) }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Главная карточка фото/видео
                    item {
                        GalleryMainContentCard(
                            photo = displayPhoto,
                            isDark = isDark,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onImageClick = { url -> zoomImageUrl = url },
                            onFullscreenVideo = { url -> fullscreenVideoUrl = url }
                        )
                    }

                    // Заголовок блока комментариев
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Комментарии (${displayPhoto.commentsCount})",
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
                            GalleryCommentCard(
                                comment = comment,
                                isDark = isDark,
                                onUserClick = onUserClick,
                                onTopicClick = onTopicClick,
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
            if (!displayPhoto.closed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDark) Color(0x33000000) else Color(0x1A000000)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Индикатор ответа
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
                                        contentDescription = "Отмена",
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
                                            photoId = photo.id,
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
                            text = "Комментирование этой записи закрыто",
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
                            photoId = photo.id,
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

@Composable
fun GalleryMainContentCard(
    photo: PhotoItem,
    isDark: Boolean,
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onImageClick: (String) -> Unit,
    onFullscreenVideo: (String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = remember(photo.user?.color) {
        photo.user?.color?.let { parseColorString(it) } ?: textColor
    }

    val mediaFiles = remember(photo.media, photo.files) {
        (photo.media + photo.files).distinctBy { it.id }
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
                        onFullscreenClick = { onFullscreenVideo(currentMedia.path) }
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
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.video_placeholder),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
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

            // Автор и дата
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!photo.user?.avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.user.avatar)
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

                val authorLogin = photo.user?.login
                Text(
                    text = photo.user?.name ?: authorLogin ?: "Пользователь",
                    color = authorColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = authorLogin != null) {
                        authorLogin?.let { onUserClick(it) }
                    }
                )

                photo.createdAt?.let { created ->
                    Text(
                        text = " • ${formatUnixTime(created)}",
                        color = secondaryTextColor,
                        fontSize = 11.sp
                    )
                }
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
                        onTopicClick = onTopicClick
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
    onUserClick: (String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onReplyClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = remember(comment.user?.color) {
        comment.user?.color?.let { parseColorString(it) } ?: textColor
    }

    val allFiles = remember(comment.media, comment.files) {
        (comment.media + comment.files).distinctBy { it.id }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        shape = RoundedCornerShape(6.dp),
        glowColor = Color.Transparent
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
                ) {
                    Text(
                        text = "В ответ @${comment.parent.login}: ${comment.parent.excerpt ?: ""}",
                        color = getSecondaryAccentColor(),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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

                comment.text?.let { text ->
                    val blocks = parseHtmlToBlocks(text)
                    RenderContentBlocks(
                        blocks = blocks,
                        isDark = isDark,
                        onUserClick = onUserClick,
                        onTopicClick = onTopicClick
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
