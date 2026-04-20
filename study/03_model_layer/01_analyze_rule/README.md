# 书源规则解析 (AnalyzeRule)

## 概述

规则解析是 Legado 的**核心能力**，负责从网站抓取书籍信息。系统支持多种解析方式：XPath、JSoup、XPath、Regex、JSON。

## 源码位置

```
app/src/main/java/io/legado/app/model/analyzeRule/
├── RuleAnalyzer.kt       # 规则分析器入口
├── AnalyzeByXPath.kt      # XPath 解析
├── AnalyzeByJSoup.kt      # JSoup 解析
├── AnalyzeByRegex.kt     # 正则解析
├── AnalyzeByJSonPath.kt  # JSON 解析
├── AnalyzeUrl.kt         # URL 分析
├── RuleData.kt          # 规则数据模型
└── RuleDataInterface.kt  # 规则接口
```

## 核心组件

### RuleAnalyzer - 规则分析器入口

**文件**: `model/analyzeRule/RuleAnalyzer.kt`

**职责**: 统一入口，分发到具体解析器

**设计原理**:

```
RuleAnalyzer.analyzeBook()
    ↓
判断规则类型 (XPath/JSoup/Regex/JSON)
    ↓
调用对应解析器
    ↓
返回统一格式 Book
```

**设计分析**:

| 设计 | 原理 | 好处 |
|------|------|------|
| 统一入口 | Facade 模式 | 调用方无需关心具体解析器 |
| 多解析器支持 | 策略模式 | 不同网站用不同解析方式 |
| 返回统一格式 | 适配器模式 | 上层无需关心解析细节 |

### 解析器选择策略

**打开** `RuleAnalyzer.kt`，查找 `analyzeBook()` 方法：

```
网站特点                    → 推荐解析器
─────────────────────────────────────────
HTML 结构清晰              → XPath / JSoup
需要执行 JS 获取数据        → JSoup + JS
纯文本匹配                → Regex
API 返回 JSON              → JSONPath
复杂多步请求               → 自定义 JS
```

## 解析器详解

### 1. AnalyzeByXPath - XPath 解析

**文件**: `model/analyzeRule/AnalyzeByXPath.kt`

**原理**:

1. 下载 HTML
2. 使用 XPath 表达式提取元素
3. 返回提取结果

**XPath 示例**:

```xpath
//div[@class="book-list"]//a/@href          获取书籍链接
//div[@class="title"]/text()                  获取标题
//img[@class="cover"]/@src                    获取封面
```

**学习要点**:
- XPath 是 XML/HTML 的查询语言
- 支持路径表达式、谓词、条件筛选
- 比正则更易读和维护

### 2. AnalyzeByJSoup - JSoup 解析

**文件**: `model/analyzeRule/AnalyzeByJSoup.kt`

**原理**:

1. 使用 JSoup 解析 HTML
2. 支持 CSS Selector 选择器
3. 可执行 JavaScript 获取动态内容

**CSS Selector 示例**:

```css
.book-list a                    获取书籍链接
.book-list .title               获取标题
.cover@src                      获取封面
```

**学习要点**:
- JSoup 比 XPath 更适合复杂 JavaScript 场景
- 支持 CSS Selector，更符合前端开发者习惯
- 内置 JavaScript 引擎执行动态 JS

### 3. AnalyzeByRegex - 正则解析

**文件**: `model/analyzeRule/AnalyzeByRegex.kt`

**原理**:

1. 使用正则表达式匹配文本
2. 捕获分组提取数据

**正则示例**:

```regex
书名：([^"]+)                捕获书名
作者：([^"]+)                捕获作者
章节：([^|]+)\|([^"]+)       捕获章节名和链接
```

**学习要点**:
- 正则适合结构不规则的文本
- 但复杂正则难维护
- 通常作为补充解析方式

### 4. AnalyzeByJSonPath - JSON 解析

**文件**: `model/analyzeRule/AnalyzeByJSonPath.kt`

**原理**:

1. 解析 JSON 响应
2. 使用 JSONPath 表达式提取数据

**JSONPath 示例**:

```jsonpath
$.data.books[*].title          提取所有书名
$.data[?(@.type==1)].url       提取类型为1的URL
```

## 规则数据结构

### BookSource 中的规则字段

**文件**: `data/entities/BookSource.kt`

```kotlin
data class BookSource(
    // 搜索规则
    val searchUrl: String?,       // 搜索 URL
    val searchJs: String?,        // JS 增强搜索

    // 目录规则
    val catalogUrl: String?,      // 目录页 URL
    val catalogJs: String?,       // JS 解析目录

    // 内容规则
    val contentPattern: String?,  // XPath/正则
    val contentJs: String?,       // JS 处理正文
)
```

**规则流程**:

```
searchUrl → 获取搜索页 → 解析书籍列表
    ↓
catalogUrl → 获取目录页 → 解析章节列表
    ↓
contentPattern → 获取正文页 → 解析正文内容
```

## JS 增强解析

### 为什么需要 JS

| 场景 | 纯 HTTP | JS 增强 |
|------|---------|---------|
| 静态 HTML | ✅ | ✅ |
| 懒加载内容 | ❌ | ✅ |
| 加密参数 | ❌ | ✅ |
| 需要登录 | ❌ | ✅ |

### JS 上下文

**文件**: `model/SharedJsScope.kt`

**原理**:

```kotlin
// Rhino JS 引擎
val rhino = RhinoEngine()
rhino.eval("""
    function search(key) {
        var result = [];
        // 自定义搜索逻辑
        return JSON.stringify(result);
    }
""")
```

**学习要点**:
- JS 脚本在 Rhino 引擎中执行
- 可以调用 Java 方法操作 Android API
- 复杂书源的核心能力

## 设计模式分析

### 1. 策略模式

```
RuleAnalyzer (Context)
    ↓
├── AnalyzeByXPath (Strategy)
├── AnalyzeByJSoup (Strategy)
├── AnalyzeByRegex (Strategy)
└── AnalyzeByJSonPath (Strategy)
```

**好处**: 可互换解析算法，运行时选择

### 2. 责任链模式

```
搜索请求 → 书源1 → 书源2 → 书源3 → ...
         (失败)   (失败)   (成功)
              ↓
         返回结果
```

### 3. 模板方法模式

```
analyzeBook()
    ↓
prepareRequest()    ← 子类实现
    ↓
parseResponse()    ← 子类实现
    ↓
formatResult()     ← 统一格式
```

## 学习任务

1. **打开** `RuleAnalyzer.kt`，分析入口方法
2. **打开** `AnalyzeByXPath.kt`，理解 XPath 解析流程
3. **理解** 为什么需要多种解析器
4. **分析** JS 增强在什么场景下需要

## 相关文件

| 文件 | 说明 |
|------|------|
| `model/analyzeRule/RuleAnalyzer.kt` | 规则分析器 |
| `model/analyzeRule/AnalyzeByXPath.kt` | XPath 解析 |
| `model/analyzeRule/AnalyzeByJSoup.kt` | JSoup 解析 |
| `model/analyzeRule/AnalyzeByRegex.kt` | 正则解析 |
| `data/entities/BookSource.kt` | 书源规则字段 |
| `modules/rhino/` | JS 引擎模块 |
