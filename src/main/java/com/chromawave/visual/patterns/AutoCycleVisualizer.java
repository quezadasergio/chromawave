package com.chromawave.visual.patterns;

import com.chromawave.visual.RenderContext;
import com.chromawave.visual.Visualizer;

import javafx.scene.canvas.GraphicsContext;

import java.util.List;

/**
 * Modo automático: va rotando entre los demás patrones. Espera a que pase el
 * intervalo y entonces cambia en el siguiente golpe, de modo que la transición
 * cae siempre a tiempo con la música en vez de cortar a mitad de un compás.
 */
public final class AutoCycleVisualizer implements Visualizer {

    private static final double INTERVAL_SEC = 22.0;
    private static final double GRACE_SEC = 6.0;

    private final List<Visualizer> rotation;
    private int index;
    private double elapsed;

    public AutoCycleVisualizer(List<Visualizer> rotation) {
        this.rotation = List.copyOf(rotation);
    }

    @Override
    public String name() {
        return "Automático";
    }

    private Visualizer current() {
        return rotation.get(index);
    }

    @Override
    public void update(RenderContext ctx) {
        elapsed += ctx.deltaSec;
        boolean due = elapsed >= INTERVAL_SEC;
        // Si tras el margen extra no ha llegado ningún golpe, cambia igualmente.
        if (due && (ctx.audio.beatOnset || elapsed >= INTERVAL_SEC + GRACE_SEC)) {
            index = (index + 1) % rotation.size();
            elapsed = 0;
            current().reset();
        }
        current().update(ctx);
    }

    @Override
    public void render(GraphicsContext g, RenderContext ctx) {
        current().render(g, ctx);
    }

    @Override
    public double trail() {
        return current().trail();
    }

    @Override
    public void reset() {
        elapsed = 0;
        current().reset();
    }

    /** Nombre del patrón que se está mostrando ahora mismo. */
    public String currentName() {
        return current().name();
    }
}
