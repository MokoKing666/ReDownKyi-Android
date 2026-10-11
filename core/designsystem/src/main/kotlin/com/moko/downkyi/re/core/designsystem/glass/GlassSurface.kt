package com.moko.downkyi.re.core.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.moko.downkyi.re.core.designsystem.theme.GlassLevel
import com.moko.downkyi.re.core.designsystem.theme.GlassStyle
import com.moko.downkyi.re.core.designsystem.theme.ReTheme

// ---------------------------------------------------------------------------
// 类型别名：界面层只说"玻璃"，不提具体渲染库。
//
// 底层用的是 backdrop（AndroidLiquidGlass，Apache-2.0）。将来若更换渲染方案，
// 只需要改这里的别名与本文件的实现，全部调用点不受影响。
// ---------------------------------------------------------------------------

/** 玻璃的采样源：玻璃面通过它读取"背后画了什么"。 */
typealias GlassBackdrop = Backdrop

/** 可作为采样源的背景层。 */
typealias GlassBackdropSource = LayerBackdrop

/**
 * 创建一个背景采样层。
 *
 * 用法是把页面内容挂到 [Modifier.glassBackdropSource] 上，再把返回的对象
 * 传给各玻璃面。玻璃采样是**显式**的 —— 这正是 iOS 液态玻璃的工作方式，
 * 也是它不会像"全屏模糊"那样把整个界面糊掉的原因。
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdropSource = rememberLayerBackdrop()

/** 把当前内容登记为玻璃的采样源。通常挂在页面最底层的背景容器上。 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdropSource): Modifier =
    this.layerBackdrop(backdrop)

/**
 * 让当前元素呈现液态玻璃材质。
 *
 * 渲染分四层，缺任何一层都会明显退化：
 *
 *  1. **折射**（`lens`）—— 把背后的内容按玻璃厚度推移、压缩。
 *     这是"液态玻璃"与"磨砂玻璃"的分界线，没有它，剩下的只是半透明色块。
 *  2. **模糊 + 提饱和**（`blur` / `vibrancy`）—— 柔化背景细节，避免折射后
 *     的高频纹理变得刺眼。
 *  3. **高光 / 投影 / 内阴影** —— 建立"厚度"。高光模拟光源在边缘的反射，
 *     内阴影压出玻璃内侧的暗部，投影把玻璃从背景里抬起来。
 *  4. **表面色**（`onDrawSurface`）—— 很淡的一层色调，让玻璃带上品牌色，
 *     但绝不用它去遮盖背景。
 *
 * 早期实现只做了第 2、4 层（且第 4 层用到 92% 不透明），
 * 结果就是"白色圆角卡片"，这是本项目走过的一段弯路，注释保留以示警醒。
 *
 * [supported] 为 false 时退化为不透明表面色 —— 界面依然协调，只是不再有光学效果。
 */
@Composable
fun Modifier.glassSurface(
    backdrop: GlassBackdrop,
    style: GlassStyle,
    shape: Shape,
    supported: Boolean = true,
): Modifier {
    if (!supported) {
        return this.background(style.surface, shape)
    }

    // 单位换算在 Composable 上下文用 LocalDensity 完成，
    // 不依赖玻璃效果作用域自身的 Density
    val density = LocalDensity.current
    val blurPx = with(density) { style.blurRadius.toPx() }
    val refractionHeightPx = with(density) { style.refractionHeight.toPx() }
    val refractionAmountPx = with(density) { style.refractionAmount.toPx() }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()

            if (blurPx > 0f) blur(blurPx)

            // 折射必须放在模糊之后：lens 会回收 blur 预留的采样边界，
            // 顺序颠倒会得到错误的边缘。
            if (refractionHeightPx > 0f && refractionAmountPx > 0f) {
                lens(
                    refractionHeight = refractionHeightPx,
                    refractionAmount = refractionAmountPx,
                    depthEffect = style.depthEffect,
                    chromaticAberration = style.chromaticAberration,
                )
            }
        },
        highlight = { Highlight.Default.copy(alpha = style.highlightAlpha) },
        shadow = { Shadow(alpha = style.shadowAlpha) },
        innerShadow = {
            InnerShadow(radius = style.innerShadowRadius, alpha = style.innerShadowAlpha)
        },
        onDrawSurface = { drawRect(style.surface) },
    )
}

/**
 * 玻璃容器。
 *
 * 界面层最常用的入口：给一个背景采样源和层级，它负责剩下的一切。
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Regular,
    shape: Shape = RoundedCornerShape(ReTheme.shapes.large),
    contentPadding: PaddingValues = PaddingValues(ReTheme.spacing.cardPadding),
    content: @Composable BoxScope.() -> Unit,
) {
    val glassTheme = ReTheme.glass
    Box(
        modifier
            .glassSurface(
                backdrop = backdrop,
                style = glassTheme.styleOf(level),
                shape = shape,
                supported = glassTheme.supported,
            )
            .padding(contentPadding),
        content = content,
    )
}


