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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ramzes.visavinet.ui.components.FormattingToolbar
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.components.applyTagToTextFieldValue
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.HtmlVisualTransformation
import com.ramzes.visavinet.util.ensureParagraphTags

@Composable
fun FullscreenInputModal(
    text: String,
    onTextChanged: (String) -> Unit,
    selectedFiles: List<Uri> = emptyList(),
    onFilesChanged: (List<Uri>) -> Unit = {},
    textMin: Int = 5,
    textMax: Int = 1000,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    isSending: Boolean = false,
    title: String = ""
) {
    val isDark = isDarkTheme()
    val primaryAccent = getPrimaryAccentColor()
    val textColor = if (isDark) Color.White else LightText
    val backdropColor = if (isDark) Color(0xF5090B10) else Color(0xF5F0F4F8)

    val htmlTransformation = remember(primaryAccent) { HtmlVisualTransformation() }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
    }
    val focusRequester = remember { FocusRequester() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesChanged(selectedFiles + uris)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var blurModifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .clickable(onClick = onDismiss)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurModifier = blurModifier.blur(24.dp)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = blurModifier)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxSize(),
                    isDark = isDark,
                    shape = RoundedCornerShape(8.dp),
                    glowColor = primaryAccent.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Шапка
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Свернуть",
                                    tint = primaryAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Панель тегов <strong>, <i>, <u>, <s>, <pre class="code"><code> с интерактивной подсветкой активного тега
                        FormattingToolbar(
                            textFieldValue = textFieldValue,
                            onInsertTag = { tagStart, tagEnd ->
                                val newValue = applyTagToTextFieldValue(textFieldValue, tagStart, tagEnd)
                                textFieldValue = newValue
                                onTextChanged(newValue.text)
                                try {
                                    focusRequester.requestFocus()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            onExpandFullscreen = null,
                            isDark = isDark
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Поле ввода: теги скрыты VisualTransformation, сохраняется курсор и фокус
                        GlassTextField(
                            value = textFieldValue,
                            onValueChange = { newValue ->
                                if (newValue.text.length <= textMax) {
                                    textFieldValue = newValue
                                    onTextChanged(newValue.text)
                                }
                            },
                            placeholderText = "Текст сообщения...",
                            singleLine = false,
                            maxLines = 40,
                            visualTransformation = htmlTransformation,
                            focusRequester = focusRequester,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            isDark = isDark
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedFiles.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                items(selectedFiles) { uri ->
                                    AssistChip(
                                        onClick = { onFilesChanged(selectedFiles - uri) },
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
                        }

                        val isTextValid = textFieldValue.text.trim().length in textMin..textMax

                        // Управление
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
                                    text = "Прикрепить файл",
                                    color = primaryAccent,
                                    fontSize = 13.sp
                                )
                            }

                            GlassButton(
                                onClick = {
                                    val formatted = ensureParagraphTags(textFieldValue.text)
                                    onTextChanged(formatted)
                                    onSend()
                                },
                                enabled = isTextValid && !isSending,
                                isDark = isDark,
                                accentColor = primaryAccent
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Отправить",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Отправить", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
