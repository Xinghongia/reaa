# 数据层 (Data Layer)

## 概述

数据层负责应用中所有持久化数据的存储和管理，基于 Room 数据库实现。

## 学习路径

```
实体类 → DAO → Repository → ViewModel
```

建议按顺序学习这四个部分，理解数据的流向。

## 目录结构

```
02_data_layer/
├── README.md           # 本文件
├── 01_entities/       # 实体类
├── 02_dao/            # 数据访问对象
├── 03_repository/     # 仓库模式
└── 04_database/       # 数据库配置
```

## 学习顺序

### 1. 实体类 (01_entities)

**目标**: 理解数据结构

**关键文件**:
- `data/entities/Book.kt` - 书籍实体
- `data/entities/BookChapter.kt` - 章节实体
- `data/entities/BookSource.kt` - 书源实体

**学习问题**:
- 书籍和章节是什么关系？
- 书源实体包含哪些解析规则字段？

### 2. DAO (02_dao)

**目标**: 理解数据如何存取

**关键文件**:
- `data/dao/BookDao.kt` - 书籍操作接口
- `data/dao/BookChapterDao.kt` - 章节操作接口

**学习问题**:
- DAO 方法的命名规范是什么？
- Flow 响应式查询有什么好处？

### 3. Repository (03_repository)

**目标**: 理解数据封装

**关键文件**:
- `data/repository/BookRepository.kt` - 书籍仓库
- `di/appModule.kt` - Koin 注入配置

**学习问题**:
- Repository 和 DAO 的区别是什么？
- ViewModel 如何获取数据？

### 4. 数据库配置 (04_database)

**目标**: 理解数据库管理

**关键文件**:
- `data/AppDatabase.kt` - 数据库主类
- `data/DatabaseMigrations.kt` - 迁移历史

**学习问题**:
- Schema 导出有什么用？
- 迁移和直接修改表的区别？

## 数据流

```
用户操作
    │
    ▼
ViewModel
    │
    ▼ 调用
Repository
    │
    ▼ 调用
DAO
    │
    ▼ 操作
Room Database
    │
    ▼ 持久化
SQLite 文件
```

## 源码阅读顺序

1. **先看实体**: `data/entities/Book.kt`
2. **再看 DAO**: `data/dao/BookDao.kt`
3. **然后 Repository**: `data/repository/BookRepository.kt`
4. **最后 ViewModel**: `ui/main/bookshelf/BookshelfViewModel.kt`

## 核心问题

**Q: 为什么需要 Repository？**
A: 封装数据访问逻辑，提供更友好的接口给 ViewModel

**Q: Flow 和普通返回值的区别？**
A: Flow 是响应式的，数据变化会自动通知观察者

**Q: 迁移有什么用？**
A: 保证老版本升级时数据不丢失

## 相关配置

- 数据库版本: `AppDatabase.kt` 中的 `dbVersion`
- Schema 导出: `app/schemas/` 目录
- KSP 配置: `app/build.gradle.kts`
