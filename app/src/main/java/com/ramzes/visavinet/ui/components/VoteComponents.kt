package com.ramzes.visavinet.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramzes.visavinet.network.VoteData
import com.ramzes.visavinet.ui.theme.*

/**
 * Универсальный компонент для голосования только "ЗА" (VoteButton)
 * Используется в новостях, где доступен только положительный голос.
 */
@Composable
fun VoteButton(
    vote: VoteData?,
    rating: Int,
    onVoteUp: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isDark: Boolean = isDarkTheme(),
    isCompact: Boolean = true
) {
    val hasVotedUp = vote?.isVotedUp == true
    val isOwn = vote?.own == true
    val canVote = !isOwn && !hasVotedUp && !isLoading

    val positiveColor = Color(0xFF10B981) // Зеленый
    val negativeColor = Color(0xFFEF4444) // Красный
    val neutralColor = if (isDark) TextLightGray.copy(alpha = 0.8f) else LightTextSecondary

    val ratingColor = when {
        hasVotedUp -> positiveColor
        rating > 0 && isCompact -> positiveColor
        rating < 0 -> negativeColor
        else -> neutralColor
    }

    val iconColor = when {
        hasVotedUp -> positiveColor
        else -> neutralColor
    }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            hasVotedUp -> (if (isDark) positiveColor.copy(alpha = 0.15f) else positiveColor.copy(alpha = 0.12f))
            else -> (if (isDark) Color(0x0DFFFFFF) else Color(0x06000000))
        },
        label = "VoteBgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            hasVotedUp -> positiveColor.copy(alpha = 0.4f)
            else -> (if (isDark) Color(0x18FFFFFF) else Color(0x10000000))
        },
        label = "VoteBorderColor"
    )

    val shape = if (isCompact) RoundedCornerShape(6.dp) else CircleShape
    val paddingValues = if (isCompact) {
        PaddingValues(horizontal = 6.dp, vertical = 3.dp)
    } else {
        PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape)
            .then(
                if (canVote) {
                    Modifier.clickable(onClick = onVoteUp)
                } else {
                    Modifier
                }
            )
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isCompact) 12.dp else 16.dp),
                    color = neutralColor,
                    strokeWidth = 1.5.dp
                )
            } else {
                Icon(
                    imageVector = if (hasVotedUp) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = if (canVote) "Проголосовать за" else if (hasVotedUp) "Вы проголосовали" else "Рейтинг",
                    tint = iconColor,
                    modifier = Modifier.size(if (isCompact) 13.dp else 18.dp)
                )
            }

            val ratingText = if (rating > 0) "+$rating" else rating.toString()
            Text(
                text = ratingText,
                color = ratingColor,
                fontSize = if (isCompact) 12.sp else 14.sp,
                fontWeight = if (hasVotedUp || rating != 0) FontWeight.Bold else FontWeight.Medium
            )

            if (!isCompact && canVote) {
                Text(
                    text = "Нравится",
                    color = neutralColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 2.dp)
                )
            } else if (!isCompact && hasVotedUp) {
                Text(
                    text = "Вы оценили",
                    color = positiveColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

/**
 * Универсальный компонент для двустороннего голосования ("ЗА" / "ПРОТИВ" с возможностью смены голоса)
 * Используется в галерее, загрузках и других разделах с полным голосованием.
 */
@Composable
fun VoteDualButton(
    vote: VoteData?,
    rating: Int,
    onVoteUp: () -> Unit,
    onVoteDown: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isDark: Boolean = isDarkTheme(),
    isCompact: Boolean = false
) {
    val hasVotedUp = vote?.isVotedUp == true
    val hasVotedDown = vote?.isVotedDown == true
    val isOwn = vote?.own == true

    // Можно голосовать ЗА: если не автор, не идет загрузка и текущий голос еще не "+"
    val canVoteUp = !isOwn && !hasVotedUp && !isLoading
    // Можно голосовать ПРОТИВ: если не автор, не идет загрузка и текущий голос еще не "-"
    val canVoteDown = !isOwn && !hasVotedDown && !isLoading

    val positiveColor = Color(0xFF10B981) // Зеленый
    val negativeColor = Color(0xFFEF4444) // Красный
    val neutralColor = if (isDark) TextLightGray.copy(alpha = 0.8f) else LightTextSecondary
    val accentColor = getPrimaryAccentColor()

    val ratingColor = when {
        rating > 0 -> positiveColor
        rating < 0 -> negativeColor
        else -> neutralColor
    }

    val upIconColor = when {
        hasVotedUp -> positiveColor
        canVoteUp -> (if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.8f))
        else -> neutralColor.copy(alpha = 0.4f)
    }

    val downIconColor = when {
        hasVotedDown -> negativeColor
        canVoteDown -> (if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.8f))
        else -> neutralColor.copy(alpha = 0.4f)
    }

    val containerBgColor = when {
        hasVotedUp -> (if (isDark) positiveColor.copy(alpha = 0.15f) else positiveColor.copy(alpha = 0.12f))
        hasVotedDown -> (if (isDark) negativeColor.copy(alpha = 0.15f) else negativeColor.copy(alpha = 0.12f))
        !isCompact -> (if (isDark) Color(0x22FFFFFF) else Color(0x11000000))
        else -> (if (isDark) Color(0x18FFFFFF) else Color(0x0C000000))
    }

    val containerBorderColor = when {
        hasVotedUp -> positiveColor.copy(alpha = 0.4f)
        hasVotedDown -> negativeColor.copy(alpha = 0.4f)
        !isCompact -> (if (isDark) Color(0x33FFFFFF) else Color(0x1A000000))
        else -> (if (isDark) Color(0x22FFFFFF) else Color(0x12000000))
    }

    val shape = RoundedCornerShape(if (isCompact) 8.dp else 12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerBgColor, shape)
            .border(1.dp, containerBorderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
                val ratingText = if (rating > 0) "+$rating" else rating.toString()
                Text(
                    text = ratingText,
                    color = ratingColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Кнопка "+" (Upvote)
                Box(
                    modifier = Modifier
                        .then(
                            if (canVoteUp) {
                                Modifier.clickable(onClick = onVoteUp)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasVotedUp) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Голосовать за (+)",
                        tint = upIconColor,
                        modifier = Modifier.size(if (isCompact) 16.dp else 20.dp)
                    )
                }

                // Значение рейтинга
                val ratingText = if (rating > 0) "+$rating" else rating.toString()
                Text(
                    text = ratingText,
                    color = ratingColor,
                    fontSize = if (isCompact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Кнопка "-" (Downvote)
                Box(
                    modifier = Modifier
                        .then(
                            if (canVoteDown) {
                                Modifier.clickable(onClick = onVoteDown)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasVotedDown) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Голосовать против (-)",
                        tint = downIconColor,
                        modifier = Modifier.size(if (isCompact) 16.dp else 20.dp)
                    )
                }
            }
        }
    }
}
