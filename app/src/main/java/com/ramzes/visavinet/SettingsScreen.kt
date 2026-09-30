package com.ramzes.visavinet

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramzes.visavinet.ui.components.GlassButton
import com.ramzes.visavinet.ui.components.GlassCard
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.AntifloodManager
import com.ramzes.visavinet.util.SleepModeHelper
import com.ramzes.visavinet.util.TextRenderPrefs
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onThemeChange: (ThemeMode) -> Unit,
    onTabletModeChange: (Boolean) -> Unit,
    onForumSortByNewestChange: ((Boolean) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    isTabletMode: Boolean = false,
    userRating: Int = 0
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val currentAccent = getPrimaryAccentColor()
    val textColor = if (isDark) Color.White else LightText
    val secondaryTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary

    val antifloodInterval = remember(userRating) {
        AntifloodManager.getAntifloodInterval(userRating)
    }

    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    var cacheSize by remember { mutableLongStateOf(getCoilCacheSize(context)) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("visavi_prefs", android.content.Context.MODE_PRIVATE)
    var itemsPerPage by remember { mutableStateOf(prefs.getInt("items_per_page", 10)) }
    var inputText by remember { mutableStateOf(itemsPerPage.toString()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    
    var fontScaleOption by remember {
        val saved = prefs.getString("font_size_scale", FontSizeOption.NORMAL.name)
        mutableStateOf(
            try {
                FontSizeOption.valueOf(saved ?: FontSizeOption.NORMAL.name)
            } catch (e: Exception) {
                FontSizeOption.NORMAL
            }
        )
    }

    var tabletMode by remember { mutableStateOf(isTabletMode) }
    var feedAsStartScreen by remember { mutableStateOf(prefs.getBoolean("feed_as_start_screen", false)) }
    var forumSortByNewest by remember { mutableStateOf(prefs.getBoolean("forum_sort_by_newest", false)) }
    var pinFirstTopicPost by remember { mutableStateOf(prefs.getBoolean("pin_first_topic_post", false)) }
    var notifySiteUpdates by remember { mutableStateOf(prefs.getBoolean("notify_site_updates", false)) }
    var statsCheckEnabled by remember { mutableStateOf(prefs.getBoolean("stats_check_enabled", true)) }
    var ignoreColoredText by remember { mutableStateOf(prefs.getBoolean("ignore_colored_text", false)) }
    var messagesCheckIntervalMs by remember {
        mutableLongStateOf(prefs.getLong("messages_check_interval_ms", 30_000L))
    }

    var sleepModeEnabled by remember {
        mutableStateOf(prefs.getBoolean(SleepModeHelper.KEY_SLEEP_MODE_ENABLED, false))
    }
    var sleepStartHour by remember {
        mutableIntStateOf(prefs.getInt(SleepModeHelper.KEY_START_HOUR, SleepModeHelper.DEFAULT_START_HOUR))
    }
    var sleepStartMinute by remember {
        mutableIntStateOf(prefs.getInt(SleepModeHelper.KEY_START_MINUTE, SleepModeHelper.DEFAULT_START_MINUTE))
    }
    var sleepEndHour by remember {
        mutableIntStateOf(prefs.getInt(SleepModeHelper.KEY_END_HOUR, SleepModeHelper.DEFAULT_END_HOUR))
    }
    var sleepEndMinute by remember {
        mutableIntStateOf(prefs.getInt(SleepModeHelper.KEY_END_MINUTE, SleepModeHelper.DEFAULT_END_MINUTE))
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.updateRemainingCheckTime(context.applicationContext)
        viewModel.checkAutoUpdateIfDayPassed(context.applicationContext, versionName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Блок внешнего вида: Тёмная тема, Акцентный цвет, Размер шрифта, Планшетный режим
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            isDark = isDark,
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Тема оформления (выпадающий список)
                val currentThemeMode = getThemeMode()
                var themeMenuExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Тема",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (currentThemeMode) {
                                ThemeMode.SYSTEM -> "Следует настройкам устройства"
                                ThemeMode.LIGHT -> "Светлое оформление"
                                ThemeMode.DARK -> "Тёмное оформление"
                                ThemeMode.AMOLED -> "Чёрный фон для OLED-экрана"
                            },
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { themeMenuExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentThemeMode.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = currentAccent
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать тему",
                                tint = textColor
                            )
                        }

                        DropdownMenu(
                            expanded = themeMenuExpanded,
                            onDismissRequest = { themeMenuExpanded = false },
                            modifier = Modifier.background(
                                when {
                                    isAmoledTheme() -> Color.Black
                                    isDark -> Color(0xF8090B10)
                                    else -> Color(0xF8F0F4F8)
                                }
                            )
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.title,
                                            fontSize = 15.sp,
                                            color = if (mode == currentThemeMode) currentAccent else textColor
                                        )
                                    },
                                    onClick = {
                                        themeMenuExpanded = false
                                        onThemeChange(mode)
                                    },
                                    trailingIcon = {
                                        if (mode == currentThemeMode) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = currentAccent
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Акцентный цвет
                Text(
                    text = "Акцентный цвет",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Ряд 1 (варианты 0-6)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvailableAccentColors.take(7).forEach { accentTheme ->
                        val isSelected = accentTheme.color == currentAccent
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accentTheme.color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    setPrimaryAccentColor(accentTheme.color)
                                    prefs.edit().putInt("accent_color_index", accentTheme.id).apply()
                                },
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Ряд 2 (варианты 7-13)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvailableAccentColors.drop(7).forEach { accentTheme ->
                        val isSelected = accentTheme.color == currentAccent
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accentTheme.color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    setPrimaryAccentColor(accentTheme.color)
                                    prefs.edit().putInt("accent_color_index", accentTheme.id).apply()
                                },
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Выбор размера шрифта
                Text(
                    text = "Размер шрифта",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        FontSizeOption.SMALL to "Меньше",
                        FontSizeOption.NORMAL to "Нормальный",
                        FontSizeOption.LARGE to "Больше"
                    ).forEach { (option, label) ->
                        val isSelected = fontScaleOption == option
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    fontScaleOption = option
                                    setFontScale(option.scale)
                                    prefs.edit().putString("font_size_scale", option.name).apply()
                                }
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) currentAccent else secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .background(
                                        if (isSelected) currentAccent
                                        else (if (isDark) Color(0x18FFFFFF) else Color(0x10000000))
                                    )
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Переключатель планшетного режима
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Планшетный режим",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (tabletMode) "Включено" else "Выключено",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        checked = tabletMode,
                        onCheckedChange = { newValue ->
                            tabletMode = newValue
                            onTabletModeChange(newValue)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Игнорировать цветной текст
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Игнорировать цветной текст",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (ignoreColoredText) "Сообщения без цветного текста" else "Показывать цветной текст",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        checked = ignoreColoredText,
                        onCheckedChange = { newValue ->
                            ignoreColoredText = newValue
                            TextRenderPrefs.updateIgnoreColoredText(newValue)
                            prefs.edit().putBoolean("ignore_colored_text", newValue).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Общий блок: Лента при запуске, Сортировка форума, Записей на странице
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            isDark = isDark,
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Лента при запуске
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Лента при запуске",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (feedAsStartScreen) "Открывать ленту событий вместо профиля" else "Открывать профиль (по умолчанию)",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        checked = feedAsStartScreen,
                        onCheckedChange = { newValue ->
                            feedAsStartScreen = newValue
                            prefs.edit().putBoolean("feed_as_start_screen", newValue).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Сортировать форум по новизне
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сортировать форум по новизне",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (forumSortByNewest) "По времени последнего обновления" else "По порядку (по умолчанию)",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        checked = forumSortByNewest,
                        onCheckedChange = { newValue ->
                            forumSortByNewest = newValue
                            prefs.edit().putBoolean("forum_sort_by_newest", newValue).apply()
                            onForumSortByNewestChange?.invoke(newValue)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Первое сообщение в темах форума
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Первое сообщение в темах",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (pinFirstTopicPost) "Закреплено вверху темы (свернуто)" else "В обычном порядке (по умолчанию)",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        checked = pinFirstTopicPost,
                        onCheckedChange = { newValue ->
                            pinFirstTopicPost = newValue
                            prefs.edit().putBoolean("pin_first_topic_post", newValue).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Записей на странице
                Text(
                    text = "Записей на странице",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        inputText = newValue
                        inputError = null

                        val intValue = newValue.toIntOrNull()
                        if (intValue != null) {
                            if (intValue in 10..50) {
                                itemsPerPage = intValue
                                prefs.edit().putInt("items_per_page", intValue).apply()
                            } else {
                                inputError = "Значение должно быть от 10 до 50"
                            }
                        } else if (newValue.isEmpty()) {
                            inputError = "Введите число"
                        }
                    },
                    singleLine = true,
                    label = { Text("Количество") },
                    isError = inputError != null,
                    supportingText = inputError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = currentAccent,
                        unfocusedBorderColor = if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.2f),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Текущее: $itemsPerPage (мин: 10, макс: 50)",
                    fontSize = 11.sp,
                    color = secondaryTextColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Проверка статистики и уведомления об обновлениях — один блок настроек
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            isDark = isDark,
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Проверка статистики (фоновый опрос счётчиков сайта)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Проверка статистики",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (statsCheckEnabled) {
                                "Счётчики разделов и уведомления об обновлениях, раз в 5 минут"
                            } else {
                                "Выключено. Счётчики обновятся при открытии приложения"
                            },
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Проверка личных сообщений от этого не зависит",
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        checked = statsCheckEnabled,
                        onCheckedChange = { newValue ->
                            statsCheckEnabled = newValue
                            prefs.edit().putBoolean("stats_check_enabled", newValue).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Уведомления об обновлениях (зависят от проверки статистики)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Уведомления об обновлениях",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                !statsCheckEnabled -> "Требуется проверка статистики"
                                notifySiteUpdates -> "Уведомлять о новых публикациях на сайте"
                                else -> "Выключено"
                            },
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        enabled = statsCheckEnabled,
                        checked = notifySiteUpdates,
                        onCheckedChange = { newValue ->
                            notifySiteUpdates = newValue
                            prefs.edit().putBoolean("notify_site_updates", newValue).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Интервал проверки личных сообщений
                Text(
                    text = "Интервал проверки сообщений",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        30_000L to "30 сек",
                        60_000L to "1 мин",
                        180_000L to "3 мин",
                        300_000L to "5 мин"
                    ).forEach { (interval, label) ->
                        val isSelected = messagesCheckIntervalMs == interval
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    messagesCheckIntervalMs = interval
                                    prefs.edit().putLong("messages_check_interval_ms", interval).apply()
                                }
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) currentAccent else secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .background(
                                        if (isSelected) currentAccent
                                        else (if (isDark) Color(0x18FFFFFF) else Color(0x10000000))
                                    )
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Режим сна
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Режим сна",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (sleepModeEnabled) {
                                "Остановка проверок с ${SleepModeHelper.formatTime(sleepStartHour, sleepStartMinute)} до ${SleepModeHelper.formatTime(sleepEndHour, sleepEndMinute)}"
                            } else {
                                "Не приостанавливать проверки (по умолчанию)"
                            },
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Switch(
                        checked = sleepModeEnabled,
                        onCheckedChange = { newValue ->
                            sleepModeEnabled = newValue
                            prefs.edit().putBoolean(SleepModeHelper.KEY_SLEEP_MODE_ENABLED, newValue).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent,
                            uncheckedThumbColor = LightTextSecondary,
                            uncheckedTrackColor = LightGray.copy(0.5f)
                        )
                    )
                }

                if (sleepModeEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Время начала
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            sleepStartHour = h
                                            sleepStartMinute = m
                                            prefs.edit()
                                                .putInt(SleepModeHelper.KEY_START_HOUR, h)
                                                .putInt(SleepModeHelper.KEY_START_MINUTE, m)
                                                .apply()
                                        },
                                        sleepStartHour,
                                        sleepStartMinute,
                                        true
                                    ).show()
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0x14FFFFFF) else Color(0x0A000000),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color(0x24FFFFFF) else Color(0x18000000)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "С", fontSize = 13.sp, color = secondaryTextColor)
                                Text(
                                    text = SleepModeHelper.formatTime(sleepStartHour, sleepStartMinute),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentAccent
                                )
                            }
                        }

                        // Время окончания
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            sleepEndHour = h
                                            sleepEndMinute = m
                                            prefs.edit()
                                                .putInt(SleepModeHelper.KEY_END_HOUR, h)
                                                .putInt(SleepModeHelper.KEY_END_MINUTE, m)
                                                .apply()
                                        },
                                        sleepEndHour,
                                        sleepEndMinute,
                                        true
                                    ).show()
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0x14FFFFFF) else Color(0x0A000000),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color(0x24FFFFFF) else Color(0x18000000)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "До", fontSize = 13.sp, color = secondaryTextColor)
                                Text(
                                    text = SleepModeHelper.formatTime(sleepEndHour, sleepEndMinute),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentAccent
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Кэш изображений и Обновление приложения
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            isDark = isDark,
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Кэш изображений
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Кэш изображений",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Занято места: ${formatCacheSize(cacheSize)}",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    GlassButton(
                        onClick = { showClearCacheDialog = true },
                        accentColor = currentAccent,
                        isDark = isDark,
                        enabled = cacheSize > 0L
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Очистить",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                )

                // Обновление приложения
                val isCheckAvailable = viewModel.remainingCheckSeconds <= 0L && viewModel.updateCheckState !is UpdateCheckState.Checking

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Обновление приложения",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Текущая версия: v$versionName",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                        if (viewModel.remainingCheckSeconds > 0L) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Повторная проверка через ${SettingsViewModel.formatRemainingTime(viewModel.remainingCheckSeconds)}",
                                fontSize = 11.sp,
                                color = secondaryTextColor.copy(alpha = 0.75f)
                            )
                        }
                    }

                    if (viewModel.updateCheckState is UpdateCheckState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = currentAccent
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.checkForUpdates(context.applicationContext, versionName)
                            },
                            enabled = isCheckAvailable
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Проверить обновление",
                                tint = if (isCheckAvailable) currentAccent else secondaryTextColor.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                when (val state = viewModel.updateCheckState) {
                    is UpdateCheckState.Idle -> {}
                    is UpdateCheckState.Checking -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Проверка наличия обновлений на GitHub...",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                    is UpdateCheckState.UpToDate -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "У вас установлена самая последняя версия",
                            fontSize = 12.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is UpdateCheckState.UpdateAvailable -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Доступна версия ${state.newVersion}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentAccent
                                )
                                if (!state.releaseName.isNullOrBlank() && state.releaseName != state.newVersion) {
                                    Text(
                                        text = state.releaseName,
                                        fontSize = 12.sp,
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassButton(
                                onClick = { viewModel.openUpdateDialog() },
                                accentColor = currentAccent,
                                isDark = isDark
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Обновить",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    is UpdateCheckState.Throttled -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = Color(0xFFFF9800)
                        )
                    }
                    is UpdateCheckState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = Color(0xFFCF6679)
                        )
                    }
                }
            }
        }

        if (onLogout != null) {
            Spacer(modifier = Modifier.height(10.dp))

            // Блок учётной записи (кнопка выхода)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                isDark = isDark,
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Учётная запись",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Выход из аккаунта на этом устройстве",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    GlassButton(
                        onClick = { showLogoutDialog = true },
                        accentColor = Color(0xFFE53935),
                        isDark = isDark
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Выйти",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Антифлуд: $antifloodInterval сек.",
            fontSize = 11.sp,
            color = secondaryTextColor.copy(0.6f)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "code by ramzes v$versionName",
            fontSize = 11.sp,
            color = secondaryTextColor.copy(0.6f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    text = "Очистка кэша",
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            },
            text = {
                Text(
                    text = "Очистить сохранённый кэш изображений и временных файлов (${formatCacheSize(cacheSize)})?",
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        clearAppCache(context)
                        cacheSize = getCoilCacheSize(context)
                        Toast.makeText(context, "Кэш успешно очищен", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Очистить", color = currentAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Отмена", color = secondaryTextColor)
                }
            },
            containerColor = if (isAmoledTheme()) Color.Black else if (isDark) Color(0xFF1E222B) else Color.White
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Выход из аккаунта",
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            },
            text = {
                Text(
                    text = "Вы действительно хотите выйти из учётной записи? Для входа потребуется повторно ввести логин и пароль.",
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout?.invoke()
                    }
                ) {
                    Text("Выйти", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена", color = secondaryTextColor)
                }
            },
            containerColor = if (isAmoledTheme()) Color.Black else if (isDark) Color(0xFF1E222B) else Color.White
        )
    }
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
private fun clearAppCache(context: android.content.Context) {
    try {
        val imageLoader = coil.Coil.imageLoader(context)
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()

        val cacheDir = File(context.cacheDir, "image_cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }

        val updatesDir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
            "updates"
        )
        if (updatesDir.exists()) {
            updatesDir.deleteRecursively()
        }
    } catch (e: Exception) {
    }
}

private fun getCoilCacheSize(context: android.content.Context): Long {
    return try {
        val cacheDir = File(context.cacheDir, "image_cache")
        val imageCacheSize = if (cacheDir.exists()) getDirectorySize(cacheDir) else 0L
        val updatesDir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
            "updates"
        )
        val updatesSize = if (updatesDir.exists()) getDirectorySize(updatesDir) else 0L
        imageCacheSize + updatesSize
    } catch (e: Exception) {
        0L
    }
}

private fun getDirectorySize(directory: File): Long {
    var size = 0L
    try {
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
    } catch (e: Exception) {
    }
    return size
}

private fun formatCacheSize(size: Long): String {
    return when {
        size < 1024 -> "$size Б"
        size < 1024 * 1024 -> "${size / 1024} КБ"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} МБ"
        else -> "${size / (1024 * 1024 * 1024)} ГБ"
    }
}
