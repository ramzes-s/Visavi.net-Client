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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.ramzes.visavinet.network.PhotoItem
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.parseColorString
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onPhotoClick: (PhotoItem) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val gridState = rememberLazyGridState()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoadingPhotos)

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
                                    onClick = { onPhotoClick(photo) },
                                    onUserClick = onUserClick
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
    }
}

@Composable
fun GalleryGridItem(
    photo: PhotoItem,
    isDark: Boolean,
    onClick: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val authorColor = remember(photo.user?.color) {
        photo.user?.color?.let { parseColorString(it) } ?: textColor
    }

    val primaryFile = photo.primaryMedia
    val isVideo = photo.isVideo || primaryFile?.isVideo == true

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        isDark = isDark,
        shape = RoundedCornerShape(10.dp),
        glowColor = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Медиа превью
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF1E293B))
            ) {
                if (isVideo) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.video_placeholder),
                        contentDescription = "Видео",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
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

                // Верхний бейдж видео
                if (isVideo) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        color = Color(0xCC000000),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = null,
                                tint = getPrimaryAccentColor(),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Видео",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Инфо-блок под превью
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = photo.title ?: "Медиа",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Автор
                val authorLogin = photo.user?.login
                Text(
                    text = photo.user?.name ?: authorLogin ?: "Пользователь",
                    color = authorColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = authorLogin != null) {
                        authorLogin?.let { onUserClick(it) }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Нижняя строка: рейтинг и комментарии
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    photo.createdAt?.let { created ->
                        Text(
                            text = formatUnixTime(created),
                            color = secondaryTextColor,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (photo.rating != 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = AmberGold,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(1.dp))
                                Text(
                                    text = "${if (photo.rating > 0) "+" else ""}${photo.rating}",
                                    color = if (photo.rating > 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = getSecondaryAccentColor(),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${photo.commentsCount}",
                                color = getSecondaryAccentColor(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
