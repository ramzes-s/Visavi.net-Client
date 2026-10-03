package com.ramzes.visavinet.ui.dialogs

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ramzes.visavinet.network.VoteData
import com.ramzes.visavinet.ui.components.VoteDualButton
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.DeviceUtils
import com.ramzes.visavinet.util.formatUnixTime

/**
 * Универсальный диалог голосования за запись (пост форума, комментарий и т.д.)
 */
@Composable
fun ItemVoteDialog(
    title: String = "Оценка записи",
    author: String,
    createdAt: Long?,
    textSnippet: String? = null,
    rating: Int,
    vote: VoteData?,
    isOwn: Boolean = false,
    isVoting: Boolean = false,
    isDark: Boolean = isDarkTheme(),
    isTablet: Boolean = false,
    onVoteUp: () -> Unit,
    onVoteDown: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogMaxWidth = DeviceUtils.dialogMaxWidth(isTabletExplicit = isTablet)

    val primaryAccent = getPrimaryAccentColor()
    val backdropColor = if (isDark) Color(0xD0090B10) else Color(0xD0E2E8F0)
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary

    val cardShape = RoundedCornerShape(16.dp)
    val baseCardBg = if (isDark) {
        if (isAmoledTheme()) Color(0xF405070A) else Color(0xEE111827)
    } else {
        Color(0xF6F8FAFC)
    }

    val cardGradient = Brush.linearGradient(
        colors = listOf(
            primaryAccent.copy(alpha = if (isDark) 0.16f else 0.10f),
            baseCardBg,
            baseCardBg,
            primaryAccent.copy(alpha = if (isDark) 0.08f else 0.04f)
        ),
        start = Offset(0f, 0f),
        end = Offset(800f, 800f)
    )

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            primaryAccent.copy(alpha = 0.70f),
            primaryAccent.copy(alpha = 0.30f),
            if (isDark) Color(0x20FFFFFF) else Color(0x18000000)
        )
    )

    val effectiveVote = remember(vote, isOwn) {
        (vote ?: VoteData(own = isOwn)).copy(own = isOwn)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        var blurModifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .clickable(onClick = onDismiss)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurModifier = blurModifier.blur(20.dp)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = blurModifier)

            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(cardGradient)
                        .border(width = 1.dp, brush = borderBrush, shape = cardShape),
                    color = Color.Transparent,
                    shape = cardShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Шапка диалога: иконка, заголовок и крестик закрытия
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                        )

                        // Информация об авторе и дате
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = author,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )

                            createdAt?.let { time ->
                                Text(
                                    text = formatUnixTime(time),
                                    fontSize = 11.sp,
                                    color = secondaryTextColor,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }

                        // Превью текста (если передано)
                        if (!textSnippet.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0x12FFFFFF) else Color(0x08000000),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "«$textSnippet»",
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = textColor.copy(alpha = 0.85f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Компонент VoteDualButton
                        VoteDualButton(
                            vote = effectiveVote,
                            rating = rating,
                            onVoteUp = onVoteUp,
                            onVoteDown = onVoteDown,
                            isLoading = isVoting,
                            isDark = isDark,
                            isCompact = false
                        )
                    }
                }
            }
        }
    }
}
