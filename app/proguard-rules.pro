# Watchface ID Tool - R8 Rules (Java 21 / Kotlin 2.1)
# 启用后 R8 会执行：类裁剪、方法内联、字符串折叠、Dead Code Elimination

# ---- 保护微验 SDK（libwyverify.so JNI 静态符号注册，类名/方法名不可混淆） ----
# native 方法符号为 Java_com_weiyan_sdk_WYVerify_native*，R8 混淆会导致
# UnsatisfiedLinkError；结果类字段由 JNI 反射 Set*Field 写入，同样必须保留。
-keep class com.weiyan.sdk.** { *; }
-dontwarn com.weiyan.sdk.**

# ---- Shizuku（反射调用，保留入口） ----
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }

# ---- T3 SDK（仅保留实际调用的方法，删除未使用代码） ----
-keep class com.watchface.idtool.oplusdk.SagVerify {
    public <fields>;
    public <methods>;
}
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3Result { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3LoginResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3NoticeResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3VersionResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3UpdateResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3QueryResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3VariableResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3CloudDocResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3CoreResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3OnlineResult { *; }
-keepclassmembers class com.watchface.idtool.oplusdk.SagVerify$T3AppSignResult { *; }

# 删除 T3Verify 中未被本项目调用的死方法（QQ/用户注册/变量等）
-dontwarn com.watchface.idtool.oplusdk.SagVerify

# ---- Compose（运行时反射需要） ----
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.SaveableComposable *;
}
-keep class androidx.compose.ui.** { *; }

# ---- 导航（路由字符串） ----
-keep class com.watchface.idtool.ui.** { *; }

# ---- 协程/Flow（反射需要） ----
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.internal.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- 保留 sealed class（运行时 instanceof 模式匹配） ----
-keep class com.watchface.idtool.ApkDownloader$DownloadState { *; }
-keep class com.watchface.idtool.PermissionStatus { *; }
-keep class com.watchface.idtool.SagAuthManager$AuthResult { *; }
-keep class com.watchface.idtool.SagAuthManager$NoticeVersion { *; }
-keep class com.watchface.idtool.LogKeyExtractor$ExtractResult { *; }
-keep class com.watchface.idtool.LogKeyExtractor$TokenInfo { *; }
-keep class com.watchface.idtool.WatchfaceParser$WatchfaceInfo { *; }
-keep class com.watchface.idtool.CloudConfigManager$CloudConfig { *; }
-keep class com.watchface.idtool.UiState { *; }
-keep class com.watchface.idtool.ImportedFile { *; }
-keep class com.watchface.idtool.WatchfaceRecord { *; }

# ---- R8 强优化开关 ----
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-useuniqueclassmembernames

# 字符串常量折叠（I18n 字典中大量字符串常量可被折叠）
-assumenosideeffects class java.lang.String {
    static java.lang.String valueOf(...);
}

# 保留调试日志（不移除 Log.d/i/w/v）
# -assumenosideeffects class android.util.Log {
#     public static int d(...);
#     public static int i(...);
#     public static int w(...);
#     public static int v(...);
# }

# ---- 进一步缩减 dex 体积 ----
# 去除无用资源引用
-dontnote org.json.**
-dontnote java.lang.invoke.**

# 压缩 JSON 相关类（仅保留必要的接口）
-keepclassmembers class org.json.** { *; }
-dontwarn org.json.**

# 移除 com.google.protobuf 警告（如果存在）
-dontwarn com.google.protobuf.**

# 消费者规则：让外部库也使用 R8 优化
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes BootstrapMethods

# 针对 SoundPool 音效优化
-keep class android.media.SoundPool { *; }
-dontwarn android.media.SoundPool
