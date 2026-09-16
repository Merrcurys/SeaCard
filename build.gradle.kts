// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// App Bundle для обоих пакетов:
//   ./gradlew bundleReleaseAll
// Полный release (AAB + universal APK + ABI-сплиты) — см. build-release.ps1 / build-release.sh.
tasks.register("bundleReleaseAll") {
    group = "build"
    description = "Собирает AAB для ru.merrcurys.seacard и com.example.seacard."
    dependsOn(
        ":app:bundleMerrcurysRelease",
        ":app:bundleExampleRelease",
    )
}
