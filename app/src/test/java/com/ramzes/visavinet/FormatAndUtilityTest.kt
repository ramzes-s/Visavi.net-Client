package com.ramzes.visavinet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ramzes.visavinet.network.parseIsoDateTime
import com.ramzes.visavinet.ui.components.applyTagToTextFieldValue
import com.ramzes.visavinet.ui.components.findEnclosingCodeBlock
import com.ramzes.visavinet.util.formatFileSize
import com.ramzes.visavinet.util.parseColorString
import androidx.compose.ui.text.font.FontFamily
import com.ramzes.visavinet.util.HtmlVisualTransformation
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

    @Test
    fun testFormatStatsElapsedTimeAndSubtitle() {
        // Тест прошедшего времени
        assertEquals("только что", formatStatsElapsedTime(-100L))
        assertEquals("только что", formatStatsElapsedTime(0L))
        assertEquals("только что", formatStatsElapsedTime(30_000L))
        assertEquals("1 минуту назад", formatStatsElapsedTime(60_000L))
        assertEquals("2 минуты назад", formatStatsElapsedTime(2 * 60_000L))
        assertEquals("3 минуты назад", formatStatsElapsedTime(3 * 60_000L))
        assertEquals("4 минуты назад", formatStatsElapsedTime(4 * 60_000L))
        assertEquals("5 минут назад", formatStatsElapsedTime(5 * 60_000L))
        assertEquals("11 минут назад", formatStatsElapsedTime(11 * 60_000L))
        assertEquals("21 минуту назад", formatStatsElapsedTime(21 * 60_000L))
        assertEquals("22 минуты назад", formatStatsElapsedTime(22 * 60_000L))
        assertEquals("1 час назад", formatStatsElapsedTime(60 * 60_000L))
        assertEquals("2 часа назад", formatStatsElapsedTime(2 * 60 * 60_000L))
        assertEquals("5 часов назад", formatStatsElapsedTime(5 * 60 * 60_000L))
        assertEquals("1 день назад", formatStatsElapsedTime(24 * 60 * 60_000L))
        assertEquals("2 дня назад", formatStatsElapsedTime(48 * 60 * 60_000L))
        assertEquals("5 дней назад", formatStatsElapsedTime(5 * 24 * 60 * 60_000L))

        // Тест формирования строки описания
        val now = 1_000_000_000L
        assertEquals(
            "Последнее обновление: ещё не выполнялось",
            formatStatsSubtitle(lastTime = 0L, currentTime = now)
        )
        assertEquals(
            "Последнее обновление: только что",
            formatStatsSubtitle(lastTime = now - 15_000L, currentTime = now)
        )
        assertEquals(
            "Последнее обновление: 3 минуты назад",
            formatStatsSubtitle(lastTime = now - 3 * 60_000L, currentTime = now)
        )
        assertEquals(
            "Последнее обновление: 5 минут назад",
            formatStatsSubtitle(lastTime = now - 5 * 60_000L, currentTime = now)
        )
        assertEquals(
            "Последнее обновление: 10 минут назад",
            formatStatsSubtitle(lastTime = now - 10 * 60_000L, currentTime = now)
        )

        // Тест формирования строки статуса ответа сервера
        assertEquals(
            "Ответ сервера: ещё не получен",
            formatStatsServerResponse(lastTime = 0L, code = 200)
        )
        assertEquals(
            "Ответ сервера: 200 (OK)",
            formatStatsServerResponse(lastTime = now, code = 200)
        )
        assertEquals(
            "Ответ сервера: ошибка подключения",
            formatStatsServerResponse(lastTime = now, code = 0)
        )
        assertEquals(
            "Ответ сервера: 403 (Forbidden)",
            formatStatsServerResponse(lastTime = now, code = 403)
        )
        assertEquals(
            "Ответ сервера: 500 (Internal Server Error)",
            formatStatsServerResponse(lastTime = now, code = 500)
        )
    }

    @Test
    fun testIsTabletConfiguration() {
        // Смартфон: sw = 360-411dp, NORMAL layout
        assertFalse(
            com.ramzes.visavinet.util.DeviceUtils.isTabletConfiguration(
                smallestScreenWidthDp = 392,
                screenLayout = android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL
            )
        )
        assertFalse(
            com.ramzes.visavinet.util.DeviceUtils.isTabletConfiguration(
                smallestScreenWidthDp = 411,
                screenLayout = android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL
            )
        )

        // 7-дюймовый планшет: sw = 600dp
        assertTrue(
            com.ramzes.visavinet.util.DeviceUtils.isTabletConfiguration(
                smallestScreenWidthDp = 600,
                screenLayout = android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
            )
        )

        // 10-дюймовый планшет: sw = 720-800dp, XLARGE layout
        assertTrue(
            com.ramzes.visavinet.util.DeviceUtils.isTabletConfiguration(
                smallestScreenWidthDp = 800,
                screenLayout = android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE
            )
        )

        // Устройство с флагом LARGE даже при пограничной ширине
        assertTrue(
            com.ramzes.visavinet.util.DeviceUtils.isTabletConfiguration(
                smallestScreenWidthDp = 590,
                screenLayout = android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
            )
        )
    }

    @Test
    fun testTabletDialogMaxWidthConstants() {
        assertEquals(630.dp, com.ramzes.visavinet.util.DeviceUtils.TABLET_DIALOG_MAX_WIDTH)
        assertEquals(420.dp, com.ramzes.visavinet.util.DeviceUtils.DEFAULT_DIALOG_MAX_WIDTH)
    }

    @Test
    fun testPingStateModels() {
        val idle: PingState = PingState.Idle
        assertSame(PingState.Idle, idle)

        val pinging: PingState = PingState.Pinging
        assertSame(PingState.Pinging, pinging)

        val success = PingState.Success(latencyMs = 85L, statusCode = 200)
        assertEquals(85L, success.latencyMs)
        assertEquals(200, success.statusCode)

        val error = PingState.Error(message = "Network error", statusCode = 0)
        assertEquals("Network error", error.message)
        assertEquals(0, error.statusCode)
    }

    @Test
    fun testPingCooldownConstant() {
        assertEquals(2L * 60 * 1000L, SettingsViewModel.PING_COOLDOWN_MS)
    }

    @Test
    fun testUserSortOptions() {
        assertEquals("point", UserSort.POINT.key)
        assertEquals("rating", UserSort.RATING.key)
        assertEquals("created", UserSort.CREATED.key)
        assertEquals("updated", UserSort.UPDATED.key)
        assertEquals("Актив", UserSort.POINT.title)
        assertEquals("Авторитет", UserSort.RATING.title)
        assertEquals("Новые", UserSort.CREATED.title)
        assertEquals("Активность", UserSort.UPDATED.title)
    }

    @Test
    fun testUsersListResponseDeserialization() {
        val json = """
            {
                "data": [
                    {
                        "login": "TestUser",
                        "name": "Тестовый",
                        "level": "admin",
                        "point": 1500,
                        "rating": 45,
                        "money": 75000,
                        "color": "#00FF00"
                    }
                ],
                "meta": {
                    "current_page": 1,
                    "last_page": 50,
                    "total": 500,
                    "per_page": 10
                }
            }
        """.trimIndent()
        val gson = com.google.gson.Gson()
        val response = gson.fromJson(json, com.ramzes.visavinet.network.UsersListResponse::class.java)

        assertNotNull(response)
        assertNotNull(response.data)
        assertEquals(1, response.data?.size)
        val user = response.data?.first()
        assertEquals("TestUser", user?.login)
        assertEquals("Тестовый", user?.name)
        assertEquals("admin", user?.level)
        assertEquals(1500, user?.point)
        assertEquals(45, user?.rating)
        assertEquals(75000L, user?.money)
        assertEquals("#00FF00", user?.color)

        assertNotNull(response.meta)
        assertEquals(1, response.meta?.currentPage)
        assertEquals(50, response.meta?.lastPage)
        assertEquals(500, response.meta?.total)
        assertEquals(10, response.meta?.perPage)
    }

    @Test
    fun testUsersSearchResponseDeserialization() {
        val json = """
            {
                "data": [
                    {
                        "login": "findme",
                        "name": "Найдёныш",
                        "level": "moder",
                        "color": "#FF0000",
                        "avatar": "/uploads/avatars/1.png",
                        "status": "Online status"
                    }
                ]
            }
        """.trimIndent()
        val gson = com.google.gson.Gson()
        val response = gson.fromJson(json, com.ramzes.visavinet.network.UsersSearchResponse::class.java)

        assertNotNull(response)
        assertNotNull(response.data)
        assertEquals(1, response.data?.size)
        val searchUser = response.data?.first()
        assertEquals("findme", searchUser?.login)
        assertEquals("Найдёныш", searchUser?.name)
        assertEquals("Найдёныш", searchUser?.displayName)
        assertEquals("moder", searchUser?.level)
        assertEquals("#FF0000", searchUser?.color)
        assertEquals("https://visavi.net/uploads/avatars/1.png", searchUser?.avatarUrl)
        assertEquals("Online status", searchUser?.status)
    }

    @Test
    fun testCodeTagRemovalWhenCursorInsideCodeBlock() {
        val tagStart = "<pre class=\"code\"><code>"
        val tagEnd = "</code></pre>"
        val originalText = "Prefix <pre class=\"code\"><code>val x = 10</code></pre> Suffix"
        // Курсор находится внутри кода (на символах '10')
        val cursor = originalText.indexOf("10")
        assertTrue(cursor > 0)

        val initialValue = TextFieldValue(text = originalText, selection = TextRange(cursor))
        val result = applyTagToTextFieldValue(initialValue, tagStart, tagEnd)

        // Тег кода должен исчезнуть, оставив содержимое нетронутым
        assertEquals("Prefix val x = 10 Suffix", result.text)
        // Курсор должен остаться на том же месте относительно текста ("10")
        assertEquals(result.text.indexOf("10"), result.selection.start)
    }

    @Test
    fun testCodeTagRemovalWhenCursorInsideEmptyCodeBlock() {
        val tagStart = "<pre class=\"code\"><code>"
        val tagEnd = "</code></pre>"
        val originalText = "<pre class=\"code\"><code></code></pre>"
        val cursor = tagStart.length // курсор прямо между тегами
        val initialValue = TextFieldValue(text = originalText, selection = TextRange(cursor))
        val result = applyTagToTextFieldValue(initialValue, tagStart, tagEnd)

        assertEquals("", result.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun testMultipleCodeBlocksOnlyRemovesEnclosingOne() {
        val tagStart = "<pre class=\"code\"><code>"
        val tagEnd = "</code></pre>"
        val block1 = "<pre class=\"code\"><code>block 1</code></pre>"
        val block2 = "<pre class=\"code\"><code>block 2</code></pre>"
        val fullText = "$block1 and $block2"

        // Курсор внутри block 2
        val cursorInBlock2 = fullText.indexOf("block 2")
        val initialValue = TextFieldValue(text = fullText, selection = TextRange(cursorInBlock2))
        val result = applyTagToTextFieldValue(initialValue, tagStart, tagEnd)

        // block 1 остался неизменным, а block 2 лишился тега
        assertEquals("$block1 and block 2", result.text)
    }

    @Test
    fun testCodeTagInsertedWhenCursorOutsideCodeBlock() {
        val tagStart = "<pre class=\"code\"><code>"
        val tagEnd = "</code></pre>"
        val text = "some normal text"
        val initialValue = TextFieldValue(text = text, selection = TextRange(4))
        val result = applyTagToTextFieldValue(initialValue, tagStart, tagEnd)

        assertEquals("some<pre class=\"code\"><code></code></pre> normal text", result.text)
        assertEquals(4 + tagStart.length, result.selection.start)
    }

    @Test
    fun testStandaloneCodeTagRemovedWhenCursorInside() {
        val tagStart = "<pre class=\"code\"><code>"
        val tagEnd = "</code></pre>"
        val text = "text with <code>inline code</code> test"
        val cursor = text.indexOf("inline")
        val initialValue = TextFieldValue(text = text, selection = TextRange(cursor))
        val result = applyTagToTextFieldValue(initialValue, tagStart, tagEnd)

        assertEquals("text with inline code test", result.text)
        assertEquals(result.text.indexOf("inline"), result.selection.start)
    }

    @Test
    fun testHtmlVisualTransformationCodeStylePreservesTextColorAndSetsDarkBackground() {
        val testBgColor = Color(0x55000000)
        val transformation = HtmlVisualTransformation(codeBgColor = testBgColor)
        val raw = "Hello <pre class=\"code\"><code>val x = 1</code></pre> World"
        val transformed = transformation.filter(androidx.compose.ui.text.AnnotatedString(raw))

        // Теги должны быть вырезаны из видимого текста
        assertEquals("Hello val x = 1 World", transformed.text.text)

        // Проверяем стиль для фрагмента "val x = 1"
        val codeStart = transformed.text.text.indexOf("val x = 1")
        val codeEnd = codeStart + "val x = 1".length
        val spanStyles = transformed.text.spanStyles.filter { it.start >= codeStart && it.end <= codeEnd }

        assertTrue(spanStyles.isNotEmpty())
        for (range in spanStyles) {
            val codeStyle = range.item
            // Шрифт должен быть моноширинным
            assertEquals(FontFamily.Monospace, codeStyle.fontFamily)
            // Фон должен быть заданным полупрозрачным
            assertEquals(testBgColor, codeStyle.background)
            // Цвет текста НЕ должен быть переопределен (Unspecified), сохраняя цвет редактора
            assertEquals(Color.Unspecified, codeStyle.color)
        }
    }
}



