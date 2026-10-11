package com.moko.downkyi.re.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一档玻璃材质的全部参数。
 *
 * 这些参数直接对应底层渲染能力的四个光学层，缺一层就会退化成"磨砂卡片"：
 *
 *  - [refractionHeight] / [refractionAmount] —— **折射**，液态玻璃的灵魂。
 *    前者是玻璃边缘折射带的厚度（玻璃看起来有多厚），后者是背景被推移的
 *    距离（形变有多强）。这两个值一旦为 0，玻璃就只剩模糊和填充，
 *    与普通磨砂玻璃没有区别 —— 这正是本项目早期实现最大的问题。
 *  - [blurRadius] —— 模糊，只负责让背景细节柔化，不是主角。
 *  - [highlightAlpha] / [shadowAlpha] / [innerShadowRadius] —— 高光、投影、
 *    内阴影，共同构成"这块玻璃有厚度"的立体感。
 *  - [surface] —— 表面色，通过 onDrawSurface 叠在折射之上。
 *    因为有真折射托底，它可以很透（40% 左右），不像纯填充方案那样
 *    必须做到 90% 才压得住背景。
 */
@Immutable
data class GlassStyle(
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    /**
     * 色散：玻璃边缘把白光拆成彩边。
     *
     * 只在较小的元素上开启（胶囊按钮、标签栏的选中指示器）。
     * 大面板开启会显得脏，还会增加 shader 开销。
     */
    val chromaticAberration: Boolean,
    /** 深度效果：让折射看起来像有真实厚度的透镜而非二维偏移。 */
    val depthEffect: Boolean,
    /** 表面色，叠在折射层之上。 */
    val surface: Color,
    val highlightAlpha: Float,
    val shadowAlpha: Float,
    val innerShadowRadius: Dp,
    val innerShadowAlpha: Float,
)

/**
 * 玻璃材质档位。
 *
 * 分档依据是**元素在界面里的层级**，而不是"想调多花哨"：
 * 越靠上层、越独立的元素，玻璃越"厚"（折射更强、表面色更实）。
 */
enum class GlassLevel {
    /** 薄：贴边的导航栏、标签栏、小胶囊按钮。 */
    Thin,

    /** 标准：输入面板、内容卡片、悬浮工具条。 */
    Regular,

    /** 厚：底部面板、弹窗、需要与背景强分离的浮层。 */
    Thick,
}

@Immutable
data class GlassTheme(
    val thin: GlassStyle,
    val regular: GlassStyle,
    val thick: GlassStyle,
    /** 是否支持真正的折射渲染（需要 RuntimeShader，即 Android 13+）。 */
    val supported: Boolean,
) {
    fun styleOf(level: GlassLevel): GlassStyle = when (level) {
        GlassLevel.Thin -> thin
        GlassLevel.Regular -> regular
        GlassLevel.Thick -> thick
    }
}

// ---------------------------------------------------------------------------
// 参数标定说明
//
// 参考实现：AndroidLiquidGlass 官方示例的 LiquidBottomTabs ——
//   effects = { vibrancy(); blur(8.dp); lens(24.dp, 24.dp) }
//   onDrawSurface = { drawRect(containerColor) }   // containerColor 仅 40% 不透明
//
// 关键结论：官方示例的表面色只有 **40%** 不透明度，却依然清晰可读。
// 原因就是折射真的在工作 —— 它把背景推移、压缩进玻璃内部，
// 而不是让背景原样透过来，所以不需要靠高不透明填充去"盖住"背景。
//
// 项目早期版本把折射设为 0、把填充提到 92%，实际得到的是"几乎不透明的
// 白条"，这正是用户看到的"普通圆角卡片"。现在按官方比例重新标定。
// ---------------------------------------------------------------------------

/** 浅色主题下的玻璃参数。 */
val LightGlassTheme: GlassTheme = lightGlassTheme()

/** 深色主题下的玻璃参数。 */
val DarkGlassTheme: GlassTheme = darkGlassTheme()

/** 当前生效的玻璃参数。 */
val LocalGlassTheme = staticCompositionLocalOf { LightGlassTheme }

internal fun lightGlassTheme(): GlassTheme = GlassTheme(
    thin = GlassStyle(
        blurRadius = 8.dp,
        refractionHeight = 18.dp,
        refractionAmount = 24.dp,
        chromaticAberration = true,
        depthEffect = true,
        // 40% 白。不必更高 —— 折射与高光已经建立了层次
        surface = Color(0x66FFFFFF),
        highlightAlpha = 0.75f,
        shadowAlpha = 0.10f,
        innerShadowRadius = 6.dp,
        innerShadowAlpha = 0.16f,
    ),
    regular = GlassStyle(
        blurRadius = 12.dp,
        refractionHeight = 16.dp,
        refractionAmount = 20.dp,
        // 大面积元素不色散：彩色边缘会显脏
        chromaticAberration = false,
        depthEffect = true,
        surface = Color(0x73FFFFFF),
        highlightAlpha = 0.65f,
        shadowAlpha = 0.12f,
        innerShadowRadius = 8.dp,
        innerShadowAlpha = 0.14f,
    ),
    thick = GlassStyle(
        blurRadius = 18.dp,
        refractionHeight = 20.dp,
        refractionAmount = 26.dp,
        chromaticAberration = false,
        depthEffect = true,
        surface = Color(0x8CFFFFFF),
        highlightAlpha = 0.55f,
        shadowAlpha = 0.20f,
        innerShadowRadius = 10.dp,
        innerShadowAlpha = 0.12f,
    ),
    supported = true,
)

internal fun darkGlassTheme(): GlassTheme = GlassTheme(
    thin = GlassStyle(
        blurRadius = 8.dp,
        refractionHeight = 18.dp,
        refractionAmount = 24.dp,
        chromaticAberration = true,
        depthEffect = true,
        // 深色下表面色要比背景"亮"一点，玻璃才有存在感
        surface = Color(0x40FFFFFF),
        highlightAlpha = 0.50f,
        shadowAlpha = 0.30f,
        innerShadowRadius = 6.dp,
        innerShadowAlpha = 0.20f,
    ),
    regular = GlassStyle(
        blurRadius = 12.dp,
        refractionHeight = 16.dp,
        refractionAmount = 20.dp,
        chromaticAberration = false,
        depthEffect = true,
        surface = Color(0x4DFFFFFF),
        highlightAlpha = 0.42f,
        shadowAlpha = 0.32f,
        innerShadowRadius = 8.dp,
        innerShadowAlpha = 0.18f,
    ),
    thick = GlassStyle(
        blurRadius = 18.dp,
        refractionHeight = 20.dp,
        refractionAmount = 26.dp,
        chromaticAberration = false,
        depthEffect = true,
        surface = Color(0x5CFFFFFF),
        highlightAlpha = 0.35f,
        shadowAlpha = 0.40f,
        innerShadowRadius = 10.dp,
        innerShadowAlpha = 0.16f,
    ),
    supported = true,
)
