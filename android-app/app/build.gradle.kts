plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val ciVersionCode = providers.gradleProperty("CI_VERSION_CODE").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.gradleProperty("CI_VERSION_NAME").orNull ?: "0.1.$ciVersionCode"
val updateRepoOwner = providers.gradleProperty("UPDATE_REPO_OWNER").orNull ?: "Perdonus"
val updateRepoName = providers.gradleProperty("UPDATE_REPO_NAME").orNull ?: "ruclaw"
val updateApkAssetName = providers.gradleProperty("UPDATE_APK_ASSET_NAME").orNull ?: "ruclaw-android-release.apk"
val updateShaAssetName = providers.gradleProperty("UPDATE_SHA256_ASSET_NAME").orNull ?: "ruclaw-android-release.apk.sha256"
val generatedRuntimeAssetsDir = providers.environmentVariable("RUCLAW_ANDROID_RUNTIME_ASSETS_DIR")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.perdonus.ruclaw.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.perdonus.ruclaw.android"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "DEFAULT_LAUNCHER_URL", "\"http://192.168.1.109:18800\"")
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"$updateRepoOwner\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"$updateRepoName\"")
        buildConfigField("String", "UPDATE_APK_ASSET_NAME", "\"$updateApkAssetName\"")
        buildConfigField("String", "UPDATE_SHA256_ASSET_NAME", "\"$updateShaAssetName\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            if (generatedRuntimeAssetsDir != null) {
                assets.srcDir(generatedRuntimeAssetsDir)
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.google.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.commonmark)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
