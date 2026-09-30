package com.ramzes.visavinet.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramzes.visavinet.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun ImageLightboxDialog(
    imageUrl: String,
    title: String? = null,
    onDismiss: () -> Unit
) {
    ImageLightboxDialog(
        images = listOf(imageUrl),
        initialPage = 0,
        title = title,
        onDismiss = onDismiss
    )
}

@Composable
fun ImageLightboxDialog(
    images: List<String>,
    initialPage: Int = 0,
    title: String? = null,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val primaryAccent = getPrimaryAccentColor()
    val coroutineScope = rememberCoroutineScope()

    val safeInitialPage = initialPage.coerceIn(0, images.size - 1)
    val isCyclic = images.size > 1
    val loopMultiplier = if (isCyclic) 1000 else 1
    val totalPageCount = images.size * loopMultiplier
    val startPage = if (isCyclic) (loopMultiplier / 2) * images.size + safeInitialPage else 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        key(images, safeInitialPage) {
            val pagerState = rememberPagerState(
                initialPage = startPage,
                pageCount = { totalPageCount }
            )
            var isCurrentPageZoomed by remember { mutableStateOf(false) }

            LaunchedEffect(pagerState.currentPage) {
                isCurrentPageZoomed = false
            }

            val actualIndex = (pagerState.currentPage % images.size + images.size) % images.size
            val currentImageUrl = images.getOrNull(actualIndex) ?: images[0]

            var blurModifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurModifier = blurModifier.blur(24.dp)
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = blurModifier.clickable(onClick = onDismiss)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Шапка галереи
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headerTitle = if (images.size > 1) {
                            "${actualIndex + 1} / ${images.size}"
                        } else {
                            title ?: "Просмотр изображения"
                        }

                        Text(
                            text = headerTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    if (currentImageUrl.isNotBlank()) {
                                        com.ramzes.visavinet.util.DownloaderHelper.downloadFile(
                                            context = context,
                                            url = currentImageUrl,
                                            fileName = title
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Скачать",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (currentImageUrl.isNotBlank()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentImageUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Открыть в браузере",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Полноэкранный пейджер изображений свайпом и зумом
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isCurrentPageZoomed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { virtualPage ->
                        val page = (virtualPage % images.size + images.size) % images.size
                        val isCurrent = virtualPage == pagerState.currentPage

                        ZoomableBox(
                            modifier = Modifier.fillMaxSize(),
                            isCurrentPage = isCurrent,
                            onZoomChanged = { zoomed ->
                                if (isCurrent) {
                                    isCurrentPageZoomed = zoomed
                                }
                            }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(images[page])
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Точечный индикатор, если фото несколько
                    if (images.size in 2..15) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .wrapContentWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(images.size) { index ->
                                val isSelected = actualIndex == index
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) primaryAccent else Color(0x66FFFFFF))
                                        .clickable {
                                            if (!isCurrentPageZoomed && index != actualIndex) {
                                                val diff = index - actualIndex
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + diff)
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Контейнер изображения с поддержкой:
 * 1. Pinch-to-zoom (двумя пальцами масштабирование от 1x до 5x).
 * 2. Двойного тапа для быстрого зума (2.5x) и сброса (1x).
 * 3. Перемещения (pan) увеличенного изображения одним или двумя пальцами.
 * 4. Бесшовного листания в HorizontalPager в исходном масштабе (1x) без перехвата жестов.
 */
@Composable
private fun ZoomableBox(
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()
    var animationJob by remember { mutableStateOf<Job?>(null) }

    val isZoomed = scale > 1.05f

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && scale != 1f) {
            animationJob?.cancel()
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    fun clampOffset(proposedOffset: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || containerSize.width <= 0 || containerSize.height <= 0) {
            return Offset.Zero
        }
        val maxPanX = (containerSize.width * (currentScale - 1f)) / 2f
        val maxPanY = (containerSize.height * (currentScale - 1f)) / 2f
        return Offset(
            x = proposedOffset.x.coerceIn(-maxPanX, maxPanX),
            y = proposedOffset.y.coerceIn(-maxPanY, maxPanY)
        )
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val targetScale = if (scale > 1.05f) 1f else 2.5f
                        val targetOffset = if (targetScale > 1f && containerSize.width > 0 && containerSize.height > 0) {
                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            clampOffset((center - tapOffset) * (targetScale - 1f), targetScale)
                        } else {
                            Offset.Zero
                        }

                        val startScale = scale
                        val startOffset = offset

                        animationJob?.cancel()
                        animationJob = coroutineScope.launch {
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 250)
                            ) { fraction, _ ->
                                scale = startScale + (targetScale - startScale) * fraction
                                offset = Offset(
                                    x = startOffset.x + (targetOffset.x - startOffset.x) * fraction,
                                    y = startOffset.y + (targetOffset.y - startOffset.y) * fraction
                                )
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    animationJob?.cancel()
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    var panAccumulator = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()

                            if (!pastTouchSlop) {
                                val centroidSize = event.calculateCentroidSize()
                                val zoomMotion = kotlin.math.abs(1f - zoomChange) * centroidSize
                                val panMotion = panChange.getDistance()
                                if (zoomMotion > 1f || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val currentScale = scale
                                val newScale = (currentScale * zoomChange).coerceIn(1f, 5f)

                                if (newScale > 1.01f && containerSize.width > 0 && containerSize.height > 0) {
                                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val zoomFactor = newScale / currentScale
                                    val proposedOffset = offset * zoomFactor + (centroid - center) * (1f - zoomFactor) + panChange
                                    offset = clampOffset(proposedOffset, newScale)
                                    scale = newScale
                                } else {
                                    scale = 1f
                                    offset = Offset.Zero
                                }

                                event.changes.forEach { it.consume() }
                            }
                        } else if (pointerCount == 1) {
                            val change = event.changes[0]
                            val isCurrentlyZoomed = scale > 1.05f

                            if (isCurrentlyZoomed) {
                                val dragAmount = change.positionChange()
                                if (!pastTouchSlop) {
                                    panAccumulator += dragAmount
                                    if (panAccumulator.getDistance() > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }
                                if (pastTouchSlop) {
                                    offset = clampOffset(offset + dragAmount, scale)
                                    change.consume()
                                }
                            } else {
                                // В исходном масштабе не потребляем жесты 1 пальца,
                                // позволяя HorizontalPager выполнять пролистывание
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (scale in 1.0001f..1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
