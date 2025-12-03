plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "com.zonallink"
    version = "0.1.0"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
}
