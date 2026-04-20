# API 层

## 概述

API 层是前端与后端通信的桥梁，负责封装 HTTP 请求和 WebSocket 连接。连接两端的 Vue3 界面和 Android NanoHTTPD 服务器。

**学习目标**：理解前后端通信机制、API 封装设计、跨域处理原理。

---

## 通信架构

### 为什么需要 API 层？

```
┌──────────────┐          API 层          ┌──────────────┐
│   Vue3 前端   │ ◀──────────────────────▶ │ Android App  │
│              │    Axios HTTP 请求        │              │
│  BookShelf   │ ◀──────────────────────▶ │ HttpServer   │
│   组件        │    WebSocket 推送        │  Controller  │
└──────────────┘                           └──────────────┘
```

**设计原理**：
- **解耦**：前端不直接接触网络细节
- **统一**：所有请求经过同一入口
- **可替换**：后端实现变了，前端调用方式不变

---

## Axios 配置原理

### axios.ts 核心配置

```typescript
const ajax = axios.create({
  timeout: 30000,           // 30秒超时
  withCredentials: true,    // 携带跨域凭证
})
```

### 为什么需要 withCredentials？

**场景**：前端 `http://localhost:5173` → 后端 `http://192.168.1.100:1122`

**原理**：
- 浏览器默认不允许跨域请求携带 Cookie
- `withCredentials: true` 允许携带认证凭证
- 服务器端也需要设置对应的 CORS 头

### URL 自动处理

```typescript
export const parseLeagdoHttpUrlWithDefault = (http_url?: string) => {
  if (!http_url) {
    http_url = localStorage.getItem('legado_remote_url') || 'http://127.0.0.1:1122'
  }
  if (!/^https?:\/\//.test(http_url)) {
    http_url = 'http://' + http_url
  }
  if (/\/+$/.test(http_url)) {
    http_url = http_url.replace(/\/+$/, '')
  }
  return http_url
}
```

**为什么这样做？**
- **补全协议**：用户可能输入 `192.168.1.100`，自动补 `http://`
- **去除尾斜杠**：统一格式，避免 `/bookshelf` 和 `/bookshelf/` 混淆
- **本地存储**：记住用户上次连接的地址

---

## API 封装设计

### 为什么要封装 API？

```typescript
// 直接使用 axios（不推荐）
axios.get('http://127.0.0.1:1122/getBookshelf')

// 使用封装后（推荐）
API.getBookShelf()
```

**好处**：
- 统一 baseURL 管理
- 统一错误处理
- 统一返回格式
- 易于维护和扩展

### 动态入口点设计

```typescript
export let legado_http_entry_point = ''
export let legado_webSocket_entry_point = ''

export const setApiEntryPoint = (
  http_entry_point: string,
  webSocket_entry_point: string,
) => {
  legado_http_entry_point = new URL(http_entry_point).toString()
  legado_webSocket_entry_point = new URL(webSocket_entry_point).toString()
  ajax.defaults.baseURL = legado_http_entry_point
}
```

**设计原理 - 配置与实现分离**：
- 服务器地址可以动态配置
- 不硬编码 IP 和端口
- 支持连接不同设备

**使用场景**：用户在设置页面输入 `192.168.1.100:1122`，应用保存并使用这个地址连接。

---

## HTTP API 设计

### 统一返回格式

```typescript
// modules/web/src/api/api.ts
export type LeagdoApiResponse<T> = {
  isSuccess: boolean    // 请求是否成功
  errorMsg: string      // 错误信息
  data: T               // 数据
}
```

**为什么这样设计？**
- **isSuccess**：快速判断成功/失败
- **errorMsg**：失败时提供原因
- **data**：成功时返回数据

**对比其他设计**：
- HTTP 状态码（200/404/500）只能表示请求级别成功
- 这个设计可以区分"请求成功但业务失败"（如"书籍不存在"）

### API 分类

| 类别 | 方法 | 示例 |
|------|------|------|
| 书架 | `getBookShelf()` | 获取书架书籍 |
| 目录 | `getChapterList(url)` | 获取目录 |
| 内容 | `getBookContent(url, index)` | 获取章节内容 |
| 书源 | `getSources()` / `saveSources()` | 书源 CRUD |

---

## WebSocket 封装原理

### 为什么用 WebSocket 而非 HTTP？

| 维度 | HTTP | WebSocket |
|------|------|-----------|
| 方向 | 请求-响应 | 双向通信 |
| 实时性 | 轮询 | 实时推送 |
| 连接 | 每次新建 | 保持连接 |
| 适用 | CRUD 操作 | 实时反馈 |

**场景**：
- **HTTP**：获取书架、修改书源
- **WebSocket**：实时搜索结果、调试日志

### 搜索回调封装

```typescript
const search = (
  searchKey: string,
  onReceive: (data: SeachBook[]) => void,
  onFinish: () => void,
) => {
  const socket = new WebSocket(
    new URL('searchBook', legado_webSocket_entry_point),
  )

  socket.onopen = () => {
    socket.send(`{"key":"${searchKey}"}`)
  }

  socket.onmessage = event => {
    onReceive(JSON.parse(event.data))
  }

  socket.onclose = () => {
    onFinish()
  }
}
```

**设计原理 - 回调模式**：
- `onReceive`：每收到一条搜索结果就调用
- `onFinish`：搜索完成后调用
- 好处：不用等待全部结果，边搜边显示

---

## sendBeacon 可靠传输

### 为什么要用 sendBeacon？

```typescript
const saveBookProgressWithBeacon = (bookProgress: BookProgress) => {
  navigator.sendBeacon(
    new URL('saveBookProgress', legado_http_entry_point),
    JSON.stringify(bookProgress),
  )
}
```

**场景**：用户关闭浏览器标签页时，需要保存阅读进度。

**问题**：
- `fetch()` 可能在页面卸载时被取消
- 用户可能看不到保存成功的反馈

**sendBeacon 优势**：
- 页面关闭后仍能发送请求
- 后台可靠传输，不阻塞页面关闭
- 适用于埋点、进度保存等场景

**限制**：
- 只支持 POST
- 不能携带复杂的认证头

---

## 学习任务

### 1. 理解跨域通信

**打开文件**：
- `modules/web/src/api/axios.ts` - Axios 配置

**思考**：
- `withCredentials: true` 的作用是什么？
- 为什么需要处理 URL 的协议和尾斜杠？

### 2. 分析 API 封装

**打开文件**：
- `modules/web/src/api/api.ts` - API 封装

**思考**：
- `getBookShelf()` 返回什么类型？
- 为什么 API 返回 `LeagdoApiResponse<T>` 而不是直接返回 `T`？

### 3. 对比 HTTP 和 WebSocket

**思考**：
- 什么场景用 HTTP？什么场景用 WebSocket？
- WebSocket 的 `onmessage` 和 HTTP 的响应有什么区别？

---

## 设计亮点总结

| 设计 | 原理 | 收益 |
|------|------|------|
| Axios 封装 | 统一请求入口 | 可维护、可扩展 |
| withCredentials | 跨域凭证携带 | 支持跨域认证 |
| 动态 baseURL | 配置与实现分离 | 支持连接不同服务器 |
| LeagdoApiResponse | 业务级成功/失败 | 细粒度错误处理 |
| WebSocket 封装 | 回调模式 | 边搜边显示 |
| sendBeacon | 后台可靠传输 | 页面关闭时保存 |
