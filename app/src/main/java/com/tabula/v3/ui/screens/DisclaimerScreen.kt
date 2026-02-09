package com.tabula.v3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.R
import com.tabula.v3.ui.util.HapticFeedback

// iOS 18 风格颜色（与引导页/个性化页保持一致）
private val iOS18Blue = Color(0xFF007AFF)
private val iOS18TextPrimary = Color(0xFF000000)
private val iOS18TextSecondary = Color(0xFF8E8E93)
private val iOS18Background = Color(0xFFFFFFFF)
private val iOS18SheetBackground = Color(0xFFF2F2F7)

/**
 * 使用前须知页面 - iOS 18 风格（与引导页/个性化页风格统一）
 *
 * 在个性化设置完成后、进入主界面前显示。
 * 用户必须滑动到底部看完所有内容后，才能点击"我已知晓"按钮。
 */
@Composable
fun DisclaimerScreen(
    onAcknowledged: () -> Unit
) {
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    // 检测是否滑到底部
    var hasScrolledToBottom by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState) {
        snapshotFlow {
            val maxScroll = scrollState.maxValue
            val currentScroll = scrollState.value
            // 容差 50px，认为已经到底
            maxScroll > 0 && currentScroll >= maxScroll - 50
        }.collect { atBottom ->
            if (atBottom) {
                hasScrolledToBottom = true
            }
        }
    }

    // 全屏白色背景 - iOS 18 风格
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
            // 可滚动的主内容
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // 小黑猫图片
                Image(
                    painter = painterResource(id = R.drawable.ydcat2),
                    contentDescription = "Tabula 小黑猫",
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 标题 - iOS 风格大标题
                Text(
                    text = "使用前须知",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = iOS18TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "请仔细阅读以下内容，了解 Tabula 的工作方式",
                    fontSize = 15.sp,
                    color = iOS18TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ========== 第一条 ==========
                DisclaimerCard(
                    number = "1",
                    title = "HDR / 实况照片 格式兼容性",
                    content = "由于各手机厂商和系统版本对 HDR、Live（实况）等特殊格式照片的处理策略不同，" +
                            "建议您先尝试将少量 Live / HDR 格式的照片进行归档操作，然后前往系统相册检查能否正常显示。" +
                            "确认无误后再批量使用，以避免造成误会和不好的体验。",
                    footer = "💡 如您的机型或系统版本无法成功识别 HDR 或 Live 格式照片，建议联系作者反馈，我们会尽力适配。"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ========== 第二条 ==========
                DisclaimerCard(
                    number = "2",
                    title = "部分实况照片归档后无法在相册播放",
                    content = "部分格式的实况照片在系统相册中本身就无法播放动态效果，归档到目标文件夹后也同样不能播放，" +
                            "但在 Tabula 内可以正常播放。这是正常现象，因为各手机厂商的实况照片采用了私有协议，" +
                            "系统相册不一定能解析所有格式。",
                    footer = null
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ========== 第三条 ==========
                DisclaimerCard(
                    number = "3",
                    title = "原位置图片的归档策略",
                    content = "每次您在图库中点击归档时，操作的都是原位置的图片。由于 Android 系统安全权限的限制，" +
                            "Tabula 采用的是先尝试「移动」，若移动失败则尝试「复制 + 删除原文件」的策略，" +
                            "以保证操作的成功率，希望您能够理解。",
                    footer = null
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 未滑到底的下滑提示
                if (!hasScrolledToBottom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = iOS18TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "请滑动到底部查看全部内容",
                            fontSize = 13.sp,
                            color = iOS18TextSecondary
                        )
                    }
                }

                // 底部留白，确保能滚到底
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ========== 底部按钮区域（固定在底部） ==========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = hasScrolledToBottom,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                ) {
                    Button(
                        onClick = {
                            HapticFeedback.heavyTap(context)
                            onAcknowledged()
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
                            text = "我已知晓，开始使用",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                    }
                }

                // 按钮未出现时显示灰色禁用按钮
                if (!hasScrolledToBottom) {
                    Button(
                        onClick = { /* 不可点击 */ },
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFFD1D1D6),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "请先阅读完以上内容",
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 须知卡片组件 - iOS 18 风格
 */
@Composable
private fun DisclaimerCard(
    number: String,
    title: String,
    content: String,
    footer: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(iOS18SheetBackground)
            .padding(16.dp)
    ) {
        // 编号 + 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 编号圆形标记
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iOS18Blue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = iOS18Blue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = iOS18TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 正文
        Text(
            text = content,
            fontSize = 15.sp,
            color = iOS18TextSecondary,
            lineHeight = 22.sp
        )

        // 补充说明
        if (footer != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = footer,
                fontSize = 13.sp,
                color = iOS18Blue.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}
