import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The ML Kit handwriting models (English and Spanish, about 24 MB) ship inside the APK, so the app never
// downloads them. They are fetched here once, checked against src/main/assets/ink-models/files.txt, and kept in
// the user's home folder so every build and clone reuses them. See docs/handwriting-models.md.
val inkModelCache = File(System.getProperty("user.home"), ".better-notes/ink-models")
val inkModelAssets = layout.buildDirectory.dir("generated/ink-model-assets")
val inkModelList = file("src/main/assets/ink-models/files.txt")
val fetchInkModels = tasks.register("fetchInkModels") {
    inputs.file(inkModelList)
    outputs.dir(inkModelAssets)
    doLast {
        val out = inkModelAssets.get().asFile.resolve("ink-models").apply { mkdirs() }
        inkModelCache.mkdirs()
        for (line in inkModelList.readLines().filter { it.isNotBlank() && !it.startsWith("#") }) {
            val (_, zip, _, sha1) = line.split('|')
            fun sha1Of(f: File) = MessageDigest.getInstance("SHA-1").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            val cached = File(inkModelCache, zip)
            if (!cached.exists() || sha1Of(cached) != sha1) {
                val tmp = File(inkModelCache, "$zip.part")
                uri("https://dl.google.com/handwriting/models/$zip").toURL().openStream().use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                check(sha1Of(tmp) == sha1) { "Handwriting model $zip does not match its recorded SHA-1" }
                tmp.renameTo(cached)
            }
            cached.copyTo(File(out, zip), overwrite = true)
        }
    }
}
tasks.configureEach {
    // Lint reads the assets too, and Gradle refuses a task that reads another's output without a dependency.
    if ((name.startsWith("merge") && name.endsWith("Assets")) || name.contains("lint", ignoreCase = true)) dependsOn(fetchInkModels)
}

android {
    namespace = "app.betternotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.betternotes"
        // API 35 gives PdfRenderer text extraction. Unverified: the Pad 7 must run Android 15+ to install this.
        minSdk = 35
        targetSdk = 37
        versionCode = 251
        versionName = "2.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key lives outside the repo; keystore.properties (git-ignored) holds its path and passwords.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "PERF_TOOLS", "true")
            buildConfigField("boolean", "DEV_CHECKS", "true")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "PERF_TOOLS", "false")
            buildConfigField("boolean", "DEV_CHECKS", "false")
        }
        // Only for restoring a backup onto a fresh install: the release key, but debuggable so `run-as` can write the
        // app's data. Install it, restore, then install the real release over it (same key, data is kept).
        create("restore") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
        // Runs like a release, so its timings mean something, but can be installed and measured.
        create("profile") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".profile"
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
            buildConfigField("boolean", "PERF_TOOLS", "true")
            buildConfigField("boolean", "DEV_CHECKS", "true")
        }
        // The everyday build once the handwriting models are on the device: it has no INTERNET permission
        // (see src/offline/AndroidManifest.xml). Same package and key as debug, so it installs over a
        // debug build that downloaded them and keeps its data.
        create("offline") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "PERF_TOOLS", "false")
            buildConfigField("boolean", "DEV_CHECKS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.directories.add(inkModelAssets.get().asFile.path)

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
    implementation(libs.mlkit.digital.ink)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
