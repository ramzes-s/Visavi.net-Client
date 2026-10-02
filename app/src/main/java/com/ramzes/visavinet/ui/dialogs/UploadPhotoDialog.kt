package com.ramzes.visavinet.ui.dialogs

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramzes.visavinet.ui.components.FormattingToolbar
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassProfileCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.insertBbTag
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.DraftsManager

@Composable
fun UploadPhotoDialog(
    onDismiss: () -> Unit,
    onSubmit: (title: String, text: String, closed: Boolean, imageUris: List<Uri>) -> Unit,
    isSubmitting: Boolean = false,
    progressText: String? = null,
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

    val titleDraftKey = remember { DraftsManager.galleryUploadTitleKey() }
    val contentDraftKey = remember { DraftsManager.galleryUploadTextKey() }

    var titleText by remember {
        mutableStateOf(DraftsManager.getDraft(context, titleDraftKey))
    }
    var contentText by remember {
        mutableStateOf(DraftsManager.getDraft(context, contentDraftKey))
    }
    var closedComments by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showFullscreenInput by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val combined = (selectedImages + uris).distinct()
            selectedImages = combined
        }
    }

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
                        // Верхняя плашка с заголовком и кнопкой закрытия
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
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Добавить фото",
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

                        // Зона выбора и предпросмотра фото
                        if (selectedImages.isEmpty()) {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clickable(enabled = !isSubmitting) {
                                        imagePickerLauncher.launch("image/*")
                                    },
                                isDark = isDark,
                                shape = RoundedCornerShape(12.dp),
                                glowColor = primaryAccent.copy(alpha = 0.15f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Выбрать фото",
                                        tint = primaryAccent,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Нажмите, чтобы выбрать фото",
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "JPG, PNG, WEBP, GIF (до 20 МБ)",
                                        color = secondaryTextColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Главное превью первого фото
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0x331E293B) else Color(0x1F64748B))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(selectedImages.first())
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Превью фото",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Кнопка удаления первого фото
                                    IconButton(
                                        onClick = {
                                            selectedImages = selectedImages.drop(1)
                                        },
                                        enabled = !isSubmitting,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .background(Color(0x80000000), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Горизонтальная лента дополнительных фото и кнопка добавления еще
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(selectedImages.drop(1)) { uri ->
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isDark) Color(0x331E293B) else Color(0x1F64748B))
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(uri)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = {
                                                    selectedImages = selectedImages - uri
                                                },
                                                enabled = !isSubmitting,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(20.dp)
                                                    .background(Color(0x80000000), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Удалить",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    item {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = primaryAccent.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable(enabled = !isSubmitting) {
                                                    imagePickerLauncher.launch("image/*")
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Добавить ещё",
                                                tint = primaryAccent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
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
                                    DraftsManager.saveDraft(context, titleDraftKey, it)
                                }
                            },
                            placeholderText = "Введите название фотографии...",
                            isDark = isDark
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Описание
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
                                    DraftsManager.saveDraft(context, contentDraftKey, contentText)
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
                                    DraftsManager.saveDraft(context, contentDraftKey, it)
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

                        // Индикатор прогресса
                        if (isSubmitting) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = primaryAccent,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = progressText ?: "Публикация...",
                                    fontSize = 13.sp,
                                    color = primaryAccent,
                                    fontWeight = FontWeight.Medium
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
                        val isPhotosValid = selectedImages.isNotEmpty()
                        val canSubmit = isTitleValid && isPhotosValid && !isSubmitting

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
                                            closedComments,
                                            selectedImages
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
                                        text = "Опубликовать",
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
                    DraftsManager.saveDraft(context, contentDraftKey, it)
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
