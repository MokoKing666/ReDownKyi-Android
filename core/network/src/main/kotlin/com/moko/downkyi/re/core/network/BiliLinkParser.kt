package com.moko.downkyi.re.core.network

/**
 * 用户粘贴内容的资源类型。
 */
enum class LinkKind(val label: String) {
    /** BV / av 稿件。 */
    VIDEO("视频"),

    /** 番剧单集（ep）。 */
    BANGUMI_EP("番剧剧集"),

    /** 番剧整季（ss）。 */
    BANGUMI_SEASON("番剧合集"),

    /** 课程（cheese / pugv）。 */
    CHEESE("课程"),

    /** 收藏夹。 */
    FAVORITES("收藏夹"),

    /** UP 主空间。 */
    SPACE("UP 主空间"),

    /** UP 主合集。 */
    SEASON("UP 主合集"),

    /** b23.tv 短链，需要跟随跳转才能确定类型。 */
    SHORT_LINK("短链"),

    UNKNOWN("未识别"),
}

/**
 * 识别结果。
 *
 * [id] 的形态按类型不同：
 *  - 视频：`BV1xx411c7mD` 或 `av123`
 *  - 番剧 / 课程：纯数字
 *  - 合集：`{mid}:{seasonId}`
 *  - 短链：短码（不是数字 id）
 */
data class LinkInfo(
    val kind: LinkKind,
    val id: String? = null,
    val raw: String = "",
    val page: Int? = null,
) {
    val isSupported: Boolean get() = kind != LinkKind.UNKNOWN

    /** 面向上层的可读描述，用于「已识别为…」这类提示。 */
    val describe: String
        get() = when (kind) {
            LinkKind.VIDEO -> "视频 $id"
            LinkKind.BANGUMI_EP -> "番剧剧集 ep$id"
            LinkKind.BANGUMI_SEASON -> "番剧合集 ss$id"
            LinkKind.CHEESE -> "课程 $id"
            LinkKind.FAVORITES -> "收藏夹 $id"
            LinkKind.SPACE -> "UP 主空间 $id"
            LinkKind.SEASON -> "UP 主合集 $id"
            LinkKind.SHORT_LINK -> "短链 $raw"
            LinkKind.UNKNOWN -> "未识别的链接"
        }
}

/**
 * 把用户粘贴的内容识别成 B 站资源类型。
 *
 * 判定顺序是**有讲究的，不能随意调整**：
 *
 *  1. 短链最先 —— `b23.tv/xxxx` 里可能含任何字符，若不先摘掉，
 *     后面的正则会从短码里误匹配出 `av` 之类的片段；
 *  2. BV 在 av 之前 —— 两种情况都可能同时出现；
 *  3. `av` 在课程链接里要跳过 —— `/cheese/av123` 是课程而不是稿件，
 *     这是实测过的误判；
 *  4. 收藏夹在纯数字之前 —— `fid=123` 要优先于「纯数字视为收藏夹」。
 *
 * 另外收藏夹、UP 主空间、合集这三类不带分 P 信息，因此不返回 [LinkInfo.page]。
 */
object BiliLinkParser {

    private val BV_REGEX = Regex("BV[0-9A-Za-z]{10}")
    private val AV_REGEX = Regex("av(\\d+)", RegexOption.IGNORE_CASE)
    private val EP_REGEX = Regex("ep(\\d+)", RegexOption.IGNORE_CASE)
    private val SS_REGEX = Regex("ss(\\d+)", RegexOption.IGNORE_CASE)
    private val FAV_REGEX = Regex("(?:fid|media_id|fav_id)=(\\d+)")
    private val SPACE_REGEX = Regex("space\\.bilibili\\.com/(\\d+)")
    private val SEASON_REGEX = Regex("/(\\d+)/lists/(\\d+)")
    private val SHORT_REGEX = Regex("(?:b23\\.tv|bili2233\\.cn)/([0-9A-Za-z]+)")
    private val PAGE_REGEX = Regex("[?&]p=(\\d+)")

    /** 纯数字（4 位以上）——视为收藏夹 media_id。 */
    private val BARE_NUMBER_REGEX = Regex("^\\d{4,}$")

    fun parse(input: String): LinkInfo {
        val text = input.trim()
        if (text.isEmpty()) return LinkInfo(LinkKind.UNKNOWN)

        val page = PAGE_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()

        // 短链最先处理：它需要跟随跳转才能确定真实类型
        SHORT_REGEX.find(text)?.let { match ->
            return LinkInfo(
                kind = LinkKind.SHORT_LINK,
                id = match.groupValues[1],
                raw = text,
                page = page,
            )
        }

        val lower = text.lowercase()
        val isCheese = text.contains("/cheese/") || text.contains("pugv")

        BV_REGEX.find(text)?.let { match ->
            return LinkInfo(LinkKind.VIDEO, match.value, text, page)
        }

        // av 号在课程链接里要跳过：/cheese/av123 是课程而非稿件
        AV_REGEX.find(text)?.let { match ->
            if (!lower.contains("/cheese/")) {
                return LinkInfo(LinkKind.VIDEO, "av${match.groupValues[1]}", text, page)
            }
        }

        EP_REGEX.find(text)?.let { match ->
            return LinkInfo(
                kind = if (isCheese) LinkKind.CHEESE else LinkKind.BANGUMI_EP,
                id = match.groupValues[1],
                raw = text,
                page = page,
            )
        }

        SS_REGEX.find(text)?.let { match ->
            return LinkInfo(
                kind = if (isCheese) LinkKind.CHEESE else LinkKind.BANGUMI_SEASON,
                id = match.groupValues[1],
                raw = text,
                page = page,
            )
        }

        FAV_REGEX.find(text)?.let { match ->
            return LinkInfo(LinkKind.FAVORITES, match.groupValues[1], text)
        }

        // 合集：/{mid}/lists/{seasonId}
        SEASON_REGEX.find(text)?.let { match ->
            return LinkInfo(
                kind = LinkKind.SEASON,
                id = "${match.groupValues[1]}:${match.groupValues[2]}",
                raw = text,
            )
        }

        SPACE_REGEX.find(text)?.let { match ->
            return LinkInfo(LinkKind.SPACE, match.groupValues[1], text)
        }

        if (BARE_NUMBER_REGEX.matches(text)) {
            return LinkInfo(LinkKind.FAVORITES, text, text)
        }

        return LinkInfo(LinkKind.UNKNOWN, raw = text, page = page)
    }
}
