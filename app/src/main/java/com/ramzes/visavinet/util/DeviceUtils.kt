package com.ramzes.visavinet.util

import android.content.Context
import android.content.res.Configuration

/**
 * Утилиты для определения типа и характеристик устройства
 */
object DeviceUtils {

    /**
     * Коэффициент масштабирования элементов интерфейса в планшетном режиме (+20%)
     */
    const val TABLET_UI_SCALE = 1.20f

    /**
     * Проверяет параметры конфигурации экрана на принадлежность к планшету:
     * smallestScreenWidthDp >= 600 (sw600dp) или SCREENLAYOUT_SIZE_LARGE / SCREENLAYOUT_SIZE_XLARGE.
     */
    fun isTabletConfiguration(smallestScreenWidthDp: Int, screenLayout: Int): Boolean {
        val isSw600 = smallestScreenWidthDp >= 600
        val isLargeScreen = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        return isSw600 || isLargeScreen
    }

    /**
     * Определяет, запущено ли приложение на планшете.
     * Проверяет стандартную для планшетов минимальную ширину экрана smallestScreenWidthDp >= 600 (sw600dp)
     * или квалификатор экрана SCREENLAYOUT_SIZE_LARGE / SCREENLAYOUT_SIZE_XLARGE.
     */
    fun isTablet(context: Context): Boolean {
        val config = context.resources.configuration
        return isTabletConfiguration(config.smallestScreenWidthDp, config.screenLayout)
    }
}
