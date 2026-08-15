package com.ramzes.visavinet.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramzes.visavinet.network.FileData
import com.ramzes.visavinet.ui.theme.*
import com.ramzes.visavinet.util.formatFileSize

/**
 * Динамический фоновый макет с элементами Glow Spheres
 * Использует главный пользовательский акцентный цвет getPrimaryAccentColor()
 */
@Composable
fun GlassBackground(
    isDark: Boolean = isDarkTheme(),
    accentColor: Color = getPrimaryAccentColor(),
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GlowTransition")
    
    val glowAnim1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow1"
    )
    val glowAnim2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow2"
    )

    val bgColor = if (isDark) DustyBlack else LightWhite

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (isDark) {
                // Сфера 1: Основной пользовательский цвет сверху слева
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.28f * glowAnim1),
                            accentColor.copy(alpha = 0.08f * glowAnim1),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.25f, height * 0.15f),
                        radius = width * 0.65f
                    ),
                    center = Offset(width * 0.25f, height * 0.15f),
                    radius = width * 0.65f
                )

                // Сфера 2: Смягченный акцентный оттенок снизу справа
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f * glowAnim2),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.85f, height * 0.75f),
                        radius = width * 0.7f
                    ),
                    center = Offset(width * 0.85f, height * 0.75f),
                    radius = width * 0.7f
                )
            } else {
                // Сфера 1: Мягкий акцентный оттенок сверху слева
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f * glowAnim1),
                            accentColor.copy(alpha = 0.08f * glowAnim1),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.25f, height * 0.15f),
                        radius = width * 0.7f
                    ),
                    center = Offset(width * 0.25f, height * 0.15f),
                    radius = width * 0.7f
                )

                // Сфера 2: Мягкий акцентный оттенок снизу справа
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f * glowAnim2),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.85f, height * 0.8f),
                        radius = width * 0.65f
                    ),
                    center = Offset(width * 0.85f, height * 0.8f),
                    radius = width * 0.65f
                )
            }
        }

        content()
    }
}

/**
 * Стеклянная карточка GlassCard с аккуратной одинарной окантовкой (без двойных линий)
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isDark: Boolean = isDarkTheme(),
    shape: Shape = RoundedCornerShape(6.dp),
    glowColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentColor = getPrimaryAccentColor()
    val glassBg = if (isDark) GlassDarkBg else GlassLightBg

    val borderBrush = if (glowColor != Color.Transparent) {
        Brush.linearGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.85f),
                glowColor.copy(alpha = 0.35f)
            )
        )
    } else {
        if (isDark) {
            GlassBorderDark
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White,
                    accentColor.copy(alpha = 0.22f),
                    Color(0x50CBD5E1),
                    Color(0x3094A3B8)
                )
            )
        }
    }

    val baseModifier = modifier.clip(shape)

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Surface(
        modifier = finalModifier
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            ),
        color = glassBg,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

/**
 * Премиальная стеклянная карточка профиля GlassProfileCard с диагональным отблеском акцентного цвета
 */
@Composable
fun GlassProfileCard(
    modifier: Modifier = Modifier,
    isDark: Boolean = isDarkTheme(),
    shape: Shape = RoundedCornerShape(12.dp),
    accentColor: Color = getPrimaryAccentColor(),
    content: @Composable ColumnScope.() -> Unit
) {
    val baseBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val glassAlpha = if (isDark) 0.65f else 0.85f

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = if (isDark) 0.28f else 0.18f),
            baseBg.copy(alpha = glassAlpha * 0.85f),
            baseBg.copy(alpha = glassAlpha),
            accentColor.copy(alpha = if (isDark) 0.16f else 0.10f)
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 1000f)
    )

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.85f),
            accentColor.copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.20f)
        )
    )

    Surface(
        modifier = modifier
            .clip(shape)
            .background(gradientBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape),
        color = Color.Transparent,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

/**
 * Стеклянная кнопка GlassButton с максимальным скруглением боков (CircleShape) и уменьшенной высотой (36.dp)
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDark: Boolean = isDarkTheme(),
    shape: Shape = CircleShape,
    accentColor: Color = getPrimaryAccentColor(),
    content: @Composable RowScope.() -> Unit
) {
    val buttonBg = if (enabled) {
        accentColor.copy(alpha = 0.40f)
    } else {
        if (isDark) Color(0x10FFFFFF) else Color(0x10000000)
    }

    val glowColor = if (enabled) accentColor.copy(alpha = 0.35f) else Color.Transparent

    Surface(
        modifier = modifier
            .height(36.dp)
            .drawBehind {
                if (enabled) {
                    drawIntoCanvas { canvas ->
                        val paint = Paint()
                        val fp = paint.asFrameworkPaint()
                        fp.color = glowColor.toArgb()
                        fp.setShadowLayer(16f, 0f, 3f, glowColor.toArgb())
                        canvas.drawRoundRect(0f, 0f, size.width, size.height, 18.dp.toPx(), 18.dp.toPx(), paint)
                    }
                }
            }
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (enabled) 0.9f else 0.2f),
                        Color.White.copy(alpha = 0.3f),
                        accentColor.copy(alpha = if (enabled) 0.5f else 0.1f)
                    )
                ),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        color = buttonBg,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Стеклянное текстовое поле ввода GlassTextField с уменьшенным скруглением (6.dp)
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "",
    isDark: Boolean = isDarkTheme(),
    singleLine: Boolean = true,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val textColor = if (isDark) Color.White else LightText
    val hintColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val fieldBg = if (isDark) Color(0x66151D2A) else Color(0xDDFFFFFF)
    val focusedBorderColor = getPrimaryAccentColor()

    var isFocused by remember { mutableStateOf(false) }

    val borderBrush = if (isFocused) {
        Brush.linearGradient(listOf(focusedBorderColor, PrimaryBlue))
    } else {
        if (isDark) GlassBorderDark else GlassBorderLight
    }

    val alignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (isFocused) {
                    drawIntoCanvas { canvas ->
                        val p = Paint()
                        val fp = p.asFrameworkPaint()
                        fp.color = focusedBorderColor.copy(alpha = 0.3f).toArgb()
                        fp.setShadowLayer(14f, 0f, 2f, focusedBorderColor.copy(alpha = 0.3f).toArgb())
                        canvas.drawRoundRect(0f, 0f, size.width, size.height, 6.dp.toPx(), 6.dp.toPx(), p)
                    }
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(fieldBg)
            .border(width = 1.dp, brush = borderBrush, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholderText,
                        color = hintColor,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(focusedBorderColor),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Перегруженная версия GlassTextField для работы с TextFieldValue и FocusRequester
 */
@Composable
fun GlassTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "",
    isDark: Boolean = isDarkTheme(),
    singleLine: Boolean = true,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val textColor = if (isDark) Color.White else LightText
    val hintColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary
    val fieldBg = if (isDark) Color(0x66151D2A) else Color(0xDDFFFFFF)
    val focusedBorderColor = getPrimaryAccentColor()

    var isFocused by remember { mutableStateOf(false) }

    val borderBrush = if (isFocused) {
        Brush.linearGradient(listOf(focusedBorderColor, PrimaryBlue))
    } else {
        if (isDark) GlassBorderDark else GlassBorderLight
    }

    val alignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart

    var boxModifier = modifier
        .fillMaxWidth()
        .drawBehind {
            if (isFocused) {
                drawIntoCanvas { canvas ->
                    val p = Paint()
                    val fp = p.asFrameworkPaint()
                    fp.color = focusedBorderColor.copy(alpha = 0.3f).toArgb()
                    fp.setShadowLayer(14f, 0f, 2f, focusedBorderColor.copy(alpha = 0.3f).toArgb())
                    canvas.drawRoundRect(0f, 0f, size.width, size.height, 6.dp.toPx(), 6.dp.toPx(), p)
                }
            }
        }
        .clip(RoundedCornerShape(6.dp))
        .background(fieldBg)
        .border(width = 1.dp, brush = borderBrush, shape = RoundedCornerShape(6.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Box(
        modifier = boxModifier,
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.text.isEmpty()) {
                    Text(
                        text = placeholderText,
                        color = hintColor,
                        fontSize = 14.sp
                    )
                }

                var tfModifier: Modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                if (focusRequester != null) {
                    tfModifier = tfModifier.focusRequester(focusRequester)
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(focusedBorderColor),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = tfModifier
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Стилизованная карточка прикрепленного файла с подсвеченным фоном, иконкой типа,
 * полужирным заголовком и точным размером файла.
 */
@Composable
fun GlassFileCard(
    file: FileData,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val primaryAccent = getPrimaryAccentColor()
    val cardBg = if (isDark) Color(0x1F222938) else Color(0x0F000000)
    val borderColor = primaryAccent.copy(alpha = 0.28f)
    val textColor = if (isDark) Color.White else LightText
    val subTextColor = if (isDark) TextLightGray.copy(0.7f) else LightTextSecondary

    val ext = file.extension?.uppercase()
        ?: file.name?.substringAfterLast('.', "")?.uppercase()
        ?: "FILE"

    val iconVector = when {
        file.isAudio -> Icons.Default.Audiotrack
        file.isVideo -> Icons.Default.Videocam
        ext in listOf("ZIP", "RAR", "7Z", "TAR", "GZ") -> Icons.Default.FolderZip
        ext in listOf("TXT", "DOC", "DOCX", "PDF", "RTF") -> Icons.Default.Description
        else -> Icons.Default.AttachFile
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null || !file.path.isNullOrBlank()) {
                onClick?.invoke() ?: run {
                    val filePath = file.path
                    if (!filePath.isNullOrBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(filePath))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name ?: "Файл",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatFileSize(file.size),
                        fontSize = 11.sp,
                        color = primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                    if (ext.isNotBlank()) {
                        Text(
                            text = "•  $ext",
                            fontSize = 11.sp,
                            color = subTextColor
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Скачать",
                tint = primaryAccent.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 2.dp)
            )
        }
    }
}
