package com.ramzes.visavinet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramzes.visavinet.network.DialogueData
import com.ramzes.visavinet.network.FileData
import com.ramzes.visavinet.network.MessageData
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatFileSize
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.parseHtmlToBlocks
import com.ramzes.visavinet.util.RenderContentBlocks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    dialogue: DialogueData,
    messages: List<MessageData>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    currentPage: Int,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBackClick: () -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onSendMessage: (text: String, files: List<Uri>) -> Unit = { _, _ -> },
    isSendingMessage: Boolean = false,
    sendErrorMessage: String? = null,
    onClearError: () -> Unit = {},
    scrollToBottom: Boolean = false,
    onScrollComplete: () -> Unit = {},
    onTopicClick: ((topicId: Int, page: Int?, postId: Int?) -> Unit)? = null,
    onNewsClick: ((newsId: Int) -> Unit)? = null,
    onDownClick: ((downId: Int) -> Unit)? = null,
    onPhotoClick: ((photoId: Int) -> Unit)? = null,
    textMin: Int = 5,
    textMax: Int = 1000
) {
    val isDark = isDarkTheme()
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showFullscreenInput by remember { mutableStateOf(false) }
    var selectedImageForLightbox by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val primaryAccent = getPrimaryAccentColor()

    val canReply = dialogue.canReply != false

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedFiles = selectedFiles + uris
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index }
            .collect { firstVisibleIndex ->
                if (firstVisibleIndex != null && firstVisibleIndex <= 3) {
                    onLoadMore()
                }
            }
    }

    LaunchedEffect(scrollToBottom, messages.size, isLoading) {
        if (scrollToBottom && messages.isNotEmpty() && !isLoading) {
            val total = listState.layoutInfo.totalItemsCount
            val targetIndex = if (total > 0) total - 1 else messages.size + 1
            listState.scrollToItem((targetIndex - 2).coerceAtLeast(0))
            listState.animateScrollToItem(targetIndex, 10000)
            onScrollComplete()
        }
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow((context as? android.app.Activity)?.currentFocus?.windowToken, 0)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Верхняя навигация (стрелка назад и ник собеседника)
        val opponentName = dialogue.name?.ifBlank { null } ?: dialogue.login ?: "Диалог"
        val textColor = if (isDark) Color.White else LightText

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = primaryAccent
                    )
                }

                Text(
                    text = opponentName,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            isLoading && messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryAccent)
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFCF6679),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text("Повторить")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = primaryAccent,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    item {
                        val pageText = if (currentPage > 1) "Страница $currentPage" else "Начало переписки"
                        DividerWithText(text = pageText, isDark = isDark)
                    }

                    items(messages, key = { it.id }) { message ->
                        GlassMessageItem(
                            message = message,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onNewsClick = onNewsClick,
                            onDownClick = onDownClick,
                            onPhotoClick = onPhotoClick,
                            onImageClick = { url -> selectedImageForLightbox = url },
                            isDark = isDark
                        )
                    }
                }
            }
        }

        if (canReply) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (selectedFiles.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Прикрепить файл",
                            tint = primaryAccent
                        )
                    }

                    val isTextValid = messageText.trim().length in textMin..textMax

                    GlassTextField(
                        value = messageText,
                        onValueChange = {
                            if (it.length <= textMax) {
                                messageText = it
                            }
                        },
                        placeholderText = "Сообщение...",
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Развернуть",
                                tint = primaryAccent,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showFullscreenInput = true }
                            )
                        }
                    )

                    IconButton(
                        onClick = {
                            if (isTextValid && !isSendingMessage) {
                                val formatted = com.ramzes.visavinet.util.ensureParagraphTags(messageText)
                                onSendMessage(formatted, selectedFiles)
                                messageText = ""
                                selectedFiles = emptyList()
                                hideKeyboard()
                            }
                        },
                        enabled = isTextValid && !isSendingMessage,
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (isSendingMessage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = primaryAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                                tint = if (isTextValid && !isSendingMessage) primaryAccent else primaryAccent.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFullscreenInput) {
        val dialogueName = dialogue.name?.ifBlank { null } ?: dialogue.login ?: ""
        FullscreenInputModal(
            text = messageText,
            onTextChanged = { messageText = it },
            selectedFiles = selectedFiles,
            onFilesChanged = { selectedFiles = it },
            onSend = {
                if (messageText.isNotBlank() && !isSendingMessage) {
                    onSendMessage(messageText, selectedFiles)
                    messageText = ""
                    selectedFiles = emptyList()
                    showFullscreenInput = false
                    hideKeyboard()
                }
            },
            onDismiss = { showFullscreenInput = false },
            isSending = isSendingMessage,
            title = dialogueName
        )
    }

    selectedImageForLightbox?.let { imageUrl ->
        ImageLightboxDialog(
            imageUrl = imageUrl,
            onDismiss = { selectedImageForLightbox = null }
        )
    }

    sendErrorMessage?.let { error ->
        LaunchedEffect(error) {
            delay(3000)
            onClearError()
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = Color(0xFFCF6679),
            contentColor = Color.White,
            action = { Text(text = "Закрыть", color = Color.White) },
            content = { Text(text = error) }
        )
    }
}

@Composable
fun DividerWithText(text: String, isDark: Boolean = true) {
    val textColor = if (isDark) TextLightGray.copy(0.5f) else LightTextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(0.1f))
        Text(
            text = text,
            fontSize = 11.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(0.1f))
    }
}

@Composable
fun GlassMessageItem(
    message: MessageData,
    onUserClick: (String) -> Unit = {},
    onTopicClick: ((topicId: Int, page: Int?, postId: Int?) -> Unit)? = null,
    onNewsClick: ((newsId: Int) -> Unit)? = null,
    onDownClick: ((downId: Int) -> Unit)? = null,
    onPhotoClick: ((photoId: Int) -> Unit)? = null,
    onImageClick: (String) -> Unit = {},
    isDark: Boolean = true
) {
    val isOutgoing = message.type == "out"
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.6f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        GlassCard(
            modifier = Modifier.widthIn(max = 300.dp),
            isDark = isDark,
            shape = RoundedCornerShape(6.dp),
            glowColor = Color.Transparent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isOutgoing) {
                    val authorName = message.displayName
                    Text(
                        text = authorName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent,
                        modifier = Modifier.clickable {
                            val login = message.authorLogin ?: return@clickable
                            onUserClick(login)
                        }
                    )
                } else {
                    Text(
                        text = "Я",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryTextColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatUnixTime(message.createdAt),
                        fontSize = 10.sp,
                        color = secondaryTextColor,
                    )
                    if (isOutgoing) {
                        val recipientRead = message.recipientRead == true
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (recipientRead) "Прочитано" else "Не прочитано",
                            tint = if (recipientRead) primaryAccent else secondaryTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            message.text?.let { text ->
                val blocks = parseHtmlToBlocks(text)
                RenderContentBlocks(
                    blocks = blocks,
                    isDark = isDark,
                    onUserClick = onUserClick,
                    onTopicClick = onTopicClick,
                    onNewsClick = onNewsClick,
                    onDownClick = onDownClick,
                    onPhotoClick = onPhotoClick
                )
            }

            if (message.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Картинки выводим выше
                    message.files.filter { isImageFile(it) }.forEach { file ->
                        ImageFilePreview(file = file, onImageClick = onImageClick)
                    }
                    // Остальные файлы выводим ниже
                    message.files.filter { !isImageFile(it) }.forEach { file ->
                        com.ramzes.visavinet.ui.components.GlassFileCard(file = file, isDark = isDark)
                    }
                }
            }
        }
    }
}

fun isImageFile(file: FileData): Boolean {
    if (file.isImage) return true
    val ext = file.extension?.lowercase() ?: file.path?.substringAfterLast('.', "")?.lowercase() ?: ""
    return ext in listOf("jpg", "jpeg", "png", "webp", "gif")
}

@Composable
fun ImageFilePreview(file: FileData, onImageClick: (String) -> Unit) {
    val path = file.path ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onImageClick(path) }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(path)
                .crossfade(true)
                .build(),
            contentDescription = file.name ?: "Превью",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun FileItem(file: FileData, isDark: Boolean = true) {
    val context = LocalContext.current
    val fileNameColor = getPrimaryAccentColor()
    val fileSizeColor = if (isDark) TextLightGray.copy(0.6f) else LightTextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val filePath = file.path
                if (!filePath.isNullOrBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(filePath))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            tint = fileNameColor,
            modifier = Modifier.size(16.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name ?: "Файл",
                fontSize = 12.sp,
                color = fileNameColor,
                maxLines = 1
            )
            Text(
                text = formatFileSize(file.size),
                fontSize = 10.sp,
                color = fileSizeColor
            )
        }
    }
}
