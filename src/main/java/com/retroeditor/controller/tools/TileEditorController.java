package com.retroeditor.controller.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retroeditor.model.SpriteFrame;
import com.retroeditor.service.gameboy.GameBoyColorQuantizer;
import com.retroeditor.service.sprite.SpriteAnimationCodeGenerator;
import com.retroeditor.service.zxspectrum.ColorQuantizer;
import com.retroeditor.service.zxspectrum.ZxSpectrumColor;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Editor de Sprites Multiplataforma (Game Boy / ZX Spectrum / Atari 8-bit /
 * Commodore 64 / Amstrad CPC / Sega Master System / Game Gear) con generación
 * de código C y persistencia.
 */
public class TileEditorController {

    public enum PlatformMode {
        GAMEBOY("Game Boy (2bpp - 4 colores)"),
        ZX_SPECTRUM("ZX Spectrum (1bpp - 16 colores)"),
        ATARI("Atari 8-bit / XE/XL (1bpp/2bpp)"),
        C64("Commodore 64 (1bpp + color RAM - 16 colores)"),
        CPC("Amstrad CPC (modo 0 - 4 colores)"),
        MASTER_SYSTEM("Sega Master System (2bpp - 4 colores)"),
        GAME_GEAR("Game Gear (4bpp - 16 colores)");

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
        SIZE_32x32(32, 32, "32x32 (16 tiles)"),
        CUSTOM(0, 0, "Personalizado (bloques 8x8)...");

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
        public boolean isCustom() { return width == 0 && height == 0; }
        @Override public String toString() { return label; }
    }

    public static class TileProjectData {
        private String platform;
        private int width;
        private int height;
        private int[][] pixels;
        private String symbol;
        private List<SpriteFrame> frames;

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
        public List<SpriteFrame> getFrames() { return frames; }
        public void setFrames(List<SpriteFrame> frames) { this.frames = frames; }
    }

    private static final int DEFAULT_CELL_SIZE = 24;
    private double cellSize = DEFAULT_CELL_SIZE;
    private static File lastUsedDirectory = null;

    private File currentFile;
    private boolean dirty;
    private Runnable stateListener;
    private TextField symbolField;
    private ChoiceBox<PlatformMode> platformChoice;
    private ChoiceBox<SpriteSizeMode> sizeChoice;
    private Spinner<Integer> customWSpinner;
    private Spinner<Integer> customHSpinner;
    private Canvas canvas;
    private Label frameLabel;
    private Spinner<Integer> durationSpinner;
    private Button playButton;
    private HBox colorStrip;
    private ToggleGroup colorGroup;
    private int selectedColor = 3;
    private ToggleButton eraseButton;
    private boolean eraseActive;
    private Button undoButton;
    private Button redoButton;
    private Spinner<Integer> brushSizeSpinner;
    private Label zxClashLabel;
    private Runnable updateAction;

    private PlatformMode currentPlatform = PlatformMode.GAMEBOY;
    private final List<SpriteFrame> frames = new ArrayList<>();
    private int currentFrameIndex = 0;
    private int currentWidth = 8;
    private int currentHeight = 8;
    private int[][] pixels;
    private int[][] lastPixels;
    private int lastWidth;
    private int lastHeight;
    private Button autoFitButton;
    private AnimationTimer previewTimer;
    private boolean previewActive;
    private int[][] autoFitUndoPixels;
    private int autoFitUndoWidth;
    private int autoFitUndoHeight;
    private boolean autoFitApplied;
    private Node rootNode;
    private static final int MAX_UNDO_STATES = 50;
    private final ArrayDeque<EditorSnapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<EditorSnapshot> redoStack = new ArrayDeque<>();
    private boolean strokeUndoPushed;

    private ResourceBundle bundle;
    private Label durationInfoLabel;
    private Label mouseInfoLabel;
    private ScrollPane contentScrollPane;
    private CheckBox showCodeToggle;
    private CheckBox showGridToggle;
    private int hoverCol = -1;
    private int hoverRow = -1;
    private int cursorCol = 0;
    private int cursorRow = 0;
    private boolean showGrid = true;
    private ToggleButton fillButton;
    private boolean fillActive;
    private ToggleButton lineButton;
    private ToggleButton rectButton;
    private boolean lineActive;
    private boolean rectActive;
    private boolean symBrush;
    private int[][] strokeStartPixels;
    private int strokeStartCol;
    private int strokeStartRow;
    private boolean strokeErase;
    private final Map<String, Color> paletteOverrides = new HashMap<>();
    private HBox filmstripBox;
    private final List<Canvas> thumbCanvases = new ArrayList<>();
    private Canvas previewCanvas;
    private Integer filmstripDragIndex = null;
    private double filmstripDragStartSceneX;
    private final Preferences prefs = Preferences.userNodeForPackage(TileEditorController.class);
    private static final double THUMB_DISPLAY_HEIGHT = 44;

    private record EditorSnapshot(List<SpriteFrame> frames, int frameIndex) {}
    private final SpriteAnimationCodeGenerator animCodeGenerator = new SpriteAnimationCodeGenerator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TileEditorController() {
        frames.add(new SpriteFrame(new int[8][8], 4));
        pixels = frames.get(0).getPixels();
    }

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

    private final Color[] c64Palette = new Color[] {
        Color.web("#000000"), Color.WHITE, Color.web("#68372b"), Color.web("#70a4b2"),
        Color.web("#6f3d86"), Color.web("#588d43"), Color.web("#352879"), Color.web("#b8c76f"),
        Color.web("#6f4f25"), Color.web("#433900"), Color.web("#9a6759"), Color.web("#444444"),
        Color.web("#6c6c6c"), Color.web("#9ad284"), Color.web("#6c5eb5"), Color.web("#959595")
    };

    private final Color[] cpcPalette = new Color[] {
        Color.BLACK, Color.web("#aa0000"), Color.web("#00aa00"), Color.web("#0000aa")
    };

    private final Color[] smsPalette = new Color[] {
        Color.BLACK, Color.web("#20f0f0"), Color.web("#f02020"), Color.WHITE
    };

    private final Color[] ggPalette = new Color[] {
        Color.BLACK, Color.web("#444444"), Color.web("#888888"), Color.WHITE,
        Color.web("#cc0000"), Color.web("#ff4444"), Color.web("#ff8800"), Color.web("#ffcc00"),
        Color.web("#00cc00"), Color.web("#44ff44"), Color.web("#00cccc"), Color.web("#44ffff"),
        Color.web("#0000cc"), Color.web("#4444ff"), Color.web("#cc00cc"), Color.web("#ff44ff")
    };

    public Node createNode(File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        return buildUi(currentProjectDir, bundle, onExported);
    }

    /**
     * Notifica al oyente externo (p. ej. MainController) cuando cambia el
     * archivo asociado o el estado de guardado de la pestaña.
     */
    public void setStateListener(Runnable listener) {
        this.stateListener = listener;
    }

    /**
     * Archivo asociado al proyecto actual, o {@code null} si aún no se ha guardado.
     */
    public File getCurrentFile() {
        return currentFile;
    }

    /**
     * {@code true} si el archivo es un proyecto de sprite de Samaruc (.tile)
     * que debe abrirse en esta herramienta.
     */
    public static boolean isTileFile(File file) {
        return file != null && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".tile");
    }

    /**
     * {@code true} si hay cambios sin guardar desde el último open/save.
     */
    public boolean hasUnsavedChanges() {
        return dirty;
    }

    /**
     * Guarda el proyecto en su archivo actual; si no tiene archivo, abre el
     * diálogo "Guardar como". Devuelve {@code true} si se guardó.
     */
    public boolean saveCurrentProject() {
        if (currentFile == null) {
            return saveProjectAs();
        }
        return saveProjectToFile(currentFile);
    }

    /**
     * Abre el diálogo "Guardar como" y guarda el proyecto. Devuelve {@code true}
     * si se guardó.
     */
    public boolean saveProjectAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(msg(bundle, "tool.tileEditor.saveDialogTitle", "Guardar proyecto de Sprite"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(msg(bundle, "tool.tileEditor.tileFilter", "Sprite Project (*.tile, *.json)"), "*.tile", "*.json"));
        
        File initialDir = lastUsedDirectory;
        if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
            initialDir = new File(System.getProperty("user.home"));
        }
        try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}

        String symbolName = symbolField != null ? symbolField.getText() : "";
        chooser.setInitialFileName((symbolName.isBlank() ? "sprite" : symbolName) + ".tile");
        File file = chooser.showSaveDialog(currentWindow());
        if (file == null) return false;
        return saveProjectToFile(file);
    }

    private boolean saveProjectToFile(File file) {
        try {
            String symbolName = symbolField != null ? symbolField.getText() : "sprite_data";
            TileProjectData data = new TileProjectData(currentPlatform.name(), currentWidth, currentHeight, pixels, symbolName);
            List<SpriteFrame> framesCopy = new ArrayList<>();
            for (SpriteFrame f : frames) {
                framesCopy.add(new SpriteFrame(deepCopyPixels(f.getPixels()), f.getDurationVbls()));
            }
            data.setFrames(framesCopy);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            lastUsedDirectory = file.getParentFile();
            currentFile = file;
            dirty = false;
            notifyStateChanged();
            showAlert(msg(bundle, "tool.tileEditor.savedTitle", "Guardado exitoso"),
                    msg(bundle, "tool.tileEditor.savedBody", "Proyecto de sprite guardado correctamente."),
                    Alert.AlertType.INFORMATION, currentWindow());
            return true;
        } catch (Exception ex) {
            showAlert(msg(bundle, "tool.tileEditor.saveErrorTitle", "Error al guardar"),
                    msg(bundle, "tool.tileEditor.saveErrorBody", "No se pudo guardar: ") + ex.getMessage(),
                    Alert.AlertType.ERROR, currentWindow());
            return false;
        }
    }

    private void markDirty() {
        if (dirty) return;
        dirty = true;
        notifyStateChanged();
    }

    /**
     * Carga un proyecto de sprite desde disco y actualiza toda la interfaz.
     * Devuelve {@code true} si se cargó correctamente.
     */
    public boolean openProject(File file) {
        if (file == null || !file.isFile()) return false;
        try {
            TileProjectData data = objectMapper.readValue(file, TileProjectData.class);
            if (data == null) return false;
            applyLoadedProject(data, file);
            return true;
        } catch (Exception ex) {
            showAlert(msg(bundle, "tool.tileEditor.openErrorTitle", "Error al abrir"),
                    msg(bundle, "tool.tileEditor.openErrorBody", "No se pudo leer el archivo: ") + ex.getMessage(),
                    Alert.AlertType.ERROR, currentWindow());
            return false;
        }
    }

    private void applyLoadedProject(TileProjectData data, File file) {
        if (data.getPlatform() != null) {
            try {
                currentPlatform = PlatformMode.valueOf(data.getPlatform());
                platformChoice.setValue(currentPlatform);
            } catch (Exception ignored) {}
        }
        List<SpriteFrame> loaded = data.getFrames();
        frames.clear();
        if (loaded != null) {
            for (SpriteFrame f : loaded) {
                if (f.getPixels() != null) {
                    frames.add(new SpriteFrame(deepCopyPixels(f.getPixels()), Math.max(1, f.getDurationVbls())));
                }
            }
        }
        if (frames.isEmpty() && data.getPixels() != null) {
            frames.add(new SpriteFrame(deepCopyPixels(data.getPixels()), 4));
        }
        if (frames.isEmpty()) {
            throw new IllegalArgumentException(msg(bundle, "tool.tileEditor.noFrames", "El archivo no contiene ningún frame."));
        }
        currentFrameIndex = 0;
        pixels = frames.get(0).getPixels();
        currentWidth = pixels[0].length;
        currentHeight = pixels.length;
        cancelAutoFitUndo();
        lastPixels = null;
        lastWidth = 0;
        lastHeight = 0;
        if (autoFitButton != null) autoFitButton.setDisable(true);
        if (previewTimer != null) previewTimer.stop();
        previewActive = false;
        playButton.setText(msg(bundle, "tool.tileEditor.play", "▶ Anim"));
        frameLabel.setText(fmt(msg(bundle, "tool.tileEditor.frameFormat", "Frame {0}/{1}"),
                currentFrameIndex + 1, frames.size()));
        durationSpinner.getValueFactory().setValue(frames.get(0).getDurationVbls());
        if (data.getSymbol() != null && !data.getSymbol().isBlank()) symbolField.setText(data.getSymbol());

        SpriteSizeMode matched = null;
        for (SpriteSizeMode mode : SpriteSizeMode.values()) {
            if (!mode.isCustom() && mode.getWidth() == currentWidth && mode.getHeight() == currentHeight) {
                matched = mode;
                break;
            }
        }
        if (matched != null) {
            sizeChoice.setValue(matched);
        } else if (currentWidth % 8 == 0 && currentHeight % 8 == 0) {
            customWSpinner.getValueFactory().setValue(currentWidth / 8);
            customHSpinner.getValueFactory().setValue(currentHeight / 8);
            sizeChoice.setValue(SpriteSizeMode.CUSTOM);
        }

        lastUsedDirectory = file.getParentFile();
        currentFile = file;
        dirty = false;
        notifyStateChanged();
        canvas.setWidth(currentWidth * cellSize);
        canvas.setHeight(currentHeight * cellSize);
        undoStack.clear();
        redoStack.clear();
        updateUndoButtons();
        rebuildColorStrip();
        redrawCanvas(canvas);
        updateDurationInfo();
        updateFilmstrip();
        updateAction.run();
    }

    private void notifyStateChanged() {
        if (stateListener != null) {
            stateListener.run();
        }
    }

    private Node buildUi(File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        this.bundle = bundle;
        loadPaletteOverrides();
        platformChoice = new ChoiceBox<>();
        platformChoice.getItems().addAll(PlatformMode.values());
        platformChoice.setValue(PlatformMode.GAMEBOY);

        sizeChoice = new ChoiceBox<>();
        sizeChoice.getItems().addAll(SpriteSizeMode.values());
        sizeChoice.setValue(SpriteSizeMode.SIZE_8x8);

        customWSpinner = new Spinner<>(1, 16, 1);
        customWSpinner.setPrefWidth(70);
        Tooltip.install(customWSpinner, new Tooltip(msg(bundle, "tool.tileEditor.customWidthTip", "Ancho en bloques de 8x8")));
        customHSpinner = new Spinner<>(1, 16, 1);
        customHSpinner.setPrefWidth(70);
        Tooltip.install(customHSpinner, new Tooltip(msg(bundle, "tool.tileEditor.customHeightTip", "Alto en bloques de 8x8")));
        Button applyCustomButton = new Button(msg(bundle, "tool.tileEditor.apply", "Aplicar"));
        applyCustomButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.applyTip", "Redimensiona el sprite al tamaño de bloques indicado. No se aplica nada hasta pulsar este botón.")));
        HBox customSizeBox = new HBox(6,
            new Label(msg(bundle, "tool.tileEditor.customWidth", "Ancho:")), customWSpinner,
            new Label(msg(bundle, "tool.tileEditor.customHeight", "Alto:")), customHSpinner);
        customSizeBox.setAlignment(Pos.CENTER_LEFT);
        customSizeBox.setVisible(false);
        customSizeBox.setManaged(false);
        Label customHint = new Label(msg(bundle, "tool.tileEditor.customHint", "(bloques de 8x8 px)"));
        customHint.setStyle("-fx-text-fill: gray;");
        customSizeBox.getChildren().addAll(customHint, applyCustomButton);
        Tooltip.install(customSizeBox, new Tooltip(msg(bundle, "tool.tileEditor.customSizeTip", "Combina bloques de 8x8 píxeles: ej. ancho 2 x alto 3 = 16x24 px. Debe pulsar Aplicar para redimensionar.")));

        frameLabel = new Label(msg(bundle, "tool.tileEditor.frameFormat", "Frame {0}/{1}").replace("{0}", "1").replace("{1}", "1"));
        Button prevFrameButton = new Button("◀");
        prevFrameButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.prevFrame", "Frame anterior")));
        Button nextFrameButton = new Button("▶");
        nextFrameButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.nextFrame", "Frame siguiente")));
        Button addFrameButton = new Button(msg(bundle, "tool.tileEditor.addFrame", "+ Añadir frame"));
        addFrameButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.addFrameTip", "Duplica el frame actual como plantilla y lo inserta después")));
        Button deleteFrameButton = new Button(msg(bundle, "tool.tileEditor.deleteFrame", "🗑 Borrar frame"));
        deleteFrameButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.deleteFrameTip", "Elimina el frame actual (se mantiene un mínimo de 1 frame)")));
        durationSpinner = new Spinner<>(1, 255, 4);
        durationSpinner.setPrefWidth(70);
        Tooltip.install(durationSpinner, new Tooltip(msg(bundle, "tool.tileEditor.durationTip",
                "Duración del frame actual en VBLs (50/60 Hz según la plataforma). Se muestra también el equivalente en milisegundos.")));
        durationInfoLabel = new Label();
        durationInfoLabel.setStyle("-fx-text-fill: gray;");
        playButton = new Button(msg(bundle, "tool.tileEditor.play", "▶ Anim"));
        playButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.playTip", "Reproduce la animación en el lienzo con las duraciones por frame")));

        HBox animBar = new HBox(8,
            new Label(msg(bundle, "tool.tileEditor.animation", "Animación:")), prevFrameButton, frameLabel, nextFrameButton,
            addFrameButton, deleteFrameButton,
            new Separator(),
            new Label(msg(bundle, "tool.tileEditor.durationVbls", "Dur.(VBLs):")), durationSpinner, durationInfoLabel,
            new Separator(),
            playButton);
        animBar.setAlignment(Pos.CENTER_LEFT);

        canvas = new Canvas(currentWidth * cellSize, currentHeight * cellSize);

        previewCanvas = new Canvas(currentWidth, currentHeight);
        redrawPreview();
        Label previewLabel = new Label(msg(bundle, "tool.tileEditor.preview", "Preview 1:1"));
        previewLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        VBox previewPane = new VBox(4, previewLabel, previewCanvas);
        previewPane.setAlignment(Pos.TOP_LEFT);
        HBox canvasRow = new HBox(12, canvas, previewPane);
        canvasRow.setAlignment(Pos.TOP_LEFT);

        filmstripBox = new HBox(6);
        filmstripBox.setAlignment(Pos.CENTER_LEFT);
        Label filmstripLabel = new Label(msg(bundle, "tool.tileEditor.filmstrip", "Frames:"));
        filmstripLabel.setStyle("-fx-font-weight: bold;");
        Label filmstripHint = new Label(msg(bundle, "tool.tileEditor.filmstripHint",
                "clic: seleccionar · arrastrar: reordenar · [ / ]: navegar"));
        filmstripHint.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        VBox filmstripPane = new VBox(4, new HBox(8, filmstripLabel, filmstripHint), filmstripBox);
        updateFilmstrip();

        Label zoomLabel = new Label("100%");
        Button zoomOutButton = new Button("-");
        zoomOutButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.zoomOutTip", "Reducir zoom (o tecla -)")));
        Button zoomInButton = new Button("+");
        zoomInButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.zoomInTip", "Ampliar zoom (o tecla +)")));
        Button zoomResetButton = new Button("100%");
        zoomResetButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.zoomResetTip", "Zoom 100% (o tecla 0)")));
        Button zoomFitButton = new Button("⤢ Ajustar");
        zoomFitButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.zoomFitTip", "Ajusta el sprite a la ventana (o Ctrl+0)")));
        zoomOutButton.setOnAction(e -> zoomByFactor(zoomLabel, 1.0 / 1.25));
        zoomInButton.setOnAction(e -> zoomByFactor(zoomLabel, 1.25));
        zoomResetButton.setOnAction(e -> zoomTo(zoomLabel, DEFAULT_CELL_SIZE));
        zoomFitButton.setOnAction(e -> zoomFit(zoomLabel));
        mouseInfoLabel = new Label();
        mouseInfoLabel.setStyle("-fx-text-fill: gray;");
        symbolField = new TextField("sprite_data");
        TextArea outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(false);
        outputArea.setPrefRowCount(10);

        CheckBox chkConst = new CheckBox(msg(bundle, "tool.tileEditor.includeConst", "Incluir const"));
        chkConst.setSelected(true);
        CheckBox chkDefines = new CheckBox(msg(bundle, "tool.tileEditor.includeDefines", "Incluir #defines"));
        chkDefines.setSelected(true);
        CheckBox chk8x16 = new CheckBox(msg(bundle, "tool.tileEditor.pairs8x16", "Tiles 8x16 por columna"));
        chk8x16.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.pairs8x16Tip", "Game Boy: ordena los tiles en pares contiguos (top, bottom) por columna, listos para sprites 8x16")));
        chk8x16.setSelected(false);
        chk8x16.setDisable(currentPlatform != PlatformMode.GAMEBOY);

        colorStrip = new HBox(4);
        colorStrip.setAlignment(Pos.CENTER_LEFT);
        rebuildColorStrip();
        Tooltip.install(colorStrip, new Tooltip(msg(bundle, "tool.tileEditor.colorStripTip",
                "Elige el color de pincel. En ZX Spectrum (y C64) cada bloque 8x8 usa un único "
                + "color: si pintas sobre un bloque con otro color, todo el bloque se recolorea. "
                + "El color 0 es PAPER/transparente/fondo.")));

        zxClashLabel = new Label();
        zxClashLabel.setStyle("-fx-text-fill: #b00020; -fx-font-weight: bold;");
        updateZxWarning();

        updateAction = () -> updateCode(outputArea, symbolField.getText(),
                new SpriteAnimationCodeGenerator.Options(chkConst.isSelected(), chkDefines.isSelected(),
                        chk8x16.isSelected() && currentPlatform == PlatformMode.GAMEBOY));

        Runnable applyCustomSize = () -> {
            snapshotPixels();
            pushUndoState();
            int w = customWSpinner.getValue() * 8;
            int h = customHSpinner.getValue() * 8;
            canvas.setWidth(w * cellSize);
            canvas.setHeight(h * cellSize);
            resizePixels(w, h);
            redrawCanvas(canvas);
            updateAction.run();
            markDirty();
        };

        Runnable refreshCustomControls = () -> {
            customWSpinner.getValueFactory().setValue(currentWidth / 8);
            customHSpinner.getValueFactory().setValue(currentHeight / 8);
        };

        applyCustomButton.setOnAction(e -> {
            int newW = customWSpinner.getValue() * 8;
            int newH = customHSpinner.getValue() * 8;
            if (newW == currentWidth && newH == currentHeight) return;
            if (hasContent() && !confirmResize()) return;
            applyCustomSize.run();
        });

        previewTimer = new AnimationTimer() {
            private long lastNanos = 0;
            private long accNanos = 0;

            @Override
            public void handle(long now) {
                if (lastNanos == 0) { lastNanos = now; return; }
                long delta = now - lastNanos;
                lastNanos = now;
                long durNanos = frames.get(currentFrameIndex).getDurationVbls() * 1_000_000_000L / refreshRateFor(currentPlatform);
                accNanos += delta;
                if (accNanos >= durNanos) {
                    accNanos = 0;
                    int next = (currentFrameIndex + 1) % frames.size();
                    switchToFrame(next, canvas, frameLabel, durationSpinner, updateAction);
                }
            }
        };

        prevFrameButton.setOnAction(e -> switchToFrame(currentFrameIndex - 1, canvas, frameLabel, durationSpinner, updateAction));
        nextFrameButton.setOnAction(e -> switchToFrame(currentFrameIndex + 1, canvas, frameLabel, durationSpinner, updateAction));

        addFrameButton.setOnAction(e -> {
            SpriteFrame template = frames.get(currentFrameIndex);
            SpriteFrame newFrame = new SpriteFrame(deepCopyPixels(template.getPixels()), template.getDurationVbls());
            pushUndoState();
            frames.add(currentFrameIndex + 1, newFrame);
            switchToFrame(currentFrameIndex + 1, canvas, frameLabel, durationSpinner, updateAction);
            markDirty();
        });

        deleteFrameButton.setOnAction(e -> {
            if (frames.size() <= 1) return;
            previewTimer.stop();
            previewActive = false;
            playButton.setText(msg(bundle, "tool.tileEditor.play", "▶ Anim"));
            pushUndoState();
            frames.remove(currentFrameIndex);
            switchToFrame(Math.min(currentFrameIndex, frames.size() - 1), canvas, frameLabel, durationSpinner, updateAction);
            markDirty();
        });

        durationSpinner.valueProperty().addListener((obs, oldV, newVal) -> {
            if (newVal != null && currentFrameIndex < frames.size()
                    && frames.get(currentFrameIndex).getDurationVbls() != newVal) {
                pushUndoState();
                frames.get(currentFrameIndex).setDurationVbls(newVal);
                updateAction.run();
                updateDurationInfo();
                markDirty();
            }
        });

        playButton.setOnAction(e -> {
            if (previewActive) {
                previewTimer.stop();
                previewActive = false;
                playButton.setText(msg(bundle, "tool.tileEditor.play", "▶ Anim"));
            } else {
                previewActive = true;
                playButton.setText(msg(bundle, "tool.tileEditor.stop", "⏸ Parar"));
                previewTimer.start();
            }
        });

        platformChoice.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                savePrefs();
                currentPlatform = newV;
                chk8x16.setDisable(currentPlatform != PlatformMode.GAMEBOY);
                rebuildColorStrip();
                loadPrefs();
                redrawCanvas(canvas);
                updateAction.run();
                updateDurationInfo();
                markDirty();
            }
        });

        sizeChoice.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            boolean custom = newV.isCustom();
            customSizeBox.setVisible(custom);
            customSizeBox.setManaged(custom);
            if (custom) {
                refreshCustomControls.run();
            } else {
                boolean sizeChange = newV.getWidth() != currentWidth || newV.getHeight() != currentHeight;
                if (sizeChange && hasContent() && !confirmResize()) {
                    sizeChoice.setValue(oldV);
                    return;
                }
                pushUndoState();
                snapshotPixels();
                resizePixels(newV.getWidth(), newV.getHeight());
                canvas.setWidth(newV.getWidth() * cellSize);
                canvas.setHeight(newV.getHeight() * cellSize);
                redrawCanvas(canvas);
                updateAction.run();
                if (sizeChange) markDirty();
            }
        });

        canvas.setOnMousePressed(evt -> {
            cancelAutoFitUndo();
            strokeUndoPushed = false;
            updateHover(evt.getX(), evt.getY());
            int x = (int) (evt.getX() / cellSize);
            int y = (int) (evt.getY() / cellSize);
            if (x < 0 || y < 0 || x >= currentWidth || y >= currentHeight) return;
            if (evt.isAltDown() || evt.getButton() == MouseButton.MIDDLE) {
                pickColorFromCell(x, y);
                return;
            }
            if (fillActive) {
                fillAt(x, y);
                return;
            }
            if (lineActive || rectActive) {
                startShapeStroke(x, y, evt.getButton() == MouseButton.SECONDARY || eraseActive);
                return;
            }
            pushUndoState();
            strokeUndoPushed = true;
            paintAt(x, y, evt.getButton() == MouseButton.SECONDARY || eraseActive);
            redrawCanvas(canvas);
            updateAction.run();
            markDirty();
        });

        canvas.setOnMouseMoved(evt -> {
            if (updateHover(evt.getX(), evt.getY())) redrawCanvas(canvas);
        });
        canvas.setOnMouseExited(evt -> {
            hoverCol = -1;
            hoverRow = -1;
            redrawCanvas(canvas);
            updateMouseInfo();
        });

        canvas.setOnMouseDragged(evt -> {
            cancelAutoFitUndo();
            updateHover(evt.getX(), evt.getY());
            int x = (int) (evt.getX() / cellSize);
            int y = (int) (evt.getY() / cellSize);
            if (x < 0 || y < 0 || x >= currentWidth || y >= currentHeight) return;
            if (!strokeUndoPushed) {
                pushUndoState();
                strokeUndoPushed = true;
            }
            boolean erase;
            if (evt.isPrimaryButtonDown()) {
                erase = eraseActive;
            } else if (evt.isSecondaryButtonDown()) {
                erase = true;
            } else {
                return;
            }
            int[] b = brushBounds(x, y);
            if (lineActive || rectActive) {
                if (strokeStartPixels != null) {
                    restorePixelsFrom(strokeStartPixels);
                    paintShape(strokeStartCol, strokeStartRow, x, y);
                    redrawCanvas(canvas);
                    updateAction.run();
                    markDirty();
                }
                return;
            }
if (blockColorMode()) {
                b[0] = (b[0] / 8) * 8;
                b[1] = (b[1] / 8) * 8;
                b[2] = Math.min(currentWidth - 1, (b[2] / 8) * 8 + 7);
                b[3] = Math.min(currentHeight - 1, (b[3] / 8) * 8 + 7);
            }
            paintAt(x, y, erase);
            redrawRegion(b[0], b[1], b[2], b[3]);
            updateAction.run();
            markDirty();
        });

        canvas.setOnScroll(evt -> {
            if (!evt.isControlDown()) return;
            double factor = evt.getDeltaY() > 0 ? 1.1 : 1.0 / 1.1;
            zoomByFactor(zoomLabel, factor);
            evt.consume();
        });

        symbolField.textProperty().addListener((obs, oldV, newV) -> {
            updateAction.run();
            savePrefs();
        });
        chkConst.selectedProperty().addListener((obs, oldV, newV) -> updateAction.run());
        chkDefines.selectedProperty().addListener((obs, oldV, newV) -> updateAction.run());
        chk8x16.selectedProperty().addListener((obs, oldV, newV) -> updateAction.run());

        Button clearButton = new Button(msg(bundle, "tool.tileEditor.clear", "Limpiar"));
        clearButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.clearTip", "Pone todos los píxeles del frame actual a 0.")));
        clearButton.setOnAction(e -> {
            cancelAutoFitUndo();
            if (hasContent()) pushUndoState();
            clearPixels();
            redrawCanvas(canvas);
            updateAction.run();
            markDirty();
        });

        autoFitButton = new Button(msg(bundle, "tool.tileEditor.autoFit", "Autoajustar"));
        autoFitButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.autoFitTip", "Escala el dibujo anterior (antes de cambiar el tamaño) para llenar el nuevo tamaño. Después de aplicarlo, el botón permite deshacerlo si no convence.")));
        autoFitButton.setDisable(true);
        autoFitButton.setOnAction(e -> {
            if (autoFitApplied) {
                pixels = autoFitUndoPixels;
                currentWidth = autoFitUndoWidth;
                currentHeight = autoFitUndoHeight;
                frames.get(currentFrameIndex).setPixels(pixels);
                autoFitUndoPixels = null;
                autoFitApplied = false;
                autoFitButton.setText(msg(bundle, "tool.tileEditor.autoFit", "Autoajustar"));
                canvas.setWidth(currentWidth * cellSize);
                canvas.setHeight(currentHeight * cellSize);
                redrawCanvas(canvas);
                updateAction.run();
                markDirty();
                return;
            }
            if (lastPixels == null || lastWidth <= 0 || lastHeight <= 0) return;
            if (lastWidth == currentWidth && lastHeight == currentHeight) return;
            autoFitUndoPixels = deepCopyPixels(pixels);
            autoFitUndoWidth = currentWidth;
            autoFitUndoHeight = currentHeight;
            pushUndoState();
            autoFitPixels();
            autoFitApplied = true;
            autoFitButton.setText(msg(bundle, "tool.tileEditor.undoAutoFit", "Deshacer autoajustar"));
            canvas.setWidth(currentWidth * cellSize);
            canvas.setHeight(currentHeight * cellSize);
            redrawCanvas(canvas);
            updateAction.run();
            markDirty();
        });

        Button openButton = new Button(msg(bundle, "tool.tileEditor.openSprite", "📂 Abrir Sprite..."));
        openButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.openSpriteTip", "Abre un proyecto de sprite Samaruc (.tile) con todos sus frames.")));
        openButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(msg(bundle, "tool.tileEditor.openDialogTitle", "Abrir proyecto de Sprite"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(msg(bundle, "tool.tileEditor.tileFilter", "Sprite Project (*.tile, *.json)"), "*.tile", "*.json"));
            
            File initialDir = currentProjectDir;
            if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = lastUsedDirectory;
            }
            if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = new File(System.getProperty("user.home"));
            }
            try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}

            File file = chooser.showOpenDialog(currentWindow());
            if (file != null) {
                openProject(file);
            }
        });

        Button saveButton = new Button(msg(bundle, "button.save", "Guardar"));
        saveButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.saveTip",
                "Guarda el proyecto .tile (frames, colores, símbolo). Es distinto de Exportar, que genera el código C para tu juego.")));
        saveButton.setOnAction(e -> saveCurrentProject());

        Button copyButton = new Button(msg(bundle, "tool.tileEditor.copyCode", "Copiar código"));
        copyButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.copyCodeTip", "Copia el código C generado (según la plataforma) al portapapeles.")));
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(outputArea.getText() != null ? outputArea.getText() : "");
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button exportButton = new Button(msg(bundle, "tool.tileEditor.exportProject", "Exportar al proyecto"));
        exportButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.exportTip",
                "Guarda el código C (.c/.h) generado en disco para usarlo en tu juego. El guardado .tile solo persiste el proyecto del editor.")));
        exportButton.setOnAction(e -> exportCodeToProject(currentProjectDir, outputArea.getText(), symbolField.getText(), bundle, onExported));

        Button helpButton = new Button(msg(bundle, "tool.tileEditor.help", "Ayuda"));
        helpButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.helpTip", "Manual de uso de los sprites generados según la plataforma seleccionada.")));
        helpButton.setOnAction(e -> showSpriteHelp());

        Button importPngButton = new Button(msg(bundle, "tool.tileEditor.importPng", "🖼 Importar PNG..."));
        importPngButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.importPngTip",
                "Carga una imagen PNG y la convierte a índices de la paleta de la plataforma activa. El color 0 (transparente) se usa para píxeles translúcidos.")));
        importPngButton.setOnAction(e -> importPng(currentProjectDir));

        eraseButton = new ToggleButton(msg(bundle, "tool.tileEditor.eraser", "🧽 Borrador"));
        eraseButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.eraserTip", "Limpia píxeles de la celda. También puedes arrastrar para borrar con el botón derecho del ratón.")));
        eraseButton.setOnAction(e -> setEraseActive(eraseButton.isSelected()));

        brushSizeSpinner = new Spinner<>(1, 8, 1);
        brushSizeSpinner.setPrefWidth(60);
        Tooltip.install(brushSizeSpinner, new Tooltip(msg(bundle, "tool.tileEditor.brushTip", "Tamaño del pincel en celdas (NxN). El centro se apoya en la celda bajo el cursor.")));

        ToggleGroup toolsGroup = new ToggleGroup();
        ToggleButton brushButton = new ToggleButton(msg(bundle, "tool.tileEditor.toolBrush", "Pincel"));
        brushButton.setToggleGroup(toolsGroup);
        brushButton.setUserData("brush");
        brushButton.setSelected(true);

        fillButton = new ToggleButton(msg(bundle, "tool.tileEditor.toolFill", "Relleno"));
        fillButton.setToggleGroup(toolsGroup);
        fillButton.setUserData("fill");
        fillButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.fillTip",
                "Rellena la región contigua del mismo color con el color actual (o 0 si el borrador está activo).")));

        lineButton = new ToggleButton(msg(bundle, "tool.tileEditor.toolLine", "↗ Línea"));
        lineButton.setToggleGroup(toolsGroup);
        lineButton.setUserData("line");
        lineButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.lineTip",
                "Dibuja una línea desde el punto pulsado hasta soltar el ratón. Con el botón derecho (o borrador activo) borra.")));

        rectButton = new ToggleButton(msg(bundle, "tool.tileEditor.toolRect", "▭ Rectángulo"));
        rectButton.setToggleGroup(toolsGroup);
        rectButton.setUserData("rect");
        rectButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.rectTip",
                "Dibuja el contorno de un rectángulo entre el punto pulsado y el punto actual del ratón.")));

        toolsGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            String tool = n != null && n.getUserData() != null ? (String) n.getUserData() : "brush";
            fillActive = "fill".equals(tool);
            lineActive = "line".equals(tool);
            rectActive = "rect".equals(tool);
            if (fillActive && eraseActive) setEraseActive(false);
        });

        CheckBox symCheck = new CheckBox(msg(bundle, "tool.tileEditor.symBrush", "Simétrico"));
        symCheck.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.symBrushTip",
                "Cada trazo del pincel se repite reflejado respecto al centro del sprite (horizontal, vertical o ambos).")));
        symCheck.selectedProperty().addListener((obs, o, n) -> symBrush = n);

        Button paletteButton = new Button(msg(bundle, "tool.tileEditor.paletteButton", "🎨 Paleta..."));
        paletteButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.paletteEditorTip",
                "Ajusta visualmente los colores de la paleta de la plataforma activa. Solo afecta a la vista previa y a los swatches; los índices del código C no cambian.")));
        paletteButton.setOnAction(e -> openPaletteEditor());

        Button flipHButton = new Button(msg(bundle, "tool.tileEditor.flipH", "↔ Voltear H"));
        flipHButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.flipHTip", "Voltea el frame actual horizontalmente (deshacible).")));
        flipHButton.setOnAction(e -> transformFrame("flipH"));
        Button flipVButton = new Button(msg(bundle, "tool.tileEditor.flipV", "↕ Voltear V"));
        flipVButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.flipVTip", "Voltea el frame actual verticalmente (deshacible).")));
        flipVButton.setOnAction(e -> transformFrame("flipV"));
        Button rotateButton = new Button(msg(bundle, "tool.tileEditor.rotate", "⟳ Rotar 90º"));
        rotateButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.rotateTip", "Rota el frame actual 90º en el sentido de las agujas del reloj (deshacible).")));
        rotateButton.setOnAction(e -> transformFrame("rotate"));

        undoButton = new Button(msg(bundle, "tool.tileEditor.undo", "↩ Deshacer"));
        undoButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.undoTip", "Deshacer el último cambio (Ctrl+Z)")));
        undoButton.setOnAction(e -> undo());
        redoButton = new Button(msg(bundle, "tool.tileEditor.redo", "↪ Rehacer"));
        redoButton.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.redoTip", "Rehacer el cambio deshecho (Ctrl+Y)")));
        redoButton.setOnAction(e -> redo());
        updateUndoButtons();

        HBox bandPlataforma = new HBox(8,
            new Label(msg(bundle, "tool.tileEditor.platform", "Plataforma:")), platformChoice);
        HBox bandTamano = new HBox(8,
            new Label(msg(bundle, "tool.tileEditor.spriteSize", "Tamaño:")), sizeChoice, customSizeBox);
        HBox bandArray = new HBox(8,
            new Label(msg(bundle, "tool.tileEditor.array", "Array:")), symbolField);
        HBox bandColor = new HBox(8,
            new Label(msg(bundle, "tool.tileEditor.color", "Color:")), colorStrip, zxClashLabel);
        HBox bandChecks = new HBox(8, chkConst, chkDefines, chk8x16);
        bandPlataforma.setAlignment(Pos.CENTER_LEFT);
        bandTamano.setAlignment(Pos.CENTER_LEFT);
        bandArray.setAlignment(Pos.CENTER_LEFT);
        bandColor.setAlignment(Pos.CENTER_LEFT);
        bandChecks.setAlignment(Pos.CENTER_LEFT);

        HBox editorTop = new HBox(8,
            bandPlataforma, new Separator(), bandTamano, new Separator(),
            bandArray, new Separator(), bandColor, new Separator(), bandChecks);
        editorTop.setAlignment(Pos.CENTER_LEFT);

        HBox projectRow = new HBox(8,
            openButton, importPngButton, saveButton, new Separator(), copyButton, exportButton, helpButton);
        projectRow.setAlignment(Pos.CENTER_LEFT);
        HBox editRow = new HBox(8,
            clearButton, eraseButton, brushButton, fillButton, lineButton, rectButton,
            new Label(msg(bundle, "tool.tileEditor.brush", "Pincel:")), brushSizeSpinner, symCheck,
            new Separator(), flipHButton, flipVButton, rotateButton, paletteButton,
            new Separator(), autoFitButton, undoButton, redoButton);
        editRow.setAlignment(Pos.CENTER_LEFT);

        showCodeToggle = new CheckBox(msg(bundle, "tool.tileEditor.toggleCode", "Ver código"));
        showCodeToggle.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.toggleCodeTip", "Muestra u oculta el código C generado para dejar espacio al lienzo.")));
        showCodeToggle.setSelected(true);
        showCodeToggle.setOnAction(e -> {
            boolean show = showCodeToggle.isSelected();
            outputArea.setVisible(show);
            outputArea.setManaged(show);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        showGridToggle = new CheckBox(msg(bundle, "tool.tileEditor.showGrid", "Rejilla"));
        showGridToggle.setTooltip(new Tooltip(msg(bundle, "tool.tileEditor.showGridTip",
                "Muestra u oculta la rejilla de celdas. El fondo a cuadros marca siempre las celdas vacías (color 0).")));
        showGridToggle.setSelected(true);
        showGridToggle.setOnAction(e -> {
            showGrid = showGridToggle.isSelected();
            redrawCanvas(canvas);
        });
        HBox zoomBar = new HBox(6, zoomFitButton, zoomOutButton, zoomLabel, zoomResetButton, zoomInButton,
            new Separator(), showGridToggle, spacer, mouseInfoLabel);
        zoomBar.setAlignment(Pos.CENTER_LEFT);

        Label shortcutsLabel = new Label(msg(bundle, "tool.tileEditor.shortcuts",
                "Flechas: mover cursor · Enter: pintar · Supr: borrar · Alt+Clic / botón central: cuenta-gotas · [ ]: frame"));
        shortcutsLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox codeHeader = new HBox(8, projectRow, spacer2, showCodeToggle);
        codeHeader.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(10, editorTop, animBar, canvasRow, filmstripPane, zoomBar, shortcutsLabel, editRow, codeHeader, outputArea);
        center.setPadding(new Insets(10));

        contentScrollPane = new ScrollPane(center);
        contentScrollPane.setFitToWidth(true);

        BorderPane root = new BorderPane(contentScrollPane);
        rootNode = root;

redrawCanvas(canvas);
        updateAction.run();
        updateDurationInfo();
        updateMouseInfo();
        loadPrefs();
        redrawCanvas(canvas);

        rootNode.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!(e.getTarget() instanceof TextInputControl)) {
                if (!e.isControlDown() && !e.isMetaDown() && !e.isAltDown()) {
                    if (e.getCode() == KeyCode.UP) { moveCursor(0, -1); e.consume(); return; }
                    if (e.getCode() == KeyCode.DOWN) { moveCursor(0, 1); e.consume(); return; }
                    if (e.getCode() == KeyCode.LEFT) { moveCursor(-1, 0); e.consume(); return; }
                    if (e.getCode() == KeyCode.RIGHT) { moveCursor(1, 0); e.consume(); return; }
                    if (e.getCode() == KeyCode.ENTER) { paintAtCursor(false); e.consume(); return; }
                    if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) { paintAtCursor(true); e.consume(); return; }
                    if (e.getCode() == KeyCode.OPEN_BRACKET) {
                        switchToFrame(currentFrameIndex - 1, canvas, frameLabel, durationSpinner, updateAction);
                        e.consume(); return;
                    }
                    if (e.getCode() == KeyCode.CLOSE_BRACKET) {
                        switchToFrame(currentFrameIndex + 1, canvas, frameLabel, durationSpinner, updateAction);
                        e.consume(); return;
                    }
                    if (e.getCode() == KeyCode.PLUS || (e.getCode() == KeyCode.EQUALS && e.isShiftDown())) {
                        zoomByFactor(zoomLabel, 1.25);
                        e.consume();
                        return;
                    }
                    if (e.getCode() == KeyCode.MINUS) {
                        zoomByFactor(zoomLabel, 1.0 / 1.25);
                        e.consume();
                        return;
                    }
                    if (e.getCode() == KeyCode.DIGIT0) {
                        zoomTo(zoomLabel, DEFAULT_CELL_SIZE);
                        e.consume();
                        return;
                    }
                }
                if (e.isControlDown() && !e.isAltDown() && e.getCode() == KeyCode.DIGIT0) {
                    zoomFit(zoomLabel);
                    e.consume();
                    return;
                }
            }
            if (!e.isControlDown() && !e.isMetaDown()) return;
            if (e.isAltDown()) return;
            if (e.getTarget() instanceof TextInputControl) return;
            if (e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) redo(); else undo();
                e.consume();
            } else if (e.getCode() == KeyCode.Y) {
                redo();
                e.consume();
            }
        });

        return rootNode;
    }

    private Window currentWindow() {
        if (rootNode != null && rootNode.getScene() != null) {
            return rootNode.getScene().getWindow();
        }
        return null;
    }

    private void paintAt(int cx, int cy, boolean erase) {
        paintBrushBox(cx, cy, erase);
        if (symBrush) {
            int mx = currentWidth - 1 - cx;
            int my = currentHeight - 1 - cy;
            if (mx != cx) paintBrushBox(mx, cy, erase);
            if (my != cy) paintBrushBox(cx, my, erase);
            if (mx != cx && my != cy) paintBrushBox(mx, my, erase);
        }
    }

    private void paintBrushBox(int cx, int cy, boolean erase) {
        int size = brushSizeSpinner != null ? brushSizeSpinner.getValue() : 1;
        int startY = cy - (size - 1) / 2;
        int endY = cy + size / 2;
        int startX = cx - (size - 1) / 2;
        int endX = cx + size / 2;

        boolean blockRecolor = !erase && blockColorMode() && selectedColor != 0;
        if (blockRecolor) {
            for (int y = startY; y <= endY; y++) {
                if (y < 0 || y >= currentHeight) continue;
                for (int x = startX; x <= endX; x++) {
                    if (x < 0 || x >= currentWidth) continue;
                    recolorZxBlock((y / 8) * 8, (x / 8) * 8, selectedColor);
                }
            }
        }

        for (int y = startY; y <= endY; y++) {
            if (y < 0 || y >= currentHeight) continue;
            for (int x = startX; x <= endX; x++) {
                if (x < 0 || x >= currentWidth) continue;
                pixels[y][x] = erase || (blockColorMode() && selectedColor == 0)
                        ? 0 : selectedColor;
            }
        }
    }

    private void startShapeStroke(int cx, int cy, boolean erase) {
        cancelAutoFitUndo();
        pushUndoState();
        strokeUndoPushed = true;
        strokeStartPixels = deepCopyPixels(pixels);
        strokeStartCol = cx;
        strokeStartRow = cy;
        strokeErase = erase;
        paintShape(cx, cy, cx, cy);
        redrawCanvas(canvas);
        updateAction.run();
        markDirty();
    }

    private void restorePixelsFrom(int[][] snap) {
        if (snap == null || pixels == null) return;
        for (int y = 0; y < currentHeight && y < snap.length; y++) {
            for (int x = 0; x < currentWidth && x < snap[y].length; x++) {
                pixels[y][x] = snap[y][x];
            }
        }
    }

    private void paintShape(int x0, int y0, int x1, int y1) {
        int color = strokeErase || (blockColorMode() && selectedColor == 0) ? 0 : selectedColor;
        if (lineActive) {
            bresenhamLine(x0, y0, x1, y1, color);
        } else {
            int minX = Math.min(x0, x1);
            int maxX = Math.max(x0, x1);
            int minY = Math.min(y0, y1);
            int maxY = Math.max(y0, y1);
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (y == minY || y == maxY || x == minX || x == maxX) {
                        setPixel(x, y, color);
                    }
                }
            }
        }
    }

    private void bresenhamLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            setPixel(x0, y0, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private void setPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= currentWidth || y >= currentHeight) return;
        pixels[y][x] = color;
    }

    private void recolorZxBlock(int blockRow, int blockCol, int color) {
        if (color == 0) return;
        for (int r = 0; r < 8; r++) {
            int y = blockRow + r;
            if (y >= currentHeight) break;
            for (int c = 0; c < 8; c++) {
                int x = blockCol + c;
                if (x >= currentWidth) break;
                if (pixels[y][x] != 0 && pixels[y][x] != color) {
                    pixels[y][x] = color;
                }
            }
        }
    }

    private int[] brushBounds(int cx, int cy) {
        int size = brushSizeSpinner != null ? brushSizeSpinner.getValue() : 1;
        int startY = Math.max(0, cy - (size - 1) / 2);
        int endY = Math.min(currentHeight - 1, cy + size / 2);
        int startX = Math.max(0, cx - (size - 1) / 2);
        int endX = Math.min(currentWidth - 1, cx + size / 2);
        return new int[] { startX, startY, endX, endY };
    }

    private void moveCursor(int dx, int dy) {
        int nc = Math.max(0, Math.min(currentWidth - 1, cursorCol + dx));
        int nr = Math.max(0, Math.min(currentHeight - 1, cursorRow + dy));
        if (nc != cursorCol || nr != cursorRow) {
            cursorCol = nc;
            cursorRow = nr;
            redrawCanvas(canvas);
        }
    }

    private void paintAtCursor(boolean erase) {
        if (pixels == null || cursorCol < 0 || cursorRow < 0
                || cursorCol >= currentWidth || cursorRow >= currentHeight) return;
        cancelAutoFitUndo();
        pushUndoState();
        strokeUndoPushed = true;
        paintAt(cursorCol, cursorRow, erase);
        redrawCanvas(canvas);
        updateAction.run();
        markDirty();
    }

    private void pickColorFromCell(int x, int y) {
        if (pixels == null || pixels[y] == null) return;
        int idx = pixels[y][x];
        int max = currentPalette(currentPlatform).length;
        if (idx < 0 || idx >= max) return;
        selectColor(idx);
        setEraseActive(false);
        savePrefs();
    }

    private void fillAt(int x, int y) {
        if (pixels == null || pixels[y] == null) return;
        cancelAutoFitUndo();
        int target = pixels[y][x];
        int fillVal = (eraseActive || (blockColorMode() && selectedColor == 0)) ? 0 : selectedColor;
        if (target == fillVal) return;
        pushUndoState();
        strokeUndoPushed = true;
        floodFill(x, y, target, fillVal);
        redrawCanvas(canvas);
        updateAction.run();
        markDirty();
    }

    private void floodFill(int startX, int startY, int target, int fillVal) {
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.addLast(new int[] { startX, startY });
        while (!stack.isEmpty()) {
            int[] p = stack.removeLast();
            int x = p[0];
            int y = p[1];
            if (x < 0 || y < 0 || x >= currentWidth || y >= currentHeight) continue;
            if (pixels[y][x] != target) continue;
            pixels[y][x] = fillVal;
            stack.addLast(new int[] { x + 1, y });
            stack.addLast(new int[] { x - 1, y });
            stack.addLast(new int[] { x, y + 1 });
            stack.addLast(new int[] { x, y - 1 });
        }
    }

    private void applyFramePixels(int[][] newPixels) {
        if (newPixels == null || newPixels.length == 0) return;
        frames.get(currentFrameIndex).setPixels(newPixels);
        pixels = newPixels;
        currentWidth = newPixels[0].length;
        currentHeight = newPixels.length;
        syncSizeUiToCurrent();
        if (canvas != null) {
            canvas.setWidth(currentWidth * cellSize);
            canvas.setHeight(currentHeight * cellSize);
        }
    }

    private void syncSizeUiToCurrent() {
        SpriteSizeMode matched = null;
        for (SpriteSizeMode mode : SpriteSizeMode.values()) {
            if (!mode.isCustom() && mode.getWidth() == currentWidth && mode.getHeight() == currentHeight) {
                matched = mode;
                break;
            }
        }
        if (matched != null) {
            sizeChoice.setValue(matched);
        } else if (currentWidth % 8 == 0 && currentHeight % 8 == 0) {
            customWSpinner.getValueFactory().setValue(currentWidth / 8);
            customHSpinner.getValueFactory().setValue(currentHeight / 8);
            sizeChoice.setValue(SpriteSizeMode.CUSTOM);
        }
    }

    private void transformFrame(String op) {
        if (pixels == null) return;
        pushUndoState();
        int w = currentWidth;
        int h = currentHeight;
        int[][] src = pixels;
        int[][] dst;
        switch (op) {
            case "flipH" -> {
                dst = new int[h][w];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        dst[y][w - 1 - x] = src[y][x];
                    }
                }
            }
            case "flipV" -> {
                dst = new int[h][w];
                for (int y = 0; y < h; y++) {
                    System.arraycopy(src[h - 1 - y], 0, dst[y], 0, w);
                }
            }
            case "rotate" -> {
                dst = new int[w][h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        dst[x][h - 1 - y] = src[y][x];
                    }
                }
            }
            default -> { return; }
        }
        applyFramePixels(dst);
        redrawCanvas(canvas);
        updateFilmstrip();
        updateAction.run();
        markDirty();
    }

    private void importPng(File currentProjectDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(msg(bundle, "tool.tileEditor.importPngDialogTitle", "Importar PNG como sprite"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                msg(bundle, "tool.tileEditor.pngFilter", "PNG images (*.png)"), "*.png"));
        File initialDir = currentProjectDir;
        if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
            initialDir = lastUsedDirectory;
        }
        if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
            initialDir = new File(System.getProperty("user.home"));
        }
        try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}

        File selected = chooser.showOpenDialog(currentWindow());
        if (selected == null) return;

        BufferedImage img;
        try {
            img = ImageIO.read(selected);
            if (img == null) throw new IOException("null");
        } catch (Exception ex) {
            showAlert(msg(bundle, "tool.tileEditor.importPngErrorTitle", "Error al importar PNG"),
                    msg(bundle, "tool.tileEditor.importPngErrorBody", "No se pudo leer la imagen: ") + ex.getMessage(),
                    Alert.AlertType.ERROR, currentWindow());
            return;
        }

        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            showAlert(msg(bundle, "tool.tileEditor.importPngErrorTitle", "Error al importar PNG"),
                    msg(bundle, "tool.tileEditor.importPngSizeBody", "El archivo no tiene dimensiones válidas."),
                    Alert.AlertType.ERROR, currentWindow());
            return;
        }

        int[][] newPx = new int[h][w];
        int oldW = currentWidth;
        int oldH = currentHeight;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                newPx[y][x] = a < 128 ? 0 : quantizeToPlatform(r, g, b);
            }
        }

        pushUndoState();
        currentWidth = w;
        currentHeight = h;
        for (int i = 0; i < frames.size(); i++) {
            int[][] fp = frames.get(i).getPixels();
            int[][] out = new int[h][w];
            for (int y = 0; y < Math.min(oldH, h); y++) {
                for (int x = 0; x < Math.min(oldW, w); x++) {
                    out[y][x] = fp[y][x];
                }
            }
            frames.get(i).setPixels(out);
        }
        frames.get(currentFrameIndex).setPixels(newPx);
        pixels = newPx;
        syncSizeUiToCurrent();
        if (canvas != null) {
            canvas.setWidth(w * cellSize);
            canvas.setHeight(h * cellSize);
        }
        cursorCol = Math.min(cursorCol, w - 1);
        cursorRow = Math.min(cursorRow, h - 1);
        redrawCanvas(canvas);
        updateFilmstrip();
        updateAction.run();
        markDirty();
    }

    private int quantizeToPlatform(int r, int g, int b) {
        if (currentPlatform == PlatformMode.GAMEBOY) {
            return new GameBoyColorQuantizer().quantize((r << 16) | (g << 8) | b);
        }
        if (currentPlatform == PlatformMode.ZX_SPECTRUM) {
            ZxSpectrumColor c = new ColorQuantizer(false).quantize(r, g, b);
            return c.colorIndex() & 0x07;
        }
        return nearestPaletteIndex(r, g, b);
    }

    private int nearestPaletteIndex(int r, int g, int b) {
        Color[] palette = currentPalette(currentPlatform);
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            Color c = palette[i];
            double dr = r - c.getRed() * 255;
            double dg = g - c.getGreen() * 255;
            double db = b - c.getBlue() * 255;
            double dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private void redrawPreview() {
        if (previewCanvas == null || pixels == null) return;
        previewCanvas.setWidth(Math.max(1, currentWidth));
        previewCanvas.setHeight(Math.max(1, currentHeight));
        GraphicsContext g = previewCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, currentWidth, currentHeight);
        for (int y = 0; y < currentHeight; y++) {
            for (int x = 0; x < currentWidth; x++) {
                g.setFill(paletteColorForIndex(pixels[y][x]));
                g.fillRect(x, y, 1, 1);
            }
        }
    }

    private void paintThumb(GraphicsContext g, SpriteFrame f, double scale) {
        int w = f.getWidth();
        int h = f.getHeight();
        int[][] fp = f.getPixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = fp[y][x];
                double px = x * scale;
                double py = y * scale;
                if (v == 0) {
                    boolean base = ((x + y) & 1) == 0;
                    g.setFill(base ? Color.web("#cccccc") : Color.web("#ffffff"));
                    g.fillRect(px, py, scale, scale);
                    double half = scale / 2;
                    g.setFill(base ? Color.web("#ffffff") : Color.web("#cccccc"));
                    g.fillRect(px, py, half, half);
                    g.fillRect(px + half, py + half, half, half);
                } else {
                    g.setFill(paletteColorForIndex(v));
                    g.fillRect(px, py, scale, scale);
                }
            }
        }
    }

    private void updateFilmstrip() {
        if (filmstripBox == null || frames.isEmpty()) return;
        filmstripBox.getChildren().clear();
        thumbCanvases.clear();
        int maxDim = Math.max(1, Math.max(currentWidth, currentHeight));
        double scale = THUMB_DISPLAY_HEIGHT / maxDim;
        for (int i = 0; i < frames.size(); i++) {
            final int idx = i;
            SpriteFrame f = frames.get(i);
            Canvas thumb = new Canvas(f.getWidth() * scale, f.getHeight() * scale);
            paintThumb(thumb.getGraphicsContext2D(), f, scale);
            Label idxLabel = new Label(String.valueOf(i + 1));
            idxLabel.setStyle("-fx-font-size: 10px;" + (i == currentFrameIndex ? "-fx-font-weight: bold;" : "-fx-text-fill: gray;"));
            VBox item = new VBox(2, thumb, idxLabel);
            item.setAlignment(Pos.CENTER);
            item.setStyle(i == currentFrameIndex
                    ? "-fx-border-color: #0078ff; -fx-border-width: 2; -fx-padding: 2;"
                    : "-fx-border-color: #bbbbbb; -fx-border-width: 1; -fx-padding: 3;");
            item.setOnMouseClicked(ev -> moveToFrame(idx));
            item.setOnMousePressed(ev -> {
                filmstripDragIndex = idx;
                filmstripDragStartSceneX = ev.getSceneX();
            });
            item.setOnMouseReleased(ev -> {
                if (filmstripDragIndex == null) return;
                double dx = ev.getSceneX() - filmstripDragStartSceneX;
                int from = filmstripDragIndex;
                int target = Math.max(0, Math.min(frames.size() - 1, from + (int) Math.round(dx / (thumb.getWidth() + 6))));
                if (target != from) {
                    pushUndoState();
                    SpriteFrame moved = frames.remove(from);
                    frames.add(target, moved);
                    currentFrameIndex = target;
                    markDirty();
                    updateFilmstrip();
                    redrawCanvas(canvas);
                    updateAction.run();
                    if (frameLabel != null) frameLabel.setText(fmt(msg(bundle, "tool.tileEditor.frameFormat", "Frame {0}/{1}"), currentFrameIndex + 1, frames.size()));
                }
                filmstripDragIndex = null;
            });
            filmstripBox.getChildren().add(item);
        }
    }

    private void loadPrefs() {
        if (symbolField == null) return;
        String plat = currentPlatform.name();
        String sym = prefs.get("tileEditor.symbol." + plat, "");
        if (sym != null && !sym.isBlank()) symbolField.setText(sym);
        int col = prefs.getInt("tileEditor.color." + plat, -1);
        if (col >= 0) {
            int max = currentPalette(currentPlatform).length;
            if (col < max) selectColor(col);
        }
    }

    private void savePrefs() {
        if (symbolField == null) return;
        String plat = currentPlatform.name();
        prefs.put("tileEditor.symbol." + plat, symbolField.getText());
        prefs.putInt("tileEditor.color." + plat, selectedColor);
    }

    private void loadPaletteOverrides() {
        for (PlatformMode pl : PlatformMode.values()) {
            Color[] pal = currentPalette(pl);
            for (int i = 0; i < pal.length; i++) {
                String hex = prefs.get("tileEditor.palette." + pl.name() + "." + i, null);
                if (hex == null || hex.isBlank()) continue;
                try {
                    paletteOverrides.put(pl.name() + ":" + i, Color.web(hex));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void savePaletteOverrides() {
        for (PlatformMode pl : PlatformMode.values()) {
            Color[] pal = currentPalette(pl);
            for (int i = 0; i < pal.length; i++) {
                String key = "tileEditor.palette." + pl.name() + "." + i;
                Color c = paletteOverrides.get(pl.name() + ":" + i);
                if (c == null) {
                    prefs.remove(key);
                } else {
                    prefs.put(key, String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                            Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255)));
                }
            }
        }
    }

    private void openPaletteEditor() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(msg(bundle, "tool.tileEditor.paletteEditorTitle", "Editor de paleta"));
        dialog.setHeaderText(msg(bundle, "tool.tileEditor.paletteEditorHint",
                "Ajusta los colores de la paleta (solo afecta a la vista y a los swatches; los índices del código C no cambian)."));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        javafx.stage.Window host = canvas != null ? canvas.getScene() != null ? canvas.getScene().getWindow() : null : null;
        if (host != null) dialog.initOwner(host);

        Map<String, Color> pending = new HashMap<>(paletteOverrides);
        ChoiceBox<PlatformMode> platformPicker = new ChoiceBox<>();
        platformPicker.getItems().addAll(PlatformMode.values());
        platformPicker.setValue(currentPlatform);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        Runnable rebuildGrid = () -> {
            grid.getChildren().clear();
            PlatformMode pl = platformPicker.getValue();
            if (pl == null) return;
            Color[] pal = currentPalette(pl);
            for (int i = 0; i < pal.length; i++) {
                final int idx = i;
                Color current = pending.getOrDefault(pl.name() + ":" + idx, pal[idx]);
                ColorPicker cp = new ColorPicker(current);
                cp.valueProperty().addListener((obs, o, n) -> {
                    if (n == null) return;
                    if (pal[idx].equals(n)) {
                        pending.remove(pl.name() + ":" + idx);
                    } else {
                        pending.put(pl.name() + ":" + idx, n);
                    }
                });
                Label lab = new Label(fmt(msg(bundle, "tool.tileEditor.colorIndex", "Color {0}"), idx));
                lab.setMinWidth(80);
                grid.addRow(idx, lab, cp);
            }
        };
        rebuildGrid.run();
        platformPicker.valueProperty().addListener((obs, o, n) -> {
            if (n != null) rebuildGrid.run();
        });

        Button resetButton = new Button(msg(bundle, "tool.tileEditor.paletteReset", "Restablecer colores"));
        resetButton.setOnAction(e -> {
            pending.clear();
            paletteOverrides.clear();
            rebuildGrid.run();
            rebuildColorStrip();
            redrawCanvas(canvas);
            redrawPreview();
            updateFilmstrip();
        });

        HBox row = new HBox(8, new Label(msg(bundle, "tool.tileEditor.platform", "Plataforma:")), platformPicker);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, row, grid, resetButton);
        content.setPadding(new Insets(14));
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            paletteOverrides.clear();
            paletteOverrides.putAll(pending);
            savePaletteOverrides();
            rebuildColorStrip();
            redrawCanvas(canvas);
            redrawPreview();
            updateFilmstrip();
        }
    }

    private int countNonZeroColors(int[][] px, int ty, int tx) {
        boolean[] seen = new boolean[16];
        int n = 0;
        for (int r = 0; r < 8; r++) {
            int y = ty + r;
            if (y >= px.length) break;
            for (int c = 0; c < 8; c++) {
                int x = tx + c;
                if (x >= px[y].length) break;
                int v = px[y][x] & 0x0F;
                if (v != 0 && !seen[v]) {
                    seen[v] = true;
                    n++;
                }
            }
        }
        return n;
    }

    private void updateZxWarning() {
        if (zxClashLabel == null) return;
        boolean active = blockColorMode();
        zxClashLabel.setVisible(active);
        zxClashLabel.setManaged(active);
        if (!active || pixels == null) {
            zxClashLabel.setText("");
            return;
        }
        int clashes = 0;
        for (int by = 0; by < currentHeight; by += 8) {
            for (int bx = 0; bx < currentWidth; bx += 8) {
                if (countNonZeroColors(pixels, by, bx) > 1) clashes++;
            }
        }
        if (clashes == 0 || currentPlatform == PlatformMode.ZX_SPECTRUM) {
            zxClashLabel.setText(clashes > 0
                    ? fmt(msg(bundle, "tool.tileEditor.clash", "{0} bloque(s) con más de 1 INK (limitación 8x8 del ZX Spectrum)"), clashes)
                    : "");
        } else {
            zxClashLabel.setText(fmt(msg(bundle, "tool.tileEditor.clashC64",
                    "{0} tile(s) con más de un color activo (el C64 solo admite un tono por bloque 8x8)"), clashes));
        }
    }

    private void setEraseActive(boolean active) {
        eraseActive = active;
        if (eraseButton != null) eraseButton.setSelected(active);
        if (colorStrip != null) colorStrip.setDisable(active);
    }

    private Color[] currentPalette(PlatformMode platform) {
        if (platform == PlatformMode.ZX_SPECTRUM) return zxPalette;
        if (platform == PlatformMode.ATARI) return atariPalette;
        if (platform == PlatformMode.C64) return c64Palette;
        if (platform == PlatformMode.CPC) return cpcPalette;
        if (platform == PlatformMode.MASTER_SYSTEM) return smsPalette;
        if (platform == PlatformMode.GAME_GEAR) return ggPalette;
        return gbPalette;
    }

    private int defaultPaletteIndex(PlatformMode platform) {
        if (platform == PlatformMode.ZX_SPECTRUM) return 7;
        if (platform == PlatformMode.ATARI) return 6;
        return 1;
    }

    /**
     * Plataformas con un único tono por bloque 8x8: ZX Spectrum (1 INK) y
     * C64 (memoria de color por tile). Pintar sobre un bloque con otro color
     * recolorea el bloque completo; el índice 0 borra.
     */
    private boolean blockColorMode() {
        return currentPlatform == PlatformMode.ZX_SPECTRUM || currentPlatform == PlatformMode.C64;
    }

    private void rebuildColorStrip() {
        if (colorStrip == null) return;
        colorStrip.getChildren().clear();
        if (colorGroup == null) colorGroup = new ToggleGroup();
        colorGroup.getToggles().clear();
        Color[] palette = currentPalette(currentPlatform);
        for (int i = 0; i < palette.length; i++) {
            final int index = i;
            Color c = paletteColorForIndex(i);
            String baseStyle = String.format(java.util.Locale.ROOT,
                    "-fx-background-color: rgb(%d,%d,%d); -fx-background-radius: 3;",
                    (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
            ToggleButton swatch = new ToggleButton();
            swatch.setToggleGroup(colorGroup);
            swatch.setPrefSize(22, 22);
            swatch.setMinSize(22, 22);
            swatch.setMaxSize(22, 22);
            swatch.setStyle(baseStyle + "-fx-border-color: #666; -fx-border-width: 1; -fx-border-radius: 3;");
            swatch.setUserData(index);
            swatch.setTooltip(new Tooltip(fmt(msg(bundle, "tool.tileEditor.colorIndex", "Color {0}"), index)));
            swatch.selectedProperty().addListener((obs, was, is) -> swatch.setStyle(
                    baseStyle + (is ? "-fx-border-color: black; -fx-border-width: 2; -fx-border-radius: 3;"
                                    : "-fx-border-color: #666; -fx-border-width: 1; -fx-border-radius: 3;")));
            swatch.setOnAction(e -> {
                swatch.setSelected(true);
                selectedColor = index;
                if (eraseActive) setEraseActive(false);
                savePrefs();
            });
            colorStrip.getChildren().add(swatch);
        }
        selectColor(defaultPaletteIndex(currentPlatform));
    }

    private void selectColor(int index) {
        selectedColor = index;
        if (colorGroup != null) {
            for (Toggle t : colorGroup.getToggles()) {
                Object data = t.getUserData();
                t.setSelected(data instanceof Integer i && i == index);
            }
        }
    }

    private EditorSnapshot captureSnapshot() {
        return new EditorSnapshot(deepCopyFrames(frames), currentFrameIndex);
    }

    private void pushUndoState() {
        undoStack.addLast(captureSnapshot());
        if (undoStack.size() > MAX_UNDO_STATES) undoStack.removeFirst();
        redoStack.clear();
        updateUndoButtons();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.addLast(captureSnapshot());
        restoreSnapshot(undoStack.removeLast());
        updateUndoButtons();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.addLast(captureSnapshot());
        restoreSnapshot(redoStack.removeLast());
        updateUndoButtons();
    }

    private void updateUndoButtons() {
        if (undoButton != null) undoButton.setDisable(undoStack.isEmpty());
        if (redoButton != null) redoButton.setDisable(redoStack.isEmpty());
    }

    private List<SpriteFrame> deepCopyFrames(List<SpriteFrame> src) {
        List<SpriteFrame> copy = new ArrayList<>(src.size());
        for (SpriteFrame f : src) {
            copy.add(new SpriteFrame(deepCopyPixels(f.getPixels()), f.getDurationVbls()));
        }
        return copy;
    }

    private void restoreSnapshot(EditorSnapshot snap) {
        if (previewActive && previewTimer != null) {
            previewTimer.stop();
            previewActive = false;
            if (playButton != null) playButton.setText(msg(bundle, "tool.tileEditor.play", "▶ Anim"));
        }
        frames.clear();
        frames.addAll(snap.frames());
        currentFrameIndex = Math.min(snap.frameIndex(), frames.size() - 1);
        if (currentFrameIndex < 0) currentFrameIndex = 0;
        SpriteFrame f = frames.get(currentFrameIndex);
        pixels = f.getPixels();
        currentWidth = f.getWidth();
        currentHeight = f.getHeight();
        cancelAutoFitUndo();
        if (canvas != null) {
            canvas.setWidth(currentWidth * cellSize);
            canvas.setHeight(currentHeight * cellSize);
            redrawCanvas(canvas);
        }
        if (frameLabel != null) frameLabel.setText(fmt(msg(bundle, "tool.tileEditor.frameFormat", "Frame {0}/{1}"),
                currentFrameIndex + 1, frames.size()));
        if (durationSpinner != null) durationSpinner.getValueFactory().setValue(f.getDurationVbls());
        updateDurationInfo();
        updateFilmstrip();
        if (updateAction != null) updateAction.run();
        markDirty();
    }

    private boolean hasContent() {
        if (pixels == null) return false;
        for (int y = 0; y < currentHeight && y < pixels.length; y++) {
            for (int x = 0; x < currentWidth && x < pixels[y].length; x++) {
                if (pixels[y][x] != 0) return true;
            }
        }
        return false;
    }

    private boolean confirmResize() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(currentWindow());
        alert.setTitle(msg(bundle, "tool.tileEditor.resizeTitle", "Cambiar tamaño"));
        alert.setHeaderText(msg(bundle, "tool.tileEditor.resizeHeader", "Se va a cambiar el tamaño del sprite."));
        alert.setContentText(msg(bundle, "tool.tileEditor.resizeBody",
                "Tiene píxeles dibujados. Si el nuevo tamaño es menor, los píxeles que queden fuera se descartarán (luego puede usar el botón Autoajustar para escalarlos al nuevo lienzo). ¿Desea continuar?"));
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void snapshotPixels() {
        if (pixels == null) return;
        lastPixels = deepCopyPixels(pixels);
        lastWidth = currentWidth;
        lastHeight = currentHeight;
        cancelAutoFitUndo();
        if (autoFitButton != null) autoFitButton.setDisable(false);
    }

    private int[][] deepCopyPixels(int[][] src) {
        if (src == null) return null;
        int h = src.length;
        int w = h > 0 ? src[0].length : 0;
        int[][] copy = new int[h][w];
        for (int y = 0; y < h; y++) {
            System.arraycopy(src[y], 0, copy[y], 0, w);
        }
        return copy;
    }

    private void cancelAutoFitUndo() {
        if (!autoFitApplied) return;
        autoFitApplied = false;
        autoFitUndoPixels = null;
        autoFitUndoWidth = 0;
        autoFitUndoHeight = 0;
        if (autoFitButton != null) autoFitButton.setText(msg(bundle, "tool.tileEditor.autoFit", "Autoajustar"));
    }

    private void autoFitPixels() {
        if (lastPixels == null || lastWidth <= 0 || lastHeight <= 0) return;
        if (lastWidth == currentWidth && lastHeight == currentHeight) return;
        int dstH = currentHeight;
        int dstW = currentWidth;
        int[][] dst = new int[dstH][dstW];
        for (int y = 0; y < dstH; y++) {
            int sy = Math.min(lastHeight - 1, (int) ((long) y * lastHeight / dstH));
            for (int x = 0; x < dstW; x++) {
                int sx = Math.min(lastWidth - 1, (int) ((long) x * lastWidth / dstW));
                dst[y][x] = lastPixels[sy][sx];
            }
        }
        pixels = dst;
        currentWidth = dstW;
        currentHeight = dstH;
        frames.get(currentFrameIndex).setPixels(pixels);
    }

    private void resizePixels(int newWidth, int newHeight) {
        for (int i = 0; i < frames.size(); i++) {
            int[][] src = frames.get(i).getPixels();
            int[][] newPixels = new int[newHeight][newWidth];
            int srcH = src.length;
            int srcW = src[0].length;
            for (int y = 0; y < Math.min(srcH, newHeight); y++) {
                for (int x = 0; x < Math.min(srcW, newWidth); x++) {
                    newPixels[y][x] = src[y][x];
                }
            }
            frames.get(i).setPixels(newPixels);
            if (i == currentFrameIndex) pixels = newPixels;
        }
        currentWidth = newWidth;
        currentHeight = newHeight;
    }

    private void clearPixels() {
        for (int y = 0; y < currentHeight; y++) {
            for (int x = 0; x < currentWidth; x++) {
                pixels[y][x] = 0;
            }
        }
    }

    private void moveToFrame(int index) {
        if (frames.isEmpty()) return;
        if (index < 0) index = 0;
        if (index >= frames.size()) index = frames.size() - 1;
        currentFrameIndex = index;
        SpriteFrame f = frames.get(index);
        pixels = f.getPixels();
        currentWidth = f.getWidth();
        currentHeight = f.getHeight();
        cancelAutoFitUndo();
        if (frameLabel != null) frameLabel.setText(fmt(msg(bundle, "tool.tileEditor.frameFormat", "Frame {0}/{1}"),
                index + 1, frames.size()));
        if (durationSpinner != null) durationSpinner.getValueFactory().setValue(f.getDurationVbls());
        if (canvas != null) {
            canvas.setWidth(currentWidth * cellSize);
            canvas.setHeight(currentHeight * cellSize);
            redrawCanvas(canvas);
        }
        updateDurationInfo();
        updateFilmstrip();
        if (updateAction != null) updateAction.run();
    }

    private void switchToFrame(int index, Canvas canvas, Label frameLabel, Spinner<Integer> durationSpinner, Runnable updateAction) {
        moveToFrame(index);
    }

    private void zoomByFactor(Label zoomLabel, double factor) {
        zoomTo(zoomLabel, cellSize * factor);
    }

    private void zoomTo(Label zoomLabel, double newCellSize) {
        cellSize = Math.max(4, Math.min(96, newCellSize));
        canvas.setWidth(currentWidth * cellSize);
        canvas.setHeight(currentHeight * cellSize);
        zoomLabel.setText(Math.round(cellSize / DEFAULT_CELL_SIZE * 100) + "%");
        redrawCanvas(canvas);
    }

    private void zoomFit(Label zoomLabel) {
        double availW = contentScrollPane != null ? contentScrollPane.getViewportBounds().getWidth() : 400;
        double availH = contentScrollPane != null ? contentScrollPane.getViewportBounds().getHeight() : 300;
        if (availW <= 0) availW = 400;
        if (availH <= 0) availH = 300;
        double wFit = currentWidth > 0 ? availW / currentWidth : 24;
        double hFit = currentHeight > 0 ? availH / currentHeight : 24;
        zoomTo(zoomLabel, Math.max(4, Math.min(96, Math.min(wFit, hFit))));
    }

    private void redrawCanvas(Canvas canvas) {
        if (canvas == null) return;
        redrawRegion(0, 0, currentWidth - 1, currentHeight - 1);
        updateZxWarning();
        updateMouseInfo();
        redrawPreview();
    }

    /**
     * Redibuja solo el tramo de celdas [x0..x1]x[y0..y1] del lienzo.
     * Se usa en el arrastre para no repintar todo el sprite en cada movimiento.
     */
    private void redrawRegion(int x0, int y0, int x1, int y1) {
        if (canvas == null || pixels == null) return;
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(currentWidth - 1, x1);
        y1 = Math.min(currentHeight - 1, y1);
        if (x1 < x0 || y1 < y0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.save();
        gc.beginPath();
        gc.rect(x0 * cellSize, y0 * cellSize, (x1 - x0 + 1) * cellSize, (y1 - y0 + 1) * cellSize);
        gc.clip();

        gc.setFill(Color.WHITE);
        gc.fillRect(x0 * cellSize, y0 * cellSize, (x1 - x0 + 1) * cellSize, (y1 - y0 + 1) * cellSize);

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                drawCell(gc, x, y);
            }
        }

        // Rejilla de celdas
        if (showGrid) {
            gc.setStroke(Color.web("#404040"));
            gc.setLineWidth(1.0);
            for (int gx = x0; gx <= x1 + 1; gx++) {
                gc.strokeLine(gx * cellSize, y0 * cellSize, gx * cellSize, (y1 + 1) * cellSize);
            }
            for (int gy = y0; gy <= y1 + 1; gy++) {
                gc.strokeLine(x0 * cellSize, gy * cellSize, (x1 + 1) * cellSize, gy * cellSize);
            }
        }

        // Límites 8x8 (tiles)
        gc.setStroke(Color.web("#202020"));
        gc.setLineWidth(2.0);
        for (int ty = 0; ty <= currentHeight / 8; ty++) {
            double ly = ty * 8 * cellSize;
            if (ly >= y0 * cellSize && ly <= (y1 + 1) * cellSize) {
                gc.strokeLine(x0 * cellSize, ly, (x1 + 1) * cellSize, ly);
            }
        }
        for (int tx = 0; tx <= currentWidth / 8; tx++) {
            double lx = tx * 8 * cellSize;
            if (lx >= x0 * cellSize && lx <= (x1 + 1) * cellSize) {
                gc.strokeLine(lx, y0 * cellSize, lx, (y1 + 1) * cellSize);
            }
        }
        gc.setLineWidth(1.0);

        // Desmarcar bloques con más de un color (ZX: attribute clash 8x8; C64: color RAM única por tile)
        if (blockColorMode()) {
            gc.setFill(Color.rgb(255, 0, 0, 0.22));
            gc.setStroke(Color.rgb(200, 0, 0, 0.7));
            gc.setLineWidth(2.0);
            int byStart = (y0 / 8) * 8;
            int bxStart = (x0 / 8) * 8;
            for (int by = byStart; by <= y1; by += 8) {
                for (int bx = bxStart; bx <= x1; bx += 8) {
                    if (countNonZeroColors(pixels, by, bx) > 1) {
                        double px = bx * cellSize;
                        double py = by * cellSize;
                        double sz = 8 * cellSize;
                        gc.fillRect(px, py, sz, sz);
                        gc.strokeLine(px, py, px + sz, py + sz);
                        gc.strokeLine(px + sz, py, px, py + sz);
                    }
                }
            }
            gc.setLineWidth(1.0);
        }

        // Hover: resaltar la celda bajo el cursor
        if (hoverCol >= x0 && hoverCol <= x1 && hoverRow >= y0 && hoverRow <= y1) {
            gc.setFill(Color.rgb(0, 120, 255, 0.22));
            gc.setStroke(Color.rgb(0, 100, 255, 0.9));
            gc.setLineWidth(2.0);
            gc.fillRect(hoverCol * cellSize, hoverRow * cellSize, cellSize, cellSize);
            gc.strokeRect(hoverCol * cellSize, hoverRow * cellSize, cellSize, cellSize);
            gc.setLineWidth(1.0);
        }

        // Cursor de teclado
        if (cursorCol >= x0 && cursorCol <= x1 && cursorRow >= y0 && cursorRow <= y1) {
            gc.setStroke(Color.web("#00aa44"));
            gc.setLineWidth(2.0);
            gc.strokeRoundRect(cursorCol * cellSize + 1, cursorRow * cellSize + 1, cellSize - 2, cellSize - 2, 4, 4);
            gc.setLineWidth(1.0);
        }

        gc.restore();
    }

    /**
     * Pinta una celda (fondo a cuadros si está vacía, o su color de paleta).
     */
    private void drawCell(GraphicsContext gc, int x, int y) {
        int colorIndex = pixels[y][x];
        double px = x * cellSize;
        double py = y * cellSize;
        if (colorIndex == 0) {
            boolean base = ((x + y) & 1) == 0;
            gc.setFill(base ? Color.web("#c8c8c8") : Color.web("#ffffff"));
            gc.fillRect(px, py, cellSize, cellSize);
            double half = cellSize / 2;
            gc.setFill(base ? Color.web("#ffffff") : Color.web("#c8c8c8"));
            gc.fillRect(px, py, half, half);
            gc.fillRect(px + half, py + half, half, half);
        } else {
            gc.setFill(paletteColorForIndex(colorIndex));
            gc.fillRect(px, py, cellSize, cellSize);
        }
    }

    private Color paletteColorForIndex(int colorIndex) {
        Color base = defaultPaletteColor(colorIndex);
        Color over = paletteOverrides.get(currentPlatform.name() + ":" + colorIndex);
        return over != null ? over : base;
    }

    private Color defaultPaletteColor(int colorIndex) {
        Color[] palette = currentPalette(currentPlatform);
        if (colorIndex < 0 || colorIndex >= palette.length) colorIndex = 0;
        return palette[colorIndex];
    }

    private boolean updateHover(double mx, double my) {
        int col = (int) (mx / cellSize);
        int row = (int) (my / cellSize);
        if (col < 0 || col >= currentWidth || row < 0 || row >= currentHeight) {
            col = -1;
            row = -1;
        }
        if (col != hoverCol || row != hoverRow) {
            hoverCol = col;
            hoverRow = row;
            updateMouseInfo();
            return true;
        }
        return false;
    }

    private void updateMouseInfo() {
        if (mouseInfoLabel == null) return;
        String dims = currentWidth + "x" + currentHeight + " px";
        mouseInfoLabel.setText(hoverCol >= 0 && hoverRow >= 0
                ? fmt(msg(bundle, "tool.tileEditor.hoverPos", "{0},{1} - {2}"), hoverCol, hoverRow, dims)
                : dims);
    }

    private void updateDurationInfo() {
        if (durationInfoLabel == null) return;
        int fps = refreshRateFor(currentPlatform);
        int vbl = durationSpinner != null ? durationSpinner.getValue() : 4;
        long ms = Math.round(vbl * 1000.0 / fps);
        durationInfoLabel.setText(fmt(msg(bundle, "tool.tileEditor.durationMs", "≈ {0} ms ({1} Hz)"), ms, fps));
    }

    private int refreshRateFor(PlatformMode platform) {
        if (platform == PlatformMode.ZX_SPECTRUM) return 50;
        if (platform == PlatformMode.CPC) return 50;
        if (platform == PlatformMode.C64) return 50;
        return 60;
    }

    private String fmt(String pattern, Object... args) {
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception ex) {
            return pattern;
        }
    }

    private void updateCode(TextArea outputArea, String symbolName, SpriteAnimationCodeGenerator.Options options) {
        List<int[][]> framePixels = new ArrayList<>();
        List<Integer> durations = new ArrayList<>();
        for (SpriteFrame f : frames) {
            framePixels.add(f.getPixels());
            durations.add(f.getDurationVbls());
        }
        SpriteAnimationCodeGenerator.Platform platform;
        switch (currentPlatform) {
            case ZX_SPECTRUM: platform = SpriteAnimationCodeGenerator.Platform.ZX_SPECTRUM; break;
            case ATARI: platform = SpriteAnimationCodeGenerator.Platform.ATARI; break;
            case C64: platform = SpriteAnimationCodeGenerator.Platform.C64; break;
            case CPC: platform = SpriteAnimationCodeGenerator.Platform.CPC; break;
            case MASTER_SYSTEM: platform = SpriteAnimationCodeGenerator.Platform.MASTER_SYSTEM; break;
            case GAME_GEAR: platform = SpriteAnimationCodeGenerator.Platform.GAME_GEAR; break;
            default: platform = SpriteAnimationCodeGenerator.Platform.GAMEBOY;
        }
        outputArea.setText(animCodeGenerator.generate(platform, framePixels, durations, symbolName, options));
    }

    private void exportCodeToProject(File currentProjectDir, String code, String symbolName, ResourceBundle bundle, Consumer<File> onExported) {
        File initialDir = currentProjectDir;
        if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
            initialDir = lastUsedDirectory;
        }
        if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
            initialDir = new File(System.getProperty("user.home"));
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(msg(bundle, "tool.tileEditor.exportDialogTitle", "Exportar código C al proyecto"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                msg(bundle, "tool.tileEditor.cFilter", "C / Header"), "*.c", "*.h"));
        try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}
        chooser.setInitialFileName(suggestFileName(symbolName));

        File selected = chooser.showSaveDialog(currentWindow());
        if (selected == null) return;

        try {
            Files.writeString(selected.toPath(), code != null ? code : "", StandardCharsets.UTF_8);
            lastUsedDirectory = selected.getParentFile();
            if (onExported != null) onExported.accept(selected);
        } catch (Exception ex) {
            showAlert(msg(bundle, "tool.tileEditor.exportErrorTitle", "Error de exportación"),
                    msg(bundle, "tool.tileEditor.exportErrorBody", "No se pudo exportar: ") + ex.getMessage(),
                    Alert.AlertType.ERROR, currentWindow());
        }
    }

    private String suggestFileName(String symbolName) {
        String raw = symbolName != null ? symbolName.trim() : "";
        if (raw.isEmpty()) return "sprite_data.c";
        return raw + ".c";
    }

    private void showAlert(String title, String content, Alert.AlertType type, Window owner) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showSpriteHelp() {
        String symbol = SpriteAnimationCodeGenerator.sanitizeSymbol(symbolField.getText());
        String up = symbol.toUpperCase(java.util.Locale.ROOT);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(msg(bundle, "tool.tileEditor.helpTitle", "Ayuda: uso de sprites generados"));
        dialog.setHeaderText(fmt(msg(bundle, "tool.tileEditor.helpHeader",
                "Manual según la plataforma. Los ejemplos usan el símbolo {0}."), symbol));
        dialog.initOwner(currentWindow());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType(msg(bundle, "button.close", "Cerrar"), ButtonBar.ButtonData.CANCEL_CLOSE));

        ChoiceBox<PlatformMode> platformPicker = new ChoiceBox<>();
        platformPicker.getItems().addAll(PlatformMode.values());
        platformPicker.setValue(currentPlatform);

        TextArea text = new TextArea();
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefSize(580, 440);

        Runnable updateHelp = () -> text.setText(helpFor(platformPicker.getValue(), symbol, up));
        updateHelp.run();
        platformPicker.valueProperty().addListener((obs, o, n) -> updateHelp.run());

        VBox box = new VBox(8, new HBox(6, new Label(msg(bundle, "tool.tileEditor.platform", "Plataforma:")), platformPicker), text);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private String helpFor(PlatformMode platform, String symbol, String up) {
        if (platform == PlatformMode.ZX_SPECTRUM) return helpZx(symbol, up);
        if (platform == PlatformMode.ATARI) return helpAtari(symbol, up);
        if (platform == PlatformMode.C64) return helpC64(symbol, up);
        if (platform == PlatformMode.CPC) return helpCpc(symbol, up);
        if (platform == PlatformMode.MASTER_SYSTEM) return helpMasterSystem(symbol, up);
        if (platform == PlatformMode.GAME_GEAR) return helpGameGear(symbol, up);
        return helpGameBoy(symbol, up);
    }

    private String helpGameBoy(String symbol, String up) {
        return """
                JUEGO - GAME BOY (GBDK, 2bpp)

                Los datos generados son 2bpp: cada tile 8x8 ocupa 16 bytes. Los "colores"
                son los 4 tonos de gris de la DMG; el índice (0..3) se elige en el editor.

                1) Cargar los tiles y mostrar los sprites:
                     set_sprite_data(0, __UP___TOTAL_TILES, __SYMBOL__);
                     SHOW_SPRITES;

                2) Dibujar el frame actual:
                     set_sprite_tile(0, __SYMBOL___frames[anim_i]);

                3) Avanzar la animación (tras wait_vbl_done()):
                     if (++anim_ticks >= __SYMBOL___durations[anim_i]) {
                         anim_ticks = 0;
                         anim_i = (anim_i + 1) % __UP___FRAME_COUNT;
                         set_sprite_tile(0, __SYMBOL___frames[anim_i]);
                     }

                Con "Tiles 8x16 por columna" activado, cada columna ocupa un par contiguo
                (arriba, abajo) listo para sprites 8x16:
                     set_sprite_tile(0, __SYMBOL___frames[anim_i]);
                     set_sprite_tile(1, __SYMBOL___frames[anim_i] + 1);

                Nota: 4 colores por píxel (2bpp). La DMG solo muestra tonos de verde.
                """.replace("__SYMBOL__", symbol).replace("__UP__", up);
    }

    private String helpZx(String symbol, String up) {
        return """
                JUEGO - ZX SPECTRUM (Z88DK, 1bpp + atributos)

                El exportado incluye por frame: el bitmap 1bpp (_frame_N), un byte de
                atributo por tile 8x8 (_attributes_N: BRIGHT<<6 | INK) y las tablas
                _frames, _attributes y _durations. PAPER = negro (0).

                1) Dibujar el frame en el píxel (px, py), con px/py múltiplos de 8:
                     __SYMBOL___draw(px, py, anim_i);

                   La función escribe los píxeles en 0x4000 y los atributos en 0x5800.

                2) Avanzar la animación:
                     if (++anim_ticks >= __SYMBOL___durations[anim_i]) {
                         anim_ticks = 0;
                         anim_i = (anim_i + 1) % __UP___FRAME_COUNT;
                     }

                Paleta (índice -> color): 1 azul, 2 rojo, 3 magenta, 4 verde, 5 cian,
                6 amarillo, 7 blanco. Los índices 8-15 son variantes BRIGHT. 0 = negro.

                Limitación real del ZX (attribute clash): cada bloque 8x8 usa UN solo
                INK. El editor recolorea el bloque al pintar en otro color. Los bloques
                con tintas mezcladas (p. ej. al abrir archivos antiguos) se marcan en
                rojo hasta recolorearlos.

                Al mover/borrar el sprite, restaura los atributos anteriores de 0x5800
                para no dejar manchas de color.
                """.replace("__SYMBOL__", symbol).replace("__UP__", up);
    }

    private String helpAtari(String symbol, String up) {
        return """
                JUEGO - ATARI 8-BIT (cc65, 1bpp)

                El exportado incluye por frame: bytes 1bpp agrupados de 8 en 8 columnas
                (_frame_N), tabla de punteros _frames y _durations.

                1) Volcar el frame:
                     unsigned char *p = __SYMBOL___frames[anim_i];
                   // copia __UP___BYTES_PER_FRAME bytes a player/missile o bitmap.

                2) El color NO va en los datos: se configura con los registros de color
                   de la máquina (p. ej. COLPM0..COLPM3 para players). El índice elegido
                   en el editor corresponde al registro de color a usar.

                3) Avanzar la animación:
                     if (++anim_ticks >= __SYMBOL___durations[anim_i]) {
                         anim_ticks = 0;
                         anim_i = (anim_i + 1) % __UP___FRAME_COUNT;
                     }

                Notas: sin attribute clash; índices 0-15 referencian los colores de
                la paleta configurada en la consola.
                """.replace("__SYMBOL__", symbol).replace("__UP__", up);
    }

    private String helpC64(String symbol, String up) {
        return """
                JUEGO - COMMODORE 64 (cc65, hires bitmap 1bpp)

                Por frame se exportan: el bitmap 1bpp (_frame_N, un byte por fila de
                cada tile 8x8) y la tabla de color RAM (_colors_N, un byte por tile).
                Los píxeles a 0 usan el fondo (registro 53281); los del tile comparten
                UN solo tono (color RAM $D800).

                1) Volcar el frame a la pantalla bitmap ($2000) y los colores a $D800:
                    memcpy((void*)0x2000, __SYMBOL___frames[anim_i], __UP___BYTES_PER_FRAME);
                    memcpy((void*)0xD800, __SYMBOL___colors[anim_i], __UP___COLOR_BYTES_PER_FRAME);

                2) Avanzar la animación:
                    if (++anim_ticks >= __SYMBOL___durations[anim_i]) {
                        anim_ticks = 0;
                        anim_i = (anim_i + 1) % __UP___FRAME_COUNT;
                    }

                Paleta (índice -> color): 1 blanco, 2 rojo, 3 cian, 4 morado, 5 verde,
                6 azul, 7 amarillo, 8 naranja, 9 marrón, 10 rojo claro, 11 gris oscuro,
                12 gris, 13 verde claro, 14 azul claro, 15 gris claro. 0 = fondo.

                Limitación real (color RAM única por tile 8x8): el editor recolorea el
                bloque al pintar con otro color y marca en rojo los tiles con tonos
                mezclados, igual que el attribute clash del ZX.
                """.replace("__SYMBOL__", symbol).replace("__UP__", up);
    }

    private String helpCpc(String symbol, String up) {
        return """
                JUEGO - AMSTRAD CPC (z88dk, modo 0)

                Cada frame se exporta como 2bpp (4 colores por píxel): cada byte
                contiene 4 píxeles de 2 bits (_frame_N); se incluye la tabla _frames.

                1) Volcar el frame a pantalla (modo 0) o a un buffer:
                    unsigned char *p = __SYMBOL___frames[anim_i]; // __UP___BYTES_PER_FRAME

                2) Elegir los inks 0-3 con el Gate Array (los índices del editor
                   corresponden a los inks activos del modo 0).

                3) Avanzar la animación:
                    if (++anim_ticks >= __SYMBOL___durations[anim_i]) {
                        anim_ticks = 0;
                        anim_i = (anim_i + 1) % __UP___FRAME_COUNT;
                    }

                Notas: modo 0 = 160x200 píxeles, 4 inks simultáneos elegibles de la
                paleta de 27 colores. El color 0 actúa como fondo/paper.
                """.replace("__SYMBOL__", symbol).replace("__UP__", up);
    }

    private String helpMasterSystem(String symbol, String up) {
        return """
                JUEGO - SEGA MASTER SYSTEM (SDCC, 2bpp)

                Cada frame se exporta como tiles 2bpp (4 colores por píxel), formato
                nativo de la VRAM del SMS: cada byte contiene 4 píxeles de 2 bits,
                empezando por el píxel izquierdo en los bits más significativos.

                1) Cargar los tiles en VRAM:
                    SMS_loadTiles(__SYMBOL___frames[anim_i], 0, __UP___BYTES_PER_FRAME);

                2) Avanzar la animación (tras wait for VBlank):
                    if (++anim_ticks >= __SYMBOL___durations[anim_i]) {
                        anim_ticks = 0;
                        anim_i = (anim_i + 1) % __UP___FRAME_COUNT;
                    }

                Notas: los 4 colores por tile se eligen de la paleta CRAM (registros
                de paleta de fondo/objetos). Color 0 = transparente.
                """.replace("__SYMBOL__", symbol).replace("__UP__", up);
    }

    private String helpGameGear(String symbol, String up) {
        return """
                JUEGO - GAME GEAR (SDCC, 4bpp)

                Cada frame se exporta como tiles 4bpp (16 colores por píxel): cada
                byte contiene un par de píxeles (nibble bajo = píxel par, alto = impar)
                (_frame_N).

                1) Volcar el frame a VRAM (equivalente al formato SMS 4bpp):
                    SMS_loadTiles(__SYMBOL___frames[anim_i], 0, __UP___BYTES_PER_FRAME);

                2) Avanzar la animación (tras wait for VBlank):
                    if (++anim_ticks >= __SYMBOL___durations[anim_i]) {
                        anim_ticks = 0;
                        anim_i = (anim_i + 1) % __UP___FRAME_COUNT;
                    }

                Notas: 16 colores por píxel desde la paleta 4096 de Game Gear.
                Color 0 = transparente.
                """.replace("__SYMBOL__", symbol).replace("__UP__", up);
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
