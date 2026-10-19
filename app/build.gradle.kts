import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing reads from keystore.properties (git-ignored). Missing file => unsigned release.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// daedalus-api deployment used when a pairing code is typed in (a scanned QR carries its own).
// Set daedalusApiUrl in local.properties or gradle.properties to point at another one.
val apiUrl: String = Properties().run {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    getProperty("daedalusApiUrl")
} ?: providers.gradleProperty("daedalusApiUrl").orNull ?: "https://daedalus-api.cd-cloud.workers.dev"

android {
    namespace = "com.codyd.daedalus.scanner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.codyd.daedalus.scanner"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "DEFAULT_API_URL", "\"${apiUrl.trimEnd('/')}\"")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.mlkit)

    // Bundled models: work offline and without Google Play services.
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.barcode)

    testImplementation(libs.junit)
    // org.json is part of Android; unit tests on the JVM need a real implementation.
    testImplementation("org.json:json:20250517")
}
