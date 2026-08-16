package com.ramzes.visavinet

import com.google.gson.Gson
import com.ramzes.visavinet.network.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ApiResilienceAndSerializationTest {

    private val gson = Gson()

    @Test
    fun testEmptyJsonDeserializationForDowns() {
        val emptyJson = "{}"
        val downItem = gson.fromJson(emptyJson, DownItem::class.java)

        assertNotNull(downItem)
        assertEquals(0, downItem.id)
        assertNull(downItem.title)
        assertNull(downItem.text)
        assertEquals(0, downItem.rating)
        assertEquals(0, downItem.categoryId)
        assertEquals(0, downItem.downloads)
        assertEquals(0, downItem.commentsCount)

        // Проверка безопасности всех геттеров при null полях
        assertTrue(downItem.safeMedia.isEmpty())
        assertTrue(downItem.safeFiles.isEmpty())
        assertTrue(downItem.safeLinks.isEmpty())
        assertNull(downItem.primaryMedia)
        assertFalse(downItem.isVideo)
        assertNull(downItem.createdAt)
        assertNull(downItem.updatedAt)
    }

    @Test
    fun testEmptyJsonDeserializationForCategories() {
        val emptyJson = "{}"
        val category = gson.fromJson(emptyJson, CategoryItem::class.java)

        assertNotNull(category)
        assertEquals(0, category.id)
        assertNull(category.name)
        assertNull(category.parentId)
        assertFalse(category.isClosed)
        assertEquals(0, category.totalDownsCount)
        assertTrue(category.subcategories.isEmpty())
    }

    @Test
    fun testCategoryWithNullChildrenInJson() {
        val jsonWithNullChildren = """
            {
                "id": 10,
                "name": "Программы",
                "downs_count": 5,
                "children": null
            }
        """.trimIndent()

        val category = gson.fromJson(jsonWithNullChildren, CategoryItem::class.java)
        assertNotNull(category)
        assertEquals(10, category.id)
        assertEquals("Программы", category.name)
        assertEquals(5, category.totalDownsCount)
        // Проверяем, что обращение к subcategories возвращает emptyList и не бросает NPE!
        assertTrue(category.subcategories.isEmpty())
        assertEquals(0, category.subcategories.size)
    }

    @Test
    fun testEmptyJsonDeserializationForGalleryPhotos() {
        val emptyJson = "{}"
        val photo = gson.fromJson(emptyJson, PhotoItem::class.java)

        assertNotNull(photo)
        assertEquals(0, photo.id)
        assertNull(photo.title)
        assertNull(photo.text)
        assertEquals(0, photo.rating)
        assertEquals(0, photo.commentsCount)
        assertTrue(photo.safeMedia.isEmpty())
        assertTrue(photo.safeFiles.isEmpty())
        assertNull(photo.primaryMedia)
        assertFalse(photo.isVideo)
        assertNull(photo.createdAt)
    }

    @Test
    fun testEmptyJsonDeserializationForNews() {
        val emptyJson = "{}"
        val news = gson.fromJson(emptyJson, NewsItem::class.java)

        assertNotNull(news)
        assertEquals(0, news.id)
        assertNull(news.title)
        assertNull(news.text)
        assertEquals(0, news.commentsCount)
        assertTrue(news.safeMedia.isEmpty())
        assertNull(news.primaryMedia)
        assertNull(news.createdAt)
    }

    @Test
    fun testEmptyJsonDeserializationForForum() {
        val emptyJson = "{}"
        val section = gson.fromJson(emptyJson, ForumSection::class.java)
        assertNotNull(section)
        assertEquals(0, section.id)
        assertNull(section.title)
        assertEquals(0, section.topicsCount)
        assertEquals(0, section.postsCount)

        val topic = gson.fromJson(emptyJson, ForumTopic::class.java)
        assertNotNull(topic)
        assertEquals(0, topic.id)
        assertNull(topic.title)
        assertEquals(0, topic.postsCount)

        val post = gson.fromJson(emptyJson, ForumPost::class.java)
        assertNotNull(post)
        assertEquals(0, post.id)
        assertNull(post.text)
        assertTrue(post.files.isEmpty())
    }

    @Test
    fun testEmptyJsonDeserializationForDialoguesAndMessages() {
        val emptyJson = "{}"
        val dialogue = gson.fromJson(emptyJson, DialogueData::class.java)
        assertNotNull(dialogue)
        assertEquals(0, dialogue.id)
        assertNull(dialogue.login)
        assertEquals(0L, dialogue.createdAt)

        val message = gson.fromJson(emptyJson, MessageData::class.java)
        assertNotNull(message)
        assertEquals(0, message.id)
        assertNull(message.text)
        assertTrue(message.files.isEmpty())
        assertEquals(0L, message.createdAt)
    }

    @Test
    fun testApiErrorResponseValidationArray() {
        val validationJson = """
            {
                "message": "The given data was invalid.",
                "errors": {
                    "text": ["Поле текст обязательно для заполнения.", "Минимум 5 символов."],
                    "title": ["Заголовок не может быть пустым."]
                }
            }
        """.trimIndent()

        val parsed = gson.fromJson(validationJson, ApiErrorResponse::class.java)
        assertNotNull(parsed)
        val formatted = parsed.getFormattedError()
        assertTrue(formatted.contains("Поле текст обязательно для заполнения."))
        assertTrue(formatted.contains("Заголовок не может быть пустым."))
    }

    @Test
    fun testApiErrorResponseSimpleMessage() {
        val simpleJson = """{"message": "Неверный логин или пароль"}"""
        val parsed = gson.fromJson(simpleJson, ApiErrorResponse::class.java)
        assertNotNull(parsed)
        assertEquals("Неверный логин или пароль", parsed.getFormattedError())
    }

    @Test
    fun testExtractErrorMessageFromHtmlServerCrash() {
        val htmlError = "<html><head><title>502 Bad Gateway</title></head><body><center>nginx</center></body></html>"
        val responseBody = htmlError.toResponseBody("text/html".toMediaTypeOrNull())
        val response: Response<Any> = Response.error(502, responseBody)

        val message = response.extractErrorMessage("Ошибка")
        assertEquals("Сервер временно недоступен (502)", message)
    }

    @Test
    fun testExtractErrorMessageFrom404Html() {
        val htmlError = "<html><body>404 Not Found</body></html>"
        val responseBody = htmlError.toResponseBody("text/html".toMediaTypeOrNull())
        val response: Response<Any> = Response.error(404, responseBody)

        val message = response.extractErrorMessage("Ошибка")
        assertEquals("Запрашиваемый ресурс не найден", message)
    }

    @Test
    fun testExtractErrorMessageFromJson422() {
        val jsonError = """{"errors": {"password": ["Слишком короткий пароль"]}}"""
        val responseBody = jsonError.toResponseBody("application/json".toMediaTypeOrNull())
        val response: Response<Any> = Response.error(422, responseBody)

        val message = response.extractErrorMessage("Ошибка")
        assertEquals("Слишком короткий пароль", message)
    }
}
