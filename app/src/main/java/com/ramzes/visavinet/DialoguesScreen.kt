package com.ramzes.visavinet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.ramzes.visavinet.network.DialogueData
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.ContentBlock
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.parseHtmlToBlocks
import com.ramzes.visavinet.util.parseInlineHtmlTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialoguesScreen(
    dialogues: List<DialogueData>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    readDialogues: Set<Int> = emptySet(),
    onDialogueClick: (DialogueData) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
) {
    val isDark = isDarkTheme()
    val listState = rememberLazyListState()
    val accentColor = if (isDark) NeonCyan else LightAccent

    val swipeRefreshState = rememberSwipeRefreshState(
        isRefreshing = isLoading
    )

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= dialogues.size - 3) {
                    onLoadMore()
                }
            }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && swipeRefreshState.isRefreshing) {
            onRefresh()
        }
    }

    when {
        isLoading && dialogues.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        }

        errorMessage != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFCF6679),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh) {
                        Text("Повторить")
                    }
                }
            }
        }

        else -> {
            SwipeRefresh(
                state = swipeRefreshState,
                modifier = Modifier.fillMaxSize(),
                onRefresh = onRefresh
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dialogues, key = { it.id }) { dialogue ->
                        DialogueItem(
                            dialogue = dialogue,
                            isRead = readDialogues.contains(dialogue.id),
                            onClick = { onDialogueClick(dialogue) },
                            isDark = isDark
                        )
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = accentColor,
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

@Composable
fun DialogueItem(
    dialogue: DialogueData,
    isRead: Boolean = false,
    onClick: () -> Unit,
    isDark: Boolean = true
) {
    val hasUnread = !isRead && dialogue.allReading == false
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val unreadColor = getSecondaryAccentColor()
    val readCheckColor = DarkNavyBlue

    val isIncoming = dialogue.type == "in"
    val isOutgoing = dialogue.type == "out"

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        glowColor = if (hasUnread) unreadColor.copy(alpha = 0.3f) else Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (dialogue.name?.ifBlank { null } ?: dialogue.login) ?: "Неизвестно",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        if (hasUnread) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                color = unreadColor,
                                shape = CircleShape
                            ) {}
                        }
                    }

                    val dateText = formatUnixTime(dialogue.createdAt)
                    if (dateText.isNotEmpty()) {
                        Text(
                            text = dateText,
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isIncoming) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Входящее",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (isOutgoing) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Исходящее",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    dialogue.text?.let { text ->
                        val cleanHtml = text
                            .replace(Regex("</?p[^>]*>", RegexOption.IGNORE_CASE), " ")
                            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
                            .trim()
                        val annotatedPreview = parseInlineHtmlTags(cleanHtml, isDark).first

                        Text(
                            text = annotatedPreview,
                            fontSize = 13.sp,
                            color = if (hasUnread) textColor else secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isOutgoing) {
                        val recipientRead = dialogue.recipientRead == true
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (recipientRead) "Прочитано" else "Не прочитано",
                            tint = if (recipientRead) readCheckColor else secondaryTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
