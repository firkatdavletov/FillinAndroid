import java.util.Properties
import java.net.URI

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
val apiBaseUrl = "https://cheerful-art-production-c4f9.up.railway.app/"

// Firebase authentication uses the deployed backend unless a test server is specified.
val firebaseApiBaseUrl = providers.gradleProperty("firebaseApiBaseUrl").orNull ?: run {
    if (localPropertiesFile.asFile.exists()) {
        Properties().apply { localPropertiesFile.asFile.reader().use(::load) }
            .getProperty("FIREBASE_API_BASE_URL", apiBaseUrl)
    } else apiBaseUrl
}
if (firebaseApiBaseUrl.isNotBlank()) {
    val uri = URI(firebaseApiBaseUrl)
    require(uri.userInfo == null && uri.query == null && uri.fragment == null && firebaseApiBaseUrl.endsWith("/")) {
        "Firebase API base URL must end in / and contain no credentials, query or fragment"
    }
    require(uri.host != null && (uri.scheme == "https" ||
        (uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost") && uri.port > 0))) {
        "Firebase API requires HTTPS; debug testing also permits localhost HTTP with an explicit port"
    }
}

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
            "\"$apiBaseUrl\"",
        )
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "FIREBASE_API_BASE_URL", "\"$firebaseApiBaseUrl\"")
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    buildTypes {
        release {
            if (firebaseApiBaseUrl.startsWith("http://")) {
                buildConfigField("String", "FIREBASE_API_BASE_URL", "\"\"")
            }
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
    implementation(libs.androidx.material3)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.process)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
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

    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
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
