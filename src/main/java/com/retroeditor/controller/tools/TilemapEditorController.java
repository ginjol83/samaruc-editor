package com.retroeditor.controller.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ResourceBundle;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Editor visual de Mapas de Tiles (Tilemap Editor) con scroll, capas de colisión y generación de C arrays para Game Boy y ZX Spectrum.
 */
public class TilemapEditorController {

    private static final int MAP_WIDTH = 32;
    private static final int MAP_HEIGHT = 32;
    private static final int TILE_SIZE = 16; // Tamaño de visualización por tile en el editor

    private final int[][] tilemap = new int[MAP_HEIGHT][MAP_WIDTH];
    private final boolean[][] collisions = new boolean[MAP_HEIGHT][MAP_WIDTH];

    private int selectedTileIndex = 0;
    private boolean collisionMode = false;

    private final Canvas mapCanvas = new Canvas(MAP_WIDTH * TILE_SIZE, MAP_HEIGHT * TILE_SIZE);
    private final TextArea codeOutputArea = new TextArea();

    public void openWindow(Stage ownerStage) {
        Stage stage = new Stage();
        stage.initOwner(ownerStage);
        stage.setTitle("Editor de Mapas de Tiles (Tilemap Editor)");

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

        ToggleButton btnToggleCollision = new ToggleButton("Modo Colisión");
        btnToggleCollision.selectedProperty().addListener((obs, oldV, newV) -> collisionMode = newV);

        Button btnExportC = new Button("Exportar Código C");
        btnExportC.setOnAction(e -> exportCode(stage));

        HBox toolbar = new HBox(10, btnClear, btnToggleCollision, btnExportC);
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
        
        ListView<Integer> tileListView = new ListView<>();
        for (int i = 0; i < 64; i++) {
            tileListView.getItems().add(i);
        }
        tileListView.getSelectionModel().select(0);
        tileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) selectedTileIndex = newV;
        });
        tileListView.setPrefSize(120, 380);
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

        stage.setScene(new Scene(root, 760, 680));
        stage.show();
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

        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        for (int r = 0; r < MAP_HEIGHT; r++) {
            for (int c = 0; c < MAP_WIDTH; c++) {
                double x = c * TILE_SIZE;
                double y = r * TILE_SIZE;

                int tile = tilemap[r][c];
                if (tile > 0) {
                    gc.setFill(Color.web("#4e9a06"));
                    gc.fillRect(x + 1, y + 1, TILE_SIZE - 2, TILE_SIZE - 2);
                    gc.setFill(Color.WHITE);
                    gc.fillText(String.valueOf(tile), x + 3, y + 12);
                } else {
                    gc.setStroke(Color.web("#333333"));
                    gc.strokeRect(x, y, TILE_SIZE, TILE_SIZE);
                }

                if (collisions[r][c]) {
                    gc.setFill(Color.web("#cc0000", 0.5));
                    gc.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                }
            }
        }
    }

    private void updateCode() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Tilemap generado por Samaruc Editor\n");
        sb.append("#define MAP_WIDTH  ").append(MAP_WIDTH).append("\n");
        sb.append("#define MAP_HEIGHT ").append(MAP_HEIGHT).append("\n\n");

        sb.append("const unsigned char level_tilemap[MAP_HEIGHT][MAP_WIDTH] = {\n");
        for (int r = 0; r < MAP_HEIGHT; r++) {
            sb.append("    { ");
            for (int c = 0; c < MAP_WIDTH; c++) {
                sb.append(String.format("%3d", tilemap[r][c]));
                if (c < MAP_WIDTH - 1) sb.append(", ");
            }
            sb.append(" }");
            if (r < MAP_HEIGHT - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("};\n\n");

        sb.append("const unsigned char level_collisions[MAP_HEIGHT][MAP_WIDTH] = {\n");
        for (int r = 0; r < MAP_HEIGHT; r++) {
            sb.append("    { ");
            for (int c = 0; c < MAP_WIDTH; c++) {
                sb.append(collisions[r][c] ? "1" : "0");
                if (c < MAP_WIDTH - 1) sb.append(", ");
            }
            sb.append(" }");
            if (r < MAP_HEIGHT - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("};\n");

        codeOutputArea.setText(sb.toString());
    }

    private void exportCode(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C Source", "*.c"));
        chooser.setInitialFileName("map.c");
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), codeOutputArea.getText(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
