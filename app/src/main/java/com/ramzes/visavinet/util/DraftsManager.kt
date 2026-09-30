package com.ramzes.visavinet.util

import android.content.Context
import android.content.SharedPreferences

object DraftsManager {
    private const val PREFS_NAME = "visavi_drafts"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDraft(context: Context, key: String): String {
        return getPrefs(context).getString(key, "") ?: ""
    }

    fun saveDraft(context: Context, key: String, text: String) {
        val trimmed = text.trim()
        val prefs = getPrefs(context)
        if (trimmed.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, text).apply()
        }
    }

    fun clearDraft(context: Context, key: String) {
        getPrefs(context).edit().remove(key).apply()
    }

    fun forumReplyKey(topicId: Int): String = "forum_reply_$topicId"
    fun forumCreateTopicKey(forumId: Int): String = "forum_create_topic_$forumId"
    fun newsCommentKey(newsId: Int): String = "news_comment_$newsId"
    fun galleryCommentKey(photoId: Int): String = "gallery_comment_$photoId"
    fun galleryUploadTitleKey(): String = "gallery_upload_title"
    fun galleryUploadTextKey(): String = "gallery_upload_text"
    fun downCommentKey(downId: Int): String = "down_comment_$downId"
    fun dialogueKey(dialogueKey: String): String = "dialogue_$dialogueKey"
}
