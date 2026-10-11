package com.moko.downkyi.re.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.glass.GlassBackdrop
import com.moko.downkyi.re.core.designsystem.glass.glassSurface
import com.moko.downkyi.re.core.designsystem.theme.GlassLevel
import com.moko.downkyi.re.core.designsystem.theme.Motion
import com.moko.downkyi.re.core.designsystem.theme.ReTheme

/**
 * 按钮的视觉层级。
 */
enum class ButtonTone {
    /** 强调：页面主操作，用品牌色实心。 */
    Accent,

    /** 玻璃：悬浮于内容之上，继承背后的颜色。 */
    Glass,

    /** 次要：浅填充，用于并列的次级操作。 */
    Secondary,

    /** 危险：删除、清空等不可逆操作。 */
    Danger,
}

/**
 * 按压反馈。
 *
 * 缩放幅度与动画曲线都来自动效规范，保证全应用的手感一致：
 * 用弹簧而不是固定时长，这样快速连点或中途取消时不会突兀。
 */
@Composable
private fun Modifier.pressModifier(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PressScale else 1f,
        animationSpec = Motion.snappy(),
        label = "button-press-scale",
    )
    return this.scale(scale)
}

/**
 * 玻璃按钮。
 *
 * 是悬浮工具条与导航栏上的主要操作形态：不打断内容，又能在任意背景上
 * 保持可读。按压时整体轻微缩小，同时材质本身会重新采样背景。
 */
@Composable
fun GlassButton(
    backdrop: GlassBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Regular,
    shape: Shape = RoundedCornerShape(ReTheme.shapes.medium),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = ReTheme.glass

    Row(
        modifier
            .pressModifier(interactionSource)
            .glassSurface(
                backdrop = backdrop,
                style = glass.styleOf(level),
                shape = shape,
                supported = glass.supported,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = ReTheme.spacing.minTouchTarget)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 实心按钮。
 *
 * 用于不悬浮于内容之上的场景（页面底部的主操作、对话框按钮）。
 * 这类按钮刻意不用玻璃：玻璃的成本是采样与模糊，把它用在不受背景影响的
 * 固定位置上是白付性能开销，而且实心色块的视觉权重更明确。
 */
@Composable
fun SolidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Accent,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ReTheme.shapes.medium),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = ReTheme.colors
    val backgroundColor = when {
        !enabled -> colors.fillSecondary
        tone == ButtonTone.Danger -> colors.danger
        tone == ButtonTone.Secondary -> colors.fillSecondary
        else -> colors.accent
    }
    val contentColor = when {
        !enabled -> colors.labelTertiary
        tone == ButtonTone.Secondary -> colors.label
        else -> colors.onAccent
    }

    Box(Modifier.pressModifier(interactionSource)) {
        Row(
            Modifier
                .background(backgroundColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .heightIn(min = ReTheme.spacing.minTouchTarget)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalContainerContentColor provides contentColor,
            ) {
                content()
            }
        }
    }
}

/**
 * 容器内的内容色。
 *
 * 让按钮内的文本与图标自动取到正确的对比色（实心按钮上用反色，
 * 次要按钮上用主文字色），调用方不必逐个指定颜色。
 */
val LocalContainerContentColor = androidx.compose.runtime.staticCompositionLocalOf<Color> {
    Color.Unspecified
}

/** 取当前容器的内容色；未提供时回退到主文字色。 */
@Composable
fun contentColor(): Color {
    val provided = LocalContainerContentColor.current
    return if (provided == Color.Unspecified) ReTheme.colors.label else provided
}
