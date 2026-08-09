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

var ThemeSetter: ((Boolean) -> Unit)? = null
var PrimaryAccentSetter: ((Color) -> Unit)? = null

val LocalIsDarkTheme = staticCompositionLocalOf { mutableStateOf(true) }
val LocalPrimaryAccentColor = staticCompositionLocalOf { mutableStateOf(FieryRed) }

@Composable
fun VisaviTheme(
    initialDarkTheme: Boolean = true,
    initialPrimaryAccent: Color = FieryRed,
    content: @Composable () -> Unit
) {
    val themeState = remember { mutableStateOf(initialDarkTheme) }
    val accentState = remember { mutableStateOf(initialPrimaryAccent) }
    
    LaunchedEffect(Unit) {
        ThemeSetter = { themeState.value = it }
        PrimaryAccentSetter = { accentState.value = it }
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
        LocalPrimaryAccentColor provides accentState
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

fun setDarkTheme(isDark: Boolean) {
    ThemeSetter?.invoke(isDark)
}

fun setPrimaryAccentColor(color: Color) {
    PrimaryAccentSetter?.invoke(color)
}

fun setSecondaryAccentColor(color: Color) {
    setPrimaryAccentColor(color)
}