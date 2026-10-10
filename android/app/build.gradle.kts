plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin：无需 kotlin-android；只剩 Compose 一个独立的 Kotlin 编译器插件
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.glassous.dsha"
    // Compose 1.12 / core-ktx 1.19 起要求 compileSdk 至少 37（本机已安装 android-37.0）
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.glassous.dsha"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.kotlinx.coroutines.android)
}
