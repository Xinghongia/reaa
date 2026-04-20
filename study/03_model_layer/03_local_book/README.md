# 本地书籍解析 (LocalBook)

## 概述

本地书籍解析负责从设备存储中读取和解析电子书，支持 TXT、EPUB、PDF、UMD 等格式。

## 源码位置

```
app/src/main/java/io/legado/app/model/localBook/
├── LocalBook.kt       # 本地书籍入口
├── TextFile.kt        # TXT 解析
├── EpubFile.kt        # EPUB 解析
├── PdfFile.kt         # PDF 解析
├── UmdFile.kt         # UMD 解析
├── MobiFile.kt        # Mobi 解析
└── BaseLocalBookParse.kt # 解析基类
```

## 支持格式

| 格式 | 特点 | 解析难度 |
|------|------|---------|
| **TXT** | 纯文本，最简单 | ⭐ |
| **EPUB** | ZIP + XML 结构 | ⭐⭐⭐ |
| **PDF** | 复杂文档格式 | ⭐⭐⭐⭐ |
| **UMD** | 早期电子书格式 | ⭐⭐⭐ |

## 解析入口

### LocalBook

**文件**: `model/localBook/LocalBook.kt`

**职责**: 根据文件扩展名选择解析器

**设计原理**:

```kotlin
object LocalBook {

    suspend fun parse(file: File): Book {
        return when (file.extension.lowercase()) {
            "txt" -> TextFile(file).parse()
            "epub" -> EpubFile(file).parse()
            "pdf" -> PdfFile(file).parse()
            "umd" -> UmdFile(file).parse()
            else -> throw UnsupportedFormatException()
        }
    }
}
```

**设计分析**:

| 设计 | 原理 | 好处 |
|------|------|------|
| 工厂模式 | 根据类型创建实例 | 易于扩展新格式 |
| 统一入口 | LocalBook.parse() | 调用方无需关心格式 |
| 异常统一 | UnsupportedFormatException | 错误处理一致 |

## TXT 解析

### TextFile

**文件**: `model/localBook/TextFile.kt`

**原理**:

```
文件流 → 编码检测 → 文本读取 → 目录识别 → 分章
```

**打开** `TextFile.kt`，分析 `parse()` 方法：

```kotlin
suspend fun parse(): Book {
    // 1. 读取文件
    val content = readFile()

    // 2. 编码检测
    val charset = detectCharset(content)

    // 3. 生成目录
    val chapters = detectChapter(content, charset)

    // 4. 返回书籍
    return Book(
        name = file.nameWithoutExtension,
        type = Type.local,
        chapters = chapters
    )
}
```

### 目录识别规则

**文件**: `data/entities/TxtTocRule.kt`

**原理**: 使用正则表达式匹配章节标题

```kotlin
data class TxtTocRule(
    val name: String,           // 规则名称
    val pattern: String,        // 正则表达式
    val example: String? = null // 示例
)

// 示例规则
TxtTocRule(
    name = "标准格式",
    pattern = "第[0-9]+章.*"     // 匹配 "第一章", "第123章"
)

TxtTocRule(
    name = "括号格式",
    pattern = "\\[第[0-9]+章.*"  // 匹配 "[第123章"
)
```

**目录识别算法**:

```
1. 按行读取文件
2. 每行匹配 TxtTocRule 正则
3. 匹配成功 → 新章节开始
4. 记录章节标题和起始位置
```

## EPUB 解析

### EpubFile

**文件**: `model/localBook/EpubFile.kt`

**原理**:

```
EPUB 文件 (ZIP)
    ↓
解压 → 解析 content.opf → 提取元数据
    ↓
解析 manifest → 获取文件列表
    ↓
解析 spine → 确定阅读顺序
    ↓
按顺序读取章节内容
```

**EPUB 结构**:

```
book.epub (ZIP)
├── META-INF/
│   └── container.xml        # 入口文件
├── OEBPS/
│   ├── content.opf          # 元数据和清单
│   ├── toc.ncx             # 目录
│   ├── chapter1.xhtml      # 章节内容
│   ├── chapter2.xhtml
│   └── styles.css          # 样式
└── mimetype                # MIME 类型
```

**解析步骤**:

1. **读取 container.xml** - 找到 content.opf 位置
2. **解析 content.opf** - 获取元数据、manifest、spine
3. **构建目录** - 从 toc.ncx 或 nav 生成目录
4. **读取章节** - 按 spine 顺序读取 XHTML

**设计分析**:

| 步骤 | 技术 | 说明 |
|------|------|------|
| 解压 | ZipInputStream | 标准 ZIP 格式 |
| XML解析 | XmlPullParser | 低内存占用 |
| 内容提取 | JSoup | HTML 解析 |

## PDF 解析

### PdfFile

**文件**: `model/localBook/PdfFile.kt`

**原理**:

```kotlin
// 使用 Android PdfRenderer
fun parse(): Book {
    val fileDescriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_ONLY
    )

    val pdfRenderer = PdfRenderer(fileDescriptor)
    val pageCount = pdfRenderer.pageCount

    val chapters = (0 until pageCount).map { index ->
        Chapter(
            title = "Page ${index + 1}",
            resource = "pdf://$filePath#$index"
        )
    }

    return Book(chapters = chapters)
}
```

**限制**:

| 问题 | 说明 |
|------|------|
| 文本提取 | PdfRenderer 不支持文本提取 |
| 只能渲染 | 需要图片化后显示 |
| 性能 | 大 PDF 加载慢 |

## 解析基类

### BaseLocalBookParse

**文件**: `model/localBook/BaseLocalBookParse.kt`

**设计**: 定义统一的解析接口

```kotlin
abstract class BaseLocalBookParse {

    abstract suspend fun parseBook(): Book

    abstract suspend fun getChapterContent(chapter: BookChapter): String?

    // 通用工具方法
    protected fun readBytes(): ByteArray { ... }

    protected fun decode(bytes: ByteArray, charset: Charset): String { ... }

    protected fun splitChapter(content: String): List<String> { ... }
}
```

**好处**:
- 统一接口，便于扩展
- 公共逻辑复用
- 便于测试

## 学习任务

1. **打开** `LocalBook.kt`，理解如何选择解析器
2. **打开** `TextFile.kt`，分析 TXT 解析流程
3. **理解** TxtTocRule 正则匹配的原理
4. **打开** `EpubFile.kt`，理解 EPUB 结构

## 相关文件

| 文件 | 说明 |
|------|------|
| `model/localBook/LocalBook.kt` | 解析入口 |
| `model/localBook/TextFile.kt` | TXT 解析 |
| `model/localBook/EpubFile.kt` | EPUB 解析 |
| `model/localBook/PdfFile.kt` | PDF 解析 |
| `data/entities/TxtTocRule.kt` | 目录规则 |
| `modules/book/` | 独立书籍解析模块 |
