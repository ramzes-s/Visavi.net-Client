package com.ramzes.visavinet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import com.ramzes.visavinet.network.ForumInfo
import com.ramzes.visavinet.network.ForumSection
import com.ramzes.visavinet.network.ForumTopic
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.dialogs.CreateTopicDialog
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatUnixTime
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ForumScreen(
    viewModel: ForumViewModel,
    currentLogin: String? = null,
    userRating: Int = 0,
    onTopicClick: (ForumTopic) -> Unit
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textColor = if (isDark) Color.White else LightText
    val accentColor = getSecondaryAccentColor()

    var showCreateTopicDialog by remember { mutableStateOf(false) }
    var createTopicError by remember { mutableStateOf<String?>(null) }

    val swipeRefreshState = rememberSwipeRefreshState(
        isRefreshing = when (viewModel.navigationState.level) {
            ForumNavigationLevel.SECTIONS -> viewModel.isLoadingSections
            ForumNavigationLevel.SECTION -> viewModel.isLoadingSection
            ForumNavigationLevel.TOPIC -> viewModel.isLoadingPosts
        }
    )

    LaunchedEffect(Unit) {
        if (viewModel.rootSections.isEmpty() && viewModel.navigationState.level == ForumNavigationLevel.SECTIONS) {
            viewModel.loadRootSections(context)
        }
    }

    LaunchedEffect(
        viewModel.isLoadingSections,
        viewModel.isLoadingSection,
        viewModel.isLoadingPosts
    ) {
        val isRefreshing = when (viewModel.navigationState.level) {
            ForumNavigationLevel.SECTIONS -> viewModel.isLoadingSections
            ForumNavigationLevel.SECTION -> viewModel.isLoadingSection
            ForumNavigationLevel.TOPIC -> viewModel.isLoadingPosts
        }
        if (!isRefreshing && swipeRefreshState.isRefreshing) {
            viewModel.refresh(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ForumTopBar(
                viewModel = viewModel,
                textColor = textColor,
                isDark = isDark
            )

            SwipeRefresh(
                state = swipeRefreshState,
                modifier = Modifier.fillMaxSize().weight(1f),
                onRefresh = { viewModel.refresh(context) }
            ) {
                when (viewModel.navigationState.level) {
                    ForumNavigationLevel.SECTIONS -> {
                        ForumSectionsList(
                            sections = viewModel.rootSections,
                            isLoading = viewModel.isLoadingSections,
                            errorMessage = viewModel.errorMessage,
                            onSectionClick = { section ->
                                viewModel.navigateToSection(section, context)
                            },
                            onRefresh = { viewModel.loadRootSections(context) },
                            isDark = isDark
                        )
                    }
                    ForumNavigationLevel.SECTION -> {
                        ForumSectionContent(
                            sectionInfo = viewModel.currentSection,
                            subsections = viewModel.subsections,
                            topics = viewModel.topics,
                            currentLogin = currentLogin,
                            isLoading = viewModel.isLoadingSection,
                            isLoadingMore = viewModel.isLoadingMoreSection,
                            errorMessage = viewModel.errorMessage,
                            onTopicClick = onTopicClick,
                            onSubsectionClick = { subsection ->
                                viewModel.navigateToSection(subsection, context)
                            },
                            onRefresh = {
                                viewModel.navigationState.sectionId?.let {
                                    viewModel.loadSection(context, it)
                                }
                            },
                            onLoadMore = { viewModel.loadMoreSection(context) },
                            isDark = isDark
                        )
                    }
                    ForumNavigationLevel.TOPIC -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Загрузка сообщений...",
                                color = textColor
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button для создания новой темы на уровне раздела
        if (viewModel.navigationState.level == ForumNavigationLevel.SECTION && viewModel.navigationState.sectionId != null) {
            FloatingActionButton(
                onClick = { 
                    createTopicError = null
                    showCreateTopicDialog = true 
                },
                containerColor = accentColor,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Создать тему")
            }
        }
    }

    if (showCreateTopicDialog && viewModel.navigationState.sectionId != null) {
        val sectionId = viewModel.navigationState.sectionId!!
        CreateTopicDialog(
            onDismiss = { 
                createTopicError = null
                showCreateTopicDialog = false 
            },
            errorMessage = createTopicError,
            onSubmit = { title, text, files ->
                createTopicError = null
                viewModel.createTopic(
                    context = context.applicationContext,
                    sectionId = sectionId,
                    title = title,
                    text = text,
                    fileUris = files,
                    userRating = userRating,
                    onSuccess = { newTopic ->
                        showCreateTopicDialog = false
                        onTopicClick(newTopic)
                    },
                    onError = { err -> createTopicError = err }
                )
            }
        )
    }
}

@Composable
fun ForumTopBar(
    viewModel: ForumViewModel,
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
            if (viewModel.navigationState.level != ForumNavigationLevel.SECTIONS) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = getSecondaryAccentColor()
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            val title = when (viewModel.navigationState.level) {
                ForumNavigationLevel.SECTIONS -> "Разделы форума"
                ForumNavigationLevel.SECTION -> {
                    viewModel.currentSection?.title 
                        ?: viewModel.navigationState.sectionTitle 
                        ?: "Подразделы"
                }
                ForumNavigationLevel.TOPIC -> "Тема"
            }
            
            Text(
                text = title,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ForumSectionContent(
    sectionInfo: ForumInfo?,
    subsections: List<ForumSection>,
    topics: List<ForumTopic>,
    currentLogin: String? = null,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    onTopicClick: (ForumTopic) -> Unit,
    onSubsectionClick: (ForumSection) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    isDark: Boolean
) {
    val listState = rememberLazyListState()
    val subsectionsCount = subsections.size
    
    LaunchedEffect(topics.size, isLoadingMore) {
        if (topics.isEmpty() || isLoadingMore) return@LaunchedEffect
        
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex == null) return@collect
                val topicIndex = lastVisibleIndex - subsectionsCount
                if (topicIndex >= topics.size - 5) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (subsections.isNotEmpty()) {
            items(subsections, key = { "sub_${it.id}" }) { subsection ->
                ForumSubsectionItem(
                    subsection = subsection,
                    onClick = { onSubsectionClick(subsection) },
                    isDark = isDark
                )
            }
        }

        if (topics.isNotEmpty()) {
            items(topics, key = { "topic_${it.id}" }) { topic ->
                ForumTopicItem(
                    topic = topic,
                    currentLogin = currentLogin,
                    onClick = { onTopicClick(topic) },
                    isDark = isDark
                )
            }
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = if (isDark) NeonCyan else LightAccent,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        
        if (!isLoading && subsections.isEmpty() && topics.isEmpty() && errorMessage == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Раздел пуст",
                        color = if (isDark) TextLightGray else LightTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ForumSectionsList(
    sections: List<ForumSection>,
    isLoading: Boolean,
    errorMessage: String?,
    onSectionClick: (ForumSection) -> Unit,
    onRefresh: () -> Unit,
    isDark: Boolean
) {
    val errorColor = Color(0xFFCF6679)

    when {
        isLoading && sections.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = getPrimaryAccentColor())
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
                        color = errorColor,
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sections, key = { it.id }) { section ->
                    ForumSectionItem(
                        section = section,
                        onClick = { onSectionClick(section) },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
fun ForumSectionItem(
    section: ForumSection,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title ?: "Без названия",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Тем: ${section.topicsCount}", fontSize = 12.sp, color = secondaryTextColor)
                    Text(text = "Сообщений: ${section.postsCount}", fontSize = 12.sp, color = secondaryTextColor)
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ForumSubsectionItem(
    subsection: ForumSection,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subsection.title ?: "Без названия",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Тем: ${subsection.topicsCount}", fontSize = 11.sp, color = secondaryTextColor)
                    Text(text = "Постов: ${subsection.postsCount}", fontSize = 11.sp, color = secondaryTextColor)
                }

                subsection.lastTopicTitle?.let { title ->
                    Text(
                        text = "➲ $title",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryTextColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ForumTopicItem(
    topic: ForumTopic,
    currentLogin: String? = null,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val primaryAccent = getPrimaryAccentColor()

    val isMyTopic = currentLogin != null && (topic.authorLogin == currentLogin || topic.authorName == currentLogin)

    val glowColor = when {
        isMyTopic -> primaryAccent.copy(alpha = 0.22f)
        topic.locked -> NeonAmber.copy(0.25f)
        topic.closed -> Color(0x33CF6679)
        else -> Color.Transparent
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        isDark = isDark,
        glowColor = glowColor,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (topic.locked) Text(text = "📌", fontSize = 13.sp)
                    if (topic.closed) Text(text = "🔒", fontSize = 13.sp)

                    Text(
                        text = topic.title ?: "Без названия",
                        fontSize = 14.sp,
                        fontWeight = if (topic.locked) FontWeight.Bold else FontWeight.Medium,
                        color = if (topic.locked) NeonAmber else textColor,
                        maxLines = 2
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        text = topic.authorName ?: topic.authorLogin ?: "Аноним",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "💬 ${topic.postsCount}", fontSize = 11.sp, color = secondaryTextColor)
                    Text(text = "👁 ${topic.visits}", fontSize = 11.sp, color = secondaryTextColor)
                }

                topic.updatedAt?.let { time ->
                    Text(
                        text = formatUnixTime(time),
                        fontSize = 10.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}
