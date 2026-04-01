plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

android {
    namespace = "com.volleyhub.pro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.volleyhub.pro"
        minSdk = 30
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Wear OS
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-input:1.1.0")
    implementation("androidx.wear:wear-remote-interactions:1.0.0")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    
    // AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    
    // UI
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}