@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.ramzes.visavinet.network.UserData
import com.ramzes.visavinet.ui.components.GlassBadge
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.TextRenderPrefs
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.parseInlineHtmlTags
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat
import java.util.Locale

@Composable
fun UsersScreen(
    viewModel: UsersViewModel,
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
        if (viewModel.users.isEmpty()) {
            viewModel.loadUsers(context, 1)
        }
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
            // Верхняя панель заголовка, поиска и сортировки
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (viewModel.isSearchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = viewModel.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        text = "Поиск по логину...",
                                        fontSize = 14.sp,
                                        color = secondaryTextColor.copy(alpha = 0.7f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Поиск",
                                        tint = primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (viewModel.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Очистить",
                                                tint = secondaryTextColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryAccent.copy(alpha = 0.7f),
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x20000000),
                                    focusedContainerColor = if (isDark) Color(0x18FFFFFF) else Color(0x0C000000),
                                    unfocusedContainerColor = if (isDark) Color(0x10FFFFFF) else Color(0x06000000),
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    cursorColor = primaryAccent
                                )
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(onClick = { viewModel.closeSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть поиск",
                                    tint = textColor
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Пользователи",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            IconButton(onClick = { viewModel.openSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Поиск пользователя",
                                    tint = primaryAccent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Переключатель сортировки (оформление как в настройках)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserSort.entries.forEach { sort ->
                                val isSelected = viewModel.currentSort == sort
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (!isSelected) {
                                                viewModel.setSort(context, sort)
                                            }
                                        }
                                        .padding(top = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = sort.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) primaryAccent else secondaryTextColor
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.5.dp)
                                            .background(
                                                if (isSelected) primaryAccent
                                                else (if (isDark) Color(0x18FFFFFF) else Color(0x10000000))
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.isSearchActive) {
                when {
                    viewModel.isSearching -> {
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
                    viewModel.searchError != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.searchError ?: "Ошибка поиска",
                                color = Color(0xFFEF4444),
                                fontSize = 14.sp
                            )
                        }
                    }
                    viewModel.searchQuery.trim().length < 2 -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = secondaryTextColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Введите логин для поиска (от 2 символов)",
                                    color = secondaryTextColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    viewModel.searchResults.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Пользователи не найдены",
                                color = secondaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = viewModel.searchResults,
                                key = { "search_${it.login ?: it.displayName}" }
                            ) { user ->
                                UserCardItem(
                                    user = user,
                                    currentSort = viewModel.currentSort,
                                    isDark = isDark,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    primaryAccent = primaryAccent,
                                    onUserClick = onUserClick
                                )
                            }
                        }
                    }
                }
            } else {
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
                                        text = viewModel.errorMessage ?: "Ошибка загрузки",
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
                                    text = "Пользователи не найдены",
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
                                    key = { it.login ?: it.displayName }
                                ) { user ->
                                    UserCardItem(
                                        user = user,
                                        currentSort = viewModel.currentSort,
                                        isDark = isDark,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor,
                                        primaryAccent = primaryAccent,
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
}

@Composable
private fun UserCardItem(
    user: UserData,
    currentSort: UserSort,
    isDark: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    primaryAccent: Color,
    onUserClick: (String) -> Unit
) {
    val avatarUrl = user.picture?.ifBlank { null } ?: user.avatar?.ifBlank { null }
    val lastLogin = user.lastLogin
    val statusText = user.status?.takeIf { it.isNotBlank() }

    val roleBadge = remember(user.level) {
        when (user.level?.lowercase()) {
            "boss" -> "BOSS" to Color(0xFF8B5CF6)
            "admin" -> "АДМИНИСТРАТОР" to FieryRed
            "moder", "moderator" -> "МОДЕРАТОР" to AmberGold
            "editor" -> "РЕДАКТОР" to EmeraldGreen
            "banned" -> "ЗАБЛОКИРОВАН" to Color(0xFF7F1D1D)
            else -> null
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable { user.login?.let(onUserClick) },
            isDark = isDark,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl ?: R.drawable.ic_default_avatar)
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Аватар",
                    modifier = Modifier
                        .size(70.dp)
                        .aspectRatio(1f)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Имя пользователя и опциональный логин (отступ справа предотвращает наложение на бейджик в углу)
                    val endReservedPadding = if (roleBadge != null) {
                        if (roleBadge.first.length > 6) 96.dp else 56.dp
                    } else {
                        0.dp
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = endReservedPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.displayName,
                            color = primaryAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!user.name.isNullOrBlank() && user.name != user.login && !user.login.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "@${user.login}",
                                fontSize = 11.5.sp,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. Статус (или дата визита, если статус отсутствует)
                    if (statusText != null) {
                        val (annotatedStatus, _) = remember(statusText, isDark, TextRenderPrefs.ignoreColoredText) {
                            parseInlineHtmlTags(statusText, isDark)
                        }
                        Text(
                            text = annotatedStatus,
                            fontSize = 12.sp,
                            color = textColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (lastLogin != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Визит: ${formatUnixTime(lastLogin)}",
                                fontSize = 11.sp,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Box(modifier = Modifier.height(16.dp))
                    }

                    // 3. Метрики (Рейтинг, КЦ, Чатлы, Визит при сортировке по активности)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Рейтинг
                        UserMetricBadge(
                            icon = Icons.Default.Star,
                            text = if (user.rating > 0) "+${user.rating}" else "${user.rating}",
                            iconColor = AmberGold,
                            textColor = if (user.rating > 0) AmberGold else secondaryTextColor
                        )

                        // КЦ (Баллы актива)
                        UserMetricBadge(
                            icon = Icons.Default.Bolt,
                            text = "${formatPoints(user.point)} КЦ",
                            iconColor = primaryAccent,
                            textColor = secondaryTextColor
                        )

                        // Чатлы (Деньги)
                        if (user.money > 0) {
                            UserMetricBadge(
                                icon = Icons.Default.Paid,
                                text = formatMoney(user.money),
                                iconColor = EmeraldGreen,
                                textColor = secondaryTextColor
                            )
                        }

                        // Если сортировка по активности и во 2 строке отображался статус — показываем визит
                        if (currentSort == UserSort.UPDATED && statusText != null && lastLogin != null) {
                            UserMetricBadge(
                                icon = Icons.Default.Schedule,
                                text = formatUnixTime(lastLogin),
                                iconColor = secondaryTextColor,
                                textColor = secondaryTextColor
                            )
                        }
                    }
                }
            }
        }

        // Плашка роли в правом верхнем углу блока (BOSS, Администратор, Модератор) — как в ленте событий
        if (roleBadge != null) {
            GlassBadge(
                text = roleBadge.first,
                color = roleBadge.second,
                isDark = isDark,
                shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { user.login?.let(onUserClick) }
            )
        }
    }
}

@Composable
private fun UserMetricBadge(
    icon: ImageVector,
    text: String,
    iconColor: Color,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatPoints(points: Int): String {
    return NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru")).format(points)
}

private fun formatMoney(money: Long): String {
    return when {
        money >= 1_000_000_000 -> String.format(Locale.US, "%.2f млрд", money / 1_000_000_000.0)
        money >= 1_000_000 -> String.format(Locale.US, "%.2f млн", money / 1_000_000.0)
        money >= 10_000 -> String.format(Locale.US, "%.1f тыс", money / 1000.0)
        else -> NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru")).format(money)
    }
}
