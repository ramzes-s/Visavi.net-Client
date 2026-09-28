package com.ramzes.visavinet

import android.content.Context
import android.content.SharedPreferences
import com.ramzes.visavinet.network.FileData
import com.ramzes.visavinet.util.CommentsReadTracker
import com.ramzes.visavinet.util.ContentBlock
import com.ramzes.visavinet.util.DraftsManager
import com.ramzes.visavinet.util.ForumTopicReadTracker
import com.ramzes.visavinet.util.parseHtmlToBlocks
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NewFeaturesTest {

    private lateinit var fakeContext: Context

    @Before
    fun setUp() {
        fakeContext = createFakeContext()
    }

    // =========================================================================
    // 1. CommentsReadTracker Tests
    // =========================================================================

    @Test
    fun testCommentsReadTrackerDefaultState() {
        // По умолчанию для неизвестного элемента нет записей
        assertFalse(CommentsReadTracker.hasRecord(fakeContext, CommentsReadTracker.SECTION_NEWS, 10))
        assertEquals(-1, CommentsReadTracker.getReadCount(fakeContext, CommentsReadTracker.SECTION_NEWS, 10))
        // Если запись еще не создавалась (первое появление), дельта новых комментариев равна 0
        assertEquals(0, CommentsReadTracker.getNewCommentsCount(fakeContext, CommentsReadTracker.SECTION_NEWS, 10, 5))
    }

    @Test
    fun testCommentsReadTrackerMarkAndCalculateNew() {
        val section = CommentsReadTracker.SECTION_NEWS
        val newsId = 42

        // Пользователь открыл новость, где было 5 комментариев
        CommentsReadTracker.markAsRead(fakeContext, section, newsId, 5)

        assertTrue(CommentsReadTracker.hasRecord(fakeContext, section, newsId))
        assertEquals(5, CommentsReadTracker.getReadCount(fakeContext, section, newsId))

        // Количество комментариев в списке не изменилось (5) -> новых 0
        assertEquals(0, CommentsReadTracker.getNewCommentsCount(fakeContext, section, newsId, 5))

        // Количество комментариев в списке увеличилось до 9 -> новых 4 (+4)
        assertEquals(4, CommentsReadTracker.getNewCommentsCount(fakeContext, section, newsId, 9))

        // Количество комментариев меньше или равно прочитанному -> новых 0
        assertEquals(0, CommentsReadTracker.getNewCommentsCount(fakeContext, section, newsId, 3))
    }

    @Test
    fun testCommentsReadTrackerPreventsCountRegression() {
        val section = CommentsReadTracker.SECTION_GALLERY
        val photoId = 100

        // Записали 10 комментариев
        CommentsReadTracker.markAsRead(fakeContext, section, photoId, 10)
        assertEquals(10, CommentsReadTracker.getReadCount(fakeContext, section, photoId))

        // Случайный вызов с меньшим значением (например, кэш) не должен откатывать счетчик
        CommentsReadTracker.markAsRead(fakeContext, section, photoId, 7)
        assertEquals(10, CommentsReadTracker.getReadCount(fakeContext, section, photoId))

        // Запись с большим значением должна обновлять счетчик
        CommentsReadTracker.markAsRead(fakeContext, section, photoId, 15)
        assertEquals(15, CommentsReadTracker.getReadCount(fakeContext, section, photoId))
    }

    @Test
    fun testCommentsReadTrackerDifferentSectionsIsolated() {
        val id = 50
        CommentsReadTracker.markAsRead(fakeContext, CommentsReadTracker.SECTION_NEWS, id, 10)
        CommentsReadTracker.markAsRead(fakeContext, CommentsReadTracker.SECTION_GALLERY, id, 20)
        CommentsReadTracker.markAsRead(fakeContext, CommentsReadTracker.SECTION_DOWNS, id, 30)

        assertEquals(10, CommentsReadTracker.getReadCount(fakeContext, CommentsReadTracker.SECTION_NEWS, id))
        assertEquals(20, CommentsReadTracker.getReadCount(fakeContext, CommentsReadTracker.SECTION_GALLERY, id))
        assertEquals(30, CommentsReadTracker.getReadCount(fakeContext, CommentsReadTracker.SECTION_DOWNS, id))

        assertEquals(5, CommentsReadTracker.getNewCommentsCount(fakeContext, CommentsReadTracker.SECTION_NEWS, id, 15))
        assertEquals(0, CommentsReadTracker.getNewCommentsCount(fakeContext, CommentsReadTracker.SECTION_GALLERY, id, 15))
    }

    @Test
    fun testCommentsReadTrackerInvalidIds() {
        // Проверка защиты от нулевых и отрицательных ID
        CommentsReadTracker.markAsRead(fakeContext, CommentsReadTracker.SECTION_NEWS, 0, 10)
        CommentsReadTracker.markAsRead(fakeContext, CommentsReadTracker.SECTION_NEWS, -5, 10)

        assertFalse(CommentsReadTracker.hasRecord(fakeContext, CommentsReadTracker.SECTION_NEWS, 0))
        assertFalse(CommentsReadTracker.hasRecord(fakeContext, CommentsReadTracker.SECTION_NEWS, -5))
        assertEquals(0, CommentsReadTracker.getNewCommentsCount(fakeContext, CommentsReadTracker.SECTION_NEWS, 0, 10))
        assertEquals(0, CommentsReadTracker.getNewCommentsCount(fakeContext, CommentsReadTracker.SECTION_NEWS, 5, -2))
    }

    // =========================================================================
    // 2. DraftsManager Tests
    // =========================================================================

    @Test
    fun testDraftsManagerKeyGenerators() {
        assertEquals("forum_reply_123", DraftsManager.forumReplyKey(123))
        assertEquals("forum_create_topic_5", DraftsManager.forumCreateTopicKey(5))
        assertEquals("news_comment_42", DraftsManager.newsCommentKey(42))
        assertEquals("gallery_comment_99", DraftsManager.galleryCommentKey(99))
        assertEquals("down_comment_77", DraftsManager.downCommentKey(77))
        assertEquals("dialogue_user_10", DraftsManager.dialogueKey("user_10"))
    }

    @Test
    fun testDraftsManagerSaveAndRetrieve() {
        val key = DraftsManager.forumReplyKey(101)

        // Изначально черновик пустой
        assertEquals("", DraftsManager.getDraft(fakeContext, key))

        // Сохранение текста черновика
        val draftText = "Привет, вот мой черновик сообщения!"
        DraftsManager.saveDraft(fakeContext, key, draftText)
        assertEquals(draftText, DraftsManager.getDraft(fakeContext, key))

        // Сохранение пустой строки или строки из пробелов автоматически удаляет черновик
        DraftsManager.saveDraft(fakeContext, key, "   \n\t  ")
        assertEquals("", DraftsManager.getDraft(fakeContext, key))
    }

    @Test
    fun testDraftsManagerClearExplicit() {
        val key = DraftsManager.newsCommentKey(33)
        DraftsManager.saveDraft(fakeContext, key, "Хорошая новость")
        assertEquals("Хорошая новость", DraftsManager.getDraft(fakeContext, key))

        DraftsManager.clearDraft(fakeContext, key)
        assertEquals("", DraftsManager.getDraft(fakeContext, key))
    }

    // =========================================================================
    // 3. ForumTopicReadTracker Tests
    // =========================================================================

    @Test
    fun testForumTopicReadTrackerDefault() {
        // Для непрочитанной темы возвращается 0
        assertEquals(0, ForumTopicReadTracker.getLastSeenPostId(fakeContext, 500))
    }

    @Test
    fun testForumTopicReadTrackerSaveAndPreventRegression() {
        val topicId = 777

        // Сохраняем последний увиденный пост #100
        ForumTopicReadTracker.saveLastSeenPostId(fakeContext, topicId, 100)
        assertEquals(100, ForumTopicReadTracker.getLastSeenPostId(fakeContext, topicId))

        // Попытка сохранить меньший ID (старый пост) не должна перезаписывать
        ForumTopicReadTracker.saveLastSeenPostId(fakeContext, topicId, 85)
        assertEquals(100, ForumTopicReadTracker.getLastSeenPostId(fakeContext, topicId))

        // Новый пост с большим ID успешно перезаписывает
        ForumTopicReadTracker.saveLastSeenPostId(fakeContext, topicId, 150)
        assertEquals(150, ForumTopicReadTracker.getLastSeenPostId(fakeContext, topicId))
    }

    @Test
    fun testForumTopicReadTrackerInvalidArguments() {
        ForumTopicReadTracker.saveLastSeenPostId(fakeContext, 0, 100)
        ForumTopicReadTracker.saveLastSeenPostId(fakeContext, -1, 100)
        ForumTopicReadTracker.saveLastSeenPostId(fakeContext, 100, 0)
        ForumTopicReadTracker.saveLastSeenPostId(fakeContext, 100, -5)

        assertEquals(0, ForumTopicReadTracker.getLastSeenPostId(fakeContext, 0))
        assertEquals(0, ForumTopicReadTracker.getLastSeenPostId(fakeContext, -1))
        assertEquals(0, ForumTopicReadTracker.getLastSeenPostId(fakeContext, 100))
    }

    // =========================================================================
    // 4. File and Image Helpers (Swipe Lightbox Logic)
    // =========================================================================

    @Test
    fun testIsImageFileDetection() {
        // Тестируем флаг isImage
        val fileWithFlag = FileData(
            id = 1,
            name = "custom_file.bin",
            path = "https://example.com/file.bin",
            extension = "bin",
            isImage = true
        )
        assertTrue(isImageFile(fileWithFlag))

        // Тестируем стандартные расширения картинок без установленного флага isImage
        val extensions = listOf("jpg", "JPG", "jpeg", "JPEG", "png", "PNG", "webp", "WEBP", "gif", "GIF")
        for (ext in extensions) {
            val file = FileData(
                id = 2,
                name = "photo.$ext",
                path = "https://example.com/photos/photo.$ext",
                extension = ext,
                isImage = false
            )
            assertTrue("Расширение $ext должно определяться как картинка", isImageFile(file))
        }

        // Тестируем извлечение расширения из path, если extension == null
        val fileNullExt = FileData(
            id = 3,
            name = "photo",
            path = "https://example.com/uploads/vacation.png",
            extension = null,
            isImage = false
        )
        assertTrue(isImageFile(fileNullExt))

        // Некартиночные форматы
        val nonImages = listOf("zip", "rar", "apk", "pdf", "mp3", "mp4", "txt", "docx")
        for (ext in nonImages) {
            val file = FileData(
                id = 4,
                name = "archive.$ext",
                path = "https://example.com/files/archive.$ext",
                extension = ext,
                isImage = false
            )
            assertFalse("Расширение $ext НЕ должно определяться как картинка", isImageFile(file))
        }
    }

    @Test
    fun testParseHtmlExtractsImageBlocksForSwiping() {
        val html = """
            <p>Вот наши фотографии с поездки:</p>
            <img src="https://example.com/img1.jpg" alt="Photo 1" />
            <p>А вот вторая:</p>
            <img src="https://example.com/img2.png" />
            <p>И смайлик: <img src="/assets/smiles/smile.gif" class="sticker" /></p>
        """.trimIndent()

        val blocks = parseHtmlToBlocks(html)
        val imageBlocks = blocks.filterIsInstance<ContentBlock.ImageBlock>()

        // Должно быть распознано 2 изображения (смайлы/стикеры фильтруются или не входят в крупные ImageBlock)
        assertEquals(2, imageBlocks.size)
        assertEquals("https://example.com/img1.jpg", imageBlocks[0].url)
        assertEquals("https://example.com/img2.png", imageBlocks[1].url)

        // Проверяем возможность извлечь список URL для свайп-просмотра в ImageLightboxDialog
        val allUrls = imageBlocks.map { it.url }
        assertEquals(listOf("https://example.com/img1.jpg", "https://example.com/img2.png"), allUrls)
    }

    // =========================================================================
    // 5. User Search & New Dialogue Tests
    // =========================================================================

    @Test
    fun testSearchUserModelProperties() {
        val user1 = com.ramzes.visavinet.network.SearchUser(
            login = "test_user",
            name = "Test Name",
            level = "admin",
            color = "#FF0000",
            avatar = "https://example.com/avatar.jpg",
            status = "Online"
        )
        assertEquals("test_user", user1.login)
        assertEquals("Test Name", user1.displayName)
        assertEquals("https://example.com/avatar.jpg", user1.avatarUrl)

        val user2 = com.ramzes.visavinet.network.SearchUser(
            login = "simple_user",
            name = "",
            level = "user",
            color = null,
            avatar = "/uploads/avatars/user2.png",
            status = null
        )
        assertEquals("simple_user", user2.displayName)
        assertEquals("https://visavi.net/uploads/avatars/user2.png", user2.avatarUrl)
    }

    @Test
    fun testSearchUsersResponseJsonParsing() {
        val json = """
            {
                "data": [
                    {
                        "login": "Admiral",
                        "name": "Адмирал",
                        "level": "user",
                        "color": "#00FF00",
                        "avatar": "https://visavi.net/uploads/avatars/admiral.png",
                        "status": "<i>Капитан</i>"
                    },
                    {
                        "login": "Admin7",
                        "name": null,
                        "level": "admin",
                        "color": null,
                        "avatar": null,
                        "status": null
                    }
                ]
            }
        """.trimIndent()

        val parsed = com.google.gson.Gson().fromJson(json, com.ramzes.visavinet.network.UsersSearchResponse::class.java)
        assertNotNull(parsed)
        val users = parsed.data
        assertNotNull(users)
        assertEquals(2, users!!.size)

        assertEquals("Admiral", users[0].login)
        assertEquals("Адмирал", users[0].displayName)
        assertEquals("#00FF00", users[0].color)

        assertEquals("Admin7", users[1].login)
        assertEquals("Admin7", users[1].displayName)
        assertNull(users[1].avatarUrl)
    }

    // =========================================================================
    // In-memory SharedPreferences Test Double
    // =========================================================================

    private class FakeContextWrapper : android.content.ContextWrapper(null) {
        private val prefsStorage = mutableMapOf<String, SharedPreferences>()

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            val key = name ?: "default"
            return prefsStorage.getOrPut(key) { FakeSharedPreferences() }
        }
    }

    private fun createFakeContext(): Context {
        return FakeContextWrapper()
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)

        override fun getString(key: String?, defValue: String?): String? {
            return (data[key] as? String) ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (data[key] as? MutableSet<String>) ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return (data[key] as? Int) ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return (data[key] as? Long) ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return (data[key] as? Float) ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return (data[key] as? Boolean) ?: defValue
        }

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(data)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val target: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = this // специальный маркер удаления
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearFlag) {
                    target.clear()
                }
                for ((k, v) in pending) {
                    if (v === this) {
                        target.remove(k)
                    } else if (v != null) {
                        target[k] = v
                    } else {
                        target.remove(k)
                    }
                }
                pending.clear()
                clearFlag = false
            }
        }
    }
}
