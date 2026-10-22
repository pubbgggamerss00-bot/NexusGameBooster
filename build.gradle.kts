// Root build file: declares plugin versions from the version catalog (gradle/libs.versions.toml).
// Nothing else should live here — module logic is in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // kotlinx-serialization compiler plugin (Phase 5 — boost report persistence).
    alias(libs.plugins.kotlin.serialization) apply false
}
