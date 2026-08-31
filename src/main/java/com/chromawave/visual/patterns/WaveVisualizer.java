package com.chromawave.visual.patterns;

import com.chromawave.dsp.SpectrumData;
import com.chromawave.visual.RenderContext;
import com.chromawave.visual.Visualizer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Ondas superpuestas: varias copias de la forma de onda desfasadas en el tiempo
 * y desplazadas en color, lo que produce ese efecto de cinta luminosa.
 */
public final class WaveVisualizer implements Visualizer {

    private static final int LAYERS = 5;

    private double phase;

    @Override
    public String name() {
        return "Onda";
    }

    @Override
    public void update(RenderContext ctx) {
        phase += ctx.deltaSec * (0.35 + ctx.audio.level * 1.8);
    }

    @Override
    public void render(GraphicsContext g, RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        double cy = ctx.centerY();
        double amplitude = ctx.height * (0.10 + audio.level * 0.30 + audio.beat * 0.10);
        int points = SpectrumData.WAVE_POINTS;
        double step = ctx.width / (points - 1.0);

        for (int layer = 0; layer < LAYERS; layer++) {
            double depth = layer / (double) (LAYERS - 1);
            // Las capas traseras van más lentas y más tenues: dan sensación de profundidad.
            double layerAmp = amplitude * (1.0 - depth * 0.55);
            double offset = phase * (1.0 + depth * 0.6) + depth * 2.4;
            double alpha = 0.9 - depth * 0.62;

            Color color = ctx.palette.at(ctx.hueShift + depth * 0.25, alpha);
            g.setStroke(color);
            g.setLineWidth(Math.max(1.2, ctx.minSide() * (0.006 - depth * 0.0035)));
            g.beginPath();
            for (int i = 0; i < points; i++) {
                double t = i / (double) (points - 1);
                // Envolvente en forma de campana para que la onda muera en los bordes.
                double envelope = Math.sin(Math.PI * t);
                double sample = audio.waveform[i];
                double wobble = Math.sin(t * 9.0 + offset) * 0.28 * audio.bass;
                double y = cy + (sample + wobble) * layerAmp * envelope;
                if (i == 0) {
                    g.moveTo(0, y);
                } else {
                    g.lineTo(i * step, y);
                }
            }
            g.stroke();
        }

        // Línea de base que pulsa con los graves.
        double glow = 0.25 + audio.bass * 0.6;
        g.setStroke(ctx.palette.at(ctx.hueShift + 0.5, glow));
        g.setLineWidth(1.0 + audio.beat * 3.0);
        g.strokeLine(0, cy, ctx.width, cy);
    }

    @Override
    public double trail() {
        return 0.75;
    }

    @Override
    public void reset() {
        phase = 0;
    }
}
