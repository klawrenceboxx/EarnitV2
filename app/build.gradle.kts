import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val environmentProperties = Properties().apply {
    val file = rootProject.file(".env")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun buildConfigValue(name: String): String =
    (System.getenv(name) ?: localProperties.getProperty(name) ?: environmentProperties.getProperty(name) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "com.example.earnitv2"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.earnitv2"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FEEDBACK_SUPABASE_URL", "\"${buildConfigValue("FEEDBACK_SUPABASE_URL")}\"")
        buildConfigField("String", "FEEDBACK_SUPABASE_ANON_KEY", "\"${buildConfigValue("FEEDBACK_SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "POSTHOG_PROJECT_TOKEN", "\"${buildConfigValue("POSTHOG_PROJECT_TOKEN")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${buildConfigValue("POSTHOG_HOST")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_ENTITLEMENT_SIMULATOR", "true")
            buildConfigField("boolean", "GRANT_BETA_ENTITLEMENT", "false")
        }
        create("internal") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "ENABLE_ENTITLEMENT_SIMULATOR", "true")
            buildConfigField("boolean", "GRANT_BETA_ENTITLEMENT", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_ENTITLEMENT_SIMULATOR", "false")
            buildConfigField("boolean", "GRANT_BETA_ENTITLEMENT", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.posthog:posthog-android:3.+")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
