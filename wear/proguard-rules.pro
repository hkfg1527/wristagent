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

# Keep enum valueOf/values: settings are parsed from stored strings.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
