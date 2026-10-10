// Top-level build file where you can add configuration options common to all sub-projects/modules.
// 注意：AGP 9 内置 Kotlin 支持，不要再应用 org.jetbrains.kotlin.android（与新 DSL 不兼容）。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
