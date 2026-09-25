package com.retroeditor.controller.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import com.retroeditor.service.tilemap.TilemapCodeGenerator;
import com.retroeditor.service.tilemap.TilemapPlatform;

/**
 * Editor visual de Mapas de Tiles (Tilemap Editor) con scroll, capas de colisión, tileset PNG y generación de C arrays multiformato.
 */
public class TilemapEditorController {

    private static final int MAP_WIDTH = 32;
    private static final int MAP_HEIGHT = 32;
    private static final int TILE_SIZE = 16; // Tamaño de visualización por tile en el editor

    private final int[][] tilemap = new int[MAP_HEIGHT][MAP_WIDTH];
    private final boolean[][] collisions = new boolean[MAP_HEIGHT][MAP_WIDTH];

    private int selectedTileIndex = 0;
    private boolean collisionMode = false;
    private TilemapPlatform selectedPlatform = TilemapPlatform.GAMEBOY;
    private final TilemapCodeGenerator codeGenerator = new TilemapCodeGenerator();

    private final List<BufferedImage> sourceTileImages = new ArrayList<>();
    private final List<javafx.scene.image.Image> tileImages = new ArrayList<>();
    private final ListView<Integer> tileListView = new ListView<>();

    private Node rootNode;

    private final Canvas mapCanvas = new Canvas(MAP_WIDTH * TILE_SIZE, MAP_HEIGHT * TILE_SIZE);
    private final TextArea codeOutputArea = new TextArea();

    /**
     * Crea la UI de la herramienta para mostrarla como pestaña del editor.
     */
    public Node createNode(File projectDir, ResourceBundle bundle, Consumer<File> onExported) {
        return buildUi(projectDir, bundle, onExported);
    }

    private Node buildUi(File projectDir, ResourceBundle bundle, Consumer<File> onExported) {
        // Controles superiores
        Button btnClear = new Button("Limpiar Mapa");
        btnClear.setOnAction(e -> {
            for (int r = 0; r < MAP_HEIGHT; r++) {
                for (int c = 0; c < MAP_WIDTH; c++) {
                    tilemap[r][c] = 0;
                    collisions[r][c] = false;
                }
            }
            redrawMap();
            updateCode();
        });

        Button btnLoadTileset = new Button("Cargar Tileset (PNG)");
        btnLoadTileset.setOnAction(e -> loadTileset(projectDir));

        ToggleButton btnToggleCollision = new ToggleButton("Modo Colisión");
        btnToggleCollision.selectedProperty().addListener((obs, oldV, newV) -> collisionMode = newV);

        Button btnExportC = new Button("Exportar Código C");
        btnExportC.setOnAction(e -> exportCode(projectDir, onExported));

        ChoiceBox<TilemapPlatform> platformChoice = new ChoiceBox<>(FXCollections.observableArrayList(TilemapPlatform.values()));
        platformChoice.setValue(selectedPlatform);
        platformChoice.setPrefWidth(190);
        platformChoice.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                selectedPlatform = newValue;
                rebuildTileImages();
                redrawMap();
                updateCode();
            }
        });

        HBox toolbar = new HBox(10, new Label("Plataforma:"), platformChoice, btnClear, btnLoadTileset, btnToggleCollision, btnExportC);
        toolbar.setPadding(new Insets(8));

        // Canvas interactivo del mapa con ScrollPane
        redrawMap();
        mapCanvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY(), e.getButton()));
        mapCanvas.setOnMouseDragged(e -> handleCanvasClick(e.getX(), e.getY(), e.getButton()));

        ScrollPane scrollMap = new ScrollPane(mapCanvas);
        scrollMap.setPannable(true);
        scrollMap.setPrefSize(520, 420);

        // Panel de selección de tile / paleta lateral
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(10));
        sidebar.getChildren().add(new Label("Seleccionar Tile:"));
        
        tileListView.getItems().clear();
        for (int i = 0; i < 64; i++) {
            tileListView.getItems().add(i);
        }
        tileListView.getSelectionModel().select(0);
        tileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) selectedTileIndex = newV;
        });

        tileListView.setCellFactory(param -> new ListCell<Integer>() {
            private final ImageView iv = new ImageView();
            {
                iv.setFitWidth(24);
                iv.setFitHeight(24);
                iv.setPreserveRatio(true);
            }
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText("Tile #" + item);
                    if (item < tileImages.size() && tileImages.get(item) != null) {
                        iv.setImage(tileImages.get(item));
                        setGraphic(iv);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        tileListView.setPrefSize(160, 380);
        sidebar.getChildren().add(tileListView);

        // Área de código C generado abajo
        codeOutputArea.setEditable(false);
        codeOutputArea.setPrefHeight(160);
        updateCode();

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollMap);
        root.setRight(sidebar);
        root.setBottom(new VBox(5, new Label("Código C Generado:"), codeOutputArea));
        ((VBox)root.getBottom()).setPadding(new Insets(8));
        rootNode = root;

        return root;
    }

    private Window currentWindow() {
        if (rootNode != null && rootNode.getScene() != null) {
            return rootNode.getScene().getWindow();
        }
        return null;
    }

    private void loadTileset(File projectDir) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Images", "*.png"));
        if (projectDir != null && projectDir.exists()) {
            chooser.setInitialDirectory(projectDir);
        }
        File file = chooser.showOpenDialog(currentWindow());
        if (file != null) {
            try {
                BufferedImage sheet = ImageIO.read(file);
                if (sheet != null) {
                    sourceTileImages.clear();
                    int cols = sheet.getWidth() / 8;
                    int rows = sheet.getHeight() / 8;
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            int tileWidth = Math.min(8, sheet.getWidth() - c * 8);
                            int tileHeight = Math.min(8, sheet.getHeight() - r * 8);
                            sourceTileImages.add(sheet.getSubimage(c * 8, r * 8, tileWidth, tileHeight));
                        }
                    }
                    rebuildTileImages();
                    tileListView.getItems().clear();
                    for (int i = 0; i < tileImages.size(); i++) {
                        tileListView.getItems().add(i);
                    }
                    tileListView.getSelectionModel().select(0);
                    redrawMap();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void rebuildTileImages() {
        tileImages.clear();
        for (BufferedImage source : sourceTileImages) {
            tileImages.add(bufferedImageToFxImage(convertTileForPlatform(source)));
        }
    }

    private BufferedImage convertTileForPlatform(BufferedImage src) {
        if (selectedPlatform != TilemapPlatform.GAMEBOY) {
            return src;
        }

        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] gbPalette = {
            0xFFE0F8D0,
            0xFF88C070,
            0xFF346856,
            0xFF081820
        };

        for (int x = 0; x < src.getWidth(); x++) {
            for (int y = 0; y < src.getHeight(); y++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 128) {
                    dest.setRGB(x, y, 0x00000000);
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int lum = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int idx;
                if (lum > 192) idx = 0;
                else if (lum > 128) idx = 1;
                else if (lum > 64) idx = 2;
                else idx = 3;

                dest.setRGB(x, y, gbPalette[idx]);
            }
        }
        return dest;
    }

    private javafx.scene.image.Image bufferedImageToFxImage(BufferedImage bi) {
        WritableImage wi = new WritableImage(bi.getWidth(), bi.getHeight());
        PixelWriter pw = wi.getPixelWriter();
        for (int x = 0; x < bi.getWidth(); x++) {
            for (int y = 0; y < bi.getHeight(); y++) {
                pw.setArgb(x, y, bi.getRGB(x, y));
            }
        }
        return wi;
    }

    private void handleCanvasClick(double x, double y, MouseButton button) {
        int col = (int) (x / TILE_SIZE);
        int row = (int) (y / TILE_SIZE);

        if (col >= 0 && col < MAP_WIDTH && row >= 0 && row < MAP_HEIGHT) {
            if (collisionMode) {
                collisions[row][col] = (button == MouseButton.PRIMARY);
            } else {
                tilemap[row][col] = (button == MouseButton.PRIMARY) ? selectedTileIndex : 0;
            }
            redrawMap();
            updateCode();
        }
    }

    private void redrawMap() {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();

        // Fondo estilo Game Boy (Lightest green)
        gc.setFill(Color.web("#e0f8d0"));
        gc.fillRect(0, 0, w, h);

        for (int r = 0; r < MAP_HEIGHT; r++) {
            for (int c = 0; c < MAP_WIDTH; c++) {
                double x = c * TILE_SIZE;
                double y = r * TILE_SIZE;

                int tile = tilemap[r][c];
                if (tile > 0 && tile < tileImages.size() && tileImages.get(tile) != null) {
                    gc.drawImage(tileImages.get(tile), x, y, TILE_SIZE, TILE_SIZE);
                    gc.setStroke(Color.web("#346856", 0.25));
                    gc.strokeRect(x, y, TILE_SIZE, TILE_SIZE);
                } else if (tile > 0) {
                    gc.setFill(Color.web("#346856"));
                    gc.fillRect(x + 1, y + 1, TILE_SIZE - 2, TILE_SIZE - 2);
                    gc.setFill(Color.web("#e0f8d0"));
                    gc.fillText(String.valueOf(tile), x + 3, y + 12);
                } else {
                    gc.setStroke(Color.web("#c2e8b0", 0.6));
                    gc.strokeRect(x, y, TILE_SIZE, TILE_SIZE);
                }

                if (collisions[r][c]) {
                    gc.setFill(Color.web("#081820", 0.35));
                    gc.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                }
            }
        }
    }

    private void updateCode() {
        codeOutputArea.setText(codeGenerator.generate(selectedPlatform, tilemap, collisions));
    }

    private void exportCode(File projectDir, Consumer<File> onExported) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C Source", "*.c"));
        chooser.setInitialFileName("map.c");
        if (projectDir != null && projectDir.exists()) {
            chooser.setInitialDirectory(projectDir);
        }
        File file = chooser.showSaveDialog(currentWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), codeOutputArea.getText(), StandardCharsets.UTF_8);
                if (onExported != null) {
                    onExported.accept(file);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
