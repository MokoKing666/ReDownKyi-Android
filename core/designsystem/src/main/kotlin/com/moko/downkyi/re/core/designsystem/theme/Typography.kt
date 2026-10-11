package com.moko.downkyi.re.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 排版阶梯。
 *
 * 字号与字重直接对应 iOS 的 Dynamic Type 层级（largeTitle → caption2），
 * 这样「什么内容用什么字号」有据可依，而不是每页各凭感觉。
 *
 * 关于字体本身的取舍：**不打包自定义字体**。
 * 打包一款覆盖中英文的字体要 5–10 MB，而这是一个下载工具，
 * 用户对安装包体积敏感。西文与数字用系统字体，靠字号、字重、
 * 行高与字距来塑造 Apple 式的排版观感 —— 这三项带来的差异远大于字形本身。
 *
 * 行高统一按 iOS 的 1.29 倍左右设置；小字号略放宽，因为中文在小字号下
 * 行距过密会明显难读。
 */
@Immutable
data class ReDownKyiTypography(
    /** 34pt 加粗。用于首页顶部品牌标题。 */
    val largeTitle: TextStyle,
    /** 28pt 加粗。用于二级页大标题。 */
    val title1: TextStyle,
    /** 22pt 加粗。用于区块主标题。 */
    val title2: TextStyle,
    /** 20pt 半粗。用于卡片内标题。 */
    val title3: TextStyle,

    /** 17pt 半粗。列表主文字（强调）。 */
    val headline: TextStyle,
    /** 17pt 常规。正文标准字号。 */
    val body: TextStyle,
    /** 16pt 常规。略小一号的正文。 */
    val callout: TextStyle,
    /** 15pt 常规。列表副文字。 */
    val subheadline: TextStyle,
    /** 13pt 常规。辅助说明。 */
    val footnote: TextStyle,
    /** 12pt 常规。角标、标签。 */
    val caption1: TextStyle,
    /** 11pt 常规。最小号文字。 */
    val caption2: TextStyle,

    /**
     * 等宽数字样式。
     *
     * 下载进度、速度、体积、剩余时间这些数字必须用等宽字形：
     * 变宽数字在每秒刷新时会左右抖动，是列表里最刺眼的一类抖动。
     */
    val numeric: TextStyle,
    /** 大号等宽数字，用于进度百分比。 */
    val numericLarge: TextStyle,
)

/** 大字号用负字距收紧，小字号用正字距拉开 —— 与 iOS 的处理一致。 */
private val TightSpacing = (-0.3).sp
private val SmallSpacing = 0.1.sp

val DefaultTypography = ReDownKyiTypography(
    largeTitle = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = TightSpacing,
        lineHeight = 41.sp,
    ),
    title1 = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = TightSpacing,
        lineHeight = 34.sp,
    ),
    title2 = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = TightSpacing,
        lineHeight = 28.sp,
    ),
    title3 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        lineHeight = 26.sp,
    ),

    headline = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        lineHeight = 22.sp,
    ),
    body = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 23.sp,
    ),
    callout = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 22.sp,
    ),
    subheadline = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 21.sp,
    ),
    footnote = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = SmallSpacing,
        lineHeight = 19.sp,
    ),
    caption1 = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = SmallSpacing,
        lineHeight = 17.sp,
    ),
    caption2 = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = SmallSpacing,
        lineHeight = 15.sp,
    ),

    numeric = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.sp,
        lineHeight = 20.sp,
    ),
    numericLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = (-0.5).sp,
        lineHeight = 30.sp,
    ),
)

val LocalReDownKyiTypography = staticCompositionLocalOf { DefaultTypography }
