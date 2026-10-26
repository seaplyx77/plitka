import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Ключ и пароли лежат вне проекта: G:/Android/plitka-release.jks и G:/Android/keystore.properties.
// Файл в корне проекта поддерживается для старых копий, но хранить пароли там не нужно.
val keystoreProps = Properties().apply {
    val candidates = listOf(
        rootProject.file("keystore.properties"),
        rootProject.projectDir.parentFile?.resolve("keystore.properties")
    )
    candidates.firstOrNull { it != null && it.exists() }?.inputStream()?.use { load(it) }
}
val hasReleaseKey = !keystoreProps.getProperty("storeFile").isNullOrBlank()

android {
    namespace = "com.plitka.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.plitka.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        // Сборка с тем же ключом, что у уже установленной debug-версии:
        // ставится поверх без удаления и без потери плиток.
        create("upgrade") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
        create("upgrade") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("upgrade")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
}
