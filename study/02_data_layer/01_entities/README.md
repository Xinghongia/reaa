# 实体类 (Entities)

## 概述

实体类是 Room 数据库的表结构定义，采用 Kotlin Data Class + JSR-380 注解风格实现。

## 源码位置

```
app/src/main/java/io/legado/app/data/entities/
```

## 核心实体

### Book - 书籍

**文件**: `Book.kt`

**实现原理**:

```kotlin
@Entity(
    indices = [
        Index(value = ["bookUrl"], unique = true),
        Index(value = ["author", "bookUrl"]),
        Index(value = ["group"])
    ]
)
data class Book(
    @PrimaryKey
    val bookUrl: String,
    val bookName: String,
    val author: String,
    // ...
)
```

**设计分析**:

| 设计 | 原理 | 好处 |
|------|------|------|
| `bookUrl` 作为 PrimaryKey | URL 是书籍的天然唯一标识 | 去重、关联查询方便 |
| 复合索引 `author + bookUrl` | 常按作者筛选书籍 | 加速按作者查询 |
| 嵌套 `ReadConfig` | 阅读配置是书籍的强关联属性 | 减少表关联、提升性能 |

**关键字段**:

- `origin` - 追溯书籍来源书源
- `group` - 支持书籍分组管理
- `durChapter/durChapterPos` - 精确到字的位置存储
- `variable` - JSON 字符串存储可变数据

### BookChapter - 章节

**文件**: `BookChapter.kt`

**实现原理**:

```kotlin
@Entity(
    indices = [
        Index(value = ["bookUrl", "index"], unique = true)
    ]
)
data class BookChapter(
    @PrimaryKey
    val url: String,  // 章节 URL 作为唯一标识
    val bookUrl: String,
    val index: Int,   // 维护目录顺序
)
```

**设计分析**:

- **唯一索引** `bookUrl + index`：防止同一书籍出现重复章节
- **章节 URL 作为主键**：网络章节天然具有唯一性
- **index 字段**：支持手动调整章节顺序

### BookSource - 书源

**文件**: `BookSource.kt`

**实现原理**:

书源实体是 Legado 的**规则配置中心**，将网页解析规则存储在数据库中。

```kotlin
data class BookSource(
    @PrimaryKey
    val bookSourceUrl: String,

    // 搜索规则
    val searchUrl: String?,      // 搜索 URL 模板，支持 {{key}} 占位符
    val searchJs: String?,       // JS 增强搜索能力

    // 目录规则
    val catalogUrl: String?,     // 目录页 URL
    val catalogJs: String?,     // JS 解析目录

    // 内容规则
    val contentPattern: String?, // XPath/正则 提取正文
    val contentJs: String?,      // JS 处理正文
)
```

**设计分析**:

| 设计 | 原理 | 适用场景 |
|------|------|---------|
| 规则内嵌实体 | 数据即规则 | 便于导入导出书源配置 |
| 多规则字段 | 覆盖不同网站结构 | 一个书源适配多种网站 |
| JSON 字符串存储 JS | JS 逻辑无法结构化 | 支持复杂自定义解析 |
| URL 模板占位符 | `{{key}}` 模板语法 | 搜索参数动态替换 |

### ReplaceRule - 净化规则

**文件**: `ReplaceRule.kt`

**实现原理**:

```kotlin
data class ReplaceRule(
    val pattern: String,      // 正则表达式
    val replacement: String,  // 替换为... (空字符串表示删除)
    val scope: String?,       // 应用范围: 全局/书源/书籍
    val priority: Int = 0,    // 执行优先级 (数字越小越先执行)
)
```

**设计分析**:

- **正则匹配**：灵活处理各种广告文本模式
- **优先级机制**：保证替换顺序确定性
- **范围控制**：支持全局/局部规则
- **类型区分**：0=普通替换, 1=标题替换, 2=正则替换

## 实体设计模式

### 1. 组合模式

```kotlin
data class Book(
    // ... 基本字段
    val readConfig: Book.ReadConfig = Book.ReadConfig()  // 嵌套对象
)
```

**原理**: 将紧密相关的配置组合为嵌套对象，减少表关联

**好处**: 阅读配置随书籍一起加载，减少查询次数

### 2. 索引优化

```kotlin
@Index(value = ["latestChapterTime"])  // 书籍按更新时间排序的常见查询
@Index(value = ["author", "bookUrl"]) // 复合索引加速组合查询
```

**原理**: 索引是独立的数据结构，加速 WHERE 查询

**选择原则**: 查询条件经常出现的字段建立索引

### 3. JSON 序列化扩展

```kotlin
@Serializable
data class Book(
    val variable: String? = null,  // 存储复杂结构
)
```

**原理**: 无法用表结构表达的数据，用 JSON 字符串存储

**应用**: 书源自定义变量、动态解析结果

## 实体关系

```
BookSource (1) ─────────── (N) Book
  │                            │
  │                            │
  └────── origin ──────────────►

Book (1) ───────────────── (N) BookChapter
  │
  └────── bookUrl ────────────►
```

## 学习任务

1. **打开** `Book.kt`，查看所有字段
2. **理解** `ReadConfig` 嵌套类的设计
3. **分析** `BookSource` 中规则字段的分类
4. **思考** 为什么要用 URL 作为主键而不是自增 ID

## 相关文件

| 文件 | 说明 |
|------|------|
| `data/entities/Book.kt` | 书籍实体 |
| `data/entities/BookChapter.kt` | 章节实体 |
| `data/entities/BookSource.kt` | 书源实体 |
| `data/entities/ReplaceRule.kt` | 净化规则 |
| `data/entities/readRecord/` | 阅读记录实体 |
| `data/entities/rule/` | 规则定义 |
