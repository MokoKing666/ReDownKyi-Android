package com.moko.downkyi.re.core.network

import com.moko.downkyi.re.core.model.BiliConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * B 站接口客户端。
 *
 * 三个刻意的设计选择：
 *
 *  1. **统一注入请求头**。UA 必须是桌面 Chrome，Referer / Origin 必须落在
 *     `.bilibili.com`。少任何一个，表现都是「接口返回成功但没有可用清晰度」
 *     或直接 -412，而且不会有任何报错指向这里。
 *  2. **业务错误当异常抛**。B 站的错误码放在 HTTP 200 的响应体里，
 *     不主动检查就会把失败当成功往下传，最终表现为界面上一片空白。
 *  3. **下载用独立连接池**。接口请求要短超时快速失败，下载需要长连接
 *     与并发连接；混用一个客户端会让两边的超时配置互相牵制。
 */
class BiliHttp(
    val cookies: BiliCookies = BiliCookies(),
    val wbi: WbiSigner = WbiSigner(),
    private val client: OkHttpClient = defaultClient(),
) {

    val isLogin: Boolean get() = cookies.isLogin

    // -----------------------------------------------------------------------
    // 接口请求
    // -----------------------------------------------------------------------

    /**
     * GET 一个返回 JSON 对象的接口。
     *
     * [sign] 为 true 时附加 WBI 签名。列表类接口（历史 / 稍后再看 / 收藏夹 /
     * 合集）建议开启，但签名失败会降级为未签名请求而不是让整个接口不可用 ——
     * 那由 [WbiSigner] 的降级逻辑保证。
     */
    suspend fun getJson(
        path: String,
        query: Map<String, String> = emptyMap(),
        host: String = BiliConstants.API_BASE,
        referer: String? = null,
        sign: Boolean = false,
    ): JsonObject {
        val finalQuery = if (sign) wbi.sign(query) else query
        val uri = buildUri(path, finalQuery, host)

        val response = execute(uri, referer)
        val body = response.use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) {
                throw ApiException(r.code, "HTTP ${r.code}", uri)
            }
            text
        }
        return parseObject(body, uri)
    }

    /** GET 接口并返回 `data` 字段。 */
    suspend fun getData(
        path: String,
        query: Map<String, String> = emptyMap(),
        host: String = BiliConstants.API_BASE,
        referer: String? = null,
        sign: Boolean = false,
    ): JsonElement? {
        val root = getJson(path, query, host, referer, sign)
        val data = root["data"]
        return if (data is JsonNull) null else data
    }

    /** GET 纯文本（例如字幕文件是 JSON 数组、弹幕描述是 XML）。 */
    suspend fun getString(uri: String, referer: String? = null): String {
        val response = execute(uri, referer)
        return response.use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) {
                throw ApiException(r.code, "HTTP ${r.code}", uri)
            }
            text
        }
    }

    /**
     * 跟随重定向并返回最终 URL。
     *
     * b23.tv 短链的真实地址只能从重定向结果里取 —— 把响应体当 URL 用
     * 会得到一整页 HTML，而错误要到很后面才会暴露（链接识别失败）。
     * OkHttp 默认跟随重定向，因此 `response.request.url` 就是终点地址。
     */
    suspend fun resolveUrl(uri: String): String {
        val request = Request.Builder()
            .url(uri)
            .header("User-Agent", BiliConstants.USER_AGENT)
            .build()
        return try {
            client.newCall(request).await().use { response -> response.request.url.toString() }
        } catch (e: IOException) {
            throw ApiException(ApiException.CODE_NETWORK, "短链解析失败：${e.message}", uri)
        }
    }

    /** GET 二进制（封面、头像）。 */
    suspend fun getBytes(uri: String, referer: String? = null): ByteArray {
        val response = execute(uri, referer)
        return response.use { r ->
            if (!r.isSuccessful) {
                throw ApiException(r.code, "HTTP ${r.code}", uri)
            }
            r.body.bytes()
        }
    }

    /**
     * GET 一个 JSON 对象，但**不校验业务码**，失败一律返回 null。
     *
     * 只为「code 非零但 data 仍然有效」的接口存在：未登录时
     * `/x/web-interface/nav` 返回 -101「账号未登录」，而 WBI 密钥就放在
     * 同一个响应的 `data.wbi_img` 里 —— 密钥本来就是对游客开放的。
     * 走严格校验的 [getJson] 会把「未登录」当成密钥获取失败抛出去，
     * 所有需要签名的接口随之表现为「需要登录后才能继续」。
     */
    suspend fun getJsonUnchecked(uri: String): JsonObject? {
        val response = execute(uri, referer = null)
        val body = response.use { r ->
            if (!r.isSuccessful) return null
            r.body.string()
        }
        return runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    }

    // -----------------------------------------------------------------------
    // 下载器接口
    // -----------------------------------------------------------------------

    /**
     * 构造一个带 B 站请求头的请求，供分片下载器使用。
     *
     * 单独暴露而不是让下载器自己拼 Header：Range 请求同样需要正确的
     * Referer 与 Cookie，否则 B 站会返回 403，而下载器只能看到「地址过期」。
     */
    fun downloadRequest(
        uri: String,
        rangeStart: Long? = null,
        rangeEnd: Long? = null,
        referer: String? = null,
    ): Request {
        val builder = baseRequestBuilder(uri, referer, accept = "*/*")
        if (rangeStart != null) {
            builder.header("Range", "bytes=$rangeStart-${rangeEnd ?: ""}")
        }
        return builder.build()
    }

    /**
     * 下载请求头，供自拼请求的分片下载器使用。
     *
     * 与 [downloadRequest] 复用同一份拼装逻辑：下载器拿到的是请求**对象**还是
     * 头**映射**只是接口形态差异，若两边各写一遍，改 Referer 时必然漏一边，
     * 表现是「能解析、下载却全 403」。
     */
    fun downloadHeaders(): Map<String, String> = buildMap {
        put("User-Agent", BiliConstants.USER_AGENT)
        put("Referer", BiliConstants.WEB_BASE)
        put("Origin", BiliConstants.WEB_BASE)
        put("Accept", "*/*")
        put("Accept-Language", "zh-CN,zh;q=0.9")
        if (!cookies.isEmpty) {
            put("Cookie", cookies.header)
        }
    }

    /**
     * 下载专用的 HTTP 客户端。
     *
     * 与接口客户端的关键差异：
     *  - 不设 callTimeout —— 单个分片可能要下载很久，用整体超时会误杀；
     *  - readTimeout 由调用方按「读空闲」语义设置（通道里没数据多久算停滞），
     *    而不是「整个响应必须在多久内完成」；
     *  - 连接池按分片并发度放大。
     */
    fun newDownloadClient(
        maxConnectionsPerHost: Int,
        readIdleTimeoutSeconds: Long = 30,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(readIdleTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(maxIdleConnections = maxConnectionsPerHost.coerceAtLeast(5), keepAliveDuration = 1, TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = maxConnectionsPerHost.coerceAtLeast(8)
            maxRequestsPerHost = maxConnectionsPerHost.coerceAtLeast(8)
        })
        .build()

    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // -----------------------------------------------------------------------
    // 内部
    // -----------------------------------------------------------------------

    private fun buildUri(path: String, query: Map<String, String>, host: String): String {
        val base = if (path.startsWith("http")) path else host + path
        if (query.isEmpty()) return base
        val builder = base.toHttpUrlOrNull()?.newBuilder() ?: return base
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build().toString()
    }

    private fun baseRequestBuilder(
        uri: String,
        referer: String?,
        accept: String,
    ): Request.Builder =
        Request.Builder()
            .url(uri)
            .header("User-Agent", BiliConstants.USER_AGENT)
            .header("Referer", referer ?: BiliConstants.WEB_BASE)
            .header("Origin", BiliConstants.WEB_BASE)
            .header("Accept", accept)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .apply {
                if (!cookies.isEmpty) {
                    header("Cookie", cookies.header)
                }
            }

    /**
     * 访问 [uri] 并吸收响应里的 Set-Cookie（登录跨域写 Cookie 用）。
     *
     * [execute] 是私有的，登录链路需要外部触发一次跟随重定向的请求来
     * 让服务端下发 SESSDATA —— 暴露这一个入口即可，不开放裸 execute。
     */
    suspend fun absorbResponse(uri: String, referer: String? = null) {
        runCatching { execute(uri, referer).use { cookies.absorb(it) } }
    }

    private suspend fun execute(uri: String, referer: String?): Response {
        val request = baseRequestBuilder(uri, referer, accept = "application/json, text/plain, */*").build()
        return try {
            val response = client.newCall(request).await()
            cookies.absorb(response)
            response
        } catch (e: IOException) {
            throw ApiException(ApiException.CODE_NETWORK, "网络请求失败：${e.message}", uri)
        }
    }

    private fun parseObject(body: String, uri: String): JsonObject {
        val element = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            throw ApiException(ApiException.CODE_MALFORMED, "接口返回不是合法 JSON", uri)
        }
        val root = element as? JsonObject
            ?: throw ApiException(ApiException.CODE_MALFORMED, "接口返回格式异常", uri)

        // B 站的业务错误码放在 HTTP 200 的响应体里，不检查就会把失败当成功
        val code = root.int("code", ApiException.CODE_OK)
        if (code != ApiException.CODE_OK) {
            val message = root.stringOrNull("message")
                ?: root.stringOrNull("msg")
                ?: "请求失败"
            throw ApiException(code, message, uri)
        }
        return root
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** 接口请求用的客户端：短超时快速失败。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * 把 OkHttp 的异步回调桥接成协程。
 *
 * OkHttp 5 也提供官方的协程扩展，但那是独立 artifact；这里代码量很小，
 * 不值得为一个 15 行的桥接再引入一个依赖。
 *
 * 取消语义是重点：协程被取消时必须 `cancel()` 掉底层请求，
 * 否则下载任务被暂停后连接仍会继续读数据。
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) {
                continuation.resumeWithException(e)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}
