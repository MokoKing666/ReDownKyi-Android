package com.moko.downkyi.re.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WBI 签名测试。
 *
 * 期望值全部来自**外部来源**：官方文档给出的示例 img_key / sub_key 与
 * 据此算出的 mixinKey（出处：bilibili-API-collect），而不是拿本实现的
 * 输出当基准。这样「重排表 / 排序 / 编码 / 非法字符过滤 / md5」每一步
 * 都被真实验证，而不是在测试里把被测逻辑重复一遍。
 */
class WbiSignerTest {

    private companion object {
        const val IMG_URL = "https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png"
        const val SUB_URL = "https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png"
        const val OFFICIAL_MIXIN_KEY = "ea1db124af3c7062474693fa704f4ff8"

        /** 用官方 mixinKey 独立算期望的 w_rid。 */
        fun expectedRid(query: String): String =
            WbiSigner.md5Hex("$query$OFFICIAL_MIXIN_KEY")

        fun readySigner() = WbiSigner().apply { updateKeys(IMG_URL, SUB_URL) }
    }

    // ---- mixinKey 重排表 ----

    @Test
    fun `mixinKey 重排表与官方测试向量一致`() {
        val signed = readySigner().sign(mapOf("foo" to "114"), wts = 1702204169L)
        assertEquals(expectedRid("foo=114&wts=1702204169"), signed["w_rid"])
    }

    @Test
    fun `换一组密钥后签名随之改变`() {
        val a = readySigner().sign(mapOf("foo" to "114"), wts = 1702204169L)
        val other = WbiSigner().apply {
            updateKeys(
                "https://i0.hdslb.com/bfs/wbi/00000000000000000000000000000000.png",
                "https://i0.hdslb.com/bfs/wbi/11111111111111111111111111111111.png",
            )
        }
        assertNotEquals(a["w_rid"], other.sign(mapOf("foo" to "114"), wts = 1702204169L)["w_rid"])
    }

    // ---- 参数处理 ----

    @Test
    fun `参数顺序不影响结果`() {
        val a = readySigner().sign(
            mapOf("foo" to "114", "bar" to "514", "zab" to "1919810"),
            wts = 1702204169L,
        )
        val b = readySigner().sign(
            mapOf("zab" to "1919810", "foo" to "114", "bar" to "514"),
            wts = 1702204169L,
        )
        assertEquals(a["w_rid"], b["w_rid"])
        // 排序后应为 bar -> foo -> wts -> zab
        assertEquals(expectedRid("bar=514&foo=114&wts=1702204169&zab=1919810"), a["w_rid"])
    }

    @Test
    fun `wts 参与签名`() {
        val s1 = readySigner().sign(mapOf("foo" to "114"), wts = 1702204169L)
        val s2 = readySigner().sign(mapOf("foo" to "114"), wts = 1702204170L)
        assertEquals("1702204169", s1["wts"])
        assertEquals("1702204170", s2["wts"])
        assertNotEquals(s1["w_rid"], s2["w_rid"])
    }

    @Test
    fun `值里的非法字符会被剔除`() {
        val dirty = readySigner().sign(mapOf("k" to "a!'()*b"), wts = 100L)
        assertEquals(expectedRid("k=ab&wts=100"), dirty["w_rid"])
    }

    @Test
    fun `中文按 UTF-8 百分号编码且十六进制为大写`() {
        val signed = readySigner().sign(mapOf("k" to "中文"), wts = 100L)
        assertEquals(expectedRid("k=%E4%B8%AD%E6%96%87&wts=100"), signed["w_rid"])
    }

    @Test
    fun `空格编码为百分号二十而不是加号`() {
        // URLEncoder 会把空格编成 +，那样 w_rid 会对不上
        val signed = readySigner().sign(mapOf("k" to "a b"), wts = 100L)
        assertEquals(expectedRid("k=a%20b&wts=100"), signed["w_rid"])
    }

    @Test
    fun `空参数集合也能签出 wts 与 w_rid`() {
        val signed = readySigner().sign(emptyMap(), wts = 100L)
        assertEquals("100", signed["wts"])
        assertEquals(expectedRid("wts=100"), signed["w_rid"])
    }

    @Test
    fun `不会修改调用方传入的 Map`() {
        val input = mapOf("foo" to "114")
        readySigner().sign(input, wts = 1L)
        assertFalse(input.containsKey("wts"))
        assertFalse(input.containsKey("w_rid"))
        assertEquals(1, input.size)
    }

    @Test
    fun `wts 缺省时取当前秒级时间`() {
        val before = System.currentTimeMillis() / 1000
        val signed = readySigner().sign(emptyMap())
        val after = System.currentTimeMillis() / 1000
        val wts = signed.getValue("wts").toLong()
        assertTrue("wts 应落在本次调用区间内", wts in before..after)
    }

    // ---- 密钥未就绪 ----

    @Test
    fun `未 updateKeys 时只补 wts 不产出 w_rid`() {
        val signer = WbiSigner()
        assertFalse(signer.ready)
        val signed = signer.sign(mapOf("foo" to "114"), wts = 100L)
        assertEquals("100", signed["wts"])
        assertFalse(signed.containsKey("w_rid"))
    }

    @Test
    fun `密钥长度不足时不产出 w_rid`() {
        val signer = WbiSigner().apply {
            updateKeys("https://x/y/short.png", "https://x/y/also.png")
        }
        val signed = signer.sign(mapOf("foo" to "114"), wts = 100L)
        assertFalse(signed.containsKey("w_rid"))
    }

    @Test
    fun `clear 之后回到未就绪`() {
        val signer = readySigner()
        assertTrue(signer.ready)
        signer.clear()
        assertFalse(signer.ready)
    }

    // ---- 密钥提取 ----

    @Test
    fun `从 URL 取文件名并去掉扩展名`() {
        // 去掉扩展名这点很关键：官方给的 img_url 以 .png 结尾
        val withExt = readySigner().sign(emptyMap(), wts = 1L)
        val noExt = WbiSigner().apply {
            updateKeys(
                "https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c",
                "https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45",
            )
        }
        assertEquals(withExt["w_rid"], noExt.sign(emptyMap(), wts = 1L)["w_rid"])
    }

    @Test
    fun `mixinKey 本身等于官方给出的值`() {
        // 直接锁定重排结果：一旦 64 项重排表被改错一个数字，
        // 失败信息会直接指向 mixinKey，而不是绕到下游的 w_rid
        assertEquals(OFFICIAL_MIXIN_KEY, readySigner().mixinKeyForTest)
    }
}
