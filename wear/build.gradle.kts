plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.powerampremote.wear"
    compileSdk = 37
    defaultConfig {
        // Mesmo applicationId nos dois dispositivos para a futura Data Layer.
        applicationId = "com.powerampremote"
        minSdk = 30
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.play.services.wearable)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.play.services)
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.wear.material3)
}
