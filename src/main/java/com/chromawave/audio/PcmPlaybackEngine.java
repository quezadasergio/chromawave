package com.chromawave.audio;

import com.chromawave.dsp.SourceAnalysis;
import com.chromawave.dsp.SpectrumAnalyzer;

import javafx.application.Platform;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import java.io.File;
import java.util.function.Consumer;

/**
 * Motor de reproducción para los formatos que JavaFX no soporta (FLAC, OGG/Vorbis).
 *
 * <p>Decodifica a PCM con los proveedores SPI de {@code javax.sound.sampled}
 * (jflac y vorbisspi), escribe en una {@link SourceDataLine} y, en el camino,
 * alimenta el analizador de espectro con las muestras reales. Por eso este motor
 * produce formas de onda auténticas, no sintetizadas.
 */
public final class PcmPlaybackEngine implements PlaybackEngine {

    private static final int BUFFER_FRAMES = 2048;

    private final SourceAnalysis target;
    private final SpectrumAnalyzer analyzer = new SpectrumAnalyzer(44100);

    private File file;
    private AudioFormat decodedFormat;
    private double duration;

    private Thread worker;
    private volatile boolean running;
    private volatile boolean paused = true;
    private volatile double volume = 1.0;
    private volatile double seekTarget = -1;
    private volatile double position;

    private Runnable onReady = () -> { };
    private Runnable onEndOfMedia = () -> { };
    private Consumer<String> onError = message -> { };

    public PcmPlaybackEngine(SourceAnalysis target) {
        this.target = target;
    }

    @Override
    public void load(File file) throws Exception {
        dispose();
        this.file = file;
        this.position = 0;
        this.paused = true;

        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(file);
        AudioFormat base = fileFormat.getFormat();
        this.decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(),
                16,
                base.getChannels(),
                base.getChannels() * 2,
                base.getSampleRate(),
                false);
        this.duration = resolveDuration(fileFormat, base);
        analyzer.setSampleRate(base.getSampleRate());
        analyzer.reset();

        running = true;
        worker = new Thread(this::pump, "chromawave-pcm");
        worker.setDaemon(true);
        worker.start();
        onFx(onReady);
    }

    /**
     * La duración no siempre está disponible: FLAC informa el número de frames,
     * mientras que OGG la publica como propiedad en microsegundos.
     */
    private static double resolveDuration(AudioFileFormat fileFormat, AudioFormat base) {
        Object micros = fileFormat.properties().get("duration");
        if (micros instanceof Number n) {
            return n.longValue() / 1_000_000.0;
        }
        if (fileFormat.getFrameLength() != AudioSystem.NOT_SPECIFIED && base.getFrameRate() > 0) {
            return fileFormat.getFrameLength() / (double) base.getFrameRate();
        }
        return 0;
    }

    private void pump() {
        while (running) {
            double startAt = seekTarget >= 0 ? seekTarget : 0;
            seekTarget = -1;
            try {
                boolean restart = playFrom(startAt);
                if (!restart) {
                    return;
                }
            } catch (Exception e) {
                running = false;
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                onFx(() -> onError.accept(message));
                return;
            }
        }
    }

    /**
     * @return true si hay que reabrir el flujo (petición de seek), false si terminó
     */
    private boolean playFrom(double startSeconds) throws Exception {
        int channels = decodedFormat.getChannels();
        int frameSize = decodedFormat.getFrameSize();
        float sampleRate = decodedFormat.getSampleRate();

        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file);
             AudioInputStream pcm = AudioSystem.getAudioInputStream(decodedFormat, raw)) {

            if (startSeconds > 0) {
                long toSkip = (long) (startSeconds * sampleRate) * frameSize;
                long skipped = 0;
                while (skipped < toSkip) {
                    long n = pcm.skip(toSkip - skipped);
                    if (n <= 0) {
                        break;
                    }
                    skipped += n;
                }
                position = skipped / (double) frameSize / sampleRate;
            } else {
                position = 0;
            }

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(decodedFormat, BUFFER_FRAMES * frameSize * 4);
                line.start();

                byte[] bytes = new byte[BUFFER_FRAMES * frameSize];
                float[] mono = new float[BUFFER_FRAMES];
                analyzer.reset();

                while (running) {
                    if (seekTarget >= 0) {
                        line.stop();
                        line.flush();
                        return true;
                    }
                    if (paused) {
                        line.stop();
                        target.clear();
                        Thread.sleep(20);
                        continue;
                    }
                    if (!line.isRunning()) {
                        line.start();
                    }

                    int read = pcm.read(bytes, 0, bytes.length);
                    if (read == 0) {
                        // Los flujos de Vorbis decodifican en un hilo aparte y
                        // devuelven 0 mientras su buffer se rellena; eso no es
                        // el final del archivo, solo hay que reintentar.
                        Thread.sleep(2);
                        continue;
                    }
                    if (read < 0) {
                        line.drain();
                        paused = true;
                        target.clear();
                        onFx(onEndOfMedia);
                        return true;
                    }

                    int frames = read / frameSize;
                    double gain = volume;
                    for (int f = 0; f < frames; f++) {
                        int sum = 0;
                        for (int c = 0; c < channels; c++) {
                            int idx = f * frameSize + c * 2;
                            short sample = (short) ((bytes[idx] & 0xFF) | (bytes[idx + 1] << 8));
                            int scaled = (int) (sample * gain);
                            scaled = Math.clamp(scaled, Short.MIN_VALUE, Short.MAX_VALUE);
                            bytes[idx] = (byte) (scaled & 0xFF);
                            bytes[idx + 1] = (byte) ((scaled >> 8) & 0xFF);
                            sum += sample;
                        }
                        mono[f] = sum / (float) channels / 32768f;
                    }

                    analyzer.push(mono, 0, frames);
                    analyzer.analyze(target);
                    line.write(bytes, 0, read);
                    position += frames / (double) sampleRate;
                }
                line.stop();
            }
        }
        return false;
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException e) {
            // Sin toolkit de JavaFX activo (diagnósticos por consola) el motor
            // sigue siendo utilizable: basta con ejecutar la notificación aquí.
            action.run();
        }
    }

    @Override
    public void play() {
        paused = false;
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void stop() {
        paused = true;
        seekTarget = 0;
        position = 0;
        target.clear();
    }

    @Override
    public void seek(double seconds) {
        seekTarget = Math.max(0, seconds);
        position = seekTarget;
    }

    @Override
    public void setVolume(double volume) {
        this.volume = Math.clamp(volume, 0.0, 1.0);
    }

    @Override
    public double positionSeconds() {
        return position;
    }

    @Override
    public double durationSeconds() {
        return duration;
    }

    @Override
    public boolean isPlaying() {
        return running && !paused;
    }

    @Override
    public void dispose() {
        running = false;
        paused = true;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        target.clear();
    }

    @Override
    public void setOnReady(Runnable callback) {
        this.onReady = callback;
    }

    @Override
    public void setOnEndOfMedia(Runnable callback) {
        this.onEndOfMedia = callback;
    }

    @Override
    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }
}
