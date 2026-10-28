plugins { id("com.android.application") }

android {
    namespace = "com.parsharec"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.parsharec"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.7.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

configurations.configureEach { exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib") }

dependencies {
    implementation("com.kosherjava:zmanim:2.5.0")
    testImplementation("junit:junit:4.13.2")
}
