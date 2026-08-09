package com.ramzes.visavinet.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

/**
 * VisualTransformation, которая СКРЫВАЕТ HTML-теги (strong, i, u, s, pre/code) из поля ввода,
 * отображая только чистый текст с наложенными стилями (жирный, курсив, код и т.д.).
 */
class HtmlVisualTransformation(
    private val codeBgColor: Color = Color(0x401E3A8A)
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text

        // Парсим теги и строим отображаемый текст без самих тегов
        val parseResult = parseAndStripHtmlTags(rawText, codeBgColor)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, rawText.length)
                return parseResult.originalToTransformed[clamped]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, parseResult.transformedLength)
                return parseResult.transformedToOriginal[clamped]
            }
        }

        return TransformedText(parseResult.annotatedString, offsetMapping)
    }
}

private data class HtmlParseResult(
    val annotatedString: AnnotatedString,
    val transformedLength: Int,
    val originalToTransformed: IntArray,
    val transformedToOriginal: IntArray
)

private fun parseAndStripHtmlTags(rawText: String, codeBgColor: Color): HtmlParseResult {
    val origLen = rawText.length
    val isTagMask = BooleanArray(origLen)

    // Список стилей, которые нужно наложить на символы
    val charStyles = Array(origLen) { mutableListOf<SpanStyle>() }

    val strongStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val underlineStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    val strikethroughStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor, color = Color(0xFF93C5FD))

    // Находим теги и размечаем маску удаляемых символов тегов и стили содержимого
    markTagsAndStyles(rawText, "strong", strongStyle, isTagMask, charStyles)
    markTagsAndStyles(rawText, "b", strongStyle, isTagMask, charStyles)
    markTagsAndStyles(rawText, "i", italicStyle, isTagMask, charStyles)
    markTagsAndStyles(rawText, "u", underlineStyle, isTagMask, charStyles)
    markTagsAndStyles(rawText, "s", strikethroughStyle, isTagMask, charStyles)
    markCodeBlockTagsAndStyles(rawText, codeStyle, isTagMask, charStyles)
    markSpanColorTagsAndStyles(rawText, isTagMask, charStyles)

    val cleanBuilder = StringBuilder()
    val origToTrans = IntArray(origLen + 1)
    val transToOrigList = mutableListOf<Int>()

    var transIndex = 0
    for (i in 0 until origLen) {
        origToTrans[i] = transIndex
        if (!isTagMask[i]) {
            cleanBuilder.append(rawText[i])
            transToOrigList.add(i)
            transIndex++
        }
    }
    origToTrans[origLen] = transIndex
    transToOrigList.add(origLen)

    val transToOrig = transToOrigList.toIntArray()

    val annotatedString = buildAnnotatedString {
        append(cleanBuilder.toString())

        // Переносим сопоставленные стили на трансформированные символы
        var currTrans = 0
        for (i in 0 until origLen) {
            if (!isTagMask[i]) {
                val styles = charStyles[i]
                for (style in styles) {
                    addStyle(style, currTrans, currTrans + 1)
                }
                currTrans++
            }
        }
    }

    return HtmlParseResult(
        annotatedString = annotatedString,
        transformedLength = cleanBuilder.length,
        originalToTransformed = origToTrans,
        transformedToOriginal = transToOrig
    )
}

private fun markTagsAndStyles(
    text: String,
    tagName: String,
    style: SpanStyle,
    isTagMask: BooleanArray,
    charStyles: Array<MutableList<SpanStyle>>
) {
    val pattern = Pattern.compile("<$tagName>(.*?)</$tagName>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
        val openTagStart = matcher.start()
        val contentStart = matcher.start(1)
        val contentEnd = matcher.end(1)
        val closeTagEnd = matcher.end()

        // Размечаем символы открывающего и закрывающего тегов как скрытые
        for (i in openTagStart until contentStart) {
            if (i in isTagMask.indices) isTagMask[i] = true
        }
        for (i in contentEnd until closeTagEnd) {
            if (i in isTagMask.indices) isTagMask[i] = true
        }

        // Применяем стиль к внутреннему содержимому
        for (i in contentStart until contentEnd) {
            if (i in charStyles.indices) {
                charStyles[i].add(style)
            }
        }
    }
}

private fun markCodeBlockTagsAndStyles(
    text: String,
    style: SpanStyle,
    isTagMask: BooleanArray,
    charStyles: Array<MutableList<SpanStyle>>
) {
    val pattern = Pattern.compile("<pre[^>]*><code[^>]*>(.*?)</code></pre>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
        val openTagStart = matcher.start()
        val contentStart = matcher.start(1)
        val contentEnd = matcher.end(1)
        val closeTagEnd = matcher.end()

        for (i in openTagStart until contentStart) {
            if (i in isTagMask.indices) isTagMask[i] = true
        }
        for (i in contentEnd until closeTagEnd) {
            if (i in isTagMask.indices) isTagMask[i] = true
        }

        for (i in contentStart until contentEnd) {
            if (i in charStyles.indices) {
                charStyles[i].add(style)
            }
        }
    }
}

private fun markSpanColorTagsAndStyles(
    text: String,
    isTagMask: BooleanArray,
    charStyles: Array<MutableList<SpanStyle>>
) {
    val pattern = Pattern.compile("<span[^>]*style=\\s*\"[^\"]*color\\s*:\\s*([^;\"]+)[;\"]?\"[^>]*>(.*?)</span>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
        val openTagStart = matcher.start()
        val colorStr = matcher.group(1)
        val contentStart = matcher.start(2)
        val contentEnd = matcher.end(2)
        val closeTagEnd = matcher.end()

        val parsedColor = parseColorString(colorStr) ?: continue

        for (i in openTagStart until contentStart) {
            if (i in isTagMask.indices) isTagMask[i] = true
        }
        for (i in contentEnd until closeTagEnd) {
            if (i in isTagMask.indices) isTagMask[i] = true
        }

        val spanStyle = SpanStyle(color = parsedColor)
        for (i in contentStart until contentEnd) {
            if (i in charStyles.indices) {
                charStyles[i].add(spanStyle)
            }
        }
    }
}

/**
 * Оборачивает абзацы текста в теги <p>...</p> перед отправкой на сервер.
 */
fun ensureParagraphTags(text: String): String {
    if (text.isBlank()) return text
    val lines = text.trim().split("\n")
    return lines.joinToString("\n") { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            ""
        } else if (trimmed.startsWith("<p>") || trimmed.startsWith("<pre") || trimmed.startsWith("<blockquote")) {
            trimmed
        } else {
            "<p>$trimmed</p>"
        }
    }
}
