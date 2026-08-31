package com.chromawave;

import com.chromawave.audio.PcmPlaybackEngine;
import com.chromawave.dsp.AudioBus;
import com.chromawave.dsp.SpectrumData;

import java.io.File;

/**
 * Diagnóstico de la cadena decodificador → analizador → bus, sin interfaz.
 * Reproduce unos segundos de cada archivo indicado y comprueba que el análisis
 * produce energía, forma de onda y golpes detectados.
 */
public final class AudioPipelineCheck {

    private static final double SECONDS = 5.0;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Uso: AudioPipelineCheck <archivo> [archivo...]");
            return;
        }
        for (String path : args) {
            check(new File(path));
        }
    }

    /** Lee el flujo decodificado directamente, para aislar fallos del decodificador. */
    private static void probe(File file) {
        try {
            javax.sound.sampled.AudioFileFormat aff =
                    javax.sound.sampled.AudioSystem.getAudioFileFormat(file);
            javax.sound.sampled.AudioFormat base = aff.getFormat();
            System.out.println("  formato base: " + base);
            System.out.println("  propiedades:  " + aff.properties());

            javax.sound.sampled.AudioFormat target = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16, base.getChannels(),
                    base.getChannels() * 2, base.getSampleRate(), false);
            System.out.println("  formato destino: " + target);

            try (var raw = javax.sound.sampled.AudioSystem.getAudioInputStream(file);
                 var pcm = javax.sound.sampled.AudioSystem.getAudioInputStream(target, raw)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int reads = 0;
                int zeros = 0;
                while (reads < 40 && zeros < 200) {
                    int n = pcm.read(buf);
                    if (n < 0) {
                        break;
                    }
                    if (n == 0) {
                        zeros++;
                        continue;
                    }
                    total += n;
                    reads++;
                }
                System.out.println("  bytes PCM leídos: " + total + " en " + reads
                        + " lecturas (" + zeros + " lecturas vacías)");
            }
        } catch (Exception e) {
            System.out.println("  PROBE FALLÓ: " + e);
        }
    }

    private static void check(File file) throws Exception {
        System.out.println("\n=== " + file.getName() + " ===");
        probe(file);
        AudioBus bus = new AudioBus();
        bus.setFileWeight(1f);
        bus.setMicWeight(0f);
        bus.setSensitivity(1f);
        bus.setSmoothing(0.5f);

        PcmPlaybackEngine engine = new PcmPlaybackEngine(bus.fileSlot());
        engine.setOnError(message -> System.out.println("  ERROR: " + message));
        engine.load(file);
        System.out.printf("  duración informada: %.2f s%n", engine.durationSeconds());
        engine.setVolume(0.0); // Silencioso: solo interesa el análisis.
        engine.play();

        SpectrumData frame = new SpectrumData();
        double elapsed = 0;
        double step = 1.0 / 60;
        int beats = 0;
        float maxLevel = 0;
        float maxWave = 0;
        int framesWithSpectrum = 0;

        while (elapsed < SECONDS) {
            Thread.sleep((long) (step * 1000));
            elapsed += step;
            bus.update(step);
            bus.snapshot(frame);

            if (frame.beatOnset) {
                beats++;
            }
            maxLevel = Math.max(maxLevel, frame.level);
            for (float w : frame.waveform) {
                maxWave = Math.max(maxWave, Math.abs(w));
            }
            float sum = 0;
            for (float b : frame.bands) {
                sum += b;
            }
            if (sum > 0.01f) {
                framesWithSpectrum++;
            }
        }

        double beforeSeek = engine.positionSeconds();

        // El salto obliga a reabrir el flujo: hay que comprobar que la posición
        // se respeta y que el análisis vuelve a producir datos después.
        engine.seek(12.0);
        Thread.sleep(900);
        double afterSeek = engine.positionSeconds();
        bus.update(step);
        bus.snapshot(frame);
        float postSeekEnergy = 0;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(16);
            bus.update(step);
            bus.snapshot(frame);
            postSeekEnergy = Math.max(postSeekEnergy, frame.level);
        }
        System.out.printf("  tras salto a 12 s:  %.2f s (energía %.3f)%n", afterSeek, postSeekEnergy);
        boolean seekOk = afterSeek > 11.5 && afterSeek < 15.0 && postSeekEnergy > 0.05;
        System.out.println("  salto: " + (seekOk ? "correcto" : "FALLO"));

        System.out.printf("  posición alcanzada: %.2f s%n", beforeSeek);
        System.out.printf("  nivel máximo:       %.3f%n", maxLevel);
        System.out.printf("  onda máxima:        %.3f%n", maxWave);
        System.out.printf("  frames con espectro:%d%n", framesWithSpectrum);
        System.out.printf("  golpes detectados:  %d%n", beats);
        engine.dispose();

        boolean ok = seekOk
                && beforeSeek > SECONDS * 0.5
                && maxLevel > 0.05
                && maxWave > 0.05
                && framesWithSpectrum > 100
                && beats > 3;
        System.out.println(ok ? "  RESULTADO: correcto" : "  RESULTADO: FALLO");
        if (!ok) {
            throw new IllegalStateException("La cadena de audio falló para " + file.getName());
        }
    }
}
