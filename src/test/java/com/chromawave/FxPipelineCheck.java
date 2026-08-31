package com.chromawave;

import com.chromawave.audio.FxPlaybackEngine;
import com.chromawave.dsp.AudioBus;
import com.chromawave.dsp.SpectrumData;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.File;

/**
 * Diagnóstico del motor basado en {@code javafx.scene.media}: comprueba que el
 * analizador de espectro integrado entrega bandas y que la onda sintetizada a
 * partir de magnitudes y fases tiene amplitud real.
 */
public final class FxPipelineCheck extends Application {

    private static final double SECONDS = 6.0;

    private final AudioBus bus = new AudioBus();
    private final SpectrumData frame = new SpectrumData();

    @Override
    public void start(Stage stage) throws Exception {
        var files = getParameters().getRaw();
        if (files.isEmpty()) {
            System.out.println("Uso: FxPipelineCheck <archivo>");
            Platform.exit();
            return;
        }
        File file = new File(files.getFirst());
        System.out.println("=== " + file.getName() + " (motor JavaFX) ===");

        bus.setFileWeight(1f);
        bus.setMicWeight(0f);
        bus.setSensitivity(1f);
        bus.setSmoothing(0.5f);

        FxPlaybackEngine engine = new FxPlaybackEngine(bus.fileSlot());
        engine.setOnError(message -> System.out.println("  ERROR: " + message));
        engine.setOnReady(() -> System.out.printf("  duración: %.2f s%n", engine.durationSeconds()));
        engine.load(file);
        engine.setVolume(0.05);
        engine.play();

        long startNanos = System.nanoTime();
        var stats = new Object() {
            float maxLevel;
            float maxWave;
            int spectrumFrames;
            int beats;
            long frames;
        };

        new AnimationTimer() {
            private long last = System.nanoTime();

            @Override
            public void handle(long now) {
                double delta = Math.min((now - last) / 1e9, 0.05);
                last = now;
                bus.update(delta);
                bus.snapshot(frame);
                stats.frames++;

                stats.maxLevel = Math.max(stats.maxLevel, frame.level);
                for (float w : frame.waveform) {
                    stats.maxWave = Math.max(stats.maxWave, Math.abs(w));
                }
                float sum = 0;
                for (float b : frame.bands) {
                    sum += b;
                }
                if (sum > 0.01f) {
                    stats.spectrumFrames++;
                }
                if (frame.beatOnset) {
                    stats.beats++;
                }

                if ((now - startNanos) / 1e9 >= SECONDS) {
                    stop();
                    System.out.printf("  posición:            %.2f s%n", engine.positionSeconds());
                    System.out.printf("  frames renderizados: %d%n", stats.frames);
                    System.out.printf("  nivel máximo:        %.3f%n", stats.maxLevel);
                    System.out.printf("  onda máxima:         %.3f%n", stats.maxWave);
                    System.out.printf("  frames con espectro: %d%n", stats.spectrumFrames);
                    System.out.printf("  golpes detectados:   %d%n", stats.beats);
                    boolean ok = engine.positionSeconds() > SECONDS * 0.5
                            && stats.maxLevel > 0.05
                            && stats.maxWave > 0.05
                            && stats.spectrumFrames > 100
                            && stats.beats > 3;
                    System.out.println(ok ? "  RESULTADO: correcto" : "  RESULTADO: FALLO");
                    engine.dispose();
                    Platform.exit();
                    if (!ok) {
                        Runtime.getRuntime().halt(1);
                    }
                }
            }
        }.start();
    }

    /**
     * JavaFX rechaza arrancar cuando la clase principal extiende
     * {@link Application} y los módulos vienen del classpath, así que el punto
     * de entrada real es esta clase intermedia.
     */
    public static final class Launcher {
        public static void main(String[] args) {
            Application.launch(FxPipelineCheck.class, args);
        }
    }
}
