import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val configuredWebUrl = providers.gradleProperty("STREAMLIVEX_WEB_URL")
    .orElse("https://streamlivex.com/app")
    .get()
val escapedWebUrl = configuredWebUrl.replace("\\", "\\\\").replace("\"", "\\\"")

// Yayın imzası bilgileri: önce android/keystore.properties dosyasından, yoksa
// ortam değişkenlerinden (CI) okunur. Bu bilgiler ASLA depoya yazılmaz
// (keystore.properties ve *.jks .gitignore ile hariç tutulur).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = signingValue("storeFile", "STREAMLIVEX_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "STREAMLIVEX_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "STREAMLIVEX_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "STREAMLIVEX_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFilePath != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.streamlivex.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.streamlivex.android"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "WEB_APP_URL", "\"$escapedWebUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // İmza bilgisi yoksa (ör. keystore.properties yok) derleme yine de
                // çalışsın diye debug imzasına düşülür; Play'e YÜKLENMEZ.
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
implementation("com.google.zxing:core:3.5.3")     

    testImplementation("junit:junit:4.13.2")
}
