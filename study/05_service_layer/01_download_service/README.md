# DownloadService - 文件下载服务

## 概述

`DownloadService` 是 Android 系统下载管理器的封装服务，利用 `DownloadManager` API 实现文件下载功能。

**源码位置**：`app/src/main/java/io/legado/app/service/DownloadService.kt`

## 核心设计

### 1. 委托系统 DownloadManager

```kotlin
private val downloadManager: DownloadManager by lazy {
    appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
}
```

**为什么使用 DownloadManager？**
- 系统级下载管理，不被杀
- 支持大文件下载
- 自动重试
- 下载完成通知
- 可以显示下载进度

### 2. 下载信息管理

```kotlin
private val downloads = hashMapOf<Long, DownloadInfo>()
private val completeDownloads = hashSetOf<Long>()

data class DownloadInfo(
    val url: String,
    val fileName: String,
    val notificationId: Int
)
```

- `downloads`: 保存正在下载的任务
- `completeDownloads`: 保存已完成任务的 ID（用于避免重复处理）

### 3. Android 12+ 广播注册

```kotlin
ContextCompat.registerReceiver(
    this,
    downloadReceiver,
    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
    ContextCompat.RECEIVER_EXPORTED
)
```

## Intent Action 处理

```kotlin
when (intent?.action) {
    IntentAction.start -> startDownload(url, fileName)
    IntentAction.play -> openDownload(id, fileName)  // 下载完成，打开文件
    IntentAction.stop -> removeDownload(downloadId)   // 取消下载
}
```

## 核心流程

### 启动下载

```kotlin
private fun startDownload(url: String?, fileName: String?) {
    val request = DownloadManager.Request(Uri.parse(url))
    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
    request.setDestinationInExternalPublicDir(
        Environment.DIRECTORY_DOWNLOADS,  // 保存到 Downloads 目录
        fileName
    )
    val downloadId = downloadManager.enqueue(request)
    downloads[downloadId] = DownloadInfo(url, fileName, notificationId)
    queryState()
}
```

### 查询下载状态

```kotlin
private fun queryState() {
    val query = DownloadManager.Query()
    query.setFilterById(*ids.toLongArray())
    downloadManager.query(query).use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val progress = cursor.getInt(progressIndex)
            val max = cursor.getInt(fileSizeIndex)
            val status = cursor.getInt(statusIndex)
            // STATUS_PAUSED, STATUS_PENDING, STATUS_RUNNING,
            // STATUS_SUCCESSFUL, STATUS_FAILED
        }
    }
}
```

### 循环检查状态

```kotlin
private fun checkDownloadState() {
    upStateJob = lifecycleScope.launch {
        while (isActive) {
            queryState()
            delay(1000)  // 每秒查询一次
        }
    }
}
```

## 状态转换图

```
用户点击下载
    ↓
enqueue() 添加任务
    ↓
状态: STATUS_PENDING (等待中)
    ↓
状态: STATUS_RUNNING (下载中) ←── 每秒查询进度
    ↓
┌─────────────────────────────────────┐
│ 成功: STATUS_SUCCESSFUL              │
│ 失败: STATUS_FAILED                  │
│ 暂停: STATUS_PAUSED                  │
└─────────────────────────────────────┘
    ↓
通知用户
```

## 通知管理

每个下载任务有独立的 `notificationId`，支持：
- 显示下载进度
- 显示下载状态文字
- 点击取消下载

## 与 CacheBookService 的区别

| 维度 | DownloadService | CacheBookService |
|------|----------------|------------------|
| 用途 | 通用文件下载 | 书籍章节缓存 |
| 引擎 | Android DownloadManager | 自定义协程池 |
| 存储 | 系统 Downloads 目录 | 应用私有目录 |
| 进度 | 系统通知栏 | 自定义通知栏 |
| 控制粒度 | 粗粒度（整个文件） | 细粒度（章节级别） |

## 学习任务

1. **打开源码文件**：`service/DownloadService.kt`
2. **理解 DownloadManager.Request 配置**
3. **观察 query() 返回的 Cursor 结构**
4. **思考**：为什么使用 `use { cursor }` 自动关闭资源？

## 设计亮点

1. **委托系统能力**：不重复造轮子，利用系统下载管理
2. **状态轮询**：每秒查询状态而不是依赖事件
3. **内存管理**：使用 `Synchronized` 方法保证线程安全
4. **错误处理**：区分不同错误类型（权限问题、网络问题等）
