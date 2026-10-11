package com.moko.downkyi.re.core.download

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 下载编排层的集成测试。
 *
 * 与 [SegmentDownloaderTest] 同样起真实本地服务器：编排层要验证的是
 * 多路并发、状态机流转与封装时机，这些只有接上真实下载器才有意义。
 */
class DownloadManagerTest {

    private lateinit var server: HttpServer
    private lateinit var tempDir: File
    private lateinit var scope: CoroutineScope

    private val videoPayload = ByteArray(8_000) { (it % 251).toByte() }
    private val audioPayload = ByteArray(3_000) { (it % 197).toByte() }
    private var videoUrl = ""
    private var audioUrl = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "downkyi-mgr-${System.nanoTime()}")
        tempDir.mkdirs()
        scope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        runCatching { server.stop(0) }
        scope.cancel()
        tempDir.deleteRecursively()
    }

    private fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/video") { exchange -> serveRange(exchange, videoPayload) }
        server.createContext("/audio") { exchange -> serveRange(exchange, audioPayload) }
        server.executor = Executors.newFixedThreadPool(8)
        server.start()
        val port = server.address.port
        videoUrl = "http://127.0.0.1:$port/video"
        audioUrl = "http://127.0.0.1:$port/audio"
    }

    private fun serveRange(exchange: HttpExchange, source: ByteArray) {
        try {
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            if (rangeHeader == null) {
                exchange.sendResponseHeaders(200, source.size.toLong())
                exchange.responseBody.use { it.write(source) }
                return
            }
            val match = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader) ?: run {
                exchange.sendResponseHeaders(400, -1); return
            }
            val start = match.groupValues[1].toInt()
            val endText = match.groupValues[2]
            val end = if (endText.isEmpty()) source.size - 1 else minOf(endText.toInt(), source.size - 1)
            if (start > end) {
                exchange.sendResponseHeaders(416, -1); return
            }
            val length = end - start + 1
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${source.size}")
            exchange.sendResponseHeaders(206, length.toLong())
            exchange.responseBody.use { it.write(source, start, length) }
        } catch (_: Exception) {
            // 客户端取消连接是正常情况
        } finally {
            exchange.close()
        }
    }

    private fun manager(muxer: Muxer = NoopMuxer) = DownloadManager(
        client = client,
        headers = { emptyMap() },
        muxer = muxer,
        segmentCount = 4,
        parentScope = scope,
    )

    /** 阻塞直到 [id] 进入终态，或超时返回 null。 */
    private suspend fun awaitDone(
        states: StateFlow<Map<String, DownloadTaskState>>,
        id: String,
        timeoutMs: Long = 10_000,
    ): DownloadTaskState? = withTimeoutOrNull(timeoutMs) {
        while (true) {
            val s = states.value[id]
            if (s != null && s.status in TERMINAL) return@withTimeoutOrNull s
            delay(20)
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    // -----------------------------------------------------------------------
    // 正常路径
    // -----------------------------------------------------------------------

    @Test
    fun `单路下载完成后状态为 Completed`() = runBlocking {
        startServer()
        val target = File(tempDir, "a.mp4")
        val m = manager()

        m.submit(
            DownloadRequest(
                id = "t1",
                title = "单路",
                streams = listOf(StreamTarget(StreamKind.Video, listOf(videoUrl), target.absolutePath)),
            ),
        )

        val done = awaitDone(m.tasks, "t1")
        assertEquals(DownloadStatus.Completed, done?.status)
        assertArrayEquals(videoPayload, target.readBytes())
    }

    @Test
    fun `两路都落盘后才调用封装并给出正确路径`() = runBlocking {
        startServer()
        val v = File(tempDir, "v.m4s")
        val a = File(tempDir, "a.m4s")
        val out = File(tempDir, "out.mp4").absolutePath
        val muxCalls = mutableListOf<Triple<String?, String?, String>>()
        val m = manager(
            muxer = Muxer { videoPath, audioPath, outputPath ->
                muxCalls += Triple(videoPath, audioPath, outputPath)
            },
        )

        m.submit(
            DownloadRequest(
                id = "t2",
                title = "两路",
                streams = listOf(
                    StreamTarget(StreamKind.Video, listOf(videoUrl), v.absolutePath),
                    StreamTarget(StreamKind.Audio, listOf(audioUrl), a.absolutePath),
                ),
                outputPath = out,
            ),
        )

        val done = awaitDone(m.tasks, "t2")
        assertEquals(DownloadStatus.Completed, done?.status)
        assertArrayEquals(videoPayload, v.readBytes())
        assertArrayEquals(audioPayload, a.readBytes())
        assertEquals(1, muxCalls.size)
        assertEquals(v.absolutePath, muxCalls[0].first)
        assertEquals(a.absolutePath, muxCalls[0].second)
        assertEquals(out, muxCalls[0].third)
    }

    @Test
    fun `单路且不封装时不触发封装`() = runBlocking {
        startServer()
        val m = manager(muxer = Muxer { _, _, _ -> throw AssertionError("不该被调用") })

        m.submit(
            DownloadRequest(
                id = "t3",
                title = "不封装",
                streams = listOf(StreamTarget(StreamKind.Video, listOf(videoUrl), File(tempDir, "x").absolutePath)),
            ),
        )

        assertEquals(DownloadStatus.Completed, awaitDone(m.tasks, "t3")?.status)
    }

    @Test
    fun `进度汇总等于两路之和`() = runBlocking {
        startServer()
        val m = manager()

        m.submit(
            DownloadRequest(
                id = "t4",
                title = "进度",
                streams = listOf(
                    StreamTarget(StreamKind.Video, listOf(videoUrl), File(tempDir, "v").absolutePath),
                    StreamTarget(StreamKind.Audio, listOf(audioUrl), File(tempDir, "a").absolutePath),
                ),
            ),
        )

        val done = awaitDone(m.tasks, "t4")
        assertEquals(DownloadStatus.Completed, done?.status)
        assertEquals((videoPayload.size + audioPayload.size).toLong(), done?.downloadedBytes)
        assertEquals(1f, done?.progress ?: 0f, 1e-6f)
    }

    // -----------------------------------------------------------------------
    // 失败
    // -----------------------------------------------------------------------

    @Test
    fun `某路失败时状态为 Failed 并带错误信息`() = runBlocking {
        // audio 上的地址不存在 → 404
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/video") { exchange -> serveRange(exchange, videoPayload) }
        server.createContext("/audio") { exchange ->
            runCatching { exchange.sendResponseHeaders(404, -1) }
            exchange.close()
        }
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
        val port = server.address.port
        videoUrl = "http://127.0.0.1:$port/video"
        audioUrl = "http://127.0.0.1:$port/audio"

        val m = manager()
        m.submit(
            DownloadRequest(
                id = "t5",
                title = "失败",
                streams = listOf(
                    StreamTarget(StreamKind.Video, listOf(videoUrl), File(tempDir, "v").absolutePath),
                    StreamTarget(StreamKind.Audio, listOf(audioUrl), File(tempDir, "a").absolutePath),
                ),
                outputPath = File(tempDir, "out.mp4").absolutePath,
            ),
        )

        val done = awaitDone(m.tasks, "t5")
        assertEquals(DownloadStatus.Failed, done?.status)
        assertTrue("错误信息应非空", !done?.errorMessage.isNullOrBlank())
    }

    @Test
    fun `失败时不再调用封装`() = runBlocking {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/audio") { exchange ->
            runCatching { exchange.sendResponseHeaders(404, -1) }
            exchange.close()
        }
        server.executor = Executors.newFixedThreadPool(2)
        server.start()
        audioUrl = "http://127.0.0.1:${server.address.port}/audio"

        val m = manager(muxer = Muxer { _, _, _ -> throw AssertionError("失败后不该封装") })
        m.submit(
            DownloadRequest(
                id = "t6",
                title = "失败不封装",
                streams = listOf(StreamTarget(StreamKind.Audio, listOf(audioUrl), File(tempDir, "a").absolutePath)),
                outputPath = File(tempDir, "out").absolutePath,
            ),
        )

        assertEquals(DownloadStatus.Failed, awaitDone(m.tasks, "t6")?.status)
    }

    // -----------------------------------------------------------------------
    // 取消
    // -----------------------------------------------------------------------

    @Test
    fun `取消任务后状态为 Cancelled`() = runBlocking {
        // 慢速服务器，确保取消落在下载中途
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/video") { exchange ->
            try {
                exchange.sendResponseHeaders(200, videoPayload.size.toLong())
                val out = exchange.responseBody
                var offset = 0
                while (offset < videoPayload.size) {
                    val n = minOf(200, videoPayload.size - offset)
                    out.write(videoPayload, offset, n)
                    out.flush()
                    offset += n
                    Thread.sleep(30)
                }
            } catch (_: Exception) {
            } finally {
                exchange.close()
            }
        }
        server.executor = Executors.newFixedThreadPool(2)
        server.start()
        videoUrl = "http://127.0.0.1:${server.address.port}/video"

        val m = manager()
        m.submit(
            DownloadRequest(
                id = "t7",
                title = "取消",
                streams = listOf(StreamTarget(StreamKind.Video, listOf(videoUrl), File(tempDir, "v").absolutePath)),
            ),
        )

        delay(60)
        m.cancel("t7")
        val done = awaitDone(m.tasks, "t7")
        assertEquals(DownloadStatus.Cancelled, done?.status)
    }

    @Test
    fun `取消后重试能凭分片续传直到完成`() = runBlocking {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/video") { exchange ->
            try {
                exchange.sendResponseHeaders(200, videoPayload.size.toLong())
                val out = exchange.responseBody
                var offset = 0
                while (offset < videoPayload.size) {
                    val n = minOf(200, videoPayload.size - offset)
                    out.write(videoPayload, offset, n)
                    out.flush()
                    offset += n
                    Thread.sleep(30)
                }
            } catch (_: Exception) {
            } finally {
                exchange.close()
            }
        }
        server.executor = Executors.newFixedThreadPool(2)
        server.start()
        videoUrl = "http://127.0.0.1:${server.address.port}/video"

        val target = File(tempDir, "v")
        val m = manager()
        m.submit(
            DownloadRequest(
                id = "t8",
                title = "重试",
                streams = listOf(StreamTarget(StreamKind.Video, listOf(videoUrl), target.absolutePath)),
            ),
        )
        delay(60)
        m.cancel("t8")
        val cancelled = awaitDone(m.tasks, "t8")
        assertEquals(DownloadStatus.Cancelled, cancelled?.status)

        m.retry("t8")
        val done = awaitDone(m.tasks, "t8")
        assertEquals(DownloadStatus.Completed, done?.status)
        assertArrayEquals(videoPayload, target.readBytes())
    }

    @Test
    fun `remove 后任务从状态表消失`() = runBlocking {
        startServer()
        val m = manager()
        m.submit(
            DownloadRequest(
                id = "t9",
                title = "移除",
                streams = listOf(StreamTarget(StreamKind.Video, listOf(videoUrl), File(tempDir, "v").absolutePath)),
            ),
        )
        awaitDone(m.tasks, "t9")

        m.remove("t9")
        assertEquals(null, m.tasks.value["t9"])
    }

    companion object {
        private val TERMINAL = setOf(
            DownloadStatus.Completed,
            DownloadStatus.Failed,
            DownloadStatus.Cancelled,
        )
    }
}
