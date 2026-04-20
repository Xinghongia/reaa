# CheckSourceService - 书源校验服务

## 概述

`CheckSourceService` 用于校验书源的有效性，测试书源的搜索、目录获取、内容解析等功能是否正常工作。

**源码位置**：`app/src/main/java/io/legado/app/service/CheckSourceService.kt`

## 核心设计

### 1. 协程线程池

```kotlin
private var threadCount = AppConfig.threadCount
private var searchCoroutine =
    Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD))
        .asCoroutineDispatcher()
```

### 2. 校验流程

```kotlin
private fun check(ids: List<String>) {
    val bookSources = ids.mapNotNull { appDb.bookSourceDao.getBookSource(it) }
    originSize = bookSources.size
    finishCount = 0

    lifecycleScope.launch(searchCoroutine) {
        bookSources.forEach { bookSource ->
            launch {
                verifyBookSource(bookSource)
            }
        }
    }
}
```

### 3. 校验步骤

```kotlin
private suspend fun verifyBookSource(bookSource: BookSource) {
    try {
        // 1. 测试搜索功能
        val searchRule = bookSource.searchRule
        if (searchRule.isNotBlank()) {
            val result = AnalyzeUrl(bookSource.getSearchUrl())
                .analyzeBookListAwait(bookSource, searchRule)
            if (result.isEmpty()) {
                Debug.putError(bookSource.bookSourceUrl, "搜索结果为空")
                return
            }
        }

        // 2. 测试获取目录
        val tocRule = bookSource.catalogRule
        if (tocRule.isNotBlank()) {
            val book = SearchRepository.getBookFromSearchResult(result.first())
            val tocResult = WebBook.getChapterListAwait(bookSource, book)
            if (tocResult.getOrNull().isNullOrEmpty()) {
                Debug.putError(bookSource.bookSourceUrl, "目录为空")
                return
            }
        }

        // 3. 测试获取内容
        val contentRule = bookSource.contentRule
        if (contentRule.isNotBlank()) {
            val chapter = tocResult.getOrNull()?.firstOrNull()
            if (chapter != null) {
                val contentResult = WebBook.getChapterContentAwait(bookSource, book, chapter)
                if (contentResult.getOrNull().isNullOrBlank()) {
                    Debug.putError(bookSource.bookSourceUrl, "内容为空")
                }
            }
        }

        Debug.putSuccess(bookSource.bookSourceUrl)
    } catch (e: Exception) {
        Debug.putError(bookSource.bookSourceUrl, e.localizedMessage)
    } finally {
        finishCount++
        upNotification()
        postEvent(EventBus.CHECK_SOURCE_PROGRESS, finishCount)
    }
}
```

## 校验规则

### 校验项目

| 步骤 | 测试内容 | 规则字段 |
|------|---------|---------|
| 1 | 搜索书籍 | `searchRule` |
| 2 | 获取目录 | `catalogRule` |
| 3 | 获取正文 | `contentRule` |

### 校验状态

```kotlin
data class DebugResult(
    val bookSourceUrl: String,
    val state: Int,  // 0=未测试, 1=成功, 2=失败
    val message: String?,
    val time: Long
)
```

## 通知管理

```kotlin
private val notificationBuilder by lazy {
    NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
        .setSmallIcon(R.drawable.ic_network_check)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentTitle(getString(R.string.check_book_source))
        .addAction(R.drawable.ic_stop_black_24dp, getString(R.string.cancel),
            servicePendingIntent<CheckSourceService>(IntentAction.stop))
}
```

## 并发控制

```kotlin
// 同时最多校验 threadCount 个书源
lifecycleScope.launch(searchCoroutine) {
    bookSources.forEach { bookSource ->
        launch {
            verifyBookSource(bookSource)
        }
    }
}
```

## Debug 单例

```kotlin
object Debug {
    private val results = ConcurrentHashMap<String, DebugResult>()

    fun putSuccess(url: String) {
        results[url] = DebugResult(url, 1, null, System.currentTimeMillis())
    }

    fun putError(url: String, message: String) {
        results[url] = DebugResult(url, 2, message, System.currentTimeMillis())
    }

    fun finishChecking() {
        results.clear()
    }
}
```

## 数据流

```
用户选择书源 → IntentAction.start
    ↓
获取 BookSource 列表
    ↓
searchCoroutine.launch { } 并发校验
    ↓
┌─────────────────────────────────────┐
│ 1. AnalyzeUrl.analyzeBookListAwait()│
│ 2. WebBook.getChapterListAwait()    │
│ 3. WebBook.getChapterContentAwait() │
└─────────────────────────────────────┘
    ↓
Debug.putSuccess/Error() 记录结果
    ↓
postEvent(EventBus.CHECK_SOURCE_PROGRESS)
    ↓
通知栏更新进度
```

## 学习任务

1. **打开源码文件**：`service/CheckSourceService.kt` + `model/Debug.kt`
2. **理解校验流程**：搜索 → 目录 → 内容
3. **分析并发控制**：线程池 + launch
4. **思考**：为什么要逐项测试而不是一次性测试？

## 设计亮点

1. **三级校验**：搜索、目录、内容逐步验证
2. **并发校验**：多线程同时校验多个书源
3. **超时控制**：使用 `withTimeout` 防止单个书源卡死
4. **实时反馈**：通过 EventBus 实时推送校验进度
5. **错误定位**：记录具体的错误步骤和错误信息
