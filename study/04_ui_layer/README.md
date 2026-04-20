# UI 层 (User Interface)

## 概述

UI 层使用 **Jetpack Compose** 构建 Material Design 3 界面，采用声明式 UI 范式。项目正在从传统 Views 迁移到 Compose。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Jetpack Compose | 2026.03.01 | 声明式 UI 框架 |
| Material 3 | 1.5.0-alpha17 | MD3 组件库 |
| Navigation Compose | 2.9.7 | 页面导航 |
| Koin | 4.2.0 | ViewModel 注入 |

## 目录结构

```
ui/
├── main/                    # 主界面
│   ├── MainActivity.kt      # 入口 Activity
│   ├── MainScreen.kt        # 主屏幕 Compose
│   ├── MainViewModel.kt     # 主界面 VM
│   ├── MainDestination.kt   # 导航目的地
│   ├── bookshelf/           # 书架
│   ├── explore/             # 发现
│   ├── rss/                 # RSS
│   └── my/                  # 我的
├── book/                    # 书籍相关
│   ├── read/                # 阅读界面
│   ├── info/                # 书籍详情
│   ├── source/              # 书源管理
│   └── search/              # 搜索
├── config/                  # 设置页面
├── theme/                   # 主题设置
└── widget/                  # 通用组件
```

## 架构模式

```
UI 层采用 MVVM 模式:

Compose Screen (UI)
    ↑
    │ observe
    ↓
ViewModel (State Holder)
    ↑
    │ call
    ↓
Repository (Data)
```

## 学习路径

```
1. UI 架构 (01_architecture)
   ├── Compose 基础
   ├── ViewModel 状态管理
   └── Screen 实现

2. 导航系统 (02_navigation)
   ├── Navigation Compose
   ├── 底部导航
   └── 页面跳转

3. 主题系统 (03_theme)
   ├── Material Design 3
   ├── 动态颜色
   └── 深色模式
```

## 核心概念

### 1. Compose 声明式 UI

传统 View (命令式):
```kotlin
// 找到视图，修改属性
textView.text = "Hello"
textView.visibility = View.VISIBLE
```

Compose (声明式):
```kotlin
// 根据状态描述 UI
@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name")
}
```

### 2. 状态管理

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableIntStateOf(0) }

    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

### 3. ViewModel + StateFlow

```kotlin
class BookshelfViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

// 在 Compose 中使用
val books by viewModel.books.collectAsState()
```

## 下一章

[UI 架构 (01_architecture)](04_ui_layer/01_architecture/README.md) - Compose 基础、ViewModel、Screen 实现
