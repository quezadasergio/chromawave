package com.chromawave.visual.patterns;

import com.chromawave.dsp.SpectrumData;
import com.chromawave.visual.RenderContext;
import com.chromawave.visual.Visualizer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Barras de frecuencia clásicas, espejadas respecto al centro y con las tapas
 * de pico que caen lentamente, como en los ecualizadores de los reproductores
 * de escritorio de antaño.
 */
public final class FrequencyBarsVisualizer implements Visualizer {

    @Override
    public String name() {
        return "Barras de frecuencia";
    }

    @Override
    public void render(GraphicsContext g, RenderContext ctx) {
        SpectrumData audio = ctx.audio;
        int bands = SpectrumData.BANDS;
        double slot = ctx.width / bands;
        double gap = Math.max(1.0, slot * 0.16);
        double barWidth = slot - gap;
        double cy = ctx.centerY();
        double maxHeight = ctx.height * 0.46;

        for (int b = 0; b < bands; b++) {
            double value = audio.bands[b];
            double height = Math.max(2.0, value * maxHeight);
            double x = b * slot + gap / 2;

            Color color = ctx.palette.at(ctx.hueShift + b / (double) bands, 0.35 + value * 0.65);
            g.setFill(color);
            // Espejo vertical: una barra hacia arriba y su reflejo hacia abajo.
            g.fillRoundRect(x, cy - height, barWidth, height, barWidth * 0.4, barWidth * 0.4);
            g.setFill(ctx.palette.at(ctx.hueShift + b / (double) bands, 0.18 + value * 0.32));
            g.fillRoundRect(x, cy, barWidth, height * 0.72, barWidth * 0.4, barWidth * 0.4);

            double peak = audio.peaks[b] * maxHeight;
            if (peak > 3) {
                g.setFill(ctx.palette.at(ctx.hueShift + b / (double) bands + 0.12, 0.95));
                g.fillRect(x, cy - peak - 3, barWidth, 3);
            }
        }

        // Destello horizontal en el eje cuando entra un golpe.
        if (audio.beat > 0.01) {
            g.setFill(ctx.palette.at(ctx.hueShift + 0.4, audio.beat * 0.35));
            g.fillRect(0, cy - 2, ctx.width, 4);
        }
    }

    @Override
    public double trail() {
        return 0.45;
    }
}
