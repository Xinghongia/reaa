# JS 扩展用法

## 概述

JS 扩展（JsExtensions）为书源 JS 脚本提供调用 Java/Kotlin 方法的能力，包括网络请求、数据处理、调试日志等。

**源码位置**：`app/src/main/java/io/legado/app/help/JsExtensions.kt`

---

## 核心机制

### java 对象注入

```javascript
// 在 JS 环境中全局可用的 java 对象
java.ajax(url)           // 网络请求
java.getSource()         // 获取书源
java.log("info")         // 调试日志
java.java                 // 访问 Java 类
```

**注入原理**：
```
App.kt 初始化时：
RhinoWrapFactory.register(BookSource::class.java, NativeBaseSource.factory)
     ↓
RhinoScriptEngine.eval()
     ↓
JsExtensions 实现类被注入到 JS 全局作用域
     ↓
JS 中可通过 java.xxx 访问
```

---

## 网络请求

### ajax()

```javascript
// 基本用法
var html = java.ajax("https://example.com/book/list");

// 带超时
var html = java.ajax(["https://example.com/1", "https://example.com/2"]);
```

**内部实现**：
```kotlin
fun ajax(url: Any): String? {
    val analyzeUrl = AnalyzeUrl(
        urlStr,
        source = getSource(),
        coroutineContext = context  // 协程上下文传递
    )
    return analyzeUrl.getStrResponse().body
}
```

**为什么需要 AnalyzeUrl？**
- 自动处理重定向
- 自动携带 Cookie
- 自动应用限流
- 自动处理编码

### ajaxAll() 并发请求

```javascript
// 并发请求多个 URL
var urls = ["url1", "url2", "url3"];
var responses = java.ajaxAll(urls);

responses.forEach(function(resp) {
    java.log(resp.body);
});
```

**设计原理 - Flow 并发**：
```kotlin
fun ajaxAll(urlList: Array<String>): Array<StrResponse> {
    return runBlocking(context) {
        urlList.asFlow()
            .mapAsync(AppConfig.threadCount) { url ->
                AnalyzeUrl(url, source = getSource()).getStrResponseAwait()
            }
            .flowOn(IO)
            .toList()
            .toTypedArray()
    }
}
```

### connect() 获取完整响应

```javascript
// 获取完整响应（包含 header、状态码）
var resp = java.connect("https://example.com/api");
java.log("Status: " + resp.code);
java.log("Header: " + resp.headers);
java.log("Body: " + resp.body);
```

---

## 数据处理

### eval() - XPath 解析

```javascript
// 使用 JSOUP 解析 HTML
var element = java.eval(html, '#book .title');

// 链式调用
var text = java.eval(html, 'div.content').text();
var href = java.eval(html, 'a.chapter').attr('href');
```

### getString() - 字符串提取

```javascript
// 正则提取
var author = java.getString(html, "作者[：:]([^<]+)", false, 0);

// 提取并 trim
var name = java.getString(html, "书名[：:]([^<]+)", true, 0);
```

### getElementsByRegex() - 正则批量提取

```javascript
// 提取所有匹配
var chapters = java.getElementsByRegex(html, "<chapter>(.*?)</chapter>");

chapters.forEach(function(ch) {
    java.log(ch);
});
```

---

## Java 类访问

### java.java 访问类

```javascript
// 访问 Java 静态类
var File = java.java.java.io.File;
var file = new File("/path/to/file");

// 调用静态方法
var exists = file.exists();
```

### 动态实例化

```javascript
// 通过类名创建实例
var HashMap = java.java.java.util.HashMap;
var map = new HashMap();

map.put("key", "value");
var value = map.get("key");
```

---

## 调试与日志

### log()

```javascript
// 输出日志（会在 Logcat 和调试界面显示）
java.log("当前 URL: " + url);
java.log("解析结果: " + JSON.stringify(result));
```

**日志级别**：
```kotlin
fun log(msg: String?) {
    AppLog.put("JS Log: $msg")  // AppLog 是应用级日志
}
```

### getTag()

```javascript
// 获取当前书源的标签（用于分类筛选）
var tag = java.getTag();
if (tag === "起点中文网") {
    // 特殊处理
}
```

---

## 书源 JS 示例

### 搜索规则

```javascript
function getSearchUrl() {
    // 使用书源配置变量
    return this.java_get_source().getBookSourceUrl;
}

function getName() {
    return "书名";
}

function getAuthor() {
    // 正则提取作者
    var match = html.match(/作者[：:]\s*([^<]+)/);
    return match ? match[1].trim() : "";
}

function getKind() {
    // 提取分类
    var kind = java.eval(html, '.tag').text();
    return kind;
}
```

### 探索规则

```javascript
function getExploreBooks() {
    // 解析列表
    var list = java.eval(html, '.book-list li');

    var result = [];
    list.forEach(function(item) {
        result.push({
            name: java.eval(item, '.title').text(),
            author: java.eval(item, '.author').text(),
            bookUrl: java.eval(item, 'a').attr('href'),
            coverUrl: java.eval(item, 'img').attr('src')
        });
    });

    return result;
}
```

### 目录规则

```javascript
function getChapterList() {
    // 解析目录
    var chapters = java.eval(html, '#chapter_list a');

    var result = [];
    var index = 0;
    chapters.forEach(function(ch) {
        result.push({
            title: ch.text(),
            url: ch.attr('href'),
            index: index++
        });
    });

    return result;
}
```

### 内容规则

```javascript
function getContent() {
    // 获取正文
    var content = java.eval(html, '#content').text();

    // 净化处理
    content = content.replace(/请记住本书首发域名[^-]+/, '');
    content = content.replace(/阅读网址[^-]+/, '');

    return content;
}
```

---

## 数据暂存

### variableMap - 内存暂存

```javascript
// 存储数据（会话级别）
java.putVariable("searchPage", page + 1);
var nextPage = java.getVariable("searchPage");

// 存储大对象
java.putBigVariable("cache", JSON.stringify(data));
var cache = JSON.parse(java.getBigVariable("cache"));
```

**区别**：
| 方法 | 存储位置 | 生命周期 | 容量 |
|------|---------|---------|------|
| `putVariable` | 内存（Book.variable） | 当前书籍会话 | 小数据 |
| `putBigVariable` | 文件（RulBigDataHelp） | 持久化 | 大数据 |

---

## 常用工具方法

### 字符串处理

```javascript
// Base64 编解码
var encoded = java.base64Encode("hello");
var decoded = java.base64Decode(encoded);

// URL 编解码
var urlEncoded = java.urlEncode("你好");
var urlDecoded = java.urlDecode(urlEncoded);

// MD5
var hash = java.md5Encode("password");
```

### JSON 处理

```javascript
// 解析 JSON
var data = JSON.parse(jsonString);

// 序列化
var json = JSON.stringify(object);

// 安全解析（失败返回 null）
var data = java.getJSONObject(html, "data");
```

### 时间处理

```javascript
// 格式化时间
var now = java.now();  // 当前时间戳
var date = java.formatDate(now, "yyyy-MM-dd");
```

---

## 错误处理

### try-catch

```javascript
try {
    var html = java.ajax(url);
    var data = JSON.parse(html);
} catch (e) {
    java.log("Error: " + e.message);
}
```

### 防御式编程

```javascript
// 使用空合并
var title = (java.eval(html, '.title') || {}).text || '';

// 检查 null
if (result) {
    // 处理
}
```

---

## 学习任务

### 1. 分析网络请求流程

**打开文件**：
- `app/src/main/java/io/legado/app/help/JsExtensions.kt` - `ajax` 方法
- `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt` - URL 分析

**思考**：
- `ajax` 和 `connect` 的区别？
- 为什么需要协程上下文传递？

### 2. 理解数据暂存

**打开文件**：
- `app/src/main/java/io/legado/app/data/entities/BaseBook.kt` - `variableMap`
- `app/src/main/java/io/legado/app/help/RuleBigDataHelp.kt` - 大数据存储

**思考**：
- `putVariable` 和 `putBigVariable` 的使用场景？
- 为什么需要大数据存储？

### 3. 编写完整书源 JS

**打开文件**：
- `app/src/main/assets/help/JsHelp.md` - JS 帮助文档
- `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt` - 规则解析

**实践**：
- 编写一个简单的书源 JS（搜索 + 目录 + 内容）
- 使用 `java.log` 调试输出
