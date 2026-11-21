import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safeargs)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.xick.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xick.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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
    implementation(libs.core)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.fragment)
    implementation(libs.constraintlayout)
    implementation(libs.coordinatorlayout)
    implementation(libs.swiperefreshlayout)
    implementation(libs.viewpager2)
    implementation(libs.recyclerview)
    implementation(libs.preference.ktx)
    implementation(libs.webkit)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.service)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.paging.runtime)
    implementation(libs.work.runtime)

    implementation(libs.coroutines)
    implementation(libs.serialization.json)

    // Kick REST API + WebSocket (Pusher protocol) chat connection
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.coil)
    implementation(libs.coil.gif)

    implementation(libs.glide)
    ksp(libs.glide.ksp)
}
