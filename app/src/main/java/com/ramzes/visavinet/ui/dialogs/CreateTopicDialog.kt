package com.ramzes.visavinet.ui.dialogs

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ramzes.visavinet.ui.components.FormattingToolbar
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassProfileCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.insertBbTag
import com.ramzes.visavinet.ui.theme.*

@Composable
fun CreateTopicDialog(
    onDismiss: () -> Unit,
    onSubmit: (title: String, text: String, files: List<Uri>) -> Unit,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    titleMin: Int = 3,
    titleMax: Int = 50,
    textMin: Int = 5,
    textMax: Int = 5000
) {
    val isDark = isDarkTheme()
    val primaryAccent = getPrimaryAccentColor()
    val backdropColor = if (isDark) Color(0xC0090B10) else Color(0xC0F0F4F8)

    var titleText by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showFullscreenInput by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedFiles = selectedFiles + uris
        }
    }

    val textColor = if (isDark) Color.White else LightText

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var backdropModifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .clickable(onClick = onDismiss)

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
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center
            ) {
                GlassProfileCard(
                    modifier = Modifier.fillMaxWidth(),
                    isDark = isDark,
                    shape = RoundedCornerShape(16.dp),
                    accentColor = primaryAccent
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Новая тема",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = textColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    GlassTextField(
                        value = titleText,
                        onValueChange = {
                            if (it.length <= titleMax) {
                                titleText = it
                            }
                        },
                        placeholderText = "Заголовок темы...",
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FormattingToolbar(
                        onInsertTag = { tagStart, tagEnd ->
                            if (contentText.length <= textMax) {
                                contentText = insertBbTag(contentText, tagStart, tagEnd)
                            }
                        },
                        onExpandFullscreen = { showFullscreenInput = true },
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    GlassTextField(
                        value = contentText,
                        onValueChange = {
                            if (it.length <= textMax) {
                                contentText = it
                            }
                        },
                        placeholderText = "Текст темы...",
                        singleLine = false,
                        maxLines = 6,
                        modifier = Modifier.heightIn(min = 100.dp),
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedFiles.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(selectedFiles) { uri ->
                                AssistChip(
                                    onClick = { selectedFiles = selectedFiles - uri },
                                    label = { Text("Файл", fontSize = 11.sp) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val isTitleValid = titleText.trim().length in titleMin..titleMax
                    val isTextValid = contentText.trim().length in textMin..textMax
                    val isFormValid = isTitleValid && isTextValid

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Файл",
                                color = primaryAccent,
                                fontSize = 13.sp
                            )
                        }

                        GlassButton(
                            onClick = {
                                if (isFormValid && !isSubmitting) {
                                    onSubmit(titleText, contentText, selectedFiles)
                                }
                            },
                            enabled = isFormValid && !isSubmitting,
                            isDark = isDark,
                            accentColor = primaryAccent
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Создать", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = Color(0xFFCF6679),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (showFullscreenInput) {
        FullscreenInputModal(
            text = contentText,
            onTextChanged = { contentText = it },
            selectedFiles = selectedFiles,
            onFilesChanged = { selectedFiles = it },
            textMin = textMin,
            textMax = textMax,
            onSend = {
                val isTitleValid = titleText.trim().length in titleMin..titleMax
                val isTextValid = contentText.trim().length in textMin..textMax
                if (isTitleValid && isTextValid && !isSubmitting) {
                    onSubmit(titleText, contentText, selectedFiles)
                    showFullscreenInput = false
                }
            },
            onDismiss = { showFullscreenInput = false },
            isSending = isSubmitting,
            title = "Создание темы: " + titleText.ifBlank { "Без названия" }
        )
    }
}
