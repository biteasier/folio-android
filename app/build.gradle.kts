import java.util.Properties

plugins {
    id("com.android.application")
}

// Official releases are signed with a private key kept outside this repo. Without it,
// release builds fall back to the local debug key so anyone can build and sideload.
val signingFile = file(
    System.getenv("FOLIO_SIGNING_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.android/folio-release/keystore.properties"
)
val signing = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use { load(it) } }

android {
    namespace = "com.biteasier.folio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.biteasier.folio"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (signingFile.isFile) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += listOf("META-INF/**")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    val cm = "0.33.0"
    implementation("org.commonmark:commonmark:$cm")
    implementation("org.commonmark:commonmark-ext-gfm-tables:$cm")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:$cm")
    implementation("org.commonmark:commonmark-ext-gfm-alerts:$cm")
    implementation("org.commonmark:commonmark-ext-task-list-items:$cm")
    implementation("org.commonmark:commonmark-ext-autolink:$cm")
    implementation("org.commonmark:commonmark-ext-heading-anchor:$cm")
    implementation("org.commonmark:commonmark-ext-footnotes:$cm")
    implementation("org.commonmark:commonmark-ext-yaml-front-matter:$cm")
    testImplementation("junit:junit:4.13.2")
}
