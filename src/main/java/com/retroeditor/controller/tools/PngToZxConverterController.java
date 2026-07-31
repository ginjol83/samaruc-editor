package com.retroeditor.controller.tools;

import com.retroeditor.service.zxspectrum.CArrayFormatter;
import com.retroeditor.service.zxspectrum.ColorImageCGenerator;
import com.retroeditor.service.zxspectrum.ConversionMode;
import com.retroeditor.service.zxspectrum.PngToZxBitmapConverter;
import com.retroeditor.service.zxspectrum.ZxBitmapExportOptions;
import com.retroeditor.service.zxspectrum.ZxBitmapLayout;
import com.retroeditor.service.zxspectrum.ZxColorImage;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controlador para convertir PNG a código C en formato ZX Spectrum 1bpp.
 *
 * Responsabilidades:
 * - Presentar dialog modal con opciones
 * - File picker para seleccionar PNG
 * - Preview simple de conversión
 * - Validaciones en tiempo real
 * - Export a file o clipboard
 */
public class PngToZxConverterController {
    private static final String PREVIEW_WIDTH = "180";
    private static final String PREVIEW_HEIGHT = "100";

    private final PngToZxBitmapConverter converter = new PngToZxBitmapConverter();
    private final CArrayFormatter formatter = new CArrayFormatter();
    private final ColorImageCGenerator colorGenerator = new ColorImageCGenerator();

    private File selectedPng;
    private byte[] convertedData;
    private ZxColorImage convertedColorImage;

    public void open(Stage owner, File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(msg(bundle, "tool.pngToZx.title", "Convertir PNG a C (ZX Spectrum)"));

        // === TOP: File selection ===
        Button btnSelectFile = new Button(msg(bundle, "tool.pngToZx.selectFile", "Seleccionar PNG"));
        Label lblFileSelected = new Label("-");
        HBox fileBox = new HBox(10, btnSelectFile, lblFileSelected);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setPadding(new Insets(10));

        // === CENTER: Options panel ===
        TextField txtArrayName = new TextField("bitmap_data");
        txtArrayName.setPrefWidth(200);

        ComboBox<ConversionMode> comboMode = new ComboBox<>();
        comboMode.getItems().addAll(ConversionMode.MONOCHROME_1BPP, ConversionMode.COLOR_8BITS);
        comboMode.setValue(ConversionMode.MONOCHROME_1BPP);
        comboMode.setPrefWidth(200);

        Spinner<Integer> spinWidth = new Spinner<>(8, 256, 256, 8);
        spinWidth.setPrefWidth(80);

        Spinner<Integer> spinHeight = new Spinner<>(1, 192, 88, 1);
        spinHeight.setPrefWidth(80);

        Slider sliderThreshold = new Slider(0, 255, 128);
        sliderThreshold.setPrefWidth(200);
        Label lblThresholdValue = new Label("128");
        sliderThreshold.valueProperty().addListener((obs, oldV, newV) -> {
            lblThresholdValue.setText(String.valueOf(newV.intValue()));
        });
        // Hide threshold slider for color mode
        VBox thresholdBox = new VBox(sliderThreshold, lblThresholdValue);

        CheckBox chkUseBrightPalette = new CheckBox(msg(bundle, "tool.pngToZx.brightPalette", "Usar colores brillantes"));
        chkUseBrightPalette.setSelected(false);
        // Hide for monocromo mode
        chkUseBrightPalette.setVisible(false);
        chkUseBrightPalette.setManaged(false);

        ComboBox<ZxBitmapLayout> comboLayout = new ComboBox<>();
        comboLayout.getItems().addAll(ZxBitmapLayout.LINEAR, ZxBitmapLayout.ZX_SCREEN_LAYOUT);
        comboLayout.setValue(ZxBitmapLayout.LINEAR);
        comboLayout.setPrefWidth(150);

        CheckBox chkIncludeConst = new CheckBox(msg(bundle, "tool.pngToZx.includeConst", "Incluir const"));
        chkIncludeConst.setSelected(true);

        CheckBox chkIncludeDefines = new CheckBox(msg(bundle, "tool.pngToZx.includeDefines", "Incluir #defines"));
        chkIncludeDefines.setSelected(false);

        VBox optionsBox = new VBox(8);
        optionsBox.setPadding(new Insets(10));
        optionsBox.getChildren().addAll(
            new Label(msg(bundle, "tool.pngToZx.arrayName", "Nombre del array:")),
            txtArrayName,
            new Label(msg(bundle, "tool.pngToZx.mode", "Modo de conversión:")),
            comboMode,
            new Label(msg(bundle, "tool.pngToZx.width", "Ancho (pixels):")),
            spinWidth,
            new Label(msg(bundle, "tool.pngToZx.height", "Alto (pixels):")),
            spinHeight,
            new Label(msg(bundle, "tool.pngToZx.threshold", "Umbral monocromo:")),
            thresholdBox,
            chkUseBrightPalette,
            new Label(msg(bundle, "tool.pngToZx.layout", "Layout:")),
            comboLayout,
            new Separator(),
            chkIncludeConst,
            chkIncludeDefines
        );

        // === CENTER-RIGHT: Preview ===
        Canvas previewCanvas = new Canvas(180, 100);
        previewCanvas.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1;");
        VBox previewBox = new VBox(5,
            new Label(msg(bundle, "tool.pngToZx.preview", "Vista previa:")),
            previewCanvas
        );
        previewBox.setPadding(new Insets(10));

        HBox centerBox = new HBox(15, optionsBox, previewBox);
        centerBox.setPadding(new Insets(10));

        // === BOTTOM: Code output ===
        TextArea outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(false);
        outputArea.setPrefRowCount(8);

        // === BUTTONS ===
        Button btnExport = new Button(msg(bundle, "tool.pngToZx.buttonExport", "Exportar a proyecto"));
        Button btnClipboard = new Button(msg(bundle, "tool.pngToZx.buttonClipboard", "Copiar al portapapeles"));
        Button btnCancel = new Button(msg(bundle, "tool.pngToZx.buttonCancel", "Cancelar"));

        HBox buttonBox = new HBox(10, btnExport, btnClipboard, btnCancel);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10));

        // === EVENT HANDLERS ===
        btnSelectFile.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
            if (currentProjectDir != null && currentProjectDir.isDirectory()) {
                chooser.setInitialDirectory(currentProjectDir);
            }
            File file = chooser.showOpenDialog(owner);
            if (file != null) {
                selectedPng = file;
                lblFileSelected.setText(file.getName());
                updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                             sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                             comboMode, chkUseBrightPalette);
            }
        });

        // Update preview when options change
        txtArrayName.textProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));
        comboMode.valueProperty().addListener((obs, oldV, newV) -> {
            // Show/hide threshold and palette options based on mode
            thresholdBox.setVisible(newV == ConversionMode.MONOCHROME_1BPP);
            thresholdBox.setManaged(newV == ConversionMode.MONOCHROME_1BPP);
            chkUseBrightPalette.setVisible(newV == ConversionMode.COLOR_8BITS);
            chkUseBrightPalette.setManaged(newV == ConversionMode.COLOR_8BITS);
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette);
        });
        spinWidth.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));
        spinHeight.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));
        sliderThreshold.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));
        comboLayout.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));
        chkIncludeConst.selectedProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));
        chkIncludeDefines.selectedProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));
        chkUseBrightPalette.selectedProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderThreshold, comboLayout, chkIncludeConst, chkIncludeDefines,
                         comboMode, chkUseBrightPalette));

        btnExport.setOnAction(e -> exportToProject(stage, owner, currentProjectDir, outputArea, onExported));
        btnClipboard.setOnAction(e -> copyToClipboard(outputArea));
        btnCancel.setOnAction(e -> stage.close());

        // === LAYOUT ===
        VBox root = new VBox(10,
            fileBox,
            new Separator(),
            centerBox,
            new Label(msg(bundle, "tool.pngToZx.output", "Código C generado:")),
            outputArea,
            buttonBox
        );
        root.setPadding(new Insets(10));

        stage.setScene(new Scene(root, 900, 700));
        stage.show();
    }

    private void updatePreview(Canvas canvas, TextArea outputArea, TextField txtArrayName, 
                              Spinner<Integer> spinWidth, Spinner<Integer> spinHeight,
                              Slider sliderThreshold, ComboBox<ZxBitmapLayout> comboLayout,
                              CheckBox chkIncludeConst, CheckBox chkIncludeDefines,
                              ComboBox<ConversionMode> comboMode, CheckBox chkUseBrightPalette) {
        if (selectedPng == null) {
            outputArea.setText("Selecciona un PNG primero");
            drawPreviewError(canvas);
            return;
        }

        try {
            ConversionMode mode = comboMode.getValue();
            
            if (mode == ConversionMode.MONOCHROME_1BPP) {
                // Modo monocromo: usar conversión existente
                ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
                    txtArrayName.getText(),
                    spinWidth.getValue(),
                    spinHeight.getValue(),
                    (int) sliderThreshold.getValue(),
                    comboLayout.getValue(),
                    chkIncludeConst.isSelected(),
                    chkIncludeDefines.isSelected(),
                    "Generado por Samaruc"
                );

                convertedData = converter.convertPngToMonochrome(selectedPng, opts);
                String cCode = formatter.formatAsC(convertedData, opts);
                outputArea.setText(cCode);

                drawPreviewMonochrome(canvas, opts);
            } else {
                // Modo color: usar nueva conversión
                convertedColorImage = converter.convertPngToColorIndexed(
                    selectedPng,
                    spinWidth.getValue(),
                    spinHeight.getValue(),
                    chkUseBrightPalette.isSelected()
                );
                
                String cCode = colorGenerator.generateC(
                    convertedColorImage,
                    txtArrayName.getText(),
                    chkIncludeConst.isSelected(),
                    chkIncludeDefines.isSelected()
                );
                outputArea.setText(cCode);

                drawPreviewColor(canvas);
            }
        } catch (IOException ex) {
            outputArea.setText("ERROR: " + ex.getMessage());
            drawPreviewError(canvas);
        } catch (IllegalArgumentException ex) {
            outputArea.setText("ERROR: " + ex.getMessage());
            drawPreviewError(canvas);
        }
    }

    private void drawPreviewMonochrome(Canvas canvas, ZxBitmapExportOptions opts) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (convertedData == null) return;

        double scale = Math.min(
            canvas.getWidth() / opts.width(),
            canvas.getHeight() / opts.height()
        );

        int bytesPerRow = opts.width() / 8;
        for (int y = 0; y < opts.height(); y++) {
            for (int byteCol = 0; byteCol < bytesPerRow; byteCol++) {
                int byteIndex = y * bytesPerRow + byteCol;
                if (byteIndex >= convertedData.length) break;

                byte b = convertedData[byteIndex];
                for (int bit = 0; bit < 8; bit++) {
                    int pixelX = byteCol * 8 + bit;
                    boolean isWhite = ((b >> (7 - bit)) & 1) == 1;
                    gc.setFill(isWhite ? Color.BLACK : Color.WHITE);
                    gc.fillRect(pixelX * scale, y * scale, scale, scale);
                }
            }
        }
    }

    private void drawPreviewColor(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        if (convertedColorImage == null) {
            drawPreviewError(canvas);
            return;
        }

        double scale = Math.min(
            canvas.getWidth() / convertedColorImage.width(),
            canvas.getHeight() / convertedColorImage.height()
        );

        for (int y = 0; y < convertedColorImage.height(); y++) {
            for (int x = 0; x < convertedColorImage.width(); x++) {
                int colorIdx = convertedColorImage.getPixelColor(x, y);
                int argbColor = convertedColorImage.getColorRgb(colorIdx);
                
                // Convertir ARGB a JavaFX Color
                int r = (argbColor >> 16) & 0xFF;
                int g = (argbColor >> 8) & 0xFF;
                int b = argbColor & 0xFF;
                gc.setFill(Color.color(r / 255.0, g / 255.0, b / 255.0));
                gc.fillRect(x * scale, y * scale, scale, scale);
            }
        }
    }

    private void drawPreviewError(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.GRAY);
        gc.setFont(javafx.scene.text.Font.font(12));
        gc.fillText("PNG no válido", 50, 50);
    }

    private void copyToClipboard(TextArea outputArea) {
        String text = outputArea.getText();
        if (text.isEmpty()) return;

        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void exportToProject(Stage stage, Stage owner, File currentProjectDir, TextArea outputArea, 
                                Consumer<File> onExported) {
        if (currentProjectDir == null || !currentProjectDir.exists()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(owner);
            alert.setTitle("Proyecto no abierto");
            alert.setContentText("Abre un proyecto para poder exportar el código.");
            alert.showAndWait();
            return;
        }

        String code = outputArea.getText();
        if (code.isEmpty() || code.startsWith("ERROR")) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(owner);
            alert.setTitle("Conversión no válida");
            alert.setContentText("Realiza una conversión válida antes de exportar.");
            alert.showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C Header / Source", "*.h", "*.c"));
        chooser.setInitialDirectory(currentProjectDir);
        chooser.setInitialFileName("bitmap.h");

        File selected = chooser.showSaveDialog(owner);
        if (selected == null) return;

        try {
            Files.write(selected.toPath(), code.getBytes(StandardCharsets.UTF_8));
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle("Exportado");
            alert.setContentText("Código exportado a: " + selected.getName());
            alert.showAndWait();

            onExported.accept(selected);
            stage.close();
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(owner);
            alert.setTitle("Error");
            alert.setContentText("No se pudo exportar: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    private String msg(ResourceBundle bundle, String key, String defaultValue) {
        if (bundle == null) return defaultValue;
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
