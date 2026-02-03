package com.tabula.v3.ui.components

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.util.findActivity
import com.tabula.v3.ui.util.rememberImageFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 可滑动查看器覆盖层状态
 * 
 * @param images 图片列表
 * @param initialIndex 初始显示的图片索引
 * @param sourceRect 源卡片位置信息（用于容器变换动画）
 */
data class SwipeableViewerState(
    val images: List<ImageFile>,
    val initialIndex: Int,
    val sourceRect: SourceRect = SourceRect()
)

/**
 * 可滑动的沉浸式图片查看器覆盖层 - 支持左右滑动切换图片
 *
 * 动画流程：
 * 1. 入场：从卡片位置展开到屏幕中央（原比例显示）
 * 2. 交互：左右滑动切换图片，双指缩放 + 双击放大
 * 3. 出场：从中央收缩回卡片原位置
 *
 * @param viewerState 查看器状态（图片列表 + 初始索引 + 源位置）
 * @param onDismiss 关闭回调
 * @param modifier 外部修饰符
 * @param showHdr 是否显示HDR
 * @param showMotionPhoto 是否显示动态照片
 * @param playMotionSound 是否播放动态照片声音
 * @param motionSoundVolume 动态照片声音音量
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableViewerOverlay(
    viewerState: SwipeableViewerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showHdr: Boolean = false,
    showMotionPhoto: Boolean = false,
    playMotionSound: Boolean = true,
    motionSoundVolume: Int = 100
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val imageLoader = CoilSetup.getImageLoader(context)

    val images = viewerState.images
    val initialIndex = viewerState.initialIndex

    // 容器尺寸
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    // 源位置信息
    val initialSourceRect = remember { viewerState.sourceRect }
    val isSourceValid = initialSourceRect.width > 0f && initialSourceRect.height > 0f
    
    val source = if (isSourceValid) {
        initialSourceRect
    } else {
        SourceRect(
            x = containerWidthPx / 2f - 100f,
            y = containerHeightPx / 2f - 150f,
            width = 200f,
            height = 300f,
            cornerRadius = 16f
        )
    }

    // Pager 状态 - 用于滑动切换图片
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex.coerceAtLeast(0)),
        pageCount = { images.size }
    )
    
    // 当前页面的缩放状态 - 用于控制 Pager 滑动
    var currentPageZoom by remember { mutableFloatStateOf(1f) }
    
    // 当前显示的图片
    val currentIndex = pagerState.currentPage
    val currentImage = images.getOrNull(currentIndex)

    // 使用当前图片计算目标位置
    val imageAspectRatio = currentImage?.let { 
        if (it.actualHeight > 0) it.actualWidth.toFloat() / it.actualHeight else 1f 
    } ?: 1f

    val maxWidth = containerWidthPx * 0.95f
    val maxHeight = containerHeightPx * 0.90f

    val (targetWidth, targetHeight) = if (containerWidthPx > 0f && containerHeightPx > 0f && maxHeight > 0f) {
        if (imageAspectRatio > maxWidth / maxHeight) {
            maxWidth to (maxWidth / imageAspectRatio)
        } else {
            (maxHeight * imageAspectRatio) to maxHeight
        }
    } else {
        0f to 0f
    }

    val targetX = (containerWidthPx - targetWidth) / 2
    val targetY = (containerHeightPx - targetHeight) / 2

    // 动画状态
    val animProgress = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }

    // 入场动画
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 500f
            )
        )
    }

    val activity = context.findActivity()
    val window = activity?.window
    val originalColorMode = remember(window) { window?.colorMode ?: ActivityInfo.COLOR_MODE_DEFAULT }

    DisposableEffect(window) {
        onDispose {
            if (window != null) {
                window.colorMode = originalColorMode
            }
        }
    }

    fun exitViewer() {
        if (isExiting) return
        isExiting = true

        scope.launch {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 600f
                )
            )
            onDismiss()
        }
    }

    val progress = animProgress.value
    
    // 放大模式：当缩放 > 1 时，容器扩展到全屏
    val isZoomed = currentPageZoom > 1.05f
    
    // 使用动画平滑过渡尺寸和位置
    val zoomExpandProgress by animateFloatAsState(
        targetValue = if (isZoomed) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "zoomExpand"
    )
    
    // 放大模式下背景完全不透明，正常模式下 95% 透明度
    val baseAlpha = progress * 0.95f
    val backgroundAlpha = lerp(baseAlpha, 1f, zoomExpandProgress)
    
    val currentX = lerp(source.x, targetX, progress)
    val currentY = lerp(source.y, targetY, progress)
    val currentWidth = lerp(source.width, targetWidth, progress).coerceAtLeast(0f)
    val currentHeight = lerp(source.height, targetHeight, progress).coerceAtLeast(0f)
    val currentCornerRadius = lerp(source.cornerRadius, 4f, progress).coerceAtLeast(0f)
    
    // 计算实际显示的尺寸和位置（在正常模式和放大模式之间插值）
    val displayWidth = lerp(currentWidth, containerWidthPx, zoomExpandProgress).coerceAtLeast(0f)
    val displayHeight = lerp(currentHeight, containerHeightPx, zoomExpandProgress).coerceAtLeast(0f)
    val displayX = lerp(currentX, 0f, zoomExpandProgress)
    val displayY = lerp(currentY, 0f, zoomExpandProgress)
    val displayCornerRadius = lerp(currentCornerRadius, 0f, zoomExpandProgress).coerceAtLeast(0f)
    
    val displayWidthDp = with(density) { displayWidth.toDp() }
    val displayHeightDp = with(density) { displayHeight.toDp() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { exitViewer() })
            },
        contentAlignment = Alignment.TopStart
    ) {
        val actualWidth = constraints.maxWidth.toFloat()
        val actualHeight = constraints.maxHeight.toFloat()

        LaunchedEffect(actualWidth, actualHeight) {
            containerWidthPx = actualWidth
            containerHeightPx = actualHeight
        }

        // 使用 HorizontalPager 实现左右滑动
        // 当图片放大时禁用滑动，以便拖动图片
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .graphicsLayer {
                    translationX = displayX
                    translationY = displayY
                }
                .size(displayWidthDp, displayHeightDp)
                .clip(RoundedCornerShape(displayCornerRadius.dp)),
            userScrollEnabled = currentPageZoom <= 1.05f,
            key = { images.getOrNull(it)?.id ?: it }
        ) { pageIndex ->
            val pageImage = images.getOrNull(pageIndex)
            if (pageImage != null) {
                SwipeableImagePage(
                    image = pageImage,
                    imageLoader = imageLoader,
                    showHdr = showHdr,
                    showMotionPhoto = showMotionPhoto,
                    playMotionSound = playMotionSound,
                    motionSoundVolume = motionSoundVolume,
                    onDismiss = { exitViewer() },
                    window = window,
                    originalColorMode = originalColorMode,
                    onZoomChanged = { zoom ->
                        // 只更新当前页面的缩放状态
                        if (pageIndex == pagerState.currentPage) {
                            currentPageZoom = zoom
                        }
                    }
                )
            }
        }

        // 顶部页码指示器
        if (images.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${currentIndex + 1} / ${images.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 可滑动查看器的单个图片页面
 * 使用 BoxWithConstraints 计算图片实际显示尺寸，确保缩放和拖动逻辑正确
 */
@Composable
private fun SwipeableImagePage(
    image: ImageFile,
    imageLoader: coil.ImageLoader,
    showHdr: Boolean,
    showMotionPhoto: Boolean,
    playMotionSound: Boolean,
    motionSoundVolume: Int,
    onDismiss: () -> Unit,
    window: android.view.Window?,
    originalColorMode: Int,
    onZoomChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current

    // 缩放状态
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomSize by remember { mutableStateOf(Size.Zero) }
    var lastTransformTime by remember { mutableLongStateOf(0L) }

    val minScale = 1f
    val maxScale = 8f
    val doubleTapScale = 3f
    
    // 当缩放变化时通知父组件
    LaunchedEffect(zoomScale) {
        onZoomChanged(zoomScale)
    }

    // HDR / Live Photo 功能
    val features = rememberImageFeatures(
        image = image,
        enableHdr = showHdr,
        enableMotion = showMotionPhoto
    )
    val isHdr = showHdr && (features?.isHdr == true)
    val motionInfo = if (showMotionPhoto) features?.motionPhotoInfo else null
    var isHdrComparePressed by remember { mutableStateOf(false) }
    var isLivePressed by remember { mutableStateOf(false) }
    var isPressing by remember { mutableStateOf(false) }
    var wasLongPressing by remember { mutableStateOf(false) }
    val pressDelayMs = 80L

    // 高清图加载状态
    var shouldLoadUltraHd by remember { mutableStateOf(false) }
    
    val thumbnailCacheKey = remember(image.id) { "album_grid_${image.id}" }

    LaunchedEffect(zoomScale) {
        if (zoomScale > 1.5f && !shouldLoadUltraHd) {
            shouldLoadUltraHd = true
        }
    }

    fun clampOffset(offset: Offset, scale: Float, size: Size): Offset {
        if (size.width <= 0f || size.height <= 0f) return Offset.Zero
        val maxOffsetX = ((size.width * scale) - size.width).coerceAtLeast(0f) / 2f
        val maxOffsetY = ((size.height * scale) - size.height).coerceAtLeast(0f) / 2f
        return Offset(
            x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    LaunchedEffect(isPressing, isHdr, motionInfo) {
        if (!isPressing) {
            isHdrComparePressed = false
            isLivePressed = false
            return@LaunchedEffect
        }
        delay(pressDelayMs)
        if (isPressing) {
            if (isHdr) {
                isHdrComparePressed = true
            }
            if (motionInfo != null) {
                isLivePressed = true
            }
        }
    }

    val desiredColorMode = when {
        isHdr && !isHdrComparePressed -> ActivityInfo.COLOR_MODE_HDR
        isHdr && isHdrComparePressed -> ActivityInfo.COLOR_MODE_DEFAULT
        else -> originalColorMode
    }

    LaunchedEffect(desiredColorMode, window) {
        if (window != null) {
            window.colorMode = desiredColorMode
        }
    }

    // 使用 BoxWithConstraints 计算图片实际显示尺寸
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 计算图片实际显示尺寸（保持宽高比）
        val aspectRatio = if (image.actualHeight > 0) {
            image.actualWidth.toFloat() / image.actualHeight
        } else {
            1f
        }
        val containerRatio = maxWidth.value / maxHeight.value
        val (targetWidth, targetHeight) = if (aspectRatio > containerRatio) {
            maxWidth to (maxWidth / aspectRatio)
        } else {
            (maxHeight * aspectRatio) to maxHeight
        }

        // 高清图目标尺寸
        // 注意：使用 constraints.maxWidth/maxHeight（像素值），而非 targetWidth.value（Dp 值）
        // 否则初始加载的分辨率会不足，导致图片模糊，只有放大后才清晰
        val hdTargetSize = run {
            val baseSize = maxOf(constraints.maxWidth, constraints.maxHeight).toFloat()
            val scaledSize = (baseSize * zoomScale).toInt()
            val imageDim = maxOf(image.actualWidth, image.actualHeight)
            val maxAllowed = if (imageDim > 0) imageDim.coerceAtMost(4096) else 4096
            val targetDim = scaledSize.coerceIn(1, maxAllowed)
            coil.size.Size(targetDim, targetDim)
        }

        Box(
            modifier = Modifier
                .size(targetWidth, targetHeight)
                .onSizeChanged { size ->
                    zoomSize = Size(size.width.toFloat(), size.height.toFloat())
                }
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = zoomOffset.x
                    translationY = zoomOffset.y
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                // 双指缩放 - 使用自定义检测，只处理双指手势，不阻止 Pager 滑动
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            // 只在双指时处理缩放
                            if (event.changes.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    lastTransformTime = SystemClock.uptimeMillis()
                                    val newScale = (zoomScale * zoomChange).coerceIn(minScale, maxScale)
                                    zoomScale = newScale
                                    if (newScale <= minScale) {
                                        zoomOffset = Offset.Zero
                                    } else {
                                        zoomOffset = clampOffset(zoomOffset, newScale, zoomSize)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            // 单指时不消费事件，让 Pager 可以接收滑动
                        } while (event.changes.any { it.pressed })
                    }
                }
                // 单击/双击/长按
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (wasLongPressing) {
                                wasLongPressing = false
                                return@detectTapGestures
                            }
                            val timeSinceTransform = SystemClock.uptimeMillis() - lastTransformTime
                            if (timeSinceTransform > 300 && zoomScale <= 1.2f) {
                                onDismiss()
                            }
                        },
                        onPress = {
                            isPressing = true
                            tryAwaitRelease()
                            if (isHdrComparePressed || isLivePressed) {
                                wasLongPressing = true
                            }
                            isPressing = false
                        },
                        onDoubleTap = { tapOffset ->
                            val targetScale = if (zoomScale > 1.2f) minScale else doubleTapScale
                            if (zoomSize.width <= 0f || zoomSize.height <= 0f) {
                                zoomScale = targetScale
                                zoomOffset = Offset.Zero
                                return@detectTapGestures
                            }
                            if (targetScale <= minScale) {
                                zoomScale = minScale
                                zoomOffset = Offset.Zero
                            } else {
                                val center = Offset(zoomSize.width / 2f, zoomSize.height / 2f)
                                val scaleChange = targetScale / zoomScale
                                val newOffset = (zoomOffset + (tapOffset - center) * (1 - scaleChange))
                                zoomScale = targetScale
                                zoomOffset = clampOffset(newOffset, targetScale, zoomSize)
                            }
                        }
                    )
                }
                // 单指拖动（只在放大时生效）
                .then(
                    if (zoomScale > minScale) {
                        Modifier.pointerInput(zoomScale) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                lastTransformTime = SystemClock.uptimeMillis()
                                if (zoomSize.width > 0f && zoomSize.height > 0f) {
                                    val panMultiplier = zoomScale.coerceIn(1f, maxScale)
                                    val adjustedPan = Offset(dragAmount.x * panMultiplier, dragAmount.y * panMultiplier)
                                    val newOffset = zoomOffset + adjustedPan
                                    zoomOffset = clampOffset(newOffset, zoomScale, zoomSize)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // 底层：缩略图（立即可见）
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.uri)
                    .size(coil.size.Size(240, 240))
                    .precision(Precision.INEXACT)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .memoryCacheKey(thumbnailCacheKey)
                    .diskCacheKey(thumbnailCacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // 上层：高清图片
            if (motionInfo != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(image.uri)
                            .size(hdTargetSize)
                            .precision(Precision.INEXACT)
                            .crossfade(200)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build(),
                        contentDescription = image.displayName,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    MotionPhotoPlayer(
                        imageUri = image.uri,
                        motionInfo = motionInfo,
                        modifier = Modifier.fillMaxSize(),
                        playWhen = isLivePressed,
                        playAudio = playMotionSound,
                        volumePercent = motionSoundVolume
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(image.uri)
                        .size(hdTargetSize)
                        .precision(Precision.INEXACT)
                        .crossfade(200)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build(),
                    contentDescription = image.displayName,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 线性插值
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}
