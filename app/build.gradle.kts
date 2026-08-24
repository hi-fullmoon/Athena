plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciVersionCodeValue = providers.environmentVariable("ATHENA_VERSION_CODE").orNull
val ciVersionCode = ciVersionCodeValue?.toIntOrNull()
    ?: if (ciVersionCodeValue == null) {
        1
    } else {
        throw GradleException("ATHENA_VERSION_CODE must be a positive integer")
    }

if (ciVersionCode < 1) {
    throw GradleException("ATHENA_VERSION_CODE must be a positive integer")
}

val ciVersionName = providers.environmentVariable("ATHENA_VERSION_NAME").orNull ?: "1.0.0"

val releaseStoreFile = providers.environmentVariable("ATHENA_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("ATHENA_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ATHENA_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ATHENA_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigningConfig = releaseSigningValues.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningValue && !hasCompleteReleaseSigningConfig) {
    throw GradleException(
        "Release signing requires ATHENA_KEYSTORE_FILE, ATHENA_KEYSTORE_PASSWORD, " +
            "ATHENA_KEY_ALIAS, and ATHENA_KEY_PASSWORD",
    )
}

android {
    namespace = "com.athena.dates"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.athena.dates"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        if (hasCompleteReleaseSigningConfig) {
            create("release") {
                storeFile = file(checkNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasCompleteReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
