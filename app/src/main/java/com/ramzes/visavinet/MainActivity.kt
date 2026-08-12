package com.ramzes.visavinet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.ramzes.visavinet.network.ForumTopic
import com.ramzes.visavinet.service.NewMessagesService
import com.ramzes.visavinet.ui.components.*
import com.ramzes.visavinet.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val currentIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntent.value = intent
        enableEdgeToEdge()

        setContent {
            val prefs = remember { getSharedPreferences("visavi_prefs", MODE_PRIVATE) }
            val initialDarkTheme = remember { prefs.getBoolean("dark_theme", true) }
            val accentIndex = remember { prefs.getInt("accent_color_index", 0) }
            val initialPrimaryAccent = remember {
                AvailableAccentColors.getOrElse(accentIndex) { AvailableAccentColors[0] }.color
            }

            VisaviTheme(
                initialDarkTheme = initialDarkTheme,
                initialPrimaryAccent = initialPrimaryAccent
            ) {
                val systemUiController = rememberSystemUiController()
                val isDark = isDarkTheme()

                systemUiController.setStatusBarColor(
                    color = Color.Transparent,
                    darkIcons = !isDark
                )

                MainNavigation(
                    intent = currentIntent.value,
                    prefs = prefs,
                    onThemeChange = { isDarkTheme ->
                        setDarkTheme(isDarkTheme)
                        prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent.value = intent
        setIntent(intent)
    }
}

enum class Screen { Profile, Private, Forum, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    intent: Intent? = null,
    prefs: android.content.SharedPreferences,
    onThemeChange: (Boolean) -> Unit = {}
) {
    val viewModel: MainViewModel = viewModel()
    val dialoguesViewModel: DialoguesViewModel = viewModel()
    val forumViewModel: ForumViewModel = viewModel()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Profile) }

    var showMessagesScreen by remember { mutableStateOf(false) }
    var showForumTopicScreen by remember { mutableStateOf(false) }
    var selectedForumTopic by remember { mutableStateOf<ForumTopic?>(null) }

    var showUserProfile by remember { mutableStateOf(false) }
    var userProfileLoading by remember { mutableStateOf(false) }
    var userProfileError by remember { mutableStateOf<String?>(null) }
    var userProfileData by remember { mutableStateOf<com.ramzes.visavinet.network.UserData?>(null) }

    var pendingUserLogin by remember { mutableStateOf<String?>(null) }
    var shouldRefreshDialoguesFromNotification by remember { mutableStateOf(false) }

    val primaryAccent = getPrimaryAccentColor()

    fun resetSubScreens() {
        showMessagesScreen = false
        showForumTopicScreen = false
        selectedForumTopic = null
        dialoguesViewModel.backToDialogues()
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    LaunchedEffect(viewModel.currentUser, viewModel.isInitialChecking) {
        if (!viewModel.isInitialChecking && viewModel.currentUser != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val contextCompat = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                if (contextCompat != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        NewMessagesService.newMessagesCount.collect { count ->
            dialoguesViewModel.updateNewMessagesCount(count)
        }
    }

    LaunchedEffect(pendingUserLogin, dialoguesViewModel.dialogues, dialoguesViewModel.isLoadingDialogues) {
        val login = pendingUserLogin ?: return@LaunchedEffect
        if (dialoguesViewModel.isLoadingDialogues) return@LaunchedEffect
        
        val dialogue = dialoguesViewModel.dialogues.find { it.login == login }
        if (dialogue != null) {
            dialoguesViewModel.selectDialogue(dialogue, context.applicationContext)
            currentScreen = Screen.Private
            showMessagesScreen = true
        } else {
            val tempDialogue = com.ramzes.visavinet.network.DialogueData(
                id = 0,
                login = login,
                name = null,
                text = null,
                type = null,
                allReading = null,
                recipientRead = null,
                createdAtRaw = null
            )
            dialoguesViewModel.selectDialogue(tempDialogue, context.applicationContext)
            currentScreen = Screen.Private
            showMessagesScreen = true
        }
        pendingUserLogin = null
    }

    val onBackPressedDispatcher = (context as? ComponentActivity)?.onBackPressedDispatcher
    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentScreen == Screen.Private && showMessagesScreen -> {
                        dialoguesViewModel.backToDialogues()
                        showMessagesScreen = false
                    }
                    currentScreen == Screen.Forum && forumViewModel.navigationState.level == ForumNavigationLevel.TOPIC -> {
                        forumViewModel.navigateBack(context.applicationContext)
                        showForumTopicScreen = false
                        selectedForumTopic = null
                    }
                    currentScreen == Screen.Forum && forumViewModel.navigationState.level == ForumNavigationLevel.SECTION -> {
                        forumViewModel.navigateBack(context.applicationContext)
                    }
                    currentScreen == Screen.Forum && forumViewModel.navigationState.level == ForumNavigationLevel.SECTIONS -> {
                        currentScreen = Screen.Profile
                    }
                    currentScreen == Screen.Private && !showMessagesScreen -> {
                        currentScreen = Screen.Profile
                    }
                }
            }
        }
    }

    LaunchedEffect(showMessagesScreen, showForumTopicScreen, currentScreen, forumViewModel.navigationState.level) {
        backCallback.isEnabled = currentScreen == Screen.Private || currentScreen == Screen.Forum
        onBackPressedDispatcher?.addCallback(backCallback)
    }

    LaunchedEffect(Unit) {
        viewModel.checkAutoLogin(context.applicationContext)
    }

    LaunchedEffect(intent) {
        val dataUri = intent?.data
        if (dataUri != null) {
            val urlString = dataUri.toString()
            when (val target = com.ramzes.visavinet.util.parseVisaviUrl(urlString)) {
                is com.ramzes.visavinet.util.VisaviUrlTarget.User -> {
                    userProfileLoading = true
                    userProfileError = null
                    dialoguesViewModel.loadUserProfile(
                        context = context.applicationContext,
                        login = target.login,
                        onSuccess = { user ->
                            userProfileData = user
                            userProfileLoading = false
                            showUserProfile = true
                        },
                        onError = { error ->
                            userProfileError = error
                            userProfileLoading = false
                            showUserProfile = true
                        }
                    )
                }
                is com.ramzes.visavinet.util.VisaviUrlTarget.Topic -> {
                    resetSubScreens()
                    currentScreen = Screen.Forum
                    forumViewModel.navigateToTopicId(context.applicationContext, target.topicId, target.page, target.postId)
                }
                else -> {}
            }
        }

        val openDialogues = intent?.getBooleanExtra("OPEN_DIALOGUES", false) ?: false
        if (openDialogues) {
            while (viewModel.isInitialChecking && viewModel.currentUser == null) {
                kotlinx.coroutines.delay(100)
            }
            if (viewModel.currentUser != null) {
                currentScreen = Screen.Private
                showMessagesScreen = false
                shouldRefreshDialoguesFromNotification = true
            }
        }
    }

    LaunchedEffect(currentScreen, showMessagesScreen, shouldRefreshDialoguesFromNotification) {
        if (currentScreen == Screen.Private && !showMessagesScreen) {
            if (dialoguesViewModel.dialogues.isEmpty() || 
                shouldRefreshDialoguesFromNotification || 
                dialoguesViewModel.needsRefresh()) {
                dialoguesViewModel.loadDialogues(context.applicationContext)
                shouldRefreshDialoguesFromNotification = false
            }
        }
    }

    LaunchedEffect(currentScreen, forumViewModel.rootSections.isEmpty()) {
        if (currentScreen == Screen.Forum && forumViewModel.rootSections.isEmpty()) {
            forumViewModel.loadRootSections(context.applicationContext)
        }
    }

    GlassBackground {
        if (viewModel.isInitialChecking) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryAccent)
            }
        } else if (viewModel.currentUser == null) {
            LoginScreen(viewModel)
        } else {
            val isDark = isDarkTheme()
            val drawerBgColor = if (isDark) Color(0xF8090B10) else Color(0xF8F0F4F8)
            val drawerTextColor = if (isDark) Color.White else LightText
            val drawerLogoColor = primaryAccent
            val drawerSelectedItemColor = primaryAccent.copy(alpha = 0.25f)
            val drawerScrimColor = if (isDark) Color(0xCC000000) else Color(0x99000000)

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                scrimColor = drawerScrimColor,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = drawerBgColor,
                        drawerShape = RectangleShape,
                        modifier = Modifier.width(260.dp)
                    ) {
                        Spacer(Modifier.height(36.dp))

                        val logoGradient = if (isDark) {
                            listOf(primaryAccent, Color.White, primaryAccent.copy(alpha = 0.85f))
                        } else {
                            listOf(primaryAccent, DarkNavyBlue, primaryAccent)
                        }
                        val headerBorder = if (isDark) {
                            listOf(primaryAccent.copy(alpha = 0.5f), Color.White.copy(alpha = 0.2f), Color.Transparent)
                        } else {
                            listOf(primaryAccent.copy(alpha = 0.6f), LightTextSecondary.copy(alpha = 0.3f), Color.Transparent)
                        }

                        // Стилизованный премиум-логотип Visavi.net в боковом меню
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            primaryAccent.copy(alpha = 0.22f),
                                            primaryAccent.copy(alpha = 0.06f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(headerBorder),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    primaryAccent,
                                                    DarkNavyBlue
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = "Разработка",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = viewModel.siteConfig?.site?.title?.ifBlank { null } ?: "Visavi.net",
                                        style = androidx.compose.ui.text.TextStyle(
                                            brush = Brush.linearGradient(colors = logoGradient),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                    Text(
                                        text = "Developer Community",
                                        color = if (isDark) TextLightGray.copy(0.6f) else LightTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Spacer(Modifier.height(12.dp))

                        val itemColors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = drawerSelectedItemColor,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = drawerTextColor,
                            unselectedTextColor = drawerTextColor,
                            selectedIconColor = drawerLogoColor,
                            unselectedIconColor = drawerTextColor
                        )

                        NavigationDrawerItem(
                            label = { Text(text = "ПРОФИЛЬ", fontWeight = FontWeight.Bold) },
                            selected = currentScreen == Screen.Profile,
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                resetSubScreens()
                                currentScreen = Screen.Profile
                                scope.launch { drawerState.close() }
                            },
                            colors = itemColors
                        )
                        Spacer(Modifier.height(2.dp))
                        NavigationDrawerItem(
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "ДИАЛОГИ", fontWeight = FontWeight.Bold)
                                    if (dialoguesViewModel.newMessagesCount > 0) {
                                        Surface(
                                            color = primaryAccent,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "+${dialoguesViewModel.newMessagesCount}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            selected = currentScreen == Screen.Private,
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                resetSubScreens()
                                currentScreen = Screen.Private
                                dialoguesViewModel.resetNewMessagesCount()
                                scope.launch { drawerState.close() }
                            },
                            colors = itemColors
                        )
                        Spacer(Modifier.height(2.dp))
                        NavigationDrawerItem(
                            label = { Text(text = "ФОРУМ", fontWeight = FontWeight.Bold) },
                            selected = currentScreen == Screen.Forum,
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                resetSubScreens()
                                currentScreen = Screen.Forum
                                scope.launch { drawerState.close() }
                            },
                            colors = itemColors
                        )

                        Spacer(Modifier.weight(1f))

                        HorizontalDivider(color = Color.White.copy(0.1f))

                        NavigationDrawerItem(
                            label = { Text(text = "НАСТРОЙКИ", fontWeight = FontWeight.Bold) },
                            selected = currentScreen == Screen.Settings,
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                resetSubScreens()
                                currentScreen = Screen.Settings
                                scope.launch { drawerState.close() }
                            },
                            colors = itemColors
                        )

                        HorizontalDivider(color = Color.White.copy(0.1f))
                        NavigationDrawerItem(
                            label = { Text(text = "ВЫХОД", fontWeight = FontWeight.Black, color = primaryAccent) },
                            selected = false,
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                resetSubScreens()
                                viewModel.logout(context.applicationContext)
                                dialoguesViewModel.clear()
                                forumViewModel.clear()
                                NewMessagesService.stop(context)
                            },
                            colors = itemColors
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            ) {
                val textColor = if (isDark) Color.White else LightText
                val iconTint = primaryAccent

                var contentModifier: Modifier = Modifier
                if (drawerState.isOpen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    contentModifier = contentModifier.blur(20.dp)
                }

                Scaffold(
                    modifier = contentModifier,
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = {
                                val titleText = when (currentScreen) {
                                    Screen.Profile -> "Профиль"
                                    Screen.Private -> {
                                        if (showMessagesScreen && dialoguesViewModel.selectedDialogue != null) {
                                            val dialogue = dialoguesViewModel.selectedDialogue!!
                                            dialogue.name?.ifBlank { null } ?: dialogue.login ?: "Неизвестно"
                                        } else {
                                            "Диалоги"
                                        }
                                    }
                                    Screen.Forum -> {
                                        if (showForumTopicScreen && selectedForumTopic != null) {
                                            selectedForumTopic?.title ?: "Тема"
                                        } else {
                                            "Форум"
                                        }
                                    }
                                    Screen.Settings -> "Настройки"
                                }
                                Text(
                                    text = titleText,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    modifier = if (currentScreen == Screen.Private && showMessagesScreen) Modifier.clickable {
                                        dialoguesViewModel.backToDialogues()
                                        showMessagesScreen = false
                                    } else Modifier
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = null, tint = iconTint)
                                }
                            },
                            actions = {
                                if (currentScreen == Screen.Private && !showMessagesScreen) {
                                    IconButton(onClick = { dialoguesViewModel.loadDialogues(context.applicationContext) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = iconTint)
                                    }
                                }
                                if (currentScreen == Screen.Private && showMessagesScreen && dialoguesViewModel.selectedDialogue != null) {
                                    IconButton(onClick = { dialoguesViewModel.refreshMessages(context.applicationContext) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = iconTint)
                                    }
                                }
                                if (currentScreen == Screen.Forum && !showForumTopicScreen) {
                                    IconButton(onClick = { forumViewModel.refresh(context.applicationContext) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = iconTint)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    },
                    content = { padding ->
                        Box(Modifier.padding(padding)) {
                            when (currentScreen) {
                                Screen.Profile -> ProfileScreen(viewModel.currentUser!!, viewModel.statusMessage)
                                Screen.Private -> {
                                    if (showMessagesScreen && dialoguesViewModel.selectedDialogue != null) {
                                        MessagesScreen(
                                            dialogue = dialoguesViewModel.selectedDialogue!!,
                                            messages = dialoguesViewModel.messages,
                                            isLoading = dialoguesViewModel.isLoadingMessages,
                                            isLoadingMore = dialoguesViewModel.isLoadingMoreMessages,
                                            currentPage = dialoguesViewModel.messagesCurrentPage,
                                            errorMessage = dialoguesViewModel.errorMessage,
                                            onRefresh = { dialoguesViewModel.refreshMessages(context.applicationContext) },
                                            onLoadMore = { dialoguesViewModel.loadMoreMessages(context.applicationContext) },
                                            onUserClick = { login ->
                                                userProfileLoading = true
                                                userProfileError = null
                                                dialoguesViewModel.loadUserProfile(
                                                    context = context.applicationContext,
                                                    login = login,
                                                    onSuccess = { user ->
                                                        userProfileData = user
                                                        userProfileLoading = false
                                                        showUserProfile = true
                                                    },
                                                    onError = { error ->
                                                        userProfileError = error
                                                        userProfileLoading = false
                                                        showUserProfile = true
                                                    }
                                                )
                                            },
                                            onTopicClick = { topicId, page, postId ->
                                                resetSubScreens()
                                                currentScreen = Screen.Forum
                                                forumViewModel.navigateToTopicId(context.applicationContext, topicId, page, postId)
                                            },
                                            onSendMessage = { text, files ->
                                                dialoguesViewModel.sendMessage(
                                                    context = context.applicationContext,
                                                    text = text,
                                                    fileUris = files,
                                                    userRating = viewModel.currentUser?.rating ?: 0,
                                                    onSuccess = { },
                                                    onError = { error ->
                                                        dialoguesViewModel.sendErrorMessage = error
                                                    }
                                                )
                                            },
                                            isSendingMessage = dialoguesViewModel.isSendingMessage,
                                            sendErrorMessage = dialoguesViewModel.sendErrorMessage,
                                            onClearError = {
                                                dialoguesViewModel.sendErrorMessage = null
                                            },
                                            scrollToBottom = dialoguesViewModel.scrollToBottom,
                                            onScrollComplete = {
                                                dialoguesViewModel.resetScrollToBottom()
                                            }
                                        )
                                    } else {
                                        DialoguesScreen(
                                            dialogues = dialoguesViewModel.dialogues,
                                            isLoading = dialoguesViewModel.isLoadingDialogues,
                                            isLoadingMore = dialoguesViewModel.isLoadingMore,
                                            errorMessage = dialoguesViewModel.errorMessage,
                                            readDialogues = dialoguesViewModel.readDialogues,
                                            onDialogueClick = { dialogue ->
                                                val currentLogin = viewModel.currentUser?.login
                                                val dialogueLogin = dialogue.login ?: dialogue.name
                                                if (currentLogin != null && currentLogin == dialogueLogin) {
                                                    currentScreen = Screen.Profile
                                                } else {
                                                    dialoguesViewModel.selectDialogue(dialogue, context.applicationContext)
                                                    dialoguesViewModel.resetNewMessagesCount()
                                                    NewMessagesService.markAsRead()
                                                    showMessagesScreen = true
                                                }
                                            },
                                            onRefresh = {
                                                dialoguesViewModel.loadDialogues(context.applicationContext)
                                            },
                                            onLoadMore = { dialoguesViewModel.loadMoreDialogues(context.applicationContext) }
                                        )
                                    }
                                }
                                Screen.Forum -> {
                                    if (forumViewModel.navigationState.level == ForumNavigationLevel.TOPIC) {
                                        val topicObj = forumViewModel.currentTopic?.let {
                                            ForumTopic(
                                                id = it.id,
                                                title = it.title,
                                                authorLogin = it.authorLogin,
                                                authorName = it.authorName,
                                                closed = it.closed,
                                                locked = it.locked,
                                                postsCount = it.postsCount,
                                                visits = it.visits,
                                                note = it.note
                                            )
                                        } ?: selectedForumTopic ?: ForumTopic(
                                            id = forumViewModel.navigationState.topicId ?: 0,
                                            title = forumViewModel.navigationState.topicTitle ?: "Тема",
                                            authorLogin = "",
                                            authorName = null,
                                            closed = false,
                                            locked = false,
                                            postsCount = 0,
                                            visits = 0,
                                            note = null
                                        )

                                        ForumTopicScreen(
                                            viewModel = forumViewModel,
                                            topic = topicObj,
                                            currentLogin = viewModel.currentUser?.login,
                                            onBackClick = {
                                                forumViewModel.navigateBack(context.applicationContext)
                                                if (forumViewModel.navigationState.level != ForumNavigationLevel.TOPIC) {
                                                    showForumTopicScreen = false
                                                    selectedForumTopic = null
                                                }
                                            },
                                            onUserClick = { login ->
                                                userProfileLoading = true
                                                userProfileError = null
                                                forumViewModel.loadUserProfile(
                                                    context = context.applicationContext,
                                                    login = login,
                                                    onSuccess = { user ->
                                                        userProfileData = user
                                                        userProfileLoading = false
                                                        showUserProfile = true
                                                    },
                                                    onError = { error ->
                                                        userProfileError = error
                                                        userProfileLoading = false
                                                        showUserProfile = true
                                                    }
                                                )
                                            }
                                        )
                                    } else {
                                        ForumScreen(
                                            viewModel = forumViewModel,
                                            currentLogin = viewModel.currentUser?.login,
                                            onTopicClick = { topic ->
                                                selectedForumTopic = topic
                                                showForumTopicScreen = true
                                                forumViewModel.navigateToTopic(topic, context.applicationContext)
                                            }
                                        )
                                    }
                                }
                                Screen.Settings -> SettingsScreen(
                                    onThemeChange = onThemeChange,
                                    onTabletModeChange = { isTablet ->
                                        prefs.edit().putBoolean("tablet_mode", isTablet).apply()
                                    },
                                    isTabletMode = prefs.getBoolean("tablet_mode", false),
                                    userRating = viewModel.currentUser?.rating ?: 0
                                )
                            }
                        }
                    }
                )
            }

            if (showUserProfile) {
                UserProfileDialog(
                    user = userProfileData,
                    isLoading = userProfileLoading,
                    errorMessage = userProfileError,
                    onDismiss = {
                        showUserProfile = false
                        userProfileData = null
                        userProfileError = null
                    },
                    onWriteClick = { login ->
                        showUserProfile = false
                        userProfileData = null
                        userProfileError = null
                        
                        val currentLogin = viewModel.currentUser?.login
                        if (currentLogin != null && currentLogin == login) {
                            return@UserProfileDialog
                        }
                        
                        pendingUserLogin = login
                        dialoguesViewModel.loadDialogues(context.applicationContext)
                    }
                )
            }
        }
    }
}

/**
 * Стеклянный экран авторизации (LoginScreen)
 */
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    val isDark = isDarkTheme()
    val context = LocalContext.current

    var useCredentialsMode by remember { mutableStateOf(true) }

    var loginInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }

    val textColor = if (isDark) Color.White else LightText
    val primaryAccent = getPrimaryAccentColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(0.95f),
            isDark = isDark,
            glowColor = primaryAccent.copy(alpha = 0.35f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Visavi.net",
                    color = primaryAccent,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(if (isDark) Color(0x20FFFFFF) else Color(0x20000000))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (useCredentialsMode) primaryAccent.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable { useCredentialsMode = true; viewModel.errorMessage = null }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Логин и Пароль",
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = if (useCredentialsMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (!useCredentialsMode) primaryAccent.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable { useCredentialsMode = false; viewModel.errorMessage = null }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "API-Токен",
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = if (!useCredentialsMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                viewModel.errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFCF6679),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (useCredentialsMode) {
                    GlassTextField(
                        value = loginInput,
                        onValueChange = { loginInput = it; viewModel.errorMessage = null },
                        placeholderText = "Логин или Email",
                        isDark = isDark,
                        trailingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = primaryAccent.copy(0.8f))
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    GlassTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; viewModel.errorMessage = null },
                        placeholderText = "Пароль",
                        isDark = isDark,
                        visualTransformation = PasswordVisualTransformation(),
                        trailingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = primaryAccent.copy(0.8f))
                        }
                    )
                } else {
                    GlassTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it; viewModel.errorMessage = null },
                        placeholderText = "API TOKEN",
                        isDark = isDark,
                        trailingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = primaryAccent.copy(0.8f))
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))

                GlassButton(
                    onClick = {
                        if (useCredentialsMode) {
                            viewModel.loginWithCredentials(loginInput, passwordInput, context.applicationContext)
                        } else {
                            viewModel.loginWithToken(tokenInput, context.applicationContext)
                        }
                    },
                    modifier = Modifier.width(180.dp),
                    isDark = isDark,
                    accentColor = primaryAccent
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "ВОЙТИ",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
