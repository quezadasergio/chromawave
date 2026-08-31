package com.chromawave.dsp;

/**
 * Convierte un espectro de bandas lineales en dB (el que publica el analizador
 * de JavaFX) a las bandas logarítmicas que usa el resto de la aplicación.
 *
 * <p>Sin esta conversión los dos motores producirían datos incomparables: en un
 * reparto lineal sobre 22 kHz, todo el registro grave cae dentro de la primera
 * banda y el detector de golpes se queda prácticamente mudo.
 */
public final class LinearBandMapper {

    private static final double MIN_HZ = 30.0;
    private static final double MAX_HZ = 16000.0;

    private final int[] start = new int[SpectrumData.BANDS];
    private final int[] end = new int[SpectrumData.BANDS];
    private final float floorDb;

    /**
     * @param linearBands número de bandas que entrega la fuente
     * @param nyquistHz   frecuencia máxima representada
     * @param floorDb     umbral inferior en dB de la fuente
     */
    public LinearBandMapper(int linearBands, double nyquistHz, float floorDb) {
        this.floorDb = floorDb;
        double bandHz = nyquistHz / linearBands;
        for (int b = 0; b < SpectrumData.BANDS; b++) {
            double lo = MIN_HZ * Math.pow(MAX_HZ / MIN_HZ, (double) b / SpectrumData.BANDS);
            double hi = MIN_HZ * Math.pow(MAX_HZ / MIN_HZ, (double) (b + 1) / SpectrumData.BANDS);
            int s = (int) Math.floor(lo / bandHz);
            int e = (int) Math.ceil(hi / bandHz);
            s = Math.clamp(s, 0, linearBands - 1);
            e = Math.clamp(e, s + 1, linearBands);
            start[b] = s;
            end[b] = e;
        }
    }

    public void map(float[] linearDb, float[] out) {
        for (int b = 0; b < SpectrumData.BANDS; b++) {
            float maxDb = floorDb;
            int to = Math.min(end[b], linearDb.length);
            for (int i = start[b]; i < to; i++) {
                if (linearDb[i] > maxDb) {
                    maxDb = linearDb[i];
                }
            }
            // Compensa la caída natural de energía hacia los agudos para que el
            // extremo derecho del espectro no quede siempre plano.
            double tilt = 12.0 * ((double) b / SpectrumData.BANDS);
            double v = (maxDb + tilt - floorDb) / (0 - floorDb);
            out[b] = (float) Math.clamp(v, 0.0, 1.0);
        }
    }
}
