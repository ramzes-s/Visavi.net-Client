package com.ramzes.visavinet.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Тёмная тема (Базовые фон и поверхности)
val DustyBlack = Color(0xFF090B10)     // Строгий космический тёмный фон
val SoftLight = Color(0xFF121622)      // Вторичный подслой
val SoftDark = Color(0xFF040508)       // Глубокая тень
val TextLightGray = Color(0xFFB8C2CC)  // Вспомогательный текст

// Светлая тема
val LightWhite = Color(0xFFD6DFE8)         // Затемненный комфортный фон с достаточным контрастом
val LightGray = Color(0xFFC2CFDC)          // Вторичный фон
val LightShadow = Color(0xFFAAB8C8)        // Тень
val LightHighlight = Color(0xFFFFFFFF)     // Блик
val LightText = Color(0xFF0F172A)          // Основной текст
val LightTextSecondary = Color(0xFF64748B) // Вторичный текст
val LightAccent = Color(0xFF2563EB)        // Тёмно-синий акцент

// Базовые сдержанные тона
val PrimaryBlue = Color(0xFF2563EB)        // Королевский синий
val DarkNavyBlue = Color(0xFF1E3A8A)       // Глубокий тёмно-синий
val BlueGlow = Color(0xFF3B82F6)           // Синее свечение

// 10 Вариантов пользовательского акцентного цвета (Primary Accent)
val NeonPink = Color(0xFFFF2A85)           // 1. Неоново-розовый
val NeonViolet = Color(0xFF9D4EDD)         // 2. Фиолетовый
val RoyalBlue = Color(0xFF2563EB)          // 3. Королевский синий
val SkyCyan = Color(0xFF00E5FF)            // 4. Ледяной голубой
val EmeraldGreen = Color(0xFF10B981)       // 5. Изумрудный
val AmberGold = Color(0xFFF59E0B)          // 6. Янтарный
val FieryRed = Color(0xFFEF4444)           // 7. Огненно-красный
val LavenderPurple = Color(0xFFA855F7)     // 8. Лавандовый
val IndigoAccent = Color(0xFF6366F1)       // 9. Сочный индиго
val LimeNeon = Color(0xFF84CC16)           // 10. Салатовый
val VibrantOrange = Color(0xFFFF5C00)      // Насыщенный оранжевый

data class AccentThemeColor(
    val id: Int,
    val name: String,
    val color: Color
)

val AvailableAccentColors = listOf(
    AccentThemeColor(0, "Красный", FieryRed),
    AccentThemeColor(1, "Розовый", NeonPink),
    AccentThemeColor(2, "Оранжевый", VibrantOrange),
    AccentThemeColor(3, "Синий", RoyalBlue),
    AccentThemeColor(4, "Голубой", SkyCyan),
    AccentThemeColor(5, "Изумрудный", EmeraldGreen),
    AccentThemeColor(6, "Янтарный", AmberGold),
    AccentThemeColor(7, "Лавандовый", LavenderPurple),
    AccentThemeColor(8, "Индиго", IndigoAccent),
    AccentThemeColor(9, "Салатовый", LimeNeon)
)

// Стеклянные подложки (Строгие без лишних цветных пятен)
val GlassDarkBg = Color(0x3B111827)       // Полупрозрачный строгий тёмный
val GlassDarkSurface = Color(0x291F2937)  // Полупрозрачный поверхностный
val GlassLightBg = Color(0xF0FFFFFF)      // Полупрозрачный светлый фон карточек
val GlassLightSurface = Color(0x99FFFFFF) // Полупрозрачный светлый поверхностный

// Строгие окантовки элементов с небольшим скруглением
val GlassBorderDark = Brush.linearGradient(
    colors = listOf(
        Color(0x40FFFFFF),
        Color(0x1FDFE7EF),
        Color(0x05FFFFFF),
        Color(0x20FFFFFF)
    )
)

val GlassBorderLight = Brush.linearGradient(
    colors = listOf(
        Color(0x80FFFFFF),
        Color(0x30CBD5E1),
        Color(0x10FFFFFF),
        Color(0x200F172A)
    )
)

// Псевдонимы для совместимости
val LogoColor = RoyalBlue
val NeonCyan = SkyCyan
val NeonGreen = DarkNavyBlue
val NeonAmber = AmberGold