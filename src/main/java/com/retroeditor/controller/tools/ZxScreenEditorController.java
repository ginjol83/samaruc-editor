package com.retroeditor.controller.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.retroeditor.model.zxscreen.ZxScreenDocument;
import com.retroeditor.service.zxspectrum.ZxScreenAttribute;
import com.retroeditor.service.zxspectrum.ZxScreenCodeGenerator;
import com.retroeditor.service.zxspectrum.ZxScreenEditorModel;
import com.retroeditor.service.zxspectrum.ZxScreenPersistenceService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Editor de la pantalla del ZX Spectrum: pinta la matriz de atributos (32x24 caracteres de
 * 8x8 pixeles) y exporta el array C con el fichero de atributos de 0x5800.
 *
 * El editor no dibuja pixeles: de eso se encarga el conversor PNG a bitmap. Aqui solo se
 * decide, caracter a caracter, que tinta y que fondo usa cada uno.
 */
public class ZxScreenEditorController {

    private static final int ATTR_COLS = ZxScreenEditorModel.ATTR_COLS;
    private static final int ATTR_ROWS = ZxScreenEditorModel.ATTR_ROWS;
    private static final int CELL_SIZE = 8;
    private static final int SCALE = 2;

    private static final String[] COLOR_NAMES = {
        "Negro", "Azul", "Rojo", "Magenta", "Verde", "Cian", "Amarillo", "Blanco"
    };
    private static final Color[] PALETTE_NORMAL = {
        Color.web("#000000"), Color.web("#0000D7"), Color.web("#D70000"), Color.web("#D700D7"),
        Color.web("#00D700"), Color.web("#00D7D7"), Color.web("#D7D700"), Color.web("#D7D7D7")
    };
    private static final Color[] PALETTE_BRIGHT = {
        Color.web("#000000"), Color.web("#0000FF"), Color.web("#FF0000"), Color.web("#FF00FF"),
        Color.web("#00FF00"), Color.web("#00FFFF"), Color.web("#FFFF00"), Color.web("#FFFFFF")
    };

    private final ZxScreenEditorModel model = new ZxScreenEditorModel();
    private final ZxScreenPersistenceService persistenceService = new ZxScreenPersistenceService();
    private final ZxScreenCodeGenerator codeGenerator = new ZxScreenCodeGenerator();
    private final Canvas canvas = new Canvas(ATTR_COLS * CELL_SIZE * SCALE, ATTR_ROWS * CELL_SIZE * SCALE);
    private final TextArea codeOutputArea = new TextArea();

    private File currentFile;
    private boolean dirty;
    private boolean applyingDocument;
    private Runnable stateListener;
    private Node rootNode;

    private int selectedInk = ZxScreenAttribute.WHITE;
    private int selectedPaper = ZxScreenAttribute.BLACK;
    private boolean selectedBright, selectedFlash;

    private ChoiceBox<String> inkChoice;
    private ChoiceBox<String> paperChoice;
    private ToggleButton brightToggle;
    private ToggleButton flashToggle;

    /**
     * Crea la UI de la herramienta para mostrarla como pestaña del editor.
     */
    public Node createNode(File projectDir, ResourceBundle bundle, Consumer<File> onExported) {
        BorderPane root = new BorderPane();
        rootNode = root;
        root.setTop(buildToolbar(projectDir, onExported));
        root.setCenter(buildCanvasView());
        root.setRight(buildPaletteLegend());
        root.setBottom(buildCodeArea());

        codeOutputArea.setEditable(false);
        updateCode();
        redraw();
        return root;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public void setStateListener(Runnable listener) {
        stateListener = listener;
    }

    /**
     * Pestana nueva todavia sin guardar: el proyecto ya tiene nombre, pero no existe en disco.
     */
    public void prepareNewProject(File file) {
        currentFile = file != null ? file.getAbsoluteFile() : null;
        dirty = currentFile != null;
        notifyStateChanged();
    }

    public void updateCurrentFile(File file) {
        currentFile = file != null ? file.getAbsoluteFile() : null;
        notifyStateChanged();
    }

    public boolean openProject(File file) {
        if (file == null || !file.isFile()) return false;
        try {
            applyDocument(persistenceService.load(file.toPath()), file);
            return true;
        } catch (Exception ex) {
            showError("Error al abrir", ex.getMessage());
            return false;
        }
    }

    public boolean saveCurrentProject() {
        if (currentFile == null) return saveProjectAs();
        return saveProjectToFile(currentFile);
    }

    public boolean saveProjectAs() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZX Screen Project (*.zxscreen)", "*.zxscreen"));
        chooser.setInitialFileName("screen.zxscreen");
        File initialDirectory = currentFile != null ? currentFile.getParentFile() : null;
        if (initialDirectory != null && initialDirectory.isDirectory()) {
            chooser.setInitialDirectory(initialDirectory);
        }
        File file = chooser.showSaveDialog(currentWindow());
        return file != null && saveProjectToFile(file);
    }

    private HBox buildToolbar(File projectDir, Consumer<File> onExported) {
        inkChoice = new ChoiceBox<>(FXCollections.observableArrayList(COLOR_NAMES));
        inkChoice.setValue(COLOR_NAMES[selectedInk]);
        inkChoice.setPrefWidth(120);
        inkChoice.valueProperty().addListener((obs, oldValue, newValue) -> {
            int index = indexOfColor(newValue);
            if (index >= 0) selectedInk = index;
        });

        paperChoice = new ChoiceBox<>(FXCollections.observableArrayList(COLOR_NAMES));
        paperChoice.setValue(COLOR_NAMES[selectedPaper]);
        paperChoice.setPrefWidth(120);
        paperChoice.valueProperty().addListener((obs, oldValue, newValue) -> {
            int index = indexOfColor(newValue);
            if (index >= 0) selectedPaper = index;
        });

        brightToggle = new ToggleButton("Brillo");
        brightToggle.selectedProperty().addListener((obs, oldValue, newValue) -> selectedBright = newValue);

        flashToggle = new ToggleButton("Parpadeo");
        flashToggle.selectedProperty().addListener((obs, oldValue, newValue) -> selectedFlash = newValue);

        Button btnOpenProject = new Button("Abrir Proyecto");
        btnOpenProject.setOnAction(e -> openProjectWithChooser(projectDir));

        Button btnSaveProject = new Button("Guardar Proyecto");
        btnSaveProject.setOnAction(e -> saveCurrentProject());

        Button btnClear = new Button("Limpiar Pantalla");
        btnClear.setOnAction(e -> {
            ZxScreenAttribute blank = new ZxScreenAttribute(ZxScreenAttribute.WHITE, ZxScreenAttribute.BLACK, false, false);
            for (int r = 0; r < ATTR_ROWS; r++) {
                for (int c = 0; c < ATTR_COLS; c++) {
                    model.setAttribute(r, c, blank);
                }
            }
            redraw();
            updateCode();
            markDirty();
        });

        Button btnExportC = new Button("Exportar Código C");
        btnExportC.setOnAction(e -> exportCode(projectDir, onExported));

        HBox toolbar = new HBox(10,
                new Label("Tinta:"), inkChoice,
                new Label("Fondo:"), paperChoice,
                brightToggle, flashToggle,
                btnOpenProject, btnSaveProject, btnClear, btnExportC);
        toolbar.setPadding(new Insets(8));
        return toolbar;
    }

    private Node buildCanvasView() {
        canvas.setOnMousePressed(e -> paintAttribute(e.getX(), e.getY(), e.getButton()));
        canvas.setOnMouseDragged(e -> paintAttribute(e.getX(), e.getY(), e.getButton()));

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(canvas);
        scroll.setPannable(true);
        scroll.setPrefSize(560, 440);
        return scroll;
    }

    private Node buildPaletteLegend() {
        VBox legend = new VBox(6);
        legend.setPadding(new Insets(10));
        legend.getChildren().add(new Label("Paleta:"));
        for (int index = 0; index < COLOR_NAMES.length; index++) {
            legend.getChildren().add(buildColorSwatch(index));
        }
        legend.getChildren().add(new Label("Click: tinta"));
        legend.getChildren().add(new Label("Mayús+click: fondo"));
        return legend;
    }

    private Button buildColorSwatch(int index) {
        Button swatch = new Button(COLOR_NAMES[index]);
        swatch.setMaxWidth(Double.MAX_VALUE);
        swatch.setStyle("-fx-background-color: " + toRgb(PALETTE_NORMAL[index]) + "; -fx-text-fill: "
                + (index == ZxScreenAttribute.BLACK ? "#d7d7d7" : "#000000") + ";");
        swatch.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.isShiftDown()) {
                selectedPaper = index;
                if (paperChoice != null) paperChoice.setValue(COLOR_NAMES[index]);
            } else {
                selectedInk = index;
                if (inkChoice != null) inkChoice.setValue(COLOR_NAMES[index]);
            }
            e.consume();
        });
        return swatch;
    }

    private Node buildCodeArea() {
        VBox bottom = new VBox(5, new Label("Atributos C generados (0x5800):"), codeOutputArea);
        bottom.setPadding(new Insets(8));
        return bottom;
    }

    private void paintAttribute(double x, double y, MouseButton button) {
        int col = (int) (x / (CELL_SIZE * SCALE));
        int row = (int) (y / (CELL_SIZE * SCALE));
        if (row < 0 || row >= ATTR_ROWS || col < 0 || col >= ATTR_COLS) return;

        if (button == MouseButton.PRIMARY) {
            model.setAttribute(row, col, new ZxScreenAttribute(selectedInk, selectedPaper, selectedBright, selectedFlash));
        } else {
            model.setAttribute(row, col, new ZxScreenAttribute(selectedPaper, selectedInk, false, false));
        }
        redraw();
        updateCode();
        markDirty();
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.save();
        gc.scale(SCALE, SCALE);
        gc.clearRect(0, 0, ATTR_COLS * CELL_SIZE, ATTR_ROWS * CELL_SIZE);
        for (int row = 0; row < ATTR_ROWS; row++) {
            for (int col = 0; col < ATTR_COLS; col++) {
                ZxScreenAttribute attribute = model.getAttribute(row, col);
                Color[] palette = attribute.bright() ? PALETTE_BRIGHT : PALETTE_NORMAL;
                // El parpadeo intercambia tinta y fondo en el ZX, asi que se previsualiza igual.
                int paperIndex = attribute.flash() ? attribute.ink() : attribute.paper();
                int inkIndex = attribute.flash() ? attribute.paper() : attribute.ink();

                double x = col * CELL_SIZE;
                double y = row * CELL_SIZE;
                gc.setFill(palette[paperIndex & 0x07]);
                gc.fillRect(x, y, CELL_SIZE, CELL_SIZE);
                gc.setFill(palette[inkIndex & 0x07]);
                gc.fillRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);
                gc.setStroke(Color.web("#808080", attribute.bright() || attribute.flash() ? 0.6 : 0.3));
                gc.strokeRect(x + 0.5, y + 0.5, CELL_SIZE - 1, CELL_SIZE - 1);
            }
        }
        gc.restore();
    }

    private void updateCode() {
        codeOutputArea.setText(codeGenerator.generate("screen_attrs", model));
    }

    private ZxScreenDocument exportDocument() {
        ZxScreenDocument document = new ZxScreenDocument();
        document.setAttrCols(ATTR_COLS);
        document.setAttrRows(ATTR_ROWS);
        document.setSelectedInk(selectedInk);
        document.setSelectedPaper(selectedPaper);
        document.setSelectedBright(selectedBright);
        document.setSelectedFlash(selectedFlash);
        List<Integer> attributes = new ArrayList<>(ATTR_COLS * ATTR_ROWS);
        for (byte value : model.getAttributeBytes()) {
            attributes.add(value & 0xFF);
        }
        document.setAttributes(attributes);
        return document;
    }

    private void applyDocument(ZxScreenDocument document, File file) {
        applyingDocument = true;
        try {
            selectedInk = document.getSelectedInk();
            selectedPaper = document.getSelectedPaper();
            selectedBright = document.isSelectedBright();
            selectedFlash = document.isSelectedFlash();
            model.setAttributeBytes(toBytes(document.getAttributes()));
            currentFile = file;

            if (inkChoice != null) inkChoice.setValue(COLOR_NAMES[selectedInk]);
            if (paperChoice != null) paperChoice.setValue(COLOR_NAMES[selectedPaper]);
            if (brightToggle != null) brightToggle.setSelected(selectedBright);
            if (flashToggle != null) flashToggle.setSelected(selectedFlash);
            redraw();
            updateCode();
            dirty = false;
        } finally {
            applyingDocument = false;
        }
        notifyStateChanged();
    }

    private byte[] toBytes(List<Integer> values) {
        byte[] bytes = new byte[ATTR_COLS * ATTR_ROWS];
        int limit = Math.min(bytes.length, values.size());
        for (int i = 0; i < limit; i++) {
            bytes[i] = (byte) (values.get(i) & 0xFF);
        }
        return bytes;
    }

    private boolean saveProjectToFile(File file) {
        try {
            persistenceService.save(file.toPath(), exportDocument());
            currentFile = file;
            dirty = false;
            notifyStateChanged();
            return true;
        } catch (Exception ex) {
            showError("Error al guardar", ex.getMessage());
            return false;
        }
    }

    private void openProjectWithChooser(File projectDir) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZX Screen Project (*.zxscreen)", "*.zxscreen"));
        if (projectDir != null && projectDir.isDirectory()) {
            chooser.setInitialDirectory(projectDir);
        }
        File file = chooser.showOpenDialog(currentWindow());
        if (file != null) openProject(file);
    }

    private void exportCode(File projectDir, Consumer<File> onExported) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C Source", "*.c"));
        chooser.setInitialFileName("screen_attrs.c");
        if (projectDir != null && projectDir.exists()) {
            chooser.setInitialDirectory(projectDir);
        }
        File file = chooser.showSaveDialog(currentWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), codeOutputArea.getText(), StandardCharsets.UTF_8);
            if (onExported != null) {
                onExported.accept(file);
            }
        } catch (Exception ex) {
            showError("Error al exportar", ex.getMessage());
        }
    }

    private void markDirty() {
        if (applyingDocument) return;
        dirty = true;
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        if (stateListener != null) stateListener.run();
    }

    private int indexOfColor(String colorName) {
        for (int index = 0; index < COLOR_NAMES.length; index++) {
            if (COLOR_NAMES[index].equals(colorName)) return index;
        }
        return -1;
    }

    private String toRgb(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private Window currentWindow() {
        if (rootNode != null && rootNode.getScene() != null) {
            return rootNode.getScene().getWindow();
        }
        return null;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "Error desconocido.");
        alert.showAndWait();
    }
}