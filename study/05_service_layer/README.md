# 服务层

## 概述

后台服务层处理下载、音频播放、Web服务等**长时间运行的任务**。这些任务需要在后台持续运行，不依赖 UI 界面。

## 架构设计

### 服务类型总览

| 服务 | 用途 | 线程模型 |
|------|------|---------|
| `DownloadService` | 文件下载管理 | Android DownloadManager |
| `CacheBookService` | 书籍缓存/离线阅读 | 协程线程池 |
| `AudioPlayService` | 音频播放（音乐、电台） | ExoPlayer + MediaSession |
| `BaseReadAloudService` | 朗读服务基类 | TTS/HttpTTS |
| `TTSReadAloudService` | 本地 TTS 朗读 | Android TextToSpeech |
| `HttpReadAloudService` | 在线朗读 | ExoPlayer 流式播放 |
| `CheckSourceService` | 书源校验 | 协程并发 |
| `ExportBookService` | 书籍导出 | 协程 + epub 库 |
| `WebService` | HTTP/WebSocket 服务器 | NanoHTTPD |
| `WebTileService` | 快捷开关服务 | Android TileService |

### 服务层次关系

```
BaseService (基类)
├── DownloadService
├── CacheBookService
├── AudioPlayService
├── BaseReadAloudService (抽象基类)
│   ├── TTSReadAloudService
│   └── HttpReadAloudService
├── CheckSourceService
├── ExportBookService
└── WebService

WebTileService (独立)
```

## 核心设计模式

### 1. 前台服务模式

所有服务都继承 `BaseService`（基于 `LifecycleService`），自动启动前台服务并显示通知：

```kotlin
// BaseService.kt 核心逻辑
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!isForeground) {
        startForegroundNotification()  // 启动前台通知
        isForeground = true
    }
    return super.onStartCommand(intent, flags, startId)
}
```

**为什么使用前台服务？**
- 防止系统杀死后台服务
- 用户可以看到服务运行状态
- Android 8.0+ 后台服务限制

### 2. 生命周期管理

```kotlin
// 使用 lifecycleScope 管理协程
lifecycleScope.launch {
    while (isActive) {
        delay(1000)
        queryState()
    }
}
```

### 3. WakeLock + WiFi Lock

对于需要持续运行的服务（如音频播放、Web服务）：

```kotlin
private val wakeLock by lazy {
    powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:ServiceName")
}

private val wifiLock by lazy {
    wifiManager.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "legado:ServiceName")
}
```

## Intent Action 统一管理

所有服务的 Action 集中在 `IntentAction` 常量中：

```kotlin
// 常用 Action
IntentAction.start      // 启动
IntentAction.stop       // 停止
IntentAction.pause      // 暂停
IntentAction.resume      // 恢复
IntentAction.play        // 播放
IntentAction.playNew     // 播放新内容
IntentAction.prev        // 上一首/上一章
IntentAction.next        // 下一首/下一章
```

## 数据流概览

### 缓存下载流程

```
用户点击缓存 → CacheBookService.start()
    ↓
CacheBookModel 管理下载任务
    ↓
协程池并发下载（最大线程数可配置）
    ↓
下载完成 → 存储到数据库
    ↓
通知栏更新进度
```

### 朗读流程

```
用户点击朗读 → ReadAloud.play()
    ↓
判断使用 TTS 还是 HttpTTS
    ↓
启动对应 Service（TTSReadAloudService / HttpReadAloudService）
    ↓
获取阅读内容 → 分段朗读
    ↓
MediaSession 控制 → 通知栏媒体控制
```

## 子模块文档

- [DownloadService](01_download_service/README.md) - 文件下载服务
- [CacheBookService](02_cache_book_service/README.md) - 书籍缓存服务
- [AudioPlayService](03_audio_play_service/README.md) - 音频播放服务
- [ReadAloud](04_read_aloud_service/README.md) - 朗读服务（TTS + HttpTTS）
- [WebService](05_web_service/README.md) - HTTP/WebSocket 服务
- [CheckSourceService](06_check_source_service/README.md) - 书源校验服务
- [ExportBookService](07_export_book_service/README.md) - 书籍导出服务

## 源码阅读顺序

建议按以下顺序阅读：

1. **BaseService** - 理解服务基类设计
2. **DownloadService** - 简单的下载管理示例
3. **CacheBookService** - 协程池 + 并发下载
4. **AudioPlayService** - MediaSession + ExoPlayer
5. **BaseReadAloudService** - 抽象基类 + 音频焦点管理
6. **WebService** - NanoHTTPD 服务器
7. **CheckSourceService** - 书源校验逻辑

## 关键文件索引

| 文件 | 说明 |
|------|------|
| `base/BaseService.kt` | 服务基类 |
| `service/DownloadService.kt` | 系统下载服务包装 |
| `service/CacheBookService.kt` | 书籍缓存服务 |
| `service/AudioPlayService.kt` | 音频播放服务 |
| `service/BaseReadAloudService.kt` | 朗读服务基类 |
| `service/TTSReadAloudService.kt` | 本地 TTS 朗读 |
| `service/HttpReadAloudService.kt` | 在线 HTTP 朗读 |
| `service/WebService.kt` | HTTP/WebSocket 服务 |
| `model/CacheBook.kt` | 缓存管理核心类 |
| `model/ReadAloud.kt` | 朗读控制入口 |
| `web/HttpServer.kt` | NanoHTTPD 服务器 |
| `web/WebSocketServer.kt` | WebSocket 服务器 |
