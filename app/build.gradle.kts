import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    // 注意：AGP 9.0 起 Kotlin 支持已内建，不能再 apply 'org.jetbrains.kotlin.android'。
    // Compose 编译器插件仍然需要单独 apply。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.watchface.idtool"
    // compileSdk 必须 >= 37：backdrop 2.0.1 传递依赖的 compose ui / lifecycle 2.11
    // 都要求在 37 或更高版本上编译。
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.watchface.idtool"
        minSdk = 24
        targetSdk = 37
        versionCode = 39
        versionName = "3.9"
        ndk {
            // 微验 SDK 仅提供 arm64-v8a 的 libwyverify.so
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/watchface.jks")
            storePassword = "android"
            keyAlias = "watchface-key"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            // R8 开关：CI 手动触发时可传 -Pr8=false 关闭混淆压缩以提速，
            // 默认（本地或未传参）保持开启。
            val r8Enabled = (project.findProperty("r8") as? String)
                ?.toBooleanStrictOrNull() ?: true
            isMinifyEnabled = r8Enabled
            // shrinkResources 依赖 minify，必须同步开关
            isShrinkResources = r8Enabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,NOTICE}"
            excludes += "/META-INF/versions/9/**"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// AGP 9.0 起 Kotlin 由 AGP 内建，kotlin { } 块提升到顶层。
// kotlinOptions 在 Kotlin 2.4 已移除，统一用 compilerOptions。
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    // 版本随 AGP 9.1.0 + compileSdk 37 一同抬升：backdrop 2.0.1 的传递依赖
    implementation("androidx.core:core-ktx:1.19.0")
    // 启动屏（KernelSU 同款）：Theme.SplashScreen + installSplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // AndroidLiquidGlass（Kyant0/backdrop）：真实液态玻璃（背景折射 + vibrancy + lens）
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
