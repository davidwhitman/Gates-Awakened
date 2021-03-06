import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/////////////////
// CHANGE ME
val starsectorDirectory = "C:/Program Files (x86)/Fractal Softworks/Starsector"
/////////////////

val starsectorCoreDirectory = "$starsectorDirectory/starsector-core"
val starsectorModDirectory = "$starsectorDirectory/mods"

plugins {
    kotlin("jvm") version "1.3.50"
    java
}

version = "1.0.0"

repositories {
    maven(url = uri("$projectDir/libs"))
    mavenCentral()
    jcenter()
}

dependencies {
    val kotlinVersionInLazyLib = "1.3.60"

    // Get kotlin sdk from LazyLib during runtime, only use it here during compile time
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersionInLazyLib")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersionInLazyLib")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")

    compileOnly(fileTree("$starsectorModDirectory/LazyLib/jars") { include("*.jar") })
    compileOnly(fileTree("$starsectorModDirectory/MagicLib/jars") { include("*.jar") })
    compileOnly(fileTree("$starsectorModDirectory/GraphicsLib/jars") { include("*.jar") })
    compileOnly(fileTree("$starsectorModDirectory/Console Commands/jars") { include("*.jar") })

    // Include to be able to browse the non-decompiled source
//    compileOnly("starfarer:starfarer-api:1.0")
    // Starsector jars and dependencies
    compileOnly(fileTree(starsectorCoreDirectory) {
        include("*.jar", "*.zip")
//        exclude("*_obf.jar")
    })

    // Handy kotlin helpers
    implementation("ch.tutteli.kbox:kbox:0.13.0") {
        exclude("org.jetbrains.kotlin")
    }
}

tasks {
    named<Jar>("jar")
    {
        // Include all runtime files in the jar so mod is standalone
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        destinationDir = file("$rootDir/jars")
        archiveName = "Gates_Awakened.jar"
    }

    register("debug-starsector", Exec::class) {
        println("Starting debugger for Starsector...")
        workingDir = file(starsectorCoreDirectory)

        commandLine = if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            listOf("cmd", "/C", "starsectorDebug.bat")
        } else {
            listOf("./starsectorDebug.bat")
        }
    }

    register("run-starsector", Exec::class) {
        println("Starting Starsector...")
        workingDir = file(starsectorCoreDirectory)

        commandLine = if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            listOf("cmd", "/C", "starsector.bat")
        } else {
            listOf("./starsector.bat")
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.6"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
    targetCompatibility = JavaVersion.VERSION_1_6
}