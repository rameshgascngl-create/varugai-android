import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cred(env: String, prop: String): String? =
    System.getenv(env) ?: ksProps.getProperty(prop)

val storeFilePath = cred("VARUGAI_KEYSTORE_PATH", "storeFile")
val storePw       = cred("VARUGAI_KEYSTORE_PASSWORD", "storePassword")
val keyAliasName  = cred("VARUGAI_KEY_ALIAS", "keyAlias")
val keyPw         = cred("VARUGAI_KEY_PASSWORD", "keyPassword")
val canSign = listOf(storeFilePath, storePw, keyAliasName, keyPw).all { !it.isNullOrBlank() } &&
        file(storeFilePath!!).exists()

android {
    namespace = "com.gasczoology.varugai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gasczoology.varugai"
        minSdk = 24
        targetSdk = 34
        versionCode = 15200
        versionName = "15.2.0"
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePw
                keyAlias = keyAliasName
                keyPassword = keyPw
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
        debug { applicationIdSuffix = ".debug" }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("html") }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
