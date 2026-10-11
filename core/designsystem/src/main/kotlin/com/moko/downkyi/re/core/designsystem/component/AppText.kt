package com.moko.downkyi.re.core.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.moko.downkyi.re.core.designsystem.theme.ReTheme

/**
 * 文本组件。
 *
 * 建立在 [BasicText] 而不是 Material 的 Text 之上：Material 的文本组件会
 * 从 MaterialTheme 继承颜色与字体，一旦界面里混入它的默认值，就会在
 * Apple 风格的排版里冒出 Material 的行高与字距 —— 这类不一致很难定位。
 *
 * 默认取主题的正文样式与主文字色，因此最常见的用法就是 `AppText("标题")`。
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ReTheme.typography.body,
    color: Color = ReTheme.colors.label,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = color)),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** 次要文字：辅助说明、列表副标题。 */
@Composable
fun AppTextSecondary(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ReTheme.typography.subheadline,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    AppText(
        text = text,
        modifier = modifier,
        style = style,
        color = ReTheme.colors.labelSecondary,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** 三级文字：脚注、提示。 */
@Composable
fun AppTextTertiary(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ReTheme.typography.footnote,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    AppText(
        text = text,
        modifier = modifier,
        style = style,
        color = ReTheme.colors.labelTertiary,
        maxLines = maxLines,
        overflow = overflow,
    )
}
