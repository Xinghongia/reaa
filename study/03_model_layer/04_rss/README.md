# RSS订阅 (RSS)

## 概述

RSS 模块负责 RSS/Atom 订阅源的解析和文章管理。

## 源码位置

```
app/src/main/java/io/legado/app/model/rss/
```

## 核心组件

### RssSource - RSS源

**文件**: `data/entities/RssSource.kt`

**职责**: 存储 RSS 订阅源配置

```kotlin
data class RssSource(
    val sourceUrl: String,       // 源 URL
    val sourceName: String,      // 源名称
    val sourceIcon: String?,     // 图标
    val patternTitle: String?,  // 标题 XPath
    val patternLink: String?,   // 链接 XPath
    val patternDescription: String?, // 描述 XPath
    val patternImage: String?,  // 图片 XPath
)
```

### RssAnalyzer - RSS解析器

**职责**: 解析 RSS/Atom 格式

**支持格式**:

| 格式 | 说明 |
|------|------|
| RSS 2.0 | 最常见的 RSS 格式 |
| Atom | 更规范的订阅格式 |
| JSON Feed | 新型轻量格式 |

**解析原理**:

```
1. 获取 XML/JSON
2. 检测格式类型
3. 调用对应解析器
4. 提取文章列表
```

## 设计原理

### 解析器选择

```kotlin
object RssAnalyzer {

    suspend fun analyze(source: RssSource): List<RssArticle> {
        val content = fetchContent(source.sourceUrl)

        return when {
            content.contains("<rss") -> parseRSS(content, source)
            content.contains("<feed") -> parseAtom(content, source)
            content.contains("{") -> parseJsonFeed(content, source)
            else -> throw UnsupportedFormatException()
        }
    }
}
```

### 与书源解析的对比

| 维度 | 书源解析 | RSS解析 |
|------|---------|---------|
| 数据格式 | HTML | XML/JSON |
| 解析方式 | XPath/JSoup | XML Parser |
| 更新频率 | 按需 | 可定时 |
| 内容类型 | 书籍 | 文章 |

## 相关文件

| 文件 | 说明 |
|------|------|
| `data/entities/RssSource.kt` | RSS源实体 |
| `data/entities/RssArticle.kt` | RSS文章实体 |
| `model/rss/` | RSS 解析模块 |
