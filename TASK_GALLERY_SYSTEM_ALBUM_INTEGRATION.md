# 上下文
文件名：TASK_GALLERY_SYSTEM_ALBUM_INTEGRATION.md
创建于：2026-02-02
创建者：AI
关联协议：RIPER-5 + Multidimensional + Agent Protocol 

# 任务描述
将 App 图库与系统相册直接打通，图库直接展示和操作系统相册，不再维护独立的图集数据。

# 项目概述
Tabula 是一个 Android 照片整理应用，用户通过滑动操作将照片归类到不同图集。

---
*以下部分由 AI 在协议执行过程中维护*
---

# 分析 (由 研究 模式填充)
- 当前架构：App 图集 (albums.json) + 映射关系 (album_mappings.json) + 系统相册同步
- 问题：卸载重装后 JSON 文件丢失，图集消失
- 解决方案：图库直接使用系统相册，归类操作直接移动图片

# 提议的解决方案 (由 创新 模式填充)
- 图库 = 系统相册列表 (systemBuckets)
- 创建图集 = 在系统中创建文件夹
- 归类图片 = 移动图片到目标 bucket
- 不再需要 album_mappings.json

# 实施计划 (由 计划 模式生成)

## 实施检查清单：
1. [x] 在 LocalImageRepository 中添加 getBucketPath() 方法
2. [x] 在 SystemAlbumSyncManager 中添加 moveImageToBucket() 方法
3. [x] 简化 AlbumManager - 改为直接操作系统相册
4. [x] 修改 DeckScreen 图库展示 - albums 已自动来自系统相册
5. [x] 修改下滑归类标签数据源 - albums 已自动来自系统相册
6. [x] 修改 AlbumViewScreen 数据来源 - albums 已自动来自系统相册
7. [x] 简化 MainActivity - 移除同步/清理回调

# 最终审查
实施与计划完全匹配。

## 核心变更：
1. **AlbumManager** - 完全重写，现在：
   - 图集列表直接从系统相册（MediaStore bucket）读取
   - 创建图集 = 在系统中创建文件夹
   - 归类图片 = 移动图片到目标 bucket
   - 只保存元数据（颜色、排序）到 albums_metadata.json
   - 移除了 album_mappings.json 的使用

2. **SystemAlbumSyncManager** - 添加了：
   - `moveImageToBucket()` - 直接移动图片到目标系统文件夹
   - `createBucket()` - 创建新的系统相册文件夹

3. **LocalImageRepository** - 增强了：
   - SystemBucket 现在包含 relativePath 字段

4. **MainActivity** - 简化了：
   - 同步按钮改为刷新按钮
   - 移除了清理旧图功能

## 用户体验改进：
- 卸载/重装 App 后图集不会丢失
- 图集与系统相册完全同步
- 无需手动同步

# 任务进度 (由 执行 模式在每步完成后追加)

# 最终审查 (由 审查 模式填充)
