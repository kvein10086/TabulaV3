package com.tabula.v3.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 触觉反馈工具类
 * 
 * 提供轻量级的震动反馈，提升交互体验
 */
object HapticFeedback {
    
    private var vibrator: Vibrator? = null
    private var enabled: Boolean = true
    private var strength: Int = 70
    
    // 上一次振动时间，用于节流
    private var lastVibrateTime: Long = 0L
    // 最小振动间隔（毫秒）
    private const val MIN_VIBRATE_INTERVAL_MS = 60L

    /**
     * Update global haptic settings.
     */
    fun updateSettings(enabled: Boolean, strength: Int) {
        this.enabled = enabled
        this.strength = strength.coerceIn(0, 100)
    }
    
    /**
     * 初始化震动器
     */
    private fun getVibrator(context: Context): Vibrator? {
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
        return vibrator
    }

    private fun canVibrate(): Boolean {
        return enabled && strength > 0
    }

    private fun resolveAmplitude(scale: Float): Int {
        val normalized = (strength.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
        val scaled = (normalized * scale).coerceIn(0f, 1f)
        return (scaled * 255f).toInt().coerceIn(1, 255)
    }

    private fun resolveDuration(base: Long, extraMax: Long): Long {
        val normalized = (strength.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
        val extra = (extraMax * normalized).toLong()
        return (base + extra).coerceAtLeast(1L)
    }

    private fun vibrateOneShot(
        context: Context, 
        baseDurationMs: Long, 
        extraMaxMs: Long, 
        scale: Float,
        throttle: Boolean = false
    ) {
        if (!canVibrate()) return
        
        // 节流检查：如果启用节流且距离上次振动时间太短，跳过本次振动
        if (throttle) {
            val now = System.currentTimeMillis()
            if (now - lastVibrateTime < MIN_VIBRATE_INTERVAL_MS) {
                return
            }
            lastVibrateTime = now
        }
        
        try {
            val v = getVibrator(context) ?: return
            val durationMs = resolveDuration(baseDurationMs, extraMaxMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = resolveAmplitude(scale)
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
    
    /**
     * 使用指定强度进行振动（带节流），用于强度预览
     * @param tempStrength 临时使用的强度值（0-100）
     */
    private fun vibrateWithStrength(
        context: Context,
        tempStrength: Int,
        baseDurationMs: Long,
        extraMaxMs: Long,
        scale: Float
    ) {
        if (!enabled) return
        val effectiveStrength = tempStrength.coerceIn(0, 100)
        if (effectiveStrength <= 0) return
        
        // 节流检查
        val now = System.currentTimeMillis()
        if (now - lastVibrateTime < MIN_VIBRATE_INTERVAL_MS) {
            return
        }
        lastVibrateTime = now
        
        try {
            val v = getVibrator(context) ?: return
            
            // 使用临时强度计算振幅和时长
            val normalized = (effectiveStrength / 100f).coerceIn(0f, 1f)
            val durationMs = (baseDurationMs + extraMaxMs * normalized).toLong().coerceAtLeast(1L)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val scaled = (normalized * scale).coerceIn(0f, 1f)
                val amplitude = (scaled * 255f).toInt().coerceIn(1, 255)
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
    
    /**
     * 轻触反馈 - 用于普通点击
     * 
     * @param throttle 是否启用节流（用于高频场景如下滑切换标签）
     */
    fun lightTap(context: Context, throttle: Boolean = false) {
        vibrateOneShot(context, baseDurationMs = 22, extraMaxMs = 24, scale = 1.0f, throttle = throttle)
    }
    
    /**
     * 中等反馈 - 用于重要操作（如删除）
     * 
     * @param throttle 是否启用节流
     */
    fun mediumTap(context: Context, throttle: Boolean = false) {
        vibrateOneShot(context, baseDurationMs = 30, extraMaxMs = 30, scale = 1.2f, throttle = throttle)
    }
    
    /**
     * 重反馈 - 用于删除确认等
     */
    fun heavyTap(context: Context) {
        vibrateOneShot(context, baseDurationMs = 42, extraMaxMs = 38, scale = 1.4f)
    }
    
    /**
     * 双击反馈 - 用于成功操作
     */
    fun doubleTap(context: Context) {
        if (!canVibrate()) return
        try {
            val v = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = resolveAmplitude(1.2f)
                val timings = longArrayOf(0, 28, 90, 28)
                val amplitudes = intArrayOf(0, amplitude, 0, amplitude)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(25)
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
    
    /**
     * 振动强度预览 - 用于调节强度滑块时的反馈
     * 
     * 使用指定的强度值产生振动，并带有节流机制防止过于频繁的振动请求。
     * 即使强度较低也会产生可感知的振动。
     * 
     * @param context Android上下文
     * @param previewStrength 要预览的强度值（0-100）
     */
    fun strengthPreview(context: Context, previewStrength: Int) {
        // 保证最小可感知强度：即使用户设置很低，预览时也用一个较高的最小值
        // 这样用户能感受到"有振动"，同时通过振动强度的变化感受差异
        val minPreviewStrength = 35  // 最小预览强度
        val effectiveStrength = maxOf(previewStrength.coerceIn(0, 100), minPreviewStrength)
        
        vibrateWithStrength(
            context = context,
            tempStrength = effectiveStrength,
            baseDurationMs = 25,
            extraMaxMs = 30,
            scale = 1.1f
        )
    }
}
