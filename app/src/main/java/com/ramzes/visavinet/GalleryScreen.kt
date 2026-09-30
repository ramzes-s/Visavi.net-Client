@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import com.ramzes.visavinet.ui.dialogs.EditPhotoDialog
import com.ramzes.visavinet.ui.dialogs.UploadPhotoDialog
import com.ramzes.visavinet.util.DraftsManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.ramzes.visavinet.network.PhotoItem
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.VideoPlaceholder
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.isDateRecent
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    isAuthorized: Boolean = false,
    currentLogin: String? = null,
    onPhotoClick: (PhotoItem) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    var showUploadDialog by remember { mutableStateOf(false) }
    var editingPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel.scrollItemIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoadingPhotos)

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.scrollItemIndex = index
                viewModel.scrollOffset = offset
            }
    }

    LaunchedEffect(Unit) {
        if (viewModel.photosList.isEmpty()) {
            viewModel.loadPhotosList(context)
        }
    }

    LaunchedEffect(viewModel.photosList.size, viewModel.isLoadingMorePhotos) {
        if (viewModel.photosList.isEmpty() || viewModel.isLoadingMorePhotos) return@LaunchedEffect

        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= viewModel.photosList.size - 4) {
                    viewModel.loadMorePhotos(context)
                }
            }
    }

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
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Все медиа",
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
                onRefresh = { viewModel.loadPhotosList(context, refresh = true) }
            ) {
                when {
                    viewModel.isLoadingPhotos && viewModel.photosList.isEmpty() -> {
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
                    viewModel.photosErrorMessage != null && viewModel.photosList.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = viewModel.photosErrorMessage ?: "Ошибка",
                                    color = Color(0xFFEF4444),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.loadPhotosList(context, refresh = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = getPrimaryAccentColor())
                                ) {
                                    Text("Повторить", color = Color.Black)
                                }
                            }
                        }
                    }
                    viewModel.photosList.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Медиафайлов пока нет",
                                color = secondaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(viewModel.photosList, key = { "photo_${it.id}" }) { photo ->
                                GalleryGridItem(
                                    photo = photo,
                                    isDark = isDark,
                                    currentLogin = currentLogin,
                                    onClick = { onPhotoClick(photo) },
                                    onUserClick = onUserClick,
                                    onEditClick = { photoToEdit ->
                                        viewModel.clearUpdateError()
                                        editingPhoto = photoToEdit
                                    }
                                )
                            }

                            if (viewModel.isLoadingMorePhotos) {
                                item(span = { GridItemSpan(2) }) {
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

        if (isAuthorized) {
            FloatingActionButton(
                onClick = {
                    viewModel.clearUploadError()
                    showUploadDialog = true
                },
                shape = CircleShape,
                containerColor = getPrimaryAccentColor(),
                contentColor = if (isDark) Color.Black else Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить фото")
            }
        }
    }

    if (showUploadDialog) {
        UploadPhotoDialog(
            onDismiss = {
                viewModel.clearUploadError()
                showUploadDialog = false
            },
            onSubmit = { title, text, closed, imageUris ->
                viewModel.uploadPhoto(
                    context = context.applicationContext,
                    title = title,
                    text = text,
                    closed = closed,
                    imageUris = imageUris,
                    onSuccess = {
                        DraftsManager.clearDraft(context, DraftsManager.galleryUploadTitleKey())
                        DraftsManager.clearDraft(context, DraftsManager.galleryUploadTextKey())
                        showUploadDialog = false
                    },
                    onError = {
                        // Сообщение сохраняется в viewModel.uploadPhotoError
                    }
                )
            },
            isSubmitting = viewModel.isUploadingPhoto,
            progressText = viewModel.uploadPhotoProgress,
            errorMessage = viewModel.uploadPhotoError
        )
    }

    editingPhoto?.let { photoToEdit ->
        EditPhotoDialog(
            photo = photoToEdit,
            onDismiss = {
                viewModel.clearUpdateError()
                editingPhoto = null
            },
            onSubmit = { newTitle, newText, closed ->
                viewModel.updatePhoto(
                    context = context.applicationContext,
                    photoId = photoToEdit.id,
                    title = newTitle,
                    text = newText,
                    closed = closed,
                    onSuccess = {
                        Toast.makeText(context, "Фотография обновлена", Toast.LENGTH_SHORT).show()
                        editingPhoto = null
                    },
                    onError = {
                        // Ошибка сохраняется в viewModel.updatePhotoError
                    }
                )
            },
            isSubmitting = viewModel.isUpdatingPhoto,
            errorMessage = viewModel.updatePhotoError
        )
    }
}

@Composable
fun GalleryGridItem(
    photo: PhotoItem,
    isDark: Boolean,
    currentLogin: String? = null,
    onClick: () -> Unit,
    onUserClick: (String) -> Unit,
    onEditClick: ((PhotoItem) -> Unit)? = null
) {
    val textColor = if (isDark) Color.White else LightText
    val context = LocalContext.current
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = getPrimaryAccentColor()

    val primaryFile = photo.primaryMedia
    val isVideo = photo.isVideo || primaryFile?.isVideo == true

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        isDark = isDark,
        shape = RoundedCornerShape(8.dp),
        glowColor = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Превью фото / видео
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(if (isDark) Color(0x331E293B) else Color(0x1F64748B))
            ) {
                if (isVideo) {
                    VideoPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        isDark = isDark,
                        accentColor = authorColor,
                        iconSize = 36.dp,
                        showLabel = true
                    )
                } else if (primaryFile?.path != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(primaryFile.path)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                val isAuthor = currentLogin != null && photo.user?.authorLogin == currentLogin
                if (isAuthor && onEditClick != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .clickable { onEditClick(photo) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Информация под фото
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Название
                Text(
                    text = photo.title ?: "Без названия",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Автор
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
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
                        contentDescription = "Аватар",
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable(enabled = authorLogin != null) {
                                authorLogin?.let { onUserClick(it) }
                            },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = photo.user?.displayName ?: "Пользователь",
                        color = authorColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = authorLogin != null) {
                            authorLogin?.let { onUserClick(it) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Единый бейджик: рейтинг, комментарии, дата
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        // Рейтинг
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Рейтинг",
                                tint = AmberGold,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (photo.rating > 0) "+${photo.rating}" else "${photo.rating}",
                                fontSize = 10.sp,
                                color = authorColor,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "•",
                            fontSize = 9.sp,
                            color = secondaryTextColor.copy(alpha = 0.4f)
                        )

                        // Комментарии
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Комментарии",
                                tint = authorColor,
                                modifier = Modifier.size(10.dp)
                            )
                            val newComments = remember(photo.id, photo.commentsCount) {
                                com.ramzes.visavinet.util.CommentsReadTracker.getNewCommentsCount(
                                    context,
                                    com.ramzes.visavinet.util.CommentsReadTracker.SECTION_GALLERY,
                                    photo.id,
                                    photo.commentsCount
                                )
                            }
                            // Счётчик: общее кол-во — акцентное, новые комментарии — белые/тёмные
                            val commentsText = buildAnnotatedString {
                                append("${photo.commentsCount}")
                                if (newComments > 0) {
                                    val start = length
                                    append(" +$newComments")
                                    addStyle(SpanStyle(color = textColor), start, length)
                                }
                            }
                            Text(
                                text = commentsText,
                                color = authorColor,
                                fontSize = 10.sp,
                                fontWeight = if (newComments > 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        photo.createdAt?.let { created ->
                            Text(
                                text = "•",
                                fontSize = 9.sp,
                                color = secondaryTextColor.copy(alpha = 0.4f)
                            )
                            val isRecent = isDateRecent(created)
                            val dateColor = if (isRecent) authorColor else secondaryTextColor
                            Text(
                                text = formatUnixTime(created),
                                fontSize = 9.5.sp,
                                color = dateColor,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
