package com.moko.downkyi.re.core.network

import com.moko.downkyi.re.core.model.BiliConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 列表接口的字段容错。
 *
 * 这些接口的共同特点是**字段位置与格式不统一**，而且错了不会报错：
 * 少一个 cid 会让下载在很久之后才失败，时长解析成 0 会让预估体积显示为
 * 「0 B」。因此每一条容错都要有独立用例。
 */
class BiliListParsersTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    // ---- cid 的多位置 ----

    @Test
    fun `cid 可以在顶层`() {
        assertEquals(111L, BiliParsers.resolveCid(obj("""{"cid": 111}""")))
    }

    @Test
    fun `cid 可以在 ugc 子对象里（收藏夹最常见）`() {
        assertEquals(222L, BiliParsers.resolveCid(obj("""{"ugc": {"first_cid": 222}}""")))
    }

    @Test
    fun `cid 可以在 pages 的第一项里`() {
        assertEquals(333L, BiliParsers.resolveCid(obj("""{"pages": [{"cid": 333}]}""")))
    }

    @Test
    fun `顶层优先于其他位置`() {
        assertEquals(1L, BiliParsers.resolveCid(obj("""{"cid": 1, "ugc": {"first_cid": 2}}""")))
    }

    @Test
    fun `取不到 cid 时返回 0 而不是抛异常`() {
        // 上层据此决定要不要补查详情接口；若拿 0 直接请求 playurl
        // 会得到 -400，而错误信息指不到病根
        assertEquals(0L, BiliParsers.resolveCid(obj("""{"bvid": "BV1"}""")))
    }

    @Test
    fun `cid 为 0 或负数时继续尝试其他位置`() {
        assertEquals(9L, BiliParsers.resolveCid(obj("""{"cid": 0, "ugc": {"first_cid": 9}}""")))
    }

    @Test
    fun `cid 以字符串形式返回时也能取到`() {
        assertEquals(444L, BiliParsers.resolveCid(obj("""{"ugc": {"first_cid": "444"}}""")))
    }

    // ---- 高亮剥离 ----

    @Test
    fun `剥掉搜索结果标题里的高亮标签`() {
        assertEquals(
            "测试关键词视频",
            BiliParsers.stripHighlight("""测试<em class="keyword">关键词</em>视频"""),
        )
    }

    @Test
    fun `没有高亮标签时保持原样`() {
        assertEquals("普通标题", BiliParsers.stripHighlight("普通标题"))
    }

    @Test
    fun `多处高亮都被剥掉`() {
        assertEquals("abc", BiliParsers.stripHighlight("""<em class="keyword">a</em>b<em>c</em>"""))
    }

    // ---- 时长文本 ----

    @Test
    fun `分秒格式的时长转成毫秒`() {
        assertEquals(258_000L, BiliParsers.parseDurationText("4:18"))
    }

    @Test
    fun `时分秒格式的时长转成毫秒`() {
        assertEquals(3_723_000L, BiliParsers.parseDurationText("1:02:03"))
    }

    @Test
    fun `零时长与非法文本都返回零`() {
        assertEquals(0L, BiliParsers.parseDurationText("0:00"))
        assertEquals(0L, BiliParsers.parseDurationText(""))
        assertEquals(0L, BiliParsers.parseDurationText("未知"))
    }

    @Test
    fun `前后空白不影响解析`() {
        assertEquals(258_000L, BiliParsers.parseDurationText("  4:18  "))
    }

    // ---- 观看历史（字段藏在 history 子对象里）----

    @Test
    fun `解析普通稿件的历史条目`() {
        val item = BiliParsers.parseHistoryItem(
            obj(
                """
                {
                  "title": "看过的视频", "cover": "//i0/cover.jpg",
                  "duration": 218, "author_name": "某UP",
                  "history": {
                    "oid": 12345, "bvid": "BV1xx411c7mD",
                    "cid": 67890, "business": "archive"
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("BV1xx411c7mD", item?.bvid)
        assertEquals(67890L, item?.cid)
        assertEquals(12345L, item?.aid)
        assertEquals(218_000L, item?.durationMs)
        assertEquals("https://i0/cover.jpg", item?.cover)
    }

    @Test
    fun `解析番剧历史条目时靠 epid`() {
        // 番剧条目没有 bvid，取播放地址只能靠 epid
        val item = BiliParsers.parseHistoryItem(
            obj(
                """
                {"title": "看过的番剧", "duration": 1440,
                 "history": {"oid": 111, "epid": 222, "cid": 333, "business": "pgc"}}
                """.trimIndent(),
            ),
        )
        assertEquals("", item?.bvid)
        assertEquals(222L, item?.epId)
        assertEquals(BiliConstants.TYPE_BANGUMI, item?.btype)
    }

    @Test
    fun `直播与专栏的历史条目被跳过`() {
        // 这些不是可下载的视频，留着会变成一堆必然失败的任务
        listOf("live", "article", "article-list").forEach { business ->
            val item = BiliParsers.parseHistoryItem(
                obj("""{"title": "x", "history": {"business": "$business", "oid": 1}}"""),
            )
            assertNull("business=$business 应被跳过", item)
        }
    }

    @Test
    fun `历史条目缺少 bvid 或 cid 时被跳过`() {
        assertNull(
            BiliParsers.parseHistoryItem(
                obj("""{"title": "x", "history": {"business": "archive", "cid": 1}}"""),
            ),
        )
        assertNull(
            BiliParsers.parseHistoryItem(
                obj("""{"title": "x", "history": {"business": "archive", "bvid": "BV1"}}"""),
            ),
        )
    }

    @Test
    fun `历史条目缺少 history 子对象时被跳过`() {
        // 顶层没有 bvid / cid，直接读顶层会拿到空值 —— 这正是曾经的 bug：
        // 每条记录都在「bvid 为空则跳过」处被过滤，最终返回空列表
        assertNull(BiliParsers.parseHistoryItem(obj("""{"title": "x", "bvid": "BV1"}""")))
    }

    // ---- 稍后再看 ----

    @Test
    fun `解析稍后再看条目（字段是平铺的）`() {
        val item = BiliParsers.parseToViewItem(
            obj(
                """
                {"bvid": "BV1xx411c7mD", "cid": 123, "aid": 456,
                 "title": "待看", "pic": "//i0/p.jpg", "duration": 300,
                 "owner": {"name": "某UP"}}
                """.trimIndent(),
            ),
        )
        assertEquals("BV1xx411c7mD", item?.bvid)
        assertEquals(300_000L, item?.durationMs)
        assertEquals("某UP", item?.ownerName)
    }

    @Test
    fun `稍后再看缺少 bvid 或 cid 时被跳过`() {
        assertNull(BiliParsers.parseToViewItem(obj("""{"cid": 1}""")))
        assertNull(BiliParsers.parseToViewItem(obj("""{"bvid": "BV1"}""")))
    }

    // ---- 收藏夹 ----

    @Test
    fun `解析收藏夹条目`() {
        val item = BiliParsers.parseFavoriteItem(
            obj(
                """
                {"bvid": "BV1", "id": 999, "title": "收藏的视频",
                 "cover": "//i0/c.jpg", "duration": 60, "attr": 0,
                 "ugc": {"first_cid": 555},
                 "upper": {"name": "某UP"}}
                """.trimIndent(),
            ),
        )
        assertEquals(555L, item?.cid)
        assertEquals("某UP", item?.ownerName)
    }

    @Test
    fun `已失效的收藏项被跳过`() {
        // attr 最低位是失效标记，这种条目没有有效 id
        assertNull(
            BiliParsers.parseFavoriteItem(
                obj("""{"bvid": "BV1", "attr": 1, "title": "已失效视频"}"""),
            ),
        )
    }

    @Test
    fun `attr 的其他位不影响解析`() {
        val item = BiliParsers.parseFavoriteItem(
            obj("""{"bvid": "BV1", "attr": 2, "title": "正常视频", "ugc": {"first_cid": 1}}"""),
        )
        assertEquals("BV1", item?.bvid)
    }

    // ---- 搜索 ----

    @Test
    fun `解析搜索结果时剥离高亮并转换时长文本`() {
        val item = BiliParsers.parseSearchItem(
            obj(
                """
                {"bvid": "BV1", "aid": 1, "pic": "//i0/s.jpg",
                 "title": "搜索<em class=\"keyword\">词</em>结果",
                 "duration": "4:18", "author": "某UP"}
                """.trimIndent(),
            ),
        )
        assertEquals("搜索词结果", item?.title)
        assertEquals(258_000L, item?.durationMs)
    }

    @Test
    fun `搜索结果不返回 cid`() {
        val item = BiliParsers.parseSearchItem(obj("""{"bvid": "BV1", "title": "x"}"""))
        assertEquals(0L, item?.cid)
    }

    @Test
    fun `搜索结果缺少 bvid 时被跳过`() {
        assertNull(BiliParsers.parseSearchItem(obj("""{"title": "x"}""")))
    }

    // ---- UP 主投稿与合集（都不返回 cid）----

    @Test
    fun `解析 UP 主投稿条目且 cid 留空待补查`() {
        val item = BiliParsers.parseSpaceArchiveItem(
            obj("""{"bvid": "BV1", "aid": 2, "title": "投稿", "pic": "//i0/p.jpg", "length": 90, "author": "UP"}"""),
        )
        assertEquals(0L, item?.cid)
        assertEquals(90_000L, item?.durationMs)
        // 注意 hasPlayableId 的语义是「能否**直接**请求播放地址」，
        // 而不是「最终能否下载」。cid 缺失时必须先补查详情，因此这里是 false
        assertEquals(false, item?.hasPlayableId)
        // 但有 bvid 就一定能补查到 cid，所以这条依然是可用的
        assertEquals("BV1", item?.bvid)
    }

    @Test
    fun `解析合集条目`() {
        val item = BiliParsers.parseSeasonArchiveItem(
            obj("""{"bvid": "BV2", "aid": 3, "title": "合集项", "pic": "//i0/s.jpg", "duration": 120}"""),
        )
        assertEquals("BV2", item?.bvid)
        assertEquals(0L, item?.cid)
        assertEquals(120_000L, item?.durationMs)
    }

    @Test
    fun `合集条目缺少 bvid 时被跳过`() {
        assertNull(BiliParsers.parseSeasonArchiveItem(obj("""{"aid": 3}""")))
    }
}
