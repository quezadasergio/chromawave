package com.chromawave.audio;

import com.chromawave.dsp.SourceAnalysis;
import com.chromawave.dsp.SpectrumAnalyzer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import java.util.function.Consumer;

/**
 * Captura del micrófono en un hilo dedicado. El audio no se reproduce, solo se
 * analiza, de modo que no hay riesgo de realimentación con los altavoces.
 */
public final class MicrophoneCapture {

    private static final float SAMPLE_RATE = 44100f;
    private static final int BUFFER_FRAMES = 1024;

    private final SourceAnalysis target;
    private final SpectrumAnalyzer analyzer = new SpectrumAnalyzer(SAMPLE_RATE);

    private TargetDataLine line;
    private Thread worker;
    private volatile boolean running;
    private volatile double gain = 1.0;
    private Consumer<String> onError = message -> { };

    public MicrophoneCapture(SourceAnalysis target) {
        this.target = target;
    }

    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    /** Ganancia de entrada aplicada por software, en [0,2]. */
    public void setGain(double gain) {
        this.gain = Math.clamp(gain, 0.0, 2.0);
    }

    public boolean isRunning() {
        return running;
    }

    public static boolean isAvailable() {
        AudioFormat format = format();
        return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, format));
    }

    private static AudioFormat format() {
        return new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        AudioFormat format = format();
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                onError.accept("No hay ninguna entrada de audio disponible en este equipo.");
                return;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format, BUFFER_FRAMES * 8);
            line.start();
        } catch (LineUnavailableException e) {
            onError.accept("No se pudo abrir el micrófono: " + e.getMessage());
            return;
        }

        analyzer.reset();
        running = true;
        worker = new Thread(this::capture, "chromawave-mic");
        worker.setDaemon(true);
        worker.start();
    }

    private void capture() {
        byte[] bytes = new byte[BUFFER_FRAMES * 2];
        float[] samples = new float[BUFFER_FRAMES];
        while (running) {
            int read = line.read(bytes, 0, bytes.length);
            if (read <= 0) {
                continue;
            }
            int frames = read / 2;
            double g = gain;
            for (int i = 0; i < frames; i++) {
                short sample = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
                samples[i] = (float) Math.clamp(sample / 32768.0 * g, -1.0, 1.0);
            }
            analyzer.push(samples, 0, frames);
            analyzer.analyze(target);
        }
    }

    public synchronized void stop() {
        running = false;
        if (worker != null) {
            try {
                worker.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        target.clear();
    }
}
