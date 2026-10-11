// Re：哔哩下载姬 —— 根构建脚本
// 只声明插件，不在这里加依赖（各模块自己的 build.gradle.kts 负责）

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
