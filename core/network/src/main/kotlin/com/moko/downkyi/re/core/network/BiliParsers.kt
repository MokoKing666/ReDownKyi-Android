package com.moko.downkyi.re.core.network

import com.moko.downkyi.re.core.model.BiliConstants
import com.moko.downkyi.re.core.model.DashInfo
import com.moko.downkyi.re.core.model.DashStream
import com.moko.downkyi.re.core.model.VideoDetail
import com.moko.downkyi.re.core.model.VideoPageInfo
import com.moko.downkyi.re.core.model.VideoQuality
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B 站接口响应的解析器。
 *
 * 全部手写而不是用代码生成，原因见 `BiliJson` 的说明：字段位置与类型都不稳定。
 * 这里额外处理两类跨接口的不一致：
 *
 *  1. **字段命名风格**：同一份数据在部分接口里是驼峰（`baseUrl`）、
 *     在另一部分是下划线（`base_url`）；
 *  2. **单位不一致**：时长在不同字段里可能是秒也可能是毫秒（见 [parseDash]）。
 */
object BiliParsers {

    /**
     * 补全 URL 协议。
     *
     * B 站返回的图片与流地址大量使用协议相对形式（`//i0.hdslb.com/...`），
     * 直接当作 URL 使用会解析失败；部分老接口还会给 `http://`，
     * 在 Android 9+ 的默认网络策略下会被明文流量限制拦掉。
     */
    fun normalizeUrl(url: String): String {
        if (url.isEmpty()) return url
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://" + url.removePrefix("http://")
            else -> url
        }
    }

    // -----------------------------------------------------------------------
    // DASH 播放信息
    // -----------------------------------------------------------------------

    /**
     * 解析 DASH 播放信息。
     *
     * ## 时长单位的坑
     *
     * 三个来源的单位**不一样**，必须统一成毫秒再用：
     *
     * | 字段 | 单位 |
     * |---|---|
     * | `dash.duration` | **秒** |
     * | `timelength` | 毫秒 |
     * | `durl[].length` | 毫秒 |
     *
     * 把 `dash.duration` 直接当毫秒会少乘 1000，所有基于时长的计算都小
     * 1000 倍 —— 最直观的表现是「清晰度选项上的预估体积把 MB 显示成了 KB」。
     *
     * 三段兜底顺序也重要：`dash` 是 DASH 请求才有的字段，老视频与部分番剧
     * 只返回 `durl` 直链，此时时长只能从 `timelength` 或 `durl.length` 取。
     */
    fun parseDash(root: JsonObject): DashInfo {
        val dash = root.obj("dash") ?: EMPTY_OBJECT

        val videos = dash.array("video").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { parseStream(it, isVideo = true) }
        }
        val audios = dash.array("audio").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { parseStream(it, isVideo = false) }
        }

        // 无损与杜比不在 audio 数组里，各自包在独立的子对象中
        val flac = dash.obj("flac")?.obj("audio")
            ?.takeIf { it.isNotEmpty() }
            ?.let { parseStream(it, isVideo = false) }
        val dolby = dash.obj("dolby")?.obj("audio")
            ?.takeIf { it.isNotEmpty() }
            ?.let { parseStream(it, isVideo = false) }

        val formats = buildMap {
            root.array("support_formats").orEmpty().forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val quality = item.int("quality")
                if (quality > 0) {
                    put(
                        quality,
                        item.stringOrNull("new_description")
                            ?: item.stringOrNull("display_desc")
                            ?: VideoQuality.fromQn(quality)?.label
                            ?: quality.toString(),
                    )
                }
            }
        }

        var durationMs = dash.long("duration") * 1000
        if (durationMs <= 0L) durationMs = root.long("timelength")
        if (durationMs <= 0L) {
            val durl = root.array("durl")
            if (!durl.isNullOrEmpty()) {
                durationMs = (durl.first() as? JsonObject)?.long("length") ?: 0L
            }
        }

        return DashInfo(
            durationMs = durationMs,
            videos = videos,
            audios = audios,
            flac = flac,
            dolby = dolby,
            supportFormats = formats,
        )
    }

    private fun parseStream(json: JsonObject, isVideo: Boolean): DashStream {
        val base = json.string("baseUrl").ifEmpty { json.string("base_url") }
        val backupArray: JsonArray? = json.array("backupUrl") ?: json.array("backup_url")
        val backups = backupArray.orEmpty().mapNotNull { element ->
            element.asStringOrNull()?.let { normalizeUrl(it) }
        }

        return DashStream(
            id = json.int("id"),
            url = normalizeUrl(base),
            backupUrls = backups,
            bandwidth = json.int("bandwidth"),
            mimeType = json.string("mimeType")
                .ifEmpty { json.string("mime_type") }
                .ifEmpty { if (isVideo) "video/mp4" else "audio/mp4" },
            codecs = json.string("codecs"),
            width = json.int("width"),
            height = json.int("height"),
            frameRate = json.string("frameRate").ifEmpty { json.string("frame_rate") },
            isVideo = isVideo,
        )
    }

    // -----------------------------------------------------------------------
    // 视频详情
    // -----------------------------------------------------------------------

    /**
     * 解析 `/x/web-interface/view` 返回的 `data`。
     *
     * 注意 `duration` 在这里是**秒**。
     */
    fun parseVideoDetail(data: JsonObject): VideoDetail {
        val owner = data.obj("owner") ?: EMPTY_OBJECT
        val stat = data.obj("stat") ?: EMPTY_OBJECT
        val durationSeconds = data.long("duration")
        val durationMs = durationSeconds * 1000

        val pages = data.array("pages").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { page ->
                VideoPageInfo(
                    cid = page.long("cid"),
                    page = page.int("page", 1),
                    part = page.string("part").ifEmpty { "P${page.int("page", 1)}" },
                    // 分 P 的 duration 同样是秒
                    durationMs = page.long("duration") * 1000,
                )
            }
        }

        return VideoDetail(
            bvid = data.string("bvid"),
            aid = data.long("aid"),
            cid = data.long("cid"),
            title = data.string("title").ifEmpty { "未命名视频" },
            cover = normalizeUrl(data.string("pic")),
            desc = data.string("desc"),
            tname = data.string("tname"),
            ownerName = owner.string("name").ifEmpty { "未知 UP 主" },
            ownerMid = owner.long("mid"),
            durationMs = durationMs,
            view = stat.int("view"),
            danmakuCount = stat.int("danmaku"),
            // pages 缺失时（部分接口不返回）构造一个单 P，避免上层到处判空
            pages = pages.ifEmpty {
                listOf(
                    VideoPageInfo(
                        cid = data.long("cid"),
                        page = 1,
                        part = data.string("title").ifEmpty { "P1" },
                        durationMs = durationMs,
                    ),
                )
            },
        )
    }

    /**
     * 解析番剧 / 课程单集。
     *
     * 与普通稿件的关键差异：标题由「季标题 · 集标题」拼成，
     * 且 `bvid` 在番剧条目里恒为空字符串，播放地址必须走 `epId`。
     */
    fun parseEpisode(
        season: JsonObject,
        episode: JsonObject,
        btype: String,
    ): VideoDetail {
        val seasonTitle = season.string("title").ifEmpty { "未命名" }
        val episodeTitle = episode.string("long_title").ifEmpty { episode.string("title") }
        val fullTitle = if (episodeTitle.isEmpty()) seasonTitle else "$seasonTitle · $episodeTitle"
        val durationMs = episode.long("duration") * 1000

        val ownerName = if (season["up_name"] == null) {
            if (btype == BiliConstants.TYPE_CHEESE) "课程" else "番剧"
        } else {
            season.string("up_name")
        }

        return VideoDetail(
            bvid = episode.string("bvid"),
            aid = episode.long("aid"),
            cid = episode.long("cid"),
            title = fullTitle,
            cover = normalizeUrl(
                episode.string("cover").ifEmpty { season.string("cover") }
            ),
            ownerName = ownerName,
            ownerMid = season.obj("up_info")?.long("mid") ?: 0L,
            durationMs = durationMs,
            btype = btype,
            epId = episode.long("id").takeIf { it > 0 },
            pages = listOf(
                VideoPageInfo(
                    cid = episode.long("cid"),
                    page = 1,
                    part = fullTitle,
                    durationMs = durationMs,
                ),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // 列表接口的字段容错
    // -----------------------------------------------------------------------

    /**
     * 从多个可能的位置取 cid。
     *
     * B 站各列表接口对 cid 的返回位置**不统一**：
     *  - 顶层 `cid`；
     *  - 收藏夹放在 `ugc.first_cid`；
     *  - 部分接口放在 `pages[0].cid`；
     *  - UP 主投稿与合集接口**完全不返回**。
     *
     * 取不到时返回 0 而不是抛异常：上层据此决定是否先补查详情接口。
     * 若拿 0 直接请求 playurl 会得到 -400「请求错误」，
     * 而错误信息完全指不到"cid 缺失"这个病根。
     */
    fun resolveCid(item: JsonObject): Long {
        item.longOrNull("cid")?.let { if (it > 0L) return it }
        item.obj("ugc")?.longOrNull("first_cid")?.let { if (it > 0L) return it }
        item.array("pages")?.firstOrNull()?.let { page ->
            (page as? JsonObject)?.longOrNull("cid")?.let { if (it > 0L) return it }
        }
        return 0L
    }

    /** 剥掉搜索结果标题里的高亮标签：`<em class="keyword">词</em>`。 */
    fun stripHighlight(html: String): String = html.replace(HIGHLIGHT_REGEX, "")

    /**
     * 把搜索结果的时长文本转成毫秒。
     *
     * 这个接口返回的是 `"4:18"` / `"1:02:03"` 这样的**文本**而不是数字。
     * 直接按数字解析会得到 0，进而让预估体积与实际进度全部失准，
     * 而界面上只会看到「0 B」或进度条不动。
     */
    fun parseDurationText(text: String): Long {
        val parts = text.trim().split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> 0L
        }
    }

    /**
     * 解析一个观看历史条目。
     *
     * 关键字段全部嵌在 `history` **子对象**里，顶层只有标题、封面、
     * 时长与作者名 —— 连 `aid` 都没有（要用 `history.oid`）。
     *
     * `business` 必须分流：`archive` 需要 bvid + cid，`pgc` 只有 epid，
     * `live` / `article` 不是可下载的视频。返回 null 表示这一条不可下载。
     */
    fun parseHistoryItem(item: JsonObject): com.moko.downkyi.re.core.model.MediaItem? {
        val nested = item.obj("history") ?: return null

        // duration 在**顶层**且是秒
        val durationMs = item.long("duration") * 1000
        val title = item.string("title").ifEmpty { "未命名" }
        val cover = normalizeUrl(item.string("cover"))
        val ownerName = item.string("author_name")

        return when (nested.string("business")) {
            "archive" -> {
                val bvid = nested.string("bvid")
                val cid = nested.long("cid")
                // 两者缺一就取不到播放地址，留着只会在下载时才失败
                if (bvid.isEmpty() || cid <= 0L) return null
                com.moko.downkyi.re.core.model.MediaItem(
                    bvid = bvid,
                    cid = cid,
                    aid = nested.long("oid"),
                    title = title,
                    cover = cover,
                    durationMs = durationMs,
                    ownerName = ownerName,
                )
            }

            "pgc" -> {
                val epId = nested.long("epid")
                if (epId <= 0L) return null
                com.moko.downkyi.re.core.model.MediaItem(
                    bvid = "",
                    cid = nested.long("cid"),
                    aid = nested.long("oid"),
                    title = title,
                    cover = cover,
                    durationMs = durationMs,
                    ownerName = ownerName,
                    btype = BiliConstants.TYPE_BANGUMI,
                    epId = epId,
                )
            }

            // live / article / article-list 都不是可下载的视频
            else -> null
        }
    }

    /** 解析一个稍后再看条目（字段是平铺的）。 */
    fun parseToViewItem(item: JsonObject): com.moko.downkyi.re.core.model.MediaItem? {
        val bvid = item.string("bvid")
        val cid = item.long("cid")
        if (bvid.isEmpty() || cid <= 0L) return null

        return com.moko.downkyi.re.core.model.MediaItem(
            bvid = bvid,
            cid = cid,
            aid = item.long("aid"),
            title = item.string("title").ifEmpty { "未命名" },
            cover = normalizeUrl(item.string("pic")),
            durationMs = item.long("duration") * 1000,
            ownerName = item.obj("owner")?.string("name") ?: "",
        )
    }

    /** 解析一个收藏夹条目。已失效的收藏项（attr 位 0）返回 null。 */
    fun parseFavoriteItem(item: JsonObject): com.moko.downkyi.re.core.model.MediaItem? {
        // attr 的最低位是「已失效」，这种条目没有有效 id
        if (item.int("attr") and 1 != 0) return null

        return com.moko.downkyi.re.core.model.MediaItem(
            bvid = item.string("bvid"),
            cid = resolveCid(item),
            aid = item.long("id"),
            title = item.string("title").ifEmpty { "未命名" },
            cover = normalizeUrl(item.string("cover")),
            durationMs = item.long("duration") * 1000,
            ownerName = item.obj("upper")?.string("name") ?: "",
        )
    }

    /**
     * 解析一个搜索结果条目。
     *
     * 标题带高亮标签、时长是文本 —— 两处都必须转换，
     * 否则标题里会出现 `<em class="keyword">` 这样的字面量。
     */
    fun parseSearchItem(item: JsonObject): com.moko.downkyi.re.core.model.MediaItem? {
        val bvid = item.string("bvid")
        if (bvid.isEmpty()) return null

        return com.moko.downkyi.re.core.model.MediaItem(
            bvid = bvid,
            cid = 0L, // 搜索结果不返回 cid
            aid = item.long("aid"),
            title = stripHighlight(item.string("title")).ifEmpty { "未命名" },
            cover = normalizeUrl(item.string("pic")),
            durationMs = parseDurationText(item.string("duration")),
            ownerName = item.string("author"),
        )
    }

    /** 解析 UP 主投稿条目（接口不返回 cid）。 */
    fun parseSpaceArchiveItem(item: JsonObject): com.moko.downkyi.re.core.model.MediaItem? {
        val bvid = item.string("bvid")
        if (bvid.isEmpty()) return null

        return com.moko.downkyi.re.core.model.MediaItem(
            bvid = bvid,
            cid = 0L, // 由下载流程在开始时补查详情
            aid = item.long("aid"),
            title = item.string("title").ifEmpty { "未命名" },
            cover = normalizeUrl(item.string("pic")),
            durationMs = item.long("length") * 1000,
            ownerName = item.string("author"),
        )
    }

    /** 解析合集条目（同样不返回 cid）。 */
    fun parseSeasonArchiveItem(item: JsonObject): com.moko.downkyi.re.core.model.MediaItem? {
        val bvid = item.string("bvid")
        if (bvid.isEmpty()) return null

        return com.moko.downkyi.re.core.model.MediaItem(
            bvid = bvid,
            cid = 0L,
            aid = item.long("aid"),
            title = item.string("title").ifEmpty { "未命名" },
            cover = normalizeUrl(item.string("pic")),
            durationMs = item.long("duration") * 1000,
            // 合集内所有条目同属一个 UP 主，调用方已知，条目上不必重复
            ownerName = "",
        )
    }

    private val HIGHLIGHT_REGEX = Regex("</?em[^>]*>")

    private val EMPTY_OBJECT = JsonObject(emptyMap())

    private fun JsonElement.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.takeIf { it.isNotEmpty() }
    }
}
