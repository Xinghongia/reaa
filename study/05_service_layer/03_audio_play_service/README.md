# AudioPlayService - 音频播放服务

## 概述

`AudioPlayService` 是音频播放服务，支持播放音乐、电台等音频内容，基于 ExoPlayer + MediaSession 实现。

**源码位置**：`app/src/main/java/io/legado/app/service/AudioPlayService.kt`

## 核心技术栈

| 组件 | 作用 |
|------|------|
| ExoPlayer | 音视频播放引擎 |
| MediaSessionCompat | 媒体会话管理 |
| AudioManager | 音频焦点管理 |
| WakeLock | 防止 CPU 休眠 |
| WiFiLock | 防止 WiFi 休眠 |

## 核心设计

### 1. ExoPlayer 初始化

```kotlin
private val exoPlayer: ExoPlayer by lazy {
    ExoPlayerHelper.createHttpExoPlayer(this)
}
```

### 2. MediaSession 管理

```kotlin
private val mediaSessionCompat: MediaSessionCompat by lazy {
    MediaSessionCompat(this, "readAloud")
}
```

### 3. 音频焦点处理

```kotlin
class AudioPlayService : BaseService(),
    AudioManager.OnAudioFocusChangeListener {

    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) resume()
                exoPlayer.volume = 1.0f
            }
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                needResumeOnAudioFocusGain = true
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                exoPlayer.volume = 0.3f
            }
        }
    }
}
```

### 4. 播放状态管理

```kotlin
companion object {
    @JvmStatic
    var isRun = false
        private set

    @JvmStatic
    var pause = true
        private set

    var url: String = ""
        private set
}
```

## Intent Action 处理

```kotlin
when (action) {
    IntentAction.play -> {
        exoPlayer.stop()
        pause = false
        position = AudioPlay.book?.durChapterPos ?: 0
        url = AudioPlay.durPlayUrl
        play()
    }
    IntentAction.playNew -> {
        exoPlayer.stop()
        pause = false
        position = 0
        url = AudioPlay.durPlayUrl
        play()
    }
    IntentAction.pause -> pause()
    IntentAction.resume -> resume()
    IntentAction.prev -> AudioPlay.prev()
    IntentAction.next -> AudioPlay.next()
    IntentAction.adjustSpeed -> upSpeed(intent.getFloatExtra("adjust", 1f))
    IntentAction.addTimer -> addTimer()
    IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
    IntentAction.stop -> stopSelf()
}
```

## 播放控制流程

### 播放

```kotlin
private fun play() {
    if (!requestFocus()) return

    val mediaItem = MediaItem.fromUri(url)
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()
    exoPlayer.seekTo(position)
    exoPlayer.play()
}
```

### 暂停/恢复

```kotlin
private fun pause() {
    exoPlayer.pause()
    pause = true
}

private fun resume() {
    if (!requestFocus()) return
    exoPlayer.play()
    pause = false
}
```

### 进度保存

```kotlin
private fun savePlayProgress() {
    upPlayProgressJob = lifecycleScope.launch {
        while (isActive) {
            AudioPlay.book?.let { book ->
                book.durChapterPos = exoPlayer.currentPosition.toInt()
                book.update()
            }
            delay(5000)  // 每 5 秒保存一次
        }
    }
}
```

## MediaSession 通知栏控制

```kotlin
private fun upMediaSessionPlaybackState(state: Int) {
    val playbackState = PlaybackStateCompat.Builder()
        .setActions(MEDIA_SESSION_ACTIONS)
        .setState(state, position, 1f)
        .build()
    mediaSessionCompat.setPlaybackState(playbackState)
}

private fun upMediaMetadata() {
    val metadata = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, AudioPlay.book?.name)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, AudioPlay.book?.author)
        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
        .build()
    mediaSessionCompat.setMetadata(metadata)
}
```

## 广播接收器

```kotlin
private val broadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
            pause()  // 耳机拔出时暂停
        }
    }
}
```

## 生命周期管理

```kotlin
override fun onDestroy() {
    super.onDestroy()
    if (useWakeLock) {
        wakeLock.release()
        wifiLock?.release()
    }
    isRun = false
    abandonFocus()
    exoPlayer.release()
    mediaSessionCompat?.release()
    unregisterReceiver(broadcastReceiver)
}
```

## 数据流图

```
用户点击播放 → AudioPlayService
    ↓
requestFocus() 请求音频焦点
    ↓
ExoPlayer.setMediaItem() + prepare()
    ↓
MediaSession.setPlaybackState() 更新通知栏
    ↓
exoPlayer.play()
    ↓
while(isActive) 保存播放进度
```

## AudioPlay 状态机

```
IDLE → PLAYING ↔ PAUSED → STOPPED
         ↑_________|
```

## 学习任务

1. **打开源码文件**：`service/AudioPlayService.kt`
2. **理解音频焦点的 4 种状态**
3. **分析 MediaSession 的作用**
4. **思考**：为什么播放进度要定期保存而不是实时保存？

## 设计亮点

1. **音频焦点管理**：完整的焦点获取、失去、恢复逻辑
2. **MediaSession**：支持通知栏控制、耳机线控
3. **WakeLock + WiFiLock**：确保后台播放不被系统杀死
4. **播放速度调整**：支持 0.5x ~ 2.0x 变速
5. **定时功能**：支持睡眠定时
