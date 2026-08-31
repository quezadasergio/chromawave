package com.chromawave.visual;

import com.chromawave.dsp.SpectrumData;

/** Todo lo que un visualizador necesita saber para pintar un frame. */
public final class RenderContext {

    public double width;
    public double height;
    public double deltaSec;
    public SpectrumData audio;
    public Palette palette;

    /** Desplazamiento continuo del color, avanza con el tiempo y con la energía. */
    public double hueShift;

    public double centerX() {
        return width / 2;
    }

    public double centerY() {
        return height / 2;
    }

    /** Lado menor del lienzo: base para escalar tamaños de forma proporcional. */
    public double minSide() {
        return Math.min(width, height);
    }
}
