package com.moko.downkyi.re.core.download

import com.moko.downkyi.re.core.network.ApiException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 分片下载器的集成测试。
 *
 * 起一个真实的本地 HTTP 服务器，而不是 mock —— 这一层要验证的正是
 * HTTP 语义（Range、Content-Range、状态码）与文件落盘的真实交互，
 * mock 掉就什么都没测到。
 *
 * 内容是可校验的确定性字节流：只比对长度无法发现"分片顺序错位"
 * 这类问题，必须逐字节比对。
 */
class SegmentDownloaderTest {

    private lateinit var server: HttpServer
    private lateinit var tempDir: File

    private val payload = ByteArray(10_000) { (it % 251).toByte() }
    private var baseUrl = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "downkyi-test-${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        runCatching { server.stop(0) }
        tempDir.deleteRecursively()
    }

    // -----------------------------------------------------------------------
    // 测试服务器
    // -----------------------------------------------------------------------

    private fun startServer(handler: (HttpExchange) -> Unit) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file") { exchange ->
            try {
                handler(exchange)
            } catch (_: Exception) {
                // 客户端取消连接是正常情况，不要让测试服务器崩掉
            } finally {
                exchange.close()
            }
        }
        server.executor = Executors.newFixedThreadPool(8)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/file"
    }

    /** 一个行为正常的支持 Range 的服务器。 */
    private fun startRangeServer(source: ByteArray = payload) {
        startServer { exchange ->
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            if (rangeHeader == null) {
                exchange.sendResponseHeaders(200, source.size.toLong())
                exchange.responseBody.use { it.write(source) }
                return@startServer
            }

            val parsed = parseRange(rangeHeader)
            if (parsed == null) {
                exchange.sendResponseHeaders(400, -1)
                return@startServer
            }
            val start = parsed.first.toInt()
            val end = minOf(parsed.second, source.size - 1L).toInt()
            if (start > end) {
                exchange.sendResponseHeaders(416, -1)
                return@startServer
            }
            val length = end - start + 1
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${source.size}")
            exchange.sendResponseHeaders(206, length.toLong())
            exchange.responseBody.use { it.write(source, start, length) }
        }
    }

    private fun parseRange(header: String): Pair<Long, Long>? {
        val match = Regex("""bytes=(\d+)-(\d*)""").find(header) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val endText = match.groupValues[2]
        val end = if (endText.isEmpty()) Long.MAX_VALUE else endText.toLongOrNull() ?: return null
        return start to end
    }

    private fun downloader(
        segments: Int = 4,
        refresher: (suspend () -> List<String>)? = null,
    ) = SegmentDownloader(
        client = client,
        initialUrls = listOf(baseUrl),
        headers = { emptyMap() },
        urlRefresher = refresher,
        segmentCount = segments,
        maxAttempts = 3,
        baseBackoffMillis = 10L,
    )

    // -----------------------------------------------------------------------
    // 正常路径
    // -----------------------------------------------------------------------

    @Test
    fun `分片下载后内容与源逐字节一致`() = runBlocking {
        startRangeServer()
        val target = File(tempDir, "out.bin")

        val outcome = downloader(segments = 4).download(target.absolutePath)

        assertTrue(outcome is DownloadOutcome.Completed)
        assertEquals(payload.size.toLong(), (outcome as DownloadOutcome.Completed).totalBytes)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `单片下载同样正确`() = runBlocking {
        startRangeServer()
        val target = File(tempDir, "single.bin")
        downloader(segments = 1).download(target.absolutePath)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `下载完成后分片文件被清理`() = runBlocking {
        startRangeServer()
        val target = File(tempDir, "out.bin")
        downloader().download(target.absolutePath)

        val leftovers = SegmentPlanner.partPaths(target.absolutePath).filter { File(it).exists() }
        assertTrue("分片应已清理，但残留：$leftovers", leftovers.isEmpty())
    }

    @Test
    fun `进度回调的最终值等于总长度`() = runBlocking {
        startRangeServer()
        val target = File(tempDir, "out.bin")
        var lastDownloaded = 0L
        var lastTotal = 0L

        downloader().download(target.absolutePath) { downloaded, total ->
            lastDownloaded = downloaded
            lastTotal = total
        }

        assertEquals(payload.size.toLong(), lastTotal)
        assertEquals(payload.size.toLong(), lastDownloaded)
    }

    // -----------------------------------------------------------------------
    // 服务端给多 / 中途断流（真机实测踩过）
    // -----------------------------------------------------------------------

    @Test
    fun `206 响应带超出请求范围的数据时按计划长度截断`() = runBlocking {
        // 部分 CDN 节点对分片请求会一路发送到文件末尾，而不是只给请求的范围。
        // 写侧必须按计划长度截断，否则分片在合并前的完整性校验处被拒，
        // 一次下载就白下了
        startServer { exchange ->
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val parsed = rangeHeader?.let { parseRange(it) }
            if (parsed == null) {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
                return@startServer
            }
            val start = parsed.first.toInt()
            // 探测请求（bytes=0-0）必须如实返回 1 字节：这个服务器模拟的是
            // 「分片请求给多」的 CDN，而不是「探测与实际不符」的更危险情形
            if (start == 0 && parsed.second == 0L) {
                exchange.responseHeaders.add("Content-Range", "bytes 0-0/${payload.size}")
                exchange.sendResponseHeaders(206, 1L)
                exchange.responseBody.use { it.write(payload, 0, 1) }
                return@startServer
            }
            // 故意越过请求的 end，一路发到文件末尾
            val length = payload.size - start
            exchange.responseHeaders.add(
                "Content-Range",
                "bytes $start-${payload.size - 1}/${payload.size}",
            )
            exchange.sendResponseHeaders(206, length.toLong())
            exchange.responseBody.use { it.write(payload, start, length) }
        }
        val target = File(tempDir, "out.bin")

        val outcome = downloader(segments = 4).download(target.absolutePath)

        assertTrue(outcome is DownloadOutcome.Completed)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `响应中途断流后续传不产生重复字节`() = runBlocking {
        // 第一次请求只发送请求范围的前一半就断开连接（模拟真机上的 CDN 断流）；
        // 客户端重试时必须从**文件实际长度**续传 —— 若仍按旧计数从原起点追加，
        // 已落盘的孤儿字节会被写两遍，分片总长超出计划
        var brokenServed = false
        startServer { exchange ->
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val parsed = rangeHeader?.let { parseRange(it) }
            if (parsed == null) {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
                return@startServer
            }
            val start = parsed.first.toInt()
            val end = minOf(parsed.second, payload.size - 1L).toInt()
            val length = end - start + 1
            if (!brokenServed) {
                brokenServed = true
                // 发一半就断：不关闭响应头之外的东西，直接中断
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
                exchange.sendResponseHeaders(206, length.toLong())
                exchange.responseBody.use { it.write(payload, start, length / 2) }
                exchange.close()
                return@startServer
            }
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, length.toLong())
            exchange.responseBody.use { it.write(payload, start, length) }
        }
        val target = File(tempDir, "out.bin")

        val outcome = downloader(segments = 4).download(target.absolutePath)

        assertTrue(outcome is DownloadOutcome.Completed)
        assertArrayEquals(payload, target.readBytes())
    }

    // -----------------------------------------------------------------------
    // 服务端不支持 Range
    // -----------------------------------------------------------------------

    @Test
    fun `服务端忽略 Range 时退化为单片并成功`() = runBlocking {
        // 永远返回 200 + 完整文件
        startServer { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        val target = File(tempDir, "out.bin")

        val outcome = downloader(segments = 8).download(target.absolutePath)

        assertTrue(outcome is DownloadOutcome.Completed)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `探测说支持 Range 但实际返回完整文件时拒绝并失败`() = runBlocking {
        // 这是最危险的情况：若接受，完整文件会被写进某个分片，产出损坏文件。
        // 探测请求（bytes=0-0）给 206 骗过探测，真实分片请求给 200
        startServer { exchange ->
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            if (rangeHeader == "bytes=0-0") {
                exchange.responseHeaders.add("Content-Range", "bytes 0-0/${payload.size}")
                exchange.sendResponseHeaders(206, 1)
                exchange.responseBody.use { it.write(payload, 0, 1) }
            } else {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
        }
        val target = File(tempDir, "out.bin")

        var failed = false
        try {
            downloader(segments = 4).download(target.absolutePath)
        } catch (e: ApiException) {
            failed = true
            assertTrue("错误信息应说明拒绝了完整文件", e.message.contains("Range"))
        }

        assertTrue("应拒绝而不是产出损坏文件", failed)
        assertFalse("不应留下成品文件", target.exists())
    }

    // -----------------------------------------------------------------------
    // 断点续传
    // -----------------------------------------------------------------------

    @Test
    fun `签名匹配时保留已完成的分片并续传`() = runBlocking {
        startRangeServer()
        val target = File(tempDir, "out.bin")

        // 预置：第一片已完整下载
        val plan = SegmentPlanner.plan(target.absolutePath, payload.size.toLong(), 4)
        val first = plan.segments.first()
        File(first.partPath).writeBytes(payload.copyOfRange(0, first.size.toInt()))

        val outcome = downloader(segments = 4)
            .download(target.absolutePath, resumeSignature = plan.signature)

        assertTrue(outcome is DownloadOutcome.Completed)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `签名不匹配时丢弃旧分片重新下载`() = runBlocking {
        startRangeServer()
        val target = File(tempDir, "out.bin")

        // 预置一个内容错误的分片；签名不匹配时应被丢弃而不是被采用
        val plan = SegmentPlanner.plan(target.absolutePath, payload.size.toLong(), 4)
        File(plan.segments.first().partPath).writeBytes(ByteArray(plan.segments.first().size.toInt()) { 0x7F })

        downloader(segments = 4).download(target.absolutePath, resumeSignature = "过期的签名")

        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `分片长度超出应有大小时被丢弃`() = runBlocking {
        startRangeServer()
        val target = File(tempDir, "out.bin")

        val plan = SegmentPlanner.plan(target.absolutePath, payload.size.toLong(), 4)
        val first = plan.segments.first()
        // 写入比应有长度更多的垃圾数据
        File(first.partPath).writeBytes(ByteArray(first.size.toInt() + 500) { 0x11 })

        downloader(segments = 4).download(target.absolutePath, resumeSignature = plan.signature)

        assertArrayEquals(payload, target.readBytes())
    }

    // -----------------------------------------------------------------------
    // 地址刷新
    // -----------------------------------------------------------------------

    @Test
    fun `地址失效时刷新后继续下载`() = runBlocking {
        var servedOnce = false
        startServer { exchange ->
            if (!servedOnce) {
                // 第一次请求返回 403，模拟地址过期
                servedOnce = true
                exchange.sendResponseHeaders(403, -1)
                return@startServer
            }
            val rangeHeader = exchange.requestHeaders.getFirst("Range") ?: "bytes=0-"
            val parsed = parseRange(rangeHeader)!!
            val start = parsed.first.toInt()
            val end = minOf(parsed.second, payload.size - 1L).toInt()
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, (end - start + 1).toLong())
            exchange.responseBody.use { it.write(payload, start, end - start + 1) }
        }

        var refreshCalls = 0
        val target = File(tempDir, "out.bin")

        val outcome = downloader(refresher = {
            refreshCalls++
            listOf(baseUrl)
        }).download(target.absolutePath)

        assertTrue(outcome is DownloadOutcome.Completed)
        assertArrayEquals(payload, target.readBytes())
        assertEquals("应只刷新一次", 1, refreshCalls)
    }

    @Test
    fun `地址始终失效时抛出可读错误`() = runBlocking {
        startServer { exchange ->
            exchange.sendResponseHeaders(404, -1)
        }
        val target = File(tempDir, "out.bin")

        var message = ""
        try {
            downloader().download(target.absolutePath)
        } catch (e: ApiException) {
            message = e.message
        }

        assertTrue("应给出失败原因，实际：$message", message.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // 取消
    // -----------------------------------------------------------------------

    @Test
    fun `取消后已下载的分片保留在磁盘上`() = runBlocking {
        // 慢速分块发送：每个分片都在"写了一半"的状态上停留很久。
        //
        // 这里刻意不用「固定 delay 之后再取消」：取消点落在第一个字节落盘之前
        // 还是之后，取决于探测请求、建连与 JIT 的耗时。取消早了，一个分片都没写，
        // 断言必然失败；取消晚了，分片可能已经下完并合并出成品。同一个测试在
        // 单跑和与其它模块一起跑时结果不同 —— 那测的是机器快慢，不是下载器行为。
        // 改成「等到确实有分片落盘再取消」，结果就与机器快慢无关。
        startServer { exchange ->
            val rangeHeader = exchange.requestHeaders.getFirst("Range") ?: "bytes=0-"
            val parsed = parseRange(rangeHeader)!!
            val start = parsed.first.toInt()
            val end = minOf(parsed.second, payload.size - 1L).toInt()
            val length = end - start + 1
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, length.toLong())
            exchange.responseBody.use { out ->
                var sent = 0
                while (sent < length) {
                    val chunk = minOf(SLOW_CHUNK_BYTES, length - sent)
                    out.write(payload, start + sent, chunk)
                    out.flush()
                    sent += chunk
                    Thread.sleep(SLOW_CHUNK_DELAY_MILLIS)
                }
            }
        }

        val target = File(tempDir, "out.bin")
        val partPaths = SegmentPlanner.partPaths(target.absolutePath)
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            runCatching { downloader(segments = 8).download(target.absolutePath) }
        }

        // 轮询而不是等待固定时长：只看"磁盘上是否已经有分片写进了字节"
        val landed = withTimeoutOrNull(LANDED_TIMEOUT_MILLIS) {
            while (partPaths.none { File(it).length() > 0L }) delay(10)
            true
        }

        job.cancel()
        job.join()
        scope.cancel()

        // 这条前置断言很重要：没有它，取消过早时后面的断言会以"分片没保留"的面目失败，
        // 掩盖掉真正的原因（取消发生在任何写入之前，这条用例根本没测到东西）
        assertTrue("取消前就应有分片落盘，否则本用例没有覆盖到取消路径", landed == true)

        // 被取消时不产出成品，但分片仍在磁盘上，下次可续传
        assertFalse("取消时不应产出成品文件", target.exists())
        val resumeParts = partPaths.filter { File(it).length() > 0L }
        assertTrue("已下载的分片应保留以便续传", resumeParts.isNotEmpty())
    }

    private companion object {
        /** 慢速发送时每块的字节数。 */
        const val SLOW_CHUNK_BYTES = 200

        /** 慢速发送时每块之间的间隔。 */
        const val SLOW_CHUNK_DELAY_MILLIS = 40L

        /** 等待第一个分片落盘的上限。 */
        const val LANDED_TIMEOUT_MILLIS = 10_000L
    }
}
