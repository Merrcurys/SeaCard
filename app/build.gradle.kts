import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Подпись релиза.
// Ключи задаются в keystore.properties (корень проекта, в git не коммитится):
//   merrcurys.storeFile / .storePassword / .keyAlias / .keyPassword
//   example.storeFile   / .storePassword / .keyAlias / .keyPassword
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(prefix: String, key: String): String? {
    val fromFile = keystoreProperties.getProperty("$prefix.$key")
    val fromEnv = System.getenv("${prefix}_${key}".uppercase())
    return (fromFile ?: fromEnv)?.trim()?.takeIf { it.isNotEmpty() }
}

// ABI-сплиты нельзя включать одновременно со сборкой App Bundle в одном запуске Gradle,
// поэтому включаем их только по флагу -PabiSplits=true (build-release.sh).
val abiSplitsEnabled = providers.gradleProperty("abiSplits").orNull?.toBoolean() ?: false

android {
    namespace = "ru.merrcurys.seacard"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        versionCode = 21
        versionName = "3.4.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("merrcurys") {
            signingValue("merrcurys", "storeFile")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("merrcurys", "storePassword")
            keyAlias = signingValue("merrcurys", "keyAlias")
            keyPassword = signingValue("merrcurys", "keyPassword")
        }
        create("example") {
            signingValue("example", "storeFile")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("example", "storePassword")
            keyAlias = signingValue("example", "keyAlias")
            keyPassword = signingValue("example", "keyPassword")
        }
    }

    val hasMerrcurysKey = signingValue("merrcurys", "storeFile") != null
    val hasExampleKey = signingValue("example", "storeFile") != null

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Один и тот же код — два разных applicationId.
    flavorDimensions += "brand"
    productFlavors {
        create("merrcurys") {
            dimension = "brand"
            applicationId = "ru.merrcurys.seacard"
            signingConfig = if (hasMerrcurysKey) {
                signingConfigs.getByName("merrcurys")
            } else {
                logger.warn("keystore.properties: merrcurys.storeFile не задан — release-подпись не настроена")
                signingConfigs.findByName("debug")
            }
        }
        create("example") {
            dimension = "brand"
            applicationId = "com.example.seacard"
            signingConfig = if (hasExampleKey) {
                signingConfigs.getByName("example")
            } else {
                logger.warn("keystore.properties: example.storeFile не задан — release-подпись не настроена")
                signingConfigs.findByName("debug")
            }
        }
    }

    // APK-сплиты по ABI + общий (universal) APK. AAB всегда содержит все ABI.
    // Включается только при -PabiSplits=true, иначе App Bundle не соберётся.
    splits {
        abi {
            isEnable = abiSplitsEnabled
            reset()
            include("x86_64", "arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Имена APK-файлов: universal -> seacard-<версия>.apk, сплиты -> seacard-<abi>-<версия>.apk.
// Применяем только в фазе ABI-сплитов (abiSplits=true), потому что outputFileName
// влияет и на имя App Bundle, а AAB должен остаться app-<flavor>-release.aab.
androidComponents {
    onVariants { variant ->
        if (!abiSplitsEnabled) return@onVariants
        val version = android.defaultConfig.versionName ?: "0.0.0"
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            output.outputFileName.set(
                if (abi != null) "seacard-$abi-$version.apk" else "seacard-$version.apk"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    implementation(platform(libs.rustore.bom))
    implementation(libs.rustore.appupdate)
    implementation(libs.rustore.review)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.runtime.retain)
    implementation(libs.coil.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit Barcode Scanning
    implementation(libs.barcode.scanning)

    // ZXing
    implementation(libs.zxing.core)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.remotecollectionview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
