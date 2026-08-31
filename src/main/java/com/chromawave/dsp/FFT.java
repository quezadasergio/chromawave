package com.chromawave.dsp;

/**
 * FFT radix-2 in-place sobre arreglos reales/imaginarios separados.
 * El tamaño se fija al construir para precalcular la tabla de twiddles
 * y la permutación de bit-reversal, ya que se invoca ~40 veces por segundo.
 */
public final class FFT {

    private final int size;
    private final int levels;
    private final double[] cosTable;
    private final double[] sinTable;
    private final int[] reversed;
    private final double[] window;

    public FFT(int size) {
        if (size < 2 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("El tamaño de la FFT debe ser potencia de dos: " + size);
        }
        this.size = size;
        this.levels = Integer.numberOfTrailingZeros(size);
        this.cosTable = new double[size / 2];
        this.sinTable = new double[size / 2];
        for (int i = 0; i < size / 2; i++) {
            double angle = 2 * Math.PI * i / size;
            cosTable[i] = Math.cos(angle);
            sinTable[i] = Math.sin(angle);
        }
        this.reversed = new int[size];
        for (int i = 0; i < size; i++) {
            reversed[i] = Integer.reverse(i) >>> (32 - levels);
        }
        this.window = new double[size];
        for (int i = 0; i < size; i++) {
            // Ventana de Hann: reduce el leakage espectral entre bins.
            window[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (size - 1)));
        }
    }

    public int size() {
        return size;
    }

    /** Aplica la ventana de Hann a la señal de entrada, copiando en destino. */
    public void applyWindow(float[] src, double[] dst) {
        for (int i = 0; i < size; i++) {
            dst[i] = src[i] * window[i];
        }
    }

    public void transform(double[] real, double[] imag) {
        for (int i = 0; i < size; i++) {
            int j = reversed[i];
            if (j > i) {
                double tr = real[i];
                real[i] = real[j];
                real[j] = tr;
                double ti = imag[i];
                imag[i] = imag[j];
                imag[j] = ti;
            }
        }
        for (int len = 2; len <= size; len <<= 1) {
            int half = len / 2;
            int step = size / len;
            for (int i = 0; i < size; i += len) {
                for (int j = i, k = 0; j < i + half; j++, k += step) {
                    int l = j + half;
                    double tRe = real[l] * cosTable[k] + imag[l] * sinTable[k];
                    double tIm = -real[l] * sinTable[k] + imag[l] * cosTable[k];
                    real[l] = real[j] - tRe;
                    imag[l] = imag[j] - tIm;
                    real[j] += tRe;
                    imag[j] += tIm;
                }
            }
        }
    }

    /** Calcula magnitudes en dB (rango [minDb, 0]) para los primeros size/2 bins. */
    public void magnitudesDb(double[] real, double[] imag, float[] out, double minDb) {
        int bins = Math.min(out.length, size / 2);
        double norm = 2.0 / size;
        for (int i = 0; i < bins; i++) {
            double mag = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]) * norm;
            double db = 20 * Math.log10(Math.max(mag, 1e-10));
            out[i] = (float) Math.max(db, minDb);
        }
    }
}
