package com.moko.downkyi.re.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.moko.downkyi.re.core.designsystem.glass.GlassBackdrop
import com.moko.downkyi.re.core.designsystem.liquidglass.DampedDragAnimation
import com.moko.downkyi.re.core.designsystem.liquidglass.InteractiveHighlight
import com.moko.downkyi.re.core.designsystem.glass.glassSurface
import com.moko.downkyi.re.core.designsystem.theme.GlassLevel
import com.moko.downkyi.re.core.designsystem.theme.Motion
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes

/**
 * 顶部玻璃导航栏。
 *
 * 用途：二级页面与首页顶部。始终贴边、使用薄档玻璃、零圆角 ——
 * 贴边元件做明显折射会带来两个问题：左右边缘必然采样越界，
 * 且滚动时持续变形会产生很明显的水波感。
 *
 * [title] 支持两种形态：传入大标题时它是 iOS 的「内联标题」样式，
 * 适合二级页；首页的大标题应该放在滚动内容里，由页面自己实现
 * 随滚动收缩的效果。
 */
@Composable
fun GlassTopBar(
    backdrop: GlassBackdrop,
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val glass = ReTheme.glass
    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(
                backdrop = backdrop,
                style = glass.thin,
                shape = RoundedCornerShape(0.dp),
                supported = glass.supported,
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(Sizes.NavBarHeight)
            .padding(horizontal = ReTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else {
            Spacer(Modifier.width(ReTheme.spacing.xs))
        }

        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = ReTheme.spacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            AppText(title, style = ReTheme.typography.headline, maxLines = 1)
        }

        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * 底部玻璃标签栏。
 *
 * 结构上取「图标 + 标签」的纵向排布（iOS 的 Tab Bar 形态），
 * 而不是 Material 的横向胶囊指示器。选中态用品牌色表示，
 * 未选中用三级文字色 —— 不额外加背景块，因为标签栏本身已是玻璃，
 * 再叠一层高亮会在浅色背景下糊成一团。
 */
/** Dock 的本体尺寸。刻意紧凑：玻璃不该靠"变高"来显得重要。 */
private val DockHeight = 64.dp

/** 选中指示器的尺寸。它比一个标签格窄得多 —— 见 [GlassBottomBar] 的说明。 */
private val DockIndicatorWidth = 72.dp
private val DockIndicatorHeight = 52.dp

/**
 * 悬浮玻璃 Dock。
 *
 * ## 实现来源（AndroidLiquidGlass，Apache-2.0）
 *
 * 玻璃容器与选中指示器**基于官方 `LiquidBottomTabs`** 的机制改写：
 *   https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 *
 * 复用的具体机制：
 *  - **玻璃绘制链**：`drawBackdrop` + `vibrancy` + `blur` + `lens`，
 *    外加 `Highlight` / `Shadow` / `InnerShadow` 三个光学层；
 *  - **`DampedDragAnimation`**：选中指示器的阻尼位移、按下缩放、
 *    速度追踪与弹簧回归（已移植到 `liquidglass` 包）；
 *  - **`InteractiveHighlight`**：跟随触点的运行时着色器高光（已移植）；
 *  - **色散只在指示器上开启**（`chromaticAberration = true`），
 *    与官方示例的选择一致 —— 大面积开启会让边缘显脏。
 *
 * 相对官方的改动：`com.kyant.shapes.Capsule` 换成 `RoundedCornerShape(percent = 50)`
 * （前者是 backdrop 的 `implementation` 依赖，不传递到本工程；两者同属
 * shader 支持的圆角矩形体系）；配色接入本工程色板；内容换成业务回调。
 *
 * ## 本工程独有的部分
 *
 * **导航项的拖动排序**。官方的那套拖动服务于"选中指示器滑动"，
 * 与本应用要的"把某项拖到别的位置"是两回事，因此单独立实现：
 * 手势只挂在**每个导航项**上，容器没有任何拖动逻辑，
 * 容器的位置 / 宽度 / 高度在外轮廓上恒为常量。
 */
/**
 * 把持久化的顺序与当前导航项对齐。
 *
 * 必须在读取时做校验，因为存下来的内容可能来自旧版本：
 *  - 可能含**已被删掉的项**（功能下线），不剔除会在界面上留下一个
 *    按不出反应的格子；
 *  - 可能**缺少新增的项**（新版本加了导航），不追加用户会发现少一个入口。
 *
 * 返回列表保证：元素与 [availableIds] 一一对应，无重复、无遗漏。
 */
fun reconcileDockOrder(
    saved: List<String>,
    availableIds: List<String>,
): List<String> {
    val kept = saved.filter { it in availableIds }.distinct()
    val missing = availableIds.filter { it !in kept }
    return kept + missing
}

@Composable
fun GlassBottomBar(
    backdrop: GlassBackdrop,
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 已持久化的顺序（稳定 id）。为空表示尚无记录，用默认顺序。 */
    savedOrder: List<String> = emptyList(),
    /** 用户完成一次重排后回调，由调用方负责持久化。 */
    onOrderChange: (List<String>) -> Unit = {},
) {
    val colors = ReTheme.colors
    val spacing = ReTheme.spacing
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()

    val defaultIds = items.map { it.id }

    // 当前排列。存稳定 id，不是下标 —— 下标会随重排失效。
    // 初始值来自持久化（经校验），本地重排期间以本地状态为准。
    var order by remember(items, savedOrder) {
        mutableStateOf(
            if (savedOrder.isEmpty()) {
                defaultIds
            } else {
                reconcileDockOrder(savedOrder, defaultIds)
            },
        )
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var suppressClickUntil by remember { mutableLongStateOf(0L) }

    val selectedId = items.getOrNull(selectedIndex)?.id

    // 官方机制：阻尼拖动状态，负责指示器的位移与弹性
    val dampedDragAnimation = remember(animationScope, items.size) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = 0f,
            valueRange = 0f..(items.size - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }

    // 选中项或顺序变化时，指示器以弹簧滑到新位置
    LaunchedEffect(selectedId, order) {
        val pos = order.indexOf(selectedId).coerceAtLeast(0)
        dampedDragAnimation.animateToValue(pos.toFloat())
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = spacing.sm),
    ) {
        val tabWidth = maxWidth / items.size
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val indicatorWidthPx = with(density) { DockIndicatorWidth.toPx() }
        val capsuleShape = RoundedCornerShape(percent = 50)

        // 官方机制：跟随触点的运行时着色器高光。
        // 位置跟随指示器当前的中心，这样高光始终落在"被按住的项"附近。
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        x = (dampedDragAnimation.value + 0.5f) * size.width / items.size,
                        y = size.height / 2f,
                    )
                },
            )
        }

        Box(Modifier.fillMaxWidth().height(DockHeight)) {

            // ── 玻璃容器（官方绘制链）──────────────────────────────
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { capsuleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawSurface = {
                            // 表面色刻意很淡：官方的 containerColor 只有 40% 不透明，
                            // 因为折射已经在承担"让背景透进来"的工作，
                            // 再堆一层厚填充就变成磨砂塑料了
                            drawRect(
                                if (colors.isLight) Color.White.copy(alpha = 0.42f)
                                else Color.White.copy(alpha = 0.14f),
                            )
                        },
                    ),
            )

            // ── 选中指示器（官方 drawBackdrop + 色散 + 三层光学）────
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            ((dampedDragAnimation.value + 0.5f) * tabWidthPx - indicatorWidthPx / 2f)
                                .roundToInt(),
                            0,
                        )
                    }
                    .align(Alignment.CenterStart)
                    .width(DockIndicatorWidth)
                    .height(DockIndicatorHeight)
                    // 官方用速度驱动轻微的横向拉伸/纵向压缩：
                    // 指示器像有质量的液体，快滑时会"淌开"一点
                    .graphicsLayer {
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX = 1f + (velocity * 0.5f).coerceIn(-0.12f, 0.12f)
                        scaleY = 1f - (velocity * 0.2f).coerceIn(-0.12f, 0.12f)
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { capsuleShape },
                        effects = {
                            // 这里刻意**不开色散**（官方示例在指示器上是开的）。
                            //
                            // 真机实测：在本工程的尺寸与配色下，开启色散会在
                            // 指示器左上角析出一段明显的彩虹色带，观感是"脏"
                            // 而不是"精致" —— 色散强度由 shader 内部固定，
                            // 没法按元件尺寸调低，所以只能整项关掉，
                            // 保留折射本身（它已经足够建立光学感）。
                            lens(
                                8.dp.toPx(),
                                10.dp.toPx(),
                                chromaticAberration = false,
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow() },
                        // 内阴影必须很淡。
                        //
                        // 官方把它绑在 pressProgress 上（只在按下时出现）；
                        // 早期版本写成常驻的 0.7 alpha，结果在指示器左侧压出
                        // 一块突兀的深色形变，看起来像玻璃上有个污点。
                        innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.12f) },
                        onDrawSurface = {
                            drawRect(
                                colors.accent.copy(
                                    alpha = if (colors.isLight) 0.20f else 0.46f,
                                ),
                            )
                        },
                    )
                    .then(interactiveHighlight.modifier),
            )

            // ── 导航项（本工程独有：长按拖动排序）──────────────────
            // key(id) 是必需的：重排会改变遍历顺序，没有稳定 key 时
            // Compose 会把动画状态错配到另一项，表现为"高亮乱跳"。
            order.forEachIndexed { pos, id ->
                val item = items.firstOrNull { it.id == id } ?: return@forEachIndexed
                val isDragging = id == draggingId
                val baseX = tabWidth * pos

                key(id) {
                    val animatedX by animateDpAsState(
                        targetValue = baseX,
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
                        label = "dock-tab-x-$id",
                    )

                    DockTab(
                        item = item,
                        selected = id == selectedId,
                        modifier = Modifier
                            // 被拖动项跟手（不加动画），其余项弹簧让位
                            .offset(
                                x = if (isDragging) {
                                    baseX + with(density) { dragOffset.toDp() }
                                } else {
                                    animatedX
                                },
                            )
                            .width(tabWidth)
                            .zIndex(if (isDragging) 1f else 0f),
                        onDragStart = {
                            draggingId = id
                            dragOffset = 0f
                        },
                        onDrag = { delta ->
                            dragOffset += delta

                            // 实时重排：以被拖动项的中心落在哪个格子为准。
                            // 不等松手 —— 其它项在拖动过程中就要让位。
                            val current = order.indexOf(id)
                            val centerPx = current * tabWidthPx + tabWidthPx / 2f + dragOffset
                            val target = (centerPx / tabWidthPx).toInt()
                                .coerceIn(0, order.lastIndex)

                            if (target != current) {
                                val next = order.toMutableList()
                                next.removeAt(current)
                                next.add(target, id)
                                order = next
                                // 序号变了，位移基准随之变化，必须补偿，
                                // 否则项会"跳"一下
                                dragOffset += (current - target) * tabWidthPx
                            }
                        },
                        onDragStop = {
                            draggingId = null
                            dragOffset = 0f
                            // 拖动刚结束的短时间内屏蔽点击：真机从拖到松手
                            // 可能补发落点事件，否则重排一次会顺带切一次页面
                            suppressClickUntil = System.currentTimeMillis() + 350L
                            // 松手即落盘。放在这里而不是拖动过程中，
                            // 是为了避免每一帧都写一次磁盘
                            onOrderChange(order)
                        },
                        onClick = {
                            if (System.currentTimeMillis() >= suppressClickUntil) {
                                // indexOfFirst 一旦返回 -1，onSelect(-1) 会落进
                                // when 的 else 分支，表现为"点一下跳到个人页"
                                val idx = items.indexOfFirst { it.id == id }
                                if (idx >= 0) onSelect(idx)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 胶囊形状。
 *
 * 50% 圆角在任意高度上都会得到标准胶囊，且它是 [CornerBasedShape]，
 * 满足折射 shader 对形状的要求（只支持圆角矩形体系，其它形状会抛异常）。
 */
private val GlassCapsuleShape = RoundedCornerShape(percent = 50)

/**
 * Dock 里的一个导航项。
 *
 * [id] 是**稳定标识**，与排列顺序无关。排序、选中态、持久化全部基于它 ——
 * 若用数组下标当标识，用户拖动重排一次之后，代码就会指到错误的项上。
 */
data class BottomBarItem(
    val id: String,
    val label: String,
    val icon: (@Composable (tint: Color) -> Unit)? = null,
)

/**
 * Dock 里的一个导航项，同时也是一个独立的拖动单元。
 *
 * 手势分工：
 *  - **长按后拖动** → 重排序。用 `detectDragGesturesAfterLongPress`，
 *    它要等长按超时才启动，所以普通点击不会被吞掉。
 *  - **普通点击** → 导航。
 *
 * 拖动过程中**不触发导航**：重排序与页面切换是两件独立的事，
 * 用户拖一次选项不应该顺带跳转页面。
 *
 * 项自己**不画选中高亮** —— 高亮已经提升到 Dock 层级统一绘制并做位移动画，
 * 这样切换时是"一枚胶囊滑过去"，而不是"两枚胶囊此消彼长"。
 */
@Composable
private fun DockTab(
    item: BottomBarItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = ReTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tint = if (selected) colors.accent else colors.labelSecondary
    var isDragging by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = Motion.snappy(),
        label = "dock-tab-press",
    )
    // 拖动时轻微抬升："被拿起来"这件事需要有物理感，
    // 否则项看起来只是在平面上滑动
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "dock-tab-lift",
    )

    Box(
        modifier
            .fillMaxHeight()
            .scale(pressScale * lift)
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragStop()
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragStop()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.x)
                    },
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (item.icon != null) {
                Box(Modifier.size(Sizes.IconSmall)) { item.icon(tint) }
            } else {
                Box(Modifier.size(6.dp).background(tint, CircleShape))
            }
            AppText(
                item.label,
                style = ReTheme.typography.caption2,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/**
 * 导航栏的返回按钮。
 *
 * 用「圆形底 + 尖角」而不是裸图标：玻璃导航栏背后的内容可能是一张封面
 * 或高对比的文字，裸图标在这种背景上会直接消失。圆形底提供了一个
 * 稳定的对比层，且在任何背景上都保持同一个可点区域。
 */
@Composable
fun TopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PressScale else 1f,
        animationSpec = Motion.snappy(),
        label = "back-press",
    )

    Box(
        modifier
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(colors.fillSecondary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcons.Back(colors.label, Modifier.size(15.dp))
    }
}
