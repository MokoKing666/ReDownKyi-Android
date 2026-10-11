package com.moko.downkyi.re.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选档与体积估算。
 *
 * 这一层错了不会报错：少算一路音轨、把"拿不到"的档位当成"没有这一档"、
 * 按 0 码率估出 0 B —— 表现都是"界面看着没问题，下完才发现不对"。
 */
class QualityPlanTest {

    private fun video(qn: Int, codecs: String, bandwidth: Int) = DashStream(
        id = qn,
        url = "https://example/video/$qn/$codecs",
        bandwidth = bandwidth,
        mimeType = "video/mp4",
        codecs = codecs,
        isVideo = true,
    )

    private fun audio(id: Int, bandwidth: Int) = DashStream(
        id = id,
        url = "https://example/audio/$id",
        bandwidth = bandwidth,
        mimeType = "audio/mp4",
        codecs = "mp4a.40.2",
        isVideo = false,
    )

    private fun dashInfo(
        videos: List<DashStream>,
        audios: List<DashStream> = listOf(audio(AudioQuality.K192.audioId, 192_000)),
        supportFormats: Map<Int, String> = emptyMap(),
    ) = DashInfo(
        durationMs = 600_000,
        videos = videos,
        audios = audios,
        supportFormats = supportFormats,
    )

    /** 一个"什么都有"的稿件：8K/4K 只有 HEVC，1080P60 有 AVC 与 HEVC。 */
    private fun fullDash() = dashInfo(
        videos = listOf(
            video(VideoQuality.Q8K.qn, "hev1.1.6.L150.90", 40_000_000),
            video(VideoQuality.Q4K.qn, "hev1.1.6.L120.90", 20_000_000),
            video(VideoQuality.Q4K.qn, "av01.0.09M.08", 16_000_000),
            video(VideoQuality.Q1080P60.qn, "avc1.640033", 8_000_000),
            video(VideoQuality.Q1080P60.qn, "hev1.1.6.L120.90", 6_000_000),
            video(VideoQuality.Q1080P.qn, "avc1.640032", 4_000_000),
            video(VideoQuality.Q720P.qn, "avc1.640028", 3_000_000),
        ),
        supportFormats = mapOf(
            VideoQuality.Q8K.qn to "8K 超清",
            VideoQuality.Q4K.qn to "4K 超清",
            VideoQuality.Q1080P60.qn to "1080P60",
            VideoQuality.Q720P.qn to "720P 高清",
        ),
    )

    // ---- 体积 ----

    @Test
    fun `体积按视频加音频的码率合计计算`() {
        // (8_000_000 + 192_000) / 8 × 600 秒 = 614_400_000 字节 ≈ 586 MB
        val bytes = QualityPlanner.estimateBytes(8_000_000, 192_000, 600_000)
        assertEquals(614_400_000L, bytes)
        assertEquals("585 MB", formatBytes(bytes))
    }

    @Test
    fun `时长未知时体积为 0 而不是抛异常`() {
        assertEquals(0L, QualityPlanner.estimateBytes(8_000_000, 192_000, 0L))
    }

    @Test
    fun `体积格式化按 1024 进制且 GB 保留一位小数`() {
        assertEquals("未知", formatBytes(0L))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1 KB", formatBytes(1024L))
        assertEquals("1.5 MB", formatBytes(1024L * 1024L * 3L / 2L))
        assertEquals("1.8 GB", formatBytes((1.8 * 1024 * 1024 * 1024).toLong()))
    }

    // ---- 数量级与时长 ----

    @Test
    fun `数量级按万与亿显示`() {
        assertEquals("0", formatCount(0))
        assertEquals("8542", formatCount(8_542))
        assertEquals("1.0 万", formatCount(10_000))
        assertEquals("128.4 万", formatCount(1_284_000))
        assertEquals("1.2 亿", formatCount(123_456_789))
    }

    @Test
    fun `时长不足一小时不写小时位`() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("12:34", formatDuration(754_000L))
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    // ---- 档位列表 ----

    @Test
    fun `档位从高到低排列且带全部可用编码`() {
        val options = QualityPlanner.options(fullDash(), 600_000)

        assertEquals(
            listOf(
                VideoQuality.Q8K.qn,
                VideoQuality.Q4K.qn,
                VideoQuality.Q1080P60.qn,
                VideoQuality.Q1080P.qn,
                VideoQuality.Q720P.qn,
            ),
            options.map { it.qn },
        )
        // 4K 有 HEVC 与 AV1，按 VideoCodec 的声明顺序（兼容性顺序）排列
        assertEquals(
            listOf(VideoCodec.HEVC, VideoCodec.AV1),
            options.first { it.qn == VideoQuality.Q4K.qn }.codecs,
        )
    }

    @Test
    fun `声明支持但没有流的下发时标记为需要登录`() {
        val dash = dashInfo(
            videos = listOf(video(VideoQuality.Q480P.qn, "avc1.64001f", 1_000_000)),
            supportFormats = mapOf(
                VideoQuality.Q1080P.qn to "1080P 高清",
                VideoQuality.Q480P.qn to "480P 清晰",
            ),
        )
        val options = QualityPlanner.options(dash, 600_000)

        val q1080 = options.first { it.qn == VideoQuality.Q1080P.qn }
        assertTrue("拿不到流的档位必须标记原因", q1080.requiresVip)
        assertEquals("需登录 / 大会员", q1080.sizeText)

        val q480 = options.first { it.qn == VideoQuality.Q480P.qn }
        assertTrue(!q480.requiresVip)
        // 估的是视频 + 音频合计：只算视频的话这里会少掉 192_000 bit/s 那一路
        assertEquals(
            QualityPlanner.estimateBytes(1_000_000, 192_000, 600_000),
            q480.estimatedBytes,
        )
    }

    @Test
    fun `未知档位编号不猜测名称`() {
        val dash = dashInfo(videos = listOf(video(3000, "avc1.64001f", 2_000_000)))
        val option = QualityPlanner.options(dash, 600_000).single()

        assertNull(option.quality)
        assertEquals("未知档位（qn=3000）", option.label)
    }

    // ---- 音轨 ----

    @Test
    fun `音轨列表把无损与全景声排在最前`() {
        val dash = DashInfo(
            durationMs = 600_000,
            videos = listOf(video(VideoQuality.Q1080P.qn, "avc1.640032", 4_000_000)),
            audios = listOf(audio(AudioQuality.K64.audioId, 64_000), audio(AudioQuality.K192.audioId, 192_000)),
            flac = audio(AudioQuality.HI_RES.audioId, 1_500_000),
            dolby = audio(AudioQuality.DOLBY_ATMOS.audioId, 448_000),
        )

        val options = QualityPlanner.audioOptions(dash, 600_000)
        assertEquals(
            listOf("Hi-Res 无损", "杜比全景声", "192K", "64K"),
            options.map { it.label },
        )
        assertEquals("1500 kbps", options.first().bitrateText)
    }

    // ---- 智能选档 ----

    @Test
    fun `画质优先取最高档与最高码率音轨`() {
        val pick = QualityPlanner.smartPick(PreferenceMode.BEST_QUALITY, fullDash())

        assertEquals(VideoQuality.Q8K.qn, pick.qn)
        // 8K 只有 HEVC
        assertEquals(VideoCodec.HEVC, pick.codec)
        assertEquals(AudioQuality.K192.audioId, pick.audioId)
    }

    @Test
    fun `兼容性优先不选只有 HEVC 的 4K`() {
        val pick = QualityPlanner.smartPick(PreferenceMode.COMPATIBILITY, fullDash())

        assertEquals(VideoQuality.Q1080P60.qn, pick.qn)
        assertEquals(VideoCodec.AVC, pick.codec)
    }

    @Test
    fun `体积优先压在 1080P 且不含 60 帧档`() {
        val pick = QualityPlanner.smartPick(PreferenceMode.SMALL_SIZE, fullDash())

        // 1080P60 的 qn(116) 比 1080P(80) 大，但它属于**更高**的档位族，
        // 体积也更大 —— 按 qn 数字比大小的实现会在这里选错
        assertEquals(VideoQuality.Q1080P.qn, pick.qn)
        // 1080P 这一档只有 AVC；不为了 AV1 而降清晰度
        assertEquals(VideoCodec.AVC, pick.codec)
    }

    @Test
    fun `体积优先在候选档有 AV1 时选 AV1`() {
        val dash = dashInfo(
            videos = listOf(
                video(VideoQuality.Q1080P.qn, "avc1.640032", 4_000_000),
                video(VideoQuality.Q1080P.qn, "av01.0.08M.08", 3_000_000),
                video(VideoQuality.Q720P.qn, "avc1.640028", 2_000_000),
            ),
            audios = listOf(audio(AudioQuality.K64.audioId, 64_000), audio(AudioQuality.K192.audioId, 192_000)),
        )
        val pick = QualityPlanner.smartPick(PreferenceMode.SMALL_SIZE, dash)

        assertEquals(VideoQuality.Q1080P.qn, pick.qn)
        assertEquals(VideoCodec.AV1, pick.codec)
        // 音轨偏好与画质偏好同向：要省体积就挑最小的一条
        assertEquals(AudioQuality.K64.audioId, pick.audioId)
    }

    @Test
    fun `速度优先落到 720P`() {
        val pick = QualityPlanner.smartPick(PreferenceMode.FAST_SPEED, fullDash())

        assertEquals(VideoQuality.Q720P.qn, pick.qn)
        assertEquals(VideoCodec.AVC, pick.codec)
    }

    @Test
    fun `上限内一档都没有时回退到实际可用最高档`() {
        // 只有 4K（且只有 HEVC）：兼容性优先的上限是 1080P60，上限内一档都没有。
        // 此时必须回退到实际能下的最高档，而不是给出空的选档 —— 用户点了
        // 「画质优先」却什么也下不了，是因为偏好不该变成一种限制
        val dash = dashInfo(videos = listOf(video(VideoQuality.Q4K.qn, "hev1.1.6.L120.90", 20_000_000)))
        val pick = QualityPlanner.smartPick(PreferenceMode.COMPATIBILITY, dash)

        assertEquals(VideoQuality.Q4K.qn, pick.qn)
        assertEquals(VideoCodec.HEVC, pick.codec)
    }

    @Test
    fun `没有任何视频流时给出空选档而不是崩溃`() {
        val dash = dashInfo(videos = emptyList())
        val pick = QualityPlanner.smartPick(PreferenceMode.BEST_QUALITY, dash)

        assertEquals(0, pick.qn)
        assertNull(pick.codec)
    }

    @Test
    fun `选中组合的体积按所选编码与音轨计算`() {
        val dash = fullDash()
        val hevc = QualityPlanner.estimateFor(dash, 600_000, VideoQuality.Q4K.qn, VideoCodec.HEVC, null)
        val av1 = QualityPlanner.estimateFor(dash, 600_000, VideoQuality.Q4K.qn, VideoCodec.AV1, null)

        assertTrue("AV1 同档位码率更低，体积必须更小", av1 < hevc)
        assertEquals(QualityPlanner.estimateBytes(20_000_000, 192_000, 600_000), hevc)
    }
}
