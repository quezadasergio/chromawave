package com.chromawave.app;

import com.chromawave.audio.AudioPlayer;
import com.chromawave.audio.MicrophoneCapture;
import com.chromawave.dsp.AudioBus;
import com.chromawave.visual.Palette;
import com.chromawave.visual.Visualizer;
import com.chromawave.visual.VisualizerPane;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Barra inferior con todos los controles de audio, patrón y color. */
public final class ControlBar extends VBox {

    private final AudioPlayer player;
    private final Slider seekSlider = new Slider(0, 1, 0);
    private final Label timeLabel = new Label("0:00 / 0:00");
    private final Button playButton = new Button("▶");
    private final ComboBox<Visualizer> patternBox = new ComboBox<>();
    private final ToggleButton playlistToggle = new ToggleButton("Lista");

    private double volumeBeforeMute = 0.8;
    private double micLevel;
    private boolean micMuted;

    public ControlBar(AudioPlayer player,
                      AudioBus bus,
                      MicrophoneCapture mic,
                      VisualizerPane pane,
                      List<Visualizer> visualizers,
                      Runnable onOpenFiles,
                      Runnable onToggleFullscreen) {
        this.player = player;
        getStyleClass().add("control-bar");
        setSpacing(8);
        setPadding(new Insets(10, 14, 12, 14));

        getChildren().addAll(
                buildSeekRow(),
                buildTransportRow(bus, mic, onOpenFiles, onToggleFullscreen),
                buildVisualRow(bus, pane, visualizers));
    }

    // ---------------------------------------------------------------- barra de posición

    private HBox buildSeekRow() {
        seekSlider.getStyleClass().add("seek-slider");
        HBox.setHgrow(seekSlider, Priority.ALWAYS);
        seekSlider.setMax(1);

        player.durationProperty().addListener((obs, old, value) -> {
            double total = value.doubleValue();
            seekSlider.setMax(total > 0 ? total : 1);
            seekSlider.setDisable(total <= 0);
            updateTimeLabel();
        });
        player.positionProperty().addListener((obs, old, value) -> {
            if (!seekSlider.isValueChanging()) {
                seekSlider.setValue(value.doubleValue());
            }
            updateTimeLabel();
        });

        seekSlider.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
            player.setSeeking(changing);
            if (!changing) {
                player.seek(seekSlider.getValue());
            }
        });
        // Un clic simple en la barra también debe saltar a esa posición.
        seekSlider.setOnMouseReleased(e -> {
            player.seek(seekSlider.getValue());
            player.setSeeking(false);
        });
        seekSlider.setOnMousePressed(e -> player.setSeeking(true));

        timeLabel.getStyleClass().add("time-label");
        return new HBox(10, seekSlider, timeLabel);
    }

    private void updateTimeLabel() {
        timeLabel.setText(formatTime(player.positionProperty().get())
                + " / " + formatTime(player.durationProperty().get()));
    }

    private static String formatTime(double seconds) {
        if (seconds <= 0 || Double.isNaN(seconds)) {
            return "0:00";
        }
        int total = (int) Math.round(seconds);
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int secs = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, secs)
                : String.format("%d:%02d", minutes, secs);
    }

    // ---------------------------------------------------------------- transporte y audio

    private FlowPane buildTransportRow(AudioBus bus,
                                       MicrophoneCapture mic,
                                       Runnable onOpenFiles,
                                       Runnable onToggleFullscreen) {
        Button open = iconButton("📂", "Abrir archivos de audio");
        open.setOnAction(e -> onOpenFiles.run());

        Button previous = iconButton("⏮", "Pista anterior");
        previous.setOnAction(e -> player.previous());

        playButton.getStyleClass().addAll("icon-button", "play-button");
        playButton.setTooltip(new Tooltip("Reproducir / Pausar"));
        playButton.setOnAction(e -> player.togglePlay());
        player.playingProperty().addListener((obs, old, playing) ->
                playButton.setText(playing ? "⏸" : "▶"));

        Button stop = iconButton("⏹", "Detener");
        stop.setOnAction(e -> player.stop());

        Button next = iconButton("⏭", "Pista siguiente");
        next.setOnAction(e -> player.next());

        HBox transport = new HBox(6, open, previous, playButton, stop, next);
        transport.setAlignment(Pos.CENTER_LEFT);

        return new FlowPane(14, 8,
                transport,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                buildFileVolume(),
                new Separator(javafx.geometry.Orientation.VERTICAL),
                buildMicControls(bus, mic),
                new Separator(javafx.geometry.Orientation.VERTICAL),
                buildWindowControls(onToggleFullscreen));
    }

    private HBox buildFileVolume() {
        Slider volume = labelledSlider(0, 1, Preferences.getDouble("volume", 0.8), 120);
        player.volumeProperty().bindBidirectional(volume.valueProperty());
        volume.valueProperty().addListener((obs, old, value) ->
                Preferences.setDouble("volume", value.doubleValue()));

        ToggleButton mute = new ToggleButton("🔊");
        mute.getStyleClass().add("icon-button");
        mute.setTooltip(new Tooltip("Silenciar el archivo"));
        mute.setOnAction(e -> {
            if (mute.isSelected()) {
                volumeBeforeMute = volume.getValue();
                volume.setValue(0);
                mute.setText("🔇");
            } else {
                volume.setValue(volumeBeforeMute > 0.01 ? volumeBeforeMute : 0.6);
                mute.setText("🔊");
            }
        });

        HBox box = new HBox(6, new Label("Archivo"), mute, volume);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private HBox buildMicControls(AudioBus bus, MicrophoneCapture mic) {
        micLevel = Preferences.getDouble("micLevel", 0.7);

        ToggleButton enable = new ToggleButton("🎙");
        enable.getStyleClass().add("icon-button");
        enable.setTooltip(new Tooltip("Activar la entrada de micrófono"));
        enable.setDisable(!MicrophoneCapture.isAvailable());

        ToggleButton muteMic = new ToggleButton("🔊");
        muteMic.getStyleClass().add("icon-button");
        muteMic.setTooltip(new Tooltip("Silenciar el micrófono en la mezcla visual"));
        muteMic.setDisable(true);

        Slider gain = labelledSlider(0, 1, micLevel, 120);
        gain.setDisable(true);

        Runnable applyMic = () -> {
            micLevel = gain.getValue();
            boolean active = enable.isSelected() && !micMuted;
            bus.setMicWeight(active ? (float) micLevel : 0f);
            // La ganancia de entrada acompaña al nivel: los micrófonos suelen
            // entregar una señal muy por debajo del fondo de escala.
            mic.setGain(0.4 + micLevel * 1.6);
            Preferences.setDouble("micLevel", micLevel);
        };

        enable.setOnAction(e -> {
            boolean on = enable.isSelected();
            muteMic.setDisable(!on);
            gain.setDisable(!on);
            if (on) {
                mic.start();
            } else {
                mic.stop();
                bus.clearMic();
            }
            applyMic.run();
        });

        muteMic.setOnAction(e -> {
            micMuted = muteMic.isSelected();
            muteMic.setText(micMuted ? "🔇" : "🔊");
            if (micMuted) {
                bus.clearMic();
            }
            applyMic.run();
        });

        gain.valueProperty().addListener((obs, old, value) -> applyMic.run());

        HBox box = new HBox(6, new Label("Micrófono"), enable, muteMic, gain);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private HBox buildWindowControls(Runnable onToggleFullscreen) {
        playlistToggle.getStyleClass().add("icon-button");
        playlistToggle.setTooltip(new Tooltip("Mostrar u ocultar la lista de reproducción"));

        Button fullscreen = iconButton("⛶", "Pantalla completa (F)");
        fullscreen.setOnAction(e -> onToggleFullscreen.run());

        HBox box = new HBox(6, playlistToggle, fullscreen);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // ---------------------------------------------------------------- patrón y color

    private FlowPane buildVisualRow(AudioBus bus, VisualizerPane pane, List<Visualizer> visualizers) {
        patternBox.getItems().setAll(visualizers);
        patternBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Visualizer v) {
                return v == null ? "" : v.name();
            }

            @Override
            public Visualizer fromString(String s) {
                return null;
            }
        });
        String savedPattern = Preferences.getVisualizerName(visualizers.getFirst().name());
        Visualizer initial = visualizers.stream()
                .filter(v -> v.name().equals(savedPattern))
                .findFirst()
                .orElse(visualizers.getFirst());
        patternBox.setValue(initial);
        pane.setVisualizer(initial);
        patternBox.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                pane.setVisualizer(value);
                Preferences.setVisualizerName(value.name());
            }
        });

        ComboBox<Palette> paletteBox = new ComboBox<>();
        paletteBox.getItems().setAll(Palette.values());
        Palette savedPalette = Preferences.getPalette();
        paletteBox.setValue(savedPalette);
        pane.setPalette(savedPalette);
        paletteBox.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                pane.setPalette(value);
                Preferences.setPalette(value);
            }
        });

        Slider sensitivity = labelledSlider(0.2, 3.0, Preferences.getDouble("sensitivity", 1.0), 130);
        bus.setSensitivity((float) sensitivity.getValue());
        sensitivity.valueProperty().addListener((obs, old, value) -> {
            bus.setSensitivity(value.floatValue());
            Preferences.setDouble("sensitivity", value.doubleValue());
        });

        Slider smoothing = labelledSlider(0, 0.95, Preferences.getDouble("smoothing", 0.55), 130);
        bus.setSmoothing((float) smoothing.getValue());
        smoothing.valueProperty().addListener((obs, old, value) -> {
            bus.setSmoothing(value.floatValue());
            Preferences.setDouble("smoothing", value.doubleValue());
        });

        CheckBox glow = new CheckBox("Brillo");
        glow.setSelected(Preferences.getBoolean("glow", true));
        pane.setGlowEnabled(glow.isSelected());
        glow.selectedProperty().addListener((obs, old, value) -> {
            pane.setGlowEnabled(value);
            Preferences.setBoolean("glow", value);
        });

        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.textProperty().bind(Bindings.createStringBinding(
                () -> player.statusProperty().get(), player.statusProperty()));
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);

        FlowPane row = new FlowPane(14, 8,
                labelled("Patrón", patternBox),
                labelled("Color", paletteBox),
                labelled("Sensibilidad", sensitivity),
                labelled("Suavizado", smoothing),
                glow,
                status);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ---------------------------------------------------------------- utilidades

    private static HBox labelled(String text, Region control) {
        Label label = new Label(text);
        HBox box = new HBox(6, label, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Slider labelledSlider(double min, double max, double value, double width) {
        Slider slider = new Slider(min, max, Math.clamp(value, min, max));
        slider.setPrefWidth(width);
        return slider;
    }

    private static Button iconButton(String glyph, String tooltip) {
        Button button = new Button(glyph);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    public ToggleButton playlistToggle() {
        return playlistToggle;
    }

    public Visualizer selectedVisualizer() {
        return patternBox.getValue();
    }

    /** Avanza al siguiente patrón de la lista, dando la vuelta al llegar al final. */
    public void cyclePattern() {
        int size = patternBox.getItems().size();
        if (size == 0) {
            return;
        }
        int index = patternBox.getItems().indexOf(patternBox.getValue());
        patternBox.setValue(patternBox.getItems().get(Math.floorMod(index + 1, size)));
    }
}
