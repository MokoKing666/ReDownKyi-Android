package com.moko.downkyi.re.core.network

import com.moko.downkyi.re.core.model.BiliConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 请求参数拼装。
 *
 * 这部分没有任何 IO，却最容易静默出错：少一个 `fnval` 位就少一整档清晰度，
 * `fourk` 写错会让 4K 永远不可用，端点选错会返回 -404 ——
 * 三种情况都不会在编译或运行时报警，只会安静地给出更差的结果。
 */
class BiliQueriesTest {

    // ---- 播放地址的公共参数 ----

    @Test
    fun `fnval 必须是覆盖全部能力位的 4048`() {
        // 16(DASH) | 64(HDR) | 128(4K) | 256(杜比音频) | 512(杜比视界) | 1024(8K) | 2048(AV1)
        // 少任何一位，对应清晰度或编码就不会出现在返回结果里
        val query = BiliQueries.playUrlBase(cid = 1, quality = 80)
        assertEquals("4048", query["fnval"])
    }

    @Test
    fun `fnval 的位组合可以逐位还原`() {
        val fnval = BiliConstants.FNVAL_DASH
        assertTrue("DASH 位缺失", fnval and 16 != 0)
        assertTrue("HDR 位缺失", fnval and 64 != 0)
        assertTrue("4K 位缺失", fnval and 128 != 0)
        assertTrue("杜比音频位缺失", fnval and 256 != 0)
        assertTrue("杜比视界位缺失", fnval and 512 != 0)
        assertTrue("8K 位缺失", fnval and 1024 != 0)
        assertTrue("AV1 位缺失", fnval and 2048 != 0)
    }

    @Test
    fun `fnver 与 fourk 必须存在否则高清晰度会被忽略`() {
        val query = BiliQueries.playUrlBase(cid = 1, quality = 116)
        assertEquals("0", query["fnver"])
        assertEquals("1", query["fourk"])
    }

    @Test
    fun `清晰度与 cid 被如实传递`() {
        val query = BiliQueries.playUrlBase(cid = 12345678L, quality = 127)
        assertEquals("12345678", query["cid"])
        assertEquals("127", query["qn"])
    }

    // ---- 端点选择 ----

    @Test
    fun `按业务类型选择正确的播放地址端点`() {
        assertEquals("/x/player/wbi/playurl", BiliQueries.playUrlPath(BiliConstants.TYPE_VIDEO))
        assertEquals("/pgc/player/web/playurl", BiliQueries.playUrlPath(BiliConstants.TYPE_BANGUMI))
        assertEquals("/pugv/player/web/playurl", BiliQueries.playUrlPath(BiliConstants.TYPE_CHEESE))
    }

    @Test
    fun `只有普通稿件需要 wbi 签名`() {
        // 番剧与课程端点不签名 —— 给它们加上签名会返回参数错误
        assertTrue(BiliQueries.needsWbi(BiliConstants.TYPE_VIDEO))
        assertFalse(BiliQueries.needsWbi(BiliConstants.TYPE_BANGUMI))
        assertFalse(BiliQueries.needsWbi(BiliConstants.TYPE_CHEESE))
    }

    @Test
    fun `未知业务类型回退到普通稿件路径`() {
        assertEquals("/x/player/wbi/playurl", BiliQueries.playUrlPath("未知类型"))
    }

    // ---- 附加参数 ----

    @Test
    fun `番剧与课程带上 otype`() {
        // 缺 otype 会被服务端按 XML 返回，解析直接失败
        val extra = BiliQueries.episodeExtra(bvid = "", epId = 123L)
        assertEquals("json", extra["otype"])
        assertEquals("123", extra["ep_id"])
    }

    @Test
    fun `普通稿件不带 otype`() {
        assertFalse(BiliQueries.archiveExtra("BV1xx411c7mD").containsKey("otype"))
    }

    @Test
    fun `epId 为空时不写进参数`() {
        assertFalse(BiliQueries.episodeExtra(bvid = "BV1", epId = null).containsKey("ep_id"))
    }

    @Test
    fun `bvid 为空时不写进参数`() {
        // 番剧条目没有 bvid，写一个空字符串会让服务端判为参数错误
        assertFalse(BiliQueries.archiveExtra("").containsKey("bvid"))
        assertFalse(BiliQueries.episodeExtra(bvid = "", epId = 1L).containsKey("bvid"))
    }

    // ---- 详情参数 ----

    @Test
    fun `BV 号用 bvid 参数`() {
        val (key, value) = BiliQueries.videoDetailQuery("BV1xx411c7mD")
        assertEquals("bvid", key)
        assertEquals("BV1xx411c7mD", value)
    }

    @Test
    fun `av 号用 aid 参数且去掉前缀`() {
        // 把 "av123" 原样传给 aid 会被服务端拒绝
        val (key, value) = BiliQueries.videoDetailQuery("av123456")
        assertEquals("aid", key)
        assertEquals("123456", value)
    }

    @Test
    fun `av 前缀大小写不敏感`() {
        assertEquals("123456", BiliQueries.videoDetailQuery("AV123456").second)
    }

    // ---- 季信息参数 ----

    @Test
    fun `季信息支持按季号或按集号查询`() {
        assertEquals("100", BiliQueries.seasonQuery(seasonId = 100L, epId = null)["season_id"])
        assertEquals("200", BiliQueries.seasonQuery(seasonId = null, epId = 200L)["ep_id"])
    }

    @Test
    fun `季信息两个参数都为空时不写入任何键`() {
        assertTrue(BiliQueries.seasonQuery(null, null).isEmpty())
    }
}
