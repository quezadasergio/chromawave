package com.chromawave.dsp;

/**
 * Instantánea del estado del audio que consumen los visualizadores.
 * Se rellena en el hilo de audio y se lee en el hilo de animación de JavaFX,
 * por lo que {@link #copyFrom} realiza una copia completa bajo el lock del bus.
 */
public final class SpectrumData {

    /** Número de bandas logarítmicas expuestas a los visualizadores. */
    public static final int BANDS = 64;
    /** Número de muestras de la forma de onda expuesta. */
    public static final int WAVE_POINTS = 512;

    /** Magnitud normalizada [0,1] por banda, ya suavizada. */
    public final float[] bands = new float[BANDS];
    /** Pico decayente por banda, útil para las tapas de las barras. */
    public final float[] peaks = new float[BANDS];
    /** Forma de onda normalizada [-1,1]. */
    public final float[] waveform = new float[WAVE_POINTS];

    /** Energía global [0,1]. */
    public float level;
    /** Energía de graves / medios / agudos [0,1]. */
    public float bass;
    public float mid;
    public float treble;
    /** Intensidad del último golpe detectado, decae con el tiempo. */
    public float beat;
    /** true solo en el frame exacto en que se detecta un golpe. */
    public boolean beatOnset;
    /** Segundos transcurridos desde el arranque, para animaciones continuas. */
    public double time;

    public void copyFrom(SpectrumData other) {
        System.arraycopy(other.bands, 0, bands, 0, BANDS);
        System.arraycopy(other.peaks, 0, peaks, 0, BANDS);
        System.arraycopy(other.waveform, 0, waveform, 0, WAVE_POINTS);
        level = other.level;
        bass = other.bass;
        mid = other.mid;
        treble = other.treble;
        beat = other.beat;
        beatOnset = other.beatOnset;
        time = other.time;
    }

    public void clear() {
        java.util.Arrays.fill(bands, 0f);
        java.util.Arrays.fill(peaks, 0f);
        java.util.Arrays.fill(waveform, 0f);
        level = bass = mid = treble = beat = 0f;
        beatOnset = false;
    }
}
