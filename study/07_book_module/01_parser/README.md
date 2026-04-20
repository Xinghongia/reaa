# 书籍解析器

## 概述

每个格式对应一个解析器，负责读取文件、解析目录、提取正文。解析器实现统一的 `BaseLocalBookParse` 接口，由 `LocalBook` 工厂统一调度。

**源码位置**：`app/src/main/java/io/legado/app/model/localBook/`

---

## 统一接口

### BaseLocalBookParse 接口

```kotlin
interface BaseLocalBookParse {
    fun upBookInfo(book: Book)                      // 更新书籍元信息
    fun getChapterList(book: Book): ArrayList<BookChapter>  // 获取目录
    fun getContent(book: Book, chapter: BookChapter): String?  // 获取正文
    fun getImage(book: Book, href: String): InputStream?  // 获取图片
}
```

**设计原理 - 接口隔离**：
- 每个解析器只需实现用到的功能
- 不强制实现不需要的方法（如 TXT 不需要 `getImage`）
- 便于扩展新格式

---

## TXT 解析器

### 核心设计

```kotlin
class TextFile(private var book: Book) {

    companion object {
        private const val bufferSize = 512000  // 512KB 缓冲
        private const val maxLengthWithNoToc = 10 * 1024  // 无目录时最大章节长度

        @Synchronized
        fun getChapterList(book: Book): ArrayList<BookChapter> {
            return getTextFile(book).getChapterList()
        }
    }
}
```

**设计原理 - 单例缓存**：
- `getTextFile(book)` 返回单例
- 同一本书多次访问复用实例
- `@Synchronized` 保证线程安全

### 编码检测

```kotlin
val buffer = ByteArray(bufferSize)
val length = bis.read(buffer)

// 检测编码
val charset = if (book.charset.isNullOrBlank() || modified) {
    EncodingDetect.getEncode(buffer.copyOf(length))
} else {
    book.fileCharset()
}
```

**为什么需要编码检测？**
- TXT 文件不存储编码信息
- 中文常用 GBK、UTF-8
- 检测错误会导致乱码

**EncodingDetect 原理**：
```
1. 检查 BOM (UTF-8 BOM: EF BB BF)
2. 分析字节频率分布
3. 尝试解码，检测非法字符
```

### 目录识别流程

```
┌─────────────────────────────────────────────────────────────┐
│                    TextFile.getChapterList()                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ↓
              ┌───────────────────────────────┐
              │ 读取文件前 512KB 到 buffer     │
              └───────────────────────────────┘
                              │
                              ↓
              ┌───────────────────────────────┐
              │ 检测文件编码                   │
              └───────────────────────────────┘
                              │
                              ↓
              ┌───────────────────────────────┐
              │ 使用 TxtTocRule 正则匹配      │
              └───────────────────────────────┘
                              │
                              ↓
              ┌───────────────────────────────┐
              │ 返回 BookChapter 列表          │
              └───────────────────────────────┘
```

### 正则匹配算法

```kotlin
private fun analyze(pattern: Pattern?): Pair<ArrayList<BookChapter>, Int> {
    // 分块读取文件
    while (bis.read(buffer, bufferStart, bufferSize - bufferStart) > 0) {
        val blockContent = String(buffer, 0, end, charset)

        // 正则匹配
        val matcher = pattern.matcher(blockContent)
        while (matcher.find()) {
            val chapterStart = matcher.start()
            val chapterTitle = matcher.group()

            // 记录章节
            toc.add(BookChapter(
                title = chapterTitle,
                start = curOffset + chapterStart,
                end = ...,
                index = toc.size
            ))
        }
    }
}
```

**关键设计 - 分块读取**：
- 不一次性加载整个文件
- 移动滑动窗口
- 记录每个章节在文件中的字节偏移量

### 内容获取

```kotlin
fun getContent(chapter: BookChapter): String {
    val start = chapter.start!!
    val end = chapter.end!!

    // 检查缓存
    if (start > bufferEnd || end < bufferStart) {
        // 重新加载 buffer
        bufferStart = bufferSize * (start / bufferSize)
        txtBuffer = ...
    }

    // 从 buffer 提取内容
    return String(buffer, charset)
        .substringAfter(chapter.title)  // 去除标题
        .replace(padRegex, "　　")      // 格式化空白
}
```

**设计原理 - 缓存热点**：
- 同一章节可能被反复阅读
- Buffer 机制减少 IO
- 按 512KB 分块缓存

---

## EPUB 解析器

### 核心设计

```kotlin
class EpubFile(var book: Book) {

    companion object : BaseLocalBookParse {
        private var eFile: EpubFile? = null

        @Synchronized
        private fun getEFile(book: Book): EpubFile {
            if (eFile == null || eFile?.book?.bookUrl != book.bookUrl) {
                eFile = EpubFile(book)
            }
            eFile?.book = book
            return eFile!!
        }
    }

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var epubBook: EpubBook? = null
}
```

**为什么用 ParcelFileDescriptor？**
- EPUB 是 ZIP，需要随机访问
- `ParcelFileDescriptor` 支持 `seek()`
- Android 系统级支持，效率高

### Lazy Loading 策略

```kotlin
private fun readEpub(): EpubBook? {
    BookHelp.getBookPFD(book)?.let {
        fileDescriptor = it
        val zipFile = AndroidZipFile(it, book.originName)
        // 懒加载：只读取必要的元数据
        EpubReader().readEpubLazy(zipFile, "utf-8")
    }
}
```

**readEpubLazy vs readEpub**：
| 方法 | 内存 | 速度 | 适用场景 |
|------|------|------|---------|
| `readEpub` | 全量加载 | 首次慢 | 小文件 |
| `readEpubLazy` | 按需加载 | 首次快 | 大文件 |

**好处**：
- 大 EPUB（100MB+）不会 OOM
- 只读取当前需要的章节
- 图片等资源延迟加载

### EPUB 结构解析

```
container.xml → content.opf 位置
content.opf  → 元数据 + manifest + spine
toc.ncx/nav.xhtml → 目录结构
spine → 阅读顺序
manifest → 文件清单
```

### 内容获取流程

```kotlin
fun getContent(chapter: BookChapter): String? {
    val contents = epubBookContents ?: return null

    // 找到章节对应的资源
    for (res in contents) {
        if (res.href != currentChapterFirstResourceHref) continue

        // 获取 body 内容
        val body = getBody(res, startFragmentId, endFragmentId)

        // 处理 HTMLEntities
        return StringEscapeUtils.unescapeHtml4(body.html())
    }
}
```

**为什么需要 fragmentId？**
- 一个 XHTML 文件可能包含多个章节
- `href="#chapter1"` 指向文件内的锚点
- 需要 fragmentId 精确定位

---

## UMD 解析器

### 文件结构

```
┌────────────────────────────────┐
│ Header (0xde9a9b89)           │ 魔数验证
├────────────────────────────────┤
│ Chapter Info                   │ 章节元信息
├────────────────────────────────┤
│ Content (zlib compressed)      │ 正文（压缩）
├────────────────────────────────┤
│ Cover (JPEG)                   │ 封面图
├────────────────────────────────┤
│ End                            │ 固定尾
└────────────────────────────────┘
```

### 魔数验证

```java
if (reader.readIntLe() != 0xde9a9b89) {
    throw new IOException("Wrong header");
}
```

**什么是魔数？**
- 文件开头的固定字节
- 用于快速验证文件格式
- UMD 格式固定为 `0xde9a9b89`

### 压缩内容读取

```java
case 132:
    if (this._AdditionalCheckNumber != additionalCheckNumber) {
        book.getChapters().contents.write(
            UmdUtils.decompress(reader.readBytes(length))
        );
    }
```

**为什么压缩？**
- 文本重复内容多，压缩率高
- 减少存储空间
- 解压开销可接受

---

## PDF 解析器

### 限制

```kotlin
class PdfFile(var book: Book) {
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    // PdfRenderer 不支持文本提取
    // 只能渲染成图片
}
```

**PdfRenderer 限制**：
| 功能 | 支持 | 说明 |
|------|------|------|
| 渲染页面 | ✅ | 高质量 |
| 文本提取 | ❌ | 无法获取文字 |
| 表单 | ❌ | 不支持 |
| 链接 | ❌ | 不支持 |

**为什么用 Android 内置 PdfRenderer？**
- Android 5.0+ 内置
- 原生支持，性能好
- 不需要第三方库

---

## 学习任务

### 1. 理解 TXT 分块读取

**打开文件**：
- `app/src/main/java/io/legado/app/model/localBook/TextFile.kt`

**思考**：
- buffer 的作用是什么？
- 为什么需要记录章节的 start/end 字节偏移？

### 2. 分析 EPUB 懒加载

**打开文件**：
- `modules/book/src/main/java/me/ag2s/epublib/epub/EpubReader.java` - 找 `readEpubLazy` 方法

**思考**：
- lazy loading 和全量加载有什么区别？
- 什么场景适合用 lazy loading？

### 3. 理解 UMD 压缩

**打开文件**：
- `modules/book/src/main/java/me/ag2s/umdlib/umd/UmdReader.java`

**思考**：
- 为什么要压缩正文？
- 压缩和解压的开销值得吗？

---

## 解析器对比

| 解析器 | 编码检测 | 目录来源 | 内容格式 | 特殊能力 |
|--------|---------|---------|---------|---------|
| **TextFile** | ✅ | 用户正则 | 纯文本 | 分块读取 |
| **EpubFile** | ✅ | XML 文件 | HTML | 懒加载 |
| **UmdFile** | ❌ | 内置 | 压缩文本 | zlib 解压 |
| **PdfFile** | ❌ | 页码 | 图片 | 渲染 |
| **MobiFile** | ❌ | 内置 | 压缩文本 | 解析 |
