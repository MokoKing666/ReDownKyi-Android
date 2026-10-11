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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.component.AppIcons
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.component.ChevronIndicator
import com.moko.downkyi.re.core.designsystem.glass.GlassBackdrop
import com.moko.downkyi.re.core.designsystem.liquidglass.LiquidButton
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes
import com.moko.downkyi.re.core.designsystem.theme.rounded
import com.moko.downkyi.re.ui.AppRoute
import com.moko.downkyi.re.ui.AppUiState

/**
 * 首页。
 *
 * 阅读顺序：
 *  1. 标题  2. 输入与解析  3. 常用入口  4. 订阅与账号  5. 免责声明
 *
 * 现代化的材质分工：
 *  - 液态玻璃只属于两处：Dock、主操作「开始解析」（官方 LiquidButton，
 *    与 Dock 同一套折射/高光/形变机制）；
 *  - 输入框是 iOS 搜索框式的**轻量灰底**（fillTertiary），不是一块大白卡；
 *  - 常用入口用 2×2 网格的**无容器排版**表达 —— 图标、文字与留白
 *    自己建立层级，不靠灰色方块底、分隔线和右箭头这些旧式列表装饰；
 *  - 订阅与账号是两行无边框内容，尾部箭头极淡。
 */
@Composable
fun HomeTab(
    state: AppUiState,
    onOpenRoute: (AppRoute) -> Unit,
    /** 背景专用采样源，供解析按钮的玻璃使用。见 AppShell 的两级采样说明。 */
    backdrop: GlassBackdrop,
) {
    val spacing = ReTheme.spacing
    val insets = WindowInsets.systemBars.asPaddingValues()
    var input by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + spacing.md,
            // 悬浮 Dock 的真实占位：系统导航栏 inset + 统一净空
            bottom = insets.calculateBottomPadding() + Sizes.DockContentClearance,
            start = spacing.screenEdge,
            end = spacing.screenEdge,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                AppText("下载姬", style = ReTheme.typography.largeTitle)
                AppTextSecondary("粘贴链接，解析后选择清晰度与编码")
            }
        }

        item {
            HomeParsePanel(
                backdrop = backdrop,
                value = input,
                onValueChange = { input = it },
                // 把原始输入原样带到解析页：识别（BV / ep / 短链…）由数据层负责，
                // 首页只需要判断"非空"，否则链接识别规则会被抄成两份
                onParse = { onOpenRoute(AppRoute.ParseResult(input.trim())) },
                onSearch = { onOpenRoute(AppRoute.Search) },
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                GroupHeader("常用入口")
                QuickEntryGrid(onOpenRoute)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SubscriptionRow(onOpenRoute)
            }
        }

        item {
            AccountRow(state, onOpenRoute)
        }

        item {
            AppTextTertiary(
                "本应用仅提供视频解析与本地下载能力，不提供任何内容存储服务。" +
                    "所有内容版权归原作者所有，仅供个人学习交流，请支持原始发布者。",
                style = ReTheme.typography.footnote,
            )
        }
    }
}

/** 分组标题：小号次要文字，不是又一个标题层级。 */
@Composable
private fun GroupHeader(text: String) {
    AppTextTertiary(
        text,
        style = ReTheme.typography.footnote,
        modifier = Modifier.padding(start = ReTheme.spacing.xxs),
    )
}

/**
 * 链接输入与解析。
 *
 * 输入框采用 iOS 搜索框的轻量材质（`fillTertiary` 极淡灰底、无边框），
 * 主操作「开始解析」是本页唯一的液态玻璃控件 —— 与 Dock 同一套
 * 官方折射/高光/流体形变机制；「站内搜索」是行内次要操作，
 * 只用图标与文字表达，不与主按钮争夺权重。
 */
@Composable
private fun HomeParsePanel(
    backdrop: GlassBackdrop,
    value: String,
    onValueChange: (String) -> Unit,
    onParse: () -> Unit,
    onSearch: () -> Unit,
) {
    val colors = ReTheme.colors
    val spacing = ReTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(ReTheme.shapes.rounded(ReTheme.shapes.large))
                    .background(colors.fillTertiary)
                    .padding(horizontal = spacing.md),
                textStyle = ReTheme.typography.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            AppText(
                                "粘贴视频链接 / BV 号…",
                                style = ReTheme.typography.subheadline,
                                color = colors.placeholder,
                            )
                        }
                        inner()
                    }
                },
            )

            LiquidButton(
                onClick = onParse,
                backdrop = backdrop,
                tint = colors.accent,
                tintAlpha = 0.32f,
                buttonHeight = 44.dp,
            ) {
                AppText("开始解析", style = ReTheme.typography.subheadline, color = Color.White)
            }
        }

        // 次要操作：一行安静的搜索入口，不放容器、不放胶囊
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onSearch,
                )
                .padding(horizontal = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            AppIcons.Search(colors.labelTertiary, Modifier.size(15.dp))
            AppText("站内搜索", style = ReTheme.typography.subheadline, color = colors.labelSecondary)
            Spacer(Modifier.weight(1f))
            ChevronIndicator(tint = colors.labelQuaternary)
        }
    }
}

/**
 * 常用入口：2×2 无容器网格。
 *
 * 刻意**不用**"灰圆角方块 + 分隔线 + 右箭头"的旧式列表模板：
 * 每格由 24dp 线性图标、标题与副标题的字号对比和留白自己建立层级。
 * 网格比四行列表少一半纵向占用，也更接近现代 Apple 应用的入口形态。
 */
@Composable
private fun QuickEntryGrid(onOpenRoute: (AppRoute) -> Unit) {
    val spacing = ReTheme.spacing
    val entries = listOf(
        Triple("我的收藏夹", "读取全部收藏夹", AppRoute.MediaList("我的收藏夹")),
        Triple("观看历史", "最近看过的视频", AppRoute.MediaList("观看历史")),
        Triple("稍后再看", "待看清单", AppRoute.MediaList("稍后再看")),
        Triple("UP 主合集", "按 UID 读取投稿", AppRoute.MediaList("UP 主合集")),
    )

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        entries.chunked(2).forEach { rowEntries ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                rowEntries.forEach { (title, subtitle, route) ->
                    QuickEntryCell(
                        title = title,
                        subtitle = subtitle,
                        onClick = { onOpenRoute(route) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 奇数格补位，保持两列对齐
                if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickEntryCell(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReTheme.colors
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
        val icon: @Composable (Color, Modifier) -> Unit = when (title) {
            "我的收藏夹" -> { t, m -> AppIcons.Library(t, m) }
            "观看历史" -> { t, m -> AppIcons.Play(t, m) }
            "稍后再看" -> { t, m -> AppIcons.Download(t, m) }
            else -> { t, m -> AppIcons.Person(t, m) }
        }
        icon(colors.accent, Modifier.size(24.dp))
        AppText(title, style = ReTheme.typography.headline, maxLines = 1)
        AppTextTertiary(subtitle, style = ReTheme.typography.footnote, maxLines = 1)
    }
}

/** 订阅更新：无边框的功能行，箭头极淡 —— 层级靠排版而不是容器。 */
@Composable
private fun SubscriptionRow(onOpenRoute: (AppRoute) -> Unit) {
    val colors = ReTheme.colors
    val spacing = ReTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onOpenRoute(AppRoute.Subscription) },
            )
            .padding(horizontal = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            AppText("订阅更新", style = ReTheme.typography.headline)
            AppTextTertiary(
                "追 UP 主 / 合集 / 收藏夹 / 番剧整季",
                style = ReTheme.typography.footnote,
            )
        }
        ChevronIndicator(tint = colors.labelQuaternary)
    }
}

/**
 * 账号：无边框一行。
 *
 * 未登录时明确写出「登录能得到什么」：B 站在未登录时只会返回低清晰度，
 * 而这是**静默的**（接口返回成功，只是档位变少）。不写清楚，
 * 用户会以为是应用本身的问题。
 */
@Composable
private fun AccountRow(state: AppUiState, onOpenRoute: (AppRoute) -> Unit) {
    val colors = ReTheme.colors
    val spacing = ReTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onOpenRoute(AppRoute.Login) },
            )
            .padding(horizontal = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.fillSecondary),
            contentAlignment = Alignment.Center,
        ) {
            AppIcons.Person(colors.labelSecondary, Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f)) {
            AppText(
                if (state.loggedIn) state.userName else "未登录",
                style = ReTheme.typography.headline,
                maxLines = 1,
            )
            AppTextTertiary(
                if (state.loggedIn) "UID ${state.userUid}" else "登录后可下载 1080P+ / 4K 及会员内容",
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
