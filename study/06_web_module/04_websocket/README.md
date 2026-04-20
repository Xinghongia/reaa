# WebSocket 实时通信

## 概述

WebSocket 提供**双向实时通信**能力，用于书籍搜索和书源调试等需要即时反馈的场景。与 HTTP 的"请求-响应"模式不同，WebSocket 建立连接后可以**双向主动推送**。

**学习目标**：理解 WebSocket 协议原理、心跳保活机制、回调模式设计。

---

## 为什么需要 WebSocket？

### HTTP 的局限性

```
HTTP 请求流程：
1. 客户端 → 请求 → 服务器
2. 客户端 ← 响应 ← 服务器
3. 连接关闭

问题：服务器不能主动推送数据给客户端
```

**场景**：实时搜索结果
- HTTP 方式：前端轮询每秒询问"有新结果吗？" → 服务器返回"有/没有"
- WebSocket 方式：连接保持，服务器找到结果就主动推送

### WebSocket 的优势

| 维度 | HTTP 轮询 | WebSocket |
|------|-----------|-----------|
| 连接 | 每次新建 | 保持连接 |
| 服务器推送 | ❌ 不支持 | ✅ 支持 |
| 实时性 | 秒级延迟 | 毫秒级 |
| 服务器负载 | 高（频繁建连） | 低（单连接） |

---

## 协议原理

### 连接建立流程

```
1. 客户端 → HTTP Upgrade 请求 → 服务器
   GET /searchBook HTTP/1.1
   Upgrade: websocket
   Connection: Upgrade

2. 客户端 ← 101 Switching Protocols ← 服务器
   HTTP/1.1 101 Switching Protocols
   Upgrade: websocket
   Connection: Upgrade

3. 双方 WebSocket 连接建立，可以双向通信
```

**关键头部**：
- `Upgrade: websocket` - 协议升级请求
- `Sec-WebSocket-Key` - 握手中的随机密钥
- `Sec-WebSocket-Accept` - 服务器确认密钥

### NanoWSD 封装

```kotlin
class BookSearchWebSocket(handshakeRequest: NanoHTTPD.IHTTPSession) :
    NanoWSD.WebSocket(handshakeRequest) {

    override fun onOpen() {
        // 连接建立
    }

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        // 收到消息
    }

    override fun onClose(closeCode: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedBy: Boolean) {
        // 连接关闭
    }
}
```

**设计原理 - 模板方法模式**：
- NanoWSD 定义了 WebSocket 生命周期的骨架
- 子类只需重写需要的方法（onOpen/onMessage/onClose）
- 不需要关心协议握手细节

---

## 心跳保活机制

### 为什么需要心跳？

```
场景：用户搜索一本书，耗时 30 秒

问题：
- 中间设备（路由器、代理）可能因超时断开空闲连接
- 服务器无法区分"连接断开"和"用户离开"
```

### 心跳实现

```kotlin
override fun onOpen() {
    // 启动心跳协程
    launch(IO) {
        while (isOpen) {
            ping("ping".toByteArray())  // 发送 Ping
            delay(30000)                 // 30秒间隔
        }
    }
}
```

**原理**：
- 客户端每 30 秒发送一个 Ping 帧
- 服务器回复 Pong 帧
- 保持连接活跃，防止超时断开

### 心跳参数选择

| 参数 | 值 | 考量 |
|------|-----|------|
| 间隔 | 30秒 | 太短浪费资源，太长可能超时 |
| 超时 | 默认 | NanoWSD 自动处理 |
| 重连 | 客户端 | 断线后需要重连逻辑 |

---

## 搜索回调模式

### 为什么用回调而非 Promise？

**Promise 模式（HTTP）**：
```typescript
const result = await API.search(key)
showResults(result)  // 等待全部结果
```

**回调模式（WebSocket）**：
```typescript
API.search(key,
    (partialResult) => appendResult(partialResult),  // 边搜边显示
    () => finishSearch()  // 搜索完成
)
```

**用户感受**：
- Promise：等待 5 秒 → 显示全部结果
- 回调：1 秒显示一批 → 2 秒再显示一批 → 5 秒完成

### 服务端推送

```kotlin
override fun onSearchSuccess(
    searchBooks: List<SearchBook>,
    processedSources: Int,
    totalSources: Int
) {
    // 每找到一个结果就立即推送
    send(GSON.toJson(searchBooks))
}
```

**好处**：
- 用户秒级看到结果
- 搜索过程透明
- 服务器内存压力小（不需要缓存全部结果）

---

## 调试日志系统

### 状态机设计

```
调试流程：
[开始] → [搜索中] → [获取目录] → [获取正文] → [完成/错误]

每个状态打印不同级别日志：
- 10: 搜索开始
- 20: 搜索完成
- 30: 获取目录
- 40: 获取正文
- -1: 调试结束
- 1000: 错误
```

### 过滤不必要日志

```kotlin
private val notPrintState = arrayOf(10, 20, 30, 40)

override fun printLog(state: Int, msg: String) {
    if (state in notPrintState) return  // 静默状态不打印
    send(msg)  // 只发送重要日志
}
```

**为什么过滤？**
- 减少网络传输
- 前端界面不会刷屏
- 用户只看到关键信息

### 实时日志流

```typescript
socket.onmessage = event => {
    onReceive(event.data)  // 每条日志立即显示
}
```

**用户感受**：调试界面实时滚动，像看直播一样。

---

## 生命周期管理

### WebSocket 状态

```
CLOSED (初始)
    ↓ open()
OPEN (可通信)
    ↓ receive message
OPEN (处理中)
    ↓ close() 或 error
CLOSED (最终)
```

### 协程作用域

```kotlin
class BookSearchWebSocket(...) :
    NanoWSD.WebSocket(handshakeRequest),
    CoroutineScope by MainScope()  // 绑定协程作用域
```

**为什么用 MainScope？**
- onOpen/onMessage 在主线程调用（NanoWSD）
- 但搜索逻辑需要 IO 线程
- CoroutineScope 让协程与 WebSocket 生命周期关联

### 连接关闭

```kotlin
override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
    close(normalClosure, SEARCH_FINISH, false)
}
```

**close 参数**：
- `normalClosure`：正常关闭码
- `SEARCH_FINISH`：关闭原因描述
- `initiatedBy`：是否由客户端发起

---

## 学习任务

### 1. 理解 WebSocket 协议

**打开文件**：
- `app/src/main/java/io/legado/app/web/socket/BookSearchWebSocket.kt`

**思考**：
- WebSocket 和 HTTP 的本质区别是什么？
- 为什么需要心跳？

### 2. 分析回调模式

**打开文件**：
- `modules/web/src/api/api.ts` 中的 `search` 函数

**思考**：
- 回调模式和 Promise 的区别？
- 什么场景适合用回调？

### 3. 追踪搜索流程

**思考**：
- 用户输入"斗罗大陆"后，发生了什么？
- 为什么能边搜边显示结果？

### 4. 实践：添加新 WebSocket

**思考**：
- 如何添加一个"实时获取书架更新"的 WebSocket？
- 需要修改哪些文件？

---

## 设计亮点总结

| 设计 | 原理 | 收益 |
|------|------|------|
| 双向通信 | WebSocket 协议 | 服务器主动推送 |
| 心跳保活 | Ping/Pong 帧 | 防止连接断开 |
| 回调模式 | 边搜边显示 | 用户体验更好 |
| 状态机 | 调试日志分级 | 信息过滤 |
| 模板方法 | NanoWSD 基类 | 简化实现 |
| 协程绑定 | CoroutineScope | 生命周期管理 |
