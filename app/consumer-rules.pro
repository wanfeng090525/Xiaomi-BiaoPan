# R8 Consumer Rules for Watchface ID Tool
# These rules are applied during library compilation to reduce dex size

# 保留所有公共 API（防止外部库误优化）
-keep public class * extends android.app.Application
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.service.NotificationService

# 移除反射探测代码
-assumenosideeffects class android.util.Log {
    public static int v(...);
}

# 移除测试代码
-dontnote junit.**
-dontwarn junit.**
-dontnote org.junit.**
-dontwarn org.junit.**

# 优化压缩率
-optimizationpasses 5
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively
-printseeds /dev/null
