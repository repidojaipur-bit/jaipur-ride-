plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.drivepartner.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drivepartner.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        val mapsApiKey = (project.findProperty("DRIVE_PARTNER_MAPS_API_KEY") as? String)
            ?.trim()
            ?.replace("\"", "")
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "mode"
    productFlavors {
        create("demo") {
            dimension = "mode"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "DEMO_MODE_ENABLED", "true")
            buildConfigField("String", "ENVIRONMENT_NAME", "\"demo\"")
            buildConfigField("String", "API_BASE_URL", "\"\"")
            buildConfigField("String", "REALTIME_WS_URL", "\"\"")
            buildConfigField("String", "SUPABASE_URL", "\"\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"\"")
        }
        create("live") {
            dimension = "mode"
            val apiBaseUrl = (project.findProperty("DRIVE_PARTNER_API_BASE_URL") as? String)
                ?.trim()
                ?.replace("\"", "")
                ?: ""
            val realtimeWsUrl = (project.findProperty("DRIVE_PARTNER_REALTIME_WS_URL") as? String)
                ?.trim()
                ?.replace("\"", "")
                ?: ""
            val supabaseUrl = (project.findProperty("DRIVE_PARTNER_SUPABASE_URL") as? String)
                ?.trim()
                ?.replace("\"", "")
                ?: ""
            val supabasePublishableKey = (project.findProperty("DRIVE_PARTNER_SUPABASE_PUBLISHABLE_KEY") as? String)
                ?.trim()
                ?.replace("\"", "")
                ?: ""
            buildConfigField("boolean", "DEMO_MODE_ENABLED", "false")
            buildConfigField("String", "ENVIRONMENT_NAME", "\"live\"")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "REALTIME_WS_URL", "\"$realtimeWsUrl\"")
            buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        }
    }

    signingConfigs {
        val keystorePath = (project.findProperty("DRIVE_PARTNER_KEYSTORE_PATH") as? String)?.trim().orEmpty()
        val storePasswordProp = (project.findProperty("DRIVE_PARTNER_KEYSTORE_PASSWORD") as? String)?.trim().orEmpty()
        val keyAliasProp = (project.findProperty("DRIVE_PARTNER_KEY_ALIAS") as? String)?.trim().orEmpty()
        val keyPasswordProp = (project.findProperty("DRIVE_PARTNER_KEY_PASSWORD") as? String)?.trim().orEmpty()
        if (keystorePath.isNotEmpty() && storePasswordProp.isNotEmpty() &&
            keyAliasProp.isNotEmpty() && keyPasswordProp.isNotEmpty()
        ) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Networking (Retrofit + OkHttp)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    // Google Maps & Fused Location Provider (behind core/maps & core/location adapters)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumentation / Compose UI Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
