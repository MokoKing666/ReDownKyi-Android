package com.moko.downkyi.re.core.model

/**
 * 一档清晰度在界面上的形态。
 *
 * [estimatedBytes] 是**视频 + 音频的合计**，而不是视频单独的体积。这一点
 * 看起来是细节，实际却经常被做错：只显示视频体积时，用户会以为「合并把文件
 * 撑大了」—— 杜比视界片源的音轨可达数百 MB，于是最合理的解释变成了"这个软件
 * 在偷偷转码"，信任就此崩掉。
 *
 * [requiresVip] 表示服务端声明支持该档位、却没有下发对应的流。这不是"视频没有
 * 这个清晰度"，而是"当前账号拿不到"（未登录 / 非大会员），界面必须把这两种
 * 情况分开说，否则用户会去反馈"视频缺清晰度"。
 */
data class QualityOption(
    val qn: Int,
    /** 已知档位对应的枚举；接口返回了未知编号时为 null（不猜测、也不静默映射）。 */
    val quality: VideoQuality?,
    /** 该档位真实存在的编码，按兼容性从好到差排列。 */
    val codecs: List<VideoCodec>,
    val estimatedBytes: Long,
    val requiresVip: Boolean,
) {
    val label: String get() = quality?.label ?: "未知档位（qn=$qn）"

    /** 右对齐的体积文案。拿不到流时直接说明原因，不给一个会误导的"0 B"。 */
    val sizeText: String get() = if (requiresVip) "需登录 / 大会员" else formatBytes(estimatedBytes)
}

/**
 * 一条音轨选项。
 *
 * [bitrateText] 用码率而不是体积：音轨之间体积差异不大，用户真正在比较的是
 * 「无损 / 全景声 / 有损」这三个语义档，码率是它们最直观的量化表达。
 */
data class AudioOption(
    val id: Int,
    val label: String,
    val bitrateText: String,
    val estimatedBytes: Long,
)

/**
 * 智能选档的结果。
 *
 * [codec] / [audioId] 可能为 null：视频只有一条流、没有音轨时就是这种情况，
 * 此时界面不该显示"编码"与"音频"两栏，而不是显示两个空选项。
 */
data class QualityPick(
    val qn: Int,
    val codec: VideoCodec?,
    val audioId: Int?,
)

/**
 * 体积格式化。
 *
 * 用 1024 进制而不是 1000：B 站网页端与下载类工具都按 1024 显示，
 * 这里跟着换一套进制只会让"预估 1.7 GB、下完 1.6 GB"这类差异更难解释。
 * 预估本身有 5% 左右的误差，进制不该再贡献第二层困惑。
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "未知"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    // 小数位只在数值本身很小的时候才有信息量：1.8 GB 有用，"585.9 MB" 是噪声。
    // 但阈值不能按单位（"GB 才带小数"）来定 —— 1.5 MB 与 1.5 GB 一样需要那一位。
    val text = if (value < 10.0) {
        val one = "%.1f".format(value)
        if (one.endsWith(".0")) one.dropLast(2) else one
    } else {
        value.toInt().toString()
    }
    return "$text ${units[index]}"
}

/**
 * 播放量 / 弹幕数的中文数量级写法。
 *
 * B 站自己的界面就是「128.4 万播放」，跟着它走：用户读的是量级，
 * 完整数字（1_284_000）在卡片上既占位置又要数位数。
 */
fun formatCount(value: Int): String {
    if (value <= 0) return "0"
    return when {
        value >= 100_000_000 -> "%.1f 亿".format(value / 100_000_000.0)
        value >= 10_000 -> "%.1f 万".format(value / 10_000.0)
        else -> value.toString()
    }
}

/**
 * 时长文案。
 *
 * 不足一小时不写 `00:`：分 P 列表里满屏的 `00:12:34` 会让用户以为每个分 P
 * 都是一小时级的，而实际上短视频占绝大多数。
 */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/**
 * 选档与体积估算。
 *
 * 全部是纯函数（只吃 [DashInfo] 与时长），因此可以单测 —— 这一层最容易出错，
 * 而且错了不会报错：少算一路音轨、把不可用档位当成可用、按 0 码率估出 0 B，
 * 表现都是"界面看着没问题，下完才发现不对"。
 */
object QualityPlanner {

    /**
     * 列出全部档位，从高到低。
     *
     * 数据源是 [DashInfo.qualityList] 而不是 `videos`：后者只包含当前账号
     * **有权访问**的流，而界面需要把"支持但拿不到"的档位也列出来，
     * 并标明原因（见 [QualityOption.requiresVip]）。
     */
    fun options(dash: DashInfo, durationMs: Long): List<QualityOption> {
        val defaultAudio = dash.pickAudio()
        return dash.qualityList.map { qn ->
            val streams = dash.videosOf(qn)
            QualityOption(
                qn = qn,
                quality = VideoQuality.fromQn(qn),
                codecs = codecOrder(streams),
                estimatedBytes = estimateBytes(
                    videoBandwidth = streams.maxOfOrNull { it.bandwidth } ?: 0,
                    audioBandwidth = defaultAudio?.bandwidth ?: 0,
                    durationMs = durationMs,
                ),
                requiresVip = streams.isEmpty(),
            )
        }
    }

    /** 某个档位真实存在的编码，按 [VideoCodec] 的声明顺序（即兼容性顺序）排列。 */
    fun codecOrder(streams: List<DashStream>): List<VideoCodec> =
        VideoCodec.entries.filter { codec -> streams.any { it.codecFamily == codec } }

    /**
     * 体积估算：`(视频码率 + 音频码率) / 8 × 时长`。
     *
     * 码率是 bit/s，体积是字节，所以要除以 8；漏掉这一步会让预估值大 8 倍，
     * 而且大到一眼看得出错，反而不容易上线 —— 真正危险的是少算一路音轨。
     */
    fun estimateBytes(videoBandwidth: Int, audioBandwidth: Int, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val bitrate = (videoBandwidth.coerceAtLeast(0) + audioBandwidth.coerceAtLeast(0)).toDouble()
        return (bitrate / 8.0 * (durationMs / 1000.0)).toLong()
    }

    /** 选中某个组合时的预估体积。 */
    fun estimateFor(
        dash: DashInfo,
        durationMs: Long,
        qn: Int,
        codec: VideoCodec?,
        audioId: Int?,
    ): Long {
        val video = dash.pickVideo(qn, codec)?.bandwidth ?: 0
        val audio = dash.pickAudio(audioId)?.bandwidth ?: 0
        return estimateBytes(video, audio, durationMs)
    }

    /** 音轨列表：无损与全景声在最前，其余按码率从高到低。 */
    fun audioOptions(dash: DashInfo, durationMs: Long): List<AudioOption> =
        dash.audioOptions.map { stream ->
            AudioOption(
                id = stream.id,
                label = AudioQuality.fromId(stream.id)?.label ?: "音轨 ${stream.id}",
                bitrateText = "${stream.bandwidth / 1000} kbps",
                estimatedBytes = estimateBytes(0, stream.bandwidth, durationMs),
            )
        }

    /**
     * 按「意图」选档：一次决定清晰度、编码、音轨三件事。
     *
     * 规则里唯一需要解释的是**上限**而不是"最高优先"：
     *  - 兼容性优先压到 1080P60 —— 4K 及以上几乎只有 HEVC / AV1，而这一档
     *    的用户要的正是"老设备和剪辑软件都吃得上"，给他 4K 就是给了一个
     *    打不开的文件；
     *  - 体积优先压到 1080P —— 再往上体积是数量级的增长，与"体积优先"相反；
     *  - 速度优先直接到 720P（[VideoQuality.ACCEPTABLE]）。
     *
     * 上限内找不到首选编码时**不降档**（[codecOrder] 里已按偏好排序），
     * 因为用户选的是编码偏好，不是"必须牺牲清晰度"。
     */
    fun smartPick(mode: PreferenceMode, dash: DashInfo): QualityPick {
        val playable = dash.qualityList.filter { dash.videosOf(it).isNotEmpty() }
        if (playable.isEmpty()) return QualityPick(0, null, dash.pickAudio()?.id)

        val allowed = allowedQualities(mode).map { it.qn }.toSet()
        // 上限内一档都没有（例如视频最高只有 4K）时回退到实际可用的最高档
        val candidates = playable.filter { it in allowed }.ifEmpty { playable }

        val order = PreferenceMode.codecOrder(mode)
        // qualityList 是从高到低，因此第一个"含偏好编码"的档位就是上限内的最高可用档
        val qn = candidates.firstOrNull { candidate ->
            order.any { codec -> dash.videosOf(candidate).any { it.codecFamily == codec } }
        } ?: candidates.first()

        val codec = order.firstOrNull { candidate ->
            dash.videosOf(qn).any { it.codecFamily == candidate }
        }

        // 音轨偏好与画质偏好方向一致：要小体积 / 要快就挑最小的一条
        val audioId = when (mode) {
            PreferenceMode.SMALL_SIZE, PreferenceMode.FAST_SPEED ->
                dash.audioOptions.minByOrNull { it.bandwidth }?.id

            else -> dash.pickAudio()?.id
        }

        return QualityPick(qn = qn, codec = codec, audioId = audioId)
    }

    /**
     * 各模式允许的档位。
     *
     * **必须按档位枚举来判，不能比 qn 的数字大小**：qn 的大小只保证"同一族内
     * 越高越好"，跨族完全不单调 —— 1080P60 是 116，而 1080P 只有 80。
     * 用 `qn <= 80` 当上限，会把 1080P60 挡在外面、却放行 4K（120）之外的判断
     * 也一并失准。这类错误不会报错，只会让用户觉得"选了模式但结果不对"。
     *
     * 未知 qn（接口将来新增的档位）只在"画质优先"里参与：其余模式要对它
     * 做取舍就必须先知道它有多高，而不知道就不猜。
     */
    private fun allowedQualities(mode: PreferenceMode): Set<VideoQuality> = when (mode) {
        PreferenceMode.BEST_QUALITY -> VideoQuality.entries.toSet()

        // 1080P60 及以下：4K 及以上几乎只有 HEVC / AV1，与"兼容性优先"的诉求相反
        PreferenceMode.COMPATIBILITY -> setOf(
            VideoQuality.Q1080P60, VideoQuality.Q1080P_PLUS, VideoQuality.Q1080P,
            VideoQuality.Q720P60, VideoQuality.Q720P,
            VideoQuality.Q480P, VideoQuality.Q360P, VideoQuality.Q240P,
        )

        // 到 1080P 为止，且**不含 1080P60 / 1080P+**：60 帧与高码率变体的体积
        // 是数量级增长，与"体积优先"直接冲突
        PreferenceMode.SMALL_SIZE -> setOf(
            VideoQuality.Q1080P,
            VideoQuality.Q720P60, VideoQuality.Q720P,
            VideoQuality.Q480P, VideoQuality.Q360P, VideoQuality.Q240P,
        )

        PreferenceMode.FAST_SPEED -> setOf(
            VideoQuality.Q720P,
            VideoQuality.Q480P, VideoQuality.Q360P, VideoQuality.Q240P,
        )
    }
}
