buildscript {
    repositories {
        maven { url = uri("https://dl.google.com/dl/android/maven2") }
        mavenCentral()
    }
    dependencies {
        // AGP 9.1.0：AndroidLiquidGlass (io.github.kyant0:backdrop) 为 Compose
        // Multiplatform 构建，传递依赖 JetBrains Compose 1.12 / lifecycle 2.11，
        // 要求 AGP >= 9.1 且 compileSdk >= 37。
        classpath("com.android.tools.build:gradle:9.1.0")
        // Kotlin 2.4.20：backdrop 2.0.1 由 Kotlin 2.4 构建，低版本编译器读不了
        // 更高版本的 Kotlin 元数据（incompatible metadata version）。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20")
    }
}
