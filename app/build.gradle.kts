import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.viinnisantoos.idleterino"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.viinnisantoos.idleterino"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.3.3"
    }

    // A chave de assinatura fica FORA do Git (keystore.properties + idleterino.jks na raiz do projeto).
    val chave = rootProject.file("keystore.properties")
    signingConfigs {
        create("release") {
            if (chave.exists()) {
                val p = Properties().apply { chave.inputStream().use { load(it) } }
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (chave.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
