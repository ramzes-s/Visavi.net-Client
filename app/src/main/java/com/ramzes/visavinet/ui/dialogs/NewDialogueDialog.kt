package com.ramzes.visavinet.ui.dialogs

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ramzes.visavinet.R
import com.ramzes.visavinet.network.SearchUser
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.extractErrorMessage
import com.ramzes.visavinet.ui.components.GlassProfileCard
import com.ramzes.visavinet.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDialogueDialog(
    currentUserLogin: String?,
    onDismiss: () -> Unit,
    onUserSelected: (login: String, name: String?) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isDarkTheme()
    val primaryAccent = getPrimaryAccentColor()
    val backdropColor = if (isDark) Color(0xC0090B10) else Color(0xC0F0F4F8)
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(alpha = 0.7f) else LightTextSecondary

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selfWarningMessage by remember { mutableStateOf<String?>(null) }

    fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 3) {
            searchResults = emptyList()
            errorMessage = null
            isLoading = false
            return
        }

        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = VisaviApi.instance.searchUsers(trimmed)
                if (response.isSuccessful && response.body() != null) {
                    searchResults = response.body()?.data ?: emptyList()
                } else {
                    errorMessage = response.extractErrorMessage("Ошибка поиска пользователей")
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Debounce автоматического поиска при вводе
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        selfWarningMessage = null
        if (trimmed.length < 3) {
            searchResults = emptyList()
            errorMessage = null
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        delay(350)
        try {
            val response = VisaviApi.instance.searchUsers(trimmed)
            if (response.isSuccessful && response.body() != null) {
                searchResults = response.body()?.data ?: emptyList()
            } else {
                errorMessage = response.extractErrorMessage("Ошибка поиска пользователей")
            }
        } catch (e: Exception) {
            errorMessage = "Ошибка сети: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        var blurModifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .clickable(onClick = onDismiss)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurModifier = blurModifier.blur(20.dp)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = blurModifier)

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f)
                    .clickable(enabled = false) {}, // блокируем клик на подложку
                contentAlignment = Alignment.Center
            ) {
                GlassProfileCard(
                    modifier = Modifier.fillMaxSize(),
                    isDark = isDark,
                    shape = RoundedCornerShape(16.dp),
                    accentColor = primaryAccent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Верхняя строка: Заголовок и кнопка закрытия
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Новый диалог",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = secondaryTextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Поле поиска с поисковой иконкой и кнопкой очистки
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(text = "Введите Логин")
                            },
                            placeholder = {
                                Text(
                                    text = "Введите Логин",
                                    fontSize = 14.sp,
                                    color = secondaryTextColor.copy(alpha = 0.7f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Поиск",
                                    tint = primaryAccent
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Очистить",
                                            tint = secondaryTextColor
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = primaryAccent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                executeSearch(searchQuery)
                            })
                        )

                        if (selfWarningMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = selfWarningMessage ?: "",
                                color = Color(0xFFCF6679),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Контент: прогресс загрузки, ошибка, подсказка или список пользователей
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            when {
                                isLoading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = primaryAccent,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                errorMessage != null -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Text(
                                                text = errorMessage ?: "Произошла ошибка",
                                                color = Color(0xFFCF6679),
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { executeSearch(searchQuery) },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                                            ) {
                                                Text("Повторить", color = Color.White)
                                            }
                                        }
                                    }
                                }
                                searchQuery.trim().length < 3 -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = secondaryTextColor.copy(alpha = 0.4f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (searchQuery.isBlank()) {
                                                    "Введите логин пользователя\nдля начала диалога"
                                                } else {
                                                    "Введите минимум 3 символа\nдля поиска пользователей"
                                                },
                                                color = secondaryTextColor,
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                searchResults.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Пользователи не найдены",
                                            color = secondaryTextColor,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(searchResults, key = { it.login }) { user ->
                                            val isMe = currentUserLogin != null &&
                                                    user.login.equals(currentUserLogin, ignoreCase = true)

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        if (isMe) {
                                                            selfWarningMessage = "Нельзя начать диалог с самим собой"
                                                        } else {
                                                            onUserSelected(user.login, user.name)
                                                            onDismiss()
                                                        }
                                                    },
                                                color = if (isDark) Color(0x10FFFFFF) else Color(0x08000000),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Аватар
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(user.avatarUrl ?: R.drawable.ic_default_avatar)
                                                            .placeholder(R.drawable.ic_default_avatar)
                                                            .error(R.drawable.ic_default_avatar)
                                                            .diskCachePolicy(CachePolicy.ENABLED)
                                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = user.displayName,
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .clip(CircleShape)
                                                            .border(
                                                                1.5.dp,
                                                                primaryAccent.copy(alpha = 0.4f),
                                                                CircleShape
                                                            ),
                                                        contentScale = ContentScale.Crop
                                                    )

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = user.login,
                                                                color = textColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 15.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (isMe) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Surface(
                                                                    color = primaryAccent.copy(alpha = 0.2f),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Это вы",
                                                                        color = primaryAccent,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (!user.name.isNullOrBlank() && !user.name.equals(user.login, ignoreCase = true)) {
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = user.name,
                                                                color = secondaryTextColor,
                                                                fontSize = 12.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                                        contentDescription = "Написать",
                                                        tint = if (isMe) secondaryTextColor.copy(alpha = 0.3f) else primaryAccent,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
