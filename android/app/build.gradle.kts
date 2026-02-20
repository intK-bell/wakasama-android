import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

fun readDotEnv(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()

    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf("=")
            val key = line.substring(0, idx).trim()
            val rawValue = line.substring(idx + 1).trim()
            val value = rawValue.removePrefix("\"").removeSuffix("\"")
            key to value
        }
}

fun resolveConfigValue(key: String, dotEnv: Map<String, String>): String {
    val fromGradleProp = (project.findProperty(key) as String?)?.trim().orEmpty()
    if (fromGradleProp.isNotEmpty()) return fromGradleProp

    val fromEnv = System.getenv(key)?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv

    return dotEnv[key]?.trim().orEmpty()
}

fun asBuildConfigString(value: String): String = "\"${value.replace("\"", "\\\"")}\""

val dotEnv = readDotEnv(rootProject.file(".env"))
val appToken = resolveConfigValue("APP_TOKEN", dotEnv)
val defaultApiBaseUrl = resolveConfigValue("API_BASE_URL", dotEnv)
val deviceId = resolveConfigValue("DEVICE_ID", dotEnv)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.launcherlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aokikensaku.launcherlock"
        minSdk = 26
        //noinspection EditedTargetSdkVersion
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.5"
        buildConfigField(
            "String",
            "APP_TOKEN",
            asBuildConfigString(appToken)
        )
        buildConfigField(
            "String",
            "DEFAULT_API_BASE_URL",
            asBuildConfigString(defaultApiBaseUrl)
        )
        buildConfigField(
            "String",
            "DEVICE_ID",
            asBuildConfigString(deviceId)
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Keep current stable network stack versions; ignore auto-update suggestions.
        disable += "NewerVersionAvailable"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    implementation("androidx.work:work-runtime-ktx:2.11.1")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    add("ksp", "androidx.room:room-compiler:2.8.4")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
}
