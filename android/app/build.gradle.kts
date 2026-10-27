plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Parser JSON profil model — D-08.
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "id.pemilah"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.pemilah"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Pustaka ikon Material — D-05. Versi mengikuti Compose BOM 2024.10.01 (tanpa
    // nomor versi), pola yang sama dengan project PHM.
    implementation("androidx.compose.material:material-icons-extended")
    // Parser JSON berkas profil model — D-08. Hanya lapisan model/storage yang
    // memakai; ConfigCodec tetap dipakai untuk konfigurasi mesin dan kalibrasi.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // ONNX Runtime Android 1.19.2 — D-01, dipakai mulai D-09 untuk membaca berkas
    // model hasil pelatihan (metadata kelas + bentuk tensor) saat impor.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
    // CameraX 1.4.0 — D-01. CameraX hanya dipakai lapisan platform/camera.
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // USB OTG CDC-ACM ke ESP32-S3 — D-04. Hanya lapisan platform/usb yang memakai.
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")

    testImplementation("junit:junit:4.13.2")
}
