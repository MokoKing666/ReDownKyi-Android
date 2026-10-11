package com.moko.downkyi.re.core.network

import com.moko.downkyi.re.core.model.BatchResult
import com.moko.downkyi.re.core.model.BiliConstants
import com.moko.downkyi.re.core.model.DashInfo
import com.moko.downkyi.re.core.model.MediaItem
import com.moko.downkyi.re.core.model.VideoDetail
import com.moko.downkyi.re.core.model.VideoQuality
import kotlinx.serialization.json.JsonObject
import java.util.zip.GZIPInputStream

/**
 * 请求参数的拼装。
 *
 * 单独抽出来是为了**可测**：这部分不含 IO，却最容易出错 ——
 * 少一个 `fnval` 位就会少一整档清晰度，`fourk` 写错会让 4K 永远不可用，
 * 而这些都不会报错，只会静默地返回更差的结果。
 */
object BiliQueries {

    /**
     * 播放地址的公共参数。
     *
     * - `fnval = 4048`：DASH 格式的全部能力位（16 DASH | 64 HDR | 128 4K |
     *   256 杜比音频 | 512 杜比视界 | 1024 8K | 2048 AV1）。少任何一位，
     *   对应的清晰度或编码就不会出现在返回结果里。
     * - `fnver = 0`、`fourk = 1`：缺了这两个，4K 及以上档位会被服务端忽略。
     */
    fun playUrlBase(cid: Long, quality: Int): Map<String, String> = buildMap {
        put("cid", cid.toString())
        put("qn", quality.toString())
        put("fnval", BiliConstants.FNVAL_DASH.toString())
        put("fnver", "0")
        put("fourk", "1")
    }

    /** 番剧与课程还要带上 `otype`。 */
    fun episodeExtra(bvid: String, epId: Long?): Map<String, String> = buildMap {
        epId?.let { put("ep_id", it.toString()) }
        if (bvid.isNotEmpty()) put("bvid", bvid)
        put("otype", "json")
    }

    /** 普通稿件只带 bvid（也可带 aid，这里统一用 bvid）。 */
    fun archiveExtra(bvid: String): Map<String, String> = buildMap {
        if (bvid.isNotEmpty()) put("bvid", bvid)
    }

    /** 按业务类型选择播放地址接口。 */
    fun playUrlPath(btype: String): String = when (btype) {
        BiliConstants.TYPE_BANGUMI -> "/pgc/player/web/playurl"
        BiliConstants.TYPE_CHEESE -> "/pugv/player/web/playurl"
        else -> "/x/player/wbi/playurl"
    }

    /** 只有普通稿件走 WBI 签名；番剧与课程接口不签名。 */
    fun needsWbi(btype: String): Boolean = btype == BiliConstants.TYPE_VIDEO

    /** 视频详情的查询参数：BV 号用 `bvid`，av 号用 `aid`（且不带 `av` 前缀）。 */
    fun videoDetailQuery(id: String): Pair<String, String> =
        if (id.startsWith("av", ignoreCase = true)) "aid" to id.drop(2) else "bvid" to id

    /** 番剧 / 课程季的查询参数。 */
    fun seasonQuery(seasonId: Long?, epId: Long?): Map<String, String> = buildMap {
        seasonId?.let { put("season_id", it.toString()) }
        epId?.let { put("ep_id", it.toString()) }
    }
}

/**
 * B 站接口。
 *
 * 每个方法都是「拼参数 → 调 [BiliHttp] → 交给 [BiliParsers]」的薄封装，
 * 不含业务判断 —— 选流、回退、归档这些决策属于上层。
 *
 * ## 为什么播放地址分三个接口
 *
 * B 站把普通稿件、番剧、课程放在三个不同的端点上，参数也有细微差别
 * （番剧/课程要 `otype`，普通稿件要 WBI 签名）。把它们合并成一个接口调用
 * 是行不通的：用错端点会返回 `-404`，而错误信息完全指不到"端点选错了"。
 */
class BiliApi(private val http: BiliHttp) {

    val cookies: BiliCookies get() = http.cookies

    private val wbi: WbiSigner get() = http.wbi

    // -----------------------------------------------------------------------
    // 会话准备
    // -----------------------------------------------------------------------

    /**
     * 确保 WBI 密钥可用。
     *
     * 密钥来自 `/x/web-interface/nav` 的 `wbi_img`。**nav 在未登录时
     * code=-101「账号未登录」，但 `data.wbi_img` 照常返回** —— 密钥本来就
     * 对游客开放。所以这里必须走不校验业务码的 [BiliHttp.getJsonUnchecked]：
     * 走严格校验的话，未登录用户的签名永远拿不到，所有 wbi 接口的表现都是
     * 「需要登录后才能继续」，而真正需要的密钥明明就在同一个响应体里。
     */
    suspend fun ensureWbiKeys(force: Boolean = false) {
        if (!force && wbi.ready) return
        val root = http.getJsonUnchecked("${BiliConstants.API_BASE}/x/web-interface/nav") ?: return
        val wbiImg = root.obj("data")?.obj("wbi_img") ?: return
        wbi.updateKeys(
            wbiImg.stringOrNull("img_url"),
            wbiImg.stringOrNull("sub_url"),
        )
    }

    /**
     * 确保拿到 buvid3 指纹。
     *
     * 搜索接口在缺少 buvid3 时会返回 -412（风控），而它**不需要登录**
     * 就能获得 —— 拿不到的话用户会看到"搜索被拦截，请先登录"，
     * 但实际原因与登录无关。
     */
    suspend fun ensureBuvid() {
        if (!cookies[BiliCookies.KEY_BUVID3].isNullOrEmpty()) return
        val data = http.getData("/x/frontend/finger/spi") as? JsonObject ?: return
        data.stringOrNull("b_3")?.let { cookies.set(BiliCookies.KEY_BUVID3, it) }
    }

    // -----------------------------------------------------------------------
    // 视频详情
    // -----------------------------------------------------------------------

    /** 普通稿件详情。 */
    suspend fun videoDetail(id: String): VideoDetail {
        val (key, value) = BiliQueries.videoDetailQuery(id)
        val data = http.getData("/x/web-interface/view", mapOf(key to value)) as? JsonObject
            ?: throw ApiException(ApiException.CODE_MALFORMED, "视频详情返回为空")
        return BiliParsers.parseVideoDetail(data)
    }

    /**
     * 番剧 / 课程单集详情。
     *
     * 返回季对象与剧集对象两部分：标题需要「季 + 集」拼起来，
     * 而播放地址需要 `epId`（番剧条目里没有 bvid）。
     */
    suspend fun episodeDetail(
        btype: String,
        seasonId: Long? = null,
        epId: Long? = null,
    ): VideoDetail {
        val path = if (btype == BiliConstants.TYPE_CHEESE) {
            "/pugv/view/web/season"
        } else {
            "/pgc/view/web/season"
        }
        val root = http.getJson(path, BiliQueries.seasonQuery(seasonId, epId))
        val data = root.obj("result") ?: root.obj("data")
            ?: throw ApiException(ApiException.CODE_MALFORMED, "番剧信息返回为空")

        val episodes = data.array("episodes")
            ?: throw ApiException(ApiException.CODE_MALFORMED, "番剧剧集列表为空")

        // 指定了 epId 就找那一集，否则取第一集 —— 避免"请求了整季却不知道返回哪一集"
        val target = if (epId != null) {
            episodes.firstOrNull { (it as? JsonObject)?.long("id") == epId }
                ?: episodes.firstOrNull()
        } else {
            episodes.firstOrNull()
        }
        val episode = target as? JsonObject
            ?: throw ApiException(ApiException.CODE_MALFORMED, "未找到指定的剧集")

        return BiliParsers.parseEpisode(data, episode, btype)
    }

    // -----------------------------------------------------------------------
    // 播放地址
    // -----------------------------------------------------------------------

    /**
     * 获取 DASH 播放信息。
     *
     * [quality] 只是「期望」档位：服务端会按账号权限与视频实际情况返回
     * 它支持的集合，真正的选择由 `DashInfo.pickVideo` 完成。
     */
    suspend fun playUrl(
        btype: String,
        cid: Long,
        bvid: String = "",
        epId: Long? = null,
        quality: Int = VideoQuality.Q1080P.qn,
    ): DashInfo {
        val base = BiliQueries.playUrlBase(cid, quality)
        val path = BiliQueries.playUrlPath(btype)
        val useWbi = BiliQueries.needsWbi(btype)

        if (useWbi) {
            // 指纹要先于签名与请求：未登录且没有 buvid3 时，wbi 接口会以
            // -101「账号未登录」拒绝 —— 表现是「解析出了视频，清晰度一栏
            // 却写着需要登录」，用户完全想不到是缺了一个游客 Cookie。
            ensureBuvid()
            ensureWbiKeys()
        }

        val query = when (btype) {
            BiliConstants.TYPE_BANGUMI, BiliConstants.TYPE_CHEESE ->
                base + BiliQueries.episodeExtra(bvid, epId)

            else -> base + BiliQueries.archiveExtra(bvid)
        }

        val root = http.getJson(path, query, sign = useWbi)

        // 番剧接口把数据放在 data 里，部分接口直接平铺，这里都兼容
        val data = root.obj("data") ?: root
        return BiliParsers.parseDash(data)
    }

    /**
     * 取某个 cid 的弹幕 XML 原文。
     *
     * 端点是老接口 `/x/v1/dm/list.so`，它有个历史包袱：**无论客户端是否声明
     * 支持 gzip，都返回 gzip 压缩后的内容**。而 OkHttp 只在「自己添加
     * Accept-Encoding」时才做透明解压 —— 这里取原始字节并按魔数
     * （1f 8b）手动解压，两种服务端行为下都能拿到可解析的文本。
     *
     * 该接口不需要 WBI 签名，Cookie 也不是必须的。
     */
    suspend fun danmakuXml(cid: Long): String {
        val bytes = http.getBytes(
            uri = "${BiliConstants.API_BASE}/x/v1/dm/list.so?oid=$cid",
            referer = "${BiliConstants.WEB_BASE}/",
        )
        val gzipped = bytes.size > 2 &&
            bytes[0] == 0x1f.toByte() &&
            bytes[1] == 0x8b.toByte()
        return if (gzipped) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
        } else {
            bytes.toString(Charsets.UTF_8)
        }
    }

    /**
     * 取 durl 直链（单个完整 mp4）。
     *
     * 部分老视频没有 DASH，只有直链。返回 null 表示该视频必须走 DASH 路径 ——
     * 这不是错误，调用方据此决定是否回退。
     */
    suspend fun plainUrl(
        btype: String,
        cid: Long,
        bvid: String = "",
        epId: Long? = null,
    ): String? {
        val query = buildMap {
            put("cid", cid.toString())
            put("qn", VideoQuality.Q1080P.qn.toString())
            // 只要 durl，不要 dash
            put("fnval", "1")
            put("fnver", "0")
            put("fourk", "1")
        }.toMutableMap()

        val path = when (btype) {
            BiliConstants.TYPE_BANGUMI -> "/pgc/player/web/playurl"
            BiliConstants.TYPE_CHEESE -> "/pugv/player/web/playurl"
            else -> "/x/player/playurl"
        }

        when (btype) {
            BiliConstants.TYPE_BANGUMI, BiliConstants.TYPE_CHEESE ->
                query.putAll(BiliQueries.episodeExtra(bvid, epId))

            else -> query.putAll(BiliQueries.archiveExtra(bvid))
        }

        val root = http.getJson(path, query)
        val data = root.obj("data") ?: root
        val durl = data.array("durl") ?: return null
        val first = durl.firstOrNull() as? JsonObject ?: return null
        val url = first.stringOrNull("url") ?: return null
        return BiliParsers.normalizeUrl(url)
    }

    /**
     * 解析 b23.tv / bili2233.cn 短链，得到真实地址。
     *
     * 必须跟随重定向取最终 URL：短链响应是一整页 HTML，
     * 把它当地址用会让后续的链接识别直接失败。
     */
    suspend fun resolveShortLink(shortUrl: String): String = http.resolveUrl(shortUrl)

    private companion object {
        /** 登录相关接口的域名（与 api.bilibili.com 分开）。 */
        const val PASSPORT_BASE = "https://passport.bilibili.com"
    }

    /**
     * 生成扫码登录二维码。
     *
     * 返回 (qrcode_key, 扫码 url)。url 编码成二维码后用 B 站 App 扫码即可登录。
     */
    suspend fun qrcodeGenerate(): Pair<String, String>? {
        // ⚠️ passport 接口在 passport.bilibili.com，不在 api.bilibili.com ——
        // 走 API_BASE 会拿到 404（接口存在，只是不在这个域）
        val root = http.getJsonUnchecked("$PASSPORT_BASE/x/passport-login/web/qrcode/generate")
            ?: return null
        val data = root.obj("data") ?: return null
        val key = data.stringOrNull("qrcode_key") ?: return null
        val url = data.stringOrNull("url") ?: return null
        return key to url
    }

    /**
     * 轮询扫码状态。
     *
     * 返回 (code, loginUrl)：
     *  - 0 = 登录成功，loginUrl 非空，需 GET 它让服务端下发 SESSDATA 等 Cookie；
     *  - 86038 = 二维码过期；86090 = 未扫码；86101 = 已扫码未确认。
     */
    suspend fun qrcodePoll(qrcodeKey: String): Pair<Int, String?> {
        val root = http.getJsonUnchecked(
            "$PASSPORT_BASE/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey&source=main_web",
        ) ?: return (-1 to null)
        val data = root.obj("data") ?: return (-1 to null)
        val code = data.longOrNull("code")?.toInt() ?: -1
        val url = data.stringOrNull("url")
        return code to url
    }

    /**
     * 登录成功后访问 [loginUrl]：服务端在响应里下发 SESSDATA 等 Cookie，
     * 被 [BiliCookies.absorb] 捕获。跟随重定向是必要的（跨域写 Cookie）。
     */
    suspend fun absorbLoginCookies(loginUrl: String) {
        http.absorbResponse(loginUrl, referer = BiliConstants.WEB_BASE)
    }

    /**
     * 当前登录用户的昵称；未登录返回 null。
     *
     * nav 走不校验业务码的路径读：未登录时它返回 code=-101，
     * 而 `data.uname` 的缺失本身就是"未登录"的判据。
     */
    suspend fun navUserName(): String? {
        val root = http.getJsonUnchecked("${BiliConstants.API_BASE}/x/web-interface/nav") ?: return null
        val name = root.obj("data")?.stringOrNull("uname").orEmpty()
        return name.ifBlank { null }
    }

    /**
     * 当前登录用户创建的第一个收藏夹 ID（「我的收藏夹」入口用）。
     *
     * 收藏夹内容接口需要 `media_id`，而界面上用户只知道"我的收藏夹"这个名字 ——
     * 取创建列表的第一个即可。未登录（无 DedeUserID）返回 0，由调用方提示登录。
     */
    suspend fun defaultFavoriteFolder(): Long {
        val mid = cookies[BiliCookies.KEY_USER_ID]?.toLongOrNull() ?: return 0L
        val data = http.getData(
            "/x/v3/fav/folder/created-list-all",
            mapOf("up_mid" to mid.toString()),
        ) as? JsonObject ?: return 0L
        val first = data.array("list").orEmpty().firstOrNull() as? JsonObject ?: return 0L
        return first.long("id")
    }

    // -----------------------------------------------------------------------
    // 列表类接口
    // -----------------------------------------------------------------------

    /**
     * 观看历史。
     *
     * ## 这个接口的字段结构和其他接口都不一样
     *
     * 关键字段全部嵌在 `list[i].history` **子对象**里，顶层只有标题、封面、
     * 时长与作者名 —— 连 `aid` 都没有（`history.oid` 才是）。
     *
     * 而且 `business` 必须分流：
     *  - `archive` → 普通稿件，需要 `bvid` + `cid` 才能取播放地址；
     *  - `pgc` → 番剧，**没有 bvid**，只能靠 `epid`；
     *  - `live` / `article` → 不是可下载的视频，直接跳过。
     *
     * 早期实现读的是顶层 `bvid`（恒为空串），于是每条记录都在
     * 「bvid 为空则跳过」处被过滤掉，最终返回空列表 —— 表现为
     * 「观看历史一直抓不到内容」，而接口本身返回 200。
     */
    suspend fun history(pageSize: Int = 30): List<MediaItem> {
        val data = http.getData(
            "/x/web-interface/history/cursor",
            // ps 上限是 30，超了会被服务端拒绝
            mapOf("ps" to pageSize.coerceIn(1, 30).toString()),
            sign = true,
        ) as? JsonObject ?: return emptyList()

        val items = data.array("list").orEmpty()
        return items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val nested = item.obj("history") ?: return@mapNotNull null

            when (val business = nested.string("business")) {
                "archive" -> {
                    val bvid = nested.string("bvid")
                    val cid = nested.long("cid")
                    // 两者缺一就取不到播放地址，留着只会在下载时失败
                    if (bvid.isEmpty() || cid <= 0L) return@mapNotNull null
                    MediaItem(
                        bvid = bvid,
                        cid = cid,
                        aid = nested.long("oid"),
                        title = item.string("title").ifEmpty { "未命名" },
                        cover = BiliParsers.normalizeUrl(item.string("cover")),
                        // 注意：duration 在**顶层**且是秒
                        durationMs = item.long("duration") * 1000,
                        ownerName = item.string("author_name"),
                    )
                }

                "pgc" -> {
                    val epId = nested.long("epid")
                    if (epId <= 0L) return@mapNotNull null
                    MediaItem(
                        bvid = "",
                        cid = nested.long("cid"),
                        aid = nested.long("oid"),
                        title = item.string("title").ifEmpty { "未命名" },
                        cover = BiliParsers.normalizeUrl(item.string("cover")),
                        durationMs = item.long("duration") * 1000,
                        ownerName = item.string("author_name"),
                        btype = BiliConstants.TYPE_BANGUMI,
                        epId = epId,
                    )
                }

                else -> null // live / article 不是可下载的视频
            }
        }
    }

    /**
     * 稍后再看。
     *
     * 与历史不同，这个接口的字段是**平铺**的，且同样需要签名。
     */
    suspend fun toView(): List<MediaItem> {
        val data = http.getData("/x/v2/history/toview", sign = true) as? JsonObject
            ?: return emptyList()

        return data.array("list").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val bvid = item.string("bvid")
            val cid = item.long("cid")
            if (bvid.isEmpty() || cid <= 0L) return@mapNotNull null

            MediaItem(
                bvid = bvid,
                cid = cid,
                aid = item.long("aid"),
                title = item.string("title").ifEmpty { "未命名" },
                cover = BiliParsers.normalizeUrl(item.string("pic")),
                durationMs = item.long("duration") * 1000,
                ownerName = item.obj("owner")?.string("name") ?: "",
            )
        }
    }

    /**
     * 收藏夹内容。
     *
     * [page] 从 1 开始。返回的 [BatchResult.hasMore] 用于判断是否还有下一页。
     */
    suspend fun favorites(
        mediaId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): BatchResult {
        val root = http.getJson(
            "/x/v3/fav/resource/list",
            mapOf(
                "media_id" to mediaId.toString(),
                "pn" to page.toString(),
                "ps" to pageSize.coerceIn(1, 20).toString(),
                "platform" to "web",
            ),
        )
        val data = root.obj("data") ?: throw ApiException(
            ApiException.CODE_MALFORMED, "收藏夹返回为空",
        )

        val items = data.array("medias").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            // 已失效的收藏项没有有效 id，留着只会在下载时失败
            if (item.int("attr") and 1 != 0) return@mapNotNull null

            MediaItem(
                bvid = item.string("bvid"),
                cid = resolveCid(item),
                aid = item.long("id"),
                title = item.string("title").ifEmpty { "未命名" },
                cover = BiliParsers.normalizeUrl(item.string("cover")),
                durationMs = item.long("duration") * 1000,
                ownerName = item.obj("upper")?.string("name") ?: "",
                btype = BiliConstants.TYPE_VIDEO,
            )
        }

        return BatchResult(
            title = data.string("info").ifEmpty { "收藏夹" },
            items = items,
            mediaId = mediaId,
            hasMore = data.bool("has_more"),
            page = page,
        )
    }

    /**
     * UP 主投稿。
     */
    suspend fun spaceArchives(
        mid: Long,
        page: Int = 1,
        pageSize: Int = 30,
    ): BatchResult {
        ensureWbiKeys()
        val root = http.getJson(
            "/x/space/wbi/arc/search",
            mapOf(
                "mid" to mid.toString(),
                "pn" to page.toString(),
                "ps" to pageSize.coerceIn(1, 30).toString(),
            ),
            sign = true,
        )
        val data = root.obj("data") ?: throw ApiException(
            ApiException.CODE_MALFORMED, "UP 主投稿返回为空",
        )
        val list = data.obj("list") ?: throw ApiException(
            ApiException.CODE_MALFORMED, "UP 主投稿列表为空",
        )

        val items = list.array("vlist").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            MediaItem(
                bvid = item.string("bvid"),
                // UP 主投稿接口**不返回 cid**，由下载流程在开始时补查详情
                cid = 0L,
                aid = item.long("aid"),
                title = item.string("title").ifEmpty { "未命名" },
                cover = BiliParsers.normalizeUrl(item.string("pic")),
                durationMs = item.long("length") * 1000,
                ownerName = item.string("author"),
            )
        }

        val pageInfo = data.obj("page")
        val count = pageInfo?.long("count") ?: 0L
        val size = pageInfo?.long("ps") ?: pageSize.toLong()

        return BatchResult(
            title = "UP 主投稿",
            items = items,
            mid = mid,
            hasMore = page * size < count,
            page = page,
        )
    }

    /**
     * 合集内容。
     *
     * [newestFirst] 为 true 时把结果倒序 —— 合集接口默认按发布时间**升序**
     * （第 1 页是最旧的），订阅追更必须看到最新的那批。
     */
    suspend fun seasonArchives(
        mid: Long,
        seasonId: Long,
        page: Int = 1,
        pageSize: Int = 30,
        newestFirst: Boolean = false,
    ): BatchResult {
        val root = http.getJson(
            "/x/polymer/web-space/seasons_archives_list",
            mapOf(
                "mid" to mid.toString(),
                "season_id" to seasonId.toString(),
                "page_num" to page.toString(),
                "page_size" to pageSize.coerceIn(1, 30).toString(),
            ),
        )
        val data = root.obj("data") ?: throw ApiException(
            ApiException.CODE_MALFORMED, "合集返回为空",
        )

        val items = data.array("archives").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            MediaItem(
                bvid = item.string("bvid"),
                cid = 0L, // 合集接口同样不返回 cid
                aid = item.long("aid"),
                title = item.string("title").ifEmpty { "未命名" },
                cover = BiliParsers.normalizeUrl(item.string("pic")),
                durationMs = item.long("duration") * 1000,
                // 合集内所有条目同属一个 UP 主，调用方已知，条目上不必重复
                ownerName = "",
            )
        }

        val pageInfo = data.obj("page")
        val total = pageInfo?.long("total") ?: 0L

        return BatchResult(
            title = "合集",
            items = if (newestFirst) items.reversed() else items,
            mid = mid,
            seasonId = seasonId,
            hasMore = page * pageSize < total,
            page = page,
        )
    }

    /**
     * 站内搜索。
     *
     * 三处与常规接口不同，缺一不可：
     *  1. 必须用 `/x/web-interface/wbi/search/type`（旧路径已废弃）；
     *  2. 必须带 `buvid3`，否则返回 -412 风控；
     *  3. **没有 `page_size` 参数**，每页固定 20 条，翻页只能靠 `page`。
     */
    suspend fun searchVideo(
        keyword: String,
        page: Int = 1,
    ): List<MediaItem> {
        ensureWbiKeys()
        ensureBuvid()

        val root = http.getJson(
            "/x/web-interface/wbi/search/type",
            mapOf(
                "keyword" to keyword,
                "search_type" to "video",
                "page" to page.toString(),
            ),
            sign = true,
        )
        val data = root.obj("data") ?: return emptyList()

        return data.array("result").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val bvid = item.string("bvid")
            if (bvid.isEmpty()) return@mapNotNull null

            MediaItem(
                bvid = bvid,
                cid = 0L, // 搜索结果不返回 cid
                aid = item.long("aid"),
                title = stripHighlight(item.string("title")),
                cover = BiliParsers.normalizeUrl(item.string("pic")),
                durationMs = parseDurationText(item.string("duration")),
                ownerName = item.string("author"),
            )
        }
    }

    // -----------------------------------------------------------------------
    // 解析辅助
    // -----------------------------------------------------------------------

    /**
     * 从多个可能的位置取 cid。
     *
     * B 站各列表接口对 cid 的返回位置不统一：
     *  - 顶层 `cid`；
     *  - 收藏夹放在 `ugc.first_cid`；
     *  - 部分接口放在 `pages[0].cid`；
     *  - UP 主投稿与合集接口**完全不返回**。
     *
     * 取不到时返回 0 而不是抛异常 —— 上层据此决定是否先补查详情接口。
     * 若返回 0 就直接请求 playurl，会得到 -400「请求错误」，
     * 而错误信息完全指不到"cid 缺失"这个病根。
     */
    // 字段容错的真正实现放在 BiliParsers：那里没有 IO，可以单测。
    // 这里只保留同名委托，免得调用点到处写全限定名。

    private fun resolveCid(item: JsonObject): Long = BiliParsers.resolveCid(item)

    private fun stripHighlight(html: String): String = BiliParsers.stripHighlight(html)

    private fun parseDurationText(text: String): Long = BiliParsers.parseDurationText(text)
}
