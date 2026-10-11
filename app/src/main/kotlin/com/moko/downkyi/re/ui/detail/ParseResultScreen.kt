package com.moko.downkyi.re.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.component.AppCard
import com.moko.downkyi.re.core.designsystem.component.AppIcons
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.component.ChipGroup
import com.moko.downkyi.re.core.designsystem.component.CheckRow
import com.moko.downkyi.re.core.designsystem.component.GlassTopBar
import com.moko.downkyi.re.core.designsystem.component.RadioMark
import com.moko.downkyi.re.core.designsystem.component.RadioRow
import com.moko.downkyi.re.core.designsystem.component.SectionCard
import com.moko.downkyi.re.core.designsystem.component.SolidButton
import com.moko.downkyi.re.core.designsystem.component.TopBarBackButton
import com.moko.downkyi.re.core.designsystem.component.contentColor
import com.moko.downkyi.re.core.designsystem.glass.GlassBackdrop
import com.moko.downkyi.re.core.designsystem.glass.GlassScaffold
import com.moko.downkyi.re.core.designsystem.glass.glassSurface
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes
import com.moko.downkyi.re.core.model.AudioOption
import com.moko.downkyi.re.core.model.DownloadFlags
import com.moko.downkyi.re.core.model.PreferenceMode
import com.moko.downkyi.re.core.model.QualityOption
import com.moko.downkyi.re.core.model.QualityPlanner
import com.moko.downkyi.re.core.model.SaveLocation
import com.moko.downkyi.re.core.model.VideoCodec
import com.moko.downkyi.re.core.model.VideoDetail
import com.moko.downkyi.re.core.model.formatBytes
import com.moko.downkyi.re.core.model.formatCount
import com.moko.downkyi.re.core.model.formatDuration

/**
 * 解析结果页。
 *
 * 这是整个产品最核心的一屏：用户在这里决定「下什么规格」。设计上要同时
 * 满足两个互相拉扯的目标：
 *
 *  1. **选项必须完整**：清晰度、编码、音轨、下载内容、保存位置缺一不可，
 *     少任何一项用户都得回到设置页去改全局默认值；
 *  2. **默认必须是对的**：绝大多数用户不会逐项调整，直接点「开始下载」。
 *     因此智能选档默认开启，进去时已经按偏好选好了三项。
 *
 * 一屏里有两个加载态是**刻意保留**的：详情先到、档位后到。合成一个 loading
 * 会让"标题已经出来了、只是档位还在转"这种状态无从表达，用户看到的是一整屏
 * 空白然后突然全部出现。
 */
@Composable
fun ParseResultScreen(
    state: ParseUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onStartDownload: (ParseSelection) -> Unit,
    /** 设置里的默认保存位置 —— 用户改过设置后，这里不该再回到硬编码值。 */
    initialSaveLocation: SaveLocation = SaveLocation.DEFAULT,
    /** 设置里的默认清晰度（qn）；0 = 智能选档。 */
    initialQn: Int = 0,
    /** 设置里的默认编码："auto" | "AVC" | "HEVC"。 */
    initialCodec: String = "auto",
) {
    val spacing = ReTheme.spacing
    val insets = WindowInsets.systemBars.asPaddingValues()
    val dash = state.dash

    var mode by remember { mutableStateOf(PreferenceMode.BEST_QUALITY) }
    // 手动选择优先于智能选档；**换分 P 或换模式时清空** —— 新分 P 的档位集合
    // 可能完全不同，沿用旧选择等于把一个此处并不存在的档位当成已选。
    // 初始值来自设置：用户设了固定清晰度/编码时，进页面就已选中
    var manualQn by remember(state.dashPageIndex, mode) {
        mutableStateOf(initialQn.takeIf { it > 0 })
    }
    var manualCodec by remember(state.dashPageIndex, mode) {
        mutableStateOf(
            when (initialCodec) {
                "AVC" -> VideoCodec.AVC
                "HEVC" -> VideoCodec.HEVC
                else -> null
            },
        )
    }
    var manualAudio by remember(state.dashPageIndex, mode) { mutableStateOf<Int?>(null) }
    var flags by remember { mutableStateOf(DownloadFlags.DEFAULT) }
    var saveLocation by remember { mutableStateOf(initialSaveLocation) }
    var selectedPages by remember(state.detail?.bvid, state.detail?.epId) { mutableStateOf(setOf(0)) }

    val auto = remember(dash, mode) { dash?.let { QualityPlanner.smartPick(mode, it) } }
    val selectedQn = manualQn ?: auto?.qn ?: 0
    val codecs = state.options.firstOrNull { it.qn == selectedQn }?.codecs.orEmpty()
    val selectedCodec = manualCodec?.takeIf { codecs.contains(it) } ?: codecs.firstOrNull()
    val selectedAudioId = manualAudio ?: auto?.audioId ?: state.audioOptions.firstOrNull()?.id

    val estimateBytes = if (dash != null) {
        QualityPlanner.estimateFor(dash, state.durationMs, selectedQn, selectedCodec, selectedAudioId)
    } else {
        0L
    }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ReTheme.colors.backgroundGrouped),

        background = {
            when {
                // 也覆盖"还没有任何状态"的那一帧：ViewModel 的默认状态是
                // 空（既没加载也没内容），不兜住会闪一屏空白
                state.detail == null && state.message == null -> LoadingBody("正在解析链接…")
                state.message != null -> FailureBody(state.message, onRetry)
                else -> ParseBody(
                    state = state,
                    mode = mode,
                    onModeChange = { mode = it },
                    selectedQn = selectedQn,
                    onSelectQn = { manualQn = it },
                    codecs = codecs,
                    selectedCodec = selectedCodec,
                    onSelectCodec = { manualCodec = it },
                    selectedAudioId = selectedAudioId,
                    onSelectAudio = { manualAudio = it },
                    flags = flags,
                    onToggleFlag = { flag, enabled -> flags = DownloadFlags.with(flags, flag, enabled) },
                    saveLocation = saveLocation,
                    onSelectSaveLocation = { saveLocation = it },
                    selectedPages = selectedPages,
                    onTogglePage = { index ->
                        selectedPages = if (index in selectedPages) selectedPages - index else selectedPages + index
                    },
                    onSelectPage = onSelectPage,
                    estimateBytes = estimateBytes,
                )
            }
        },

        overlay = { backdrop ->
            GlassTopBar(
                backdrop = backdrop,
                title = "解析结果",
                modifier = Modifier.align(Alignment.TopCenter),
                leading = { TopBarBackButton(onClick = onBack) },
            )

            if (state.detail != null && !state.loading && state.message == null) {
                BottomActionBar(
                    backdrop = backdrop,
                    enabled = selectedPages.isNotEmpty() && estimateBytes > 0L,
                    count = selectedPages.size,
                    onClick = {
                        onStartDownload(
                            ParseSelection(
                                pageIndices = selectedPages.sorted(),
                                qn = selectedQn,
                                codec = selectedCodec,
                                audioId = selectedAudioId,
                                flags = flags,
                                saveLocation = saveLocation,
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        },
    )
}

/** 结果列表。加载档位时**不清空已有内容**，只让底部的预估体积变成"估算中"。 */
@Composable
private fun ParseBody(
    state: ParseUiState,
    mode: PreferenceMode,
    onModeChange: (PreferenceMode) -> Unit,
    selectedQn: Int,
    onSelectQn: (Int) -> Unit,
    codecs: List<VideoCodec>,
    selectedCodec: VideoCodec?,
    onSelectCodec: (VideoCodec) -> Unit,
    selectedAudioId: Int?,
    onSelectAudio: (Int) -> Unit,
    flags: Int,
    onToggleFlag: (Int, Boolean) -> Unit,
    saveLocation: SaveLocation,
    onSelectSaveLocation: (SaveLocation) -> Unit,
    selectedPages: Set<Int>,
    onTogglePage: (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
    estimateBytes: Long,
) {
    val spacing = ReTheme.spacing
    val insets = WindowInsets.systemBars.asPaddingValues()
    val detail = state.detail ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + Sizes.NavBarHeight + spacing.md,
            bottom = insets.calculateBottomPadding() + spacing.xxxl + 72.dp,
            start = spacing.screenEdge,
            end = spacing.screenEdge,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item { VideoInfoCard(detail) }

        item {
            if (detail.isMultiPage) {
                PageSelector(
                    detail = detail,
                    currentIndex = state.dashPageIndex,
                    selected = selectedPages,
                    onToggle = onTogglePage,
                    onActivate = onSelectPage,
                )
            }
        }

        item {
            PreferenceSection(
                mode = mode,
                onSelect = onModeChange,
                estimateText = when {
                    state.loadingDash -> "估算中…"
                    estimateBytes > 0L -> "约 ${formatBytes(estimateBytes)}"
                    else -> "—"
                },
            )
        }

        item {
            QualitySection(
                options = state.options,
                selected = selectedQn,
                onSelect = onSelectQn,
                loading = state.loadingDash,
                message = state.dashMessage,
            )
        }

        item {
            CodecSection(
                available = codecs,
                selected = selectedCodec,
                onSelect = onSelectCodec,
            )
        }

        item {
            AudioSection(
                options = state.audioOptions,
                selectedId = selectedAudioId,
                onSelect = onSelectAudio,
            )
        }

        item {
            ContentSection(
                flags = flags,
                onToggle = onToggleFlag,
            )
        }

        item {
            SaveLocationSection(
                selected = saveLocation,
                onSelect = onSelectSaveLocation,
            )
        }
    }
}

/** 视频信息：封面、标题、UP 主、播放与弹幕数。封面暂用占位图（图片加载未接入）。 */
@Composable
private fun VideoInfoCard(detail: VideoDetail) {
    val spacing = ReTheme.spacing
    AppCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(130.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(ReTheme.shapes.small))
                    .background(ReTheme.colors.fillTertiary),
                contentAlignment = Alignment.Center,
            ) {
                AppIcons.Play(ReTheme.colors.labelQuaternary, Modifier.size(20.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(detail.title, style = ReTheme.typography.headline, maxLines = 2)
                if (detail.ownerName.isNotEmpty()) {
                    AppTextSecondary(detail.ownerName, style = ReTheme.typography.footnote)
                }
                AppTextTertiary(
                    "${formatCount(detail.view)} 播放 · ${formatCount(detail.danmakuCount)} 弹幕 · " +
                        formatDuration(detail.durationMs),
                    style = ReTheme.typography.caption1,
                )
            }
        }
    }
}

/**
 * 分 P 选集。只在多 P 时出现。
 *
 * 勾上某一集就把它切成"当前查看"：用户勾 P3 时想要的正是"看看 P3 有什么档位"。
 * 反过来只切换高亮而不勾选，会让"看一眼"变成一次静默的取消选择。
 * 「全选 / 全不选」**不改变**当前查看的分 P —— 它是批量动作，不是导航。
 */
@Composable
private fun PageSelector(
    detail: VideoDetail,
    currentIndex: Int,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onActivate: (Int) -> Unit,
) {
    val spacing = ReTheme.spacing
    SectionCard(title = "选集（共 ${detail.pages.size} P）") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextSecondary("已选 ${selected.size} / ${detail.pages.size}")
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                TextAction("全选") { detail.pages.indices.forEach { onToggle(it) } }
                TextAction("全不选") { selected.forEach { onToggle(it) } }
            }
        }

        detail.pages.forEachIndexed { index, page ->
            CheckRow(
                checked = index in selected,
                title = page.part.ifBlank { "P${page.page}" },
                subtitle = buildString {
                    append(formatDuration(page.durationMs))
                    if (index == currentIndex) append(" · 当前查看")
                },
                onCheckedChange = { checked ->
                    onToggle(index)
                    if (checked) onActivate(index)
                },
            )
        }
    }
}

/**
 * 智能选档。
 *
 * 四种模式各自决定「清晰度 + 编码 + 音轨」三件事。用户表达意图，
 * 由系统翻译成技术参数 —— 这是这一屏最重要的减负设计，
 * 因为大部分用户并不清楚 AV1 和 HEVC 的区别。
 */
@Composable
private fun PreferenceSection(
    mode: PreferenceMode,
    onSelect: (PreferenceMode) -> Unit,
    estimateText: String,
) {
    val spacing = ReTheme.spacing
    SectionCard(title = "智能选档") {
        ChipGroup(
            options = PreferenceMode.entries,
            selected = mode,
            onSelect = onSelect,
            label = { it.label },
        )
        AppTextTertiary(mode.description, style = ReTheme.typography.caption1)

        Spacer(Modifier.height(spacing.xxs))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ReTheme.shapes.small))
                .background(ReTheme.colors.fillTertiary)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppTextSecondary("当前组合预估体积", style = ReTheme.typography.footnote)
            AppText(
                estimateText,
                style = ReTheme.typography.numeric,
                color = ReTheme.colors.accent,
            )
        }
        AppTextTertiary(
            "预估按「视频 + 音频」合计计算，误差通常在 5% 以内",
            style = ReTheme.typography.caption2,
        )
    }
}

/**
 * 清晰度。
 *
 * 列表来自接口返回的**真实档位**：`videos` 里没有、只在服务端声明里出现的
 * 档位会标明「需登录 / 大会员」而不是被隐藏 —— 隐藏会让用户以为视频本身
 * 只有 480P，明明充了会员也找不到入口。
 */
@Composable
private fun QualitySection(
    options: List<QualityOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    loading: Boolean,
    message: String?,
) {
    SectionCard(title = "清晰度") {
        when {
            options.isNotEmpty() -> options.forEach { option ->
                QualityRow(
                    label = option.label,
                    sizeText = option.sizeText,
                    selected = option.qn == selected,
                    onClick = { onSelect(option.qn) },
                )
            }

            loading -> AppTextTertiary("正在获取可下载的档位…", style = ReTheme.typography.caption1)
            else -> AppTextTertiary(message ?: "没有拿到可用的清晰度", style = ReTheme.typography.caption1)
        }

        AppTextTertiary(
            "只列出该视频真实支持的档位；高清晰度与会员内容需要登录",
            style = ReTheme.typography.caption2,
        )
    }
}

@Composable
private fun QualityRow(
    label: String,
    sizeText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ReTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ReTheme.shapes.small))
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = ReTheme.spacing.sm, vertical = ReTheme.spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.xs),
        ) {
            RadioMark(selected = selected)
            AppText(
                label,
                style = ReTheme.typography.body,
                color = if (selected) colors.accent else colors.label,
            )
        }
        AppText(
            sizeText,
            style = ReTheme.typography.numeric,
            color = colors.labelSecondary,
        )
    }
}

/** 视频编码。只在当前清晰度存在多种编码时出现。 */
@Composable
private fun CodecSection(
    available: List<VideoCodec>,
    selected: VideoCodec?,
    onSelect: (VideoCodec) -> Unit,
) {
    if (available.size <= 1 || selected == null) return
    SectionCard(title = "视频编码") {
        ChipGroup(
            options = available,
            selected = selected,
            onSelect = onSelect,
            label = { it.label },
        )
        AppTextTertiary(
            "AVC 兼容性最好；HEVC / AV1 同画质下体积更小，但部分老设备硬解不支持",
            style = ReTheme.typography.caption2,
        )
    }
}

/** 音轨。 */
@Composable
private fun AudioSection(
    options: List<AudioOption>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    if (options.isEmpty()) return
    SectionCard(title = "音频") {
        options.forEach { audio ->
            RadioRow(
                selected = audio.id == selectedId,
                title = audio.label,
                trailing = audio.bitrateText,
                onClick = { onSelect(audio.id) },
            )
        }
    }
}

/** 下载内容。允许只下附加资源，但会说明文件去向。 */
@Composable
private fun ContentSection(
    flags: Int,
    onToggle: (Int, Boolean) -> Unit,
) {
    SectionCard(title = "下载内容") {
        CheckRow(
            checked = DownloadFlags.has(flags, DownloadFlags.VIDEO),
            title = "视频",
            onCheckedChange = { onToggle(DownloadFlags.VIDEO, it) },
        )
        CheckRow(
            checked = DownloadFlags.has(flags, DownloadFlags.AUDIO),
            title = "音频",
            onCheckedChange = { onToggle(DownloadFlags.AUDIO, it) },
        )
        CheckRow(
            checked = DownloadFlags.has(flags, DownloadFlags.COVER),
            title = "封面",
            subtitle = "保存为图片",
            onCheckedChange = { onToggle(DownloadFlags.COVER, it) },
        )
        CheckRow(
            checked = DownloadFlags.has(flags, DownloadFlags.DANMAKU),
            title = "弹幕",
            subtitle = "格式：ASS 字幕（可在设置页改成 XML / TXT）",
            onCheckedChange = { onToggle(DownloadFlags.DANMAKU, it) },
        )
        CheckRow(
            checked = DownloadFlags.has(flags, DownloadFlags.SUBTITLE),
            title = "字幕",
            subtitle = "语言：简体中文（设置页可多选）",
            onCheckedChange = { onToggle(DownloadFlags.SUBTITLE, it) },
        )

        if (!DownloadFlags.hasAnyMedia(flags)) {
            AppTextTertiary(
                "只下封面 / 弹幕 / 字幕，不保存视频文件；这些文件会放进 Download 目录",
                style = ReTheme.typography.caption2,
            )
        }
    }
}

/** 保存位置。 */
@Composable
private fun SaveLocationSection(
    selected: SaveLocation,
    onSelect: (SaveLocation) -> Unit,
) {
    SectionCard(title = "保存位置") {
        ChipGroup(
            options = SaveLocation.entries,
            selected = selected,
            onSelect = onSelect,
            label = { it.label },
        )
        AppTextTertiary(
            when (selected) {
                SaveLocation.GALLERY ->
                    "视频 → Movies/DownKyi，图片 → Pictures/DownKyi，弹幕与字幕 → Download/DownKyi。系统相册在其它应用里也能直接看到。"
                SaveLocation.APP_DIR ->
                    "保存在应用专属目录，卸载应用会一并删除。需要长期保留时可在媒体库里导出到相册。"
                SaveLocation.CUSTOM ->
                    "保存到你指定的目录。请确保该目录有写入权限。"
            },
            style = ReTheme.typography.caption2,
        )
    }
}

/** 底部主操作。用厚玻璃：它悬浮在列表之上，且承载唯一的主路径。 */
@Composable
private fun BottomActionBar(
    backdrop: GlassBackdrop,
    enabled: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ReTheme.spacing
    val glass = ReTheme.glass
    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(
                backdrop = backdrop,
                style = glass.thick,
                shape = RoundedCornerShape(
                    topStart = ReTheme.shapes.extraLarge,
                    topEnd = ReTheme.shapes.extraLarge,
                ),
                supported = glass.supported,
            )
            .padding(horizontal = spacing.screenEdge, vertical = spacing.md)
            .padding(bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            AppText("已选 $count 项", style = ReTheme.typography.subheadline)
            AppTextTertiary("清晰度与编码可在上方调整", style = ReTheme.typography.caption2)
        }
        SolidButton(onClick = onClick, enabled = enabled) {
            AppText("开始下载", style = ReTheme.typography.headline, color = contentColor())
        }
    }
}

/** 解析中。 */
@Composable
private fun LoadingBody(text: String) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = insets.calculateTopPadding() + Sizes.NavBarHeight + ReTheme.spacing.lg,
                start = ReTheme.spacing.screenEdge,
                end = ReTheme.spacing.screenEdge,
            ),
        verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
    ) {
        AppText(text, style = ReTheme.typography.headline)
        AppTextTertiary("正在向 B 站请求视频信息…", style = ReTheme.typography.footnote)
    }
}

/**
 * 解析失败。
 *
 * 一定要给出**重试**入口：失败最常见的两个原因是网络抖动与风控，
 * 二者的正确处理都是"过一会儿再来一次"，而返回上一页再重新粘贴链接
 * 对用户来说成本太高。
 */
@Composable
private fun FailureBody(message: String, onRetry: () -> Unit) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = insets.calculateTopPadding() + Sizes.NavBarHeight + ReTheme.spacing.lg,
                start = ReTheme.spacing.screenEdge,
                end = ReTheme.spacing.screenEdge,
            ),
        verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.md),
    ) {
        AppText("解析失败", style = ReTheme.typography.title2)
        AppTextSecondary(message)
        SolidButton(onClick = onRetry) {
            AppText("重试", style = ReTheme.typography.headline, color = contentColor())
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    AppText(
        label,
        style = ReTheme.typography.subheadline,
        color = ReTheme.colors.accent,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
    )
}
