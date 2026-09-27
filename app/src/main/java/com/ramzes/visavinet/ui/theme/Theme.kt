package com.ramzes.visavinet.ui.theme

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

var ThemeSetter: ((Boolean) -> Unit)? = null
var PrimaryAccentSetter: ((Color) -> Unit)? = null
var FontScaleSetter: ((Float) -> Unit)? = null

val LocalIsDarkTheme = staticCompositionLocalOf { mutableStateOf(true) }
val LocalPrimaryAccentColor = staticCompositionLocalOf { mutableStateOf(FieryRed) }
val LocalFontScale = staticCompositionLocalOf { mutableStateOf(1.0f) }

@Composable
fun VisaviTheme(
    initialDarkTheme: Boolean = true,
    initialPrimaryAccent: Color = FieryRed,
    initialFontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val themeState = remember { mutableStateOf(initialDarkTheme) }
    val accentState = remember { mutableStateOf(initialPrimaryAccent) }
    val fontScaleState = remember { mutableStateOf(initialFontScale) }
    
    LaunchedEffect(Unit) {
        ThemeSetter = { themeState.value = it }
        PrimaryAccentSetter = { accentState.value = it }
        FontScaleSetter = { fontScaleState.value = it }
    }

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
        LocalIsDarkTheme provides themeState,
        LocalPrimaryAccentColor provides accentState,
        LocalFontScale provides fontScaleState
    ) {
        val colorScheme = if (themeState.value) darkScheme else lightScheme
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

fun setDarkTheme(isDark: Boolean) {
    ThemeSetter?.invoke(isDark)
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