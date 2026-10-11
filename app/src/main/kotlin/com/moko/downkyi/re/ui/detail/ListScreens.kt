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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moko.downkyi.re.core.designsystem.component.AppIcons
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.component.ChevronIndicator
import com.moko.downkyi.re.core.designsystem.component.GlassTopBar
import com.moko.downkyi.re.core.designsystem.component.TopBarBackButton
import com.moko.downkyi.re.core.designsystem.glass.GlassScaffold
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.designsystem.theme.Sizes
import com.moko.downkyi.re.core.designsystem.theme.rounded
import com.moko.downkyi.re.core.model.MediaItem
import com.moko.downkyi.re.core.network.ApiException
import com.moko.downkyi.re.core.network.BiliApi
import kotlinx.coroutines.launch

/**
 * 「观看历史 / 稍后再看 / 收藏夹」共用的媒体列表页。
 *
 * 行点击后带着 bvid 走解析链路 —— 列表接口不返回分 P，详情由解析页
 * 重新请求。未登录时收藏夹/历史不可用，页面会明确提示。
 */
@Composable
fun MediaListScreen(
    title: String,
    api: BiliApi,
    onBack: () -> Unit,
    onOpenParse: (String) -> Unit,
) {
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsLogin by remember { mutableStateOf(false) }

    LaunchedEffect(title) {
        items = null
        error = null
        needsLogin = false
        runCatching {
            when (title) {
                "观看历史" -> api.history()
                "稍后再看" -> api.toView()
                "我的收藏夹" -> {
                    val folder = api.defaultFavoriteFolder()
                    if (folder <= 0L) {
                        needsLogin = true
                        emptyList()
                    } else {
                        api.favorites(folder).items
                    }
                }

                else -> emptyList()
            }
        }.onSuccess { items = it }
            .onFailure {
                error = it.message ?: "加载失败"
                needsLogin = (it as? ApiException)?.needLogin == true
            }
    }

    GlassScaffold(
        modifier = Modifier.fillMaxSize(),
        background = {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                when {
                    items == null && error == null -> LoadingState()
                    needsLogin -> MessageState("需要登录后才能查看，请先在「我的」页登录")
                    error != null -> MessageState(error ?: "加载失败")
                    else -> items?.let { list ->
                        if (list.isEmpty()) {
                            MessageState("这里还没有内容")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = Sizes.DockContentClearance + 24.dp,
                                    start = ReTheme.spacing.screenEdge,
                                    end = ReTheme.spacing.screenEdge,
                                ),
                                verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
                            ) {
                                items(list, key = { it.bvid.ifEmpty { "ep${it.epId}" } + it.cid }) { item ->
                                    MediaRow(item = item, onClick = { onOpenParse(item.bvid) })
                                }
                            }
                        }
                    }
                }
            }
        },
        overlay = { backdrop ->
            GlassTopBar(
                backdrop = backdrop,
                title = title,
                modifier = Modifier.align(Alignment.TopCenter),
                leading = { TopBarBackButton(onClick = onBack) },
            )
        },
    )
}

/** 站内搜索。输入法搜索键或搜索按钮触发；结果与媒体列表同一种行。 */
@Composable
fun SearchScreen(
    api: BiliApi,
    loggedIn: Boolean,
    onBack: () -> Unit,
    onOpenParse: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun doSearch() {
        if (query.isBlank()) return
        loading = true
        error = null
        runCatching { api.searchVideo(query.trim()) }
            .onSuccess {
                results = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "搜索失败"
                loading = false
            }
    }

    // 触发器 +1 就搜一次：键盘搜索键与图标点击共用同一条路径
    LaunchedEffect(searchTrigger) {
        if (searchTrigger > 0) doSearch()
    }

    GlassScaffold(
        modifier = Modifier.fillMaxSize(),
        background = {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ReTheme.spacing.screenEdge,
                            end = ReTheme.spacing.screenEdge,
                            // 顶栏是悬浮的：输入行必须让出它的高度，否则会顶到标题上
                            top = ReTheme.spacing.xxl + ReTheme.spacing.lg,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(ReTheme.shapes.rounded(ReTheme.shapes.large))
                            .background(ReTheme.colors.fillTertiary)
                            .padding(horizontal = ReTheme.spacing.md),
                        textStyle = ReTheme.typography.body.copy(color = ReTheme.colors.label),
                        cursorBrush = SolidColor(ReTheme.colors.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { searchTrigger += 1 },
                        ),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    AppText(
                                        "搜索视频",
                                        style = ReTheme.typography.subheadline,
                                        color = ReTheme.colors.placeholder,
                                    )
                                }
                                inner()
                            }
                        },
                    )

                    // 可点的搜索按钮：键盘的搜索键不是所有输入法都会给，
                    // 只靠 IME action 会让一部分用户"找不到怎么搜"
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(ReTheme.shapes.rounded(ReTheme.shapes.large))
                            .background(ReTheme.colors.fillTertiary)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = { searchTrigger += 1 },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcons.Search(ReTheme.colors.accent, Modifier.size(Sizes.IconSmall))
                    }
                }

                Spacer(Modifier.height(8.dp))

                when {
                    loading -> LoadingState()
                    error != null -> MessageState(error ?: "搜索失败")
                    results != null -> {
                        val list = results.orEmpty()
                        if (list.isEmpty()) {
                            // 未登录时 B 站搜索接口经常返回空结果而非报错 ——
                            // 静默显示"没有找到"会让用户以为应用坏了
                            MessageState(
                                if (loggedIn) {
                                    "没有找到相关视频"
                                } else {
                                    "没有找到相关视频。未登录时搜索常被返回空结果，请先到「我的」页登录"
                                },
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = ReTheme.spacing.screenEdge,
                                    end = ReTheme.spacing.screenEdge,
                                    bottom = Sizes.DockContentClearance + 24.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
                            ) {
                                items(list, key = { it.bvid.ifEmpty { "ep${it.epId}" } + it.cid }) { item ->
                                    MediaRow(item = item, onClick = { onOpenParse(item.bvid) })
                                }
                            }
                        }
                    }
                }
            }
        },
        overlay = { backdrop ->
            GlassTopBar(
                backdrop = backdrop,
                title = "站内搜索",
                modifier = Modifier.align(Alignment.TopCenter),
                leading = { TopBarBackButton(onClick = onBack) },
            )
        },
    )
}

@Composable
private fun LoadingState() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = ReTheme.colors.accent,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun MessageState(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, start = ReTheme.spacing.screenEdge, end = ReTheme.spacing.screenEdge),
        contentAlignment = Alignment.Center,
    ) {
        AppTextSecondary(text, style = ReTheme.typography.body)
    }
}

/**
 * 媒体列表行：封面 + 标题 + UP 主与时长。
 *
 * 封面用 Coil 异步加载（磁盘缓存），加载失败显示占位底色 ——
 * 列表行不能因为网络图挂了而整行塌掉。
 */
@Composable
private fun MediaRow(item: MediaItem, onClick: () -> Unit) {
    val colors = ReTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(ReTheme.shapes.rounded(ReTheme.shapes.medium))
            .background(colors.fillTertiary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 110.dp, height = 62.dp)
                .clip(ReTheme.shapes.rounded(ReTheme.shapes.extraSmall))
                .background(colors.fillSecondary),
        ) {
            if (item.cover.isNotEmpty()) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    AppIcons.Play(colors.labelQuaternary, Modifier.size(18.dp))
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 62.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AppText(item.title, style = ReTheme.typography.subheadline, maxLines = 2)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.ownerName.isNotEmpty()) {
                    AppTextTertiary(item.ownerName, style = ReTheme.typography.footnote, maxLines = 1)
                }
                if (item.durationMs > 0) {
                    AppTextTertiary(formatDuration(item.durationMs), style = ReTheme.typography.footnote)
                }
            }
        }

        ChevronIndicator(tint = colors.labelQuaternary)
    }
}

/** 时长格式化：小时位为 0 时省略。 */
private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
