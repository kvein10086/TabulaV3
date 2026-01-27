package com.tabula.v3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.model.SyncMode
import com.tabula.v3.ui.components.AlbumDeleteConfirmDialog
import com.tabula.v3.ui.components.AlbumEditDialog
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.ViewerOverlay
import com.tabula.v3.ui.components.ViewerState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 相册视图屏幕
 *
 * 展示所有自定义相册，点击进入相册内容。
 * 支持相册管理（新建、编辑、删除、排序）。
 *
 * 设计风格：
 * - 大卡片布局，突出视觉效果
 * - 渐变封面 + Emoji 图标
 * - 流畅的交互动画
 */
@Composable
fun AlbumViewScreen(
    albums: List<Album>,
    allImages: List<ImageFile>,
    getImagesForAlbum: suspend (String) -> List<Long>,
    onCreateAlbum: (name: String, color: Long?, emoji: String?) -> Unit,
    onUpdateAlbum: (Album) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onToggleSync: ((String, Boolean) -> Unit)? = null,
    onChangeSyncMode: ((String, SyncMode) -> Unit)? = null,
    onNavigateBack: () -> Unit,
    initialAlbumId: String? = null,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = false,
    motionSoundVolume: Int = 100
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current

    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    // 查找当前相册
    val currentAlbum = remember(initialAlbumId, albums) {
        albums.find { it.id == initialAlbumId }
    }

    // 状态
    var editingAlbum by remember { mutableStateOf<Album?>(null) }
    var deletingAlbum by remember { mutableStateOf<Album?>(null) }
    var viewerState by remember { mutableStateOf<ViewerState?>(null) }
    var albumImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }

    // 加载图片逻辑
    LaunchedEffect(currentAlbum?.id, allImages) {
        if (currentAlbum != null) {
            val imageIds = getImagesForAlbum(currentAlbum.id)
            albumImages = allImages.filter { it.id in imageIds }
        } else {
            albumImages = emptyList()
        }
    }
    
    // 如果没有指定相册，直接退出
    LaunchedEffect(initialAlbumId) {
        if (initialAlbumId == null) {
            onNavigateBack()
        }
    }

    // 返回拦截
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (currentAlbum != null) {
            // 直接显示详情
             AlbumContentView(
                album = currentAlbum,
                images = albumImages,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                isDarkTheme = isDarkTheme,
                showHdrBadges = showHdrBadges,
                showMotionBadges = showMotionBadges,
                onImageClick = { image, sourceRect ->
                    viewerState = ViewerState(image, sourceRect)
                },
                onEditClick = { editingAlbum = currentAlbum },
                onDeleteClick = { deletingAlbum = currentAlbum },
                onToggleSyncClick = if (onToggleSync != null) {
                    { onToggleSync(currentAlbum.id, !currentAlbum.isSyncEnabled) }
                } else null,
                onChangeSyncModeClick = if (onChangeSyncMode != null) {
                    { mode -> onChangeSyncMode(currentAlbum.id, mode) }
                } else null,
                onSetCover = if (onUpdateAlbum != null) { imageId ->
                    val updatedAlbum = currentAlbum.copy(coverImageId = imageId)
                    onUpdateAlbum(updatedAlbum)
                } else null,
                onNavigateBack = onNavigateBack
            )
        } else {
             // Loading state
             Box(Modifier.fillMaxSize())
        }
        
        // 查看器
        viewerState?.let { state ->
            ViewerOverlay(
                viewerState = state,
                onDismiss = { viewerState = null },
                showHdr = showHdrBadges,
                showMotionPhoto = showMotionBadges,
                playMotionSound = playMotionSound,
                motionSoundVolume = motionSoundVolume
            )
        }
    }
    
     // 编辑相册对话框
    editingAlbum?.let { album ->
        AlbumEditDialog(
            isEdit = true,
            initialName = album.name,
            initialColor = album.color,
            initialEmoji = album.emoji,
            onConfirm = { name, color, emoji ->
                onUpdateAlbum(album.copy(name = name, color = color, emoji = emoji))
                editingAlbum = null
            },
            onDismiss = { editingAlbum = null }
        )
    }

    // 删除确认对话框
    deletingAlbum?.let { album ->
        AlbumDeleteConfirmDialog(
            albumName = album.name,
            imageCount = album.imageCount,
            onConfirm = {
                onDeleteAlbum(album.id)
                deletingAlbum = null
                onNavigateBack() // 删除后退出
            },
            onDismiss = { deletingAlbum = null }
        )
    }
}

/**
 * 相册内容视图
 */
@Composable
private fun AlbumContentView(
    album: Album,
    images: List<ImageFile>,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    onImageClick: (ImageFile, SourceRect) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleSyncClick: (() -> Unit)? = null,
    onChangeSyncModeClick: ((SyncMode) -> Unit)? = null,
    onSetCover: ((Long) -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (album.emoji != null) {
                        Text(text = album.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = album.name,
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // 同步状态指示
                    if (album.isSyncEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "已同步",
                            tint = Color(0xFF30D158),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${images.size} 张照片",
                    color = secondaryTextColor,
                    fontSize = 13.sp
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = textColor
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑相册") },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        }
                    )
                    // 同步开关选项
                    if (onToggleSyncClick != null) {
                        DropdownMenuItem(
                            text = { 
                                Text(if (album.isSyncEnabled) "关闭系统同步" else "同步到系统相册")
                            },
                            onClick = {
                                showMenu = false
                                HapticFeedback.mediumTap(context)
                                onToggleSyncClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (album.isSyncEnabled) 
                                        Icons.Outlined.SyncDisabled 
                                    else 
                                        Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = if (album.isSyncEnabled) 
                                        Color(0xFFFF9F0A) 
                                    else 
                                        Color(0xFF30D158)
                                )
                            }
                        )
                        // 同步模式选择（仅当同步已开启时显示）
                        if (album.isSyncEnabled && onChangeSyncModeClick != null) {
                            val isMoveMode = album.syncMode == SyncMode.MOVE
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(
                                            text = "同步模式",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (isMoveMode) "移动（节省空间）" else "复制（保留原图）",
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    HapticFeedback.lightTap(context)
                                    val newMode = if (isMoveMode) SyncMode.COPY else SyncMode.MOVE
                                    onChangeSyncModeClick(newMode)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isMoveMode) Color(0xFF30D158) else Color(0xFF007AFF)
                                    )
                                }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("删除相册", color = Color(0xFFFF3B30)) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }

        if (images.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = album.emoji ?: "📷",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "相册是空的",
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "在清理照片时点击相册名称添加照片",
                        color = secondaryTextColor,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 照片网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                items(images, key = { it.id }) { image ->
                    PhotoGridItem(
                        image = image,
                        showHdrBadge = showHdrBadges,
                        showMotionBadge = showMotionBadges,
                        onClick = { sourceRect ->
                            onImageClick(image, sourceRect)
                        },
                        onSetCover = onSetCover?.let { callback ->
                            { callback(image.id) }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 照片网格项
 * 
 * 支持显示 HDR/Live 标识和长按设置封面
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    image: ImageFile,
    showHdrBadge: Boolean = false,
    showMotionBadge: Boolean = false,
    onClick: (SourceRect) -> Unit,
    onSetCover: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf(SourceRect(0f, 0f, 0f, 0f, 0f)) }
    var showCoverMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onClick(bounds)
                },
                onLongClick = if (onSetCover != null) {
                    {
                        HapticFeedback.heavyTap(context)
                        showCoverMenu = true
                    }
                } else null
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(image.uri)
                .crossfade(true)
                .build(),
            contentDescription = image.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // HDR / Live 标识
        val badges = rememberPhotoGridBadges(
            image = image,
            showHdr = showHdrBadge,
            showMotion = showMotionBadge
        )
        
        if (badges.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                badges.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // 设置封面菜单
        if (showCoverMenu && onSetCover != null) {
            DropdownMenu(
                expanded = showCoverMenu,
                onDismissRequest = { showCoverMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("设为相册封面") },
                    onClick = {
                        showCoverMenu = false
                        onSetCover()
                    }
                )
            }
        }
    }
}

/**
 * 记住照片网格项的标识
 */
@Composable
private fun rememberPhotoGridBadges(
    image: ImageFile,
    showHdr: Boolean,
    showMotion: Boolean
): List<String> {
    // 简化版本：基于文件名检测
    // 完整版本应该使用 EXIF 数据 
    val badges = mutableListOf<String>()
    
    val name = image.displayName.lowercase()
    if (showHdr && (name.contains("hdr") || name.contains("_hdr"))) {
        badges.add("HDR")
    }
    if (showMotion && (name.contains("mvimg") || name.contains("motion") || name.contains("live"))) {
        badges.add("Live")
    }
    
    return badges
}

