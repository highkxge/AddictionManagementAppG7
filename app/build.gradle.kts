plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "mode"

    productFlavors {
        create("uiOnly") {
            dimension = "mode"
            buildConfigField("boolean", "CAMERA_ENABLED", "false")
        }

        create("full") {
            dimension = "mode"
            buildConfigField("boolean", "CAMERA_ENABLED", "true")
        }
    }

    namespace = "com.yourname.addictionmanager"
    compileSdk = 34 // CHANGED from 36

    defaultConfig {
        applicationId = "com.yourname.addictionmanager"
        minSdk = 26
        targetSdk = 34 // CHANGED from 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Core
    implementation("com.google.mlkit:face-detection:16.1.6")
    implementation("androidx.camera:camera-camera2:1.3.2")
    implementation("androidx.camera:camera-lifecycle:1.3.2")
    implementation("androidx.camera:camera-view:1.3.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Room (✅ correct)
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Security
    implementation("androidx.security:security-crypto:1.0.0")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
