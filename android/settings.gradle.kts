pluginManagement {
    repositories {
        google()
        // Mirror Central: repo1.maven.org mengembalikan HTTP 403 dari jaringan
        // build; lihat .work/DECISIONS.md D-01 dan evidence/device-requirements.md.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
    }
}

rootProject.name = "PemilahCamera"
include(":app")
