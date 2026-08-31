plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
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

javafx {
    version = "25.0.1"
    modules = listOf("javafx.controls", "javafx.media", "javafx.swing")
}

dependencies {
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

tasks.named<JavaExec>("run") {
    // El renderizado usa Canvas 2D con efectos; forzamos el pipeline acelerado.
    jvmArgs("-Dprism.order=es2,sw", "-Dprism.vsync=true")
    jvmArgs("--enable-native-access=javafx.graphics,javafx.media")
    // Permite abrir pistas directamente: ./gradlew run --args="/ruta/cancion.mp3"
    if (project.hasProperty("tracks")) {
        args((project.property("tracks") as String).split(","))
    }
}
