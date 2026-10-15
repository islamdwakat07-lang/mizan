plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.islam.mizan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.islam.mizan"
        minSdk = 26
        targetSdk = 34
        // رقم الإصدار يزيد تلقائياً مع كل بناء على GitHub حتى يتثبت التحديث فوق القديم
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // مفتاح توقيع ثابت: بدونه كل بناء جديد يحتاج حذف التطبيق وتضيع بياناتك
    signingConfigs {
        create("fixed") {
            storeFile = file("mizan.keystore")
            storePassword = "mizan123"
            keyAlias = "mizan"
            keyPassword = "mizan123"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
