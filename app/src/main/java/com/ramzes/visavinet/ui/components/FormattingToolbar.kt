package com.ramzes.visavinet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ramzes.visavinet.ui.theme.*
import java.util.regex.Pattern

@Composable
fun FormattingToolbar(
    textFieldValue: TextFieldValue = TextFieldValue(""),
    onInsertTag: (tagStart: String, tagEnd: String) -> Unit,
    onExpandFullscreen: (() -> Unit)? = null,
    isDark: Boolean = isDarkTheme()
) {
    val primaryAccent = getPrimaryAccentColor()
    val iconDefaultColor = if (isDark) Color.White.copy(0.9f) else LightText

    val text = textFieldValue.text
    val cursor = textFieldValue.selection.start

    val isStrongActive = isTagActiveAtCursor(text, cursor, "strong") || isTagActiveAtCursor(text, cursor, "b")
    val isItalicActive = isTagActiveAtCursor(text, cursor, "i")
    val isUnderlineActive = isTagActiveAtCursor(text, cursor, "u")
    val isStrikethroughActive = isTagActiveAtCursor(text, cursor, "s")
    val isFontSizeActive = isSpanFontSizeActiveAtCursor(text, cursor)
    val isCodeActive = findEnclosingCodeBlock(text, cursor) != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // <strong> Жирный
            ToolbarButton(
                onClick = { onInsertTag("<strong>", "</strong>") },
                isActive = isStrongActive,
                primaryAccent = primaryAccent,
                isDark = isDark
            ) {
                Icon(
                    imageVector = Icons.Default.FormatBold,
                    contentDescription = "Жирный <strong>",
                    tint = if (isStrongActive) Color.White else iconDefaultColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // <i> Курсив
            ToolbarButton(
                onClick = { onInsertTag("<i>", "</i>") },
                isActive = isItalicActive,
                primaryAccent = primaryAccent,
                isDark = isDark
            ) {
                Icon(
                    imageVector = Icons.Default.FormatItalic,
                    contentDescription = "Курсив <i>",
                    tint = if (isItalicActive) Color.White else iconDefaultColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // <u> Подчеркнутый
            ToolbarButton(
                onClick = { onInsertTag("<u>", "</u>") },
                isActive = isUnderlineActive,
                primaryAccent = primaryAccent,
                isDark = isDark
            ) {
                Icon(
                    imageVector = Icons.Default.FormatUnderlined,
                    contentDescription = "Подчеркнутый <u>",
                    tint = if (isUnderlineActive) Color.White else iconDefaultColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // <s> Зачеркнутый
            ToolbarButton(
                onClick = { onInsertTag("<s>", "</s>") },
                isActive = isStrikethroughActive,
                primaryAccent = primaryAccent,
                isDark = isDark
            ) {
                Icon(
                    imageVector = Icons.Default.FormatStrikethrough,
                    contentDescription = "Зачеркнутый <s>",
                    tint = if (isStrikethroughActive) Color.White else iconDefaultColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // <span style="font-size: 1.3em;"> Крупный текст
            ToolbarButton(
                onClick = { onInsertTag("<span style=\"font-size: 1.3em;\">", "</span>") },
                isActive = isFontSizeActive,
                primaryAccent = primaryAccent,
                isDark = isDark
            ) {
                Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = "Крупный текст <span style=\"font-size: 1.3em;\">",
                    tint = if (isFontSizeActive) Color.White else iconDefaultColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // <pre class="code"><code> Код
            ToolbarButton(
                onClick = { onInsertTag("<pre class=\"code\"><code>", "</code></pre>") },
                isActive = isCodeActive,
                primaryAccent = primaryAccent,
                isDark = isDark
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Код <pre><code>",
                    tint = if (isCodeActive) Color.White else iconDefaultColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (onExpandFullscreen != null) {
            ToolbarButton(
                onClick = onExpandFullscreen,
                isActive = false,
                primaryAccent = primaryAccent,
                isDark = isDark
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Развернуть на весь экран",
                    tint = primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    onClick: () -> Unit,
    isActive: Boolean,
    primaryAccent: Color,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val bgColor = if (isActive) {
        primaryAccent.copy(alpha = 0.45f)
    } else {
        if (isDark) Color(0x20FFFFFF) else Color(0x15000000)
    }

    val borderModifier = if (isActive) {
        Modifier.border(width = 1.dp, color = primaryAccent, shape = RoundedCornerShape(6.dp))
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(borderModifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Проверка, находится ли каретка внутри незакрытого HTML-тега
 */
fun isTagActiveAtCursor(
    text: String,
    cursor: Int,
    tagName: String
): Boolean {
    if (text.isEmpty() || cursor < 0 || cursor > text.length) return false

    val textBefore = text.substring(0, cursor)
    val textAfter = text.substring(cursor)

    val openPattern = Pattern.compile("<$tagName[^>]*>", Pattern.CASE_INSENSITIVE)
    val closePattern = Pattern.compile("</$tagName>", Pattern.CASE_INSENSITIVE)

    var openCount = 0
    val openMatcher = openPattern.matcher(textBefore)
    while (openMatcher.find()) openCount++

    var closeCount = 0
    val closeMatcher = closePattern.matcher(textBefore)
    while (closeMatcher.find()) closeCount++

    val hasCloseAfter = closePattern.matcher(textAfter).find()

    return openCount > closeCount && hasCloseAfter
}

/**
 * Проверка, находится ли каретка внутри тега <span style="...font-size...">
 */
fun isSpanFontSizeActiveAtCursor(
    text: String,
    cursor: Int
): Boolean {
    if (text.isEmpty() || cursor < 0 || cursor > text.length) return false

    val textBefore = text.substring(0, cursor)
    val textAfter = text.substring(cursor)

    val openPattern = Pattern.compile("<span[^>]*style=\\s*['\"][^'\"]*font-size[^'\"]*['\"][^>]*>", Pattern.CASE_INSENSITIVE)
    val closePattern = Pattern.compile("</span>", Pattern.CASE_INSENSITIVE)

    var openCount = 0
    val openMatcher = openPattern.matcher(textBefore)
    while (openMatcher.find()) openCount++

    var closeCount = 0
    val closeMatcher = closePattern.matcher(textBefore)
    while (closeMatcher.find()) closeCount++

    val hasCloseAfter = closePattern.matcher(textAfter).find()

    return openCount > closeCount && hasCloseAfter
}

data class EnclosingCodeBlock(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int
)

fun isCodeTag(tagStart: String, tagEnd: String): Boolean {
    return tagStart.contains("code", ignoreCase = true) ||
           tagStart.contains("pre", ignoreCase = true) ||
           tagEnd.contains("code", ignoreCase = true) ||
           tagEnd.contains("pre", ignoreCase = true)
}

/**
 * Находит блок кода (code / pre), внутри которого находится курсор.
 * Корректно учитывает как составные теги (<pre class="code"><code>...</code></pre>),
 * так и одиночные (<pre>...</pre>, <code>...</code>).
 * Если в тексте несколько блоков кода — возвращает именно тот конкретный блок, внутри которого курсор.
 */
fun findEnclosingCodeBlock(text: String, cursor: Int): EnclosingCodeBlock? {
    if (text.isEmpty() || cursor < 0 || cursor > text.length) return null

    val tagTokenRegex = Regex(
        "(<pre\\b[^>]*>\\s*<code\\b[^>]*>|<pre\\b[^>]*>|<code\\b[^>]*>)|(</code\\s*>\\s*</pre\\s*>|</pre\\s*>|</code\\s*>)",
        RegexOption.IGNORE_CASE
    )

    data class OpenMatch(val start: Int, val end: Int)
    val openStack = ArrayDeque<OpenMatch>()
    val matchedBlocks = mutableListOf<EnclosingCodeBlock>()

    for (match in tagTokenRegex.findAll(text)) {
        val isOpen = match.groups[1] != null
        if (isOpen) {
            openStack.addLast(OpenMatch(match.range.first, match.range.last + 1))
        } else {
            if (openStack.isNotEmpty()) {
                val open = openStack.removeLast()
                matchedBlocks.add(
                    EnclosingCodeBlock(
                        openStart = open.start,
                        openEnd = open.end,
                        closeStart = match.range.first,
                        closeEnd = match.range.last + 1
                    )
                )
            }
        }
    }

    // Если остались незакрытые теги в стеке, дополняем их до конца текста
    while (openStack.isNotEmpty()) {
        val open = openStack.removeLast()
        matchedBlocks.add(
            EnclosingCodeBlock(
                openStart = open.start,
                openEnd = open.end,
                closeStart = text.length,
                closeEnd = text.length
            )
        )
    }

    // Фильтруем блоки, внутри которых находится курсор (строго между началом открытия и концом закрытия)
    val candidates = matchedBlocks.filter { block ->
        cursor > block.openStart && cursor < block.closeEnd
    }

    if (candidates.isEmpty()) return null

    // Если есть вложенные блоки, выбираем самый внутренний (наименьшая длина)
    return candidates.minByOrNull { it.closeEnd - it.openStart }
}

fun removeCodeTagBlock(value: TextFieldValue, block: EnclosingCodeBlock): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start
    val selection = value.selection

    val openTagLen = block.openEnd - block.openStart

    val newText = text.substring(0, block.openStart) +
            text.substring(block.openEnd, block.closeStart) +
            text.substring(block.closeEnd)

    return if (selection.collapsed) {
        val newCursor = when {
            cursor <= block.openEnd -> block.openStart
            cursor >= block.closeStart -> block.openStart + (block.closeStart - block.openEnd)
            else -> cursor - openTagLen
        }.coerceIn(0, newText.length)

        TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
    } else {
        val newSelStart = when {
            selection.start <= block.openEnd -> block.openStart
            selection.start >= block.closeStart -> block.openStart + (block.closeStart - block.openEnd)
            else -> selection.start - openTagLen
        }.coerceIn(0, newText.length)

        val newSelEnd = when {
            selection.end <= block.openEnd -> block.openStart
            selection.end >= block.closeStart -> block.openStart + (block.closeStart - block.openEnd)
            else -> selection.end - openTagLen
        }.coerceIn(0, newText.length)

        TextFieldValue(
            text = newText,
            selection = TextRange(newSelStart, newSelEnd)
        )
    }
}

/**
 * Вставка или отмена дальнейшего применения тега при повторном клике по активной кнопке
 */
fun applyTagToTextFieldValue(
    value: TextFieldValue,
    tagStart: String,
    tagEnd: String
): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val cursor = selection.start

    // 1. Специальная обработка для тегов кода:
    // если курсор находится внутри любого блока кода (составного или одиночного),
    // удаляем именно этот тег кода, оставляя всё содержимое нетронутым.
    if (isCodeTag(tagStart, tagEnd)) {
        val codeBlock = findEnclosingCodeBlock(text, cursor)
        if (codeBlock != null) {
            return removeCodeTagBlock(value, codeBlock)
        }
    }

    val tagCleanName = tagEnd.substringAfter("</").substringBefore(">").trim()

    // Если тег активен в текущей позиции курсора и нет выделения
    if (selection.collapsed && isTagActiveAtCursor(text, cursor, tagCleanName)) {
        val textBefore = text.substring(0, cursor)
        val textAfter = text.substring(cursor)

        // 1. Курсор прямо перед закрывающим тегом (например "<strong>Текст|</strong>") -> Шагаем за тег
        if (textAfter.startsWith("</$tagCleanName>")) {
            val newCursorPos = cursor + "</$tagCleanName>".length
            return TextFieldValue(
                text = text,
                selection = TextRange(newCursorPos)
            )
        }

        // 2. Курсор внутри совершенно пустого тега (например "<strong>|</strong>") -> Удаляем пустую пару
        val openTagPos = textBefore.lastIndexOf("<$tagCleanName")
        if (openTagPos != -1 && textAfter.startsWith("</$tagCleanName>")) {
            val closeTagEndPos = cursor + "</$tagCleanName>".length
            val newText = text.removeRange(openTagPos, closeTagEndPos)
            return TextFieldValue(
                text = newText,
                selection = TextRange(openTagPos)
            )
        }

        // 3. Курсор посреди заполненного тега -> Выводим курсор за пределы закрывающего тега
        val closeIndex = textAfter.indexOf("</$tagCleanName>")
        if (closeIndex != -1) {
            val newCursorPos = cursor + closeIndex + "</$tagCleanName>".length
            return TextFieldValue(
                text = text,
                selection = TextRange(newCursorPos)
            )
        }
    }

    // Если тег не был активен или есть выделение -> Вставляем тег
    return if (selection.collapsed) {
        val newText = StringBuilder(text)
            .insert(cursor, tagStart + tagEnd)
            .toString()
        val newCursorPos = cursor + tagStart.length
        TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    } else {
        val start = selection.min
        val end = selection.max
        val selectedText = text.substring(start, end)
        val newText = StringBuilder(text)
            .replace(start, end, "$tagStart$selectedText$tagEnd")
            .toString()
        val newSelectionEnd = start + tagStart.length + selectedText.length + tagEnd.length
        TextFieldValue(
            text = newText,
            selection = TextRange(newSelectionEnd)
        )
    }
}

fun insertHtmlTag(currentText: String, tagStart: String, tagEnd: String): String {
    if (isCodeTag(tagStart, tagEnd)) {
        val trimmed = currentText.trimEnd()
        val emptyCode = "<pre class=\"code\"><code></code></pre>"
        if (trimmed.endsWith(emptyCode)) {
            return trimmed.removeSuffix(emptyCode).trimEnd()
        }
    }
    return "$currentText $tagStart$tagEnd"
}
fun insertBbTag(currentText: String, tagStart: String, tagEnd: String): String = insertHtmlTag(currentText, tagStart, tagEnd)
