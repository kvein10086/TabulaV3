package com.tabula.v3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.ui.components.TagPosition
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.SwipeStyle
import com.tabula.v3.data.preferences.TagSelectionMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.data.repository.LocalImageRepository
import androidx.compose.ui.geometry.Offset
import com.tabula.v3.ui.components.ActionIconButton
import com.tabula.v3.ui.components.AlbumDropTarget
import com.tabula.v3.ui.components.TAGS_PER_ROW
import com.tabula.v3.ui.components.AlbumEditDialog
import com.tabula.v3.ui.components.BatchCompletionScreen
import com.tabula.v3.ui.components.CategorizedAlbumsView
import com.tabula.v3.ui.components.DraggableAlbumsGrid
import com.tabula.v3.ui.components.FixedTagBar
import com.tabula.v3.ui.components.AdaptiveGlass
import com.tabula.v3.ui.components.FrostedGlass
import com.tabula.v3.ui.components.BackdropLiquidGlassConfig
import com.tabula.v3.ui.components.ModeToggle
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.SwipeableCardStack
import com.tabula.v3.ui.components.TopBar
import com.tabula.v3.ui.components.UndoSnackbar
import com.tabula.v3.ui.components.ViewerOverlay
import com.tabula.v3.ui.components.ViewerState
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.components.quickaction.QuickActionButton
import com.tabula.v3.ui.components.quickaction.rememberSafeArea
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Deck 屏幕状态
 */
private enum class DeckState {
    BROWSING,     // 浏览中
    COMPLETED,    // 一组完成
    EMPTY         // 无图片
}

/**
 * 主界面 (Deck Screen) - 卡片堆叠照片清理
 *
 * 核心逻辑：
 * - 从相册随机抽取 batchSize 张作为一组
 * - 滑完一组后显示总结页面
 * - 可选择"再来一组"继续
 *
 * @param allImages 所有图片列表
 * @param batchSize 每组数量
 * @param isLoading 是否正在加载
 * @param topBarDisplayMode 顶部栏显示模式
 * @param onRemove 删除（移到回收站）回调
 * @param onNavigateToTrash 导航到回收站
 * @param onNavigateToSettings 导航到设置
 */
@Composable
fun DeckScreen(
    allImages: List<ImageFile>,
    batchSize: Int,
    isLoading: Boolean,
    topBarDisplayMode: TopBarDisplayMode = TopBarDisplayMode.INDEX,
    onRemove: (ImageFile) -> Unit,
    onKeep: () -> Unit = {},
    onNavigateToTrash: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAlbumDetail: (Album) -> Unit = {},
    onNavigateToAlbums: () -> Unit = {},
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = true,
    motionSoundVolume: Int = 100,
    enableSwipeHaptics: Boolean = true,
    isAdaptiveCardStyle: Boolean = false,
    swipeStyle: SwipeStyle = SwipeStyle.SHUFFLE,
    // 标签收纳模式
    tagSelectionMode: TagSelectionMode = TagSelectionMode.SWIPE_AUTO,
    tagsPerRow: Int = TAGS_PER_ROW,
    tagSwitchSpeed: Float = 1.0f,
    // 推荐算法回调
    getRecommendedBatch: suspend (List<ImageFile>, Int) -> List<ImageFile> = { images, size -> 
        images.shuffled().take(size) 
    },
    // 相册归类相关
    albums: List<Album> = emptyList(),
    systemBuckets: List<LocalImageRepository.SystemBucket> = emptyList(),
    currentImageAlbumIds: Set<String> = emptySet(),
    onAlbumSelect: (imageId: Long, imageUri: String, albumId: String) -> Unit = { _, _, _ -> },
    onCreateAlbum: (name: String, color: Long?, emoji: String?) -> Unit = { _, _, _ -> },
    // 新建图集并归档图片（用于下滑归类时选择"新建"的场景）
    onCreateAlbumAndClassify: (name: String, color: Long?, emoji: String?, ImageFile) -> Unit = { _, _, _, _ -> },
    onUndoAlbumAction: () -> Unit = {},
    onCommitArchive: () -> Unit = {},  // Snackbar 消失时提交归档
    onReorderAlbums: (List<String>) -> Unit = {},
    onSystemBucketClick: (String) -> Unit = {},
    onSyncClick: () -> Unit = {},
    lastAlbumActionName: String? = null,
    // 模式切换
    isAlbumMode: Boolean = false,
    onModeChange: (Boolean) -> Unit = {},
    // 清理所有图集原图
    onCleanupAllAlbums: (() -> Unit)? = null,
    // 流体云：批次剩余数量回调
    onBatchRemainingChange: (Int) -> Unit = {},
    // 快捷操作按钮
    quickActionEnabled: Boolean = false,
    leftHandButtonX: Float = 0.85f,
    leftHandButtonY: Float = 0.55f,
    onQuickActionPositionChanged: (isLeftHand: Boolean, x: Float, y: Float) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 背景颜色 - 纯白/纯黑，液态玻璃效果只在卡片上
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)

    // ========== 批次状态 ==========
    var currentBatch by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var markedCount by remember { mutableIntStateOf(0) }
    var deckState by remember { mutableStateOf(DeckState.BROWSING) }

    // ========== 查看器状态 ==========
    var viewerState by remember { mutableStateOf<ViewerState?>(null) }

    // ========== 相册归类状态 ==========
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var showUndoSnackbar by remember { mutableStateOf(false) }
    var undoMessage by remember { mutableStateOf("") }
    
    // 撤销所需的状态：记住最后一次归档操作的图片和位置
    var lastClassifiedImage by remember { mutableStateOf<ImageFile?>(null) }
    var lastClassifiedIndex by remember { mutableIntStateOf(-1) }
    var lastClassifyWasSwipe by remember { mutableStateOf(false) }  // 是否是下滑归类
    
    // 下滑归类模式状态（用于隐藏底部切换按钮）
    var isClassifyMode by remember { mutableStateOf(false) }
    
    // 等待归档的图片（用于新建图集时自动归档）
    var pendingClassifyImage by remember { mutableStateOf<ImageFile?>(null) }

    // 用于异步初始化批次
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var needsInitialBatch by remember { mutableStateOf(true) }
    
    // ========== 快捷操作按钮状态 ==========
    val safeArea = rememberSafeArea()
    
    // 加载下一组的状态（用于显示加载指示器）
    var isLoadingNextBatch by remember { mutableStateOf(false) }
    
    // 监听批次剩余数量变化，通知流体云
    androidx.compose.runtime.LaunchedEffect(currentBatch, currentIndex, deckState) {
        val remaining = if (deckState == DeckState.BROWSING && currentBatch.isNotEmpty()) {
            (currentBatch.size - currentIndex - 1).coerceAtLeast(0)
        } else {
            0
        }
        onBatchRemainingChange(remaining)
    }

    // 异步初始化批次
    androidx.compose.runtime.LaunchedEffect(allImages, isLoading, needsInitialBatch) {
        android.util.Log.d("DeckScreen", "LaunchedEffect: allImages.size=${allImages.size}, isLoading=$isLoading, needsInitialBatch=$needsInitialBatch, currentBatch.isEmpty=${currentBatch.isEmpty()}")
        if (currentBatch.isEmpty() && allImages.isNotEmpty() && !isLoading && needsInitialBatch) {
            android.util.Log.d("DeckScreen", "Starting batch fetch...")
            try {
                val newBatch = getRecommendedBatch(allImages, batchSize)
                android.util.Log.d("DeckScreen", "Got batch: size=${newBatch.size}")
                
                // 只有成功获取后才设置 needsInitialBatch = false
                needsInitialBatch = false
                
                if (newBatch.isNotEmpty()) {
                    currentBatch = newBatch
                    currentIndex = 0
                    markedCount = 0
                    deckState = DeckState.BROWSING
                } else {
                    // 如果推荐结果为空（例如都在冷却中），直接显示完成状态
                    deckState = DeckState.COMPLETED
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消（例如组件重新创建），不做处理，让下次LaunchedEffect重试
                android.util.Log.d("DeckScreen", "Batch fetch cancelled, will retry")
            } catch (e: Exception) {
                android.util.Log.e("DeckScreen", "Error fetching batch", e)
                needsInitialBatch = false  // 出错时也设置，避免无限重试
            }
        }
    }

    // 开始新一组
    fun startNewBatch() {
        if (isLoadingNextBatch) return  // 防止重复点击
        
        scope.launch {
            isLoadingNextBatch = true
            try {
                val newBatch = getRecommendedBatch(allImages, batchSize)
                if (newBatch.isNotEmpty()) {
                    currentBatch = newBatch
                    currentIndex = 0
                    markedCount = 0
                    deckState = DeckState.BROWSING
                } else {
                    deckState = DeckState.COMPLETED
                }
            } finally {
                isLoadingNextBatch = false
            }
        }
    }

    // 处理返回键（关闭查看器）
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        AnimatedContent(
            targetState = when {
                isLoading -> "loading"
                isAlbumMode -> "albums"  // 图集模式
                allImages.isEmpty() -> "empty"
                deckState == DeckState.COMPLETED -> "completed"
                currentBatch.isEmpty() -> "loading" // 等待批次加载
                else -> "browsing"
            },
            transitionSpec = {
                // 从 completed 切换到 browsing 时使用更快的动画，减少闪烁感
                when {
                    initialState == "completed" && targetState == "browsing" -> {
                        // 快速切换：新页面立即显示，旧页面快速淡出
                        fadeIn(animationSpec = tween(150)) togetherWith 
                            fadeOut(animationSpec = tween(100))
                    }
                    else -> {
                        fadeIn(animationSpec = tween(300)) togetherWith 
                            fadeOut(animationSpec = tween(300))
                    }
                }
            },
            label = "deck_state",
            modifier = Modifier.fillMaxSize()
        ) { state ->
            when (state) {
                "loading" -> LoadingState()
                "empty" -> EmptyState()
                "completed" -> {
                    BatchCompletionScreen(
                        totalReviewed = currentBatch.size,
                        totalMarked = markedCount,
                        onContinue = { startNewBatch() },
                        onViewMarked = onNavigateToTrash,
                        isLoading = isLoadingNextBatch
                    )
                }
                "albums" -> {
                    // 图集模式 - 显示相册列表
                    AlbumsGridContent(
                        albums = albums,
                        systemBuckets = systemBuckets,
                        allImages = allImages,
                        onCreateAlbum = onCreateAlbum,
                        onAlbumClick = onNavigateToAlbumDetail,
                        onSystemBucketClick = onSystemBucketClick,
                        onReorderAlbums = onReorderAlbums,
                        onSyncClick = onSyncClick,
                        onSettingsClick = onNavigateToSettings,
                        onCleanupAllAlbums = onCleanupAllAlbums
                    )
                }
                else -> {
                    // 浏览模式
                    val currentImage = currentBatch.getOrNull(currentIndex)
                    
                    DeckContent(
                        images = currentBatch,
                        currentIndex = currentIndex,
                        topBarDisplayMode = topBarDisplayMode,
                        showHdrBadges = showHdrBadges,
                        showMotionBadges = showMotionBadges,
                        enableSwipeHaptics = enableSwipeHaptics,
                        isAdaptiveCardStyle = isAdaptiveCardStyle,
                        swipeStyle = swipeStyle,
                        tagSelectionMode = tagSelectionMode,
                        tagsPerRow = tagsPerRow,
                        tagSwitchSpeed = tagSwitchSpeed,
                        albums = albums,
                        currentImageAlbumIds = currentImageAlbumIds,
                        onIndexChange = { newIndex ->
                            // 如果向后滑超过最后一张，进入完成页面
                            if (newIndex >= currentBatch.size) {
                                // 不额外触发振动，CardStack 滑动时已触发过
                                deckState = DeckState.COMPLETED
                            } else {
                                if (newIndex > currentIndex) {
                                    onKeep()
                                }
                                // 防止空列表时 lastIndex 为 -1 导致 coerceIn 崩溃
                                currentIndex = newIndex.coerceIn(0, currentBatch.lastIndex.coerceAtLeast(0))
                            }
                        },
                        onRemove = { image ->
                            onRemove(image)
                            markedCount++
                            // 删除后如果没有剩余，进入完成页面
                            val newBatch = currentBatch.toMutableList().apply { remove(image) }
                            currentBatch = newBatch
                            if (currentIndex >= newBatch.size && newBatch.isNotEmpty()) {
                                currentIndex = newBatch.lastIndex
                            } else if (newBatch.isEmpty()) {
                                if (enableSwipeHaptics) {
                                    HapticFeedback.doubleTap(context)
                                }
                                deckState = DeckState.COMPLETED
                            }
                        },
                        onCardClick = { image, sourceRect ->
                            viewerState = ViewerState(image, sourceRect)
                        },
                        onTrashClick = onNavigateToTrash,
                        onSettingsClick = onNavigateToSettings,
                        onAlbumClick = { album ->
                            currentImage?.let { image ->
                                // 记录撤销信息
                                lastClassifiedImage = image
                                lastClassifiedIndex = currentIndex
                                lastClassifyWasSwipe = false
                                
                                onAlbumSelect(image.id, image.uri.toString(), album.id)
                                undoMessage = "已添加到「${album.name}」"
                                showUndoSnackbar = true
                                // 自动进入下一张
                                if (currentIndex < currentBatch.lastIndex) {
                                    currentIndex++
                                    onKeep()
                                }
                            }
                        },
                        onAddAlbumClick = { image ->
                            pendingClassifyImage = image  // 保存待归档的图片
                            showCreateAlbumDialog = true
                        },
                        onAlbumsClick = onNavigateToAlbums,
                        isAlbumMode = isAlbumMode,
                        onModeChange = onModeChange,
                        // 下滑归类专用回调：使用回调传入的 image 而不是 currentImage
                        // 因为 Genie 动画的 onComplete 是异步的，currentImage 可能已经变化
                        onSwipeClassifyToAlbum = { image, album ->
                            // 记录撤销信息（在移除前记录索引位置）
                            val imageIndex = currentBatch.indexOf(image)
                            lastClassifiedImage = image
                            lastClassifiedIndex = imageIndex
                            lastClassifyWasSwipe = true
                            
                            onAlbumSelect(image.id, image.uri.toString(), album.id)
                            undoMessage = "已添加到「${album.name}」"
                            showUndoSnackbar = true
                            
                            // 从当前批次中移除已归类的图片
                            // 因为 Genie 动画已经播放了图片"飞走"的效果，用户预期图片已被处理
                            val newBatch = currentBatch.toMutableList().apply { remove(image) }
                            currentBatch = newBatch
                            
                            if (newBatch.isEmpty()) {
                                // 如果没有剩余图片，进入完成页面
                                if (enableSwipeHaptics) {
                                    HapticFeedback.doubleTap(context)
                                }
                                deckState = DeckState.COMPLETED
                            } else if (currentIndex >= newBatch.size) {
                                // 如果当前索引超出范围，调整到最后一张
                                currentIndex = newBatch.lastIndex
                            }
                            // 不需要增加 currentIndex，因为图片已被移除，原来的下一张会自动变成当前位置的图片
                        },
                        // 下滑归类模式状态回调
                        onClassifyModeChange = { mode ->
                            isClassifyMode = mode
                        }
                    )
                }
            }
        }

        // 底部模式切换器 (悬浮) - 在所有模式下都显示，但下滑归类时隐藏
        // 移到顶层，确保在图片模式和图集模式下都可见
        androidx.compose.animation.AnimatedVisibility(
            visible = !isLoading && allImages.isNotEmpty() && !isClassifyMode && deckState != DeckState.COMPLETED,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color.Transparent)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                ModeToggle(
                    isAlbumMode = isAlbumMode,
                    onModeChange = onModeChange
                )
            }
        }

        // ========== 快捷操作按钮 ==========
        QuickActionButton(
            visible = quickActionEnabled && 
                !isLoading && 
                currentBatch.isNotEmpty() && 
                !isClassifyMode && 
                !isAlbumMode &&
                deckState == DeckState.BROWSING &&
                viewerState == null,
            hasPrevious = currentIndex > 0,
            hasNext = currentIndex < currentBatch.lastIndex,
            positionX = leftHandButtonX,  // 使用统一的位置设置
            positionY = leftHandButtonY,
            safeArea = safeArea,
            onPrevious = {
                if (currentIndex > 0) {
                    currentIndex--
                }
            },
            onNext = {
                if (currentIndex < currentBatch.lastIndex) {
                    onKeep()
                    currentIndex++
                }
            },
            onPositionChanged = { x, y ->
                onQuickActionPositionChanged(true, x, y)
            }
        )

        // 查看器覆盖层

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

        // 撤销 Snackbar
        UndoSnackbar(
            visible = showUndoSnackbar,
            message = undoMessage,
            onUndo = {
                // 执行数据层撤销（取消待执行的归档操作）
                onUndoAlbumAction()
                
                // 恢复 UI 状态
                lastClassifiedImage?.let { image ->
                    if (lastClassifyWasSwipe) {
                        // 下滑归类：将图片重新插入 batch
                        val newBatch = currentBatch.toMutableList()
                        val insertIndex = lastClassifiedIndex.coerceIn(0, newBatch.size)
                        newBatch.add(insertIndex, image)
                        currentBatch = newBatch
                        currentIndex = insertIndex
                        // 如果之前进入了完成状态，恢复浏览状态
                        if (deckState == DeckState.COMPLETED) {
                            deckState = DeckState.BROWSING
                        }
                    } else {
                        // 点击 Chip 归类：回退索引
                        if (currentIndex > 0 && currentIndex > lastClassifiedIndex) {
                            currentIndex = lastClassifiedIndex
                        }
                    }
                }
                
                // 清理撤销状态
                lastClassifiedImage = null
                lastClassifiedIndex = -1
                showUndoSnackbar = false
            },
            onDismiss = { 
                // Snackbar 超时消失 = 用户确认归档
                // 执行真正的复制操作
                onCommitArchive()
                
                // 清理撤销状态
                lastClassifiedImage = null
                lastClassifiedIndex = -1
                showUndoSnackbar = false 
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 新建相册对话框
    if (showCreateAlbumDialog) {
        AlbumEditDialog(
            isEdit = false,
            existingAlbumNames = albums.map { it.name },
            onConfirm = { name, color, emoji ->
                val pending = pendingClassifyImage
                if (pending != null) {
                    // 有待归档的图片，创建图集后自动归档
                    onCreateAlbumAndClassify(name, color, emoji, pending)
                    pendingClassifyImage = null
                    
                    // 显示成功提示
                    undoMessage = "已添加到「$name」"
                    showUndoSnackbar = true
                    
                    // 移除当前图片并切换到下一张
                    val imageIndex = currentBatch.indexOf(pending)
                    lastClassifiedImage = pending
                    lastClassifiedIndex = imageIndex
                    lastClassifyWasSwipe = true
                    
                    if (imageIndex >= 0) {
                        currentBatch = currentBatch.toMutableList().also { it.removeAt(imageIndex) }
                        if (currentIndex >= currentBatch.size && currentIndex > 0) {
                            currentIndex = currentBatch.size - 1
                        }
                    }
                } else {
                    // 普通新建图集
                    onCreateAlbum(name, color, emoji)
                }
                showCreateAlbumDialog = false
            },
            onDismiss = { 
                showCreateAlbumDialog = false
                pendingClassifyImage = null  // 取消时清除待归档图片
            }
        )
    }
}

/**
 * Deck 内容布局
 */
@Composable
private fun DeckContent(
    images: List<ImageFile>,
    currentIndex: Int,
    topBarDisplayMode: TopBarDisplayMode,
    showHdrBadges: Boolean,
    showMotionBadges: Boolean,
    enableSwipeHaptics: Boolean,
    isAdaptiveCardStyle: Boolean,
    swipeStyle: SwipeStyle,
    tagSelectionMode: TagSelectionMode = TagSelectionMode.SWIPE_AUTO,
    tagsPerRow: Int = TAGS_PER_ROW,
    tagSwitchSpeed: Float = 1.0f,
    albums: List<Album>,
    currentImageAlbumIds: Set<String>,
    onIndexChange: (Int) -> Unit,
    onRemove: (ImageFile) -> Unit,
    onCardClick: (ImageFile, SourceRect) -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAddAlbumClick: (ImageFile?) -> Unit,  // 可选参数：待归档的图片
    onAlbumsClick: () -> Unit,
    isAlbumMode: Boolean = false,
    onModeChange: (Boolean) -> Unit = {},
    // 下滑归类专用回调（携带图片信息，避免异步回调时 currentImage 变化的问题）
    onSwipeClassifyToAlbum: (ImageFile, Album) -> Unit = { _, album -> onAlbumClick(album) },
    // 下滑归类模式状态回调
    onClassifyModeChange: (Boolean) -> Unit = {},
    // 固定标签点击模式的归类回调
    onFixedTagAlbumClick: (Album) -> Unit = onAlbumClick
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < images.lastIndex
    val remaining = (images.size - currentIndex - 1).coerceAtLeast(0)
    val currentImage = images.getOrNull(currentIndex)
    
    // 下滑归类模式状态
    var isClassifyMode by remember { mutableStateOf(false) }
    // 默认选中第一个相册（索引1），因为索引0是新建按钮
    var selectedAlbumIndex by remember(albums) { mutableIntStateOf(if (albums.isNotEmpty()) 1 else 0) }
    
    // 标签位置映射（索引 -> TagPosition）
    var tagPositions by remember { mutableStateOf<Map<Int, TagPosition>>(emptyMap()) }
    
    // 回收站按钮位置（用于上滑删除的 Genie 动画目标点）
    var trashButtonBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    
    // 固定标签点击模式：触发 Genie 动画的目标索引和相册
    var fixedTagTriggerIndex by remember { mutableStateOf<Int?>(null) }
    var pendingFixedTagAlbum by remember { mutableStateOf<Album?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部栏（显示时间或索引）
            TopBar(
                currentIndex = currentIndex,
                totalCount = images.size,
                currentImage = currentImage,
                displayMode = topBarDisplayMode,
                onTrashClick = onTrashClick,
                onSettingsClick = onSettingsClick,
                onTrashButtonBoundsChanged = { bounds ->
                    trashButtonBounds = bounds
                }
            )

            // 卡片堆叠区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp),  // 卡片向下移动
                contentAlignment = Alignment.Center
            ) {
                if (images.isNotEmpty() && currentIndex < images.size) {
                    SwipeableCardStack(
                        images = images,
                        currentIndex = currentIndex,
                        onIndexChange = onIndexChange,
                        onRemove = onRemove,
                        onCardClick = onCardClick,
                        showHdrBadges = showHdrBadges,
                        showMotionBadges = showMotionBadges,
                        enableSwipeHaptics = enableSwipeHaptics,
                        modifier = Modifier.fillMaxSize(),
                        isAdaptiveCardStyle = isAdaptiveCardStyle,
                        swipeStyle = swipeStyle,
                        // 下滑归类相关参数（固定标签点击模式下禁用下滑手势）
                        enableDownSwipeClassify = tagSelectionMode == TagSelectionMode.SWIPE_AUTO,
                        albums = albums,
                        onClassifyToAlbum = { image, album ->
                            // 将图片添加到图集（使用新的下滑归类回调）
                            onSwipeClassifyToAlbum(image, album)
                        },
                        onCreateNewAlbum = { image ->
                            // 打开新建图集对话框，并传递待归档的图片
                            onAddAlbumClick(image)
                        },
                        onClassifyModeChange = { mode ->
                            isClassifyMode = mode
                            onClassifyModeChange(mode)  // 同步到外部
                            if (mode) {
                                // 进入归类模式时，默认选中第一个标签（新建后面的）
                                selectedAlbumIndex = if (albums.isNotEmpty()) 1 else 0
                            }
                        },
                        onSelectedIndexChange = { index ->
                            selectedAlbumIndex = index
                        },
                        // 传递标签位置
                        tagPositions = tagPositions,
                        // 每行标签数量和切换速度
                        tagsPerRow = tagsPerRow,
                        tagSwitchSpeed = tagSwitchSpeed,
                        // 传递回收站按钮位置（用于上滑删除的 Genie 动画）
                        trashButtonBounds = trashButtonBounds,
                        // 固定标签点击模式：外部触发 Genie 动画
                        fixedTagTriggerIndex = fixedTagTriggerIndex,
                        onFixedTagAnimationComplete = {
                            // 动画完成后，执行归类回调
                            pendingFixedTagAlbum?.let { album ->
                                currentImage?.let { image ->
                                    onSwipeClassifyToAlbum(image, album)
                                }
                            }
                            // 重置状态
                            fixedTagTriggerIndex = null
                            pendingFixedTagAlbum = null
                        }
                    )
                }
            }

            // 底部固定高度区域 - 保持布局稳定，避免下滑时整体移动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp) // 固定高度
            ) {
                // ========== 模式一：下滑自动选择 ==========
                if (tagSelectionMode == TagSelectionMode.SWIPE_AUTO) {
                    // 正常模式：显示剩余提示
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isClassifyMode,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${remaining} 张待整理",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 2.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = textColor.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }

                            // 底部留白，为模式切换按钮预留空间
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                    
                    // 归类模式：显示标签选择器
                    // 只在归类模式下渲染 AlbumDropTarget，避免 FrostedGlass 在 alpha=0 时的视觉残留
                    if (isClassifyMode) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(200)
                            ),
                            exit = androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(200)
                            )
                        ) {
                        AlbumDropTarget(
                            albums = albums,
                            selectedIndex = selectedAlbumIndex,
                            onTagPositionChanged = { index, tagPosition ->
                                // 更新标签位置映射
                                tagPositions = tagPositions + (index to tagPosition)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            tagsPerRow = tagsPerRow
                        )
                        }
                    }
                }
                
                // ========== 模式二：固定标签点击 ==========
                if (tagSelectionMode == TagSelectionMode.FIXED_TAP) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 剩余提示（减少间距让标签往上）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${remaining} 张待整理",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                        
                        // 固定标签栏
                        FixedTagBar(
                            albums = albums,
                            onAlbumClick = { album ->
                                currentImage?.let { image ->
                                    // 找到相册在 albums 列表中的索引（+1 因为索引0是新建按钮）
                                    val albumIndex = albums.indexOf(album) + 1
                                    // 设置待归类的相册和触发索引
                                    pendingFixedTagAlbum = album
                                    fixedTagTriggerIndex = albumIndex
                                }
                            },
                            onCreateNewAlbumClick = {
                                onAddAlbumClick(currentImage)
                            },
                            onTagPositionChanged = { index, tagPosition ->
                                // 更新标签位置映射（用于 Genie 动画目标点）
                                tagPositions = tagPositions + (index to tagPosition)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // 底部留白，为模式切换按钮预留空间
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        // 底部模式切换器已移至 DeckScreen 顶层，确保在图片和图集模式下都可见
    }
}

/**
 * 加载状态
 */
@Composable
private fun LoadingState() {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Tabula Logo",
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = textColor,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在加载照片...",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState() {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Tabula Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "没有找到照片",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请确保已授权访问相册",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}



/**
 * 图集网格内容
 * 
 * 显示用户创建的图集列表，支持毛玻璃导航栏效果。
 */
@Composable
private fun AlbumsGridContent(
    albums: List<Album>,
    systemBuckets: List<LocalImageRepository.SystemBucket> = emptyList(),
    allImages: List<ImageFile>,
    onCreateAlbum: (String, Long?, String?) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSystemBucketClick: (String) -> Unit = {},
    onReorderAlbums: (List<String>) -> Unit = {},
    onSyncClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onCleanupAllAlbums: (() -> Unit)? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    var showCreateDialog by remember { mutableStateOf(false) }
    var showCleanupAllConfirm by remember { mutableStateOf(false) }
    
    // 简化后只显示 App 图集，移除了 Tab 切换功能

    // 内容层的滚动状态
    val listState = rememberLazyListState()
    var topBarHeight by remember { mutableStateOf(0.dp) }
    
    // 移除延迟渲染：之前的 100ms 延迟会导致切换时先显示空白再显示内容，造成卡顿感
    // 现在直接渲染内容，配合 AnimatedContent 的渐变动画即可平滑过渡
    
    // 滚动折叠阈值
    val collapseThreshold = 40.dp
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { collapseThreshold.toPx() }
    val blurRadius = 40.dp  // 增加模糊半径，实现更柔和的毛玻璃效果
    
    // 计算滚动偏移量（用于导航栏折叠动画）
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                collapseThresholdPx // 超过第一项时完全折叠
            } else {
                listState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(collapseThresholdPx)
            }
        }
    }
    
    val collapsedFraction by remember {
        derivedStateOf { scrollOffset / collapseThresholdPx }
    }
    
    // ========== 厚实毛玻璃质感参数 ==========
    // 材质厚度（Opacity）：模拟 iOS 键盘的实体感
    // - 浅色模式：0.70f，色值 Color(0xFFF5F5F7)
    // - 深色模式：0.60f
    val glassAlpha by animateFloatAsState(
        targetValue = if (collapsedFraction > 0.15f) {
            if (isDarkTheme) 0.65f else 0.75f  // 滚动后略微增强
        } else {
            if (isDarkTheme) 0.60f else 0.70f  // 基础厚度
        },
        label = "TopBarGlassAlpha"
    )

    val topBarVisibleHeight = if (topBarHeight > 0.dp) {
        topBarHeight
    } else {
        // 默认高度，状态栏+标题栏
        100.dp
    }

    // 顶部内容间距
    val contentTopPadding = topBarVisibleHeight + 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. Blurred backdrop (top bar region only)
        // 使用简化的纯色模糊背景，不再渲染复杂的 CategorizedAlbumsView
        // 原因：DraggableAlbumsGridInternal 组件太复杂（拖拽、动画），双重渲染会导致跳帧
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topBarVisibleHeight)
                .clipToBounds()
                .background(
                    if (isDarkTheme) Color(0xFF1C1C1E).copy(alpha = 0.8f)
                    else Color.White.copy(alpha = 0.8f)
                )
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        )

        // 2. Content area (base)
        // 直接渲染内容，AnimatedContent 的渐变动画会处理过渡效果
        CategorizedAlbumsView(
                appAlbums = albums,
                systemBuckets = null,  // 简化：只显示 App 图集
                allImages = allImages,
                onAppAlbumClick = onAlbumClick,
                onSystemBucketClick = onSystemBucketClick,
                onReorderAlbums = onReorderAlbums,
                onCreateAlbumClick = { showCreateDialog = true },
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                isDarkTheme = isDarkTheme,
                hideHeaders = true,  // 只有 App 图集，不需要分类标题
                listState = listState,
                topPadding = contentTopPadding,
                headerContent = null,
                userScrollEnabled = true
            )

        // ========== 3. 浮动毛玻璃导航栏 ==========
        // 简洁设计：从下到上的渐变 + 磨砂模糊质感
        val frostedGradient = if (isDarkTheme) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1C1C1E).copy(alpha = 0.50f),  // 顶部：较透
                    Color(0xFF1C1C1E).copy(alpha = 0.75f)   // 底部：较实
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.60f),  // 顶部：较透
                    Color.White.copy(alpha = 0.85f)  // 底部：较实
                )
            )
        }

        AdaptiveGlass(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    topBarHeight = with(density) { size.height.toDp() }
                },
            shape = RoundedCornerShape(0.dp),
            blurRadius = 40.dp,  // 磨砂模糊
            tintGradient = frostedGradient,  // 从下到上渐变
            noiseAlpha = if (isDarkTheme) 0.02f else 0.03f,  // 轻微噪点增加质感
            backdropConfig = BackdropLiquidGlassConfig.Bar.copy(cornerRadius = 0.dp),  // Backdrop 液态玻璃配置
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                // 顶部栏内容 - 与照片界面保持一致的padding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                ) {
                    // 标题
                    Text(
                        text = "图集",
                        color = textColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    
                    // 右侧按钮组
                    val buttonBgColor = if (isDarkTheme) TabulaColors.CatBlackLight else Color.White
                    val buttonIconColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
                    
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 清理旧图按钮（仅当有图集且有回调时显示）
                        if (onCleanupAllAlbums != null && albums.isNotEmpty()) {
                            ActionIconButton(
                                icon = Icons.Outlined.Delete,
                                contentDescription = "清理旧图",
                                onClick = {
                                    com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                    showCleanupAllConfirm = true
                                },
                                backgroundColor = buttonBgColor,
                                iconColor = buttonIconColor
                            )
                        }
                        
                        // 设置按钮
                        ActionIconButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = "设置",
                            onClick = {
                                com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                onSettingsClick()
                            },
                            backgroundColor = buttonBgColor,
                            iconColor = buttonIconColor
                        )
                    }
                }
            }
        }
    }
    
    // 清理所有图集旧图确认对话框
    if (showCleanupAllConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCleanupAllConfirm = false },
            containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White,
            title = {
                Text(
                    text = "清理所有旧图",
                    color = if (isDarkTheme) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "将删除所有图集中图片在旧位置的文件（包括原图和移动残留）。\n\n当前图集中的图片不受影响。已清理过的会自动跳过。",
                    color = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onCleanupAllAlbums?.invoke()
                        showCleanupAllConfirm = false
                        com.tabula.v3.ui.util.HapticFeedback.mediumTap(context)
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
                androidx.compose.material3.TextButton(onClick = { showCleanupAllConfirm = false }) {
                    Text(
                        text = "取消",
                        color = Color(0xFF007AFF)
                    )
                }
            }
        )
    }

    // 新建图集对话框
    if (showCreateDialog) {
        AlbumEditDialog(
            existingAlbumNames = albums.map { it.name },
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color, emoji ->
                onCreateAlbum(name, color, emoji)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    allImages: List<ImageFile>,
    onAlbumClick: (Album) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    
    // 建立 ID 到 ImageFile 的映射，加速查找
    val imageMap = remember(allImages) { allImages.associateBy { it.id } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 提示文字
        item(span = { GridItemSpan(2) }) {
            Text(
                text = "点击查看详情",
                color = secondaryTextColor,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(albums) { album ->
            // 尝试获取封面图片，如果在 imageMap 中找不到则用 coverImageId 构建 URI
            val coverImage = album.coverImageId?.let { imageMap[it] }
            val coverUri = coverImage?.uri 
                ?: album.coverImageId?.let { android.net.Uri.parse("content://media/external/images/media/$it") }
            
            // 跟踪图片加载状态
            var loadFailed by remember { mutableStateOf(false) }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { 
                        com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                        onAlbumClick(album) 
                    }
            ) {
                // 封面图容器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // 正方形
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            color = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
                        )
                ) {
                   if (coverUri != null && !loadFailed) {
                       AsyncImage(
                           model = ImageRequest.Builder(context)
                               .data(coverUri)
                               .crossfade(true)
                               .build(),
                           contentDescription = null,
                           contentScale = ContentScale.Crop,
                           modifier = Modifier.fillMaxSize(),
                           onError = { loadFailed = true }
                       )
                   } else {
                       // 没有图片或加载失败时的背景色
                       val albumColor = album.color?.let { Color(it) } ?: Color(0xFF7986CB)
                       Box(
                           modifier = Modifier
                               .fillMaxSize()
                               .background(albumColor.copy(alpha = 0.2f)),
                           contentAlignment = Alignment.Center
                       ) {
                           if (album.emoji != null) {
                               Text(
                                   text = album.emoji,
                                   fontSize = 32.sp
                               )
                           } else {
                               Box(
                                   modifier = Modifier
                                       .size(32.dp)
                                       .background(albumColor, RoundedCornerShape(8.dp))
                               )
                           }
                       }
                   }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 标题
                Text(
                    text = album.name,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                // 数量
                Text(
                    text = "${album.imageCount} 张",
                    color = secondaryTextColor,
                    fontSize = 13.sp,
                )
            }
        }
        
        // 底部留白，为了不被 toggle 遮挡
        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
