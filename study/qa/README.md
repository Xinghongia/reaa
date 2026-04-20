# 学习问答记录

本文档记录学习 Legado with MD3 项目过程中的问答，按照章节组织。

## 目录结构

```
qa/
├── README.md                      # 问答总览
├── 01_project_overview/           # 第一章：项目概览 ✅
├── 02_data_layer/                 # 第二章：数据层 ✅
├── 03_model_layer/               # 第三章：业务逻辑层
├── 04_ui_layer/                  # 第四章：UI层
├── 05_service_layer/             # 第五章：服务层
├── 06_web_module/                # 第六章：Web模块
├── 07_book_module/               # 第七章：书籍模块
├── 08_rhino_module/              # 第八章：JS引擎模块
└── 09_tools/                     # 第九章：工具类模块
```

## 记录规则

- **学习笔记**：包含本章核心概念、流程图、架构图
- **Q&A**：问答记录，包含问题背景、解答、流程图

## 已有内容

### 第一章：项目概览 ✅
- 技术栈总览图
- 项目架构图
- 分层架构图
- Q1: Koin 和 Hilt 是什么？（含对比图）
- Q2: Legado with MD3 相比原版 Legado 有什么新功能？（含迁移对比图）

### 第二章：数据层 ✅
- 为什么需要数据库（对比图）
- Room 三要素图
- Entity 结构图
- DAO 方法与 SQL 对应图
- Flow 响应式原理图
- Database 单例模式图
- Repository 模式对比图
- 完整数据流向图
- Q1: 什么是依赖？跟继承有什么区别？（含对比图）
- Q2: Koin 的 get() 函数是如何工作的？
- Q3: 什么叫 Flow 响应式数据流？（含完整流程图）
- Q4: Repository 和 Service、DAO 和 Mapper 有什么区别？（含架构对比图）
- Q5: BookRepository.kt 是什么功能？（含方法流程图）
- Q6: SearchRepository 接口的实现类在哪？（含依赖注入图）
- Q7: Koin 依赖注入是什么？（含工作流程图）
- Q8: Koin 依赖注入关键字含义和语法？（含语法对比图）
- Q9: AppDatabase 和 DatabaseMigrations 数据库配置与迁移？（含迁移版本图）

## 查看特定章节

- [第一章：项目概览问答](./01_project_overview/README.md)
- [第二章：数据层问答](./02_data_layer/README.md)
- [第三章：业务逻辑层问答](./03_model_layer/README.md)
- [第四章：UI层问答](./04_ui_layer/README.md)
- [第五章：服务层问答](./05_service_layer/README.md)
- [第六章：Web模块问答](./06_web_module/README.md)
- [第七章：书籍模块问答](./07_book_module/README.md)
- [第八章：JS引擎模块问答](./08_rhino_module/README.md)
- [第九章：工具类模块问答](./09_tools/README.md)
