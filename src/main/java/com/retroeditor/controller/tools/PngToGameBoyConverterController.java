package com.retroeditor.controller.tools;

import com.retroeditor.service.gameboy.GameBoyArrayFormatter;
import com.retroeditor.service.gameboy.GameBoyBitmapExportOptions;
import com.retroeditor.service.gameboy.GameBoyColorQuantizer;
import com.retroeditor.service.gameboy.GameBoyTilemapFormatter;
import com.retroeditor.service.gameboy.GameBoyTilemapResult;
import com.retroeditor.service.gameboy.PngToGameBoyBitmapConverter;
import com.retroeditor.service.gameboy.PngToGameBoyTilemapConverter;

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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controlador para convertir PNG a código C en formato Game Boy 2bpp.
 * Soporta dos modos: Bitmap plano o Tilemap (para fondos con set_bkg_data/set_bkg_submap).
 *
 * Responsabilidades:
 * - Presentar dialog modal con opciones
 * - File picker para seleccionar PNG
 * - Preview simple de conversión
 * - Validaciones en tiempo real
 * - Export a file o clipboard
 */
public class PngToGameBoyConverterController {
    private static final String PREVIEW_WIDTH = "180";
    private static final String PREVIEW_HEIGHT = "144";

    private final PngToGameBoyBitmapConverter bitmapConverter = new PngToGameBoyBitmapConverter();
    private final PngToGameBoyTilemapConverter tilemapConverter = new PngToGameBoyTilemapConverter();
    private final GameBoyArrayFormatter formatter = new GameBoyArrayFormatter();
    private final GameBoyTilemapFormatter tilemapFormatter = new GameBoyTilemapFormatter();

    private File selectedPng;
    private byte[] convertedData;
    private GameBoyTilemapResult tilemapResult;

    public void open(Stage owner, File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(msg(bundle, "tool.pngToGameBoy.title", "Convertir PNG a C (Game Boy)"));

        // === TOP: File selection ===
        Button btnSelectFile = new Button(msg(bundle, "tool.pngToGameBoy.selectFile", "Seleccionar PNG"));
        Label lblFileSelected = new Label("-");
        HBox fileBox = new HBox(10, btnSelectFile, lblFileSelected);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setPadding(new Insets(10));

        // === CENTER: Options panel ===
        TextField txtArrayName = new TextField("bitmap_data");
        txtArrayName.setPrefWidth(200);

        ComboBox<String> comboExportMode = new ComboBox<>();
        comboExportMode.getItems().addAll("Bitmap plano", "Tilemap (para fondos GBDK)");
        comboExportMode.setValue("Bitmap plano");
        comboExportMode.setPrefWidth(200);

        Spinner<Integer> spinWidth = new Spinner<>(8, 320, 160, 8);
        spinWidth.setPrefWidth(80);
        spinWidth.setDisable(true);

        Spinner<Integer> spinHeight = new Spinner<>(8, 256, 144, 8);
        spinHeight.setPrefWidth(80);
        spinHeight.setDisable(true);

        comboExportMode.valueProperty().addListener((obs, oldV, newV) -> {
            boolean isTilemap = newV != null && newV.contains("Tilemap");
            spinWidth.setDisable(isTilemap);
            spinHeight.setDisable(isTilemap);
            if (isTilemap) {
                spinWidth.getValueFactory().setValue(160);
                spinHeight.getValueFactory().setValue(144);
            }
        });

        Slider sliderContrast = new Slider(-50, 50, 0);
        sliderContrast.setPrefWidth(200);
        sliderContrast.setShowTickLabels(true);
        sliderContrast.setShowTickMarks(true);
        sliderContrast.setMajorTickUnit(10);
        sliderContrast.setMinorTickCount(1);
        Label lblContrastValue = new Label("0");
        sliderContrast.valueProperty().addListener((obs, oldV, newV) -> {
            lblContrastValue.setText(String.valueOf(newV.intValue()));
        });
        VBox contrastBox = new VBox(5,
            new HBox(10, sliderContrast, lblContrastValue)
        );

        CheckBox chkInvertColors = new CheckBox(msg(bundle, "tool.pngToGameBoy.invertColors", "Invertir colores"));
        chkInvertColors.setSelected(false);

        CheckBox chkIncludeConst = new CheckBox(msg(bundle, "tool.pngToGameBoy.includeConst", "Incluir const"));
        chkIncludeConst.setSelected(true);

        CheckBox chkIncludeDefines = new CheckBox(msg(bundle, "tool.pngToGameBoy.includeDefines", "Incluir #defines"));
        chkIncludeDefines.setSelected(false);

        VBox optionsBox = new VBox(8);
        optionsBox.setPadding(new Insets(10));
        optionsBox.getChildren().addAll(
            new Label(msg(bundle, "tool.pngToGameBoy.arrayName", "Nombre del array:")),
            txtArrayName,
            new Label(msg(bundle, "tool.pngToGameBoy.exportMode", "Modo de exportación:")),
            comboExportMode,
            new Label(msg(bundle, "tool.pngToGameBoy.width", "Ancho (pixels):")),
            spinWidth,
            new Label(msg(bundle, "tool.pngToGameBoy.height", "Alto (pixels):")),
            spinHeight,
            new Label(msg(bundle, "tool.pngToGameBoy.contrast", "Contraste:")),
            contrastBox,
            chkInvertColors,
            new Separator(),
            chkIncludeConst,
            chkIncludeDefines
        );

        // === CENTER-RIGHT: Preview ===
        Canvas previewCanvas = new Canvas(180, 144);
        previewCanvas.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1;");
        VBox previewBox = new VBox(5,
            new Label(msg(bundle, "tool.pngToGameBoy.preview", "Vista previa:")),
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
        Button btnExport = new Button(msg(bundle, "tool.pngToGameBoy.buttonExport", "Exportar a proyecto"));
        Button btnClipboard = new Button(msg(bundle, "tool.pngToGameBoy.buttonClipboard", "Copiar al portapapeles"));
        Button btnCancel = new Button(msg(bundle, "tool.pngToGameBoy.buttonCancel", "Cancelar"));

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
                             sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode);
            }
        });

        // Update preview when options change
        txtArrayName.textProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));
        comboExportMode.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));
        spinWidth.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));
        spinHeight.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));
        sliderContrast.valueProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));
        chkInvertColors.selectedProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));
        chkIncludeConst.selectedProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));
        chkIncludeDefines.selectedProperty().addListener((obs, oldV, newV) -> 
            updatePreview(previewCanvas, outputArea, txtArrayName, spinWidth, spinHeight, 
                         sliderContrast, chkInvertColors, chkIncludeConst, chkIncludeDefines, comboExportMode));

        btnExport.setOnAction(e -> exportToProject(stage, owner, currentProjectDir, outputArea, onExported));
        btnClipboard.setOnAction(e -> copyToClipboard(outputArea));
        btnCancel.setOnAction(e -> stage.close());

        // === LAYOUT ===
        VBox root = new VBox(10,
            fileBox,
            new Separator(),
            centerBox,
            new Label(msg(bundle, "tool.pngToGameBoy.output", "Código C generado:")),
            outputArea,
            buttonBox
        );
        root.setPadding(new Insets(10));

        stage.setScene(new Scene(root, 900, 700));
        stage.show();
    }

    private void updatePreview(Canvas canvas, TextArea outputArea, TextField txtArrayName, 
                              Spinner<Integer> spinWidth, Spinner<Integer> spinHeight,
                              Slider sliderContrast, CheckBox chkInvertColors,
                              CheckBox chkIncludeConst, CheckBox chkIncludeDefines,
                              ComboBox<String> comboExportMode) {
        if (selectedPng == null) {
            outputArea.setText("Selecciona un PNG primero");
            drawPreviewError(canvas);
            return;
        }

        try {
            boolean isTilemap = comboExportMode.getValue() != null && comboExportMode.getValue().contains("Tilemap");

            if (isTilemap) {
                // Modo Tilemap: convertir a tilemap
                BufferedImage image = ImageIO.read(selectedPng);
                if (image == null) {
                    throw new IOException("No se puede leer el archivo PNG");
                }

                tilemapResult = tilemapConverter.convertImageToTilemap(
                    image,
                    (int) sliderContrast.getValue(),
                    chkInvertColors.isSelected()
                );

                String headerCode = tilemapFormatter.generateHeaderFile(txtArrayName.getText());
                String sourceCode = tilemapFormatter.generateSourceFile(txtArrayName.getText(), tilemapResult);
                
                outputArea.setText(headerCode + "\n// ========== SOURCE FILE ==========\n" + sourceCode);
                drawPreviewFromTilemap(canvas);
            } else {
                // Modo Bitmap plano
                GameBoyBitmapExportOptions opts = new GameBoyBitmapExportOptions(
                    txtArrayName.getText(),
                    spinWidth.getValue(),
                    spinHeight.getValue(),
                    false, // useGameBoyColor
                    chkIncludeConst.isSelected(),
                    chkIncludeDefines.isSelected(),
                    (int) sliderContrast.getValue(),
                    chkInvertColors.isSelected(),
                    "Generado por Samaruc"
                );

                convertedData = bitmapConverter.convertPngTo2bpp(selectedPng, opts);
                String cCode = formatter.formatAsC(convertedData, opts);
                outputArea.setText(cCode);

                drawPreview(canvas, opts);
            }
        } catch (IOException ex) {
            outputArea.setText("ERROR: " + ex.getMessage());
            drawPreviewError(canvas);
        } catch (IllegalArgumentException ex) {
            outputArea.setText("ERROR: " + ex.getMessage());
            drawPreviewError(canvas);
        }
    }

    private void drawPreview(Canvas canvas, GameBoyBitmapExportOptions opts) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (convertedData == null) return;

        double scale = Math.min(
            canvas.getWidth() / opts.width(),
            canvas.getHeight() / opts.height()
        );

        GameBoyColorQuantizer quantizer = new GameBoyColorQuantizer();
        
        // Desempaquetar y visualizar pixels
        int pixelIdx = 0;
        for (int y = 0; y < opts.height() && pixelIdx < convertedData.length; y++) {
            for (int x = 0; x < opts.width(); x += 4) {
                int lowByte = convertedData[pixelIdx] & 0xFF;
                int highByte = convertedData[pixelIdx + 1] & 0xFF;
                pixelIdx += 2;

                for (int bit = 0; bit < 4 && x + bit < opts.width(); bit++) {
                    int bitPos = 7 - bit;
                    int colorIdx = 0;
                    
                    if ((lowByte & (1 << bitPos)) != 0) colorIdx |= 1;
                    if ((highByte & (1 << bitPos)) != 0) colorIdx |= 2;

                    int rgb = quantizer.getRgb(colorIdx);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    
                    gc.setFill(Color.color(r / 255.0, g / 255.0, b / 255.0));
                    gc.fillRect((x + bit) * scale, y * scale, scale, scale);
                }
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

    private void drawPreviewFromTilemap(Canvas canvas) {
        if (tilemapResult == null) {
            drawPreviewError(canvas);
            return;
        }

        // Dibujar tilemap reconstruido
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double scaleX = canvas.getWidth() / (tilemapResult.getMapWidth() * 8.0);
        double scaleY = canvas.getHeight() / (tilemapResult.getMapHeight() * 8.0);

        GameBoyColorQuantizer quantizer = new GameBoyColorQuantizer();
        byte[] mapData = tilemapResult.getMapData();
        byte[] tileData = tilemapResult.getTileData();

        // Procesar cada tile
        for (int tileY = 0; tileY < tilemapResult.getMapHeight(); tileY++) {
            for (int tileX = 0; tileX < tilemapResult.getMapWidth(); tileX++) {
                int mapIdx = tileY * tilemapResult.getMapWidth() + tileX;
                int tileIdx = mapData[mapIdx] & 0xFF;
                int tileDataOffset = tileIdx * 16;

                // Dibujar pixels de este tile
                for (int y = 0; y < 8; y++) {
                    int lowByte = tileData[tileDataOffset + y * 2] & 0xFF;
                    int highByte = tileData[tileDataOffset + y * 2 + 1] & 0xFF;

                    for (int x = 0; x < 8; x++) {
                        int bitPos = 7 - x;
                        int colorIdx = 0;
                        if ((lowByte & (1 << bitPos)) != 0) colorIdx |= 1;
                        if ((highByte & (1 << bitPos)) != 0) colorIdx |= 2;

                        int rgb = quantizer.getRgb(colorIdx);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;

                        gc.setFill(Color.color(r / 255.0, g / 255.0, b / 255.0));
                        gc.fillRect((tileX * 8 + x) * scaleX, (tileY * 8 + y) * scaleY, scaleX, scaleY);
                    }
                }
            }
        }
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

        // Determinar nombre por defecto según modo
        boolean isTilemap = tilemapResult != null;
        chooser.setInitialFileName(isTilemap ? "tilemap.h" : "bitmap.h");

        File selected = chooser.showSaveDialog(owner);
        if (selected == null) return;

        try {
            if (isTilemap) {
                // Exportar tilemap como .h y .c
                String baseName = selected.getName();
                if (baseName.endsWith(".h")) {
                    baseName = baseName.substring(0, baseName.length() - 2);
                } else if (baseName.endsWith(".c")) {
                    baseName = baseName.substring(0, baseName.length() - 2);
                }

                String headerCode = tilemapFormatter.generateHeaderFile(baseName);
                String sourceCode = tilemapFormatter.generateSourceFile(baseName, tilemapResult);

                File headerFile = new File(selected.getParent(), baseName + ".h");
                File sourceFile = new File(selected.getParent(), baseName + ".c");

                Files.write(headerFile.toPath(), headerCode.getBytes(StandardCharsets.UTF_8));
                Files.write(sourceFile.toPath(), sourceCode.getBytes(StandardCharsets.UTF_8));

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.initOwner(owner);
                alert.setTitle("Exportado");
                alert.setContentText("Tilemap exportado a:\n" + headerFile.getName() + "\n" + sourceFile.getName());
                alert.showAndWait();

                onExported.accept(headerFile);
            } else {
                // Exportar bitmap como un único archivo
                Files.write(selected.toPath(), code.getBytes(StandardCharsets.UTF_8));
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.initOwner(owner);
                alert.setTitle("Exportado");
                alert.setContentText("Código exportado a: " + selected.getName());
                alert.showAndWait();

                onExported.accept(selected);
            }

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
