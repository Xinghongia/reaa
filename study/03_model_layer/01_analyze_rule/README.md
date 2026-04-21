# 书源规则解析 (AnalyzeRule)

## 概述

规则解析是 Legado 的**核心能力**，发生在**拿到网站返回的原始内容（HTML/JSON）之后**，负责从这些原始文本中提取需要的书籍数据。

```
┌─────────────────────────────────────────────────────────────┐
│                    书源解析三阶段流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  阶段1：构造请求    阶段2：网络请求    阶段3：规则解析        │
│  （URL拼接）        （OkHttp）        （AnalyzeRule）       │
│       ↓                 ↓                  ↓                 │
│  {{key}} {{page}}   拿到原始内容    用规则提取数据          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 核心入口：AnalyzeRule

**文件**: `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt`

`AnalyzeRule` 是规则解析的**唯一入口**，所有规则解析都通过它完成。

---

## 完整数据流（重点）

```
┌─────────────────────────────────────────────────────────────┐
│                      规则解析完整数据流                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 调用方设置内容                                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ AnalyzeRule.setContent(html/json)                  │   │
│  │ • 保存原始内容                                      │   │
│  │ • 判断是 JSON 还是 HTML                             │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓                                  │
│  2. 调用方发起解析请求                                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ getStringList("规则字符串") 或 getString()         │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓                                  │
│  3. splitSourceRule() 切分规则                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 规则字符串 → [SourceRule列表]                      │   │
│  │ • 识别规则前缀（@XPath: / @Json: / $.）           │   │
│  │ • 确定每个规则的解析模式（Mode）                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓                                  │
│  4. 循环遍历 SourceRule 列表                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ for (sourceRule in ruleList) {                     │   │
│  │     result = when(sourceRule.mode) {               │   │
│  │         Mode.Js      → evalJS()                   │   │
│  │         Mode.Json    → getAnalyzeByJSonPath()     │   │
│  │         Mode.XPath   → getAnalyzeByXPath()        │   │
│  │         Mode.Default → getAnalyzeByJSoup()        │   │
│  │     }                                              │   │
│  │ }                                                  │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓                                  │
│  5. 返回结果                                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ getStringList() → List<String>?                   │   │
│  │ getString()     → String                          │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓                                  │
│  6. 调用方（WebBook/BookInfo）拿去展示/存储                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 实际书源解析示例

**书源规则**：
```json
{
  "ruleSearch": {
    "name": "a.1@text",
    "author": "p.1@text",
    "coverUrl": "img@src",
    "bookList": ".item"
  }
}
```

**解析流程**：

```
原始HTML内容:
<div class="item">
  <a href="/book/1"><img src="/cover.jpg"/></a>
  <a href="/book/1">斗破苍穹</a>
  <p class="itemtxt"><p>天蚕土豆</p></p>
</div>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

提取书名: getStringList("a.1@text")
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌─────────────────────────────────────────────────────────────┐
│ 1. setContent(html)                                        │
│    → 保存原始HTML                                           │
│                                                             │
│ 2. getStringList("a.1@text")                              │
│    → splitSourceRule() → [SourceRule("a.1@text", Default)]│
│                                                             │
│ 3. for循环 (只有1个SourceRule，只循环1次)                   │
│    → Mode.Default → getAnalyzeByJSoup(content)             │
│    → .getStringList("a.1@text")                          │
│    → 返回: ["斗破苍穹"]                                    │
└─────────────────────────────────────────────────────────────┘

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

提取作者: getStringList("p.1@text")
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌─────────────────────────────────────────────────────────────┐
│ 同上流程，只是规则字符串不同                                 │
│ → 返回: ["天蚕土豆"]                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## SourceRule 模式识别

```
┌─────────────────────────────────────────────────────────────┐
│                  SourceRule.mode 识别规则                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  规则前缀              → Mode        解析器                  │
│  ────────────────────────────────────────────────────────  │
│  @XPath:            → XPath      AnalyzeByXPath           │
│  @Json:             → Json       AnalyzeByJSonPath        │
│  $. 或 $[           → Json       AnalyzeByJSonPath        │
│  / 开头             → XPath      AnalyzeByXPath            │
│  @js: / <js>        → Js        Rhino JS 引擎            │
│  无前缀             → Default    AnalyzeByJSoup            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 四大解析器

| 解析器 | 用途 | 示例 |
|--------|------|------|
| **AnalyzeByJSoup** | HTML CSS选择器（默认） | `.book-list a`, `a.1@text` |
| **AnalyzeByXPath** | XPath表达式 | `//div[@class="book"]//a/@href` |
| **AnalyzeByJSonPath** | JSONPath表达式 | `$.data.books[*].name` |
| **AnalyzeByRegex** | 正则匹配 | `书名：([^，]+)` |

---

## 各解析器返回类型

| 解析器 | 调用的方法 | 返回类型 |
|--------|-----------|----------|
| AnalyzeByJSonPath | `.getStringList(rule)` | `List<String>` |
| AnalyzeByJSonPath | `.getString(rule)` | `String?` |
| AnalyzeByJSonPath | `.getObject(rule)` | `Any` |
| AnalyzeByJSonPath | `.getList(rule)` | `ArrayList<Any>` |
| AnalyzeByXPath | `.getStringList(rule)` | `List<String>` |
| AnalyzeByJSoup | `.getStringList(rule)` | `List<String>` |

---

## 简单场景 vs 复杂场景

### 简单场景（大多数书源）

**特点**：每个规则字段单独使用，无连接符

```
ruleBookInfo:
  name: "h1>a@text"      ← 单规则，直接解析
  author: ".author@text"  ← 单规则，直接解析
```

**流程**：setContent → getString(单规则) → 返回结果

### 复杂场景（少数书源）

**特点**：一个字段包含多个子规则，用连接符组合

```
规则: "@XPath://div && .title @@ $.author"
```

**流程**：setContent → splitSourceRule() → 切分成多个 SourceRule → 遍历执行

---

## 学习任务

1. **打开** `AnalyzeRule.kt`，找到 `getStringList()` 方法（第176行）
2. **追踪** `setContent()` → `splitSourceRule()` → `when(mode)` 分发流程
3. **打开** `AnalyzeByJSonPath.kt`，理解 `getStringList()` 内部如何调用 `ctx.read()`
4. **实践**：用书源测试不同前缀（@XPath: / @Json: / 无前缀）的效果
