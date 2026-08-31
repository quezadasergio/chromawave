package com.chromawave.audio;

import java.io.File;
import java.util.function.Consumer;

/**
 * Contrato común para los dos motores de reproducción de ChromaWave:
 * el basado en {@code javafx.scene.media} y el basado en decodificación PCM.
 * Todos los métodos se invocan desde el hilo de aplicación de JavaFX.
 */
public interface PlaybackEngine {

    void load(File file) throws Exception;

    void play();

    void pause();

    void stop();

    /** Salta a la posición indicada en segundos. */
    void seek(double seconds);

    /** Volumen lineal en [0,1]. */
    void setVolume(double volume);

    double positionSeconds();

    double durationSeconds();

    boolean isPlaying();

    void dispose();

    void setOnReady(Runnable callback);

    void setOnEndOfMedia(Runnable callback);

    void setOnError(Consumer<String> callback);
}
