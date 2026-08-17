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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
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
import androidx.compose.ui.text.style.TextOverflow
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

data class QuoteInfo(
    val author: String,
    val text: String
)

@Composable
fun FullscreenInputModal(
    text: String,
    onTextChanged: (String) -> Unit,
    selectedFiles: List<Uri> = emptyList(),
    onFilesChanged: (List<Uri>) -> Unit = {},
    replyToUser: String? = null,
    onRemoveReplyToUser: (() -> Unit)? = null,
    quoteInfo: QuoteInfo? = null,
    onRemoveQuote: (() -> Unit)? = null,
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

    LaunchedEffect(text) {
        if (text != textFieldValue.text) {
            val newSelection = if (textFieldValue.selection.end <= text.length) {
                textFieldValue.selection
            } else {
                TextRange(text.length)
            }
            textFieldValue = TextFieldValue(text, selection = newSelection)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesChanged(selectedFiles + uris)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(100)
            textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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

                        // Блок бейджей обращения и цитирования
                        if (!replyToUser.isNullOrBlank() || quoteInfo != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Бейдж обращения к пользователю
                                if (!replyToUser.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) Color(0x3300E5FF) else Color(0x2200E5FF),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = primaryAccent.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AlternateEmail,
                                                contentDescription = null,
                                                tint = primaryAccent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = replyToUser,
                                                color = primaryAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (onRemoveReplyToUser != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Удалить обращение",
                                                    tint = primaryAccent.copy(alpha = 0.8f),
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { onRemoveReplyToUser() }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Бейдж цитаты
                                if (quoteInfo != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDark) Color(0x20FFFFFF) else Color(0x15000000),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (isDark) Color(0x30FFFFFF) else Color(0x25000000)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FormatQuote,
                                                contentDescription = null,
                                                tint = primaryAccent,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .padding(top = 1.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = quoteInfo.author,
                                                    color = primaryAccent,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = quoteInfo.text,
                                                    color = if (isDark) TextLightGray else LightTextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (onRemoveQuote != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = onRemoveQuote,
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Удалить цитату",
                                                        tint = if (isDark) TextLightGray else LightTextSecondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
