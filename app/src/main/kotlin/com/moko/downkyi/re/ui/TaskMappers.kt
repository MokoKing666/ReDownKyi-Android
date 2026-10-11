package com.moko.downkyi.re.ui

import com.moko.downkyi.re.core.download.DownloadStatus
import com.moko.downkyi.re.core.download.DownloadTaskState

/**
 * 下载状态 → 界面卡片。
 *
 * 映射函数（而不是让页面吃数据实体）是 [UiModels] 里定下的规矩：
 * 下载实体将来会有几十个字段，卡片只关心其中的五六个。
 *
 * 状态取一个值得说明的子集：[DownloadStatus.Cancelled] 在界面上没有对应的
 * 枚举值 —— 用户点了删除的任务不该继续占一行，但在「删除 = 移除任务」
 * 接通之前它仍会短暂出现在状态表里，按「已暂停」呈现，避免凭空多出一种状态。
 */
fun DownloadTaskState.toTaskCard(): TaskCardUi {
    val known = totalBytes > 0L
    return TaskCardUi(
        id = id,
        title = title,
        subtitle = subtitle,
        status = when (status) {
            DownloadStatus.Pending -> TaskStatusUi.Queued
            DownloadStatus.Running -> TaskStatusUi.Running
            DownloadStatus.Merging -> TaskStatusUi.Merging
            DownloadStatus.Paused, DownloadStatus.Cancelled -> TaskStatusUi.Paused
            DownloadStatus.Completed -> TaskStatusUi.Completed
            DownloadStatus.Failed -> TaskStatusUi.Failed
        },
        // 总长度未知时给 -1：进度环与进度条按「不确定态」渲染，而不是停在 0%
        progress = if (known) progress else -1f,
        detail = detailLine(),
        sizeText = if (known) formatBytes(totalBytes) else "",
    )
}

private fun DownloadTaskState.detailLine(): String = when (status) {
    DownloadStatus.Pending -> "等待开始"
    DownloadStatus.Running ->
        if (totalBytes > 0L) "下载中 · ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
        else "下载中 · 正在解析播放地址…"
    DownloadStatus.Merging -> "正在合并音视频…"
    DownloadStatus.Paused ->
        if (totalBytes > 0L) "已暂停 · ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
        else "已暂停"
    DownloadStatus.Completed -> "已完成" + if (totalBytes > 0L) " · ${formatBytes(totalBytes)}" else ""
    DownloadStatus.Failed -> errorMessage ?: "下载失败"
    DownloadStatus.Cancelled -> "已取消 · 分片已保留"
}

/** 「进行中」= 未到终态；完成与失败都归入「已完成」分段（与原占位数据语义一致）。 */
val DownloadStatus.isTerminal: Boolean
    get() = this == DownloadStatus.Completed || this == DownloadStatus.Failed

val TaskStatusUi.isTerminalUi: Boolean
    get() = this == TaskStatusUi.Completed || this == TaskStatusUi.Failed

/** 人类可读的字节数。进位用 1024：下载工具的用户预期是「文件管理器口径」。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return ""
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
}
