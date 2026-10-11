package com.moko.downkyi.re.ui

/**
 * 界面展示模型。
 *
 * 这些类型描述的是「页面上要显示什么」，而不是「数据库里存了什么」。
 * 两者刻意分开：真实的下载任务有 40 多个字段（分片进度、aria2 gid、
 * 参数快照……），但卡片上只需要其中五六个。让界面直接吃数据实体，
 * 会导致改一个存储字段就牵动一堆页面。
 *
 * 接入真实数据时，只需要为每个模型写一个映射函数。
 */

/** 下载任务在界面上的形态。 */
data class TaskCardUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: TaskStatusUi,
    /** 0..1；负值表示进度未知（未拿到总长度或正在解析地址）。 */
    val progress: Float,
    /** 状态行的文案，例如「12.4 MB/s · 剩余 18 秒」。 */
    val detail: String,
    val sizeText: String,
    /** 非本机引擎时显示来源标签，例如「远程 Aria2」；本机任务为 null。 */
    val sourceLabel: String? = null,
)

enum class TaskStatusUi(val label: String) {
    Queued("等待中"),
    Running("下载中"),
    Paused("已暂停"),
    Merging("合并中"),
    Completed("已完成"),
    Failed("失败"),
}

/** 首页的常用入口。 */
data class QuickEntryUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val requiresLogin: Boolean = false,
)

/** 媒体库条目。 */
data class LibraryItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val sizeText: String,
    val kind: LibraryKind,
)

enum class LibraryKind(val label: String) {
    Video("视频"),
    Audio("音频"),
    Image("图片"),
    Danmaku("弹幕"),
    Subtitle("字幕"),
    Other("其它"),
}

/** 媒体库的工具入口。 */
data class ToolEntryUi(
    val id: String,
    val title: String,
    val description: String,
)

/**
 * 首页与下载页共享的顶层状态。
 *
 * 现在是占位数据。接入真实数据层后，这个类会被 ViewModel 的 UiState 取代，
 * 页面代码基本不用改 —— 它们已经在读这些字段了。
 */
data class AppUiState(
    val loggedIn: Boolean = false,
    val userName: String = "",
    val userUid: String = "",
    val tasks: List<TaskCardUi> = emptyList(),
    val finishedTasks: List<TaskCardUi> = emptyList(),
    val libraryItems: List<LibraryItemUi> = emptyList(),
    val libraryTotalSize: String = "0 B",
    val quickEntries: List<QuickEntryUi> = emptyList(),
    val tools: List<ToolEntryUi> = emptyList(),
)
