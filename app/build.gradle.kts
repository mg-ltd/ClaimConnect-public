import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.melosome.claimconnect"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.melosome.claimconnect"
        minSdk = 30
        targetSdk = 37
        // The protected main-branch release job supplies a monotonically increasing Play code.
        // Local/PR builds retain the checked-in default; secrets are not involved.
        // The user-visible name is CalVer (<year>.<code>) so testers can tell
        // builds apart; only versionCode must be a monotonic integer for Play.
        val playCode = providers.environmentVariable("CC_BUILD_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionCode = playCode
        versionName = "${LocalDate.now().year}.$playCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AI_PROXY_URL", "\"" + (providers.environmentVariable("CC_AI_PROXY_URL").orNull ?: "") + "\"")
        buildConfigField("String", "AI_SIMULATE_SCENARIO", "\"" + (providers.environmentVariable("CC_AI_SIMULATE_SCENARIO").orNull ?: "SUPPORTED") + "\"")
        buildConfigField("String", "AI_PRODUCT_ID", "\"" + (providers.environmentVariable("CC_AI_PRODUCT_ID").orNull ?: "") + "\"")
        buildConfigField("String", "AI_API_BASE_URL", "\"" + (providers.environmentVariable("CC_AI_API_BASE_URL").orNull ?: "https://api.meta.ai/v1") + "\"")
        buildConfigField("String", "AI_MODEL", "\"" + (providers.environmentVariable("CC_AI_MODEL").orNull ?: "muse-spark-1.3") + "\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
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
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.billing.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}