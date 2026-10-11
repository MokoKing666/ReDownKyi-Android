plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.moko.downkyi.re.core.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 设计系统是全部界面模块的地基，因此对外暴露 Compose 与玻璃渲染的 API
    implementation(platform(libs.compose.bom))

    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.animation)

    // Liquid Glass 渲染。
    // 刻意用 api 而非 implementation 之外的可见性控制 —— 但**业务模块不应直接引用**，
    // 必须经由本模块封装的玻璃 API，这样才能在后续更换渲染方案时只改一处。
    api(libs.kyant.backdrop)
    api(libs.kyant.shapes)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}
