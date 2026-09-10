// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://artifacts.applovin.com/android") }
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://mint.splunk.com/gradle/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}
tasks.register<Delete>("clean") {
    delete(layout.buildDirectory.asFile.get())
}
