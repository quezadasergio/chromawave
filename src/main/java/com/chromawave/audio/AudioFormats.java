package com.chromawave.audio;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Extensiones admitidas y elección del motor adecuado para cada una. */
public final class AudioFormats {

    /** Formatos que reproduce directamente el motor multimedia de JavaFX. */
    private static final Set<String> NATIVE = Set.of("mp3", "wav", "aiff", "aif", "m4a", "aac", "mp4");

    /** Formatos cubiertos por los decodificadores SPI en Java puro. */
    private static final Set<String> DECODED = Set.of("flac", "ogg", "oga");

    public static final List<String> EXTENSION_FILTERS = List.of(
            "*.mp3", "*.wav", "*.aiff", "*.aif", "*.m4a", "*.aac", "*.mp4", "*.flac", "*.ogg", "*.oga");

    private AudioFormats() {
    }

    public static String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(File file) {
        String ext = extensionOf(file);
        return NATIVE.contains(ext) || DECODED.contains(ext);
    }

    public static boolean requiresDecoder(File file) {
        return DECODED.contains(extensionOf(file));
    }
}
