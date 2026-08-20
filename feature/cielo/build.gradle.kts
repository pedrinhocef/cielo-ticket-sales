import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pedrosoares.cielosales.cielo"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val clientId = localProperties.getProperty("CIELO_CLIENT_ID")
            ?: localProperties.getProperty("cielo.client.id")
            ?: ""
        val accessToken = localProperties.getProperty("CIELO_ACCESS_TOKEN")
            ?: localProperties.getProperty("cielo.access.token")
            ?: ""
        buildConfigField("String", "CIELO_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "CIELO_ACCESS_TOKEN", "\"$accessToken\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test.v180)
    testImplementation(libs.robolectric)
}
