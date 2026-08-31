package com.chromawave.visual;

import com.chromawave.visual.patterns.AutoCycleVisualizer;
import com.chromawave.visual.patterns.FrequencyBarsVisualizer;
import com.chromawave.visual.patterns.LiquidBlobVisualizer;
import com.chromawave.visual.patterns.ParticleFieldVisualizer;
import com.chromawave.visual.patterns.RadialBurstVisualizer;
import com.chromawave.visual.patterns.RandomBarsVisualizer;
import com.chromawave.visual.patterns.WaveVisualizer;

import java.util.List;

/** Construye el catálogo de patrones disponibles, con el modo automático al final. */
public final class VisualizerRegistry {

    private VisualizerRegistry() {
    }

    public static List<Visualizer> createAll() {
        List<Visualizer> patterns = List.of(
                new WaveVisualizer(),
                new FrequencyBarsVisualizer(),
                new RandomBarsVisualizer(),
                new RadialBurstVisualizer(),
                new ParticleFieldVisualizer(),
                new LiquidBlobVisualizer());

        // El modo automático rota sobre instancias propias para no compartir
        // el estado interno con el patrón que el usuario pueda tener elegido.
        List<Visualizer> ownRotation = List.of(
                new WaveVisualizer(),
                new FrequencyBarsVisualizer(),
                new RandomBarsVisualizer(),
                new RadialBurstVisualizer(),
                new ParticleFieldVisualizer(),
                new LiquidBlobVisualizer());

        return java.util.stream.Stream.concat(
                        patterns.stream(),
                        java.util.stream.Stream.of(new AutoCycleVisualizer(ownRotation)))
                .toList();
    }
}
