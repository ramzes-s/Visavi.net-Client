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
}
