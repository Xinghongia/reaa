# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概述

**Legado with MD3** 是开源项目 [Legado](https://github.com/gedoor/legado) 的 Material Design 3 重写版本，支持本地书籍（TXT、EPUB、PDF、UMD）、在线书源和 RSS 订阅。项目正在积极从传统 Views 迁移至 Jetpack Compose。

## 构建命令

### Android 应用

```bash
./gradlew assembleRelease          # Release 构建
./gradlew assembleDebug           # Debug 构建
./gradlew test                    # 运行单元测试
./gradlew :app:lint               # Lint 分析
```

### Web 模块 (`modules/web/`)

```bash
cd modules/web
pnpm dev                          # 开发服务器
pnpm build                        # 生产构建（带同步）
pnpm type-check                   # TypeScript 验证
```

## 架构

### 应用模块 (`app/src/main/java/io/legado/app/`)

| 目录 | 用途 |
|------|------|
| `api/` | Web 服务的 REST API 控制器 |
| `base/` | 基础类（Activities、Fragments、ViewModels） |
| `constant/` | 应用级常量（事件、意图、偏好设置键） |
| `data/` | Room 数据库、DAOs 和实体定义 |
| `help/` | 工具辅助类（加密、JS 桥接、主题） |
| `lib/` | 独立库（第三方集成） |
| `model/` | 业务逻辑：书籍解析、RSS、规则分析 |
| `receiver/` | 广播接收器 |
| `service/` | 后台服务（下载、音频、Web 服务） |
| `ui/` | 按功能组织的 UI 组件 |
| `utils/` | 通用 Kotlin 扩展和辅助函数 |
| `web/` | 内嵌 HTTP 服务器（NanoHTTPD） |

### 扩展模块

- `modules/book/` - 书籍格式解析库
- `modules/rhino/` - JavaScript 执行引擎
- `modules/web/` - Vue3 Web 界面（书架/书源管理）

### 数据层

Room 数据库（`AppDatabase.kt`）管理所有实体：

- `Book`、`BookChapter`、`BookSource` - 核心阅读数据
- `RssSource`、`RssArticle` - RSS 订阅数据
- `ReplaceRule` - 内容净化规则
- `ReadRecord`、`ReadRecordDetail` - 阅读历史和统计

## 核心技术

- **UI**: Jetpack Compose + Material 3（从 Views 迁移中）
- **DI**: Koin
- **数据库**: Room + KSP
- **网络**: OkHttp + Cronet（嵌入式 Chromium）
- **JS 执行**: Rhino（通过 `modules/rhino`）
- **Web 服务器**: NanoHTTPD（本地 HTTP API）
- **图片加载**: Coil + Glide
- **PDF**: PdfRenderer for Android

## 重要约定

### 书源规则

书源解析规则定义在 `model/analyzeRule/`。添加新的书源支持时，需要扩展规则 DSL 并更新 `ui/book/source/` 中的对应 UI。

### Web 服务 API

应用在可配置端口上运行本地 HTTP 服务器。Vue3 Web 界面（`modules/web/`）通过 `api/controller/` 中定义的 REST API 进行通信。本地开发时，确保手机和电脑在同一网络，并在 `.env.development` 中配置 `VITE_API`。

### 主题系统

使用基于 Monet 的动态主题。由于 Monet 引擎重构，官方 Legado 应用的自定义主题**不兼容**。动态颜色功能需要 Android 12+。

### 本地书籍解析

支持的格式：TXT、EPUB、PDF、UMD。解析入口在 `model/localBook/LocalBook.kt`。TXT 目录规则（`TxtTocRule`）使用定义在 `data/entities/TxtTocRule.kt` 中的正则表达式模式。

## 文件命名规范

- Kotlin 文件：`PascalCase.kt`
- 类名：`PascalCase`
- 函数/属性：`camelCase`
- 常量：`SCREAMING_SNAKE_CASE`

## 相关文档

- 学习文档：`study/` 目录
- 帮助文档：`app/src/main/assets/web/help/md/*.md`
- 官方 wiki：<https://www.yuque.com/legado/wiki>
- 书源教程：<https://mgz0227.github.io/The-tutorial-of-Legado/>
