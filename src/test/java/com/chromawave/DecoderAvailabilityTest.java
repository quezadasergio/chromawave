package com.chromawave;

import javax.sound.sampled.spi.AudioFileReader;
import java.util.ServiceLoader;

/**
 * Comprobación de arranque: confirma que los proveedores SPI de FLAC y Vorbis
 * están en el classpath. Si faltan, la aplicación seguiría abriendo MP3 y WAV
 * pero fallaría en silencio con los formatos decodificados.
 */
public final class DecoderAvailabilityTest {

    public static void main(String[] args) {
        boolean flac = false;
        boolean vorbis = false;
        for (AudioFileReader reader : ServiceLoader.load(AudioFileReader.class)) {
            String name = reader.getClass().getName();
            System.out.println("Proveedor detectado: " + name);
            if (name.toLowerCase().contains("flac")) {
                flac = true;
            }
            if (name.toLowerCase().contains("vorbis") || name.toLowerCase().contains("ogg")) {
                vorbis = true;
            }
        }
        System.out.println("FLAC disponible:   " + flac);
        System.out.println("Vorbis disponible: " + vorbis);
        if (!flac || !vorbis) {
            throw new IllegalStateException("Faltan decodificadores en el classpath");
        }
    }
}
