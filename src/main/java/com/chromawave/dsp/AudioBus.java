package com.chromawave.dsp;

/**
 * Punto central de mezcla del análisis: combina la contribución del archivo y
 * la del micrófono, aplica ganancia y suavizado, y publica un {@link SpectrumData}
 * coherente para los visualizadores.
 *
 * <p>Los productores escriben en {@link #fileSlot()} / {@link #micSlot()} desde
 * sus propios hilos; el hilo de JavaFX llama a {@link #update} una vez por frame.
 */
public final class AudioBus {

    private final SourceAnalysis fileAnalysis = new SourceAnalysis();
    private final SourceAnalysis micAnalysis = new SourceAnalysis();
    private final SpectrumData data = new SpectrumData();
    private final BeatDetector beatDetector = new BeatDetector();
    private final Object lock = new Object();

    private final float[] mixed = new float[SpectrumData.BANDS];

    private volatile float fileWeight = 1f;
    private volatile float micWeight = 0f;
    private volatile float sensitivity = 1f;
    private volatile float smoothing = 0.55f;

    private double elapsed;

    public SourceAnalysis fileSlot() {
        return fileAnalysis;
    }

    public SourceAnalysis micSlot() {
        return micAnalysis;
    }

    public void setFileWeight(float w) {
        this.fileWeight = Math.clamp(w, 0f, 1f);
    }

    public void setMicWeight(float w) {
        this.micWeight = Math.clamp(w, 0f, 1f);
    }

    /** Ganancia global del análisis, entre 0.2x y 3x. */
    public void setSensitivity(float s) {
        this.sensitivity = Math.clamp(s, 0.2f, 3f);
    }

    /** 0 = respuesta instantánea y nerviosa, 1 = movimiento muy suave. */
    public void setSmoothing(float s) {
        this.smoothing = Math.clamp(s, 0f, 0.95f);
    }

    public void clearFile() {
        synchronized (lock) {
            fileAnalysis.clear();
        }
    }

    public void clearMic() {
        synchronized (lock) {
            micAnalysis.clear();
        }
    }

    public void reset() {
        synchronized (lock) {
            fileAnalysis.clear();
            micAnalysis.clear();
            data.clear();
            beatDetector.reset();
        }
    }

    /** Recalcula el estado mezclado. Debe llamarse una vez por frame de animación. */
    public void update(double deltaSec) {
        synchronized (lock) {
            elapsed += deltaSec;
            data.time = elapsed;

            float fw = fileWeight;
            float mw = micWeight;
            float gain = sensitivity;

            for (int b = 0; b < SpectrumData.BANDS; b++) {
                // Mezcla por máximo ponderado: evita que dos fuentes activas
                // se sumen hasta saturar constantemente el visualizador.
                float v = Math.max(fileAnalysis.bands[b] * fw, micAnalysis.bands[b] * mw);
                mixed[b] = Math.clamp(v * gain, 0f, 1f);
            }

            // Ataque rápido y caída lenta: así los golpes se ven nítidos
            // pero el descenso resulta agradable a la vista.
            float attack = 1f - smoothing * 0.55f;
            float release = 1f - smoothing * 0.92f;
            for (int b = 0; b < SpectrumData.BANDS; b++) {
                float target = mixed[b];
                float current = data.bands[b];
                float k = target > current ? attack : release;
                data.bands[b] = current + (target - current) * k;

                float peak = data.peaks[b] - (float) (deltaSec * 0.85);
                data.peaks[b] = Math.max(data.bands[b], Math.max(peak, 0f));
            }

            for (int i = 0; i < SpectrumData.WAVE_POINTS; i++) {
                float w = fileAnalysis.waveform[i] * fw + micAnalysis.waveform[i] * mw;
                data.waveform[i] = Math.clamp(w * gain, -1f, 1f);
            }

            data.bass = average(data.bands, 0, 10);
            data.mid = average(data.bands, 10, 34);
            data.treble = average(data.bands, 34, SpectrumData.BANDS);
            data.level = Math.clamp(Math.max(fileAnalysis.level * fw, micAnalysis.level * mw) * gain, 0f, 1f);

            data.beatOnset = beatDetector.update(data.bass, elapsed, deltaSec);
            data.beat = beatDetector.intensity();
        }
    }

    /** Copia el estado actual en el destino indicado, para consumo del renderer. */
    public void snapshot(SpectrumData target) {
        synchronized (lock) {
            target.copyFrom(data);
        }
    }

    private static float average(float[] values, int from, int to) {
        float sum = 0;
        for (int i = from; i < to; i++) {
            sum += values[i];
        }
        return sum / (to - from);
    }
}
