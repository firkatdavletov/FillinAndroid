import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import com.google.gms.googleservices.GoogleServicesTask
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.tracer)
}

// A checkout without Firebase configuration must still support the local budget.
val debugFirebaseConfigured = layout.projectDirectory.file("src/debug/google-services.json").asFile.isFile
val releaseFirebaseConfigured = layout.projectDirectory.file("google-services.json").asFile.isFile
if (debugFirebaseConfigured || releaseFirebaseConfigured) {
    pluginManager.apply("com.google.gms.google-services")

    androidComponents.onVariants { variant ->
        val hasConfig = when (variant.buildType) {
            "debug" -> debugFirebaseConfigured
            "release" -> releaseFirebaseConfigured
            else -> return@onVariants
        }
        if (!hasConfig) {
            val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
            tasks.named<GoogleServicesTask>("process${variantName}GoogleServices") {
                // Prevent fallback to another variant's JSON and clear any output from an earlier build.
                googleServicesJsonFiles.set(emptyList())
                missingGoogleServicesStrategy.set(MissingGoogleServicesStrategy.IGNORE)
                val generatedResources = outputDirectory
                val generatedAppId = gmpAppId
                doLast {
                    generatedResources.get().asFile.deleteRecursively()
                    generatedAppId.get().asFile.delete()
                }
            }
        }
    }
}

val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
val apiBaseUrl = "https://cheerful-art-production-c4f9.up.railway.app/"
val rustoreReleaseSigning = providers.gradleProperty("rustoreReleaseSigning").orNull == "true"

fun requireSigningEnv(
    name: String
): String = requireNotNull(providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }) {
    "$name is required when rustoreReleaseSigning is enabled"
}

// Firebase authentication uses the deployed backend unless a test server is specified.
val firebaseApiBaseUrl = providers.gradleProperty("firebaseApiBaseUrl").orNull ?: run {
    if (localPropertiesFile.asFile.exists()) {
        Properties().apply { localPropertiesFile.asFile.reader().use(::load) }
            .getProperty("FIREBASE_API_BASE_URL", apiBaseUrl)
    } else {
        apiBaseUrl
    }
}
if (firebaseApiBaseUrl.isNotBlank()) {
    val uri = URI(firebaseApiBaseUrl)
    require(uri.userInfo == null && uri.query == null && uri.fragment == null && firebaseApiBaseUrl.endsWith("/")) {
        "Firebase API base URL must end in / and contain no credentials, query or fragment"
    }
    require(
        uri.host != null &&
            (
                uri.scheme == "https" ||
                    (uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost") && uri.port > 0)
                )
    ) {
        "Firebase API requires HTTPS; debug testing also permits localhost HTTP with an explicit port"
    }
}

android {
    namespace = "com.firkat.fillin"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.firkat.fillin"
        minSdk = 33
        targetSdk = 37
        versionCode = 4
        versionName = "0.3"

        testInstrumentationRunner = "com.firkat.fillin.FillinTestRunner"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"$apiBaseUrl\""
        )
        buildConfigField("String", "FIREBASE_API_BASE_URL", "\"$firebaseApiBaseUrl\"")
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    if (rustoreReleaseSigning) {
        signingConfigs.create("rustoreRelease") {
            storeFile = file(requireSigningEnv("RUSTORE_KEYSTORE_PATH")).also {
                require(it.isFile) { "RUSTORE_KEYSTORE_PATH must point to a keystore file" }
            }
            storePassword = requireSigningEnv("RUSTORE_KEYSTORE_PASSWORD")
            keyAlias = requireSigningEnv("RUSTORE_KEY_ALIAS")
            keyPassword = requireSigningEnv("RUSTORE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "FIREBASE_CONFIGURED", debugFirebaseConfigured.toString())
        }
        release {
            buildConfigField("boolean", "FIREBASE_CONFIGURED", releaseFirebaseConfigured.toString())
            signingConfig = signingConfigs.getByName(if (rustoreReleaseSigning) "rustoreRelease" else "debug")
            if (firebaseApiBaseUrl.startsWith("http://")) {
                buildConfigField("String", "FIREBASE_API_BASE_URL", "\"\"")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
                isShrinkResources = false
            }
        }
    }

    flavorDimensions += "store"

    productFlavors {
        create("google") {
            dimension = "store"
        }
        create("rustore") {
            dimension = "store"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    lint {
        abortOnError = true

        warningsAsErrors = true

        checkReleaseBuilds = true

        checkTestSources = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs all static code quality checks"

    dependsOn(
        ":app:ktlintCheck",
        ":app:detekt",
        ":app:lintDebug"
    )
}

detekt {
    toolVersion = "1.23.8"

    buildUponDefaultConfig = true
    allRules = false

    config.setFrom(
        rootProject.file("config/detekt/detekt.yml")
    )

    baseline = file("detekt-baseline.xml")

    ignoreFailures = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "11"
}

// Tracer
val enableRustoreTracer = providers.gradleProperty("enableRustoreTracer")
    .map { it.toBoolean() }
    .getOrElse(false)

val configuredTracerAppToken = providers.gradleProperty("tracerAppToken")
    .orNull
    .orEmpty()

val configuredTracerPluginToken = providers.gradleProperty("tracerPluginToken")
    .orNull
    .orEmpty()

if (enableRustoreTracer) {
    require(configuredTracerAppToken.isNotBlank()) {
        "Tracer app token is missing"
    }

    require(configuredTracerPluginToken.isNotBlank()) {
        "Tracer plugin token is missing"
    }
}

tracer {
    create("defaultConfig") {
        isDisabled = !enableRustoreTracer

        appToken = configuredTracerAppToken
        pluginToken = configuredTracerPluginToken

        uploadMapping = enableRustoreTracer
        uploadNativeSymbols = enableRustoreTracer
        uploadRetryCount = 2
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
    implementation(libs.compose.shimmer)
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

    detektPlugins(
        libs.detekt.formatting
    )

    add(
        "rustoreImplementation",
        platform(libs.tracer.platform)
    )

    add(
        "rustoreImplementation",
        libs.tracer.crash.report
    )

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
