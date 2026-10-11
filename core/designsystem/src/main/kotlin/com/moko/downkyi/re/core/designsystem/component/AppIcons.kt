package com.moko.downkyi.re.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 应用图标集。
 *
 * **全部自绘，不引入图标库。** 两个原因：
 *  1. Material Symbols 的笔画粗细、圆角与端点处理是 Google 的视觉语言，
 *     混在 Apple 风格的界面里会明显"不对劲"，而这种不协调很难靠调参消除；
 *  2. 一个图标库动辄几百 KB 到数 MB，而这里实际需要的不到二十个。
 *
 * 统一约束（保证一套图标看起来是一家人）：
 *  - 画布按 1:1 归一化坐标绘制，与最终尺寸无关；
 *  - 线宽按画布短边的固定比例，因此任何尺寸下线宽观感一致；
 *  - 一律用圆头端点与圆角连接，这是 iOS 图标的基本特征；
 *  - 需要填充的图标（播放三角、圆点）不描边，需要描边的不填充。
 */
object AppIcons {

    /** 默认线宽比例，约等于 iOS 图标的笔画粗细。 */
    private const val DefaultStrokeScale = 0.085f

    @Composable
    private fun Glyph(
        modifier: Modifier,
        strokeScale: Float = DefaultStrokeScale,
        draw: DrawScope.(stroke: Float) -> Unit,
    ) {
        Canvas(modifier) {
            draw(size.minDimension * strokeScale)
        }
    }

    private fun strokeOf(width: Float) = Stroke(
        width = width,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    /** 返回：向左的尖角。 */
    @Composable
    fun Back(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        drawPath(
            Path().apply {
                moveTo(size.width * 0.62f, size.height * 0.16f)
                lineTo(size.width * 0.26f, size.height * 0.5f)
                lineTo(size.width * 0.62f, size.height * 0.84f)
            },
            color = tint,
            style = strokeOf(stroke),
        )
    }

    /** 向右的尖角，用于列表行的向前指示。 */
    @Composable
    fun ChevronRight(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        drawPath(
            Path().apply {
                moveTo(size.width * 0.38f, size.height * 0.16f)
                lineTo(size.width * 0.74f, size.height * 0.5f)
                lineTo(size.width * 0.38f, size.height * 0.84f)
            },
            color = tint,
            style = strokeOf(stroke),
        )
    }

    /** 关闭。 */
    @Composable
    fun Close(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.22f)
                lineTo(size.width * 0.78f, size.height * 0.78f)
            },
            color = tint,
            style = s,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.78f, size.height * 0.22f)
                lineTo(size.width * 0.22f, size.height * 0.78f)
            },
            color = tint,
            style = s,
        )
    }

    /** 勾选。 */
    @Composable
    fun Check(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        drawPath(
            Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.54f)
                lineTo(size.width * 0.4f, size.height * 0.78f)
                lineTo(size.width * 0.84f, size.height * 0.24f)
            },
            color = tint,
            style = strokeOf(stroke),
        )
    }

    /** 放大镜。 */
    @Composable
    fun Search(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        val radius = size.minDimension * 0.28f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = tint, radius = radius, center = center, style = s)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.63f, size.height * 0.63f)
                lineTo(size.width * 0.86f, size.height * 0.86f)
            },
            color = tint,
            style = s,
        )
    }

    /** 播放三角（填充）。 */
    @Composable
    fun Play(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) {
        drawPath(
            Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.16f)
                lineTo(size.width * 0.84f, size.height * 0.5f)
                lineTo(size.width * 0.3f, size.height * 0.84f)
                close()
            },
            color = tint,
        )
    }

    /** 下载：向下的箭头 + 承载线。 */
    @Composable
    fun Download(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.14f)
                lineTo(size.width * 0.5f, size.height * 0.6f)
            },
            color = tint,
            style = s,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.38f)
                lineTo(size.width * 0.5f, size.height * 0.62f)
                lineTo(size.width * 0.74f, size.height * 0.38f)
            },
            color = tint,
            style = s,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.84f)
                lineTo(size.width * 0.8f, size.height * 0.84f)
            },
            color = tint,
            style = s,
        )
    }

    /** 首页：房子。 */
    @Composable
    fun Home(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.13f, size.height * 0.48f)
                lineTo(size.width * 0.5f, size.height * 0.15f)
                lineTo(size.width * 0.87f, size.height * 0.48f)
            },
            color = tint,
            style = s,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.24f, size.height * 0.44f)
                lineTo(size.width * 0.24f, size.height * 0.85f)
                lineTo(size.width * 0.76f, size.height * 0.85f)
                lineTo(size.width * 0.76f, size.height * 0.44f)
            },
            color = tint,
            style = s,
        )
    }

    /** 媒体库：两个叠放的方块。 */
    @Composable
    fun Library(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.32f, size.height * 0.17f)
                lineTo(size.width * 0.86f, size.height * 0.17f)
                lineTo(size.width * 0.86f, size.height * 0.71f)
            },
            color = tint,
            style = s,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.14f, size.height * 0.31f),
            size = Size(size.width * 0.6f, size.height * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 2.2f),
            style = s,
        )
    }

    /** 我的：人像。 */
    @Composable
    fun Person(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.18f,
            center = Offset(size.width * 0.5f, size.height * 0.33f),
            style = s,
        )
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.56f),
            size = Size(size.width * 0.64f, size.height * 0.6f),
            style = s,
        )
    }

    /** 设置：两条带滑块的轨道。 */
    @Composable
    fun Settings(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.32f)
                lineTo(size.width * 0.88f, size.height * 0.32f)
            },
            color = tint,
            style = s,
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.1f,
            center = Offset(size.width * 0.66f, size.height * 0.32f),
            style = s,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.7f)
                lineTo(size.width * 0.88f, size.height * 0.7f)
            },
            color = tint,
            style = s,
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.1f,
            center = Offset(size.width * 0.34f, size.height * 0.7f),
            style = s,
        )
    }

    /** 更多：三个点。 */
    @Composable
    fun More(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) {
        listOf(0.22f, 0.5f, 0.78f).forEach { x ->
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.07f,
                center = Offset(size.width * x, size.height * 0.5f),
            )
        }
    }

    /** 暂停：两条竖线。 */
    @Composable
    fun Pause(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.3f, size.height * 0.2f),
            size = Size(size.width * 0.14f, size.height * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.07f),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.56f, size.height * 0.2f),
            size = Size(size.width * 0.14f, size.height * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.07f),
        )
    }

    /** 删除：垃圾桶。 */
    @Composable
    fun Trash(tint: Color, modifier: Modifier = Modifier) = Glyph(modifier) { stroke ->
        val s = strokeOf(stroke)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.28f)
                lineTo(size.width * 0.84f, size.height * 0.28f)
            },
            color = tint,
            style = s,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.4f, size.height * 0.28f)
                lineTo(size.width * 0.42f, size.height * 0.16f)
                lineTo(size.width * 0.58f, size.height * 0.16f)
                lineTo(size.width * 0.6f, size.height * 0.28f)
            },
            color = tint,
            style = s,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.25f, size.height * 0.34f)
                lineTo(size.width * 0.3f, size.height * 0.86f)
                lineTo(size.width * 0.7f, size.height * 0.86f)
                lineTo(size.width * 0.75f, size.height * 0.34f)
            },
            color = tint,
            style = s,
        )
    }
}
