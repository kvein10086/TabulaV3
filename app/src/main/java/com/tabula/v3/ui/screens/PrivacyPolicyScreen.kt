package com.tabula.v3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 隐私政策页面
 */
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // 顶部导航栏
        IconButton(
            onClick = {
                HapticFeedback.lightTap(context)
                onNavigateBack()
            },
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = textColor
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // 标题
            Text(
                text = "隐私政策",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "最后更新日期：2026年2月",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // 概述
            SectionTitle("概述", textColor)
            SectionContent(
                """Tabula（以下简称"本应用"）是一款完全离线运行的相册管理应用。我们高度重视您的隐私保护，本隐私政策旨在向您说明本应用如何处理您的数据。

简而言之：您的所有数据都仅存储在您的设备本地，我们不会收集、上传或分享您的任何个人信息。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 数据收集
            SectionTitle("数据收集", textColor)
            SectionContent(
                """本应用不收集任何个人数据。具体而言：

• 不收集设备信息
• 不收集使用统计
• 不收集位置信息
• 不收集任何形式的用户行为数据
• 不使用任何第三方分析或追踪服务""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 本地数据存储
            SectionTitle("本地数据存储", textColor)
            SectionContent(
                """本应用在您的设备上本地存储以下数据：

• 应用设置和偏好（如主题、显示选项等）
• 您创建的图集和标签信息
• 图片的分类和整理记录
• 回收站中的图片信息

所有这些数据都仅存储在您的设备上，不会被传输到任何服务器。卸载应用将删除所有本地存储的应用数据。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 相册访问权限
            SectionTitle("相册访问权限", textColor)
            SectionContent(
                """本应用需要访问您设备上的照片和媒体文件，这是应用核心功能所必需的。该权限仅用于：

• 读取和显示您的照片
• 让您对照片进行分类和整理
• 将照片移动到回收站或删除

我们承诺：您的照片永远不会离开您的设备，不会被上传到任何服务器或云端存储。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 网络访问
            SectionTitle("网络访问", textColor)
            SectionContent(
                """本应用的核心功能完全离线运行，不需要网络连接。唯一的网络访问场景是：

• 当您主动点击"检查更新"时，应用会连接服务器检查是否有新版本可用

除此之外，应用不会：
• 自动连接任何服务器
• 上传您的照片或任何个人数据
• 在后台进行任何网络通信

您的照片和使用数据永远不会通过网络传输。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 第三方服务
            SectionTitle("第三方服务", textColor)
            SectionContent(
                """本应用不集成任何第三方服务，包括但不限于：

• 广告服务
• 数据分析服务
• 社交媒体集成
• 云存储服务
• 崩溃报告服务

这意味着没有任何第三方可以通过本应用访问您的数据。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 数据安全
            SectionTitle("数据安全", textColor)
            SectionContent(
                """由于所有数据都存储在您的设备本地，数据的安全性取决于您设备的安全设置。我们建议您：

• 设置设备锁屏密码或生物识别
• 定期备份重要照片
• 保持设备操作系统更新

本应用不提供额外的加密功能，您的数据安全依赖于设备本身的安全机制。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 儿童隐私
            SectionTitle("儿童隐私", textColor)
            SectionContent(
                """本应用不针对13岁以下儿童，也不会有意收集儿童的个人信息。由于本应用不收集任何用户数据，因此不存在儿童隐私问题。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 政策变更
            SectionTitle("政策变更", textColor)
            SectionContent(
                """如果我们对本隐私政策进行重大变更，我们将通过应用更新说明通知您。我们建议您定期查阅本政策以了解最新信息。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 联系我们
            SectionTitle("联系我们", textColor)
            SectionContent(
                """如果您对本隐私政策有任何疑问或建议，欢迎通过应用内的"支持与反馈"功能与我们联系。""",
                secondaryTextColor
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 底部声明
            Text(
                text = "感谢您选择 Tabula。我们承诺始终尊重并保护您的隐私。",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun SectionTitle(title: String, textColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SectionContent(content: String, textColor: Color) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        lineHeight = 24.sp
    )
}
