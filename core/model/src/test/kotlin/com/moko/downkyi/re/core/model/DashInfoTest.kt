package com.moko.downkyi.re.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DASH 选流。
 *
 * 这是「用户选了 4K，实际拿到什么」的唯一决定点。里面每一级回退都对应
 * 一个真实场景，尤其是**档位回退**：批量下载时选项面板给的是「参考视频」
 * 支持的档位，其余视频未必有同一档 —— 没有回退就会整批失败。
 */
class DashInfoTest {

    private fun video(qn: Int, codecs: String, bandwidth: Int) = DashStream(
        id = qn,
        url = "https://example/$qn/$codecs",
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
        videos: List<DashStream> = emptyList(),
        audios: List<DashStream> = emptyList(),
        flac: DashStream? = null,
        dolby: DashStream? = null,
        supportFormats: Map<Int, String> = emptyMap(),
    ) = DashInfo(
        durationMs = 60_000,
        videos = videos,
        audios = audios,
        flac = flac,
        dolby = dolby,
        supportFormats = supportFormats,
    )

    // ---- 编码族判断 ----

    @Test
    fun `编码族从 codecs 前缀判断`() {
        assertEquals(VideoCodec.AVC, video(80, "avc1.640032", 1).codecFamily)
        assertEquals(VideoCodec.HEVC, video(80, "hev1.1.6.L120.90", 1).codecFamily)
        assertEquals(VideoCodec.HEVC, video(80, "hvc1.1.6.L120.90", 1).codecFamily)
        assertEquals(VideoCodec.AV1, video(80, "av01.0.08M.08", 1).codecFamily)
    }

    @Test
    fun `编码族对大小写不敏感`() {
        assertEquals(VideoCodec.HEVC, video(80, "HEV1.1.6.L120.90", 1).codecFamily)
        assertEquals(VideoCodec.AV1, video(80, "AV01.0.08M.08", 1).codecFamily)
    }

    @Test
    fun `无法识别的编码串归为 AVC`() {
        // 归到兼容性最好的那一类，而不是丢弃这条流
        assertEquals(VideoCodec.AVC, video(80, "unknown", 1).codecFamily)
        assertEquals(VideoCodec.AVC, video(80, "", 1).codecFamily)
    }

    // ---- 可用档位 ----

    @Test
    fun `可用档位从高到低且去重`() {
        val info = dashInfo(
            videos = listOf(
                video(80, "avc1", 100),
                video(64, "avc1", 100),
                video(80, "hev1", 100),
                video(120, "hev1", 100),
            ),
        )
        assertEquals(listOf(120, 80, 64), info.qualityList)
    }

    @Test
    fun `声明支持的档位也计入可用档位`() {
        // supportFormats 会包含「本视频支持但当前账号无权限」的档位，
        // 界面需要据此提示「需要登录」，而不是干脆不显示这一档
        val info = dashInfo(
            videos = listOf(video(80, "avc1", 100)),
            supportFormats = mapOf(112 to "1080P+", 120 to "4K 超清"),
        )
        assertEquals(listOf(120, 112, 80), info.qualityList)
    }

    // ---- 选视频流 ----

    @Test
    fun `精确匹配档位与编码`() {
        val info = dashInfo(
            videos = listOf(
                video(120, "hev1", 5000),
                video(80, "avc1", 2000),
                video(80, "hev1", 1800),
            ),
        )
        assertEquals("hev1", info.pickVideo(80, VideoCodec.HEVC)?.codecs)
        assertEquals("avc1", info.pickVideo(80, VideoCodec.AVC)?.codecs)
        assertEquals(120, info.pickVideo(120, VideoCodec.HEVC)?.id)
    }

    @Test
    fun `指定编码在该档位不存在时回退到其它编码`() {
        // 用户选了 AV1，但 1080P 只有 AVC
        val info = dashInfo(videos = listOf(video(80, "avc1", 2000)))
        assertEquals("avc1", info.pickVideo(80, VideoCodec.AV1)?.codecs)
    }

    @Test
    fun `档位不存在时回退到可用的最高档`() {
        // 批量下载的关键路径：选项面板给的是参考视频的档位，
        // 其余视频未必有同一档，必须各自回退而不是失败
        val info = dashInfo(
            videos = listOf(
                video(64, "avc1", 1200),
                video(32, "avc1", 600),
            ),
        )
        val picked = info.pickVideo(120, VideoCodec.HEVC)
        assertEquals(64, picked?.id)
    }

    @Test
    fun `档位回退时跳过只有声明没有实际流的档位`() {
        // supportFormats 里有 120，但 videos 里没有 120 的流（无权限），
        // 回退时不能停在 120 上，否则会拿到 null
        val info = dashInfo(
            videos = listOf(video(80, "avc1", 2000)),
            supportFormats = mapOf(120 to "4K 超清"),
        )
        assertEquals(80, info.pickVideo(120, VideoCodec.HEVC)?.id)
    }

    @Test
    fun `同一档位有多个码率变体时取最高`() {
        val info = dashInfo(
            videos = listOf(
                video(80, "avc1", 1500),
                video(80, "avc1", 2400),
                video(80, "avc1", 1900),
            ),
        )
        assertEquals(2400, info.pickVideo(80, VideoCodec.AVC)?.bandwidth)
    }

    @Test
    fun `编码回退后仍在剩余候选里取最高码率`() {
        val info = dashInfo(
            videos = listOf(
                video(80, "avc1", 1500),
                video(80, "hev1", 2400),
            ),
        )
        // 优先 AVC：只剩 avc1 一条，取它
        assertEquals("avc1", info.pickVideo(80, VideoCodec.AVC)?.codecs)
        // 优先 AV1：没有 av01，回退到全部候选后取码率最高
        assertEquals("hev1", info.pickVideo(80, VideoCodec.AV1)?.codecs)
    }

    @Test
    fun `没有指定编码时直接取该档位最高码率`() {
        val info = dashInfo(
            videos = listOf(
                video(80, "avc1", 1500),
                video(80, "hev1", 2400),
            ),
        )
        assertEquals(2400, info.pickVideo(80, null)?.bandwidth)
    }

    @Test
    fun `完全没有视频流时返回 null`() {
        val info = dashInfo(videos = emptyList())
        assertNull(info.pickVideo(80, VideoCodec.AVC))
        assertEquals(false, info.hasVideo)
    }

    // ---- 选音频流 ----

    @Test
    fun `无损音轨优先于普通音轨`() {
        val info = dashInfo(
            audios = listOf(audio(30280, 192_000)),
            flac = audio(30251, 1_500_000),
        )
        assertEquals(30251, info.pickAudio()?.id)
    }

    @Test
    fun `没有指定音轨时取码率最高的一条`() {
        val info = dashInfo(
            audios = listOf(audio(30216, 64_000), audio(30280, 192_000), audio(30232, 132_000)),
        )
        assertEquals(30280, info.pickAudio()?.id)
    }

    @Test
    fun `指定音轨时精确匹配`() {
        val info = dashInfo(
            audios = listOf(audio(30216, 64_000), audio(30280, 192_000)),
        )
        assertEquals(30216, info.pickAudio(30216)?.id)
    }

    @Test
    fun `指定的音轨不存在时回退到最高码率`() {
        // 音轨选项与视频一样存在「参考视频有、这个视频没有」的问题
        val info = dashInfo(audios = listOf(audio(30216, 64_000)))
        assertEquals(30216, info.pickAudio(30251)?.id)
    }

    @Test
    fun `杜比音轨在未指定时优先于普通音轨`() {
        val info = dashInfo(
            audios = listOf(audio(30280, 192_000)),
            dolby = audio(30250, 448_000),
        )
        assertEquals(30250, info.pickAudio()?.id)
    }

    @Test
    fun `完全没有音频时返回 null`() {
        assertNull(dashInfo().pickAudio())
        assertEquals(false, dashInfo().hasAudio)
    }

    @Test
    fun `音轨选项里无损与杜比排在最前`() {
        val info = dashInfo(
            audios = listOf(audio(30280, 192_000), audio(30216, 64_000)),
            flac = audio(30251, 1_500_000),
            dolby = audio(30250, 448_000),
        )
        assertEquals(listOf(30251, 30250, 30280, 30216), info.audioOptions.map { it.id })
    }

    // ---- 地址回退 ----

    @Test
    fun `流地址包含主地址与备用地址`() {
        val stream = DashStream(
            id = 80,
            url = "https://main",
            backupUrls = listOf("https://backup1", "https://backup2"),
            bandwidth = 1,
            mimeType = "video/mp4",
            codecs = "avc1",
        )
        assertEquals(listOf("https://main", "https://backup1", "https://backup2"), stream.allUrls)
    }

    @Test
    fun `空地址不会进入地址列表`() {
        val stream = DashStream(
            id = 80,
            url = "",
            backupUrls = listOf("", "https://backup"),
            bandwidth = 1,
            mimeType = "video/mp4",
            codecs = "avc1",
        )
        assertEquals(listOf("https://backup"), stream.allUrls)
    }
}
