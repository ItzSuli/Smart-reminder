import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.itzsuli.smartreminder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.itzsuli.smartreminder"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Personal signing key for side-loading. CI can override it through env vars
        // (see .github/workflows/build.yml). The same key must be used for every build,
        // otherwise Android refuses to install the new APK over the old one.
        create("release") {
            // CI exports these even when the secrets are unset (as empty strings), so blank means "use the default".
            fun env(name: String, default: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
            storeFile = rootProject.file(env("KEYSTORE_FILE", "keystore/smart-reminder.jks"))
            storePassword = env("KEYSTORE_PASSWORD", "smartreminder")
            keyAlias = env("KEY_ALIAS", "smartreminder")
            keyPassword = env("KEY_PASSWORD", "smartreminder")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.mlkit.genai.prompt)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
