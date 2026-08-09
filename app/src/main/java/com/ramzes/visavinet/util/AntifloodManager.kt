package com.ramzes.visavinet.util

/**
 * Менеджер антифлуд задержек между отправкой сообщений и тем
 * На основе рейтинга пользователя:
 * > 300 рейтинга -> 10 сек
 * > 200 рейтинга -> 15 сек
 * > 100 рейтинга -> 20 сек
 * По умолчанию     -> 30 сек
 */
object AntifloodManager {
    private var lastSendTimestamp: Long = 0L

    /**
     * Расчёт разрешённого интервала в секундах на основе рейтинга
     */
    fun getAntifloodInterval(userRating: Int): Int {
        return when {
            userRating > 300 -> 10
            userRating > 200 -> 15
            userRating > 100 -> 20
            else -> 30
        }
    }

    /**
     * Оставшееся время ожидания антифлуда в секундах.
     * Возвращает 0, если отправка разрешена.
     */
    fun getRemainingWaitSeconds(userRating: Int): Int {
        if (lastSendTimestamp == 0L) return 0
        val intervalSec = getAntifloodInterval(userRating)
        val elapsedSec = ((System.currentTimeMillis() - lastSendTimestamp) / 1000L).toInt()
        val remaining = intervalSec - elapsedSec
        return if (remaining > 0) remaining else 0
    }

    /**
     * Зафиксировать момент успешной отправки сообщения/темы
     */
    fun markMessageSent() {
        lastSendTimestamp = System.currentTimeMillis()
    }
}
