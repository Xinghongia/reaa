# 数据库配置与迁移

## 概述

AppDatabase 是 Room 数据库的统一配置类，包含版本管理、类型转换、Schema 导出等核心配置。

## 源码位置

```
app/src/main/java/io/legado/app/data/
├── AppDatabase.kt           # 数据库主类
└── DatabaseMigrations.kt   # 迁移历史
```

**注意**：类型转换器不是单独文件，而是作为**内部类**定义在各个 Entity 中。

## AppDatabase 实现原理

@Database 注解

```kotlin
@Database(
    entities = [
        Book::class,
        BookChapter::class,
        BookSource::class,
        // ... 共 25+ 个实体
    ],
    version = 37,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookChapterDao: BookChapterDao
    // ...
}
```

**设计分析**:

| 配置项               | 原理     | 作用              |
| ----------------- | ------ | --------------- |
| `entities`        | 声明所有表  | Room 据此生成建表 SQL |
| `version`         | 版本号    | 追踪数据库结构变化       |
| `exportSchema`    | 导出建表语句 | 便于迁移和问题排查       |
| `@TypeConverters` | 全局类型转换 | 所有实体共享          |

### 数据库版本管理

```kotlin
companion object {
    const val dbVersion = 37

    fun getInstance(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "legado.db"  // 数据库文件名
        ).build()
    }
}
```

**设计原理**:

- 版本号从 1 开始，每次结构变更 +1
- 文件名 `legado.db` 是 SQLite 数据库文件

## 类型转换器原理

Room 只支持基本数据类型，复杂类型需要转换。

**实际情况**：类型转换器是作为**内部类**定义在各个 Entity 中的，没有单独文件。

### Book.kt 中的 Converters

```kotlin
// app/src/main/java/io/legado/app/data/entities/Book.kt

data class Book(...) {
    
    class Converters {
        
        @TypeConverter
        fun stringToIntList(value: String?): List<Int>? {
            return value?.split(",")?.mapNotNull { it.toIntOrNull() }
        }
        
        @TypeConverter
        fun intListToString(list: List<Int>?): String? {
            return list?.joinToString(",")
        }
        
        // ... 其他转换方法
    }
}

@TypeConverters(Book.Converters::class)  // 使用内部类
data class Book(...) { ... }
```

### BookSource.kt 中的 Converters

```kotlin
// app/src/main/java/io/legado/app/data/entities/BookSource.kt

data class BookSource(...) {
    
    class Converters {
        
        @TypeConverter
        fun fromHeaders(headers: Map<String, String>?): String? {
            // Map 转 JSON 字符串
            return headers?.let { Gson().toJson(it) }
        }
        
        @TypeConverter
        fun toHeaders(json: String?): Map<String, String>? {
            // JSON 字符串转 Map
            return json?.let { Gson().fromJson(it, object : TypeToken<Map<String, String>>() {}) }
        }
        
        // ... 其他转换方法
    }
}
```

**转换器注册方式**:

1. **实体级**: 在实体类上加 `@TypeConverters(Entity.Converters::class)`
2. **数据库级**: 在 AppDatabase 上加 `@TypeConverters(Entity.Converters::class)`

**原理**:

```
实体字段类型 → TypeConverter → SQLite 支持的类型
─────────────────────────────────────────────────
Date        → dateToTimestamp → Long
List<Int>   → intListToString → String (逗号分隔)
Map<K,V>    → fromHeaders → JSON String
```

## 迁移策略原理

### 为什么需要迁移

```
用户安装 v1.0  → 数据库 v1
用户升级 v2.0  → 数据库 v2 (需要迁移)
用户卸载重装   → 数据库新建 (无需迁移)
```

**迁移的作用**:

- 老版本升级时，保留已有数据
- 结构变更时，执行 ALTER TABLE 语句
- 保证数据不丢失

### Migration 实现原理

**文件**: `data/DatabaseMigrations.kt`

```kotlin
object DatabaseMigrations {

    val migrations: List<Migration> = listOf(
        object : Migration(35, 36) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE book ADD COLUMN customTag TEXT")
            }
        }
    )
}
```

**编译时检查**:

- Room 编译时检查是否有 `Migration(35, 36)` 的迁移
- 如果缺少且需要升级，报错提醒

### 迁移配置

```kotlin
Room.databaseBuilder(...)
    .addMigrations(*DatabaseMigrations.migrations.toTypedArray())
    .build()
```

**迁移流程**:

```
打开数据库 (v35 存在)
    ↓
查找 Migration(35, 36)
    ↓
执行 migrate() 中的 SQL
    ↓
数据库版本更新为 36
```

## Schema 导出

### 导出位置

```
app/schemas/
├── 1.json
├── 2.json
├── ...
└── 37.json
```

### 配置方式

```kotlin
// app/build.gradle.kts
room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### Schema 文件内容

```json
{
  "formatVersion": 1,
  "database": {
    "version": 37,
    "entities": [
      {
        "tableName": "book",
        "columns": [
          {"name": "bookUrl", "type": "TEXT", "affinity": "2"}
        ]
      }
    ]
  }
}
```

**作用**:

1. 记录每次数据库变更
2. 生成迁移文件时参考
3. 问题排查时可以对比差异

## 数据库构建流程

```
AppDatabase.getInstance()
    ↓
Room.databaseBuilder()
    ↓
配置 addMigrations()
    ↓
build()
    ↓
返回 RoomDatabase 实例
```

## 学习任务

1. **打开** `AppDatabase.kt`，统计有多少个实体
2. **打开** `DatabaseMigrations.kt`，查看最新迁移添加了什么
3. **打开** `Converters.kt`，理解 Date 和 List 的转换方式
4. **查看** `app/schemas/` 目录，了解版本变化历史
5. **思考** 为什么用 `SupportSQLiteDatabase` 而不是 JDBC

## 相关文件

| 文件                           | 说明          |
| ---------------------------- | ----------- |
| `data/AppDatabase.kt`        | 数据库主类       |
| `data/DatabaseMigrations.kt` | 迁移历史        |
| `data/Converters.kt`         | 类型转换器       |
| `app/schemas/`               | Schema 导出目录 |
| `app/build.gradle.kts`       | KSP 配置      |

