# 阅读器核心 (ReadBook)

## 概述

阅读器核心负责电子书内容的加载、渲染、翻页、进度管理等核心功能。

## 源码位置

```
app/src/main/java/io/legado/app/model/
├── ReadBook.kt       # 阅读器核心 (40KB+)
├── ReadManga.kt     # 漫画阅读器
├── CacheBook.kt     # 书籍缓存管理
├── BookCover.kt     # 封面处理
└── SourceCallBack.kt # 书源回调
```

## 核心类

### ReadBook - 阅读器核心类

**文件**: `model/ReadBook.kt` (约 40KB，1000+ 行)

**职责**:
- 章节内容加载
- 内容分页
- 进度保存
- 样式管理

**设计分析**:

| 功能 | 设计 | 原理 |
|------|------|------|
| 内容加载 | 协程异步 | 不阻塞 UI |
| 分页算法 | 动态计算 | 根据屏幕大小和字体 |
| 进度保存 | 持久化 | 保存到 Room |
| 样式配置 | 内存缓存 | ReadConfig |

### ReadBook 核心流程

```
打开书籍
    ↓
LoadBookContent (加载章节内容)
    ↓
ParseContent (解析内容)
    ↓
Paginate (分页)
    ↓
RenderPage (渲染页面)
    ↓
用户翻页
    ↓
SaveProgress (保存进度)
```

## 内容加载

### 章节内容获取

**打开** `ReadBook.kt`，查找 `loadContent()` 方法：

```kotlin
suspend fun loadContent(chapter: BookChapter): String? {
    // 1. 检查本地缓存
    val cached = localCache.get(chapter.url)
    if (cached != null) return cached

    // 2. 网络获取
    val content = fetchFromNetwork(chapter)

    // 3. 内容净化
    val cleaned = content?.let { replaceRule.apply(it) }

    // 4. 缓存
    cleaned?.let { localCache.put(chapter.url, it) }

    return cleaned
}
```

**设计分析**:

| 步骤 | 设计 | 好处 |
|------|------|------|
| 本地缓存 | 内存缓存 | 避免重复加载 |
| 网络获取 | 协程 | 异步不阻塞 |
| 内容净化 | ReplaceRule | 去除广告 |
| 持久化缓存 | Room | 重启后可用 |

## 分页算法

### 分页原理

```
总内容长度 ÷ 每页可显示字数 = 总页数
```

**打开** `ReadBook.kt`，查找 `paginate()` 方法：

```kotlin
fun paginate(content: String, pageWidth: Int, pageHeight: Int): List<String> {
    val pages = mutableListOf<String>()
    val lines = content.split("\n")

    var currentPage = StringBuilder()
    var currentHeight = 0

    for (line in lines) {
        val lineHeight = measureLineHeight(line)
        if (currentHeight + lineHeight > pageHeight) {
            pages.add(currentPage.toString())
            currentPage.clear()
            currentHeight = 0
        }
        currentPage.append(line)
        currentHeight += lineHeight
    }

    return pages
}
```

**设计分析**:

| 要素 | 说明 |
|------|------|
| 字符宽度 | 中文字符宽度 ≠ 英文字符 |
| 行高 | 字体大小 × 行间距 |
| 段落 | 换行符分割 |
| 翻页方向 | 覆盖/仿真/滑动 |

## 进度管理

### 进度保存时机

```
用户翻页 → 自动保存
退出阅读 → 自动保存
切换章节 → 自动保存
App 切换 → 自动保存
```

### 保存内容

```kotlin
data class Progress(
    val chapterIndex: Int,    // 当前章节
    val pageIndex: Int,       // 当前页
    val charIndex: Long,      // 字符位置 (精确)
    val scrollOffset: Int     // 滚动偏移
)
```

**打开** `ReadBook.kt`，查找 `saveProgress()` 方法：

```kotlin
suspend fun saveProgress() {
    val book = currentBook ?: return
    val chapter = currentChapter ?: return

    // 更新 Book 实体
    book.durChapter = chapter.index
    book.durChapterPos = currentPosition

    // 持久化
    bookRepository.updateBook(book)
}
```

## 净化规则应用

### ReplaceRule 处理流程

```
原始内容
    ↓
Rule 1 (优先级最高)
    ↓
Rule 2
    ↓
...
    ↓
Rule N (优先级最低)
    ↓
净化后内容
```

**打开** `CacheBook.kt`，查找 `replaceContent()` 方法：

```kotlin
fun replaceContent(content: String, rules: List<ReplaceRule>): String {
    var result = content
    for (rule in rules.sortedBy { it.priority }) {
        result = when (rule.type) {
            0 -> result.replace(rule.pattern, rule.replacement)
            1 -> result.replaceFirst(rule.pattern, rule.replacement)
            2 -> Regex(rule.pattern).replace(result, rule.replacement)
        }
    }
    return result
}
```

## 阅读模式

### 翻页模式

| 模式 | 说明 | 实现 |
|------|------|------|
| 覆盖 | 新页覆盖旧页 | 最常用 |
| 仿真 | 仿真书页翻动 | Canvas 动画 |
| 滑动 | 跟随手势滑动 | ViewPager2 |
| 无效果 | 即时切换 | 最简单 |

### 自动阅读

**文件**: `model/ReadAloud.kt`

**原理**:

```kotlin
// TTS 朗读
val tts = TextToSpeech(context) { engine ->
    tts.speak(content, QUEUE_FLUSH, null, null)
}

// 自动翻页
fun startAutoRead() {
    timer.scheduleAtFixedRate(30000) { // 30秒
        nextPage()
    }
}
```

## CacheBook - 缓存管理

**文件**: `model/CacheBook.kt`

**职责**:

| 功能 | 说明 |
|------|------|
| 章节预加载 | 当前章节 ±2 预加载 |
| 缓存清理 | 清理过期缓存 |
| 批量下载 | 下载整个书籍 |

**设计分析**:

```kotlin
class CacheBook(private val book: Book) {

    // 预加载策略
    suspend fun preload(chapterIndex: Int) {
        listOf(chapterIndex - 2, chapterIndex - 1, chapterIndex + 1, chapterIndex + 2)
            .filter { it >= 0 && it < totalChapters }
            .forEach { loadChapter(it) }
    }

    // LRU 缓存淘汰
    fun trimCache(maxSize: Int) {
        while (cacheSize > maxSize) {
            val oldest = cache.removeOldest()
            cacheSize -= oldest.size
        }
    }
}
```

## 学习任务

1. **打开** `ReadBook.kt`，分析 `loadContent()` 流程
2. **理解** 分页算法如何适应不同屏幕
3. **分析** 进度保存的时机和内容
4. **查看** `CacheBook.kt` 的预加载策略

## 相关文件

| 文件 | 说明 |
|------|------|
| `model/ReadBook.kt` | 阅读器核心 |
| `model/CacheBook.kt` | 缓存管理 |
| `model/ReadManga.kt` | 漫画阅读 |
| `model/ReadAloud.kt` | 朗读功能 |
| `data/entities/Book.kt` | 阅读配置字段 |
| `data/entities/ReplaceRule.kt` | 净化规则 |
