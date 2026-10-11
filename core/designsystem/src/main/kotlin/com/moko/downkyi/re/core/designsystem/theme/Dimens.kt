package com.moko.downkyi.re.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距节奏。
 *
 * 采用 4dp 基准网格：所有间距都是 4 的倍数，这样任意两个元素并排时
 * 视觉基线都能对齐。奇数间距（如 5dp、7dp）会让界面"看起来差一点"
 * 却说不出哪里不对，通常就是网格没对齐导致的。
 *
 * 命名按语义而非数值（[screenEdge] 而不是 [s16]），
 * 这样调整规范时只改一处，调用方的意图也不会失真。
 */
@Immutable
data class ReDownKyiSpacing(
    /** 2dp。贴边微调：图标与文字之间的紧贴。 */
    val hairline: Dp = 2.dp,
    /** 4dp。最小间距：徽标与文字。 */
    val xxs: Dp = 4.dp,
    /** 8dp。紧凑间距：图标与标签。 */
    val xs: Dp = 8.dp,
    /** 12dp。列表项内部纵向间距。 */
    val sm: Dp = 12.dp,
    /** 16dp。标准间距：屏幕左右边距、卡片内边距。 */
    val md: Dp = 16.dp,
    /** 20dp。宽松间距：卡片之间。 */
    val lg: Dp = 20.dp,
    /** 24dp。区块之间。 */
    val xl: Dp = 24.dp,
    /** 32dp。大区块之间：页面顶部留白。 */
    val xxl: Dp = 32.dp,
    /** 44dp。章节之间。 */
    val xxxl: Dp = 44.dp,
) {
    /** 屏幕左右安全边距。 */
    val screenEdge: Dp get() = md

    /** 卡片内部边距。 */
    val cardPadding: Dp get() = md

    /** 列表项左右边距。 */
    val listRowHorizontal: Dp get() = md

    /** 列表项上下边距。 */
    val listRowVertical: Dp get() = sm

    /**
     * 最小可点击尺寸。
     *
     * 48dp 是可访问性下限：低于这个值，手指按压会频繁点错。
     * 视觉上可以更小，但点击热区必须撑到这个尺寸。
     */
    val minTouchTarget: Dp get() = 48.dp
}

/**
 * 尺寸常量（不属于间距节奏，语义独立的固定值）。
 */
object Sizes {
    /** 底部标签栏高度（不含系统手势区）。 */
    val TabBarHeight = 56.dp

    /** 导航栏高度（不含状态栏）。 */
    val NavBarHeight = 52.dp

    /**
     * 悬浮 Dock 的容器高度。
     *
     * 它是**悬浮**控件，盖在内容上方 —— 每个滚动容器都必须为它预留底部
     * 空间，否则滚动到底时最后一行会被 Dock 压住。预留量见
     * [DockContentClearance]。
     */
    val DockContainerHeight = 64.dp

    /**
     * 滚动容器为 Dock 预留的底部净空（**不含**系统导航栏 inset）。
     *
     * 构成：容器高 64 + 容器与导航栏的间隙 8 + 内容与 Dock 之间的呼吸 16。
     * 调用方再自行加上 `WindowInsets.navigationBars` 的底部值。
     * 之前各页各自写 76dp 一类 magic number，Dock 一改尺寸就会出现
     * "最后一行被压住一半"这种只在某个页面出现的遮挡。
     */
    val DockContentClearance = 88.dp

    /** 列表缩略图尺寸（视频卡片封面，16:9）。 */
    val ThumbnailHeight = 62.dp
    val ThumbnailWidth = 110.dp

    /** 圆形头像尺寸。 */
    val Avatar = 56.dp
    val AvatarSmall = 32.dp

    /** 行内图标尺寸。 */
    val IconInline = 18.dp
    val IconSmall = 20.dp
    val IconMedium = 24.dp
    val IconLarge = 28.dp

    /** 进度条高度。 */
    val ProgressBarHeight = 4.dp

    /** 发丝线宽度：分隔线用物理像素级细线，视觉上才"不抢戏"。 */
    val HairlineWidth = 0.5.dp
}

val DefaultSpacing = ReDownKyiSpacing()

val LocalReDownKyiSpacing = staticCompositionLocalOf { DefaultSpacing }
