package com.moko.downkyi.re

import android.app.Application
import com.moko.downkyi.re.data.AppContainer

/**
 * 应用入口。
 *
 * 依赖装配（网络层 / 解析仓储 / 后续的数据库与下载引擎）挂在 [container] 上，
 * UI 层从这里取实例，不允许自己 new —— 见 [AppContainer] 的说明。
 *
 * 用 `by lazy` 而不是在 `onCreate` 里直接构造：首个真正需要网络的页面
 * 才会触发 OkHttp 的初始化，冷启动不必为它付代价。
 */
class ReDownKyiApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
