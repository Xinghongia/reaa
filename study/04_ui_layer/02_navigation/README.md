# 导航系统 (Navigation)

## 概述

导航系统使用 **Navigation Compose** 实现页面间的跳转和参数传递。

## 技术选型

| 导航方式 | 用途 | 说明 |
|----------|------|------|
| Navigation Compose | 页面导航 | 主框架 |
| 底部导航栏 | Tab 切换 | 书架/发现/RSS/我的 |
| Deep Link | 外部跳转 | 书源导入、分享链接 |

## 核心组件

### 1. NavHost

**职责**: 定义导航图，管理页面堆栈

**文件**: `ui/main/MainActivity.kt`

**打开文件分析**:

```kotlin
@Composable
fun LegadoNavHost(
    navController: NavHostController,
    startDestination: String = "bookshelf"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 书架
        composable("bookshelf") {
            BookshelfScreen()
        }

        // 书籍详情
        composable(
            "book/{bookUrl}",
            arguments = listOf(navArgument("bookUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookUrl = backStackEntry.arguments?.getString("bookUrl")
            BookInfoScreen(bookUrl = bookUrl!!)
        }

        // 阅读界面
        composable("read/{bookUrl}/{chapterIndex}") { ... }
    }
}
```

**设计分析**:

| 组件 | 说明 |
|------|------|
| `NavHost` | 导航容器，定义所有路由 |
| `composable()` | 声明一个页面 |
| `arguments` | 定义路由参数 |
| `backStackEntry` | 获取导航参数 |

### 2. NavController

**职责**: 控制导航操作

```kotlin
// 获取 NavController
val navController = rememberNavController()

// 导航到某页
navController.navigate("book/$bookUrl")

// 返回上一页
navController.popBackStack()

// 导航并清空堆栈
navController.navigate("bookshelf") {
    popUpTo("bookshelf") { inclusive = true }
}
```

### 3. 底部导航栏

**文件**: `ui/main/MainScreen.kt`

**打开文件分析**:

```kotlin
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf("bookshelf", "explore", "rss", "my")

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = currentRoute(navController)
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(...) },
                        label = { Text(...) },
                        selected = currentRoute == screen,
                        onClick = {
                            navController.navigate(screen) {
                                // 避免重复点击产生多个实例
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        LegadoNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
```

**设计分析**:

| 属性 | 说明 |
|------|------|
| `launchSingleTop = true` | 避免重复创建页面 |
| `saveState = true` | 保存页面状态 |
| `restoreState = true` | 恢复页面状态 |

## 导航类型

### 1. 无参数导航

```kotlin
// 定义
composable("bookshelf") { BookshelfScreen() }

// 跳转
navController.navigate("bookshelf")
```

### 2. 带参数导航

```kotlin
// 定义
composable(
    "book/{bookUrl}",
    arguments = listOf(
        navArgument("bookUrl") {
            type = NavType.StringType
            nullable = false
        }
    )
) { entry ->
    val bookUrl = entry.arguments?.getString("bookUrl")
    BookInfoScreen(bookUrl)
}

// 跳转
val encodedUrl = Uri.encode(bookUrl)
navController.navigate("book/$encodedUrl")
```

**URL 编码**: 参数含特殊字符时需编码

### 3. 可选参数导航

```kotlin
// 定义
composable(
    "search?keyword={keyword}",
    arguments = listOf(
        navArgument("keyword") {
            defaultValue = ""
            nullable = true
        }
    )
)

// 跳转（两种方式）
navController.navigate("search")               // 使用默认值
navController.navigate("search?keyword=小说")  // 带参数
```

### 4. Deep Link

```kotlin
// AndroidManifest.xml 配置
deepLink {
    uriPattern = "legado://book/{bookUrl}"
}

// NavHost 配置
composable(
    "book/{bookUrl}",
    deepLinks = listOf(
        navDeepLink { uriPattern = "legado://book/{bookUrl}" }
    )
) { ... }
```

**应用场景**:
- 书源分享链接
- 外部应用跳转
- 推送通知跳转

## 导航状态管理

### 返回结果

```kotlin
// A 页面
navController.navigateForResult("search") { result ->
    val selectedBook = result.getString("bookUrl")
}

// B 页面
navController.previousBackStackEntry
    ?.savedStateHandle
    ?.set("bookUrl", bookUrl)
navController.popBackStack()
```

### 导航与 ViewModel 配合

```kotlin
class BookshelfViewModel : ViewModel() {

    private val _navigateToRead = MutableSharedFlow<String>()
    val navigateToRead = _navigateToRead.asSharedFlow()

    fun onBookClick(book: Book) {
        viewModelScope.launch {
            _navigateToRead.emit(book.bookUrl)
        }
    }
}

// Screen
val viewModel: BookshelfViewModel = koinViewModel()
val navController = rememberNavController()

LaunchedEffect(Unit) {
    viewModel.navigateToRead.collect { bookUrl ->
        navController.navigate("read/$bookUrl")
    }
}
```

**设计分析**:

- ViewModel 不持有 NavController（避免内存泄漏）
- 通过 Flow 发送导航事件
- Screen 层执行实际导航

## 页面过渡动画

### 进入/退出动画

```kotlin
composable(
    "book/{bookUrl}",
    enterTransition = {
        slideInHorizontally { fullWidth -> fullWidth }
    },
    exitTransition = {
        slideOutHorizontally { fullWidth -> -fullWidth }
    },
    popEnterTransition = {
        slideInHorizontally { fullWidth -> -fullWidth }
    },
    popExitTransition = {
        slideOutHorizontally { fullWidth -> fullWidth }
    }
) { ... }
```

### 共享元素动画 (Experimental)

```kotlin
// 起始页面
val image = sharedElement("cover_$bookUrl")
Image(
    painter = cover,
    modifier = Modifier.sharedElement(image)
)

// 目标页面
val image = sharedElement("cover_$bookUrl")
Image(
    painter = cover,
    modifier = Modifier.sharedElement(image)
)
```

## 学习任务

1. **打开** `ui/main/MainActivity.kt`，找到 NavHost 定义
2. **分析** 底部导航栏如何避免重复创建页面
3. **理解** Deep Link 的配置方式
4. **思考** 为什么 ViewModel 不直接调用 navigate()

## 相关文件

| 文件 | 说明 |
|------|------|
| `ui/main/MainActivity.kt` | NavHost 定义 |
| `ui/main/MainScreen.kt` | 底部导航栏 |
| `ui/main/MainDestination.kt` | 导航目的地枚举 |
| `AndroidManifest.xml` | Deep Link 配置 |
