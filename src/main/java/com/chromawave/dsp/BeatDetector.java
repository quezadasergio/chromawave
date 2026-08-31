package com.chromawave.dsp;

/**
 * Detector de golpes por energía instantánea contra la media móvil de graves.
 * Es el mismo principio que usaban los visualizadores clásicos: un pico de
 * energía muy por encima del promedio reciente se interpreta como un beat.
 */
public final class BeatDetector {

    private static final int HISTORY = 48;
    private static final double MIN_INTERVAL_SEC = 0.11;

    private final double[] history = new double[HISTORY];
    private int index;
    private int filled;
    private double lastBeatTime = -1;
    private float intensity;

    /**
     * @param energy   energía de graves actual [0,1]
     * @param time     tiempo absoluto en segundos
     * @param deltaSec tiempo transcurrido desde la llamada anterior
     * @return true si en este frame comienza un golpe
     */
    public boolean update(double energy, double time, double deltaSec) {
        double average = 0;
        for (int i = 0; i < filled; i++) {
            average += history[i];
        }
        average = filled > 0 ? average / filled : 0;

        double variance = 0;
        for (int i = 0; i < filled; i++) {
            double d = history[i] - average;
            variance += d * d;
        }
        variance = filled > 0 ? variance / filled : 0;

        // Umbral adaptativo: con mucha varianza (música dinámica) se exige menos margen.
        double threshold = average * (1.45 - Math.min(0.35, variance * 12.0));

        history[index] = energy;
        index = (index + 1) % HISTORY;
        filled = Math.min(filled + 1, HISTORY);

        intensity = (float) Math.max(0, intensity - deltaSec * 3.2);

        boolean onset = filled >= HISTORY / 2
                && energy > threshold
                && energy > 0.06
                && (lastBeatTime < 0 || time - lastBeatTime > MIN_INTERVAL_SEC);

        if (onset) {
            lastBeatTime = time;
            intensity = (float) Math.clamp(0.55 + (energy - average) * 2.5, 0.0, 1.0);
        }
        return onset;
    }

    public float intensity() {
        return intensity;
    }

    public void reset() {
        java.util.Arrays.fill(history, 0);
        index = 0;
        filled = 0;
        intensity = 0;
        lastBeatTime = -1;
    }
}
