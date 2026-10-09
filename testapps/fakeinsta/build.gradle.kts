// A stand-in "Instagram" used only by the emulator tests. It mimics the structure of a Reels screen
// (selected Reels tab, full-screen vertical list, like/comment/share on the right) so detection and the
// overlay can be tested end to end without the real app. Never shipped.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.instagram.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.instagram.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.recyclerview)
}
