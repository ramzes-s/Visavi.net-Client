package com.ramzes.visavinet.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Глобальные настройки отображения текста.
 * Значения синхронизируются с SharedPreferences("visavi_prefs"):
 * - "ignore_colored_text" -> ignoreColoredText
 */
object TextRenderPrefs {
    /**
     * Игнорировать цветной текст (<span style="color: ...">) при рендере сообщений,
     * статусов и цитат. Текст отображается стандартным цветом темы.
     */
    var ignoreColoredText by mutableStateOf(false)
        private set

    fun updateIgnoreColoredText(value: Boolean) {
        ignoreColoredText = value
    }
}
