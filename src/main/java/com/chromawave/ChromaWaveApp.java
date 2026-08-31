package com.chromawave;

import com.chromawave.app.ControlBar;
import com.chromawave.app.PlaylistPane;
import com.chromawave.app.Preferences;
import com.chromawave.audio.AudioFormats;
import com.chromawave.audio.AudioPlayer;
import com.chromawave.audio.MicrophoneCapture;
import com.chromawave.dsp.AudioBus;
import com.chromawave.dsp.SpectrumData;
import com.chromawave.visual.Visualizer;
import com.chromawave.visual.VisualizerPane;
import com.chromawave.visual.VisualizerRegistry;
import com.chromawave.visual.patterns.AutoCycleVisualizer;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.List;

/**
 * ChromaWave: visualizador de audio en JavaFX.
 *
 * <p>La aplicación conecta tres piezas: las fuentes de audio (archivo y
 * micrófono) alimentan un {@link AudioBus} que mezcla y suaviza el análisis, y
 * un único bucle de animación toma una instantánea de ese bus por frame y se la
 * entrega al {@link VisualizerPane}. De ese modo el hilo de audio nunca dibuja
 * y el hilo de dibujo nunca bloquea al audio.
 */
public final class ChromaWaveApp extends Application {

    private static final double HIDE_DELAY_SEC = 2.5;

    private final AudioBus bus = new AudioBus();
    private final SpectrumData frame = new SpectrumData();

    private AudioPlayer player;
    private MicrophoneCapture microphone;
    private VisualizerPane visualizerPane;
    private ControlBar controlBar;
    private PlaylistPane playlistPane;
    private Label overlayLabel;
    private BorderPane root;
    private Stage stage;
    private AnimationTimer loop;
    private PauseTransition hideControls;

    private long lastFrameNanos;
    private String lastOverlayText = "";

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        player = new AudioPlayer(bus.fileSlot());
        microphone = new MicrophoneCapture(bus.micSlot());
        microphone.setOnError(message -> player.statusProperty().set("Micrófono: " + message));

        visualizerPane = new VisualizerPane();
        List<Visualizer> visualizers = VisualizerRegistry.createAll();

        controlBar = new ControlBar(player, bus, microphone, visualizerPane, visualizers,
                this::chooseFiles, this::toggleFullscreen);
        playlistPane = new PlaylistPane(player, this::chooseFiles);

        overlayLabel = new Label();
        overlayLabel.getStyleClass().add("overlay-label");
        overlayLabel.setOpacity(0);
        StackPane.setAlignment(overlayLabel, Pos.TOP_LEFT);
        StackPane.setMargin(overlayLabel, new Insets(18));

        StackPane center = new StackPane(visualizerPane, overlayLabel);
        center.setPickOnBounds(true);

        root = new BorderPane();
        root.setCenter(center);
        root.setBottom(controlBar);

        controlBar.playlistToggle().selectedProperty().addListener((obs, old, visible) -> {
            root.setRight(visible ? playlistPane : null);
            Preferences.setBoolean("playlistVisible", visible);
        });
        controlBar.playlistToggle().setSelected(Preferences.getBoolean("playlistVisible", true));

        Scene scene = new Scene(root, 1180, 720);
        scene.getStylesheets().add(
                java.util.Objects.requireNonNull(getClass().getResource("/chromawave.css")).toExternalForm());
        installShortcuts(scene);
        installDragAndDrop(scene);

        center.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleFullscreen();
            }
        });

        stage.setTitle("ChromaWave");
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        setupAutoHide(scene);
        startLoop();
        loadFilesFromArguments();
    }

    /** Permite abrir la aplicación con archivos ya indicados en la línea de órdenes. */
    private void loadFilesFromArguments() {
        List<File> files = getParameters().getRaw().stream()
                .map(File::new)
                .filter(File::isFile)
                .filter(AudioFormats::isSupported)
                .toList();
        if (!files.isEmpty()) {
            player.addAll(files);
        }
    }

    // ---------------------------------------------------------------- bucle de render

    private void startLoop() {
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNanos == 0) {
                    lastFrameNanos = now;
                    return;
                }
                // Se limita el delta para que una pausa del sistema (por ejemplo
                // al arrastrar la ventana) no provoque un salto en las animaciones.
                double delta = Math.min((now - lastFrameNanos) / 1_000_000_000.0, 0.05);
                lastFrameNanos = now;

                player.tick();
                bus.update(delta);
                bus.snapshot(frame);
                visualizerPane.render(frame, delta);
                updateOverlay();
            }
        };
        loop.start();
    }

    private void updateOverlay() {
        String track = player.currentTrackProperty().get() == null
                ? "Sin pista"
                : player.currentTrackProperty().get().title();
        String pattern = patternLabel();
        String text = track + "\n" + pattern;
        if (!text.equals(lastOverlayText)) {
            lastOverlayText = text;
            overlayLabel.setText(text);
            flashOverlay();
        }
    }

    private String patternLabel() {
        Visualizer selected = controlBar.selectedVisualizer();
        if (selected instanceof AutoCycleVisualizer auto) {
            return "Automático · " + auto.currentName();
        }
        return selected == null ? "" : selected.name();
    }

    private void flashOverlay() {
        FadeTransition in = new FadeTransition(Duration.millis(220), overlayLabel);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(700), overlayLabel);
        out.setToValue(0);
        out.setDelay(Duration.seconds(2.6));
        in.setOnFinished(e -> out.play());
        in.play();
    }

    // ---------------------------------------------------------------- interacción

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elegir archivos de audio");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio", AudioFormats.EXTENSION_FILTERS),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));

        String lastDir = Preferences.getString("lastDirectory", null);
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.isDirectory()) {
                chooser.setInitialDirectory(dir);
            }
        }

        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            Preferences.setString("lastDirectory", files.getFirst().getParent());
            player.addAll(files);
        }
    }

    private void installDragAndDrop(Scene scene) {
        scene.setOnDragOver((DragEvent event) -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        scene.setOnDragDropped((DragEvent event) -> {
            boolean handled = false;
            if (event.getDragboard().hasFiles()) {
                List<File> files = event.getDragboard().getFiles().stream()
                        .filter(AudioFormats::isSupported)
                        .toList();
                if (!files.isEmpty()) {
                    player.addAll(files);
                    handled = true;
                }
            }
            event.setDropCompleted(handled);
            event.consume();
        });
    }

    private void installShortcuts(Scene scene) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case F -> toggleFullscreen();
                case N -> player.next();
                case P -> player.previous();
                case V -> controlBar.cyclePattern();
                case L -> controlBar.playlistToggle().setSelected(
                        !controlBar.playlistToggle().isSelected());
                case LEFT -> player.seek(Math.max(0, player.positionProperty().get() - 5));
                case RIGHT -> player.seek(player.positionProperty().get() + 5);
                default -> {
                    return;
                }
            }
            event.consume();
        });
        // La barra espaciadora se captura antes de llegar a los controles: de lo
        // contrario activaría el botón que tuviera el foco en vez de pausar.
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                player.togglePlay();
                event.consume();
            }
        });
    }

    private void toggleFullscreen() {
        stage.setFullScreen(!stage.isFullScreen());
    }

    /** En pantalla completa los controles se ocultan si el ratón deja de moverse. */
    private void setupAutoHide(Scene scene) {
        hideControls = new PauseTransition(Duration.seconds(HIDE_DELAY_SEC));
        hideControls.setOnFinished(e -> {
            if (stage.isFullScreen()) {
                setChromeVisible(false);
            }
        });

        scene.setOnMouseMoved(e -> {
            if (stage.isFullScreen()) {
                setChromeVisible(true);
                hideControls.playFromStart();
            }
        });

        stage.fullScreenProperty().addListener((obs, old, full) -> {
            if (full) {
                stage.setFullScreenExitHint("Pulsa Esc o F para salir de pantalla completa");
                hideControls.playFromStart();
            } else {
                hideControls.stop();
                setChromeVisible(true);
            }
        });
    }

    private void setChromeVisible(boolean visible) {
        root.setBottom(visible ? controlBar : null);
        root.setRight(visible && controlBar.playlistToggle().isSelected() ? playlistPane : null);
    }

    private void shutdown() {
        if (loop != null) {
            loop.stop();
        }
        microphone.stop();
        player.dispose();
        Preferences.flush();
    }

    @Override
    public void stop() {
        shutdown();
    }

}
