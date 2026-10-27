// Versi mengikuti keputusan D-01 (.work/DECISIONS.md) dan .work/Structure.md.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Parser JSON profil model — D-08. Versi mengikuti versi Kotlin D-01.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
