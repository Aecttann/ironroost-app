import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val releaseVersionCode = providers.gradleProperty("releaseVersionCode").map(String::toInt).orElse(1)
val releaseVersionName = providers.gradleProperty("releaseVersionName").orElse("1.0.0")

// Release signing is driven entirely by the environment, so no key material lives in the repo.
val signingEnvironment = mapOf(
    "ANDROID_KEYSTORE_PATH" to providers.environmentVariable("ANDROID_KEYSTORE_PATH"),
    "ANDROID_KEYSTORE_PASSWORD" to providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD"),
    "ANDROID_KEY_ALIAS" to providers.environmentVariable("ANDROID_KEY_ALIAS"),
    "ANDROID_KEY_PASSWORD" to providers.environmentVariable("ANDROID_KEY_PASSWORD")
)
val configuredSigningValues = signingEnvironment.filterValues { it.isPresent }
check(configuredSigningValues.isEmpty() || configuredSigningValues.size == signingEnvironment.size) {
    val missing = signingEnvironment.filterValues { !it.isPresent }.keys.joinToString()
    "Release signing configuration is incomplete. Missing: $missing"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.google.mobileAds)
    implementation(libs.google.ump)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.aectann.battlecity"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.aectann.battlecity"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = releaseVersionCode.get()
        versionName = releaseVersionName.get()
        resourceConfigurations += listOf("en", "uk", "de", "hi", "ja", "ko", "tr", "zh")
    }

    signingConfigs {
        if (configuredSigningValues.size == signingEnvironment.size) {
            create("release") {
                storeFile = file(signingEnvironment.getValue("ANDROID_KEYSTORE_PATH").get())
                storePassword = signingEnvironment.getValue("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = signingEnvironment.getValue("ANDROID_KEY_ALIAS").get()
                keyPassword = signingEnvironment.getValue("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "MENU_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "RESURRECTION_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5354046379\"")
            buildConfigField("String", "STREAK_FREEZE_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5354046379\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-4014372145678923~7542987599"
            buildConfigField("String", "MENU_BANNER_AD_UNIT_ID", "\"ca-app-pub-4014372145678923/4235221412\"")
            buildConfigField("String", "RESURRECTION_AD_UNIT_ID", "\"ca-app-pub-4014372145678923/3029027517\"")
            buildConfigField("String", "STREAK_FREEZE_AD_UNIT_ID", "\"ca-app-pub-4014372145678923/7850857780\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
