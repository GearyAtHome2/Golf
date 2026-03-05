plugins {
    java
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val gdxVersion = "1.11.0"

dependencies {
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("org.example.DesktopLauncher")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "org.example.DesktopLauncher"
    }
    // Bundle everything into the JAR
    from("assets") { into("assets") }
    val runtimeClasspath = configurations.runtimeClasspath.get()
    from(runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) })
}

// Custom task to create the portable folder with EXE and JRE
tasks.register<Exec>("packageGame") {
    group = "distribution"
    description = "Packages the game as a native executable folder with an embedded JRE."

    dependsOn(tasks.jar)

    val jarFile = tasks.jar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("dist").get().asFile.absolutePath
    val jdkHome = System.getProperty("java.home")
    // Detect OS for the correct binary path
    val jpackage = if (System.getProperty("os.name").lowercase().contains("win")) "$jdkHome/bin/jpackage.exe" else "$jdkHome/bin/jpackage"

    doFirst {
        delete(outputDir)
    }

    commandLine(
        jpackage,
        "--type", "app-image",
        "--input", jarFile.parentFile.absolutePath,
        "--dest", outputDir,
        "--name", "GolfGame",
        "--main-jar", jarFile.name,
        "--main-class", "org.example.DesktopLauncher",
        "--vendor", "MyGolfProject",
        "--app-version", "1.0.0"
    )

    doLast {
        println("Portable bundle created at: $outputDir/GolfGame")
    }
}