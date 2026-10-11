package com.moko.downkyi.re.ui.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.component.AppCard
import com.moko.downkyi.re.core.designsystem.component.AppIcons
import com.moko.downkyi.re.core.designsystem.component.AppListGroup
import com.moko.downkyi.re.core.designsystem.component.AppListRow
import com.moko.downkyi.re.core.designsystem.component.AppSwitch
import com.moko.downkyi.re.core.designsystem.component.ChevronIndicator
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.component.RowTrailingText
import com.moko.downkyi.re.core.designsystem.component.SolidButton
import com.moko.downkyi.re.core.designsystem.component.contentColor
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes
import com.moko.downkyi.re.core.model.SaveLocation
import com.moko.downkyi.re.ui.AppRoute
import com.moko.downkyi.re.ui.AppUiState

/**
 * 我的。
 *
 * 这一页的两件事：账号状态与设置入口。设置项直接在这里露出「当前值」，
 * 而不是只给一个「设置」入口 —— 下载工具的用户最常改的就是清晰度、
 * 保存位置和并发，能在列表上直接看到当前值能省掉大量来回点击。
 */
@Composable
fun ProfileTab(
    state: AppUiState,
    onOpenRoute: (AppRoute) -> Unit,
    onToggleTheme: () -> Unit,
    isDark: Boolean,
    /** 真实生效的保存位置（设置存储）。 */
    saveLocation: SaveLocation = SaveLocation.DEFAULT,
    /** 循环切换保存位置（系统相册 ↔ 应用目录）。 */
    onCycleSaveLocation: () -> Unit = {},
    /** 默认清晰度（qn）；0 = 智能选档。 */
    defaultQn: Int = 0,
    onCycleDefaultQn: () -> Unit = {},
    /** 默认编码："auto" | "AVC" | "HEVC"。 */
    defaultCodec: String = "auto",
    onCycleDefaultCodec: () -> Unit = {},
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
                AppText("我的", style = ReTheme.typography.largeTitle)
                AppTextSecondary("账号与偏好设置")
            }
        }

        item { AccountCard(state, onOpenRoute) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SectionTitle("下载")
                DownloadGroup(
                    saveLocation = saveLocation,
                    onCycleSaveLocation = onCycleSaveLocation,
                    defaultQn = defaultQn,
                    onCycleDefaultQn = onCycleDefaultQn,
                    defaultCodec = defaultCodec,
                    onCycleDefaultCodec = onCycleDefaultCodec,
                    onOpenRoute = onOpenRoute,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SectionTitle("外观")
                // 跟随系统设为关闭，因为这里提供的是手动切换入口；
                // 真实实现里「跟随系统」与「手动选择」是同一组选项的两个状态
                AppearanceGroup(
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SectionTitle("订阅与其它")
                OtherGroup(onOpenRoute)
            }
        }

        item { AboutCard() }

        item {
            AppTextTertiary(
                "本应用仅提供视频解析与本地下载能力，不提供任何内容存储服务。\n" +
                    "所有内容版权归原作者所有，仅供个人学习交流，请支持原始发布者。"
            )
        }
    }
}

/**
 * 分组标题。
 *
 * iOS 分组列表的 header 是小号次要文字，不是又一个标题层级 ——
 * 标题层级留给页面大标题与行内主文字。
 */
/** 清晰度标签：0 = 智能选档。 */
private fun qnLabel(qn: Int): String = when (qn) {
    0 -> "智能选档"
    116 -> "1080P 60帧"
    112 -> "1080P 高码率"
    80 -> "1080P 高清"
    64 -> "720P 高清"
    32 -> "480P 清晰"
    else -> "qn=$qn"
}

private fun codecLabel(codec: String): String = when (codec) {
    "AVC" -> "AVC / H.264"
    "HEVC" -> "HEVC / H.265"
    else -> "自动"
}

@Composable
private fun SectionTitle(text: String) {
    AppTextTertiary(
        text,
        style = ReTheme.typography.footnote,
        modifier = Modifier.padding(start = ReTheme.spacing.sm),
    )
}

/**
 * 账号区：无边框排版。
 *
 * 未登录时明确写出「登录能得到什么」：B 站在未登录时只会返回低清晰度，
 * 而这是**静默的**（接口返回成功，只是档位变少）。不写清楚，
 * 用户会以为是应用本身的问题。
 *
 * 不再套卡片：账号是本页的**门面信息**，用头像与文字的排版直接表达；
 * 切换账号是次要动作，只以品牌色文字参与。
 */
@Composable
private fun AccountCard(
    state: AppUiState,
    onOpenRoute: (AppRoute) -> Unit,
) {
    val colors = ReTheme.colors
    val spacing = ReTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onOpenRoute(AppRoute.Login) },
            )
            .padding(horizontal = ReTheme.spacing.xxs, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.fillSecondary),
            contentAlignment = Alignment.Center,
        ) {
            AppIcons.Person(colors.labelSecondary, Modifier.size(Sizes.IconMedium))
        }

        Column(Modifier.weight(1f)) {
            AppText(
                if (state.loggedIn) state.userName else "未登录",
                style = ReTheme.typography.title3,
                maxLines = 1,
            )
            AppTextTertiary(
                if (state.loggedIn) {
                    "UID ${state.userUid}"
                } else {
                    "登录后可下载 1080P+ / 4K 及会员内容"
                },
                style = ReTheme.typography.footnote,
            )
        }

        AppText(
            if (state.loggedIn) "切换" else "登录",
            style = ReTheme.typography.subheadline,
            color = colors.accent,
        )
        ChevronIndicator(tint = colors.labelQuaternary)
    }
}

@Composable
private fun DownloadGroup(
    saveLocation: SaveLocation,
    onCycleSaveLocation: () -> Unit,
    defaultQn: Int,
    onCycleDefaultQn: () -> Unit,
    defaultCodec: String,
    onCycleDefaultCodec: () -> Unit,
    onOpenRoute: (AppRoute) -> Unit,
) {
    // 无容器：设置行直接排在页面上，层级由标题、行距与右侧当前值建立。
    // 前三行都真实生效（点击循环切换并立即持久化）
    Column {
        AppListRow(
            title = "保存位置",
            subtitle = "视频进 Movies/DownKyi，弹幕与字幕进 Download/DownKyi",
            trailing = { RowTrailingText(saveLocation.label) },
            showChevron = true,
            showDivider = false,
            onClick = onCycleSaveLocation,
        )
        AppListRow(
            title = "默认清晰度",
            subtitle = "智能选档按分 P 可用档位自动选，也可固定",
            trailing = { RowTrailingText(qnLabel(defaultQn)) },
            showChevron = true,
            showDivider = false,
            onClick = onCycleDefaultQn,
        )
        AppListRow(
            title = "默认编码",
            subtitle = "AVC 兼容性最好，HEVC 体积更小",
            trailing = { RowTrailingText(codecLabel(defaultCodec)) },
            showChevron = true,
            showDivider = false,
            onClick = onCycleDefaultCodec,
        )
        AppListRow(
            title = "并发",
            subtitle = "任务启动间隔可降低批量下载被风控的概率",
            trailing = { RowTrailingText("2 任务 · 8 分片") },
            showChevron = true,
            showDivider = false,
            onClick = { onOpenRoute(AppRoute.Settings) },
        )
        AppListRow(
            title = "下载引擎",
            trailing = { RowTrailingText("内置下载器") },
            showChevron = true,
            showDivider = false,
            onClick = { onOpenRoute(AppRoute.Settings) },
        )
    }
}

/**
 * 外观。
 *
 * 「跟随系统」在真实实现里与手动选择是同一组选项的三个状态
 * （跟随系统 / 强制浅色 / 强制深色）。这里先用手动开关把视觉验证跑通。
 */
@Composable
private fun AppearanceGroup(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    Column {
        AppListRow(
            title = "主题",
            subtitle = "当前：随机型与手动设置共同决定",
            trailing = {
                AppSwitch(
                    checked = isDark,
                    onCheckedChange = { onToggleTheme() },
                )
            },
            showDivider = false,
        )
        AppListRow(
            title = "下载设置",
            subtitle = "弹幕格式、字幕语言、合并方式、文件命名",
            showChevron = true,
            showDivider = false,
            onClick = {},
        )
    }
}

@Composable
private fun OtherGroup(onOpenRoute: (AppRoute) -> Unit) {
    var archiveEnabled by remember { mutableStateOf(true) }
    var wifiOnly by remember { mutableStateOf(false) }

    Column {
        AppListRow(
            title = "订阅更新",
            subtitle = "UP 主 / 合集 / 收藏夹 / 番剧整季",
            showChevron = true,
            showDivider = false,
            onClick = { onOpenRoute(AppRoute.Subscription) },
        )
        AppListRow(
            title = "下载归档",
            subtitle = "按 BV / 分P / 清晰度 / 编码判定，追更时不重复下载",
            trailing = {
                AppSwitch(checked = archiveEnabled, onCheckedChange = { archiveEnabled = it })
            },
            showDivider = false,
        )
        AppListRow(
            title = "仅 Wi-Fi 下载",
            subtitle = "移动网络下任务会自动暂停",
            trailing = {
                AppSwitch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            },
            showDivider = false,
        )
        AppListRow(
            title = "查看运行日志",
            subtitle = "敏感信息已自动脱敏",
            showChevron = true,
            showDivider = false,
            onClick = { onOpenRoute(AppRoute.Settings) },
        )
    }
}

@Composable
private fun AboutCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ReTheme.spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            AppText("Re：哔哩下载姬", style = ReTheme.typography.headline)
            AppTextTertiary("基于原项目的业务规格以 Kotlin + Compose 重写")
        }
        AppText("v1.0.0", style = ReTheme.typography.numeric, color = ReTheme.colors.labelTertiary)
    }
}
