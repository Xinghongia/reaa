# WebService - HTTP/WebSocket 服务

## 概述

`WebService` 是内嵌 HTTP 服务器，允许用户在浏览器中管理书架、书源等。同时提供 WebSocket 支持，实现实时调试功能。

**源码位置**：
- `service/WebService.kt` - 服务入口
- `web/HttpServer.kt` - HTTP 服务器
- `web/WebSocketServer.kt` - WebSocket 服务器

## 核心技术栈

| 组件 | 作用 |
|------|------|
| NanoHTTPD | 轻量级 HTTP 服务器 |
| NanoWSD | WebSocket 服务器 |
| Kotlin 协程 | 异步处理 |
| WakeLock/WiFiLock | 保持后台运行 |

## WebService 服务

### 启动管理

```kotlin
class WebService : BaseService() {

    companion object {
        fun start(context: Context) = context.startService<WebService>()
        fun startForeground(context: Context) = context.startForegroundServiceCompat(intent)
        fun stop(context: Context) = context.stopService<WebService>()
        fun serve() = appCtx.startService<WebService> { action = "serve" }
    }

    private var httpServer: HttpServer? = null
    private var webSocketServer: WebSocketServer? = null
}
```

### 端口配置

```kotlin
private fun getPort(): Int {
    var port = getPrefInt(PreferKey.webPort, 1122)
    if (port > 65530 || port < 1024) {
        port = 1122
    }
    return port
}
```

### 网络状态监听

```kotlin
private val networkChangedListener by lazy {
    NetworkChangedListener(this)
}

networkChangedListener.onNetworkChanged = {
    val addressList = NetworkUtils.getLocalIPAddress()
    notificationList.clear()
    if (addressList.any()) {
        notificationList.addAll(addressList.map { address ->
            getString(R.string.http_ip, address.hostAddress, getPort())
        })
        hostAddress = notificationList.first()
    }
}
```

### 启动服务器

```kotlin
private fun upWebServer() {
    httpServer?.stop()
    webSocketServer?.stop()

    val addressList = NetworkUtils.getLocalIPAddress()
    if (addressList.any()) {
        val port = getPort()
        httpServer = HttpServer(port)
        webSocketServer = WebSocketServer(port + 1)

        httpServer?.start()
        webSocketServer?.start(1000 * 30)  // 30秒超时

        hostAddress = notificationList.first()
        startForegroundNotification()
    }
}
```

## HttpServer - HTTP 服务器

### 请求路由

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

### GET 请求处理

```kotlin
when (uri) {
    "/getBookSource" -> BookSourceController.getSource(parameters)
    "/getBookSources" -> BookSourceController.sources
    "/getBookshelf" -> BookController.bookshelf
    "/getChapterList" -> BookController.getChapterList(parameters)
    "/getBookContent" -> BookController.getBookContent(parameters)
    "/cover" -> BookController.getCover(parameters)
    "/image" -> BookController.getImg(parameters)
    // ...
}
```

### POST 请求处理

```kotlin
when (uri) {
    "/saveBookSource" -> BookSourceController.saveSource(postData)
    "/saveBook" -> BookController.saveBook(postData)
    "/deleteBook" -> BookController.deleteBook(postData)
    "/saveBookProgress" -> BookController.saveBookProgress(postData)
    // ...
}
```

### 静态资源

```kotlin
private val assetsWeb = AssetsWeb("web")

if (returnData == null) {
    if (uri.endsWith("/")) uri += "index.html"
    return assetsWeb.getResponse(uri)  // 静态资源
}
```

### 返回数据

```kotlin
val response = if (returnData.data is Bitmap) {
    // 图片处理
    val outputStream = ByteArrayOutputStream()
    (returnData.data as Bitmap).compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    newFixedLengthResponse(..., "image/png", inputStream, byteArray.size.toLong())
} else {
    // JSON 处理，大数据使用流式响应
    if (data is List<*> && data.size > 3000) {
        val pipe = Pipe(16 * 1024)
        Coroutine.async {
            pipe.sink.buffer().outputStream().bufferedWriter(Charsets.UTF_8).use {
                GSON.toJson(returnData, it)
            }
        }
        newChunkedResponse(..., "application/json", pipe.source.buffer().inputStream())
    } else {
        newFixedLengthResponse(GSON.toJson(returnData))
    }
}
```

## WebSocketServer - WebSocket 服务器

### 路由分发

```kotlin
class WebSocketServer(port: Int) : NanoWSD(port) {

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        WebService.serve()
        return when (handshake.uri) {
            "/bookSourceDebug" -> BookSourceDebugWebSocket(handshake)
            "/rssSourceDebug" -> RssSourceDebugWebSocket(handshake)
            "/searchBook" -> BookSearchWebSocket(handshake)
            else -> null
        }
    }
}
```

### 实时调试 WebSocket

```kotlin
class BookSourceDebugWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {

    override fun onMessage(message: Message?) {
        val data = GSON.fromJson(message!!.textData, BookSourceDebug::class.java)
        // 处理调试请求
    }

    override fun onClose(code: CloseCode?, reason: String?, initiatedByUs: Boolean) {
        // 清理资源
    }
}
```

## API 设计

### 书籍相关

| 接口 | 方法 | 说明 |
|------|------|------|
| `/getBookshelf` | GET | 获取书架书籍列表 |
| `/getChapterList` | GET | 获取书籍目录 |
| `/getBookContent` | GET | 获取章节内容 |
| `/saveBookProgress` | POST | 保存阅读进度 |
| `/cover` | GET | 获取书籍封面 |

### 书源相关

| 接口 | 方法 | 说明 |
|------|------|------|
| `/getBookSources` | GET | 获取所有书源 |
| `/getBookSource` | GET | 获取单个书源 |
| `/saveBookSource` | POST | 保存书源 |
| `/bookSourceDebug` | WS | 书源调试 |

### 实时 WebSocket

| 接口 | 说明 |
|------|------|
| `/bookSourceDebug` | 书源调试（实时交互） |
| `/rssSourceDebug` | RSS 源调试 |
| `/searchBook` | 书籍搜索（实时进度） |

## 使用场景

```
┌─────────────────────────────────────────────────────┐
│                     浏览器                           │
│  ┌─────────────────────────────────────────────┐    │
│  │  Vue3 Web 界面 (modules/web/)               │    │
│  │  - 书架管理                                  │    │
│  │  - 书源管理                                  │    │
│  │  - RSS 订阅                                  │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
                    │
                    │ HTTP/WebSocket
                    ↓
┌─────────────────────────────────────────────────────┐
│           Legado App (WebService)                   │
│  ┌──────────────┐    ┌──────────────────┐          │
│  │  HttpServer  │    │ WebSocketServer │          │
│  │  端口: 1122  │    │  端口: 1123      │          │
│  └──────────────┘    └──────────────────┘          │
│         │                      │                   │
│         └──────────┬─────────────┘                  │
│                    ↓                                │
│         ┌─────────────────┐                        │
│         │  REST API        │                        │
│         │  Controller      │                        │
│         └─────────────────┘                        │
└─────────────────────────────────────────────────────┘
                    │
                    ↓
           ┌─────────────────┐
           │  Room Database  │
           └─────────────────┘
```

## 学习任务

1. **打开源码文件**：
   - `service/WebService.kt`
   - `web/HttpServer.kt`
   - `web/WebSocketServer.kt`

2. **理解服务器启动流程**：
   - 端口分配
   - 网络状态监听
   - 前台通知

3. **分析 API 设计**：
   - REST API vs WebSocket
   - 何时使用流式响应

## 设计亮点

1. **NanoHTTPD 轻量级**：无需 Tomcat/Jetty，应用内嵌入
2. **端口 +1 策略**：HTTP 1122，WebSocket 1123
3. **静态资源打包**：Vue3 构建产物放入 assets/web
4. **网络切换监听**：IP 变化时更新通知栏
5. **流式响应优化**：大数据量使用 Pipe 流式传输，避免内存峰值
