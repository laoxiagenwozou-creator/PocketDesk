import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pocketdesk.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketdesk.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.0-beta.1"
        testInstrumentationRunner = "com.pocketdesk.app.GestureRegressionRunner"
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val signingFile = rootProject.file("keystore.properties")
            if (signingFile.exists()) {
                val signing = Properties()
                signingFile.inputStream().use { signing.load(it) }
                signingConfigs.create("release") {
                    storeFile = rootProject.file(signing.getProperty("storeFile"))
                    storePassword = signing.getProperty("storePassword")
                    keyAlias = signing.getProperty("keyAlias")
                    keyPassword = signing.getProperty("keyPassword")
                }
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.kotlin_module",
            "/META-INF/DEPENDENCIES"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
