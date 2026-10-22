import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localWeather = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun weatherString(key: String, fallback: String = ""): String =
    "\"" + localWeather.getProperty(key, fallback).replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r") + "\""
val weatherLatitude = localWeather.getProperty("weather.latitude")?.toDoubleOrNull()
val weatherLongitude = localWeather.getProperty("weather.longitude")?.toDoubleOrNull()
require((weatherLatitude == null && weatherLongitude == null) ||
    (weatherLatitude != null && weatherLongitude != null && weatherLatitude in -90.0..90.0 && weatherLongitude in -180.0..180.0)) {
    "Set both weather.latitude and weather.longitude to valid coordinates, or omit both for automatic local weather."
}

android {
    namespace = "com.navadeep.kanso.tv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.navadeep.kanso.tv"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("double", "WEATHER_LATITUDE", weatherLatitude?.toString() ?: "Double.NaN")
        buildConfigField("double", "WEATHER_LONGITUDE", weatherLongitude?.toString() ?: "Double.NaN")
        buildConfigField("String", "WEATHER_NAME", weatherString("weather.name"))
        buildConfigField("String", "WEATHER_COUNTRY", weatherString("weather.country"))
        buildConfigField("String", "WEATHER_TIMEZONE", weatherString("weather.timezone", "auto"))

    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // Personal sideloaded app: sign with the debug key so it installs over debug builds.
            signingConfig = signingConfigs.getByName("debug")
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
