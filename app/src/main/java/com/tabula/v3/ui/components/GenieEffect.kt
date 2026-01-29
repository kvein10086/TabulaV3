package com.tabula.v3.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Genie效果方向
 */
enum class GenieDirection {
    DOWN,   // 向下吸入（上宽下窄）- 用于下滑归类
    UP      // 向上吸入（下宽上窄）- 用于上滑删除
}

/**
 * Genie Effect 覆盖层 - 标准macOS神灯吸入效果
 * 
 * 物理本质：整张图片在被吸入瓶口（目标点）的过程中，
 * 高度被压缩，宽度随着靠近瓶口呈指数级收缩
 * 
 * @param direction 吸入方向：DOWN=向下吸入（上宽下窄），UP=向上吸入（下宽上窄）
 */
@Composable
fun GenieEffectOverlay(
    bitmap: Bitmap?,
    sourceBounds: Rect,
    targetX: Float,
    targetY: Float,
    progress: Float,
    screenHeight: Float = 2000f,
    direction: GenieDirection = GenieDirection.DOWN,
    modifier: Modifier = Modifier
) {
    // 检查 bitmap 是否为 null 或已被 recycle（避免闪退）
    if (bitmap == null || bitmap.isRecycled || progress <= 0f) return
    
    // 增加网格密度，让曲线更平滑
    val meshCols = 16
    val meshRows = 32
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // 再次检查，因为 Canvas 的绘制可能在异步执行
        if (bitmap.isRecycled) return@Canvas
        
        val verts = when (direction) {
            GenieDirection.DOWN -> calculateGenieVertices(
                sourceLeft = sourceBounds.left,
                sourceTop = sourceBounds.top,
                sourceWidth = sourceBounds.width,
                sourceHeight = sourceBounds.height,
                destX = targetX,
                destY = targetY,
                progress = progress.coerceIn(0f, 1f),
                meshCols = meshCols,
                meshRows = meshRows,
                screenHeight = screenHeight
            )
            GenieDirection.UP -> calculateGenieVerticesUpward(
                sourceLeft = sourceBounds.left,
                sourceTop = sourceBounds.top,
                sourceWidth = sourceBounds.width,
                sourceHeight = sourceBounds.height,
                destX = targetX,
                destY = targetY,
                progress = progress.coerceIn(0f, 1f),
                meshCols = meshCols,
                meshRows = meshRows,
                screenHeight = screenHeight
            )
        }
        
        // 透明度：最后20%开始淡出
        val alpha = if (progress > 0.8f) {
            1f - (progress - 0.8f) / 0.2f
        } else {
            1f
        }
        
        val paint = Paint().apply {
            this.alpha = (alpha * 255).toInt()
            isFilterBitmap = true
            isAntiAlias = true
        }
        
        drawIntoCanvas { canvas ->
            // 最后一道防线：绘制前再检查一次
            if (!bitmap.isRecycled) {
                canvas.nativeCanvas.drawBitmapMesh(
                    bitmap,
                    meshCols,
                    meshRows,
                    verts,
                    0,
                    null,
                    0,
                    paint
                )
            }
        }
    }
}

/**
 * 计算 macOS 风格神奇效果的网格顶点
 * 
 * 基于 https://daniate.github.io/2021/07/27/细说如何完美实现macOS中的神奇效果/
 * 
 * 极致丝滑版本：
 * 1. 两阶段动画平滑过渡
 * 2. 使用高阶缓动函数
 * 3. 曲线形变更自然
 */
private fun calculateGenieVertices(
    sourceLeft: Float,
    sourceTop: Float,
    sourceWidth: Float,
    sourceHeight: Float,
    destX: Float,
    destY: Float,
    progress: Float,
    meshCols: Int,
    meshRows: Int,
    screenHeight: Float
): FloatArray {
    val vertexCount = (meshCols + 1) * (meshRows + 1)
    val verts = FloatArray(vertexCount * 2)
    
    val originalCenterX = sourceLeft + sourceWidth / 2
    
    // ========== 平滑的两阶段动画 ==========
    // 使用 smoothstep 让两阶段之间无缝过渡
    val curveRatio = 0.4f
    
    // 曲线收窄进度（使用 smoothstep 缓动）
    val rawCurveProgress = (progress / curveRatio).coerceIn(0f, 1f)
    val curveProgress = smoothstep(rawCurveProgress)
    
    // 向下平移进度（平滑启动）
    val rawTranslationProgress = ((progress - curveRatio * 0.5f) / (1f - curveRatio * 0.5f)).coerceIn(0f, 1f)
    val translationProgress = smoothstep(rawTranslationProgress)
    
    // ========== 收窄参数 ==========
    val targetOffsetX = (destX - originalCenterX) / sourceWidth
    val clampedOffset = targetOffsetX.coerceIn(-0.48f, 0.48f)
    val finalCenterNorm = 0.5f + clampedOffset
    
    var index = 0
    
    for (row in 0..meshRows) {
        val rowRatio = row.toFloat() / meshRows
        val y = 1f - rowRatio  // 反转坐标系
        
        // ========== 1. 优化的正弦曲线 ==========
        // 使用更平滑的曲线系数
        val leftMax = curveProgress * finalCenterNorm
        val leftD = leftMax / 2f
        val leftA = leftD
        // 使用 smoothed sin 让曲线更自然
        val sinValueLeft = sin(PI.toFloat() * y + PI.toFloat() / 2f)
        val leftNorm = leftA * sinValueLeft + leftD
        
        val rightMin = 1f - curveProgress * (1f - finalCenterNorm)
        val rightD = (rightMin + 1f) / 2f
        val rightA = 1f - rightD
        val sinValueRight = sin(PI.toFloat() * y - PI.toFloat() / 2f)
        val rightNorm = rightA * sinValueRight + rightD
        
        var leftX = sourceLeft + leftNorm * sourceWidth
        var rightX = sourceLeft + rightNorm * sourceWidth
        
        // ========== 2. 平滑的向下吸收 ==========
        val originalY = sourceTop + sourceHeight * rowRatio
        val totalTranslation = destY - sourceTop - sourceHeight
        
        // 使用非线性的平移，底部先到达
        val rowTranslationFactor = 1f + rowRatio * 0.3f  // 底部移动更快
        val effectiveTranslation = translationProgress * totalTranslation * rowTranslationFactor
        var meshY = originalY + effectiveTranslation.coerceAtMost(destY - originalY)
        
        // 平滑地收缩已到达目标的行
        if (meshY >= destY - 1f) {
            meshY = destY
            // 已吸收的行宽度收缩到0
            val absorptionProgress = ((meshY - (originalY + effectiveTranslation * 0.8f)) / (destY - originalY)).coerceIn(0f, 1f)
            val shrinkFactor = 1f - smoothstep(absorptionProgress)
            val centerX = (leftX + rightX) / 2f
            leftX = lerp(centerX, leftX, shrinkFactor)
            rightX = lerp(centerX, rightX, shrinkFactor)
        }
        
        // ========== 3. 极致平滑的最终收缩 ==========
        if (progress > 0.85f) {
            val squeeze = smoothstep((progress - 0.85f) / 0.15f)
            val centerX = (leftX + rightX) / 2f
            val currentWidth = rightX - leftX
            val targetWidth = currentWidth * (1f - squeeze)
            leftX = lerp(centerX - targetWidth / 2f, destX, squeeze.pow(1.5f))
            rightX = lerp(centerX + targetWidth / 2f, destX, squeeze.pow(1.5f))
            meshY = lerp(meshY, destY, squeeze.pow(1.2f))
        }
        
        // ========== 4. 生成顶点 ==========
        for (col in 0..meshCols) {
            val colRatio = col.toFloat() / meshCols
            val x = if (progress >= 0.995f) destX else lerp(leftX, rightX, colRatio)
            val finalY = if (progress >= 0.995f) destY else meshY
            
            verts[index++] = x
            verts[index++] = finalY
        }
    }
    
    return verts
}

/**
 * 计算向上吸入效果的网格顶点（下宽上窄）
 * 
 * 用于上滑删除动画，图片被吸向上方的回收站按钮
 * 与向下版本相反：底部保持原样，顶部收缩到目标点
 */
private fun calculateGenieVerticesUpward(
    sourceLeft: Float,
    sourceTop: Float,
    sourceWidth: Float,
    sourceHeight: Float,
    destX: Float,
    destY: Float,
    progress: Float,
    meshCols: Int,
    meshRows: Int,
    screenHeight: Float
): FloatArray {
    val vertexCount = (meshCols + 1) * (meshRows + 1)
    val verts = FloatArray(vertexCount * 2)
    
    val originalCenterX = sourceLeft + sourceWidth / 2
    
    // ========== 平滑的两阶段动画 ==========
    val curveRatio = 0.4f
    
    // 曲线收窄进度
    val rawCurveProgress = (progress / curveRatio).coerceIn(0f, 1f)
    val curveProgress = smoothstep(rawCurveProgress)
    
    // 向上平移进度
    val rawTranslationProgress = ((progress - curveRatio * 0.5f) / (1f - curveRatio * 0.5f)).coerceIn(0f, 1f)
    val translationProgress = smoothstep(rawTranslationProgress)
    
    // ========== 收窄参数 ==========
    val targetOffsetX = (destX - originalCenterX) / sourceWidth
    val clampedOffset = targetOffsetX.coerceIn(-0.48f, 0.48f)
    val finalCenterNorm = 0.5f + clampedOffset
    
    var index = 0
    
    for (row in 0..meshRows) {
        val rowRatio = row.toFloat() / meshRows  // 0=顶部, 1=底部
        
        // ========== 1. 向上版本的正弦曲线（顶部收窄，底部保持）==========
        // 使用 rowRatio 直接作为 y（不反转），让顶部（row=0）先收窄
        val y = rowRatio
        
        val leftMax = curveProgress * finalCenterNorm
        val leftD = leftMax / 2f
        val leftA = leftD
        val sinValueLeft = sin(PI.toFloat() * y + PI.toFloat() / 2f)
        val leftNorm = leftA * sinValueLeft + leftD
        
        val rightMin = 1f - curveProgress * (1f - finalCenterNorm)
        val rightD = (rightMin + 1f) / 2f
        val rightA = 1f - rightD
        val sinValueRight = sin(PI.toFloat() * y - PI.toFloat() / 2f)
        val rightNorm = rightA * sinValueRight + rightD
        
        var leftX = sourceLeft + leftNorm * sourceWidth
        var rightX = sourceLeft + rightNorm * sourceWidth
        
        // ========== 2. 向上吸收（顶部先到达目标）==========
        val originalY = sourceTop + sourceHeight * rowRatio
        val totalTranslation = destY - sourceTop  // 向上移动（destY < sourceTop）
        
        // 顶部移动更快（rowRatio 越小，移动越快）
        val rowTranslationFactor = 1f + (1f - rowRatio) * 0.3f
        val effectiveTranslation = translationProgress * totalTranslation * rowTranslationFactor
        var meshY = originalY + effectiveTranslation.coerceAtLeast(destY - originalY)
        
        // 平滑地收缩已到达目标的行
        if (meshY <= destY + 1f) {
            meshY = destY
            val absorptionProgress = ((originalY + effectiveTranslation * 0.8f - meshY) / (originalY - destY)).coerceIn(0f, 1f)
            val shrinkFactor = 1f - smoothstep(absorptionProgress)
            val centerX = (leftX + rightX) / 2f
            leftX = lerp(centerX, leftX, shrinkFactor)
            rightX = lerp(centerX, rightX, shrinkFactor)
        }
        
        // ========== 3. 极致平滑的最终收缩 ==========
        if (progress > 0.85f) {
            val squeeze = smoothstep((progress - 0.85f) / 0.15f)
            val centerX = (leftX + rightX) / 2f
            val currentWidth = rightX - leftX
            val targetWidth = currentWidth * (1f - squeeze)
            leftX = lerp(centerX - targetWidth / 2f, destX, squeeze.pow(1.5f))
            rightX = lerp(centerX + targetWidth / 2f, destX, squeeze.pow(1.5f))
            meshY = lerp(meshY, destY, squeeze.pow(1.2f))
        }
        
        // ========== 4. 生成顶点 ==========
        for (col in 0..meshCols) {
            val colRatio = col.toFloat() / meshCols
            val x = if (progress >= 0.995f) destX else lerp(leftX, rightX, colRatio)
            val finalY = if (progress >= 0.995f) destY else meshY
            
            verts[index++] = x
            verts[index++] = finalY
        }
    }
    
    return verts
}

/**
 * Smoothstep 缓动函数 - 极致平滑的 S 形曲线
 * 比 easeInOutQuad 更平滑，没有突变
 */
private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * 更平滑的 Smootherstep（Ken Perlin 改进版）
 */
private fun smootherstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

/**
 * 线性插值
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

/**
 * 缓动函数 - 加速（先慢后快）
 */
private fun easeInQuad(t: Float): Float = t * t

/**
 * 缓动函数 - 先加速后减速（平滑过渡）
 */
private fun easeInOutQuad(t: Float): Float {
    return if (t < 0.5f) {
        2f * t * t
    } else {
        1f - (-2f * t + 2f).pow(2f) / 2f
    }
}

/**
 * Genie动画控制器
 */
class GenieAnimationController {
    var isAnimating by mutableStateOf(false)
        private set
    
    var progress by mutableFloatStateOf(0f)
        private set
    
    var targetX by mutableFloatStateOf(0f)
        private set
    
    var targetY by mutableFloatStateOf(0f)
        private set
    
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    
    var sourceBounds by mutableStateOf(Rect.Zero)
        private set
    
    var screenHeight by mutableFloatStateOf(2000f)
        private set
    
    var direction by mutableStateOf(GenieDirection.DOWN)
        private set
    
    private val animatable = Animatable(0f)
    
    suspend fun startAnimation(
        bitmap: Bitmap,
        sourceBounds: Rect,
        targetX: Float,
        targetY: Float,
        screenHeight: Float = 2000f,
        direction: GenieDirection = GenieDirection.DOWN,
        durationMs: Int = 380,  // 稍快但丝滑
        onComplete: () -> Unit = {}
    ) {
        this.bitmap = bitmap
        this.sourceBounds = sourceBounds
        this.targetX = targetX
        this.targetY = targetY
        this.screenHeight = screenHeight
        this.direction = direction
        this.isAnimating = true
        this.progress = 0f
        
        animatable.snapTo(0f)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                // iOS/macOS 风格的缓动曲线 - 快速启动，优雅减速
                easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
            )
        ) {
            progress = value
        }
        
        onComplete()
        reset()
    }
    
    fun reset() {
        isAnimating = false
        progress = 0f
        direction = GenieDirection.DOWN
        // 先保存引用并清空，再 recycle，避免 Compose 重组时访问已 recycle 的 bitmap
        val oldBitmap = bitmap
        bitmap = null
        oldBitmap?.recycle()
    }
}

@Composable
fun rememberGenieAnimationController(): GenieAnimationController {
    return remember { GenieAnimationController() }
}

/**
 * 从ImageFile创建用于Genie效果的缩略图Bitmap
 * 使用较小的目标尺寸以提高加载速度
 */
suspend fun createGenieBitmap(
    context: android.content.Context,
    imageUri: android.net.Uri,
    width: Int,
    height: Int
): Bitmap? {
    return try {
        // 限制最大尺寸为 400px，提高加载速度
        val maxSize = 400
        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height, 1f)
        val targetWidth = (width * scale).toInt().coerceAtLeast(100)
        val targetHeight = (height * scale).toInt().coerceAtLeast(100)
        
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, options)
        }
        
        // 使用更激进的采样以提高速度
        val sampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565  // 使用更小的颜色深度
        }
        
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, decodeOptions)
        }?.let { bitmap ->
            // 缩放到目标尺寸
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 快速创建 Genie Bitmap - 专为避免 Motion Photo 视频解码设计
 * 
 * 性能优化：
 * 1. 直接使用 BitmapFactory 解码，绕过 Coil 的 Motion Photo 处理
 * 2. 使用激进的采样率，快速解码大尺寸 HDR 图片
 * 3. 使用 RGB_565 格式减少内存和解码时间
 * 4. 限制最大尺寸，Genie 动画不需要高分辨率
 */
fun createGenieBitmapFast(
    context: android.content.Context,
    imageUri: android.net.Uri,
    width: Int,
    height: Int
): Bitmap? {
    return try {
        // Genie 动画目标尺寸限制为 250px，足够显示效果且加载快速
        val maxSize = 250
        val targetWidth = width.coerceAtMost(maxSize)
        val targetHeight = height.coerceAtMost(maxSize)
        
        // 第一步：只读取图片尺寸，不解码
        val boundsOptions = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, boundsOptions)
        }
        
        // 如果无法读取尺寸，返回 null
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }
        
        // 计算采样率 - 对于大图片使用更激进的采样
        val sampleSize = calculateInSampleSizeFast(
            boundsOptions.outWidth, 
            boundsOptions.outHeight, 
            targetWidth, 
            targetHeight
        )
        
        // 第二步：使用采样率解码
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565  // 16位色深，节省内存和解码时间
            inDither = false  // 禁用抖动，加快解码
            inPurgeable = false
            inInputShareable = false
        }
        
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, decodeOptions)
        }
        
        // 如果解码后尺寸仍然过大，进行缩放
        bitmap?.let { bmp ->
            if (bmp.width > targetWidth * 1.5 || bmp.height > targetHeight * 1.5) {
                val scaled = Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, false)
                if (scaled != bmp) bmp.recycle()
                scaled
            } else {
                bmp
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 快速计算采样率 - 针对大图片优化
 * 
 * 对于超大图片（如 HDR 4K+ 照片），使用更激进的采样率
 */
private fun calculateInSampleSizeFast(
    srcWidth: Int,
    srcHeight: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    // 计算源图片的最大边
    val srcMaxDimension = maxOf(srcWidth, srcHeight)
    
    // 计算宽高比
    val widthRatio = srcWidth / reqWidth
    val heightRatio = srcHeight / reqHeight
    
    // 取较大的比例，确保结果不超过目标尺寸
    var ratio = maxOf(widthRatio, heightRatio)
    
    // 对于超大图片（如 4K HDR），额外增加采样率以加速解码
    // 4K = 3840x2160，8K = 7680x4320
    if (srcMaxDimension > 4000) {
        ratio = maxOf(ratio, 8)  // 至少 8 倍采样
    } else if (srcMaxDimension > 2000) {
        ratio = maxOf(ratio, 4)  // 至少 4 倍采样
    }
    
    // 转换为 2 的幂次（BitmapFactory 优化）
    return when {
        ratio >= 32 -> 32
        ratio >= 16 -> 16
        ratio >= 8 -> 8
        ratio >= 4 -> 4
        ratio >= 2 -> 2
        else -> 1
    }
}

private fun calculateInSampleSize(
    options: android.graphics.BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}
