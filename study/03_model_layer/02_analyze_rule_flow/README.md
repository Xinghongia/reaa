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

---

## 附录：JavaScript 桥接架构

### Q1: Android 能执行 JS 代码吗？

**问**：安卓原生是不能执行 JS 代码的对吧？但能够实现 Java 代码？书源里面的 JS 代码能够调用 `java` 对象？为什么啊？所以说最后还是安卓发送这些请求功能？

**答**：你的理解基本正确，让我更清晰地解释这个架构。

#### Android 能执行 JS 代码吗？

**能**，但不是原生支持，需要引入 **JS 引擎**。

```
Android 原生 ──不能──► 直接执行 JS

Android + Rhino 引擎 ──能──► 执行 JS
```

Legado 集成了一个叫 **Rhino** 的 JS 引擎，所以 Android 应用可以执行 JS 代码。

#### 调用流程（关键问题）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        书源规则解析流程                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 书源规则（字符串）                                                     │
│     "@js: var html = java.ajax(url); log(html);"                          │
│                                                                             │
│           ▼                                                                │
│                                                                             │
│  2. Legado 的 Rhino JS 引擎执行这段 JS 代码                                │
│     （JS 引擎在 Android 里面运行）                                          │
│                                                                             │
│           ▼                                                                │
│                                                                             │
│  3. JS 代码调用 java.ajax()                                               │
│     （JS 引擎内置的 bridge，发现 java 对象就转发）                           │
│                                                                             │
│           ▼                                                                │
│                                                                             │
│  4. 转发到 JsExtensions.kt 的 ajax() 方法                                  │
│     （这是 Kotlin/Java 代码）                                               │
│                                                                             │
│           ▼                                                                │
│                                                                             │
│  5. ajax() 方法内部用 OkHttp 发送网络请求                                  │
│     （真正的 Android 网络请求！）                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 回答你的问题

| 问题 | 答案 |
|------|------|
| **安卓原生不能执行 JS？** | 对，需要 JS 引擎 |
| **安卓原生能实现 Java 代码？** | 对，原生支持 |
| **JS 代码能调用 java 对象？** | 对，这是 Legado 故意设计的桥接层 |
| **最终是谁发送请求？** | **Android（通过 OkHttp）**，JS 只是写了调用逻辑 |

#### 代码验证

看 `JsExtensions.kt` 里的 ajax 方法：

```kotlin
fun ajax(url: Any, callTimeout: Long?): String? {
    // ...
    val analyzeUrl = AnalyzeUrl(
        urlStr,
        source = getSource(),
        callTimeout = callTimeout,
        coroutineContext = context
    )
    return kotlin.runCatching {
        analyzeUrl.getStrResponse().body  // <-- 这里才是真正发请求的地方
    }.getOrElse {
        it.stackTraceStr
    }
}
```

可以看到，`ajax()` 方法内部用的是 `AnalyzeUrl.getStrResponse()`，这底层就是 **OkHttp** 在发请求。

#### 总结

> **JS 代码只是"命令"，真正干活的是 Android！**
>
> JS 引擎负责解析书源规则，发现 `java.ajax()` 就转发给 Android，Android 用 OkHttp 真正发请求，然后把结果返回给 JS。

---

### Q2: 什么叫包含了在 JavaScript 中，又为什么通过 java 对象调用？

#### 概念解释

##### 1. 书源规则支持 JavaScript

Legado 的书源规则（如正文提取、目录分析等）允许使用 **JavaScript** 来编写解析逻辑。这是因为：
- 有些网站的内容需要动态执行 JS 才能获取（如加密内容、混淆数据）
- JS 语法灵活，可以处理复杂的字符串操作

##### 2. JavaScript 不能直接访问 Android 功能

问题来了：JavaScript 运行在 **Rhino 引擎**（一个 JS 解释器）中，它本身：
- ❌ 不能发起网络请求
- ❌ 不能读写文件
- ❌ 不能访问 Cookie
- ❌ 不能弹出 Toast 提示

这些功能是 Android 系统提供的，JS 引擎根本无法直接调用。

##### 3. 通过 `java` 对象桥接

所以 Legado 设计了一个桥接层：

```
JavaScript 代码                    Kotlin/Android 原生代码
┌─────────────────────┐           ┌─────────────────────────┐
│  java.ajax(url)     │ ───────►  │  JsExtensions.ajax()    │
│  java.log("hello")  │ ───────►  │  JsExtensions.log()     │
│  java.getCookie()   │ ───────►  │  JsExtensions.getCookie│
└─────────────────────┘           └─────────────────────────┘
```

在书源 JS 规则中，你可以这样写：

```javascript
// 这些是 JS 代码，可以写进书源的 @js: 规则里
var html = java.ajax("https://example.com/book/1");  // 调用 Android 网络请求
java.log("抓取成功");                                  // 调用 Android 日志
var content = java.readTxtFile("chapter1.txt");       // 调用 Android 文件读取
```

##### 4. JsExtensions.kt 的作用

`JsExtensions.kt` 就是定义 **`java` 对象有哪些方法**：

```kotlin
interface JsExtensions {
    fun ajax(url: Any): String?    // 供 JS 调用的网络请求方法
    fun log(msg: Any?): Any?       // 供 JS 调用的日志方法
    fun getCookie(tag: String): String  // 供 JS 调用的获取 Cookie 方法
    // ... 共 80+ 个方法
}
```

##### 5. 调用流程图

```
书源规则 JS 代码
    │
    ▼
java.ajax(url)      ◄─── JS 语法调用 java 对象
    │
    ▼
JsExtensions.kt     ◄─── 定义 java 对象有哪些方法
    │
    ▼
Kotlin/Android     ◄─── 实际执行 Android 原生功能
    │
    ▼
返回结果给 JS
```

##### 总结

| 概念 | 解释 |
|------|------|
| **JS 代码** | 书源规则里写的 `@js: ...` 部分 |
| **java 对象** | Legado 暴露给 JS 的桥梁对象 |
| **JsExtensions.kt** | 定义这个 `java` 对象有哪些方法 |
| **Kotlin 实现** | 实际调用 Android 系统功能 |

简单理解就是：**JsExtensions.kt 让 JavaScript 能够"借道" Android 的功能来完成网络请求、文件操作等任务。**
