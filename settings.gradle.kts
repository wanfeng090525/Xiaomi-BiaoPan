pluginManagement {
    repositories {
        // Termux 本地仓库:仅在路径存在时使用(便携化处理)
        if (file("/data/data/com.termux/files/home/.gradle/local-maven").isDirectory) {
            maven { url = uri("file:///data/data/com.termux/files/home/.gradle/local-maven") }
        }
        maven { url = uri("https://dl.google.com/dl/android/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (file("/data/data/com.termux/files/home/.gradle/local-maven").isDirectory) {
            maven { url = uri("file:///data/data/com.termux/files/home/.gradle/local-maven") }
        }
        maven { url = uri("https://dl.google.com/dl/android/maven2") }
        mavenCentral()
    }
}

rootProject.name = "WatchfaceIdTool"
include(":app")
