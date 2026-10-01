package com.ramzes.visavinet

import androidx.compose.ui.graphics.Color
import com.ramzes.visavinet.network.parseIsoDateTime
import com.ramzes.visavinet.util.formatFileSize
import com.ramzes.visavinet.util.parseColorString
import org.junit.Assert.*
import org.junit.Test

class FormatAndUtilityTest {

    @Test
    fun testParseIsoDateTimeValidFormats() {
        val isoUtc = "2026-08-16T12:30:00Z"
        val timestampUtc = parseIsoDateTime(isoUtc)
        assertNotNull(timestampUtc)
        assertTrue((timestampUtc ?: 0L) > 0L)

        val isoOffset = "2026-08-16T15:30:00+03:00"
        val timestampOffset = parseIsoDateTime(isoOffset)
        assertNotNull(timestampOffset)
        assertEquals(timestampUtc, timestampOffset)

        val isoMillis = "2026-08-16T12:30:00.000Z"
        val timestampMillis = parseIsoDateTime(isoMillis)
        assertNotNull(timestampMillis)
        assertEquals(timestampUtc, timestampMillis)
    }

    @Test
    fun testParseIsoDateTimeInvalidOrCorrupted() {
        assertNull(parseIsoDateTime(null))
        assertNull(parseIsoDateTime(""))
        assertNull(parseIsoDateTime("   "))
        assertNull(parseIsoDateTime("invalid-date-string"))
        assertNull(parseIsoDateTime("2026-99-99T99:99:99Z"))
    }

    @Test
    fun testFormatFileSizeBoundaries() {
        assertEquals("0 Б", formatFileSize(0))
        assertEquals("0 Б", formatFileSize(-100))
        assertEquals("500 Б", formatFileSize(500))
        assertEquals("1023 Б", formatFileSize(1023))
        assertEquals("1 КБ", formatFileSize(1024))
        assertEquals("1.5 КБ", formatFileSize(1536))
        assertEquals("1 МБ", formatFileSize(1024 * 1024))
        assertEquals("2.5 МБ", formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1 ГБ", formatFileSize(1024L * 1024L * 1024L))
    }

    @Test
    fun testParseColorStringValidHex() {
        val white = parseColorString("#FFFFFF")
        assertNotNull(white)
        assertEquals(Color(0xFFFFFFFF), white)

        val black = parseColorString("#000000")
        assertNotNull(black)
        assertEquals(Color(0xFF000000), black)

        val withAlpha = parseColorString("#80FF0000")
        assertNotNull(withAlpha)
        assertEquals(Color(0x80FF0000), withAlpha)

        val shortHex = parseColorString("#FFF")
        assertNotNull(shortHex)
    }

    @Test
    fun testParseColorStringInvalidOrFallback() {
        assertNull(parseColorString(null))
        assertNull(parseColorString(""))
        assertNull(parseColorString("not-a-color"))
        assertNull(parseColorString("#ZZZZZZ"))
    }

    @Test
    fun testParseVisaviUrlLinks() {
        val downUrl = "https://visavi.net/downs/1976"
        val downTarget = com.ramzes.visavinet.util.parseVisaviUrl(downUrl)
        assertTrue(downTarget is com.ramzes.visavinet.util.VisaviUrlTarget.Down)
        assertEquals(1976, (downTarget as com.ramzes.visavinet.util.VisaviUrlTarget.Down).downId)

        val newsUrl = "https://visavi.net/news/329"
        val newsTarget = com.ramzes.visavinet.util.parseVisaviUrl(newsUrl)
        assertTrue(newsTarget is com.ramzes.visavinet.util.VisaviUrlTarget.News)
        assertEquals(329, (newsTarget as com.ramzes.visavinet.util.VisaviUrlTarget.News).newsId)

        val photoUrl = "https://visavi.net/photos/3303"
        val photoTarget = com.ramzes.visavinet.util.parseVisaviUrl(photoUrl)
        assertTrue(photoTarget is com.ramzes.visavinet.util.VisaviUrlTarget.Photo)
        assertEquals(3303, (photoTarget as com.ramzes.visavinet.util.VisaviUrlTarget.Photo).photoId)

        val userUrl = "/users/ramzes"
        val userTarget = com.ramzes.visavinet.util.parseVisaviUrl(userUrl)
        assertTrue(userTarget is com.ramzes.visavinet.util.VisaviUrlTarget.User)
        assertEquals("ramzes", (userTarget as com.ramzes.visavinet.util.VisaviUrlTarget.User).login)

        val topicUrl = "https://visavi.net/topics/44999?page=2#post_717088"
        val topicTarget = com.ramzes.visavinet.util.parseVisaviUrl(topicUrl)
        assertTrue(topicTarget is com.ramzes.visavinet.util.VisaviUrlTarget.Topic)
        val topic = topicTarget as com.ramzes.visavinet.util.VisaviUrlTarget.Topic
        assertEquals(44999, topic.topicId)
        assertEquals(2, topic.page)
        assertEquals(717088, topic.postId)
    }

    @Test
    fun testVoteDataLogic() {
        val voteNull = com.ramzes.visavinet.network.VoteData(type = "news", id = 1, value = null, own = false)
        assertTrue(voteNull.canVote)
        assertFalse(voteNull.hasVoted)
        assertFalse(voteNull.isVotedUp)

        val voteUp = com.ramzes.visavinet.network.VoteData(type = "news", id = 1, value = "+", own = false)
        assertFalse(voteUp.canVote)
        assertTrue(voteUp.hasVoted)
        assertTrue(voteUp.isVotedUp)
        assertFalse(voteUp.isVotedDown)

        val voteDown = com.ramzes.visavinet.network.VoteData(type = "news", id = 1, value = "-", own = false)
        assertFalse(voteDown.canVote)
        assertTrue(voteDown.hasVoted)
        assertFalse(voteDown.isVotedUp)
        assertTrue(voteDown.isVotedDown)

        val voteOwn = com.ramzes.visavinet.network.VoteData(type = "news", id = 1, value = null, own = true)
        assertFalse(voteOwn.canVote)
        assertFalse(voteOwn.hasVoted)
    }

    @Test
    fun testAvailableAccentColorsContainsGrayShades() {
        assertEquals(16, com.ramzes.visavinet.ui.theme.AvailableAccentColors.size)
        val ids = com.ramzes.visavinet.ui.theme.AvailableAccentColors.map { it.id }
        assertEquals((0..15).toList(), ids)

        val lightGray = com.ramzes.visavinet.ui.theme.AvailableAccentColors.find { it.id == 14 }
        assertNotNull(lightGray)
        assertEquals("Светло-серый", lightGray?.name)
        assertEquals(com.ramzes.visavinet.ui.theme.LightGrayAccent, lightGray?.color)

        val darkGray = com.ramzes.visavinet.ui.theme.AvailableAccentColors.find { it.id == 15 }
        assertNotNull(darkGray)
        assertEquals("Темно-серый", darkGray?.name)
        assertEquals(com.ramzes.visavinet.ui.theme.DarkGrayAccent, darkGray?.color)
    }

    @Test
    fun testStripMarkdownRemovesFormatting() {
        val input = """
            ## 🚀 Релиз 1.2.0
            ### Список изменений:
            - **Новая функция**: добавлены крутые фичи
              - Подробность в коде `version_code` и `versionName`
            * *Курсивный пункт* с [ссылкой](https://github.com)
            + Обычный пункт

            > Важное замечание
            ---
            **Full Changelog**: https://github.com/ramzes-s/Visavi.net-Client/compare/v1.0...v1.2
        """.trimIndent()

        val cleaned = com.ramzes.visavinet.ui.dialogs.stripMarkdown(input)

        assertFalse("Не должно быть заголовков ##", cleaned.contains("##"))
        assertFalse("Не должно быть заголовков ###", cleaned.contains("###"))
        assertFalse("Не должно быть двойных звездочек", cleaned.contains("**"))
        assertFalse("Не должно быть одинарных звездочек", cleaned.contains("*"))
        assertFalse("Не должно быть обратных кавычек", cleaned.contains("`"))
        assertFalse("Не должно быть markdown ссылок [..](..)", cleaned.contains("[ссылкой]"))
        assertFalse("Не должно быть цитаты >", cleaned.startsWith(">") || cleaned.contains("\n>"))
        assertFalse("Не должно быть разделителя ---", cleaned.contains("---"))

        assertTrue("Должен остаться заголовок с эмодзи", cleaned.contains("🚀 Релиз 1.2.0"))
        assertTrue("Должен быть маркер списка •", cleaned.contains("• Новая функция: добавлены крутые фичи"))
        assertTrue("Должен быть вложенный маркер списка", cleaned.contains("  • Подробность в коде version_code и versionName"))
        assertTrue("Должен остаться текст ссылки", cleaned.contains("ссылкой"))
        assertTrue("Должна остаться ссылка на changelog", cleaned.contains("Full Changelog: https://github.com/ramzes-s/Visavi.net-Client/compare/v1.0...v1.2"))
    }

    @Test
    fun testStripMarkdownEmptyAndNull() {
        assertEquals("", com.ramzes.visavinet.ui.dialogs.stripMarkdown(null))
        assertEquals("", com.ramzes.visavinet.ui.dialogs.stripMarkdown(""))
        assertEquals("", com.ramzes.visavinet.ui.dialogs.stripMarkdown("    \n\n  "))
    }
}

