package com.moko.downkyi.re.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moko.downkyi.re.core.designsystem.component.AppListRow
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextTertiary
import com.moko.downkyi.re.core.designsystem.component.GlassTopBar
import com.moko.downkyi.re.core.designsystem.component.TopBarBackButton
import com.moko.downkyi.re.core.designsystem.glass.GlassScaffold
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.data.AppContainer
import com.moko.downkyi.re.ui.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 工具箱。
 *
 * 本轮只「清理下载缓存」真实生效：扫描下载工作目录占用并一键清空
 * （分片、半成品、临时封面弹幕），不影响已发布到媒体库的成品。
 * 其余工具（格式转换 / 重新合并 / 弹幕转换 / 导出）依赖 FFmpeg，
 * 先以只读行展示，不做死按钮。
 */
@Composable
fun ToolboxScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var cleaning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun scan() {
        cacheBytes = withContext(Dispatchers.IO) { dirSize(container.downloadStorage.workRoot()) }
    }

    LaunchedEffect(Unit) { scan() }

    GlassScaffold(
        modifier = Modifier.fillMaxSize(),
        background = {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(ReTheme.spacing.screenEdge),
                verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.lg),
            ) {
                AppText("缓存与清理", style = ReTheme.typography.title3)
                AppListRow(
                    title = "清理下载缓存",
                    subtitle = when {
                        cacheBytes < 0 -> "正在计算…"
                        cacheBytes == 0L -> "工作目录已是空的"
                        else -> "分片与半成品占用 ${formatBytes(cacheBytes)}，清理不影响已发布成品"
                    },
                    trailing = {
                        AppText(
                            if (cacheBytes > 0) "清理" else "",
                            style = ReTheme.typography.subheadline,
                            color = if (cacheBytes > 0) ReTheme.colors.danger else ReTheme.colors.labelQuaternary,
                        )
                    },
                    showChevron = false,
                    showDivider = false,
                    onClick = {
                        if (cacheBytes > 0 && !cleaning) {
                            cleaning = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    container.downloadStorage.workRoot().deleteRecursively()
                                }
                                cleaning = false
                                scan()
                            }
                        }
                    },
                )

                AppText("媒体工具", style = ReTheme.typography.title3)
                AppTextTertiary("以下工具依赖 FFmpeg，后续版本接入", style = ReTheme.typography.footnote)
                AppListRow(
                    title = "格式转换",
                    subtitle = "无损封装 MP4 / MKV，或提取音频",
                    showChevron = false,
                    showDivider = false,
                    onClick = {},
                )
                AppListRow(
                    title = "重新合并音视频",
                    subtitle = "对已下载的流重新封装",
                    showChevron = false,
                    showDivider = false,
                    onClick = {},
                )
                AppListRow(
                    title = "弹幕格式转换",
                    subtitle = "XML ↔ ASS，可调字体与遮挡",
                    showChevron = false,
                    showDivider = false,
                    onClick = {},
                )
            }
        },
        overlay = { backdrop ->
            GlassTopBar(
                backdrop = backdrop,
                title = "工具箱",
                modifier = Modifier.align(Alignment.TopCenter),
                leading = { TopBarBackButton(onClick = onBack) },
            )
        },
    )
}

/** 递归计算目录大小。 */
private suspend fun dirSize(dir: File): Long = withContext(Dispatchers.IO) {
    if (!dir.exists()) return@withContext 0L
    var total = 0L
    dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
    total
}
