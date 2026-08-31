package com.chromawave.app;

import com.chromawave.audio.AudioPlayer;
import com.chromawave.audio.Track;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Panel lateral con la lista de reproducción. */
public final class PlaylistPane extends VBox {

    private final ListView<Track> list = new ListView<>();

    public PlaylistPane(AudioPlayer player, Runnable onAddFiles) {
        getStyleClass().add("playlist-pane");
        setPrefWidth(280);
        setSpacing(8);
        setPadding(new Insets(12));

        Label title = new Label("Lista de reproducción");
        title.getStyleClass().add("panel-title");

        list.setItems(player.playlist());
        list.setPlaceholder(new Label("Arrastra archivos aquí"));
        VBox.setVgrow(list, Priority.ALWAYS);

        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Track track, boolean empty) {
                super.updateItem(track, empty);
                if (empty || track == null) {
                    setText(null);
                    getStyleClass().remove("current-track");
                } else {
                    setText(track.title());
                    boolean current = track.equals(player.currentTrackProperty().get());
                    getStyleClass().remove("current-track");
                    if (current) {
                        getStyleClass().add("current-track");
                    }
                }
            }
        });

        // Refrescar las celdas al cambiar de pista resalta la que está sonando.
        player.currentTrackProperty().addListener((obs, old, value) -> list.refresh());

        list.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Track selected = list.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    player.open(selected, true);
                }
            }
        });

        MenuItem remove = new MenuItem("Quitar de la lista");
        remove.setOnAction(e -> {
            Track selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                player.remove(selected);
            }
        });
        list.setContextMenu(new ContextMenu(remove));

        Button add = new Button("Añadir…");
        add.setOnAction(e -> onAddFiles.run());
        Button clear = new Button("Vaciar");
        clear.setOnAction(e -> player.clearPlaylist());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, add, spacer, clear);

        getChildren().addAll(title, list, buttons);
    }
}
