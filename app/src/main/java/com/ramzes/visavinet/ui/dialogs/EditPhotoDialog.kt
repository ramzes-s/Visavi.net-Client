package com.ramzes.visavinet.ui.dialogs

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramzes.visavinet.network.PhotoItem
import com.ramzes.visavinet.ui.components.FormattingToolbar
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassProfileCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.VideoPlaceholder
import com.ramzes.visavinet.ui.components.insertBbTag
import com.ramzes.visavinet.ui.theme.*

@Composable
fun EditPhotoDialog(
    photo: PhotoItem,
    onDismiss: () -> Unit,
    onSubmit: (title: String, text: String, closed: Boolean) -> Unit,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    titleMin: Int = 3,
    titleMax: Int = 100,
    textMax: Int = 3000
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val primaryAccent = getPrimaryAccentColor()
    val backdropColor = if (isDark) Color(0xC0090B10) else Color(0xC0F0F4F8)
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary

    var titleText by remember(photo.id) {
        mutableStateOf(photo.title ?: "")
    }
    var contentText by remember(photo.id) {
        mutableStateOf(photo.text ?: "")
    }
    var closedComments by remember(photo.id) {
        mutableStateOf(photo.closed)
    }
    var showFullscreenInput by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var backdropModifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .clickable(enabled = !isSubmitting, onClick = onDismiss)

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
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                contentAlignment = Alignment.Center
            ) {
                GlassProfileCard(
                    modifier = Modifier.fillMaxSize(),
                    isDark = isDark,
                    shape = RoundedCornerShape(20.dp),
                    accentColor = primaryAccent
                ) {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Верхняя панель заголовка
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Редактирование фото",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                enabled = !isSubmitting
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = textColor.copy(alpha = if (isSubmitting) 0.4f else 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Превью текущего изображения/видео
                        val primaryFile = photo.primaryMedia
                        val isVideo = photo.isVideo || primaryFile?.isVideo == true

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0x331E293B) else Color(0x1F64748B))
                        ) {
                            if (isVideo) {
                                VideoPlaceholder(
                                    modifier = Modifier.fillMaxSize(),
                                    isDark = isDark,
                                    accentColor = primaryAccent,
                                    iconSize = 36.dp,
                                    showLabel = true
                                )
                            } else if (primaryFile?.path != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(primaryFile.path)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = photo.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Поле ввода названия
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Название *",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryTextColor
                            )
                            Text(
                                text = "${titleText.length}/$titleMax",
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        GlassTextField(
                            value = titleText,
                            onValueChange = {
                                if (!isSubmitting && it.length <= titleMax) {
                                    titleText = it
                                }
                            },
                            placeholderText = "Введите название фотографии...",
                            isDark = isDark
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Поле ввода описания
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Описание (необязательно)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryTextColor
                            )
                            Text(
                                text = "${contentText.length}/$textMax",
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        FormattingToolbar(
                            onInsertTag = { tagStart, tagEnd ->
                                if (!isSubmitting && contentText.length <= textMax) {
                                    contentText = insertBbTag(contentText, tagStart, tagEnd)
                                }
                            },
                            onExpandFullscreen = { showFullscreenInput = true },
                            isDark = isDark
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        GlassTextField(
                            value = contentText,
                            onValueChange = {
                                if (!isSubmitting && it.length <= textMax) {
                                    contentText = it
                                }
                            },
                            placeholderText = "Краткое описание или история снимка...",
                            singleLine = false,
                            maxLines = 5,
                            modifier = Modifier.heightIn(min = 90.dp),
                            isDark = isDark
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Опция: Закрыть комментирование
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSubmitting) {
                                    closedComments = !closedComments
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = closedComments,
                                onCheckedChange = { closedComments = it },
                                enabled = !isSubmitting,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = primaryAccent,
                                    uncheckedColor = secondaryTextColor.copy(alpha = 0.6f)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Закрыть комментарии",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    text = "Запретить пользователям комментировать фотографию",
                                    fontSize = 11.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        // Ошибка
                        errorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = err,
                                color = Color(0xFFCF6679),
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Кнопки управления
                        val isTitleValid = titleText.trim().length in titleMin..titleMax
                        val canSubmit = isTitleValid && !isSubmitting

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isSubmitting
                            ) {
                                Text(
                                    text = "Отмена",
                                    color = secondaryTextColor,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            GlassButton(
                                onClick = {
                                    if (canSubmit) {
                                        onSubmit(
                                            titleText.trim(),
                                            contentText.trim(),
                                            closedComments
                                        )
                                    }
                                },
                                enabled = canSubmit,
                                isDark = isDark,
                                accentColor = primaryAccent
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Сохранить",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullscreenInput) {
        FullscreenInputModal(
            text = contentText,
            onTextChanged = {
                if (it.length <= textMax) {
                    contentText = it
                }
            },
            textMin = 0,
            textMax = textMax,
            onSend = {
                showFullscreenInput = false
            },
            onDismiss = { showFullscreenInput = false },
            title = if (titleText.isNotBlank()) "Описание: $titleText" else "Описание фото"
        )
    }
}
