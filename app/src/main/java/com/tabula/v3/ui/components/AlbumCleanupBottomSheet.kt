package com.tabula.v3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.AlbumCleanupEngine
import com.tabula.v3.data.repository.AlbumCleanupInfo
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 清理模式枚举
 */
enum class CleanupMode {
    ALBUM,  // 图集专清
    MONTH   // 月份专清
}

/**
 * 月份信息数据类
 */
data class MonthInfo(
    val year: Int,
    val month: Int,  // 1-12
    val imageCount: Int,
    val coverImageId: Long?,
    val images: List<ImageFile>
) {
    val id: String get() = "$year-$month"
    
    /** 用于清理引擎的虚拟 Album ID（以 month_ 前缀区分） */
    val cleanupAlbumId: String get() = "month_${year}_${month}"
    
    val displayName: String get() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return if (year == currentYear) {
            "${month}月"
        } else {
            "${year}年${month}月"
        }
    }
    
    /**
     * 创建用于清理引擎的虚拟 Album 对象
     */
    fun toVirtualAlbum(): Album {
        return Album(
            id = cleanupAlbumId,
            name = displayName,
            coverImageId = coverImageId,
            imageCount = imageCount
        )
    }
}

fun buildMonthInfos(allImages: List<ImageFile>): List<MonthInfo> {
    if (allImages.isEmpty()) return emptyList()

    val calendar = Calendar.getInstance()
    return allImages.groupBy { image ->
        calendar.timeInMillis = image.dateModified
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1  // Calendar.MONTH 是 0-11
        "$year-$month"
    }.map { (key, images) ->
        val parts = key.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        // 按时间倒序排列图片，取最新的作为封面
        val sortedImages = images.sortedByDescending { it.dateModified }
        MonthInfo(
            year = year,
            month = month,
            imageCount = images.size,
            coverImageId = sortedImages.firstOrNull()?.id,
            images = sortedImages
        )
    }.sortedWith(compareByDescending<MonthInfo> { it.year }.thenByDescending { it.month })
}

/**
 * 图集清理选择底部弹窗
 * 
 * 用于选择要进行清理的图集或月份，显示：
 * - 顶部"切换到全局整理"按钮
 * - 模式切换（图集专清 / 月份专清）
 * - 图集列表或月份列表
 * - 已完成的图集会显示勾选标记，选择时弹窗提醒
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCleanupBottomSheet(
    albums: List<Album>,
    albumCleanupInfos: List<AlbumCleanupInfo>,
    selectedAlbumId: String?,
    analyzingAlbumId: String?,
    analysisProgress: Float,
    onSelectAlbum: (Album) -> Unit,
    onSwitchToGlobal: () -> Unit,
    onDismiss: () -> Unit,
    onStartCleanup: () -> Unit,
    onResetAndStartCleanup: ((Album) -> Unit)? = null,  // 重置并重新开始清理（用于已完成的图集）
    // 月份专清相关参数
    allImages: List<ImageFile> = emptyList(),
    selectedMonthId: String? = null,
    onSelectMonth: ((MonthInfo) -> Unit)? = null,
    onStartMonthCleanup: (() -> Unit)? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val accentColor = Color(0xFF007AFF)
    
    // 清理模式状态
    var cleanupMode by remember { mutableStateOf(CleanupMode.ALBUM) }
    
    // 确认弹窗状态（用于已完成图集的重新整理提醒）
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var pendingResetAlbum by remember { mutableStateOf<Album?>(null) }
    
    // 显示所有图集（包括已完成的），已完成的会有特殊标记
    // 排序规则：只有已完成的置底，其他保持原有顺序
    val displayAlbums = albums.sortedBy { album ->
        val cleanupInfo = albumCleanupInfos.find { it.album.id == album.id }
        // 只有已完成的排到后面，其他（未分析、正在清理）保持原有顺序
        cleanupInfo?.isCompleted == true
    }
    
    // 从图片列表中提取月份分组（按时间倒序排列）
    val monthInfos = remember(allImages) {
        buildMonthInfos(allImages)
    }
    
    // 获取导航栏高度，用于底部内边距
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },  // 沉浸式：延伸到导航栏区域
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.3f)
                        else Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = maxOf(navigationBarPadding, 16.dp))  // 沉浸式：内容底部留出导航栏安全距离，最小16dp
        ) {
            // 标题
            Text(
                text = "选择图集进行整理",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            // 切换到全局整理按钮
            Button(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSwitchToGlobal()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "切换到全局整理",
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 模式切换分段控件（图集专清 / 月份专清）
            CleanupModeToggle(
                currentMode = cleanupMode,
                onModeChange = { mode ->
                    HapticFeedback.lightTap(context)
                    cleanupMode = mode
                },
                isDarkTheme = isDarkTheme,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 根据模式显示不同的列表
            when (cleanupMode) {
                CleanupMode.ALBUM -> {
                    // 图集列表
                    if (displayAlbums.isEmpty()) {
                        // 空状态
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "所有图集已整理完毕",
                                    color = secondaryTextColor,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(displayAlbums, key = { it.id }) { album ->
                                val info = albumCleanupInfos.find { it.album.id == album.id }
                                val isSelected = selectedAlbumId == album.id
                                val isAnalyzing = analyzingAlbumId == album.id
                                
                                AlbumCleanupItem(
                                    album = album,
                                    info = info,
                                    isSelected = isSelected,
                                    isAnalyzing = isAnalyzing,
                                    analysisProgress = if (isAnalyzing) analysisProgress else (info?.analysisProgress ?: 0f),
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme,
                                    onClick = {
                                        HapticFeedback.lightTap(context)
                                        onSelectAlbum(album)
                                    }
                                )
                            }
                        }
                    }
                }
                
                CleanupMode.MONTH -> {
                    // 月份列表
                    if (monthInfos.isEmpty()) {
                        // 空状态
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "没有找到照片",
                                    color = secondaryTextColor,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(monthInfos, key = { it.id }) { monthInfo ->
                                val isSelected = selectedMonthId == monthInfo.id
                                
                                MonthCleanupItem(
                                    monthInfo = monthInfo,
                                    isSelected = isSelected,
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme,
                                    onClick = {
                                        HapticFeedback.lightTap(context)
                                        onSelectMonth?.invoke(monthInfo)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // 底部开始按钮 - 根据模式显示不同状态
            val showStartButton = when (cleanupMode) {
                CleanupMode.ALBUM -> selectedAlbumId != null && displayAlbums.isNotEmpty()
                CleanupMode.MONTH -> selectedMonthId != null && monthInfos.isNotEmpty()
            }
            
            if (showStartButton) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // 检查选中的图集是否已完成（仅图集模式）
                val selectedAlbum = albums.find { it.id == selectedAlbumId }
                val selectedInfo = albumCleanupInfos.find { it.album.id == selectedAlbumId }
                val isSelectedCompleted = selectedInfo?.isCompleted == true
                
                Button(
                    onClick = {
                        HapticFeedback.mediumTap(context)
                        when (cleanupMode) {
                            CleanupMode.ALBUM -> {
                                if (isSelectedCompleted && selectedAlbum != null) {
                                    // 已完成的图集，显示确认弹窗
                                    pendingResetAlbum = selectedAlbum
                                    showResetConfirmDialog = true
                                } else {
                                    onStartCleanup()
                                }
                            }
                            CleanupMode.MONTH -> {
                                onStartMonthCleanup?.invoke()
                            }
                        }
                    },
                    enabled = analyzingAlbumId == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = accentColor.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(50.dp)
                ) {
                    if (analyzingAlbumId != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在分析...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "开始整理",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
    
    // 重新整理确认弹窗
    if (showResetConfirmDialog && pendingResetAlbum != null) {
        AlertDialog(
            onDismissRequest = {
                showResetConfirmDialog = false
                pendingResetAlbum = null
            },
            containerColor = containerColor,
            title = {
                Text(
                    text = "重新整理",
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "「${pendingResetAlbum?.name}」已经整理完毕。\n\n是否重新整理这个图集？",
                    color = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingResetAlbum?.let { album ->
                            onResetAndStartCleanup?.invoke(album)
                        }
                        showResetConfirmDialog = false
                        pendingResetAlbum = null
                    }
                ) {
                    Text(
                        text = "重新整理",
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        pendingResetAlbum = null
                    }
                ) {
                    Text(
                        text = "取消",
                        color = secondaryTextColor
                    )
                }
            }
        )
    }
}

/**
 * 图集清理列表项
 */
@Composable
private fun AlbumCleanupItem(
    album: Album,
    info: AlbumCleanupInfo?,
    isSelected: Boolean,
    isAnalyzing: Boolean,
    analysisProgress: Float,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            accentColor.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        label = "ItemBackground"
    )
    
    // 计算进度显示
    val showProgress = info != null && info.totalGroups > 0
    val cleanupProgress = info?.let { 
        if (it.totalGroups > 0) it.processedGroups.toFloat() / it.totalGroups else 0f 
    } ?: 0f
    
    // 状态文本
    val statusText = when {
        isAnalyzing -> "分析中..."
        info?.isCompleted == true -> "已完成"
        info?.totalGroups ?: -1 < 0 -> "点击开始分析"
        info?.totalGroups == 0 -> null  // 无相似照片时不显示文字
        else -> "共 ${info?.totalGroups} 组 · 剩余 ${info?.remainingGroups} 组"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面图
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
        ) {
            album.coverImageId?.let { coverId ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("content://media/external/images/media/$coverId")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } ?: run {
                // 无封面时显示颜色块
                val albumColor = album.color?.let { Color(it) } ?: Color(0xFF7986CB)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(albumColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = album.name.take(1),
                        color = albumColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 名称和状态
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = album.name,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "${album.imageCount} 张",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 进度条或状态文本
            if (isAnalyzing) {
                // 分析中显示分析进度
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { analysisProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(analysisProgress * 100).toInt()}%",
                        color = secondaryTextColor,
                        fontSize = 11.sp
                    )
                }
            } else if (showProgress && info?.isCompleted == false && statusText != null) {
                // 清理进度
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { cleanupProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (info.isCompleted) Color(0xFF34C759) else accentColor,
                        trackColor = accentColor.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        color = secondaryTextColor,
                        fontSize = 11.sp
                    )
                }
            } else if (statusText != null) {
                Text(
                    text = statusText,
                    color = if (info?.isCompleted == true) Color(0xFF34C759) else secondaryTextColor,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 右侧：已完成图标 或 单选按钮
        if (info?.isCompleted == true) {
            // 已完成的图集显示勾选图标
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "已完成",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(28.dp)
            )
        } else {
            // 未完成的显示单选按钮
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accentColor,
                    unselectedColor = secondaryTextColor
                )
            )
        }
    }
}

/**
 * 清理模式切换分段控件 - iOS风格居中设计
 */
@Composable
private fun CleanupModeToggle(
    currentMode: CleanupMode,
    onModeChange: (CleanupMode) -> Unit,
    isDarkTheme: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    // 容器背景色
    val containerColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    
    // 滑块背景色
    val selectedBgColor = if (isDarkTheme) Color(0xFF48484A) else Color.White
    
    // 文字颜色
    val selectedTextColor = if (isDarkTheme) Color.White else Color.Black
    val unselectedTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor)
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // 图集专清
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (currentMode == CleanupMode.ALBUM) selectedBgColor else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentMode != CleanupMode.ALBUM) {
                            onModeChange(CleanupMode.ALBUM)
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "图集专清",
                    fontSize = 14.sp,
                    fontWeight = if (currentMode == CleanupMode.ALBUM) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (currentMode == CleanupMode.ALBUM) selectedTextColor else unselectedTextColor
                )
            }
            
            // 月份专清
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (currentMode == CleanupMode.MONTH) selectedBgColor else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentMode != CleanupMode.MONTH) {
                            onModeChange(CleanupMode.MONTH)
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "月份专清",
                    fontSize = 14.sp,
                    fontWeight = if (currentMode == CleanupMode.MONTH) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (currentMode == CleanupMode.MONTH) selectedTextColor else unselectedTextColor
                )
            }
        }
    }
}

/**
 * 月份清理列表项
 */
@Composable
private fun MonthCleanupItem(
    monthInfo: MonthInfo,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            accentColor.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        label = "MonthItemBackground"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面图
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
        ) {
            monthInfo.coverImageId?.let { coverId ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("content://media/external/images/media/$coverId")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } ?: run {
                // 无封面时显示日历图标
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 名称和数量
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = monthInfo.displayName,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${monthInfo.imageCount} 张照片",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 单选按钮
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor,
                unselectedColor = secondaryTextColor
            )
        )
    }
}
