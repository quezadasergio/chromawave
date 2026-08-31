package com.chromawave.visual;

import com.chromawave.dsp.SpectrumData;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Superficie de dibujo de ChromaWave.
 *
 * <p>Usa dos lienzos superpuestos: uno nítido con el fondo opaco y otro
 * transparente que se dibuja desenfocado y se compone en modo aditivo. Esa
 * segunda capa es la que produce el halo luminoso. Ninguno de los dos se borra
 * del todo entre frames, sino que se atenúa, y de ahí salen las estelas.
 */
public final class VisualizerPane extends Region {

    private final Canvas sharpCanvas = new Canvas();
    private final Canvas glowCanvas = new Canvas();
    private final GaussianBlur blur = new GaussianBlur(24);
    private final RenderContext ctx = new RenderContext();

    private Visualizer visualizer;
    private Palette palette = Palette.NEON;
    private boolean glowEnabled = true;

    public VisualizerPane() {
        glowCanvas.setEffect(blur);
        glowCanvas.setBlendMode(BlendMode.ADD);
        glowCanvas.setMouseTransparent(true);
        sharpCanvas.setMouseTransparent(true);
        getChildren().addAll(sharpCanvas, glowCanvas);
        setStyle("-fx-background-color: black;");
    }

    public void setVisualizer(Visualizer visualizer) {
        this.visualizer = visualizer;
        if (visualizer != null) {
            visualizer.reset();
        }
        clear();
    }

    public void setPalette(Palette palette) {
        this.palette = palette;
        clear();
    }

    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
        glowCanvas.setVisible(enabled);
        if (!enabled) {
            GraphicsContext g = glowCanvas.getGraphicsContext2D();
            g.clearRect(0, 0, glowCanvas.getWidth(), glowCanvas.getHeight());
        }
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        for (Canvas canvas : new Canvas[]{sharpCanvas, glowCanvas}) {
            if (canvas.getWidth() != w || canvas.getHeight() != h) {
                canvas.setWidth(w);
                canvas.setHeight(h);
            }
            canvas.relocate(0, 0);
        }
        // El desenfoque se escala con el lienzo para que el halo se vea igual
        // tanto en ventana pequeña como en pantalla completa.
        blur.setRadius(Math.clamp(Math.min(w, h) * 0.035, 10, 46));
    }

    private void clear() {
        GraphicsContext sharp = sharpCanvas.getGraphicsContext2D();
        sharp.setFill(palette.background());
        sharp.fillRect(0, 0, sharpCanvas.getWidth(), sharpCanvas.getHeight());
        GraphicsContext glow = glowCanvas.getGraphicsContext2D();
        glow.clearRect(0, 0, glowCanvas.getWidth(), glowCanvas.getHeight());
    }

    /** Dibuja un frame completo. Debe llamarse desde el hilo de JavaFX. */
    public void render(SpectrumData audio, double deltaSec) {
        double w = getWidth();
        double h = getHeight();
        if (visualizer == null || w <= 0 || h <= 0) {
            return;
        }

        ctx.width = w;
        ctx.height = h;
        ctx.deltaSec = deltaSec;
        ctx.audio = audio;
        ctx.palette = palette;
        // El color deriva más deprisa cuanta más energía tiene la música.
        ctx.hueShift += deltaSec * (0.015 + audio.level * 0.13 + audio.beat * 0.05);

        visualizer.update(ctx);
        double fade = 1.0 - Math.clamp(visualizer.trail(), 0.0, 0.98);

        GraphicsContext sharp = sharpCanvas.getGraphicsContext2D();
        sharp.save();
        Color bg = palette.background();
        sharp.setGlobalBlendMode(BlendMode.SRC_OVER);
        sharp.setFill(Color.color(bg.getRed(), bg.getGreen(), bg.getBlue(), fade));
        sharp.fillRect(0, 0, w, h);
        visualizer.render(sharp, ctx);
        sharp.restore();

        if (glowEnabled) {
            GraphicsContext glow = glowCanvas.getGraphicsContext2D();
            glow.save();
            // El halo solo envuelve al frame actual: se borra por completo en vez
            // de atenuarse, porque las estelas ya las aporta la capa nítida.
            glow.clearRect(0, 0, w, h);
            glow.setGlobalBlendMode(BlendMode.SRC_OVER);
            glow.setGlobalAlpha(0.7);
            visualizer.render(glow, ctx);
            glow.restore();
        }
    }
}
