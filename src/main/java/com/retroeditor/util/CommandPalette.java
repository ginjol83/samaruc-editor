package com.retroeditor.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Paleta de comandos estilo editor profesional (VS Code / IntelliJ).
 *
 * <p>Muestra una ventana flotante centrada sobre la ventana activa con un
 * campo de filtrado y una lista de entradas seleccionables. Cada entrada es
 * un {@link Entry} (etiqueta, ayuda opcional y acción). Úsese para acciones
 * globales (Ctrl+Shift+P) o para abrir archivos por nombre (Ctrl+P).</p>
 */
public final class CommandPalette {

    private static final int PREF_WIDTH = 560;
    private static final int PREF_HEIGHT = 380;

    /** Una entrada de la paleta: etiqueta visible, guía opcional y acción a ejecutar. */
    public record Entry(String label, String hint, Runnable action) {
    }

    private CommandPalette() {
    }

    /**
     * Muestra la paleta.
     *
     * @param owner       ventana propietaria (se usa para centrar), puede ser null.
     * @param stylesheets hojas de estilo del tema activo (p.ej. "/css/app-dark.css").
     * @param prompt      texto de ayuda del campo de filtrado.
     * @param entries     entradas a mostrar; se filtran al escribir.
     */
    public static void show(Window owner, List<String> stylesheets, String prompt, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return;

        List<Entry> full = new ArrayList<>(entries);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);

        TextField filter = new TextField();
        filter.setPromptText(prompt);
        filter.getStyleClass().add("palette-filter");
        filter.setFocusTraversable(true);

        ListView<Entry> list = new ListView<>();
        list.getStyleClass().add("palette-list");
        list.setFocusTraversable(false);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Entry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item.hint() != null && !item.hint().isBlank()) {
                    Label hint = new Label(item.hint());
                    hint.getStyleClass().add("palette-item-hint");
                    Label label = new Label(item.label());
                    label.getStyleClass().add("palette-item-label");
                    HBox row = new HBox(12, label, hint);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                    setText(null);
                } else {
                    setText(item.label());
                }
            }
        });

        Runnable refresh = () -> {
            String q = filter.getText() == null
                ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
            List<Entry> shown = new ArrayList<>();
            for (Entry e : full) {
                if (q.isEmpty() || matches(e, q)) {
                    shown.add(e);
                }
            }
            list.getItems().setAll(shown);
            if (!shown.isEmpty()) list.getSelectionModel().select(0);
        };

        filter.textProperty().addListener((obs, oldV, newV) -> refresh.run());

        Runnable execute = () -> {
            Entry selected = list.getSelectionModel().getSelectedItem();
            if (selected == null && !list.getItems().isEmpty()) {
                selected = list.getItems().get(0);
            }
            if (selected != null) {
                stage.close();
                try {
                    selected.action().run();
                } catch (RuntimeException ex) {
                    System.err.println("[CommandPalette] Error al ejecutar '" + selected.label() + "': " + ex);
                }
            }
        };

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) execute.run();
        });

        filter.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            int size = list.getItems().size();
            if (event.getCode() == KeyCode.DOWN) {
                int idx = Math.min(list.getSelectionModel().getSelectedIndex() + 1, size - 1);
                list.getSelectionModel().select(idx);
                list.scrollTo(idx);
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                int idx = Math.max(list.getSelectionModel().getSelectedIndex() - 1, 0);
                list.getSelectionModel().select(idx);
                list.scrollTo(idx);
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                execute.run();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            }
        });

        VBox root = new VBox(6, filter, list);
        root.getStyleClass().add("palette-root");
        root.setPrefSize(PREF_WIDTH, PREF_HEIGHT);

        Scene scene = new Scene(root);
        if (stylesheets != null) {
            scene.getStylesheets().setAll(stylesheets);
        }
        stage.setScene(scene);

        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - PREF_WIDTH) / 2.0);
            stage.setY(owner.getY() + Math.max(40, owner.getHeight() / 4.0));
        } else {
            stage.centerOnScreen();
        }

        stage.show();
        refresh.run();
        Platform.runLater(filter::requestFocus);
    }

    private static boolean matches(Entry entry, String query) {
        String hay = (entry.label() + " " + (entry.hint() == null ? "" : entry.hint()))
                .toLowerCase(Locale.ROOT);
        if (hay.contains(query)) return true;
        for (String word : query.split("\\s+")) {
            if (!word.isEmpty() && !hay.contains(word)) return false;
        }
        return true;
    }
}