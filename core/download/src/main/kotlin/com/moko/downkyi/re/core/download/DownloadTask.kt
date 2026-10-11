package com.moko.downkyi.re.core.download

/** 媒体流种类。B 站 DASH 音视频分离，封装前各自是一个 [StreamTarget]。 */
enum class StreamKind { Video, Audio }

/**
 * 一次要落盘的单个媒体流。
 *
 * [urls] 是备用地址列表：同一段流有多个 CDN 节点，第一个失效时依次尝试，
 * 全失效才去刷新 —— 换节点比整段重解析便宜得多。
 */
data class StreamTarget(
    val kind: StreamKind,
    val urls: List<String>,
    /** 落盘路径。 */
    val path: String,
    /** 预期字节数，探测前为 0，仅用于进度显示。 */
    val expectedBytes: Long = 0L,
)

/**
 * 一个完整任务的输入：1~2 个流 + 可选封装输出。
 *
 * 用列表而非写死 video/audio 两个字段，是为了兼容「只下音频」和
 * 「durl 单文件直链」这两种只有一路的情况。
 */
data class DownloadRequest(
    /** 任务唯一 id，由上层按 (bvid, cid, 清晰度, 内容选项) 生成。 */
    val id: String,
    /** 显示名。 */
    val title: String,
    /** 副标题（分 P / 清晰度一类）。它属于展示信息，但与 [title] 同源，故一并携带。 */
    val subtitle: String = "",
    val streams: List<StreamTarget>,
    /** 封装输出路径。为 null 表示不合并（只下一路或由上层另行处理）。 */
    val outputPath: String? = null,
)

/** 单个流的进度。 */
data class StreamProgress(
    val kind: StreamKind,
    val downloaded: Long = 0L,
    val total: Long = 0L,
)

/** 任务状态机。Paused/Failed 都是需要继续的正常态，不是错误。 */
enum class DownloadStatus { Pending, Running, Merging, Completed, Paused, Failed, Cancelled }

/** 任务对外暴露的聚合状态。 */
data class DownloadTaskState(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val status: DownloadStatus,
    val streams: List<StreamProgress> = emptyList(),
    val errorMessage: String? = null,
) {
    val downloadedBytes: Long get() = streams.sumOf { it.downloaded }
    val totalBytes: Long get() = streams.sumOf { it.total }
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/**
 * 合并（封装）接口。
 *
 * 抽成接口而不是直接调 MediaMuxer：封装是 Android 能力，而下载编排这层要能在
 * 纯 JVM 上单测；FFmpeg 备用实现也能替换。
 */
fun interface Muxer {
    /** 把视频/音频两路封装到 [outputPath]。某一路为 null 表示缺该路。 */
    suspend fun mux(videoPath: String?, audioPath: String?, outputPath: String)
}

/** 只下一路、无需合并时的空实现。 */
object NoopMuxer : Muxer {
    override suspend fun mux(videoPath: String?, audioPath: String?, outputPath: String) = Unit
}
