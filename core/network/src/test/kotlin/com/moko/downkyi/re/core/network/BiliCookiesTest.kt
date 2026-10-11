package com.moko.downkyi.re.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cookie 解析与登录态判定。
 *
 * 重点覆盖「手工粘贴」这种真实场景 —— 用户从浏览器扩展或开发者工具里
 * 复制出来的 Cookie 经常带着多余空格、中文分号、尾随分号。
 * 解析失败的表现是「Cookie 明明填了却说未登录」，很难自查。
 */
class BiliCookiesTest {

    @Test
    fun `解析标准的浏览器 Cookie 字符串`() {
        val cookies = BiliCookies()
        cookies.setHeader("SESSDATA=abc123; bili_jct=def456; DedeUserID=789")
        assertEquals("abc123", cookies[BiliCookies.KEY_SESSDATA])
        assertEquals("def456", cookies[BiliCookies.KEY_CSRF])
        assertEquals("789", cookies.userId)
        assertTrue(cookies.isLogin)
    }

    @Test
    fun `容忍中文分号与等号两侧的空格`() {
        val cookies = BiliCookies()
        cookies.setHeader("  SESSDATA = abc123 ； bili_jct = def456 ；")
        assertEquals("abc123", cookies[BiliCookies.KEY_SESSDATA])
        assertEquals("def456", cookies[BiliCookies.KEY_CSRF])
        // 尾随的空条目不应产生垃圾键
        assertEquals(2, cookies.snapshot().size)
    }

    @Test
    fun `没有 SESSDATA 就不算登录`() {
        val cookies = BiliCookies()
        cookies.setHeader("bili_jct=def456; DedeUserID=789")
        assertFalse(cookies.isLogin)
        // 但其它 Cookie 依然保留 —— 它们对某些接口仍有用
        assertEquals("789", cookies.userId)
    }

    @Test
    fun `SESSDATA 为空串也不算登录`() {
        val cookies = BiliCookies()
        cookies.setHeader("SESSDATA=; bili_jct=def456")
        assertFalse(cookies.isLogin)
    }

    @Test
    fun `header 按插入顺序拼接`() {
        val cookies = BiliCookies()
        cookies.set("a", "1")
        cookies.set("b", "2")
        assertEquals("a=1; b=2", cookies.header)
    }

    @Test
    fun `空名字与空值都不会写入`() {
        val cookies = BiliCookies()
        cookies.set("", "1")
        cookies.set("a", "")
        cookies.set("  ", "1")
        assertTrue(cookies.isEmpty)
        assertNull(cookies["a"])
    }

    @Test
    fun `没有等号的片段被跳过`() {
        val cookies = BiliCookies()
        cookies.setHeader("garbage; SESSDATA=abc; =nokey")
        assertEquals("abc", cookies[BiliCookies.KEY_SESSDATA])
        assertEquals(1, cookies.snapshot().size)
    }

    @Test
    fun `快照与恢复保持内容`() {
        val cookies = BiliCookies()
        cookies.setHeader("SESSDATA=abc; bili_jct=def")
        val snapshot = cookies.snapshot()

        cookies.clear()
        assertTrue(cookies.isEmpty)
        assertFalse(cookies.isLogin)

        cookies.restore(snapshot)
        assertEquals("abc", cookies[BiliCookies.KEY_SESSDATA])
        assertEquals("def", cookies[BiliCookies.KEY_CSRF])
        assertTrue(cookies.isLogin)
    }

    @Test
    fun `快照是副本，修改它不会影响原仓库`() {
        val cookies = BiliCookies()
        cookies.set("a", "1")
        val snapshot = cookies.snapshot()
        (snapshot as MutableMap)["b"] = "2"
        assertNull(cookies["b"])
    }

    @Test
    fun `覆盖同名 Cookie 时保留原位`() {
        val cookies = BiliCookies()
        cookies.set("a", "1")
        cookies.set("b", "2")
        cookies.set("a", "9")
        // LinkedHashMap 覆盖已存在的键不会改变顺序
        assertEquals("a=9; b=2", cookies.header)
    }
}
