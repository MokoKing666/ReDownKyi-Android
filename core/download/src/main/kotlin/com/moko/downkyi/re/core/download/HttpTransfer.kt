package com.moko.downkyi.re.core.download

/**
 * HTTP 状态码的处理分类。
 *
 * 为什么需要分类而不是「失败就重试」：B 站的播放地址**带时效签名**，
 * 过期后返回 403 / 404。对这种状态继续按原地址重试是纯浪费 ——
 * 正确的动作是重新解析一次播放地址。而 500 类是服务端临时问题，
 * 重试才有意义。混在一起处理的典型症状是「重试 8 次后失败」，
 * 用户等了很久却什么也没发生。
 */
enum class HttpStatusClass {
    /** 2xx：请求成功。 */
    Success,

    /** 地址已过期：应重新解析播放地址，而不是原地重试。 */
    UrlExpired,

    /** 可重试：服务端临时问题或限流。 */
    Retryable,

    /** 不可重试：参数错误、权限不足。立即失败比反复重试更诚实。 */
    Fatal,
}

/**
 * 对 HTTP 状态码分类。
 *
 * **必须显式覆盖全部区间，不能用 `else` 兜底成 Fatal。**
 * 曾经的写法只列举了错误码，把 200 / 206 留给了 `else` ——
 * 于是一个正常的 206 响应会被判成"不可重试的错误"，
 * 探测阶段直接抛异常，表现为「所有下载都无法开始」。
 */
fun classifyStatus(status: Int): HttpStatusClass = when {
    status in 200..299 -> HttpStatusClass.Success

    // 播放地址过期
    status == 403 || status == 404 || status == 410 -> HttpStatusClass.UrlExpired

    // 限流与服务端临时故障。
    // 416 也放在这里：它可能意味着「我要的那段已经下完了」，
    // 由调用方核对本地长度后再决定，比直接判死更合适
    status == 408 || status == 429 || status == 416 -> HttpStatusClass.Retryable
    status in 500..599 -> HttpStatusClass.Retryable

    else -> HttpStatusClass.Fatal
}

/**
 * `Content-Range` 的解析结果。
 *
 * [total] 为 null 表示服务端用 `*` 表示"总长未知"。
 */
data class ContentRange(
    val start: Long,
    val end: Long,
    val total: Long?,
)

object ContentRangeParser {
    private val PATTERN = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")

    fun parse(header: String?): ContentRange? {
        if (header.isNullOrBlank()) return null
        val match = PATTERN.find(header) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val totalText = match.groupValues[3]
        val total = if (totalText == "*") null else totalText.toLongOrNull() ?: return null
        return ContentRange(start, end, total)
    }
}

/**
 * Range 响应校验结果。
 */
sealed interface RangeCheck {

    data class Accepted(
        val start: Long,
        val end: Long,
        /** 服务端声明的总长度；`*` 或缺失时为 null。 */
        val total: Long?,
    ) : RangeCheck

    data class Rejected(val reason: String) : RangeCheck
}

/**
 * 分片响应的严格校验。
 *
 * ## 为什么必须严格
 *
 * 服务端**忽略 Range** 时会返回 200 + 整个文件。如果不检查，这段完整文件
 * 会被写进某个分片的 `.partN` 里 —— 结果是拼接出来的文件大小翻几倍、
 * 内容错位，而**下载过程全程显示成功**。这类损坏极难定位，因为
 * 「下载成功但播放损坏」会被归咎于播放器或编码问题。
 *
 * 因此这里的策略是：任何不确定的情况一律拒绝，宁可重下一次。
 */
object RangeValidator {

    fun validate(
        status: Int,
        contentRangeHeader: String?,
        requestedStart: Long,
        requestedEnd: Long,
        expectedTotal: Long,
        segmentIndex: Int,
    ): RangeCheck {
        if (status == 200) {
            // 200 只在一种情况下可接受：服务端本来就不支持 Range，
            // 而我们请求的正是「单片、从 0 开始、覆盖整个文件」。
            // 此时这个响应确实就是我们要的完整内容
            val coversWholeFile = requestedStart == 0L &&
                expectedTotal > 0L &&
                requestedEnd == expectedTotal - 1
            return if (coversWholeFile) {
                RangeCheck.Accepted(start = 0L, end = expectedTotal - 1, total = expectedTotal)
            } else {
                RangeCheck.Rejected(
                    "服务端忽略了 Range 请求并返回了完整文件（分片 $segmentIndex），已中止以免产生损坏文件",
                )
            }
        }

        if (status != 206) {
            return RangeCheck.Rejected("HTTP $status")
        }

        if (contentRangeHeader.isNullOrBlank()) {
            return RangeCheck.Rejected("206 响应缺少 Content-Range 头")
        }

        val range = ContentRangeParser.parse(contentRangeHeader)
            ?: return RangeCheck.Rejected("Content-Range 格式无法解析：$contentRangeHeader")

        if (range.start != requestedStart) {
            return RangeCheck.Rejected(
                "Content-Range 起点不符：请求 $requestedStart，返回 ${range.start}",
            )
        }

        // 终点超出请求范围**接受而不是拒绝**：部分 CDN 节点会把响应一路
        // 发到文件末尾（真机实测踩过）。只要起点正确，响应就是请求范围的
        // 完整连续前缀，多余的字节由写侧按分片计划长度截断。
        // 若仍然拒绝，这类源会永远下不完 —— 重试拿到的还是同一个不合规响应。

        // 总长变化说明地址指向的内容变了（例如视频被替换），
        // 继续拼下去会得到前后不一致的文件
        val total = range.total
        if (total != null && total > 0L && expectedTotal > 0L && total != expectedTotal) {
            return RangeCheck.Rejected("Content-Range 总长变化：原 $expectedTotal，现 $total")
        }

        return RangeCheck.Accepted(start = range.start, end = range.end, total = total)
    }
}

/**
 * 退避时长计算。
 *
 * 指数退避加抖动。抖动是必须的：多个分片同时失败时，如果退避时长完全一致，
 * 它们会在同一毫秒一起重试，把服务端再打一遍 —— 这正是触发风控的典型模式。
 */
object Backoff {

    fun delayMillis(
        attempt: Int,
        baseMillis: Long = 500L,
        randomFraction: Double,
    ): Long {
        val step = baseMillis.coerceAtLeast(1L)
        val exponent = (attempt - 1).coerceIn(0, 6)
        val exponential = step * (1L shl exponent)
        val capped = minOf(exponential, step * 64)
        val jitter = (step * randomFraction.coerceIn(0.0, 1.0)).toLong()
        return capped + jitter
    }
}
