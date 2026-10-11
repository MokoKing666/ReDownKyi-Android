/*
 * 基于 AndroidLiquidGlass 官方 LiquidBottomTabs 的底部 Dock。
 *
 * 官方组件（本模块 liquidglass/LiquidBottomTabs.kt，移植自
 * https://github.com/Kyant0/AndroidLiquidGlass kmp 分支）承担全部交互与视觉：
 *
 *  - 四个导航项经固定 Row 等分放置，**位置与顺序恒定不动**；
 *  - 选中态是一颗独立的液态玻璃气泡（滑块）：drawBackdrop + vibrancy + lens
 *    （带色散）+ Highlight + Shadow + InnerShadow，按压/速度驱动流体形变；
 *  - DampedDragAnimation 驱动气泡的阻尼拖动与松手回弹：按住气泡拖动时，
 *    气泡跟随手指连续移动并实时预览途经的导航项，松手吸附到最近位置
 *    并回调 onTabSelected 切换页面；
 *  - 点击导航项时，气泡经弹簧动画移动到该项。
 *
 * 本文件只做装配：把应用的主题色与导航模型接到官方组件上。
 */
package com.moko.downkyi.re.core.designsystem.liquidglass

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.BottomBarItem
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes

@Composable
fun LiquidDockBar(
    backdrop: Backdrop,
    items: List<BottomBarItem>,
    /** 选中项下标（与 [items] 顺序一致）。 */
    selectedIndex: Int,
    /** 选中回调（拖动气泡吸附、点击导航项都会走到这里）。 */
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReTheme.colors

    // 官方组件以 lambda 身份作 remember 的 key，并用 snapshotFlow 观察其读数
    // 来驱动滑块动画。必须传**稳定**的 lambda 读稳定 State —— 若每次重组都传
    // 新 lambda，组件内部状态会被反复重置，滑块定位动画随之丢失。
    val selectedIndexState = remember { mutableIntStateOf(selectedIndex) }
    selectedIndexState.intValue = selectedIndex

    LiquidBottomTabs(
        selectedTabIndex = remember { { selectedIndexState.intValue } },
        onTabSelected = onSelect,
        backdrop = backdrop,
        tabsCount = items.size,
        // 主题色对齐应用设计（官方缺省为蓝色）
        accentColor = colors.accent,
        modifier = modifier,
    ) {
        items.forEachIndexed { index, item ->
            LiquidBottomTab(
                onClick = { onSelect(index) },
                modifier = Modifier,
            ) {
                item.icon?.invoke(colors.labelSecondary)
                AppText(
                    item.label,
                    style = ReTheme.typography.caption2,
                    color = colors.labelSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
