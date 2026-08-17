plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace   = "com.siwanonts.valksbridge"
    compileSdk  = 37
    defaultConfig {
        applicationId = "com.siwanonts.valksbridge"
        minSdk        = 33
        targetSdk     = 37
        versionCode   = 112
        versionName   = "1.1.2"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.preview)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.nordic.ble.client)
    implementation(libs.nordic.ble.scanner)
    implementation(libs.coroutines.android)
}
