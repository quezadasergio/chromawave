package com.chromawave.visual.patterns;

import com.chromawave.dsp.SpectrumData;
import com.chromawave.visual.RenderContext;
import com.chromawave.visual.Visualizer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Manchas orgánicas de color cuyo contorno se deforma con el espectro y cuyo
 * tamaño crece con la energía. Es el patrón más pictórico del conjunto: no
 * dibuja datos, dibuja una forma que respira con la música.
 */
public final class LiquidBlobVisualizer implements Visualizer {

    private static final int BLOBS = 4;
    private static final int VERTICES = 96;

    private final double[] blobPhase = new double[BLOBS];
    private final double[] blobDrift = new double[BLOBS];
    private final double[] xs = new double[VERTICES];
    private final double[] ys = new double[VERTICES];

    private double pulse;

    public LiquidBlobVisualizer() {
        for (int i = 0; i < BLOBS; i++) {
            blobPhase[i] = i * 1.7;
            blobDrift[i] = 0.25 + i * 0.17;
        }
    }

    @Override
    public String name() {
        return "Manchas líquidas";
    }

    @Override
    public void update(RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        for (int i = 0; i < BLOBS; i++) {
            blobPhase[i] += ctx.deltaSec * (0.18 + audio.mid * 0.9) * blobDrift[i];
        }
        // El pulso sube de golpe con el beat y baja despacio, como un latido.
        double target = audio.level * 0.6 + audio.beat * 0.6;
        double rate = target > pulse ? 9.0 : 1.9;
        pulse += (target - pulse) * Math.min(1, ctx.deltaSec * rate);
    }

    @Override
    public void render(GraphicsContext g, RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        double min = ctx.minSide();

        for (int b = 0; b < BLOBS; b++) {
            double phase = blobPhase[b];
            // Cada mancha describe una figura de Lissajous lenta alrededor del centro.
            double cx = ctx.centerX() + Math.cos(phase * 0.7 + b) * ctx.width * 0.16;
            double cy = ctx.centerY() + Math.sin(phase * 0.53 + b * 1.3) * ctx.height * 0.16;
            double base = min * (0.10 + 0.05 * b / BLOBS) * (1.0 + pulse * 1.1);

            for (int v = 0; v < VERTICES; v++) {
                double theta = v * 2 * Math.PI / VERTICES;
                int bandIndex = (v * SpectrumData.BANDS / VERTICES + b * 7) % SpectrumData.BANDS;
                double bump = audio.bands[bandIndex] * min * 0.13;
                double ripple = Math.sin(theta * 3 + phase * 2.1) * min * 0.02
                        + Math.sin(theta * 7 - phase * 1.4) * min * 0.012;
                double r = base + bump + ripple;
                xs[v] = cx + Math.cos(theta) * r;
                ys[v] = cy + Math.sin(theta) * r;
            }

            double tint = ctx.hueShift + b * 0.18;
            Color inner = ctx.palette.at(tint, 0.42 + pulse * 0.35);
            Color outer = ctx.palette.at(tint + 0.22, 0.0);
            g.setFill(new RadialGradient(0, 0, cx, cy, base * 2.1, false, CycleMethod.NO_CYCLE,
                    new Stop(0, inner), new Stop(1, outer)));
            g.fillPolygon(xs, ys, VERTICES);

            g.setStroke(ctx.palette.at(tint + 0.35, 0.35 + audio.treble * 0.5));
            g.setLineWidth(Math.max(1.0, min * 0.0022));
            g.strokePolygon(xs, ys, VERTICES);
        }
    }

    @Override
    public double trail() {
        return 0.58;
    }

    @Override
    public void reset() {
        pulse = 0;
    }
}
