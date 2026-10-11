package com.moko.downkyi.re.core.download

import com.moko.downkyi.re.core.network.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * 下载结果的最终状态。
 *
 * 失败不走这里而是抛 [ApiException] —— 失败必须携带原因（网络 / 鉴权 /
 * 地址过期 / 数据校验不过），而"成功但被取消"是一个正常状态：
 * 用户主动暂停时已下载的分片必须保留，下次接着下。
 */
sealed interface DownloadOutcome {
    data class Completed(val totalBytes: Long) : DownloadOutcome

    /** 被取消。已下载的分片保留在磁盘上，可直接续传。 */
    data class Cancelled(val downloadedBytes: Long, val totalBytes: Long) : DownloadOutcome
}

/**
 * 多线程分片下载器。
 *
 * 设计要点（每一条都对应一类真实故障）：
 *
 *  - **每个分片写独立的 `.partN`**，而不是多线程往同一文件的不同偏移写。
 *    共享文件句柄需要精确 seek，任何一次错位都会静默损坏文件，
 *    且恢复进度时无从判断"写到哪了"；`.partN` 只需看文件长度。
 *  - **拼接后复核总大小**。前两步都对但拼接出错（磁盘写满、IO 异常）
 *    必须被发现 —— 这种损坏若放过去，用户会拿到一个打不开的文件。
 *  - **地址过期时重新解析而不是原地重试**。B 站播放地址带时效签名，
 *    403/404 重试一百次也不会变好；而重新解析后**已下载的分片继续有效**
 *    （字节内容不变，只是地址变了）。
 */
class SegmentDownloader(
    private val client: OkHttpClient,
    initialUrls: List<String>,
    private val headers: () -> Map<String, String>,
    /** 地址失效时重新解析。返回空列表表示刷新失败。 */
    private val urlRefresher: (suspend () -> List<String>)? = null,
    private val segmentCount: Int = 8,
    private val maxAttempts: Int = 8,
    private val baseBackoffMillis: Long = 500L,
    private val maxRefreshes: Int = 3,
) {

    private var urls: List<String> = initialUrls.filter { it.isNotEmpty() }
    private var refreshCount = 0

    /** 当前是否还有可用的下载地址。 */
    val hasUrls: Boolean get() = urls.isNotEmpty()

    /**
     * 下载到 [targetPath]。
     *
     * [resumeSignature] 是上次中断时保存的签名（见 [DownloadPlan.signature]）。
     * 签名不匹配时旧分片会被丢弃重下，这是刻意的：宁可多下一次，
     * 也不能拼出一个字节错位的文件。
     */
    suspend fun download(
        targetPath: String,
        resumeSignature: String? = null,
        onSignature: (String) -> Unit = {},
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val probe = probe()

        // 服务端不支持 Range 时必须退化为单片。
        //
        // 探测返回 200 说明它忽略 Range、每次都回整个文件；此时若还按
        // segmentCount 分片，每个分片都会收到完整文件，而校验层会逐个拒绝 ——
        // 结果是重试到上限后失败，用户看到的是"下载失败"而不是"这个源不支持分片"。
        val effectiveSegments = if (probe.supportRange) segmentCount else 1

        val plan = SegmentPlanner.plan(targetPath, probe.totalBytes, effectiveSegments)
        onSignature(plan.signature)

        // 总长未知：只能顺序写到一个分片文件里
        if (probe.totalBytes <= 0L) {
            val segment = plan.segments.single()
            val sequentialProgress = ProgressTracker(total = 0L, initial = 0L, listener = onProgress)
            downloadSequential(segment.partPath, sequentialProgress)
            currentCoroutineContext().ensureActive()
            concat(plan, probe.supportRange)
            return@withContext DownloadOutcome.Completed(plan.totalBytes)
        }

        val states = resolveResume(plan, resumeSignature)
        val progress = ProgressTracker(
            total = plan.totalBytes,
            initial = states.sumOf { it.done },
            listener = onProgress,
        )
        progress.emit()

        downloadSegments(plan, states, progress)

        // 下载阶段的收尾必须强制发一次进度。
        //
        // 进度是节流回调的（每 256 KB 一次），最后不足阈值的增量不会触发回调 ——
        // 不补这一次，界面会永远停在「99%」这类旧值上，
        // 而实际上文件已经下载完毕。
        progress.emit()

        // 走到这里说明所有分片都完成了（否则上面会抛异常或返回取消）
        currentCoroutineContext().ensureActive()
        concat(plan, supportRange = true)
        DownloadOutcome.Completed(plan.totalBytes)
    }

    // -----------------------------------------------------------------------
    // 探测
    // -----------------------------------------------------------------------

    private data class ProbeResult(val totalBytes: Long, val supportRange: Boolean)

    /**
     * 用 `Range: bytes=0-0` 探测总长度。
     *
     * 顺带判断服务端是否支持 Range：
     *  - 206 → 支持，从 `Content-Range` 取总长；
     *  - 200 → **不支持**，返回的是整个文件，只能顺序下载。
     *
     * 这两条路径必须分开，否则会把完整文件写进一个分片里。
     */
    private suspend fun probe(): ProbeResult {
        var lastError: ApiException? = null

        while (true) {
            for (url in urls) {
                try {
                    val response = client.newCall(buildRequest(url, 0L, 0L)).execute()
                    response.use { r ->
                        when (classifyStatus(r.code)) {
                            HttpStatusClass.Success -> {
                                if (r.code == 206) {
                                    val range = ContentRangeParser.parse(r.header("Content-Range"))
                                    return ProbeResult(range?.total ?: 0L, supportRange = true)
                                }
                                // 200：服务端忽略了 Range，只能顺序下载
                                val length = r.header("Content-Length")?.toLongOrNull() ?: 0L
                                return ProbeResult(length, supportRange = false)
                            }

                            HttpStatusClass.UrlExpired, HttpStatusClass.Retryable -> return@use

                            HttpStatusClass.Fatal ->
                                if (r.code == 416) return@use
                                else throw ApiException(r.code, "HTTP ${r.code}", url)
                        }
                    }
                } catch (e: IOException) {
                    lastError = ApiException(ApiException.CODE_NETWORK, "探测失败：${e.message}", url)
                }
            }

            // 所有地址都不行 → 重新解析一次
            if (!tryRefresh()) {
                throw lastError ?: ApiException(
                    ApiException.CODE_LOCAL,
                    "所有下载地址都不可用",
                    urls.firstOrNull().orEmpty(),
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 断点恢复
    // -----------------------------------------------------------------------

    private fun resolveResume(plan: DownloadPlan, resumeSignature: String?): List<SegmentResumeState> {
        val actualLengths = plan.segments.associate { segment ->
            segment.index to File(segment.partPath).takeIf { it.exists() }?.length().orZero()
        }
        val states = ResumePlanner.resolve(plan, resumeSignature, actualLengths)

        // 需要作废的分片必须真的删掉，否则下次启动又会被当成进度
        states.filter { it.shouldReset }.forEach { state ->
            val segment = plan.segments.first { it.index == state.index }
            runCatching { File(segment.partPath).delete() }
        }
        return states
    }

    // -----------------------------------------------------------------------
    // 分片下载
    // -----------------------------------------------------------------------

    private suspend fun downloadSegments(
        plan: DownloadPlan,
        states: List<SegmentResumeState>,
        progress: ProgressTracker,
    ) {
        // 已下完的分片不参与调度
        val pending = plan.segments.filter { segment ->
            val done = states.firstOrNull { it.index == segment.index }?.done ?: 0L
            done < segment.size
        }
        if (pending.isEmpty()) return

        val work = pending.map { segment ->
            val done = states.firstOrNull { it.index == segment.index }?.done ?: 0L
            segment to done
        }

        // 分片之间并发，但每个 worker 顺序地取下一个分片 —— 用共享游标，
        // 而不是把分片预先切成 N 份平均分给 worker：后者在某个分片
        // 特别慢时会出现其他 worker 早已空闲却无法帮忙的情况
        val workerCount = segmentCount.coerceIn(1, work.size)
        val cursor = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            List(workerCount) {
                async {
                    while (true) {
                        val index = cursor.getAndIncrement()
                        if (index >= work.size) break
                        val (segment, alreadyDone) = work[index]
                        downloadSingleSegment(plan, segment, alreadyDone, progress)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun downloadSingleSegment(
        plan: DownloadPlan,
        segment: SegmentPlan,
        alreadyDone: Long,
        progress: ProgressTracker,
    ) {
        var done = alreadyDone
        var attempts = 0
        var refreshesAtThisSegment = 0

        while (done < segment.size) {
            currentCoroutineContext().ensureActive()

            val url = urls.firstOrNull()
                ?: throw ApiException(ApiException.CODE_LOCAL, "没有可用的下载地址")

            try {
                // 续传基准以**文件实际长度**为准：上次尝试可能在抛异常前已经
                // 落盘了一批字节（append 模式下它们本身就是这段流的顺序数据）。
                // 若仍用旧计数续传，会从同一起点重复请求、重复追加，产出比
                // 计划更长的分片 —— 合并前的完整性校验虽然拦得住，但那一次
                // 下载就白下了。文件超出计划长度属于异常状态，删掉整片重下。
                val partFile = File(segment.partPath)
                val onDisk = if (partFile.exists()) partFile.length() else 0L
                done = if (onDisk > segment.size) {
                    partFile.delete()
                    0L
                } else {
                    onDisk
                }

                val rangeStart = segment.start + done
                val response = client
                    .newCall(buildRequest(url, rangeStart, segment.end))
                    .execute()

                response.use { r ->
                    val check = RangeValidator.validate(
                        status = r.code,
                        contentRangeHeader = r.header("Content-Range"),
                        requestedStart = rangeStart,
                        requestedEnd = segment.end,
                        expectedTotal = plan.totalBytes,
                        segmentIndex = segment.index,
                    )

                    when {
                        check is RangeCheck.Rejected -> {
                            // 416：本地长度若已等于分片大小，说明它其实已经下完了
                            if (r.code == 416 && done == segment.size) return
                            when (classifyStatus(r.code)) {
                                // 2xx 却校验不过：重试不会变好，直接失败并带上原因
                                HttpStatusClass.Success, HttpStatusClass.Fatal -> throw ApiException(
                                    r.code, check.reason, url,
                                )

                                HttpStatusClass.UrlExpired -> {
                                    // 地址过期：重新解析后立刻用新地址继续，不消耗重试次数。
                                    // 已下载的分片依然有效 —— 字节内容没变，只是地址变了
                                    if (tryRefresh()) return@use
                                    throw RetrySignal(r.code, check.reason)
                                }

                                HttpStatusClass.Retryable -> {
                                    throw RetrySignal(r.code, check.reason)
                                }
                            }
                        }

                        check is RangeCheck.Accepted -> {
                            done = writeBody(r.body.byteStream(), segment.partPath, done, segment, progress)
                        }
                    }
                }

                if (done >= segment.size) return

                // 流正常结束但字节数不够：服务端提前断流
                attempts++
                if (attempts >= maxAttempts) {
                    throw ApiException(
                        ApiException.CODE_LOCAL,
                        "分片 ${segment.index} 数据不足（$done/${segment.size}）",
                        url,
                    )
                }
                delay(backoff(attempts))
            } catch (signal: RetrySignal) {
                attempts++
                if (attempts >= maxAttempts) {
                    throw ApiException(signal.code, signal.reason, url)
                }
                delay(backoff(attempts))
            } catch (e: IOException) {
                attempts++
                if (attempts >= maxAttempts) {
                    throw ApiException(ApiException.CODE_NETWORK, "分片 ${segment.index} 失败：${e.message}", url)
                }
                delay(backoff(attempts))
            }
        }
    }

    /**
     * 把响应体写入分片文件（追加）。
     *
     * 每读一块都检查取消：暂停一个正在下载的任务必须立即停下，
     * 而不是等他下完当前分片 —— 后者在慢速网络下会有几十秒无响应。
     */
    private suspend fun writeBody(
        input: java.io.InputStream,
        partPath: String,
        startOffset: Long,
        segment: SegmentPlan,
        progress: ProgressTracker,
    ): Long {
        val file = File(partPath)
        file.parentFile?.mkdirs()

        // 追加写入：[startOffset] 是此前已经落盘的长度，
        // 以 append 模式打开即可从文件末尾接着写，不需要 seek
        var written = startOffset
        java.io.FileOutputStream(file, /* append = */ true).use { output ->
            input.use { source ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()

                    // 计划长度是硬上限：服务端偶尔会在 206 里带上超出请求
                    // 范围的数据（某些 CDN 节点会这样）。超出部分直接丢弃 ——
                    // 写进去的话分片会在合并前的完整性校验处被拒，那一次
                    // 下载就白下了
                    val remaining = segment.size - written
                    if (remaining <= 0L) break

                    val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    progress.add(read.toLong())

                    // 兜底哨兵：上面的截断之后 written 理应不会越界，
                    // 保留这条断言，真越界说明写入逻辑出了别的 bug
                    if (written > segment.size) {
                        throw ApiException(
                            ApiException.CODE_LOCAL,
                            "分片 ${segment.index} 收到的数据超出了请求范围",
                            "",
                        )
                    }
                }
            }
        }
        return written
    }

    // -----------------------------------------------------------------------
    // 拼接
    // -----------------------------------------------------------------------

    /**
     * 顺序拼接所有分片，并复核最终大小。
     *
     * 拼接前逐片校验长度、拼接后复核总大小 —— 两道都要有：
     * 前者定位到具体哪一片出了问题，后者兜住拼接过程本身的异常
     * （磁盘写满、IO 错误）。缺了后者，损坏的文件会被当成成功的产物。
     */
    private fun concat(plan: DownloadPlan, supportRange: Boolean) {
        val target = File(plan.segments.first().partPath.removeSuffix(".part0"))

        if (plan.totalBytes <= 0L) {
            // 顺序下载模式：part0 就是完整文件，直接改名
            val part = File(plan.segments.first().partPath)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            return
        }

        val problems = plan.segments.mapNotNull { segment ->
            val file = File(segment.partPath)
            when {
                !file.exists() -> "分片 ${segment.index} 缺失"
                file.length() != segment.size -> "分片 ${segment.index} 长度异常（${file.length()} ≠ ${segment.size}）"
                else -> null
            }
        }
        if (problems.isNotEmpty()) {
            throw ApiException(
                ApiException.CODE_LOCAL,
                "分片不完整，已中止合并：${problems.take(4).joinToString("；")}",
                target.absolutePath,
            )
        }

        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        target.outputStream().use { output ->
            plan.segments.sortedBy { it.index }.forEach { segment ->
                File(segment.partPath).inputStream().use { input ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
        }

        val actual = target.length()
        if (actual != plan.totalBytes) {
            target.delete()
            throw ApiException(
                ApiException.CODE_LOCAL,
                "合并后文件大小异常（$actual ≠ ${plan.totalBytes}），已丢弃损坏文件",
                target.absolutePath,
            )
        }

        // 只有确认成品正确才清理分片
        plan.segments.forEach { segment -> runCatching { File(segment.partPath).delete() } }
    }

    private suspend fun downloadSequential(
        partPath: String,
        progress: ProgressTracker,
    ) {
        val url = urls.firstOrNull()
            ?: throw ApiException(ApiException.CODE_LOCAL, "没有可用的下载地址")
        val response = client.newCall(buildRequest(url, null, null)).execute()
        response.use { r ->
            if (!r.isSuccessful) {
                throw ApiException(r.code, "HTTP ${r.code}", url)
            }
            val length = r.header("Content-Length")?.toLongOrNull() ?: 0L
            val segment = SegmentPlan(0, 0L, (length - 1).coerceAtLeast(0L), partPath)
            File(partPath).parentFile?.mkdirs()
            File(partPath).delete()
            writeBody(r.body.byteStream(), partPath, 0L, segment, progress)
        }
    }

    // -----------------------------------------------------------------------
    // 地址刷新
    // -----------------------------------------------------------------------

    /** 重新解析下载地址。返回是否拿到了新地址。 */
    private suspend fun tryRefresh(): Boolean {
        val refresher = urlRefresher ?: return false
        if (refreshCount >= maxRefreshes) return false
        val fresh = refresher().filter { it.isNotEmpty() }
        refreshCount++
        if (fresh.isEmpty()) return false
        urls = fresh
        return true
    }

    private fun buildRequest(url: String, rangeStart: Long?, rangeEnd: Long?): Request {
        val builder = Request.Builder().url(url)
        headers().forEach { (name, value) -> builder.header(name, value) }
        if (rangeStart != null) {
            builder.header("Range", "bytes=$rangeStart-${rangeEnd ?: ""}")
        }
        return builder.build()
    }

    private fun backoff(attempt: Int): Long =
        Backoff.delayMillis(attempt, baseBackoffMillis, Random.nextDouble())

    /** 可重试的服务端响应：带出原因，供重试耗尽后生成最终错误。 */
    private class RetrySignal(val code: Int, val reason: String) : Exception(reason)

    private fun Long?.orZero(): Long = this ?: 0L

    /**
     * 进度汇总。
     *
     * 节流是必要的：分片并发时每读一块都回调会让上层每秒收到上千次更新，
     * 界面与数据库写入都跟不上，反而拖慢下载。
     */
    private class ProgressTracker(
        private val total: Long,
        initial: Long,
        private val listener: (Long, Long) -> Unit,
    ) {
        private var downloaded = initial
        private var lastEmit = 0L

        fun add(delta: Long) {
            downloaded += delta
            maybeEmit()
        }

        fun emit() {
            lastEmit = downloaded
            listener(downloaded, total)
        }

        private fun maybeEmit() {
            if (downloaded - lastEmit >= EMIT_THRESHOLD) emit()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024

        /** 每累计 256 KB 才回调一次进度。 */
        const val EMIT_THRESHOLD = 256L * 1024
    }
}
