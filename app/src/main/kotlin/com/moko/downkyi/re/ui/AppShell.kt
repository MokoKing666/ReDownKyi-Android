package com.moko.downkyi.re.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.moko.downkyi.re.core.designsystem.component.AppIcons
import com.moko.downkyi.re.core.designsystem.component.BottomBarItem
import com.moko.downkyi.re.core.designsystem.glass.glassBackdropSource
import com.moko.downkyi.re.core.designsystem.glass.rememberGlassBackdrop
import com.moko.downkyi.re.core.designsystem.liquidglass.LiquidDockBar
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes
import com.moko.downkyi.re.core.model.SaveLocation
import com.moko.downkyi.re.ui.tab.DownloadsTab
import com.moko.downkyi.re.ui.tab.HomeTab
import com.moko.downkyi.re.ui.tab.LibraryTab
import com.moko.downkyi.re.ui.tab.ProfileTab

/**
 * 四个顶级标签的外壳。
 *
 * 玻璃链路全部来自官方 AndroidLiquidGlass（backdrop 库）：
 * 页面经 [Modifier.glassBackdropSource] 登记为采样层，Dock（官方
 * LiquidBottomTabs 结构）悬浮在内容层之外采样它 —— 折射、高光、阴影、
 * 滑块动画都是官方实现，本文件只负责装配。
 *
 * 采样源有两级，缺一不可：
 *  - **contentBackdrop**（背景 + 内容）：Dock 悬浮于内容层**之外**，
 *    采样整个内容层不会构成图层自引用，所以它折射到的是完整页面；
 *  - **backgroundBackdrop**（仅背景）：内容层**内部**的玻璃元件
 *    （首页的解析按钮）若采样含自身的内容层会直接崩溃，
 *    因此只能采样背景层 —— 折射到的是背景的色彩与光斑。
 */
@Composable
fun AppShell(
    state: AppUiState,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenRoute: (AppRoute) -> Unit,
    onToggleTheme: () -> Unit,
    isDark: Boolean,
    onTaskPause: (String) -> Unit = {},
    onTaskResume: (String) -> Unit = {},
    onTaskRetry: (String) -> Unit = {},
    onTaskDelete: (String) -> Unit = {},
    /** 打开已完成的成品（content uri 或应用内路径）。 */
    onTaskOpen: (String) -> Unit = {},
    /** 真实生效的保存位置（设置存储）。 */
    saveLocation: SaveLocation = SaveLocation.DEFAULT,
    /** 循环切换保存位置。 */
    onCycleSaveLocation: () -> Unit = {},
    defaultQn: Int = 0,
    onCycleDefaultQn: () -> Unit = {},
    defaultCodec: String = "auto",
    onCycleDefaultCodec: () -> Unit = {},
) {
    val backgroundBackdrop = rememberGlassBackdrop()
    val contentBackdrop = rememberGlassBackdrop()

    Box(Modifier.fillMaxSize()) {
        // 内容层内部的玻璃元件（首页按钮等）专用：只有背景，没有内容
        Box(Modifier.fillMaxSize().glassBackdropSource(backgroundBackdrop)) {
            AppBackground()
        }

        // Dock 的采样源：背景 + 完整内容。Dock 在这一层之外，不自引用
        Box(Modifier.fillMaxSize().glassBackdropSource(contentBackdrop)) {
            AppBackground()
            when (selectedTab) {
                0 -> HomeTab(
                    state = state,
                    onOpenRoute = onOpenRoute,
                    backdrop = backgroundBackdrop,
                )

                1 -> DownloadsTab(
                    state = state,
                    onOpenRoute = onOpenRoute,
                    backdrop = backgroundBackdrop,
                    onPause = onTaskPause,
                    onResume = onTaskResume,
                    onRetry = onTaskRetry,
                    onDelete = onTaskDelete,
                    onOpen = onTaskOpen,
                )
                2 -> LibraryTab(state = state, onOpenRoute = onOpenRoute)
                else -> ProfileTab(
                    state = state,
                    onOpenRoute = onOpenRoute,
                    onToggleTheme = onToggleTheme,
                    isDark = isDark,
                    saveLocation = saveLocation,
                    onCycleSaveLocation = onCycleSaveLocation,
                    defaultQn = defaultQn,
                    onCycleDefaultQn = onCycleDefaultQn,
                    defaultCodec = defaultCodec,
                    onCycleDefaultCodec = onCycleDefaultCodec,
                )
            }
        }

        // Dock：固定在底部中央，永不随手势移动。
        // 唯一可拖动的是液态玻璃气泡（选中指示器），拖动即切换导航
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp),
        ) {
            LiquidDockBar(
                backdrop = contentBackdrop,
                items = bottomBarItems,
                selectedIndex = selectedTab,
                onSelect = onSelectTab,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 页面背景。
 *
 * 设计原则：**背景为内容服务，不与内容争夺注意力。**
 *
 * 底色是不透明的 iOS 分组灰（`backgroundGrouped`），品牌色只以两团
 * 极淡的光斑出现在页面上部 —— 它们的职责是给液态玻璃的折射提供
 * 可辨认的色彩梯度（纯色背景上折射"发生了"但肉眼零信息量），
 * 而不是充当整页的主视觉。
 *
 * 上一版把光斑铺满全屏、alpha 高达 0.22，整页像通用玻璃拟态模板；
 * 现在光斑只在顶部三分之一内渐隐，越往下越接近纯分组灰，
 * 内容层级由排版与分组容器建立。
 */
@Composable
private fun AppBackground(modifier: Modifier = Modifier) {
    val isLight = ReTheme.colors.isLight
    Box(
        modifier
            .fillMaxSize()
            .background(ReTheme.colors.backgroundGrouped)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 深色下光斑略加强：黑底会"吃掉"大半色彩，
            // 同一 alpha 在深色下几乎不可见。
            val boost = if (isLight) 1f else 1.4f

            // 品牌粉光斑：顶部左侧，标题区背后。
            drawBrandSpot(
                center = Offset(size.width * 0.12f, size.height * 0.04f),
                radius = size.width * 0.55f,
                color = Color(0xFFFB7299),
                alpha = 0.10f * boost,
            )
            // 蓝光斑：顶部右侧，给玻璃一个可辨认的第二色相。
            drawBrandSpot(
                center = Offset(size.width * 0.92f, size.height * 0.02f),
                radius = size.width * 0.45f,
                color = Color(0xFF3A9BE8),
                alpha = 0.07f * boost,
            )
        }
    }
}

/** 一团柔和的品牌色光斑：从中心向外的径向渐变，边缘完全消隐。 */
private fun DrawScope.drawBrandSpot(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private val bottomBarItems: List<BottomBarItem> = listOf(
    BottomBarItem("home", "首页") { tint -> AppIcons.Home(tint, Modifier.size(Sizes.IconSmall)) },
    BottomBarItem("downloads", "下载") { tint -> AppIcons.Download(tint, Modifier.size(Sizes.IconSmall)) },
    BottomBarItem("library", "媒体库") { tint -> AppIcons.Library(tint, Modifier.size(Sizes.IconSmall)) },
    BottomBarItem("profile", "我的") { tint -> AppIcons.Person(tint, Modifier.size(Sizes.IconSmall)) },
)


