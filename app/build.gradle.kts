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
        create("release") {
            // Ключ в репозиторий не кладём: CI достаёт его из GitHub Secrets
            // во временный файл и передаёт путь/пароли через переменные
            // окружения. Без них release-сборка технически возможна собрать,
            // но подписать нечем — так и задумано, релизный ключ не должен
            // валяться в системе просто так.
            storeFile = file(System.getenv("RELEASE_KEYSTORE_PATH") ?: "release.keystore")
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
