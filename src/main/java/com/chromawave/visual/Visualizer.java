package com.chromawave.visual;

import javafx.scene.canvas.GraphicsContext;

/**
 * Un patrón abstracto capaz de dibujarse a partir del estado del audio.
 *
 * <p>{@link #render} puede invocarse dos veces por frame (capa nítida y capa de
 * brillo), por lo que no debe modificar el estado de la animación; toda la
 * evolución temporal debe ocurrir en {@link #update}.
 */
public interface Visualizer {

    String name();

    /** Avanza la simulación interna. Se llama una sola vez por frame. */
    default void update(RenderContext ctx) {
    }

    void render(GraphicsContext g, RenderContext ctx);

    /** Cuánta estela dejar: 0 = sin estela (borrado total), 1 = estela infinita. */
    default double trail() {
        return 0.82;
    }

    /** Reinicia el estado interno al seleccionar el visualizador. */
    default void reset() {
    }
}
