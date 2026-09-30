package com.retroeditor.controller.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.retroeditor.service.converter.PngToRetroImageConverter;
import com.retroeditor.service.converter.RetroCGenerator;
import com.retroeditor.service.converter.RetroExportOptions;
import com.retroeditor.service.converter.RetroImageConversion;
import com.retroeditor.service.converter.RetroImageMode;
import com.retroeditor.service.converter.RetroImagePacker;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Controlador de la herramienta que convierte un PNG en codigo C con el
 * formato de pixeles de CPC, Commodore 64, Master System, Game Gear o Atari
 * 8-bit.
 *
 * La conversion se ejecuta en un hilo secundario porque cuantizar una imagen y
 * empaquetar sus bytes puede tardar lo suficiente como para congelar la
 * interfaz; el resultado se aplica de vuelta en el hilo de JavaFX.
 */
public class PngToRetroConverterController {
    private static final double PREVIEW_WIDTH = 220;
    private static final double PREVIEW_HEIGHT = 170;
    private static final int MIN_DIMENSION = 8;
    private static final int MAX_DIMENSION = 1024;
    private static final long RECONVERT_DELAY_MS = 250;

    /** Modo de video con su etiqueta ya traducida. */
    private record ModeOption(RetroImageMode mode, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final PngToRetroImageConverter converter = new PngToRetroImageConverter();
    private final RetroCGenerator codeGenerator = new RetroCGenerator();

    private File selectedPng;
    private String generatedCode = "";
    private int conversionGeneration;

    private ResourceBundle bundle;
    private Node rootNode;
    private Runnable closeHandler;
    private final PauseTransition reconvertDelay =
            new PauseTransition(Duration.millis(RECONVERT_DELAY_MS));

    /**
     * Crea la UI de la herramienta para mostrarla como pestana del editor.
     *
     * @param currentProjectDir proyecto abierto, para abrir el dialogo en su carpeta
     * @param bundle bundle de traduccion de la interfaz
     * @param onExported se invoca con el archivo exportado para refrescar el
     *        arbol de ficheros y abrirlo en el editor
     */
    public Node createNode(File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        this.bundle = bundle;
        return buildUi(currentProjectDir, onExported);
    }

    /**
     * Registra la accion a ejecutar cuando la pestana de la herramienta se cierra.
     */
    public void setOnCloseRequest(Runnable onClose) {
        this.closeHandler = onClose;
    }

    private void requestClose() {
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    private Node buildUi(File currentProjectDir, Consumer<File> onExported) {
        Button selectButton = new Button(msg("tool.pngToRetro.selectFile", "Seleccionar PNG"));
        Label fileLabel = new Label("-");
        HBox fileBox = new HBox(10, selectButton, fileLabel);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setPadding(new Insets(10));

        ComboBox<ModeOption> modeBox = new ComboBox<>();
        modeBox.getItems().addAll(modeOptions());
        modeBox.setValue(modeBox.getItems().get(1));
        modeBox.setPrefWidth(300);

        TextField arrayName = new TextField("image_data");
        arrayName.setPrefWidth(200);

        Spinner<Integer> width = new Spinner<>(MIN_DIMENSION, MAX_DIMENSION,
                RetroImageMode.CPC_MODE_1.getDefaultWidth(), 8);
        width.setPrefWidth(90);
        Spinner<Integer> height = new Spinner<>(MIN_DIMENSION, MAX_DIMENSION,
                RetroImageMode.CPC_MODE_1.getDefaultHeight(), 8);
        height.setPrefWidth(90);

        Button defaultSize = new Button(msg("tool.pngToRetro.defaultSize", "Tamanos del modo"));
        defaultSize.setOnAction(e -> applyModeDefaults(modeBox, width, height));

        CheckBox includeConst = new CheckBox(msg("tool.pngToRetro.includeConst", "Incluir const"));
        includeConst.setSelected(true);
        CheckBox includeDefines = new CheckBox(msg("tool.pngToRetro.includeDefines", "Incluir #defines"));
        includeDefines.setSelected(true);

        Label infoLabel = new Label(modeBox.getValue().label());
        infoLabel.setWrapText(true);

        VBox optionsBox = new VBox(8,
                new Label(msg("tool.pngToRetro.mode", "Modo de video:")),
                modeBox,
                new Label(msg("tool.pngToRetro.arrayName", "Nombre del array:")),
                arrayName,
                new Label(msg("tool.pngToRetro.width", "Ancho (pixeles):")),
                width,
                new Label(msg("tool.pngToRetro.height", "Alto (pixeles):")),
                height,
                defaultSize,
                new Separator(),
                includeConst,
                includeDefines,
                infoLabel);
        optionsBox.setPadding(new Insets(10));

        Canvas preview = new Canvas(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        preview.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1;");
        VBox previewBox = new VBox(5,
                new Label(msg("tool.pngToRetro.preview", "Vista previa:")),
                preview);
        previewBox.setPadding(new Insets(10));

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(false);
        output.setPrefRowCount(10);

        Button exportButton = new Button(msg("tool.pngToRetro.buttonExport", "Exportar a proyecto"));
        Button clipboardButton = new Button(msg("tool.pngToRetro.buttonClipboard", "Copiar al portapapeles"));
        Button closeButton = new Button(msg("tool.pngToRetro.buttonClose", "Cerrar"));
        HBox buttonBox = new HBox(10, exportButton, clipboardButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10));

        selectButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
            if (currentProjectDir != null && currentProjectDir.isDirectory()) {
                chooser.setInitialDirectory(currentProjectDir);
            }
            File file = chooser.showOpenDialog(currentWindow());
            if (file != null) {
                selectedPng = file;
                fileLabel.setText(file.getName());
                reconvert(modeBox, arrayName, width, height, includeConst, includeDefines, infoLabel, preview, output);
            }
        });

        Runnable reconvertNow = () -> reconvert(modeBox, arrayName, width, height,
                includeConst, includeDefines, infoLabel, preview, output);
        reconvertDelay.setOnFinished(e -> reconvertNow.run());
        Runnable reconvertSoon = reconvertDelay::playFromStart;
        modeBox.valueProperty().addListener((obs, oldV, newV) -> {
            applyModeDefaults(modeBox, width, height);
            reconvertSoon.run();
        });
        arrayName.textProperty().addListener((obs, oldV, newV) -> reconvertSoon.run());
        width.valueProperty().addListener((obs, oldV, newV) -> reconvertSoon.run());
        height.valueProperty().addListener((obs, oldV, newV) -> reconvertSoon.run());
        includeConst.selectedProperty().addListener((obs, oldV, newV) -> reconvertSoon.run());
        includeDefines.selectedProperty().addListener((obs, oldV, newV) -> reconvertSoon.run());

        exportButton.setOnAction(e -> exportToProject(currentProjectDir, output, onExported));
        clipboardButton.setOnAction(e -> copyToClipboard());
        closeButton.setOnAction(e -> requestClose());

        VBox root = new VBox(10,
                fileBox,
                new Separator(),
                new HBox(15, optionsBox, previewBox),
                new Label(msg("tool.pngToRetro.output", "Codigo C generado:")),
                output,
                buttonBox);
        root.setPadding(new Insets(10));
        rootNode = root;
        drawMessage(preview, msg("tool.pngToRetro.noImage", "Sin imagen"));
        return root;
    }

    private List<ModeOption> modeOptions() {
        List<ModeOption> options = new ArrayList<>();
        for (RetroImageMode mode : RetroImageMode.valuesInUiOrder()) {
            options.add(new ModeOption(mode, msg("tool.pngToRetro.mode." + modeKey(mode), mode.getDisplayName())));
        }
        return options;
    }

    private String modeKey(RetroImageMode mode) {
        return switch (mode) {
            case CPC_MODE_0 -> "cpc0";
            case CPC_MODE_1 -> "cpc1";
            case CPC_MODE_2 -> "cpc2";
            case ATARI_PLAYFIELD -> "atariPlayfield";
            case ATARI_MODE_7 -> "atariMode7";
            case C64_CHARSET -> "c64Charset";
            case C64_BITMAP -> "c64Bitmap";
            case MASTER_SYSTEM_TILES -> "masterSystem";
            case GAME_GEAR_TILES -> "gameGear";
            case MSX_TILES -> "msx";
            case NES_TILES -> "nes";
        };
    }

    private void applyModeDefaults(ComboBox<ModeOption> modeBox, Spinner<Integer> width,
                                   Spinner<Integer> height) {
        ModeOption option = modeBox.getValue();
        if (option == null) {
            return;
        }
        width.getValueFactory().setValue(option.mode().getDefaultWidth());
        height.getValueFactory().setValue(option.mode().getDefaultHeight());
    }

    private void reconvert(ComboBox<ModeOption> modeBox, TextField arrayName,
                           Spinner<Integer> width, Spinner<Integer> height,
                           CheckBox includeConst, CheckBox includeDefines, Label infoLabel,
                           Canvas preview, TextArea output) {
        ModeOption option = modeBox.getValue();
        if (option == null || selectedPng == null) {
            return;
        }
        RetroImageMode mode = option.mode();
        File png = selectedPng;
        int targetWidth = width.getValue();
        int targetHeight = height.getValue();
        RetroExportOptions options = new RetroExportOptions(arrayName.getText(),
                includeConst.isSelected(), includeDefines.isSelected(), true, "Generado por Samaruc");
        int generation = ++conversionGeneration;

        output.setText(msg("tool.pngToRetro.converting", "Convirtiendo..."));
        Thread worker = new Thread(() -> {
            try {
                RetroImageConversion result = converter.convert(png, mode, targetWidth, targetHeight);
                String code = codeGenerator.generate(result, options);
                Platform.runLater(() -> {
                    if (generation != conversionGeneration) {
                        return;
                    }
                    generatedCode = code;
                    output.setText(code);
                    infoLabel.setText(describe(result, option.label()));
                    drawPreview(preview, result);
                });
            } catch (IOException | RuntimeException ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                Platform.runLater(() -> {
                    if (generation != conversionGeneration) {
                        return;
                    }
                    generatedCode = "";
                    output.setText(msg("tool.pngToRetro.error", "ERROR") + ": " + message);
                    infoLabel.setText(option.label());
                    drawMessage(preview, message);
                });
            }
        }, "retro-png-converter");
        worker.setDaemon(true);
        worker.start();
    }

    private String describe(RetroImageConversion result, String modeLabel) {
        StringBuilder text = new StringBuilder(modeLabel)
                .append(": ").append(result.totalBytes()).append(" bytes");
        if (result.layers().size() > 1) {
            List<String> names = RetroImagePacker.layerNames(result.mode(), "datos");
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < result.layers().size() && i < names.size(); i++) {
                parts.add(names.get(i) + " (" + result.layers().get(i).data().length + " bytes)");
            }
            text.append(" - ").append(String.join(", ", parts));
        }
        return text.toString();
    }

    private void drawPreview(Canvas canvas, RetroImageConversion result) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double scale = Math.max(1, Math.min(
                canvas.getWidth() / result.width(),
                canvas.getHeight() / result.height()));
        int[] palette = result.palette();
        for (int y = 0; y < result.height(); y++) {
            for (int x = 0; x < result.width(); x++) {
                int argb = palette[result.indexAt(x, y)];
                gc.setFill(Color.color(((argb >> 16) & 0xFF) / 255.0,
                        ((argb >> 8) & 0xFF) / 255.0, (argb & 0xFF) / 255.0));
                gc.fillRect(x * scale, y * scale, scale, scale);
            }
        }
    }

    private void drawMessage(Canvas canvas, String message) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.GRAY);
        gc.setFont(Font.font(12));
        gc.fillText(message == null ? "" : message, 10, 30);
    }

    private void copyToClipboard() {
        if (generatedCode.isEmpty()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(generatedCode);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void exportToProject(File currentProjectDir, TextArea output, Consumer<File> onExported) {
        if (currentProjectDir == null || !currentProjectDir.exists()) {
            showAlert(Alert.AlertType.WARNING, "Proyecto no abierto",
                    "Abre un proyecto para poder exportar el codigo.");
            return;
        }
        if (generatedCode.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Conversion no valida",
                    "Selecciona un PNG y espera a que termine la conversion.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C Header / Source", "*.h", "*.c"));
        chooser.setInitialDirectory(currentProjectDir);
        chooser.setInitialFileName("image_data.h");
        File selected = chooser.showSaveDialog(currentWindow());
        if (selected == null) {
            return;
        }
        try {
            Files.write(selected.toPath(), output.getText().getBytes(StandardCharsets.UTF_8));
            showAlert(Alert.AlertType.INFORMATION, "Exportado", "Codigo exportado a: " + selected.getName());
            if (onExported != null) {
                onExported.accept(selected);
            }
            requestClose();
        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo exportar: " + ex.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(currentWindow());
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Window currentWindow() {
        if (rootNode != null && rootNode.getScene() != null) {
            return rootNode.getScene().getWindow();
        }
        return null;
    }

    private String msg(String key, String defaultValue) {
        if (bundle == null) {
            return defaultValue;
        }
        try {
            return bundle.getString(key);
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}
