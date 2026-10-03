package com.ramzes.visavinet.ui.dialogs

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ramzes.visavinet.UpdateCheckState
import com.ramzes.visavinet.UpdateDownloadState
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassProfileCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.DeviceUtils
import java.io.File
import java.util.Locale

@Composable
fun AppUpdateDialog(
    updateState: UpdateCheckState.UpdateAvailable,
    downloadState: UpdateDownloadState,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit = {},
    onInstallClick: (File) -> Unit,
    onOpenBrowserClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isDarkTheme()
    val primaryAccent = getPrimaryAccentColor()
    val backdropColor = if (isDark) Color(0xC0090B10) else Color(0xC0F0F4F8)
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray else LightTextSecondary
    val dialogMaxWidth = DeviceUtils.dialogMaxWidth(defaultWidth = 480.dp)

    Dialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var backdropModifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .clickable(
                enabled = downloadState !is UpdateDownloadState.Downloading,
                onClick = onDismiss
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdropModifier = backdropModifier.blur(24.dp)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = backdropModifier)

            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center
            ) {
                GlassProfileCard(
                    modifier = Modifier.fillMaxWidth(),
                    isDark = isDark,
                    shape = RoundedCornerShape(16.dp),
                    accentColor = primaryAccent
                ) {
                    // Шапка: Иконка, Заголовок, Кнопка закрытия
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = primaryAccent.copy(alpha = 0.18f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Новая версия",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }

                        IconButton(
                            onClick = {
                                if (downloadState is UpdateDownloadState.Downloading) {
                                    onCancelDownloadClick()
                                }
                                onDismiss()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = secondaryTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Информация о версиях
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0x18FFFFFF) else Color(0x0C000000),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isDark) Color(0x22FFFFFF) else Color(0x15000000)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Версия: v${updateState.currentVersion} ➔ ${updateState.newVersion}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryAccent
                                )

                                if (updateState.apkSize > 0) {
                                    Text(
                                        text = formatBytes(updateState.apkSize),
                                        fontSize = 12.sp,
                                        color = secondaryTextColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (!updateState.releaseName.isNullOrBlank() && updateState.releaseName != updateState.newVersion) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = updateState.releaseName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }

                    // Список изменений (если есть)
                    val notes = stripMarkdown(updateState.releaseNotes)
                    if (notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Что нового:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0x12FFFFFF) else Color(0x08000000),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isDark) Color(0x1EFFFFFF) else Color(0x10000000)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = notes,
                                    fontSize = 12.sp,
                                    color = secondaryTextColor,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Блок состояний скачивания и действий
                    when (downloadState) {
                        is UpdateDownloadState.Idle -> {
                            if (!updateState.downloadUrl.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = onDismiss) {
                                        Text(
                                            text = "Позже",
                                            color = secondaryTextColor,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    GlassButton(
                                        onClick = onDownloadClick,
                                        accentColor = primaryAccent,
                                        isDark = isDark
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Обновить",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "APK-файл не прикреплен к релизу напрямую. Вы можете загрузить его со страницы GitHub.",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = onDismiss) {
                                            Text("Позже", color = secondaryTextColor)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        GlassButton(
                                            onClick = onOpenBrowserClick,
                                            accentColor = primaryAccent,
                                            isDark = isDark
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInBrowser,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Открыть GitHub",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is UpdateDownloadState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Скачивание обновления...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor
                                    )
                                    if (downloadState.progress >= 0f) {
                                        Text(
                                            text = "${(downloadState.progress * 100).toInt()}%",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryAccent
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (downloadState.progress >= 0f) {
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        color = primaryAccent,
                                        trackColor = primaryAccent.copy(alpha = 0.2f),
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        color = primaryAccent,
                                        trackColor = primaryAccent.copy(alpha = 0.2f),
                                    )
                                }

                                if (downloadState.totalBytes > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${formatBytes(downloadState.downloadedBytes)} из ${formatBytes(downloadState.totalBytes)}",
                                        fontSize = 11.5.sp,
                                        color = secondaryTextColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = onCancelDownloadClick) {
                                        Text(
                                            text = "Отмена",
                                            color = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateDownloadState.ReadyToInstall -> {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Обновление готово к установке!",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = onDismiss) {
                                        Text("Закрыть", color = secondaryTextColor)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    GlassButton(
                                        onClick = { onInstallClick(downloadState.apkFile) },
                                        accentColor = primaryAccent,
                                        isDark = isDark
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Установить",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateDownloadState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFCF6679),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = downloadState.message,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFFCF6679),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = onDismiss) {
                                        Text("Закрыть", color = secondaryTextColor)
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    TextButton(onClick = onOpenBrowserClick) {
                                        Text("Браузер", color = primaryAccent)
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    GlassButton(
                                        onClick = onDownloadClick,
                                        accentColor = primaryAccent,
                                        isDark = isDark
                                    ) {
                                        Text(
                                            text = "Повторить",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f МБ", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.US, "%.1f КБ", kb)
    }
}

/**
 * Очищает текст от символов и тегов Markdown / HTML, возвращая чистый читаемый текст.
 */
fun stripMarkdown(markdown: String?): String {
    if (markdown.isNullOrBlank()) return ""

    var result = markdown.replace("\r\n", "\n").replace('\r', '\n')

    // Удаление HTML-комментариев <!-- ... -->
    result = result.replace(Regex("<!--[\\s\\S]*?-->"), "")

    // Обработка переносов строк HTML и удаление остальных HTML-тегов
    result = result.replace(Regex("(?i)<br\\s*/?>"), "\n")
    result = result.replace(Regex("(?i)</p>"), "\n\n")
    result = result.replace(Regex("<[^>]+>"), "")

    // HTML-сущности
    result = result
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    // Блоки кода ```code``` -> оставляем только содержимое
    result = result.replace(Regex("```[^\n]*\n([\\s\\S]*?)```"), "$1")
    result = result.replace(Regex("```([\\s\\S]*?)```"), "$1")

    // Встроенный код `code` -> code
    result = result.replace(Regex("`([^`\n]+)`"), "$1")

    // Изображения ![alt](url) -> alt
    result = result.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")

    // Ссылки в формате [текст](url) -> текст
    result = result.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
    // Авто-ссылки <https://...> -> https://...
    result = result.replace(Regex("<(https?://[^>]+)>"), "$1")

    // Заголовки: # Заголовок -> Заголовок
    result = result.replace(Regex("(?m)^[ \\t]*#{1,6}[ \\t]+(.*?)(?:[ \\t]*#+)?$"), "$1")

    // Цитаты: > цитата -> цитата
    result = result.replace(Regex("(?m)^[ \\t]*>[ \\t]?"), "")

    // Горизонтальные разделители: ---, ***, ___
    result = result.replace(Regex("(?m)^[ \\t]*([-*_]){3,}[ \\t]*$"), "")

    // Чекбоксы списков задач: [ ] -> ☐, [x] -> ☑
    result = result.replace(Regex("(?i)\\[ \\] "), "☐ ").replace(Regex("(?i)\\[x\\] "), "☑ ")

    // Маркеры списков: *, +, - в начале строки -> аккуратный маркер •
    result = result.replace(Regex("(?m)^([ \\t]*)[*+-][ \\t]+"), "$1• ")

    // Полужирный и курсив:
    // ***текст*** или ___текст___
    result = result.replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "$1")
    result = result.replace(Regex("___(.*?)___"), "$1")
    // **текст** или __текст__
    result = result.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
    result = result.replace(Regex("__(.*?)__"), "$1")
    // *текст*
    result = result.replace(Regex("(?<!\\*)\\*(?!\\s)(.*?)(?<!\\s)\\*(?!\\*)"), "$1")
    // _текст_ (не затрагивая snake_case внутри слов)
    result = result.replace(Regex("(?<=^|\\s|[\"'(])_(?!\\s)(.*?)(?<!\\s)_(?=$|\\s|[\"')!?,.:;])"), "$1")

    // Зачёркнутый текст ~~текст~~
    result = result.replace(Regex("~~(.*?)~~"), "$1")

    // Удаление концевых пробелов и избыточных пустых строк
    result = result.lines().joinToString("\n") { it.trimEnd() }
    result = result.replace(Regex("\n{3,}"), "\n\n")

    return result.trim()
}
