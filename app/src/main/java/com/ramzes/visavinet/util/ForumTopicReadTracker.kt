package com.ramzes.visavinet.util

import android.content.Context
import android.content.SharedPreferences

object ForumTopicReadTracker {
    private const val PREFS_NAME = "visavi_forum_read_posts"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun key(topicId: Int): String = "topic_last_post_${topicId}"

    fun getLastSeenPostId(context: Context, topicId: Int): Int {
        if (topicId <= 0) return 0
        return getPrefs(context).getInt(key(topicId), 0)
    }

    fun saveLastSeenPostId(context: Context, topicId: Int, postId: Int) {
        if (topicId <= 0 || postId <= 0) return
        val currentLast = getLastSeenPostId(context, topicId)
        if (postId > currentLast) {
            getPrefs(context).edit().putInt(key(topicId), postId).apply()
        }
    }
}
