plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.moko.downkyi.re.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 用 api 而非 implementation：本模块的公开 API 直接返回 core:model 的类型
    // （DashInfo / VideoDetail），调用方需要能看见它们
    api(project(":core:model"))

    // HTTP。用 OkHttp 而不是 HttpURLConnection：分片下载需要连接池复用、
    // 细粒度的超时控制（读空闲 vs 等响应头），以及可靠的取消语义。
    implementation(libs.okhttp)

    // JSON。不使用 @Serializable 代码生成 —— B 站接口返回的字段位置与类型
    // 经常不一致，代码生成遇到脏数据会直接抛异常，手动访问才能逐字段兜底。
    implementation(libs.kotlinx.serialization.json)

    // 协程：把 OkHttp 的回调桥接成挂起函数，并保证取消能真正中断底层请求
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
