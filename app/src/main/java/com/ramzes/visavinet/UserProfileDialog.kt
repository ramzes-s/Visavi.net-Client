package com.ramzes.visavinet

import android.os.Build
import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramzes.visavinet.network.UserData
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassProfileCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatUnixTime

@Composable
fun UserProfileDialog(
    user: UserData?,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onWriteClick: ((String) -> Unit)? = null
) {
    val isDark = isDarkTheme()
    val primaryAccent = getPrimaryAccentColor()
    val backdropColor = if (isDark) Color(0xC0090B10) else Color(0xC0F0F4F8)

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
                modifier = Modifier.fillMaxWidth(0.90f),
                contentAlignment = Alignment.Center
            ) {
                GlassProfileCard(
                    modifier = Modifier.fillMaxWidth(),
                    isDark = isDark,
                    shape = RoundedCornerShape(16.dp),
                    accentColor = primaryAccent
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primaryAccent)
                        }
                    } else if (errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFCF6679),
                                fontSize = 14.sp
                            )
                        }
                    } else if (user != null) {
                        UserProfileContent(user = user, onDismiss = onDismiss, onWriteClick = onWriteClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileContent(
    user: UserData,
    onDismiss: () -> Unit,
    onWriteClick: ((String) -> Unit)? = null
) {
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Аватар с неоновой тенью
        Box(
            modifier = Modifier
                .size(104.dp)
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val paint = Paint()
                        val fp = paint.asFrameworkPaint()
                        fp.color = primaryAccent.copy(alpha = 0.45f).toArgb()
                        fp.setShadowLayer(22f, 0f, 4f, primaryAccent.copy(alpha = 0.45f).toArgb())
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stripHtml(user.login).ifBlank { "Аноним" },
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = textColor
        )

        user.name?.let { name ->
            val cleanName = stripHtml(name)
            if (cleanName.isNotBlank() && cleanName != user.login) {
                Text(text = cleanName, fontSize = 13.sp, color = secondaryTextColor)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        RoleBadge(level = user.level, isDark = isDark)

        user.status?.let { status ->
            val cleanStatus = stripHtml(status)
            if (cleanStatus.isNotBlank()) {
                Text(
                    text = cleanStatus,
                    color = secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        user.lastLogin?.let { lastTime ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Вход: ${formatUnixTime(lastTime)}",
                color = secondaryTextColor.copy(0.7f),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GlassInfoBlock("КЦ", user.point.toString(), isDark = isDark)
            GlassInfoBlock("ЧАТЛЫ", formatMoney(user.money), isDark = isDark)
            GlassInfoBlock("РЕЙТИНГ", user.rating.toString(), isDark = isDark)
        }

        if (hasExtraInfo(user)) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(0.1f))
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val cleanInfo = stripHtml(user.info)
                if (cleanInfo.isNotBlank()) {
                    ProfileDetailRow("О себе:", cleanInfo, isDark)
                }
                val cleanSite = stripHtml(user.site)
                if (cleanSite.isNotBlank()) {
                    ProfileDetailRow("Сайт:", cleanSite, isDark)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (onWriteClick != null && user.login != null) {
            GlassButton(
                onClick = { onWriteClick(user.login) },
                modifier = Modifier.fillMaxWidth(0.85f),
                isDark = isDark
            ) {
                Text(
                    text = "НАПИСАТЬ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun hasExtraInfo(user: UserData): Boolean {
    val cleanInfo = stripHtml(user.info)
    val cleanSite = stripHtml(user.site)
    return cleanInfo.isNotBlank() || cleanSite.isNotBlank()
}

private fun formatMoney(money: Long): String {
    return when {
        money >= 1_000_000_000 -> String.format("%.2f млрд", money / 1_000_000_000.0)
        money >= 1_000_000 -> String.format("%.2f млн", money / 1_000_000.0)
        money >= 1_000 -> String.format("%.1f тыс", money / 1_000.0)
        else -> money.toString()
    }
}
