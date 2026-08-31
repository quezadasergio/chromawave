import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.chromawave"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val javafxVersion = "25.0.1"
val javafxModules = listOf("base", "graphics", "controls", "media")

/**
 * Los artefactos de JavaFX llevan bibliotecas nativas y se publican con un
 * clasificador por sistema. Se declaran a mano en lugar de usar el plugin
 * oficial porque así la plataforma es un parámetro de la compilación, que es lo
 * que permite generar el fat jar para un sistema distinto al del equipo.
 */
val javafxPlatform: String =
    System.getProperty("javafx.platform")
        ?: findProperty("javafx.platform") as String?
        ?: defaultJavafxPlatform()

dependencies {
    javafxModules.forEach {
        implementation("org.openjfx:javafx-$it:$javafxVersion:$javafxPlatform")
    }

    // Decodificadores puros en Java registrados como SPI de javax.sound.sampled.
    // Permiten leer FLAC y OGG/Vorbis sin dependencias nativas.
    implementation("org.jflac:jflac-codec:1.5.2")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
}

application {
    mainClass = "com.chromawave.Launcher"
    applicationDefaultJvmArgs = listOf(
        "-Dprism.order=es2,sw",
        "--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
}

sourceSets {
    test {
        compileClasspath += sourceSets.main.get().runtimeClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
}

/** Comprueba que los decodificadores SPI de FLAC y OGG estén disponibles. */
tasks.register<JavaExec>("checkDecoders") {
    group = "verification"
    mainClass = "com.chromawave.DecoderAvailabilityTest"
    classpath = sourceSets.test.get().runtimeClasspath
}

/** Diagnóstico por consola de la cadena de decodificación y análisis. */
tasks.register<JavaExec>("checkPipeline") {
    group = "verification"
    mainClass = "com.chromawave.AudioPipelineCheck"
    classpath = sourceSets.test.get().runtimeClasspath
    if (project.hasProperty("tracks")) {
        args((project.property("tracks") as String).split(","))
    }
}

/** Diagnóstico del motor de reproducción de JavaFX (MP3, WAV, M4A). */
tasks.register<JavaExec>("checkFxPipeline") {
    group = "verification"
    mainClass = "com.chromawave.FxPipelineCheck\$Launcher"
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("tracks")) {
        args((project.property("tracks") as String).split(","))
    }
}

/** Diagnóstico de la captura de micrófono. */
tasks.register<JavaExec>("checkMicrophone") {
    group = "verification"
    mainClass = "com.chromawave.MicrophoneCheck"
    classpath = sourceSets.test.get().runtimeClasspath
}

/**
 * Fat jar ejecutable con `java -jar`.
 *
 * <p>Incluye JavaFX, sus bibliotecas nativas y los decodificadores de audio. Las
 * nativas son propias de cada sistema, así que el jar resultante sirve solo para
 * la plataforma con la que se construyó; para las demás hay que indicar
 * `-Djavafx.platform=win|linux|mac|mac-aarch64`.
 */
tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName = "chromawave"
    archiveClassifier = javafxPlatform
    archiveVersion = project.version.toString()

    // Los proveedores SPI de FLAC y OGG declaran el mismo archivo de servicio.
    // Sin fusionarlos, el último en entrar pisaría al otro y un formato dejaría
    // de reconocerse sin dar ningún error. La estrategia INCLUDE es necesaria
    // para que los duplicados lleguen al fusionador en vez de descartarse antes.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    // Cada dependencia trae su propio descriptor de módulo y su firma; ambos
    // pierden sentido al fundir todo en un único artefacto del classpath.
    exclude("module-info.class", "META-INF/versions/*/module-info.class")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    manifest {
        attributes(
            "Main-Class" to "com.chromawave.Launcher",
            "Implementation-Title" to "ChromaWave",
            "Implementation-Version" to project.version,
            "Enable-Native-Access" to "ALL-UNNAMED")
    }
}

/** Clasificador de JavaFX correspondiente al equipo que ejecuta la compilación. */
fun defaultJavafxPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("win") -> "win"
        os.contains("mac") -> if (arch == "aarch64") "mac-aarch64" else "mac"
        arch == "aarch64" -> "linux-aarch64"
        else -> "linux"
    }
}

tasks.named<JavaExec>("run") {
    // El renderizado usa Canvas 2D con efectos; forzamos el pipeline acelerado.
    jvmArgs("-Dprism.order=es2,sw", "-Dprism.vsync=true")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Permite abrir pistas directamente: ./gradlew run --args="/ruta/cancion.mp3"
    if (project.hasProperty("tracks")) {
        args((project.property("tracks") as String).split(","))
    }
}
