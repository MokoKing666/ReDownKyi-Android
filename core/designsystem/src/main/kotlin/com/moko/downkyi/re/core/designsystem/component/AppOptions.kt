package com.moko.downkyi.re.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.theme.ReTheme

/**
 * 带标题的卡片区块。
 *
 * 解析结果页、设置页这类「一屏多组选项」的页面全靠它组织：
 * 每组一个标题、一张卡，卡片内是若干选项。用统一的容器而不是每组各写一套
 * 布局，是保证间距节奏一致的成本最低的做法。
 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            AppTextTertiary(
                title,
                modifier = Modifier.padding(
                    start = ReTheme.spacing.xxs,
                    bottom = ReTheme.spacing.xs,
                ),
            )
        }
        AppCard(content = content)
    }
}

/**
 * 选择芯片组。
 *
 * 用于选项数量少、且各选项之间**互斥平级**的场景（选档模式、视频编码、
 * 保存位置）。自动换行，因此窄屏下也不会被截断。
 *
 * 与分段控件的分工：分段控件用于 2–3 个选项且需要强调「当前在第几段」；
 * 芯片组用于 3 个以上选项，或者各选项文字长度差异较大的场景。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.xs),
    ) {
        options.forEach { option ->
            Chip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ReTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(ReTheme.shapes.capsule))
            .background(if (selected) colors.accent else colors.fillTertiary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .heightIn(min = 32.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            label,
            style = ReTheme.typography.subheadline,
            color = if (selected) colors.onAccent else colors.label,
            maxLines = 1,
        )
    }
}

/**
 * 勾选行。
 *
 * 勾选框是**自己画的圆环**，不是 Material 的 Checkbox ——
 * Material 的勾选框在深色模式下会在圆圈外露出一块方形底色，
 * 那个底色是组件内部的 `surface`，既遮不掉也很难对齐。
 * 这里用纯圆环 + 实心点，两套主题下表现一致。
 */
@Composable
fun CheckRow(
    checked: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val colors = ReTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Checkbox,
                        onClick = { onCheckedChange(!checked) },
                    )
                } else {
                    Modifier
                }
            )
            .heightIn(min = ReTheme.spacing.minTouchTarget)
            .padding(vertical = ReTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
    ) {
        CheckMark(checked = checked, enabled = enabled)

        Column(Modifier.weight(1f)) {
            AppText(
                title,
                style = ReTheme.typography.body,
                color = if (enabled) colors.label else colors.labelTertiary,
            )
            if (subtitle != null) {
                AppTextTertiary(subtitle, style = ReTheme.typography.caption1)
            }
        }
    }
}

/**
 * 勾选标记。
 *
 * 选中时整圆填品牌色并在中间画勾；未选中时是灰色实心圆。
 * 未选中态用**填充**而不是描边：描边圆在密集列表里几乎看不见，
 * 用户会以为这一行不可点。
 */
@Composable
fun CheckMark(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ReTheme.colors
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> colors.fillTertiary
                    checked -> colors.accent
                    else -> colors.fillTertiary
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            AppIcons.Check(
                tint = if (enabled) colors.onAccent else colors.labelTertiary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * 单选标记（圆环 + 实心点）。
 *
 * 用于互斥选择（清晰度、音轨）。与 [CheckMark] 的差异必须一眼可辨：
 * 勾选是「填满 + 打勾」，单选是「空心环 + 中心点」。
 */
@Composable
fun RadioMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ReTheme.colors
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.fillTertiary),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.onAccent)
            )
        }
    }
}

/** 单选行：与 [CheckRow] 结构相同，但语义是互斥的。 */
@Composable
fun RadioRow(
    selected: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val colors = ReTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ReTheme.shapes.small))
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .heightIn(min = ReTheme.spacing.minTouchTarget)
            .padding(horizontal = ReTheme.spacing.xs, vertical = ReTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
    ) {
        RadioMark(selected = selected)
        Column(Modifier.weight(1f)) {
            AppText(
                title,
                style = ReTheme.typography.body,
                color = if (selected) colors.accent else colors.label,
            )
            if (subtitle != null) {
                AppTextTertiary(subtitle, style = ReTheme.typography.caption1)
            }
        }
        if (trailing != null) {
            AppText(trailing, style = ReTheme.typography.numeric, color = colors.labelSecondary)
        }
    }
}

/** 一行说明文字，用于选项下方的解释。 */
@Composable
fun OptionHint(text: String, modifier: Modifier = Modifier) {
    AppTextTertiary(
        text,
        modifier = modifier.padding(top = ReTheme.spacing.xxs),
        style = ReTheme.typography.caption1,
    )
}

/** 卡片内的分隔线，用于把卡片分成上下两段。 */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(ReTheme.colors.separator)
    )
}
