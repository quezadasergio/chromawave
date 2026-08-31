package com.chromawave.visual.patterns;

import com.chromawave.dsp.SpectrumData;
import com.chromawave.visual.RenderContext;
import com.chromawave.visual.Visualizer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Corona radial: el espectro se enrolla alrededor de un círculo que respira con
 * los graves, y cada golpe lanza un anillo de choque hacia fuera.
 */
public final class RadialBurstVisualizer implements Visualizer {

    private static final int RINGS = 6;

    private final double[] ringRadius = new double[RINGS];
    private final double[] ringEnergy = new double[RINGS];
    private int nextRing;

    private double rotation;
    private double breath;

    @Override
    public String name() {
        return "Estallido radial";
    }

    @Override
    public void update(RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        rotation += ctx.deltaSec * (0.12 + audio.treble * 1.1);
        breath += (audio.bass - breath) * Math.min(1, ctx.deltaSec * 6);

        for (int i = 0; i < RINGS; i++) {
            if (ringEnergy[i] > 0) {
                ringRadius[i] += ctx.deltaSec * ctx.minSide() * 0.55;
                ringEnergy[i] -= ctx.deltaSec * 0.85;
            }
        }
        if (audio.beatOnset) {
            ringRadius[nextRing] = ctx.minSide() * 0.12;
            ringEnergy[nextRing] = audio.beat;
            nextRing = (nextRing + 1) % RINGS;
        }
    }

    @Override
    public void render(GraphicsContext g, RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        double cx = ctx.centerX();
        double cy = ctx.centerY();
        double baseRadius = ctx.minSide() * (0.14 + breath * 0.10);
        int bands = SpectrumData.BANDS;

        for (int i = 0; i < RINGS; i++) {
            if (ringEnergy[i] <= 0) {
                continue;
            }
            g.setStroke(ctx.palette.at(ctx.hueShift + 0.3, ringEnergy[i] * 0.55));
            g.setLineWidth(2 + ringEnergy[i] * 8);
            g.strokeOval(cx - ringRadius[i], cy - ringRadius[i], ringRadius[i] * 2, ringRadius[i] * 2);
        }

        // El espectro se dibuja duplicado en espejo para lograr simetría bilateral.
        int spokes = bands * 2;
        for (int s = 0; s < spokes; s++) {
            int b = s < bands ? s : spokes - 1 - s;
            double value = audio.bands[b];
            double theta = rotation + s * (2 * Math.PI / spokes);
            double length = baseRadius + value * ctx.minSide() * 0.34;

            double sin = Math.sin(theta);
            double cos = Math.cos(theta);
            Color color = ctx.palette.at(ctx.hueShift + b / (double) bands, 0.25 + value * 0.75);
            g.setStroke(color);
            g.setLineWidth(Math.max(1.5, ctx.minSide() * 0.006 * (0.5 + value)));
            g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            g.strokeLine(cx + cos * baseRadius, cy + sin * baseRadius,
                    cx + cos * length, cy + sin * length);
        }

        double coreRadius = baseRadius * (0.55 + audio.beat * 0.35);
        g.setFill(ctx.palette.at(ctx.hueShift + 0.6, 0.20 + audio.level * 0.5));
        g.fillOval(cx - coreRadius, cy - coreRadius, coreRadius * 2, coreRadius * 2);
    }

    @Override
    public double trail() {
        return 0.72;
    }

    @Override
    public void reset() {
        java.util.Arrays.fill(ringEnergy, 0);
        java.util.Arrays.fill(ringRadius, 0);
        rotation = 0;
        breath = 0;
    }
}
