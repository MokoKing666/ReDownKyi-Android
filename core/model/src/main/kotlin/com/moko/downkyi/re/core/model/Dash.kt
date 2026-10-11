package com.moko.downkyi.re.core.model

/**
 * 单条 DASH 流（视频或音频）。
 *
 * B 站的 DASH 播放信息把每个清晰度下的每种编码列为**独立的一条流**，
 * 所以「4K」不是一条流，而是「4K + HEVC」「4K + AV1」两条。
 * 选流的本质是在这个二维表里挑一格。
 */
data class DashStream(
    /** 视频流是清晰度 qn，音频流是音轨 id。 */
    val id: Int,
    val url: String,
    val backupUrls: List<String> = emptyList(),
    /** 平均码率（bit/s）。体积预估与「选最高码率」都依赖它。 */
    val bandwidth: Int,
    val mimeType: String,
    /** 原始编码串，例如 `avc1.640032`、`hev1.1.6.L120.90`、`av01.0.08M.08`。 */
    val codecs: String,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: String = "",
    val isVideo: Boolean = true,
) {

    /**
     * 编码族。
     *
     * 从 `codecs` 的前缀判断，而不是用接口返回的 codecid：
     * 同一档清晰度下 codecid 与编码串偶尔对不上，而编码串是实际用来解码的依据。
     */
    val codecFamily: VideoCodec
        get() {
            val value = codecs.lowercase()
            return when {
                value.startsWith("hev") || value.startsWith("hvc") -> VideoCodec.HEVC
                value.startsWith("av01") -> VideoCodec.AV1
                else -> VideoCodec.AVC
            }
        }

    /** 主地址 + 备用地址。地址过期时按顺序重试，不必立即重新解析。 */
    val allUrls: List<String>
        get() = buildList {
            if (url.isNotEmpty()) add(url)
            addAll(backupUrls.filter { it.isNotEmpty() })
        }
}

/**
 * DASH 播放信息。
 */
data class DashInfo(
    val durationMs: Long,
    val videos: List<DashStream>,
    val audios: List<DashStream>,
    /** Hi-Res 无损音轨，接口放在独立字段里而不是 audios 列表。 */
    val flac: DashStream? = null,
    /** 杜比全景声音轨，同上。 */
    val dolby: DashStream? = null,
    /** 服务端声明的支持档位（qn -> 名称）。用于补全 videos 里没体现的档位。 */
    val supportFormats: Map<Int, String> = emptyMap(),
) {

    val hasVideo: Boolean get() = videos.isNotEmpty()

    val hasAudio: Boolean get() = audios.isNotEmpty() || flac != null || dolby != null

    /**
     * 所有可用清晰度，**从高到低**。
     *
     * 合并 videos 的实际档位与 supportFormats 的声明档位：
     * 后者会包含「本视频支持但当前账号无权限」的档位，界面需要据此
     * 给出「需要登录 / 需要大会员」的提示，而不是干脆不显示。
     */
    val qualityList: List<Int>
        get() = (videos.map { it.id } + supportFormats.keys).toSet().sortedDescending()

    fun videosOf(quality: Int): List<DashStream> = videos.filter { it.id == quality }

    /**
     * 选一条视频流。
     *
     * 三级回退，每一级都对应一个真实场景：
     *  1. **精确匹配所选档位与编码** —— 单视频下载的正常路径；
     *  2. **同档位换编码** —— 用户选了 AV1 但该清晰度只有 HEVC；
     *  3. **换档位** —— 批量下载时的关键路径：选项面板给的是「参考视频」
     *     支持的档位，其余视频未必有同一档，必须各自回退到可用的最高档，
     *     而不是整条任务失败。
     *
     * 最后按码率取最高的一条：同一档位可能有多个码率变体。
     */
    fun pickVideo(quality: Int, preferCodec: VideoCodec? = null): DashStream? {
        var candidates = videosOf(quality)

        if (candidates.isEmpty()) {
            val available = qualityList
            // qualityList 由 videos 派生，为空即表示一条流都没有
            if (available.isEmpty()) return null
            val fallbackQuality = available.firstOrNull { videosOf(it).isNotEmpty() }
                ?: return null
            candidates = videosOf(fallbackQuality)
        }

        if (preferCodec != null) {
            val matched = candidates.filter { it.codecFamily == preferCodec }
            if (matched.isNotEmpty()) candidates = matched
        }

        return candidates.maxByOrNull { it.bandwidth }
    }

    /**
     * 选一条音频流。
     *
     * 无损与杜比不参与「按码率排序」的常规竞争 —— 它们被放在独立字段里，
     * 语义上就是「默认优先」。指定了 [preferId] 时精确匹配，匹配不到
     * 才回退到码率最高的一条。
     */
    fun pickAudio(preferId: Int? = null): DashStream? {
        val all = buildList {
            flac?.let { add(it) }
            dolby?.let { add(it) }
            addAll(audios)
        }
        if (all.isEmpty()) return null

        if (preferId != null) {
            all.firstOrNull { it.id == preferId }?.let { return it }
        }
        return all.maxByOrNull { it.bandwidth }
    }

    /** 可选的音轨列表（界面用），无损与杜比排在最前。 */
    val audioOptions: List<DashStream>
        get() = buildList {
            flac?.let { add(it) }
            dolby?.let { add(it) }
            addAll(audios.sortedByDescending { it.bandwidth })
        }
}
