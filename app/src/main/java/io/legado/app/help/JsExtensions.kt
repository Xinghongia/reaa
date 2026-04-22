package io.legado.app.help

import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import androidx.annotation.Keep
import androidx.core.net.toUri
import cn.hutool.core.codec.Base64
import cn.hutool.core.util.HexUtil
import com.script.rhino.rhinoContext
import com.script.rhino.rhinoContextOrNull
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppConst.dateFormat
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.BaseSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.OldThemeConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.http.BackstageWebView
import io.legado.app.help.http.CookieManager.cookieJarHeader
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.SSLHelper
import io.legado.app.help.http.StrResponse
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.help.source.getSourceType
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.QueryTTF
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.JsURL
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.compress.LibArchiveUtils
import io.legado.app.utils.createFileReplace
import io.legado.app.utils.externalCache
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isMainThread
import io.legado.app.utils.longToastForJs
import io.legado.app.utils.mapAsync
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.startActivity
import io.legado.app.utils.toStringArray
import io.legado.app.utils.toastForJs
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okio.use
import org.jsoup.Connection
import org.jsoup.Jsoup
import splitties.init.appCtx
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * js扩展类, 在js中通过java变量调用
 * 添加方法，请更新文档/legado/app/src/main/assets/help/JsHelp.md
 * 所有对于文件的读写删操作都是相对路径,只能操作阅读缓存内的文件
 * /android/data/{package}/cache/...
 */
@Keep
@Suppress("unused")
interface JsExtensions : JsEncodeUtils {

    /**
     * 获取当前书源对象
     * @return BaseSource? 当前激活的书源对象，包含书源配置信息
     */
    fun getSource(): BaseSource?

    /**
     * 获取当前书源的标签/名称
     * @return String? 书源标签名称，用于日志和调试显示
     */
    fun getTag(): String?

    /**
     * 获取协程上下文，用于处理异步任务
     * @return CoroutineContext 协程上下文，如果Rhino上下文不可用则返回空上下文
     */
    private val context: CoroutineContext
        get() = rhinoContextOrNull?.coroutineContext ?: EmptyCoroutineContext

    /**
     * 访问网络,返回String（简化版，使用默认超时时间）
     * @param url 目标URL，支持String或List类型（List时取第一个元素）
     * @return String? 请求返回的响应体字符串，发生错误时返回异常堆栈信息
     */
    fun ajax(url: Any): String? {
        return ajax(url, null)
    }

    /**
     * 访问网络,返回String（完整版）
     * @param url 目标URL，支持String或List类型（List时取第一个元素）
     * @param callTimeout 自定义超时时间（毫秒），传null使用默认配置
     * @return String? 请求返回的响应体字符串，发生错误时返回异常堆栈信息
     */
    fun ajax(url: Any, callTimeout: Long?): String? {
        val urlStr = if (url is List<*>) {
            url.firstOrNull().toString()
        } else {
            url.toString()
        }
        val analyzeUrl = AnalyzeUrl(
            urlStr,
            source = getSource(),
            callTimeout = callTimeout,
            coroutineContext = context
        )
        return kotlin.runCatching {
            analyzeUrl.getStrResponse().body
        }.onFailure {
            rhinoContextOrNull?.ensureActive()
            AppLog.put("ajax(${urlStr}) error\n${it.localizedMessage}", it)
        }.getOrElse {
            it.stackTraceStr
        }
    }

    /**
     * 并发访问多个网络请求（简化版）
     * @param urlList URL数组，所有URL会同时发起请求
     * @return Array<StrResponse> 每个URL对应的响应结果数组
     */
    fun ajaxAll(urlList: Array<String>): Array<StrResponse> {
        return ajaxAll(urlList, false)
    }

    /**
     * 并发访问多个网络请求（完整版）
     * @param urlList URL数组，所有URL会同时发起请求
     * @param skipRateLimit 是否跳过限流控制，默认false（启用限流）
     * @return Array<StrResponse> 每个URL对应的响应结果数组
     */
    fun ajaxAll(urlList: Array<String>, skipRateLimit: Boolean): Array<StrResponse> {
        return runBlocking(context) {
            urlList.asFlow().mapAsync(AppConfig.threadCount) { url ->
                val analyzeUrl = AnalyzeUrl(
                    url,
                    source = getSource(),
                    coroutineContext = coroutineContext
                )
                analyzeUrl.getStrResponseAwait(skipRateLimit = skipRateLimit)
            }.flowOn(IO).toList().toTypedArray()
        }
    }

    /**
     * 并发测试网络延迟（简化版）
     * @param urlList URL数组
     * @param timeout 超时时间（毫秒）
     * @return Array<StrResponse> 每个URL的测试响应结果
     */
    fun ajaxTestAll(urlList: Array<String>, timeout: Int): Array<StrResponse> {
        return ajaxTestAll(urlList, timeout, false)
    }

    /**
     * 并发测试网络延迟（完整版）
     * @param urlList URL数组
     * @param timeout 超时时间（毫秒）
     * @param skipRateLimit 是否跳过限流控制
     * @return Array<StrResponse> 每个URL的测试响应结果，包含响应时间等信息
     */
    fun ajaxTestAll(
        urlList: Array<String>,
        timeout: Int,
        skipRateLimit: Boolean
    ): Array<StrResponse> {
        return runBlocking(context) {
            urlList.asFlow().mapAsync(AppConfig.threadCount) { url ->
                val analyzeUrl = AnalyzeUrl(
                    url,
                    source = getSource(),
                    coroutineContext = coroutineContext,
                    callTimeout = timeout.toLong()
                )
                analyzeUrl.getStrResponseAwait(isTest = true, skipRateLimit = skipRateLimit)
            }.flowOn(IO).toList().toTypedArray()
        }
    }


    /**
     * 访问网络,返回完整响应对象（简化版）
     * @param urlStr 目标URL
     * @return StrResponse 包含url、body、code、headers、error等信息的响应对象
     */
    fun connect(urlStr: String): StrResponse {
        val analyzeUrl = AnalyzeUrl(
            urlStr,
            source = getSource(),
            coroutineContext = context
        )
        return kotlin.runCatching {
            analyzeUrl.getStrResponse()
        }.onFailure {
            rhinoContextOrNull?.ensureActive()
            AppLog.put("connect(${urlStr}) error\n${it.localizedMessage}", it)
        }.getOrElse {
            StrResponse(analyzeUrl.url, it.stackTraceStr)
        }
    }

    /**
     * 访问网络,返回完整响应对象（双参数版）
     * @param urlStr 目标URL
     * @param header JSON格式的请求头字符串，如 '{"Referer":"https://example.com"}'
     * @return StrResponse 包含url、body、code、headers、error等信息的响应对象
     */
    fun connect(urlStr: String, header: String?): StrResponse {
        return connect(urlStr, header, null)
    }

    /**
     * 访问网络,返回完整响应对象（完整版）
     * @param urlStr 目标URL
     * @param header JSON格式的请求头字符串，如 '{"Referer":"https://example.com"}'
     * @param callTimeout 自定义超时时间（毫秒），传null使用默认配置
     * @return StrResponse 包含以下属性的响应对象：
     *   - url: 最终请求的URL（可能因重定向而改变）
     *   - body: 响应体字符串
     *   - code: HTTP状态码
     *   - headers: 响应头Map
     *   - error: 错误信息（如果有）
     */
    fun connect(urlStr: String, header: String?, callTimeout: Long?): StrResponse {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(header).getOrNull()
        val analyzeUrl = AnalyzeUrl(
            urlStr,
            headerMapF = headerMap,
            source = getSource(),
            callTimeout = callTimeout,
            coroutineContext = context
        )
        return kotlin.runCatching {
            analyzeUrl.getStrResponse()
        }.onFailure {
            rhinoContextOrNull?.ensureActive()
            AppLog.put("connect($urlStr,$header) error\n${it.localizedMessage}", it)
        }.getOrElse {
            StrResponse(analyzeUrl.url, it.stackTraceStr)
        }
    }

    /**
     * 使用webView访问网络（简化版）
     * @param html 直接用webView载入的html内容，如果为空则直接访问url
     * @param url 页面URL，用于解析相对路径资源
     * @param js 用来获取返回值的js语句，没有就返回整个源代码
     * @return String? js执行结果或页面源代码
     */
    fun webView(html: String?, url: String?, js: String?): String? {
        return webView(html, url, js, false)
    }

    /**
     * 使用webView访问网络（完整版）
     * @param html 直接用webView载入的html内容，如果为空则直接访问url
     * @param url 页面URL，用于解析相对路径资源
     * @param js 用来获取返回值的js语句，没有就返回整个源代码
     * @param cacheFirst 优先使用缓存，true能提高访问速度
     * @return String? js执行结果或页面源代码
     * @note 此方法必须在后台线程调用，不能在主线程调用
     */
    fun webView(html: String?, url: String?, js: String?, cacheFirst: Boolean): String? {
        if (isMainThread) {
            error("webView must be called on a background thread")
        }
        return runBlocking(context) {
            BackstageWebView(
                url = url,
                html = html,
                javaScript = js,
                headerMap = getSource()?.getHeaderMap(true),
                tag = getSource()?.getKey()
            ).getStrResponse().body
        }
    }

    /**
     * 使用webView获取资源url（双参数版）
     * @param html 直接用webView载入的html内容
     * @param url 页面URL
     * @param js 用来获取返回值的js语句
     * @param sourceRegex 用于匹配资源URL的正则表达式
     * @return String? 匹配到的资源URL
     */
    fun webViewGetSource(html: String?, url: String?, js: String?, sourceRegex: String): String? {
        return webViewGetSource(html, url, js, sourceRegex, false, 0)
    }

    /**
     * 使用webView获取资源url（三参数版）
     * @param html 直接用webView载入的html内容
     * @param url 页面URL
     * @param js 用来获取返回值的js语句
     * @param sourceRegex 用于匹配资源URL的正则表达式
     * @param cacheFirst 优先使用缓存
     * @return String? 匹配到的资源URL
     */
    fun webViewGetSource(
        html: String?,
        url: String?,
        js: String?,
        sourceRegex: String,
        cacheFirst: Boolean
    ): String? {
        return webViewGetSource(html, url, js, sourceRegex, cacheFirst, 0)
    }

    /**
     * 使用webView获取资源url（完整版）
     * @param html 直接用webView载入的html内容
     * @param url 页面URL
     * @param js 用来获取返回值的js语句
     * @param sourceRegex 用于匹配资源URL的正则表达式
     * @param cacheFirst 优先使用缓存
     * @param delayTime 延迟时间（毫秒），等待js执行后再获取资源
     * @return String? 匹配到的资源URL，如果没有匹配到返回null
     * @note 用于获取页面中通过js动态加载的资源URL，如图片、视频等
     */
    fun webViewGetSource(
        html: String?,
        url: String?,
        js: String?,
        sourceRegex: String,
        cacheFirst: Boolean,
        delayTime: Long
    ): String? {
        if (isMainThread) {
            error("webViewGetSource must be called on a background thread")
        }
        return runBlocking(context) {
            BackstageWebView(
                url = url,
                html = html,
                javaScript = js,
                headerMap = getSource()?.getHeaderMap(true),
                tag = getSource()?.getKey(),
                sourceRegex = sourceRegex,
                delayTime = delayTime
            ).getStrResponse().body
        }
    }

    /**
     * 使用webView获取跳转url（双参数版）
     * @param html 直接用webView载入的html内容
     * @param url 页面URL
     * @param js 用来触发跳转的js语句
     * @param overrideUrlRegex 用于匹配跳转后URL的正则表达式
     * @return String? 匹配到的跳转URL
     */
    fun webViewGetOverrideUrl(
        html: String?,
        url: String?,
        js: String?,
        overrideUrlRegex: String
    ): String? {
        return webViewGetOverrideUrl(html, url, js, overrideUrlRegex, false, 0)
    }

    /**
     * 使用webView获取跳转url（三参数版）
     * @param html 直接用webView载入的html内容
     * @param url 页面URL
     * @param js 用来触发跳转的js语句
     * @param overrideUrlRegex 用于匹配跳转后URL的正则表达式
     * @param cacheFirst 优先使用缓存
     * @return String? 匹配到的跳转URL
     */
    fun webViewGetOverrideUrl(
        html: String?,
        url: String?,
        js: String?,
        overrideUrlRegex: String,
        cacheFirst: Boolean
    ): String? {
        return webViewGetOverrideUrl(html, url, js, overrideUrlRegex, cacheFirst, 0)
    }

    /**
     * 使用webView获取跳转url（完整版）
     * @param html 直接用webView载入的html内容
     * @param url 页面URL
     * @param js 用来触发跳转的js语句
     * @param overrideUrlRegex 用于匹配跳转后URL的正则表达式
     * @param cacheFirst 优先使用缓存
     * @param delayTime 延迟时间（毫秒），等待js执行后再获取跳转URL
     * @return String? 匹配到的跳转URL，如果没有匹配到返回null
     * @note 用于获取页面跳转后的URL，如登录后重定向、授权回调等场景
     */
    fun webViewGetOverrideUrl(
        html: String?,
        url: String?,
        js: String?,
        overrideUrlRegex: String,
        cacheFirst: Boolean,
        delayTime: Long
    ): String? {
        if (isMainThread) {
            error("webViewGetOverrideUrl must be called on a background thread")
        }
        return runBlocking(context) {
            BackstageWebView(
                url = url,
                html = html,
                javaScript = js,
                headerMap = getSource()?.getHeaderMap(true),
                tag = getSource()?.getKey(),
                overrideUrlRegex = overrideUrlRegex,
                delayTime = delayTime
            ).getStrResponse().body
        }
    }

    /**
     * 使用内置浏览器打开链接，手动验证网站防爬（简化版）
     * @param url 要打开的链接
     * @param title 浏览器页面的标题，用于显示
     * @note 会阻塞等待用户完成验证
     */
    fun startBrowser(url: String, title: String) {
        return startBrowser(url, title, null)
    }

    /**
     * 使用内置浏览器打开链接，手动验证网站防爬（完整版）
     * @param url 要打开的链接
     * @param title 浏览器页面的标题，用于显示
     * @param html 可选的html内容，如果提供则直接加载html而不访问url
     * @note 用于需要人工验证的场景，如验证码、拼图等
     * @note 验证完成后cookie会自动保存
     */
    fun startBrowser(url: String, title: String, html: String?) {
        rhinoContext.ensureActive()
        SourceVerificationHelp.startBrowser(getSource(), url, title, html = html)
    }

    /**
     * 使用内置浏览器打开链接，并等待网页结果（简化版）
     * @param url 要打开的链接
     * @param title 浏览器页面的标题
     * @return StrResponse 包含最终URL和页面内容
     */
    fun startBrowserAwait(url: String, title: String): StrResponse {
        return startBrowserAwait(url, title, true, null)
    }

    /**
     * 使用内置浏览器打开链接，并等待网页结果（双参数版）
     * @param url 要打开的链接
     * @param title 浏览器页面的标题
     * @param refetchAfterSuccess 验证成功后是否重新获取内容
     * @return StrResponse 包含最终URL和页面内容
     */
    fun startBrowserAwait(url: String, title: String, refetchAfterSuccess: Boolean): StrResponse {
        return startBrowserAwait(url, title, refetchAfterSuccess, null)
    }

    /**
     * 使用内置浏览器打开链接，并等待网页结果（完整版）
     * @param url 要打开的链接
     * @param title 浏览器页面的标题
     * @param refetchAfterSuccess 验证成功后是否重新获取内容
     * @param html 可选的html内容
     * @return StrResponse 包含以下属性：
     *   - url: 最终URL（可能是跳转后的URL）
     *   - body: 页面内容
     */
    fun startBrowserAwait(
        url: String,
        title: String,
        refetchAfterSuccess: Boolean,
        html: String?
    ): StrResponse {
        rhinoContext.ensureActive()
        val pair = SourceVerificationHelp.getVerificationResult(
            getSource(), url, title, true, refetchAfterSuccess, html
        )
        val (url2, body) = pair
        return StrResponse(url2.ifEmpty { url }, body)
    }

    /**
     * 打开图片验证码对话框，等待返回验证结果
     * @param imageUrl 验证码图片的URL
     * @return String 用户输入的验证码
     * @note 会弹出一个对话框显示验证码图片，用户输入后返回
     */
    fun getVerificationCode(imageUrl: String): String {
        rhinoContext.ensureActive()
        return SourceVerificationHelp.getVerificationResult(getSource(), imageUrl, "", false).second
    }

    /**
     * 导入JavaScript脚本（简化版）
     * @param path 脚本路径，支持网络URL或本地文件路径
     * @return String 脚本内容
     * @note 如果是网络脚本会先下载缓存到本地
     * @note 本地路径相对于阅读缓存目录
     * @throws NoStackTraceException 如果脚本内容获取失败或为空
     */
    @JavascriptInterface
    fun importScript(path: String): String {
        val result = when {
            path.startsWith("http") -> cacheFile(path)
            else -> readTxtFile(path)
        }
        if (result.isBlank()) throw NoStackTraceException("$path 内容获取失败或者为空")
        return result
    }

    /**
     * 缓存以文本方式保存的文件，如.js、.txt等（简化版，默认永不过期）
     * @param urlStr 网络文件的链接
     * @return String 缓存后的文件内容
     */
    @JavascriptInterface
    fun cacheFile(urlStr: String): String {
        return cacheFile(urlStr, 0)
    }

    /**
     * 缓存以文本方式保存的文件，如.js、.txt等（完整版）
     * @param urlStr 网络文件的链接
     * @param saveTime 缓存时间，单位：秒。传0表示永不过期
     * @return String 缓存后的文件内容
     * @note 首次调用会下载文件并缓存，后续调用直接读缓存
     */
    @JavascriptInterface
    fun cacheFile(urlStr: String, saveTime: Int): String {
        val key = md5Encode16(urlStr)
        val cachePath = CacheManager.get(key)
        return if (
            cachePath.isNullOrBlank() ||
            !getFile(cachePath).exists()
        ) {
            val path = downloadFile(urlStr)
            log("首次下载 $urlStr >> $path")
            CacheManager.put(key, path, saveTime)
            readTxtFile(path)
        } else {
            readTxtFile(cachePath)
        }
    }

    /**
     * 获取Cookie（简化版，获取所有cookie）
     * @param tag 域名标签，用于标识哪个网站的cookie
     * @return String 该域名下的所有cookie，格式为 "key1=value1; key2=value2"
     */
    @JavascriptInterface
    fun getCookie(tag: String): String {
        return getCookie(tag, null)
    }

    /**
     * 获取Cookie（完整版）
     * @param tag 域名标签，用于标识哪个网站的cookie
     * @param key 要获取的cookie名称，传null则返回所有cookie
     * @return String 如果key不为null，返回该cookie的值；如果key为null，返回所有cookie字符串
     */
    @JavascriptInterface
    fun getCookie(tag: String, key: String?): String {
        return if (key != null) {
            CookieStore.getKey(tag, key)
        } else {
            CookieStore.getCookie(tag)
        }
    }

    /**
     * 下载文件到缓存目录
     * @param url 下载地址，支持带type参数指定文件类型
     * @return String 下载后的文件相对路径（相对于缓存目录）
     * @note 下载会使用书源的header和cookie
     * @note 文件名自动MD5加密，扩展名从URL或type参数获取
     */
    @JavascriptInterface
    fun downloadFile(url: String): String {
        rhinoContextOrNull?.ensureActive()
        val analyzeUrl = AnalyzeUrl(url, source = getSource(), coroutineContext = context)
        val type = analyzeUrl.type ?: UrlUtil.getSuffix(url)
        val path = FileUtils.getPath(
            File(FileUtils.getCachePath()),
            "${MD5Utils.md5Encode16(url)}.${type}"
        )
        val file = File(path)
        file.delete()
        analyzeUrl.getInputStream().use { iStream ->
            file.createFileReplace()
            try {
                file.outputStream().buffered().use { oStream ->
                    iStream.copyTo(oStream)
                }
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
        }
        return path.substring(FileUtils.getCachePath().length)
    }


    /**
     * 将16进制字符串转换为文件（已废弃）
     * @param content 需要转成文件的16进制字符串
     * @param url 通过url里的参数来判断文件类型
     * @return String 相对路径
     * @deprecated 请使用 downloadFile(url) 替代
     */
    @Deprecated(
        "Deprecated",
        ReplaceWith("downloadFile(url)")
    )
    @JavascriptInterface
    fun downloadFile(content: String, url: String): String {
        rhinoContextOrNull?.ensureActive()
        val type = AnalyzeUrl(url, source = getSource(), coroutineContext = context).type
            ?: return ""
        val path = FileUtils.getPath(
            FileUtils.createFolderIfNotExist(FileUtils.getCachePath()),
            "${MD5Utils.md5Encode16(url)}.${type}"
        )
        val file = File(path)
        file.createFileReplace()
        HexUtil.decodeHex(content).let {
            if (it.isNotEmpty()) {
                file.writeBytes(it)
            }
        }
        return path.substring(FileUtils.getCachePath().length)
    }

    /**
     * 发送GET请求，支持自定义headers和重定向拦截（简化版）
     * @param urlStr 目标URL
     * @param headers 请求头Map，如 {"Referer":"https://example.com", "User-Agent":"..."}
     * @return Connection.Response Jsoup封装的响应对象，包含body、statusCode、headers等
     * @note 不会自动跟随重定向，需要手动处理location header
     */
    fun get(urlStr: String, headers: Map<String, String>): Connection.Response {
        return get(urlStr, headers, null)
    }

    /**
     * 发送GET请求，支持自定义headers和重定向拦截（完整版）
     * @param urlStr 目标URL
     * @param headers 请求头Map，如 {"Referer":"https://example.com", "User-Agent":"..."}
     * @param timeout 超时时间（毫秒），传null使用默认30000ms
     * @return Connection.Response Jsoup封装的响应对象，包含以下方法：
     *   - statusCode(): HTTP状态码
     *   - statusMessage(): 状态消息
     *   - body(): 响应体字符串
     *   - headers(name): 获取指定响应头
     */
    fun get(urlStr: String, headers: Map<String, String>, timeout: Int?): Connection.Response {
        val requestHeaders = if (getSource()?.enabledCookieJar == true) {
            headers.toMutableMap().apply { put(cookieJarHeader, "1") }
        } else headers
        val rateLimiter = ConcurrentRateLimiter(getSource())
        val response = rateLimiter.withLimitBlocking {
            rhinoContextOrNull?.ensureActive()
            Jsoup.connect(urlStr)
                .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory)
                .timeout(timeout ?: 30000)
                .ignoreContentType(true)
                .followRedirects(false)
                .headers(requestHeaders)
                .method(Connection.Method.GET)
                .execute()
        }
        return response
    }

    /**
     * 发送HEAD请求，不返回Response Body，更省流量（简化版）
     * @param urlStr 目标URL
     * @param headers 请求头Map
     * @return Connection.Response Jsoup封装的响应对象
     * @note HEAD请求只返回响应头，不返回body
     */
    fun head(urlStr: String, headers: Map<String, String>): Connection.Response {
        return head(urlStr, headers, null)
    }

    /**
     * 发送HEAD请求，不返回Response Body，更省流量（完整版）
     * @param urlStr 目标URL
     * @param headers 请求头Map
     * @param timeout 超时时间（毫秒），传null使用默认30000ms
     * @return Connection.Response Jsoup封装的响应对象
     * @note HEAD请求只返回响应头，不返回body
     * @note 常用于检查文件是否存在、获取文件大小等
     */
    fun head(urlStr: String, headers: Map<String, String>, timeout: Int?): Connection.Response {
        val requestHeaders = if (getSource()?.enabledCookieJar == true) {
            headers.toMutableMap().apply { put(cookieJarHeader, "1") }
        } else headers
        val rateLimiter = ConcurrentRateLimiter(getSource())
        val response = rateLimiter.withLimitBlocking {
            rhinoContextOrNull?.ensureActive()
            Jsoup.connect(urlStr)
                .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory)
                .timeout(timeout ?: 30000)
                .ignoreContentType(true)
                .followRedirects(false)
                .headers(requestHeaders)
                .method(Connection.Method.HEAD)
                .execute()
        }
        return response
    }

    /**
     * 发送POST请求（简化版）
     * @param urlStr 目标URL
     * @param body 请求体内容
     * @param headers 请求头Map
     * @return Connection.Response Jsoup封装的响应对象
     */
    fun post(urlStr: String, body: String, headers: Map<String, String>): Connection.Response {
        return post(urlStr, body, headers, null)
    }

    /**
     * 发送POST请求（完整版）
     * @param urlStr 目标URL
     * @param body 请求体内容，如 JSON、Form数据等
     * @param headers 请求头Map
     * @param timeout 超时时间（毫秒），传null使用默认30000ms
     * @return Connection.Response Jsoup封装的响应对象
     */
    fun post(
        urlStr: String,
        body: String,
        headers: Map<String, String>,
        timeout: Int?
    ): Connection.Response {
        val requestHeaders = if (getSource()?.enabledCookieJar == true) {
            headers.toMutableMap().apply { put(cookieJarHeader, "1") }
        } else headers
        val rateLimiter = ConcurrentRateLimiter(getSource())
        val response = rateLimiter.withLimitBlocking {
            rhinoContextOrNull?.ensureActive()
            Jsoup.connect(urlStr)
                .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory)
                .timeout(timeout ?: 30000)
                .ignoreContentType(true)
                .followRedirects(false)
                .requestBody(body)
                .headers(requestHeaders)
                .method(Connection.Method.POST)
                .execute()
        }
        return response
    }

    /**
     * 字符串转换为字节数组（UTF-8编码）
     * @param str 源字符串
     * @return ByteArray UTF-8编码的字节数组
     */
    fun strToBytes(str: String): ByteArray {
        return str.toByteArray(charset("UTF-8"))
    }

    /**
     * 字符串转换为字节数组（指定编码）
     * @param str 源字符串
     * @param charset 字符编码名称，如 "GBK", "UTF-8", "ISO-8859-1"
     * @return ByteArray 指定编码的字节数组
     */
    fun strToBytes(str: String, charset: String): ByteArray {
        return str.toByteArray(charset(charset))
    }

    /**
     * 字节数组转换为字符串（UTF-8编码）
     * @param bytes 源字节数组
     * @return String UTF-8解码的字符串
     */
    fun bytesToStr(bytes: ByteArray): String {
        return String(bytes, charset("UTF-8"))
    }

    /**
     * 字节数组转换为字符串（指定编码）
     * @param bytes 源字节数组
     * @param charset 字符编码名称
     * @return String 指定编码解码的字符串
     */
    fun bytesToStr(bytes: ByteArray, charset: String): String {
        return String(bytes, charset(charset))
    }

    /**
     * Base64解码（简化版，UTF-8编码）
     * @param str Base64编码的字符串
     * @return String 解码后的字符串
     */
    @JavascriptInterface
    fun base64Decode(str: String?): String {
        return Base64.decodeStr(str)
    }

    /**
     * Base64解码（指定编码）
     * @param str Base64编码的字符串
     * @param charset 目标字符串编码，如 "UTF-8", "GBK"
     * @return String 解码后的字符串
     */
    @JavascriptInterface
    fun base64Decode(str: String?, charset: String): String {
        return Base64.decodeStr(str, charset(charset))
    }

    /**
     * Base64解码（Android标志位版本）
     * @param str Base64编码的字符串
     * @param flags Android Base64解码标志，如 0, 1, 2, 4 或组合
     * @return String 解码后的字符串
     */
    @JavascriptInterface
    fun base64Decode(str: String, flags: Int): String {
        return EncoderUtils.base64Decode(str, flags)
    }

    /**
     * Base64解码为字节数组（简化版）
     * @param str Base64编码的字符串
     * @return ByteArray? 解码后的字节数组，失败返回null
     */
    fun base64DecodeToByteArray(str: String?): ByteArray? {
        if (str.isNullOrBlank()) {
            return null
        }
        return EncoderUtils.base64DecodeToByteArray(str, 0)
    }

    /**
     * Base64解码为字节数组（完整版）
     * @param str Base64编码的字符串
     * @param flags Android Base64解码标志
     * @return ByteArray? 解码后的字节数组，失败返回null
     */
    fun base64DecodeToByteArray(str: String?, flags: Int): ByteArray? {
        if (str.isNullOrBlank()) {
            return null
        }
        return EncoderUtils.base64DecodeToByteArray(str, flags)
    }

    /**
     * Base64编码（简化版，标准Base64）
     * @param str 原始字符串
     * @return String? Base64编码的字符串
     */
    @JavascriptInterface
    fun base64Encode(str: String): String? {
        return EncoderUtils.base64Encode(str, 2)
    }

    /**
     * Base64编码（完整版）
     * @param str 原始字符串
     * @param flags Android Base64编码标志
     * @return String? Base64编码的字符串
     */
    @JavascriptInterface
    fun base64Encode(str: String, flags: Int): String? {
        return EncoderUtils.base64Encode(str, flags)
    }

    /**
     * 十六进制字符串解码为字节数组
     * @param hex 十六进制字符串，如 "48656c6c6f576f726c64"
     * @return ByteArray? 解码后的字节数组，失败返回null
     */
    fun hexDecodeToByteArray(hex: String): ByteArray? {
        return HexUtil.decodeHex(hex)
    }

    /**
     * 十六进制字符串解码为UTF-8字符串
     * @param hex 十六进制字符串
     * @return String? 解码后的UTF-8字符串，失败返回null
     */
    @JavascriptInterface
    fun hexDecodeToString(hex: String): String? {
        return HexUtil.decodeHexStr(hex)
    }

    /**
     * UTF-8字符串编码为十六进制字符串
     * @param utf8 UTF-8编码的字符串
     * @return String? 十六进制字符串，失败返回null
     */
    @JavascriptInterface
    fun hexEncodeToString(utf8: String): String? {
        return HexUtil.encodeHexStr(utf8)
    }

    /**
     * 格式化UTC时间（指定时区偏移）
     * @param time 时间戳（毫秒）
     * @param format 日期格式，如 "yyyy-MM-dd HH:mm:ss"
     * @param sh 时区偏移小时数，如 8 表示 UTC+8
     * @return String? 格式化后的时间字符串
     */
    @JavascriptInterface
    fun timeFormatUTC(time: Long, format: String, sh: Int): String? {
        val utc = SimpleTimeZone(sh, "UTC")
        return SimpleDateFormat(format, Locale.getDefault()).run {
            timeZone = utc
            format(Date(time))
        }
    }

    /**
     * 格式化本地时间（使用应用默认格式）
     * @param time 时间戳（毫秒）
     * @return String 格式化后的时间字符串，格式为 "yyyy-MM-dd HH:mm:ss"
     */
    @JavascriptInterface
    fun timeFormat(time: Long): String {
        return dateFormat.format(Date(time))
    }

    /**
     * URL编码（UTF-8）
     * @param str 待编码的字符串
     * @return String URL编码后的字符串
     */
    @JavascriptInterface
    fun encodeURI(str: String): String {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * URL编码（指定编码）
     * @param str 待编码的字符串
     * @param enc 字符编码，如 "UTF-8", "GBK"
     * @return String URL编码后的字符串
     */
    @JavascriptInterface
    fun encodeURI(str: String, enc: String): String {
        return try {
            URLEncoder.encode(str, enc)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 格式化HTML，保留图片
     * @param str HTML字符串
     * @return String 格式化后的HTML，图片保持不变
     */
    @JavascriptInterface
    fun htmlFormat(str: String): String {
        return HtmlFormatter.formatKeepImg(str)
    }

    /**
     * 繁体中文转换为简体中文
     * @param text 繁体中文文本
     * @return String 简体中文文本
     */
    @JavascriptInterface
    fun t2s(text: String): String {
        return ChineseUtils.t2s(text)
    }

    /**
     * 简体中文转换为繁体中文
     * @param text 简体中文文本
     * @return String 繁体中文文本
     */
    @JavascriptInterface
    fun s2t(text: String): String {
        return ChineseUtils.s2t(text)
    }

    /**
     * 获取WebView的User-Agent
     * @return String WebView默认的User-Agent字符串
     */
    @JavascriptInterface
    fun getWebViewUA(): String {
        return WebSettings.getDefaultUserAgent(appCtx)
    }

    /**
     * 获取本地文件对象
     * @param path 相对路径（相对于应用缓存目录）
     * @return File 文件对象
     * @throws SecurityException 如果路径试图逃逸到缓存目录之外
     */
    fun getFile(path: String): File {
        val cachePath = appCtx.externalCache.absolutePath
        val aPath = if (path.startsWith(File.separator)) {
            cachePath + path
        } else {
            cachePath + File.separator + path
        }
        val file = File(aPath)
        val safePath = appCtx.externalCache.parent!!
        if (!file.canonicalPath.startsWith(safePath)) {
            throw SecurityException("非法路径")
        }
        return file
    }

    /**
     * 读取文件为字节数组
     * @param path 文件相对路径
     * @return ByteArray? 文件内容字节数组，文件不存在返回null
     */
    fun readFile(path: String): ByteArray? {
        val file = getFile(path)
        if (file.exists()) {
            return file.readBytes()
        }
        return null
    }

    /**
     * 读取文本文件（自动检测编码）
     * @param path 文件相对路径
     * @return String 文件文本内容，文件不存在返回空字符串
     */
    @JavascriptInterface
    fun readTxtFile(path: String): String {
        val file = getFile(path)
        if (file.exists()) {
            val charsetName = EncodingDetect.getEncode(file)
            return String(file.readBytes(), charset(charsetName))
        }
        return ""
    }

    /**
     * 读取文本文件（指定编码）
     * @param path 文件相对路径
     * @param charsetName 字符编码名称，如 "UTF-8", "GBK"
     * @return String 文件文本内容，文件不存在返回空字符串
     */
    @JavascriptInterface
    fun readTxtFile(path: String, charsetName: String): String {
        val file = getFile(path)
        if (file.exists()) {
            return String(file.readBytes(), charset(charsetName))
        }
        return ""
    }

    /**
     * 删除本地文件
     * @param path 文件相对路径
     * @return Boolean 是否删除成功
     */
    @JavascriptInterface
    fun deleteFile(path: String): Boolean {
        val file = getFile(path)
        return FileUtils.delete(file, true)
    }

    /**
     * 解压Zip压缩文件（简化版）
     * @param zipPath 相对路径
     * @return String 解压后的文件夹相对路径
     */
    @JavascriptInterface
    fun unzipFile(zipPath: String): String {
        return unArchiveFile(zipPath)
    }

    /**
     * 解压7Zip压缩文件（简化版）
     * @param zipPath 相对路径
     * @return String 解压后的文件夹相对路径
     */
    @JavascriptInterface
    fun un7zFile(zipPath: String): String {
        return unArchiveFile(zipPath)
    }

    /**
     * 解压Rar压缩文件（简化版）
     * @param zipPath 相对路径
     * @return String 解压后的文件夹相对路径
     */
    @JavascriptInterface
    fun unrarFile(zipPath: String): String {
        return unArchiveFile(zipPath)
    }

    /**
     * 解压压缩文件（支持zip、7z、rar）
     * @param zipPath 相对路径
     * @return String 解压后的文件夹相对路径
     */
    @JavascriptInterface
    fun unArchiveFile(zipPath: String): String {
        if (zipPath.isEmpty()) return ""
        val zipFile = getFile(zipPath)
        return ArchiveUtils.deCompress(zipFile.absolutePath).let {
            ArchiveUtils.TEMP_FOLDER_NAME + File.separator + MD5Utils.md5Encode16(zipFile.name)
        }
    }

    /**
     * 读取文件夹内所有文本文件
     * @param path 文件夹相对路径
     * @return String 所有文件内容用换行符连接
     * @note 读取后会删除该文件夹
     */
    @JavascriptInterface
    fun getTxtInFolder(path: String): String {
        if (path.isEmpty()) return ""
        val folder = getFile(path)
        val contents = StringBuilder()
        folder.listFiles().let {
            if (it != null) {
                for (f in it) {
                    val charsetName = EncodingDetect.getEncode(f)
                    contents.append(String(f.readBytes(), charset(charsetName)))
                        .append("\n")
                }
                contents.deleteCharAt(contents.length - 1)
            }
        }
        FileUtils.delete(folder.absolutePath)
        return contents.toString()
    }

    /**
     * 获取Zip文件内指定文件的内容（自动检测编码）
     * @param url Zip文件的链接或十六进制字符串
     * @param path 所需获取文件在Zip内的路径，如 "chapter1.txt"
     * @return String 指定文件的内容
     */
    @JavascriptInterface
    fun getZipStringContent(url: String, path: String): String {
        val byteArray = getZipByteArrayContent(url, path) ?: return ""
        val charsetName = EncodingDetect.getEncode(byteArray)
        return String(byteArray, Charset.forName(charsetName))
    }

    /**
     * 获取Zip文件内指定文件的内容（指定编码）
     * @param url Zip文件的链接或十六进制字符串
     * @param path 所需获取文件在Zip内的路径
     * @param charsetName 字符编码名称
     * @return String 指定文件的内容
     */
    @JavascriptInterface
    fun getZipStringContent(url: String, path: String, charsetName: String): String {
        val byteArray = getZipByteArrayContent(url, path) ?: return ""
        return String(byteArray, Charset.forName(charsetName))
    }

    /**
     * 获取Rar文件内指定文件的内容（自动检测编码）
     * @param url Rar文件的链接或十六进制字符串
     * @param path 所需获取文件在Rar内的路径
     * @return String 指定文件的内容
     */
    @JavascriptInterface
    fun getRarStringContent(url: String, path: String): String {
        val byteArray = getRarByteArrayContent(url, path) ?: return ""
        val charsetName = EncodingDetect.getEncode(byteArray)
        return String(byteArray, Charset.forName(charsetName))
    }

    /**
     * 获取Rar文件内指定文件的内容（指定编码）
     * @param url Rar文件的链接或十六进制字符串
     * @param path 所需获取文件在Rar内的路径
     * @param charsetName 字符编码名称
     * @return String 指定文件的内容
     */
    @JavascriptInterface
    fun getRarStringContent(url: String, path: String, charsetName: String): String {
        val byteArray = getRarByteArrayContent(url, path) ?: return ""
        return String(byteArray, Charset.forName(charsetName))
    }

    /**
     * 获取7Zip文件内指定文件的内容（自动检测编码）
     * @param url 7Zip文件的链接或十六进制字符串
     * @param path 所需获取文件在7Zip内的路径
     * @return String 指定文件的内容
     */
    @JavascriptInterface
    fun get7zStringContent(url: String, path: String): String {
        val byteArray = get7zByteArrayContent(url, path) ?: return ""
        val charsetName = EncodingDetect.getEncode(byteArray)
        return String(byteArray, Charset.forName(charsetName))
    }

    /**
     * 获取7Zip文件内指定文件的内容（指定编码）
     * @param url 7Zip文件的链接或十六进制字符串
     * @param path 所需获取文件在7Zip内的路径
     * @param charsetName 字符编码名称
     * @return String 指定文件的内容
     */
    @JavascriptInterface
    fun get7zStringContent(url: String, path: String, charsetName: String): String {
        val byteArray = get7zByteArrayContent(url, path) ?: return ""
        return String(byteArray, Charset.forName(charsetName))
    }

    /**
     * 获取Zip文件内指定文件的字节数组
     * @param url Zip文件的链接或十六进制字符串
     * @param path 所需获取文件在Zip内的路径
     * @return ByteArray? 指定文件的字节数组，未找到返回null
     */
    fun getZipByteArrayContent(url: String, path: String): ByteArray? {
        val bytes = if (url.isAbsUrl()) {
            AnalyzeUrl(url, source = getSource(), coroutineContext = context).getByteArray()
        } else {
            HexUtil.decodeHex(url)
        }
        val bos = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry
            while (zis.nextEntry.also { entry = it } != null) {
                if (entry.name.equals(path)) {
                    zis.use { it.copyTo(bos) }
                    return bos.toByteArray()
                }
                entry = zis.nextEntry
            }
        }

        log("getZipContent 未发现内容")
        return null
    }

    /**
     * 获取Rar文件内指定文件的字节数组
     * @param url Rar文件的链接或十六进制字符串
     * @param path 所需获取文件在Rar内的路径
     * @return ByteArray? 指定文件的字节数组，未找到返回null
     */
    fun getRarByteArrayContent(url: String, path: String): ByteArray? {
        val bytes = if (url.isAbsUrl()) {
            AnalyzeUrl(url, source = getSource(), coroutineContext = context).getByteArray()
        } else {
            HexUtil.decodeHex(url)
        }

        return ByteArrayInputStream(bytes).use {
            LibArchiveUtils.getByteArrayContent(it, path)
        }
    }

    /**
     * 获取7Zip文件内指定文件的字节数组
     * @param url 7Zip文件的链接或十六进制字符串
     * @param path 所需获取文件在7Zip内的路径
     * @return ByteArray? 指定文件的字节数组，未找到返回null
     */
    fun get7zByteArrayContent(url: String, path: String): ByteArray? {
        val bytes = if (url.isAbsUrl()) {
            AnalyzeUrl(url, source = getSource(), coroutineContext = context).getByteArray()
        } else {
            HexUtil.decodeHex(url)
        }

        return ByteArrayInputStream(bytes).use {
            LibArchiveUtils.getByteArrayContent(it, path)
        }
    }

    /**
     * 解析字体Base64数据,返回字体解析类（已废弃）
     * @param data Base64编码的字体数据
     * @return QueryTTF? 字体解析类
     * @deprecated 请使用 queryTTF(Any) 替代
     */
    @Deprecated(
        "Deprecated",
        ReplaceWith("queryTTF(data)")
    )
    fun queryBase64TTF(data: String?): QueryTTF? {
        log("queryBase64TTF(String)方法已过时,并将在未来删除；请无脑使用queryTTF(Any)替代，新方法支持传入 url、本地文件、base64、ByteArray 自动判断&自动缓存，特殊情况需禁用缓存请传入第二可选参数false:Boolean")
        return queryTTF(data)
    }

    /**
     * 返回字体解析类（完整版）
     * @param data 支持以下格式：
     *   - String (URL): 从网络下载字体
     *   - String (Base64): 解码后作为字体数据
     *   - String (本地路径): 读取本地文件作为字体
     *   - ByteArray: 直接作为字体数据
     * @param useCache 是否使用缓存，true使用缓存，false禁用缓存
     * @return QueryTTF? 字体解析类，失败返回null
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun queryTTF(data: Any?, useCache: Boolean): QueryTTF? {
        try {
            var key: String? = null
            var qTTF: QueryTTF?
            when (data) {
                is String -> {
                    if (useCache) {
                        key = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
                            .toHexString()
                        qTTF = AppCacheManager.getQueryTTF(key)
                        if (qTTF != null) return qTTF
                    }
                    val font: ByteArray? = when {
                        data.isAbsUrl() -> AnalyzeUrl(
                            data,
                            source = getSource(),
                            coroutineContext = context
                        ).getByteArray()

                        else -> base64DecodeToByteArray(data)
                    }
                    font ?: return null
                    qTTF = QueryTTF(font)
                }

                is ByteArray -> {
                    if (useCache) {
                        key = MessageDigest.getInstance("SHA-256").digest(data).toHexString()
                        qTTF = AppCacheManager.getQueryTTF(key)
                        if (qTTF != null) return qTTF
                    }
                    qTTF = QueryTTF(data)
                }

                else -> return null
            }
            if (key != null) AppCacheManager.put(key, qTTF)
            return qTTF
        } catch (e: Exception) {
            AppLog.put("[queryTTF] 获取字体处理类出错", e)
            throw e
        }
    }

    /**
     * 返回字体解析类（简化版，默认启用缓存）
     * @param data 支持url、本地文件、base64、ByteArray，自动判断格式
     * @return QueryTTF? 字体解析类
     */
    fun queryTTF(data: Any?): QueryTTF? {
        return queryTTF(data, true)
    }

    /**
     * 替换错误字体为正确字体（完整版）
     * @param text 包含错误字体的内容
     * @param errorQueryTTF 错误的字体解析类
     * @param correctQueryTTF 正确的字体解析类
     * @param filter 是否删除错误字体中不存在的字符
     * @return String 字体替换后的内容
     * @note 通过字形轮廓（glyf）匹配来找到正确的Unicode字符
     */
    fun replaceFont(
        text: String,
        errorQueryTTF: QueryTTF?,
        correctQueryTTF: QueryTTF?,
        filter: Boolean
    ): String {
        if (errorQueryTTF == null || correctQueryTTF == null) return text
        val contentArray = text.toStringArray()
        val intArray = IntArray(1)
        contentArray.forEachIndexed { index, s ->
            val oldCode = s.codePointAt(0)
            if (errorQueryTTF.isBlankUnicode(oldCode)) {
                return@forEachIndexed
            }
            var glyf = errorQueryTTF.getGlyfByUnicode(oldCode)
            if (errorQueryTTF.getGlyfIdByUnicode(oldCode) == 0) glyf = null
            if (filter && (glyf == null)) {
                contentArray[index] = ""
                return@forEachIndexed
            }
            val code = correctQueryTTF.getUnicodeByGlyf(glyf)
            if (code != 0) {
                intArray[0] = code
                contentArray[index] = String(intArray, 0, 1)
            }
        }
        return contentArray.joinToString("")
    }

    /**
     * 替换错误字体为正确字体（简化版，不过滤不存在的字符）
     * @param text 包含错误字体的内容
     * @param errorQueryTTF 错误的字体解析类
     * @param correctQueryTTF 正确的字体解析类
     * @return String 字体替换后的内容
     */
    fun replaceFont(
        text: String,
        errorQueryTTF: QueryTTF?,
        correctQueryTTF: QueryTTF?
    ): String {
        return replaceFont(text, errorQueryTTF, correctQueryTTF, false)
    }


    /**
     * 章节数转数字
     * @param s 包含章节号的字符串，如 "第1234章"
     * @return String? 转换后的字符串，如 "第1234章"；如果原字符串没有数字则返回原值
     */
    @JavascriptInterface
    fun toNumChapter(s: String?): String? {
        s ?: return null
        val matcher = AppPattern.titleNumPattern.matcher(s)
        if (matcher.find()) {
            val intStr = StringUtils.stringToInt(matcher.group(2))
            return "${matcher.group(1)}${intStr}${matcher.group(3)}"
        }
        return s
    }

    /**
     * 解析URL为对象（简化版，只有绝对URL）
     * @param urlStr URL字符串
     * @return JsURL 解析后的URL对象，提供便捷的解析方法
     */
    fun toURL(urlStr: String): JsURL {
        return JsURL(urlStr)
    }

    /**
     * 解析URL为对象（完整版，支持baseUrl）
     * @param url URL字符串或相对路径
     * @param baseUrl 基础URL，用于解析相对路径
     * @return JsURL 解析后的URL对象，提供以下属性和方法：
     *   - url: 完整URL字符串
     *   - baseUrl: 基础URL
     *   - host: 主机名
     *   - path: 路径部分
     *   - params: 查询参数Map
     *   - getQueryValue(name): 获取指定参数值
     */
    fun toURL(url: String, baseUrl: String? = null): JsURL {
        return JsURL(url, baseUrl)
    }

    /**
     * 弹窗提示（短时间）
     * @param msg 要显示的消息内容
     * @note 在主线程显示Toast，1秒后消失
     */
    fun toast(msg: Any?) {
        rhinoContext.ensureActive()
        appCtx.toastForJs("${getSource()?.getTag()}: ${msg.toString()}")
    }

    /**
     * 弹窗提示（长时间）
     * @param msg 要显示的消息内容
     * @note 在主线程显示Toast，3.5秒后消失
     */
    fun longToast(msg: Any?) {
        rhinoContext.ensureActive()
        appCtx.longToastForJs("${getSource()?.getTag()}: ${msg.toString()}")
    }

    /**
     * 输出调试日志
     * @param msg 要输出的日志内容
     * @return Any? 返回传入的消息，便于链式调用
     */
    fun log(msg: Any?): Any? {
        rhinoContextOrNull?.ensureActive()
        getSource()?.let {
            Debug.log(it.getKey(), msg.toString())
        } ?: Debug.log(msg.toString())
        AppLog.putDebug("${getTag() ?: "源"}调试输出: $msg")
        return msg
    }

    /**
     * 输出对象类型
     * @param any 任意对象
     * @note 输出对象的Java类名，用于调试
     */
    fun logType(any: Any?) {
        if (any == null) {
            log("null")
        } else {
            log(any.javaClass.name)
        }
    }

    /**
     * 生成UUID
     * @return String 随机UUID字符串，格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     */
    @JavascriptInterface
    fun randomUUID(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * 获取Android设备ID
     * @return String 设备Android ID
     */
    @JavascriptInterface
    fun androidId(): String {
        return AppConst.androidId
    }

    /**
     * 打开URL（简化版）
     * @param url 要打开的链接
     * @note 支持 legado:// 和 yuedu:// 协议
     */
    fun openUrl(url: String) {
        openUrl(url, null)
    }

    /**
     * 打开URL（完整版）
     * @param url 要打开的链接
     * @param mimeType MIME类型，如 "application/pdf"，用于指定打开方式
     * @note 如果URL超过64KB会抛出异常
     */
    @JavascriptInterface
    fun openUrl(url: String, mimeType: String? = null) {
        require(url.length < 64 * 1024) { "openUrl parameter url too long" }
        rhinoContextOrNull?.ensureActive()
        if (url.startsWith("legado://") || url.startsWith("yuedu://")) {
            appCtx.startActivity<OnLineImportActivity> {
                data = url.toUri()
            }
            return
        }
        val source = getSource() ?: throw NoStackTraceException("openUrl source cannot be null")
        appCtx.startActivity<OpenUrlConfirmActivity> {
            putExtra("uri", url)
            putExtra("mimeType", mimeType)
            putExtra("sourceOrigin", source.getKey())
            putExtra("sourceName", source.getTag())
            putExtra("sourceType", source.getSourceType())
        }
    }

    /**
     * 获取阅读配置（JSON格式）
     * @return String 阅读配置的JSON字符串
     * @note 包含阅读界面相关配置，如字体、背景、翻页模式等
     */
    @JavascriptInterface
    fun getReadBookConfig(): String {
        return GSON.toJson(ReadBookConfig.durConfig)
    }

    /**
     * 获取阅读配置（Map格式）
     * @return Map<String, Any> 阅读配置的Map对象
     */
    fun getReadBookConfigMap(): Map<String, Any> {
        return ReadBookConfig.durConfig.toMap()
    }

    /**
     * 获取主题模式
     * @return String 主题模式标识，"0"=跟随系统，"1"=浅色，"2"=深色
     */
    @JavascriptInterface
    fun getThemeMode(): String {
        return AppConfig.themeMode ?: "0"
    }

    /**
     * 获取主题配置
     * @return String 主题配置的JSON字符串
     */
    @JavascriptInterface
    fun getThemeConfig(): String {
        val themeConfig = OldThemeConfig.getDurConfig(appCtx)
        return GSON.toJson(themeConfig)
    }

    /**
     * 获取主题配置（Map格式）
     * @return Map<String, Any?> 主题配置的Map对象
     */
    fun getThemeConfigMap(): Map<String, Any?> {
        return OldThemeConfig.getDurConfig(appCtx).toMap()
    }

}
