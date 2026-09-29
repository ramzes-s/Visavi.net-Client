package com.ramzes.visavinet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

enum class FontSizeOption(val scale: Float, val title: String) {
    SMALL(0.85f, "Меньше"),
    NORMAL(1.0f, "Нормальный"),
    LARGE(1.15f, "Больше")
}

/**
 * Режим оформления приложения.
 * SYSTEM — следовать системной настройке светлой/тёмной темы,
 * AMOLED — тёмная тема с чёрным фоном (пиксели выключены, экономия батареи).
 */
enum class ThemeMode(val title: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    AMOLED("AMOLED")
}

var ThemeModeSetter: ((ThemeMode) -> Unit)? = null
var PrimaryAccentSetter: ((Color) -> Unit)? = null
var FontScaleSetter: ((Float) -> Unit)? = null

val LocalThemeMode = staticCompositionLocalOf { mutableStateOf(ThemeMode.DARK) }
val LocalIsDarkTheme = staticCompositionLocalOf { mutableStateOf(true) }
val LocalPrimaryAccentColor = staticCompositionLocalOf { mutableStateOf(FieryRed) }
val LocalFontScale = staticCompositionLocalOf { mutableStateOf(1.0f) }

@Composable
fun VisaviTheme(
    initialThemeMode: ThemeMode = ThemeMode.DARK,
    initialPrimaryAccent: Color = FieryRed,
    initialFontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val themeModeState = remember { mutableStateOf(initialThemeMode) }
    val accentState = remember { mutableStateOf(initialPrimaryAccent) }
    val fontScaleState = remember { mutableStateOf(initialFontScale) }

    LaunchedEffect(Unit) {
        ThemeModeSetter = { themeModeState.value = it }
        PrimaryAccentSetter = { accentState.value = it }
        FontScaleSetter = { fontScaleState.value = it }
    }

    // Системная тёмная тема (реактивно на смену настроек устройства)
    val systemDark = isSystemInDarkTheme()
    val resolvedIsDark = when (themeModeState.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val darkState = remember(resolvedIsDark) { mutableStateOf(resolvedIsDark) }

    val darkScheme = darkColorScheme(
        background = DustyBlack,
        surface = DustyBlack,
        primary = accentState.value,
        onPrimary = Color.White,
        secondary = TextLightGray,
        onSecondary = DustyBlack,
        onBackground = Color.White,
        onSurface = Color.White
    )

    // AMOLED: чисто чёрный фон — выключенные пиксели OLED-экрана
    val amoledScheme = darkColorScheme(
        background = Color.Black,
        surface = Color.Black,
        primary = accentState.value,
        onPrimary = Color.White,
        secondary = TextLightGray,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    val lightScheme = lightColorScheme(
        background = LightWhite,
        surface = LightWhite,
        primary = accentState.value,
        onPrimary = Color.White,
        secondary = LightTextSecondary,
        onSecondary = LightWhite,
        onBackground = LightText,
        onSurface = LightText
    )

    CompositionLocalProvider(
        LocalThemeMode provides themeModeState,
        LocalIsDarkTheme provides darkState,
        LocalPrimaryAccentColor provides accentState,
        LocalFontScale provides fontScaleState
    ) {
        val colorScheme = when {
            themeModeState.value == ThemeMode.AMOLED -> amoledScheme
            resolvedIsDark -> darkScheme
            else -> lightScheme
        }
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun isDarkTheme(): Boolean {
    return LocalIsDarkTheme.current.value
}

/**
 * Включена ли сейчас AMOLED-тема (чёрный фон)
 */
@Composable
fun isAmoledTheme(): Boolean {
    return LocalThemeMode.current.value == ThemeMode.AMOLED
}

/**
 * Текущий режим темы (для выпадающего списка в настройках)
 */
@Composable
fun getThemeMode(): ThemeMode {
    return LocalThemeMode.current.value
}

@Composable
fun getPrimaryAccentColor(): Color {
    return LocalPrimaryAccentColor.current.value
}

// Псевдоним для совместимости
@Composable
fun getSecondaryAccentColor(): Color = getPrimaryAccentColor()

@Composable
fun getFontScale(): Float {
    return LocalFontScale.current.value
}

@Composable
fun ProvideContentFontScale(content: @Composable () -> Unit) {
    val scale = getFontScale()
    if (scale == 1.0f) {
        content()
    } else {
        val currentDensity = LocalDensity.current
        val customDensity = remember(currentDensity, scale) {
            Density(
                density = currentDensity.density,
                fontScale = currentDensity.fontScale * scale
            )
        }
        CompositionLocalProvider(LocalDensity provides customDensity) {
            content()
        }
    }
}

fun setThemeMode(mode: ThemeMode) {
    ThemeModeSetter?.invoke(mode)
}

fun setPrimaryAccentColor(color: Color) {
    PrimaryAccentSetter?.invoke(color)
}

fun setSecondaryAccentColor(color: Color) {
    setPrimaryAccentColor(color)
}

fun setFontScale(scale: Float) {
    FontScaleSetter?.invoke(scale)
}