package com.moko.downkyi.re.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 状态分类、Content-Range 校验与退避。
 *
 * 校验这一层的存在意义是：服务端忽略 Range 时会返回 200 + 整个文件，
 * 不检查就会把完整文件写进某个分片里，最终产出**大小翻倍、内容错位**
 * 但全程显示成功的文件。所以这里对"不确定的情况"一律要求拒绝。
 */
class HttpTransferTest {

    // ---- 状态分类 ----

    @Test
    fun `2xx 被视为成功而不是错误`() {
        // 固定一个真实出现过的缺陷：早先的分类只列举了错误码，
        // 200 / 206 落到 else 分支被当成 Fatal，于是探测拿到正常的 206
        // 就抛异常 —— 表现是「所有下载都无法开始」，而错误信息只有一句 HTTP 206
        assertEquals(HttpStatusClass.Success, classifyStatus(200))
        assertEquals(HttpStatusClass.Success, classifyStatus(206))
        assertEquals(HttpStatusClass.Success, classifyStatus(204))
    }

    @Test
    fun `地址过期的状态码归类为需要重新解析`() {
        assertEquals(HttpStatusClass.UrlExpired, classifyStatus(403))
        assertEquals(HttpStatusClass.UrlExpired, classifyStatus(404))
        assertEquals(HttpStatusClass.UrlExpired, classifyStatus(410))
    }

    @Test
    fun `限流与服务端临时故障可重试`() {
        listOf(408, 429, 500, 502, 503, 504).forEach { status ->
            assertEquals("status=$status", HttpStatusClass.Retryable, classifyStatus(status))
        }
    }

    @Test
    fun `416 归类为可重试`() {
        // 416 可能意味着「我要的那段已经下完了」，由调用方核对本地长度后再决定
        assertEquals(HttpStatusClass.Retryable, classifyStatus(416))
    }

    @Test
    fun `未列出的 5xx 仍可重试`() {
        assertEquals(HttpStatusClass.Retryable, classifyStatus(599))
        assertEquals(HttpStatusClass.Retryable, classifyStatus(520))
    }

    @Test
    fun `参数或权限类错误不重试`() {
        listOf(400, 401, 402, 405, 422).forEach { status ->
            assertEquals("status=$status", HttpStatusClass.Fatal, classifyStatus(status))
        }
    }

    // ---- Content-Range 解析 ----

    @Test
    fun `解析标准的 Content-Range`() {
        val range = ContentRangeParser.parse("bytes 0-249/1000")
        assertEquals(0L, range?.start)
        assertEquals(249L, range?.end)
        assertEquals(1000L, range?.total)
    }

    @Test
    fun `总长为星号时视为未知`() {
        assertNull(ContentRangeParser.parse("bytes 0-249/*")?.total)
    }

    @Test
    fun `格式不符时返回 null 而不是猜测`() {
        assertNull(ContentRangeParser.parse(null))
        assertNull(ContentRangeParser.parse(""))
        assertNull(ContentRangeParser.parse("0-249/1000"))
        assertNull(ContentRangeParser.parse("items 0-249/1000"))
    }

    // ---- Range 响应校验 ----

    private fun validate(
        status: Int,
        contentRange: String? = null,
        requestedStart: Long = 0,
        requestedEnd: Long = 249,
        expectedTotal: Long = 1000,
        segmentIndex: Int = 0,
    ) = RangeValidator.validate(
        status = status,
        contentRangeHeader = contentRange,
        requestedStart = requestedStart,
        requestedEnd = requestedEnd,
        expectedTotal = expectedTotal,
        segmentIndex = segmentIndex,
    )

    @Test
    fun `正常的 206 响应被接受`() {
        val result = validate(206, "bytes 0-249/1000")
        assertTrue(result is RangeCheck.Accepted)
        assertEquals(1000L, (result as RangeCheck.Accepted).total)
    }

    @Test
    fun `服务端返回部分范围时按实际范围接受`() {
        // 只要不超过请求范围，返回更少的字节是允许的
        val result = validate(206, "bytes 0-99/1000")
        assertTrue(result is RangeCheck.Accepted)
        assertEquals(99L, (result as RangeCheck.Accepted).end)
    }

    @Test
    fun `206 缺少 Content-Range 时拒绝`() {
        val result = validate(206, null)
        assertTrue(result is RangeCheck.Rejected)
        assertTrue((result as RangeCheck.Rejected).reason.contains("Content-Range"))
    }

    @Test
    fun `Content-Range 无法解析时拒绝`() {
        assertTrue(validate(206, "garbage") is RangeCheck.Rejected)
    }

    @Test
    fun `Content-Range 起点不符时拒绝`() {
        // 服务端返回了别的区间，写进这个分片会让字节错位
        val result = validate(206, "bytes 500-749/1000")
        assertTrue(result is RangeCheck.Rejected)
        assertTrue((result as RangeCheck.Rejected).reason.contains("起点不符"))
    }

    @Test
    fun `Content-Range 终点超出请求范围时接受并由写侧截断`() {
        // 部分 CDN 会把响应一路发到文件末尾。只要起点正确，请求范围内的
        // 字节就是完整的连续前缀；拒绝会让这类源永远下不完
        val result = validate(206, "bytes 0-999/1000", requestedEnd = 249)
        assertTrue(result is RangeCheck.Accepted)
        assertEquals(0L, (result as RangeCheck.Accepted).start)
        assertEquals(999L, result.end)
    }

    @Test
    fun `Content-Range 总长变化时拒绝`() {
        // 总长变了说明地址指向的内容变了，继续拼会得到前后不一致的文件
        val result = validate(206, "bytes 0-249/2000")
        assertTrue(result is RangeCheck.Rejected)
        assertTrue((result as RangeCheck.Rejected).reason.contains("总长变化"))
    }

    @Test
    fun `总长为未知时不做总长比对`() {
        assertTrue(validate(206, "bytes 0-249/*") is RangeCheck.Accepted)
    }

    @Test
    fun `服务端忽略 Range 返回完整文件时拒绝`() {
        // 这是最危险的情况：完整文件会被写进一个分片里
        val result = validate(200)
        assertTrue(result is RangeCheck.Rejected)
        assertTrue((result as RangeCheck.Rejected).reason.contains("忽略了 Range"))
    }

    @Test
    fun `单分片且请求整个文件时接受 200 响应`() {
        // 服务端不支持 Range 是允许的，此时它返回的就是我们要的内容
        val plan = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 1)
        val segment = plan.segments.single()
        val result = validate(
            status = 200,
            requestedStart = segment.start,
            requestedEnd = segment.end,
            expectedTotal = plan.totalBytes,
        )
        assertTrue(result is RangeCheck.Accepted)
        assertEquals(1000L, (result as RangeCheck.Accepted).total)
    }

    @Test
    fun `多分片时即便从零请求也不接受 200`() {
        // 第二片请求的是中间一段，服务端返回完整文件绝不能接受
        val result = validate(200, requestedStart = 250, requestedEnd = 499)
        assertTrue(result is RangeCheck.Rejected)
    }

    @Test
    fun `非 200 非 206 的状态一律拒绝`() {
        assertTrue(validate(416) is RangeCheck.Rejected)
        assertTrue(validate(500) is RangeCheck.Rejected)
        assertTrue(validate(403) is RangeCheck.Rejected)
    }

    // ---- 退避 ----

    @Test
    fun `退避时长按指数增长`() {
        val first = Backoff.delayMillis(attempt = 1, baseMillis = 500, randomFraction = 0.0)
        val second = Backoff.delayMillis(attempt = 2, baseMillis = 500, randomFraction = 0.0)
        val third = Backoff.delayMillis(attempt = 3, baseMillis = 500, randomFraction = 0.0)
        assertEquals(500L, first)
        assertEquals(1000L, second)
        assertEquals(2000L, third)
    }

    @Test
    fun `退避时长有上限`() {
        val huge = Backoff.delayMillis(attempt = 30, baseMillis = 500, randomFraction = 0.0)
        assertEquals(500L * 64, huge)
    }

    @Test
    fun `抖动让同一轮次的重试错开`() {
        // 多个分片同时失败时，若退避完全一致会在同一毫秒一起重试，把服务端再打一遍
        val withoutJitter = Backoff.delayMillis(attempt = 3, baseMillis = 500, randomFraction = 0.0)
        val withJitter = Backoff.delayMillis(attempt = 3, baseMillis = 500, randomFraction = 0.9)
        assertTrue(withJitter > withoutJitter)
        assertTrue(withJitter <= withoutJitter + 500)
    }

    @Test
    fun `抖动比例越界时被裁剪`() {
        val base = Backoff.delayMillis(attempt = 1, baseMillis = 500, randomFraction = 0.0)
        assertEquals(base, Backoff.delayMillis(1, 500, -1.0))
        assertEquals(base + 500, Backoff.delayMillis(1, 500, 5.0))
    }
}
