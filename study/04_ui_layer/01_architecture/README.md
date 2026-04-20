# UI 架构

## 概述

UI 架构基于 **Jetpack Compose** 声明式 UI 框架，采用 MVVM 模式分离 UI 逻辑和业务逻辑。

## 核心组件

### 1. Compose Screen

**职责**: 纯 UI 展示，无业务逻辑

**文件示例**: `ui/main/bookshelf/BookshelfScreen.kt`

**设计原则**:

| 原则 | 说明 |
|------|------|
| 纯函数 | 相同输入产生相同 UI |
| 无副作用 | 不直接操作数据库/网络 |
| 可预览 | `@Preview` 注解支持 |
| 可测试 | 逻辑在 ViewModel，UI 纯展示 |

**代码结构**:

```kotlin
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel = koinViewModel()
) {
    val books by viewModel.books.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    BookshelfContent(
        books = books,
        onBookClick = viewModel::onBookClick,
        onBookLongClick = viewModel::onBookLongClick
    )
}

@Composable
private fun BookshelfContent(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    LazyColumn {
        items(books) { book ->
            BookItem(
                book = book,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) }
            )
        }
    }
}
```

**设计分析**:

- **Screen 函数**: 连接 ViewModel 和 Content，处理状态订阅
- **Content 函数**: 纯 UI 展示，方便 `@Preview` 预览
- **分离好处**: Content 可独立测试，不依赖 ViewModel

### 2. ViewModel 状态管理

**职责**: 持有 UI 状态，处理用户交互

**文件示例**: `ui/main/bookshelf/BookshelfViewModel.kt`

**打开文件分析**:

```kotlin
class BookshelfViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    // 1. 数据流
    val books: StateFlow<List<Book>> = bookRepository.getAllBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 2. UI 状态
    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    // 3. 用户交互处理
    fun onBookClick(book: Book) {
        // 导航到阅读界面
        _uiState.update { it.copy(navigateToRead = book.bookUrl) }
    }

    fun onBookLongClick(book: Book) {
        // 显示操作菜单
        _uiState.update { it.copy(showBookMenu = book) }
    }
}
```

**设计分析**:

| 组件 | 类型 | 说明 |
|------|------|------|
| `books` | `StateFlow` | 来自 Repository 的数据流 |
| `_uiState` | `MutableStateFlow` | 内部可变状态 |
| `uiState` | `StateFlow` | 对外暴露的只读状态 |

**状态订阅原理**:

```
Repository Flow
    ↓
stateIn() → 转换为 StateFlow
    ↓
Compose collectAsState() → 自动重组 UI
```

### 3. UiState 数据类

**职责**: 封装屏幕所有状态

```kotlin
data class BookshelfUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showBookMenu: Book? = null,
    val navigateToRead: String? = null,
    val selectedBooks: Set<String> = emptySet()
)
```

**设计好处**:

- **单一数据源**: 所有 UI 状态在一个类中
- **易于恢复**: 系统回收后可保存/恢复
- **可预测**: 状态变化可追踪

## 状态管理策略

### 1. 普通状态 (remember)

```kotlin
@Composable
fun SearchBar() {
    var query by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    TextField(
        value = query,
        onValueChange = { query = it }
    )
}
```

**适用场景**: 仅当前 Composable 使用的局部状态

### 2. 共享状态 (ViewModel)

```kotlin
@Composable
fun BookListScreen(
    viewModel: BookListViewModel = koinViewModel()
) {
    val books by viewModel.books.collectAsState()
    // ...
}
```

**适用场景**: 多个 Composable 共享的状态

### 3.  rememberSaveable

```kotlin
@Composable
fun EditScreen() {
    var draft by rememberSaveable { mutableStateOf("") }
    // 旋转屏幕后数据保留
}
```

**适用场景**: 需要跨配置变更保留的状态

## 事件处理

### 单向数据流

```
用户点击
    ↓
调用 ViewModel 方法
    ↓
更新 StateFlow
    ↓
Compose 自动重组
```

### 事件分类

| 事件类型 | 处理方式 | 示例 |
|----------|----------|------|
| UI 事件 | 直接处理 | 展开/收起动画 |
| 业务事件 | ViewModel 处理 | 删除书籍 |
| 导航事件 | ViewModel 触发，UI 执行 | 跳转到阅读页 |

## 副作用处理

### LaunchedEffect

```kotlin
@Composable
fun BookDetailScreen(bookId: String) {
    val viewModel: BookDetailViewModel = koinViewModel()

    // bookId 变化时重新加载
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }
}
```

### SideEffect

```kotlin
@Composable
fun AnalyticsScreen() {
    SideEffect {
        // 每次重组后执行
        analytics.track("screen_view")
    }
}
```

### DisposableEffect

```kotlin
@Composable
fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current

    DisposableEffect(enabled) {
        val callback = object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() = onBack()
        }
        dispatcher?.onBackPressedDispatcher?.addCallback(callback)

        onDispose {
            callback.remove()
        }
    }
}
```

## 性能优化

### 1. 避免不必要的重组

```kotlin
// ❌ 每次重组都创建新对象
BookItem(book = Book(...))

// ✅ 使用 remember 缓存
val book = remember { Book(...) }
BookItem(book = book)
```

### 2. 使用 key 优化列表

```kotlin
LazyColumn {
    items(
        items = books,
        key = { it.bookUrl }  // 使用唯一标识
    ) { book ->
        BookItem(book)
    }
}
```

### 3. 延迟加载

```kotlin
LazyColumn {
    items(books) { book ->
        // 只有可见时才组合
        BookItem(book)
    }
}
```

## 学习任务

1. **打开** `ui/main/bookshelf/BookshelfScreen.kt`，分析 Screen/Content 分离
2. **打开** `ui/main/bookshelf/BookshelfViewModel.kt`，理解 StateFlow 用法
3. **思考** 为什么 ViewModel 比 remember 更适合管理业务状态
4. **分析** UiState 数据类包含哪些类型的状态

## 相关文件

| 文件 | 说明 |
|------|------|
| `ui/main/MainActivity.kt` | 入口 Activity |
| `ui/main/bookshelf/BookshelfScreen.kt` | 书架界面 |
| `ui/main/bookshelf/BookshelfViewModel.kt` | 书架 VM |
| `ui/book/read/ReadBookScreen.kt` | 阅读界面 |
