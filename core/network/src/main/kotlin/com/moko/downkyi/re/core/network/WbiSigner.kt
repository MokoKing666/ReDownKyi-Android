package com.moko.downkyi.re.core.network

import java.security.MessageDigest

/**
 * B 站 WBI 签名。
 *
 * 算法来自官方前端：
 *  1. 把 `wbi_img` 的 img_key 与 sub_key 拼接，按固定表重排后取前 32 位作为 mixinKey；
 *  2. 请求参数加入 `wts`（秒级时间戳），按 key 升序排序；
 *  3. 每个值剔除 `!'()*` 五个字符（与官方前端一致）；
 *  4. 拼成 query 后用 UTF-8 百分号编码；
 *  5. `w_rid = md5(query + mixinKey)`。
 *
 * 那张 64 项重排表只要错一个数字，所有 `wbi` 前缀的接口（搜索、合集、
 * 投稿、番剧季、历史、收藏夹）都会静默返回 -403，表现为「功能突然全挂」，
 * 而且极难定位。因此实现与测试都锚定在**外部来源**的官方测试向量上，
 * 而不是拿本实现的输出自证。
 */
class WbiSigner {

    private var imgKey: String? = null
    private var subKey: String? = null

    /**
     * 密钥是否已就绪。
     *
     * 未就绪时 [sign] 只补 `wts`，不产出 `w_rid` —— 这条降级通路是刻意的：
     * 列表类接口未签名通常仍可用，而因为签名环节失败就让整个列表加载不了，
     * 是更糟的结果。
     */
    val ready: Boolean get() = mixinKey != null

    /** 用 `/x/web-interface/nav` 返回的 `wbi_img` 刷新密钥。 */
    fun updateKeys(imgUrl: String?, subUrl: String?) {
        imgKey = fileName(imgUrl)
        subKey = fileName(subUrl)
    }

    fun clear() {
        imgKey = null
        subKey = null
    }

    /**
     * 重排后的 mixinKey，仅供测试断言。
     *
     * 暴露它是为了让测试能直接锁定「重排表算对了」这件事 ——
     * 否则一旦重排表被改动，只能从下游 w_rid 不匹配间接推断，
     * 失败信息指向的是 md5 而不是真正出错的表。
     */
    internal val mixinKeyForTest: String? get() = mixinKey

    /**
     * 返回带 `wts` / `w_rid` 的完整参数。
     *
     * **不修改调用方传入的 Map**：入参常被复用于日志、重试或拼装其它请求，
     * 就地修改会让调用方拿到意料之外的内容。
     *
     * [wts] 仅用于测试注入固定时间戳；正式调用传 null 取当前秒级时间。
     */
    fun sign(params: Map<String, String>, wts: Long? = null): Map<String, String> {
        val signed = LinkedHashMap<String, String>(params.size + 2)
        signed.putAll(params)
        signed["wts"] = (wts ?: (System.currentTimeMillis() / 1000)).toString()

        val key = mixinKey ?: return signed

        val query = signed.keys
            .sorted()
            .joinToString("&") { name ->
                val value = signed.getValue(name).replace(ILLEGAL_CHARS, "")
                "${percentEncode(name)}=${percentEncode(value)}"
            }
        signed["w_rid"] = md5Hex(query + key)
        return signed
    }

    private val mixinKey: String?
        get() {
            val img = imgKey ?: return null
            val sub = subKey ?: return null
            val raw = img + sub
            // 短于 64 说明密钥没取对（例如把整段 URL 当成了密钥）
            if (raw.length < 64) return null
            val reordered = buildString(MIXIN_KEY_ENC_TAB.size) {
                MIXIN_KEY_ENC_TAB.forEach { index -> append(raw[index]) }
            }
            return reordered.take(32)
        }

    companion object {

        /** 官方前端的 mixinKey 重排表（64 项）。 */
        private val MIXIN_KEY_ENC_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
        )

        private val ILLEGAL_CHARS = Regex("[!'()*]")
        private const val HEX = "0123456789ABCDEF"

        private fun fileName(url: String?): String? {
            if (url.isNullOrEmpty()) return null
            val name = url.substringAfterLast('/')
            val dot = name.lastIndexOf('.')
            // 去掉扩展名这一步很关键：官方给的 img_url 以 .png 结尾，
            // 把扩展名一起当密钥会让签名全部错误
            return if (dot > 0) name.substring(0, dot) else name
        }

        /**
         * UTF-8 百分号编码。
         *
         * 刻意不用 [java.net.URLEncoder]：它把空格编成 `+`，且对 `!'()*`
         * 的处理与官方前端不一致，会让 `w_rid` 对不上。
         * 这里保留的字符集与官方前端使用的 `encodeURIComponent` 语义一致。
         */
        private fun percentEncode(value: String): String {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val sb = StringBuilder(bytes.size)
            for (b in bytes) {
                val c = (b.toInt() and 0xFF).toChar()
                val safe = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
                    c == '-' || c == '_' || c == '.' || c == '~' ||
                    c == '!' || c == '\'' || c == '(' || c == ')' || c == '*'
                if (safe) {
                    sb.append(c)
                } else {
                    sb.append('%')
                    sb.append(HEX[(b.toInt() shr 4) and 0xF])
                    sb.append(HEX[b.toInt() and 0xF])
                }
            }
            return sb.toString()
        }

        internal fun md5Hex(text: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                sb.append(HEX[(b.toInt() shr 4) and 0xF])
                sb.append(HEX[b.toInt() and 0xF])
            }
            return sb.toString()
        }
    }
}
