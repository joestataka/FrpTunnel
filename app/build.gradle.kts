import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 从本机 local.properties（已在 .gitignore 中）读取可选的默认服务器地址，
// 不写入仓库，避免把私有服务器信息提交到公开代码库。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun localString(key: String): String =
    (localProps.getProperty(key) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "io.github.joestataka.frptunnel"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.joestataka.frptunnel"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            // 内置的 frp-panel 为 linux/arm64 静态二进制，仅支持 arm64 设备
            abiFilters += "arm64-v8a"
        }

        // 可选的默认服务器地址。仓库内为空字符串，用户需在粘贴的命令中提供，
        // 或由使用者在 local.properties / CI Secrets 中自行注入。
        buildConfigField("String", "FRP_DEFAULT_API_URL", "\"${localString("frp.defaultApiUrl")}\"")
        buildConfigField("String", "FRP_DEFAULT_RPC_URL", "\"${localString("frp.defaultRpcUrl")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 个人项目：release 复用 debug 签名，便于 CI 直接产出可安装的 release APK
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // 关键：把 libfrppanel.so 解压到 nativeLibraryDir，才能被 exec
            useLegacyPackaging = true
            // 不要 strip，否则会破坏这个可执行二进制
            keepDebugSymbols += "**/libfrppanel.so"
        }
    }
}

// 仅在 aarch64 主机（如 proot/ARM 环境）上强制使用 ARM64 版 aapt2；
// x86_64 的 CI（GitHub Actions）使用官方默认版本。
if (System.getProperty("os.arch").contains("aarch64")) {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${'$'}{requested.version}:linux-aarch64")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}