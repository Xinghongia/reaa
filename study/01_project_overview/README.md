# 项目概览

## 项目信息

| 属性 | 值 |
|------|-----|
| **项目名称** | Legado with MD3 |
| **项目类型** | Android 阅读器应用 |
| **UI框架** | Material Design 3 (Jetpack Compose) |
| **编程语言** | Kotlin |
| **最低SDK** | Android 8.0 (API 26) |
| **目标SDK** | Android 14 (API 37) |

## 技术栈

| 分类 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| DI | Koin |
| 数据库 | Room + KSP |
| 网络 | OkHttp + Cronet |
| JS执行 | Rhino |
| Web服务器 | NanoHTTPD |
| 图片加载 | Coil + Glide |

## 项目结构

```
legado-with-MD3/
├── app/                    # 主应用模块
│   └── src/main/java/io/legado/app/
│       ├── data/           # 数据库层
│       ├── model/          # 业务逻辑
│       ├── ui/            # Compose界面
│       ├── service/        # 后台服务
│       └── api/            # Web API
├── modules/
│   ├── book/              # 书籍解析库
│   ├── rhino/             # JS引擎
│   └── web/               # Vue3界面
└── study/                  # 学习文档
```

## 相关资源

- [Legado 官方](https://github.com/gedoor/legado)
- [官方 Wiki](https://www.yuque.com/legado/wiki)
