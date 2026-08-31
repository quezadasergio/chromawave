package com.chromawave.visual.patterns;

import com.chromawave.dsp.SpectrumData;
import com.chromawave.visual.RenderContext;
import com.chromawave.visual.Visualizer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.random.RandomGenerator;

/**
 * Campo de partículas que orbitan el centro. Los agudos aceleran el giro, los
 * graves las empujan hacia fuera y cada golpe provoca una expansión brusca,
 * de forma parecida a una nebulosa que late.
 */
public final class ParticleFieldVisualizer implements Visualizer {

    private static final int COUNT = 320;

    private final RandomGenerator random = RandomGenerator.getDefault();
    private final double[] angle = new double[COUNT];
    private final double[] radius = new double[COUNT];
    private final double[] speed = new double[COUNT];
    private final double[] size = new double[COUNT];
    private final int[] band = new int[COUNT];

    private boolean initialized;

    @Override
    public String name() {
        return "Partículas";
    }

    private void spawn(int i, boolean fromCenter) {
        angle[i] = random.nextDouble() * Math.PI * 2;
        radius[i] = fromCenter ? random.nextDouble() * 0.05 : random.nextDouble() * 0.5;
        speed[i] = 0.25 + random.nextDouble() * 0.9;
        size[i] = 0.002 + random.nextDouble() * 0.010;
        band[i] = random.nextInt(SpectrumData.BANDS);
    }

    @Override
    public void update(RenderContext ctx) {
        if (!initialized) {
            for (int i = 0; i < COUNT; i++) {
                spawn(i, false);
            }
            initialized = true;
        }
        SpectrumData audio = ctx.audio;
        double push = (0.02 + audio.bass * 0.55 + audio.beat * 0.7) * ctx.deltaSec;
        double spin = (0.15 + audio.treble * 1.9) * ctx.deltaSec;

        for (int i = 0; i < COUNT; i++) {
            angle[i] += spin * speed[i];
            radius[i] += push * speed[i];
            // Al salir del encuadre la partícula renace en el centro: el campo
            // se mantiene poblado sin necesidad de asignar objetos nuevos.
            if (radius[i] > 0.78) {
                spawn(i, true);
            }
        }
    }

    @Override
    public void render(GraphicsContext g, RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        double cx = ctx.centerX();
        double cy = ctx.centerY();
        double min = ctx.minSide();

        for (int i = 0; i < COUNT; i++) {
            double value = audio.bands[band[i]];
            double r = radius[i] * min;
            double px = cx + Math.cos(angle[i]) * r;
            double py = cy + Math.sin(angle[i]) * r * 0.92;
            double d = min * size[i] * (0.8 + value * 3.2);
            double fade = Math.clamp(1.0 - radius[i] / 0.78, 0.0, 1.0);

            Color color = ctx.palette.at(
                    ctx.hueShift + band[i] / (double) SpectrumData.BANDS,
                    (0.12 + value * 0.85) * fade);
            g.setFill(color);
            g.fillOval(px - d / 2, py - d / 2, d, d);
        }
    }

    @Override
    public double trail() {
        return 0.86;
    }

    @Override
    public void reset() {
        initialized = false;
    }
}
