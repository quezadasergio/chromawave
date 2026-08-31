package com.chromawave.dsp;

/** Resultado crudo (sin suavizar) del análisis de una única fuente de audio. */
public final class SourceAnalysis {
    public final float[] bands = new float[SpectrumData.BANDS];
    public final float[] waveform = new float[SpectrumData.WAVE_POINTS];
    public float level;

    public void clear() {
        java.util.Arrays.fill(bands, 0f);
        java.util.Arrays.fill(waveform, 0f);
        level = 0f;
    }
}
