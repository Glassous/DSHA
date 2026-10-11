plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin：无需 kotlin-android；只剩 Compose 一个独立的 Kotlin 编译器插件
    alias(libs.plugins.kotlin.compose)
}

// 版本号的唯一事实来源：GitHub Release 的 tag 即 "v" + versionName，
// 更新检测也拿它与应用自身的 versionName 比较，因此这里改了，发版时的 tag 要跟着改。
val appVersionName = "1.0.0"

/** 发布产物命名：DSHA-v1.0.0.apk（debug 加后缀），与 tag 一一对应，上传前不必再改名。 */
fun apkFileName(buildType: String?): String =
    if (buildType == "release") "DSHA-v$appVersionName.apk" else "DSHA-v$appVersionName-$buildType.apk"

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
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 调试包使用独立应用标识：可与正式版共存安装，本地数据各存各的，互不覆盖。
            // 应用名在 src/debug/res 中覆盖为 "DSHA Debug"，同样按 build type 区分。
            applicationIdSuffix = ".debug"
        }
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

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(apkFileName(variant.buildType))
        }
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

    testImplementation(libs.junit)
}
