package com.moko.downkyi.re.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.theme.Motion
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes

/**
 * 分段控件（iOS 的 Segmented Control）。
 *
 * 选中滑块会在选项之间平滑滑动，而不是各选项自己切换底色 ——
 * 滑动让「当前在哪一段」这件事在切换瞬间就可读，
 * 也让「进行中 / 已完成」这类二分切换有连续的方向感。
 */
@Composable
fun <T> AppSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
) {
    if (options.isEmpty()) return

    val colors = ReTheme.colors
    val shapes = ReTheme.shapes
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val trackShape = RoundedCornerShape(shapes.small)
    val thumbShape = RoundedCornerShape(shapes.small - 2.dp)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(trackShape)
            .background(colors.fillTertiary)
    ) {
        val itemWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = Motion.snappy(),
            label = "segmented-thumb",
        )

        // 选中滑块
        Box(
            Modifier
                .offset(x = thumbOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(2.dp)
                .clip(thumbShape)
                .background(colors.backgroundElevated)
        )

        // 文字层
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        label(option),
                        style = ReTheme.typography.subheadline,
                        color = if (isSelected) colors.label else colors.labelSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 线性进度条。
 *
 * [progress] 为负值时表示**不确定进度**（服务端未返回总长、或正在解析地址），
 * 此时显示循环往复的动画。这个区分很重要：用 0% 表示"未知"会让用户
 * 以为任务卡住了。
 */
@Composable
fun AppProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ReTheme.colors.accent,
    trackColor: Color = ReTheme.colors.fillTertiary,
    height: Dp = Sizes.ProgressBarHeight,
) {
    val shape = RoundedCornerShape(height / 2)
    val indeterminate = progress < 0f

    if (!indeterminate) {
        val animated by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = Motion.standard(),
            label = "progress",
        )
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(trackColor)
        ) {
            if (animated > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .fillMaxHeight()
                        .background(color, shape)
                )
            }
        }
        return
    }

    // 不确定进度：一段光带循环扫过
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "sweep",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bandWidth = size.width * 0.35f
            val start = -bandWidth + sweep * (size.width + bandWidth)
            drawRoundRect(
                color = color,
                topLeft = Offset(start, 0f),
                size = Size(bandWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
        }
    }
}

/**
 * 环形进度。
 *
 * 用于下载任务卡片 —— 环形相比线性条更适合放在卡片右侧的固定位置，
 * 且能在圆心显示百分比，不必再占一行文字。
 */
@Composable
fun AppProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = ReTheme.colors.accent,
    trackColor: Color = ReTheme.colors.fillTertiary,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val indeterminate = progress < 0f
    val animated by animateFloatAsState(
        targetValue = if (indeterminate) 0f else progress.coerceIn(0f, 1f),
        animationSpec = Motion.standard(),
        label = "ring-progress",
    )

    val transition = rememberInfiniteTransition(label = "ring-indeterminate")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
        ),
        label = "ring-spin",
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            if (indeterminate) {
                drawArc(
                    color = color,
                    startAngle = spin,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            } else if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * 开关。
 *
 * 自己实现而不是用 Material 的 Switch：Material 的开关在轨道里
 * 有独立的图标与更粗的轨道，放进 Apple 风格的列表里会显得突兀。
 * 这里用 iOS 的比例 —— 51×31 的轨道与 27 的圆形滑块。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ReTheme.colors
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val thumbSize = 27.dp
    val padding = 2.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - padding else padding,
        animationSpec = Motion.standard(),
        label = "switch-thumb",
    )
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            !enabled -> colors.fillTertiary
            checked -> colors.success
            else -> colors.fillTertiary
        },
        animationSpec = Motion.fade(Motion.DurationInstant),
        label = "switch-track",
    )

    Box(
        modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(thumbSize / 2), clip = false)
                .background(Color.White, RoundedCornerShape(thumbSize / 2))
        )
    }
}

/** 分段控件针对字符串选项的便捷重载。 */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    AppSegmentedControl(
        options = options,
        selected = options[selectedIndex.coerceIn(options.indices)],
        onSelect = { onSelect(options.indexOf(it)) },
        modifier = modifier,
        label = { it },
    )
}
