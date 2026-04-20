# 业务逻辑层 (Model Layer)

## 概述

业务逻辑层处理核心业务功能：书籍解析、RSS订阅、规则分析、缓存管理等。

## 目录结构

```
model/
├── 01_analyze_rule/       # 书源规则解析
├── 02_read_book/          # 阅读器核心
├── 03_local_book/         # 本地书籍解析
└── 04_rss/                # RSS订阅
```

## 学习路径

```
规则解析 → 书籍获取 → 本地解析 → 阅读渲染
```

## 核心模块

### 1. 规则解析 (01_analyze_rule)

**目标**: 理解如何从网站解析书籍信息

**关键文件**:
- `model/analyzeRule/RuleAnalyzer.kt` - 规则分析器入口
- `model/analyzeRule/AnalyzeByXPath.kt` - XPath 解析
- `model/analyzeRule/AnalyzeByJSoup.kt` - JSoup 解析

**核心流程**:

```
输入: 书源URL + 书源规则
    ↓
RuleAnalyzer 分析规则类型
    ↓
选择解析器 (XPath/JSoup/Regex/JSON)
    ↓
执行解析脚本
    ↓
输出: 书籍信息/章节列表/正文内容
```

### 2. 阅读器核心 (02_read_book)

**目标**: 理解阅读器的实现原理

**关键文件**:
- `model/ReadBook.kt` - 阅读器核心类 (40KB+)
- `model/CacheBook.kt` - 书籍缓存管理

### 3. 本地书籍 (03_local_book)

**目标**: 理解本地书籍格式解析

**关键文件**:
- `model/localBook/LocalBook.kt` - 本地书籍入口
- `model/localBook/TextFile.kt` - TXT 解析
- `model/localBook/EpubFile.kt` - EPUB 解析

### 4. RSS订阅 (04_rss)

**目标**: 理解 RSS 解析和订阅

**关键文件**:
- `model/rss/` - RSS 相关类

## 数据流

```
┌─────────────────────────────────────────────────────────────┐
│                     书源搜索                                 │
│  SearchViewModel → SearchRepository → RuleAnalyzer        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     书籍缓存                                 │
│  CacheBook → BookRepository → Room Database                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     本地解析                                 │
│  LocalBook → TextFile/EpubFile → BookChapter              │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     阅读渲染                                 │
│  ReadBook → ReadViewModel → Compose UI                    │
└─────────────────────────────────────────────────────────────┘
```

## 下一章

[规则解析 (01_analyze_rule)](03_model_layer/01_analyze_rule/README.md)
