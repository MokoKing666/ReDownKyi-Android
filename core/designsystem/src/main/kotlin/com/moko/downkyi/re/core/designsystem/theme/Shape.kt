package com.moko.downkyi.re.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle

/**
 * 圆角阶梯。
 *
 * 数值对标 iOS 的实际控件尺度，而不是"越大越 Apple"：
 *
 *  - **分组列表（inset grouped）在 iOS 上是 10pt 圆角** —— 这是出现频率
 *    最高的容器圆角，对应 [grouped]；
 *  - 卡片、面板略大，但 18–24dp 的"大圆角"会让普通内容容器看起来
 *    像玩具或聊天气泡，这正是此前"圆角容器泛滥"观感的来源之一；
 *  - 胶囊只属于胶囊形控件（分段、开关、药丸按钮），不是万能圆角。
 *
 * 层级关系：越大的容器用越大的圆角。卡片套卡片时内层按 [nested] 降档，
 * 保持圆角同心 —— 这是 iOS 的隐含规则。
 *
 * 注：这里用的是标准圆角而非连续圆角（squircle）。两者在大圆角下
 * 有可见差异，连续圆角需要额外引入形状库；当前先用标准圆角把体系
 * 建立起来，形状升级作为独立的优化项，不影响任何调用方。
 */
@Immutable
data class ReDownKyiShapes(
    /** 8dp。最小元件：徽标、小标签、行内图标底。 */
    val extraSmall: Dp = 8.dp,
    /** 10dp。小元件：输入框、小按钮、嵌套区块。 */
    val small: Dp = 10.dp,
    /** 12dp。分组列表容器（inset grouped 的标准圆角）。 */
    val medium: Dp = 12.dp,
    /** 16dp。面板：输入区、主要浮动面板。 */
    val large: Dp = 16.dp,
    /** 20dp。底部面板顶部、页面级容器。 */
    val extraLarge: Dp = 20.dp,
    /** 24dp。弹窗。 */
    val dialog: Dp = 24.dp,
) {
    /** 胶囊：仅用于胶囊形控件（分段控件、开关、药丸按钮）。 */
    val capsule: Dp get() = 100.dp

    /**
     * 嵌套一层的圆角。
     *
     * 外层圆角减去内层内边距，是 iOS 保持"圆角同心"的做法：
     * 18dp 的卡片里放一个 8dp 内边距的子区块，子区块圆角取 10dp 才同心。
     * 直接减到负数时归零（矩形），不会产生异常形状。
     */
    fun nested(outer: Dp, inset: Dp): Dp {
        val result = outer - inset
        return if (result < 0.dp) 0.dp else result
    }
}

/** 生成 Compose 的圆角形状。 */
fun ReDownKyiShapes.toRoundedCornerShape(radius: Dp): RoundedCornerShape =
    RoundedCornerShape(radius)

/**
 * 生成**连续曲率**的圆角形状（iOS 27 的 R 观感）。
 *
 * 标准圆角的曲率在拐点处是突变的，肉眼能感到"圆是后接上去的"；
 * iOS 的圆角曲率连续渐变，看起来圆润一体。本工程已经依赖官方
 * AndroidLiquidGlass 配套的 kyant/shapes 库，其 [RoundedRectangle]
 * 正是连续圆角实现 —— 官方所有玻璃容器都在用它。
 *
 * 玻璃面板、内容容器一律走这个入口；普通非玻璃元件可以用
 * [toRoundedCornerShape]，但混用时同屏的 R 观感会不一致，尽量统一。
 */
fun ReDownKyiShapes.rounded(radius: Dp): Shape = RoundedRectangle(radius)

val DefaultShapes = ReDownKyiShapes()

val LocalReDownKyiShapes = staticCompositionLocalOf { DefaultShapes }
