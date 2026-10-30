import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.x requires the Compose compiler Gradle plugin (org.jetbrains.kotlin.plugin.compose).
    alias(libs.plugins.kotlin.compose)
    // kotlinx-serialization compiler plugin (Phase 5 — @Serializable boost models).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nexus.gamebooster"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexus.gamebooster"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = "2.2.0"
    }

    // ---------------------------------------------------------------------------
    // Release signing (v1.2.1 SIGNING-PERMANENCE FIX): the keystore IS committed
    // to the repo (`release.keystore` + `keystore.properties` at the project root,
    // deliberate .gitignore exceptions — see README → "Release signing"). NGP_*
    // env vars still override for rotation/CI scenarios. If neither source
    // provides all four values, the release signing config is simply not created
    // and `assembleRelease` produces an unsigned APK.
    // ---------------------------------------------------------------------------
    val keystoreProperties = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingProp(name: String): String? =
        keystoreProperties.getProperty(name) ?: System.getenv(name)

    val storeFileValue: String? = signingProp("NGP_STORE_FILE")
    val storePasswordValue: String? = signingProp("NGP_STORE_PASSWORD")
    val keyAliasValue: String? = signingProp("NGP_KEY_ALIAS")
    val keyPasswordValue: String? = signingProp("NGP_KEY_PASSWORD")

    signingConfigs {
        if (storeFileValue != null && storePasswordValue != null &&
            keyAliasValue != null && keyPasswordValue != null
        ) {
            create("release") {
                // Relative NGP_STORE_FILE paths resolve against the PROJECT ROOT
                // (rootProject.file), not the app module — release.keystore lives there.
                storeFile = rootProject.file(storeFileValue)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            // Phase 6: R8 shrinking + resource shrinking are ENABLED and verified by
            // an actual build. The kotlinx-serialization keep rules in
            // proguard-rules.pro protect the @Serializable boost report models.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Attach the signing config only when credentials were provided;
            // otherwise the output stays unsigned (documented in README.md).
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            // Signed with the auto-generated debug keystore — installable out of the box.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Phase 10.5: WireGuard tunnel + network code uses java.time-like APIs on minSdk 26.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // Required so Settings can show the real versionName/applicationId from BuildConfig.
        buildConfig = true
    }

    // ---------------------------------------------------------------------------
    // JVM unit tests (app/src/test — JUnit + kotlinx-coroutines-test, no Robolectric).
    // isReturnDefaultValues makes the mockable android.jar return default values
    // instead of throwing "Stub!" so the two Context-touching USER-EXPLICIT boost
    // actions (OpenBatterySettingsAction / OpenGameDetailsAction) can be constructed
    // on the JVM and their ActivityNotFoundException → Unsupported mapping verified
    // with a minimal fake Context (see BoostActionsTest).
    // ---------------------------------------------------------------------------
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose: all versions come from the BOM.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Boost report persistence (Phase 5): @Serializable models encoded to JSON in DataStore.
    implementation(libs.kotlinx.serialization.json)

    // Phase 9 — real aggressive booster via Shizuku (ADB-level privileges, no full root).
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Phase 10.5 — network tools: official embeddable WireGuard tunnel (GoBackend)
    implementation(libs.wireguard.tunnel)
    coreLibraryDesugaring(libs.core.library.desugaring)

    // IDE preview rendering only.
    debugImplementation(libs.androidx.compose.ui.tooling)

    // JVM unit tests only (app/src/test). No Robolectric, no Android framework —
    // these never enter the APK.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
