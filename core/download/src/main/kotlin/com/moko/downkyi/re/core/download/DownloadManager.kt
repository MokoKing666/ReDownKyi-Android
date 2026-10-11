package com.moko.downkyi.re.core.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * 下载编排：把一次 [DownloadRequest] 转成对若干 [SegmentDownloader] 的并发调用，
 * 聚合进度并在全部落盘后交给 [Muxer] 封装。
 *
 * 刻意把「编排」和「单段下载」分开：[SegmentDownloader] 只管一段流的分片与续传，
 * 本类只管多路并发、状态机、失败与取消，各自可独立单测。
 */
class DownloadManager(
    private val client: OkHttpClient,
    /** 请求头（cookie/ua/referer），登录后会变，故惰性取而非固定值。 */
    private val headers: () -> Map<String, String> = { emptyMap() },
    private val muxer: Muxer = NoopMuxer,
    private val segmentCount: Int = 8,
    private val maxAttempts: Int = 8,
    private val baseBackoffMillis: Long = 500L,
    /** 地址过期时的刷新器；返回空表示不再重试。 */
    private val urlRefresher: suspend () -> List<String> = { emptyList() },
    parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    private val _tasks = MutableStateFlow<Map<String, DownloadTaskState>>(emptyMap())
    /** 所有任务的快照，key 为任务 id。 */
    val tasks: StateFlow<Map<String, DownloadTaskState>> = _tasks.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    /**
     * 各流最近的下载签名，key 为落盘路径。
     *
     * 取消/暂停只停协程、不删分片；重新 [submit] 时凭这个签名续传。
     * 放在内存里（而不是数据库）是刻意的：签名只对「进程还活着」的续传有意义，
     * 进程死后分片由上层在重建任务时一并处理。
     */
    private val signatures = mutableMapOf<String, String>()

    /** 最近一次提交的请求，key 为任务 id。「重试」靠它原样重提。 */
    private val requests = mutableMapOf<String, DownloadRequest>()

    /** 入队并立即开始。重复 id 视为续传/刷新，先停旧的。 */
    fun submit(request: DownloadRequest) {
        jobs.remove(request.id)?.cancel()
        requests[request.id] = request
        update(request.id) {
            DownloadTaskState(
                id = request.id,
                title = request.title,
                subtitle = request.subtitle,
                status = DownloadStatus.Pending,
            )
        }
        jobs[request.id] = scope.launch {
            update(request.id) { it.copy(status = DownloadStatus.Running) }
            try {
                val paths = runStreams(request)
                val out = request.outputPath
                if (out != null) {
                    update(request.id) { it.copy(status = DownloadStatus.Merging) }
                    muxer.mux(
                        videoPath = paths[StreamKind.Video],
                        audioPath = paths[StreamKind.Audio],
                        outputPath = out,
                    )
                }
                update(request.id) { it.copy(status = DownloadStatus.Completed) }
            } catch (e: CancellationException) {
                update(request.id) { it.copy(status = DownloadStatus.Cancelled) }
                throw e
            } catch (e: Exception) {
                update(request.id) {
                    it.copy(
                        status = DownloadStatus.Failed,
                        errorMessage = e.message ?: e::class.simpleName,
                    )
                }
            }
        }
    }

    /** 取消任务；分片保留在磁盘上，续传靠 resumeSignature。 */
    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
    }

    fun pause(id: String) = cancel(id)

    /** 原样重提上次的请求。没有记录（从未提交过）时静默忽略。 */
    fun retry(id: String) {
        requests[id]?.let { submit(it) }
    }

    /**
     * 恢复一个持久化的任务：只登记请求、断点签名与「已暂停」状态，**不启动下载**。
     *
     * 进程重启后分片文件还在工作目录里，凭持久化的签名即可续传；
     * 是否继续由用户决定（点「继续」走 [retry]）—— 静默自动开跑
     * 会在用户不知情时消耗流量与电量。
     */
    fun restore(request: DownloadRequest, signatures: Map<String, String>) {
        this.signatures.putAll(signatures)
        requests[request.id] = request
        update(request.id) {
            DownloadTaskState(
                id = request.id,
                title = request.title,
                subtitle = request.subtitle,
                status = DownloadStatus.Paused,
            )
        }
    }

    /** 各流当前的断点签名快照（按落盘路径），供上层持久化。 */
    fun signatureSnapshot(): Map<String, String> = signatures.toMap()

    /** 指定任务的断点签名快照（只含该任务流的路径键）。 */
    fun signatureSnapshot(id: String): Map<String, String> =
        requests[id]?.streams
            ?.map { it.path }
            ?.mapNotNull { path -> signatures[path]?.let { path to it } }
            ?.toMap()
            ?: emptyMap()

    /**
     * 彻底移除任务：停协程、丢签名与请求记录、从状态表里消失。
     *
     * 分片文件的删除归上层（它知道工作目录边界）；这里只清内存痕迹。
     */
    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        requests.remove(id)?.streams?.forEach { signatures.remove(it.path) }
        _tasks.update { it - id }
    }

    private suspend fun runStreams(request: DownloadRequest): Map<StreamKind, String> {
        val paths = mutableMapOf<StreamKind, String>()
        // 顺序下载而非并发：两路各自分片已并发（segmentCount），再叠一层并发收益小、
        // 却会让进度聚合与失败语义复杂化。
        for (target in request.streams) {
            paths[target.kind] = downloadOne(request, target)
        }
        return paths
    }

    private suspend fun downloadOne(request: DownloadRequest, target: StreamTarget): String {
        val downloader = SegmentDownloader(
            client = client,
            initialUrls = target.urls,
            headers = headers,
            urlRefresher = urlRefresher,
            segmentCount = segmentCount,
            maxAttempts = maxAttempts,
            baseBackoffMillis = baseBackoffMillis,
        )
        downloader.download(
            targetPath = target.path,
            resumeSignature = signatures[target.path],
            onSignature = { signature -> signatures[target.path] = signature },
            onProgress = { downloaded, total ->
                update(request.id) { cur ->
                    cur.copy(
                        streams = upsertProgress(
                            cur.streams, StreamProgress(target.kind, downloaded, total),
                        ),
                    )
                }
            },
        )
        return target.path
    }

    private fun upsertProgress(
        list: List<StreamProgress>,
        p: StreamProgress,
    ): List<StreamProgress> = list.filterNot { it.kind == p.kind } + p

    /**
     * 对单个任务做原子更新。
     *
     * 进度回调来自**并发的分片 worker**（每个 worker 一个 IO 线程），
     * 而「读整个表 → 拷贝 → 写回」不是原子的 —— 两个回调交错时后写者会
     * 用自己的旧快照覆盖前者的更新，表现是偶发的进度回退或丢一段。
     * [MutableStateFlow.update] 的 CAS 循环保证每次更新都基于最新值。
     */
    private fun update(id: String, transform: (DownloadTaskState) -> DownloadTaskState) {
        _tasks.update { map ->
            val cur = map[id]
                ?: DownloadTaskState(id = id, title = id, status = DownloadStatus.Pending)
            map + (id to transform(cur))
        }
    }
}
