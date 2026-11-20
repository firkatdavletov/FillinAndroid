// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.tracer) apply false
}

ktlint {
    version.set("1.8.0")
    ignoreFailures.set(false)
}
