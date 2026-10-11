package com.moko.downkyi.re.core.download

/**
 * 单个分片的规划。
 *
 * [end] 是**闭区间**（与 HTTP Range 头一致），所以长度是 `end - start + 1`。
 * 用闭区间而不是半开区间，是为了让校验逻辑能直接和 `Content-Range`
 * 返回的 `bytes a-b/total` 比对，不必来回换算 —— 换算处最容易差 1。
 */
data class SegmentPlan(
    val index: Int,
    val start: Long,
    val end: Long,
    /** 分片文件路径，形如 `<目标文件>.part0`。 */
    val partPath: String,
) {
    val size: Long get() = end - start + 1
}

/**
 * 一份完整的下载计划。
 */
data class DownloadPlan(
    val totalBytes: Long,
    val chunkSize: Long,
    val segments: List<SegmentPlan>,
) {
    /**
     * 断点签名。
     *
     * 由「总长度 + 分片大小 + 分片数」组成。续传时必须先比对签名：
     * 只要总长度或分片数变了，之前下载的 `.partN` 与当前规划就对不上，
     * 硬用会拼出损坏的文件 —— 宁可丢进度重下，也不能产出坏文件。
     */
    val signature: String get() = "$totalBytes:$chunkSize:${segments.size}"

    val segmentCount: Int get() = segments.size
}

/**
 * 分片规划器。
 *
 * ## 为什么用独立的 `.partN` 而不是共享一个文件句柄
 *
 * 多线程往同一个文件的不同偏移写入需要精确的 `seek`，任何一次错位都会
 * 静默损坏文件；而 `.partN` 各写各的，恢复进度只需看文件长度，
 * 且播放地址过期后重新解析（字节内容不变）依然能续传。
 * 代价是最后要拼接一次，但拼接可以复核大小，反而多了一道保险。
 */
object SegmentPlanner {

    /**
     * 分片数上限。
     *
     * 超过 16 收益很小（瓶颈在服务端与带宽），反而更容易触发风控，
     * 也让断点状态更难管理。
     */
    const val MAX_SEGMENTS = 16

    /**
     * 生成下载计划。
     *
     * [requestedSegments] 会被裁剪到 1..[MAX_SEGMENTS]；
     * [totalBytes] 已知时按向上取整均分，保证所有分片加起来恰好覆盖全文件
     * （最后一片会短一些）。
     */
    fun plan(
        filePath: String,
        totalBytes: Long,
        requestedSegments: Int,
    ): DownloadPlan {
        val count = requestedSegments.coerceIn(1, MAX_SEGMENTS)

        if (totalBytes <= 0L) {
            // 服务端没给总长度：只能单片顺序下载
            return DownloadPlan(
                totalBytes = 0L,
                chunkSize = 0L,
                segments = listOf(
                    SegmentPlan(index = 0, start = 0L, end = -1L, partPath = "$filePath.part0"),
                ),
            )
        }

        val chunk = (totalBytes + count - 1) / count
        val segments = buildList {
            var index = 0
            while (index < count) {
                val start = index * chunk
                if (start >= totalBytes) break
                val end = minOf(totalBytes - 1, start + chunk - 1)
                add(
                    SegmentPlan(
                        index = index,
                        start = start,
                        end = end,
                        partPath = "$filePath.part$index",
                    ),
                )
                index++
            }
        }

        return DownloadPlan(totalBytes = totalBytes, chunkSize = chunk, segments = segments)
    }

    /** 清理可能残留的分片文件路径。 */
    fun partPaths(filePath: String): List<String> =
        (0 until MAX_SEGMENTS).map { "$filePath.part$it" }
}

/**
 * 某个分片在本次续传里的初始状态。
 */
data class SegmentResumeState(
    val index: Int,
    /** 已经成功写入的字节数。 */
    val done: Long,
    /** 是否需要先删除已有的分片文件再重下。 */
    val shouldReset: Boolean,
)

/**
 * 断点判定。
 *
 * 这是续传逻辑里最容易做错的一环，三条规则缺一不可：
 *
 *  1. **签名不一致就全部作废**。总长度或分片数变了意味着旧分片与当前规划
 *     对不上，继续用会拼出损坏文件；
 *  2. **以磁盘上的实际长度为准**，不用内存里记的进度。进程被杀时内存
 *     进度会丢失，而文件长度是唯一的真相；
 *  3. **长度超过该分片应有大小就丢弃**。这说明分片文件被别的内容污染过，
 *     或者上次的分片规划与这次不同，保留它会得到错位的字节。
 */
object ResumePlanner {

    fun resolve(
        plan: DownloadPlan,
        savedSignature: String?,
        actualPartLengths: Map<Int, Long>,
    ): List<SegmentResumeState> {
        val signatureMatches = savedSignature == plan.signature

        return plan.segments.map { segment ->
            if (!signatureMatches) {
                return@map SegmentResumeState(
                    index = segment.index,
                    done = 0L,
                    shouldReset = true,
                )
            }

            val actual = actualPartLengths[segment.index] ?: 0L
            when {
                actual <= 0L -> SegmentResumeState(segment.index, 0L, shouldReset = false)
                actual > segment.size -> SegmentResumeState(segment.index, 0L, shouldReset = true)
                else -> SegmentResumeState(segment.index, actual, shouldReset = false)
            }
        }
    }

    /** 是否所有分片都已下完。 */
    fun isComplete(plan: DownloadPlan, states: List<SegmentResumeState>): Boolean {
        if (plan.segments.isEmpty()) return false
        return plan.segments.all { segment ->
            val done = states.firstOrNull { it.index == segment.index }?.done ?: 0L
            done == segment.size
        }
    }

    /** 已下载字节数合计。 */
    fun downloadedBytes(states: List<SegmentResumeState>): Long = states.sumOf { it.done }
}
