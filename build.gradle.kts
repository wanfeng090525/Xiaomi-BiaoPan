buildscript {
    repositories {
        maven { url = uri("https://dl.google.com/dl/android/maven2") }
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.20")
    }
}
