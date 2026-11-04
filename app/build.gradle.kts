plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.betternotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.betternotes"
        // API 35 gives PdfRenderer text extraction. Unverified: the Pad 7 must run Android 15+ to install this.
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "PERF_TOOLS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            buildConfigField("boolean", "PERF_TOOLS", "false")
        }
        // Runs like a release, so its timings mean something, but can be installed and measured.
        create("profile") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".profile"
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
            buildConfigField("boolean", "PERF_TOOLS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)

    implementation(libs.ink.authoring)
    implementation(libs.ink.brush)
    implementation(libs.ink.geometry)
    implementation(libs.ink.rendering)
    implementation(libs.ink.strokes)
    implementation(libs.motion.prediction)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
