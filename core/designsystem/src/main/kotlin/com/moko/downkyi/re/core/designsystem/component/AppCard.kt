package com.moko.downkyi.re.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes

/**
 * 内容卡片。
 *
 * **普通内容的容器是不透明的 iOS 分组底色，不是玻璃，也不是半透明白。**
 *
 * 三个层级各归其位：
 *  - 页面底色：`backgroundGrouped`（安静的系统灰/纯黑）；
 *  - 内容分组：这里的 `backgroundElevated`（白 / #1C1C1E），**不透明** ——
 *    半透明白底叠在彩色渐变上会随背景变色，同样的卡片在页面不同位置
 *    呈现不同颜色，正是"塑料感"的来源；
 *  - 玻璃：只属于悬浮功能层（Dock、浮动按钮），见 glass/liquidglass。
 *
 * 液态玻璃没有用在这里的原因：列表卡片本身就是被采样内容，
 * 自采样会构成图层自引用；且滚动时每张卡都要离屏采样，帧率直接掉。
 */
/**
 * 列表容器的托底色。
 *
 * [AppCard] 与 [AppListGroup] 共用这一个来源。
 *
 * 材质参数散落在多个文件里是典型的维护陷阱：调一次要改好几处，
 * 漏改一处界面上就会出现"两种不同的白"—— 这种差异肉眼看得出来，
 * 却很难定位。所有托底容器都必须走这个函数，不要各自写透明度。
 */
@Composable
internal fun cardSurfaceColor(): Color {
    // 不透明的 iOS 分组底色。文字对比度由此固定，
    // 不随背景渐变漂移。
    return ReTheme.colors.backgroundElevated
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ReTheme.shapes.medium),
    color: Color = cardSurfaceColor(),
    contentPadding: PaddingValues = PaddingValues(ReTheme.spacing.cardPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier
            .clip(shape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.xs),
        content = content,
    )
}

/**
 * 分组卡片：一屏里承载同一类内容的容器，内部通常是若干 [AppListRow]。
 *
 * 与 [AppCard] 的区别是它自带内边距与行距节奏，行与行之间由
 * [AppListRow] 自己画发丝分隔线（最后一行传 `showDivider = false`）。
 */
@Composable
fun AppListGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    shape: Shape = RoundedCornerShape(ReTheme.shapes.medium),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            AppTextTertiary(
                title,
                modifier = Modifier.padding(
                    start = ReTheme.spacing.md,
                    end = ReTheme.spacing.md,
                    bottom = ReTheme.spacing.xs,
                ),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                // 与 AppCard 共用同一个托底色，避免出现"两种不同的白"
                .background(cardSurfaceColor()),
            content = content,
        )
    }
}

/**
 * 列表行。
 *
 * 触控区域强制撑到 [Sizes.minTouchTarget] 以上：视觉上可以很紧凑，
 * 但热区不足 48dp 时手指会频繁点错。
 */
@Composable
fun AppListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = ReTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = ReTheme.spacing.minTouchTarget)
                .padding(
                    horizontal = ReTheme.spacing.listRowHorizontal,
                    vertical = ReTheme.spacing.listRowVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
        ) {
            if (leading != null) leading()

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppText(
                    title,
                    style = ReTheme.typography.body,
                    color = if (enabled) colors.label else colors.labelTertiary,
                    maxLines = 2,
                )
                if (subtitle != null) {
                    AppTextSecondary(subtitle, style = ReTheme.typography.footnote, maxLines = 2)
                }
            }

            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.xs),
                    content = trailing,
                )
            }

            if (showChevron) {
                ChevronIndicator()
            }
        }

        if (showDivider) {
            Spacer(
                Modifier
                    .padding(start = ReTheme.spacing.listRowHorizontal)
                    .fillMaxWidth()
                    .height(Sizes.HairlineWidth)
                    .background(colors.separator)
            )
        }
    }
}

/**
 * 列表行右侧的向前指示箭头。
 *
 * 用两条线拼出一个「>」而不是引入图标资源：这个形状在任何字号下
 * 都不会糊，也不增加包体积。
 */
@Composable
fun ChevronIndicator(tint: Color = ReTheme.colors.labelTertiary) {
    androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.18f)
            lineTo(w * 0.7f, h * 0.5f)
            lineTo(w * 0.25f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/** 行内的分隔留白，用于把一组行与下一组分开。 */
@Composable
fun ListSectionSpacer(height: androidx.compose.ui.unit.Dp = ReTheme.spacing.xl) {
    Spacer(Modifier.height(height))
}

/**
 * 行首的图标底，用于列表左侧的图标。
 *
 * 底色刻意擦淡：在带色彩氛围的背景上，一块实心灰底会让整行凭空变重，
 * 而且四行叠在一起会形成明显的"灰块阵列"。淡底只负责把图标从背景上
 * 轻轻托起来，不参与构图。
 */
@Composable
fun ListLeadingIcon(
    tint: Color,
    background: Color = ReTheme.colors.fillSecondary.copy(alpha = 0.55f),
    size: androidx.compose.ui.unit.Dp = 30.dp,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier
            .size(size)
            .background(background, RoundedCornerShape(ReTheme.shapes.extraSmall)),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .background(tint, RoundedCornerShape(3.dp))
            )
        }
    }
}

/** 行尾的次要文字（例如体积、时长）。 */
@Composable
fun RowTrailingText(text: String, color: Color = ReTheme.colors.labelSecondary) {
    AppText(text, style = ReTheme.typography.subheadline, color = color, maxLines = 1)
    Spacer(Modifier.width(ReTheme.spacing.xxs))
}
