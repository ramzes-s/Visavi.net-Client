@file:Suppress("DEPRECATION")

package com.ramzes.visavinet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.ramzes.visavinet.network.FileData
import com.ramzes.visavinet.network.ForumPost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.ramzes.visavinet.network.ForumTopic
import com.ramzes.visavinet.network.TopicInfo
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.components.GlassTextField
import com.ramzes.visavinet.ui.dialogs.FullscreenInputModal
import com.ramzes.visavinet.ui.dialogs.ImageLightboxDialog
import com.ramzes.visavinet.ui.dialogs.QuoteInfo
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.ensureParagraphTags
import com.ramzes.visavinet.util.formatFileSize
import com.ramzes.visavinet.util.formatUnixTime
import com.ramzes.visavinet.util.parseHtmlToBlocks
import com.ramzes.visavinet.util.sanitizeHtml
import com.ramzes.visavinet.util.ContentBlock
import com.ramzes.visavinet.util.RenderContentBlocks

fun buildFinalForumPostText(rawText: String, replyToUser: String?, quoteInfo: QuoteInfo?): String {
    val parts = mutableListOf<String>()
    if (quoteInfo != null && quoteInfo.text.isNotBlank()) {
        parts.add("<blockquote class=\"post-quote\"><b>${quoteInfo.author}</b>: ${quoteInfo.text}</blockquote>")
    }
    val textWithUser = if (!replyToUser.isNullOrBlank()) {
        val userLink = "<a class=\"user\" href=\"/users/$replyToUser\">@$replyToUser</a> "
        if (rawText.startsWith(userLink)) rawText else "$userLink$rawText"
    } else {
        rawText
    }
    if (textWithUser.isNotBlank()) {
        parts.add(textWithUser)
    }
    return ensureParagraphTags(parts.joinToString("\n"))
}

@Composable
fun ForumTopicScreen(
    viewModel: ForumViewModel,
    topic: ForumTopic,
    currentLogin: String?,
    userRating: Int = 0,
    isTabletLayout: Boolean = false,
    onBackClick: () -> Unit,
    onUserClick: (String) -> Unit = {},
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit = { _, _, _ -> },
    onNewsClick: (newsId: Int) -> Unit = {},
    onDownClick: (downId: Int) -> Unit = {},
    onPhotoClick: (photoId: Int) -> Unit = {},
    textMin: Int = 5,
    textMax: Int = 5000
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val primaryAccent = getPrimaryAccentColor()

    val prefs = remember { context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE) }
    val pinFirstTopicPost = remember { prefs.getBoolean("pin_first_topic_post", false) }
    var isFirstPostExpanded by rememberSaveable(topic.id) { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var hasScrolledToBottom by remember { mutableStateOf(false) }

    val initialLastSeenPostId = rememberSaveable(topic.id) {
        com.ramzes.visavinet.util.ForumTopicReadTracker.getLastSeenPostId(context, topic.id)
    }

    val firstNewPostIndex = remember(viewModel.posts, initialLastSeenPostId) {
        if (initialLastSeenPostId > 0) {
            viewModel.posts.indexOfFirst { it.id > initialLastSeenPostId }
        } else {
            -1
        }
    }

    LaunchedEffect(viewModel.posts) {
        val maxPostId = viewModel.posts.maxOfOrNull { it.id } ?: 0
        if (maxPostId > 0) {
            com.ramzes.visavinet.util.ForumTopicReadTracker.saveLastSeenPostId(context, topic.id, maxPostId)
        }
    }

    val draftKey = remember(topic.id) { com.ramzes.visavinet.util.DraftsManager.forumReplyKey(topic.id) }
    var replyText by rememberSaveable(topic.id) {
        mutableStateOf(com.ramzes.visavinet.util.DraftsManager.getDraft(context, draftKey))
    }
    var replyToUser by remember { mutableStateOf<String?>(null) }
    var quoteInfo by remember { mutableStateOf<QuoteInfo?>(null) }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSendingReply by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf<String?>(null) }
    var showFullscreenInput by remember { mutableStateOf(false) }
    var lightboxImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var lightboxInitialIndex by remember { mutableIntStateOf(0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedFiles = selectedFiles + uris
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(
        isRefreshing = viewModel.isLoadingPosts
    )

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow((context as? android.app.Activity)?.currentFocus?.windowToken, 0)
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState, viewModel.posts.size, hasScrolledToBottom) {
        if (!hasScrolledToBottom) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index }
            .distinctUntilChanged()
            .collect { firstVisibleIndex ->
                if (hasScrolledToBottom && firstVisibleIndex != null && firstVisibleIndex <= 2 && !viewModel.isLoadingOlderPosts) {
                    viewModel.loadOlderPosts(context) { addedCount ->
                        coroutineScope.launch {
                            val currentFirstIndex = listState.firstVisibleItemIndex
                            val currentFirstOffset = listState.firstVisibleItemScrollOffset
                            listState.scrollToItem(currentFirstIndex + addedCount, currentFirstOffset)
                        }
                    }
                }
            }
    }

    LaunchedEffect(listState, viewModel.posts.size, hasScrolledToBottom) {
        if (!hasScrolledToBottom) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (hasScrolledToBottom && lastVisibleIndex != null && lastVisibleIndex >= viewModel.posts.size - 2 && !viewModel.isLoadingNewerPosts) {
                    viewModel.loadNewerPosts(context)
                }
            }
    }

    LaunchedEffect(viewModel.posts.size, viewModel.isLoadingPosts) {
        if (viewModel.posts.isNotEmpty() && !viewModel.isLoadingPosts && !hasScrolledToBottom) {
            val total = listState.layoutInfo.totalItemsCount
            val targetIndex = if (total > 0) total - 1 else viewModel.posts.size + 1
            
            // Позиционируем на конец темы
            listState.scrollToItem(maxOf(0, targetIndex))
            hasScrolledToBottom = true
        }
    }

    LaunchedEffect(topic.id) {
        hasScrolledToBottom = false
        viewModel.navigateToTopic(topic, context)
    }

    LaunchedEffect(viewModel.isLoadingPosts) {
        if (!viewModel.isLoadingPosts && swipeRefreshState.isRefreshing) {
            viewModel.refresh(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isTabletLayout) {
                val sectionTitle = viewModel.currentTopic?.forum?.title
                    ?: viewModel.navigationState.sectionTitle
                    ?: viewModel.currentSection?.title
                    ?: "Раздел"

                ForumTopicTopBar(
                    sectionTitle = sectionTitle,
                    onBackClick = onBackClick,
                    textColor = textColor,
                    isDark = isDark
                )
            }

            if (pinFirstTopicPost && viewModel.firstTopicPost != null) {
                PinnedFirstPostCard(
                    post = viewModel.firstTopicPost!!,
                    topic = topic,
                    topicInfo = viewModel.currentTopic,
                    isExpanded = isFirstPostExpanded,
                    onToggleExpand = { isFirstPostExpanded = !isFirstPostExpanded },
                    currentLogin = currentLogin,
                    onUserClick = onUserClick,
                    onUserReplyClick = { login ->
                        replyToUser = login
                        showFullscreenInput = true
                    },
                    onQuoteClick = { author, text ->
                        quoteInfo = QuoteInfo(author, text)
                        showFullscreenInput = true
                    },
                    onTopicClick = onTopicClick,
                    onNewsClick = onNewsClick,
                    onDownClick = onDownClick,
                    onPhotoClick = onPhotoClick,
                    onImageClick = { url ->
                        lightboxImages = listOf(url)
                        lightboxInitialIndex = 0
                    },
                    onImagesClick = { images, index ->
                        lightboxImages = images
                        lightboxInitialIndex = index
                    },
                    isDark = isDark
                )
            }

            SwipeRefresh(
                state = swipeRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                onRefresh = { viewModel.refresh(context) }
            ) {
                when {
                    viewModel.isLoadingPosts && viewModel.posts.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primaryAccent)
                        }
                    }
                    viewModel.errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = viewModel.errorMessage ?: "Ошибка",
                                    color = Color(0xFFCF6679),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.refresh(context) }) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (viewModel.isLoadingOlderPosts) {
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
                                val pageText = if (viewModel.minLoadedPage > 1)
                                    "Страница ${viewModel.minLoadedPage} из ${viewModel.postsLastPage}"
                                else
                                    "Начало темы"
                                ForumDividerWithText(text = pageText, isDark = isDark)
                            }

                            item {
                                TopicInfoCard(
                                    topic = topic,
                                    topicInfo = viewModel.currentTopic,
                                    isDark = isDark
                                )
                            }

                            itemsIndexed(viewModel.posts, key = { _, post -> post.id }) { index, post ->
                                if (index == firstNewPostIndex) {
                                    ForumDividerWithText(text = "Новые сообщения", isDark = isDark, highlight = true)
                                }
                                ForumPostItem(
                                    post = post,
                                    currentLogin = currentLogin,
                                    onUserClick = onUserClick,
                                    onTopicClick = onTopicClick,
                                    onNewsClick = onNewsClick,
                                    onDownClick = onDownClick,
                                    onPhotoClick = onPhotoClick,
                                    onUserReplyClick = { userLogin ->
                                        replyToUser = userLogin
                                        showFullscreenInput = true
                                    },
                                    onQuoteClick = { author, rawText ->
                                        val cleanText = sanitizeHtml(rawText).replace(Regex("\\s+"), " ").trim()
                                        quoteInfo = QuoteInfo(author = author, text = cleanText)
                                        showFullscreenInput = true
                                    },
                                    onImageClick = { url ->
                                        lightboxImages = listOf(url)
                                        lightboxInitialIndex = 0
                                    },
                                    onImagesClick = { urls, imgIndex ->
                                        lightboxImages = urls
                                        lightboxInitialIndex = imgIndex
                                    },
                                    isDark = isDark
                                )
                            }

                            if (viewModel.isLoadingNewerPosts) {
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

                            viewModel.appendPostsErrorMessage?.let { err ->
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = err, fontSize = 12.sp, color = Color(0xFFCF6679))
                                            TextButton(onClick = {
                                                viewModel.loadOlderPosts(context)
                                                viewModel.loadNewerPosts(context)
                                            }) {
                                                Text("Повторить", fontSize = 12.sp, color = primaryAccent)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!topic.closed) {
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
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (replyText.isNotBlank()) "Ответить в тему (черновик)" else "Ответить в тему",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showFullscreenInput) {
        val topicTitle = topic.title ?: ""
        FullscreenInputModal(
            text = replyText,
            onTextChanged = {
                replyText = it
                com.ramzes.visavinet.util.DraftsManager.saveDraft(context, draftKey, it)
            },
            selectedFiles = selectedFiles,
            onFilesChanged = { selectedFiles = it },
            replyToUser = replyToUser,
            onRemoveReplyToUser = { replyToUser = null },
            quoteInfo = quoteInfo,
            onRemoveQuote = { quoteInfo = null },
            textMin = textMin,
            textMax = textMax,
            onSend = {
                val isReplyValid = replyText.trim().length in textMin..textMax || (quoteInfo != null && replyText.isNotBlank())
                if (isReplyValid && !isSendingReply) {
                    isSendingReply = true
                    val formatted = buildFinalForumPostText(
                        rawText = replyText,
                        replyToUser = replyToUser,
                        quoteInfo = quoteInfo
                    )
                    viewModel.createPost(
                        context = context.applicationContext,
                        topicId = topic.id,
                        text = formatted,
                        fileUris = selectedFiles,
                        userRating = userRating,
                        onSuccess = {
                            replyText = ""
                            com.ramzes.visavinet.util.DraftsManager.clearDraft(context, draftKey)
                            replyToUser = null
                            quoteInfo = null
                            selectedFiles = emptyList()
                            isSendingReply = false
                            showFullscreenInput = false
                            hideKeyboard()
                        },
                        onError = { err ->
                            replyError = err
                            isSendingReply = false
                        }
                    )
                }
            },
            onDismiss = { showFullscreenInput = false },
            isSending = isSendingReply,
            title = topicTitle
        )
    }

    if (lightboxImages.isNotEmpty()) {
        ImageLightboxDialog(
            images = lightboxImages,
            initialPage = lightboxInitialIndex,
            onDismiss = { lightboxImages = emptyList() }
        )
    }
}

@Composable
fun PinnedFirstPostCard(
    post: ForumPost,
    topic: ForumTopic,
    topicInfo: TopicInfo?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    currentLogin: String?,
    onUserClick: (String) -> Unit,
    onUserReplyClick: (String) -> Unit,
    onQuoteClick: (author: String, text: String) -> Unit,
    onTopicClick: (topicId: Int, page: Int?, postId: Int?) -> Unit,
    onNewsClick: (newsId: Int) -> Unit,
    onDownClick: (downId: Int) -> Unit,
    onPhotoClick: (photoId: Int) -> Unit,
    onImageClick: (String) -> Unit,
    onImagesClick: ((List<String>, Int) -> Unit)?,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryAccent = getPrimaryAccentColor()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val isMyPost = currentLogin != null && (post.authorLogin == currentLogin || post.authorName == currentLogin)

    val cleanPreview = remember(post.text) {
        (post.text ?: "")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .animateContentSize(),
        isDark = isDark,
        shape = RoundedCornerShape(8.dp),
        glowColor = primaryAccent.copy(alpha = 0.22f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Закреплено",
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )

                    val topicTitle = topic.title ?: topicInfo?.title ?: "Тема"

                    Text(
                        text = topicTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoChip(text = "💬 ${topicInfo?.postsCount ?: topic.postsCount}", isDark = isDark)
                        InfoChip(text = "👁 ${topicInfo?.visits ?: topic.visits}", isDark = isDark)
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        tint = primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (!isExpanded && cleanPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanPreview,
                    fontSize = 11.sp,
                    color = secondaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() }
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val author = post.authorName ?: post.authorLogin ?: "Аноним"
                            Text(
                                text = author,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent,
                                modifier = Modifier.clickable {
                                    post.authorLogin?.let { onUserClick(it) }
                                }
                            )

                            if (post.rating != 0) {
                                val ratingColor = if (post.rating > 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Рейтинг",
                                        tint = ratingColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = if (post.rating > 0) "+${post.rating}" else "${post.rating}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ratingColor
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0x0DFFFFFF) else Color(0x06000000),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                            ),
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(start = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                if (!isMyPost) {
                                    IconButton(
                                        onClick = {
                                            val userLogin = post.authorLogin ?: post.authorName ?: "user"
                                            onUserReplyClick(userLogin)
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AlternateEmail,
                                            contentDescription = "Обратиться по имени",
                                            tint = primaryAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val authorName = post.authorName ?: post.authorLogin ?: "Аноним"
                                            val textContent = post.text ?: ""
                                            onQuoteClick(authorName, textContent)
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatQuote,
                                            contentDescription = "Цитировать",
                                            tint = primaryAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (post.createdAt != null) {
                                        Text(
                                            text = "•",
                                            fontSize = 10.sp,
                                            color = secondaryTextColor.copy(alpha = 0.4f),
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                    }
                                }

                                post.createdAt?.let { time ->
                                    Text(
                                        text = formatUnixTime(time),
                                        fontSize = 10.sp,
                                        color = secondaryTextColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val allPostImages = remember(post.text, post.files) {
                        val textImages: List<String> = post.text?.let { t ->
                            parseHtmlToBlocks(t).filterIsInstance<ContentBlock.ImageBlock>().map { it.url }
                        } ?: emptyList()
                        val fileImages: List<String> = post.files.filter { isImageFile(it) }.mapNotNull { it.path }
                        (textImages + fileImages).distinct()
                    }

                    val handlePostImageClick: (String) -> Unit = { url ->
                        if (allPostImages.isNotEmpty() && onImagesClick != null) {
                            val idx = allPostImages.indexOf(url).coerceAtLeast(0)
                            onImagesClick(allPostImages, idx)
                        } else {
                            onImageClick(url)
                        }
                    }

                    post.text?.let { text ->
                        val blocks = remember(text) { parseHtmlToBlocks(text) }
                        RenderContentBlocks(
                            blocks = blocks,
                            isDark = isDark,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onNewsClick = onNewsClick,
                            onDownClick = onDownClick,
                            onPhotoClick = onPhotoClick,
                            onImageClick = handlePostImageClick
                        )
                    }

                    if (post.files.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val imageFiles = remember(post.files) { post.files.filter { isImageFile(it) } }
                        imageFiles.forEach { file ->
                            ImageFilePreview(file = file, onImageClick = handlePostImageClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForumTopicTopBar(
    sectionTitle: String,
    onBackClick: () -> Unit,
    textColor: Color,
    isDark: Boolean
) {
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
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = getPrimaryAccentColor()
                )
            }

            Text(
                text = sectionTitle,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TopicInfoCard(
    topic: ForumTopic,
    topicInfo: TopicInfo?,
    isDark: Boolean
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        shape = RoundedCornerShape(6.dp),
        glowColor = getPrimaryAccentColor().copy(alpha = 0.15f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val forum = topicInfo?.forum ?: topic.forum
            val breadcrumbs = if (forum != null) {
                if (forum.parent != null && !forum.parent.title.isNullOrBlank()) {
                    "${forum.parent.title} ❯ ${forum.title ?: ""}"
                } else {
                    forum.title
                }
            } else null

            if (!breadcrumbs.isNullOrBlank()) {
                Text(
                    text = breadcrumbs,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = getPrimaryAccentColor(),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Text(
                text = topicInfo?.title ?: topic.title ?: "Тема",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Автор: ${topicInfo?.authorName ?: topic.authorName ?: topic.authorLogin ?: "Аноним"}",
                        fontSize = 11.sp,
                        color = secondaryTextColor
                    )
                    
                    topicInfo?.createdAt?.let { time ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Создана: ${formatUnixTime(time)}",
                            fontSize = 10.sp,
                            color = secondaryTextColor
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoChip(text = "💬 ${topicInfo?.postsCount ?: topic.postsCount}", isDark = isDark)
                    InfoChip(text = "👁 ${topicInfo?.visits ?: topic.visits}", isDark = isDark)
                }
            }
        }
    }
}

@Composable
fun InfoChip(text: String, isDark: Boolean) {
    val textColor = if (isDark) TextLightGray else LightTextSecondary

    Surface(
        color = if (isDark) Color(0x20FFFFFF) else Color(0x20000000),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ForumPostItem(
    post: ForumPost,
    currentLogin: String?,
    onUserClick: (String) -> Unit,
    onTopicClick: ((topicId: Int, page: Int?, postId: Int?) -> Unit)? = null,
    onNewsClick: ((newsId: Int) -> Unit)? = null,
    onDownClick: ((downId: Int) -> Unit)? = null,
    onPhotoClick: ((photoId: Int) -> Unit)? = null,
    onUserReplyClick: (String) -> Unit,
    onQuoteClick: (author: String, text: String) -> Unit,
    onImageClick: (String) -> Unit,
    onImagesClick: ((List<String>, Int) -> Unit)? = null,
    isDark: Boolean
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()
    val isMyPost = currentLogin != null && (post.authorLogin == currentLogin || post.authorName == currentLogin)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        shape = RoundedCornerShape(6.dp),
        glowColor = if (isMyPost) primaryAccent.copy(alpha = 0.22f) else Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = post.authorName ?: post.authorLogin ?: "Аноним",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent,
                        modifier = Modifier.clickable {
                            val login = post.authorLogin ?: return@clickable
                            onUserClick(login)
                        }
                    )

                    if (post.rating != 0) {
                        val ratingColor = if (post.rating > 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Рейтинг",
                                tint = ratingColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (post.rating > 0) "+${post.rating}" else "${post.rating}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ratingColor
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isDark) Color(0x0DFFFFFF) else Color(0x06000000),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                    ),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        if (!isMyPost) {
                            IconButton(
                                onClick = {
                                    val userLogin = post.authorLogin ?: post.authorName ?: "user"
                                    onUserReplyClick(userLogin)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlternateEmail,
                                    contentDescription = "Обратиться по имени",
                                    tint = primaryAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val author = post.authorName ?: post.authorLogin ?: "Аноним"
                                    val textContent = post.text ?: ""
                                    onQuoteClick(author, textContent)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatQuote,
                                    contentDescription = "Цитировать",
                                    tint = primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (post.createdAt != null) {
                                Text(
                                    text = "•",
                                    fontSize = 10.sp,
                                    color = secondaryTextColor.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }

                        post.createdAt?.let { time ->
                            Text(
                                text = formatUnixTime(time),
                                fontSize = 10.sp,
                                color = secondaryTextColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val allPostImages = remember(post.text, post.files) {
                val textImages: List<String> = post.text?.let { t ->
                    parseHtmlToBlocks(t).filterIsInstance<ContentBlock.ImageBlock>().map { it.url }
                } ?: emptyList()
                val fileImages: List<String> = post.files.filter { isImageFile(it) }.mapNotNull { it.path }
                (textImages + fileImages).distinct()
            }

            val handlePostImageClick: (String) -> Unit = { url ->
                if (allPostImages.isNotEmpty() && onImagesClick != null) {
                    val idx = allPostImages.indexOf(url).coerceAtLeast(0)
                    onImagesClick(allPostImages, idx)
                } else {
                    onImageClick(url)
                }
            }

            post.text?.let { text ->
                val blocks = remember(text) { parseHtmlToBlocks(text) }
                RenderContentBlocks(
                    blocks = blocks,
                    isDark = isDark,
                    onUserClick = onUserClick,
                    onTopicClick = onTopicClick,
                    onNewsClick = onNewsClick,
                    onDownClick = onDownClick,
                    onPhotoClick = onPhotoClick,
                    onImageClick = handlePostImageClick
                )
            }

            if (post.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val imageFiles = remember(post.files) { post.files.filter { isImageFile(it) } }
                    val otherFiles = remember(post.files) { post.files.filter { !isImageFile(it) } }

                    if (imageFiles.size == 1) {
                        ImageFilePreview(
                            file = imageFiles[0],
                            onImageClick = handlePostImageClick
                        )
                    } else if (imageFiles.size > 1) {
                        val pagerState = rememberPagerState(initialPage = 0) { imageFiles.size }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0x33000000) else Color(0x10000000))
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val file = imageFiles[page]
                                val filePath = file.path
                                if (!filePath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(filePath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = file.name,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                handlePostImageClick(filePath)
                                            },
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${imageFiles.size}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Остальные файлы выводим ниже
                    otherFiles.forEach { file ->
                        com.ramzes.visavinet.ui.components.GlassFileCard(file = file, isDark = isDark)
                    }
                }
            }
        }
    }
}

@Composable
fun ForumDividerWithText(text: String, isDark: Boolean, highlight: Boolean = false) {
    val accent = getPrimaryAccentColor()
    val textColor = when {
        highlight -> accent
        isDark -> TextLightGray.copy(0.5f)
        else -> LightTextSecondary
    }
    val lineColor = if (highlight) accent.copy(0.5f) else Color.White.copy(0.1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = lineColor)
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = lineColor)
    }
}

@Composable
fun ForumFileItem(file: FileData, isDark: Boolean) {
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
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            tint = fileNameColor,
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = file.name ?: "Файл", fontSize = 12.sp, color = fileNameColor, maxLines = 1)
            Text(text = formatFileSize(file.size), fontSize = 10.sp, color = fileSizeColor)
        }
    }
}
