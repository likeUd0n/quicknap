plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.quicknap.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.quicknap.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.0-beta.3"
    }

    dependenciesInfo {
        // F-Droid 向けに Google Play 固有の依存メタデータブロックを除外する
        includeInApk = false
        includeInBundle = false
    }

    // 署名情報はリポジトリ外の ~/.gradle/gradle.properties に置く
    val storeFilePath = providers.gradleProperty("QUICKNAP_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("QUICKNAP_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("QUICKNAP_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("QUICKNAP_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
}
