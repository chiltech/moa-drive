import java.util.Properties
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val localSecrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val kakaoKey = localSecrets.getProperty("KAKAO_NATIVE_APP_KEY", "")
val itsCctvKey = localSecrets.getProperty("ITS_CCTV_API_KEY", "").trim()
val neisKey = localSecrets.getProperty("NEIS_API_KEY", "").trim()
// Signing credentials stay outside the published source tree. Contributors may
// provide their own signing.properties, or pass -Pmoa.signing.properties=/path/to/file.
val releaseSigningFile = rootProject.file(
    providers.gradleProperty("moa.signing.properties").orNull ?: "signing.properties"
)
val releaseSigning = Properties().apply {
    releaseSigningFile.takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val hasReleaseSigning = releaseSigning.isNotEmpty()
if (hasReleaseSigning) {
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { key ->
        check(!releaseSigning.getProperty(key).isNullOrBlank()) { "Missing release signing property: $key" }
    }
}
check(neisKey.matches(Regex("[A-Za-z0-9]{0,128}"))) { "Invalid NEIS key format" }
check(itsCctvKey.matches(Regex("[A-Za-z0-9._~+/%=\\-]{0,512}"))) { "Invalid ITS CCTV key format" }
check(kakaoKey.isEmpty() || kakaoKey.matches(Regex("[A-Za-z0-9]+"))) { "Invalid Kakao key format" }
// Kotlin inlines Java BuildConfig constants. Invalidate incremental compilation when
// credentials change so an unchanged ViewModel cannot retain an older embedded key.
val navigationSecretsFingerprint = MessageDigest.getInstance("SHA-256")
    .digest("$kakaoKey|$itsCctvKey|$neisKey".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    inputs.property("navigationSecretsFingerprint", navigationSecretsFingerprint)
}
android {
    namespace = "com.moagauge.pleos"
    compileSdk = 35
    useLibrary("android.car")
    defaultConfig {
        applicationId = "com.moagauge.pleos"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        buildConfigField("String", "ITS_CCTV_API_KEY", "\"$itsCctvKey\"")
        buildConfigField("String", "NEIS_API_KEY", "\"$neisKey\"")
    }
    signingConfigs {
        if (hasReleaseSigning) create("submissionRelease") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("submissionRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.databinding:viewbinding:8.8.2")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.kakaomobility.knsdk:knsdk_ui:1.12.8-hotfix05")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
