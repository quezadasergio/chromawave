package com.chromawave;

import com.chromawave.audio.MicrophoneCapture;
import com.chromawave.dsp.AudioBus;
import com.chromawave.dsp.SpectrumData;

/**
 * Diagnóstico de la captura de micrófono. En macOS la primera ejecución
 * dispara la solicitud de permiso del sistema; si se deniega, la línea se abre
 * pero solo entrega silencio.
 */
public final class MicrophoneCheck {

    public static void main(String[] args) throws Exception {
        System.out.println("Entrada disponible: " + MicrophoneCapture.isAvailable());

        AudioBus bus = new AudioBus();
        bus.setFileWeight(0f);
        bus.setMicWeight(1f);
        bus.setSensitivity(1.5f);

        MicrophoneCapture mic = new MicrophoneCapture(bus.micSlot());
        mic.setOnError(message -> System.out.println("ERROR: " + message));
        mic.setGain(1.5);
        mic.start();
        System.out.println("Capturando 5 segundos, haz algo de ruido…");

        SpectrumData frame = new SpectrumData();
        float maxLevel = 0;
        float maxBand = 0;
        for (int i = 0; i < 300; i++) {
            Thread.sleep(16);
            bus.update(1.0 / 60);
            bus.snapshot(frame);
            maxLevel = Math.max(maxLevel, frame.level);
            for (float b : frame.bands) {
                maxBand = Math.max(maxBand, b);
            }
        }
        mic.stop();

        System.out.printf("Nivel máximo:  %.4f%n", maxLevel);
        System.out.printf("Banda máxima:  %.4f%n", maxBand);
        System.out.println(maxLevel > 0.001 || maxBand > 0.01
                ? "RESULTADO: el micrófono entrega señal"
                : "RESULTADO: silencio absoluto (revisa el permiso del sistema)");
    }
}
