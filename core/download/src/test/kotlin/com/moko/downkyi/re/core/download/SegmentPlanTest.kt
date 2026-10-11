package com.moko.downkyi.re.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分片规划与断点判定。
 *
 * 这两件事决定了「断点续传是否可信」。做错的表现不是报错，
 * 而是**拼出来的文件损坏但全程显示成功** —— 极难定位，
 * 所以关键性质（覆盖完整、不重叠）都用断言钉住，而不是抽查几个数字。
 */
class SegmentPlanTest {

    // ---- 分片规划 ----

    @Test
    fun `整除时各分片等长`() {
        val plan = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 4)
        assertEquals(4, plan.segmentCount)
        assertEquals(250L, plan.chunkSize)
        plan.segments.forEach { assertEquals(250L, it.size) }
        assertEquals(0L, plan.segments.first().start)
        assertEquals(999L, plan.segments.last().end)
    }

    @Test
    fun `有余数时最后一片较短且总覆盖完整`() {
        val plan = SegmentPlanner.plan("f", totalBytes = 1001, requestedSegments = 4)
        // chunk 向上取整为 251：前 3 片各 251，最后一片 248
        assertEquals(251L, plan.chunkSize)
        assertEquals(251L, plan.segments[0].size)
        assertEquals(248L, plan.segments[3].size)
        assertEquals(1001L, plan.segments.sumOf { it.size })
    }

    @Test
    fun `分片首尾相接不重叠也不留空隙`() {
        // 这是最关键的性质：任何重叠会产生多余字节，任何空隙会让
        // 拼接后的文件比预期短 —— 两者都只在播放时才暴露
        listOf(1L, 7L, 999L, 1000L, 1001L, 123_456L, 9_999_999L).forEach { total ->
            listOf(1, 2, 3, 8, 16).forEach { requested ->
                val plan = SegmentPlanner.plan("f", total, requested)
                assertEquals("total=$total requested=$requested", 0L, plan.segments.first().start)
                assertEquals("total=$total requested=$requested", total - 1, plan.segments.last().end)
                plan.segments.zipWithNext { a, b ->
                    assertEquals("total=$total requested=$requested 分片不相接", a.end + 1, b.start)
                }
                assertEquals("total=$total requested=$requested 覆盖不完整", total, plan.segments.sumOf { it.size })
            }
        }
    }

    @Test
    fun `分片数被裁剪到上限`() {
        val plan = SegmentPlanner.plan("f", totalBytes = 100_000, requestedSegments = 999)
        assertEquals(SegmentPlanner.MAX_SEGMENTS, plan.segmentCount)
    }

    @Test
    fun `分片数至少为一`() {
        val plan = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 0)
        assertEquals(1, plan.segmentCount)
        assertEquals(1000L, plan.segments[0].size)
    }

    @Test
    fun `总长未知时退化为单片顺序下载`() {
        val plan = SegmentPlanner.plan("f", totalBytes = 0, requestedSegments = 8)
        assertEquals(1, plan.segmentCount)
        assertEquals(0L, plan.totalBytes)
    }

    @Test
    fun `总长小于分片数时不会产生空分片`() {
        // 3 字节的文件请求 8 个分片：只能产生 3 片
        val plan = SegmentPlanner.plan("f", totalBytes = 3, requestedSegments = 8)
        assertEquals(3, plan.segmentCount)
        assertTrue(plan.segments.all { it.size > 0 })
        assertEquals(3L, plan.segments.sumOf { it.size })
    }

    @Test
    fun `分片文件路径按索引命名`() {
        val plan = SegmentPlanner.plan("video.m4s", totalBytes = 1000, requestedSegments = 3)
        assertEquals("video.m4s.part0", plan.segments[0].partPath)
        assertEquals("video.m4s.part2", plan.segments[2].partPath)
    }

    @Test
    fun `清理列表覆盖全部可能的分片编号`() {
        val paths = SegmentPlanner.partPaths("f")
        assertEquals(SegmentPlanner.MAX_SEGMENTS, paths.size)
        assertEquals("f.part0", paths.first())
        assertEquals("f.part15", paths.last())
    }

    // ---- 断点签名 ----

    @Test
    fun `总长变化会让签名不同`() {
        val a = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 4)
        val b = SegmentPlanner.plan("f", totalBytes = 1001, requestedSegments = 4)
        assertNotEquals(a.signature, b.signature)
    }

    @Test
    fun `分片数变化会让签名不同`() {
        val a = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 4)
        val b = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 8)
        assertNotEquals(a.signature, b.signature)
    }

    @Test
    fun `相同参数产生的签名一致`() {
        val a = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 4)
        val b = SegmentPlanner.plan("g", totalBytes = 1000, requestedSegments = 4)
        assertEquals(a.signature, b.signature)
    }

    // ---- 断点判定 ----

    private val plan = SegmentPlanner.plan("f", totalBytes = 1000, requestedSegments = 4)

    @Test
    fun `签名一致时按磁盘实际长度续传`() {
        val states = ResumePlanner.resolve(
            plan = plan,
            savedSignature = plan.signature,
            actualPartLengths = mapOf(0 to 100L, 1 to 250L, 2 to 0L, 3 to 30L),
        )
        assertEquals(100L, states[0].done)
        assertEquals(250L, states[1].done)
        assertEquals(0L, states[2].done)
        assertEquals(30L, states[3].done)
        assertTrue(states.none { it.shouldReset })
    }

    @Test
    fun `磁盘上没有的分片视为未开始`() {
        val states = ResumePlanner.resolve(
            plan = plan,
            savedSignature = plan.signature,
            actualPartLengths = emptyMap(),
        )
        assertTrue(states.all { it.done == 0L && !it.shouldReset })
    }

    @Test
    fun `签名不一致时全部作废`() {
        // 总长或分片数变了，旧分片与当前规划对不上，硬用会拼出损坏文件
        val states = ResumePlanner.resolve(
            plan = plan,
            savedSignature = "其他签名",
            actualPartLengths = mapOf(0 to 250L, 1 to 250L),
        )
        assertTrue(states.all { it.done == 0L && it.shouldReset })
    }

    @Test
    fun `没有保存过签名时全部作废`() {
        val states = ResumePlanner.resolve(plan, savedSignature = null, actualPartLengths = mapOf(0 to 250L))
        assertTrue(states.all { it.shouldReset })
    }

    @Test
    fun `分片长度超出应有大小时丢弃该分片`() {
        // 超出说明文件被污染过，或上次的分片规划与这次不同
        val states = ResumePlanner.resolve(
            plan = plan,
            savedSignature = plan.signature,
            actualPartLengths = mapOf(0 to 300L, 1 to 250L),
        )
        assertEquals(0L, states[0].done)
        assertTrue(states[0].shouldReset)
        assertEquals(250L, states[1].done)
        assertTrue(!states[1].shouldReset)
    }

    @Test
    fun `全部下完时判定为完成`() {
        val states = ResumePlanner.resolve(
            plan = plan,
            savedSignature = plan.signature,
            actualPartLengths = mapOf(0 to 250L, 1 to 250L, 2 to 250L, 3 to 250L),
        )
        assertTrue(ResumePlanner.isComplete(plan, states))
        assertEquals(1000L, ResumePlanner.downloadedBytes(states))
    }

    @Test
    fun `缺一片就不算完成`() {
        val states = ResumePlanner.resolve(
            plan = plan,
            savedSignature = plan.signature,
            actualPartLengths = mapOf(0 to 250L, 1 to 250L, 2 to 250L, 3 to 249L),
        )
        assertTrue(!ResumePlanner.isComplete(plan, states))
    }

    @Test
    fun `空计划不算完成`() {
        val emptyPlan = DownloadPlan(0L, 0L, emptyList())
        assertTrue(!ResumePlanner.isComplete(emptyPlan, emptyList()))
    }
}
