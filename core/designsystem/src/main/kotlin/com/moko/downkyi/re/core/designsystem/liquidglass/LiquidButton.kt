/*
 * 移植自 AndroidLiquidGlass
 *   https://github.com/Kyant0/AndroidLiquidGlass
 *   源文件：app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt
 *
 * Copyright (c) Kyant0
 * Licensed under the Apache License, Version 2.0
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * 改动：
 *   1. 包名；
 *   2. com.kyant.shapes.Capsule 换成可传入的 shape（默认 50% 圆角胶囊）——
 *      Capsule 所属的 kyant.shapes 是 backdrop 的 implementation 依赖，
 *      不传递到本工程；
 *   3. 高度与水平内边距参数化。
 * 玻璃绘制、流体形变与染色逻辑未作修改。
 */
package com.moko.downkyi.re.core.designsystem.liquidglass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 官方的液态玻璃按钮。
 *
 * 它比"半透明胶囊 + 按下缩放"多做了两件关键的事：
 *
 *  1. **流体形变**（[layerBlock]）。按下时不仅整体放大，还会沿手指移动的
 *     方向做**各向异性**拉伸：横着滑就横向淌开、竖着滑就纵向压扁，
 *     位移本身还被 `tanh` 阻尼，越推越沉。这是"液体"与"有弹性的塑料"
 *     手感上的分界。
 *
 *  2. **色相染色**（[tint]）。用 `BlendMode.Hue` 先把玻璃的色相拧向品牌色，
 *     再叠一层 75% 的同色 —— 得到的是"这块玻璃是粉的"，
 *     而不是"玻璃上贴了一块粉纸"。后者是普通实心按钮的做法。
 *
 * **它必须拿到 [backdrop] 才能工作**，因此它不能出现在被采样的那一层里
 * （见 `GlassScaffold` 的说明）；放进采样层会导致图层自引用并崩溃。
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    /**
     * 染色叠加的不透明度。官方源码里是硬编码的 0.75。
     *
     * 暴露出来是因为 0.75 已经很接近实心填充 —— 在浅色背景上，按钮看起来
     * 就是"一块粉色药丸"，而不是"粉色的玻璃"，这与液态玻璃的意图相反
     * （玻璃应当让背景参与最终观感）。调低它可以让背景色透上来。
     */
    tintAlpha: Float = 0.75f,
    surfaceColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(percent = 50),
    buttonHeight: Dp = 48.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        // 先拧色相再叠色：得到的才是"有色的玻璃"
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = tintAlpha))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(buttonHeight)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
