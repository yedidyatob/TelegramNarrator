import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.hiltAndroid)
    kotlin("kapt")
}

android {
    namespace = "com.example.telegramnarrator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.telegramnarrator"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Inject API keys from local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        
        buildConfigField("String", "TELEGRAM_API_ID", "\"${localProperties.getProperty("TELEGRAM_API_ID") ?: ""}\"")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProperties.getProperty("TELEGRAM_API_HASH") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint {
        // The UI is English-only on purpose (only the spoken phrases are translated to Hebrew, in values-he)
        disable += "MissingTranslation"
        abortOnError = true
        // Existing findings are tracked in the baseline; new ones fail the build. Burn it down over time (#22).
        baseline = file("lint-baseline.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 16 KB page-size (Android 15+ / Play): store native libs compressed and extract at install.
        // That avoids the APK mmap ZIP-alignment requirement. ELF LOAD alignment of the .so itself
        // still matters — see README "TDLib / 16 KB page size". arm64-v8a and x86_64 from tdlibx
        // 1.8.56 are already 16 KB; 32-bit ABIs remain 4 KB (tracked in the GitHub issue).
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Security
    implementation(libs.androidx.security.crypto)
    
    // TDLib
    implementation(libs.tdlib)

    // Experimental Edge TTS (WebSocket)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    // Unit tests run on the JVM, where the Android org.json stubs do nothing (used for the channel rules JSON)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Allow references to generated Hilt code
kapt {
    correctErrorTypes = true
}
