package com.moko.downkyi.re.data

import android.content.Context
import com.moko.downkyi.re.core.download.DownloadManager
import com.moko.downkyi.re.core.network.BiliApi
import com.moko.downkyi.re.core.network.BiliCookies
import com.moko.downkyi.re.core.network.BiliHttp
import com.moko.downkyi.re.download.AndroidMuxer
import com.moko.downkyi.re.download.DownloadStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

/**
 * 依赖装配。
 *
 * 只有一处地方允许 `new` 出网络层与仓储，UI 层一律拿现成的实例 —— 界面里
 * 随手 new 一个 [BiliHttp] 的后果不是崩，而是**每个页面各自持有一份 Cookie**：
 * 登录页写进去的 SESSDATA，解析页看不到，于是"登录了还是要不了 1080P"。
 * 这类 bug 排查起来极其费时，直接堵住来源更省事。
 *
 * 生命周期与 Application 一致：连接池、Cookie 都需要跨页面存活。
 */
class AppContainer(context: Context) {

    /**
     * 持有 Application 级的 Context。
     *
     * 现在只有 Cookie 的持久化会用它，但它的作用是避免后面几个模块
     * （下载任务的数据库、通知渠道）各自再去拿一次 Context —— 那会让
     * 装配点从一处变成四处。
     */
    private val appContext: Context = context.applicationContext

    /** Cookie 是全应用唯一一份，网络层与应用层共享同一个实例。 */
    val cookies: BiliCookies = BiliCookies()

    val http: BiliHttp = BiliHttp(cookies = cookies)

    val api: BiliApi = BiliApi(http)

    /** Cookie 持久化：登录态跨进程存活。 */
    val cookieStore = CookieStore(appContext)

    /** 启动时从磁盘恢复登录态。在 AppRoot 首次组合时调用一次。 */
    suspend fun restoreCookies() {
        val saved = cookieStore.load()
        if (saved.isNotEmpty()) cookies.setHeader(saved)
    }

    /** 登录成功后落盘。 */
    suspend fun persistLogin() {
        cookieStore.save(cookies.header)
    }

    /** 退出登录：清内存 + 清磁盘。 */
    suspend fun clearLogin() {
        cookies.clear()
        cookieStore.clear()
    }

    val parseRepository: ParseRepository = ParseRepository(api)

    // -----------------------------------------------------------------------
    // 下载链路
    // -----------------------------------------------------------------------

    /**
     * 下载链路自己的作用域：任务必须**独立于任何页面**存活 ——
     * 用户离开解析页、甚至旋转屏幕销毁 Activity，下载都不能跟着死。
     * 挂在 viewModelScope 上是最常见的错误接法。
     */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val downloadStorage: DownloadStorage = DownloadStorage(appContext)

    /** 任务快照持久化：进程重启后未完成任务以「已暂停」状态恢复。 */
    internal val taskPersistence = TaskPersistence(appContext)

    /** 媒体库真实数据源：扫描本应用发布到系统媒体库的文件。 */
    val mediaLibraryRepository = MediaLibraryRepository(appContext)

    /** 应用设置：保存位置等。 */
    val settingsStore = SettingsStore(appContext)

    /**
     * 分片下载引擎的编排层。
     *
     * 客户端用下载专用的：整体 callTimeout 会误杀长分片；连接池按分片
     * 并发度（[SEGMENT_COUNT]）放大，否则并发分片会排队、速度上不去。
     */
    val downloadManager: DownloadManager = DownloadManager(
        client = http.newDownloadClient(maxConnectionsPerHost = SEGMENT_COUNT),
        headers = { http.downloadHeaders() },
        muxer = AndroidMuxer,
        segmentCount = SEGMENT_COUNT,
        parentScope = downloadScope,
    )

    val downloadRepository: DownloadRepository = DownloadRepository(
        parse = parseRepository,
        api = api,
        http = http,
        manager = downloadManager,
        storage = downloadStorage,
        persistence = taskPersistence,
        parentScope = downloadScope,
    )

    /** 关闭连接池与下载作用域。进程退出时调用，避免线程吊住进程。 */
    fun shutdown() {
        downloadScope.coroutineContext.cancelChildren()
        http.shutdown()
    }

    private companion object {
        const val SEGMENT_COUNT = 8
    }
}
