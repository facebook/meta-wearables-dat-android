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
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

val localProperties =
    Properties().apply {
      val localPropertiesFile = rootProject.file("local.properties")
      if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
      }
    }

android {
  namespace = "com.meta.wearable.dat.externalsampleapps.displayaccess"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.meta.wearable.dat.externalsampleapps.displayaccess"
    minSdk = 31
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    manifestPlaceholders["app_package_name"] =
        "com.meta.wearable.dat.externalsampleapps.displayaccess"
    manifestPlaceholders["mwdat_application_id"] =
        providers.gradleProperty("mwdat_application_id").orNull
            ?: localProperties.getProperty("mwdat_application_id", "")
    manifestPlaceholders["mwdat_client_token"] =
        providers.gradleProperty("mwdat_client_token").orNull
            ?: localProperties.getProperty("mwdat_client_token", "")
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions { jvmTarget = "1.8" }

  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.display)
}
