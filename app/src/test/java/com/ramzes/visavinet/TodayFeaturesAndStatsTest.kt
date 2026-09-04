package com.ramzes.visavinet

import com.google.gson.Gson
import com.ramzes.visavinet.network.ForumSection
import com.ramzes.visavinet.network.StatsResponse
import org.junit.Assert.*
import org.junit.Test

class TodayFeaturesAndStatsTest {

    private val gson = Gson()

    @Test
    fun testStatsJsonParsing() {
        val json = """
            {
              "users": {
                "total": 15783,
                "today": 5,
                "admins": 6
              },
              "online": {
                "users": 17,
                "guests": 215,
                "total": 232
              },
              "sections": {
                "articles": {
                  "total": 500,
                  "today": 0
                },
                "photos": {
                  "total": 2482,
                  "today": 3
                },
                "guestbook": {
                  "total": 7626,
                  "today": 0
                },
                "downs": {
                  "total": 1914,
                  "today": 12
                },
                "news": {
                  "total": 329,
                  "today": 1
                },
                "items": {
                  "total": 0,
                  "today": 0
                },
                "offers": {
                  "total": 207,
                  "today": 0
                },
                "topics": {
                  "total": 39148,
                  "today": 8
                },
                "posts": {
                  "total": 626258,
                  "today": 42
                }
              }
            }
        """.trimIndent()

        val stats = gson.fromJson(json, StatsResponse::class.java)

        assertNotNull(stats)
        assertNotNull(stats.users)
        assertEquals(15783L, stats.users?.total)
        assertEquals(5L, stats.users?.today)
        assertEquals(6L, stats.users?.admins)

        assertNotNull(stats.online)
        assertEquals(17L, stats.online?.users)
        assertEquals(215L, stats.online?.guests)
        assertEquals(232L, stats.online?.total)

        assertNotNull(stats.sections)
        assertEquals(42L, stats.sections?.posts?.today)
        assertEquals(3L, stats.sections?.photos?.today)
        assertEquals(1L, stats.sections?.news?.today)
        assertEquals(12L, stats.sections?.downs?.today)
        assertEquals(0L, stats.sections?.articles?.today)
    }

    @Test
    fun testStatsResilienceWithEmptyJson() {
        val emptyJson = "{}"
        val stats = gson.fromJson(emptyJson, StatsResponse::class.java)

        assertNotNull(stats)
        assertNull(stats.users)
        assertNull(stats.online)
        assertNull(stats.sections)

        // Проверка безопасного извлечения значений today через elvis оператор
        val postsToday = stats?.sections?.posts?.today ?: 0L
        val photosToday = stats?.sections?.photos?.today ?: 0L
        val newsToday = stats?.sections?.news?.today ?: 0L
        val downsToday = stats?.sections?.downs?.today ?: 0L

        assertEquals(0L, postsToday)
        assertEquals(0L, photosToday)
        assertEquals(0L, newsToday)
        assertEquals(0L, downsToday)
    }

    @Test
    fun testForumSortByNewestLogic() {
        // Создаем тестовые разделы с разным временем активности
        val secOld = ForumSection(
            id = 1,
            sort = 1,
            title = "Старый раздел",
            lastPostAtRaw = "2025-01-01T10:00:00+03:00"
        )
        val secNew = ForumSection(
            id = 2,
            sort = 2,
            title = "Новый раздел",
            lastPostAtRaw = "2026-09-02T18:00:00+03:00"
        )
        val secWithNewerChild = ForumSection(
            id = 3,
            sort = 3,
            title = "Раздел со свежим подразделом",
            lastPostAtRaw = "2024-01-01T10:00:00+03:00",
            children = listOf(
                ForumSection(
                    id = 31,
                    sort = 1,
                    title = "Подраздел",
                    lastPostAtRaw = "2026-09-02T19:00:00+03:00" // Самое свежее сообщение
                )
            )
        )
        val secNoPosts = ForumSection(
            id = 4,
            sort = 4,
            title = "Раздел без постов",
            lastPostAtRaw = null
        )

        val rawList = listOf(secOld, secNew, secWithNewerChild, secNoPosts)

        // 1. Сортировка по умолчанию (когда sortByNewest == false) - сохраняет исходный порядок
        assertEquals(1, rawList[0].id)
        assertEquals(2, rawList[1].id)
        assertEquals(3, rawList[2].id)
        assertEquals(4, rawList[3].id)

        // 2. Сортировка по новизне (когда sortByNewest == true)
        val sortedList = rawList.sortedWith(
            compareByDescending<ForumSection> {
                maxOf(it.lastPostAt ?: 0L, it.children?.mapNotNull { c -> c.lastPostAt }?.maxOrNull() ?: 0L)
            }.thenBy { it.sort }
        )

        // secWithNewerChild имеет 19:00 -> должен быть первым
        assertEquals(3, sortedList[0].id)
        // secNew имеет 18:00 -> второй
        assertEquals(2, sortedList[1].id)
        // secOld имеет 2025 год -> третий
        assertEquals(1, sortedList[2].id)
        // secNoPosts не имеет постов (0) -> в конце
        assertEquals(4, sortedList[3].id)
    }

    @Test
    fun testForumSortStableWithEqualDates() {
        val secA = ForumSection(
            id = 10,
            sort = 1,
            title = "Раздел А",
            lastPostAtRaw = "2026-08-01T12:00:00+03:00"
        )
        val secB = ForumSection(
            id = 20,
            sort = 2,
            title = "Раздел Б",
            lastPostAtRaw = "2026-08-01T12:00:00+03:00"
        )

        val list = listOf(secB, secA)
        val sorted = list.sortedWith(
            compareByDescending<ForumSection> {
                maxOf(it.lastPostAt ?: 0L, it.children?.mapNotNull { c -> c.lastPostAt }?.maxOrNull() ?: 0L)
            }.thenBy { it.sort }
        )

        // При равных датах упорядочивание происходит по sort (sort = 1 перед sort = 2)
        assertEquals(10, sorted[0].id)
        assertEquals(20, sorted[1].id)
    }

    @Test
    fun testGitHubReleaseParsingAndApkUrl() {
        val json = """
            {
              "tag_name": "v1.1.4",
              "name": "Visavi.net Client v1.1.4",
              "html_url": "https://github.com/ramzes-s/Visavi.net-Client/releases/tag/v1.1.4",
              "body": "Описание релиза",
              "assets": [
                {
                  "name": "Visavi.net.Client.v1.1.4.apk",
                  "browser_download_url": "https://github.com/ramzes-s/Visavi.net-Client/releases/download/v1.1.4/Visavi.net.Client.v1.1.4.apk",
                  "size": 6599124
                }
              ]
            }
        """.trimIndent()

        val release = gson.fromJson(json, com.ramzes.visavinet.network.GitHubRelease::class.java)

        assertNotNull(release)
        assertEquals("v1.1.4", release.tagName)
        assertEquals("Visavi.net Client v1.1.4", release.name)
        assertEquals("https://github.com/ramzes-s/Visavi.net-Client/releases/tag/v1.1.4", release.htmlUrl)
        assertEquals("https://github.com/ramzes-s/Visavi.net-Client/releases/download/v1.1.4/Visavi.net.Client.v1.1.4.apk", release.apkDownloadUrl)
    }

    @Test
    fun testSemanticVersionComparison() {
        val isNewer = { cur: String, lat: String -> com.ramzes.visavinet.network.isNewerVersion(cur, lat) }

        // Новее патч-версия
        assertTrue(isNewer("1.1.4", "1.1.5"))
        assertTrue(isNewer("1.1.4", "v1.1.5"))

        // Новее минорная или мажорная версия
        assertTrue(isNewer("1.1.4", "1.2.0"))
        assertTrue(isNewer("1.1.4", "2.0.0"))
        assertTrue(isNewer("v1.1.4", "v1.2.0"))

        // Одинаковые версии
        assertFalse(isNewer("1.1.4", "1.1.4"))
        assertFalse(isNewer("1.1.4", "v1.1.4"))
        assertFalse(isNewer("v1.1.4", "1.1.4"))

        // Более старая версия
        assertFalse(isNewer("1.1.4", "1.1.3"))
        assertFalse(isNewer("1.1.4", "1.0.9"))
        assertFalse(isNewer("1.1.4", "0.9.9"))
        assertFalse(isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun testUpdateCheckThrottleAndFormatting() {
        // Проверка константы интервала: 6 часов в миллисекундах
        assertEquals(6 * 60 * 60 * 1000L, SettingsViewModel.UPDATE_CHECK_INTERVAL_MS)

        // Проверка человекопонятного форматирования оставшегося времени
        assertEquals("5 ч. 42 мин.", SettingsViewModel.formatRemainingTime(5 * 3600 + 42 * 60 + 15))
        assertEquals("2 ч.", SettingsViewModel.formatRemainingTime(2 * 3600))
        assertEquals("45 мин.", SettingsViewModel.formatRemainingTime(45 * 60 + 10))
        assertEquals("30 сек.", SettingsViewModel.formatRemainingTime(30))
    }

    @Test
    fun testDetectTotalUpdates() {
        // 1. Первый запуск (нет baseline) -> уведомления не отправляются
        val snapshot1 = com.ramzes.visavinet.util.TotalStatsSnapshot(
            postsTotal = 1000,
            newsTotal = 200,
            photosTotal = 500,
            downsTotal = 150
        )
        val initialUpdates = com.ramzes.visavinet.util.detectTotalUpdates(null, snapshot1)
        assertTrue(initialUpdates.isEmpty())

        // 2. Нет изменений -> список пуст
        val noChangeUpdates = com.ramzes.visavinet.util.detectTotalUpdates(snapshot1, snapshot1)
        assertTrue(noChangeUpdates.isEmpty())

        // 3. Увеличение в разделах Форум (+2) и Новости (+1)
        val snapshot2 = com.ramzes.visavinet.util.TotalStatsSnapshot(
            postsTotal = 1002,
            newsTotal = 201,
            photosTotal = 500,
            downsTotal = 150
        )
        val updates = com.ramzes.visavinet.util.detectTotalUpdates(snapshot1, snapshot2)
        assertEquals(2, updates.size)
        assertTrue(updates.contains("Форум +2"))
        assertTrue(updates.contains("Новости +1"))

        // 4. Уменьшение значений (например, удалили посты/новости) -> уведомления не отправляются
        val decreasedSnapshot = com.ramzes.visavinet.util.TotalStatsSnapshot(
            postsTotal = 995,
            newsTotal = 201,
            photosTotal = 490,
            downsTotal = 150
        )
        val decreasedUpdates = com.ramzes.visavinet.util.detectTotalUpdates(snapshot2, decreasedSnapshot)
        assertTrue(decreasedUpdates.isEmpty())
    }

    @Test
    fun testFeedJsonParsingAndFiltering() {
        val json = """
        {
          "data": [
            {
              "type": "topics",
              "id": 45029,
              "section": "Темы",
              "title": "Обновился на 14",
              "url": "https://visavi.net/topics/45029?pid=717175",
              "breadcrumbs": [
                { "title": "Форум", "url": "https://visavi.net/forums" },
                { "title": "RotorCMS", "url": "https://visavi.net/forums/26" }
              ],
              "text": "<p>Тест поста</p>",
              "rating": 0,
              "vote": { "type": "posts", "id": 717175, "value": null, "own": false },
              "comments_count": 11,
              "user": { "login": "Godzilla", "name": "GodZiLLa", "level": "user" }
            },
            {
              "type": "photos",
              "id": 3305,
              "section": "Галерея",
              "title": "Frontend и backend",
              "url": "https://visavi.net/photos/3305",
              "comments_count": 0,
              "user": { "login": "Vantuz", "name": "Вантуз-мен", "level": "boss" },
              "media": [
                {
                  "id": 8648,
                  "name": "video.mp4",
                  "path": "https://visavi.net/uploads/photos/6a9aa787.mp4",
                  "is_image": false,
                  "is_video": true
                }
              ]
            },
            {
              "type": "comments",
              "id": 31899,
              "title": "Вышел Rotor 14.4.0",
              "url": "https://visavi.net/news/331#comment_31899",
              "text": "<p>Комментарий к новости</p>",
              "user": { "login": "ramzes", "name": "ramzes" },
              "relate": {
                "type": "news",
                "id": 331,
                "title": "Вышел Rotor 14.4.0"
              }
            },
            {
              "type": "comments",
              "id": 31885,
              "title": "Кнопки поделиться",
              "url": "https://visavi.net/offers/209#comment_31885",
              "text": "<p>Ага, норм выглядит.</p>",
              "user": { "login": "XaOS" },
              "relate": {
                "type": "offers",
                "id": 209,
                "title": "Кнопки поделиться"
              }
            },
            {
              "type": "offers",
              "id": 100,
              "title": "Неподдерживаемый модуль",
              "user": { "login": "test" }
            }
          ]
        }
        """.trimIndent()

        val response = gson.fromJson(json, com.ramzes.visavinet.network.FeedResponse::class.java)
        assertNotNull(response)
        assertEquals(5, response.data.size)

        // 1. Проверяем topic
        val topicItem = response.data[0]
        assertTrue(topicItem.isSupported)
        assertEquals("topics", topicItem.type)
        assertEquals(45029L, topicItem.id)
        assertEquals(717175, topicItem.postId)
        assertEquals(2, topicItem.breadcrumbs.size)

        // 2. Проверяем photos
        val photoItem = response.data[1]
        assertTrue(photoItem.isSupported)
        assertEquals("photos", photoItem.type)
        assertEquals(1, photoItem.media.size)
        assertTrue(photoItem.media[0].isVideo)

        // 3. Проверяем comment к news (поддерживается)
        val newsCommentItem = response.data[2]
        assertTrue(newsCommentItem.isSupported)
        assertEquals("news", newsCommentItem.relate?.type)

        // 4. Проверяем comment к offers (НЕ поддерживается)
        val offersCommentItem = response.data[3]
        assertFalse(offersCommentItem.isSupported)

        // 5. Проверяем корневой offers (НЕ поддерживается)
        val offersRootItem = response.data[4]
        assertFalse(offersRootItem.isSupported)

        // Фильтрация для отображения в UI
        val filtered = response.data.filter { it.isSupported }
        assertEquals(3, filtered.size)
    }

    @Test
    fun testFeedPreviewTextTruncation() {
        // Короткий HTML
        val shortHtml = "<p>Привет <b>мир</b> &amp; друзья!</p>"
        assertEquals("Привет мир & друзья!", formatFeedPreviewText(shortHtml, 300))

        // Длинный текст > 300 символов
        val longContent = "А".repeat(350)
        val longHtml = "<div>$longContent</div>"
        val formatted = formatFeedPreviewText(longHtml, 300)
        assertEquals(301, formatted.length) // 300 + '…'
        assertTrue(formatted.endsWith("…"))
        assertEquals("А".repeat(300) + "…", formatted)

        // Удаление тегов скриптов и стилей
        val scriptHtml = "<p>Текст<script>alert(1)</script><style>body{color:red;}</style> продолжение</p>"
        assertEquals("Текст продолжение", formatFeedPreviewText(scriptHtml, 300))
    }
}

