package com.moko.downkyi.re.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清晰度 / 编码 / 音轨枚举的数值锚定测试。
 *
 * 这些 qn 与 codecid 是 B 站接口的硬约定，写错任何一个都会导致
 * 「请求 4K 却拿到 1080P」这类不报错但结果错误的问题。
 * 因此这里逐个钉死取值，而不是只测行为。
 */
class VideoQualityTest {

    @Test
    fun `清晰度 qn 取值与 B 站接口约定一致`() {
        assertEquals(127, VideoQuality.Q8K.qn)
        assertEquals(126, VideoQuality.DOLBY_VISION.qn)
        assertEquals(125, VideoQuality.HDR.qn)
        assertEquals(120, VideoQuality.Q4K.qn)
        assertEquals(116, VideoQuality.Q1080P60.qn)
        assertEquals(112, VideoQuality.Q1080P_PLUS.qn)
        assertEquals(80, VideoQuality.Q1080P.qn)
        assertEquals(74, VideoQuality.Q720P60.qn)
        assertEquals(64, VideoQuality.Q720P.qn)
        assertEquals(32, VideoQuality.Q480P.qn)
        assertEquals(16, VideoQuality.Q360P.qn)
        assertEquals(6, VideoQuality.Q240P.qn)
    }

    @Test
    fun `fromQn 能双向映射且未知编号返回 null`() {
        VideoQuality.entries.forEach { quality ->
            assertEquals(quality, VideoQuality.fromQn(quality.qn))
        }
        assertNull(VideoQuality.fromQn(9999))
        assertNull(VideoQuality.fromQn(0))
        assertNull(VideoQuality.fromQnOrNull(null))
    }

    @Test
    fun `优先级列表覆盖全部档位且按由高到低排列`() {
        assertEquals(VideoQuality.entries.size, VideoQuality.PRIORITY.size)
        assertEquals(VideoQuality.entries.toSet(), VideoQuality.PRIORITY.toSet())
        // 必须严格递减，选档逻辑依赖这个顺序
        VideoQuality.PRIORITY.zipWithNext { high, low ->
            assertTrue(
                "优先级顺序错误：${high.label}(${high.qn}) 应高于 ${low.label}(${low.qn})",
                high.qn > low.qn,
            )
        }
    }

    @Test
    fun `编码 codecid 与持久化标识符合约定`() {
        assertEquals(7, VideoCodec.AVC.codecId)
        assertEquals(12, VideoCodec.HEVC.codecId)
        assertEquals(13, VideoCodec.AV1.codecId)

        assertEquals(VideoCodec.AVC, VideoCodec.fromToken("avc"))
        assertEquals(VideoCodec.HEVC, VideoCodec.fromToken("HEVC"))
        assertEquals(VideoCodec.AVC, VideoCodec.fromTokenOrDefault("不认识的编码"))
        assertNull(VideoCodec.fromToken(null))
    }

    @Test
    fun `音轨 id 与约定一致`() {
        assertEquals(30216, AudioQuality.K64.audioId)
        assertEquals(30232, AudioQuality.K132.audioId)
        assertEquals(30280, AudioQuality.K192.audioId)
        assertEquals(30250, AudioQuality.DOLBY_ATMOS.audioId)
        assertEquals(30251, AudioQuality.HI_RES.audioId)

        assertEquals(AudioQuality.HI_RES, AudioQuality.fromId(30251))
        assertNull(AudioQuality.fromId(null))
        assertNull(AudioQuality.fromId(12345))
    }
}
