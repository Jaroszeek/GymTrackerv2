import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

// A8: the version comes from the release tag, `-PappVersion=1.2.3` (CI passes the tag without "v"); local builds
// get 0.1.0. versionCode = major * 10_000 + minor * 100 + patch. The in-app updater computes the same code from
// the latest release tag, so the formula must stay the same in both places.
val appVersion = providers.gradleProperty("appVersion").getOrElse("0.1.0")
val appVersionCode = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersion)
    ?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: -1 }
    ?.takeIf { (major, minor, patch) -> major >= 0 && minor in 0..99 && patch in 0..99 }
    ?.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
    ?: throw GradleException("appVersion must be MAJOR.MINOR.PATCH with MINOR and PATCH below 100, got \"$appVersion\"")

// A8: release signing from keystore.properties in the project root (local, gitignored; storeFile relative to the
// root), otherwise from the environment (CI secrets). Without credentials assembleRelease builds an unsigned APK.
val keystoreProperties = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText.orNull?.let { text -> Properties().apply { load(text.reader()) } }

fun signingValue(key: String, env: String): String? =
    keystoreProperties?.getProperty(key) ?: providers.environmentVariable(env).orNull

val releaseSigning = listOf(
    signingValue("storeFile", "KEYSTORE_FILE"),
    signingValue("storePassword", "KEYSTORE_PASSWORD"),
    signingValue("keyAlias", "KEY_ALIAS"),
    signingValue("keyPassword", "KEY_PASSWORD"),
).takeIf { values -> values.none { it.isNullOrBlank() } }?.filterNotNull()

android {
    namespace = "com.jaroszeek.gymtracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jaroszeek.gymtracker"
        minSdk = 35
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // GitHub API endpoint of the in-app updater (A5.11); empty = no update checks (debug)
        buildConfigField("String", "UPDATE_URL", "\"\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                val (file, password, alias, aliasPassword) = releaseSigning
                storeFile = rootProject.file(file)
                storePassword = password
                keyAlias = alias
                keyPassword = aliasPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
            buildConfigField("String", "UPDATE_URL", "\"https://api.github.com/repos/Jaroszeek/GymTrackerv2/releases/latest\"")
        }
        // Release-like build for macrobenchmarks (:baselineprofile); also carries the debug gallery
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            // Never GitHub: macrobenchmarks must not hit the network. The updater is tested end to end against a
            // local server, `-PupdateUrl=http://10.0.2.2:8000/latest` (cleartext to the emulator host: src/benchmark)
            buildConfigField("String", "UPDATE_URL", "\"${providers.gradleProperty("updateUrl").getOrElse("")}\"")
        }
    }
    sourceSets {
        named("benchmark") {
            kotlin.directories += "src/debug/java"
            res.directories += "src/debug/res"
            // Its own manifest (src/benchmark): the debug one plus the Health Connect test data writer
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // android.util.Log in production code returns defaults instead of throwing in JVM tests
        unitTests.isReturnDefaultValues = true
    }
}

// The Baseline Profile plugin's copies of release keep R8 on through `optimization` (AGP 9). Its generator build
// must keep the real names, or the profile cannot match the release build (D-079). Runs after the plugin's own setup.
androidComponents {
    finalizeDsl { android ->
        android.buildTypes.matching { it.name.startsWith("nonMinified") }.configureEach { optimization { enable = false } }
        // The copies (benchmarkRelease, nonMinifiedRelease, ...) run the generator and macrobenchmarks: no updater
        android.buildTypes
            .matching { it.name != "benchmark" && (it.name.startsWith("nonMinified") || it.name.startsWith("benchmark")) }
            .configureEach { buildConfigField("String", "UPDATE_URL", "\"\"") }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.backdrop)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kyant.shapes)
    implementation(libs.reorderable)
    implementation(libs.androidx.work.runtime.ktx)
    // Generated by :baselineprofile into src/release/generated/baselineProfiles (D-079)
    baselineProfile(project(":baselineprofile"))
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Hosts composables in Compose UI tests (an empty activity in the debug manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
