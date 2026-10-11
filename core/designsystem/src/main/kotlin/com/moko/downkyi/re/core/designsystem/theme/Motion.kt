package com.moko.downkyi.re.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * 动效规范。
 *
 * 核心原则是**用弹簧而不是固定时长**：iOS 的交互反馈几乎全部是弹簧动画，
 * 它能在被打断时保留速度（例如快速连点、中途反向拖拽），
 * 而固定时长的 tween 被打断时会突兀地重置。列表与手势反馈一律用弹簧。
 *
 * 只有与帧率无关的纯视觉变化（淡入淡出、颜色过渡）才用 tween。
 */
object Motion {

    // ---- 弹簧 ----

    /**
     * 标准弹簧。用于绝大多数的位移与缩放：
     * 按钮按下、卡片展开、列表项出现。
     *
     * 阻尼比 0.85 接近 iOS 的手感 —— 有轻微回弹但不过冲，
     * 低于 0.7 会明显"弹"，高于 0.95 就显得呆板。
     */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * 轻快弹簧。用于高频、小位移的反馈：按下态、勾选框。
     * 刚度更高，收得更快，避免连续操作时反馈滞后。
     */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * 柔性弹簧。用于大面积、大位移的转场：底部面板升起、整页切换。
     * 刚度低，动作更从容。
     */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessLow,
    )

    /** 位置动画的专用弹簧（IntOffset 有独立重载）。 */
    fun offset(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ---- 缓动曲线 ----

    /**
     * iOS 风格的标准缓动。
     * 前段快、后段缓慢收尾，是界面元素进出最常用的曲线。
     */
    val StandardEasing: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /**
     * 强调缓动。用于需要吸引注意的变化（弹窗出现、错误提示）。
     * 起步更慢、收尾更缓，观感更"重"。
     */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 减速缓动。用于元素进入（从屏外滑入）。 */
    val DecelerateEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** 加速缓动。用于元素离开（滑出屏外）。 */
    val AccelerateEasing: Easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

    // ---- 时长 ----

    /** 120ms。微反馈：颜色、透明度。 */
    const val DurationInstant = 120

    /** 200ms。小元素进退场。 */
    const val DurationFast = 200

    /** 300ms。标准转场。 */
    const val DurationStandard = 300

    /** 420ms。大面积转场：页面切换、面板弹起。 */
    const val DurationEmphasized = 420

    /** 淡入淡出。 */
    fun <T> fade(durationMillis: Int = DurationFast): TweenSpec<T> =
        TweenSpec(durationMillis = durationMillis, easing = StandardEasing)

    /** 缓慢淡入淡出，用于大面积的背景变化。 */
    fun <T> fadeSlow(): TweenSpec<T> =
        TweenSpec(durationMillis = DurationStandard, easing = StandardEasing)

    // ---- 交互反馈参数 ----

    /**
     * 按下时的缩放比例。
     *
     * 0.96 是 iOS 上按钮与卡片的常见幅度。幅度过小（>0.98）察觉不到，
     * 过大（<0.92）会显得廉价、像网页的悬停效果。
     */
    const val PressScale = 0.96f

    /** 按下时的亮度叠加（浅色主题下压暗、深色主题下提亮由调用方决定）。 */
    const val PressOverlayAlpha = 0.08f
}
