/*
 * 移植自 AndroidLiquidGlass
 *   https://github.com/Kyant0/AndroidLiquidGlass
 *   源文件：app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt
 *
 * Copyright (c) Kyant0
 * Licensed under the Apache License, Version 2.0
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * 改动：
 *  1. 包名；
 *  2. 新增 [intensity] 参数（默认 1f = 官方强度）。
 *
 *     为什么需要它：官方示例的背景是深色照片墙纸，`BlendMode.Plus` 的白色
 *     高光叠上去是"玻璃被点亮"；而本工程的页面底色是极浅的分组灰 ——
 *     加法混合在一个已经接近白的底上等于持续做过曝，按压/拖动时
 *     气泡连同它周围会被冲成一整块纯白，材质的透射与折射全部被盖掉。
 *     强度只是对同一高光的增益调整，混合方式、着色器与动画时序未动。
 */
package com.moko.downkyi.re.core.designsystem.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 跟随触点的交互高光。
 *
 * 这是一段**运行时着色器**：它在按下位置画一个柔和的径向光斑（`BlendMode.Plus`
 * 叠加），让玻璃在手指接触处亮起来，并随手指移动而滑动。
 *
 * 这是"玻璃被触碰"与"一块半透明色板被触碰"的分界线 —— 前者会响应，
 * 后者不会。属于官方的核心材质表现，不要用静态渐变去模仿。
 *
 * 设备不支持运行时着色器时，退化为整体提亮（仍保留按压反馈）。
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    /** 高光增益。1f = 官方强度；浅色底上应低于 1（加法白光会过曝）。 */
    val intensity: Float = 1f,
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                if (shader != null) {
                    drawRect(
                        Color.White.copy(0.08f * progress * intensity),
                        blendMode = BlendMode.Plus
                    )
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.15f * progress * intensity))
                        setFloatUniform("radius", size.minDimension * 1.5f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader.asComposeShader()),
                        blendMode = BlendMode.Plus
                    )
                } else {
                    drawRect(
                        Color.White.copy(0.25f * progress * intensity),
                        blendMode = BlendMode.Plus
                    )
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
