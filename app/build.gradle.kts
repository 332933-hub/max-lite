plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.maxlite.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.maxlite.app"
        minSdk = 26
        targetSdk = 34
        // CI передаёт реальные значения флагами -PversionName/-PversionCode
        // (из тега релиза), локальная сборка — просто "dev".
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = project.findProperty("versionName") as String? ?: "dev"
    }

    signingConfigs {
        getByName("debug") {
            // Закреплённый ключ, а не рандомный ~/.android/debug.keystore
            // раннера: иначе у каждой CI-сборки новая подпись, и Android
            // отказывается ставить новый APK поверх старого (тихо, без
            // внятной ошибки) — апдейт молча не происходит.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
}
