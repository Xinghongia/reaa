# Repository (仓库模式)

## 概述

Repository 是封装数据访问逻辑的中间层，提供统一的数据操作接口给 ViewModel 使用。

## 源码位置

```
app/src/main/java/io/legado/app/data/repository/
```

## 设计原理

### 为什么需要 Repository

```
直接使用 DAO 的问题:
├── ViewModel 直接依赖 DAO
├── 业务逻辑分散在 ViewModel
├── 测试困难 (需要真实数据库)
└── 换存储方式困难 (比如从 Room 换成 DataStore)

引入 Repository 后:
├── ViewModel → Repository → DAO
├── 业务逻辑封装在 Repository
├── Repository 可注入，便于测试
└── 换存储方式只需改 Repository
```

### Repository 与 DAO 的区别

| 维度 | DAO | Repository |
|------|-----|-----------|
| **职责** | 数据存取 | 业务逻辑 + 数据存取 |
| **接口** | 数据库操作 | 领域操作 |
| **抽象** | 底层实现 | 用例驱动 |
| **依赖** | ViewModel 直接依赖 DAO | ViewModel 依赖 Repository |

## 核心 Repository 分析

### BookRepository

**文件**: `data/repository/BookRepository.kt`

**实现原理**:

```kotlin
class BookRepository(
    private val bookDao: BookDao,           // 依赖 DAO
    private val bookChapterDao: BookChapterDao
) {
    // 封装业务逻辑
    suspend fun saveBookWithChapters(
        book: Book,
        chapters: List<BookChapter>
    ) {
        // 组合多个 DAO 操作
        bookDao.insert(book)
        chapters.forEach { chapter ->
            bookChapterDao.insert(chapter)
        }
    }
}
```

**设计分析**:

| 设计 | 原理 | 好处 |
|------|------|------|
| 构造器注入 DAO | 依赖反转 | 可替换实现 |
| 封装组合操作 | 事务逻辑内聚 | 避免 ViewModel 重复编写 |
| 暴露 Flow | 响应式数据流 | UI 自动感知变化 |

### SearchRepository

**文件**: `data/repository/SearchRepository.kt` + `SearchRepositoryImpl.kt`

**实现原理**:

```kotlin
interface SearchRepository {
    suspend fun searchBook(key: String): List<SearchBook>
}

class SearchRepositoryImpl(
    private val bookSourceDao: BookSourceDao
) : SearchRepository {

    override suspend fun searchBook(key: String): List<SearchBook> {
        // 1. 获取所有启用的书源
        val sources = bookSourceDao.getEnabledBookSources().first()

        // 2. 并发搜索多个书源
        return sources.map { source ->
            async { searchWithSource(source, key) }
        }.awaitAll().flatten()
    }
}
```

**设计分析**:

- **接口与实现分离**: `SearchRepository` 是接口，`SearchRepositoryImpl` 是实现
- **并发搜索**: 使用 `async/await` 并发查询多个书源
- **First() 转换**: Flow → List 的简单转换

## 依赖注入配置

**文件**: `app/src/main/java/io/legado/app/di/appModule.kt`

**实现原理**:

```kotlin
val appModule = module {
    // singleOf 创建单例
    singleOf(::BookRepository)
    singleOf(::BookGroupRepository)

    // 带接口的注入
    single<SearchRepository> { SearchRepositoryImpl(get()) }
}
```

**Koin 原理**:

1. `get<Repository>()` 解析依赖图
2. 自动注入构造器所需的 DAO
3. 返回单例实例

## Repository 在 ViewModel 中的使用

**文件**: `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfViewModel.kt`

**实现原理**:

```kotlin
class BookshelfViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    // Repository 返回 Flow，转换为 StateFlow 给 Compose
    val books: StateFlow<List<Book>> = bookRepository.getAllBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteBook(bookUrl: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookUrl)
        }
    }
}
```

**设计分析**:

| 转换 | 原理 |
|------|------|
| `Flow` → `StateFlow` | Compose 需要 StateFlow 观察 |
| `viewModelScope` | 生命周期绑定，ViewModel 销毁时取消 |
| `SharingStarted.WhileSubscribed(5000)` | 新订阅前保留旧数据 5 秒 |

## 数据流完整链路

```
┌─────────────────────────────────────────────────────────────┐
│                        UI 层 (Compose)                       │
│  val books by viewModel.books.collectAsState()             │
└─────────────────────────────┬───────────────────────────────┘
                              │ 观察 StateFlow
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     ViewModel 层                            │
│  val books: StateFlow<List<Book>> = repository.getAllBooks()│
└─────────────────────────────┬───────────────────────────────┘
                              │ 调用
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Repository 层                            │
│  BookRepository.getAllBooks() → return bookDao.allBooks()  │
└─────────────────────────────┬───────────────────────────────┘
                              │ 调用
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       DAO 层                                │
│  @Query("SELECT * FROM book") fun allBooks(): Flow<List>  │
└─────────────────────────────┬───────────────────────────────┘
                              │ Room KSP 生成实现
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Room Database                            │
│  SQLite 文件: legado.db                                    │
└─────────────────────────────────────────────────────────────┘
```

## 响应式数据更新原理

```
数据库变化 → DAO 发射新数据 → Flow 传递 → Repository 转发 → StateFlow 更新 → Compose 重组
```

**触发时机**:
- 插入/更新/删除数据时
- Flow 会自动发射最新查询结果

## 学习任务

1. **打开** `BookRepository.kt`，分析构造器依赖
2. **打开** `appModule.kt`，查找 Repository 注入方式
3. **打开** `BookshelfViewModel.kt`，理解 Flow → StateFlow 转换
4. **理解** 为什么 ViewModel 不直接使用 DAO

## 相关文件

| 文件 | 说明 |
|------|------|
| `data/repository/BookRepository.kt` | 书籍仓库 |
| `data/repository/SearchRepository.kt` | 搜索仓库接口 |
| `data/repository/SearchRepositoryImpl.kt` | 搜索仓库实现 |
| `di/appModule.kt` | Koin 注入配置 |
| `ui/main/bookshelf/BookshelfViewModel.kt` | 书架 ViewModel |
