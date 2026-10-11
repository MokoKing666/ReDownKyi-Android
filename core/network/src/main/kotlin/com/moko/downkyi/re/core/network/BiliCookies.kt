package com.moko.downkyi.re.core.network

import okhttp3.Response

/**
 * Cookie 仓库。
 *
 * 用有序 Map 而不是 OkHttp 的 CookieJar：B 站需要的是**原样转发一串
 * 浏览器 Cookie**（含 `SESSDATA`、`bili_jct`、`DedeUserID`、`buvid3` 等），
 * 而 CookieJar 的语义是「按域名与路径规则匹配」—— 那套规则在这里用不上，
 * 反而会因为域匹配问题悄悄丢掉关键条目。
 */
class BiliCookies {

    private val store = LinkedHashMap<String, String>()

    /** 拼成请求头用的形态。 */
    val header: String
        get() = store.entries.joinToString("; ") { "${it.key}=${it.value}" }

    /**
     * 是否已登录。
     *
     * 判据是 `SESSDATA` 非空 —— 这是 B 站唯一的会话凭据，
     * 其它 Cookie（`bili_jct` 是 CSRF token、`DedeUserID` 是用户标识）
     * 单独存在都不构成登录态。
     */
    val isLogin: Boolean get() = !store[KEY_SESSDATA].isNullOrEmpty()

    /** 当前用户 UID，未登录时为空串。 */
    val userId: String get() = store[KEY_USER_ID].orEmpty()

    val isEmpty: Boolean get() = store.isEmpty()

    fun set(name: String, value: String) {
        if (name.isBlank() || value.isBlank()) return
        store[name] = value
    }

    operator fun get(name: String): String? = store[name]

    /**
     * 解析从浏览器复制出来的 Cookie 字符串。
     *
     * 容忍常见的手工粘贴问题：分隔符是中文分号、条目里有多余空格、
     * 以及结尾的尾随分号。
     */
    fun setHeader(raw: String) {
        raw.split(';', '；').forEach { part ->
            val index = part.indexOf('=')
            // index == 0 说明没有名字，直接跳过
            if (index <= 0) return@forEach
            val name = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            set(name, value)
        }
    }

    /** 吸收响应里下发的 Set-Cookie。 */
    fun absorb(response: Response) {
        response.headers.values("Set-Cookie").forEach { raw ->
            // Set-Cookie 形如 "name=value; Path=/; Domain=.bilibili.com"
            val head = raw.substringBefore(';')
            val index = head.indexOf('=')
            if (index <= 0) return@forEach
            set(head.substring(0, index).trim(), head.substring(index + 1).trim())
        }
    }

    /** 导出当前全部 Cookie 的快照，供持久化层落盘。 */
    fun snapshot(): Map<String, String> = store.toMap()

    /** 清空全部 Cookie（退出登录）。 */
    fun clear() = store.clear()

    fun restore(saved: Map<String, String>) {
        store.clear()
        saved.forEach { (name, value) -> set(name, value) }
    }

    companion object {
        const val KEY_SESSDATA = "SESSDATA"
        const val KEY_CSRF = "bili_jct"
        const val KEY_USER_ID = "DedeUserID"
        const val KEY_BUVID3 = "buvid3"
    }
}
