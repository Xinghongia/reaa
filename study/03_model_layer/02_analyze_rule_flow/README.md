# 书源解析完整流程

## 概述

以搜索功能为例，详解书源从配置到数据提取的完整解析流程。

---

## 完整数据流

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        书源解析完整流程（以搜索为例）                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【第一阶段】URL 构造                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ 书源配置                                                             │  │
│  │ searchUrl = "/search?page={{page}}&keyword={{key}}"                  │  │
│  │ header = "{\"User-Agent\": \"okhttp/4.9.2\", ...}"                   │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ AnalyzeUrl.initUrl()                                               │  │
│  │ 1. analyzeJs()      → 执行 @js: 和 <js></js> 中的 JS               │  │
│  │ 2. replaceKeyPageJs() → 替换 {{key}}, {{page}}                     │  │
│  │ 3. analyzeUrl()     → 解析 URL 参数（charset, method, body 等）      │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                       │
│  【第二阶段】网络请求                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ OkHttp 发送请求                                                     │  │
│  │ GET /search?page=1&keyword=斗破苍穹                                  │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                       │
│  【第三阶段】规则解析                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ AnalyzeRule.setContent(json) → 保存内容，判断 isJSON                 │  │
│  │ getStringList("$.data[*]")   → 提取书籍列表                         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                       │
│  【第四阶段】字段提取                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ getString("$.novelName")  → "斗破苍穹"                              │  │
│  │ getString("$.authorName") → "天蚕土豆"                              │  │
│  │ getString("$.cover")      → "https://..."                          │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                       │
│  【第五阶段】URL 处理                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ bookUrl = "/novel/{{$.novelId}}?isSearch=1"                       │  │
│  │ → innerRule() 替换 {{}} → JS 拼接                                 │  │
│  │ → NetworkUtils.getAbsoluteURL() → 转绝对 URL                        │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                       │
│  【最终结果】Book 对象                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 四大核心类的职责

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              四大核心类                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  AnalyzeUrl                                                                 │
│  ├─ 职责: URL 构造和参数处理                                                │
│  ├─ 输入: 书源配置 (searchUrl, header, bookSourceUrl)                        │
│  └─ 输出: 完整的请求信息 (URL, Method, Headers, Body)                       │
│           ↓                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │ initUrl()                                                             │ │
│  │    ↓                                                                  │ │
│  │ analyzeJs()      → 执行 @js: / <js></js> 中的 JS                     │ │
│  │ replaceKeyPageJs() → 替换 {{key}} {{page}}                          │ │
│  │ analyzeUrl()     → 解析 URL 参数 {charset, method, body, ...}        │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│           ↓                                                                  │
│  AnalyzeRule                                                               │
│  ├─ 职责: 规则解析入口（唯一入口）                                           │
│  ├─ 输入: 原始内容 (HTML/JSON) + 规则字符串                                 │
│  └─ 输出: 提取的数据 (List<String>)                                        │
│           ↓                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │ setContent(content)        → 保存内容，判断 isJSON                    │ │
│  │ getStringList(rule)        → 外部调用入口                             │ │
│  │    ↓                                                                      │ │
│  │ splitSourceRule()         → 切分规则字符串                           │ │
│  │    ↓                                                                      │ │
│  │ for (sourceRule in list)  → 遍历 SourceRule                          │ │
│  │    ↓                                                                      │ │
│  │ when(mode) → 分发到具体解析器                                          │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│           ↓                                                                  │
│  RuleAnalyzer                                                              │
│  ├─ 职责: 规则字符串切分工具                                               │
│  └─ 功能:                                                                  │
│      • splitRule("&&", "||", "%%")  → 分割多规则                         │
│      • innerRule("{{", "}}")        → 替换内嵌 JS                        │
│      • innerRule("{$.")              → 替换 JSONPath 内嵌规则              │
│      • chompBalanced()              → 平衡括号匹配                        │
│           ↓                                                                  │
│  AnalyzeByJSonPath                                                         │
│  ├─ 职责: JSON 内容解析                                                    │
│  └─ 功能:                                                                  │
│      • parse(json)          → 解析 JSON 为 ReadContext                     │
│      • getString(rule)      → 获取单个字符串                               │
│      • getStringList(rule)  → 获取字符串列表                              │
│      • getObject(rule)      → 获取原始对象                                 │
│      • getList(rule)        → 获取列表                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 源码位置对照表

| 源码位置 | 功能 | 对应书源示例 |
|----------|------|--------------|
| `AnalyzeUrl.initUrl()` | URL 初始化 | `searchUrl = "/search?keyword={{key}}"` |
| `AnalyzeUrl.analyzeJs()` | 执行 JS | `header = @js: JSON.stringify({...})` |
| `AnalyzeUrl.replaceKeyPageJs()` | 替换变量 | `{{key}}`, `{{page}}` |
| `AnalyzeRule.setContent()` | 设置内容 | 收到 JSON 响应 |
| `AnalyzeRule.splitSourceRule()` | 切分规则 | `ruleStr → SourceRule列表` |
| `RuleAnalyzer.splitRule()` | 分割多规则 | `&& \|\| %%` |
| `RuleAnalyzer.innerRule()` | 内嵌规则 | `{{}}` `{$}` |
| `getAnalyzeByJSonPath().getStringList()` | JSONPath | `$.data[*]` |

---

## 实际书源解析示例

### 书源配置

```json
{
    "bookSourceUrl": "http://api.lemiyigou.com",
    "header": "{\"User-Agent\": \"okhttp/4.9.2\", ...}",
    "searchUrl": "/search?page={{page}}&keyword={{key}}",
    "ruleSearch": {
        "bookList": "$.data[*]",
        "name": "$.novelName",
        "author": "$.authorName",
        "coverUrl": "$.cover",
        "bookUrl": "/novel/{{$.novelId}}?isSearch=1"
    }
}
```

### 解析流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 阶段一：URL 构造                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  输入: searchUrl = "/search?page={{page}}&keyword={{key}}"                  │
│                                                                             │
│  1. analyzeJs()                                                            │
│     → 无 @js: / <js>，跳过                                                  │
│                                                                             │
│  2. replaceKeyPageJs()                                                     │
│     → {{page}} → 1                                                         │
│     → {{key}} → "斗破苍穹"                                                 │
│     → 结果: "/search?page=1&keyword=斗破苍穹"                               │
│                                                                             │
│  3. analyzeUrl()                                                           │
│     → baseUrl = "http://api.lemiyigou.com"                                 │
│     → 拼接: "http://api.lemiyigou.com/search?page=1&keyword=斗破苍穹"      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│ 阶段二：网络请求                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  GET http://api.lemiyigou.com/search?page=1&keyword=斗破苍穹                │
│  Headers:                                                                  │
│    User-Agent: okhttp/4.9.2                                                 │
│    Authorization: Bearer ...                                                │
│                                                                             │
│  响应:                                                                      │
│  {                                                                         │
│    "data": [                                                               │
│      { "novelId": "12345", "novelName": "斗破苍穹",                        │
│        "authorName": "天蚕土豆", "cover": "https://...",                   │
│        "summary": "讲述了天才少年萧炎..." }                                  │
│    ]                                                                       │
│  }                                                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│ 阶段三：规则解析                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. setContent(jsonString)                                                  │
│     → isJSON = true                                                        │
│     → content = jsonString                                                  │
│                                                                             │
│  2. getStringList("$.data[*]")  ← bookList                                │
│     → splitSourceRule("$.data[*]")                                        │
│     → SourceRule(mode=Json, rule="$.data[*]")                              │
│     → getAnalyzeByJSonPath(content).getStringList()                      │
│     → 返回书籍列表                                                         │
│                                                                             │
│  3. 循环提取每个字段:                                                       │
│     getString("$.novelName")  → "斗破苍穹"                                │
│     getString("$.authorName") → "天蚕土豆"                                │
│     getString("$.cover")      → "https://..."                             │
│                                                                             │
│  4. bookUrl = "/novel/{{$.novelId}}?isSearch=1"                           │
│     → innerRule("{{", "}}") { evalJS("$..novelId") }                     │
│     → "$..novelId" 在上一轮 result 中提取 → "12345"                       │
│     → 拼接: "/novel/12345?isSearch=1"                                     │
│     → 转绝对URL: "http://api.lemiyigou.com/novel/12345?isSearch=1"       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│ 最终结果                                                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Book(                                                                     │
│    name = "斗破苍穹",                                                       │
│    author = "天蚕土豆",                                                     │
│    coverUrl = "https://...",                                               │
│    bookUrl = "http://api.lemiyigou.com/novel/12345?isSearch=1",           │
│    intro = "讲述了天才少年萧炎...",                                         │
│    ...                                                                     │
│  )                                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 复杂规则解析

### 1. 多规则连接符

书源规则：
```json
"intro": "$..summary##(^|[。！？]+[”》）】]?)##$1<br>"
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 规则: "$..summary##(^|[。！？]+[”》）】]?)##$1<br>"                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  splitSourceRule() 切分                                                    │
│  → SourceRule(mode=Json, rule="$..summary", replaceRegex="正则", ...)      │
│                                                                             │
│  执行流程:                                                                  │
│  1. getAnalyzeByJSonPath(content).getString("$..summary")                  │
│     → "这是一个简介。这是一个简介！"                                        │
│                                                                             │
│  2. replaceRegex("正则", result)                                            │
│     → 替换: "这是一个简介<br>这是一个简介！"                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2. 内嵌 JS 变量

书源规则：
```json
"kind": "{{$..className}},{{$.averageScore}}分"
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 规则: "{{$..className}},{{$.averageScore}}分"                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  innerRule("{{", "}}") { evalJS(it) }                                     │
│                                                                             │
│  第一次匹配: {$..className}                                                │
│  → evalJS("$..className")                                                  │
│  → "玄幻,都市"                                                             │
│                                                                             │
│  第二次匹配: {$.averageScore}                                              │
│  → evalJS("$.averageScore")                                                │
│  → "9.2"                                                                   │
│                                                                             │
│  拼接结果: "玄幻,都市,9.2分"                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3. AES 解密

书源规则：
```json
"chapterUrl": "$.path@js:java.aesBase64DecodeToString(result,\"f041c49714d39908\",\"AES/CBC/PKCS5Padding\",\"0123456789abcdef\")"
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 规则: "$.path@js:java.aesBase64DecodeToString(...)"                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. splitSourceRule()                                                      │
│     → SourceRule(mode=Json, rule="$.path")                                 │
│     → SourceRule(mode=Js, rule="java.aesBase64DecodeToString(...)")        │
│                                                                             │
│  2. 第一步: getAnalyzeByJSonPath(content).getString("$.path")            │
│     → "base64编码的字符串..."                                              │
│                                                                             │
│  3. 第二步: evalJS("java.aesBase64DecodeToString(result,...)")            │
│     → result = 上一步的提取结果                                            │
│     → 解密得到真实URL: "/chapter/12345"                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 学习任务

1. **打开** `AnalyzeUrl.kt`，追踪 `initUrl()` → `analyzeJs()` → `replaceKeyPageJs()` → `analyzeUrl()` 流程
2. **打开** `AnalyzeRule.kt`，找到 `getStringList()` 方法，理解规则分发逻辑
3. **打开** `RuleAnalyzer.kt`，理解 `splitRule()` 如何切分 `&& || %%` 连接符
4. **实践**：用调试功能测试书源，观察 URL 构造和规则解析过程

---

# 高级书源实战：爱下电子书

## 实战背景

网站 **爱下电子书 (ixdzs8.com)** 是一个典型的有反爬机制的小说网站：

| 页面 | 反爬情况 |
|------|----------|
| 搜索页 | ✅ 正常，无需验证 |
| 详情页 | ✅ 正常 |
| 目录页 | ✅ 正常 |
| 正文页 | ❌ 有 JS 验证挑战 |

---

## 完整书源配置

```json
{
   "bookSourceGroup": "小说",
   "bookSourceName": "爱下电子书",
   "bookSourceType": 0,
   "bookSourceUrl": "https://ixdzs8.com",
   "enabled": true,
   "enabledCookieJar": true,
   "enabledExplore": true,
   "respondTime": 180000,
   "searchUrl": "/bsearch?q={{key}}&page={{page}}",
   "ruleSearch": {
     "bookList": ".u-list@tag.li",
     "name": "a@title",
     "author": ".bauthor@tag.a@text",
     "coverUrl": "img@src\n@js:\nif (result == \"https://img22.ixdzs.com/nopic2.jpg\"){\n\tnull;\n\t}else{result}",
     "intro": ".l-p2@text",
     "kind": ".lz@text",
     "lastChapter": ".l-chapter@text",
     "wordCount": ".size@text",
     "bookUrl": "a@href",
     "checkKeyWord": "洪荒之龙族称霸"
   },
   "ruleBookInfo": {
     "intro": "爱下电子书\n书名:《{{@class.n-text[0]@tag.h1@text}}》\n作者:{{@class.bauthor[0]@text}}\n{{@class.trend@text}}\n简介:{{@id.intro@text## 　　##\n}}",
     "kind": ".tags@tag.a@text",
     "downloadUrls": ".btn-solid@tag.a@href"
   },
   "ruleToc": {
     "chapterList": "@js:\nvar bid = baseUrl.match(/read\\/(\\d+)/)[1];\nvar resp = java.post(source.getKey()+\"/novel/clist/\",\"bid=\"+bid,{});\nvar json = JSON.parse(resp.body()).data;\nvar page = 0;\nvar n = \"\";\nfor(var i=json.length - 1; i >= 0; i--){\n\tif(json[i].ctype === \"1\"){\n\t\tn = json[i].title;\n\t\tjson.splice(i, 1);\n\t\tcontinue;\n\t\t}\n\tpage = json[i][\"ordernum\"];\n\tjson[i][\"url\"] = source.getKey() + \"/read/\" + bid + \"/p\" + page + \".html\";\n\tjson[i][\"n\"] = n;\n\t}\njson;",
     "chapterName": "title",
     "chapterUrl": "url",
     "updateTime": "n"
   },
   "ruleContent": {
     "content": "@js:\nvar token = src.match(/token\\s*=\\s*\"([^\"]+)\"/)?.[1];\nvar tourl = baseUrl + \"?challenge=\" + encodeURIComponent(token);\nvar sectionHtml = java.ajax(tourl).match(/<section>\\s*((?:<p>.*?<\\/p>\\s*)+)(.*?)\\s*<\\/section>/i)[1];\nvar text = sectionHtml.replace(new RegExp('<\\\\/?p>', 'g'), '\\n').trim();\ntext;"
   },
   "exploreUrl": "玄幻::/sort/1/index-0-0-1-{{page}}.html\n武侠::/sort/10/index-0-0-1-{{page}}.html\n修真::/sort/2/index-0-0-1-{{page}}.html\n灵异::/sort/6/index-0-0-1-{{page}}.html\n日榜::/hot/day/?page={{page}}\n周榜::/hot/month/?page={{page}}\n总榜::/hot/?page={{page}}"
}
```

---

## 正文反爬解决方案详解

### 问题分析

正文页返回的 HTML 包含 JS 验证：

```html
<title>正在验证浏览器</title>
<p>请稍等，正在进行安全验证...</p>
<script>
    let token = "MTc3NjgzODQ5OTo4YmU5MzEzOTU3ODI5YWQ4Nzc2NDJiMjUyOTIwYTYyMGRiMDc1NGY2NzBmM2EzMDUxNmU4NDg2YjllMDZmNWE3";
    window.location.href = location.pathname + "?challenge=" + encodeURIComponent(token);
</script>
```

### 解决方案：@js: 二次请求

```javascript
@js:
var token = src.match(/token\s*=\s*"([^"]+)"/)?.[1];           // 1. 从验证页提取 token
var tourl = baseUrl + "?challenge=" + encodeURIComponent(token); // 2. 构造验证 URL
var sectionHtml = java.ajax(tourl).match(/<section>\s*((?:<p>.*?<\/p>\s*)+)(.*?)\s*<\/section>/i)[1]; // 3. 二次请求获取正文
var text = sectionHtml.replace(new RegExp('<\\/?p>', 'g'), '\n').trim(); // 4. 清理标签
text;
```

---

## 目录 API 调用方案详解

### 问题分析

目录页 HTML 结构复杂，且有分页限制。

### 解决方案：调用内部 API

```javascript
@js:
var bid = baseUrl.match(/read\/(\d+)/)[1];                    // 1. 提取书籍ID
var resp = java.post(source.getKey()+"/novel/clist/","bid="+bid,{}); // 2. POST 请求 API
var json = JSON.parse(resp.body()).data;                      // 3. 解析 JSON
var page = 0;
var n = "";
for(var i=json.length - 1; i >= 0; i--){                      // 4. 遍历处理
    if(json[i].ctype === "1"){                                //   卷名处理
        n = json[i].title;
        json.splice(i, 1);
        continue;
    }
    page = json[i]["ordernum"];                               //   章节页码
    json[i]["url"] = source.getKey() + "/read/" + bid + "/p" + page + ".html"; // 5. 构造URL
    json[i]["n"] = n;                                          //   添加卷名
}
json;
```

---

## @js: 语法详解

### 1. 基础语法

```json
"字段名": "@js: JavaScript代码"
```

### 2. 可用变量

| 变量 | 说明 |
|------|------|
| `result` | 上一步提取的结果 |
| `src` | 当前页面的完整 HTML 源码 |
| `baseUrl` | 当前页面的 URL |
| `source` | 书源对象 |
| `source.getKey()` | 书源 URL（如 `https://ixdzs8.com`）|
| `page` | 当前页码 |

### 3. 可用函数

| 函数 | 说明 |
|------|------|
| `java.ajax(url)` | GET 请求，返回 HTML |
| `java.post(url, body, header)` | POST 请求 |
| `java.get(url, header)` | 带请求头的 GET |
| `java.base64Decode(str)` | Base64 解码 |
| `java.base64Encode(str)` | Base64 编码 |
| `java.md5(str)` | MD5 加密 |
| `java.aesDecode(str, key, mode, iv)` | AES 解密 |

### 4. 返回值

`@js:` 代码块的最后一行会自动作为结果返回。

---

## 核心 JS 技巧

### 技巧 1：正则提取

```javascript
var token = src.match(/token\s*=\s*"([^"]+)"/)?.[1];
```

**示例**：

```html
<script>let token = "abc123";</script>
```

| 部分 | 含义 |
|------|------|
| `src` | 当前页面的完整 HTML 源码 |
| `.match(/.../)` | 正则匹配 |
| `token\s*=\s*` | 匹配 `token = `（允许空格）|
| `"([^"]+)"` | 捕获双引号内的值 |
| `?.[1]` | 可选链，如果没匹配到返回 undefined |

**执行流程**：
```
src.match(/token\s*=\s*"([^"]+)"/)?.[1]
         ↓
匹配结果: ["token = \"abc123\"", "abc123"]
         ↓
?.[1] → "abc123"
```

### 技巧 2：二次请求

```javascript
var tourl = baseUrl + "?challenge=" + encodeURIComponent(token);
var sectionHtml = java.ajax(tourl);
```

| 步骤 | 代码 |
|------|------|
| URL 编码 | `encodeURIComponent(token)` |
| 发起请求 | `java.ajax(url)` |
| 获取响应 | 返回 HTML 字符串 |

### 技巧 3：正则提取 HTML 内容

```javascript
var sectionHtml = html.match(/<section>\s*((?:<p>.*?<\/p>\s*)+)(.*?)<\/section>/i)[1];
```

| 正则部分 | 含义 |
|----------|------|
| `<section>\s*` | 匹配 `<section>` 标签 |
| `((?:<p>.*?<\/p>\s*)+)` | **捕获组**：匹配多个 `<p>...</p>` |
| `(.*?)` | 非贪婪匹配任意内容 |
| `<\/section>` | 匹配结束标签 |
| `/i` | 忽略大小写 |
| `[1]` | 取第一个捕获组 |

### 技巧 4：字符串替换

```javascript
var text = sectionHtml.replace(new RegExp('<\\/?p>', 'g'), '\n').trim();
```

| 部分 | 含义 |
|------|------|
| `new RegExp('<\\/?p>', 'g')` | 匹配 `<p>` 或 `</p>` |
| `'g'` | 全局替换 |
| `'\n'` | 替换成换行符 |
| `.trim()` | 去掉首尾空白 |

### 技巧 5：POST 请求

```javascript
var resp = java.post(source.getKey()+"/novel/clist/","bid="+bid,{});
var json = JSON.parse(resp.body()).data;
```

| 参数 | 说明 |
|------|------|
| `source.getKey()` | 书源 URL |
| `"bid="+bid` | POST 请求体 |
| `{}` | 请求头（空）|
| `resp.body()` | 响应体字符串 |
| `JSON.parse()` | 转 JSON 对象 |

### 技巧 6：JSON 数据处理

```javascript
for(var i=json.length - 1; i >= 0; i--){
    if(json[i].ctype === "1"){
        n = json[i].title;
        json.splice(i, 1);
        continue;
    }
    page = json[i]["ordernum"];
    json[i]["url"] = source.getKey() + "/read/" + bid + "/p" + page + ".html";
    json[i]["n"] = n;
}
```

---

## 常用正则表达式

### 提取 token

```javascript
/token\s*=\s*"([^"]+)"/           // "token":"xxx"
/token\s*=\s*'([^']+)'/           // 'token':'xxx'
/token\s*=\s*(\w+)/               // token=xxx
```

### 提取 HTML 内容

```javascript
/<section>([\s\S]*?)<\/section>/i  // section 标签内容
/<p>([\s\S]*?)<\/p>/i              // p 标签内容
/<div class="content">([\s\S]*?)<\/div>/i  // div 内容
```

### URL 处理

```javascript
/\/read\/(\d+)/                      // 提取书籍 ID
/\/read\/(\d+)\/p(\d+)\.html/       // 提取书籍 ID 和章节页码
/[?&]challenge=([^&]+)/              // 提取 challenge 参数
```

---

## 调试技巧

### 1. 打印变量

```javascript
@js:
console.log("result:", result);
console.log("src:", src);
result;
```

### 2. 简化测试

```javascript
@js:
result                              // 直接返回 result 测试
```

### 3. 错误处理

```javascript
@js:
try {
    var token = src.match(/token\s*=\s*"([^"]+)"/)?.[1];
    if (!token) throw new Error("未找到 token");
    // ...
} catch(e) {
    console.log("Error:", e.message);
    result;
}
```

---

## 完整数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                         搜索流程                                  │
├─────────────────────────────────────────────────────────────────┤
│  /bsearch?q=斗破苍穹                                             │
│       ↓                                                          │
│  HTML: <li class="burl">...                                      │
│       ↓                                                          │
│  ruleSearch.bookList: ".u-list@tag.li"                          │
│       ↓                                                          │
│  提取: name, author, coverUrl, bookUrl...                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                         正文流程（关键）                          │
├─────────────────────────────────────────────────────────────────┤
│  请求: /read/359916/p362.html                                    │
│       ↓                                                          │
│  返回验证页: <script>token="abc123"</script>                     │
│       ↓                                                          │
│  @js: var token = src.match(/token.../)?.[1];                   │
│       ↓                                                          │
│  构造: tourl = "/read/359916/p362.html?challenge=abc123"         │
│       ↓                                                          │
│  java.ajax(tourl) → 返回真实正文页                               │
│       ↓                                                          │
│  正则提取: <section>...</section>                                │
│       ↓                                                          │
│  清理标签: 替换 <p> 为换行符                                     │
│       ↓                                                          │
│  返回纯文本内容                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 练习任务

### 初级：简单替换

```json
"name": "h1@text@js:result.trim()"
```

### 中级：正则提取

```json
"author": "@js:src.match(/作者：([^<]+)/)?.[1]"
```

### 高级：二次请求 + 正文提取

```json
"content": "@js:\nvar token = src.match(/token\\s*=\\s*\"([^\"]+)\"/)?.[1];\nvar tourl = baseUrl + \"?challenge=\" + encodeURIComponent(token);\nvar html = java.ajax(tourl);\nvar section = html.match(/<section>[\\s\\S]*?<\\/section>/i)[0];\nsection.replace(/<\\/?p>/g, '\\n').trim();"
```

### 实战：模仿 ixdzs8 写一个书源

1. 找一个有反爬的网站
2. 分析验证机制
3. 用 `@js:` + `java.ajax()` 绕过验证
4. 用正则提取内容
5. 测试完整流程
