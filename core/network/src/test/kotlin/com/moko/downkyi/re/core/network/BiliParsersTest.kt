package com.moko.downkyi.re.core.network

import com.moko.downkyi.re.core.model.BiliConstants
import com.moko.downkyi.re.core.model.VideoCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 接口响应解析。
 *
 * 重点在两处**跨接口的不一致**：
 *  - 时长单位：`dash.duration` 是秒，`timelength` 与 `durl[].length` 是毫秒；
 *  - 字段命名：同一份数据可能是 `baseUrl` 也可能是 `base_url`。
 *
 * 这两类问题都不会报错，只会让数字或地址悄悄变成错的。
 */
class BiliParsersTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    // ---- URL 规范化 ----

    @Test
    fun `协议相对地址补全为 https`() {
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            BiliParsers.normalizeUrl("//i0.hdslb.com/x.jpg"),
        )
    }

    @Test
    fun `http 地址升级为 https`() {
        // Android 9+ 默认禁止明文流量，http 地址会被系统直接拦掉
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            BiliParsers.normalizeUrl("http://i0.hdslb.com/x.jpg"),
        )
    }

    @Test
    fun `已是 https 的地址保持不变`() {
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            BiliParsers.normalizeUrl("https://i0.hdslb.com/x.jpg"),
        )
    }

    @Test
    fun `空地址保持为空`() {
        assertEquals("", BiliParsers.normalizeUrl(""))
    }

    // ---- DASH 时长单位 ----

    @Test
    fun `dash 的 duration 是秒需要乘以一千`() {
        // 这是最要命的一条：少乘 1000 会让所有基于时长的计算小 1000 倍，
        // 表现为「预估体积把 MB 显示成 KB」
        val info = BiliParsers.parseDash(obj("""{"dash": {"duration": 218}}"""))
        assertEquals(218_000L, info.durationMs)
    }

    @Test
    fun `没有 dash 时回退到 timelength 且它已是毫秒`() {
        val info = BiliParsers.parseDash(obj("""{"timelength": 218000}"""))
        assertEquals(218_000L, info.durationMs)
    }

    @Test
    fun `dash 与 timelength 都缺失时回退到 durl 的长度字段`() {
        // 老视频与部分番剧只返回 durl 直链
        val info = BiliParsers.parseDash(
            obj("""{"durl": [{"length": 93000}, {"length": 50000}]}"""),
        )
        assertEquals(93_000L, info.durationMs)
    }

    @Test
    fun `三段兜底按优先级取第一个有效值`() {
        val info = BiliParsers.parseDash(
            obj("""{"dash": {"duration": 10}, "timelength": 99999, "durl": [{"length": 111}]}"""),
        )
        assertEquals(10_000L, info.durationMs)
    }

    @Test
    fun `全部缺失时时长为零而不是负数`() {
        assertEquals(0L, BiliParsers.parseDash(obj("""{}""")).durationMs)
    }

    // ---- DASH 流解析 ----

    @Test
    fun `解析视频流与音频流`() {
        val info = BiliParsers.parseDash(
            obj(
                """
                {
                  "dash": {
                    "duration": 100,
                    "video": [
                      {"id": 80, "baseUrl": "//x/v.m4s", "bandwidth": 2000, "codecs": "avc1.640032"},
                      {"id": 64, "base_url": "//x/v2.m4s", "bandwidth": 1200, "codecs": "hev1.1.6"}
                    ],
                    "audio": [
                      {"id": 30280, "baseUrl": "//x/a.m4s", "bandwidth": 192000, "codecs": "mp4a.40.2"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(2, info.videos.size)
        assertEquals(1, info.audios.size)
        assertTrue(info.hasVideo)
        assertTrue(info.hasAudio)
        // 驼峰与下划线两种命名都能取到，且地址已补全协议
        assertEquals("https://x/v.m4s", info.videos[0].url)
        assertEquals("https://x/v2.m4s", info.videos[1].url)
        assertEquals("video/mp4", info.videos[0].mimeType)
        assertEquals("audio/mp4", info.audios[0].mimeType)
        assertEquals(VideoCodec.AVC, info.videos[0].codecFamily)
        assertEquals(VideoCodec.HEVC, info.videos[1].codecFamily)
    }

    @Test
    fun `备用地址被规范化并保留`() {
        val info = BiliParsers.parseDash(
            obj(
                """
                {"dash": {"duration": 1, "video": [
                  {"id": 80, "baseUrl": "//a/v", "backupUrl": ["//b/v", "//c/v"], "bandwidth": 1}
                ]}}
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("//b/v", "//c/v").map { BiliParsers.normalizeUrl(it) }, info.videos[0].backupUrls)
        assertEquals(3, info.videos[0].allUrls.size)
    }

    @Test
    fun `无损与杜比音频从嵌套字段解析`() {
        val info = BiliParsers.parseDash(
            obj(
                """
                {
                  "dash": {
                    "duration": 1,
                    "flac": {"audio": {"id": 30251, "baseUrl": "//x/f.m4s", "bandwidth": 1500000}},
                    "dolby": {"audio": {"id": 30250, "baseUrl": "//x/d.m4s", "bandwidth": 448000}}
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(30251, info.flac?.id)
        assertEquals(30250, info.dolby?.id)
        assertTrue(info.hasAudio)
        // 未指定音轨时优先无损
        assertEquals(30251, info.pickAudio()?.id)
    }

    @Test
    fun `空的 flac 子对象不会产生流`() {
        val info = BiliParsers.parseDash(obj("""{"dash": {"duration": 1, "flac": {"audio": null}}}"""))
        assertNull(info.flac)
    }

    @Test
    fun `支持档位从 support_formats 解析并优先用服务端描述`() {
        val info = BiliParsers.parseDash(
            obj(
                """
                {
                  "dash": {"duration": 1},
                  "support_formats": [
                    {"quality": 120, "new_description": "4K 超清"},
                    {"quality": 80, "new_description": "1080P 高清"},
                    {"quality": 0, "new_description": "应被忽略"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals("4K 超清", info.supportFormats[120])
        assertEquals("1080P 高清", info.supportFormats[80])
        assertNull(info.supportFormats[0])
    }

    @Test
    fun `缺少描述时回退到本地档位名`() {
        val info = BiliParsers.parseDash(
            obj("""{"dash": {"duration": 1}, "support_formats": [{"quality": 127}]}"""),
        )
        assertEquals("8K 超清", info.supportFormats[127])
    }

    @Test
    fun `流没有 id 或地址时仍然解析出条目`() {
        // 解析层不做过滤，交由选流逻辑判断可用性 ——
        // 在这里丢弃会让「为什么这条流不见了」无从追查
        val info = BiliParsers.parseDash(
            obj("""{"dash": {"duration": 1, "video": [{"bandwidth": 100}]}}"""),
        )
        assertEquals(1, info.videos.size)
        assertEquals(0, info.videos[0].id)
        assertEquals("", info.videos[0].url)
    }

    // ---- 视频详情 ----

    @Test
    fun `解析普通视频详情且 duration 按秒处理`() {
        val detail = BiliParsers.parseVideoDetail(
            obj(
                """
                {
                  "bvid": "BV1xx411c7mD", "aid": 123, "cid": 456,
                  "title": "测试视频", "pic": "//i0.hdslb.com/cover.jpg",
                  "desc": "简介", "tname": "生活", "duration": 218,
                  "owner": {"name": "某UP", "mid": 999},
                  "stat": {"view": 12345, "danmaku": 678},
                  "pages": [
                    {"cid": 456, "page": 1, "part": "P1 开篇", "duration": 100},
                    {"cid": 789, "page": 2, "part": "P2 进阶", "duration": 118}
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals("BV1xx411c7mD", detail.bvid)
        assertEquals("测试视频", detail.title)
        assertEquals("https://i0.hdslb.com/cover.jpg", detail.cover)
        assertEquals(218_000L, detail.durationMs)
        assertEquals("某UP", detail.ownerName)
        assertEquals(12345, detail.view)
        assertEquals(2, detail.pages.size)
        assertEquals(100_000L, detail.pages[0].durationMs)
        assertTrue(detail.isMultiPage)
    }

    @Test
    fun `pages 缺失时构造单 P 兜底`() {
        // 部分接口不返回 pages，上层若到处判空会很难维护
        val detail = BiliParsers.parseVideoDetail(
            obj("""{"bvid": "BV1", "cid": 456, "title": "单P视频", "duration": 60}"""),
        )
        assertEquals(1, detail.pages.size)
        assertEquals(456L, detail.pages[0].cid)
        assertEquals("单P视频", detail.pages[0].part)
        assertEquals(false, detail.isMultiPage)
    }

    @Test
    fun `缺失字段回退到可读的默认值`() {
        val detail = BiliParsers.parseVideoDetail(obj("""{}"""))
        assertEquals("未命名视频", detail.title)
        assertEquals("未知 UP 主", detail.ownerName)
        assertEquals(BiliConstants.TYPE_VIDEO, detail.btype)
        assertNull(detail.epId)
    }

    @Test
    fun `多 P 视频按时长与标题生成分 P 标题`() {
        val detail = BiliParsers.parseVideoDetail(
            obj(
                """
                {"bvid": "BV1", "cid": 1, "title": "主标题", "duration": 200,
                 "pages": [{"cid": 1, "page": 1, "part": "开篇", "duration": 100},
                           {"cid": 2, "page": 2, "part": "进阶", "duration": 100}]}
                """.trimIndent(),
            ),
        )
        assertEquals("主标题 - 开篇", detail.pageTitle(0))
        assertEquals("主标题 - 进阶", detail.pageTitle(1))
        assertEquals(2L, detail.cidAt(1))
        assertEquals(1, detail.indexOfCid(2L))
    }

    // ---- 番剧 / 课程单集 ----

    @Test
    fun `解析番剧单集时标题由季与集拼成`() {
        val detail = BiliParsers.parseEpisode(
            season = obj("""{"title": "某番剧", "up_name": "制作组", "up_info": {"mid": 7}}"""),
            episode = obj("""{"id": 111, "cid": 222, "long_title": "第一话", "duration": 1440}"""),
            btype = BiliConstants.TYPE_BANGUMI,
        )
        assertEquals("某番剧 · 第一话", detail.title)
        assertEquals("制作组", detail.ownerName)
        assertEquals(111L, detail.epId)
        assertEquals(1_440_000L, detail.durationMs)
        assertTrue(detail.isEpisode)
    }

    @Test
    fun `番剧条目没有 bvid 时不影响解析`() {
        // 番剧的播放地址只能靠 epId，bvid 恒为空
        val detail = BiliParsers.parseEpisode(
            season = obj("""{"title": "某番剧"}"""),
            episode = obj("""{"id": 111, "cid": 222, "title": "第1话", "duration": 100}"""),
            btype = BiliConstants.TYPE_BANGUMI,
        )
        assertEquals("", detail.bvid)
        assertEquals(111L, detail.epId)
    }

    @Test
    fun `课程单集在缺少 UP 主名称时用课程兜底`() {
        val detail = BiliParsers.parseEpisode(
            season = obj("""{"title": "某课程"}"""),
            episode = obj("""{"id": 1, "cid": 2, "title": "第一讲", "duration": 600}"""),
            btype = BiliConstants.TYPE_CHEESE,
        )
        assertEquals("课程", detail.ownerName)
        assertEquals(BiliConstants.TYPE_CHEESE, detail.btype)
    }

    @Test
    fun `番剧缺少 up_name 时用番剧兜底`() {
        val detail = BiliParsers.parseEpisode(
            season = obj("""{"title": "某番剧"}"""),
            episode = obj("""{"id": 1, "cid": 2, "title": "第1话"}"""),
            btype = BiliConstants.TYPE_BANGUMI,
        )
        assertEquals("番剧", detail.ownerName)
    }

    @Test
    fun `单集封面上回退到季封面`() {
        val detail = BiliParsers.parseEpisode(
            season = obj("""{"title": "某番剧", "cover": "//s/cover.jpg"}"""),
            episode = obj("""{"id": 1, "cid": 2, "title": "第1话"}"""),
            btype = BiliConstants.TYPE_BANGUMI,
        )
        assertEquals("https://s/cover.jpg", detail.cover)
    }
}
