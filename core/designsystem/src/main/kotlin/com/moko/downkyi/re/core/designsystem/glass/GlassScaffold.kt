package com.moko.downkyi.re.core.designsystem.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 玻璃页面的骨架。
 *
 * ## 为什么必须用它，而不是自己拼 Box
 *
 * 玻璃需要"采样背后的内容"，采样是通过一个离屏图层完成的。
 * 如果玻璃元素本身位于被采样的图层**内部**，图层就会包含自己 →
 * 递归引用 → 渲染树无限展开 → **native 栈溢出崩溃**（不是可捕获的异常，
 * 是进程直接死掉，日志里表现为 `libhwui.so` 的 `RenderNode::prepareTreeImpl`
 * 递归几百层）。
 *
 * 所以有一条铁律：
 *
 * > **任何玻璃元素都不能是被采样内容的子树。**
 *
 * 这个组件把结构固定下来，让这条规则不依赖开发者记性：
 *  - [background] 的内容被采样，且**拿不到** `backdrop` —— 因此写不出玻璃元素；
 *  - [overlay] 才拿到 `backdrop`，它整体位于采样层之外，是采样内容的兄弟节点。
 *
 * 这也正是 iOS 的实际做法：列表内容是**不透明卡片**，
 * 玻璃只用于导航栏、标签栏、悬浮控件与弹窗。把玻璃铺满列表
 * 既会撞上上述结构限制，也会让界面在滚动的每一帧都付出离屏合成的代价。
 *
 * ## 用法
 *
 * ```
 * GlassScaffold(
 *     background = {
 *         // 这里放背景与可滚动内容：图片、列表、文字。不要放玻璃。
 *     },
 *     overlay = { backdrop ->
 *         // 这里放玻璃：导航栏、标签栏、悬浮按钮。
 *         GlassSurface(backdrop, level = GlassLevel.Thin) { ... }
 *     },
 * )
 * ```
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    /** 被采样的内容：背景与滚动内容。此处不可放置玻璃元素。 */
    background: @Composable BoxScope.() -> Unit,
    /** 玻璃层。整体位于采样层之上，可对任意悬浮元素应用玻璃材质。 */
    overlay: @Composable BoxScope.(backdrop: GlassBackdropSource) -> Unit,
) {
    val backdrop = rememberGlassBackdrop()

    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop)
        ) {
            background()
        }

        overlay(backdrop)
    }
}
