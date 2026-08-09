package com.ramzes.visavinet.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Блок контента — результат парсинга HTML
 */
sealed class ContentBlock {
    data class TextBlock(val text: String, val html: String? = null) : ContentBlock()
    data class CodeBlock(val code: String) : ContentBlock()
    data class QuoteBlock(val quoteText: String, val footerText: String, val quoteHtml: String? = null, val footerHtml: String? = null) : ContentBlock()
}

/**
 * Парсинг HTML в список блоков контента
 */
fun parseHtmlToBlocks(html: String?): List<ContentBlock> {
    if (html == null) return emptyList()

    // Декодируем HTML entities
    val decoded = decodeHtmlEntities(html)

    // Заменяем </p> и <br> на переносы строк
    var processed = decoded
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

    // Находим все блоки и сортируем по позиции
    val allMatches = mutableListOf<MatchResult>()

    // Находим все <pre> блоки
    val prePattern = Regex("<pre[^>]*>(.*?)</pre>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    allMatches.addAll(prePattern.findAll(processed))

    // Находим все <blockquote> блоки
    val quotePattern = Regex("<blockquote[^>]*>(.*?)</blockquote>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    allMatches.addAll(quotePattern.findAll(processed))

    // Сортируем по позиции начала
    allMatches.sortBy { it.range.first }

    val blocks = mutableListOf<ContentBlock>()
    var currentPosition = 0

    for (match in allMatches) {
        // Защита от некорректных позиций
        val startPos = maxOf(0, match.range.first)
        if (startPos < currentPosition) continue

        // Добавляем текст до текущего блока
        val textBefore = processed.substring(currentPosition, startPos)
        if (textBefore.isNotBlank()) {
            // Сохраняем HTML для inline-обработки (уже декодированный)
            blocks.add(ContentBlock.TextBlock(text = textBefore, html = textBefore))
        }

        val tag = match.value.lowercase()

        // Обрабатываем <pre>
        if (tag.startsWith("<pre")) {
            val code = match.groupValues[1].trim()
            blocks.add(ContentBlock.CodeBlock(code))
        }
        // Обрабатываем <blockquote>
        else if (tag.startsWith("<blockquote")) {
            val innerHtml = match.groupValues[1]
            val footerPattern = Regex("<footer[^>]*>(.*?)</footer>", setOf(RegexOption.IGNORE_CASE))
            val footerMatch = footerPattern.find(innerHtml)

            val quoteHtml = if (footerMatch != null) {
                innerHtml.substring(0, footerMatch.range.first).trim()
            } else {
                innerHtml.trim()
            }

            val footerHtml = footerMatch?.groupValues?.get(1)?.trim() ?: ""

            blocks.add(ContentBlock.QuoteBlock(
                quoteText = quoteHtml,
                footerText = footerHtml,
                quoteHtml = quoteHtml,
                footerHtml = footerHtml
            ))
        }

        currentPosition = maxOf(currentPosition, match.range.last + 1)
    }

    // Добавляем оставшийся текст
    if (currentPosition < processed.length) {
        val remainingText = processed.substring(currentPosition, processed.length)
        if (remainingText.isNotBlank()) {
            blocks.add(ContentBlock.TextBlock(text = remainingText, html = remainingText))
        }
    }

    // Если не было найдено блоков, добавляем весь текст как один блок
    if (blocks.isEmpty()) {
        val cleanText = processed
        if (cleanText.isNotBlank()) {
            blocks.add(ContentBlock.TextBlock(text = cleanText, html = cleanText))
        }
    }

    return blocks
}

/**
 * Декодирование HTML entities
 */
private fun decodeHtmlEntities(html: String): String {
    return html
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&#60;", "<")
        .replace("&#62;", ">")
        .replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else match.value
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else match.value
        }
}

/**
 * Рендеринг списка блоков контента
 */
@Composable
fun RenderContentBlocks(
    blocks: List<ContentBlock>,
    isDark: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.TextBlock -> {
                    TextBlock(block.text, isDark, block.html)
                }
                is ContentBlock.CodeBlock -> {
                    CodeBlock(block.code, isDark)
                }
                is ContentBlock.QuoteBlock -> {
                    QuoteBlock(block.quoteText, block.footerText, isDark, block.quoteHtml, block.footerHtml)
                }
            }
        }
    }
}

/**
 * Рендеринг текстового блока с обработкой inline-тегов
 */
@Composable
private fun TextBlock(text: String, isDark: Boolean, html: String? = null) {
    // Используем HTML если есть, иначе простой текст
    val sourceText = html ?: text
    val annotatedText = parseInlineHtmlTags(sourceText, isDark)

    Text(
        text = annotatedText,
        color = if (isDark) Color.White else Color.Black,
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * Парсинг inline HTML тегов (strong, b, i, u, s, code, a) в AnnotatedString
 * text уже декодирован (HTML entities обработаны)
 * Поддерживает вложенные теги
 */
fun parseInlineHtmlTags(text: String, isDark: Boolean): AnnotatedString {
    return buildAnnotatedString {
        parseNestedTags(this, text, isDark, emptyList())
    }
}

/**
 * Рекурсивная обработка вложенных тегов
 */
/**
 * Парсит строку цвета (#hex, rgb, название) в Compose Color
 */
fun parseColorString(colorStr: String?): Color? {
    if (colorStr.isNullOrBlank()) return null
    val clean = colorStr.trim()
    return try {
        if (clean.startsWith("#")) {
            val hex = clean.removePrefix("#")
            when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2)
                    val g = hex[1].toString().repeat(2)
                    val b = hex[2].toString().repeat(2)
                    Color(android.graphics.Color.parseColor("#FF$r$g$b"))
                }
                6 -> Color(android.graphics.Color.parseColor("#FF$hex"))
                8 -> Color(android.graphics.Color.parseColor("#$hex"))
                else -> null
            }
        } else if (clean.startsWith("rgb", ignoreCase = true)) {
            val digits = Regex("\\d+").findAll(clean).map { it.value.toInt() }.toList()
            if (digits.size >= 3) {
                Color(digits[0].coerceIn(0, 255), digits[1].coerceIn(0, 255), digits[2].coerceIn(0, 255))
            } else null
        } else {
            Color(android.graphics.Color.parseColor(clean))
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Извлекает цвет из тега <span style="color: ...">
 */
fun parseColorFromSpanTag(fullTag: String): Color? {
    val styleRegex = Regex("style\\s*=\\s*\"[^\"]*color\\s*:\\s*([^;\"]+)[;\"]?", RegexOption.IGNORE_CASE)
    val colorMatch = styleRegex.find(fullTag) ?: return null
    return parseColorString(colorMatch.groupValues[1])
}

private fun parseNestedTags(
    builder: AnnotatedString.Builder,
    text: String,
    isDark: Boolean,
    activeStyles: List<SpanStyle>,
    depth: Int = 0
) {
    if (depth >= 15 || text.isEmpty()) {
        builder.appendWithStyles(text, activeStyles)
        return
    }

    var currentPosition = 0
    
    // Ищем первый открывающий тег
    val openTagRegex = Regex("<(strong|b|i|u|s|code|span|a)(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE)
    val match = openTagRegex.find(text, currentPosition) ?: run {
        // Нет тегов - добавляем текст
        builder.appendWithStyles(text, activeStyles)
        return
    }
    
    // Добавляем текст до тега
    if (match.range.first > currentPosition) {
        val beforeText = text.substring(currentPosition, match.range.first)
        builder.appendWithStyles(beforeText, activeStyles)
    }
    
    val tagName = match.groupValues[1].lowercase()
    
    // Находим соответствующий закрывающий тег с учётом вложенности
    val closeTagRegex = Regex("</${tagName}>", RegexOption.IGNORE_CASE)
    var searchPos = match.range.last + 1
    var nestingLevel = 1
    var closeTagPos = -1
    
    while (nestingLevel > 0 && searchPos < text.length) {
        val remainingText = text.substring(searchPos)
        val nextOpen = openTagRegex.find(remainingText)
        val nextClose = closeTagRegex.find(remainingText)
        
        when {
            nextClose == null -> break
            nextOpen != null && nextOpen.range.first < nextClose.range.first && 
                nextOpen.groupValues[1].lowercase() == tagName -> {
                // Вложенный открывающий тег того же типа
                nestingLevel++
                searchPos += nextOpen.range.last + 1
            }
            else -> {
                // Закрывающий тег
                nestingLevel--
                if (nestingLevel == 0) {
                    closeTagPos = searchPos + nextClose.range.first
                }
                searchPos += nextClose.range.last + 1
            }
        }
    }
    
    if (closeTagPos > 0) {
        // Извлекаем содержимое между тегами
        val innerStart = match.range.last + 1
        val innerText = text.substring(innerStart, closeTagPos)
        
        // Добавляем стиль для текущего тега
        val newStyle = when (tagName) {
            "strong", "b" -> SpanStyle(fontWeight = FontWeight.Bold)
            "i" -> SpanStyle(fontStyle = FontStyle.Italic)
            "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
            "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            "code" -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = if (isDark) Color(0x401E3A8A) else Color(0x201E40AF),
                color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
            )
            "span" -> {
                val spanColor = parseColorFromSpanTag(match.value)
                if (spanColor != null) SpanStyle(color = spanColor) else null
            }
            "a" -> SpanStyle(
                color = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2),
                textDecoration = TextDecoration.Underline
            )
            else -> null
        }
        
        val newActiveStyles = if (newStyle != null) activeStyles + newStyle else activeStyles
        
        // Рекурсивно обрабатываем содержимое
        parseNestedTags(builder, innerText, isDark, newActiveStyles, depth + 1)
        
        // Пропускаем закрывающий тег
        val afterClosePos = closeTagPos + "</${tagName}>".length
        currentPosition = afterClosePos
        
        // Обрабатываем оставшийся текст
        if (currentPosition < text.length) {
            val remainingText = text.substring(currentPosition)
            parseNestedTags(builder, remainingText, isDark, activeStyles, depth + 1)
        }
    } else {
        // Нет закрывающего тега - пропускаем открывающий
        currentPosition = match.range.last + 1
        if (currentPosition < text.length) {
            val remainingText = text.substring(currentPosition)
            parseNestedTags(builder, remainingText, isDark, activeStyles, depth + 1)
        }
    }
}

/**
 * Добавление текста с применением всех активных стилей
 */
private fun AnnotatedString.Builder.appendWithStyles(text: String, styles: List<SpanStyle>) {
    if (text.isEmpty()) return

    val start = this.length
    append(text)
    val end = this.length

    styles.forEach { style ->
        addStyle(style, start, end)
    }
}

/**
 * Рендеринг блока кода с рамкой и зелёным фоном
 */
@Composable
private fun CodeBlock(code: String, isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = if (isDark) Color(0x401E3A8A) else Color(0x201E40AF),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (isDark) Color(0x803B82F6) else Color(0x401E40AF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

/**
 * Рендеринг блока цитаты с оранжевым фоном
 */
@Composable
private fun QuoteBlock(
    quoteText: String,
    footerText: String,
    isDark: Boolean,
    quoteHtml: String? = null,
    footerHtml: String? = null
) {
    // Полупрозрачный оранжевый фон #d67904
    val backgroundColor = Color(0x17D67904)
    val textColor = if (isDark) Color(0xFFF6DAC0) else Color(0x90A95E03)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0x50D67904),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // Текст цитаты с обработкой inline-тегов
        if (quoteText.isNotBlank()) {
            val quoteSourceText = quoteHtml ?: quoteText
            val annotatedQuote = parseInlineHtmlTags(quoteSourceText, isDark)
            Text(
                text = annotatedQuote,
                color = textColor,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Footer цитаты (жирный) с обработкой inline-тегов
        if (footerText.isNotBlank()) {
            val footerSourceText = footerHtml ?: footerText
            val annotatedFooter = parseInlineHtmlTags(footerSourceText, isDark)
            Text(
                text = annotatedFooter,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Парсинг HTML в AnnotatedString (старый метод для обратной совместимости)
 */
fun htmlToAnnotatedString(html: String?, isDark: Boolean = true): AnnotatedString {
    if (html == null) return AnnotatedString("")
    
    val blocks = parseHtmlToBlocks(html)
    
    return buildAnnotatedString {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ContentBlock.TextBlock -> {
                    append(block.text)
                    if (index < blocks.lastIndex) append("\n")
                }
                is ContentBlock.CodeBlock -> {
                    val start = this.length
                    append(block.code)
                    val end = this.length
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x404CAF50)
                        ),
                        start, end
                    )
                    if (index < blocks.lastIndex) append("\n")
                }
                is ContentBlock.QuoteBlock -> {
                    val quoteStart = this.length
                    append(block.quoteText)
                    val quoteEnd = this.length
                    // Оранжевый цвет #d67904
                    val quoteColor = if (isDark) Color(0x90FFD67904) else Color(0x90D67904)
                    addStyle(
                        SpanStyle(
                            color = quoteColor,
                            fontStyle = FontStyle.Italic
                        ),
                        quoteStart, quoteEnd
                    )
                    
                    if (block.footerText.isNotBlank()) {
                        append("\n")
                        val footerStart = this.length
                        append(block.footerText)
                        val footerEnd = this.length
                        addStyle(
                            SpanStyle(
                                color = quoteColor,
                                fontWeight = FontWeight.Bold
                            ),
                            footerStart, footerEnd
                        )
                    }
                    
                    append("\n\n")
                }
            }
        }
    }
}

/**
 * Очистка HTML от запрещённых тегов
 */
fun sanitizeHtml(html: String?): String {
    if (html == null) return ""
    return html.replace(Regex("<[^>]*>"), "")
}

/**
 * Форматирование времени в читаемый формат
 */
fun formatUnixTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Форматирование размера файла с точностью до десятых/сотых
 */
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 Б"
    val k = 1024.0
    return when {
        size < 1024 -> "$size Б"
        size < 1024 * 1024 -> {
            val kb = size / k
            val formatted = String.format(Locale.US, "%.1f", kb).trimEnd('0').trimEnd('.')
            "$formatted КБ"
        }
        size < 1024 * 1024 * 1024 -> {
            val mb = size / (k * k)
            val formatted = String.format(Locale.US, "%.2f", mb).trimEnd('0').trimEnd('.')
            "$formatted МБ"
        }
        else -> {
            val gb = size / (k * k * k)
            val formatted = String.format(Locale.US, "%.2f", gb).trimEnd('0').trimEnd('.')
            "$formatted ГБ"
        }
    }
}
