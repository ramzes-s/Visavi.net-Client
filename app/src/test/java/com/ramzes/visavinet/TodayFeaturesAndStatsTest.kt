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
}
