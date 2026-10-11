package com.moko.downkyi.re.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.GlassTopBar
import com.moko.downkyi.re.core.designsystem.component.TopBarBackButton
import com.moko.downkyi.re.core.designsystem.glass.GlassScaffold
import com.moko.downkyi.re.core.designsystem.theme.ReDownKyiTheme
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.core.model.SaveLocation
import com.moko.downkyi.re.data.AppContainer
import com.moko.downkyi.re.data.MediaLibraryRepository
import com.moko.downkyi.re.ui.detail.LoginScreen
import com.moko.downkyi.re.ui.detail.MediaListScreen
import com.moko.downkyi.re.ui.detail.ParseResultScreen
import com.moko.downkyi.re.ui.detail.ParseViewModel
import com.moko.downkyi.re.ui.detail.SearchScreen
import com.moko.downkyi.re.ui.detail.ToolboxScreen
import com.moko.downkyi.re.ui.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 应用根节点：主题 + 导航。
 *
 * 导航刻意没有用 Navigation Compose。这个应用只有两层（四个顶级标签 +
 * 若干全屏二级页），而玻璃材质要求「一级页面共享同一个采样源」。
 * 引用通用导航库反而要额外对抗它的状态管理；纯状态让
 * 「哪个标签在前台」「哪条二级路由打开」这两个问题一目了然。
 */
@Composable
fun AppRoot(container: AppContainer) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<AppRoute?>(null) }
    // 主题覆盖：null 表示跟随系统
    var darkOverride by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dark = darkOverride ?: isSystemInDarkTheme()

    ReDownKyiTheme(darkTheme = dark) {
        // 任务列表已接真实下载链路；媒体库接真实文件扫描；
        // 收藏夹 / 工具箱等仍由占位数据提供 —— 哪块接了真实数据哪块才覆盖占位。
        var library by remember {
            mutableStateOf(MediaLibraryRepository.Snapshot(emptyList(), 0L))
        }

        // 启动恢复登录态（cookie 从磁盘读回）
        LaunchedEffect(Unit) {
            container.restoreCookies()
        }

        // 账号信息接真实登录态：登录后取昵称，退出后清空。
        //
        // 登录态从**持久化流**读而不是直接读 cookies：cookies 是普通对象，
        // 登录成功落盘后不会触发重组，界面会一直停在"未登录"。
        // 占位数据里的"已登录 MokoKing"必须被真实值覆盖，
        // 否则未登录用户会看到别人的名字和 UID。
        val cookieHeader by container.cookieStore.headerFlow
            .collectAsStateWithLifecycle(initialValue = "")
        val loggedIn = cookieHeader.isNotEmpty() || !container.cookies.isEmpty
        var userName by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(loggedIn) {
            userName = if (loggedIn) container.api.navUserName() else null
        }

        // 媒体库在启动时扫一次，之后每次切到媒体库标签刷新。
        // 无媒体读取权限时系统对非自有文件返回空 —— 首次进入媒体库时请求。
        var refreshLibrary by remember { mutableStateOf(false) }
        LaunchedEffect(refreshLibrary) {
            if (refreshLibrary) {
                library = container.mediaLibraryRepository.scan()
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { granted ->
            if (granted.values.any { it }) {
                refreshLibrary = !refreshLibrary
            }
        }
        var permissionsRequested by remember { mutableStateOf(false) }

        // 媒体库在启动时扫一次，之后每次切到媒体库标签刷新
        LaunchedEffect(Unit) {
            library = container.mediaLibraryRepository.scan()
        }
        LaunchedEffect(selectedTab) {
            if (selectedTab == 2) {
                if (!permissionsRequested) {
                    permissionsRequested = true
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.READ_MEDIA_VIDEO,
                            android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_AUDIO,
                        ),
                    )
                }
                library = container.mediaLibraryRepository.scan()
            }
        }

        // 设置都是真实的：保存位置影响发布去向，默认清晰度/编码影响解析面板的初始选择
        val saveLocation by container.settingsStore.saveLocationFlow
            .collectAsStateWithLifecycle(initialValue = SaveLocation.DEFAULT)
        val defaultQn by container.settingsStore.defaultQnFlow
            .collectAsStateWithLifecycle(initialValue = 0)
        val defaultCodec by container.settingsStore.defaultCodecFlow
            .collectAsStateWithLifecycle(initialValue = "auto")

        val appState by container.downloadRepository.tasks
            .map { states ->
                val cards = states.values.map { it.toTaskCard() }
                SampleData.appState().copy(
                    tasks = cards.filter { !it.status.isTerminalUi },
                    finishedTasks = cards.filter { it.status.isTerminalUi },
                    libraryItems = library.items,
                    libraryTotalSize = formatBytes(library.totalBytes),
                    // 真实登录态覆盖占位账号
                    loggedIn = loggedIn,
                    userName = userName ?: container.cookies.userId,
                    userUid = container.cookies.userId,
                )
            }
            .collectAsStateWithLifecycle(initialValue = SampleData.appState())

        val currentRoute = route
        if (currentRoute == null) {
            val downloads = container.downloadRepository
            AppShell(
                state = appState,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onOpenRoute = { route = it },
                onToggleTheme = { darkOverride = !dark },
                isDark = dark,
                onTaskPause = downloads::pause,
                onTaskResume = downloads::retry,
                onTaskRetry = downloads::retry,
                onTaskDelete = downloads::delete,
                onTaskOpen = { taskId ->
                    // content:// 的成品直接用系统播放器/查看器打开；
                    // 应用目录内的文件路径需要 FileProvider，暂提示到媒体库
                    val location = downloads.publishedLocationOf(taskId)
                    when {
                        location?.startsWith("content://") == true -> {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(Uri.parse(location), "video/*")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            }.onFailure {
                                Toast.makeText(context, "无法打开：${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        location != null ->
                            Toast.makeText(context, "已保存在应用目录：$location", Toast.LENGTH_LONG).show()

                        else -> Toast.makeText(context, "成品还在发布中，稍后再试", Toast.LENGTH_SHORT).show()
                    }
                },
                saveLocation = saveLocation,
                onCycleSaveLocation = {
                    scope.launch {
                        val next = when (saveLocation) {
                            SaveLocation.GALLERY -> SaveLocation.APP_DIR
                            else -> SaveLocation.GALLERY
                        }
                        container.settingsStore.setSaveLocation(next)
                    }
                },
                defaultQn = defaultQn,
                onCycleDefaultQn = {
                    scope.launch {
                        // 智能(0) → 1080P(80) → 720P(64) → 480P(32) → 智能
                        val next = when (defaultQn) {
                            0 -> 80
                            80 -> 64
                            64 -> 32
                            else -> 0
                        }
                        container.settingsStore.setDefaultQn(next)
                    }
                },
                defaultCodec = defaultCodec,
                onCycleDefaultCodec = {
                    scope.launch {
                        val next = when (defaultCodec) {
                            "auto" -> "AVC"
                            "AVC" -> "HEVC"
                            else -> "auto"
                        }
                        container.settingsStore.setDefaultCodec(next)
                    }
                },
            )
        } else {
            DetailHost(
                route = currentRoute,
                container = container,
                initialSaveLocation = saveLocation,
                initialQn = defaultQn,
                initialCodec = defaultCodec,
                onBack = { route = null },
            )
        }
    }
}

/**
 * 二级页面路由。
 *
 * 用 sealed interface 而不是字符串：路由参数直接是类型化字段，
 * 拼错或漏传在编译期就会暴露。
 */
sealed interface AppRoute {
    data object Search : AppRoute
    data object Subscription : AppRoute
    data object Login : AppRoute
    data object Settings : AppRoute
    data object Toolbox : AppRoute

    /** 解析结果。携带用户**原始输入**（链接或编号），识别与请求都交给 ViewModel。 */
    data class ParseResult(val input: String) : AppRoute
    data class MediaList(val title: String) : AppRoute
}

/**
 * 二级页面宿主。
 *
 * 二级页面各自拥有**独立的采样源与顶部导航栏**，而不是嵌在标签页的
 * 采样层里 —— 嵌套采样会让玻璃采样到自己，那正是之前那次 native
 * 栈溢出崩溃的成因。
 */
@Composable
private fun DetailHost(
    route: AppRoute,
    container: AppContainer,
    initialSaveLocation: SaveLocation,
    initialQn: Int = 0,
    initialCodec: String = "auto",
    onBack: () -> Unit,
    onOpenRoute: (AppRoute) -> Unit = {},
) {
    when (route) {
        AppRoute.Search -> SearchScreen(
            api = container.api,
            loggedIn = !container.cookies.isEmpty,
            onBack = onBack,
            onOpenParse = { onOpenRoute(AppRoute.ParseResult(it)) },
        )

        AppRoute.Subscription -> DetailPlaceholder(
            title = "订阅更新",
            description = "支持 UP 主投稿 / 合集 / 收藏夹 / 番剧整季四类订阅。\n\n" +
                "每次只拉第 1 页做增量比对；首次检查只记录基准、不通知，避免刚订阅就收到一屏存量内容。",
            onBack = onBack,
        )

        AppRoute.Login -> LoginScreen(
            container = container,
            onBack = onBack,
            onLoggedIn = onBack,
        )

        AppRoute.Settings -> DetailPlaceholder(
            title = "设置",
            description = "默认清晰度与编码、并发与分片、保存位置、弹幕样式、字幕语言与 AI 策略、" +
                "合并方式、下载引擎、下载归档、订阅检查周期、文件命名模板。",
            onBack = onBack,
        )

        AppRoute.Toolbox -> ToolboxScreen(
            container = container,
            onBack = onBack,
        )

        is AppRoute.ParseResult -> ParseResultHost(
            input = route.input,
            container = container,
            initialSaveLocation = initialSaveLocation,
            initialQn = initialQn,
            initialCodec = initialCodec,
            onBack = onBack,
        )

        is AppRoute.MediaList -> MediaListScreen(
            title = route.title,
            api = container.api,
            onBack = onBack,
            onOpenParse = { onOpenRoute(AppRoute.ParseResult(it)) },
        )
    }
}

/**
 * 解析结果页的宿主：创建 ViewModel，并把状态与回调喂给页面。
 *
 * ViewModel 由 Activity 的 ViewModelStore 持有，返回首页再进来还是同一个实例；
 * 但链接可能已经变了，所以用 [LaunchedEffect] 以链接为键触发加载 ——
 * 同一个链接重复进入时，ViewModel 内部会跳过重复请求。
 */
@Composable
private fun ParseResultHost(
    input: String,
    container: AppContainer,
    initialSaveLocation: SaveLocation,
    initialQn: Int,
    initialCodec: String,
    onBack: () -> Unit,
) {
    val viewModel: ParseViewModel = viewModel(
        factory = ParseViewModel.factory(container.parseRepository),
    )
    LaunchedEffect(input) { viewModel.load(input) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 只负责「提交任务」这一次调用；真正的下载在 Application 级作用域里跑，
    // 用户离开本页不影响进行中的任务
    val scope = rememberCoroutineScope()

    ParseResultScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSelectPage = viewModel::selectPage,
        initialSaveLocation = initialSaveLocation,
        initialQn = initialQn,
        initialCodec = initialCodec,
        onStartDownload = { selection ->
            val detail = state.detail
            if (detail == null) {
                Toast.makeText(context, "视频信息还没就绪，请稍后再试", Toast.LENGTH_SHORT).show()
                return@ParseResultScreen
            }
            scope.launch {
                try {
                    container.downloadRepository.start(detail, selection)
                    Toast.makeText(
                        context,
                        "已开始下载 ${selection.pageIndices.size} 个分 P，可在「下载」页查看进度",
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 失败必须说人话：下载链路的错误大多来自逐分 P 的解析，笼统的
                    // 「下载失败」会让用户以为要重装应用
                    Toast.makeText(context, "下载未开始：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        },
    )
}

/** 尚未实现的二级页面占位。它让导航链路现在就能走通，本身不是最终形态。 */
@Composable
private fun DetailPlaceholder(
    title: String,
    description: String,
    onBack: () -> Unit,
) {
    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ReTheme.colors.background),
        // 占位页把标题与说明放在采样层里，玻璃导航栏会采样到它们的内容
        background = {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(
                        start = ReTheme.spacing.screenEdge,
                        end = ReTheme.spacing.screenEdge,
                        top = ReTheme.spacing.xxl + ReTheme.spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(ReTheme.spacing.sm),
            ) {
                AppText(title, style = ReTheme.typography.title1)
                AppTextSecondary(description)
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
