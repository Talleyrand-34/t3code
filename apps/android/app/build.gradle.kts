plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

/**
 * A release build is signed only when this machine holds a keystore for it. The
 * path and the passwords come from the Gradle properties or the environment,
 * so a checkout stays free of keys: `t3code.android.keystore`,
 * `t3code.android.storePassword`, `t3code.android.keyAlias` and
 * `t3code.android.keyPassword` (or the same names as T3CODE_ANDROID_*
 * environment variables). Without one the release variant stays unsigned,
 * which only matters for an artifact that leaves the machine.
 */
val releaseKeystorePath: String? =
  providers.gradleProperty("t3code.android.keystore").orNull
    ?: providers.environmentVariable("T3CODE_ANDROID_KEYSTORE").orNull
val releaseStorePassword: String? =
  providers.gradleProperty("t3code.android.storePassword").orNull
    ?: providers.environmentVariable("T3CODE_ANDROID_STORE_PASSWORD").orNull
val releaseKeyAlias: String? =
  providers.gradleProperty("t3code.android.keyAlias").orNull
    ?: providers.environmentVariable("T3CODE_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword: String? =
  providers.gradleProperty("t3code.android.keyPassword").orNull
    ?: providers.environmentVariable("T3CODE_ANDROID_KEY_PASSWORD").orNull
val releaseSigningReady =
  releaseKeystorePath != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
  namespace = "com.t3tools.peer"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.t3tools.peer"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
  }

  signingConfigs {
    if (releaseSigningReady) {
      create("release") {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (releaseSigningReady) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.okhttp)
  implementation(libs.androidx.security.crypto)

  testImplementation(libs.junit)
  // The Android framework stubs org.json out, so the real one stands in for it.
  testImplementation(libs.json)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
