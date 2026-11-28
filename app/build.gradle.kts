import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

/**
 * Release signing material lives outside the repo (`keystore.properties` and
 * the `.jks` are git-ignored). Without it the release builds **unsigned**, on
 * purpose: a placeholder key could never update an install signed with the
 * real one, and the local database would be lost.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun buildConfigString(key: String, default: String): String =
    localProperties.getProperty(key, default)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

// Notes API (servers/notes) and sito-auth are two different hosts.
// 10.0.2.2 is the development machine as seen from the Android emulator.
val releaseApiUrl = buildConfigString("sitonotas.release.apiUrl", "https://api.notes.sito8943.com")
val releaseAuthUrl = buildConfigString("sitonotas.release.authUrl", "https://api.auth.sito8943.com")
val debugApiUrl = buildConfigString("sitonotas.debug.apiUrl", "http://10.0.2.2:8083")
val debugAuthUrl = buildConfigString("sitonotas.debug.authUrl", "http://10.0.2.2:8081")
// Public note links (`<shareUrl>/<token>`, as the web's VITE_SHARE_URL); empty
// falls back to the API's own share page `<apiUrl>/s/<token>`. The web app URL
// is what "Copy link" gives when a note has no public link.
val releaseShareUrl = buildConfigString("sitonotas.release.shareUrl", "https://share.notes.sito8943.com")
val debugShareUrl = buildConfigString("sitonotas.debug.shareUrl", "")
val releaseWebUrl = buildConfigString("sitonotas.release.webUrl", "https://notas.sito8943.com")
val debugWebUrl = buildConfigString("sitonotas.debug.webUrl", "http://10.0.2.2:5173")

android {
    namespace = "dev.sito8943.sitonotas"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sito8943.notas"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures {
        buildConfig = true
        compose = true
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
        debug {
            // Own identity so a debug install never replaces the release one
            // (and its local database).
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
            buildConfigField("String", "AUTH_BASE_URL", "\"$debugAuthUrl\"")
            buildConfigField("String", "SHARE_BASE_URL", "\"$debugShareUrl\"")
            buildConfigField("String", "WEB_APP_URL", "\"$debugWebUrl\"")
        }
        release {
            // Null without keystore.properties: the APK comes out unsigned.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiUrl\"")
            buildConfigField("String", "AUTH_BASE_URL", "\"$releaseAuthUrl\"")
            buildConfigField("String", "SHARE_BASE_URL", "\"$releaseShareUrl\"")
            buildConfigField("String", "WEB_APP_URL", "\"$releaseWebUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the resources to build a Context.
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // MigrationTestHelper reads exported schemas from the variant assets.
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

// Exports Room schemas so future migrations can be written and tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
