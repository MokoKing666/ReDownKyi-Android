package com.moko.downkyi.re.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用语义色板。
 *
 * 分层参照 iOS 的语义色体系，而不是照搬 Material 的 primary/onPrimary 命名：
 *
 *  - **背景三级递进**：页面底 → 分组底 → 卡片。iOS 用这套层级表达"内容归属"，
 *    同一屏上出现三层以上时视觉会开始发灰，所以只提供三级。
 *  - **文字四级不透明度**：label / secondary / tertiary / quaternary。
 *    直接用不透明度而不是调好的灰色，是为了让文字在任何背景（含玻璃）上
 *    都能保持同样的层级关系。
 *  - **玻璃填充色单列**：玻璃材质本身是半透明的，它上方的"表面色"必须
 *    与不透明背景色分开定义，否则浅色模式下玻璃会比背景更白、显得脏。
 *
 * 颜色值取自 iOS 系统语义色，强调色保留原项目的哔哩哔哩粉以维持品牌识别。
 */
@Immutable
data class ReDownKyiColors(
    // ---- 背景层级 ----
    /** 页面底色。 */
    val background: Color,
    /** 分组容器底色（列表所在的底）。 */
    val backgroundGrouped: Color,
    /** 卡片底色。 */
    val backgroundElevated: Color,
    /** 卡片底色（次级，用于卡片内再嵌套的区块）。 */
    val backgroundElevatedSecondary: Color,

    // ---- 玻璃表面填充 ----
    /** 薄玻璃的填充色（导航栏、悬浮工具条）。 */
    val glassThin: Color,
    /** 标准玻璃的填充色（卡片、面板）。 */
    val glassRegular: Color,
    /** 厚玻璃的填充色（弹窗、底部面板，需要更强遮蔽性以保证可读）。 */
    val glassThick: Color,
    /** 玻璃边缘高光的颜色。 */
    val glassStroke: Color,

    // ---- 文字层级 ----
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelQuaternary: Color,
    /** 输入框占位符，比 quaternary 再淡一档。 */
    val placeholder: Color,

    // ---- 分隔与描边 ----
    /** 发丝分隔线（列表项之间）。 */
    val separator: Color,
    /** 不透明分隔线，用于玻璃等半透明背景之上。 */
    val separatorOpaque: Color,
    /** 普通描边（输入框、按钮轮廓）。 */
    val stroke: Color,

    // ---- 交互填充 ----
    /** 主填充（不透明按钮、选中态底色）。 */
    val fillPrimary: Color,
    /** 次级填充（标签底、hover 态）。 */
    val fillSecondary: Color,
    /** 三级填充（最轻的按压反馈底色）。 */
    val fillTertiary: Color,

    // ---- 强调色 ----
    /** 品牌强调色（哔哩哔哩粉）。 */
    val accent: Color,
    /** 强调色按下态。 */
    val accentPressed: Color,
    /** 强调色的柔和底（标签、徽标底）。 */
    val accentSoft: Color,
    /** 强调色之上的文字色。 */
    val onAccent: Color,

    // ---- 状态语义色 ----
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val successSoft: Color,
    val warningSoft: Color,
    val dangerSoft: Color,
    val infoSoft: Color,

    /** 是否为浅色主题。玻璃材质的参数需要据此调整。 */
    val isLight: Boolean,
)

/**
 * 品牌强调色：哔哩哔哩粉。
 *
 * 深色模式下提亮到 #FF8CB0 —— 直接沿用 #FB7299 在深色背景上会显得发闷、
 * 且与深色系统色的对比度不足。
 */
private val BrandPinkLight = Color(0xFFFB7299)
private val BrandPinkDark = Color(0xFFFF8CB0)
private val BrandPinkPressedLight = Color(0xFFE05C82)
private val BrandPinkPressedDark = Color(0xFFE87A9C)

/** 浅色主题色板：iOS Light 语义色 + 品牌粉。 */
val LightColors = ReDownKyiColors(
    background = Color(0xFFFFFFFF),
    backgroundGrouped = Color(0xFFF2F2F7),
    backgroundElevated = Color(0xFFFFFFFF),
    backgroundElevatedSecondary = Color(0xFFF2F2F7),

    glassThin = Color(0x66FFFFFF),
    glassRegular = Color(0x80FFFFFF),
    glassThick = Color(0xE6F7F7FA),
    glassStroke = Color(0x99FFFFFF),

    label = Color(0xFF000000),
    labelSecondary = Color(0x993C3C43),
    labelTertiary = Color(0x4D3C3C43),
    labelQuaternary = Color(0x2E3C3C43),
    placeholder = Color(0x2E3C3C43),

    separator = Color(0x493C3C43),
    separatorOpaque = Color(0xFFC6C6C8),
    stroke = Color(0x1F000000),

    fillPrimary = Color(0xFF787880),
    fillSecondary = Color(0x1F767680),
    fillTertiary = Color(0x14747480),

    accent = BrandPinkLight,
    accentPressed = BrandPinkPressedLight,
    accentSoft = Color(0x1AFB7299),
    onAccent = Color(0xFFFFFFFF),

    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    danger = Color(0xFFFF3B30),
    info = Color(0xFF007AFF),
    successSoft = Color(0x1A34C759),
    warningSoft = Color(0x1AFF9500),
    dangerSoft = Color(0x1AFF3B30),
    infoSoft = Color(0x1A007AFF),

    isLight = true,
)

/** 深色主题色板：iOS Dark 语义色 + 提亮后的品牌粉。 */
val DarkColors = ReDownKyiColors(
    background = Color(0xFF000000),
    backgroundGrouped = Color(0xFF000000),
    backgroundElevated = Color(0xFF1C1C1E),
    backgroundElevatedSecondary = Color(0xFF2C2C2E),

    glassThin = Color(0x331F1F21),
    glassRegular = Color(0x591C1C1E),
    glassThick = Color(0xE6272729),
    glassStroke = Color(0x33FFFFFF),

    label = Color(0xFFFFFFFF),
    labelSecondary = Color(0x99EBEBF5),
    labelTertiary = Color(0x4DEBEBF5),
    labelQuaternary = Color(0x2EEBEBF5),
    placeholder = Color(0x2EEBEBF5),

    separator = Color(0x99545458),
    separatorOpaque = Color(0xFF38383A),
    stroke = Color(0x33FFFFFF),

    fillPrimary = Color(0xFF787880),
    fillSecondary = Color(0x3D767680),
    fillTertiary = Color(0x28747480),

    accent = BrandPinkDark,
    accentPressed = BrandPinkPressedDark,
    accentSoft = Color(0x33FF8CB0),
    onAccent = Color(0xFF1C1C1E),

    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFFF453A),
    info = Color(0xFF0A84FF),
    successSoft = Color(0x3330D158),
    warningSoft = Color(0x33FF9F0A),
    dangerSoft = Color(0x33FF453A),
    infoSoft = Color(0x330A84FF),

    isLight = false,
)

/**
 * 色板的 CompositionLocal。
 *
 * 用 [staticCompositionLocalOf] 而不是 compositionLocalOf：主题切换是全量重建，
 * 不需要细粒度追踪读取者，静态版本的开销更低。
 */
val LocalReDownKyiColors = staticCompositionLocalOf<ReDownKyiColors> {
    error("ReDownKyiColors 未提供。请确认界面位于 ReDownKyiTheme 之内。")
}
