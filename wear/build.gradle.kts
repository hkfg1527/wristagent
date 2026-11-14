import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing credentials come from keystore.properties, which is git-ignored.
// Without it (CI, fresh clone) the release build type falls back to debug signing so
// the project still builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val releaseKeystoreFile = keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it) }
val hasReleaseSigning = releaseKeystoreFile != null && releaseKeystoreFile.exists()

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    // 实验包：-PcompatMinSdk=30 出给非 Wear OS / API 30 手表试装；默认仍是 33 的主包
    val compatMinSdk = providers.gradleProperty("compatMinSdk").orNull?.toIntOrNull()

    defaultConfig {
        applicationId = "dev.ghr.wristagent"
        minSdk = compatMinSdk ?: 33
        targetSdk = 37
        manifestPlaceholders["wearableLibRequired"] = (compatMinSdk == null).toString()
        versionCode = 54
        versionName = "0.0.0.16"
        // 任何密钥都不参与构建：API key 只能由用户在应用设置里填，进 SharedPreferences。
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
        buildConfig = true
        // Shizuku UserService 的 IUserService 走 AIDL 生成 Stub，AGP 8+ 默认关闭该开关。
        aidl = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        // 单测直接构造 Application/AndroidViewModel：框架方法返回默认值而不是抛 Stub!
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    debugImplementation(libs.androidx.wear.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.nanohttpd)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}