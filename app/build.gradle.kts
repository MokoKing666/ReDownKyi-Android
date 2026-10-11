plugins {
    // AGP 9.0 起内置 Kotlin 支持，不再需要单独应用 org.jetbrains.kotlin.android
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.moko.downkyi.re"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moko.downkyi.re"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        // 只出 arm64-v8a：2017 年后的 Android 设备基本都是这个架构，
        // 单 ABI 能显著减小体积（原项目同样只出 arm64-v8a）
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
        jniLibs {
            // 排除依赖 AAR 里可能夹带的其他 ABI 预编译库
            excludes += setOf(
                "lib/armeabi/**",
                "lib/armeabi-v7a/**",
                "lib/x86/**",
                "lib/x86_64/**",
                "lib/mips/**",
                "lib/mips64/**",
            )
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    // 自有模块
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    // 解析链路要用真实的接口层；:core:network 以 api 形式带出 :core:model
    implementation(project(":core:network"))
    // 下载链路：编排层与分片下载引擎
    implementation(project(":core:download"))
    // 任务持久化（下载任务快照落盘）
    implementation(libs.kotlinx.serialization.json)

    // Compose 平台（版本由 BOM 统一管理）
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Liquid Glass 渲染
    implementation(libs.kyant.backdrop)

    // 列表封面加载（Coil 3，Apache-2.0）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 扫码登录二维码生成（zxing，Apache-2.0）
    implementation(libs.zxing.core)

    // 轻量持久化：保存 Dock 的导航顺序
    implementation(libs.androidx.datastore.preferences)
}
