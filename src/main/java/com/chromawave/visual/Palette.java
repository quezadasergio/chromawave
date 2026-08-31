package com.chromawave.visual;

import javafx.scene.paint.Color;

/**
 * Paletas de color de ChromaWave. Cada paleta se define por una lista de
 * paradas y se muestrea con un parámetro continuo t en [0,1], de forma que los
 * visualizadores puedan pedir "el color de esta banda" sin saber nada del tema.
 */
public enum Palette {

    NEON("Neón", new Color[]{
            Color.web("#00e5ff"), Color.web("#7c4dff"), Color.web("#ff2d95"), Color.web("#ffd166")}),
    FUEGO("Fuego", new Color[]{
            Color.web("#2b0a00"), Color.web("#ff4d00"), Color.web("#ff9e00"), Color.web("#ffe66d")}),
    OCEANO("Océano", new Color[]{
            Color.web("#012a4a"), Color.web("#1f7a8c"), Color.web("#41ead4"), Color.web("#d9faff")}),
    ARCOIRIS("Arcoíris", new Color[]{
            Color.web("#ff0040"), Color.web("#ffee00"), Color.web("#00ff7f"), Color.web("#00b3ff"),
            Color.web("#b400ff"), Color.web("#ff0040")}),
    MONOCROMO("Monocromo", new Color[]{
            Color.web("#1a1a1a"), Color.web("#8a8a8a"), Color.web("#ffffff")}),
    ATARDECER("Atardecer", new Color[]{
            Color.web("#22162b"), Color.web("#e85d75"), Color.web("#ff9e5e"), Color.web("#ffe9c9")});

    private final String label;
    private final Color[] stops;

    Palette(String label, Color[] stops) {
        this.label = label;
        this.stops = stops;
    }

    public String label() {
        return label;
    }

    /** Interpola la paleta en t (se envuelve fuera de [0,1] para animaciones cíclicas). */
    public Color at(double t) {
        double x = t - Math.floor(t);
        double scaled = x * (stops.length - 1);
        int i = (int) scaled;
        int j = Math.min(i + 1, stops.length - 1);
        return stops[i].interpolate(stops[j], scaled - i);
    }

    public Color at(double t, double opacity) {
        Color c = at(t);
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), Math.clamp(opacity, 0.0, 1.0));
    }

    /** Color de fondo coherente con la paleta, muy oscuro para dar contraste. */
    public Color background() {
        Color base = stops[0].deriveColor(0, 1, 0.18, 1);
        return Color.color(base.getRed() * 0.4, base.getGreen() * 0.4, base.getBlue() * 0.45);
    }

    @Override
    public String toString() {
        return label;
    }
}
