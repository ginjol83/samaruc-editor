package com.retroeditor.controller.tools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.retroeditor.model.TranslatorModel;
import com.retroeditor.service.gameboy.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/**
 * Herramienta de traducción de texto para ROMs de Game Boy.
 * Permite cargar un ROM, detectar regiones de texto, traducir y exportar.
 * Refactorizado para funcionar como Node integrado en una pestaña del editor.
 */
public class RomTextTranslatorController {

    private GbRomParser romParser;
    private GbFontTable currentFontTable;
    private TextRegionFinder regionFinder;
    private RomPatcher romPatcher;
    private Path loadedRomPath;
    private final ObservableList<TextStringEntry> entries = FXCollections.observableArrayList();
    private Path smtFileDir;
    private Runnable onStateChanged;

    private Label lblRomInfo;
    private Label lblResults;
    private ComboBox<TextEncoding> comboEncoding;
    private ComboBox<String> comboFontTable;
    private TableView<Map.Entry<Integer, String>> detectedMappingsTable;
    private final ObservableList<Map.Entry<Integer, String>> detectedMappings = FXCollections.observableArrayList();

    /**
     * Crea el Node de la interfaz del traductor para integrarlo en una pestaña.
     *
     * @param bundle ResourceBundle para i18n.
     * @return Node con toda la UI del traductor.
     */
    public Node createTranslatorNode(ResourceBundle bundle) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        root.setTop(createTopPanel(bundle));
        root.setCenter(createCenterPanel(bundle));
        root.setBottom(createBottomPanel(bundle));

        return root;
    }

    /**
     * Carga el estado desde un modelo TranslatorModel.
     */
    public void loadFromModel(TranslatorModel model, Path smtFileDirectory) {
        this.smtFileDir = smtFileDirectory;

        if (model == null) return;

        if (model.getFontTable() != null) {
            comboFontTable.setValue(resolveFontTableLabel(model.getFontTable()));
        }
        if (model.getEncodingName() != null) {
            try {
                comboEncoding.setValue(TextEncoding.valueOf(model.getEncodingName()));
            } catch (IllegalArgumentException e) {
                comboEncoding.setValue(TextEncoding.SENTINEL_0x50);
            }
        }

        if (model.getDetectedMappings() != null && !model.getDetectedMappings().isEmpty()) {
            detectedMappings.clear();
            for (Map.Entry<String, String> e : model.getDetectedMappings().entrySet()) {
                try {
                    int key = Integer.parseInt(e.getKey(), 16);
                    detectedMappings.add(Map.entry(key, e.getValue()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (model.getRomPath() != null && smtFileDir != null) {
            Path romPath = smtFileDir.resolve(model.getRomPath()).normalize();
            if (Files.exists(romPath)) {
                loadRomFromPath(romPath);
            }
        }

        if (model.getEntries() != null && !model.getEntries().isEmpty() && romParser != null) {
            entries.clear();
            GbFontTable fontTable = currentFontTable != null ? currentFontTable : GbFontTable.createPokemonTable();
            TextEncoding encoding = getSelectedEncoding();
            for (TranslatorModel.TranslatorEntry te : model.getEntries()) {
                try {
                    int bank = te.getBank();
                    int offset = Integer.parseInt(te.getOffset(), 16);
                    byte[] bytes = parseHexBytes(te.getOriginalBytes());
                    if (bytes != null && bytes.length > 0) {
                        TextStringEntry entry = new TextStringEntry(bank, offset, bytes, te.getDecodedText(), te.getTranslatedText(), encoding);
                        entries.add(entry);
                    }
                } catch (Exception ignored) {
                }
            }
            lblResults.setText(String.format("Resultados: %d strings cargados", entries.size()));
        }
    }

    /**
     * Exporta el estado actual a un TranslatorModel.
     */
    public TranslatorModel exportToModel(Path smtFileDirectory) {
        TranslatorModel model = new TranslatorModel();
        this.smtFileDir = smtFileDirectory;

        if (loadedRomPath != null && smtFileDir != null) {
            model.setRomPath(smtFileDir.relativize(loadedRomPath).toString().replace('\\', '/'));
        }

        if (comboFontTable != null) {
            model.setFontTable(resolveFontTableId(comboFontTable.getValue()));
        }
        if (comboEncoding != null && comboEncoding.getValue() != null) {
            model.setEncoding(comboEncoding.getValue());
        }

        if (!detectedMappings.isEmpty()) {
            java.util.LinkedHashMap<String, String> mappings = new java.util.LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : detectedMappings) {
                mappings.put(String.format("%02X", e.getKey()), e.getValue());
            }
            model.setDetectedMappings(mappings);
        }

        for (TextStringEntry entry : entries) {
            model.addEntry(new TranslatorModel.TranslatorEntry(
                entry.bank(),
                String.format("%04X", entry.offset()),
                bytesToHex(entry.originalBytes()),
                entry.decodedText(),
                entry.translatedText()
            ));
        }

        return model;
    }

    public void setOnStateChanged(Runnable onStateChanged) {
        this.onStateChanged = onStateChanged;
    }

    private void notifyStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    // === UI Creation ===

    private VBox createTopPanel(ResourceBundle bundle) {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(0, 0, 8, 0));

        Button btnLoadRom = new Button(msg(bundle, "tool.romTranslator.loadRom", "Cargar ROM (.gb/.gbc)"));
        btnLoadRom.setOnAction(e -> loadRomFromChooser());

        lblRomInfo = new Label(msg(bundle, "tool.romTranslator.noRom", "No hay ROM cargado"));

        HBox loadRow = new HBox(10, btnLoadRom, lblRomInfo);
        loadRow.setAlignment(Pos.CENTER_LEFT);

        Label lblFontTable = new Label(msg(bundle, "tool.romTranslator.fontTable", "Tabla de fuentes:"));
        comboFontTable = new ComboBox<>();
        comboFontTable.getItems().addAll(
            "Pokémon Red/Blue/Yellow",
            "Dragon Quest",
            "ASCII estándar",
            "Archivo .tbl personalizado..."
        );
        comboFontTable.setValue("Pokémon Red/Blue/Yellow");
        comboFontTable.setOnAction(e -> onFontTableChanged(comboFontTable.getValue()));

        Label lblEncoding = new Label(msg(bundle, "tool.romTranslator.encoding", "Codificación:"));
        comboEncoding = new ComboBox<>();
        comboEncoding.getItems().addAll(TextEncoding.values());
        comboEncoding.setValue(TextEncoding.SENTINEL_0x50);

        HBox configRow = new HBox(10, lblFontTable, comboFontTable, lblEncoding, comboEncoding);
        configRow.setAlignment(Pos.CENTER_LEFT);

        Button btnAutoGen = new Button(msg(bundle, "tool.romTranslator.autoGenTbl", "Generar .tbl desde ROM"));
        btnAutoGen.setOnAction(e -> autoGenerateTbl());

        HBox autoGenRow = new HBox(10, btnAutoGen);
        autoGenRow.setAlignment(Pos.CENTER_LEFT);

        panel.getChildren().addAll(loadRow, configRow, autoGenRow);
        return panel;
    }

    private VBox createCenterPanel(ResourceBundle bundle) {
        VBox panel = new VBox(6);

        Button btnQuickScan = new Button(msg(bundle, "tool.romTranslator.quickScan", "Escaneo rápido"));
        btnQuickScan.setOnAction(e -> quickScan());

        Button btnFullScan = new Button(msg(bundle, "tool.romTranslator.fullScan", "Escaneo completo"));
        btnFullScan.setOnAction(e -> fullScan());

        Button btnClear = new Button(msg(bundle, "tool.romTranslator.clear", "Limpiar"));
        btnClear.setOnAction(e -> entries.clear());

        lblResults = new Label(msg(bundle, "tool.romTranslator.results", "Resultados:"));

        HBox scanRow = new HBox(8, btnQuickScan, btnFullScan, btnClear, lblResults);
        scanRow.setAlignment(Pos.CENTER_LEFT);

        Label lblDetected = new Label(msg(bundle, "tool.romTranslator.detectedMappings", "Mapeos detectados:"));
        detectedMappingsTable = new TableView<>(detectedMappings);
        detectedMappingsTable.setPrefHeight(150);
        detectedMappingsTable.setPlaceholder(new Label(msg(bundle, "tool.romTranslator.noDetectedMappings", "Usa 'Generar .tbl desde ROM' para detectar mapeos")));

        TableColumn<Map.Entry<Integer, String>, String> colTileIdx = new TableColumn<>(msg(bundle, "tool.romTranslator.colTileIndex", "Tile Index"));
        colTileIdx.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            String.format("%02X", data.getValue().getKey())));
        colTileIdx.setPrefWidth(80);

        TableColumn<Map.Entry<Integer, String>, String> colChar = new TableColumn<>(msg(bundle, "tool.romTranslator.colCharacter", "Carácter"));
        colChar.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getValue()));
        colChar.setEditable(true);
        colChar.setCellFactory(col -> new TableCell<>() {
            private final TextField textField = new TextField();
            {
                textField.setOnAction(e -> commitEdit());
                textField.focusedProperty().addListener((obs, old, fresh) -> {
                    if (!fresh) commitEdit();
                });
            }
            private void commitEdit() {
                int idx = getIndex();
                if (idx >= 0 && idx < getTableView().getItems().size()) {
                    Map.Entry<Integer, String> old = getTableView().getItems().get(idx);
                    Map.Entry<Integer, String> updated = Map.entry(old.getKey(), textField.getText());
                    detectedMappings.set(idx, updated);
                }
                commitEdit(textField.getText());
                notifyStateChanged();
            }
            @Override public void startEdit() {
                super.startEdit();
                textField.setText(getItem() != null ? getItem() : "");
                setGraphic(textField);
                textField.requestFocus();
            }
            @Override public void cancelEdit() { super.cancelEdit(); setGraphic(null); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || isEditing()) setGraphic(null);
                else setGraphic(new Label(item));
            }
        });
        colChar.setPrefWidth(120);

        TableColumn<Map.Entry<Integer, String>, String> colHex = new TableColumn<>(msg(bundle, "tool.romTranslator.colHexValue", "Valor HEX"));
        colHex.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            String.format("0x%02X", data.getValue().getKey())));
        colHex.setPrefWidth(80);

        detectedMappingsTable.getColumns().addAll(colTileIdx, colChar, colHex);
        detectedMappingsTable.setEditable(true);

        HBox detectedRow = new HBox(8, lblDetected);
        detectedRow.setAlignment(Pos.CENTER_LEFT);

        Button btnApplyDetected = new Button(msg(bundle, "tool.romTranslator.applyDetected", "Aplicar mapeos detectados"));
        btnApplyDetected.setOnAction(e -> applyDetectedMappings());

        Button btnExportDetectedTbl = new Button(msg(bundle, "tool.romTranslator.exportDetectedTbl", "Exportar detectados como .tbl"));
        btnExportDetectedTbl.setOnAction(e -> exportDetectedAsTbl());

        HBox detectedBtnRow = new HBox(8, btnApplyDetected, btnExportDetectedTbl);
        detectedBtnRow.setAlignment(Pos.CENTER_LEFT);

        VBox detectedSection = new VBox(4, detectedRow, detectedMappingsTable, detectedBtnRow);

        TableView<TextStringEntry> table = new TableView<>(entries);
        table.setPlaceholder(new Label(msg(bundle, "tool.romTranslator.emptyTable", "Carga un ROM y ejecuta un escaneo")));

        TableColumn<TextStringEntry, Number> colBank = new TableColumn<>(msg(bundle, "tool.romTranslator.colBank", "Bank"));
        colBank.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().bank()));
        colBank.setPrefWidth(60);

        TableColumn<TextStringEntry, String> colOffset = new TableColumn<>(msg(bundle, "tool.romTranslator.colOffset", "Offset"));
        colOffset.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            String.format("%04X", data.getValue().offset())));
        colOffset.setPrefWidth(80);

        TableColumn<TextStringEntry, String> colOriginal = new TableColumn<>(msg(bundle, "tool.romTranslator.colOriginal", "Original"));
        colOriginal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().decodedText()));
        colOriginal.setPrefWidth(250);

        TableColumn<TextStringEntry, String> colTranslation = new TableColumn<>(msg(bundle, "tool.romTranslator.colTranslation", "Traducción"));
        colTranslation.setCellValueFactory(data -> {
            String t = data.getValue().translatedText();
            return new javafx.beans.property.SimpleStringProperty(t != null ? t : "");
        });
        colTranslation.setPrefWidth(250);

        TableColumn<TextStringEntry, String> colBytes = new TableColumn<>(msg(bundle, "tool.romTranslator.colBytes", "Bytes"));
        colBytes.setCellValueFactory(data -> {
            byte[] bytes = data.getValue().originalBytes();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(bytes.length, 16); i++) {
                if (i > 0) sb.append(" ");
                sb.append(String.format("%02X", bytes[i] & 0xFF));
            }
            if (bytes.length > 16) sb.append("...");
            return new javafx.beans.property.SimpleStringProperty(sb.toString());
        });
        colBytes.setPrefWidth(200);

        table.getColumns().addAll(colBank, colOffset, colOriginal, colTranslation, colBytes);

        colTranslation.setCellFactory(col -> new TableCell<>() {
            private final TextField textField = new TextField();
            {
                textField.setOnAction(e -> commitEdit());
                textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal) commitEdit();
                });
            }

            private void commitEdit() {
                int idx = getIndex();
                if (idx >= 0 && idx < getTableView().getItems().size()) {
                    TextStringEntry entry = getTableView().getItems().get(idx);
                    TextStringEntry updated = entry.withTranslation(textField.getText());
                    entries.set(idx, updated);
                }
                commitEdit(textField.getText());
                notifyStateChanged();
            }

            @Override
            public void startEdit() {
                super.startEdit();
                String text = getItem();
                textField.setText(text != null ? text : "");
                setGraphic(textField);
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || isEditing()) {
                    setGraphic(null);
                } else {
                    Label lbl = new Label(item);
                    lbl.setWrapText(true);
                    setGraphic(lbl);
                }
            }
        });

        Button btnExportText = new Button(msg(bundle, "tool.romTranslator.exportText", "Exportar texto"));
        btnExportText.setOnAction(e -> exportTextList());

        Button btnImportTranslations = new Button(msg(bundle, "tool.romTranslator.importTranslations", "Importar traducciones"));
        btnImportTranslations.setOnAction(e -> importTranslations());

        Button btnExportTbl = new Button(msg(bundle, "tool.romTranslator.exportTbl", "Exportar .tbl"));
        btnExportTbl.setOnAction(e -> exportTblFile());

        HBox actionRow = new HBox(8, btnExportText, btnImportTranslations, btnExportTbl);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        panel.getChildren().addAll(scanRow, detectedSection, table, actionRow);
        VBox.setVgrow(table, Priority.ALWAYS);
        return panel;
    }

    private HBox createBottomPanel(ResourceBundle bundle) {
        Button btnApplyPatch = new Button(msg(bundle, "tool.romTranslator.applyPatch", "Aplicar parche al ROM"));
        btnApplyPatch.setOnAction(e -> applyPatchToRom());

        Button btnExportIps = new Button(msg(bundle, "tool.romTranslator.exportIps", "Exportar como .ips"));
        btnExportIps.setOnAction(e -> exportIpsPatch());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox panel = new HBox(10, btnApplyPatch, btnExportIps, spacer);
        panel.setPadding(new Insets(8, 0, 0, 0));
        panel.setAlignment(Pos.CENTER_RIGHT);
        return panel;
    }

    // === Actions ===

    private void loadRomFromChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar ROM de Game Boy");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("ROMs de Game Boy", "*.gb", "*.gbc"),
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );
        File selected = chooser.showOpenDialog(null);
        if (selected == null) return;

        loadRomFromPath(selected.toPath());
        notifyStateChanged();
    }

    private void loadRomFromPath(Path romPath) {
        try {
            romParser = GbRomParser.fromFile(romPath);
            loadedRomPath = romPath;
            currentFontTable = GbFontTable.createPokemonTable();
            romPatcher = new RomPatcher(romParser);

            String info = String.format("%s | %d banks | %s | %s",
                romParser.getTitle(),
                romParser.getBankCount(),
                formatSize(romParser.getRomSize()),
                romParser.getCartridgeTypeDescription());
            lblRomInfo.setText(info);
        } catch (Exception ex) {
            showAlert("Error", "No se pudo cargar el ROM: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void onFontTableChanged(String selection) {
        if (selection == null) return;

        if (selection.contains(".tbl")) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar tabla .tbl");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Tablas de fuentes", "*.tbl")
            );
            File selected = chooser.showOpenDialog(null);
            if (selected != null) {
                try {
                    currentFontTable = GbFontTable.loadTblFile(selected.toPath());
                } catch (Exception ex) {
                    showAlert("Error", "No se pudo cargar la tabla: " + ex.getMessage(), Alert.AlertType.ERROR);
                }
            }
        } else {
            currentFontTable = switch (selection) {
                case "Dragon Quest" -> GbFontTable.createDragonQuestTable();
                case "ASCII estándar" -> GbFontTable.createAsciiTable();
                default -> GbFontTable.createPokemonTable();
            };
        }
        notifyStateChanged();
    }

    private void quickScan() {
        if (romParser == null) {
            showAlert("Aviso", "Primero carga un ROM", Alert.AlertType.WARNING);
            return;
        }
        GbTextDecoder decoder = new GbTextDecoder(currentFontTable, getSelectedEncoding());
        regionFinder = new TextRegionFinder(romParser, decoder);

        List<TextStringEntry> found = regionFinder.quickScan();
        entries.clear();
        entries.addAll(found);
        lblResults.setText(String.format("Resultados: %d strings encontrados", found.size()));
        notifyStateChanged();
    }

    private void fullScan() {
        if (romParser == null) {
            showAlert("Aviso", "Primero carga un ROM", Alert.AlertType.WARNING);
            return;
        }
        GbTextDecoder decoder = new GbTextDecoder(currentFontTable, getSelectedEncoding());
        regionFinder = new TextRegionFinder(romParser, decoder);

        List<TextStringEntry> found = regionFinder.scanAllBanks();
        entries.clear();
        entries.addAll(found);
        lblResults.setText(String.format("Resultados: %d strings encontrados", found.size()));
        notifyStateChanged();
    }

    private void exportTextList() {
        if (entries.isEmpty()) {
            showAlert("Aviso", "No hay entries para exportar", Alert.AlertType.WARNING);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar lista de textos");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto", "*.txt", "*.csv"));

        File selected = chooser.showSaveDialog(null);
        if (selected == null) return;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Bank\tOffset\tOriginal\tTranslation\n");
            for (TextStringEntry entry : entries) {
                sb.append(String.format("%d\t%04X\t%s\t%s\n",
                    entry.bank(), entry.offset(), entry.decodedText(), entry.translatedText()));
            }
            Files.writeString(selected.toPath(), sb.toString());
        } catch (Exception ex) {
            showAlert("Error", "No se pudo exportar: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void importTranslations() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar traducciones");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto", "*.txt", "*.csv"));

        File selected = chooser.showOpenDialog(null);
        if (selected == null) return;

        try {
            List<String> lines = Files.readAllLines(selected.toPath());
            for (String line : lines) {
                if (line.startsWith("Bank")) continue;
                String[] parts = line.split("\t", 4);
                if (parts.length < 4) continue;

                int bank = Integer.parseInt(parts[0].trim());
                int offset = Integer.parseInt(parts[1].trim(), 16);
                String translation = parts[3].trim();

                for (int i = 0; i < entries.size(); i++) {
                    TextStringEntry e = entries.get(i);
                    if (e.bank() == bank && e.offset() == offset) {
                        entries.set(i, e.withTranslation(translation));
                        break;
                    }
                }
            }
            notifyStateChanged();
        } catch (Exception ex) {
            showAlert("Error", "No se pudo importar: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void exportTblFile() {
        if (currentFontTable == null) {
            showAlert("Aviso", "No hay tabla de fuentes cargada", Alert.AlertType.WARNING);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar tabla de fuentes .tbl");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Tablas de fuentes", "*.tbl")
        );

        File selected = chooser.showSaveDialog(null);
        if (selected == null) return;

        try {
            currentFontTable.saveTblFile(selected.toPath());
            showAlert("Éxito", "Tabla exportada: " + selected.getName() + " (" + currentFontTable.size() + " mapeos)", Alert.AlertType.INFORMATION);
        } catch (Exception ex) {
            showAlert("Error", "No se pudo exportar la tabla: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void autoGenerateTbl() {
        if (romParser == null) {
            showAlert("Aviso", "Primero carga un ROM", Alert.AlertType.WARNING);
            return;
        }

        try {
            TblGeneratorService generator = new TblGeneratorService(romParser);
            GbFontTable detected = generator.detectTable();

            detectedMappings.setAll(detected.getMappings().entrySet());

            lblResults.setText(String.format("Detección: %d mapeos", detected.size()));
            notifyStateChanged();

            showAlert("Mapeos detectados",
                "Se detectaron " + detected.size() + " mapeos.\n\n"
                + "Revisa la tabla de mapeos detectados y edita los caracteres si es necesario.",
                Alert.AlertType.INFORMATION);
        } catch (Exception ex) {
            showAlert("Error", "No se pudo auto-detectar la tabla: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void applyDetectedMappings() {
        if (detectedMappings.isEmpty()) {
            showAlert("Aviso", "No hay mapeos detectados. Usa 'Generar .tbl desde ROM' primero", Alert.AlertType.WARNING);
            return;
        }

        GbFontTable candidate = new GbFontTable("Auto-detectada");
        detectedMappings.forEach(e -> {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                candidate.put(e.getKey(), e.getValue());
            }
        });
        currentFontTable = candidate;
        comboFontTable.setValue("Auto-detectada");
        showAlert("Éxito", "Mapeos detectados aplicados como tabla de fuentes actual (" + currentFontTable.size() + " mapeos)", Alert.AlertType.INFORMATION);
    }

    private void exportDetectedAsTbl() {
        if (detectedMappings.isEmpty()) {
            showAlert("Aviso", "No hay mapeos detectados para exportar", Alert.AlertType.WARNING);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar .tbl detectado");
        chooser.setInitialFileName("detected.tbl");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Tablas de fuentes", "*.tbl")
        );

        File selected = chooser.showSaveDialog(null);
        if (selected == null) return;

        try {
            GbFontTable table = new GbFontTable("Auto-detectada: " + romParser.getTitle());
            detectedMappings.forEach(e -> {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    table.put(e.getKey(), e.getValue());
                }
            });
            table.saveTblFile(selected.toPath());
            showAlert("Éxito", "Tabla exportada: " + selected.getName() + " (" + table.size() + " mapeos)", Alert.AlertType.INFORMATION);
        } catch (Exception ex) {
            showAlert("Error", "No se pudo exportar la tabla: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void applyPatchToRom() {
        if (romParser == null || entries.isEmpty()) {
            showAlert("Aviso", "No hay datos para parchear", Alert.AlertType.WARNING);
            return;
        }

        List<String> errors = romPatcher.validateTranslations(entries);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Errores de validación:\n");
            errors.forEach(e -> sb.append("• ").append(e).append("\n"));
            showAlert("Errores", sb.toString(), Alert.AlertType.ERROR);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar ROM parcheado");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ROMs de Game Boy", "*.gb", "*.gbc"));

        File selected = chooser.showSaveDialog(null);
        if (selected == null) return;

        try {
            byte[] patched = romPatcher.applyTranslations(entries);
            romPatcher.savePatchedRom(patched, selected.toPath());
        } catch (Exception ex) {
            showAlert("Error", "No se pudo guardar: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void exportIpsPatch() {
        if (romParser == null || entries.isEmpty()) {
            showAlert("Aviso", "No hay datos para exportar", Alert.AlertType.WARNING);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar parche IPS");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Parches IPS", "*.ips"));

        File selected = chooser.showSaveDialog(null);
        if (selected == null) return;

        try {
            romPatcher.saveIpsPatch(entries, selected.toPath());
        } catch (Exception ex) {
            showAlert("Error", "No se pudo exportar: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // === Helpers ===

    private TextEncoding getSelectedEncoding() {
        return comboEncoding != null ? comboEncoding.getValue() : TextEncoding.SENTINEL_0x50;
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String msg(ResourceBundle bundle, String key, String fallback) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String resolveFontTableLabel(String fontTableId) {
        if (fontTableId == null) return "Pokémon Red/Blue/Yellow";
        return switch (fontTableId) {
            case "dragonquest" -> "Dragon Quest";
            case "ascii" -> "ASCII estándar";
            default -> "Pokémon Red/Blue/Yellow";
        };
    }

    private String resolveFontTableId(String label) {
        if (label == null) return "pokemon";
        if (label.contains("Dragon")) return "dragonquest";
        if (label.contains("ASCII")) return "ascii";
        return "pokemon";
    }

    private byte[] parseHexBytes(String hex) {
        if (hex == null || hex.isBlank()) return new byte[0];
        String cleaned = hex.replaceAll("\\s+", "");
        int len = cleaned.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(cleaned.substring(i, i + 2), 16);
        }
        return bytes;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }
}
