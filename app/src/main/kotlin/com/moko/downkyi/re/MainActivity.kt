package com.moko.downkyi.re

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moko.downkyi.re.ui.AppRoot

/**
 * 唯一的 Activity。
 *
 * 整个应用是单 Activity + Compose 状态导航：界面切换全部由 Compose 承担，
 * 这样页面转场、玻璃材质与系统栏行为才能保持一致。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 依赖装配挂在 Application 上；Activity 只负责把它交给 UI。
        // 用 applicationContext 取容器，配置变更（转屏）不会重建网络层
        val container = (application as ReDownKyiApp).container

        setContent {
            AppRoot(container = container)
        }
    }
}
