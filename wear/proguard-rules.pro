# Kotlin coroutines register internal services through META-INF/services (ServiceLoader).
# R8 strips them by default, which breaks Dispatchers.Main wiring on release builds.
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.** { *; }
-keep class kotlinx.coroutines.android.** { *; }

# Shizuku binds through its own provider/permission classes and API_V23 checks.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# AIDL stub for the privileged UserService (bound from the shell process).
-keep class com.example.myapplication.agent.privilege.IUserService { *; }
-keep class com.example.myapplication.agent.privilege.IUserService$Stub { *; }
-keep class com.example.myapplication.agent.privilege.IUserService$Stub$Proxy { *; }
-keep class com.example.myapplication.agent.privilege.UserService { *; }

# 发布包剥离调试日志：既省体积，也防止以后有人顺手在热路径里打日志把用户内容带进 release。
# 只剥 d/v，保留 w/e —— 控制台端口绑定失败这类生产排障日志必须留在包里。
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Keep enum valueOf/values: settings are parsed from stored strings.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
