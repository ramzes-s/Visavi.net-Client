package com.ramzes.visavinet

import com.ramzes.visavinet.util.DownloaderHelper
import com.ramzes.visavinet.util.parseHtmlToBlocks
import com.ramzes.visavinet.util.sanitizeHtml
import org.junit.Assert.*
import org.junit.Test

class SecurityAndSanitizationTest {

    @Test
    fun testFileNameSanitizationAgainstPathTraversal() {
        val malicious1 = "../../evil.apk"
        val clean1 = DownloaderHelper.sanitizeFileName(malicious1)
        assertEquals("evil.apk", clean1)

        val malicious2 = "..\\..\\windows\\system32\\evil.exe"
        val clean2 = DownloaderHelper.sanitizeFileName(malicious2)
        assertEquals("evil.exe", clean2)

        val malicious3 = "/root/secret/../../data.txt"
        val clean3 = DownloaderHelper.sanitizeFileName(malicious3)
        assertEquals("data.txt", clean3)
    }

    @Test
    fun testFileNameSanitizationSpecialCharacters() {
        val dangerous = "file:name*with?illegal<chars>|end.zip"
        val clean = DownloaderHelper.sanitizeFileName(dangerous)
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("|"))
        assertTrue(clean.endsWith(".zip"))
    }

    @Test
    fun testFileNameSanitizationEmptyOrNull() {
        val nullResult = DownloaderHelper.sanitizeFileName(null)
        assertTrue(nullResult.startsWith("download_"))

        val blankResult = DownloaderHelper.sanitizeFileName("   ")
        assertTrue(blankResult.startsWith("download_"))

        val dotsResult = DownloaderHelper.sanitizeFileName("....")
        assertTrue(dotsResult.startsWith("download_"))
    }

    @Test
    fun testSanitizeHtmlRemovesScripts() {
        val xss = "Hello <script>alert('xss')</script> world"
        val cleaned = sanitizeHtml(xss)
        assertFalse(cleaned.contains("<script>", ignoreCase = true))
        assertFalse(cleaned.contains("alert('xss')", ignoreCase = true))
        assertTrue(cleaned.contains("Hello"))
        assertTrue(cleaned.contains("world"))
    }

    @Test
    fun testSanitizeHtmlRemovesEventHandlers() {
        val dangerous = "<img src='valid.jpg' onerror='alert(1)' onload='alert(2)' />"
        val cleaned = sanitizeHtml(dangerous)
        assertFalse(cleaned.contains("onerror", ignoreCase = true))
        assertFalse(cleaned.contains("onload", ignoreCase = true))
    }

    @Test
    fun testParseHtmlToBlocksWithMaliciousAndMalformedInput() {
        val malformed = "<div><b>Unclosed bold <i>italic <script>bad()</script> <a href='javascript:alert(1)'>link</a>"
        val blocks = parseHtmlToBlocks(malformed)
        assertNotNull(blocks)
        assertTrue(blocks.isNotEmpty())

        // Проверяем, что парсер не упал на незакрытых тегах и битом HTML
        val combinedText = blocks.joinToString { it.toString() }
        assertFalse(combinedText.contains("<script>", ignoreCase = true))
    }
}
