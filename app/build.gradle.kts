import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cred(k: String): String = localProps.getProperty(k) ?: System.getenv(k) ?: ""

android {
    signingConfigs {
        create("release") {
            val kp = Properties().apply { load(rootProject.file("keystore.properties").inputStream()) }
            storeFile = file(kp["storeFile"] as String)
            storePassword = kp["storePassword"] as String
            keyAlias = kp["keyAlias"] as String
            keyPassword = kp["keyPassword"] as String
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = !project.hasProperty("noMinify")
            isShrinkResources = !project.hasProperty("noMinify")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    namespace = "com.hayate.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        ndk {
            abiFilters += if (project.hasProperty("emu")) listOf("arm64-v8a", "x86_64") else listOf("arm64-v8a")
        }
        applicationId = "com.hayate.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${cred("GOOGLE_CLIENT_ID")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"${cred("GOOGLE_CLIENT_SECRET")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            // signing handled externally
            optimization {
                enable = false
            }
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("io.github.qdsfdhvh:quickjs-kt:1.0.0-alpha15")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(project(":core"))
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.exo.core)
    implementation(libs.exo.hls)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}