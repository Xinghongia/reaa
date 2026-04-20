# 工具类模块

## 设计思想

工具类模块是整个应用的基础支撑层，封装了大量与业务无关的通用能力。这一模块的设计体现了几个核心思想：

**1. 扩展函数替代继承**

Kotlin 的扩展函数机制允许在不修改原类的情况下为其添加新功能。项目中大量使用扩展函数来为 `String`、`Context`、`View`、`Flow` 等常用类型添加便捷操作。这种方式比继承更灵活，也比 Utility 类更符合 Kotlin 的惯用风格。

**2. 面向协议而非面向继承**

许多工具类采用 `Object` 单例模式，提供静态方法而非实例方法。这不是面向对象的继承体系，而是一种更轻量的组织方式。例如 `FileUtils`、`NetworkUtils`、`MD5Utils` 都是单例对象，它们代表一组相关操作的协议。

**3. 空安全是默认值**

所有扩展函数都充分考虑了空指针情况，使用 `?.` 操作符和 `?: ` elvis 操作符来处理可能的空值。这是 Kotlin 空安全特性的典型应用。

**4. 线程安全与性能平衡**

对于需要频繁调用的工具类（如 `MD5Utils`），使用 `ThreadLocal` 来避免锁竞争，同时保证线程安全。这种设计在高性能场景下尤为重要。

## 核心工具类解析

### 1. 字符串扩展 (StringExtensions.kt)

字符串处理是应用中最频繁的操作之一。这个文件定义了丰富多样的字符串扩展函数，体现了"最小化样板代码"的设计原则。

**核心设计：空安全优先**

```kotlin
fun String?.isContentScheme(): Boolean = this?.startsWith("content://") == true
fun String?.isAbsUrl() = this?.let { ... } ?: false
```

注意参数类型是 `String?`（可空），返回值根据情况灵活处理。这是 Kotlin 空安全特性的典型应用——调用者无需进行显式的空判断，扩展函数内部已经处理好了。

**设计原理：简化常见判断**

```kotlin
fun String?.isJson(): Boolean = this?.run { ... } ?: false
```

使用 `run` 作用域函数将判断逻辑包装，使代码更紧凑。JSON 检测通过检查首尾字符来判断，这是一个经验优化——完整的 JSON 解析代价较高，而大多数场景下只需快速判断。

**学习要点：**

- 打开 [StringExtensions.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\StringExtensions.kt)，逐行分析每个扩展函数的空安全处理
- 理解 `run`、`let`、`also` 等作用域函数的适用场景
- 思考 `splitNotBlank` 为什么返回 `Array<String>` 而不是 `List<String>`

### 2. 编码检测 (EncodingDetect.kt)

本地书籍阅读需要正确识别文件编码，这是一个看似简单实则复杂的问题。

**核心设计：多层检测策略**

编码检测采用"先快后准"的策略：

1. **HTML Meta 标签检测**：对于 HTML 文件，优先解析 `<meta charset="...">` 或 `<meta http-equiv="content-type" content="...; charset=...">`
2. **ICU4J 库检测**：对于普通文本文件，使用 IBM ICU 库的字符编码检测算法
3. **默认 UTF-8**：兜底方案

```kotlin
fun getHtmlEncode(bytes: ByteArray): String {
    // 1. 尝试从 HTML meta 标签提取编码
    // 2. 如果失败，调用 ICU4J 检测
    return getEncode(bytes)
}
```

**设计原理：平衡性能与准确性**

为什么不直接用 ICU4J？因为 ICU4J 需要分析整个文件内容，而 HTML 文件的编码信息通常就在 meta 标签里。先尝试快速定位 meta 标签，只有找不到时才走重量级的通用检测，这是典型的"快速路径"优化。

**学习要点：**

- 打开 [EncodingDetect.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\EncodingDetect.kt)
- 分析 `getHtmlEncode` 的检测逻辑流程
- 理解为什么需要处理 `charset=` 和 `http-equiv` 两种 meta 标签格式
- 思考：GBK 和 GB2312 在检测算法中如何区分？

### 3. HTML 格式化 (HtmlFormatter.kt)

阅读书籍时需要将网页 HTML 转换为纯文本或保留图片的格式。

**核心设计：正则表达式管道**

```kotlin
fun format(html: String?, otherRegex: Regex = otherHtmlRegex): String {
    return html ?: return ""
    return html
        .replace(nbspRegex, " ")
        .replace(espRegex, " ")
        .replace(noPrintRegex, "")
        .replace(wrapHtmlRegex, "\n")
        .replace(commentRegex, "")
        .replace(otherRegex, "")
        .replace(indent1Regex, "\n　　")
        .replace(indent2Regex, "　　")
        .replace(lastRegex, "")
}
```

**设计原理：链式替换**

每个 `replace` 调用都是一次管道操作，将输入逐步转换为最终输出。这种设计的好处是：
- 每个正则表达式职责单一，易于理解和维护
- 可以灵活组合不同的正则来生成不同的格式化结果（如 `formatKeepImg` 保留图片标签）

**设计原理：正则的熔断机制**

代码注释中提到：`正则的"\|"处于顶端而不处于（）中时，具有类似||的熔断效果`。这是正则表达式的一个高级特性，当多个备选模式用 `|` 连接且不在捕获组中时，如果前面的模式已经匹配成功，后面的模式就不会尝试。这种特性被用来简化复杂的图片 URL 提取逻辑。

**学习要点：**

- 打开 [HtmlFormatter.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\HtmlFormatter.kt)
- 分析各个正则表达式的含义和匹配目标
- 理解 `formatKeepImg` 如何处理带参数的图片 URL（如 `{xxx}` 格式的动态参数）
- 思考：为什么有些正则要用 `toRegex()` 而不是直接写字符串？

### 4. 网络工具 (NetworkUtils.kt)

网络相关的工具函数，处理 URL 编码和绝对 URL 拼接。

**核心设计：BitSet 优化**

URL 编码判断使用了 `BitSet` 来优化字符检测：

```kotlin
private val notNeedEncodingQuery: BitSet by lazy {
    val bitSet = BitSet(256)
    for (i in 'a'.code..'z'.code) { bitSet.set(i) }
    for (i in 'A'.code..'Z'.code) { bitSet.set(i) }
    // ...
    return@lazy bitSet
}

fun encodedQuery(str: String): Boolean {
    var i = 0
    while (i < str.length) {
        val c = str[i]
        if (notNeedEncodingQuery.get(c.code)) {
            i++; continue
        }
        // ...
    }
}
```

**设计原理：空间换时间**

如果用 `str[i] in 'a'..'z' || str[i] in 'A'..'Z' || ...` 的方式，每次字符判断都需要多次比较。而用 `BitSet` 只需要一次数组访问（`bitSet.get(c.code)`），在大量 URL 编码判断的场景下性能提升显著。

**设计原理：绝对 URL 拼接**

```kotlin
fun getAbsoluteURL(baseURL: URL?, relativePath: String): String {
    if (baseURL == null) return relativePathTrim
    if (relativePathTrim.isAbsUrl()) return relativePathTrim
    if (relativePathTrim.isDataUrl()) return relativePathTrim
    if (relativePathTrim.startsWith("javascript")) return ""
    return URL(baseURL, relativePath).toString()
}
```

使用 Java 标准库的 `URL` 构造函数来处理相对路径转绝对路径，这是最可靠的方式，因为它正确处理了各种边界情况如 `../` 、 `./` 等。

**学习要点：**

- 打开 [NetworkUtils.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\NetworkUtils.kt)
- 分析 `isAvailable()` 方法对不同 Android 版本的兼容处理
- 理解 `getAbsoluteURL` 为什么需要处理那么多种情况（data url、javascript: 等）

### 5. 文件工具 (FileUtils.kt)

文件和目录操作的工具类。

**核心设计：同步保护**

```kotlin
@Synchronized
fun createFileIfNotExist(filePath: String): File {
    val file = File(filePath)
    if (!file.exists()) {
        file.parent?.let { createFolderIfNotExist(it) }
        file.createNewFile()
    }
    return file
}
```

`createFileIfNotExist` 使用 `@Synchronized` 注解确保多线程安全。这是必要的，因为可能多个协程同时尝试创建同一个文件。

**设计原理：路径拼接的跨平台兼容**

```kotlin
fun getPath(root: File, vararg subDirs: String): String {
    val path = StringBuilder(root.absolutePath)
    subDirs.forEach {
        if (it.isNotEmpty()) {
            path.append(File.separator).append(it)
        }
    }
    return path.toString()
}
```

使用 `File.separator` 而不是硬编码 `/` 或 `\` ，确保在 Windows 和 Linux 系统上都能正常工作。

**学习要点：**

- 打开 [FileUtils.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\FileUtils.kt)
- 分析排序相关常量的设计（`BY_NAME_ASC` 等 IntDef 注解）
- 理解 `@JvmOverloads` 注解的作用

### 6. SAF 支持 (DocumentUtils.kt)

Storage Access Framework（存储访问框架）是 Android 4.4 引入的跨版本文件访问方案。

**核心设计：DocumentFile 封装**

```kotlin
object DocumentUtils {
    fun exists(root: DocumentFile, fileName: String, vararg subDirs: String): Boolean {
        val parent = getDirDocument(root, *subDirs) ?: return false
        return parent.findFile(fileName)?.exists() ?: false
    }

    fun createFolderIfNotExist(root: DocumentFile, vararg subDirs: String): DocumentFile? {
        var parent: DocumentFile? = root
        for (subDirName in subDirs) {
            val subDir = parent?.findFile(subDirName) ?: parent?.createDirectory(subDirName)
            parent = subDir
        }
        return parent
    }
}
```

**设计原理：链式目录创建**

`createFolderIfNotExist` 逐层检查并创建目录，每次循环处理一个路径层级。这种递归式的设计比一次性创建更安全，也能更好地处理中间目录已存在的情况。

**学习要点：**

- 打开 [DocumentUtils.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\DocumentUtils.kt)
- 理解 DocumentFile 和普通 File 的区别
- 思考：为什么需要 SAF？传统的 File API 有什么局限？

### 7. MD5 工具 (MD5Utils.kt)

**核心设计：ThreadLocal 复用 Digester**

```kotlin
object MD5Utils {
    private val threadLocal = ThreadLocal<Digester>()
    private val MD5Digester get() = threadLocal.getOrSet { DigestUtil.digester("MD5") }

    fun md5Encode(str: String?): String = MD5Digester.digestHex(str)
    fun md5Encode16(str: String): String = md5Encode(str).substring(8, 24)
}
```

**设计原理：对象复用**

`Digester` 是重量级对象，创建成本较高。使用 `ThreadLocal` 确保每个线程复用同一个实例，既保证了线程安全，又避免了频繁创建对象的开销。

**学习要点：**

- 打开 [MD5Utils.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\MD5Utils.kt)
- 理解 `ThreadLocal` 的作用原理
- 思考：为什么选择 `DigestUtil`（Hutool 库）而不是自己实现？

### 8. Flow 扩展 (FlowExtensions.kt)

协程 Flow 的并行处理扩展。

**核心设计：flatMapMerge 并行化**

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T> Flow<T>.onEachParallel(
    concurrency: Int,
    crossinline action: suspend (T) -> Unit
): Flow<T> = flatMapMerge(concurrency) { value ->
    flow {
        action(value)
        emit(value)
    }
}.buffer(0)
```

**设计原理：无阻塞并行处理**

`flatMapMerge(concurrency)` 允许同时运行多个协程处理不同的元素。与 `map` 的顺序处理不同，这实现了真正的并行管道。当需要同时下载多个章节内容时，这种扩展非常有用。

**设计原理：Safe 版本**

```kotlin
inline fun <T> Flow<T>.onEachParallelSafe(
    concurrency: Int,
    crossinline action: suspend (T) -> Unit
): Flow<T> = flatMapMerge(concurrency) { value ->
    flow {
        try {
            action(value)
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
        }
        emit(value)
    }
}.buffer(0)
```

Safe 版本捕获了异常并调用 `ensureActive()` 检查协程是否已被取消，防止在已取消的协程中继续处理。

**学习要点：**

- 打开 [FlowExtensions.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\FlowExtensions.kt)
- 理解 `buffer(0)` 的作用（消除背压，让并行真正发挥作用）
- 分析 Safe 和非 Safe 版本的选择场景

### 9. JSON 扩展 (GsonExtensions.kt)

Gson 的封装和常用类型扩展。

**核心设计：预配置单例**

```kotlin
val INITIAL_GSON: Gson by lazy {
    GsonBuilder()
        .registerTypeAdapter(...)
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
}
```

**设计原理：复杂配置一次性完成**

Gson 的配置项较多，如果每次解析都重新配置会造成浪费。通过预创建的单例，所有解析操作都使用同一套配置，既高效又保证一致性。

**设计原理：类型适配器解决精度问题**

```kotlin
.registerTypeAdapter(
    object : TypeToken<Map<String?, Any?>?>() {}.type,
    MapDeserializerDoubleAsIntFix()
)
```

JSON 解析中浮点数转整数容易丢失精度，这个适配器确保在需要时能正确处理。

**学习要点：**

- 打开 [GsonExtensions.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\GsonExtensions.kt)
- 分析三个 Gson 实例（`INITIAL_GSON`、`GSON`、`GSONStrict`）的区别和适用场景
- 理解 `Result<T>` 作为返回类型的优势

### 10. Context 扩展 (ContextExtensions.kt)

Activity、Service 等 Context 相关的扩展函数。

**核心设计：泛型具体化**

```kotlin
inline fun <reified A : Activity> Context.startActivity(configIntent: Intent.() -> Unit = {}) {
    val intent = Intent(this, A::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.apply(configIntent)
    startActivity(intent)
}
```

**设计原理：内联函数 + 具体类型参数**

`<reified A : Activity>` 使得在运行时可以获取到 `A::class.java`，从而创建正确的 Intent。这是 Kotlin 反射的巧妙应用，让调用者可以写成 `context.startActivity<ReadBookActivity>()` 的形式。

**设计原理：DSL 风格的 Intent 配置**

```kotlin
context.startActivity<ReadBookActivity> {
    putExtra("bookUrl", bookUrl)
}
```

`configIntent: Intent.() -> Unit` 参数允许用 DSL 风格配置 Intent，使代码更简洁易读。

**学习要点：**

- 打开 [ContextExtensions.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\ContextExtensions.kt)
- 分析 `startActivityForBook` 如何根据书籍类型选择不同的 Activity
- 理解 PendingIntent 的 `FLAG_MUTABLE` 和 `FLAG_UPDATE_CURRENT` 区别

## 设计模式总结

工具类模块体现了以下设计模式：

### 1. 单例模式 (Object Declaration)

所有工具类都用 `object` 声明为单例，这是 Kotlin 的惯用模式：

```kotlin
object FileUtils { ... }
object NetworkUtils { ... }
```

相比 Java 的饿汉/懒汉单例，Kotlin 的 `object` 更简洁且天然线程安全。

### 2. 扩展函数模式

通过扩展函数为现有类添加功能，而不是创建包装类或继承：

```kotlin
fun String?.isAbsUrl(): Boolean = ...
fun Context.defaultSharedPreferences: SharedPreferences ...
```

### 3. 策略模式

根据不同情况选择不同策略，如编码检测的多种检测方法。

### 4. 模板方法模式

如 `DocumentUtils` 的链式目录创建，每步操作类似但具体细节不同。

### 5. Builder 模式

Gson 的配置使用典型的 Builder 模式，`GsonBuilder` 提供了流畅的 API。

## 架构位置

工具类模块在整个应用架构中处于基础层，被所有其他模块依赖：

```
┌─────────────────────────────────────┐
│           UI 层 (Compose)            │
├─────────────────────────────────────┤
│          业务逻辑层 (Model)           │
├─────────────────────────────────────┤
│           数据层 (Room)              │
├─────────────────────────────────────┤
│  ┌─────────────┬─────────────────┐  │
│  │   utils/    │     help/       │  │
│  │  通用扩展函数 │   业务辅助类     │  │
│  └─────────────┴─────────────────┘  │
├─────────────────────────────────────┤
│        Android Framework / JDK       │
└─────────────────────────────────────┘
```

## 学习任务

完成以下练习，深入理解工具类的设计思想：

### 初级任务

1. **字符串扩展练习**
   - 打开 [StringExtensions.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\StringExtensions.kt)
   - 找出所有以 `is` 开头的扩展函数，总结它们的命名规律
   - 分析 `splitNotBlank` 和 Kotlin 标准库的 `split` 有什么区别

2. **文件工具练习**
   - 打开 [FileUtils.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\FileUtils.kt)
   - 实现一个新功能：按文件大小排序显示目录列表
   - 注意 `@SortType` 注解的作用

3. **Context 扩展练习**
   - 打开 [ContextExtensions.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\ContextExtensions.kt)
   - 理解 `startActivityForBook` 如何根据书籍类型选择阅读界面
   - 添加一个新扩展函数 `inline fun <reified A : Activity> Context.startActivityWithResult(...)`

### 中级任务

4. **编码检测优化**
   - 打开 [EncodingDetect.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\EncodingDetect.kt)
   - 分析现有检测策略的局限性
   - 思考：如何添加对 Big5 繁体中文的支持？

5. **Flow 并行处理**
   - 打开 [FlowExtensions.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\FlowExtensions.kt)
   - 实现一个带错误收集的 `mapParallelWithErrors` 扩展
   - 注意错误处理和协程取消的平衡

6. **SAF 集成**
   - 打开 [DocumentUtils.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\DocumentUtils.kt)
   - 实现 `listFiles` 方法，列出目录下的所有文件
   - 支持按文件类型过滤

### 高级任务

7. **正则表达式优化**
   - 打开 [HtmlFormatter.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\HtmlFormatter.kt)
   - 分析当前正则的性能瓶颈
   - 设计一个基准测试，比较不同正则实现的性能

8. **BitSet 优化分析**
   - 打开 [NetworkUtils.kt](file:///f:\2025python\legado-with-MD3\app\src\main\java\io\legado\app\utils\NetworkUtils.kt)
   - 编写一个微基准测试，比较 BitSet 和区间判断的性能差异
   - 分析在什么场景下 BitSet 的优化效果最明显

9. **工具类设计实践**
   - 设计一个通用的缓存工具类，支持内存缓存和磁盘缓存
   - 参考项目中已有的缓存实现（如 ACache）
   - 思考：如何保证线程安全？如何控制缓存大小？

## 延伸阅读

- [Kotlin 扩展函数文档](https://kotlinlang.org/docs/extensions.html)
- [Kotlin 标准库源码](https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/coroutines/)
- [Jsoup 选择器文档](https://jsoup.org/cookbook/extracting-data/selectors)
- [Android 存储访问框架](https://developer.android.com/guide/topics/providers/document-based)
- [ICU4J 字符编码检测](https://icu4j.sourceforge.net/userguide/legacy/char-detection/)
