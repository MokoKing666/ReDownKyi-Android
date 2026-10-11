package com.moko.downkyi.re.core.model

/**
 * 分 P 信息。
 *
 * 多 P 视频的每个分 P 都有自己的 cid，而播放地址是**按 cid** 请求的 ——
 * 拿主视频的 cid 去下 P3 会安静的拿到 P1 的内容。
 */
data class VideoPageInfo(
    val cid: Long,
    val page: Int,
    val part: String,
    val durationMs: Long,
)

/**
 * 视频详情。
 *
 * 同一个类型承载三种来源：普通稿件（`/x/web-interface/view`）、
 * 番剧单集、课程单集。它们靠 [btype] 与 [epId] 区分 ——
 * 番剧没有 bvid（只有 epid），而播放地址接口在两种情况下完全不同。
 */
data class VideoDetail(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val cover: String,
    val ownerName: String,
    val ownerMid: Long,
    val durationMs: Long,
    val desc: String = "",
    val tname: String = "",
    val view: Int = 0,
    val danmakuCount: Int = 0,
    val pages: List<VideoPageInfo> = emptyList(),
    val btype: String = BiliConstants.TYPE_VIDEO,
    /** 番剧 / 课程单集 id。普通稿件为 null。 */
    val epId: Long? = null,
) {

    /** 是否为番剧或课程（决定播放地址走哪个接口）。 */
    val isEpisode: Boolean
        get() = btype == BiliConstants.TYPE_BANGUMI || btype == BiliConstants.TYPE_CHEESE

    /** 是否为多 P 视频。 */
    val isMultiPage: Boolean get() = pages.size > 1

    /**
     * 某个分 P 在下载任务里的标题。
     *
     * 多 P 时拼成「主标题 - 分 P 名」，单 P 时直接用主标题 ——
     * 否则多 P 视频的所有分 P 会得到同一个文件名。
     */
    fun pageTitle(index: Int): String {
        if (!isMultiPage) return title
        val page = pages.getOrNull(index) ?: return title
        val part = page.part.ifBlank { "P${page.page}" }
        return if (part.isBlank() || part == title) title else "$title - $part"
    }

    /** 计算某个分 P 对应的 cid。索引越界时回退到主 cid。 */
    fun cidAt(index: Int): Long = pages.getOrNull(index)?.cid ?: cid

    /** 按 cid 反查分 P 索引。用于「按 cid 恢复任务」这类场景。 */
    fun indexOfCid(cid: Long): Int = pages.indexOfFirst { it.cid == cid }
}

/**
 * 批量列表里的单个条目。
 *
 * 与 [VideoDetail] 的区别：列表接口**不返回**分 P 信息，也常常不返回 cid
 * （收藏夹放在 `ugc.first_cid`，UP 主投稿与合集接口根本不返回）。
 * 因此 [cid] 允许为 0，由下载流程在真正开始时补查详情。
 */
data class MediaItem(
    val bvid: String,
    val cid: Long,
    val aid: Long,
    val title: String,
    val cover: String,
    val durationMs: Long,
    val ownerName: String = "",
    val btype: String = BiliConstants.TYPE_VIDEO,
    val epId: Long? = null,
) {
    /** 能否直接请求播放地址。false 时需要先补查 cid。 */
    val hasPlayableId: Boolean
        get() = if (epId != null && epId > 0) true else cid > 0 && bvid.isNotEmpty()
}

/**
 * 批量解析结果。
 *
 * [hasMore] 与 [page] 保证分页语义 —— 只有收藏夹 / UP 主投稿 / 合集
 * 这三类接口支持翻页，历史与稍后再看是整包返回。
 */
data class BatchResult(
    val title: String,
    val items: List<MediaItem>,
    val cover: String = "",
    val btype: String = BiliConstants.TYPE_VIDEO,
    val mid: Long? = null,
    val seasonId: Long? = null,
    val mediaId: Long? = null,
    val hasMore: Boolean = false,
    val page: Int = 1,
)

/**
 * 一次解析的产出：要么是单个视频，要么是批量列表。
 */
data class ParseOutcome(
    val video: VideoDetail? = null,
    val batch: BatchResult? = null,
) {
    val isEmpty: Boolean get() = video == null && batch == null
}
