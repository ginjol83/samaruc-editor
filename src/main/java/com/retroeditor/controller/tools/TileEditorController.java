package com.retroeditor.controller.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import javafx.scene.control.ScrollPane;
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
 * Editor de Sprites Multiplataforma (Game Boy / ZX Spectrum / Atari 8-bit) con generación de código C y persistencia.
 */
public class TileEditorController {

    public enum PlatformMode {
        GAMEBOY("Game Boy (2bpp - 4 colores)"),
        ZX_SPECTRUM("ZX Spectrum (1bpp - 16 colores)"),
        ATARI("Atari 8-bit / XE/XL (1bpp/2bpp)");

        private final String label;
        PlatformMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum SpriteSizeMode {
        SIZE_8x8(8, 8, "8x8 (1 tile)"),
        SIZE_8x16(8, 16, "8x16"),
        SIZE_16x8(16, 8, "16x8"),
        SIZE_16x16(16, 16, "16x16 (4 tiles)"),
        SIZE_24x24(24, 24, "24x24"),
        SIZE_32x32(32, 32, "32x32 (16 tiles)");

        private final int width;
        private final int height;
        private final String label;

        SpriteSizeMode(int width, int height, String label) {
            this.width = width;
            this.height = height;
            this.label = label;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        @Override public String toString() { return label; }
    }

    public static class TileProjectData {
        private String platform;
        private int width;
        private int height;
        private int[][] pixels;
        private String symbol;

        public TileProjectData() {}

        public TileProjectData(String platform, int width, int height, int[][] pixels, String symbol) {
            this.platform = platform;
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.symbol = symbol;
        }

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        public int[][] getPixels() { return pixels; }
        public void setPixels(int[][] pixels) { this.pixels = pixels; }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
    }

    private static final int CELL_SIZE = 24;
    private static File lastUsedDirectory = null;

    private PlatformMode currentPlatform = PlatformMode.GAMEBOY;
    private int currentWidth = 8;
    private int currentHeight = 8;
    private int[][] pixels = new int[8][8];
    private final GameBoyTileCodeGenerator codeGenerator = new GameBoyTileCodeGenerator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Color[] gbPalette = new Color[] {
        Color.web("#f8f8f8"),
        Color.web("#a8a8a8"),
        Color.web("#585858"),
        Color.web("#101010")
    };

    private final Color[] zxPalette = new Color[] {
        Color.BLACK, Color.BLUE, Color.RED, Color.MAGENTA,
        Color.GREEN, Color.CYAN, Color.YELLOW, Color.WHITE,
        Color.DARKGRAY, Color.LIGHTBLUE, Color.LIGHTCORAL, Color.VIOLET,
        Color.LIGHTGREEN, Color.LIGHTCYAN, Color.LIGHTYELLOW, Color.WHITE
    };

    private final Color[] atariPalette = new Color[] {
        Color.BLACK, Color.web("#3b3b3b"), Color.web("#757575"), Color.web("#adadad"),
        Color.web("#e0e0e0"), Color.WHITE, Color.web("#ff5555"), Color.web("#ffaa55"),
        Color.web("#ffff55"), Color.web("#55ff55"), Color.web("#55ffff"), Color.web("#5555ff"),
        Color.web("#ff55ff"), Color.web("#8b0000"), Color.web("#00008b"), Color.web("#006400")
    };

    public void open(Stage owner, File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Editor de Sprites Multiplataforma (Game Boy / ZX Spectrum / Atari)");

        ChoiceBox<PlatformMode> platformChoice = new ChoiceBox<>();
        platformChoice.getItems().addAll(PlatformMode.values());
        platformChoice.setValue(PlatformMode.GAMEBOY);

        ChoiceBox<SpriteSizeMode> sizeChoice = new ChoiceBox<>();
        sizeChoice.getItems().addAll(SpriteSizeMode.values());
        sizeChoice.setValue(SpriteSizeMode.SIZE_8x8);

        Canvas canvas = new Canvas(currentWidth * CELL_SIZE, currentHeight * CELL_SIZE);
        TextField symbolField = new TextField("sprite_data");
        TextArea outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(false);
        outputArea.setPrefRowCount(10);

        CheckBox chkConst = new CheckBox("Incluir const");
        chkConst.setSelected(true);
        CheckBox chkDefines = new CheckBox("Incluir #defines");
        chkDefines.setSelected(false);

        ChoiceBox<Integer> colorChoice = new ChoiceBox<>();
        updateColorChoice(colorChoice, currentPlatform);

        Runnable updateAction = () -> updateCode(outputArea, symbolField.getText(), chkConst.isSelected(), chkDefines.isSelected());

        platformChoice.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                currentPlatform = newV;
                updateColorChoice(colorChoice, currentPlatform);
                redrawCanvas(canvas);
                updateAction.run();
            }
        });

        sizeChoice.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                resizePixels(newV.getWidth(), newV.getHeight());
                canvas.setWidth(newV.getWidth() * CELL_SIZE);
                canvas.setHeight(newV.getHeight() * CELL_SIZE);
                redrawCanvas(canvas);
                updateAction.run();
            }
        });

        canvas.setOnMousePressed(evt -> {
            int x = (int) (evt.getX() / CELL_SIZE);
            int y = (int) (evt.getY() / CELL_SIZE);
            if (x < 0 || y < 0 || x >= currentWidth || y >= currentHeight) return;

            if (evt.getButton() == MouseButton.SECONDARY) {
                pixels[y][x] = 0;
            } else {
                pixels[y][x] = colorChoice.getValue() != null ? colorChoice.getValue() : 1;
            }
            redrawCanvas(canvas);
            updateAction.run();
        });

        canvas.setOnMouseDragged(evt -> {
            if (!evt.isPrimaryButtonDown()) return;
            int x = (int) (evt.getX() / CELL_SIZE);
            int y = (int) (evt.getY() / CELL_SIZE);
            if (x < 0 || y < 0 || x >= currentWidth || y >= currentHeight) return;
            pixels[y][x] = colorChoice.getValue() != null ? colorChoice.getValue() : 1;
            redrawCanvas(canvas);
            updateAction.run();
        });

        symbolField.textProperty().addListener((obs, oldV, newV) -> updateAction.run());
        chkConst.selectedProperty().addListener((obs, oldV, newV) -> updateAction.run());
        chkDefines.selectedProperty().addListener((obs, oldV, newV) -> updateAction.run());

        Button clearButton = new Button("Limpiar");
        clearButton.setOnAction(e -> {
            clearPixels();
            redrawCanvas(canvas);
            updateAction.run();
        });

        Button saveButton = new Button("💾 Guardar Sprite...");
        saveButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar proyecto de Sprite");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sprite Project (*.tile, *.json)", "*.tile", "*.json"));
            
            File initialDir = currentProjectDir;
            if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = lastUsedDirectory;
            }
            if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = new File(System.getProperty("user.home"));
            }
            try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}

            chooser.setInitialFileName((symbolField.getText().isBlank() ? "sprite" : symbolField.getText()) + ".tile");
            File file = chooser.showSaveDialog(stage);
            if (file != null) {
                try {
                    TileProjectData data = new TileProjectData(currentPlatform.name(), currentWidth, currentHeight, pixels, symbolField.getText());
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
                    lastUsedDirectory = file.getParentFile();
                    showAlert("Guardado exitoso", "Proyecto de sprite guardado correctamente.", Alert.AlertType.INFORMATION, stage);
                } catch (Exception ex) {
                    showAlert("Error al guardar", "No se pudo guardar: " + ex.getMessage(), Alert.AlertType.ERROR, stage);
                }
            }
        });

        Button openButton = new Button("📂 Abrir Sprite...");
        openButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Abrir proyecto de Sprite");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sprite Project (*.tile, *.json)", "*.tile", "*.json"));
            
            File initialDir = currentProjectDir;
            if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = lastUsedDirectory;
            }
            if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = new File(System.getProperty("user.home"));
            }
            try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}

            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                try {
                    TileProjectData data = objectMapper.readValue(file, TileProjectData.class);
                    if (data != null && data.getPixels() != null) {
                        if (data.getPlatform() != null) {
                            try {
                                currentPlatform = PlatformMode.valueOf(data.getPlatform());
                                platformChoice.setValue(currentPlatform);
                            } catch (Exception ignored) {}
                        }
                        currentWidth = data.getWidth();
                        currentHeight = data.getHeight();
                        pixels = data.getPixels();
                        if (data.getSymbol() != null) symbolField.setText(data.getSymbol());

                        for (SpriteSizeMode mode : SpriteSizeMode.values()) {
                            if (mode.getWidth() == currentWidth && mode.getHeight() == currentHeight) {
                                sizeChoice.setValue(mode);
                                break;
                            }
                        }

                        lastUsedDirectory = file.getParentFile();
                        canvas.setWidth(currentWidth * CELL_SIZE);
                        canvas.setHeight(currentHeight * CELL_SIZE);
                        updateColorChoice(colorChoice, currentPlatform);
                        redrawCanvas(canvas);
                        updateAction.run();
                    }
                } catch (Exception ex) {
                    showAlert("Error al abrir", "No se pudo leer el archivo: " + ex.getMessage(), Alert.AlertType.ERROR, stage);
                }
            }
        });

        Button copyButton = new Button("Copiar código");
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(outputArea.getText() != null ? outputArea.getText() : "");
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button exportButton = new Button("Exportar al proyecto");
        exportButton.setOnAction(e -> exportCodeToProject(stage, currentProjectDir, outputArea.getText(), symbolField.getText(), bundle, onExported));

        HBox editorTop = new HBox(8,
            new Label("Plataforma:"), platformChoice,
            new Label("Tamaño:"), sizeChoice,
            new Label("Array:"), symbolField,
            new Label("Color:"), colorChoice,
            chkConst, chkDefines
        );
        editorTop.setAlignment(Pos.CENTER_LEFT);

        HBox buttons = new HBox(8, saveButton, openButton, clearButton, copyButton, exportButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(10, editorTop, canvas, buttons, outputArea);
        center.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(center);
        scrollPane.setFitToWidth(true);

        BorderPane root = new BorderPane(scrollPane);

        redrawCanvas(canvas);
        updateAction.run();

        stage.setScene(new Scene(root, 860, 720));
        stage.show();
    }

    private void updateColorChoice(ChoiceBox<Integer> colorChoice, PlatformMode platform) {
        colorChoice.getItems().clear();
        if (platform == PlatformMode.GAMEBOY) {
            colorChoice.getItems().addAll(0, 1, 2, 3);
            colorChoice.setValue(3);
        } else if (platform == PlatformMode.ZX_SPECTRUM) {
            for (int i = 0; i < 16; i++) colorChoice.getItems().add(i);
            colorChoice.setValue(7); // White
        } else {
            for (int i = 0; i < 16; i++) colorChoice.getItems().add(i);
            colorChoice.setValue(6); // Red/Bright
        }
    }

    private void resizePixels(int newWidth, int newHeight) {
        int[][] newPixels = new int[newHeight][newWidth];
        for (int y = 0; y < Math.min(currentHeight, newHeight); y++) {
            for (int x = 0; x < Math.min(currentWidth, newWidth); x++) {
                newPixels[y][x] = pixels[y][x];
            }
        }
        currentWidth = newWidth;
        currentHeight = newHeight;
        pixels = newPixels;
    }

    private void clearPixels() {
        for (int y = 0; y < currentHeight; y++) {
            for (int x = 0; x < currentWidth; x++) {
                pixels[y][x] = 0;
            }
        }
    }

    private void redrawCanvas(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int y = 0; y < currentHeight; y++) {
            for (int x = 0; x < currentWidth; x++) {
                int colorIndex = pixels[y][x];
                Color col;
                if (currentPlatform == PlatformMode.GAMEBOY) {
                    if (colorIndex < 0 || colorIndex > 3) colorIndex = 0;
                    col = gbPalette[colorIndex];
                } else if (currentPlatform == PlatformMode.ZX_SPECTRUM) {
                    if (colorIndex < 0 || colorIndex >= zxPalette.length) colorIndex = 0;
                    col = zxPalette[colorIndex];
                } else {
                    if (colorIndex < 0 || colorIndex >= atariPalette.length) colorIndex = 0;
                    col = atariPalette[colorIndex];
                }
                gc.setFill(col);
                gc.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                gc.setStroke(Color.web("#404040"));
                gc.strokeRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }

        // Draw 8x8 tile boundary lines
        gc.setStroke(Color.web("#202020"));
        gc.setLineWidth(2.0);
        for (int ty = 0; ty <= currentHeight / 8; ty++) {
            gc.strokeLine(0, ty * 8 * CELL_SIZE, currentWidth * CELL_SIZE, ty * 8 * CELL_SIZE);
        }
        for (int tx = 0; tx <= currentWidth / 8; tx++) {
            gc.strokeLine(tx * 8 * CELL_SIZE, 0, tx * 8 * CELL_SIZE, currentHeight * CELL_SIZE);
        }
        gc.setLineWidth(1.0);
    }

    private void updateCode(TextArea outputArea, String symbolName, boolean includeConst, boolean includeDefines) {
        if (currentPlatform == PlatformMode.GAMEBOY) {
            outputArea.setText(codeGenerator.generateTileCode(pixels, symbolName, includeConst, includeDefines));
        } else if (currentPlatform == PlatformMode.ZX_SPECTRUM) {
            outputArea.setText(generateZxSpectrumTileCode(pixels, symbolName, includeConst));
        } else {
            outputArea.setText(generateAtariTileCode(pixels, symbolName, includeConst));
        }
    }

    private String generateZxSpectrumTileCode(int[][] px, String symbol, boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        String safeSymbol = (symbol == null || symbol.isBlank()) ? "zx_tile" : symbol.replaceAll("[^A-Za-z0-9_]", "_");
        sb.append("// Generated by Samaruc - ZX Spectrum Tile (1bpp)\n");
        if (includeConst) sb.append("const ");
        sb.append("unsigned char ").append(safeSymbol).append("[] = {\n");

        int h = px.length;
        int w = px[0].length;

        for (int ty = 0; ty < h; ty += 8) {
            for (int tx = 0; tx < w; tx += 8) {
                sb.append("    // Tile at (").append(tx/8).append(", ").append(ty/8).append(")\n");
                for (int row = 0; row < 8; row++) {
                    int b = 0;
                    for (int col = 0; col < 8; col++) {
                        int pVal = px[ty + row][tx + col];
                        if (pVal > 0) b |= (1 << (7 - col));
                    }
                    sb.append(String.format("    0x%02X,\n", b));
                }
            }
        }
        sb.append("};\n");
        return sb.toString();
    }

    private String generateAtariTileCode(int[][] px, String symbol, boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        String safeSymbol = (symbol == null || symbol.isBlank()) ? "atari_sprite" : symbol.replaceAll("[^A-Za-z0-9_]", "_");
        sb.append("// Generated by Samaruc - Atari 8-bit Sprite (cc65 / ANTIC)\n");
        if (includeConst) sb.append("const ");
        sb.append("unsigned char ").append(safeSymbol).append("[] = {\n");

        int h = px.length;
        int w = px[0].length;

        for (int row = 0; row < h; row++) {
            sb.append("    ");
            for (int byteCol = 0; byteCol < w; byteCol += 8) {
                int b = 0;
                for (int col = 0; col < 8 && (byteCol + col) < w; col++) {
                    int pVal = px[row][byteCol + col];
                    if (pVal > 0) b |= (1 << (7 - col));
                }
                sb.append(String.format("0x%02X%s", b, (byteCol + 8 < w ? ", " : "")));
            }
            sb.append(",\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    private void exportCodeToProject(Stage owner, File currentProjectDir, String code, String symbolName, ResourceBundle bundle, Consumer<File> onExported) {
        File initialDir = currentProjectDir;
        if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
            initialDir = lastUsedDirectory;
        }
        if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
            initialDir = new File(System.getProperty("user.home"));
        }

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C / Header", "*.c", "*.h"));
        try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}
        chooser.setInitialFileName(suggestFileName(symbolName));

        File selected = chooser.showSaveDialog(owner);
        if (selected == null) return;

        try {
            Files.writeString(selected.toPath(), code != null ? code : "", StandardCharsets.UTF_8);
            lastUsedDirectory = selected.getParentFile();
            if (onExported != null) onExported.accept(selected);
        } catch (Exception ex) {
            showAlert("Error de exportación", "No se pudo exportar: " + ex.getMessage(), Alert.AlertType.ERROR, owner);
        }
    }

    private String suggestFileName(String symbolName) {
        String raw = symbolName != null ? symbolName.trim() : "";
        if (raw.isEmpty()) return "sprite_data.c";
        return raw + ".c";
    }

    private void showAlert(String title, String content, Alert.AlertType type, Stage owner) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String msg(ResourceBundle bundle, String key, String fallback) {
        try {
            if (bundle != null && bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}
