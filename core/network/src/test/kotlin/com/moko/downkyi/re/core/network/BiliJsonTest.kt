package com.moko.downkyi.re.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 容错访问层。
 *
 * 这里的每个用例都对应一类真实出现过的脏数据。B 站接口不是稳定契约：
 * 同一个语义的字段会在不同接口里换位置、换类型、或者干脆不返回，
 * 而这一层就是用来吃下这些差异的 —— 所以必须逐条钉住。
 */
class BiliJsonTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun JsonPrimitive.number(): Long = content.toLong()

    // ---- 缺失与 null ----

    @Test
    fun `缺失字段返回默认值而不抛异常`() {
        val json = obj("""{"a": 1}""")
        assertEquals("", json.string("missing"))
        assertEquals(0, json.int("missing"))
        assertEquals(0L, json.long("missing"))
        assertEquals(0.0, json.double("missing"), 0.0)
        assertFalse(json.bool("missing"))
        assertNull(json.stringOrNull("missing"))
        assertNull(json.obj("missing"))
        assertNull(json.array("missing"))
    }

    @Test
    fun `显式 null 按缺失处理`() {
        val json = obj("""{"a": null}""")
        assertNull(json.stringOrNull("a"))
        assertEquals("", json.string("a"))
        assertEquals(0L, json.long("a"))
    }

    @Test
    fun `空字符串在 stringOrNull 里视为无值`() {
        // bvid 在番剧条目里恒为空串，调用方需要能区分「没有」与「有但是空」
        val json = obj("""{"bvid": ""}""")
        assertNull(json.stringOrNull("bvid"))
        assertEquals("", json.string("bvid"))
    }

    // ---- 类型不稳定 ----

    @Test
    fun `数字字段同时接受数字与字符串形态`() {
        // duration 在番剧接口里是字符串、在稿件接口里是数字
        val asNumber = obj("""{"cid": 123}""")
        val asString = obj("""{"cid": "123"}""")
        assertEquals(123L, asNumber.longLoose("cid"))
        assertEquals(123L, asString.longLoose("cid"))
        assertEquals(123, asString.intLoose("cid"))
    }

    @Test
    fun `宽松数字遇到非数字内容时回退默认值`() {
        val json = obj("""{"v": "abc"}""")
        assertEquals(7L, json.longLoose("v", 7L))
        assertEquals(0L, json.longLoose("v"))
    }

    @Test
    fun `布尔字段接受 0 与 1`() {
        assertTrue(obj("""{"f": 1}""").bool("f"))
        assertFalse(obj("""{"f": 0}""").bool("f"))
        assertTrue(obj("""{"f": true}""").bool("f"))
        assertFalse(obj("""{"f": false}""").bool("f"))
    }

    @Test
    fun `布尔字段缺失时用默认值`() {
        assertTrue(obj("""{}""").bool("f", default = true))
        assertFalse(obj("""{}""").bool("f"))
    }

    // ---- 深层访问 ----

    @Test
    fun `deepGet 逐层进入`() {
        val json = obj("""{"ugc": {"first_cid": 456}}""")
        assertEquals(456L, (json.deepGet("ugc", "first_cid") as JsonPrimitive).number())
        assertNull(json.deepGet("ugc", "missing"))
        assertNull(json.deepGet("nope", "x"))
        // 中途遇到非对象也要安全返回
        assertNull(json.deepGet("ugc", "first_cid", "deeper"))
    }

    @Test
    fun `firstOf 返回第一个命中的路径`() {
        val json = obj("""{"a": {"b": 1}}""")
        val found = json.firstOf(listOf("x"), listOf("a", "b"))
        assertEquals(1L, (found as JsonPrimitive).number())
        assertNull(json.firstOf(listOf("x"), listOf("y")))
        assertNull(json.firstOf())
    }

    // ---- cid 的多位置查找（原项目踩过的真实坑）----

    @Test
    fun `cid 可以从顶层取到`() {
        val json = obj("""{"cid": 111, "bvid": "BV1"}""")
        assertEquals(111L, json.longFromPaths(listOf("cid")))
    }

    @Test
    fun `cid 可以从 ugc 子对象取到`() {
        // 收藏夹接口把 cid 放在 ugc.first_cid
        val json = obj("""{"ugc": {"first_cid": 222}}""")
        assertEquals(222L, json.longFromPaths(listOf("cid"), listOf("ugc", "first_cid")))
    }

    @Test
    fun `cid 以字符串返回时也能取到`() {
        val json = obj("""{"ugc": {"first_cid": "333"}}""")
        assertEquals(333L, json.longFromPaths(listOf("cid"), listOf("ugc", "first_cid")))
    }

    @Test
    fun `UP 主投稿与合集不返回 cid 时得到 null`() {
        // 这种情况必须返回 null 而不是 0 —— 上层据此决定要不要补查详情接口。
        // 若返回 0，请求 playurl 会拿到 -400，而错误信息完全指不到病根
        val json = obj("""{"bvid": "BV1", "title": "x"}""")
        assertNull(json.longFromPaths(listOf("cid"), listOf("ugc", "first_cid"), listOf("aid")))
    }

    @Test
    fun `按顺序命中第一个可用的 cid 位置`() {
        // 顶层有就用顶层，不去看后面的位置
        val json = obj("""{"cid": 1, "ugc": {"first_cid": 2}}""")
        assertEquals(1L, json.longFromPaths(listOf("cid"), listOf("ugc", "first_cid")))
    }
}
