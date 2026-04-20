# ReadAloud Service - 朗读服务

## 概述

朗读服务包含一个抽象基类 `BaseReadAloudService` 和两个具体实现：
- `TTSReadAloudService` - 本地 TextToSpeech 朗读
- `HttpReadAloudService` - 在线 HTTP TTS 朗读

通过 `ReadAloud` 单例统一控制。

**源码位置**：
- `service/BaseReadAloudService.kt` - 抽象基类
- `service/TTSReadAloudService.kt` - 本地 TTS 实现
- `service/HttpReadAloudService.kt` - 在线 TTS 实现
- `model/ReadAloud.kt` - 统一入口

## 架构设计

### 继承层次

```
BaseReadAloudService (抽象基类)
├── TTSReadAloudService  (本地 TTS)
└── HttpReadAloudService (在线 HTTP TTS)
```

### ReadAloud 入口

```kotlin
object ReadAloud {
    private var aloudClass: Class<*> = getReadAloudClass()

    private fun getReadAloudClass(): Class<*> {
        val ttsEngine = ttsEngine
        if (ttsEngine.isNullOrBlank()) {
            return TTSReadAloudService::class.java
        }
        if (StringUtils.isNumeric(ttsEngine)) {
            httpTTS = appDb.httpTTSDao.get(ttsEngine.toLong())
            if (httpTTS != null) {
                return HttpReadAloudService::class.java
            }
        }
        return TTSReadAloudService::class.java
    }

    fun play(context: Context, play: Boolean = true, ...) {
        val intent = Intent(context, aloudClass)
        intent.action = IntentAction.play
        context.startForegroundServiceCompat(intent)
    }
}
```

## BaseReadAloudService 抽象基类

### 核心组件

```kotlin
abstract class BaseReadAloudService : BaseService(),
    AudioManager.OnAudioFocusChangeListener {

    internal var contentList = emptyList<String>()  // 朗读内容列表
    internal var nowSpeak: Int = 0                    // 当前朗读位置
    internal var readAloudNumber: Int = 0            // 已读字数
    internal var textChapter: TextChapter? = null     // 当前章节
    internal var pageIndex = 0
    internal var paragraphStartPos = 0               // 段落起始位置
}
```

### 音频焦点管理

与 AudioPlayService 相同的音频焦点处理逻辑。

### 电话中断处理

```kotlin
private val phoneStateListener by lazy {
    ReadAloudPhoneStateListener()
}

private fun initPhoneStateListener() {
    if (appCtx.getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, true)) {
        telephonyManager.listen(phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE)
    }
}

private inner class ReadAloudPhoneStateListener : PhoneStateListener() {
    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                needResumeOnCallStateIdle = !pause
                pauseReadAloud()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (needResumeOnCallStateIdle) {
                    resume()
                    needResumeOnCallStateIdle = false
                }
            }
        }
    }
}
```

## TTSReadAloudService - 本地朗读

### 初始化

```kotlin
private var textToSpeech: TextToSpeech? = null
private var ttsInitFinish = false

override fun onCreate() {
    super.onCreate()
    initTts()
}

private fun initTts() {
    val engine = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value
    textToSpeech = if (engine.isNullOrBlank()) {
        TextToSpeech(this, this)
    } else {
        TextToSpeech(this, this, engine)  // 指定 TTS 引擎
    }
}

override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
        textToSpeech?.setOnUtteranceProgressListener(ttsUtteranceListener)
        ttsInitFinish = true
        play()
    }
}
```

### 朗读实现

```kotlin
override fun play() {
    if (!ttsInitFinish) return
    if (!requestFocus()) return
    if (contentList.isEmpty()) {
        ReadBook.readAloud()  // 获取内容
        return
    }

    execute {
        for (i in nowSpeak until contentList.size) {
            ensureActive()
            var text = contentList[i]
            if (paragraphStartPos > 0 && i == nowSpeak) {
                text = text.substring(paragraphStartPos)
            }

            if (text.matches(AppPattern.notReadAloudRegex)) {
                continue  // 跳过不需要朗读的内容
            }

            val result = tts.speak(
                text,
                if (i == nowSpeak) TextToSpeech.QUEUE_FLUSH
                else TextToSpeech.QUEUE_ADD,
                null,
                AppConst.APP_TAG + i
            )
        }
    }
}
```

### 朗读监听器

```kotlin
private inner class TTSUtteranceListener : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) { }

    override fun onDone(utteranceId: String?) {
        updateNextPos()
        if (nowSpeak >= contentList.size) {
            nextChapter()
        }
    }

    override fun onError(utteranceId: String?) {
        // 重试或跳过
    }
}
```

## HttpReadAloudService - 在线朗读

### 与 TTSReadAloudService 的区别

| 维度 | TTSReadAloudService | HttpReadAloudService |
|------|---------------------|---------------------|
| 语音来源 | 本地 TTS 引擎 | HTTP API |
| 音质 | 取决于系统 | 可配置（高清音质） |
| 离线 | 支持 | 不支持 |
| 速度 | 依赖引擎 | 可精确控制 |

### 缓存机制

```kotlin
private val cache by lazy {
    SimpleCache(
        File(baseDir, "httpTTS_cache"),
        LeastRecentlyUsedCacheEvictor(128 * 1024 * 1024),  // 128MB 缓存
        StandaloneDatabaseProvider(appCtx)
    )
}

private val cacheDataSinkFactory by lazy {
    CacheDataSink.Factory().setCache(cache)
}
```

### 流式下载播放

```kotlin
private fun downloadAndPlayAudiosStream() {
    lifecycleScope.launch {
        for (i in nowSpeak until contentList.size) {
            ensureActive()
            val audioUrl = getAudioUrl(contentList[i])

            // 边下载边播放
            val mediaItem = MediaItem.fromUri(audioUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()

            // 等待这一段播放完成
            while (exoPlayer.currentPosition < exoPlayer.duration) {
                delay(100)
            }

            updateNextPos()
        }
        nextChapter()
    }
}
```

## 朗读控制

### 播放控制

```kotlin
fun play(context: Context, play: Boolean = true, pageIndex: Int, startPos: Int)
fun pause(context: Context)
fun resume(context: Context)
fun stop(context: Context)
```

### 段落控制

```kotlin
fun prevParagraph(context: Context)  // 上一段
fun nextParagraph(context: Context)  // 下一段
```

### 定时控制

```kotlin
fun setTimer(context: Context, minute: Int)  // 睡眠定时
```

## 状态流转

```
用户点击朗读
    ↓
ReadAloud.play()
    ↓
判断使用哪种 TTS
    ↓
┌─────────────────┬───────────────────┐
│ TTSService      │ HttpTTSService    │
├─────────────────┼───────────────────┤
│ TextToSpeech    │ ExoPlayer + Cache │
│ .speak()        │ 流式播放           │
└─────────────────┴───────────────────┘
    ↓
朗读完成 → nextChapter() → 加载下一章
    ↓
全部完成 → playStop()
```

## 与 AudioPlayService 对比

| 维度 | AudioPlayService | ReadAloudService |
|------|------------------|-------------------|
| 内容 | 音频文件 URL | 文字内容 |
| 来源 | 现有音频 | 实时转换 |
| 交互 | 播放/暂停/进度拖动 | 朗读/暂停/段落跳转 |
| 焦点 | 音频焦点 | 音频焦点 + 电话中断 |

## 学习任务

1. **打开源码文件**：
   - `service/BaseReadAloudService.kt`
   - `service/TTSReadAloudService.kt`
   - `service/HttpReadAloudService.kt`
   - `model/ReadAloud.kt`

2. **理解朗读流程**：
   - 如何获取朗读内容
   - 如何分段落朗读
   - 如何处理朗读完成事件

3. **思考**：
   - 为什么需要 `QUEUE_FLUSH` 和 `QUEUE_ADD`？
   - HttpReadAloudService 为什么要缓存音频？

## 设计亮点

1. **策略模式**：通过 `ReadAloud` 入口动态选择 TTS 实现
2. **模板方法**：BaseReadAloudService 定义朗读骨架，子类实现具体逻辑
3. **电话中断处理**：朗读时来电自动暂停，通话结束后恢复
4. **音频焦点统一管理**：与 AudioPlayService 共用焦点处理逻辑
5. **缓存优化**：HttpReadAloudService 使用 LRU 缓存减少重复请求
