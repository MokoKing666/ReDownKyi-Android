package com.moko.downkyi.re.data

import com.moko.downkyi.re.core.download.DownloadManager
import com.moko.downkyi.re.core.download.DownloadRequest
import com.moko.downkyi.re.core.download.DownloadStatus
import com.moko.downkyi.re.core.download.StreamKind
import com.moko.downkyi.re.core.download.StreamTarget
import com.moko.downkyi.re.core.model.DownloadFlags
import com.moko.downkyi.re.core.model.SaveLocation
import com.moko.downkyi.re.core.model.VideoDetail
import com.moko.downkyi.re.core.model.VideoQuality
import com.moko.downkyi.re.core.network.ApiException
import com.moko.downkyi.re.core.network.BiliApi
import com.moko.downkyi.re.core.network.BiliHttp
import com.moko.downkyi.re.download.DownloadStorage
import com.moko.downkyi.re.download.MediaKind
import com.moko.downkyi.re.ui.detail.ParseSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 下载链路的业务决策层：把「用户在解析结果页的选择」变成若干个
 * [DownloadRequest] 并交给 [DownloadManager]，任务完成后把成品发布到保存位置。
 *
 * 为什么这一层存在（而不是让 UI 直接拼请求）：
 *  - **按 cid 逐个取 DASH**：多 P 下载要对每个勾选的分 P 各请求一次播放地址，
 *    拿主视频的 cid 去下 P3 会安静地拿到 P1 的内容；
 *  - **档位回退**：选项面板给的是「参考分 P」支持的档位，其余分 P 未必有同一档，
 *    `DashInfo.pickVideo` 会各自回退到可用的最高档，而不是整条任务失败；
 *  - **成品发布时机**：只有全部流落盘且封装成功后才发布，失败与取消的
 *    分片留在工作目录里供续传。
 *
 * 字幕（[DownloadFlags.SUBTITLE]）尚未接入：字幕接口走另一条 player/wbi 链路，
 * 接入前勾选该位会被忽略，其余内容照常下载。
 */
class DownloadRepository(
    private val parse: ParseRepository,
    private val api: BiliApi,
    private val http: BiliHttp,
    private val manager: DownloadManager,
    private val storage: DownloadStorage,
    private val persistence: TaskPersistence,
    parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    /** 任务 id → 待发布产物。发布前先移除，保证同一任务只发布一次。 */
    private val pending = mutableMapOf<String, PendingPublish>()

    init {
        // 先恢复持久化的任务（以「已暂停」状态回到列表，分片与签名都在），
        // 再监听完成事件 —— 顺序反过来会让恢复任务缺失待发布登记
        scope.launch { restoreAll() }
        scope.launch {
            manager.tasks.collect { states ->
                states.entries
                    .filter { it.value.status == DownloadStatus.Completed }
                    .mapNotNull { entry -> pending.remove(entry.key)?.let { entry.key to it } }
                    .forEach { (id, job) ->
                        persistence.delete(id)
                        publish(id, job)
                    }
            }
        }
    }

    /**
     * 按用户的选择开始下载。
     *
     * 挂起直到所有选中分 P 的任务都已入队（要逐个取 DASH）；
     * 抛出的异常是「整次点击失败」的语义，调用方据此提示用户。
     */
    suspend fun start(detail: VideoDetail, selection: ParseSelection) {
        if (!DownloadFlags.hasAnyMedia(selection.flags)) {
            throw ApiException(
                ApiException.CODE_LOCAL,
                "至少要选择视频或音频中的一种，否则没有可下载的内容",
            )
        }

        val quality = VideoQuality.fromQn(selection.qn) ?: VideoQuality.Q1080P
        val baseKey = detail.bvid.ifBlank { "ep${detail.epId ?: 0}" }

        for (index in selection.pageIndices) {
            val cid = detail.cidAt(index)
            val taskId = "$baseKey-$cid-${selection.qn}-${selection.flags}"
            val dash = parse.dash(detail, index, quality)

            val video = if (DownloadFlags.has(selection.flags, DownloadFlags.VIDEO)) {
                dash.pickVideo(selection.qn, selection.codec)
            } else {
                null
            }
            val audio = if (DownloadFlags.has(selection.flags, DownloadFlags.AUDIO)) {
                dash.pickAudio(selection.audioId)
            } else {
                null
            }
            if (video == null && audio == null) {
                throw ApiException(
                    ApiException.CODE_LOCAL,
                    "「${detail.pageTitle(index)}」没有可用的媒体流，可能已被下架或无权限访问",
                )
            }

            val work = storage.workDir(taskId)
            val safeTitle = sanitize(detail.pageTitle(index))

            val streams = buildList {
                video?.let {
                    add(
                        StreamTarget(
                            kind = StreamKind.Video,
                            urls = it.allUrls,
                            path = File(work, "$safeTitle.video.m4s").absolutePath,
                        ),
                    )
                }
                audio?.let {
                    add(
                        StreamTarget(
                            kind = StreamKind.Audio,
                            urls = it.allUrls,
                            path = File(work, "$safeTitle.audio.m4s").absolutePath,
                        ),
                    )
                }
            }

            // 只下音频时成品是 m4a；有视频轨才谈得上 mp4
            val outputName = if (video != null) "$safeTitle.mp4" else "$safeTitle.m4a"
            val artifacts = buildList {
                video?.let { add(PendingArtifact(File(work, outputName), MediaKind.VIDEO)) }
                audio?.takeIf { video == null }?.let {
                    add(PendingArtifact(File(work, outputName), MediaKind.AUDIO))
                }
            }

            // 封面与弹幕是小文件，不走分片引擎；失败不连坐媒体流
            val extras = mutableListOf<PendingArtifact>()
            if (DownloadFlags.has(selection.flags, DownloadFlags.COVER) && detail.cover.isNotBlank()) {
                runCatching {
                    val bytes = http.getBytes(detail.cover)
                    extras += PendingArtifact(
                        File(work, "$safeTitle.cover.jpg").apply { writeBytes(bytes) },
                        MediaKind.COVER,
                    )
                }
            }
            if (DownloadFlags.has(selection.flags, DownloadFlags.DANMAKU)) {
                runCatching {
                    val xml = api.danmakuXml(cid)
                    extras += PendingArtifact(
                        File(work, "$safeTitle.danmaku.xml").apply { writeText(xml) },
                        MediaKind.DANMAKU,
                    )
                }
            }

            pending[taskId] = PendingPublish(
                fileName = safeTitle,
                location = selection.saveLocation,
                artifacts = artifacts + extras,
            )

            // 任务快照落盘：进程被杀后凭它恢复为「已暂停」并可续传。
            // 断点签名为空（尚未开始下载），运行中由 pause 时更新。
            persistence.save(
                PersistedDownload(
                    id = taskId,
                    title = detail.pageTitle(index),
                    subtitle = pageSubtitle(detail, index, selection.qn),
                    streams = streams.map {
                        PersistedStream(kind = it.kind.name, path = it.path, urls = it.urls)
                    },
                    publishFileName = safeTitle,
                    publishLocation = selection.saveLocation.name,
                    extras = extras.map {
                        PersistedExtra(path = it.file.absolutePath, kind = it.kind.name)
                    },
                ),
            )

            manager.submit(
                DownloadRequest(
                    id = taskId,
                    title = detail.pageTitle(index),
                    subtitle = pageSubtitle(detail, index, selection.qn),
                    streams = streams,
                    outputPath = if (video != null || audio != null) {
                        File(work, outputName).absolutePath
                    } else {
                        null
                    },
                ),
            )
        }
    }

    /** 暂停（分片保留，凭签名续传）。暂停时把最新断点签名写进快照。 */
    fun pause(taskId: String) {
        manager.pause(taskId)
        persistSignatures(taskId)
    }

    fun cancel(taskId: String) = manager.cancel(taskId)

    /** 原样重提失败的任务。 */
    fun retry(taskId: String) = manager.retry(taskId)

    /** 移除任务并清掉它的工作目录（分片与半成品）与持久化快照。 */
    fun delete(taskId: String) {
        manager.remove(taskId)
        persistence.delete(taskId)
        scope.launch { storage.cleanup(taskId) }
    }

    val tasks get() = manager.tasks

    /**
     * 已发布成品的最终位置（按任务 id）。
     *
     * 只记本次会话：重启后「已完成」任务不再出现在任务列表，
     * 产物统一由媒体库承载。值为 content:// 时可直接用 ACTION_VIEW 打开；
     * 应用目录内的文件路径需要 FileProvider，暂不支持一键打开。
     */
    private val publishedLocations = mutableMapOf<String, String>()

    fun publishedLocationOf(taskId: String): String? = publishedLocations[taskId]

    // -----------------------------------------------------------------------

    /**
     * 恢复持久化的任务。
     *
     * 恢复为「已暂停」而不是直接开跑：重启意味着上一场会话被打断，
     * 用户可能换了网络环境或在流量下 —— 是否继续由用户点一下。
     * 分片与断点签名都从快照还原，点「继续」不会从头重下。
     */
    private suspend fun restoreAll() {
        persistence.loadAll().forEach { p ->
            val request = DownloadRequest(
                id = p.id,
                title = p.title,
                subtitle = p.subtitle,
                streams = p.streams.map {
                    StreamTarget(
                        kind = StreamKind.valueOf(it.kind),
                        urls = it.urls,
                        path = it.path,
                    )
                },
                outputPath = p.outputPath,
            )

            val hasVideo = p.streams.any { it.kind == StreamKind.Video.name }
            val mediaKind = if (hasVideo) MediaKind.VIDEO else MediaKind.AUDIO
            val artifacts = buildList {
                p.outputPath?.let { add(PendingArtifact(File(it), mediaKind)) }
                p.extras.forEach { extra ->
                    add(
                        PendingArtifact(
                            File(extra.path),
                            MediaKind.valueOf(extra.kind),
                        ),
                    )
                }
            }
            pending[p.id] = PendingPublish(
                fileName = p.publishFileName,
                location = SaveLocation.valueOf(p.publishLocation),
                artifacts = artifacts,
            )

            manager.restore(request, p.signatures)
        }
    }

    /** 把该任务当前的断点签名写进持久化快照。 */
    private fun persistSignatures(taskId: String) {
        persistence.updateSignatures(taskId, manager.signatureSnapshot(taskId))
    }

    private suspend fun publish(taskId: String, job: PendingPublish) {
        for (artifact in job.artifacts) {
            if (!artifact.file.exists()) continue
            runCatching {
                val published = storage.publish(
                    artifact.file,
                    artifact.fileName(job),
                    artifact.kind,
                    job.location,
                )
                // 只记媒体主产物的位置；封面/弹幕随产物一起发布，不单独打开
                if (artifact.kind == MediaKind.VIDEO || artifact.kind == MediaKind.AUDIO) {
                    publishedLocations[taskId] = published
                }
            }
        }
        storage.cleanup(taskId)
    }

    private fun PendingArtifact.fileName(job: PendingPublish): String = when (kind) {
        MediaKind.VIDEO, MediaKind.AUDIO -> job.fileName
        MediaKind.COVER -> "${job.fileName} 封面"
        MediaKind.DANMAKU -> "${job.fileName} 弹幕"
    }

    /** 「P2 · 1080P 高清」一类的展示副标题。 */
    private fun pageSubtitle(detail: VideoDetail, index: Int, qn: Int): String {
        val page = detail.pages.getOrNull(index)?.page ?: (index + 1)
        val quality = VideoQuality.fromQn(qn)?.label ?: "qn=$qn"
        return "P$page · $quality"
    }

    private data class PendingArtifact(val file: File, val kind: MediaKind)

    private data class PendingPublish(
        val fileName: String,
        val location: SaveLocation,
        val artifacts: List<PendingArtifact>,
    )

    private companion object {
        fun sanitize(name: String): String =
            name.replace(ILLEGAL, "_").trim().ifBlank { "未命名" }

        val ILLEGAL = Regex("""[\\/:*?"<>|]""")
    }
}
