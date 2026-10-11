package com.moko.downkyi.re.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载内容位掩码与各枚举的兜底解析测试。
 *
 * 位掩码会写进数据库整数列、并参与任务唯一键的哈希计算，
 * 因此位值一旦漂移，历史任务会被判成"新任务"而重复下载。
 */
class DownloadOptionsTest {

    @Test
    fun `下载内容位值固定`() {
        assertEquals(1, DownloadFlags.VIDEO)
        assertEquals(2, DownloadFlags.AUDIO)
        assertEquals(4, DownloadFlags.COVER)
        assertEquals(8, DownloadFlags.DANMAKU)
        assertEquals(16, DownloadFlags.SUBTITLE)
    }

    @Test
    fun `默认下载内容为视频加音频加封面加弹幕`() {
        assertEquals(
            DownloadFlags.VIDEO or DownloadFlags.AUDIO or DownloadFlags.COVER or DownloadFlags.DANMAKU,
            DownloadFlags.DEFAULT,
        )
        // 字幕默认关闭：需要稿件本身有 CC 字幕，否则每次下载都会报一条失败
        assertFalse(DownloadFlags.has(DownloadFlags.DEFAULT, DownloadFlags.SUBTITLE))
        assertTrue(DownloadFlags.hasAnyMedia(DownloadFlags.DEFAULT))
    }

    @Test
    fun `位开关可以独立增删且互不影响`() {
        var flags = DownloadFlags.DEFAULT

        flags = DownloadFlags.with(flags, DownloadFlags.SUBTITLE, true)
        assertTrue(DownloadFlags.has(flags, DownloadFlags.SUBTITLE))
        assertTrue(DownloadFlags.has(flags, DownloadFlags.VIDEO))

        flags = DownloadFlags.with(flags, DownloadFlags.VIDEO, false)
        assertFalse(DownloadFlags.has(flags, DownloadFlags.VIDEO))
        // 关掉视频不应影响音频
        assertTrue(DownloadFlags.has(flags, DownloadFlags.AUDIO))
    }

    @Test
    fun `只选封面弹幕字幕时没有媒体流`() {
        val extrasOnly = DownloadFlags.COVER or DownloadFlags.DANMAKU or DownloadFlags.SUBTITLE
        assertFalse(DownloadFlags.hasAnyMedia(extrasOnly))
        // 但这些任务本身是合法的：允许只下附加资源
        assertEquals(listOf("封面", "弹幕", "字幕"), DownloadFlags.describe(extrasOnly))
    }

    @Test
    fun `枚举反序列化对脏数据一律回退默认值`() {
        assertEquals(SaveLocation.GALLERY, SaveLocation.fromName(null))
        assertEquals(SaveLocation.GALLERY, SaveLocation.fromName("不存在的位置"))
        assertEquals(SaveLocation.CUSTOM, SaveLocation.fromName("CUSTOM"))

        assertEquals(MuxEngine.SYSTEM, MuxEngine.fromName(null))
        assertEquals(MuxEngine.SYSTEM, MuxEngine.fromName("ffmpeg-nonsense"))
        assertEquals(MuxEngine.FFMPEG, MuxEngine.fromName("ffmpeg"))

        assertEquals(DanmakuFormat.ASS, DanmakuFormat.fromName(null))
        assertEquals(DanmakuFormat.XML, DanmakuFormat.fromName("xml"))

        assertEquals(DownloadEngine.BUILTIN, DownloadEngine.fromName(null))
        assertEquals(DownloadEngine.ARIA2, DownloadEngine.fromName("aria2"))

        assertEquals(PreferenceMode.BEST_QUALITY, PreferenceMode.fromName(null))
        assertEquals(PreferenceMode.COMPATIBILITY, PreferenceMode.fromName("COMPATIBILITY"))
    }

    @Test
    fun `合并引擎默认必须是系统封装`() {
        // 这是兼容性红线：FFmpeg 重封装在部分机型上会产出播不了的文件
        assertEquals(MuxEngine.SYSTEM, MuxEngine.DEFAULT)
    }

    @Test
    fun `选档模式的编码优先级符合各自定位`() {
        // 兼容性优先：H.264 必须在最前
        assertEquals(VideoCodec.AVC, PreferenceMode.codecOrder(PreferenceMode.COMPATIBILITY).first())

        // 画质优先：HEVC 最前
        assertEquals(VideoCodec.HEVC, PreferenceMode.codecOrder(PreferenceMode.BEST_QUALITY).first())

        // 体积 / 速度优先：同画质下 AV1 码率最低
        assertEquals(VideoCodec.AV1, PreferenceMode.codecOrder(PreferenceMode.SMALL_SIZE).first())
        assertEquals(VideoCodec.AV1, PreferenceMode.codecOrder(PreferenceMode.FAST_SPEED).first())

        // 每种模式都必须覆盖全部编码，否则选档时会出现"无可用候选"
        PreferenceMode.entries.forEach { mode ->
            assertEquals(
                "模式 ${mode.name} 的编码候选不完整",
                VideoCodec.entries.toSet(),
                PreferenceMode.codecOrder(mode).toSet(),
            )
        }
    }

    @Test
    fun `弹幕格式的扩展名与展示名一致`() {
        assertEquals("", DanmakuFormat.NONE.extension)
        assertEquals("xml", DanmakuFormat.XML.extension)
        assertEquals("ass", DanmakuFormat.ASS.extension)
        assertEquals("txt", DanmakuFormat.TXT.extension)
    }

    @Test
    fun `提取音频类转换目标会被标记`() {
        assertTrue(ConvertTarget.MP3.needsAudioOnly)
        assertTrue(ConvertTarget.M4A.needsAudioOnly)
        assertFalse(ConvertTarget.MP4_COPY.needsAudioOnly)
        assertFalse(ConvertTarget.GIF.needsAudioOnly)
    }
}
