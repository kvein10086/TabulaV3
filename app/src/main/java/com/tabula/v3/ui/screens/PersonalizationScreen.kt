package com.tabula.v3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.CardStyleMode
import com.tabula.v3.data.preferences.RecommendMode
import com.tabula.v3.data.preferences.SwipeStyle
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.ui.util.HapticFeedback
import kotlinx.coroutines.launch

// iOS 18 风格颜色
private val iOS18Blue = Color(0xFF007AFF)
private val iOS18TextPrimary = Color(0xFF000000)
private val iOS18TextSecondary = Color(0xFF8E8E93)
private val iOS18Background = Color(0xFFFFFFFF)
private val iOS18SheetBackground = Color(0xFFF2F2F7)

/**
 * 个性化设置流程 - iOS 18 风格（全屏）
 * 
 * 5页设置：
 * 1. 顶部显示设置（索引 vs 日期）
 * 2. 卡片样式设置（固定 vs 自适应）
 * 3. 切换样式设置（流转 vs 轻掠）
 * 4. 推荐模式设置
 * 5. 标识显示与快捷操作
 */
@Composable
fun PersonalizationScreen(
    preferences: AppPreferences,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 设置状态
    var topBarMode by remember { mutableStateOf(preferences.topBarDisplayMode) }
    var cardStyleMode by remember { mutableStateOf(preferences.cardStyleMode) }
    var swipeStyle by remember { mutableStateOf(preferences.swipeStyle) }
    var recommendMode by remember { mutableStateOf(preferences.recommendMode) }
    var showHdrBadges by remember { mutableStateOf(preferences.showHdrBadges) }
    var showMotionBadges by remember { mutableStateOf(preferences.showMotionBadges) }
    var quickActionEnabled by remember { mutableStateOf(preferences.quickActionButtonEnabled) }
    
    // Pager 状态
    val pagerState = rememberPagerState(pageCount = { 5 })
    
    // 监听页面变化，自动保存设置（解决滑动切换页面时设置不生效的问题）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { _ ->
                // 每次页面切换时保存当前所有设置
                saveAllSettings(
                    preferences, topBarMode, cardStyleMode, swipeStyle,
                    recommendMode, showHdrBadges, showMotionBadges, quickActionEnabled
                )
            }
    }
    
    // 全屏白色背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(iOS18Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部区域：跳过按钮（最后一页不显示）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                if (pagerState.currentPage < 4) {
                    TextButton(
                        onClick = {
                            HapticFeedback.lightTap(context)
                            saveAllSettings(preferences, topBarMode, cardStyleMode, swipeStyle, 
                                recommendMode, showHdrBadges, showMotionBadges, quickActionEnabled)
                            onSkip()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = "跳过",
                            color = iOS18Blue,
                            fontSize = 17.sp
                        )
                    }
                }
            }
            
            // 内容区域
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> TopBarDisplayPage(
                        selected = topBarMode,
                        onSelect = { 
                            topBarMode = it
                            preferences.topBarDisplayMode = it
                        }
                    )
                    1 -> CardStylePage(
                        selected = cardStyleMode,
                        onSelect = {
                            cardStyleMode = it
                            preferences.cardStyleMode = it
                        }
                    )
                    2 -> SwipeStylePage(
                        selected = swipeStyle,
                        onSelect = {
                            swipeStyle = it
                            preferences.swipeStyle = it
                        }
                    )
                    3 -> RecommendModePage(
                        selected = recommendMode,
                        onSelect = {
                            recommendMode = it
                            preferences.recommendMode = it
                        }
                    )
                    4 -> BadgesAndQuickActionPage(
                        showHdrBadges = showHdrBadges,
                        showMotionBadges = showMotionBadges,
                        quickActionEnabled = quickActionEnabled,
                        onHdrBadgesChange = {
                            showHdrBadges = it
                            preferences.showHdrBadges = it
                        },
                        onMotionBadgesChange = {
                            showMotionBadges = it
                            preferences.showMotionBadges = it
                        },
                        onQuickActionChange = {
                            quickActionEnabled = it
                            preferences.quickActionButtonEnabled = it
                        }
                    )
                }
            }
            
            // 页面指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(5) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) iOS18Blue
                                else Color(0xFFD1D1D6)
                            )
                    )
                }
            }
            
            // 底部按钮 - iOS 风格
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .padding(bottom = 16.dp)
            ) {
                val isLastPage = pagerState.currentPage == 4
                
                Button(
                    onClick = {
                        HapticFeedback.heavyTap(context)
                        if (isLastPage) {
                            saveAllSettings(preferences, topBarMode, cardStyleMode, swipeStyle,
                                recommendMode, showHdrBadges, showMotionBadges, quickActionEnabled)
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iOS18Blue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = if (isLastPage) "开始使用" else "继续",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}

private fun saveAllSettings(
    preferences: AppPreferences,
    topBarMode: TopBarDisplayMode,
    cardStyleMode: CardStyleMode,
    swipeStyle: SwipeStyle,
    recommendMode: RecommendMode,
    showHdrBadges: Boolean,
    showMotionBadges: Boolean,
    quickActionEnabled: Boolean
) {
    preferences.topBarDisplayMode = topBarMode
    preferences.cardStyleMode = cardStyleMode
    preferences.swipeStyle = swipeStyle
    preferences.recommendMode = recommendMode
    preferences.showHdrBadges = showHdrBadges
    preferences.showMotionBadges = showMotionBadges
    preferences.quickActionButtonEnabled = quickActionEnabled
}

// ==================== 第1页：顶部显示设置 ====================

@Composable
private fun TopBarDisplayPage(
    selected: TopBarDisplayMode,
    onSelect: (TopBarDisplayMode) -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // 标题
        Text(
            text = "顶部显示方式",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "选择浏览照片时顶部显示的信息",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 对比展示区域 - iOS 18 风格（分离式设计）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 索引模式
            iOS18ComparisonItem(
                modifier = Modifier.weight(1f),
                isSelected = selected == TopBarDisplayMode.INDEX,
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSelect(TopBarDisplayMode.INDEX)
                },
                preview = {
                    TopBarPreviewBox(displayText = "3/15")
                },
                label = "索引"
            )
            
            // 日期模式
            iOS18ComparisonItem(
                modifier = Modifier.weight(1f),
                isSelected = selected == TopBarDisplayMode.DATE,
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSelect(TopBarDisplayMode.DATE)
                },
                preview = {
                    TopBarPreviewBox(displayText = "Jan 2026")
                },
                label = "日期"
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TopBarPreviewBox(displayText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(iOS18SheetBackground)
            .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = iOS18TextPrimary,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
    }
}

// ==================== 第2页：卡片样式设置 ====================

@Composable
private fun CardStylePage(
    selected: CardStyleMode,
    onSelect: (CardStyleMode) -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "卡片样式",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "选择照片卡片的显示方式",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 固定样式
            iOS18ComparisonItem(
                modifier = Modifier.weight(1f),
                isSelected = selected == CardStyleMode.FIXED,
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSelect(CardStyleMode.FIXED)
                },
                preview = {
                    CardStylePreviewBox(isFixed = true)
                },
                label = "固定样式"
            )
            
            // 自适应样式
            iOS18ComparisonItem(
                modifier = Modifier.weight(1f),
                isSelected = selected == CardStyleMode.ADAPTIVE,
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSelect(CardStyleMode.ADAPTIVE)
                },
                preview = {
                    CardStylePreviewBox(isFixed = false)
                },
                label = "自适应"
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (selected == CardStyleMode.FIXED) 
                "所有卡片统一 3:4 比例" 
            else 
                "根据图片比例动态调整",
            fontSize = 15.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CardStylePreviewBox(isFixed: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(iOS18SheetBackground)
            .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isFixed) {
            // 固定 3:4 比例卡片
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(67.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(8.dp))
            )
        } else {
            // 自适应卡片（显示不同比例）
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(6.dp))
                )
                Box(
                    modifier = Modifier
                        .width(45.dp)
                        .height(35.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

// ==================== 第3页：切换样式设置 ====================

@Composable
private fun SwipeStylePage(
    selected: SwipeStyle,
    onSelect: (SwipeStyle) -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "切换样式",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "选择左右滑动时卡片的切换方式",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 流转模式
            iOS18ComparisonItem(
                modifier = Modifier.weight(1f),
                isSelected = selected == SwipeStyle.SHUFFLE,
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSelect(SwipeStyle.SHUFFLE)
                },
                preview = {
                    SwipeStylePreviewBox(isShuffle = true)
                },
                label = "流转模式"
            )
            
            // 轻掠模式
            iOS18ComparisonItem(
                modifier = Modifier.weight(1f),
                isSelected = selected == SwipeStyle.DRAW,
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSelect(SwipeStyle.DRAW)
                },
                preview = {
                    SwipeStylePreviewBox(isShuffle = false)
                },
                label = "轻掠模式"
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (selected == SwipeStyle.SHUFFLE) 
                "卡片循环切换，插入底部" 
            else 
                "右滑发牌飞出，左滑收牌飞回",
            fontSize = 15.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SwipeStylePreviewBox(isShuffle: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(iOS18SheetBackground)
            .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isShuffle) {
            // 流转模式：堆叠卡片
            Box {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(55.dp)
                        .offset(x = 6.dp, y = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFD0D0D0))
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(55.dp)
                        .offset(x = 3.dp, y = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE0E0E0))
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(6.dp))
                )
                Icon(
                    imageVector = Icons.Rounded.Autorenew,
                    contentDescription = null,
                    tint = iOS18Blue,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 10.dp, y = 10.dp)
                )
            }
        } else {
            // 轻掠模式
            Box {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(6.dp))
                )
                Icon(
                    imageVector = Icons.Rounded.SwipeRight,
                    contentDescription = null,
                    tint = iOS18Blue,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 18.dp)
                )
            }
        }
    }
}

// ==================== 第4页：推荐模式设置 ====================

@Composable
private fun RecommendModePage(
    selected: RecommendMode,
    onSelect: (RecommendMode) -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "推荐模式",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "选择照片的推荐方式",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 推荐模式选项
        iOS18OptionCard(
            icon = Icons.Outlined.Shuffle,
            title = "随机漫步",
            description = "真正随机抽取照片，已看过的照片短期内不会再次出现",
            isSelected = selected == RecommendMode.RANDOM_WALK,
            onClick = {
                HapticFeedback.lightTap(context)
                onSelect(RecommendMode.RANDOM_WALK)
            }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        iOS18OptionCard(
            icon = Icons.Outlined.PhotoLibrary,
            title = "相似推荐",
            description = "优先推荐相似照片，帮助清理连拍和重复照片",
            isSelected = selected == RecommendMode.SIMILAR,
            onClick = {
                HapticFeedback.lightTap(context)
                onSelect(RecommendMode.SIMILAR)
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun iOS18OptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(iOS18SheetBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iOS18Blue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iOS18Blue,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = iOS18TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = iOS18TextSecondary,
                    lineHeight = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // iOS 风格选中圆环
            iOS18RadioButton(isSelected = isSelected)
        }
    }
}

// ==================== 第5页：标识显示与快捷操作 ====================

@Composable
private fun BadgesAndQuickActionPage(
    showHdrBadges: Boolean,
    showMotionBadges: Boolean,
    quickActionEnabled: Boolean,
    onHdrBadgesChange: (Boolean) -> Unit,
    onMotionBadgesChange: (Boolean) -> Unit,
    onQuickActionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "标识与快捷操作",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "自定义显示偏好",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 设置卡片 - iOS 风格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(iOS18SheetBackground)
        ) {
            iOS18ToggleItem(
                title = "显示 HDR 标识",
                description = "在 HDR 照片上显示标识",
                checked = showHdrBadges,
                onCheckedChange = {
                    HapticFeedback.lightTap(context)
                    onHdrBadgesChange(it)
                }
            )
            
            iOS18Divider()
            
            iOS18ToggleItem(
                title = "显示 Live 照片",
                description = "在 Live 照片上显示标识",
                checked = showMotionBadges,
                onCheckedChange = {
                    HapticFeedback.lightTap(context)
                    onMotionBadgesChange(it)
                }
            )
            
            iOS18Divider()
            
            iOS18ToggleItem(
                title = "快捷操作按钮",
                description = "显示悬浮按钮，点击快速切换照片",
                checked = quickActionEnabled,
                onCheckedChange = {
                    HapticFeedback.lightTap(context)
                    onQuickActionChange(it)
                }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun iOS18ToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = iOS18TextPrimary
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = iOS18TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iOS18Blue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE9E9EB)
            ),
            modifier = Modifier.scale(0.9f)
        )
    }
}

@Composable
private fun iOS18Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(Color(0xFFD1D1D6))
    )
}

// ==================== 通用组件 ====================

/**
 * iOS 18 风格对比项 - 分离式设计（无 ripple 效果）
 * 预览区域、名称、选中按钮三者分开
 */
@Composable
private fun iOS18ComparisonItem(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
    label: String
) {
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 预览区域
        preview()
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 名称
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 选中按钮 - iOS 风格圆环
        iOS18RadioButton(isSelected = isSelected)
    }
}

/**
 * iOS 18 风格单选按钮
 */
@Composable
private fun iOS18RadioButton(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.background(iOS18Blue)
                } else {
                    Modifier.border(2.dp, Color(0xFFD1D1D6), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // 内部白色圆点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
