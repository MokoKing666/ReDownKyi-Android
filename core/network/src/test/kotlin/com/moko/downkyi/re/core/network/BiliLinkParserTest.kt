package com.moko.downkyi.re.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 链接识别。
 *
 * 这是整个解析流程的入口：识别错了，后面所有步骤都会在错误的资源上展开，
 * 而错误信息往往出现在很远的地方（例如「请求失败」而不是「识别成了课程」）。
 * 因此判定顺序相关的场景都要单独钉住。
 */
class BiliLinkParserTest {

    private fun parse(text: String) = BiliLinkParser.parse(text)

    // ---- 基础 ----

    @Test
    fun `空输入与空白输入都视为未识别`() {
        assertEquals(LinkKind.UNKNOWN, parse("").kind)
        assertEquals(LinkKind.UNKNOWN, parse("   ").kind)
        assertFalse(parse("").isSupported)
    }

    @Test
    fun `裸 BV 号`() {
        val info = parse("BV1xx411c7mD")
        assertEquals(LinkKind.VIDEO, info.kind)
        assertEquals("BV1xx411c7mD", info.id)
        assertTrue(info.isSupported)
    }

    @Test
    fun `完整视频链接`() {
        val info = parse("https://www.bilibili.com/video/BV1xx411c7mD")
        assertEquals(LinkKind.VIDEO, info.kind)
        assertEquals("BV1xx411c7mD", info.id)
    }

    @Test
    fun `带分 P 参数时提取页码`() {
        val info = parse("https://www.bilibili.com/video/BV1xx411c7mD?p=3")
        assertEquals(LinkKind.VIDEO, info.kind)
        assertEquals("BV1xx411c7mD", info.id)
        assertEquals(3, info.page)
    }

    @Test
    fun `没有分 P 参数时 page 为 null`() {
        assertNull(parse("BV1xx411c7mD").page)
    }

    // ---- av 号 ----

    @Test
    fun `av 号统一输出小写形式`() {
        assertEquals("av123456", parse("av123456").id)
        assertEquals("av123456", parse("AV123456").id)
        assertEquals(LinkKind.VIDEO, parse("AV123456").kind)
    }

    @Test
    fun `课程链接里的 av 不该被当成稿件`() {
        // /cheese/av123 是课程，识别成稿件会让后续请求打到错误的接口上
        val info = parse("https://www.bilibili.com/cheese/play/av123")
        assertFalse("不应识别为稿件", info.kind == LinkKind.VIDEO)
    }

    // ---- 番剧与课程 ----

    @Test
    fun `番剧单集与整季`() {
        val ep = parse("https://www.bilibili.com/bangumi/play/ep123456")
        assertEquals(LinkKind.BANGUMI_EP, ep.kind)
        assertEquals("123456", ep.id)

        val ss = parse("https://www.bilibili.com/bangumi/play/ss6789")
        assertEquals(LinkKind.BANGUMI_SEASON, ss.kind)
        assertEquals("6789", ss.id)
    }

    @Test
    fun `课程链接识别为课程而不是番剧`() {
        val ep = parse("https://www.bilibili.com/cheese/play/ep123456")
        assertEquals(LinkKind.CHEESE, ep.kind)

        val ss = parse("https://www.bilibili.com/cheese/play/ss6789")
        assertEquals(LinkKind.CHEESE, ss.kind)
    }

    @Test
    fun `pugv 前缀同样把 ep 与 ss 归为课程`() {
        // 注意 pugv 是课程**接口**的路径前缀，不是网页路径。
        // 它本身不足以判定资源类型 —— 必须同时出现 ep / ss 才有意义，
        // 光有 /pugv/play/123 既不是番剧也不是课程
        val ep = parse("https://www.bilibili.com/pugv/play/ep123")
        assertEquals(LinkKind.CHEESE, ep.kind)
        assertEquals("123", ep.id)

        val ss = parse("https://www.bilibili.com/pugv/play/ss456")
        assertEquals(LinkKind.CHEESE, ss.kind)
        assertEquals("456", ss.id)
    }

    @Test
    fun `只有 pugv 没有 ep 或 ss 时无法判定类型`() {
        // 记录下来：这类链接需要用户补充更完整的地址，而不是猜
        assertEquals(LinkKind.UNKNOWN, parse("https://www.bilibili.com/pugv/play/123").kind)
    }

    // ---- 收藏夹 ----

    @Test
    fun `收藏夹链接从 fid 取 id`() {
        val info = parse("https://www.bilibili.com/medialist/detail/ml123456?fid=123456")
        assertEquals(LinkKind.FAVORITES, info.kind)
        assertEquals("123456", info.id)
    }

    @Test
    fun `media_id 与 fav_id 都能识别`() {
        assertEquals("789", parse("https://x/?media_id=789").id)
        assertEquals("101", parse("https://x/?fav_id=101").id)
    }

    @Test
    fun `四位以上的纯数字视为收藏夹`() {
        val info = parse("123456")
        assertEquals(LinkKind.FAVORITES, info.kind)
        assertEquals("123456", info.id)
    }

    @Test
    fun `三位数字不算收藏夹`() {
        // 阈值是 4 位：避免把「123」这类误输入当成收藏夹 id
        assertEquals(LinkKind.UNKNOWN, parse("123").kind)
    }

    // ---- UP 主空间与合集 ----

    @Test
    fun `UP 主空间链接`() {
        val info = parse("https://space.bilibili.com/12345")
        assertEquals(LinkKind.SPACE, info.kind)
        assertEquals("12345", info.id)
    }

    @Test
    fun `合集链接优先于 UP 主空间被识别`() {
        // 这个链接同时匹配空间与合集两条规则，必须命中合集 ——
        // 否则一个合集链接会变成「读整个 UP 主的投稿」
        val info = parse("https://space.bilibili.com/12345/lists/6789")
        assertEquals(LinkKind.SEASON, info.kind)
        assertEquals("12345:6789", info.id)
    }

    @Test
    fun `收藏夹、空间与合集不带分 P 信息`() {
        assertNull(parse("https://space.bilibili.com/12345").page)
        assertNull(parse("https://space.bilibili.com/12345/lists/6789").page)
        assertNull(parse("https://x/?fid=123456").page)
    }

    // ---- 短链 ----

    @Test
    fun `b23 短链识别为短链并保留短码`() {
        val info = parse("https://b23.tv/abc123")
        assertEquals(LinkKind.SHORT_LINK, info.kind)
        assertEquals("abc123", info.id)
    }

    @Test
    fun `bili2233 短链同样识别`() {
        val info = parse("https://bili2233.cn/xyz789")
        assertEquals(LinkKind.SHORT_LINK, info.kind)
        assertEquals("xyz789", info.id)
    }

    @Test
    fun `短链判定优先于其它规则`() {
        // 短码里可能出现形似 BV 号的片段，若不让短链先匹配，
        // 会从一个未跳转的短码里解析出错误的资源 id
        val info = parse("https://b23.tv/BV1xx411c7mD")
        assertEquals(LinkKind.SHORT_LINK, info.kind)
        assertEquals("BV1xx411c7mD", info.id)
    }

    // ---- 未识别 ----

    @Test
    fun `普通文字视为未识别并保留原文`() {
        val info = parse("这是一个视频标题")
        assertEquals(LinkKind.UNKNOWN, info.kind)
        assertFalse(info.isSupported)
        assertEquals("这是一个视频标题", info.raw)
    }

    @Test
    fun `describe 对各类型都有可读描述`() {
        assertEquals("视频 BV1xx411c7mD", parse("BV1xx411c7mD").describe)
        assertEquals("番剧剧集 ep123", parse("ep123").describe)
        assertEquals("收藏夹 123456", parse("123456").describe)
        assertEquals("未识别的链接", parse("随便什么").describe)
    }

    @Test
    fun `两侧空白会被裁剪`() {
        val info = parse("  BV1xx411c7mD  ")
        assertEquals(LinkKind.VIDEO, info.kind)
        assertEquals("BV1xx411c7mD", info.id)
    }
}
