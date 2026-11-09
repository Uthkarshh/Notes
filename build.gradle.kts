// AGP 9 bundles Kotlin support, so the kotlin-android plugin is intentionally not applied.
// The Kotlin and KSP versions below override AGP's bundled defaults (see libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
