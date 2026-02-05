package com.tabula.v3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.Album
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 隐藏与屏蔽图集管理页面
 * 
 * 统一管理：
 * - 已隐藏图集：不在图集列表中显示
 * - 已屏蔽图集：不在推荐流中出现
 */
@Composable
fun HiddenAlbumsScreen(
    hiddenAlbums: List<Album>,
    excludedAlbums: List<Album>,
    onUnhideAlbum: (String) -> Unit,
    onUnexcludeAlbum: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val cardColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = Color(0xFF8E8E93)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // 顶部栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onNavigateBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "隐藏与屏蔽",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ========== 已隐藏图集 ==========
            SectionHeader("已隐藏图集", textColor)
            
            if (hiddenAlbums.isEmpty()) {
                EmptyStateCard(
                    cardColor = cardColor,
                    textColor = secondaryTextColor,
                    message = "没有隐藏的图集",
                    description = "隐藏的图集不会在图集列表中显示"
                )
            } else {
                AlbumListCard(cardColor) {
                    hiddenAlbums.forEachIndexed { index, album ->
                        AlbumItem(
                            album = album,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            actionText = "取消隐藏",
                            actionColor = Color(0xFF34C759), // Green
                            onAction = { 
                                HapticFeedback.lightTap(context)
                                onUnhideAlbum(album.id) 
                            }
                        )
                        if (index < hiddenAlbums.lastIndex) {
                            AlbumListDivider(isDarkTheme)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ========== 已屏蔽图集 ==========
            SectionHeader("已屏蔽图集", textColor)
            
            // 说明文字
            Text(
                text = "已屏蔽的图集中的照片不会出现在推荐流中",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (excludedAlbums.isEmpty()) {
                EmptyStateCard(
                    cardColor = cardColor,
                    textColor = secondaryTextColor,
                    message = "没有屏蔽的图集",
                    description = "可以在图集菜单中屏蔽不想推荐的图集"
                )
            } else {
                AlbumListCard(cardColor) {
                    excludedAlbums.forEachIndexed { index, album ->
                        AlbumItem(
                            album = album,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            actionText = "取消屏蔽",
                            actionColor = Color(0xFFFF9500), // Orange
                            onAction = { 
                                HapticFeedback.lightTap(context)
                                onUnexcludeAlbum(album.id) 
                            }
                        )
                        if (index < excludedAlbums.lastIndex) {
                            AlbumListDivider(isDarkTheme)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * 图集列表卡片容器
 */
@Composable
private fun AlbumListCard(
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
    ) {
        content()
    }
}

/**
 * 图集项
 */
@Composable
private fun AlbumItem(
    album: Album,
    textColor: Color,
    secondaryTextColor: Color,
    actionText: String,
    actionColor: Color,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图集颜色指示器
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    album.color?.let { Color(it) } 
                        ?: Color(0xFF8E8E93).copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (album.color != null) Color.White else Color(0xFF8E8E93),
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        // 图集信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.imageCount} 张照片",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
        }
        
        // 操作按钮
        TextButton(
            onClick = onAction
        ) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = actionColor
            )
        }
    }
}

/**
 * 空状态卡片
 */
@Composable
private fun EmptyStateCard(
    cardColor: Color,
    textColor: Color,
    message: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.3f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = textColor.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.4f)
        )
    }
}

/**
 * 分割线
 */
@Composable
private fun AlbumListDivider(isDarkTheme: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(0.5.dp)
            .background(
                if (isDarkTheme) Color(0xFF38383A) else Color(0xFFE5E5EA)
            )
    )
}
