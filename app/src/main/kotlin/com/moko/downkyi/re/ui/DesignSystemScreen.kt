package com.moko.downkyi.re.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.component.AppCard
import com.moko.downkyi.re.core.designsystem.component.AppListGroup
import com.moko.downkyi.re.core.designsystem.component.AppListRow
import com.moko.downkyi.re.core.designsystem.component.AppProgressBar
import com.moko.downkyi.re.core.designsystem.component.AppProgressRing
import com.moko.downkyi.re.core.designsystem.component.AppSwitch
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.component.BottomBarItem
import com.moko.downkyi.re.core.designsystem.component.ButtonTone
import com.moko.downkyi.re.core.designsystem.component.GlassBottomBar
import com.moko.downkyi.re.core.designsystem.component.GlassButton
import com.moko.downkyi.re.core.designsystem.component.GlassTopBar
import com.moko.downkyi.re.core.designsystem.component.ListLeadingIcon
import com.moko.downkyi.re.core.designsystem.component.RowTrailingText
import com.moko.downkyi.re.core.designsystem.component.SegmentedRow
import com.moko.downkyi.re.core.designsystem.component.SolidButton
import com.moko.downkyi.re.core.designsystem.component.contentColor
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.moko.downkyi.re.core.designsystem.glass.GlassBackdrop
import com.moko.downkyi.re.core.designsystem.glass.GlassScaffold
import com.moko.downkyi.re.core.designsystem.glass.glassSurface
import com.moko.downkyi.re.core.designsystem.theme.GlassLevel
import com.moko.downkyi.re.core.designsystem.theme.ReDownKyiTheme
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes

/**
 * 设计系统全组件验收页（真机检查表）。
 *
 * 覆盖：玻璃三档、导航栏、标签栏、卡片、列表行、分段控件、
 * 进度条（确定 / 不确定）、环形进度、开关、按钮四态、排版、色彩、主题切换。
 */
@Composable
fun DesignSystemScreen() {
    var dark by remember { mutableStateOf(false) }

    ReDownKyiTheme(darkTheme = dark) {
        GlassScaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(ReTheme.colors.background),

            background = {
                ProbeBackground()
                ProbeScrollContent(dark = dark, onToggleTheme = { dark = !dark })
            },

            overlay = { backdrop ->
                Column(
                    Modifier.align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassTopBar(backdrop = backdrop, title = "设计系统")
                    ProbeGlassChips(backdrop)
                }

                GlassBottomBar(
                    backdrop = backdrop,
                    items = listOf(
                        BottomBarItem("home", "首页"),
                        BottomBarItem("downloads", "下载"),
                        BottomBarItem("library", "媒体库"),
                        BottomBarItem("profile", "我的"),
                    ),
                    selectedIndex = 1,
                    onSelect = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            },
        )
    }
}

/**
 * 采样背景：渐变底 + 彩色光斑 + 高频网格。
 * 网格用于让玻璃的模糊与折射差异可判断。
 */
@Composable
private fun ProbeBackground() {
    // Canvas 的绘制 lambda 不是 Composable 上下文，主题取值必须在此完成
    val isLight = ReTheme.colors.isLight

    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (isLight) {
                    Brush.linearGradient(
                        listOf(Color(0xFFF7E9F0), Color(0xFFE4ECF7), Color(0xFFF3EDE2))
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color(0xFF101322), Color(0xFF1B2440), Color(0xFF2A1730))
                    )
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spots = listOf(
                Triple(Offset(size.width * 0.12f, size.height * 0.14f), size.width * 0.38f, Color(0xFFFB7299)),
                Triple(Offset(size.width * 0.92f, size.height * 0.26f), size.width * 0.34f, Color(0xFF23ADE5)),
                Triple(Offset(size.width * 0.24f, size.height * 0.62f), size.width * 0.42f, Color(0xFFFFA726)),
                Triple(Offset(size.width * 0.86f, size.height * 0.78f), size.width * 0.36f, Color(0xFF8E44AD)),
                Triple(Offset(size.width * 0.44f, size.height * 0.94f), size.width * 0.40f, Color(0xFF2ECC71)),
            )
            val spotAlpha = if (isLight) 0.55f else 0.80f
            spots.forEach { (center, radius, color) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = spotAlpha), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            val lineColor = if (isLight) {
                Color.Black.copy(alpha = 0.07f)
            } else {
                Color.White.copy(alpha = 0.09f)
            }
            val step = 26.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}

@Composable
private fun ProbeScrollContent(dark: Boolean, onToggleTheme: () -> Unit) {
    val spacing = ReTheme.spacing
    val insets = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + Sizes.NavBarHeight + 96.dp,
            bottom = insets.calculateBottomPadding() + Sizes.TabBarHeight + spacing.xxl,
            start = spacing.screenEdge,
            end = spacing.screenEdge,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                AppText("Re：哔哩下载姬", style = ReTheme.typography.largeTitle)
                AppTextSecondary("设计系统全组件验收", style = ReTheme.typography.callout)
                Spacer(Modifier.height(spacing.sm))
                SolidButton(onClick = onToggleTheme) {
                    AppText(
                        if (dark) "切到浅色" else "切到深色",
                        style = ReTheme.typography.subheadline,
                        color = contentColor(),
                    )
                }
            }
        }

        item { SectionLabel("列表与卡片", "不透明容器，不参与玻璃采样") }
        item { ListDemoGroup() }

        item { SectionLabel("分段控件", "选中滑块平滑滑动，二分切换有方向感") }
        item { SegmentedDemo() }

        item { SectionLabel("进度指示", "负值表示进度未知，与 0% 有本质区别") }
        item { ProgressDemo() }

        item { SectionLabel("开关与按钮", "弹簧驱动的反馈，非固定时长") }
        item { ControlDemo() }

        item { SectionLabel("排版阶梯", "字号与字重对应 iOS 层级语义") }
        item { TypographyDemo() }

        item { SectionLabel("色彩令牌", "语义命名，浅色 / 深色各一套") }
        item { ColorDemo() }

        item { SectionLabel("运行环境", "确认玻璃走到了哪一档能力") }
        item { DeviceDemo() }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppText(title, style = ReTheme.typography.title3)
        AppTextTertiary(subtitle)
    }
}

@Composable
private fun ListDemoGroup() {
    AppListGroup {
        AppListRow(
            title = "下载设置",
            subtitle = "清晰度、编码、并发、保存位置",
            leading = { ListLeadingIcon(ReTheme.colors.accent) },
            showChevron = true,
            onClick = {},
        )
        AppListRow(
            title = "同时下载任务数",
            subtitle = "占用网络带宽的总上限",
            trailing = { RowTrailingText("2 个") },
            showChevron = true,
            onClick = {},
        )
        AppListRow(
            title = "下载归档",
            subtitle = "按 BV / 分P / 清晰度 / 编码判定，追更时不重复下载",
            trailing = { RowTrailingText("已开启") },
            showDivider = false,
            onClick = {},
        )
    }
}

@Composable
private fun SegmentedDemo() {
    var tab by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm)) {
        SegmentedRow(
            options = listOf("进行中 (2)", "已完成 (7)"),
            selectedIndex = tab,
            onSelect = { tab = it },
        )
        AppTextSecondary(
            if (tab == 0) "当前查看：进行中的任务" else "当前查看：已完成的任务"
        )
    }
}

@Composable
private fun ProgressDemo() {
    val spacing = ReTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            AppTextSecondary("确定进度 · 62%")
            AppProgressBar(progress = 0.62f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            AppTextSecondary("不确定进度 · 正在解析播放地址")
            AppProgressBar(progress = -1f)
        }

        Spacer(Modifier.height(spacing.xxs))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppProgressRing(progress = 0.73f) {
                    AppText("73", style = ReTheme.typography.caption2)
                }
                Spacer(Modifier.height(4.dp))
                AppTextTertiary("确定", style = ReTheme.typography.caption2)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppProgressRing(progress = -1f)
                Spacer(Modifier.height(4.dp))
                AppTextTertiary("未知", style = ReTheme.typography.caption2)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppProgressRing(progress = 1f, color = ReTheme.colors.success) {
                    AppText("✓", style = ReTheme.typography.caption1)
                }
                Spacer(Modifier.height(4.dp))
                AppTextTertiary("完成", style = ReTheme.typography.caption2)
            }
        }
    }
}

@Composable
private fun ControlDemo() {
    val spacing = ReTheme.spacing
    var switchA by remember { mutableStateOf(true) }
    var switchB by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        AppListGroup {
            AppListRow(
                title = "仅 Wi-Fi 下载",
                trailing = { AppSwitch(checked = switchA, onCheckedChange = { switchA = it }) },
                showDivider = true,
            )
            AppListRow(
                title = "写入标题与封面",
                subtitle = "部分机型对杜比视界片源可能不兼容",
                trailing = { AppSwitch(checked = switchB, onCheckedChange = { switchB = it }) },
                showDivider = false,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            SolidButton(onClick = {}) {
                AppText("主要", style = ReTheme.typography.subheadline, color = contentColor())
            }
            SolidButton(onClick = {}, tone = ButtonTone.Secondary) {
                AppText("次要", style = ReTheme.typography.subheadline, color = contentColor())
            }
            SolidButton(onClick = {}, tone = ButtonTone.Danger) {
                AppText("删除", style = ReTheme.typography.subheadline, color = contentColor())
            }
            SolidButton(onClick = {}, enabled = false) {
                AppText("禁用", style = ReTheme.typography.subheadline, color = contentColor())
            }
        }
    }
}

@Composable
private fun TypographyDemo() {
    val typography = ReTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.xs)) {
        AppText("largeTitle · 34", style = typography.largeTitle)
        AppText("title1 · 28", style = typography.title1)
        AppText("title2 · 22", style = typography.title2)
        AppText("title3 · 20", style = typography.title3)
        AppText("headline · 17 半粗", style = typography.headline)
        AppText("body · 17 常规", style = typography.body)
        AppText("subheadline · 15", style = typography.subheadline)
        AppText("footnote · 13", style = typography.footnote)
        AppText("caption1 · 12", style = typography.caption1)
        AppText("caption2 · 11", style = typography.caption2)
        Spacer(Modifier.height(ReTheme.spacing.xs))
        AppTextTertiary("等宽数字：进度与速度必须用它，否则刷新时会左右抖动")
        Row(horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.md)) {
            AppText("128.4 MB/s", style = typography.numeric)
            AppText("73%", style = typography.numericLarge, color = ReTheme.colors.accent)
        }
    }
}

@Composable
private fun ColorDemo() {
    val colors = ReTheme.colors
    val spacing = ReTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        SwatchRow("accent · 品牌粉", colors.accent)
        SwatchRow("success", colors.success)
        SwatchRow("warning", colors.warning)
        SwatchRow("danger", colors.danger)
        SwatchRow("info", colors.info)
        Spacer(Modifier.height(spacing.xxs))
        AppText("label — 主文字", style = ReTheme.typography.subheadline)
        AppTextSecondary("labelSecondary — 次要文字")
        AppTextTertiary("labelTertiary — 辅助说明")
        AppText(
            "labelQuaternary — 最弱层级",
            style = ReTheme.typography.footnote,
            color = colors.labelQuaternary,
        )
    }
}

@Composable
private fun SwatchRow(name: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(color, CircleShape)
        )
        AppTextSecondary(name, style = ReTheme.typography.subheadline)
    }
}

@Composable
private fun DeviceDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.xs)) {
        InfoRow("Android", "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        InfoRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow("玻璃渲染", if (ReTheme.glass.supported) "完整（折射 + 色散）" else "降级（不透明填充）")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppTextSecondary(label)
        AppText(value, style = ReTheme.typography.subheadline)
    }
}

// ---------------------------------------------------------------------------
// 玻璃层（overlay）：位于采样层之外
// ---------------------------------------------------------------------------

/**
 * 三档材质对照 + 玻璃按钮。
 *
 * 悬浮在内容之上既是结构要求（玻璃不能位于被采样内容内部），
 * 也是 iOS 的真实用法：液态玻璃只出现在悬浮层。
 */
@Composable
private fun ProbeGlassChips(backdrop: GlassBackdrop) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassLevelChip(backdrop, GlassLevel.Thin, "薄")
            GlassLevelChip(backdrop, GlassLevel.Regular, "标准")
            GlassLevelChip(backdrop, GlassLevel.Thick, "厚")
        }
        GlassButton(backdrop = backdrop, onClick = {}) {
            AppText("玻璃按钮 · 按压有缩放反馈", style = ReTheme.typography.footnote)
        }

        // ── 原生 API 对照块 ────────────────────────────────────────────
        // 完全绕过应用的玻璃封装，直接调用 backdrop 的 drawBackdrop。
        // 用途：把「我们的封装写错了」与「库本身不工作」两种可能分开。
        // 验证结束后应当删除。
        // shape lambda 不是 Composable 上下文，形状要先在这里算好
        val capsuleShape = RoundedCornerShape(ReTheme.shapes.capsule)

        // 对照 A：带模糊。若模糊生效，块内的网格会被糊掉
        Box(
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = { blur(60.dp.toPx()) },
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            AppText("A · blur 60dp", style = ReTheme.typography.footnote)
        }

        // 对照 B：完全不加效果。它应当与背景融为一体（看不到块）
        Box(
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = { },
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            AppText("B · 无效果", style = ReTheme.typography.footnote)
        }
    }
}

@Composable
private fun GlassLevelChip(
    backdrop: GlassBackdrop,
    level: GlassLevel,
    label: String,
) {
    val glass = ReTheme.glass
    Box(
        Modifier
            .glassSurface(
                backdrop = backdrop,
                style = glass.styleOf(level),
                shape = RoundedCornerShape(ReTheme.shapes.capsule),
                supported = glass.supported,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        AppText(label, style = ReTheme.typography.footnote)
    }
}
