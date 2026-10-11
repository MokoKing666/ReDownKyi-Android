package com.moko.downkyi.re.ui.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.component.AppCard
import com.moko.downkyi.re.core.designsystem.component.AppIcons
import com.moko.downkyi.re.core.designsystem.component.AppProgressBar
import com.moko.downkyi.re.core.designsystem.component.AppProgressRing
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.glass.GlassBackdrop
import com.moko.downkyi.re.core.designsystem.liquidglass.LiquidBottomTab
import com.moko.downkyi.re.core.designsystem.liquidglass.LiquidBottomTabs
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes
import com.moko.downkyi.re.core.designsystem.theme.rounded
import com.moko.downkyi.re.ui.AppRoute
import com.moko.downkyi.re.ui.AppUiState
import com.moko.downkyi.re.ui.TaskCardUi
import com.moko.downkyi.re.ui.TaskStatusUi

/**
 * 下载页。
 *
 * 「进行中 / 已完成」的切换直接复用官方 [LiquidBottomTabs]：
 * 玻璃胶囊容器 + 可拖动的液态玻璃气泡 —— 按住气泡左右拖动即可切换
 * 两个分段，松手吸附；点击文字同样切换。与 Dock 是同一套
 * 折射/高光/流体形变机制。
 *
 * 界面上刻意把「状态」放在最显眼的位置：进度环、状态行、进度条三者
 * 表达的是同一件事的不同侧面 —— 一眼看整体、一行看细节、一栏看趋势。
 * 下载工具最忌讳的是「看起来在跑但不知道到哪一步了」。
 */
@Composable
fun DownloadsTab(
    state: AppUiState,
    onOpenRoute: (AppRoute) -> Unit,
    /** 背景专用采样源：分段玻璃在内容层内，只能采样背景（避免图层自引用）。 */
    backdrop: GlassBackdrop,
    onPause: (String) -> Unit = {},
    onResume: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    var segment by remember { mutableIntStateOf(0) }
    val spacing = ReTheme.spacing
    val insets = WindowInsets.systemBars.asPaddingValues()

    val list = if (segment == 0) state.tasks else state.finishedTasks
    val runningCount = state.tasks.count { it.status == TaskStatusUi.Running }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + spacing.md,
            // 悬浮 Dock 的真实占位：系统导航栏 inset + 统一净空
            bottom = insets.calculateBottomPadding() + Sizes.DockContentClearance,
            start = spacing.screenEdge,
            end = spacing.screenEdge,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText("下载", style = ReTheme.typography.largeTitle)
                Spacer(Modifier.width(spacing.xs))
                if (runningCount > 0) {
                    RunningBadge(runningCount)
                }
            }
        }

        item {
            // 官方 LiquidBottomTabs：两段切换 = 玻璃胶囊容器 + 可拖动气泡
            LiquidBottomTabs(
                selectedTabIndex = { segment },
                onTabSelected = { segment = it },
                backdrop = backdrop,
                tabsCount = 2,
                accentColor = ReTheme.colors.accent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LiquidBottomTab(onClick = { segment = 0 }) {
                    AppText(
                        "进行中 (${state.tasks.size})",
                        style = ReTheme.typography.subheadline,
                        color = ReTheme.colors.label,
                        maxLines = 1,
                    )
                }
                LiquidBottomTab(onClick = { segment = 1 }) {
                    AppText(
                        "已完成 (${state.finishedTasks.size})",
                        style = ReTheme.typography.subheadline,
                        color = ReTheme.colors.label,
                        maxLines = 1,
                    )
                }
            }
        }

        if (list.isEmpty()) {
            item { EmptyState(segment == 0) }
        } else {
            items(list, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onPause = onPause,
                    onResume = onResume,
                    onRetry = onRetry,
                    onDelete = onDelete,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun RunningBadge(count: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(ReTheme.shapes.capsule))
            .background(ReTheme.colors.accentSoft)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        AppText(
            "$count 进行中",
            style = ReTheme.typography.caption1,
            color = ReTheme.colors.accent,
        )
    }
}

@Composable
private fun EmptyState(isRunning: Boolean) {
    val spacing = ReTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = spacing.xxl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppIcons.Download(ReTheme.colors.accent, Modifier.size(28.dp))
        }
        Spacer(Modifier.height(spacing.xxs))
        AppTextSecondary(if (isRunning) "暂无下载任务" else "还没有完成的任务")
        AppTextTertiary(
            if (isRunning) "去首页粘贴链接开始下载" else "完成的任务会出现在这里",
            style = ReTheme.typography.footnote,
        )
    }
}

/**
 * 下载任务卡片。
 *
 * 信息优先级：标题 → 进度 → 状态 → 操作。副标题（分P / UP 主 / 清晰度）
 * 用次级文字色，它属于「需要时才看」的信息。
 */
@Composable
private fun TaskCard(
    task: TaskCardUi,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = ReTheme.colors
    val spacing = ReTheme.spacing
    val progressColor = when (task.status) {
        TaskStatusUi.Failed -> colors.danger
        TaskStatusUi.Completed -> colors.success
        TaskStatusUi.Paused -> colors.labelTertiary
        else -> colors.accent
    }

    // 任务容器用极淡灰底（iOS 搜索框材质）而不是白卡：
    // 任务是高频滚动的内容，轻材质才不会每行都"跳"出来。
    // **纵向排列必须用 Column**：之前用 Box 并列"封面行"与"下方信息"，
    // Box 是叠放容器，两块内容全部叠在左上角
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ReTheme.shapes.rounded(ReTheme.shapes.medium))
            .background(ReTheme.colors.fillTertiary)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            ThumbnailPlaceholder()

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AppText(
                    task.title,
                    style = ReTheme.typography.headline,
                    maxLines = 2,
                )
                AppTextSecondary(task.subtitle, style = ReTheme.typography.footnote, maxLines = 1)
                if (task.sourceLabel != null) {
                    SourceLabel(task.sourceLabel)
                }
            }

            // 右侧固定位置放进度环：环形比线性条更适合这个小方块，
            // 且能在圆心显示百分比，不必多占一行文字
            if (task.status == TaskStatusUi.Running || task.status == TaskStatusUi.Merging) {
                AppProgressRing(
                    progress = task.progress,
                    size = 38.dp,
                    strokeWidth = 3.5.dp,
                    color = progressColor,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            AppProgressBar(progress = task.progress, color = progressColor)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    task.detail,
                    style = ReTheme.typography.footnote,
                    color = if (task.status == TaskStatusUi.Failed) colors.danger else colors.labelSecondary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(spacing.xs))
                AppText(
                    task.sizeText,
                    style = ReTheme.typography.numeric,
                    color = colors.labelTertiary,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                TaskActions(task, onPause, onResume, onRetry, onDelete, onOpen)
            }
        }
    }
}

/** 封面占位。接入真实数据后会换成封面图 + 时长角标。 */
@Composable
private fun ThumbnailPlaceholder() {
    Box(
        Modifier
            .width(Sizes.ThumbnailWidth)
            .height(Sizes.ThumbnailHeight)
            .clip(RoundedCornerShape(ReTheme.shapes.small))
            .background(ReTheme.colors.fillTertiary),
        contentAlignment = Alignment.Center,
    ) {
        AppIcons.Play(ReTheme.colors.labelQuaternary, Modifier.size(18.dp))
    }
}

/** 任务来源标签。只对非本机任务显示 —— 本机任务加标签反而会让界面变脏。 */
@Composable
private fun SourceLabel(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(ReTheme.shapes.extraSmall))
            .background(ReTheme.colors.fillSecondary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        AppText(text, style = ReTheme.typography.caption2, color = ReTheme.colors.labelSecondary)
    }
}

/**
 * 任务操作。
 *
 * 按状态给不同的主动作 —— 这是下载工具最容易被做错的地方：
 * 把「暂停 / 继续 / 重试 / 打开」全铺出来，用户每次都要先判断该点哪个。
 * 界面上只出现当前状态下有意义的那一个。
 */
@Composable
private fun TaskActions(
    task: TaskCardUi,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = ReTheme.colors

    when (task.status) {
        TaskStatusUi.Queued, TaskStatusUi.Running, TaskStatusUi.Merging -> {
            IconAction({ t, m -> AppIcons.Pause(t, m) }, "暂停", colors.label) { onPause(task.id) }
        }

        TaskStatusUi.Paused -> {
            IconAction({ t, m -> AppIcons.Play(t, m) }, "继续", colors.accent) { onResume(task.id) }
        }

        TaskStatusUi.Failed -> {
            IconAction({ t, m -> AppIcons.Download(t, m) }, "重试", colors.accent) { onRetry(task.id) }
        }

        // 完成态的「打开」只在成品已发布（记住位置）时才渲染 —— 上层会判断
        TaskStatusUi.Completed -> {
            IconAction({ t, m -> AppIcons.Play(t, m) }, "打开", colors.accent) { onOpen(task.id) }
        }
    }
    IconAction({ t, m -> AppIcons.Trash(t, m) }, "删除", colors.danger) { onDelete(task.id) }
}

@Composable
private fun IconAction(
    icon: @Composable (Color, Modifier) -> Unit,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(ReTheme.shapes.small))
            .background(ReTheme.colors.fillTertiary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint, Modifier.size(13.dp))
        AppText(label, style = ReTheme.typography.footnote, color = tint)
    }
}
