# 书籍解析模块

## 概述

书籍解析模块负责从设备存储或网络读取电子书，解析 TXT、EPUB、PDF、UMD 等格式，提取目录结构和正文内容。

**源码位置**：
- 解析入口：`app/src/main/java/io/legado/app/model/localBook/`
- EPUB 库：`modules/book/src/main/java/me/ag2s/epublib/`
- UMD 库：`modules/book/src/main/java/me/ag2s/umdlib/`

**学习目标**：理解解析器工厂模式、目录识别算法、文件格式结构。

---

## 架构设计

### 为什么需要统一的解析入口？

```
┌─────────────────────────────────────────────┐
│                   LocalBook                  │
│              (解析器工厂)                     │
└─────────────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ↓             ↓             ↓
   ┌─────────┐   ┌─────────┐   ┌─────────┐
   │TextFile │   │EpubFile │   │ PdfFile │
   └─────────┘   └─────────┘   └─────────┘
```

**设计原理 - 工厂模式**：
- 调用方只需知道 `LocalBook.getChapterList(book)`
- 根据文件类型自动选择解析器
- 新增格式只需添加新的解析器，无需修改调用方

### 解析器选择逻辑

```kotlin
fun getChapterList(book: Book): ArrayList<BookChapter> {
    return when {
        book.isEpub -> EpubFile.getChapterList(book)
        book.isUmd -> UmdFile.getChapterList(book)
        book.isPdf -> PdfFile.getChapterList(book)
        book.isMobi -> MobiFile.getChapterList(book)
        else -> TextFile.getChapterList(book)  // 默认 TXT
    }
}
```

**设计原理 - 类型分发**：
- `Book` 类通过扩展属性（`isEpub`、`isTxt`）判断类型
- 避免使用 `if (ext == "epub")` 这种字符串比较
- 符合开闭原则：新增格式不修改分发逻辑

---

## 支持的格式

### 格式对比

| 格式 | 文件结构 | 解析难度 | 特点 |
|------|---------|---------|------|
| **TXT** | 纯文本 | ⭐ | 简单，但目录需要正则识别 |
| **EPUB** | ZIP + XML | ⭐⭐⭐ | 主流电子书格式，结构清晰 |
| **UMD** | 二进制 | ⭐⭐⭐ | 早期国产格式，结构简单 |
| **PDF** | 混合 | ⭐⭐⭐⭐ | 复杂，支持有限 |
| **MOBI** | 二进制 | ⭐⭐⭐⭐ | Kindle 格式 |

### TXT 格式

```
第1章 少年归来
第一章 少年归来
[第一章] 少年归来
都是合法的章节标题
```

**特点**：
- 纯文本，无目录结构
- 需要用户定义目录规则
- 支持多种编码（UTF-8、GBK）

### EPUB 格式

```
book.epub (实际是 ZIP 文件)
├── META-INF/
│   └── container.xml        # 书架文件位置
├── OEBPS/
│   ├── content.opf          # 元数据、目录、 Spine
│   ├── toc.ncx              # 目录（NCX 格式）
│   ├── nav.xhtml            # 目录（Nav 格式）
│   └── chapters/            # 正文内容
│       ├── chapter1.xhtml
│       └── chapter2.xhtml
```

**为什么用 ZIP？**
- 多个文件打包减少 IO
- 压缩减少存储空间
- 支持图片和字体资源

### UMD 格式

```
┌──────────────┐
│   Header    │ 固定头 0xde9a9b89
├──────────────┤
│  Chapters   │ 章节信息块
├──────────────┤
│   Content   │ 正文（压缩）
├──────────────┤
│   Cover     │ 封面图片
├──────────────┤
│    End      │ 固定尾
└──────────────┘
```

**特点**：
- 二进制格式，结构简单
- 正文使用 zlib 压缩
- 支持封面

---

## 目录识别原理

### 为什么 TXT 需要目录识别？

```
EPUB: toc.ncx → 自动获取目录结构
TXT:  "第1章 xxx" → 需要正则匹配
```

### 目录规则设计

```kotlin
@Entity(tableName = "txtTocRules")
data class TxtTocRule(
    var id: Long = System.currentTimeMillis(),
    var name: String = "",           // 规则名称
    var rule: String = "",          // 正则表达式
    var example: String? = null,     // 示例
    var serialNumber: Int = -1,      // 优先级
    var enable: Boolean = true
)
```

**匹配示例**：

| 规则 | 正则 | 匹配结果 |
|------|------|---------|
| 标准格式 | `第[0-9]+章.*` | 第1章、第123章 |
| 括号格式 | `\\[第[0-9]+章` | [第1章、[第123章 |
| 括号外部 | `第[0-9]+章.*` | 第1章 xxx |
| 分割线 | `===+` | ======== |

### 识别算法流程

```
1. 读取文件前 512KB（buffer）
2. 检测编码（UTF-8、GBK、GB2312）
3. 按规则匹配章节标题
4. 记录每个章节的起始位置
5. 返回章节列表
```

**为什么不一次性读取整个文件？**
- 大文件（10MB+）全读会 OOM
- 分块读取，按需加载
- 缓存热点块减少 IO

---

## 文件读取抽象

### Book 输入流获取

```kotlin
fun getBookInputStream(book: Book): InputStream {
    val uri = book.getLocalUri()

    // 尝试直接打开
    uri.inputStream(appCtx)?.let { return it }

    // 重新从压缩包提取
    importArchiveFile(localArchiveUri, book.originName) {
        it.contains(book.originName)
    }.firstOrNull()?.let { return getBookInputStream(it) }

    // 尝试下载远程文件
    if (webDavUrl != null && downloadRemoteBook(book)) {
        return getBookInputStream(book)
    }
}
```

**设计原理 - 多级回退**：
1. 本地文件优先
2. 压缩包内文件尝试重新解压
3. 远程文件尝试下载
4. 都不行才报错

### Content URI vs File URI

```kotlin
// Content URI ( SAF - Storage Access Framework )
content://com.example.provider/book/123

// File URI
file:///data/user/0/io.legado.app/files/book.txt
```

**为什么两种都要支持？**
- Android 10+ 限制直接访问文件系统
- SAF 允许用户授权访问任意位置
- 不同来源用不同 URI

---

## 学习任务

### 1. 理解工厂模式

**打开文件**：
- `app/src/main/java/io/legado/app/model/localBook/LocalBook.kt` - 解析入口
- `app/src/main/java/io/legado/app/model/localBook/TextFile.kt` - TXT 解析

**思考**：
- 为什么 `getChapterList` 要用 `when` 分发而不是 `if`？
- 如果要支持新格式（如 AZW3），需要改哪些地方？

### 2. 分析目录识别

**打开文件**：
- `app/src/main/java/io/legado/app/data/entities/TxtTocRule.kt` - 规则实体
- `app/src/main/java/io/legado/app/model/localBook/TextFile.kt` - 识别逻辑

**思考**：
- 正则匹配章节标题的优缺点？
- 如何处理"第1章"和"第一章"都存在的情况？

### 3. 理解 EPUB 结构

**打开文件**：
- `modules/book/src/main/java/me/ag2s/epublib/epub/EpubReader.java` - 阅读器
- `app/src/main/java/io/legado/app/model/localBook/EpubFile.kt` - 封装

**思考**：
- 为什么 EPUB 要用 ZIP 打包？
- lazy loading 有什么好处？

---

## 设计亮点总结

| 设计 | 原理 | 收益 |
|------|------|------|
| 工厂模式 | 类型分发解析器 | 易于扩展 |
| 多级回退 | 本地→压缩包→远程 | 容错性强 |
| 分块读取 | Buffer + 缓存 | 防止 OOM |
| 正则目录 | 用户定义规则 | 灵活性高 |
| SAF 兼容 | Content URI | 适配 Android 10+ |
| 懒加载 | 按需读取资源 | 内存占用低 |
