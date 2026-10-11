package com.moko.downkyi.re.data

import com.moko.downkyi.re.core.model.BiliConstants
import com.moko.downkyi.re.core.model.DashInfo
import com.moko.downkyi.re.core.model.VideoDetail
import com.moko.downkyi.re.core.model.VideoQuality
import com.moko.downkyi.re.core.network.ApiException
import com.moko.downkyi.re.core.network.BiliApi
import com.moko.downkyi.re.core.network.BiliLinkParser
import com.moko.downkyi.re.core.network.LinkInfo
import com.moko.downkyi.re.core.network.LinkKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 解析链路：把「用户粘贴的一段文本」变成「可以开始下载的东西」。
 *
 * 为什么在这里而不是在 [BiliApi] 里：接口层只回答"调哪个端点、参数怎么拼"，
 * 而下面这些判断都是**业务决策**，一条不对就会在整个应用里表现为"解析失败"，
 * 错误信息却指不到病根：
 *
 *  - 短链必须先跟随跳转再重新识别（`b23.tv/xxxx` 里没有任何类型信息）；
 *  - 番剧要先用 ep / ss 号换到具体的一集，之后才能拿 cid 与 bvid；
 *  - 播放地址**按 cid 请求**，多 P 视频换个分 P 就得重新取一次。
 */
class ParseRepository(private val api: BiliApi) {

    /**
     * 取视频详情（含分 P 列表）。
     *
     * 只支持单集：批量链接（收藏夹 / 合集 / UP 主空间）走的是另一条路径 ——
     * 它们先出列表、再由用户勾选，与"解析一集并选规格"不是同一屏。
     *
     * 自带 [Dispatchers.IO]：调用方是 ViewModel 的 `viewModelScope`（主线程），
     * 而 OkHttp 的桥接虽然把请求丢到了自己的线程，响应的 JSON 解析与
     * Cookie 处理都还在调用线程上 —— 不在这一层切线程，就等于在主线程上
     * 解析响应体，网络一慢就会掉帧。
     */
    suspend fun detail(input: String): VideoDetail = withContext(Dispatchers.IO) {
        val link = recognize(input)

        return@withContext when (link.kind) {
            LinkKind.VIDEO ->
                api.videoDetail(requireId(link))

            LinkKind.BANGUMI_EP ->
                api.episodeDetail(BiliConstants.TYPE_BANGUMI, epId = requireId(link).toLongOrNull())

            LinkKind.BANGUMI_SEASON ->
                api.episodeDetail(BiliConstants.TYPE_BANGUMI, seasonId = requireId(link).toLongOrNull())

            LinkKind.CHEESE -> cheeseDetail(link)

            LinkKind.FAVORITES, LinkKind.SPACE, LinkKind.SEASON -> throw ApiException(
                ApiException.CODE_LOCAL,
                "这是${link.kind.label}链接，批量勾选页还没接入；" +
                    "现在可以粘贴 BV 号、av 号，或番剧 / 课程的剧集链接",
            )

            LinkKind.SHORT_LINK, LinkKind.UNKNOWN -> throw ApiException(
                ApiException.CODE_LOCAL,
                "没能识别出链接。可以粘贴 BV 号、av 号，或视频 / 番剧 / 课程的完整链接",
            )
        }
    }

    /**
     * 取某个分 P 的 DASH 播放信息。
     *
     * [quality] 只是"期望档位"：未登录时服务端只会下发低档位，
     * 真正的可选集合以返回结果为准（见 `DashInfo.qualityList`）。
     */
    suspend fun dash(
        detail: VideoDetail,
        pageIndex: Int = 0,
        quality: VideoQuality = VideoQuality.Q1080P,
    ): DashInfo = withContext(Dispatchers.IO) {
        api.playUrl(
            btype = detail.btype,
            cid = detail.cidAt(pageIndex),
            bvid = detail.bvid,
            epId = detail.epId,
            quality = quality.qn,
        )
    }

    /** 是否已登录。未登录只影响能拿到的档位，不影响解析本身。 */
    val isLogin: Boolean get() = api.cookies.isLogin

    // -----------------------------------------------------------------------
    // 内部
    // -----------------------------------------------------------------------

    /**
     * 识别链接，短链先跟随跳转。
     *
     * 跳转失败（网络异常）时退回"短链"这个识别结果 —— 它会以"没能识别出链接"
     * 的面目报错。反过来把短码当 id 去请求，会得到一条 -400，用户完全看不懂。
     */
    private suspend fun recognize(input: String): LinkInfo {
        val link = BiliLinkParser.parse(input)
        if (link.kind != LinkKind.SHORT_LINK) return link

        val real = runCatching { api.resolveShortLink(link.raw) }.getOrNull() ?: return link
        val reparsed = BiliLinkParser.parse(real)
        return if (reparsed.isSupported) reparsed else link
    }

    /**
     * 课程（cheese）的 ep / ss 号在接口上走两个字段，而链接里两者的形态
     * 只差 `ep` / `ss` 两个字母 —— 猜错会直接得到 -404，
     * 因此从原始文本里读，而不是"先试 epId 再试 seasonId"打两次接口。
     */
    private suspend fun cheeseDetail(link: LinkInfo): VideoDetail {
        val id = requireId(link).toLongOrNull()
        return if (SS_REGEX.containsMatchIn(link.raw)) {
            api.episodeDetail(BiliConstants.TYPE_CHEESE, seasonId = id)
        } else {
            api.episodeDetail(BiliConstants.TYPE_CHEESE, epId = id)
        }
    }

    /** id 缺失说明识别出了类型却没解出编号，属于解析器的 bug，这里明确失败而不是拿空串去请求。 */
    private fun requireId(link: LinkInfo): String = link.id?.takeIf { it.isNotBlank() }
        ?: throw ApiException(ApiException.CODE_LOCAL, "链接里没有解析出编号：${link.raw}")

    private companion object {
        val SS_REGEX = Regex("ss\\d+", RegexOption.IGNORE_CASE)
    }
}
