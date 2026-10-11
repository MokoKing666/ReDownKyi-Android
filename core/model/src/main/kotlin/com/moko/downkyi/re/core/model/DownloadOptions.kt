package com.moko.downkyi.re.core.model

/**
 * 下载内容选择。
 *
 * 用位掩码而不是 5 个布尔值：它要作为任务唯一键的一部分参与哈希，
 * 也要被持久化到数据库的一个整数列里。
 * 位值与原项目保持一致，避免历史数据语义漂移。
 */
object DownloadFlags {
    const val VIDEO = 1
    const val AUDIO = 2
    const val COVER = 4
    const val DANMAKU = 8
    const val SUBTITLE = 16

    /** 默认：视频 + 音频 + 封面 + 弹幕（字幕默认关闭，需要账号侧支持）。 */
    const val DEFAULT = VIDEO or AUDIO or COVER or DANMAKU

    const val ALL = VIDEO or AUDIO or COVER or DANMAKU or SUBTITLE

    fun has(flags: Int, flag: Int): Boolean = (flags and flag) != 0

    fun with(flags: Int, flag: Int, enabled: Boolean): Int =
        if (enabled) flags or flag else flags and flag.inv()

    /** 至少要下载一种媒体流，否则任务没有意义。 */
    fun hasAnyMedia(flags: Int): Boolean =
        has(flags, VIDEO) || has(flags, AUDIO)

    fun describe(flags: Int): List<String> = buildList {
        if (has(flags, VIDEO)) add("视频")
        if (has(flags, AUDIO)) add("音频")
        if (has(flags, COVER)) add("封面")
        if (has(flags, DANMAKU)) add("弹幕")
        if (has(flags, SUBTITLE)) add("字幕")
    }
}

/**
 * 保存位置。
 */
enum class SaveLocation(val label: String) {
    /** 系统相册：视频进 Movies/DownKyi，图片进 Pictures/DownKyi，其余进 Download/DownKyi。 */
    GALLERY("系统相册"),

    /** 应用专属外部目录，卸载即删除。 */
    APP_DIR("应用目录"),

    /** 用户自定义目录。 */
    CUSTOM("自定义目录"),
    ;

    companion object {
        val DEFAULT = GALLERY
        fun fromName(name: String?): SaveLocation =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * 音视频合并采用哪套实现。
 *
 * **默认必须是 SYSTEM**：Android 自带的 MediaMuxer 与系统播放器对 MP4 的
 * 处理最一致，兼容性最好 —— 尤其杜比视界一类带额外元数据的片源。
 * FFmpeg 的 `-c copy` 纸面上更"标准"，但实测在部分机型上会产出播不了的成品。
 *
 * FFMPEG 只作为**手动备用**：MediaMuxer 对 B 帧回退 PTS 的处理在个别设备上有差异，
 * 表现是「画面一顿一顿」，此时切到 FFmpeg 可绕开。封装是核心链路，宁可稳。
 */
enum class MuxEngine(val label: String, val description: String) {
    SYSTEM(
        "系统封装（推荐）",
        "用 Android 自带的 MediaMuxer，与系统播放器兼容性最好；杜比视界等片源请用它",
    ),
    FFMPEG(
        "FFmpeg 重封装",
        "用内置 FFmpeg 的 -c copy；仅在遇到「合并后画面一顿一顿」时切换，个别人可能播不了",
    ),
    ;

    companion object {
        val DEFAULT = SYSTEM
        fun fromName(name: String?): MuxEngine =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * 弹幕保存格式。
 */
enum class DanmakuFormat(val label: String, val extension: String) {
    NONE("不下载", ""),
    XML("XML 原始格式", "xml"),
    ASS("ASS 字幕（可用播放器加载）", "ass"),
    TXT("TXT 纯文本", "txt"),
    ;

    companion object {
        val DEFAULT = ASS
        fun fromName(name: String?): DanmakuFormat =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * 下载引擎。
 */
enum class DownloadEngine(val label: String, val description: String) {
    BUILTIN("内置下载器", "多线程分片 + 断点续传，无需额外部署"),
    ARIA2("Aria2", "通过 JSON-RPC 调用 aria2，支持远程 / NAS 下载"),
    ;

    companion object {
        val DEFAULT = BUILTIN
        fun fromName(name: String?): DownloadEngine =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * 智能选档的偏好模式。
 *
 * 每一项都同时决定「清晰度 + 编码 + 音轨」三件事，用户只需要表达意图。
 */
enum class PreferenceMode(val label: String, val description: String) {
    BEST_QUALITY("画质优先", "能拿多高拿多高（8K / 4K / HDR 都优先）"),
    COMPATIBILITY("兼容性优先", "优先 H.264，老设备与剪辑软件通吃"),
    SMALL_SIZE("体积优先", "尽量不降画质，选体积更小的编码"),
    FAST_SPEED("速度优先", "最快下完，可接受降画质"),
    ;

    companion object {
        val DEFAULT = BEST_QUALITY
        fun fromName(name: String?): PreferenceMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT

        /**
         * 各模式下的编码候选顺序（越靠前越优先）。
         *
         * 兼容性优先把 AVC 放首位是刻意的：老设备和剪辑软件对 H.264 的支持最稳。
         * 体积 / 速度优先把 AV1 放首位，因为同画质下 AV1 的码率最低。
         */
        fun codecOrder(mode: PreferenceMode): List<VideoCodec> = when (mode) {
            BEST_QUALITY -> listOf(VideoCodec.HEVC, VideoCodec.AV1, VideoCodec.AVC)
            COMPATIBILITY -> listOf(VideoCodec.AVC, VideoCodec.HEVC, VideoCodec.AV1)
            SMALL_SIZE -> listOf(VideoCodec.AV1, VideoCodec.HEVC, VideoCodec.AVC)
            FAST_SPEED -> listOf(VideoCodec.AV1, VideoCodec.HEVC, VideoCodec.AVC)
        }
    }
}

/**
 * AI 字幕策略。
 *
 * B 站的 CC 字幕里混有人工上传与机器翻译两类，`ai-zh` 这类语言码即为机翻。
 * 有些人明确不想要机翻字幕，所以「只有 AI 字幕」要能被当成「没有字幕」。
 */
enum class AiSubtitleStrategy(val label: String) {
    PREFER_HUMAN("人工优先"),
    EXCLUDE_AI("不用 AI 字幕"),
    ONLY_AI("只要 AI 字幕"),
    ;

    companion object {
        val DEFAULT = PREFER_HUMAN
        fun fromName(name: String?): AiSubtitleStrategy =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * FFmpeg 格式转换目标。
 */
enum class ConvertTarget(
    val label: String,
    val extension: String,
    val description: String,
) {
    MP4_COPY("MP4（原画无损封装）", "mp4", "容器不变、编码不变，只是重新封装容器"),
    MKV_COPY("MKV（原画无损封装）", "mkv", "把音视频重新封装为 Matroska，画质零损失"),
    MP3("MP3 音频", "mp3", "提取音轨并转码为 MP3（192kbps）"),
    M4A("M4A 音频", "m4a", "提取音轨封装为 M4A，几乎无损失"),
    GIF("GIF 动图", "gif", "截取片段生成动图，适合做表情包"),
    COMPRESS("压缩视频（H.264）", "mp4", "用 H.264 重新编码，体积更小、兼容性更好"),
    AVC_TO_HEVC("转 H.265（HEVC）", "mp4", "体积更小，需要设备支持硬解"),
    HEVC_TO_AVC("转 H.264（AVC）", "mp4", "把 H.265 转回 H.264，老设备也能播"),
    ;

    /** 只提取音频的目标需要源文件有音轨，转换前要校验。 */
    val needsAudioOnly: Boolean get() = this == MP3 || this == M4A
}
