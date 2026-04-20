# 主题系统 (Theme)

## 概述

主题系统基于 **Material Design 3** (MD3)，支持动态颜色、深色模式、自定义主题。

## 核心技术

| 技术 | 版本 | 用途 |
|------|------|------|
| Material 3 | 1.5.0-alpha17 | MD3 组件库 |
| Material Kolor | 4.1.1 | 动态取色 |
| Monet | Android 12+ | 系统动态颜色 |

## 主题架构

```
MaterialTheme
├── colorScheme (颜色)
│   ├── primary
│   ├── secondary
│   ├── background
│   └── surface
├── typography (字体)
└── shapes (形状)
```

## 核心组件

### 1. 主题定义

**文件**: `ui/theme/Theme.kt`

**打开文件分析**:

```kotlin
@Composable
fun LegadoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ 动态颜色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // 自定义主题
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
```

**设计分析**:

| 参数 | 说明 |
|------|------|
| `darkTheme` | 是否深色模式 |
| `dynamicColor` | 是否使用系统动态颜色 |
| `colorScheme` | 颜色方案 |

### 2. 颜色方案

**文件**: `ui/theme/Color.kt`

```kotlin
// 亮色主题
val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

// 暗色主题
val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF1C1B1F),
    onSecondary = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE3E2E6),
    onSurface = Color(0xFFE3E2E6),
)
```

**命名规则**:

| Token | 用途 |
|-------|------|
| `primary` | 主要操作、按钮 |
| `secondary` | 次要操作 |
| `background` | 背景色 |
| `surface` | 卡片、对话框背景 |
| `onPrimary` | 在 primary 上的文字颜色 |
| `onSurface` | 在 surface 上的文字颜色 |

### 3. 动态颜色 (Monet)

**原理**: 从壁纸提取主色调，自动生成完整色板

```kotlin
// Android 12+ 系统 API
val context = LocalContext.current
val colorScheme = dynamicLightColorScheme(context)
```

**色板生成**:

```
壁纸主色
    ↓
Monet 引擎
    ↓
生成完整色板 (40+ 颜色)
    ↓
应用到 MaterialTheme
```

**限制**:
- 需要 Android 12+
- 与官方 Legado 主题不兼容

## 主题应用

### 在应用中使用主题

```kotlin
// Application.kt
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // 应用动态颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
}
```

### 在 Compose 中使用主题

```kotlin
LegadoTheme {
    // 所有内容自动应用主题
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        MainScreen()
    }
}
```

### 获取主题颜色

```kotlin
// 获取当前颜色方案
val colorScheme = MaterialTheme.colorScheme

// 使用颜色
Text(
    text = "Hello",
    color = colorScheme.primary
)

// 使用背景
Box(
    modifier = Modifier.background(colorScheme.surface)
)
```

## 自定义主题

### 1. 阅读主题

阅读界面使用独立主题配置:

```kotlin
// Book.ReadConfig 中的主题设置
data class ReadConfig(
    val textColor: Int = 0,
    val backgroundColor: Int = 0,
    val lineSpacing: Float = 1.2f,
    val fontSize: Int = 16,
)
```

**原理**:
- 阅读主题不跟随系统
- 用户可自定义背景/字体/行距
- 保存到 Book 实体的 `readConfig` 字段

### 2. 主题切换

```kotlin
// 切换深色模式
val isDarkTheme = isSystemInDarkTheme()
LegadoTheme(darkTheme = isDarkTheme) {
    // ...
}

// 手动切换
data class ThemeState(
    val isDarkMode: Boolean = false,
    val useDynamicColor: Boolean = true
)
```

## 字体排版

**文件**: `ui/theme/Type.kt`

```kotlin
val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

**使用**:

```kotlin
Text(
    text = "Title",
    style = MaterialTheme.typography.headlineLarge
)
```

## 形状

**文件**: `ui/theme/Shape.kt`

```kotlin
val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp)
)
```

**使用**:

```kotlin
Card(
    shape = MaterialTheme.shapes.medium
) { ... }
```

## 颜色对比度

MD3 自动处理对比度:

```kotlin
// 自动选择对比度足够的颜色
val containerColor = colorScheme.primaryContainer
val contentColor = contentColorFor(containerColor)  // 自动计算
```

## 主题持久化

**保存**:

```kotlin
// 保存到 DataStore
dataStore.edit { preferences ->
    preferences[IS_DARK_MODE] = isDarkMode
    preferences[USE_DYNAMIC_COLOR] = useDynamicColor
}
```

**读取**:

```kotlin
val isDarkMode by dataStore.data
    .map { it[IS_DARK_MODE] ?: false }
    .collectAsState(initial = false)
```

## 学习任务

1. **打开** `ui/theme/Theme.kt`，分析动态颜色逻辑
2. **打开** `ui/theme/Color.kt`，理解颜色命名规则
3. **思考** 阅读主题为什么独立于系统主题
4. **分析** Monet 引擎如何生成色板

## 相关文件

| 文件 | 说明 |
|------|------|
| `ui/theme/Theme.kt` | 主题定义 |
| `ui/theme/Color.kt` | 颜色方案 |
| `ui/theme/Type.kt` | 字体排版 |
| `ui/theme/Shape.kt` | 形状定义 |
| `data/entities/Book.kt` | ReadConfig 阅读主题 |
| `help/theme/ThemeHelp.kt` | 主题辅助类 |
