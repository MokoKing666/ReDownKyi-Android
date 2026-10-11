plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.moko.downkyi.re.core.download"
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
    implementation(project(":core:model"))
    implementation(project(":core:network"))

    implementation(libs.kotlinx.coroutines.android)
    // api 而非 implementation：DownloadManager 的公共签名里出现了 OkHttpClient，
    // 装配它的模块必须能在编译期看到这个类型
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
