import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.hiltAndroid)
    kotlin("kapt")
}

// local.properties is git-ignored: Telegram API credentials and (optionally) the release keystore live there.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}

/** A setting from (in order) a Gradle property (-P / gradle.properties), an environment variable, or local.properties. */
fun setting(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

// ---- Versioning ---------------------------------------------------------------------------------
// versionName is MAJOR.MINOR.PATCH from gradle.properties (TN_VERSION_NAME); a release tag can override it
// on the command line with -PTN_VERSION_NAME=1.2.3. versionCode is derived from it as
// MAJOR * 10000 + MINOR * 100 + PATCH (1.0.0 -> 10000, 1.2.3 -> 10203), so it always increases with the
// version. TN_VERSION_CODE overrides the derived value if you ever need to. See docs/publishing.md.
val tnVersionName: String = setting("TN_VERSION_NAME") ?: "1.0.0"
val tnVersionCode: Int = setting("TN_VERSION_CODE")?.toInt() ?: run {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(tnVersionName)
        ?: throw GradleException("TN_VERSION_NAME must be MAJOR.MINOR.PATCH (e.g. 1.2.3), was '$tnVersionName'")
    val (major, minor, patch) = match.destructured.toList().map { it.toInt() }
    if (minor > 99 || patch > 99) {
        throw GradleException("TN_VERSION_NAME minor and patch must be 0..99 to derive versionCode, was '$tnVersionName'")
    }
    major * 10_000 + minor * 100 + patch
}

// ---- Release signing ------------------------------------------------------------------------------
// The upload keystore is NEVER committed. Point the build at it with these four settings, either in
// local.properties or as environment variables (CI): TN_KEYSTORE_PATH, TN_KEYSTORE_PASSWORD, TN_KEY_ALIAS,
// TN_KEY_PASSWORD. Without them release builds are produced unsigned (fine for CI and for checking that
// R8 works); set TN_RELEASE_DEBUG_SIGNING=true to sign a local release build with the debug key instead,
// so it can be installed on a device. See docs/publishing.md.
val keystorePath = setting("TN_KEYSTORE_PATH")
val keystoreFile = keystorePath?.let { rootProject.file(it) }
val releaseSigningValues = listOf("TN_KEYSTORE_PATH", "TN_KEYSTORE_PASSWORD", "TN_KEY_ALIAS", "TN_KEY_PASSWORD")
    .associateWith { setting(it) }
val hasReleaseKeystore = releaseSigningValues.values.all { it != null } && keystoreFile?.isFile == true
if (!hasReleaseKeystore && releaseSigningValues.values.any { it != null }) {
    val missing = releaseSigningValues.filterValues { it == null }.keys
    val reason = if (missing.isNotEmpty()) "missing $missing" else "keystore file not found: $keystoreFile"
    logger.warn("w: Release signing is only partly configured ($reason); release builds will be unsigned.")
}
val useDebugSigningForRelease = !hasReleaseKeystore && setting("TN_RELEASE_DEBUG_SIGNING") == "true"

android {
    namespace = "io.github.yedidyatob.telegramnarrator"
    compileSdk = 36

    defaultConfig {
        // Permanent: Google Play never allows the applicationId to change after the first upload.
        applicationId = "io.github.yedidyatob.telegramnarrator"
        minSdk = 26
        // Google Play requires targetSdk 36 (Android 16) for new apps and updates since Aug 31, 2026.
        targetSdk = 36
        versionCode = tnVersionCode
        versionName = tnVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }


        // Telegram API credentials come only from local.properties (CI writes them there from repo secrets)
        buildConfigField("String", "TELEGRAM_API_ID", "\"${localProperties.getProperty("TELEGRAM_API_ID") ?: ""}\"")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProperties.getProperty("TELEGRAM_API_HASH") ?: ""}\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = releaseSigningValues.getValue("TN_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("TN_KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("TN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                hasReleaseKeystore -> signingConfigs.getByName("release")
                useDebugSigningForRelease -> signingConfigs.getByName("debug")
                else -> null // unsigned: app-release-unsigned.apk / an unsigned AAB
            }
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
    // Android 12-style splash on every API level (#17)
    implementation(libs.androidx.core.splashscreen)
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

    // Login phone field: country dial codes, trunk prefix and length rules (pure Java, works in JVM unit tests)
    implementation(libs.libphonenumber)

    testImplementation(libs.junit)
    // Unit tests run on the JVM, where the Android org.json stubs do nothing (used for the channel rules JSON)
    testImplementation("org.json:json:20231013")
    // ViewModel tests (runTest, virtual time, Dispatchers.setMain)
    testImplementation(libs.kotlinx.coroutines.test)
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
