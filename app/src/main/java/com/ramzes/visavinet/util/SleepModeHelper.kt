package com.ramzes.visavinet.util

import android.content.SharedPreferences
import java.util.Calendar
import java.util.Locale

/**
 * Утилита для управления и проверки «Режима сна»
 * При активном режиме сна приостанавливаются фоновые проверки обновлений и личных сообщений.
 */
object SleepModeHelper {
    const val KEY_SLEEP_MODE_ENABLED = "sleep_mode_enabled"
    const val KEY_START_HOUR = "sleep_mode_start_hour"
    const val KEY_START_MINUTE = "sleep_mode_start_minute"
    const val KEY_END_HOUR = "sleep_mode_end_hour"
    const val KEY_END_MINUTE = "sleep_mode_end_minute"

    const val DEFAULT_START_HOUR = 23
    const val DEFAULT_START_MINUTE = 0
    const val DEFAULT_END_HOUR = 7
    const val DEFAULT_END_MINUTE = 0

    /**
     * Проверяет, активен ли режим сна в текущий момент времени.
     */
    fun isSleepModeActive(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false)) {
            return false
        }

        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val startHour = prefs.getInt(KEY_START_HOUR, DEFAULT_START_HOUR)
        val startMinute = prefs.getInt(KEY_START_MINUTE, DEFAULT_START_MINUTE)
        val endHour = prefs.getInt(KEY_END_HOUR, DEFAULT_END_HOUR)
        val endMinute = prefs.getInt(KEY_END_MINUTE, DEFAULT_END_MINUTE)

        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute

        return if (startTotal <= endTotal) {
            currentMinutes in startTotal..endTotal
        } else {
            // Переход через полночь (например, с 23:00 до 07:00)
            currentMinutes >= startTotal || currentMinutes <= endTotal
        }
    }

    /**
     * Форматирует время в виде HH:mm
     */
    fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }
}
