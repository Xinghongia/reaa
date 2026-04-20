# REST API

## 概述

REST API 是 Android 端的控制器层，处理来自 Vue3 前端的 HTTP 请求，返回统一格式的 `ReturnData`。采用**前后端分离**架构，通过 JSON 数据格式通信。

**学习目标**：理解 Controller 设计模式、统一返回格式、请求路由分发原理。

---

## 架构设计

### 为什么用 Controller 而直接写在 Server 里？

```kotlin
// HttpServer 直接处理（不推荐）
override fun serve(session: IHTTPSession): Response {
    when (uri) {
        "/getBookshelf" -> {
            // 200+ 行书籍查询逻辑
        }
    }
}

// Controller 分离（推荐）
when (uri) {
    "/getBookshelf" -> BookController.bookshelf
}
```

**设计原理 - 职责分离**：

- **HttpServer**：只负责 HTTP 协议处理（路由分发、参数解析）
- **Controller**：只负责业务逻辑（书籍查询、数据组装）
- **好处**：代码清晰、易于测试、便于扩展

### ReturnData 统一返回格式

```kotlin
class ReturnData {
    var isSuccess: Boolean = false
    var errorMsg: String = "未知错误,请联系开发者!"
    var data: Any? = null

    fun setErrorMsg(errorMsg: String): ReturnData { ... }
    fun setData(data: Any): ReturnData { ... }
}
```

**为什么设计成可变字段？**

- Kotlin 默认类是不可变的（val）
- 这里用 `var` + `private set` 允许内部修改，外部只读
- 链式调用：`returnData.setData(x).setErrorMsg(y)`（实际不用）

**JSON 格式**：

```json
// 成功
{ "isSuccess": true, "errorMsg": "", "data": [...] }

// 失败
{ "isSuccess": false, "errorMsg": "书籍不存在", "data": null }
```

---

## 路由分发机制

### HttpServer 如何分发请求？

```kotlin
class HttpServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return when (session.method) {
            Method.OPTIONS -> handleCors(session)
            Method.POST -> handlePost(session)
            Method.GET -> handleGet(session)
            else -> assetsWeb.getResponse(uri)
        }
    }
}
```

**设计原理 - 策略模式**：

- 不同 HTTP 方法（GET/POST）用不同策略处理
- 符合开闭原则：新增接口不需要修改分发逻辑

### GET 请求处理

```kotlin
private fun handleGet(session: IHTTPSession): Response {
    val uri = session.uri
    val parameters = session.parameters

    val returnData = when (uri) {
        "/getBookshelf" -> BookController.bookshelf
        "/getChapterList" -> BookController.getChapterList(parameters)
        "/getBookSources" -> BookSourceController.sources
        "/cover" -> BookController.getCover(parameters)
        "/image" -> BookController.getImg(parameters)
        else -> null  // 静态资源
    }

    return newFixedLengthResponse(
        Response.Status.lookup(returnData.isSuccess.toInt()),
        "application/json",
        GSON.toJson(returnData)
    )
}
```

**为什么用 when 表达式而非 if-else？**

- when 更简洁，适合多分支
- when 可以匹配任意类型（不只是 enum）
- 编译器检查穷尽性

---

## Controller 设计模式

### BookController 结构

```kotlin
object BookController {

    // 属性型接口（类似 REST 端点）
    val bookshelf: ReturnData
        get() {
            val books = appDb.bookDao.all
            return if (books.isEmpty()) {
                returnData.setErrorMsg("还没有添加小说")
            } else {
                returnData.setData(sortBooks(books))
            }
        }

    // 方法型接口
    fun getChapterList(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
            ?: return returnData.setErrorMsg("bookUrl为空")
        // ...
    }
}
```

**设计原理 - Object Singleton**：

- Kotlin `object` 声明单例对象
- 全局只有一个实例，无需手动创建
- 适合作为 Controller 这种无状态类

**属性 vs 方法型接口**：

| 类型 | 用法 | 适用场景 |
|------|------|---------|
| 属性 | `BookController.bookshelf` | 只读的简单查询 |
| 方法 | `BookController.getChapterList(params)` | 需要参数的操作 |

---

## 数据流转分析

### 获取书架的完整流程

```
1. 前端: API.getBookShelf()
   ↓ axios.get('getBookshelf')
2. 网络: HTTP GET /getBookshelf
   ↓
3. HttpServer.serve()
   ↓ session.method = GET
4. handleGet(session)
   ↓ uri = "/getBookshelf"
5. BookController.bookshelf
   ↓ appDb.bookDao.all
6. Room 数据库查询
   ↓
7. ReturnData { isSuccess: true, data: [Book, ...] }
   ↓ GSON.toJson()
8. HTTP 响应体 JSON
   ↓
9. 前端: response.data.isSuccess === true
10. Store: this.shelf = data
   ↓
11. UI 自动更新（Pinia 响应式）
```

### 为什么用 GSON 而非 Kotlinx.serialization？

**GSON 优势**：

- 成熟稳定，生态完善
- 对复杂泛型支持好
- 与 Java 生态兼容

**Kotlinx.serialization 优势**：

- Kotlin 原生，类型安全
- 编译期检查

**选择依据**：Android 端 GSON 更常见，与 Java 代码交互方便。

---

## 书源 CRUD 设计

### 查询所有书源

```kotlin
val sources: ReturnData
    get() {
        val bookSources = appDb.bookSourceDao.all
        return if (bookSources.isEmpty()) {
            returnData.setErrorMsg("设备源列表为空")
        } else {
            returnData.setData(bookSources)
        }
    }
```

### 保存单个书源

```kotlin
fun saveSource(postData: String?): ReturnData {
    postData ?: return returnData.setErrorMsg("数据为空")

    val bookSource = GSON.fromJsonObject<BookSource>(postData).getOrNull()
        ?: return returnData.setErrorMsg("转换书籍失败")

    return when {
        bookSource.bookSourceName.isBlank() ->
            returnData.setErrorMsg("源名称不能为空")
        bookSource.bookSourceUrl.isBlank() ->
            returnData.setErrorMsg("URL 不能为空")
        else -> {
            appDb.bookSourceDao.insert(bookSource)
            returnData.setData("")
        }
    }
}
```

**设计原理 - 防御性编程**：

- 检查空值 `?: return`
- 检查业务规则（名称不能为空）
- 明确的错误信息

### 批量保存书源

```kotlin
fun saveSources(postData: String?): ReturnData {
    postData ?: return ReturnData().setErrorMsg("数据为空")

    val bookSources = GSON.fromJsonArray<BookSource>(postData).getOrNull()
        ?: return ReturnData().setErrorMsg("转换源失败")

    bookSources.forEach { bookSource ->
        if (bookSource.bookSourceName.isNotBlank() &&
            bookSource.bookSourceUrl.isNotBlank()) {
            appDb.bookSourceDao.insert(bookSource)
        }
    }
    return ReturnData().setData(bookSources)
}
```

**为什么要遍历校验而非一次性全部替换？**

- 部分数据有效时尽量保存有用的部分
- 符合"宽进严出"原则

---

## 学习任务

### 1. 理解路由分发

**打开文件**：

- `app/src/main/java/io/legado/app/web/HttpServer.kt` - HTTP 服务器

**思考**：

- HttpServer 如何区分 GET 和 POST 请求？
- 为什么返回 JSON 时要用 `application/json`？

### 2. 分析 Controller 设计

**打开文件**：

- `app/src/main/java/io/legado/app/api/controller/BookController.kt` - 书籍控制器
- `app/src/main/java/io/legado/app/api/ReturnData.kt` - 返回格式

**思考**：

- BookController 为什么用 `object` 声明？
- ReturnData 的 `private set` 有什么作用？

### 3. 追踪数据流

**思考**：

- 前端 `API.getBookShelf()` 的响应数据流向哪里？
- 如果数据库为空，前端收到什么？

### 4. 实践：添加新 API

**思考**：

- 如何添加一个"获取书籍数量"的 API？
- 需要修改哪些文件？

---

## 设计亮点总结

| 设计 | 原理 | 收益 |
|------|------|------|
| Controller 分离 | 职责分离 | 代码清晰、易测试 |
| Object Singleton | Kotlin 单例 | 全局唯一、无需创建 |
| ReturnData 统一 | 标准化响应 | 前端处理一致 |
| when 路由分发 | 策略模式 | 易于扩展 |
| 防御性编程 | 空值检查 | 健壮性 |
| GSON 序列化 | JSON 互通 | 前后端解耦 |
