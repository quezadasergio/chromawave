package com.chromawave.audio;

import com.chromawave.dsp.SourceAnalysis;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.util.List;

/**
 * Fachada de reproducción: gestiona la lista de pistas y delega en el motor
 * adecuado según el formato, exponiendo un estado observable para la interfaz.
 */
public final class AudioPlayer {

    private final SourceAnalysis analysisSlot;
    private final FxPlaybackEngine fxEngine;
    private final PcmPlaybackEngine pcmEngine;

    private final ObservableList<Track> playlist = FXCollections.observableArrayList();
    private final ObjectProperty<Track> currentTrack = new SimpleObjectProperty<>();
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final DoubleProperty position = new SimpleDoubleProperty(0);
    private final DoubleProperty duration = new SimpleDoubleProperty(0);
    private final DoubleProperty volume = new SimpleDoubleProperty(0.8);
    private final StringProperty status = new SimpleStringProperty("");

    private PlaybackEngine active;
    private boolean seeking;

    public AudioPlayer(SourceAnalysis analysisSlot) {
        this.analysisSlot = analysisSlot;
        this.fxEngine = new FxPlaybackEngine(analysisSlot);
        this.pcmEngine = new PcmPlaybackEngine(analysisSlot);
        configure(fxEngine);
        configure(pcmEngine);
        volume.addListener((obs, old, value) -> {
            if (active != null) {
                active.setVolume(value.doubleValue());
            }
        });
    }

    private void configure(PlaybackEngine engine) {
        engine.setOnReady(() -> duration.set(engine.durationSeconds()));
        engine.setOnEndOfMedia(this::next);
        engine.setOnError(message -> {
            status.set("Error: " + message);
            playing.set(false);
        });
    }

    public ObservableList<Track> playlist() {
        return playlist;
    }

    public ObjectProperty<Track> currentTrackProperty() {
        return currentTrack;
    }

    public BooleanProperty playingProperty() {
        return playing;
    }

    public DoubleProperty positionProperty() {
        return position;
    }

    public DoubleProperty durationProperty() {
        return duration;
    }

    public DoubleProperty volumeProperty() {
        return volume;
    }

    public StringProperty statusProperty() {
        return status;
    }

    /** Añade archivos a la lista y arranca el primero si no hay nada sonando. */
    public void addAll(List<File> files) {
        List<Track> added = files.stream()
                .filter(AudioFormats::isSupported)
                .map(Track::new)
                .toList();
        if (added.isEmpty()) {
            status.set("Ningún archivo con un formato compatible.");
            return;
        }
        boolean wasEmpty = playlist.isEmpty();
        playlist.addAll(added);
        if (wasEmpty) {
            open(added.getFirst(), true);
        } else {
            status.set(added.size() + " pista(s) añadida(s) a la lista.");
        }
    }

    /** Carga una pista y opcionalmente comienza a reproducirla. */
    public void open(Track track, boolean autoPlay) {
        if (track == null) {
            return;
        }
        if (active != null) {
            active.stop();
            active.dispose();
        }
        analysisSlot.clear();
        position.set(0);
        duration.set(0);

        active = AudioFormats.requiresDecoder(track.file()) ? pcmEngine : fxEngine;
        try {
            active.load(track.file());
            active.setVolume(volume.get());
            currentTrack.set(track);
            duration.set(active.durationSeconds());
            status.set(track.title());
            if (autoPlay) {
                active.play();
                playing.set(true);
            } else {
                playing.set(false);
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            status.set("No se pudo abrir «" + track.title() + "»: " + message);
            active = null;
            playing.set(false);
        }
    }

    public void togglePlay() {
        if (active == null) {
            if (!playlist.isEmpty()) {
                open(playlist.getFirst(), true);
            }
            return;
        }
        if (playing.get()) {
            active.pause();
            playing.set(false);
        } else {
            active.play();
            playing.set(true);
        }
    }

    public void stop() {
        if (active != null) {
            active.stop();
        }
        playing.set(false);
        position.set(0);
        analysisSlot.clear();
    }

    public void next() {
        step(1);
    }

    public void previous() {
        // Como en cualquier reproductor: si ya avanzó bastante, reinicia la pista.
        if (active != null && active.positionSeconds() > 3) {
            seek(0);
            return;
        }
        step(-1);
    }

    private void step(int delta) {
        if (playlist.isEmpty()) {
            return;
        }
        int index = playlist.indexOf(currentTrack.get());
        int target = index < 0 ? 0 : Math.floorMod(index + delta, playlist.size());
        open(playlist.get(target), true);
    }

    public void remove(Track track) {
        boolean isCurrent = track.equals(currentTrack.get());
        playlist.remove(track);
        if (isCurrent) {
            stop();
            if (active != null) {
                active.dispose();
                active = null;
            }
            currentTrack.set(null);
            if (!playlist.isEmpty()) {
                open(playlist.getFirst(), false);
            }
        }
    }

    public void clearPlaylist() {
        stop();
        if (active != null) {
            active.dispose();
            active = null;
        }
        currentTrack.set(null);
        playlist.clear();
        duration.set(0);
        status.set("Lista vacía.");
    }

    public void seek(double seconds) {
        if (active != null) {
            active.seek(seconds);
            position.set(seconds);
        }
    }

    /** Marca que el usuario está arrastrando la barra, para no pisar su valor. */
    public void setSeeking(boolean seeking) {
        this.seeking = seeking;
    }

    /** Sincroniza posición y duración con el motor. Se llama una vez por frame. */
    public void tick() {
        if (active == null) {
            return;
        }
        if (!seeking) {
            position.set(active.positionSeconds());
        }
        double total = active.durationSeconds();
        if (total > 0 && Math.abs(total - duration.get()) > 0.05) {
            duration.set(total);
        }
        if (playing.get() != active.isPlaying() && !seeking) {
            playing.set(active.isPlaying());
        }
    }

    public void dispose() {
        fxEngine.dispose();
        pcmEngine.dispose();
    }
}
