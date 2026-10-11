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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.component.AppCard
import com.moko.downkyi.re.core.designsystem.component.AppIcons
import com.moko.downkyi.re.core.designsystem.component.AppListGroup
import com.moko.downkyi.re.core.designsystem.component.AppListRow
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.component.RowTrailingText
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes
import com.moko.downkyi.re.core.designsystem.theme.rounded
import com.moko.downkyi.re.ui.AppRoute
import com.moko.downkyi.re.ui.AppUiState
import com.moko.downkyi.re.ui.LibraryItemUi
import com.moko.downkyi.re.ui.LibraryKind
import com.moko.downkyi.re.ui.ToolEntryUi

/**
 * 媒体库。
 *
 * 与「下载」页的分工：下载页管**任务**（还在跑的过程），
 * 媒体库管**产物**（已经在磁盘上的文件）。同一个视频可能同时出现在两处，
 * 但它们回答的是不同问题 —— 「下完了吗」和「文件在哪」。
 *
 * 工具箱合并在这一页，因为工具的作用对象就是这些产物；
 * 拆成两个入口会让用户在两页之间来回跳。
 */
@Composable
fun LibraryTab(
    state: AppUiState,
    onOpenRoute: (AppRoute) -> Unit,
) {
    val spacing = ReTheme.spacing
    val insets = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + spacing.md,
            // 悬浮 Dock 的真实占位：系统导航栏 inset + 统一净空
            bottom = insets.calculateBottomPadding() + Sizes.DockContentClearance,
            start = spacing.screenEdge,
            end = spacing.screenEdge,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                AppText("媒体库", style = ReTheme.typography.largeTitle)
                AppTextSecondary("已下载的文件与工具")
            }
        }

        item { StorageSummary(state) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                GroupHeader("工具")
                ToolGrid(state.tools) { onOpenRoute(AppRoute.Toolbox) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                GroupHeader("已下载的文件")
                if (state.libraryItems.isEmpty()) {
                    AppTextTertiary(
                        "还没有已下载的文件",
                        modifier = Modifier.padding(start = ReTheme.spacing.sm),
                    )
                } else {
                    // 与下载任务卡同一种轻材质：极淡灰底。
                    // 全宽大容器的 R 角用面板级（20dp）连续曲率 ——
                    // 12dp 用在这么大的容器上会显得"方"
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(ReTheme.shapes.rounded(ReTheme.shapes.extraLarge))
                            .background(ReTheme.colors.fillTertiary)
                    ) {
                        Column {
                            state.libraryItems.forEachIndexed { index, item ->
                                LibraryItemRow(
                                    item = item,
                                    showDivider = index != state.libraryItems.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分组标题。
 *
 * iOS 分组列表的 header 是小号次要文字（13pt 左右、灰阶），
 * 而不是又一个标题层级 —— 标题层级留给页面大标题与行内主文字。
 */
@Composable
private fun GroupHeader(text: String) {
    AppTextTertiary(
        text,
        style = ReTheme.typography.footnote,
        modifier = Modifier.padding(start = ReTheme.spacing.sm),
    )
}

/** 存储概览：无容器的排版 —— 大数字自己就是视觉重心，不需要一块白卡。 */
@Composable
private fun StorageSummary(state: AppUiState) {
    val spacing = ReTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ReTheme.spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppTextTertiary("本地占用", style = ReTheme.typography.footnote)
                AppText(state.libraryTotalSize, style = ReTheme.typography.numericLarge)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppTextTertiary("文件数", style = ReTheme.typography.footnote)
                AppText("${state.libraryItems.size}", style = ReTheme.typography.title2)
            }
        }
        AppTextTertiary(
            "视频保存在 Movies/DownKyi，封面、弹幕与字幕保存在 Download/DownKyi",
            style = ReTheme.typography.caption2,
        )
    }
}

/**
 * 工具入口：2×2 无容器网格（与首页常用入口同一语言）。
 *
 * 工具是**入口**不是设置项 —— 入口用排版与图标表达，不套分组容器；
 * 点击后都进入工具箱页，所以不放 chevron。
 */
@Composable
private fun ToolGrid(tools: List<ToolEntryUi>, onClick: () -> Unit) {
    val spacing = ReTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        tools.chunked(2).forEach { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                rowTools.forEach { tool ->
                    ToolCell(tool, onClick, Modifier.weight(1f))
                }
                if (rowTools.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolCell(tool: ToolEntryUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = ReTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier
            .clip(RoundedCornerShape(ReTheme.shapes.medium))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        ToolLeading(tool.id)
        AppText(tool.title, style = ReTheme.typography.headline, maxLines = 1)
        AppTextTertiary(tool.description, style = ReTheme.typography.footnote, maxLines = 2)
    }
}

/** 工具图标：直接的线性图标，不套方块底 —— 方块底是旧式列表的装饰。 */
@Composable
private fun ToolLeading(id: String) {
    val tint = ReTheme.colors.labelSecondary
    val icon: @Composable (Color, Modifier) -> Unit = when (id) {
        "convert" -> { t, m -> AppIcons.Settings(t, m) }
        "merge" -> { t, m -> AppIcons.Library(t, m) }
        "danmaku" -> { t, m -> AppIcons.Play(t, m) }
        "export" -> { t, m -> AppIcons.Download(t, m) }
        else -> { t, m -> AppIcons.Trash(t, m) }
    }
    icon(tint, Modifier.size(Sizes.IconMedium))
}

/** 文件行：类型徽章、标题、路径与体积。所有行共用一个轻材质容器。 */
@Composable
private fun LibraryItemRow(item: LibraryItemUi, showDivider: Boolean) {
    val spacing = ReTheme.spacing
    val colors = ReTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(
                horizontal = ReTheme.spacing.listRowHorizontal,
                vertical = ReTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        KindBadge(item.kind)

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppText(item.title, style = ReTheme.typography.body, maxLines = 1)
            AppTextTertiary(item.subtitle, style = ReTheme.typography.footnote, maxLines = 1)
        }

        // 体积用等宽数字右对齐：一列数字纵向对齐才好扫读
        AppText(
            item.sizeText,
            style = ReTheme.typography.numeric,
            color = colors.labelSecondary,
            maxLines = 1,
        )
    }

    if (showDivider) {
        Box(
            Modifier
                .padding(start = 12.dp + 30.dp + spacing.sm)
                .fillMaxWidth()
                .height(Sizes.HairlineWidth)
                .background(colors.separator)
        )
    }
}

/**
 * 类型徽章：柔色圆底 + 类型单字。
 *
 * 彩色圆徽是 iOS 文件类应用的经典类型表达 —— 五种媒体类型各自一个
 * 色相，扫读时靠**颜色**就能分辨类型，比灰底方块或灰字标签快得多。
 * 底色用各状态色的 Soft 档，字用同色相的实色，深浅两种主题都成立。
 */
@Composable
private fun KindBadge(kind: LibraryKind) {
    val colors = ReTheme.colors
    val (bg, tint, label) = when (kind) {
        LibraryKind.Video -> Triple(colors.accentSoft, colors.accent, "视")
        LibraryKind.Audio -> Triple(colors.infoSoft, colors.info, "音")
        LibraryKind.Image -> Triple(colors.warningSoft, colors.warning, "图")
        LibraryKind.Danmaku -> Triple(colors.successSoft, colors.success, "弹")
        LibraryKind.Subtitle -> Triple(colors.fillSecondary, colors.labelSecondary, "字")
        LibraryKind.Other -> Triple(colors.fillSecondary, colors.labelSecondary, "件")
    }

    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        AppText(label, style = ReTheme.typography.caption1, color = tint)
    }
}
