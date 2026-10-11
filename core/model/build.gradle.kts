plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.moko.downkyi.re.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 纯领域模型模块：无界面、无网络、不依赖 Android 框架类，
    // 因此全部逻辑都能用 JVM 单元测试覆盖。
}

dependencies {
    // JSON 树访问。
    //
    // 刻意不使用 @Serializable 代码生成：B 站接口返回的 JSON 字段位置与类型
    // 经常不一致（同一个 cid 在不同接口里出现在 4 个不同位置、番剧条目没有 bvid、
    // duration 有时是秒有时是毫秒）。代码生成遇到脏数据会直接抛异常，
    // 而手动访问可以逐字段容错与兜底。
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
