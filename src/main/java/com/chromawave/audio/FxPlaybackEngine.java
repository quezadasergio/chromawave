package com.chromawave.audio;

import com.chromawave.dsp.LinearBandMapper;
import com.chromawave.dsp.SourceAnalysis;
import com.chromawave.dsp.SpectrumData;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.function.Consumer;

/**
 * Motor para los formatos que JavaFX reproduce de forma nativa
 * (MP3, WAV, AIFF y M4A/AAC).
 *
 * <p>JavaFX no expone las muestras PCM, pero sí un analizador de espectro
 * integrado. A partir de sus magnitudes y fases reconstruimos una forma de
 * onda por síntesis aditiva, de modo que los visualizadores de onda funcionan
 * igual que con el motor PCM.
 */
public final class FxPlaybackEngine implements PlaybackEngine {

    /**
     * JavaFX reparte sus bandas linealmente, así que se piden muchas más de las
     * que se muestran: solo con esa resolución el registro grave queda separado
     * del resto al remapear a escala logarítmica.
     */
    private static final int SPECTRUM_BANDS = 256;
    private static final double NYQUIST_HZ = 22050.0;
    private static final double SPECTRUM_THRESHOLD_DB = -78.0;
    private static final int SYNTH_PARTIALS = 28;

    private final SourceAnalysis target;
    private final float[] dbBands = new float[SPECTRUM_BANDS];
    private final LinearBandMapper mapper =
            new LinearBandMapper(SPECTRUM_BANDS, NYQUIST_HZ, (float) SPECTRUM_THRESHOLD_DB);

    private MediaPlayer player;
    private Runnable onReady = () -> { };
    private Runnable onEndOfMedia = () -> { };
    private Consumer<String> onError = message -> { };
    private double volume = 1.0;
    private boolean playRequested;

    public FxPlaybackEngine(SourceAnalysis target) {
        this.target = target;
    }

    @Override
    public void load(File file) {
        dispose();
        Media media = new Media(file.toURI().toString());
        player = new MediaPlayer(media);
        player.setVolume(volume);
        player.setAudioSpectrumNumBands(SPECTRUM_BANDS);
        // Un intervalo corto conserva los transitorios del bombo, que son los
        // que alimentan la detección de golpes.
        player.setAudioSpectrumInterval(1.0 / 60.0);
        player.setAudioSpectrumThreshold((int) SPECTRUM_THRESHOLD_DB);
        player.setAudioSpectrumListener(this::onSpectrum);
        player.setOnReady(() -> {
            onReady.run();
            if (playRequested) {
                player.play();
            }
        });
        player.setOnEndOfMedia(() -> {
            target.clear();
            onEndOfMedia.run();
        });
        player.setOnError(() -> {
            MediaPlayer p = player;
            onError.accept(p != null && p.getError() != null
                    ? p.getError().getMessage()
                    : "Error desconocido de reproducción");
        });
    }

    private void onSpectrum(double timestamp, double duration, float[] magnitudes, float[] phases) {
        java.util.Arrays.fill(dbBands, (float) SPECTRUM_THRESHOLD_DB);
        System.arraycopy(magnitudes, 0, dbBands, 0, Math.min(magnitudes.length, SPECTRUM_BANDS));
        mapper.map(dbBands, target.bands);
        synthesizeWaveform(phases);

        float sum = 0;
        for (float band : target.bands) {
            sum += band;
        }
        target.level = Math.clamp(sum / SpectrumData.BANDS * 1.8f, 0f, 1f);
    }

    /**
     * Reconstruye una onda plausible sumando parciales cuya amplitud viene de
     * cada banda y cuya fase la aporta el propio analizador. No es la señal
     * original, pero conserva su ritmo y su contenido armónico.
     */
    private void synthesizeWaveform(float[] phases) {
        float[] wave = target.waveform;
        java.util.Arrays.fill(wave, 0f);
        float norm = 0f;
        for (int p = 0; p < SYNTH_PARTIALS; p++) {
            float amp = target.bands[p];
            if (amp < 0.02f) {
                continue;
            }
            norm += amp;
            double phase = p < phases.length ? phases[p] : 0;
            double omega = 2 * Math.PI * (p + 1) * 1.5 / SpectrumData.WAVE_POINTS;
            for (int i = 0; i < SpectrumData.WAVE_POINTS; i++) {
                wave[i] += (float) (amp * Math.sin(omega * i + phase));
            }
        }
        if (norm > 0) {
            float scale = 1f / norm;
            for (int i = 0; i < wave.length; i++) {
                wave[i] = Math.clamp(wave[i] * scale, -1f, 1f);
            }
        }
    }

    @Override
    public void play() {
        playRequested = true;
        if (player != null && player.getStatus() != MediaPlayer.Status.UNKNOWN) {
            player.play();
        }
    }

    @Override
    public void pause() {
        playRequested = false;
        if (player != null) {
            player.pause();
        }
    }

    @Override
    public void stop() {
        playRequested = false;
        if (player != null) {
            player.stop();
        }
        target.clear();
    }

    @Override
    public void seek(double seconds) {
        if (player != null) {
            player.seek(Duration.seconds(seconds));
        }
    }

    @Override
    public void setVolume(double volume) {
        this.volume = volume;
        if (player != null) {
            player.setVolume(volume);
        }
    }

    @Override
    public double positionSeconds() {
        return player == null ? 0 : player.getCurrentTime().toSeconds();
    }

    @Override
    public double durationSeconds() {
        if (player == null || player.getMedia() == null) {
            return 0;
        }
        Duration total = player.getMedia().getDuration();
        return total == null || total.isUnknown() ? 0 : total.toSeconds();
    }

    @Override
    public boolean isPlaying() {
        return player != null && player.getStatus() == MediaPlayer.Status.PLAYING;
    }

    @Override
    public void dispose() {
        if (player != null) {
            player.setAudioSpectrumListener(null);
            player.dispose();
            player = null;
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
