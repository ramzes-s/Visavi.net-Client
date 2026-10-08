package com.ramzes.visavinet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.ramzes.visavinet.network.OnlineUser
import com.ramzes.visavinet.ui.components.GlassBadge
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.getUserRoleBadgeInfo
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.TextRenderPrefs
import com.ramzes.visavinet.util.parseInlineHtmlTags
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun OnlineScreen(
    viewModel: OnlineViewModel,
    onUserClick: (String) -> Unit
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.scrollItemIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoading)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.scrollItemIndex = index
                viewModel.scrollOffset = offset
            }
    }

    LaunchedEffect(Unit) {
        // Список онлайн никогда не кешируется: при каждом открытии вкладки загружаем его заново
        viewModel.refresh(context)
        listState.scrollToItem(0)
    }

    LaunchedEffect(viewModel.users.size, viewModel.isLoadingMore) {
        if (viewModel.users.isEmpty() || viewModel.isLoadingMore) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= viewModel.users.size - 3) {
                    viewModel.loadMore(context)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя панель заголовка и статистики
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Сейчас на сайте",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        IconButton(onClick = { viewModel.refresh(context) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                tint = primaryAccent
                            )
                        }
                    }

                    // Чипы со статистикой онлайна
                    viewModel.meta?.let { meta ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OnlineStatChip(
                                icon = Icons.Default.Person,
                                text = "Пользователи: ${meta.users}",
                                color = primaryAccent,
                                isDark = isDark
                            )
                            OnlineStatChip(
                                icon = Icons.Default.People,
                                text = "Гости: ${meta.guests}",
                                color = secondaryTextColor,
                                isDark = isDark
                            )
                        }
                    }
                }
            }

            SwipeRefresh(
                state = swipeRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                onRefresh = { viewModel.refresh(context) }
            ) {
                when {
                    viewModel.isLoading && viewModel.users.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = primaryAccent,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    viewModel.errorMessage != null && viewModel.users.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = viewModel.errorMessage ?: "Ошибка",
                                    color = Color(0xFFEF4444),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.refresh(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                                ) {
                                    Text("Повторить", color = Color.White)
                                }
                            }
                        }
                    }
                    viewModel.users.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Пользователей онлайн нет",
                                color = secondaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = viewModel.users,
                                key = { it.login }
                            ) { user ->
                                OnlineUserItem(
                                    user = user,
                                    isDark = isDark,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onUserClick = onUserClick
                                )
                            }

                            if (viewModel.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = primaryAccent,
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
private fun OnlineStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    isDark: Boolean
) {
    Surface(
        color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OnlineUserItem(
    user: OnlineUser,
    isDark: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    onUserClick: (String) -> Unit
) {
    val roleBadge = remember(user.level) { getUserRoleBadgeInfo(user.level) }

    Box(modifier = Modifier.fillMaxWidth()) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUserClick(user.login) },
            isDark = isDark,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(user.avatar ?: R.drawable.ic_default_avatar)
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Аватар",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val endReservedPadding = if (roleBadge != null) {
                        if (roleBadge.first.length > 6) 96.dp else 56.dp
                    } else {
                        0.dp
                    }

                    Text(
                        text = user.displayName,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = endReservedPadding)
                    )

                    if (!user.name.isNullOrBlank() && user.name != user.login) {
                        Text(
                            text = "@${user.login}",
                            fontSize = 11.sp,
                            color = secondaryTextColor,
                            maxLines = 1
                        )
                    }

                    val cleanStatus = remember(user.status) { user.status?.let { stripHtml(it) }?.trim() }
                    if (!cleanStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cleanStatus,
                            fontSize = 12.sp,
                            color = if (isDark) Color.White else Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Плашка роли в правом верхнем углу блока (BOSS, Администратор, Модератор) — как в списке всех пользователей
        if (roleBadge != null) {
            GlassBadge(
                text = roleBadge.first,
                color = roleBadge.second,
                isDark = isDark,
                shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onUserClick(user.login) }
            )
        }
    }
}
