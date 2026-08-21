package com.ramzes.visavinet

import android.text.Html
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramzes.visavinet.network.UserData
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassProfileCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.isDateToday

@Composable
fun ProfileScreen(user: UserData, statusMessage: String?) {
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            statusMessage?.let { msg ->
                Surface(
                    color = primaryAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = msg,
                        color = primaryAccent,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            GlassProfileCard(
                modifier = Modifier.fillMaxWidth(),
                isDark = isDark,
                shape = RoundedCornerShape(12.dp),
                accentColor = primaryAccent
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Аватар увеличенного размера (166.dp) с неоновой тенью
                    Box(
                        modifier = Modifier
                            .size(166.dp)
                            .drawBehind {
                                drawIntoCanvas { canvas ->
                                    val paint = Paint()
                                    val fp = paint.asFrameworkPaint()
                                    fp.color = primaryAccent.copy(alpha = 0.45f).toArgb()
                                    fp.setShadowLayer(26f, 0f, 4f, primaryAccent.copy(alpha = 0.45f).toArgb())
                                    canvas.drawCircle(
                                        Offset(size.width / 2f, size.height / 2f),
                                        size.width / 2f,
                                        paint
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(user.picture)
                                .placeholder(R.drawable.ic_default_avatar)
                                .error(R.drawable.ic_default_avatar)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, primaryAccent.copy(alpha = 0.6f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stripHtml(user.login).ifBlank { "Неизвестный" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )

                    user.name?.let { name ->
                        val cleanName = stripHtml(name)
                        if (cleanName.isNotBlank() && cleanName != user.login) {
                            Text(
                                text = cleanName,
                                fontSize = 14.sp,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Бейдж роли
                    RoleBadge(level = user.level, isDark = isDark)

                    user.status?.let { status ->
                        val cleanStatus = stripHtml(status)
                        if (cleanStatus.isNotBlank()) {
                            Text(
                                text = cleanStatus,
                                color = secondaryTextColor,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    user.lastLogin?.let { lastTime ->
                        val isToday = isDateToday(lastTime)
                        val lastLoginColor = if (isToday) textColor else secondaryTextColor.copy(0.7f)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Был: ${formatUnixTime(lastTime)}",
                            color = lastLoginColor,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GlassInfoBlock("КЦ", user.point.toString(), isDark = isDark)
                        GlassInfoBlock("ЧАТЛЫ", formatMoney(user.money), isDark = isDark)
                        GlassInfoBlock("РЕЙТИНГ", user.rating.toString(), isDark = isDark)
                    }

                    if (hasExtraInfo(user)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val rawInfo = stripHtml(user.info)
                            val cleanInfo = if (rawInfo.length > 120) rawInfo.take(120).trimEnd() + "…" else rawInfo
                            if (cleanInfo.isNotBlank()) {
                                ProfileDetailRow("О себе:", cleanInfo, isDark, maxLines = 2)
                            }
                            val cleanSite = stripHtml(user.site)
                            if (cleanSite.isNotBlank()) {
                                ProfileDetailRow("Сайт:", cleanSite, isDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun stripHtml(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

private fun hasExtraInfo(user: UserData): Boolean {
    val cleanInfo = stripHtml(user.info)
    val cleanSite = stripHtml(user.site)
    return cleanInfo.isNotBlank() || cleanSite.isNotBlank()
}

@Composable
fun RoleBadge(level: String?, isDark: Boolean) {
    val (roleName, roleColor) = when (level?.lowercase()) {
        "boss" -> "BOSS" to Color(0xFF8B5CF6)
        "admin" -> "Администратор" to FieryRed
        "moder", "moderator" -> "Модератор" to AmberGold
        "editor" -> "Редактор" to EmeraldGreen
        "banned" -> "Заблокирован" to Color(0xFF7F1D1D)
        else -> "Пользователь" to getPrimaryAccentColor()
    }

    Surface(
        color = roleColor.copy(alpha = 0.25f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = roleName.uppercase(),
            color = roleColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ProfileDetailRow(
    label: String,
    value: String,
    isDark: Boolean,
    maxLines: Int = Int.MAX_VALUE
) {
    val textColor = if (isDark) Color.White else LightText
    val labelColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = labelColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = textColor,
            fontWeight = FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun GlassInfoBlock(label: String, value: String, isDark: Boolean) {
    val textColor = if (isDark) Color.White else LightText
    val labelColor = if (isDark) TextLightGray.copy(0.6f) else LightTextSecondary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = textColor
        )
    }
}

private fun formatMoney(money: Long): String {
    return when {
        money >= 1_000_000_000 -> String.format("%.2f млрд", money / 1_000_000_000.0)
        money >= 1_000_000 -> String.format("%.2f млн", money / 1_000_000.0)
        money >= 1_000 -> String.format("%.1f тыс", money / 1_000.0)
        else -> money.toString()
    }
}
