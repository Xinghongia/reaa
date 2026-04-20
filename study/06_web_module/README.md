# Web 模块

## 概述

Web 模块是 Legado 的 Vue3 前端界面，允许用户在浏览器中管理书架、书源、RSS 订阅等。通过内嵌的 HTTP 服务器与 Android 应用通信。

**源码位置**：
- 前端：`modules/web/`
- 后端 API：`app/src/main/java/io/legado/app/api/controller/`
- HTTP 服务器：`app/src/main/java/io/legado/app/web/`

## 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        浏览器                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Vue3 应用 (modules/web/)                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │   │
│  │  │  BookShelf  │  │ SourceEditor │  │  路由     │ │   │
│  │  │   书架页面   │  │   书源编辑器  │  │  VueRouter │ │   │
│  │  └─────────────┘  └─────────────┘  └───────────┘ │   │
│  │  ┌─────────────────────────────────────────────┐  │   │
│  │  │              Pinia Store                    │  │   │
│  │  │   bookStore  │  sourceStore  │  connectionStore  │  │   │
│  │  └─────────────────────────────────────────────┘  │   │
│  │  ┌─────────────────────────────────────────────┐  │   │
│  │  │              API Layer (axios)              │  │   │
│  │  └─────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP / WebSocket
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Legado Android App                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              WebService (NanoHTTPD)                  │   │
│  │  ┌─────────────────┐    ┌──────────────────────┐  │   │
│  │  │   HttpServer    │    │  WebSocketServer     │  │   │
│  │  │   端口: 1122    │    │   端口: 1123         │  │   │
│  │  └─────────────────┘    └──────────────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              REST API Controller                     │   │
│  │  BookController  │  BookSourceController           │   │
│  │  RssSourceController  │  ReplaceRuleController     │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Room Database                          │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 前端技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 | 渐进式前端框架 |
| Vue Router | SPA 路由管理 |
| Pinia | 状态管理 |
| Axios | HTTP 客户端 |
| Element Plus | UI 组件库 |
| Vite | 构建工具 |

### 后端技术栈

| 技术 | 用途 |
|------|------|
| NanoHTTPD | 轻量级 HTTP 服务器 |
| NanoWSD | WebSocket 扩展 |
| Kotlin 协程 | 异步处理 |
| Room | 数据库访问 |

## 目录结构

### 前端结构 (`modules/web/`)

```
modules/web/
├── src/
│   ├── api/               # API 调用层
│   │   ├── api.ts         # API 封装
│   │   └── axios.ts        # Axios 配置
│   ├── components/        # Vue 组件
│   │   ├── BookItems.vue       # 书籍列表项
│   │   ├── SourceDebug.vue     # 书源调试
│   │   ├── SourceItem.vue      # 书源项
│   │   └── SourceTabForm.vue   # 书源表单
│   ├── config/            # 配置文件
│   │   ├── bookSourceEditConfig.ts
│   │   └── rssSourceEditConfig.ts
│   ├── pages/             # 页面入口
│   │   ├── bookshelf/
│   │   └── source/
│   ├── router/            # 路由配置
│   │   ├── index.ts
│   │   ├── bookRouter.ts
│   │   └── sourceRouter.ts
│   ├── store/             # Pinia 状态管理
│   │   ├── bookStore.ts       # 书籍状态
│   │   ├── sourceStore.ts     # 书源状态
│   │   └── connectionStore.ts # 连接状态
│   ├── views/             # 页面视图
│   │   ├── BookShelf.vue      # 书架页面
│   │   ├── BookChapter.vue    # 章节阅读
│   │   └── SourceEditor.vue   # 书源编辑器
│   ├── main.ts           # 入口文件
│   └── App.vue           # 根组件
├── public/               # 静态资源
└── package.json
```

### 后端结构 (`app/.../api/`)

```
app/src/main/java/io/legado/app/api/
├── ReturnData.kt         # 统一返回格式
├── controller/
│   ├── BookController.kt         # 书籍 API
│   ├── BookSourceController.kt   # 书源 API
│   ├── RssSourceController.kt    # RSS 源 API
│   └── ReplaceRuleController.kt  # 净化规则 API
└── ...
```

## 子模块文档

- [Vue3 前端](01_vue3_frontend/README.md) - 界面、路由、状态管理
- [API 层](02_api_layer/README.md) - Axios 配置、API 封装
- [REST API](03_rest_api/README.md) - Controller 设计、数据格式
- [WebSocket](04_websocket/README.md) - 实时搜索、调试功能

## 源码阅读顺序

1. **ReturnData** - 理解统一返回格式
2. **HttpServer** - 理解服务器路由分发
3. **BookController** - 理解书籍 API
4. **Vue3 前端 API 调用** - 理解前端如何调用
5. **WebSocket** - 理解实时通信

## 关键文件索引

### 前端

| 文件 | 说明 |
|------|------|
| `modules/web/src/api/api.ts` | API 封装 |
| `modules/web/src/store/bookStore.ts` | 书架状态管理 |
| `modules/web/src/store/sourceStore.ts` | 书源状态管理 |
| `modules/web/src/views/BookShelf.vue` | 书架页面 |
| `modules/web/src/views/SourceEditor.vue` | 书源编辑器 |
| `modules/web/src/router/index.ts` | 路由配置 |

### 后端

| 文件 | 说明 |
|------|------|
| `api/ReturnData.kt` | 统一返回格式 |
| `api/controller/BookController.kt` | 书籍 API |
| `api/controller/BookSourceController.kt` | 书源 API |
| `web/HttpServer.kt` | HTTP 服务器 |
| `web/WebSocketServer.kt` | WebSocket 服务器 |
| `web/socket/BookSearchWebSocket.kt` | 搜索 WebSocket |
| `web/socket/BookSourceDebugWebSocket.kt` | 调试 WebSocket |
