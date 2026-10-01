package com.ramzes.visavinet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatFileSize
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.parseHtmlToBlocks
import com.ramzes.visavinet.util.ContentBlock
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
    onSendMessage: (text: String, files: List<Uri>, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
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
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val draftKey = remember(dialogue.id, dialogue.authorLogin) {
        com.ramzes.visavinet.util.DraftsManager.dialogueKey("${dialogue.id}_${dialogue.authorLogin}")
    }
    var messageText by rememberSaveable(dialogue.id) {
        mutableStateOf(com.ramzes.visavinet.util.DraftsManager.getDraft(context, draftKey))
    }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showFullscreenInput by remember { mutableStateOf(false) }
    var lightboxImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var lightboxInitialIndex by remember { mutableIntStateOf(0) }
    val primaryAccent = getPrimaryAccentColor()

    val canReply = dialogue.canReply != false

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
                            onImageClick = { url ->
                                lightboxImages = listOf(url)
                                lightboxInitialIndex = 0
                            },
                            onImagesClick = { urls, idx ->
                                lightboxImages = urls
                                lightboxInitialIndex = idx
                            },
                            isDark = isDark
                        )
                    }
                }
            }
        }

        if (canReply) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.Transparent
            ) {
                GlassButton(
                    onClick = { showFullscreenInput = true },
                    modifier = Modifier.fillMaxWidth(),
                    isDark = isDark,
                    accentColor = primaryAccent
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (messageText.isNotBlank()) "Написать сообщение (черновик)" else "Написать сообщение",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showFullscreenInput) {
        val dialogueName = dialogue.name?.ifBlank { null } ?: dialogue.login ?: "Диалог"
        FullscreenInputModal(
            text = messageText,
            onTextChanged = {
                messageText = it
                com.ramzes.visavinet.util.DraftsManager.saveDraft(context, draftKey, it)
            },
            selectedFiles = selectedFiles,
            onFilesChanged = { selectedFiles = it },
            textMin = textMin,
            textMax = textMax,
            onSend = {
                val isMessageValid = messageText.trim().length in textMin..textMax
                if (isMessageValid && !isSendingMessage) {
                    val formatted = com.ramzes.visavinet.util.ensureParagraphTags(messageText.trim())
                    onSendMessage(formatted, selectedFiles) {
                        messageText = ""
                        com.ramzes.visavinet.util.DraftsManager.clearDraft(context, draftKey)
                        selectedFiles = emptyList()
                        showFullscreenInput = false
                        hideKeyboard()
                    }
                }
            },
            onDismiss = { showFullscreenInput = false },
            isSending = isSendingMessage,
            title = dialogueName
        )
    }

    if (lightboxImages.isNotEmpty()) {
        val dialogueTitle = dialogue.name?.ifBlank { null } ?: dialogue.login ?: "Диалог"
        ImageLightboxDialog(
            images = lightboxImages,
            initialPage = lightboxInitialIndex,
            title = dialogueTitle,
            onDismiss = { lightboxImages = emptyList() }
        )
    }

    sendErrorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
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
    onImagesClick: ((List<String>, Int) -> Unit)? = null,
    isDark: Boolean = true
) {
    val isOutgoing = message.type == "out"
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.6f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    val allMessageImages = remember(message.text, message.files) {
        val textImages: List<String> = message.text?.let { t ->
            parseHtmlToBlocks(t).filterIsInstance<ContentBlock.ImageBlock>().map { it.url }
        } ?: emptyList()
        val fileImages: List<String> = message.files.filter { isImageFile(it) }.mapNotNull { it.path }
        (textImages + fileImages).distinct()
    }

    val handleMessageImageClick: (String) -> Unit = { url ->
        if (allMessageImages.isNotEmpty() && onImagesClick != null) {
            val idx = allMessageImages.indexOf(url).coerceAtLeast(0)
            onImagesClick(allMessageImages, idx)
        } else {
            onImageClick(url)
        }
    }

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
                val blocks = remember(text) { parseHtmlToBlocks(text) }
                RenderContentBlocks(
                    blocks = blocks,
                    isDark = isDark,
                    onUserClick = onUserClick,
                    onTopicClick = onTopicClick,
                    onNewsClick = onNewsClick,
                    onDownClick = onDownClick,
                    onPhotoClick = onPhotoClick,
                    onImageClick = handleMessageImageClick
                )
            }

            if (message.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Картинки выводим выше
                    message.files.filter { isImageFile(it) }.forEach { file ->
                        ImageFilePreview(file = file, onImageClick = handleMessageImageClick)
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
            .height(180.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onImageClick(path) }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(path)
                .crossfade(true)
                .build(),
            contentDescription = file.name ?: "Превью",
            modifier = Modifier.fillMaxSize(),
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
