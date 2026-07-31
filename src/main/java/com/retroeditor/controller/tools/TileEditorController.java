package com.retroeditor.controller.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.retroeditor.service.GameBoyTileCodeGenerator;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Editor de tiles 8x8 para Game Boy con generación de código C (2bpp).
 */
public class TileEditorController {

    private static final int TILE_SIZE = 8;
    private static final int CELL_SIZE = 28;

    private final int[][] pixels = new int[TILE_SIZE][TILE_SIZE];
    private final GameBoyTileCodeGenerator codeGenerator = new GameBoyTileCodeGenerator();

    private final Color[] palette = new Color[] {
        Color.web("#f8f8f8"),
        Color.web("#a8a8a8"),
        Color.web("#585858"),
        Color.web("#101010")
    };

    public void open(Stage owner, File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(msg(bundle, "tool.tileEditor.title", "Editor de Tiles (Game Boy)"));

        Canvas canvas = new Canvas(TILE_SIZE * CELL_SIZE, TILE_SIZE * CELL_SIZE);
        TextField symbolField = new TextField("tile_data");
        TextArea outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(false);
        outputArea.setPrefRowCount(10);

        CheckBox chkConst = new CheckBox(msg(bundle, "tool.tileEditor.includeConst", "Incluir const"));
        chkConst.setSelected(true);
        CheckBox chkDefines = new CheckBox(msg(bundle, "tool.tileEditor.includeDefines", "Incluir #defines"));
        chkDefines.setSelected(false);

        ChoiceBox<Integer> colorChoice = new ChoiceBox<>();
        colorChoice.getItems().addAll(0, 1, 2, 3);
        colorChoice.setValue(3);

        Runnable updateAction = () -> updateCode(outputArea, symbolField.getText(), chkConst.isSelected(), chkDefines.isSelected());

        canvas.setOnMousePressed(evt -> {
            int x = (int) (evt.getX() / CELL_SIZE);
            int y = (int) (evt.getY() / CELL_SIZE);
            if (x < 0 || y < 0 || x >= TILE_SIZE || y >= TILE_SIZE) return;

            if (evt.getButton() == MouseButton.SECONDARY) {
                pixels[y][x] = 0;
            } else {
                pixels[y][x] = colorChoice.getValue() != null ? colorChoice.getValue() : 0;
            }
            redrawCanvas(canvas);
            updateAction.run();
        });

        canvas.setOnMouseDragged(evt -> {
            if (!evt.isPrimaryButtonDown()) return;
            int x = (int) (evt.getX() / CELL_SIZE);
            int y = (int) (evt.getY() / CELL_SIZE);
            if (x < 0 || y < 0 || x >= TILE_SIZE || y >= TILE_SIZE) return;
            pixels[y][x] = colorChoice.getValue() != null ? colorChoice.getValue() : 0;
            redrawCanvas(canvas);
            updateAction.run();
        });

        symbolField.textProperty().addListener((obs, oldV, newV) -> updateAction.run());
        chkConst.selectedProperty().addListener((obs, oldV, newV) -> updateAction.run());
        chkDefines.selectedProperty().addListener((obs, oldV, newV) -> updateAction.run());

        Button clearButton = new Button(msg(bundle, "tool.tileEditor.clear", "Limpiar"));
        clearButton.setOnAction(e -> {
            clearPixels();
            redrawCanvas(canvas);
            updateAction.run();
        });

        Button copyButton = new Button(msg(bundle, "tool.tileEditor.copy", "Copiar código"));
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(outputArea.getText() != null ? outputArea.getText() : "");
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button exportButton = new Button(msg(bundle, "tool.tileEditor.export", "Exportar al proyecto"));
        exportButton.setOnAction(e -> exportCodeToProject(stage, currentProjectDir, outputArea.getText(), symbolField.getText(), bundle, onExported));

        HBox editorTop = new HBox(8,
            new Label(msg(bundle, "tool.tileEditor.symbol", "Nombre del array:")),
            symbolField,
            new Label(msg(bundle, "tool.tileEditor.color", "Color:")),
            colorChoice,
            chkConst,
            chkDefines
        );
        editorTop.setAlignment(Pos.CENTER_LEFT);

        HBox buttons = new HBox(8, clearButton, copyButton, exportButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(10, editorTop, canvas, buttons, outputArea);
        center.setPadding(new Insets(10));

        BorderPane root = new BorderPane(center);

        redrawCanvas(canvas);
        updateAction.run();

        stage.setScene(new Scene(root, 750, 620));
        stage.show();
    }

    private void clearPixels() {
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                pixels[y][x] = 0;
            }
        }
    }

    private void redrawCanvas(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int colorIndex = pixels[y][x];
                if (colorIndex < 0 || colorIndex > 3) colorIndex = 0;
                gc.setFill(palette[colorIndex]);
                gc.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                gc.setStroke(Color.web("#404040"));
                gc.strokeRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
    }

    private void updateCode(TextArea outputArea, String symbolName, boolean includeConst, boolean includeDefines) {
        outputArea.setText(codeGenerator.generateTileCode(pixels, symbolName, includeConst, includeDefines));
    }

    private void exportCodeToProject(Stage owner, File currentProjectDir, String code, String symbolName, ResourceBundle bundle, Consumer<File> onExported) {
        if (currentProjectDir == null || !currentProjectDir.exists() || !currentProjectDir.isDirectory()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(owner);
            alert.setTitle(msg(bundle, "tool.tileEditor.export.projectMissing.title", "Proyecto no abierto"));
            alert.setHeaderText(null);
            alert.setContentText(msg(bundle, "tool.tileEditor.export.projectMissing.content", "Abre un proyecto para poder exportar el tile."));
            alert.showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C / Header", "*.c", "*.h"));
        chooser.setInitialDirectory(currentProjectDir);
        chooser.setInitialFileName(suggestFileName(symbolName));

        File selected = chooser.showSaveDialog(owner);
        if (selected == null) return;

        try {
            Files.writeString(selected.toPath(), code != null ? code : "", StandardCharsets.UTF_8);
            if (onExported != null) onExported.accept(selected);
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(owner);
            alert.setTitle(msg(bundle, "tool.tileEditor.export.error.title", "Error de exportación"));
            alert.setHeaderText(null);
            alert.setContentText(msg(bundle, "tool.tileEditor.export.error.content", "No se pudo exportar el archivo: ") + ex.getMessage());
            alert.showAndWait();
        }
    }

    private String suggestFileName(String symbolName) {
        String raw = symbolName != null ? symbolName.trim() : "";
        if (raw.isEmpty()) return "tile_data.c";
        return raw + ".c";
    }

    private String msg(ResourceBundle bundle, String key, String fallback) {
        try {
            if (bundle != null && bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}

