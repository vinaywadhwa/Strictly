plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vwap.strictly.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vwap.strictly.sample"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // The whole point: debug builds get the real SDK, release builds get the
    // empty stub. Consumer apps wire it exactly like this in their gradle file.
    debugImplementation(project(":strictly"))
    releaseImplementation(project(":strictly-noop"))

    debugImplementation(libs.androidx.compose.ui.tooling)
}
