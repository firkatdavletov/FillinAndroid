import java.util.Properties

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) apply false
}

// A checkout without Firebase configuration must still support the local budget.
val firebaseConfigured = layout.projectDirectory.file("google-services.json").asFile.isFile
if (firebaseConfigured) {
    pluginManager.apply("com.google.gms.google-services")
}

val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
val mobileApiToken = if (localPropertiesFile.asFile.exists()) {
    providers.fileContents(localPropertiesFile).asText.map { contents ->
        Properties().apply { contents.reader().use(::load) }
            .getProperty("MOBILE_API_TOKEN", "")
    }.get()
} else {
    ""
}

val escapedMobileApiToken = mobileApiToken
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.firkat.fillin"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.firkat.fillin"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.firkat.fillin.FillinTestRunner"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://cheerful-art-production-c4f9.up.railway.app/\"",
        )
        buildConfigField("String", "MOBILE_API_TOKEN", "\"$escapedMobileApiToken\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.process)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(platform(libs.retrofit.bom))
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
