package com.moko.downkyi.re.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 全局主题。
 *
 * 玻璃材质参数随色彩主题一起提供（见 [LocalGlassTheme]）：
 * 浅色主题下玻璃偏白、高光强；深色主题下玻璃偏暗、高光弱。
 * 让各处自行判断深浅会很快出现不一致 —— 同一屏上两块玻璃色调不同最显廉价。
 */
@Composable
fun ReDownKyiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    SyncSystemBarAppearance(darkTheme)

    CompositionLocalProvider(
        LocalReDownKyiColors provides colors,
        LocalReDownKyiTypography provides DefaultTypography,
        LocalReDownKyiShapes provides DefaultShapes,
        LocalReDownKyiSpacing provides DefaultSpacing,
        LocalGlassTheme provides if (colors.isLight) LightGlassTheme else DarkGlassTheme,
        content = content,
    )
}

/**
 * 同步系统栏图标颜色。
 *
 * 界面是边到边（edge-to-edge）的，内容会顶到状态栏与导航栏下方，
 * 而这两个区域的图标由系统绘制 —— 不同步的话，"浅色界面 + 白色状态栏图标"
 * 会让时间和电量完全看不见。
 */
@Composable
private fun SyncSystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        // 浅色主题需要深色图标
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 主题访问入口。
 *
 * 用 `ReTheme.colors` 而不是让界面直接读 CompositionLocal：
 * 这样将来增删令牌不必全局替换调用点，也让"主题提供了哪些东西"一目了然。
 */
object ReTheme {
    val colors: ReDownKyiColors
        @Composable @ReadOnlyComposable get() = LocalReDownKyiColors.current

    val typography: ReDownKyiTypography
        @Composable @ReadOnlyComposable get() = LocalReDownKyiTypography.current

    val shapes: ReDownKyiShapes
        @Composable @ReadOnlyComposable get() = LocalReDownKyiShapes.current

    val spacing: ReDownKyiSpacing
        @Composable @ReadOnlyComposable get() = LocalReDownKyiSpacing.current

    val glass: GlassTheme
        @Composable @ReadOnlyComposable get() = LocalGlassTheme.current
}
