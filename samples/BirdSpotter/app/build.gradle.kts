/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

// Machine-local values live in local.properties, which is gitignored. The file is optional:
// a fresh clone without it builds and runs, each reader below falling back to a value that
// keeps the app whole.
val localProperties: Properties =
    Properties().apply {
      rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

// Empty still builds and runs — only the sighting map renders blank until a key is dropped in.
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "")

// DAT attestation identifiers, held out of the tree because the client token is a credential
// and this repo is public. "0" is the documented Developer Mode placeholder, and Developer Mode
// is the only way BirdSpotter runs — attestation is skipped there — so a clone that never
// fills these in is not a degraded one.
val mwdatApplicationId: String = localProperties.getProperty("mwdat_application_id", "0")
val mwdatClientToken: String = localProperties.getProperty("mwdat_client_token", "0")

android {
  namespace = "com.meta.pixelandtexel.birdspotter"
  compileSdk {
    version =
        release(36) {
          minorApiLevel = 1
        }
  }

  defaultConfig {
    applicationId = "com.meta.pixelandtexel.birdspotter"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Injected into the matching <meta-data> entries in AndroidManifest.xml.
    manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    manifestPlaceholders["mwdat_application_id"] = mwdatApplicationId
    manifestPlaceholders["mwdat_client_token"] = mwdatClientToken
  }

  buildTypes {
    release {
      optimization {
        enable = false
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }

  // Robolectric runs the journal.db tests on the JVM, so `./gradlew test` covers
  // the whole storage stack without an emulator. It needs the merged resources.
  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

// Room writes one directory per @Database class under app/schemas. journal.db's
// export lands in .../JournalDatabase/ and is deliberately ignored by the seed
// pipeline, which only reads *CatalogDatabase exports.
ksp {
  arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
  arg("room.generateKotlin", "true")
}

// The bird catalog (assets/catalog.db + assets/birds/) and the Demo Director
// presets (assets/presets/) ship as committed static assets under
// src/main/assets/. The upstream demo generated them from a
// Python pipeline; the DAT sample bundles the prebuilt output directly, so
// there is no build-time codegen step.

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.maps.compose)
  implementation(libs.play.services.maps)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.camera)
  implementation(libs.mwdat.inputs)
  implementation(libs.mwdat.motion)
  implementation(libs.mwdat.speech)
  implementation(libs.mwdat.display)
  implementation(libs.mwdat.mockdevice)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
