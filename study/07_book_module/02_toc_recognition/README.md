# 目录识别系统

## 概述

目录识别是将书籍正文切分为章节的技术。EPUB 等格式自带目录结构，而 TXT 需要通过**正则规则**智能识别章节边界。

**源码位置**：
- 规则实体：`app/src/main/java/io/legado/app/data/entities/TxtTocRule.kt`
- 识别逻辑：`app/src/main/java/io/legado/app/model/localBook/TextFile.kt`

---

## 为什么 TXT 需要目录识别？

### 格式对比

| 格式 | 目录来源 | 识别方式 |
|------|---------|---------|
| EPUB | `toc.ncx` / `nav.xhtml` | XML 解析 |
| PDF | 书签或页码 | 结构分析 |
| UMD | 内置章节表 | 二进制解析 |
| **TXT** | **无** | **正则匹配** |

### TXT 的问题

```
《斗破苍穹》.txt 内容：
第1章 陨落的天才
...
第2章 斗气大陆
...
第3章 修炼天赋
```

**问题**：
- TXT 是纯文本，没有目录结构
- 没有标准的章节分隔符
- 需要用户定义识别规则

---

## 目录规则设计

### TxtTocRule 实体

```kotlin
@Entity(tableName = "txtTocRules")
data class TxtTocRule(
    @PrimaryKey
    var id: Long = System.currentTimeMillis(),

    var name: String = "",           // 规则名称
    var rule: String = "",          // 正则表达式
    var example: String? = null,    // 示例文本
    var serialNumber: Int = -1,      // 优先级（数字越小越优先）
    var enable: Boolean = true       // 是否启用
)
```

### 规则示例

| 规则名称 | 正则 | 示例 |
|---------|------|------|
| 标准格式 | `第[0-9]+章.*` | 第1章、第123章 |
| 中文数字 | `第[一二三四五六七八九十百千]+章.*` | 第一章、第十章 |
| 括号格式 | `\\[第[0-9]+章` | [第1章、[第123章 |
| 分割线 | `===+` | ======== |
| 空白行 | `^\\s*$` | （空行，用于分卷） |

### 正则解释

```
第[0-9]+章.*
│ │  │    │
│ │  │    └─ 任意字符（章节名）
│ │  └─ 一个或多个数字
│ └─ 字面量"章"
└─ 字面量"第"
```

---

## 识别算法

### 整体流程

```
┌─────────────────────────────────────────────┐
│              TextFile.getChapterList()          │
└─────────────────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────┐
│ 1. 读取文件前 512KB 到 buffer                │
└─────────────────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────┐
│ 2. 检测文件编码                              │
└─────────────────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────┐
│ 3. 获取用户设置的目录规则                     │
└─────────────────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────┐
│ 4. 按正则匹配章节标题                         │
└─────────────────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────┐
│ 5. 记录每个章节的 start/end 位置             │
└─────────────────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────┐
│ 6. 返回 BookChapter 列表                     │
└─────────────────────────────────────────────┘
```

### 分块读取

```kotlin
private fun analyze(pattern: Pattern?): Pair<ArrayList<BookChapter>, Int> {
    val toc = arrayListOf<BookChapter>()
    var curOffset: Long = 0

    // 每次读取 bufferSize 大小
    while (bis.read(buffer, bufferStart, bufferSize - bufferStart) > 0) {
        val blockContent = String(buffer, 0, end, charset)

        // 正则匹配
        val matcher = pattern.matcher(blockContent)
        while (matcher.find()) {
            val chapterStart = matcher.start()

            // 记录章节信息
            toc.add(BookChapter(
                title = matcher.group(),
                start = curOffset + chapterStart,
                end = ...,
                index = toc.size
            ))
        }
    }
}
```

**为什么分块读取？**
- 大文件（50MB+）全量读取会 OOM
- 章节位置是字节偏移，按需读取
- 缓存热点块减少 IO

### 滑动窗口

```
文件：
[==========Buffer1==========][==========Buffer2==========]
                               ↑
                            章节标题
```

**边界情况处理**：
- 章节标题可能跨越 Buffer 边界
- 需要在块交界处额外检查
- 通过 `bufferStart` 偏移量修正

---

## 编码检测

### 为什么需要编码检测？

```
问题：TXT 文件不存储编码信息

示例：
- UTF-8 编码：0xE7 0xAC 0xA0 (一个字)
- GBK 编码：  0xD7 0xD7 (一个字)
```

**检测方法**：
1. 检查 BOM（Byte Order Mark）
2. 分析字节频率分布
3. 尝试解码检测非法字符

### BOM 检测

```kotlin
// UTF-8 BOM
if (bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF) {
    return "UTF-8"
}

// UTF-16 LE BOM
if (bytes[0] == 0xFF && bytes[1] == 0xFE) {
    return "UTF-16LE"
}
```

### 字节频率分析

```kotlin
fun getEncode(bytes: ByteArray): String {
    // GBK 中文高字节范围：0x81-0xFE
    val gbCount = bytes.count { it in 0x81..0xFE.toByte() }

    // 如果高频出现，倾向 GBK
    return if (gbCount > bytes.size * 0.1) "GBK" else "UTF-8"
}
```

---

## 规则优先级

### 为什么需要优先级？

```
场景：同一本书有多种章节格式

第1章 少年归来
第2章 斗气大陆
[第一卷] 修炼开始
```

**解决方案**：
- `serialNumber` 字段控制优先级
- 数字越小越先尝试
- 匹配成功就停止

### 匹配逻辑

```kotlin
val rules = appDb.txtTocRuleDao.all
    .filter { it.enable }
    .sortedBy { it.serialNumber }  // 按优先级排序

for (rule in rules) {
    val pattern = rule.rule.toPattern()
    val toc = tryMatch(pattern)
    if (toc.isNotEmpty()) {
        return toc  // 匹配成功
    }
}
```

---

## 长章节分割

### 为什么要分割？

```
问题：某些书没有目录，全文在一个大章节里

解决：如果章节超过最大长度，自动分割
```

### 分割参数

```kotlin
private const val maxLengthWithToc = 102400   // 有目录时最大 100KB
private const val maxLengthWithNoToc = 10 * 1024  // 无目录时最大 10KB
```

### 分割逻辑

```kotlin
if (book.getSplitLongChapter()) {
    val chapterLength = chapterStart - lastChapterStart
    if (chapterLength > maxLengthWithToc) {
        // 插入分割点
        toc.lastOrNull()?.let {
            it.end = it.start  // 强制结束上一章
        }
    }
}
```

---

## 学习任务

### 1. 理解正则匹配

**打开文件**：
- `app/src/main/java/io/legado/app/data/entities/TxtTocRule.kt` - 规则实体
- `app/src/main/java/io/legado/app/model/localBook/TextFile.kt` - 匹配逻辑

**思考**：
- 正则 `[0-9]+` 和 `[0-9]*` 有什么区别？
- `^` 和 `$` 在多行模式下匹配什么？

### 2. 分析分块读取

**打开文件**：
- `app/src/main/java/io/legado/app/model/localBook/TextFile.kt` - `analyze` 方法

**思考**：
- 如果章节标题刚好在 Buffer 交界处会怎样？
- 如何保证不漏掉章节？

### 3. 实践：设计新规则

**思考**：
- 如果书使用「第①章」格式，正则怎么写？
- 如果用「Chapter 1」英文格式呢？

---

## 设计亮点

| 设计 | 原理 | 收益 |
|------|------|------|
| 正则匹配 | 用户定义模式 | 灵活性高 |
| 多规则优先级 | 逐一尝试匹配 | 容错性强 |
| 分块读取 | Buffer + 滑动窗口 | 防止 OOM |
| 编码检测 | BOM + 字节频率 | 自动识别 |
| 长章节分割 | 阈值自动切分 | 处理无目录书 |
| 字节偏移 | 记录 start/end | 精确定位 |
