# 数据访问对象 (DAO)

## 概述

DAO (Data Access Object) 是 Room 数据库的访问接口，通过接口 + SQL 注解的方式定义数据操作。

## 源码位置

```
app/src/main/java/io/legado/app/data/dao/
```

## 实现原理

### Room 编译时生成实现

```kotlin
@Dao
interface BookDao {
    @Query("SELECT * FROM book WHERE bookUrl = :bookUrl")
    suspend fun getBook(bookUrl: String): Book?
}
```

**原理**:

1. **KSP 编译时处理**: KSP 读取 `@Dao` 注解的接口
2. **生成实现类**: Room 自动生成 `BookDao_Impl` 类
3. **SQL 验证**: 编译时检查 SQL 语法是否正确
4. **运行时调用**: 调用时执行生成的实现类

### SQL 注解映射

| 注解 | 生成的 SQL |
|------|-----------|
| `@Query` | SELECT |
| `@Insert` | INSERT |
| `@Update` | UPDATE |
| `@Delete` | DELETE |

## 核心 DAO 分析

### BookDao

**文件**: `BookDao.kt`

**查询方法设计**:

```kotlin
// 精确查询 - 唯一索引
@Query("SELECT * FROM book WHERE bookUrl = :bookUrl")
suspend fun getBook(bookUrl: String): Book?

// 列表查询 - 支持 Flow 响应式
@Query("SELECT * FROM book ORDER BY latestChapterTime DESC")
fun getBooksByUpdateTime(): Flow<List<Book>>

// 分页查询 - LIMIT/OFFSET
@Query("SELECT * FROM book LIMIT :limit OFFSET :offset")
suspend fun getBooksByPage(limit: Int, offset: Int): List<Book>

// 模糊搜索 - LIKE 模式匹配
@Query("SELECT * FROM book WHERE bookName LIKE '%' || :key || '%'")
suspend fun searchBook(key: String): List<Book>
```

**设计分析**:

| 方法 | 设计原理 | 性能考量 |
|------|---------|---------|
| 精确查询 | 主键/唯一索引查询 | O(1) 时间复杂度 |
| Flow 列表 | Reactive Streams 模式 | 数据变化自动推送 |
| 分页查询 | 延迟加载 | 避免加载全部数据到内存 |
| LIKE 搜索 | 全表扫描 | 大数据量时需优化 |

### Flow 响应式原理

```kotlin
@Query("SELECT * FROM book")
fun allBooks(): Flow<List<Book>>
```

**实现原理**:

1. Room 在后台线程执行查询
2. 查询结果封装为 `Flow`
3. 数据库变化时，Flow 自动发射新数据
4. 上游取消时，自动释放资源

**为什么用 Flow**:
- **自动更新**: 数据库变化时 UI 自动刷新
- **生命周期感知**: 与 ViewModel 生命周期配合
- **背压处理**: 数据量大时可以处理

### OnConflictStrategy 策略

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insert(book: Book)
```

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| `REPLACE` | 删除旧记录，插入新记录 | 更新书籍信息 |
| `IGNORE` | 忽略冲突，不插入 | 避免重复插入 |
| `ABORT` | 事务回滚 | 需要手动处理冲突 |

**为什么用 REPLACE**:
- 书源 URL 唯一，重复插入即更新
- 简化业务逻辑，无需判断存在与否

## 索引在查询中的作用

### 查看 Book 的索引

**文件**: `data/entities/Book.kt`

```kotlin
@Index(value = ["bookUrl"], unique = true)    // 主键索引
@Index(value = ["author", "bookUrl"])         // 复合索引
@Index(value = ["latestChapterTime"])          // 排序索引
```

### 查询与索引对应

| 查询 | 使用的索引 |
|------|-----------|
| `WHERE bookUrl = ?` | 主键索引 (最快) |
| `WHERE author = ? AND bookUrl = ?` | 复合索引 |
| `ORDER BY latestChapterTime` | 排序索引 |

**设计原理**:
- 索引让 WHERE 和 ORDER 查询更快
- 但索引也有代价：占用空间、影响写入性能
- 按查询频率决定是否建索引

## DAO 方法命名规范

```
get + 实体名    → 返回单条或 null
get + 实体名 + s → 返回列表
insert         → 插入，返回主键
update         → 更新
delete         → 删除
find           → 模糊查询
count          → 统计数量
exists        → 检查存在性
```

## 事务支持

```kotlin
@Transaction
suspend fun updateBookAndChapters(book: Book, chapters: List<BookChapter>) {
    update(book)
    deleteOldChapters(book.bookUrl)
    insert(chapters)
}
```

**原理**:
- `@Transaction` 保证多个操作在同一个事务
- 任一操作失败，全部回滚
- 保证数据一致性

## 学习任务

1. **打开** `BookDao.kt`，找到 `allBooks()` 方法
2. **分析** 返回类型 `Flow<List<Book>>` 的含义
3. **理解** `REPLACE` 策略在 `insert()` 中的作用
4. **思考** 哪些查询需要索引优化

## 相关文件

| 文件 | 说明 |
|------|------|
| `data/dao/BookDao.kt` | 书籍 DAO |
| `data/dao/BookChapterDao.kt` | 章节 DAO |
| `data/dao/BookSourceDao.kt` | 书源 DAO |
| `data/dao/ReplaceRuleDao.kt` | 净化规则 DAO |
| `data/entities/Book.kt` | 查看索引定义 |
