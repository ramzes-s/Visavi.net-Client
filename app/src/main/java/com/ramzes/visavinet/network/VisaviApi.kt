package com.ramzes.visavinet.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/**
 * Структура стандартной ошибки API RotorCMS
 */
data class ApiErrorResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("errors") val errors: Map<String, List<String>>? = null
) {
    fun getFormattedError(): String {
        val validationErrors = errors?.values?.flatten()?.filter { it.isNotBlank() }
        if (!validationErrors.isNullOrEmpty()) {
            return validationErrors.joinToString("\n")
        }
        if (!message.isNullOrBlank()) return message
        if (!error.isNullOrBlank()) return error
        return "Ошибка сервера"
    }
}

/**
 * Извлечь человекочитаемый текст ошибки из ответа Retrofit Response
 */
fun <T> Response<T>.extractErrorMessage(defaultMsg: String = "Ошибка сервера"): String {
    return try {
        val errorBodyStr = errorBody()?.string()?.trim()
        if (!errorBodyStr.isNullOrBlank()) {
            // Если ответ в формате HTML (например, 502/504 от Nginx/Cloudflare)
            if (errorBodyStr.startsWith("<") || errorBodyStr.contains("<html", ignoreCase = true)) {
                when (code()) {
                    401 -> "Требуется авторизация"
                    403 -> "Доступ запрещен"
                    404 -> "Запрашиваемый ресурс не найден"
                    429 -> "Слишком много запросов. Пожалуйста, подождите"
                    502, 503, 504 -> "Сервер временно недоступен (${code()})"
                    else -> "$defaultMsg (${code()})"
                }
            } else {
                val gson = Gson()
                val parsedError = gson.fromJson(errorBodyStr, ApiErrorResponse::class.java)
                parsedError?.getFormattedError() ?: defaultMsg
            }
        } else {
            message().ifBlank { defaultMsg }
        }
    } catch (e: Exception) {
        defaultMsg
    }
}

/**
 * Запрос авторизации (POST /auth)
 */
data class AuthRequest(
    @SerializedName("login") val login: String,
    @SerializedName("password") val password: String
)

/**
 * Ответ авторизации (POST /auth)
 */
data class AuthResponse(
    @SerializedName("token") val token: String? = null
)

/**
 * Модели конфигурации сайта (GET /config)
 */
data class SiteConfig(
    @SerializedName("title") val title: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("money_name") val moneyName: String? = null,
    @SerializedName("score_name") val scoreName: String? = null,
    @SerializedName("site_closed") val siteClosed: Boolean = false,
    @SerializedName("registration_open") val registrationOpen: Boolean = true,
    @SerializedName("invite_only") val inviteOnly: Boolean = false
)

data class UploadConfig(
    @SerializedName("max_files") val maxFiles: Int = 10,
    @SerializedName("max_file_size") val maxFileSize: Long = 20971520L,
    @SerializedName("extensions") val extensions: List<String> = emptyList()
)

data class TextLimits(
    @SerializedName("title_min") val titleMin: Int = 3,
    @SerializedName("title_max") val titleMax: Int = 50,
    @SerializedName("text_min") val textMin: Int = 5,
    @SerializedName("text_max") val textMax: Int = 5000
)

data class VoteConfig(
    @SerializedName("title_min") val titleMin: Int = 5,
    @SerializedName("title_max") val titleMax: Int = 50,
    @SerializedName("answer_min") val answerMin: Int = 1,
    @SerializedName("answer_max") val answerMax: Int = 50,
    @SerializedName("answers_min") val answersMin: Int = 2,
    @SerializedName("answers_max") val answersMax: Int = 10
)

data class MessageConfig(
    @SerializedName("text_min") val textMin: Int = 5,
    @SerializedName("text_max") val textMax: Int = 1000
)

data class ConfigData(
    @SerializedName("site") val site: SiteConfig? = null,
    @SerializedName("upload") val upload: UploadConfig? = null,
    @SerializedName("forum") val forum: TextLimits? = null,
    @SerializedName("vote") val vote: VoteConfig? = null,
    @SerializedName("message") val message: MessageConfig? = null
)

/**
 * Модель данных пользователя Visavi.net
 */
data class UserData(
    @SerializedName("login") val login: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("site") val site: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("newwall") val newWall: Int = 0,
    @SerializedName("point") val point: Int = 0,
    @SerializedName("money") val money: Long = 0,
    @SerializedName("ban") val ban: Int = 0,
    @SerializedName("allprivat") val allPrivat: Int = 0,
    @SerializedName("newprivat") val newPrivat: Int = 0,
    @SerializedName("status") val status: String? = null,
    @SerializedName("info") val info: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("lastlogin") val lastloginRaw: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("visits") val visitsRaw: Int? = null,
    @SerializedName("visit") val visitRaw: Int? = null,
    @SerializedName("logins") val loginsRaw: Int? = null,
    @SerializedName("conttime") val conttimeRaw: Int? = null,
    @SerializedName("allvisit") val allvisitRaw: Int? = null
) {
    val userLogin: String?
        get() = login?.ifBlank { null }

    val displayName: String
        get() = name?.ifBlank { null } ?: userLogin ?: "Пользователь"

    val visits: Int
        get() = visitsRaw ?: visitRaw ?: loginsRaw ?: conttimeRaw ?: allvisitRaw ?: 0

    val lastLogin: Long?
        get() = lastloginRaw?.let { parseIsoDateTime(it) }
}

/**
 * Модель нового сообщения в диалоге
 */
data class NewMessageInfo(
    @SerializedName("login") val login: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("last_message_at") val lastMessageAtRaw: String? = null
) {
    val lastMessageAt: Long?
        get() = lastMessageAtRaw?.let { parseIsoDateTime(it) }
}

/**
 * Ответ API о новых сообщениях
 */
data class NewMessagesResponse(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("dialogues") val dialogues: List<NewMessageInfo>? = null
)

fun parseIsoDateTime(isoDate: String?): Long? {
    if (isoDate.isNullOrBlank()) return null
    val clean = isoDate.trim()
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (pattern in patterns) {
        try {
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val date = sdf.parse(clean)
            if (date != null) return date.time
        } catch (e: Exception) {
            // пробуем следующий формат
        }
    }
    return null
}

data class ApiData(
    @SerializedName("data") val data: UserData? = null
)

/**
 * Модель диалога с пользователем
 */
data class DialogueData(
    @SerializedName("id") val id: Int,
    @SerializedName("login") val login: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("all_reading") val allReading: Boolean? = null,
    @SerializedName("recipient_read") val recipientRead: Boolean? = null,
    @SerializedName("can_reply") val canReply: Boolean? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null
) {
    val authorLogin: String?
        get() = login?.ifBlank { null } ?: name?.ifBlank { null }

    val displayName: String
        get() = name?.ifBlank { null } ?: authorLogin ?: "Пользователь"

    val createdAt: Long
        get() = createdAtRaw?.let { parseIsoDateTime(it) } ?: 0L
}

data class PaginationMeta(
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("last_page") val lastPage: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("path") val path: String? = null
)

data class DialoguesData(
    @SerializedName("data") val data: List<DialogueData>? = null,
    @SerializedName("links") val links: DialogueLinks? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null
)

data class DialogueLinks(
    @SerializedName("next") val next: String? = null,
    @SerializedName("prev") val prev: String? = null
)

data class FileData(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("extension") val extension: String? = null,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("is_image") val isImage: Boolean = false,
    @SerializedName("is_audio") val isAudio: Boolean = false,
    @SerializedName("is_video") val isVideo: Boolean = false
)

data class MessageData(
    @SerializedName("id") val id: Int,
    @SerializedName("login") val login: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("recipient_read") val recipientRead: Boolean? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("files") val filesRaw: List<FileData>? = null
) {
    val authorLogin: String?
        get() = login?.ifBlank { null } ?: name?.ifBlank { null }

    val displayName: String
        get() = name?.ifBlank { null } ?: authorLogin ?: "Пользователь"

    val createdAt: Long
        get() = createdAtRaw?.let { parseIsoDateTime(it) } ?: 0L

    val files: List<FileData>
        get() = filesRaw ?: emptyList()
}

data class MessagesData(
    @SerializedName("data") val data: List<MessageData>? = null,
    @SerializedName("links") val links: DialogueLinks? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null,
    @SerializedName("dialogue") val dialogue: DialogueData? = null
)

data class ForumSection(
    @SerializedName("id") val id: Int,
    @SerializedName("parent_id") val parentId: Int = 0,
    @SerializedName("sort") val sort: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("closed") val closed: Boolean = false,
    @SerializedName("count_topics") val topicsCount: Int = 0,
    @SerializedName("count_posts") val postsCount: Int = 0,
    @SerializedName("last_topic_id") val lastTopicId: Int? = null,
    @SerializedName("last_topic_title") val lastTopicTitle: String? = null,
    @SerializedName("last_post_user_login") val lastPostUserLogin: String? = null,
    @SerializedName("last_post_user_name") val lastPostUserName: String? = null,
    @SerializedName("last_post_at") val lastPostAtRaw: String? = null,
    @SerializedName("parent") val parent: ForumSection? = null,
    @SerializedName("children") val children: List<ForumSection>? = null
) {
    val lastPostAt: Long?
        get() = lastPostAtRaw?.let { parseIsoDateTime(it) }

    fun isRootSection(): Boolean = parentId == 0

    fun getSubsections(): List<ForumSection> = children ?: emptyList()
}

data class ForumTopic(
    @SerializedName("id") val id: Int,
    @SerializedName("forum_id") val forumId: Int? = null,
    @SerializedName("section_id") val sectionId: Int? = null,
    @SerializedName("forum") val forum: ForumSection? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("login") val authorLogin: String? = null,
    @SerializedName("name") val authorName: String? = null,
    @SerializedName("closed") val closed: Boolean = false,
    @SerializedName("locked") val locked: Boolean = false,
    @SerializedName("count_posts") val postsCount: Int = 0,
    @SerializedName("visits") val visits: Int = 0,
    @SerializedName("moderators") val moderators: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("last_post_id") val lastPostId: Int? = null,
    @SerializedName("last_post_user_login") val lastPostUserLogin: String? = null,
    @SerializedName("last_post_user_name") val lastPostUserName: String? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("updated_at") val updatedAtRaw: String? = null
) {
    val lastPostAuthor: String?
        get() = lastPostUserName?.ifBlank { null }
            ?: lastPostUserLogin?.ifBlank { null }
            ?: authorName?.ifBlank { null }
            ?: authorLogin?.ifBlank { null }

    val realForumId: Int?
        get() = forumId ?: sectionId ?: forum?.id

    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }

    val updatedAt: Long?
        get() = updatedAtRaw?.let { parseIsoDateTime(it) }
}

data class ForumPost(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("login") val authorLogin: String? = null,
    @SerializedName("name") val authorName: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("files") val filesRaw: List<FileData>? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("updated_at") val updatedAtRaw: String? = null
) {
    val files: List<FileData>
        get() = filesRaw ?: emptyList()

    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }

    val updatedAt: Long?
        get() = updatedAtRaw?.let { parseIsoDateTime(it) }
}

data class ForumInfo(
    @SerializedName("id") val id: Int,
    @SerializedName("parent_id") val parentId: Int = 0,
    @SerializedName("sort") val sort: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("closed") val closed: Boolean = false,
    @SerializedName("count_topics") val topicsCount: Int = 0,
    @SerializedName("count_posts") val postsCount: Int = 0,
    @SerializedName("last_topic_id") val lastTopicId: Int? = null,
    @SerializedName("last_topic_title") val lastTopicTitle: String? = null,
    @SerializedName("last_post_user_login") val lastPostUserLogin: String? = null,
    @SerializedName("last_post_user_name") val lastPostUserName: String? = null,
    @SerializedName("last_post_at") val lastPostAtRaw: String? = null,
    @SerializedName("parent") val parent: ForumSection? = null
) {
    val lastPostAt: Long?
        get() = lastPostAtRaw?.let { parseIsoDateTime(it) }
}

data class TopicInfo(
    @SerializedName("id") val id: Int,
    @SerializedName("forum_id") val forumId: Int? = null,
    @SerializedName("section_id") val sectionId: Int? = null,
    @SerializedName("forum") val forum: ForumSection? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("login") val authorLogin: String? = null,
    @SerializedName("name") val authorName: String? = null,
    @SerializedName("closed") val closed: Boolean = false,
    @SerializedName("locked") val locked: Boolean = false,
    @SerializedName("count_posts") val postsCount: Int = 0,
    @SerializedName("visits") val visits: Int = 0,
    @SerializedName("moderators") val moderators: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("last_post_id") val lastPostId: Int? = null,
    @SerializedName("last_post_user_login") val lastPostUserLogin: String? = null,
    @SerializedName("last_post_user_name") val lastPostUserName: String? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("updated_at") val updatedAtRaw: String? = null
) {
    val realForumId: Int?
        get() = forumId ?: sectionId ?: forum?.id

    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }

    val updatedAt: Long?
        get() = updatedAtRaw?.let { parseIsoDateTime(it) }
}

data class ForumSectionsData(
    @SerializedName("data") val data: List<ForumSection>? = null
)

data class SendMessageResponse(
    @SerializedName("data") val data: MessageData? = null,
    @SerializedName("message") val message: String? = null
)

data class PostCreateResponse(
    @SerializedName("post") val post: ForumPost? = null,
    @SerializedName("data") val data: ForumPost? = null,
    @SerializedName("message") val message: String? = null
)

data class TopicCreateResponse(
    @SerializedName("topic") val topic: ForumTopic? = null,
    @SerializedName("data") val data: ForumTopic? = null,
    @SerializedName("message") val message: String? = null
)

data class ForumTopicsData(
    @SerializedName("data") val data: List<ForumTopic>? = null,
    @SerializedName("links") val links: DialogueLinks? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null,
    @SerializedName("forum") val forum: ForumInfo? = null
)

data class ForumPostsData(
    @SerializedName("data") val data: List<ForumPost>? = null,
    @SerializedName("links") val links: DialogueLinks? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null,
    @SerializedName("topic") val topic: TopicInfo? = null,
    @SerializedName("forum") val forum: ForumInfo? = null
)

data class NewsAuthor(
    @SerializedName("login") val login: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("status") val status: String? = null
) {
    val authorLogin: String?
        get() = login?.ifBlank { null }

    val displayName: String
        get() = name?.ifBlank { null } ?: authorLogin ?: "Пользователь"
}

data class VoteData(
    @SerializedName("type") val type: String? = null,
    @SerializedName("id") val id: Int? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("own") val own: Boolean = false
) {
    val hasVoted: Boolean
        get() = !value.isNullOrBlank()

    val isVotedUp: Boolean
        get() = value == "+"

    val isVotedDown: Boolean
        get() = value == "-"

    val canVote: Boolean
        get() = !own && value == null
}

typealias NewsVote = VoteData

data class VoteRequest(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Int,
    @SerializedName("vote") val vote: String = "+"
)

data class VoteResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("cancel") val cancel: Boolean = false,
    @SerializedName("rating") val rating: Int = 0
)

data class NewsItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("vote") val vote: NewsVote? = null,
    @SerializedName("closed") val closed: Boolean = false,
    @SerializedName("top") val top: Boolean = false,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    @SerializedName("user") val user: NewsAuthor? = null,
    @SerializedName("media") val media: List<FileData>? = null,
    @SerializedName("files") val files: List<FileData>? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("updated_at") val updatedAtRaw: String? = null
) {
    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }

    val updatedAt: Long?
        get() = updatedAtRaw?.let { parseIsoDateTime(it) }

    val safeMedia: List<FileData>
        get() = media ?: emptyList()

    val safeFiles: List<FileData>
        get() = files ?: emptyList()

    val primaryMedia: FileData?
        get() = safeMedia.firstOrNull() ?: safeFiles.firstOrNull()
}

data class NewsCommentParent(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("login") val login: String? = null,
    @SerializedName("excerpt") val excerpt: String? = null
)

data class NewsCommentItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("parent_id") val parentId: Int? = null,
    @SerializedName("depth") val depth: Int = 0,
    @SerializedName("parent") val parent: NewsCommentParent? = null,
    @SerializedName("deleted") val deleted: Boolean = false,
    @SerializedName("text") val text: String? = null,
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("vote") val vote: NewsVote? = null,
    @SerializedName("user") val user: NewsAuthor? = null,
    @SerializedName("media") val media: List<FileData>? = null,
    @SerializedName("files") val files: List<FileData>? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null
) {
    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }

    val safeMedia: List<FileData>
        get() = media ?: emptyList()

    val safeFiles: List<FileData>
        get() = files ?: emptyList()
}

data class NewsCommentsWrapper(
    @SerializedName("data") val data: List<NewsCommentItem>? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null,
    @SerializedName("links") val links: DialogueLinks? = null
)

data class NewsListResponse(
    @SerializedName("data") val data: List<NewsItem>? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null,
    @SerializedName("links") val links: DialogueLinks? = null
)

data class NewsDetailResponse(
    @SerializedName("data") val data: NewsItem? = null,
    @SerializedName("comments") val comments: NewsCommentsWrapper? = null
)

data class CommentCreateResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("comment") val comment: NewsCommentItem? = null
)

data class PhotoItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("vote") val vote: NewsVote? = null,
    @SerializedName("closed") val closed: Boolean = false,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    @SerializedName("user") val user: NewsAuthor? = null,
    @SerializedName("media") val media: List<FileData>? = null,
    @SerializedName("files") val files: List<FileData>? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("updated_at") val updatedAtRaw: String? = null
) {
    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }

    val updatedAt: Long?
        get() = updatedAtRaw?.let { parseIsoDateTime(it) }

    val safeMedia: List<FileData>
        get() = media ?: emptyList()

    val safeFiles: List<FileData>
        get() = files ?: emptyList()

    val allMedia: List<FileData>
        get() = (safeMedia + safeFiles).distinctBy { it.id }

    val primaryMedia: FileData?
        get() = allMedia.firstOrNull()

    val isVideo: Boolean
        get() = allMedia.any { it.isVideo || it.extension?.lowercase() in listOf("mp4", "webm", "mkv", "mov", "avi", "3gp") }
}

data class PhotosListResponse(
    @SerializedName("data") val data: List<PhotoItem>? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null,
    @SerializedName("links") val links: DialogueLinks? = null
)

data class PhotoDetailResponse(
    @SerializedName("data") val data: PhotoItem? = null,
    @SerializedName("comments") val comments: NewsCommentsWrapper? = null
)

data class CategoryItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("parent_id") val parentId: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("closed") val closed: Boolean? = false,
    @SerializedName("downs_count") val downsCount: Int? = 0,
    @SerializedName("children") val children: List<CategoryItem>? = null,
    @SerializedName("parent") val parent: CategoryItem? = null
) {
    val subcategories: List<CategoryItem>
        get() = children ?: emptyList()

    val totalDownsCount: Int
        get() = downsCount ?: 0

    val isClosed: Boolean
        get() = closed ?: false
}

data class DownFileItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("extension") val extension: String? = null,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("archive_url") val archiveUrl: String? = null
)

data class DownExternalLink(
    @SerializedName("name") val name: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null
)

data class DownItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("vote") val vote: NewsVote? = null,
    @SerializedName("category_id") val categoryId: Int = 0,
    @SerializedName("category") val category: CategoryItem? = null,
    @SerializedName("active") val active: Boolean = true,
    @SerializedName("downloads") val downloads: Int = 0,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    @SerializedName("user") val user: NewsAuthor? = null,
    @SerializedName("can_download") val canDownload: Boolean = true,
    @SerializedName("media") val media: List<FileData>? = null,
    @SerializedName("files") val files: List<DownFileItem>? = null,
    @SerializedName("links") val links: List<DownExternalLink>? = null,
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("updated_at") val updatedAtRaw: String? = null
) {
    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }

    val updatedAt: Long?
        get() = updatedAtRaw?.let { parseIsoDateTime(it) }

    val safeMedia: List<FileData>
        get() = media ?: emptyList()

    val safeFiles: List<DownFileItem>
        get() = files ?: emptyList()

    val safeLinks: List<DownExternalLink>
        get() = links ?: emptyList()

    val primaryMedia: FileData?
        get() = safeMedia.firstOrNull()

    val isVideo: Boolean
        get() = safeMedia.any { it.isVideo || it.extension?.lowercase() in listOf("mp4", "webm", "mkv", "mov", "avi", "3gp") }
}

data class LoadsCategoriesResponse(
    @SerializedName("data") val data: List<CategoryItem>? = null
)

data class DownsListResponse(
    @SerializedName("data") val data: List<DownItem>? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null,
    @SerializedName("links") val links: DialogueLinks? = null
)

data class DownDetailResponse(
    @SerializedName("data") val data: DownItem? = null,
    @SerializedName("comments") val comments: NewsCommentsWrapper? = null
)

/**
 * Модели общей ленты событий (GET /api/feed)
 */
data class FeedResponse(
    @SerializedName("data") val data: List<FeedItem> = emptyList(),
    @SerializedName("links") val links: DialogueLinks? = null,
    @SerializedName("meta") val meta: PaginationMeta? = null
)

data class FeedItem(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Long,
    @SerializedName("section") val section: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("breadcrumbs") val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    @SerializedName("text") val text: String? = null,
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("vote") val vote: FeedVote? = null,
    @SerializedName("comments_count") val commentsCount: Int? = null,
    @SerializedName("user") val user: FeedUserData? = null,
    @SerializedName("media") val media: List<FileData> = emptyList(),
    @SerializedName("files") val files: List<FileData> = emptyList(),
    @SerializedName("created_at") val createdAtRaw: String? = null,
    @SerializedName("relate") val relate: RelateData? = null
) {
    val isSupported: Boolean
        get() = when (type) {
            "topics", "news", "photos" -> true
            "comments" -> relate?.type in listOf("news", "photos", "downs")
            else -> false
        }

    val topicId: Int
        get() = id.toInt()

    val postId: Int?
        get() {
            if (type == "topics") {
                vote?.id?.let { return it.toInt() }
                val match = Regex("[?&]pid=(\\d+)").find(url ?: "")
                if (match != null) {
                    return match.groupValues[1].toIntOrNull()
                }
            }
            return null
        }

    val createdAt: Long?
        get() = createdAtRaw?.let { parseIsoDateTime(it) }
}

data class RelateData(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null
)

data class BreadcrumbItem(
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String? = null
)

data class FeedUserData(
    @SerializedName("login") val login: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("status") val status: String? = null
) {
    val displayName: String
        get() = name?.ifBlank { null } ?: login
}

data class FeedVote(
    @SerializedName("type") val type: String? = null,
    @SerializedName("id") val id: Long? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("own") val own: Boolean = false
)

/**
 * Модели статистики сайта (GET /stats)
 */
data class StatsItem(
    @SerializedName("total") val total: Long = 0,
    @SerializedName("today") val today: Long = 0
)

data class StatsUsers(
    @SerializedName("total") val total: Long = 0,
    @SerializedName("today") val today: Long = 0,
    @SerializedName("admins") val admins: Long = 0
)

data class StatsOnline(
    @SerializedName("users") val users: Long = 0,
    @SerializedName("guests") val guests: Long = 0,
    @SerializedName("total") val total: Long = 0
)

data class StatsSections(
    @SerializedName("articles") val articles: StatsItem? = null,
    @SerializedName("photos") val photos: StatsItem? = null,
    @SerializedName("guestbook") val guestbook: StatsItem? = null,
    @SerializedName("downs") val downs: StatsItem? = null,
    @SerializedName("news") val news: StatsItem? = null,
    @SerializedName("items") val items: StatsItem? = null,
    @SerializedName("offers") val offers: StatsItem? = null,
    @SerializedName("topics") val topics: StatsItem? = null,
    @SerializedName("posts") val posts: StatsItem? = null
)

data class StatsResponse(
    @SerializedName("users") val users: StatsUsers? = null,
    @SerializedName("online") val online: StatsOnline? = null,
    @SerializedName("sections") val sections: StatsSections? = null
)

/**
 * API сервис Visavi.net
 * RotorCMS OpenAPI v1.0.0
 */
interface VisaviApiService {
    @POST("api/auth")
    suspend fun auth(
        @Body request: AuthRequest
    ): Response<AuthResponse>

    @GET("api/config")
    suspend fun getConfig(): Response<ConfigData>

    @GET("api/user")
    suspend fun getUser(): Response<ApiData>

    @GET("api/users/{login}")
    suspend fun getUserByLogin(
        @Path("login") login: String
    ): Response<ApiData>

    @GET("api/dialogues")
    suspend fun getDialogues(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<DialoguesData>

    @GET("api/talk/{login}")
    suspend fun getTalk(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<MessagesData>

    /**
     * Отправка личного сообщения только текстом (FormUrlEncoded в POST /talk/{login})
     */
    @FormUrlEncoded
    @POST("api/talk/{login}")
    suspend fun sendTalkText(
        @Path("login") login: String,
        @Field("text") text: String
    ): Response<SendMessageResponse>

    /**
     * Отправка личного сообщения с файлами (Multipart в POST /talk/{login})
     */
    @Multipart
    @POST("api/talk/{login}")
    suspend fun sendTalkMultipart(
        @Path("login") login: String,
        @Part text: MultipartBody.Part,
        @Part files: List<MultipartBody.Part>? = null
    ): Response<SendMessageResponse>

    @GET("api/messages/new")
    suspend fun getNewMessages(): Response<NewMessagesResponse>

    @GET("api/forums")
    suspend fun getForumSections(): Response<ForumSectionsData>

    @GET("api/forums/{id}")
    suspend fun getForumSection(
        @Path("id") sectionId: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<ForumTopicsData>

    @Multipart
    @POST("api/forums/{id}")
    suspend fun createTopicMultipart(
        @Path("id") sectionId: Int,
        @Part title: MultipartBody.Part,
        @Part text: MultipartBody.Part,
        @Part files: List<MultipartBody.Part>? = null
    ): Response<TopicCreateResponse>

    @FormUrlEncoded
    @POST("api/forums/{id}")
    suspend fun createTopicForm(
        @Path("id") sectionId: Int,
        @Field("title") title: String,
        @Field("text") text: String
    ): Response<TopicCreateResponse>

    @GET("api/topics/{id}")
    suspend fun getTopicPosts(
        @Path("id") topicId: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("order") order: String = "desc"
    ): Response<ForumPostsData>

    @Multipart
    @POST("api/topics/{id}")
    suspend fun createPostMultipart(
        @Path("id") topicId: Int,
        @Part text: MultipartBody.Part,
        @Part files: List<MultipartBody.Part>? = null
    ): Response<PostCreateResponse>

    @FormUrlEncoded
    @POST("api/topics/{id}")
    suspend fun createPostForm(
        @Path("id") topicId: Int,
        @Field("text") text: String
    ): Response<PostCreateResponse>

    @GET("api/news")
    suspend fun getNewsList(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order") order: String = "desc"
    ): Response<NewsListResponse>

    @GET("api/news/{id}")
    suspend fun getNewsDetail(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order") order: String = "asc"
    ): Response<NewsDetailResponse>

    @Multipart
    @POST("api/comments")
    suspend fun createCommentMultipart(
        @Part("type") type: okhttp3.RequestBody,
        @Part("id") id: okhttp3.RequestBody,
        @Part("text") text: okhttp3.RequestBody,
        @Part("parent_id") parentId: okhttp3.RequestBody? = null,
        @Part files: List<MultipartBody.Part>? = null
    ): Response<CommentCreateResponse>

    @FormUrlEncoded
    @POST("api/comments")
    suspend fun createCommentForm(
        @Field("type") type: String,
        @Field("id") id: Int,
        @Field("text") text: String,
        @Field("parent_id") parentId: Int? = null
    ): Response<CommentCreateResponse>

    @GET("api/photos")
    suspend fun getPhotosList(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order") order: String = "desc",
        @Query("sort") sort: String? = null
    ): Response<PhotosListResponse>

    @GET("api/photos/{id}")
    suspend fun getPhotoDetail(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order") order: String = "asc"
    ): Response<PhotoDetailResponse>

    @GET("api/loads")
    suspend fun getLoadCategories(): Response<LoadsCategoriesResponse>

    @GET("api/downs")
    suspend fun getDownsList(
        @Query("category_id") categoryId: Int? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order") order: String = "desc",
        @Query("sort") sort: String? = null
    ): Response<DownsListResponse>

    @GET("api/downs/{id}")
    suspend fun getDownDetail(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order") order: String = "asc"
    ): Response<DownDetailResponse>

    @POST("api/rating")
    suspend fun vote(
        @Body request: VoteRequest
    ): Response<VoteResponse>

    @GET("api/stats")
    suspend fun getStats(): Response<StatsResponse>

    @GET("api/feed")
    suspend fun getFeed(
        @Query("page") page: Int = 1
    ): Response<FeedResponse>
}

object VisaviApi {
    const val BASE_HOST = "visavi.net"
    const val BASE_URL = "https://$BASE_HOST/"

    private var apiToken: String? = null

    fun setToken(token: String) {
        apiToken = token
    }

    fun getToken(): String? = apiToken

    fun clearToken() {
        apiToken = null
    }

    val instance: VisaviApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.ramzes.visavinet.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val authInterceptor = okhttp3.Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "VisaviClient")

            apiToken?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VisaviApiService::class.java)
    }

    suspend fun vote(
        type: String,
        id: Int,
        vote: String = "+"
    ): Response<VoteResponse> {
        return instance.vote(VoteRequest(type = type, id = id, vote = vote))
    }
}
