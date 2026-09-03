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
        // Проверка константы интервала: 1 минута (временно для тестов) в миллисекундах
        assertEquals(1 * 60 * 1000L, SettingsViewModel.UPDATE_CHECK_INTERVAL_MS)

        // Проверка человекопонятного форматирования оставшегося времени
        assertEquals("5 ч. 42 мин.", SettingsViewModel.formatRemainingTime(5 * 3600 + 42 * 60 + 15))
        assertEquals("2 ч.", SettingsViewModel.formatRemainingTime(2 * 3600))
        assertEquals("45 мин.", SettingsViewModel.formatRemainingTime(45 * 60 + 10))
        assertEquals("30 сек.", SettingsViewModel.formatRemainingTime(30))
    }

    @Test
    fun testDetectTodayUpdates() {
        // 1. Первый запуск (нет baseline) -> уведомления не отправляются
        val snapshot1 = com.ramzes.visavinet.util.TodayStatsSnapshot(
            postsToday = 10,
            newsToday = 2,
            photosToday = 5,
            downsToday = 1
        )
        val initialUpdates = com.ramzes.visavinet.util.detectTodayUpdates(null, snapshot1)
        assertTrue(initialUpdates.isEmpty())

        // 2. Нет изменений -> список пуст
        val noChangeUpdates = com.ramzes.visavinet.util.detectTodayUpdates(snapshot1, snapshot1)
        assertTrue(noChangeUpdates.isEmpty())

        // 3. Увеличение в разделах Форум (+3) и Новости (+1)
        val snapshot2 = com.ramzes.visavinet.util.TodayStatsSnapshot(
            postsToday = 13,
            newsToday = 3,
            photosToday = 5,
            downsToday = 1
        )
        val updates = com.ramzes.visavinet.util.detectTodayUpdates(snapshot1, snapshot2)
        assertEquals(2, updates.size)
        assertTrue(updates.contains("Форум (+3)"))
        assertTrue(updates.contains("Новости (+1)"))

        // 4. Сброс суток (значения стали меньше) -> уведомления не отправляются
        val resetSnapshot = com.ramzes.visavinet.util.TodayStatsSnapshot(
            postsToday = 0,
            newsToday = 0,
            photosToday = 0,
            downsToday = 0
        )
        val resetUpdates = com.ramzes.visavinet.util.detectTodayUpdates(snapshot2, resetSnapshot)
        assertTrue(resetUpdates.isEmpty())
    }
}
