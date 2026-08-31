package com.chromawave.visual.patterns;

import com.chromawave.dsp.SpectrumData;
import com.chromawave.visual.RenderContext;
import com.chromawave.visual.Visualizer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.random.RandomGenerator;

/**
 * Barras dispersas por el lienzo con posición, ángulo y grosor aleatorios.
 * Cada barra está atada a una banda del espectro y todas se recolocan de golpe
 * cuando el detector encuentra un beat, lo que genera una composición que se
 * reinventa constantemente.
 */
public final class RandomBarsVisualizer implements Visualizer {

    private static final int COUNT = 90;

    private final RandomGenerator random = RandomGenerator.getDefault();
    private final double[] x = new double[COUNT];
    private final double[] y = new double[COUNT];
    private final double[] angle = new double[COUNT];
    private final double[] thickness = new double[COUNT];
    private final double[] lengthScale = new double[COUNT];
    private final double[] spin = new double[COUNT];
    private final int[] band = new int[COUNT];

    private boolean initialized;

    @Override
    public String name() {
        return "Barras aleatorias";
    }

    private void scatter(int i) {
        x[i] = random.nextDouble();
        y[i] = random.nextDouble();
        angle[i] = random.nextDouble() * Math.PI * 2;
        thickness[i] = 0.004 + random.nextDouble() * 0.016;
        lengthScale[i] = 0.08 + random.nextDouble() * 0.32;
        spin[i] = (random.nextDouble() - 0.5) * 1.6;
        band[i] = random.nextInt(SpectrumData.BANDS);
    }

    @Override
    public void update(RenderContext ctx) {
        if (!initialized) {
            for (int i = 0; i < COUNT; i++) {
                scatter(i);
            }
            initialized = true;
        }
        for (int i = 0; i < COUNT; i++) {
            angle[i] += spin[i] * ctx.deltaSec * (0.3 + ctx.audio.level * 2.0);
        }
        if (ctx.audio.beatOnset) {
            // Solo un tercio se recoloca en cada golpe: así la escena cambia
            // sin perder por completo la continuidad visual.
            int toMove = COUNT / 3;
            for (int n = 0; n < toMove; n++) {
                scatter(random.nextInt(COUNT));
            }
        }
    }

    @Override
    public void render(GraphicsContext g, RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        double min = ctx.minSide();

        for (int i = 0; i < COUNT; i++) {
            double value = audio.bands[band[i]];
            if (value < 0.03) {
                continue;
            }
            double length = min * lengthScale[i] * (0.25 + value * 1.4);
            double width = min * thickness[i] * (0.6 + value);
            double cx = x[i] * ctx.width;
            double cy = y[i] * ctx.height;
            double dx = Math.cos(angle[i]) * length / 2;
            double dy = Math.sin(angle[i]) * length / 2;

            Color color = ctx.palette.at(
                    ctx.hueShift + band[i] / (double) SpectrumData.BANDS,
                    0.15 + value * 0.8);
            g.setStroke(color);
            g.setLineWidth(width);
            g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            g.strokeLine(cx - dx, cy - dy, cx + dx, cy + dy);
        }
    }

    @Override
    public double trail() {
        return 0.80;
    }

    @Override
    public void reset() {
        initialized = false;
    }
}
