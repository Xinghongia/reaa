# Legado MD3 项目学习指南

## 目录结构

```
study/
├── README.md                      # 学习指南
├── 01_project_overview/           # 项目概览 (已完成)
│   └── README.md
├── 02_data_layer/                 # 数据层 (已完成)
│   ├── README.md                  # 数据层总览
│   ├── 01_entities/              # 实体类 (25个)
│   │   └── README.md
│   ├── 02_dao/                   # DAO 数据访问 (23个)
│   │   └── README.md
│   ├── 03_repository/            # Repository 仓库模式
│   │   └── README.md
│   └── 04_database/              # 数据库配置与迁移
│       └── README.md
├── 03_model_layer/               # 业务逻辑层 (已完成)
│   ├── README.md                  # 业务逻辑层总览
│   ├── 01_analyze_rule/          # 书源规则解析
│   │   └── README.md
│   ├── 02_read_book/            # 阅读器核心
│   │   └── README.md
│   ├── 03_local_book/           # 本地书籍解析
│   │   └── README.md
│   └── 04_rss/                  # RSS订阅
│       └── README.md
├── 04_ui_layer/                  # UI层 (已完成)
│   ├── README.md                  # UI层总览
│   ├── 01_architecture/         # UI架构
│   │   └── README.md
│   ├── 02_navigation/          # 导航系统
│   │   └── README.md
│   └── 03_theme/                # 主题系统
│       └── README.md
├── 05_service_layer/             # 服务层 (已完成)
│   ├── README.md                  # 服务层总览
│   ├── 01_download_service/     # 下载服务
│   │   └── README.md
│   ├── 02_cache_book_service/   # 缓存服务
│   │   └── README.md
│   ├── 03_audio_play_service/   # 音频播放
│   │   └── README.md
│   ├── 04_read_aloud_service/   # 朗读服务
│   │   └── README.md
│   ├── 05_web_service/          # Web服务
│   │   └── README.md
│   ├── 06_check_source_service/ # 书源校验
│   │   └── README.md
│   └── 07_export_book_service/  # 书籍导出
│       └── README.md
├── 06_web_module/                # Web模块 (已完成)
│   ├── README.md                  # Web模块总览
│   ├── 01_vue3_frontend/       # Vue3前端
│   │   └── README.md
│   ├── 02_api_layer/           # API层
│   │   └── README.md
│   ├── 03_rest_api/            # REST API
│   │   └── README.md
│   └── 04_websocket/            # WebSocket
│       └── README.md
├── 07_book_module/               # 书籍解析模块 (已完成)
│   ├── README.md                  # 书籍模块总览
│   ├── 01_parser/              # 解析器
│   │   └── README.md
│   └── 02_toc_recognition/     # 目录识别
│       └── README.md
├── 08_rhino_module/             # JS引擎模块 (已完成)
│   ├── README.md                  # JS引擎总览
│   └── 01_js_extensions/       # JS扩展用法
│       └── README.md
└── 09_tools/                   # 工具类
```

## 学习进度

| 模块 | 状态 | 说明 |
|------|------|------|
| 项目概览 | ✅ 完成 | 技术栈、架构、目录结构 |
| 数据层 | ✅ 完成 | 实体、DAO、Repository、数据库 |
| 业务逻辑层 | ✅ 完成 | 书源解析、阅读器、RSS |
| UI层 | ✅ 完成 | Compose界面、导航、主题 |
| 服务层 | ✅ 完成 | 下载、音频、Web服务 |
| Web模块 | ✅ 完成 | Vue3界面、HTTP服务 |
| 书籍模块 | ✅ 完成 | TXT/EPUB/PDF解析 |
| JS引擎 | ✅ 完成 | Rhino引擎、JS扩展 |
| 工具类 | ✅ 完成 | 扩展函数、辅助类 |

## 项目概览学习要点

### 1. 技术栈
- Kotlin + Jetpack Compose
- Koin 依赖注入
- Room 数据库
- OkHttp + Cronet 网络

### 2. 架构
- MVVM + Clean Architecture
- Repository 模式
- 协程 + Flow

### 3. 模块划分
- app 主应用
- modules/book EPUB/UMD 解析库
- modules/rhino JS 引擎
- modules/web Vue3 Web 界面

## 数据层学习要点

### 1. 实体类 (Entities)
- Room 数据库表结构定义
- 索引设计原理
- URL 作为主键的考量
- 嵌套对象设计

### 2. DAO (Data Access Object)
- KSP 编译时生成实现
- Flow 响应式原理
- OnConflictStrategy 选择

### 3. Repository (仓库模式)
- 为什么需要 Repository
- 封装数据访问逻辑
- Flow → StateFlow 转换

### 4. 数据库配置
- Room 数据库初始化
- TypeConverter 原理
- Migration 迁移策略

## 业务逻辑层学习要点

### 1. 规则解析 (AnalyzeRule)
- 多解析器策略 (XPath/JSoup/Regex/JSON)
- 规则数据结构
- JS 增强解析原理

### 2. 阅读器核心 (ReadBook)
- 章节内容加载流程
- 分页算法原理
- 进度保存机制
- 净化规则应用

### 3. 本地书籍 (LocalBook)
- TXT/EPUB/PDF 解析流程
- 目录识别正则规则
- 解析器工厂模式

### 4. RSS订阅
- RSS/Atom 解析
- 与书源解析的对比

## UI层学习要点

### 1. Compose 架构
- 状态管理 (StateFlow)
- 副作用处理
- 重组优化

### 2. 导航系统
- NavGraph 定义
- 路由参数传递
- 深度链接

### 3. 主题系统
- Monet 动态颜色
- Material 3 组件
- 深色模式

## 服务层学习要点

### 1. DownloadService
- Android DownloadManager 封装
- 状态轮询机制
- 系统下载通知

### 2. CacheBookService
- 协程线程池并发下载
- Semaphore 限流
- Flow 状态推送

### 3. AudioPlayService
- ExoPlayer + MediaSession
- 音频焦点管理
- WakeLock + WiFiLock

### 4. ReadAloudService
- TTS/HttpTTS 双实现
- 策略模式选择
- 电话中断处理

### 5. WebService
- NanoHTTPD 内嵌服务器
- REST API 设计
- WebSocket 实时通信

### 6. CheckSourceService
- 书源三级校验
- 并发校验控制
- 实时进度反馈

### 7. ExportBookService
- TXT/EPUB 导出
- 分卷支持
- 进度追踪

## Web模块学习要点

### 1. Vue3 前端
- Vue Router Hash 路由
- Pinia 状态管理
- Element Plus 组件库

### 2. API 层
- Axios 配置
- 统一返回格式
- sendBeacon 可靠传输

### 3. REST API
- ReturnData 统一格式
- BookController 书籍接口
- BookSourceController 书源接口

### 4. WebSocket
- 实时搜索推送
- 书源调试日志
- 心跳保活机制

## 书籍模块学习要点

### 1. 解析器架构
- 工厂模式统一入口
- BaseLocalBookParse 接口
- 单例缓存解析器

### 2. TXT 解析
- 编码检测原理
- 分块读取算法
- 滑动窗口边界处理

### 3. EPUB 解析
- ZIP 结构解析
- Lazy Loading 策略
- FragmentId 定位

### 4. 目录识别
- 正则匹配原理
- 规则优先级设计
- 长章节自动分割

## JS引擎模块学习要点

### 1. Rhino 引擎封装
- JSR-223 规范实现
- 协程上下文集成
- 单例模式

### 2. 安全沙箱
- ClassShutter 白名单
- RhinoWrapFactory
- 危险类控制

### 3. 协程挂起
- ContinuationPending
- 脚本可取消执行
- 递归深度限制

### 4. JS扩展机制
- JsExtensions 接口
- ajax/connect 网络请求
- variableMap 数据暂存

## 工具类学习要点

### 1. 扩展函数设计
- String 扩展（空安全优先）
- Context 扩展（泛型具体化）
- Flow 扩展（并行处理）
- 作用域函数（run/let/also）

### 2. 网络工具设计
- BitSet 优化 URL 编码判断
- 绝对 URL 拼接算法
- 多版本 Android 兼容

### 3. 文件工具设计
- FileUtils 同步保护
- DocumentUtils SAF 支持
- 路径拼接跨平台兼容

### 4. 编码与格式化
- EncodingDetect 多层检测策略
- HtmlFormatter 正则管道
- MD5Utils ThreadLocal 复用

### 5. 设计模式
- 单例模式（Object Declaration）
- 扩展函数模式
- 策略模式（编码检测）
- Builder 模式（Gson）

## 快速开始

```bash
# 构建项目
./gradlew assembleDebug

# 运行 Web 模块
cd modules/web && pnpm dev

# 查看数据库 Schema
ls app/schemas/
```
