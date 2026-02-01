package com.tabula.v3.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.R
import com.tabula.v3.ui.util.HapticFeedback

// iOS 18 风格颜色
private val iOS18Blue = Color(0xFF007AFF)
private val iOS18TextPrimary = Color(0xFF000000)
private val iOS18TextSecondary = Color(0xFF8E8E93)
private val iOS18Background = Color(0xFFFFFFFF)
private val iOS18SheetBackground = Color(0xFFF2F2F7)

/**
 * 全屏引导页 - iOS 18 风格（全屏白色背景）
 * 
 * 包含三个页面：
 * 1. 欢迎页 - 小黑猫萌图 + 欢迎语
 * 2. 手势演示页 - 四方向手势说明
 * 3. 结束页 - 期待表情 + 两个选择按钮
 */
@Composable
fun OnboardingScreen(
    onSkipToMain: () -> Unit,
    onPersonalize: () -> Unit
) {
    val context = LocalContext.current
    
    // Pager 状态
    val pagerState = rememberPagerState(pageCount = { 3 })
    
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
            // 内容区域
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage()
                    1 -> OnboardingGesturePage()
                    2 -> OnboardingReadyPage(
                        onPersonalize = {
                            HapticFeedback.heavyTap(context)
                            onPersonalize()
                        },
                        onSkip = {
                            HapticFeedback.lightTap(context)
                            onSkipToMain()
                        }
                    )
                }
            }
            
            // 页面指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
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
        }
    }
}

/**
 * 第一页：欢迎页 - iOS 18 风格
 */
@Composable
private fun OnboardingWelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 小黑猫图片
        Image(
            painter = painterResource(id = R.drawable.ydcat1),
            contentDescription = "Tabula 小黑猫",
            modifier = Modifier.size(160.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 标题 - iOS 风格大标题
        Text(
            text = "欢迎使用 Tabula",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 副标题
        Text(
            text = "让整理相册变得简单又有趣",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 第二页：手势演示页 - iOS 18 风格
 */
@Composable
private fun OnboardingGesturePage() {
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "gesture")
    
    val upArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "upArrow"
    )
    
    val downArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "downArrow"
    )
    
    val leftArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leftArrow"
    )
    
    val rightArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rightArrow"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            text = "简单手势，高效整理",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 手势演示区域
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            // 中心卡片示意
            Box(
                modifier = Modifier
                    .size(90.dp, 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iOS18SheetBackground),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(0.5f)
                )
            }
            
            // 上方：标记
            OnboardingGestureIndicator(
                icon = Icons.Rounded.KeyboardArrowUp,
                label = "标记",
                color = Color(0xFFFF3B30),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = upArrowOffset.dp)
            )
            
            // 下方：整理
            OnboardingGestureIndicator(
                icon = Icons.Rounded.KeyboardArrowDown,
                label = "整理",
                color = Color(0xFF34C759),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = downArrowOffset.dp)
            )
            
            // 左侧：下一张
            OnboardingGestureIndicator(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                label = "下一张",
                color = iOS18Blue,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = leftArrowOffset.dp),
                isHorizontal = true
            )
            
            // 右侧：上一张
            OnboardingGestureIndicator(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                label = "上一张",
                color = iOS18Blue,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = rightArrowOffset.dp),
                isHorizontal = true
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 提示文字
        Text(
            text = "滑动卡片即可快速整理照片",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 手势方向指示器
 */
@Composable
private fun OnboardingGestureIndicator(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false
) {
    if (isHorizontal) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon == Icons.AutoMirrored.Rounded.KeyboardArrowLeft) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = label,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon == Icons.Rounded.KeyboardArrowUp) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = label,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = label,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * 第三页：准备好了页 - iOS 18 风格
 */
@Composable
private fun OnboardingReadyPage(
    onPersonalize: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        // 期待表情的小黑猫
        Image(
            painter = painterResource(id = R.drawable.ydcat2),
            contentDescription = "Tabula 期待表情",
            modifier = Modifier.size(150.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 标题
        Text(
            text = "准备好了吗？",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iOS18TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 副标题
        Text(
            text = "跟 Tabula 一起整理相册吧！",
            fontSize = 17.sp,
            color = iOS18TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 底部按钮区域 - iOS 18 风格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 个性化设置按钮（主按钮）- iOS 蓝色
            Button(
                onClick = onPersonalize,
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
                    text = "个性化设置",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 直接使用按钮（次要按钮）
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "不了，直接使用",
                    color = iOS18Blue,
                    fontWeight = FontWeight.Normal,
                    fontSize = 17.sp
                )
            }
        }
    }
}
