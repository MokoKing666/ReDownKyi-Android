package com.moko.downkyi.re.core.model

/**
 * 应用自身的元信息。
 */
object AppInfo {
    const val NAME = "Re：哔哩下载姬"
    const val ENGLISH_NAME = "DownKyi"
    const val DISCLAIMER =
        "本应用仅提供视频解析与本地下载能力，不提供任何内容存储服务。" +
            "所有内容版权归原作者所有，仅供个人学习交流，请勿用于商业用途，并支持原始发布者。"
}

/**
 * B 站接口层常量。
 *
 * 这些值直接来自对 B 站客户端接口的逆向与实测：UA 必须像桌面浏览器、
 * DASH 请求必须带足 fnval 位、Referer 必须落在 .bilibili.com，
 * 否则会出现「接口返回成功但没有可用清晰度」这类静默降级。
 */
object BiliConstants {

    const val API_BASE = "https://api.bilibili.com"
    const val PASSPORT_BASE = "https://passport.bilibili.com"
    const val WEB_BASE = "https://www.bilibili.com"

    /**
     * 接口要求的 User-Agent。
     *
     * 用桌面 Chrome 而不是 App 自己的 UA：B 站对未知 UA 会下调返回
     * 的清晰度上限，甚至对部分接口直接返回 -412 风控。
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /**
     * DASH 格式请求位掩码。
     *
     * 16(DASH) | 64(HDR) | 128(4K) | 256(杜比音频) | 512(杜比视界) | 1024(8K) | 2048(AV1)
     * 少任何一位，对应的清晰度或编码就不会出现在返回结果里。
     */
    const val FNVAL_DASH = 4048

    /** 单文件分片并发的上限。超过这个值收益很小，反而更容易触发风控。 */
    const val MAX_SEGMENT_CONCURRENCY = 8

    // --- 业务类型标识 ---
    const val TYPE_VIDEO = "video"
    const val TYPE_BANGUMI = "bangumi"
    const val TYPE_CHEESE = "cheese"
}

/**
 * 清晰度档位。
 *
 * [qn] 是 B 站接口使用的数字编号，必须与官方一致；
 * 顺序按「由高到低」排列，很多选档逻辑依赖这个顺序。
 */
enum class VideoQuality(val qn: Int, val label: String) {
    Q8K(127, "8K 超清"),
    DOLBY_VISION(126, "杜比视界"),
    HDR(125, "HDR 真彩"),
    Q4K(120, "4K 超清"),
    Q1080P60(116, "1080P60"),
    Q1080P_PLUS(112, "1080P+"),
    Q1080P(80, "1080P 高清"),
    Q720P60(74, "720P60"),
    Q720P(64, "720P 高清"),
    Q480P(32, "480P 清晰"),
    Q360P(16, "360P 流畅"),
    Q240P(6, "240P 最流畅"),
    ;

    companion object {
        /** 按 qn 查找；未知编号返回 null（不猜测，也不静默降级）。 */
        fun fromQn(qn: Int): VideoQuality? = entries.firstOrNull { it.qn == qn }

        /** 按 qn 查找并带兜底，用于反序列化历史数据。 */
        fun fromQnOrNull(qn: Int?): VideoQuality? = qn?.let { fromQn(it) }

        /**
         * 缓存的清晰度优先级：越靠前越高。
         * 与接口返回顺序无关，用于「画质优先」选档。
         */
        val PRIORITY: List<VideoQuality> = listOf(
            Q8K, DOLBY_VISION, HDR, Q4K, Q1080P60, Q1080P_PLUS,
            Q1080P, Q720P60, Q720P, Q480P, Q360P, Q240P,
        )

        /** 可作为「体积优先 / 速度优先」候选的最低门槛（720P）。 */
        val ACCEPTABLE = Q720P
    }
}

/**
 * 视频编码。
 *
 * [codecId] 是 B 站 dash 流里的 codecid。
 * 字符串标识（[token]）用于持久化，因为它比数字更可读、更稳定。
 */
enum class VideoCodec(val codecId: Int, val token: String, val label: String) {
    AVC(7, "avc", "AVC / H.264"),
    HEVC(12, "hevc", "HEVC / H.265"),
    AV1(13, "av1", "AV1"),
    ;

    companion object {
        /** 默认编码。H.264 的兼容性最好，作为默认值风险最低。 */
        val DEFAULT = AVC

        fun fromToken(token: String?): VideoCodec? =
            entries.firstOrNull { it.token.equals(token, ignoreCase = true) }

        fun fromTokenOrDefault(token: String?): VideoCodec = fromToken(token) ?: DEFAULT

        fun fromCodecId(id: Int): VideoCodec? = entries.firstOrNull { it.codecId == id }
    }
}

/**
 * 音频流规格。
 *
 * [audioId] 是 B 站 dash 音频流的 id。除常见的三档码率外，
 * 杜比全景声与 Hi-Res 无损是会员内容，只对特定稿件开放。
 */
enum class AudioQuality(val audioId: Int, val label: String) {
    K64(30216, "64K"),
    K132(30232, "132K"),
    K192(30280, "192K"),
    DOLBY_ATMOS(30250, "杜比全景声"),
    HI_RES(30251, "Hi-Res 无损"),
    ;

    companion object {
        fun fromId(id: Int?): AudioQuality? = id?.let { raw -> entries.firstOrNull { it.audioId == raw } }

        /** 无损与全景声不参与「按码率挑最低」的排序，单独归类。 */
        val LOSSLESS: Set<AudioQuality> = setOf(HI_RES)
        val IMMERSIVE: Set<AudioQuality> = setOf(DOLBY_ATMOS)
    }
}
