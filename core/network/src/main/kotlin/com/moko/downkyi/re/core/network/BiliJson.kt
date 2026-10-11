package com.moko.downkyi.re.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * B 站接口 JSON 的容错访问层。
 *
 * 这一层是必需的，不是过度设计。B 站接口的字段有三类常见陷阱：
 *
 *  1. **同一个字段出现在不同层级**：`cid` 可能出现在顶层、`ugc.first_cid`、
 *     `pages[0].cid`，而 UP 主投稿与合集接口**根本不返回** `cid`；
 *  2. **类型不稳定**：`duration` 有时是秒有时是毫秒，数字型 id 偶尔以字符串返回；
 *  3. **字段缺失**：`bvid` 在番剧条目里恒为空字符串。
 *
 * 用代码生成（`@Serializable`）的话，任何一条都会直接抛异常，整个响应报废。
 * 手动访问则能逐字段兜底，缺少某个字段只影响用到它的那一个功能。
 */

// ---------------------------------------------------------------------------
// 基础访问器：缺失或类型不符时返回默认值，绝不抛异常
// ---------------------------------------------------------------------------

fun JsonObject.stringOrNull(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    return primitive.content.takeIf { it.isNotEmpty() }
}

fun JsonObject.string(key: String, default: String = ""): String = stringOrNull(key) ?: default

fun JsonObject.int(key: String, default: Int = 0): Int = intOrNull(key) ?: default

fun JsonObject.intOrNull(key: String): Int? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.intOrNull
}

fun JsonObject.long(key: String, default: Long = 0L): Long = longOrNull(key) ?: default

fun JsonObject.longOrNull(key: String): Long? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.longOrNull
}

fun JsonObject.double(key: String, default: Double = 0.0): Double {
    val primitive = this[key] as? JsonPrimitive ?: return default
    return primitive.doubleOrNull ?: default
}

fun JsonObject.bool(key: String, default: Boolean = false): Boolean {
    val primitive = this[key] as? JsonPrimitive ?: return default
    // 有些接口用 0/1 表示布尔
    return primitive.booleanOrNull
        ?: primitive.intOrNull?.let { it != 0 }
        ?: default
}

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

// ---------------------------------------------------------------------------
// 宽松数字：B 站的数字型 id / 时长偶尔以字符串返回
// ---------------------------------------------------------------------------

/**
 * 读一个数字，同时接受 `123` 与 `"123"` 两种形态。
 *
 * 这个容错不是猜测：`duration` 在番剧接口里是字符串、在稿件接口里是数字，
 * 不兼容的话会在番剧解析上静默拿到 0，进而让预估体积和进度计算全部失准。
 */
fun JsonObject.longLoose(key: String, default: Long = 0L): Long {
    val element = this[key] ?: return default
    val primitive = element as? JsonPrimitive ?: return default
    primitive.longOrNull?.let { return it }
    return primitive.content.trim().toLongOrNull() ?: default
}

fun JsonObject.intLoose(key: String, default: Int = 0): Int =
    longLoose(key, default.toLong()).toInt()

// ---------------------------------------------------------------------------
// 深层访问
// ---------------------------------------------------------------------------

/**
 * 逐层进入。
 *
 * 用途是应对「同一个字段出现在不同层级」：调用方可以按
 * `json.path("ugc", "first_cid")` 试一个位置，拿不到再试另一个，
 * 而不是写一串 `(json["ugc"] as? JsonObject)?.get("first_cid")`。
 *
 * 任何一层缺失或类型不符都返回 null。
 */
fun JsonObject.deepGet(vararg keys: String): JsonElement? {
    var current: JsonElement = this
    for (key in keys) {
        val obj = current as? JsonObject ?: return null
        current = obj[key] ?: return null
    }
    return current.takeIf { it !is JsonNull }
}

/** 按顺序尝试多个路径，返回第一个非空结果。 */
fun JsonObject.firstOf(vararg keyPaths: List<String>): JsonElement? {
    for (keys in keyPaths) {
        if (keys.isEmpty()) continue
        val found = deepGet(*keys.toTypedArray())
        if (found != null) return found
    }
    return null
}

/**
 * 在多个路径里找一个数字并转成 Long。
 *
 * 专为 `cid` 这类字段设计 —— 它可能在顶层、`ugc.first_cid`、`pages[0].cid`，
 * 也可能全都取不到（需要用详情接口补查）。
 */
fun JsonObject.longFromPaths(vararg keyPaths: List<String>): Long? {
    for (keys in keyPaths) {
        val element = deepGet(*keys.toTypedArray()) ?: continue
        val primitive = element as? JsonPrimitive ?: continue
        primitive.longOrNull?.let { return it }
        primitive.content.trim().toLongOrNull()?.let { return it }
    }
    return null
}
