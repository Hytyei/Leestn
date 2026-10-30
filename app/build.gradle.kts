import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing. Updates only install over the old app when every release is signed with the SAME key.
// The key comes from keystore.properties (never committed) or from environment variables (GitHub Actions).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(prop: String, env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
val releaseStore = signing("storeFile", "LEESTN_KEYSTORE_FILE")

android {
    namespace = "app.leestn"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.leestn"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "1.7.0"
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signing("storePassword", "LEESTN_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "LEESTN_KEY_ALIAS")
                keyPassword = signing("keyPassword", "LEESTN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without a key (a casual local build) fall back to the debug key so the build still works.
            signingConfig = if (releaseStore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.animation)
    implementation(libs.coil.compose)
    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.google.id)
    debugImplementation(libs.androidx.ui.tooling)
}
