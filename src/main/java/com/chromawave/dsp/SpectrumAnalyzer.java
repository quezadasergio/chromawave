package com.chromawave.dsp;

/**
 * Convierte un flujo continuo de muestras PCM mono en bandas logarítmicas
 * y una forma de onda lista para dibujar.
 *
 * <p>Las muestras se acumulan en un buffer circular; cada llamada a
 * {@link #analyze} usa la ventana más reciente de {@code fftSize} muestras,
 * de modo que el productor (hilo de audio) y el consumidor (hilo de render)
 * pueden ir a ritmos distintos sin bloquearse.
 */
public final class SpectrumAnalyzer {

    private static final int FFT_SIZE = 2048;
    private static final double MIN_DB = -85.0;
    private static final double MAX_DB = -10.0;
    private static final double MIN_HZ = 30.0;
    private static final double MAX_HZ = 16000.0;

    private final FFT fft = new FFT(FFT_SIZE);
    private final float[] ring = new float[FFT_SIZE * 2];
    private final float[] frame = new float[FFT_SIZE];
    private final double[] re = new double[FFT_SIZE];
    private final double[] im = new double[FFT_SIZE];
    private final float[] magnitudes = new float[FFT_SIZE / 2];

    private final int[] bandStart = new int[SpectrumData.BANDS];
    private final int[] bandEnd = new int[SpectrumData.BANDS];

    private int writePos;
    private final Object lock = new Object();
    private float sampleRate;

    public SpectrumAnalyzer(float sampleRate) {
        setSampleRate(sampleRate);
    }

    public void setSampleRate(float sampleRate) {
        if (this.sampleRate == sampleRate) {
            return;
        }
        this.sampleRate = sampleRate;
        double binHz = sampleRate / FFT_SIZE;
        int maxBin = FFT_SIZE / 2 - 1;
        for (int b = 0; b < SpectrumData.BANDS; b++) {
            // Reparto logarítmico: cada banda cubre el mismo intervalo en octavas.
            double lo = MIN_HZ * Math.pow(MAX_HZ / MIN_HZ, (double) b / SpectrumData.BANDS);
            double hi = MIN_HZ * Math.pow(MAX_HZ / MIN_HZ, (double) (b + 1) / SpectrumData.BANDS);
            int s = (int) Math.floor(lo / binHz);
            int e = (int) Math.ceil(hi / binHz);
            s = Math.clamp(s, 1, maxBin);
            e = Math.clamp(e, s + 1, maxBin + 1);
            bandStart[b] = s;
            bandEnd[b] = e;
        }
    }

    /** Añade muestras mono al buffer circular. Seguro desde el hilo de audio. */
    public void push(float[] samples, int offset, int length) {
        synchronized (lock) {
            for (int i = 0; i < length; i++) {
                ring[writePos] = samples[offset + i];
                writePos = (writePos + 1) % ring.length;
            }
        }
    }

    /** Vacía el historial acumulado (al cambiar de pista o parar la captura). */
    public void reset() {
        synchronized (lock) {
            java.util.Arrays.fill(ring, 0f);
            writePos = 0;
        }
    }

    /** Calcula bandas, forma de onda y nivel RMS de la ventana más reciente. */
    public void analyze(SourceAnalysis out) {
        synchronized (lock) {
            int start = (writePos - FFT_SIZE + ring.length) % ring.length;
            for (int i = 0; i < FFT_SIZE; i++) {
                frame[i] = ring[(start + i) % ring.length];
            }
        }

        double sumSquares = 0;
        for (int i = 0; i < FFT_SIZE; i++) {
            sumSquares += (double) frame[i] * frame[i];
        }
        out.level = (float) Math.min(1.0, Math.sqrt(sumSquares / FFT_SIZE) * 3.0);

        // La onda se submuestrea tomando el pico de cada tramo para no perder transitorios.
        int stride = FFT_SIZE / SpectrumData.WAVE_POINTS;
        for (int i = 0; i < SpectrumData.WAVE_POINTS; i++) {
            float peak = 0f;
            for (int j = 0; j < stride; j++) {
                float v = frame[i * stride + j];
                if (Math.abs(v) > Math.abs(peak)) {
                    peak = v;
                }
            }
            out.waveform[i] = Math.clamp(peak, -1f, 1f);
        }

        fft.applyWindow(frame, re);
        java.util.Arrays.fill(im, 0.0);
        fft.transform(re, im);
        fft.magnitudesDb(re, im, magnitudes, MIN_DB);

        for (int b = 0; b < SpectrumData.BANDS; b++) {
            float maxDb = (float) MIN_DB;
            for (int i = bandStart[b]; i < bandEnd[b]; i++) {
                if (magnitudes[i] > maxDb) {
                    maxDb = magnitudes[i];
                }
            }
            out.bands[b] = normalizeDb(maxDb, b);
        }
    }

    /**
     * Lleva dB a [0,1] y compensa la caída natural de energía en agudos,
     * de otro modo el extremo derecho del espectro quedaría siempre plano.
     */
    private static float normalizeDb(float db, int band) {
        double tilt = 14.0 * ((double) band / SpectrumData.BANDS);
        double v = (db + tilt - MIN_DB) / (MAX_DB - MIN_DB);
        return (float) Math.clamp(v, 0.0, 1.0);
    }
}
