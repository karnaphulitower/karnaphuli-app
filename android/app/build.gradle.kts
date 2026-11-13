plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

val vCode = (System.getenv("KT_VCODE") ?: "1").toInt()
val vName = System.getenv("KT_VNAME") ?: "2.0.$vCode"

android {
    namespace = "com.karnaphuli.tower"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.karnaphuli.tower"
        minSdk = 24
        targetSdk = 34
        versionCode = vCode
        versionName = vName
        buildConfigField("long", "BUILD_TIME", System.currentTimeMillis().toString() + "L")
        buildConfigField("String", "START_URL", "\"" + (System.getenv("KT_URL") ?: "https://karnaphulitower.github.io/karnaphuli-app/pilot/") + "\"")
    }
    signingConfigs {
        create("rel") {
            val ks = file(System.getenv("KT_KS") ?: "none.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KT_KS_PASS")
                keyAlias = "karnaphuli"
                keyPassword = System.getenv("KT_KS_PASS")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            val c = signingConfigs.getByName("rel")
            signingConfig = if (c.storeFile != null) c else signingConfigs.getByName("debug")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging")
}
