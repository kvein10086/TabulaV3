package com.tabula.v3

import android.Manifest
import android.graphics.Color as AndroidColor
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.CardStyleMode
import com.tabula.v3.data.preferences.SourceImageDeletionStrategy
import com.tabula.v3.data.preferences.SwipeStyle
import com.tabula.v3.data.preferences.TagSelectionMode
import com.tabula.v3.data.preferences.ThemeMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.data.repository.FileOperationManager
import com.tabula.v3.data.repository.LocalImageRepository
import com.tabula.v3.data.repository.RecycleBinManager
import com.tabula.v3.data.repository.RecommendationEngine
import com.tabula.v3.data.repository.AlbumCleanupEngine
import com.tabula.v3.data.repository.AlbumCleanupInfo
import com.tabula.v3.ui.navigation.AppScreen
import com.tabula.v3.ui.navigation.PredictiveBackContainer
import com.tabula.v3.ui.screens.AboutScreen
import com.tabula.v3.ui.screens.DeckScreen
import com.tabula.v3.ui.screens.RecycleBinScreen
import com.tabula.v3.ui.screens.SettingsScreen
import com.tabula.v3.ui.screens.SupportScreen
import com.tabula.v3.ui.screens.VibrationSoundScreen
import com.tabula.v3.ui.screens.LabScreen
import com.tabula.v3.ui.screens.DisplaySettingsScreen
import com.tabula.v3.ui.screens.ReminderSettingsScreen
import com.tabula.v3.ui.screens.StatisticsScreen
import com.tabula.v3.ui.screens.AlbumViewScreen
import com.tabula.v3.ui.screens.SystemAlbumViewScreen
import com.tabula.v3.ui.screens.OnboardingScreen
import com.tabula.v3.ui.screens.PersonalizationScreen

import com.tabula.v3.ui.screens.PrivacyPolicyScreen
import com.tabula.v3.ui.screens.TutorialScreen
import com.tabula.v3.ui.screens.OtherAlbumsScreen
import com.tabula.v3.data.repository.AlbumManager
import com.tabula.v3.data.model.Album
import androidx.compose.runtime.collectAsState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.screens.AlbumTagManagementScreen
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.theme.TabulaTheme
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.service.FluidCloudService
import android.widget.Toast
import com.tabula.v3.ui.components.isBackdropLiquidGlassSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity - Tabula 应用入口
 *
 * 核心功能：
 * 1. 沉浸式 Edge-to-Edge UI
 * 2. 权限管理（READ_MEDIA_IMAGES）
 * 3. 简单路由管理
 * 4. ColorOS 16 风格预测性返回
 */
class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)
    
    // 流体云：跟踪当前批次剩余数量
    private var currentBatchRemaining = 0
    private var isFluidCloudEnabled = false

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        
        // 权限被拒绝后的处理
        if (!isGranted) {
            // 检查是否应该显示权限说明（如果返回 false，表示用户选择了"不再询问"）
            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            
            val shouldShowRationale = shouldShowRequestPermissionRationale(permission)
            if (!shouldShowRationale) {
                // 用户选择了"不再询问"，提示前往设置开启权限
                Log.w("MainActivity", "Permission permanently denied, user should enable it in settings")
            }
        }
    }

    // 删除权限请求（Android 11+ MediaStore 删除确认）
    private lateinit var deletePermissionLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var onDeletePermissionResult: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 使用完全透明的导航栏样式，实现沉浸式体验
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        checkPermission()

        // 初始化删除权限启动器
        deletePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            onDeletePermissionResult?.invoke(success)
            onDeletePermissionResult = null
        }

        setContent {
            val context = LocalContext.current
            val preferences = remember { AppPreferences(context) }
            var currentTheme by remember { mutableStateOf(preferences.themeMode) }
            
            // 液态玻璃实验室功能（独立于主题）
            var liquidGlassLabEnabled by remember { mutableStateOf(preferences.liquidGlassLabEnabled) }

            // 根据设置决定深色模式
            val darkTheme = when (currentTheme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.LIQUID_GLASS -> false  // 兼容旧数据，降级为 SYSTEM 行为
            }
            
            // 是否启用液态玻璃效果（来自实验室设置，而非主题）
            val liquidGlassEnabled = liquidGlassLabEnabled && android.os.Build.VERSION.SDK_INT >= 35

            TabulaTheme(darkTheme = darkTheme, liquidGlassEnabled = liquidGlassEnabled) {
                if (hasPermission) {
                    TabulaApp(
                        preferences = preferences,
                        onThemeChange = { theme -> currentTheme = theme },
                        onRequestDeletePermission = { intentSender, callback ->
                            onDeletePermissionResult = callback
                            deletePermissionLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        },
                        liquidGlassLabEnabled = liquidGlassLabEnabled,
                        onLiquidGlassLabEnabledChange = { enabled ->
                            liquidGlassLabEnabled = enabled
                            preferences.liquidGlassLabEnabled = enabled
                        }
                    )
                } else {
                    PermissionRequestScreen(
                        onRequestPermission = { requestPermission() }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 返回应用时重新检查权限（用户可能在系统设置中撤销了权限）
        checkPermission()
        // 返回应用时隐藏流体云
        FluidCloudService.hide(this)
    }
    
    override fun onStop() {
        super.onStop()
        // 离开应用时，如果有未完成的批次且流体云已启用，则显示流体云
        if (isFluidCloudEnabled && currentBatchRemaining > 0) {
            FluidCloudService.show(this, currentBatchRemaining)
        }
    }
    
    /**
     * 更新批次剩余数量（供 Composable 调用）
     */
    fun updateBatchRemaining(remaining: Int, fluidCloudEnabled: Boolean) {
        currentBatchRemaining = remaining
        isFluidCloudEnabled = fluidCloudEnabled
    }

    private fun checkPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用细粒度媒体权限
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 12 使用旧权限
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        hasPermission = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用细粒度媒体权限
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 12 使用旧权限
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }
    
}

/**
 * Tabula 应用主体
 *
 * 包含路由管理和预测性返回动画
 */
@Composable
fun TabulaApp(
    preferences: AppPreferences,
    onThemeChange: (ThemeMode) -> Unit,
    onRequestDeletePermission: (android.content.IntentSender, (Boolean) -> Unit) -> Unit,
    liquidGlassLabEnabled: Boolean = false,
    onLiquidGlassLabEnabledChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ========== 引导流程状态 ==========
    // 只在首次启动时显示引导流程
    // 使用 rememberSaveable 保存状态，避免配置变更时丢失
    var showOnboarding by rememberSaveable { mutableStateOf(!preferences.hasCompletedOnboarding) }
    
    // 监听 preferences.hasCompletedOnboarding 变化，保持状态同步
    LaunchedEffect(preferences.hasCompletedOnboarding) {
        if (preferences.hasCompletedOnboarding) {
            showOnboarding = false
        }
    }

    // ========== 设置 ==========
    var currentBatchSize by remember { mutableIntStateOf(preferences.batchSize) }
    var currentTopBarMode by remember { mutableStateOf(preferences.topBarDisplayMode) }
    var showDeleteConfirm by remember { mutableStateOf(preferences.showDeleteConfirm) }
    var showHdrBadges by remember { mutableStateOf(preferences.showHdrBadges) }
    var showMotionBadges by remember { mutableStateOf(preferences.showMotionBadges) }
    var cardStyleMode by remember { mutableStateOf(preferences.cardStyleMode) }
    var swipeStyle by remember { mutableStateOf(preferences.swipeStyle) }
    // 标签收纳设置
    var tagSelectionMode by remember { mutableStateOf(preferences.tagSelectionMode) }
    var tagSwitchSpeed by remember { mutableFloatStateOf(preferences.tagSwitchSpeed) }
    var tagsPerRow by remember { mutableIntStateOf(preferences.tagsPerRow) }
    var playMotionSound by remember { mutableStateOf(preferences.playMotionSound) }
    var motionSoundVolume by remember { mutableIntStateOf(preferences.motionSoundVolume) }
    var hapticEnabled by remember { mutableStateOf(preferences.hapticEnabled) }
    var hapticStrength by remember { mutableIntStateOf(preferences.hapticStrength) }
    var swipeHapticsEnabled by remember { mutableStateOf(preferences.swipeHapticsEnabled) }
    var fluidCloudEnabled by remember { mutableStateOf(preferences.fluidCloudEnabled) }
    
    // ========== 快捷操作按钮状态 ==========
    var quickActionEnabled by remember { mutableStateOf(preferences.quickActionButtonEnabled) }
    var quickActionButtonX by remember { mutableFloatStateOf(preferences.quickActionButtonX) }
    var quickActionButtonY by remember { mutableFloatStateOf(preferences.quickActionButtonY) }

    // ========== 路由状态 ==========
    // 如果需要显示引导，则初始屏幕为 ONBOARDING，否则为 DECK
    var currentScreen by remember { mutableStateOf(if (showOnboarding) AppScreen.ONBOARDING else AppScreen.DECK) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedBucketName by remember { mutableStateOf<String?>(null) }
    var isAlbumMode by remember { mutableStateOf(false) }
    var showHiddenAlbums by remember { mutableStateOf(false) }
    var otherAlbumsForNavigation by remember { mutableStateOf<List<Album>>(emptyList()) }

    // ========== 图片数据状态 ==========
    var allImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    // 用于主界面卡片整理的图片列表（排除已归类到 Tabula 图集的照片）
    // 这样已归类的照片不会再次出现在待整理列表中
    var imagesForSorting by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var deletedImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var systemBuckets by remember { mutableStateOf<List<LocalImageRepository.SystemBucket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 管理器
    val fileOperationManager = remember { FileOperationManager(context) }
    val recycleBinManager = remember { RecycleBinManager.getInstance(context) }
    val albumManager = remember { AlbumManager.getInstance(context) }
    val recommendationEngine = remember { RecommendationEngine.getInstance(context) }
    val albumCleanupEngine = remember { AlbumCleanupEngine.getInstance(context) }
    val albums by albumManager.albums.collectAsState()
    val albumMappings by albumManager.mappings.collectAsState()
    val excludedAlbumCount by albumManager.excludedAlbumCount.collectAsState()
    val hiddenAlbumCount by albumManager.hiddenAlbumCount.collectAsState()
    
    // ========== 图集清理状态 ==========
    var albumCleanupInfos by remember { mutableStateOf<List<AlbumCleanupInfo>>(emptyList()) }
    
    // 图集清理模式状态（提升到 TabulaApp 级别，避免导航时丢失）
    var isAlbumCleanupMode by remember { mutableStateOf(false) }
    var selectedCleanupAlbum by remember { mutableStateOf<Album?>(null) }
    
    // 刷新图集清理信息的回调
    fun refreshAlbumCleanupInfos() {
        albumCleanupInfos = albums.map { album ->
            albumCleanupEngine.getAlbumCleanupInfo(album)
        }
    }
    
    // 当 albums 变化时刷新清理信息
    LaunchedEffect(albums) {
        refreshAlbumCleanupInfos()
    }

    // ========== 同步状态 ==========
    var isSyncing by remember { mutableStateOf(false) }

    // ========== 推荐模式刷新触发器 ==========
    // 用于在设置中切换推荐模式后，返回主页时刷新批次
    var recommendModeRefreshKey by remember { mutableIntStateOf(0) }
    
    // ========== 原图删除提醒状态 ==========
    var showSourceImageDeletionDialog by remember { mutableStateOf(false) }
    var pendingSourceImageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var sourceImageDeletionStrategy by remember { mutableStateOf(preferences.sourceImageDeletionStrategy) }
    
    // 监听图集模式切换，当切换到图集界面时检查是否需要提醒删除原图
    LaunchedEffect(isAlbumMode, sourceImageDeletionStrategy) {
        if (isAlbumMode && sourceImageDeletionStrategy == SourceImageDeletionStrategy.ASK_EVERY_TIME) {
            // 获取所有待删除的原图
            val cleanableResult = albumManager.getCleanableUrisForAllAlbums()
            if (!cleanableResult.isEmpty && cleanableResult.sourceUris.isNotEmpty()) {
                pendingSourceImageUris = cleanableResult.sourceUris
                showSourceImageDeletionDialog = true
            }
        }
    }

    // ========== 各页面的滚动状态（在 TabulaApp 级别保存，导航返回时保持位置） ==========
    val settingsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    // 加载数据
    LaunchedEffect(Unit) {
        val loadedData = withContext(Dispatchers.IO) {
            val repository = LocalImageRepository(context)
            // 单次 MediaStore 查询获取所有数据（优化：原来需要 3+2 次查询）
            val loadResult = repository.loadAllData()
            // 回收站加载（与图集初始化无依赖，可并行）
            val deleted = recycleBinManager.loadRecycleBin()
            // 传入预加载的 bucket 数据，避免 AlbumManager 重复查询 MediaStore
            albumManager.initialize(preloadedBuckets = loadResult.systemBuckets)
            Pair(loadResult, deleted)
        }
        // 在主线程更新状态
        allImages = loadedData.first.allImages
        imagesForSorting = loadedData.first.imagesExcludingTabula
        systemBuckets = loadedData.first.systemBuckets
        deletedImages = loadedData.second
        
        isLoading = false
    }

    /**
     * 执行删除操作
     */
    fun performDelete(image: ImageFile, onResult: (Boolean) -> Unit) {
        scope.launch {
            val result = fileOperationManager.deleteImage(image.uri)
            when (result) {
                is FileOperationManager.DeleteResult.Success -> {
                    val success = result.deleted.isNotEmpty()
                    Log.d("TabulaApp", "Deleted: ${image.displayName}, success=$success")
                    onResult(success)
                }
                is FileOperationManager.DeleteResult.NeedsPermission -> {
                    onRequestDeletePermission(result.intentSender) { granted ->
                        if (granted) {
                            // 授权后验证实际删除结果
                            scope.launch {
                                val actuallyDeleted = fileOperationManager.verifyDeletion(result.pendingUris)
                                val success = actuallyDeleted.isNotEmpty()
                                Log.d("TabulaApp", "Delete permission granted for: ${image.displayName}, verified=$success")
                                onResult(success)
                            }
                        } else {
                            Log.w("TabulaApp", "Delete permission denied for: ${image.displayName}")
                            onResult(false)
                        }
                    }
                }
                is FileOperationManager.DeleteResult.Error -> {
                    Log.e("TabulaApp", "Delete error: ${result.message}")
                    onResult(false)
                }
            }
        }
    }
    
    /**
     * Batch delete
     */
    fun performDeleteBatch(images: List<ImageFile>, onResult: (Boolean) -> Unit) {
        if (images.isEmpty()) {
            onResult(true)
            return
        }
        
        scope.launch {
            val result = fileOperationManager.deleteImages(images.map { it.uri })
            when (result) {
                is FileOperationManager.DeleteResult.Success -> {
                    val success = result.deleted.size == images.size
                    Log.d("TabulaApp", "Deleted batch: ${result.deleted.size}/${images.size}")
                    onResult(success)
                }
                is FileOperationManager.DeleteResult.NeedsPermission -> {
                    onRequestDeletePermission(result.intentSender) { granted ->
                        if (granted) {
                            // 授权后验证实际删除结果
                            scope.launch {
                                val actuallyDeleted = fileOperationManager.verifyDeletion(result.pendingUris)
                                val success = actuallyDeleted.size == images.size
                                Log.d("TabulaApp", "Delete permission granted for batch: ${actuallyDeleted.size}/${images.size}")
                                onResult(success)
                            }
                        } else {
                            Log.w("TabulaApp", "Delete permission denied for batch: ${images.size}")
                            onResult(false)
                        }
                    }
                }
                is FileOperationManager.DeleteResult.Error -> {
                    Log.e("TabulaApp", "Delete error: ${result.message}")
                    onResult(false)
                }
            }
        }
    }
    
    /**
     * 批量删除原图（清理功能）
     * 
     * @param uris 待删除的 URI 列表
     * @param onResult 回调，返回成功删除的 URI 列表
     */
    fun performDeleteUris(uris: List<android.net.Uri>, onResult: (Set<String>) -> Unit) {
        if (uris.isEmpty()) {
            onResult(emptySet())
            return
        }
        
        scope.launch {
            val result = fileOperationManager.deleteImages(uris)
            when (result) {
                is FileOperationManager.DeleteResult.Success -> {
                    val deletedUris = result.deleted.map { it.toString() }.toSet()
                    Log.d("TabulaApp", "Cleanup deleted: ${deletedUris.size}/${uris.size}")
                    onResult(deletedUris)
                }
                is FileOperationManager.DeleteResult.NeedsPermission -> {
                    onRequestDeletePermission(result.intentSender) { granted ->
                        if (granted) {
                            scope.launch {
                                val actuallyDeleted = fileOperationManager.verifyDeletion(result.pendingUris)
                                val deletedUris = actuallyDeleted.map { it.toString() }.toSet()
                                Log.d("TabulaApp", "Cleanup permission granted, deleted: ${deletedUris.size}/${uris.size}")
                                onResult(deletedUris)
                            }
                        } else {
                            Log.w("TabulaApp", "Cleanup permission denied for: ${uris.size} images")
                            onResult(emptySet())
                        }
                    }
                }
                is FileOperationManager.DeleteResult.Error -> {
                    Log.e("TabulaApp", "Cleanup error: ${result.message}")
                    onResult(emptySet())
                }
            }
        }
    }

    // ========== 屏幕内容定义 ==========
    LaunchedEffect(hapticEnabled, hapticStrength) {
        com.tabula.v3.ui.util.HapticFeedback.updateSettings(
            enabled = hapticEnabled,
            strength = hapticStrength
        )
    }

    val deckContent: @Composable () -> Unit = {
        DeckScreen(
            // 使用 imagesForSorting（排除已归类到 Tabula 图集的照片）
            // 这样已归类的照片不会再次出现在待整理列表中
            allImages = imagesForSorting,
            allImagesForCleanup = allImages,
            batchSize = currentBatchSize,
            isLoading = isLoading,
            topBarDisplayMode = currentTopBarMode,
            onKeep = {
                preferences.totalReviewedCount++
            },
            onRemove = { image ->
                val newImages = allImages.toMutableList().apply { remove(image) }
                allImages = newImages
                // 同时从待整理列表中移除
                val newSortingImages = imagesForSorting.toMutableList().apply { remove(image) }
                imagesForSorting = newSortingImages
                deletedImages = deletedImages + image.copy(deletedAt = System.currentTimeMillis())
                
                preferences.totalReviewedCount++
                preferences.totalDeletedCount++
                
                // 清除相似组缓存（图片列表已变化）
                recommendationEngine.invalidateSimilarGroupCache()
                
                scope.launch {
                    recycleBinManager.addToRecycleBin(image)
                }
                Log.d("TabulaApp", "Moved to trash: ${image.displayName}")
            },
            onNavigateToTrash = {
                currentScreen = AppScreen.RECYCLE_BIN
            },
            onNavigateToSettings = {
                currentScreen = AppScreen.SETTINGS
            },
            onNavigateToAlbumDetail = { album ->
                selectedAlbumId = album.id
                currentScreen = AppScreen.ALBUM_VIEW
            },
            albums = albums,
            systemBuckets = systemBuckets,
            onAlbumSelect = { imageId, imageUri, albumId ->
                 scope.launch {
                     // 先提交之前的 pending action（如果有）
                     // 这样快速连续归档时，前一个会被自动提交
                     albumManager.getLastPendingArchive()?.let { 
                         albumManager.commitPendingArchive(it.id)
                     }
                     
                     // 加入新的 pending action（不立即复制）
                     val album = albums.find { it.id == albumId }
                     albumManager.queueImageToAlbum(imageId, imageUri, albumId, album?.name ?: albumId)
                 }
            },
            onCreateAlbum = { name, color, emoji ->
                scope.launch {
                    try {
                        albumManager.createAlbum(name, color, emoji)
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Failed to create album: $name", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "创建图集失败: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onCreateAlbumAndClassify = { name, color, emoji, image ->
                scope.launch {
                    try {
                        // 先提交之前的 pending action（如果有）
                        albumManager.getLastPendingArchive()?.let { 
                            albumManager.commitPendingArchive(it.id)
                        }
                        
                        // 1. 创建图集
                        val newAlbum = albumManager.createAlbum(name, color, emoji)
                        // 2. 将图片加入待归档队列
                        albumManager.queueImageToAlbum(image.id, image.uri.toString(), newAlbum.id, newAlbum.name)
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Failed to create album and classify: $name", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "创建图集失败: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onUndoAlbumAction = {
                scope.launch {
                    // 取消待执行的归档操作（图片还没被复制，所以无需删除）
                    albumManager.cancelLastPendingArchive()
                }
            },
            onCommitArchive = {
                scope.launch {
                    // Snackbar 消失时提交归档，执行真正的复制
                    albumManager.getLastPendingArchive()?.let { 
                        albumManager.commitPendingArchive(it.id)
                    }
                }
            },
            onReorderAlbums = { newOrder ->
                scope.launch {
                    albumManager.reorderAlbums(newOrder)
                }
            },
            onSystemBucketClick = { bucketName ->
                selectedBucketName = bucketName
                currentScreen = AppScreen.SYSTEM_ALBUM_VIEW
            },
            // 推荐算法回调 - 使用 RecommendationEngine
            getRecommendedBatch = { images, size ->
                recommendationEngine.getRecommendedBatch(images, size)
            },
            // 推荐模式刷新触发器（切换算法时刷新批次）
            recommendModeRefreshKey = recommendModeRefreshKey,
            // 从冷却池移除照片的回调（切换算法时移除未浏览的照片）
            onRemoveFromCooldown = { imageIds ->
                recommendationEngine.removeFromCooldown(imageIds)
            },
            // 刷新按钮回调（系统相册集成后，不需要同步，只需刷新）
            onSyncClick = {
                scope.launch {
                    Toast.makeText(context, "正在刷新图库...", Toast.LENGTH_SHORT).show()
                    try {
                        // 刷新图集列表和图片列表
                        val repository = LocalImageRepository(context)
                        val refreshedData = withContext(Dispatchers.IO) {
                            val loadResult = repository.loadAllData()
                            albumManager.refreshAlbumsFromSystem(loadResult.systemBuckets)
                            loadResult
                        }
                        systemBuckets = refreshedData.systemBuckets
                        allImages = refreshedData.allImages
                        imagesForSorting = refreshedData.imagesExcludingTabula
                        Toast.makeText(context, "刷新完成", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Refresh failed", e)
                        Toast.makeText(context, "刷新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            enableSwipeHaptics = swipeHapticsEnabled,
            isAdaptiveCardStyle = cardStyleMode == CardStyleMode.ADAPTIVE,
            swipeStyle = swipeStyle,
            tagSelectionMode = tagSelectionMode,
            tagsPerRow = tagsPerRow,
            tagSwitchSpeed = tagSwitchSpeed,
            isAlbumMode = isAlbumMode,
            onModeChange = { isAlbumMode = it },
            // 显示/隐藏 隐藏的图集
            showHiddenAlbums = showHiddenAlbums,
            onToggleShowHidden = { showHiddenAlbums = !showHiddenAlbums },
            // 图集操作回调
            onHideAlbum = { album ->
                scope.launch {
                    albumManager.hideAlbum(album.id)
                    Toast.makeText(context, "相册已隐藏", Toast.LENGTH_SHORT).show()
                }
            },
            onExcludeAlbum = { album, excluded ->
                scope.launch {
                    albumManager.setAlbumExcludedFromRecommend(album.id, excluded)
                    val message = if (excluded) "已屏蔽，不再推荐此图集的照片" else "已取消屏蔽"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            isAlbumExcluded = { album ->
                albumManager.isAlbumExcludedFromRecommend(album.id)
            },
            onBatchRemainingChange = { remaining ->
                // 更新流体云的批次剩余数量
                (context as? MainActivity)?.updateBatchRemaining(remaining, fluidCloudEnabled)
            },
            // 快捷操作按钮
            quickActionEnabled = quickActionEnabled,
            leftHandButtonX = quickActionButtonX,
            leftHandButtonY = quickActionButtonY,
            onQuickActionPositionChanged = { _, x, y ->
                quickActionButtonX = x
                quickActionButtonY = y
                preferences.quickActionButtonX = x
                preferences.quickActionButtonY = y
            },
            // 图集清理模式
            albumCleanupEngine = albumCleanupEngine,
            albumCleanupInfos = albumCleanupInfos,
            onRefreshAlbumCleanupInfos = { refreshAlbumCleanupInfos() },
            // 图集清理模式状态（提升到 TabulaApp 级别）
            isAlbumCleanupMode = isAlbumCleanupMode,
            onAlbumCleanupModeChange = { isAlbumCleanupMode = it },
            selectedCleanupAlbum = selectedCleanupAlbum,
            onSelectedCleanupAlbumChange = { selectedCleanupAlbum = it },
            onNavigateToOtherAlbums = { otherAlbumsList ->
                otherAlbumsForNavigation = otherAlbumsList
                currentScreen = AppScreen.OTHER_ALBUMS
            }
        )
    }

    val recycleBinContent: @Composable () -> Unit = {
        RecycleBinScreen(
            deletedImages = deletedImages,
            onRestore = { image ->
                deletedImages = deletedImages.toMutableList().apply { remove(image) }
                allImages = allImages + image
                // 恢复图片时也添加回待整理列表（如果不是 Tabula 图集内的照片）
                // 简单起见，检查 bucketDisplayName 是否不在 albums 列表中
                // 注意：这只是近似判断，更准确的需要查询 RELATIVE_PATH
                val isInTabulaAlbum = albums.any { album -> 
                    album.systemAlbumPath?.trimEnd('/')?.substringAfterLast('/') == image.bucketDisplayName 
                }
                if (!isInTabulaAlbum) {
                    imagesForSorting = imagesForSorting + image
                }
                scope.launch {
                    recycleBinManager.removeFromRecycleBin(image)
                }
                Log.d("TabulaApp", "Restored: ${image.displayName}")
            },
            onPermanentDelete = { image ->
                performDelete(image) { success ->
                    if (success) {
                        deletedImages = deletedImages.toMutableList().apply { remove(image) }
                        scope.launch {
                            recycleBinManager.removeFromRecycleBin(image)
                        }
                    }
                }
            },
            onPermanentDeleteBatch = { images ->
                performDeleteBatch(images) { success ->
                    if (success) {
                        deletedImages = deletedImages.toMutableList().apply { removeAll(images) }
                        scope.launch {
                            recycleBinManager.removeAllFromRecycleBin(images)
                        }
                    }
                }
            },
            onClearAll = {
                val imagesToDelete = deletedImages
                performDeleteBatch(imagesToDelete) { success ->
                    if (success) {
                        scope.launch {
                            recycleBinManager.clearRecycleBin()
                        }
                        deletedImages = emptyList()
                    }
                }
            },
            onNavigateBack = {
                currentScreen = AppScreen.DECK
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume
        )
    }

    val settingsContent: @Composable () -> Unit = {
        SettingsScreen(
            preferences = preferences,
            imageCount = allImages.size,
            trashCount = deletedImages.size,
            onThemeChange = onThemeChange,
            onBatchSizeChange = { size -> currentBatchSize = size },
            onTopBarModeChange = { mode -> currentTopBarMode = mode },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            hapticEnabled = hapticEnabled,
            hapticStrength = hapticStrength,
            swipeHapticsEnabled = swipeHapticsEnabled,
            onShowHdrBadgesChange = { enabled ->
                showHdrBadges = enabled
                preferences.showHdrBadges = enabled
            },
            onShowMotionBadgesChange = { enabled ->
                showMotionBadges = enabled
                preferences.showMotionBadges = enabled
            },
            onPlayMotionSoundChange = { enabled ->
                playMotionSound = enabled
                preferences.playMotionSound = enabled
            },
            onMotionSoundVolumeChange = { volume ->
                motionSoundVolume = volume
                preferences.motionSoundVolume = volume
            },
            onHapticEnabledChange = { enabled ->
                hapticEnabled = enabled
                preferences.hapticEnabled = enabled
            },
            onHapticStrengthChange = { strength ->
                hapticStrength = strength
                preferences.hapticStrength = strength
            },
            onSwipeHapticsEnabledChange = { enabled ->
                swipeHapticsEnabled = enabled
                preferences.swipeHapticsEnabled = enabled
            },
            onNavigateToAbout = { currentScreen = AppScreen.ABOUT },
            onNavigateBack = { currentScreen = AppScreen.DECK },
            onNavigateToStatistics = { currentScreen = AppScreen.STATISTICS },
            onRecommendModeChange = { 
                // 切换推荐模式后，增加刷新触发器以刷新批次
                recommendModeRefreshKey++
            },
            fluidCloudEnabled = fluidCloudEnabled,
            onFluidCloudEnabledChange = { enabled ->
                fluidCloudEnabled = enabled
                preferences.fluidCloudEnabled = enabled
            },
            onNavigateToVibrationSound = { currentScreen = AppScreen.VIBRATION_SOUND },
            onNavigateToDisplaySettings = { currentScreen = AppScreen.DISPLAY_SETTINGS },
            onNavigateToLab = { currentScreen = AppScreen.LAB },
            onNavigateToSupport = { currentScreen = AppScreen.SUPPORT },
            onNavigateToReminderSettings = { currentScreen = AppScreen.REMINDER_SETTINGS },
            excludedAlbumCount = excludedAlbumCount,
            hiddenAlbumCount = hiddenAlbumCount,
            onNavigateToHiddenAlbums = { currentScreen = AppScreen.HIDDEN_ALBUMS },
            onNavigateToPrivacyPolicy = { currentScreen = AppScreen.PRIVACY_POLICY },
            onNavigateToTutorial = { currentScreen = AppScreen.TUTORIAL },
            scrollState = settingsScrollState
        )
    }
    
    // 使用教程页面
    val tutorialContent: @Composable () -> Unit = {
        TutorialScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val vibrationSoundContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        VibrationSoundScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            accentColor = TabulaColors.EyeGold,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            hapticEnabled = hapticEnabled,
            hapticStrength = hapticStrength,
            swipeHapticsEnabled = swipeHapticsEnabled,
            onPlayMotionSoundChange = { enabled ->
                playMotionSound = enabled
                preferences.playMotionSound = enabled
            },
            onMotionSoundVolumeChange = { volume ->
                motionSoundVolume = volume
                preferences.motionSoundVolume = volume
            },
            onHapticEnabledChange = { enabled ->
                hapticEnabled = enabled
                preferences.hapticEnabled = enabled
                HapticFeedback.updateSettings(enabled, hapticStrength)
            },
            onHapticStrengthChange = { strength ->
                hapticStrength = strength
                preferences.hapticStrength = strength
                HapticFeedback.updateSettings(hapticEnabled, strength)
            },
            onSwipeHapticsEnabledChange = { enabled ->
                swipeHapticsEnabled = enabled
                preferences.swipeHapticsEnabled = enabled
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val labContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        LabScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            fluidCloudEnabled = fluidCloudEnabled,
            onFluidCloudEnabledChange = { enabled ->
                fluidCloudEnabled = enabled
                preferences.fluidCloudEnabled = enabled
            },
            liquidGlassLabEnabled = liquidGlassLabEnabled,
            onLiquidGlassLabEnabledChange = onLiquidGlassLabEnabledChange,
            // 标签收纳设置
            tagSelectionMode = tagSelectionMode,
            tagSwitchSpeed = tagSwitchSpeed,
            tagsPerRow = tagsPerRow,
            onTagSelectionModeChange = { mode ->
                tagSelectionMode = mode
                preferences.tagSelectionMode = mode
            },
            onTagSwitchSpeedChange = { speed ->
                tagSwitchSpeed = speed
                preferences.tagSwitchSpeed = speed
            },
            onTagsPerRowChange = { count ->
                tagsPerRow = count
                preferences.tagsPerRow = count
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }
    
    // 提醒设置页面
    val reminderSettingsContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        val context = LocalContext.current
        ReminderSettingsScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            accentColor = TabulaColors.EyeGold,
            showDeleteConfirm = showDeleteConfirm,
            onShowDeleteConfirmChange = { enabled ->
                showDeleteConfirm = enabled
                preferences.showDeleteConfirm = enabled
            },
            sourceImageDeletionStrategy = sourceImageDeletionStrategy,
            onSourceImageDeletionStrategyChange = { strategy ->
                sourceImageDeletionStrategy = strategy
                preferences.sourceImageDeletionStrategy = strategy
                val message = if (strategy == SourceImageDeletionStrategy.ASK_EVERY_TIME) "归档提醒已开启" else "归档提醒已关闭"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }
    
    // 隐藏与屏蔽图集管理页面
    // 隐藏与屏蔽图集管理页面 (实际上是图集标签管理)
    val hiddenAlbumsContent: @Composable () -> Unit = {
        // 使用 count 作为 key 触发列表刷新，实现响应式更新
        // 获取排除的相册 ID 集合
        val excludedIds = remember(excludedAlbumCount) {
             albumManager.getExcludedAlbumIds()
        }
        
        AlbumTagManagementScreen(
            albums = albums,
            excludedAlbumIds = excludedIds,
            preferences = preferences,
            onReorderAlbums = { newOrder ->
                scope.launch { albumManager.reorderAlbums(newOrder) }
            },
            onToggleAlbumHidden = { albumId, isHidden ->
                scope.launch {
                     if (isHidden) albumManager.hideAlbum(albumId) else albumManager.unhideAlbum(albumId)
                }
            },
            onToggleAlbumExcluded = { albumId, isExcluded ->
                scope.launch {
                    albumManager.setAlbumExcludedFromRecommend(albumId, isExcluded)
                }
            },
            onResetTagSorting = {
                // 如果需要重置排序，尝试传入空列表或待实现的方法
                // 目前暂时提示用户
                Toast.makeText(context, "排序已重置", Toast.LENGTH_SHORT).show()
                // scope.launch { albumManager.resetAlbumOrder() } // 方法不存在
            },
            onRefreshAlbums = {
                 scope.launch { albumManager.refreshAlbumsFromSystem() }
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }
    
    // 隐私政策页面
    val privacyPolicyContent: @Composable () -> Unit = {
        PrivacyPolicyScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    // 显示设置页面
    val displaySettingsContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        DisplaySettingsScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            accentColor = TabulaColors.EyeGold,
            batchSize = currentBatchSize,
            onBatchSizeChange = { size ->
                currentBatchSize = size
                preferences.batchSize = size
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            cardStyleMode = cardStyleMode,
            swipeStyle = swipeStyle,
            onShowHdrBadgesChange = { enabled ->
                showHdrBadges = enabled
                preferences.showHdrBadges = enabled
            },
            onShowMotionBadgesChange = { enabled ->
                showMotionBadges = enabled
                preferences.showMotionBadges = enabled
            },
            onCardStyleModeChange = { mode ->
                cardStyleMode = mode
                preferences.cardStyleMode = mode
            },
            onSwipeStyleChange = { style ->
                swipeStyle = style
                preferences.swipeStyle = style
            },
            // 快捷操作按钮
            quickActionEnabled = quickActionEnabled,
            onQuickActionEnabledChange = { enabled ->
                quickActionEnabled = enabled
                preferences.quickActionButtonEnabled = enabled
            },
            onResetButtonPosition = {
                preferences.resetQuickActionButtonPosition()
                quickActionButtonX = preferences.quickActionButtonX
                quickActionButtonY = preferences.quickActionButtonY
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val aboutContent: @Composable () -> Unit = {
        AboutScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val supportContent: @Composable () -> Unit = {
        SupportScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val statisticsContent: @Composable () -> Unit = {
        StatisticsScreen(
            onBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val albumViewContent: @Composable () -> Unit = {
        AlbumViewScreen(
            albums = albums,
            allImages = allImages,
            getImagesForAlbum = { albumId -> albumManager.getImageIdsForAlbum(albumId) },
            getImageMappingsForAlbum = { albumId -> albumManager.getImageMappingsForAlbum(albumId) },
            onCreateAlbum = { name, color, emoji ->
                scope.launch {
                    try {
                        albumManager.createAlbum(name, color, emoji)
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Failed to create album in AlbumView: $name", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "创建图集失败: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onUpdateAlbum = { album ->
                scope.launch { albumManager.updateAlbum(album) }
            },
            onDeleteAlbum = { albumId ->
                scope.launch { albumManager.deleteAlbum(albumId) }
            },
            onHideAlbum = { albumId ->
                scope.launch { 
                    albumManager.hideAlbum(albumId)
                    Toast.makeText(context, "相册已隐藏", Toast.LENGTH_SHORT).show()
                }
            },
            onUnhideAlbum = { albumId ->
                scope.launch {
                    albumManager.unhideAlbum(albumId)
                    Toast.makeText(context, "相册已取消隐藏", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteEmptyAlbum = { albumId ->
                scope.launch {
                    val success = albumManager.deleteEmptyAlbum(albumId)
                    if (success) {
                        Toast.makeText(context, "相册已删除", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onExcludeFromRecommend = { albumId, excluded ->
                scope.launch {
                    albumManager.setAlbumExcludedFromRecommend(albumId, excluded)
                    val message = if (excluded) "已屏蔽，不再推荐此图集的照片" else "已取消屏蔽"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            isAlbumExcludedFromRecommend = { albumId ->
                albumManager.isAlbumExcludedFromRecommend(albumId)
            },
            onNavigateBack = { currentScreen = AppScreen.DECK },
            initialAlbumId = selectedAlbumId,
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            onMoveToAlbum = { imageIds, targetAlbumId ->
                scope.launch {
                    selectedAlbumId?.let { currentAlbumId ->
                        albumManager.moveImagesToAlbum(imageIds, currentAlbumId, targetAlbumId)
                    }
                }
            },
            onCopyToAlbum = { imageIds, targetAlbumId ->
                scope.launch {
                    albumManager.copyImagesToAlbum(imageIds, targetAlbumId)
                }
            },
            onDeleteImages = { imageIds ->
                // 找到对应的 ImageFile
                val imagesToDelete = allImages.filter { it.id in imageIds }
                if (imagesToDelete.isNotEmpty()) {
                    // 执行永久删除操作
                    performDeleteBatch(imagesToDelete) { success ->
                        if (success) {
                            // 从 allImages 中移除
                            val deletedIds = imagesToDelete.map { it.id }.toSet()
                            allImages = allImages.filter { it.id !in deletedIds }
                            imagesForSorting = imagesForSorting.filter { it.id !in deletedIds }
                            
                            // 刷新图集信息（更新 imageCount 等，确保与系统相册同步）
                            scope.launch {
                                albumManager.refreshAlbumsFromSystem()
                            }
                            
                            Toast.makeText(context, "已永久删除 ${imagesToDelete.size} 张图片", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    val systemAlbumViewContent: @Composable () -> Unit = {
        var bucketImages by remember(selectedBucketName) { mutableStateOf<List<ImageFile>>(emptyList()) }
        LaunchedEffect(selectedBucketName) {
             if (selectedBucketName != null) {
                 bucketImages = withContext(Dispatchers.IO) {
                     LocalImageRepository(context).getImagesByBucket(selectedBucketName!!)
                 }
             }
        }
        
        SystemAlbumViewScreen(
            albumName = selectedBucketName ?: "",
            images = bucketImages,
            onNavigateBack = { currentScreen = AppScreen.DECK },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume
        )
    }
    
    val otherAlbumsContent: @Composable () -> Unit = {
        OtherAlbumsScreen(
            albums = otherAlbumsForNavigation,
            allImages = allImages,
            onAlbumClick = { album ->
                selectedAlbumId = album.id
                currentScreen = AppScreen.ALBUM_VIEW
            },
            onPinAlbum = { albumId ->
                // TODO: Implement pin album
                Toast.makeText(context, "固定相册: $albumId", Toast.LENGTH_SHORT).show()
            },
            onNavigateBack = { currentScreen = AppScreen.DECK }
        )
    }

    // ========== 引导流程处理（全屏，独立于主导航） ==========
    if (currentScreen == AppScreen.ONBOARDING) {
        OnboardingScreen(
            onSkipToMain = {
                preferences.hasCompletedOnboarding = true
                showOnboarding = false
                currentScreen = AppScreen.DECK
            },
            onPersonalize = {
                currentScreen = AppScreen.PERSONALIZATION
            }
        )
        return
    }
    
    if (currentScreen == AppScreen.PERSONALIZATION) {
        PersonalizationScreen(
            preferences = preferences,
            onComplete = {
                preferences.hasCompletedOnboarding = true
                showOnboarding = false
                // 从 preferences 刷新所有个性化设置到 UI 状态
                currentTopBarMode = preferences.topBarDisplayMode
                cardStyleMode = preferences.cardStyleMode
                swipeStyle = preferences.swipeStyle
                showHdrBadges = preferences.showHdrBadges
                showMotionBadges = preferences.showMotionBadges
                quickActionEnabled = preferences.quickActionButtonEnabled
                // 刷新推荐模式
                recommendModeRefreshKey++
                currentScreen = AppScreen.DECK
            },
            onSkip = {
                preferences.hasCompletedOnboarding = true
                showOnboarding = false
                // 从 preferences 刷新所有个性化设置到 UI 状态
                currentTopBarMode = preferences.topBarDisplayMode
                cardStyleMode = preferences.cardStyleMode
                swipeStyle = preferences.swipeStyle
                showHdrBadges = preferences.showHdrBadges
                showMotionBadges = preferences.showMotionBadges
                quickActionEnabled = preferences.quickActionButtonEnabled
                // 刷新推荐模式
                recommendModeRefreshKey++
                currentScreen = AppScreen.DECK
            }
        )
        return
    }

    // 根据当前屏幕决定 前景 和 背景
    val (backgroundContent, foregroundContent) = when (currentScreen) {
        AppScreen.ONBOARDING -> deckContent to null  // 不会到达这里，但需要完整的 when
        AppScreen.PERSONALIZATION -> deckContent to null  // 不会到达这里
        AppScreen.DECK -> deckContent to null
        AppScreen.RECYCLE_BIN -> deckContent to recycleBinContent
        AppScreen.SETTINGS -> deckContent to settingsContent
        AppScreen.ABOUT -> settingsContent to aboutContent
        AppScreen.SUPPORT -> settingsContent to supportContent
        AppScreen.STATISTICS -> settingsContent to statisticsContent
        AppScreen.ALBUM_VIEW -> deckContent to albumViewContent
        AppScreen.SYSTEM_ALBUM_VIEW -> deckContent to systemAlbumViewContent
        AppScreen.OTHER_ALBUMS -> deckContent to otherAlbumsContent
        AppScreen.VIBRATION_SOUND -> settingsContent to vibrationSoundContent
        AppScreen.DISPLAY_SETTINGS -> settingsContent to displaySettingsContent
        AppScreen.LAB -> settingsContent to labContent
        AppScreen.REMINDER_SETTINGS -> settingsContent to reminderSettingsContent
        AppScreen.HIDDEN_ALBUMS -> settingsContent to hiddenAlbumsContent
        AppScreen.PRIVACY_POLICY -> settingsContent to privacyPolicyContent
        AppScreen.TUTORIAL -> settingsContent to tutorialContent
    }
    
    // ========== 渲染容器 ==========
    // 液态玻璃效果现在由 AGSL 着色器在各个组件内部处理
    // 不再需要全局的 Backdrop Provider
    Box(modifier = Modifier.fillMaxSize()) {
        PredictiveBackContainer(
            currentScreen = currentScreen,
            onNavigateBack = {
                currentScreen = when (currentScreen) {
                    AppScreen.ONBOARDING -> AppScreen.ONBOARDING  // 引导页不支持返回
                    AppScreen.PERSONALIZATION -> AppScreen.ONBOARDING  // 个性化返回到引导页
                    AppScreen.RECYCLE_BIN -> AppScreen.DECK
                    AppScreen.SETTINGS -> AppScreen.DECK
                    AppScreen.ABOUT -> AppScreen.SETTINGS
                    AppScreen.SUPPORT -> AppScreen.SETTINGS
                    AppScreen.STATISTICS -> AppScreen.SETTINGS
                    AppScreen.ALBUM_VIEW -> AppScreen.DECK
                    AppScreen.SYSTEM_ALBUM_VIEW -> AppScreen.DECK
                    AppScreen.OTHER_ALBUMS -> AppScreen.DECK
                    AppScreen.VIBRATION_SOUND -> AppScreen.SETTINGS
                    AppScreen.DISPLAY_SETTINGS -> AppScreen.SETTINGS
                    AppScreen.LAB -> AppScreen.SETTINGS
                    AppScreen.REMINDER_SETTINGS -> AppScreen.SETTINGS
                    AppScreen.HIDDEN_ALBUMS -> AppScreen.SETTINGS
                    AppScreen.PRIVACY_POLICY -> AppScreen.SETTINGS
                    AppScreen.TUTORIAL -> AppScreen.SETTINGS
                    AppScreen.DECK -> AppScreen.DECK
                }
            },
            backgroundContent = backgroundContent,
            foregroundContent = foregroundContent ?: {}
        )
        
        // ========== 原图删除提醒对话框 ==========
        if (showSourceImageDeletionDialog) {
            SourceImageDeletionDialog(
                imageCount = pendingSourceImageUris.size,
                onConfirm = {
                    showSourceImageDeletionDialog = false
                    scope.launch {
                        Toast.makeText(context, "正在删除 ${pendingSourceImageUris.size} 张原图...", Toast.LENGTH_SHORT).show()
                        
                        performDeleteUris(pendingSourceImageUris) { deletedUris ->
                            if (deletedUris.isNotEmpty()) {
                                scope.launch {
                                    albumManager.clearDeletedUris(deletedUris)
                                    allImages = allImages.filter { img -> 
                                        !deletedUris.contains(img.uri.toString())
                                    }
                                    // 同时从待整理列表中移除
                                    imagesForSorting = imagesForSorting.filter { img ->
                                        !deletedUris.contains(img.uri.toString())
                                    }
                                }
                                Toast.makeText(context, "已删除 ${deletedUris.size} 张原图", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "删除失败或已取消", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDismiss = {
                    showSourceImageDeletionDialog = false
                },
                isDarkTheme = LocalIsDarkTheme.current
            )
        }
    }
}

/**
 * 原图删除提醒对话框
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SourceImageDeletionDialog(
    imageCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    val containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
    val accentColor = Color(0xFF007AFF)
    val warningColor = Color(0xFFFF9F0A)
    val context = LocalContext.current
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = warningColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "删除原位置的照片",
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "检测到有 $imageCount 张照片已复制到图集，原位置的照片可以删除了。",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(warningColor.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已确认照片成功复制，删除是安全的。",
                            color = textColor.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    HapticFeedback.mediumTap(context)
                    onConfirm()
                }
            ) {
                Text(
                    text = "删除原图",
                    color = Color(0xFFFF3B30),  // 红色
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onDismiss()
                }
            ) {
                Text(
                    text = "稍后处理",
                    color = secondaryTextColor
                )
            }
        }
    )
}

/**
 * 权限请求界面 - 黑猫风格
 */
@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val backgroundColor = if (isDarkTheme) TabulaColors.CatBlack else TabulaColors.WarmWhite
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val buttonBgColor = if (isDarkTheme) TabulaColors.EyeGold else TabulaColors.CatBlack
    val buttonTextColor = if (isDarkTheme) TabulaColors.CatBlack else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Tabula Logo",
                modifier = Modifier.size(150.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "欢迎使用 Tabula",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "高效整理你的照片库",
                fontSize = 16.sp,
                color = textColor.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .size(width = 200.dp, height = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBgColor,
                    contentColor = buttonTextColor
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "授权访问照片",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
