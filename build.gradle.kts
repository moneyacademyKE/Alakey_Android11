// Top-level build file where you should add configuration options common to all sub-projects/modules.
plugins {
    // L6: versions live in gradle/libs.versions.toml only — the literal
    // duplicates here could drift from the catalog.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
