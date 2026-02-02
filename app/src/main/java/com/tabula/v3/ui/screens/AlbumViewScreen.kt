package com.tabula.v3.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.model.SyncMode
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.components.AlbumDeleteConfirmDialog
import com.tabula.v3.ui.components.AlbumEditDialog
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.SwipeableViewerOverlay
import com.tabula.v3.ui.components.SwipeableViewerState
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
    getImageMappingsForAlbum: (suspend (String) -> List<Pair<Long, String>>)? = null,
    onCreateAlbum: (name: String, color: Long?, emoji: String?) -> Unit,
    onUpdateAlbum: (Album) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onNavigateBack: () -> Unit,
    initialAlbumId: String? = null,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = false,
    motionSoundVolume: Int = 100,
    onMoveToAlbum: ((List<Long>, String) -> Unit)? = null,
    onCopyToAlbum: ((List<Long>, String) -> Unit)? = null,
    onCleanupAlbum: ((String) -> Unit)? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val contentResolver = context.contentResolver

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
    var viewerState by remember { mutableStateOf<SwipeableViewerState?>(null) }
    var albumImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    
    // 渐进式加载状态
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreImages by remember { mutableStateOf(true) }
    
    // 使用 Map 优化查找，将 O(n) 降为 O(1)
    val imageMap = remember(allImages) { allImages.associateBy { it.id } }

    // 加载图片逻辑 - 优化版：渐进式加载 + Map 查找
    LaunchedEffect(currentAlbum?.id) {
        if (currentAlbum != null) {
            // 重置加载状态
            hasMoreImages = true
            
            if (getImageMappingsForAlbum != null) {
                // 使用新的加载方式：支持已清理原图的图片
                val mappings = getImageMappingsForAlbum(currentAlbum.id)
                
                // 使用 Map 进行 O(1) 查找，而非 O(n) 的 find
                // 收集需要从 MediaStore 查询的 URI（不在 imageMap 中的）
                val foundImages = mutableListOf<ImageFile>()
                val missingUris = mutableListOf<android.net.Uri>()
                
                for ((imageId, imageUri) in mappings) {
                    val image = imageMap[imageId]
                    if (image != null) {
                        foundImages.add(image)
                    } else {
                        missingUris.add(android.net.Uri.parse(imageUri))
                    }
                }
                
                // 批量查询缺失的图片（限制批量大小避免 ANR）
                val batchSize = 50
                val queriedImages = mutableListOf<ImageFile>()
                for (i in missingUris.indices step batchSize) {
                    val batch = missingUris.subList(i, minOf(i + batchSize, missingUris.size))
                    batch.mapNotNull { uri ->
                        queryImageFromUri(contentResolver, uri)
                    }.let { queriedImages.addAll(it) }
                }
                
                // 合并结果并去重
                albumImages = (foundImages + queriedImages).distinctBy { it.id }
            } else {
                // 兼容旧的加载方式 - 也使用 Set 优化
                val imageIds = getImagesForAlbum(currentAlbum.id).toSet()
                albumImages = allImages.filter { it.id in imageIds }
            }
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
                    val index = albumImages.indexOf(image).coerceAtLeast(0)
                    viewerState = SwipeableViewerState(
                        images = albumImages,
                        initialIndex = index,
                        sourceRect = sourceRect
                    )
                },
                onEditClick = { editingAlbum = currentAlbum },
                onDeleteClick = { deletingAlbum = currentAlbum },
                onSetCover = if (onUpdateAlbum != null) { imageId ->
                    val updatedAlbum = currentAlbum.copy(coverImageId = imageId)
                    onUpdateAlbum(updatedAlbum)
                } else null,
                onNavigateBack = onNavigateBack,
                albums = albums,
                allImages = allImages,
                onMoveToAlbum = onMoveToAlbum,
                onCopyToAlbum = onCopyToAlbum,
                onCleanupAlbum = onCleanupAlbum
            )
        } else {
             // Loading state
             Box(Modifier.fillMaxSize())
        }
        
        // 查看器 - 支持左右滑动切换图片
        viewerState?.let { state ->
            SwipeableViewerOverlay(
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
            existingAlbumNames = albums.map { it.name },
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
    onSetCover: ((Long) -> Unit)? = null,
    onNavigateBack: () -> Unit,
    albums: List<Album> = emptyList(),
    allImages: List<ImageFile> = emptyList(),
    onMoveToAlbum: ((List<Long>, String) -> Unit)? = null,
    onCopyToAlbum: ((List<Long>, String) -> Unit)? = null,
    onCleanupAlbum: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    
    // 多选模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedImageIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 图集选择弹窗状态
    var showAlbumPicker by remember { mutableStateOf(false) }
    var isCopyMode by remember { mutableStateOf(false) }  // true=复制, false=移动
    
    // 清理旧图确认弹窗状态
    var showCleanupConfirm by remember { mutableStateOf(false) }
    
    // 退出多选模式
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedImageIds = emptySet()
    }
    
    // 切换选中状态
    fun toggleSelection(imageId: Long) {
        selectedImageIds = if (imageId in selectedImageIds) {
            selectedImageIds - imageId
        } else {
            selectedImageIds + imageId
        }
        // 如果取消选中后没有选中项，退出多选模式
        if (selectedImageIds.isEmpty()) {
            isSelectionMode = false
        }
    }
    
    // 全选/取消全选
    fun toggleSelectAll() {
        selectedImageIds = if (selectedImageIds.size == images.size) {
            emptySet()
        } else {
            images.map { it.id }.toSet()
        }
        if (selectedImageIds.isEmpty()) {
            isSelectionMode = false
        }
    }
    
    // 返回处理：多选模式下先退出多选
    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }
    
    // 背景色（确保覆盖整个屏幕包括导航栏区域）
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
        // 顶部栏 - 根据多选模式切换显示内容
        AnimatedContent(
            targetState = isSelectionMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                fadeOut(animationSpec = tween(200))
            },
            label = "topBarTransition"
        ) { selectionMode ->
            if (selectionMode) {
                // 多选模式顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 取消按钮
                    IconButton(onClick = { exitSelectionMode() }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "取消",
                            tint = textColor
                        )
                    }
                    
                    // 选中数量
                    Text(
                        text = "已选 ${selectedImageIds.size} 项",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 全选按钮
                    TextButton(
                        onClick = { toggleSelectAll() }
                    ) {
                        Text(
                            text = if (selectedImageIds.size == images.size) "取消全选" else "全选",
                            color = Color(0xFF007AFF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // 普通模式顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
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
                        }
                        Text(
                            text = "${images.size} 张照片",
                            color = secondaryTextColor,
                            fontSize = 13.sp
                        )
                    }

                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "更多",
                                tint = textColor
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(
                                    color = if (isDarkTheme) Color(0xFF2C2C2E) else Color.White,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .width(180.dp)
                        ) {
                            // 编辑相册
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "编辑相册",
                                        color = textColor,
                                        fontWeight = FontWeight.Medium
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    onEditClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Edit, 
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            
                            // 清理旧图（仅当有图片且回调存在时显示）
                            if (onCleanupAlbum != null && images.isNotEmpty()) {
                                // 分隔线
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .height(0.5.dp)
                                        .background(secondaryTextColor.copy(alpha = 0.2f))
                                )
                                
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            "清理旧图",
                                            color = textColor,
                                            fontWeight = FontWeight.Medium
                                        ) 
                                    },
                                    onClick = {
                                        showMenu = false
                                        showCleanupConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            
                            // 分隔线
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .height(0.5.dp)
                                    .background(secondaryTextColor.copy(alpha = 0.2f))
                            )
                            
                            // 删除相册
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "删除相册", 
                                        color = Color(0xFFFF3B30),
                                        fontWeight = FontWeight.Medium
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFFF3B30),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 只有当 imageCount 也为 0 时才显示空状态，避免加载期间闪烁
        if (images.isEmpty() && album.imageCount == 0) {
            // 空状态 - TaTa 占领相册
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // TaTa 图片
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(R.drawable.zpcat1)
                            .crossfade(true)
                            .build(),
                        contentDescription = "TaTa",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    // 主标题
                    Text(
                        text = "糟糕",
                        color = textColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 副标题 - TaTa 强调显示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TaTa",
                            color = Color(0xFF007AFF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = " 占领了你的相册",
                            color = textColor.copy(alpha = 0.8f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 提示文字
                    Text(
                        text = "在滑一滑中添加照片到这里",
                        color = secondaryTextColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 照片网格 - 使用 WindowInsets 获取导航栏高度，让内容沉浸到导航栏下方
            val navBarInsets = WindowInsets.navigationBars
            val navBarHeight = with(LocalDensity.current) { navBarInsets.getBottom(this).toDp() }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 4.dp,
                    end = 4.dp,
                    top = 4.dp,
                    // 底部留出导航栏空间 + 多选模式下的操作栏空间
                    bottom = navBarHeight + if (isSelectionMode) 80.dp else 4.dp
                ),
                state = gridState,
                modifier = Modifier.fillMaxSize()
                // 注：LazyVerticalGrid 的预取由 Compose 自动管理
                // 图片加载优化主要通过 Coil 的缓存策略实现
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
                        },
                        isSelectionMode = isSelectionMode,
                        isSelected = image.id in selectedImageIds,
                        onLongPress = {
                            // 长按进入多选模式并选中当前图片
                            isSelectionMode = true
                            selectedImageIds = setOf(image.id)
                        },
                        onToggleSelect = {
                            toggleSelection(image.id)
                        }
                    )
                }
            }
        }
        }
        
        // 多选模式底部操作栏
        androidx.compose.animation.AnimatedVisibility(
            visible = isSelectionMode && selectedImageIds.isNotEmpty(),
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (isDarkTheme) Color.Black.copy(alpha = 0.95f) 
                                else Color.White.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 复制到其他图集按钮
                    if (onCopyToAlbum != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    HapticFeedback.mediumTap(context)
                                    isCopyMode = true
                                    showAlbumPicker = true
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileCopy,
                                contentDescription = "复制到图集",
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "复制到图集",
                                color = Color(0xFF30D158),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // 移动到其他图集按钮
                    if (onMoveToAlbum != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    HapticFeedback.mediumTap(context)
                                    isCopyMode = false
                                    showAlbumPicker = true
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DriveFileMove,
                                contentDescription = "移动到图集",
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "移动到图集",
                                color = Color(0xFF007AFF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // 图集选择弹窗
        if (showAlbumPicker) {
            AlbumPickerDialog(
                albums = albums.filter { it.id != album.id },  // 排除当前图集
                allImages = allImages,
                title = if (isCopyMode) "复制到图集" else "移动到图集",
                onAlbumSelected = { targetAlbumId ->
                    // 根据模式执行复制或移动操作
                    if (isCopyMode) {
                        onCopyToAlbum?.invoke(selectedImageIds.toList(), targetAlbumId)
                    } else {
                        onMoveToAlbum?.invoke(selectedImageIds.toList(), targetAlbumId)
                    }
                    showAlbumPicker = false
                    exitSelectionMode()
                    HapticFeedback.mediumTap(context)
                },
                onDismiss = { showAlbumPicker = false },
                isDarkTheme = isDarkTheme
            )
        }
        
        // 清理旧图确认对话框
        if (showCleanupConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCleanupConfirm = false },
                containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White,
                title = {
                    Text(
                        text = "清理旧图",
                        color = if (isDarkTheme) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "将删除此图集中图片在旧位置的文件（包括原图和移动残留）。\n\n当前图集中的图片不受影响。已清理过的会自动跳过。",
                        color = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onCleanupAlbum?.invoke(album.id)
                            showCleanupConfirm = false
                            HapticFeedback.mediumTap(context)
                        }
                    ) {
                        Text(
                            text = "清理",
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanupConfirm = false }) {
                        Text(
                            text = "取消",
                            color = Color(0xFF007AFF)
                        )
                    }
                }
            )
        }
    }
}

/**
 * 照片网格项
 * 
 * 支持显示 HDR/Live 标识、长按设置封面、多选模式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    image: ImageFile,
    showHdrBadge: Boolean = false,
    showMotionBadge: Boolean = false,
    onClick: (SourceRect) -> Unit,
    onSetCover: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelect: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageLoader = remember { CoilSetup.getImageLoader(context) }
    val coordinatesHolder = remember { AlbumLayoutCoordinatesHolder() }
    var showCoverMenu by remember { mutableStateOf(false) }
    
    // 选中状态的动画
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectionScale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .scale(selectionScale)
            .clip(RoundedCornerShape(4.dp))
            .onGloballyPositioned { coordinates ->
                coordinatesHolder.value = coordinates
            }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        // 多选模式下，点击切换选中状态
                        HapticFeedback.lightTap(context)
                        onToggleSelect()
                    } else {
                        // 普通模式下，点击查看大图
                        HapticFeedback.lightTap(context)
                        val rect = coordinatesHolder.value?.takeIf { it.isAttached }?.boundsInRoot()
                        val sourceRect = if (rect != null) {
                            SourceRect(
                                x = rect.left,
                                y = rect.top,
                                width = rect.width,
                                height = rect.height,
                                cornerRadius = 4f
                            )
                        } else {
                            SourceRect()
                        }
                        onClick(sourceRect)
                    }
                },
                onLongClick = {
                    HapticFeedback.heavyTap(context)
                    if (!isSelectionMode) {
                        // 长按进入多选模式
                        onLongPress()
                    } else if (onSetCover != null) {
                        // 多选模式下长按可以设置封面（可选）
                        showCoverMenu = true
                    }
                }
            )
    ) {
        // 使用稳定的缓存键，基于图片 ID
        val cacheKey = remember(image.id) { "album_grid_${image.id}" }
        
        // 占位符背景色 - 在图片加载前显示，避免空白闪烁
        val isDarkTheme = LocalIsDarkTheme.current
        val placeholderColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
        
        // 先绘制占位符背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(placeholderColor)
        )
        
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(image.uri)
                .size(Size(240, 240))
                .precision(Precision.INEXACT)
                .bitmapConfig(Bitmap.Config.RGB_565)
                // 启用硬件位图加速（Android 8.0+）- 更快的渲染
                .allowHardware(true)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                // 禁用 crossfade 动画 - 直接显示，减少视觉延迟
                .crossfade(false)
                .build(),
            contentDescription = image.displayName,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // 选中状态遮罩和勾选标记
        if (isSelectionMode) {
            // 半透明遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) Color.Black.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
            )
            
            // 勾选图标
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .shadow(2.dp, CircleShape)
                    .background(
                        color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.9f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "已选中",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    // 空心圆
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Transparent, CircleShape)
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }
        
        // HDR / Live 标识
        val badges = rememberPhotoGridBadges(
            image = image,
            showHdr = showHdrBadge,
            showMotion = showMotionBadge
        )
        
        if (badges.isNotEmpty() && !isSelectionMode) {
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
        
        // 设置封面菜单（非多选模式）
        if (showCoverMenu && onSetCover != null && !isSelectionMode) {
            DropdownMenu(
                expanded = showCoverMenu,
                onDismissRequest = { showCoverMenu = false },
                modifier = Modifier
                    .background(
                        color = Color(0xFF2C2C2E),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                DropdownMenuItem(
                    text = { 
                        Text(
                            "设为相册封面",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
                    onClick = {
                        showCoverMenu = false
                        onSetCover()
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private class AlbumLayoutCoordinatesHolder(var value: LayoutCoordinates? = null)

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

/**
 * 图集选择弹窗
 */
@Composable
private fun AlbumPickerDialog(
    albums: List<Album>,
    allImages: List<ImageFile>,
    title: String = "移动到图集",
    onAlbumSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val imageLoader = remember { CoilSetup.getImageLoader(context) }
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = Color(0xFF8E8E93)
    
    // 每个图集项高度约 72dp，最多显示 3.5 个（可以看到下面还有内容）
    val maxListHeight = 72.dp * 3.5f
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // 获取导航栏高度
            val navBarInsets = WindowInsets.navigationBars
            val navBarHeight = with(LocalDensity.current) { navBarInsets.getBottom(this).toDp() }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(backgroundColor)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* 阻止点击穿透 */ }
                    .padding(bottom = navBarHeight)  // 底部留出导航栏空间
            ) {
                // 顶部拖拽指示器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .background(
                                color = Color.Gray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
                
                // 标题
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                
                if (albums.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有其他图集可选择",
                            color = secondaryTextColor,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    // 图集列表 - 最多显示约 3.5 个
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxListHeight)
                    ) {
                        items(albums.size) { index ->
                            val album = albums[index]
                            // 查找封面图片
                            val coverImage = album.coverImageId?.let { coverId ->
                                allImages.find { it.id == coverId }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAlbumSelected(album.id)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 封面图片或 Emoji
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            color = if (album.color != null) 
                                                Color(album.color).copy(alpha = 0.2f) 
                                            else Color(0xFF007AFF).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (coverImage != null) {
                                        // 显示封面图片
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(coverImage.uri)
                                                .size(coil.size.Size(144, 144))
                                                .precision(Precision.INEXACT)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = album.name,
                                            imageLoader = imageLoader,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // 显示 Emoji 或默认图标
                                        Text(
                                            text = album.emoji ?: "📁",
                                            fontSize = 24.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(14.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.name,
                                        color = textColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${album.imageCount} 张照片",
                                        color = secondaryTextColor,
                                        fontSize = 13.sp
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(scaleX = -1f, scaleY = 1f)  // 翻转成向右箭头
                                )
                            }
                        }
                    }
                }
                
                // 取消按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消",
                        color = Color(0xFF007AFF),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 通过 URI 从 MediaStore 查询图片信息
 * 用于加载已清理原图后同步到系统相册的图片
 */
private fun queryImageFromUri(
    contentResolver: android.content.ContentResolver,
    uri: android.net.Uri
): ImageFile? {
    return try {
        val projection = arrayOf(
            android.provider.MediaStore.Images.Media._ID,
            android.provider.MediaStore.Images.Media.DISPLAY_NAME,
            android.provider.MediaStore.Images.Media.DATE_MODIFIED,
            android.provider.MediaStore.Images.Media.SIZE,
            android.provider.MediaStore.Images.Media.WIDTH,
            android.provider.MediaStore.Images.Media.HEIGHT,
            android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            android.provider.MediaStore.Images.Media.ORIENTATION
        )
        
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID))
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)) ?: "Unknown"
                val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_MODIFIED))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.SIZE))
                val width = cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.WIDTH))
                val height = cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.HEIGHT))
                val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                val orientation = cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.ORIENTATION))
                
                ImageFile(
                    id = id,
                    uri = uri,
                    displayName = displayName,
                    dateModified = dateModified,
                    size = size,
                    width = width,
                    height = height,
                    bucketDisplayName = bucketName,
                    orientation = orientation
                )
            } else null
        }
    } catch (e: Exception) {
        android.util.Log.w("AlbumViewScreen", "Failed to query image from URI: $uri", e)
        null
    }
}

