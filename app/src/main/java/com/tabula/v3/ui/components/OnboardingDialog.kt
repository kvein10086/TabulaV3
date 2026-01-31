package com.tabula.v3.ui.components

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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tabula.v3.R
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 引导弹窗 - 首次使用时展示
 * 
 * 包含三个页面：
 * 1. 欢迎页 - 小黑猫萌图 + 欢迎语
 * 2. 手势演示页 - 四方向手势说明
 * 3. 结束页 - 期待表情 + 开始按钮
 */
@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 主题颜色
    val backgroundColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color.White
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF666666)
    val accentColor = TabulaColors.EyeGold
    
    // Pager 状态
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    Dialog(
        onDismissRequest = { /* 禁止点击外部关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = backgroundColor,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                // 内容区域
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    userScrollEnabled = true
                ) { page ->
                    when (page) {
                        0 -> WelcomePage(
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                        1 -> GesturePage(
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor,
                            isDarkTheme = isDarkTheme
                        )
                        2 -> ReadyPage(
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
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
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isSelected) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) accentColor
                                    else secondaryTextColor.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
                
                // 底部按钮区域 - 只在最后一页显示开始按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pagerState.currentPage == 2) {
                        Button(
                            onClick = {
                                HapticFeedback.heavyTap(context)
                                onComplete()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = TabulaColors.CatBlack
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "开始整理",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 第一页：欢迎页
 */
@Composable
private fun WelcomePage(
    textColor: Color,
    secondaryTextColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 标题
        Text(
            text = "欢迎来到 Tabula 的世界",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = textColor,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 副标题
        Text(
            text = "让整理相册变得简单又有趣",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 第二页：手势演示页
 */
@Composable
private fun GesturePage(
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    isDarkTheme: Boolean
) {
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "gesture")
    
    val upArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "upArrow"
    )
    
    val downArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "downArrow"
    )
    
    val leftArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leftArrow"
    )
    
    val rightArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rightArrow"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            text = "简单手势，高效整理",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = textColor,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 手势演示区域
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // 中心卡片示意
            Box(
                modifier = Modifier
                    .size(70.dp, 90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isDarkTheme) {
                                listOf(Color(0xFF4D4D4D), Color(0xFF3D3D3D))
                            } else {
                                listOf(Color(0xFFF0F0F0), Color(0xFFE0E0E0))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .alpha(0.5f)
                )
            }
            
            // 上方：标记
            GestureIndicator(
                icon = Icons.Rounded.KeyboardArrowUp,
                label = "标记",
                color = TabulaColors.DangerRed,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = upArrowOffset.dp)
            )
            
            // 下方：整理
            GestureIndicator(
                icon = Icons.Rounded.KeyboardArrowDown,
                label = "整理",
                color = TabulaColors.SuccessGreen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = downArrowOffset.dp)
            )
            
            // 左侧：下一张
            GestureIndicator(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                label = "下一张",
                color = accentColor,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = leftArrowOffset.dp),
                isHorizontal = true
            )
            
            // 右侧：上一张
            GestureIndicator(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                label = "上一张",
                color = accentColor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = rightArrowOffset.dp),
                isHorizontal = true
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // 提示文字
        Text(
            text = "滑动卡片即可快速整理照片",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 手势方向指示器
 */
@Composable
private fun GestureIndicator(
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
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp)
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
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * 第三页：准备好了页
 */
@Composable
private fun ReadyPage(
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 期待表情的小黑猫
        Image(
            painter = painterResource(id = R.drawable.ydcat2),
            contentDescription = "Tabula 期待表情",
            modifier = Modifier.size(150.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 标题
        Text(
            text = "准备好了吗？",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = textColor,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 副标题
        Text(
            text = "跟 Tabula 一起整理相册吧！",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp
            ),
            color = secondaryTextColor,
            textAlign = TextAlign.Center
        )
    }
}

