plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cmux.android"
    compileSdk = 35
    val cmuxAuthOrigin = providers.gradleProperty("cmuxAuthOrigin")
        .orElse("https://cmux.com")
    val cmuxStackBaseUrl = providers.gradleProperty("cmuxStackBaseUrl")
        .orElse("https://api.stack-auth.com")
    val cmuxStackProjectId = providers.gradleProperty("cmuxStackProjectId")
        .orElse("9790718f-14cd-4f7e-824d-eaf527a82b82")
    val cmuxStackPublishableClientKey = providers.gradleProperty("cmuxStackPublishableClientKey")
        .orElse("pck_kzj80gx4mh2jrzn1cx6y5e8jk0kwa01vkevh2p9zd4twr")

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.cmux.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CMUX_AUTH_ORIGIN", "\"${cmuxAuthOrigin.get()}\"")
        buildConfigField("String", "CMUX_STACK_BASE_URL", "\"${cmuxStackBaseUrl.get()}\"")
        buildConfigField("String", "CMUX_STACK_PROJECT_ID", "\"${cmuxStackProjectId.get()}\"")
        buildConfigField("String", "CMUX_STACK_PUBLISHABLE_CLIENT_KEY", "\"${cmuxStackPublishableClientKey.get()}\"")
    }

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
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20250517")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

val testMobileWebAssets = tasks.register<Exec>("testMobileWebAssets") {
    commandLine("node", "src/test/js/mobile_app_test.js")
}

tasks.named("check") {
    dependsOn(testMobileWebAssets)
}
