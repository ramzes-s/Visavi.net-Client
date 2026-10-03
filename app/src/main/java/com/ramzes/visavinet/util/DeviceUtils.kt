package com.ramzes.visavinet.util

import android.content.Context
import android.content.res.Configuration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Утилиты для определения типа и характеристик устройства
 */
object DeviceUtils {

    /**
     * Коэффициент масштабирования элементов интерфейса в планшетном режиме (+20%)
     */
    const val TABLET_UI_SCALE = 1.20f

    /**
     * Стандартизированная максимальная ширина диалоговых окон в планшетном режиме (+50% от базовых 420.dp)
     */
    val TABLET_DIALOG_MAX_WIDTH: Dp = 630.dp

    /**
     * Базовая максимальная ширина диалоговых окон в телефонном режиме
     */
    val DEFAULT_DIALOG_MAX_WIDTH: Dp = 420.dp

    /**
     * Вычисляет стандартную максимальную ширину для модального диалогового окна:
     * в планшетном режиме возвращает TABLET_DIALOG_MAX_WIDTH (630.dp),
     * в обычном режиме — указанную defaultWidth.
     */
    @Composable
    fun dialogMaxWidth(
        isTabletExplicit: Boolean = false,
        defaultWidth: Dp = DEFAULT_DIALOG_MAX_WIDTH
    ): Dp {
        val context = LocalContext.current
        val config = LocalConfiguration.current
        val isTabletMode = isTabletExplicit || remember(config) {
            isTablet(context) || config.screenWidthDp >= 600
        }
        return if (isTabletMode) TABLET_DIALOG_MAX_WIDTH else defaultWidth
    }

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
