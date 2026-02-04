package com.tabula.v3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.AlbumStorageInfo
import com.tabula.v3.data.model.StorageScanResult
import com.tabula.v3.data.repository.StorageStatsManager
import com.tabula.v3.ui.components.GlassDivider
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 综合统计页面
 *
 * 展示图片存储占用情况和各图集空间大小
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    
    // 存储统计管理器
    val storageStatsManager = remember { StorageStatsManager.getInstance(context) }
    val scanResult by storageStatsManager.scanResult.collectAsState()
    val isScanning by storageStatsManager.isScanning.collectAsState()
    
    // 扫描进度（原始值）
    var rawScanProgress by remember { mutableFloatStateOf(0f) }
    
    // 平滑的扫描进度动画
    val smoothScanProgress = remember { Animatable(0f) }
    
    // 当原始进度变化时，平滑动画到目标值
    LaunchedEffect(rawScanProgress) {
        if (rawScanProgress > smoothScanProgress.value) {
            // 计算动画时长：进度差越大，动画越长，但有上下限
            val progressDiff = rawScanProgress - smoothScanProgress.value
            val duration = (progressDiff * 800).toInt().coerceIn(150, 500)
            
            smoothScanProgress.animateTo(
                targetValue = rawScanProgress,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
    
    // 扫描开始时重置
    LaunchedEffect(isScanning) {
        if (isScanning) {
            rawScanProgress = 0f
            smoothScanProgress.snapTo(0f)
        }
    }
    
    // 初始化加载上次扫描结果
    LaunchedEffect(Unit) {
        storageStatsManager.initialize()
    }
    
    // 配色
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val cardColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val accentColor = TabulaColors.EyeGold
    val surfaceColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    
    // 圆环进度动画 - 使用 Animatable 实现更丝滑的效果
    val progressAnimatable = remember { Animatable(0f) }
    
    LaunchedEffect(scanResult.scanTimestamp, scanResult.imageStorageRatio) {
        if (scanResult.scanTimestamp > 0) {
            // 延迟一点启动动画，让界面先渲染
            delay(150)
            // 使用 spring 动画，更加自然流畅
            progressAnimatable.animateTo(
                targetValue = scanResult.imageStorageRatio,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessVeryLow
                )
            )
        }
    }
    
    // 重置动画（重新扫描时）
    LaunchedEffect(isScanning) {
        if (isScanning) {
            progressAnimatable.snapTo(0f)
        }
    }
    
    val animatedProgress = progressAnimatable.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // ========== 顶部栏 ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor
                )
            }
            
            Text(
                text = "存储统计",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== 顶部圆环卡片 ==========
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(cardColor)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 圆环图表
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(200.dp)
                        ) {
                            StorageCircularChart(
                                progress = animatedProgress,
                                isScanning = isScanning,
                                scanProgress = smoothScanProgress.value,
                                size = 200.dp,
                                color = accentColor,
                                trackColor = surfaceColor,
                                isDarkTheme = isDarkTheme
                            )
                            
                            // 中心内容
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                if (isScanning) {
                                    ScanningIndicator(accentColor = accentColor)
                                } else if (scanResult.scanTimestamp > 0) {
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 42.sp
                                        ),
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = StorageStatsManager.formatBytes(scanResult.totalImageStorage),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = accentColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "图片占用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor
                                    )
                                } else {
                                    // 首次使用引导
                                    Icon(
                                        imageVector = Icons.Outlined.Storage,
                                        contentDescription = null,
                                        tint = accentColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "分析存储占用",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "了解照片如何占用空间",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 扫描按钮
                        ScanButton(
                            isScanning = isScanning,
                            hasPreviousScan = scanResult.scanTimestamp > 0,
                            accentColor = accentColor,
                            onClick = {
                                HapticFeedback.mediumTap(context)
                                scope.launch {
                                    storageStatsManager.performScan { progress ->
                                        rawScanProgress = progress
                                    }
                                }
                            }
                        )
                        
                        // 上次扫描时间
                        AnimatedVisibility(
                            visible = scanResult.scanTimestamp > 0 && !isScanning,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(300))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "上次扫描 · ${formatTimestamp(scanResult.scanTimestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
            
            // ========== 存储概览卡片 ==========
            if (scanResult.scanTimestamp > 0 && !isScanning) {
                item {
                    StorageOverviewCard(
                        scanResult = scanResult,
                        cardColor = cardColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        accentColor = accentColor,
                        surfaceColor = surfaceColor
                    )
                }
            }
            
            // ========== 图集存储列表标题 ==========
            if (scanResult.albumStorageList.isNotEmpty() && !isScanning) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "相册占用详情",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textColor
                        )
                        Text(
                            text = "${scanResult.albumStorageList.size} 个相册",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )
                    }
                }
                
                // ========== 图集存储列表 ==========
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardColor)
                    ) {
                        scanResult.albumStorageList.forEachIndexed { index, album ->
                            AlbumStorageRow(
                                album = album,
                                totalImageStorage = scanResult.totalImageStorage,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentColor,
                                surfaceColor = surfaceColor,
                                index = index
                            )
                            
                            if (index < scanResult.albumStorageList.size - 1) {
                                GlassDivider(
                                    isDarkTheme = isDarkTheme,
                                    startPadding = 68.dp
                                )
                            }
                        }
                    }
                }
            }
            
            // 底部留白
            item {
                Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())
            }
        }
    }
}

/**
 * 扫描中指示器
 */
@Composable
private fun ScanningIndicator(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    
    // 脉冲缩放动画
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // 透明度动画
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Storage,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
                .alpha(alpha)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "正在扫描...",
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 扫描按钮
 */
@Composable
private fun ScanButton(
    isScanning: Boolean,
    hasPreviousScan: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "button")
    
    // 扫描时的旋转动画 - 更慢更流畅
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    androidx.compose.material3.Button(
        onClick = { if (!isScanning) onClick() },
        enabled = !isScanning,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isScanning) accentColor.copy(alpha = 0.15f) else accentColor,
            contentColor = if (isScanning) accentColor else Color.Black,
            disabledContainerColor = accentColor.copy(alpha = 0.15f),
            disabledContentColor = accentColor
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .then(if (isScanning) Modifier.rotate(rotation) else Modifier)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    isScanning -> "扫描中..."
                    hasPreviousScan -> "重新扫描"
                    else -> "立即扫描"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * 存储圆环图表
 */
@Composable
private fun StorageCircularChart(
    progress: Float,
    isScanning: Boolean,
    scanProgress: Float,  // 已经是平滑处理后的值
    size: Dp,
    color: Color,
    trackColor: Color,
    isDarkTheme: Boolean
) {
    val strokeWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
    
    val infiniteTransition = rememberInfiniteTransition(label = "chart")
    
    // 扫描时的旋转动画 - 缓慢平稳旋转
    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanRotation"
    )
    
    // 渐变色 - 更柔和
    val gradientColors = listOf(
        color,
        color.copy(alpha = 0.85f),
        color.copy(alpha = 0.7f)
    )
    
    Canvas(
        modifier = Modifier
            .size(size)
            .then(if (isScanning) Modifier.rotate(scanRotation) else Modifier)
    ) {
        // 背景轨道
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // 进度弧
        val sweepAngle = if (isScanning) {
            // 扫描时：基础弧度 + 随平滑进度增长
            // 使用更大的基础弧度，让进度条一开始就有明显的视觉反馈
            90f + 270f * scanProgress
        } else {
            360f * progress
        }
        
        if (sweepAngle > 0) {
            drawArc(
                brush = Brush.sweepGradient(gradientColors),
                startAngle = -90f,
                sweepAngle = sweepAngle.coerceAtMost(360f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 存储概览卡片
 */
@Composable
private fun StorageOverviewCard(
    scanResult: StorageScanResult,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    surfaceColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StorageInfoItem(
            icon = Icons.Outlined.PhotoLibrary,
            label = "图片总量",
            value = StorageStatsManager.formatBytesShort(scanResult.totalImageStorage),
            change = scanResult.totalStorageChange,
            accentColor = accentColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            cardColor = cardColor,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        
        StorageInfoItem(
            icon = Icons.Outlined.Storage,
            label = "设备存储",
            value = StorageStatsManager.formatBytesShort(scanResult.totalPhoneStorage),
            subValue = "已用 ${StorageStatsManager.formatBytesShort(scanResult.usedPhoneStorage)}",
            accentColor = Color(0xFF007AFF),
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            cardColor = cardColor,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

/**
 * 存储信息项
 */
@Composable
private fun StorageInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    change: Long? = null,
    subValue: String? = null,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部：图标和标签
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 底部：数值和变化
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 变化指示或副标题
            if (change != null && change != 0L) {
                val isIncrease = change > 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isIncrease) Color(0xFFFF6B6B).copy(alpha = 0.12f)
                            else Color(0xFF51CF66).copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isIncrease) 
                            Icons.AutoMirrored.Outlined.TrendingUp 
                        else 
                            Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        tint = if (isIncrease) Color(0xFFFF6B6B) else Color(0xFF51CF66),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${if (isIncrease) "+" else ""}${StorageStatsManager.formatBytesShort(kotlin.math.abs(change))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isIncrease) Color(0xFFFF6B6B) else Color(0xFF51CF66),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (subValue != null) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor.copy(alpha = 0.7f)
                )
            } else {
                // 占位，保持高度一致
                Text(
                    text = "相比上次",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * 相册存储行
 */
@Composable
private fun AlbumStorageRow(
    album: AlbumStorageInfo,
    totalImageStorage: Long,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    surfaceColor: Color,
    index: Int
) {
    val ratio = if (totalImageStorage > 0) {
        (album.storageSize.toFloat() / totalImageStorage).coerceIn(0f, 1f)
    } else 0f
    
    // 进度条动画
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progressBar"
    )
    
    // 根据排名设置不同颜色
    val iconColor = when (index) {
        0 -> accentColor
        1 -> Color(0xFF007AFF)
        2 -> Color(0xFF34C759)
        else -> secondaryTextColor
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 名称和进度条
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.bucketName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // 进度条（带动画，最小显示宽度）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(surfaceColor)
            ) {
                // 确保进度条有最小可见宽度（至少 2%）
                val displayRatio = if (animatedRatio > 0f) animatedRatio.coerceAtLeast(0.02f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayRatio)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(iconColor)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${album.imageCount} 张",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                Text(
                    text = " · ${String.format("%.1f", ratio * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor.copy(alpha = 0.6f)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 大小和变化
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = StorageStatsManager.formatBytesShort(album.storageSize),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor
                )
            }
            
            // 变化量
            if (album.hasChange) {
                val change = album.storageChange!!
                val isIncrease = change > 0
                val absChange = kotlin.math.abs(change)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isIncrease) Color(0xFFFF6B6B).copy(alpha = 0.12f)
                            else Color(0xFF51CF66).copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isIncrease) 
                            Icons.AutoMirrored.Outlined.TrendingUp 
                        else 
                            Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        tint = if (isIncrease) Color(0xFFFF6B6B) else Color(0xFF51CF66),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${if (isIncrease) "+" else "-"}${StorageStatsManager.formatBytesShort(absChange)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isIncrease) Color(0xFFFF6B6B) else Color(0xFF51CF66)
                    )
                }
            }
        }
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} 分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} 小时前"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} 天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
