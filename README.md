# ChromaWave

Visualizador de audio en JavaFX que convierte la música de un archivo o la señal del
micrófono en imágenes abstractas que crecen y se mueven al ritmo del sonido, al estilo
de los visualizadores clásicos de escritorio.

## Requisitos

- Cualquier JDK reciente (17 o superior) para arrancar el wrapper
- Nada más: Gradle viene incluido como wrapper, y el JDK 25 que necesita la
  compilación se descarga solo mediante *toolchains*
- JavaFX y los códecs de FLAC y OGG se resuelven como dependencias

La primera ejecución descarga Gradle y las dependencias, así que tarda unos minutos
y necesita conexión a internet. Las siguientes arrancan en segundos.

## Cómo ejecutarlo

En macOS y Linux:

```bash
./gradlew run
```

En Windows:

```bat
gradlew.bat run
```

Para abrir directamente una o varias pistas:

```bash
./gradlew run --args="/ruta/cancion.mp3 /ruta/otra.flac"
```

También se puede generar un paquete distribuible con `./gradlew installDist`, que deja
un lanzador en `build/install/chromawave/bin/`.

## Un único JAR ejecutable

```bash
./gradlew shadowJar
```

Genera en `build/libs/` un jar autocontenido de unos 10 MB que se ejecuta con:

```bash
java -jar chromawave-1.0.0-mac-aarch64.jar
java -jar chromawave-1.0.0-mac-aarch64.jar /ruta/cancion.flac
```

Dentro va todo lo necesario: JavaFX con sus bibliotecas nativas y los decodificadores
de FLAC y OGG. Quien lo reciba solo necesita tener instalado un Java 25 o superior.

**Es específico de cada sistema operativo.** JavaFX incluye código nativo, así que el
jar sirve únicamente para la plataforma con la que se construyó. El nombre lleva esa
plataforma como sufijo, y para las demás se indica al compilar:

```bash
./gradlew shadowJar -Pjavafx.platform=win           # chromawave-1.0.0-win.jar
./gradlew shadowJar -Pjavafx.platform=linux         # chromawave-1.0.0-linux.jar
./gradlew shadowJar -Pjavafx.platform=mac           # Macs con Intel
./gradlew shadowJar -Pjavafx.platform=mac-aarch64   # Macs con Apple Silicon
```

Los jars para otras plataformas se pueden construir desde cualquier equipo, así que no
hace falta una máquina de cada sistema para repartir la aplicación.

Al arrancar aparece el aviso `Unsupported JavaFX configuration: classes were loaded from
unnamed module`. Es inofensivo: solo indica que JavaFX viene del classpath en lugar del
module path, algo inevitable cuando todo se funde en un único jar.

## Formatos admitidos

| Formato | Motor | Notas |
| --- | --- | --- |
| MP3, WAV, AIFF, M4A/AAC | `javafx.scene.media` | Usa el analizador de espectro integrado |
| FLAC, OGG/Vorbis | Decodificación PCM propia | `jflac` y `vorbisspi` como proveedores SPI |

Ambos motores publican los mismos datos de análisis, así que todos los patrones
funcionan igual con cualquier formato.

## Patrones

- **Onda**: cintas de forma de onda superpuestas y desfasadas
- **Barras de frecuencia**: ecualizador espejado con tapas de pico
- **Barras aleatorias**: composición que se recoloca en cada golpe
- **Estallido radial**: espectro enrollado en círculo con anillos de choque
- **Partículas**: campo de partículas en órbita que se expande con los graves
- **Manchas líquidas**: formas orgánicas deformadas por el espectro
- **Automático**: rota entre todos los anteriores, cambiando siempre a tiempo con la música

Hay seis paletas de color (Neón, Fuego, Océano, Arcoíris, Monocromo, Atardecer) y los
ajustes de sensibilidad, suavizado y brillo se guardan entre sesiones.

## Atajos de teclado

| Tecla | Acción |
| --- | --- |
| `Espacio` | Reproducir / pausar |
| `F` | Pantalla completa (en ella los controles se ocultan solos) |
| `N` / `P` | Pista siguiente / anterior |
| `←` / `→` | Retroceder / avanzar 5 segundos |
| `V` | Siguiente patrón |
| `L` | Mostrar u ocultar la lista de reproducción |

También se pueden arrastrar archivos a la ventana y hacer doble clic sobre el lienzo
para entrar y salir de pantalla completa.

## Arquitectura

El audio y el dibujo nunca se bloquean entre sí:

```
archivo  ──► motor de reproducción ──┐
                                     ├─► AudioBus (mezcla, ganancia, suavizado, beats)
micrófono ─► captura + FFT ──────────┘                │
                                                      ▼
                        AnimationTimer ──► instantánea ──► VisualizerPane
```

Cada fuente escribe su análisis en su propio hueco desde su hilo; el bucle de animación
toma una instantánea coherente una vez por frame y la entrega al visualizador. El lienzo
combina dos capas: una nítida con estelas y otra desenfocada en modo aditivo, que es la
que produce el halo luminoso.

## Diagnósticos

Tareas de verificación por consola, útiles si algo no suena o no reacciona:

```bash
./gradlew checkDecoders                                  # ¿Están los SPI de FLAC y OGG?
./gradlew checkPipeline   -Ptracks="/tmp/a.flac,/tmp/b.ogg"  # Motor PCM: decodificación, análisis y salto
./gradlew checkFxPipeline -Ptracks="/tmp/a.mp3"          # Motor JavaFX: espectro y onda sintetizada
./gradlew checkMicrophone                                # Captura de entrada
```

En macOS, la primera vez que se activa el micrófono el sistema pide permiso; si se
deniega, la línea se abre igualmente pero solo entrega silencio.
