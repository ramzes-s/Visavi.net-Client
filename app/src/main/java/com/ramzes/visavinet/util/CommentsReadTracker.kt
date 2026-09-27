package com.ramzes.visavinet.util

import android.content.Context
import android.content.SharedPreferences

object CommentsReadTracker {
    private const val PREFS_NAME = "visavi_read_comments"
    const val SECTION_NEWS = "news"
    const val SECTION_GALLERY = "gallery"
    const val SECTION_DOWNS = "downs"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun key(section: String, itemId: Int): String = "${section}_${itemId}"

    fun getReadCount(context: Context, section: String, itemId: Int): Int {
        return getPrefs(context).getInt(key(section, itemId), -1)
    }

    fun hasRecord(context: Context, section: String, itemId: Int): Boolean {
        return getPrefs(context).contains(key(section, itemId))
    }

    fun markAsRead(context: Context, section: String, itemId: Int, count: Int) {
        if (itemId <= 0) return
        val currentRead = getReadCount(context, section, itemId)
        val newRead = if (currentRead > count) currentRead else count
        getPrefs(context).edit().putInt(key(section, itemId), newRead).apply()
    }

    fun getNewCommentsCount(context: Context, section: String, itemId: Int, currentCount: Int): Int {
        if (itemId <= 0 || currentCount <= 0) return 0
        val readCount = getReadCount(context, section, itemId)
        return if (readCount >= 0 && currentCount > readCount) {
            currentCount - readCount
        } else {
            0
        }
    }
}
