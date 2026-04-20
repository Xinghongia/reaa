# CacheBookService - 书籍缓存服务

## 概述

`CacheBookService` 实现书籍离线缓存功能，支持多线程并发下载书籍章节，将内容存储到应用私有目录。

**源码位置**：`app/src/main/java/io/legado/app/service/CacheBookService.kt`

## 核心设计

### 1. 协程线程池

```kotlin
private val threadCount = AppConfig.threadCount
private var cachePool =
    Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD))
        .asCoroutineDispatcher()
```

- 可配置线程数（根据用户设置）
- 最大不超过 `MAX_THREAD` (16)
- 使用协程调度器，方便取消和控制

### 2. 缓存管理核心类

```kotlin
object CacheBook {
    val cacheBookMap = ConcurrentHashMap<String, CacheBookModel>()

    @Synchronized
    fun getOrCreate(bookUrl: String): CacheBookModel? {
        val book = appDb.bookDao.getBook(bookUrl) ?: return null
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin) ?: return null
        // ...
    }
}
```

### 3. CacheBookModel

```kotlin
class CacheBookModel(
    val bookSource: BookSource,
    var book: Book
) {
    val waitCount: Int  // 等待下载的章节数
    val onDownloadCount: Int  // 正在下载的章节数
    val successDownloadSet: Set<Int>  // 已完成的章节索引
    val errorDownloadMap: Map<Int, String>  // 失败的章节和错误信息
}
```

## 下载流程

### 1. 启动下载

```kotlin
IntentAction.start -> {
    val bookUrl = intent.getStringExtra("bookUrl") ?: return@let
    val indices = intent.getIntegerArrayListExtra("indices")
    addDownloadData(bookUrl, indices ?: emptyList)
}
```

### 2. 添加下载任务

```kotlin
private fun addDownloadData(bookUrl: String?, indices: List<Int>) {
    val cacheBook = CacheBook.getOrCreate(bookUrl) ?: return

    // 并发下载章节
    indices.forEachParallel { index ->
        cacheBook.download(
            scope = lifecycleScope,
            dispatcher = cachePool,
            index = index
        )
    }
}
```

### 3. 章节下载实现

```kotlin
suspend fun download(index: Int) {
    // 1. 获取章节信息
    val chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
        ?: return

    // 2. 获取内容
    val content = WebBook.getChapterContentAwait(bookSource, book, chapter)
        ?: throw ContentEmptyException("内容为空")

    // 3. 净化内容
    val cleanContent = ContentProcessor.getInstance()
        .getContent(bookSource, book, chapter, content)

    // 4. 保存到数据库
    chapter.setContent(cleanContent)
    appDb.bookChapterDao.update(chapter)

    // 5. 发射成功事件
    _cacheSuccessFlow.emit(chapter)
}
```

## 并发控制

### Semaphore 限流

```kotlin
private val semaphore = Semaphore(maxConcurrent)

suspend fun download() {
    semaphore.withPermit {
        // 下载逻辑
    }
}
```

### Mutex 互斥

```kotlin
private val mutex = Mutex()

suspend fun updateProgress() {
    mutex.withLock {
        // 更新进度
    }
}
```

## 通知管理

```kotlin
private val notificationBuilder by lazy {
    NotificationCompat.Builder(this, AppConst.channelIdDownload)
        .setSmallIcon(R.drawable.ic_download)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentTitle(getString(R.string.offline_cache))
        .addAction(R.drawable.ic_stop_black_24dp, getString(R.string.cancel),
            servicePendingIntent<CacheBookService>(IntentAction.stop))
}
```

## 数据流图

```
用户选择章节 → IntentAction.start
    ↓
CacheBook.getOrCreate() 获取/创建缓存模型
    ↓
indices.forEachParallel() 并发遍历章节
    ↓
CacheBookModel.download() 逐章下载
    ↓
┌──────────────────────────────────────────┐
│ 1. 获取章节信息                           │
│ 2. WebBook.getChapterContentAwait()      │
│ 3. ContentProcessor.getInstance().getContent() │
│ 4. 保存到数据库                           │
│ 5. 发射成功事件                           │
└──────────────────────────────────────────┘
    ↓
进度更新通知 → FlowEventBus.post(EventBus.UP_DOWNLOAD)
```

## 与 DownloadService 对比

| 维度 | CacheBookService | DownloadService |
|------|-----------------|-----------------|
| 目标 | 书籍章节内容 | 任意文件 |
| 存储 | 应用私有目录（加密） | 系统 Downloads |
| 粒度 | 章节级别 | 文件级别 |
| 控制 | 协程 + Flow | 系统 API |
| 进度 | 自定义通知 | 系统通知 |

## 学习任务

1. **打开源码文件**：`service/CacheBookService.kt` + `model/CacheBook.kt`
2. **理解 CacheBookModel 的数据结构**
3. **分析 forEachParallel 的实现**
4. **思考**：为什么要用 Mutex 而不是 synchronized？

## 设计亮点

1. **细粒度缓存**：支持按章节缓存，可选择性下载
2. **并发控制**：Semaphore 限制同时下载数
3. **状态流**：使用 Flow 实时推送下载状态
4. **错误恢复**：记录失败章节，可重试
5. **进度聚合**：统计所有书籍的总体进度
