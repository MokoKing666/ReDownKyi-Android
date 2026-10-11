package com.moko.downkyi.re.ui

/**
 * 占位数据。
 *
 * **这是临时的。** 存在的唯一目的是让页面骨架能被真实地看到与验收 ——
 * 空白页面无法判断排版、层级与玻璃采样是否正确。
 *
 * 接入数据层时需要删除这个文件，改由 ViewModel 提供 `AppUiState`。
 * 所有页面已经在读 `AppUiState` 的字段，因此替换时页面代码基本不用动。
 */
object SampleData {

    fun appState(): AppUiState = AppUiState(
        loggedIn = true,
        userName = "MokoKing",
        userUid = "672627254",

        quickEntries = listOf(
            QuickEntryUi("fav", "我的收藏夹", "读取全部收藏夹", requiresLogin = true),
            QuickEntryUi("history", "观看历史", "最近看过的视频", requiresLogin = true),
            QuickEntryUi("toview", "稍后再看", "待看清单", requiresLogin = true),
            QuickEntryUi("space", "UP 主合集", "按 UID 读取投稿"),
        ),

        tasks = listOf(
            TaskCardUi(
                id = "t1",
                title = "【4K 修复】老番经典片段合集 · 第一辑",
                subtitle = "P1 · 某某UP主 · 4K 超清",
                status = TaskStatusUi.Running,
                progress = 0.42f,
                detail = "12.4 MB/s · 剩余 18 秒",
                sizeText = "1.8 GB",
            ),
            TaskCardUi(
                id = "t2",
                title = "杜比视界演示片：城市夜景延时",
                subtitle = "P1 · DemoChannel · 杜比视界",
                status = TaskStatusUi.Running,
                progress = -1f,
                detail = "正在解析播放地址…",
                sizeText = "3.2 GB",
            ),
            TaskCardUi(
                id = "t3",
                title = "编程教程：从零实现一个下载器",
                subtitle = "P3 · TechTalk · 1080P 高清",
                status = TaskStatusUi.Paused,
                progress = 0.87f,
                detail = "已暂停 · 1.2 GB / 1.4 GB",
                sizeText = "1.4 GB",
            ),
            TaskCardUi(
                id = "t4",
                title = "8K 风光素材：雪山日出",
                subtitle = "P1 · NatureLab · 8K 超清",
                status = TaskStatusUi.Queued,
                progress = 0f,
                detail = "等待开始",
                sizeText = "6.1 GB",
                sourceLabel = "远程 Aria2",
            ),
            TaskCardUi(
                id = "t5",
                title = "音乐现场：交响乐全集",
                subtitle = "P2 · ConcertHall · 1080P60",
                status = TaskStatusUi.Merging,
                progress = 0.99f,
                detail = "正在合并音视频…",
                sizeText = "2.4 GB",
            ),
        ),

        finishedTasks = listOf(
            TaskCardUi(
                id = "f1",
                title = "纪录片：深海之下 第一集",
                subtitle = "P1 · OceanDoc · 4K 超清",
                status = TaskStatusUi.Completed,
                progress = 1f,
                detail = "已完成 · 2.1 GB",
                sizeText = "2.1 GB",
            ),
            TaskCardUi(
                id = "f2",
                title = "钢琴曲合集 · 舒缓纯音乐",
                subtitle = "P1 · MusicBox · Hi-Res 无损",
                status = TaskStatusUi.Completed,
                progress = 1f,
                detail = "已完成（未合并，可在「工具」里手动合并）· 480 MB",
                sizeText = "480 MB",
            ),
            TaskCardUi(
                id = "f3",
                title = "动画短片：星海彼岸",
                subtitle = "P1 · AnimationLab · 1080P 高清",
                status = TaskStatusUi.Completed,
                progress = 1f,
                detail = "已完成，但 字幕 未成功 · 620 MB",
                sizeText = "620 MB",
            ),
            TaskCardUi(
                id = "f4",
                title = "现场演出：夏日音乐节",
                subtitle = "P1 · LiveHouse · 1080P60",
                status = TaskStatusUi.Failed,
                progress = 0.35f,
                detail = "播放地址已过期，自动刷新 3 次后仍失败",
                sizeText = "1.1 GB",
            ),
        ),

        libraryItems = listOf(
            LibraryItemUi("l1", "纪录片：深海之下 第一集", "Movies/DownKyi · 4K", "2.1 GB", LibraryKind.Video),
            LibraryItemUi("l2", "钢琴曲合集 · 舒缓纯音乐", "Download/DownKyi · m4a", "480 MB", LibraryKind.Audio),
            LibraryItemUi("l3", "动画短片：星海彼岸", "Movies/DownKyi · 1080P", "620 MB", LibraryKind.Video),
            LibraryItemUi("l4", "深海之下 第一集 · 封面", "Download/DownKyi", "1.2 MB", LibraryKind.Image),
            LibraryItemUi("l5", "钢琴曲合集 · 弹幕", "Download/DownKyi · ass", "3.4 MB", LibraryKind.Danmaku),
            LibraryItemUi("l6", "星海彼岸 · 字幕", "Download/DownKyi · srt", "86 KB", LibraryKind.Subtitle),
        ),
        libraryTotalSize = "3.2 GB",

        tools = listOf(
            ToolEntryUi("convert", "格式转换（FFmpeg）", "无损封装 MP4 / MKV、提取音频、GIF、压缩、H.264 ↔ H.265"),
            ToolEntryUi("merge", "重新合并音视频", "把保留的分片用系统封装重新合成"),
            ToolEntryUi("danmaku", "重新生成弹幕", "重新拉取弹幕并生成 ASS / XML / TXT"),
            ToolEntryUi("export", "导出到相册 / 下载目录", "视频进 Movies，其余文件进 Download"),
            ToolEntryUi("clean", "清理缓存", "删除临时分片与残留文件，不影响已完成的作品"),
        ),
    )
}
