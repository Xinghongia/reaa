# ExportBookService - 书籍导出服务

## 概述

`ExportBookService` 实现书籍导出功能，支持将书籍导出为 TXT、EPUB 等格式，存储到用户指定的目录。

**源码位置**：`app/src/main/java/io/legado/app/service/ExportBookService.kt`

## 导出格式

| 格式 | 说明 | 依赖 |
|------|------|------|
| TXT | 纯文本 | 标准库 |
| EPUB | 电子书 | epublib 库 |

## 核心设计

### 导出配置

```kotlin
data class ExportConfig(
    val path: String,       // 导出路径
    val type: String,       // 导出格式: txt, epub
    val epubSize: Int = 1,  // 分卷大小
    val epubScope: String? = null  // 导出范围
)

private val waitExportBooks = linkedMapOf<String, ExportConfig>()
```

### Intent Action

```kotlin
IntentAction.start -> {
    val bookUrl = intent.getStringExtra("bookUrl")!!
    val path = intent.getStringExtra("path")!!
    val type = intent.getStringExtra("type")!!
    val epubSize = intent.getIntExtra("epubSize", 1)
    val epubScope = intent.getStringExtra("epubScope")

    waitExportBooks[bookUrl] = ExportConfig(path, type, epubSize, epubScope)
    startExport()
}
```

## TXT 导出

### 流程

```kotlin
private suspend fun exportToTxt(book: Book, path: String) {
    // 1. 获取所有章节
    val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)

    // 2. 构建文件
    val file = File(path, "${book.name}.txt")
    file.bufferedWriter(Charset.forName(book.bookCharset)).use { writer ->
        chapters.forEachIndexed { index, chapter ->
            ensureActive()
            upProgress(book.bookUrl, index, chapters.size)

            // 写入章节标题
            writer.write("\n${chapter.title}\n\n")

            // 写入章节内容
            chapter.getContent()?.let { content ->
                writer.write(content)
            }
        }
    }

    // 3. 完成
    exportMsg[book.bookUrl] = appCtx.getString(R.string.export_success)
}
```

## EPUB 导出

### 流程

```kotlin
private suspend fun exportToEpub(book: Book, config: ExportConfig) {
    // 1. 创建 EPUB
    val epubBook = EpubBook().apply {
        metadata = Metadata().apply {
            addTitle(book.name)
            addAuthor(Author(book.author))
            language = "zh"
        }
    }

    // 2. 获取所有章节
    val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)

    // 3. 添加章节
    chapters.forEachIndexed { index, chapter ->
        ensureActive()
        upProgress(book.bookUrl, index, chapters.size)

        val content = chapter.getContent() ?: return@forEachIndexed

        // 创建资源
        val resource = Resource(
            content.toByteArray(Charset.forName(book.bookCharset)),
            chapter.title + ".xhtml"
        )

        // 添加到 EPUB
        epubBook.addSection(chapter.title, resource)
    }

    // 4. 写入文件
    val file = File(config.path, "${book.name}.epub")
    EpubWriter().writeEpub(epubBook, file, "UTF-8")
}
```

## 分卷导出

```kotlin
private suspend fun exportEpubWithScope(book: Book, config: ExportConfig) {
    val scope = config.epubScope?.split(",")?.map { it.toInt() } ?: return
    val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        .slice(scope[0] until scope[1])

    // 按分卷大小分组
    chapters.chunked(config.epubSize).forEachIndexed { index, chunk ->
        val epubBook = createEpubBook(book, chunk)
        val file = File(config.path, "${book.name}_${index + 1}.epub")
        EpubWriter().writeEpub(epubBook, file, "UTF-8")
    }
}
```

## 进度管理

```kotlin
companion object {
    val exportProgress = ConcurrentHashMap<String, Int>()  // bookUrl -> progress
    val exportMsg = ConcurrentHashMap<String, String>()    // bookUrl -> message
}

private fun upProgress(bookUrl: String, current: Int, total: Int) {
    exportProgress[bookUrl] = (current * 100) / total
    // 更新通知栏
}
```

## 导出队列

```kotlin
private var exportJob: Job? = null

private fun startExport() {
    exportJob?.cancel()
    exportJob = lifecycleScope.launch {
        waitExportBooks.forEach { (bookUrl, config) ->
            val book = appDb.bookDao.getBook(bookUrl) ?: return@forEach
            kotlin.runCatching {
                when (config.type) {
                    "txt" -> exportToTxt(book, config.path)
                    "epub" -> exportToEpub(book, config)
                }
            }.onFailure {
                exportMsg[bookUrl] = it.localizedMessage ?: "导出失败"
            }
            waitExportBooks.remove(bookUrl)
        }
    }
}
```

## 数据流

```
用户点击导出 → IntentAction.start
    ↓
waitExportBooks[bookUrl] = ExportConfig
    ↓
startExport()
    ↓
lifecycleScope.launch {
    for ((bookUrl, config) in waitExportBooks) {
        val book = appDb.bookDao.getBook(bookUrl)
        when (config.type) {
            "txt" -> exportToTxt(book, config.path)
            "epub" -> exportToEpub(book, config)
        }
    }
}
    ↓
upProgress() 更新进度
    ↓
exportMsg[bookUrl] = "导出成功"
```

## 学习任务

1. **打开源码文件**：`service/ExportBookService.kt`
2. **理解 TXT 导出流程**
3. **分析 EPUB 导出中的 Metadata 设置**
4. **思考**：为什么需要 `ensureActive()` 检查协程状态？

## 设计亮点

1. **队列管理**：支持多个导出任务排队
2. **格式抽象**：TXT/EPUB 共用导出框架
3. **分卷支持**：EPUB 支持按章节数分卷
4. **字符编码**：使用书籍原始编码（bookCharset）保证内容正确
5. **进度追踪**：实时更新导出进度
