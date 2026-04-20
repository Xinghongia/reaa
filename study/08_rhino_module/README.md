# JS 引擎模块

## 概述

JS 引擎模块为 Legado 提供 JavaScript 脚本执行能力，用于书源规则解析、网页内容提取、数据清洗等核心功能。

**源码位置**：`modules/rhino/src/main/java/com/script/rhino/`

**学习目标**：理解 Rhino 引擎封装、协程集成、安全沙箱、JS 扩展机制。

---

## 架构设计

### 为什么需要 JS 引擎？

```
┌─────────────────────────────────────────────────────────────┐
│                      书源规则                                │
│  searchRule = "JSOUP('.booklist li', ...) + JS('...')"     │
│  contentRule = "JSONPATH('$.chapter_list')"                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    AnalyzeRule                              │
│              (规则解析器 - 支持 JS)                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   RhinoScriptEngine                        │
│                  (JS 引擎封装)                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   Mozilla Rhino 1.7.14                      │
│                  (JavaScript 引擎)                          │
└─────────────────────────────────────────────────────────────┘
```

**设计原理 - 为什么要用 JS？**
- 书源网站结构多样，固定解析规则无法覆盖
- JS 可自定义解析逻辑，灵活应对各种网站
- 书源作者可以编写 JS 代码实现复杂提取

### 核心组件

```
com.script.rhino/
├── RhinoScriptEngine.kt      # JSR-223 脚本引擎实现
├── RhinoContext.kt           # 协程上下文集成
├── RhinoTopLevel.kt          # 全局作用域
├── RhinoWrapFactory.kt       # Java 对象包装工厂
├── RhinoClassShutter.kt     # 安全沙箱
├── ExternalScriptable.kt     # 外部作用域
├── InterfaceImplementor.kt   # 接口实现
├── JSAdapter.kt             # JS 适配器
├── JavaAdapter.kt           # Java 适配器
├── RhinoErrors.kt           # 错误定义
└── RhinoExtensions.kt        # 协程扩展
```

---

## JSR-223 规范

### 什么是 JSR-223？

**JSR-223** 是 Java 的脚本引擎 API 标准，定义了 `ScriptEngine` 接口，让 Java 应用可以调用各种脚本语言。

```kotlin
// 标准 JSR-223 用法
val engine = ScriptEngineManager().getEngineByName("JavaScript")
val result = engine.eval("1 + 2")  // 3
```

### RhinoScriptEngine 实现

```kotlin
object RhinoScriptEngine : AbstractScriptEngine(), Invocable, Compilable {

    override fun eval(reader: Reader, scope: Scriptable, ...): Any? {
        val cx = Context.enter() as RhinoContext
        cx.allowScriptRun = true
        cx.recursiveCount++

        try {
            cx.checkRecursive()  // 防止无限递归
            ret = cx.evaluateReader(scope, reader, filename, 1, null)
        } finally {
            Context.exit()
        }
        return unwrapReturnValue(ret)
    }
}
```

**设计原理 - 单例模式**：
- `RhinoScriptEngine` 是 `object`，全局唯一
- 避免重复创建引擎，减少开销
- 线程安全：`@Synchronized` 保护关键方法

### 协程集成

```kotlin
class RhinoContext(factory: ContextFactory) : Context(factory) {
    var coroutineContext: CoroutineContext? = null
    var allowScriptRun = false
    var recursiveCount = 0

    @Throws(RhinoInterruptError::class)
    fun ensureActive() {
        coroutineContext?.ensureActive()
    }

    @Throws(RhinoRecursionError::class)
    fun checkRecursive() {
        if (recursiveCount >= 10) {
            throw RhinoRecursionError()
        }
    }
}
```

**为什么需要协程集成？**
```
问题：JS 脚本可能执行很慢或死循环

解决：协程可取消 + 递归深度限制
```

---

## 安全沙箱

### 为什么需要安全控制？

```javascript
// 恶意书源可能尝试：
java.lang.System.exit(0)           // 关闭应用
java.lang.Runtime.getRuntime()     // 获取运行时
android.app.Activity              // 访问 Activity
```

### RhinoClassShutter

```kotlin
object RhinoClassShutter : ClassShutter {

    private val visibleClasses = hashSetOf(
        // 允许访问的 Android 类
        "android.content.Intent",
        "android.provider.Settings",
        "android.os.Looper",

        // 允许访问的工具类
        "cn.hutool.core.util.RuntimeUtil",
        "cn.hutool.core.util.ReflectUtil",

        // 允许访问的应用类
        "io.legado.app.data.AppDatabase",
        "io.legado.app.help.JsExtensions",
    )

    private val visiblePackages = hashSetOf(
        "android.system",
        "android.database",
        "dalvik.system",
        "java.lang.reflect",
    )

    override fun visibleToScripts(fullClassName: String): Boolean {
        // 白名单模式：默认拒绝，只允许列表中的类
    }
}
```

**设计原理 - 白名单模式**：
```
黑名单模式：禁止少数危险类 → 不安全，可能漏掉
白名单模式：只允许安全类 → 安全，默认拒绝

危险类：java.lang.reflect.Method.invoke()
      java.lang.Class.forName()
      java.lang.System.exit()
```

### RhinoWrapFactory

```kotlin
object RhinoWrapFactory : WrapFactory() {

    override fun wrapJavaClass(cx: Context, scope: Scriptable, javaClass: Class<*>): Scriptable {
        if (!RhinoClassShutter.visibleToScripts(javaClass)) {
            // 未授权的类只能以包形式访问，不能实例化
            val pkg = NativeJavaPackage(javaClass.name, null)
            return pkg
        }
        return RhinoClassShutter.wrapJavaClass(scope, javaClass)
    }
}
```

---

## Java-Kotlin 互操作

### JS 调用 Java 方法

```kotlin
// Kotlin 端注册扩展
RhinoWrapFactory.register(BookSource::class.java, NativeBaseSource.factory)
RhinoWrapFactory.register(Book::class.java, NativeBaseSource.factory)
RhinoWrapFactory.register(HttpTTS::class.java, NativeBaseSource.factory)

// JS 端调用
var source = book.getBookSource();    // 调用 Kotlin 方法
source.getSearchUrl();                  // 访问 Kotlin 属性
```

### JsExtensions 接口

```kotlin
interface JsExtensions : JsEncodeUtils {
    fun getSource(): BaseSource?
    fun ajax(url: Any): String?
    fun connect(urlStr: String): StrResponse?
    fun getTag(): String?
    // ... 50+ 方法
}
```

**在 JS 中可用**：
```javascript
// 网络请求
var html = java.ajax("https://example.com/book/123");

// 访问书源信息
var source = java.getSource();
var searchUrl = source.getSearchUrl();

// 调试日志
java.log("debug info");
```

### InterfaceImplementor

```kotlin
class InterfaceImplementor(private val engine: Invocable) {

    fun <T> getInterface(obj: Any?, clazz: Class<T>): T {
        return clazz.cast(
            Proxy.newProxyInstance(
                clazz.classLoader,
                arrayOf(clazz),
                InterfaceImplementorInvocationHandler(obj, accContext)
            )
        )
    }
}
```

**设计原理 - 动态代理**：
```
JS 对象 → Java 接口
var r = new java.lang.Runnable() {
    run: function() { ... }
};
```

---

## 协程挂起机制

### ContinuationPending

```kotlin
override suspend fun evalSuspend(reader: Reader, scope: Scriptable): Any? {
    try {
        ret = cx.executeScriptWithContinuations(script, scope)
    } catch (e: ContinuationPending) {
        // 暂停脚本，保存 continuation
        val suspendFunction = e.applicationState as suspend () -> Any?
        val functionResult = suspendFunction()
        // 恢复执行
        ret = cx.resumeContinuation(continuation, scope, functionResult)
    }
}
```

**为什么需要挂起？**
```
JS 脚本执行时遇到 await（异步操作）

未集成协程：阻塞等待 → UI 卡死
集成协程：挂起 → 让出线程 → UI 流畅
```

---

## 书源规则中的 JS 使用

### 规则类型

| 规则类型 | 说明 | 示例 |
|---------|------|------|
| `JS()` | 执行 JS 代码 | `JS('result.push(...)')` |
| `JSOUP()` | DOM 解析 | `JSOUP('.booklist li', 'href')` |
| `JSONPATH()` | JSON 提取 | `JSONPATH('$.data.list[*]')` |
| `REGEX()` | 正则匹配 | `REGEX('<a>(.*?)</a>')` |
| `XPath()` | XML 解析 | `XPath('//book/title')` |

### JS 代码示例

```javascript
// 书源 JS 搜索规则
function getSortName() {
    return $.eval(html, '#sort .active').text();
}

// 获取作者名
function getAuthor() {
    var match = html.match(/作者[：:]\s*([^<]+)/);
    return match ? match[1].trim() : '';
}

// 处理数据
function getExploreBooks() {
    var list = JSON.parse(html);
    return list.map(function(item) {
        return {
            name: item.title,
            author: item.author,
            bookUrl: item.url
        };
    });
}
```

---

## 设计亮点总结

| 设计 | 原理 | 收益 |
|------|------|------|
| JSR-223 封装 | 标准脚本引擎接口 | 可替换引擎 |
| 协程集成 | Continuation 挂起 | 非阻塞执行 |
| 安全沙箱 | ClassShutter 白名单 | 防止恶意代码 |
| 动态代理 | InterfaceImplementor | JS 调用 Java |
| 递归限制 | recursiveCount | 防止死循环 |
| 单例模式 | object RhinoScriptEngine | 减少开销 |

---

## 学习任务

### 1. 理解 JSR-223 规范

**打开文件**：
- `modules/rhino/src/main/java/com/script/rhino/RhinoScriptEngine.kt` - 引擎实现
- `modules/rhino/src/main/java/com/script/ScriptEngine.kt` - 接口定义

**思考**：
- JSR-223 的核心接口有哪些？
- 为什么需要 `Bindings`？

### 2. 分析协程挂起

**打开文件**：
- `modules/rhino/src/main/java/com/script/rhino/RhinoContext.kt` - 上下文
- `modules/rhino/src/main/java/com/script/rhino/RhinoScriptEngine.kt` - `evalSuspend`

**思考**：
- `ContinuationPending` 什么时候抛出？
- 如何恢复一个暂停的脚本？

### 3. 理解安全沙箱

**打开文件**：
- `modules/rhino/src/main/java/com/script/rhino/RhinoClassShutter.kt` - 类过滤
- `modules/rhino/src/main/java/com/script/rhino/RhinoWrapFactory.kt` - 对象包装

**思考**：
- 黑名单和白名单的区别？
- 为什么 `java.lang.reflect` 要开放但 `java.lang.System` 要禁止？

### 4. 实践：编写书源 JS

**打开文件**：
- `app/src/main/java/io/legado/app/help/JsExtensions.kt` - 可用方法
- `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt` - 规则解析

**思考**：
- `ajax()` 和 `connect()` 的区别？
- 如何在 JS 中处理错误？
