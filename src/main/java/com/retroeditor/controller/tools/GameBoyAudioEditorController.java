package com.retroeditor.controller.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retroeditor.service.gameboy.GameBoyAudioPlayer;
import com.retroeditor.service.gameboy.GameBoySoundCodeGenerator;
import com.retroeditor.service.gameboy.ModVgmImportService;

import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
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
 * Editor de audio / efectos de sonido y compositor visual con Timeline, Silencios e Importador Tracker (.mod/.vgm) para Game Boy.
 */
public class GameBoyAudioEditorController {

    private static File lastUsedDirectory = null;

    private final GameBoyAudioPlayer audioPlayer = new GameBoyAudioPlayer();
    private final GameBoySoundCodeGenerator codeGenerator = new GameBoySoundCodeGenerator();
    private final ModVgmImportService modVgmImportService = new ModVgmImportService();

    private static final Map<String, Integer> NOTE_FREQS = new LinkedHashMap<>();
    static {
        NOTE_FREQS.put("Silencio (Rest)", 0);
        NOTE_FREQS.put("C4", 262);
        NOTE_FREQS.put("D4", 294);
        NOTE_FREQS.put("E4", 330);
        NOTE_FREQS.put("F4", 349);
        NOTE_FREQS.put("G4", 392);
        NOTE_FREQS.put("A4", 440);
        NOTE_FREQS.put("B4", 494);
        NOTE_FREQS.put("C5", 523);
        NOTE_FREQS.put("D5", 587);
        NOTE_FREQS.put("E5", 659);
        NOTE_FREQS.put("F5", 698);
        NOTE_FREQS.put("G5", 784);
        NOTE_FREQS.put("A5", 880);
    }

    private static final String[] CHANNELS = {"Square 1", "Square 2", "Wave Channel", "Noise Channel"};

    public void open(Stage owner, File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Editor de Audio, Tracker e Importador .MOD/.VGM (Game Boy)");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ================= TAB 1: EFECTO DE SONIDO (SFX) =================
        Tab tabSfx = new Tab("Efecto de Sonido (SFX)");
        
        ComboBox<String> comboChannel = new ComboBox<>();
        comboChannel.getItems().addAll("Square 1 (Sweep)", "Square 2", "Wave Channel", "Noise Channel");
        comboChannel.setValue("Square 1 (Sweep)");
        comboChannel.setPrefWidth(220);

        Slider sliderFreq = new Slider(64, 2048, 440);
        sliderFreq.setPrefWidth(250);
        sliderFreq.setShowTickLabels(true);
        sliderFreq.setShowTickMarks(true);
        Label lblFreqVal = new Label("440 Hz");
        sliderFreq.valueProperty().addListener((obs, oldV, newV) -> lblFreqVal.setText(newV.intValue() + " Hz"));

        Slider sliderVol = new Slider(0, 15, 10);
        sliderVol.setPrefWidth(250);
        sliderVol.setShowTickLabels(true);
        sliderVol.setShowTickMarks(true);
        sliderVol.setMajorTickUnit(5);
        Label lblVolVal = new Label("10");
        sliderVol.valueProperty().addListener((obs, oldV, newV) -> lblVolVal.setText(String.valueOf(newV.intValue())));

        ComboBox<String> comboDuty = new ComboBox<>();
        comboDuty.getItems().addAll("12.5%", "25%", "50%", "75%");
        comboDuty.setValue("50%");

        Spinner<Integer> spinDuration = new Spinner<>(50, 2000, 300, 50);
        spinDuration.setEditable(true);
        spinDuration.setPrefWidth(100);

        TextField txtSymbol = new TextField("jump_sound");
        CheckBox chkConst = new CheckBox("Incluir const");
        chkConst.setSelected(true);

        TextArea outputAreaSfx = new TextArea();
        outputAreaSfx.setEditable(false);
        outputAreaSfx.setPrefRowCount(12);

        Runnable updateSfxAction = () -> {
            String code = codeGenerator.generateSoundCode(
                comboChannel.getValue(),
                (int) sliderFreq.getValue(),
                (int) sliderVol.getValue(),
                comboDuty.getValue(),
                spinDuration.getValue(),
                chkConst.isSelected()
            );
            outputAreaSfx.setText(code);
        };

        comboChannel.valueProperty().addListener((obs, oldV, newV) -> updateSfxAction.run());
        sliderFreq.valueProperty().addListener((obs, oldV, newV) -> updateSfxAction.run());
        sliderVol.valueProperty().addListener((obs, oldV, newV) -> updateSfxAction.run());
        comboDuty.valueProperty().addListener((obs, oldV, newV) -> updateSfxAction.run());
        spinDuration.valueProperty().addListener((obs, oldV, newV) -> updateSfxAction.run());
        chkConst.selectedProperty().addListener((obs, oldV, newV) -> updateSfxAction.run());

        Button btnPlaySfx = new Button("Probar sonido (Preview)");
        btnPlaySfx.setOnAction(e -> {
            String ch = comboChannel.getValue();
            if (ch != null && ch.contains("Noise")) {
                audioPlayer.playNoise(spinDuration.getValue(), (int) sliderVol.getValue());
            } else {
                audioPlayer.playTone((int) sliderFreq.getValue(), spinDuration.getValue(), (int) sliderVol.getValue(), comboDuty.getValue());
            }
        });

        Button btnCopySfx = new Button("Copiar código");
        btnCopySfx.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(outputAreaSfx.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button btnExportSfx = new Button("Exportar al proyecto");
        btnExportSfx.setOnAction(e -> exportCode(stage, currentProjectDir, txtSymbol.getText(), outputAreaSfx.getText(), onExported));

        VBox formSfx = new VBox(10,
            new HBox(10, new Label("Canal APU:"), comboChannel),
            new HBox(10, new Label("Frecuencia:"), sliderFreq, lblFreqVal),
            new HBox(10, new Label("Volumen (0-15):"), sliderVol, lblVolVal),
            new HBox(10, new Label("Duty Cycle:"), comboDuty),
            new HBox(10, new Label("Duración (ms):"), spinDuration),
            new HBox(10, new Label("Símbolo C:"), txtSymbol, chkConst),
            new HBox(10, btnPlaySfx, btnCopySfx, btnExportSfx)
        );
        formSfx.setPadding(new Insets(12));
        tabSfx.setContent(new VBox(10, formSfx, outputAreaSfx));
        updateSfxAction.run();


        // ================= TAB 2: TRACKER / TIMELINE INTERACTIVO + IMPORTADOR =================
        Tab tabMelody = new Tab("Tracker / Importador .MOD/.VGM");

        ComboBox<String> comboMelodyChannel = new ComboBox<>();
        comboMelodyChannel.getItems().addAll(CHANNELS);
        comboMelodyChannel.setValue("Square 1");

        ComboBox<String> comboNotes = new ComboBox<>();
        comboNotes.getItems().addAll(NOTE_FREQS.keySet());
        comboNotes.setValue("C4");

        Spinner<Integer> spinMelodyDuration = new Spinner<>(50, 1000, 200, 50);
        spinMelodyDuration.setEditable(true);
        spinMelodyDuration.setPrefWidth(90);

        Spinner<Integer> spinMelodyVol = new Spinner<>(0, 15, 12);
        spinMelodyVol.setEditable(true);
        spinMelodyVol.setPrefWidth(80);

        ComboBox<String> comboMelodyDuty = new ComboBox<>();
        comboMelodyDuty.getItems().addAll("12.5%", "25%", "50%", "75%");
        comboMelodyDuty.setValue("50%");

        ObservableList<GameBoyAudioPlayer.NoteStep> melodyNotesList = FXCollections.observableArrayList();
        ListView<GameBoyAudioPlayer.NoteStep> listViewNotes = new ListView<>(melodyNotesList);
        listViewNotes.setPrefHeight(130);

        Canvas trackerCanvas = new Canvas(680, 180);
        GraphicsContext gc = trackerCanvas.getGraphicsContext2D();

        class NoteBlock {
            final GameBoyAudioPlayer.NoteStep step;
            final double x, y, width, height;
            final int index;
            NoteBlock(GameBoyAudioPlayer.NoteStep step, double x, double y, double width, double height, int index) {
                this.step = step; this.x = x; this.y = y; this.width = width; this.height = height; this.index = index;
            }
        }
        final List<NoteBlock> renderedBlocks = new java.util.ArrayList<>();
        double maxDuration = 4000;

        Runnable drawTrackerGrid = () -> {
            renderedBlocks.clear();
            double w = trackerCanvas.getWidth();
            double h = trackerCanvas.getHeight();
            double trackH = h / CHANNELS.length;

            gc.setFill(Color.web("#0f1721"));
            gc.fillRect(0, 0, w, h);

            for (int t = 0; t < CHANNELS.length; t++) {
                double y = t * trackH;
                gc.setFill(t % 2 == 0 ? Color.web("#151f2e") : Color.web("#0f1721"));
                gc.fillRect(0, y, w, trackH);
                gc.setStroke(Color.web("#253348"));
                gc.strokeRect(0, y, w, trackH);

                gc.setFill(Color.web("#94a3b8"));
                gc.fillText(CHANNELS[t], 8, y + 20);
            }

            double pxPerMs = (w - 90) / maxDuration;

            for (int idx = 0; idx < melodyNotesList.size(); idx++) {
                GameBoyAudioPlayer.NoteStep step = melodyNotesList.get(idx);
                String ch = step.getChannel() != null ? step.getChannel() : "Square 1";
                int trackIdx = 0;
                for (int i = 0; i < CHANNELS.length; i++) {
                    if (CHANNELS[i].equalsIgnoreCase(ch) || CHANNELS[i].contains(ch)) {
                        trackIdx = i;
                        break;
                    }
                }

                double x = 90.0 + (step.getStartTimeMs() * pxPerMs);
                double y = trackIdx * trackH + 6;
                double noteW = Math.max(24, step.getDurationMs() * pxPerMs);
                double noteH = trackH - 12;

                renderedBlocks.add(new NoteBlock(step, x, y, noteW, noteH, idx));

                if (trackIdx == 0) gc.setFill(Color.web("#3b82f6"));
                else if (trackIdx == 1) gc.setFill(Color.web("#10b981"));
                else if (trackIdx == 2) gc.setFill(Color.web("#8b5cf6"));
                else gc.setFill(Color.web("#f59e0b"));

                if (listViewNotes.getSelectionModel().getSelectedIndex() == idx) {
                    gc.setStroke(Color.web("#ef4444"));
                    gc.setLineWidth(2);
                } else {
                    gc.setStroke(Color.web("#ffffff"));
                    gc.setLineWidth(1);
                }

                gc.fillRect(x, y, noteW, noteH);
                gc.strokeRect(x, y, noteW, noteH);

                gc.setFill(Color.web("#ffffff"));
                gc.fillText(step.getNoteName(), x + 4, y + 16);
            }
        };

        final boolean[] isResizing = {false};
        final boolean[] isDragging = {false};
        final int[] activeNoteIdx = {-1};
        final double[] dragOffsetX = {0};

        trackerCanvas.setOnMouseMoved(e -> {
            boolean overResizeEdge = false;
            for (NoteBlock nb : renderedBlocks) {
                if (e.getX() >= nb.x + nb.width - 8 && e.getX() <= nb.x + nb.width && e.getY() >= nb.y && e.getY() <= nb.y + nb.height) {
                    overResizeEdge = true;
                    break;
                }
            }
            trackerCanvas.setCursor(overResizeEdge ? Cursor.H_RESIZE : Cursor.DEFAULT);
        });

        trackerCanvas.setOnMousePressed(e -> {
            activeNoteIdx[0] = -1;
            isResizing[0] = false;
            isDragging[0] = false;

            for (NoteBlock nb : renderedBlocks) {
                if (e.getX() >= nb.x && e.getX() <= nb.x + nb.width && e.getY() >= nb.y && e.getY() <= nb.y + nb.height) {
                    activeNoteIdx[0] = nb.index;
                    listViewNotes.getSelectionModel().select(nb.index);
                    drawTrackerGrid.run();

                    if (e.getX() >= nb.x + nb.width - 8) {
                        isResizing[0] = true;
                    } else {
                        isDragging[0] = true;
                        dragOffsetX[0] = e.getX() - nb.x;
                    }
                    break;
                }
            }
        });

        trackerCanvas.setOnMouseDragged(e -> {
            if (activeNoteIdx[0] < 0) return;
            GameBoyAudioPlayer.NoteStep current = melodyNotesList.get(activeNoteIdx[0]);
            double pxPerMs = (trackerCanvas.getWidth() - 90) / maxDuration;

            if (isResizing[0]) {
                int newDur = (int) Math.max(50, (e.getX() - (90.0 + current.getStartTimeMs() * pxPerMs)) / pxPerMs);
                newDur = ((newDur + 25) / 50) * 50;
                if (newDur >= 50 && newDur <= 2000) {
                    melodyNotesList.set(activeNoteIdx[0], new GameBoyAudioPlayer.NoteStep(current.getChannel(), current.getNoteName(), current.getFrequency(), newDur, current.getStartTimeMs(), current.getVolume(), current.getDutyCycle()));
                    drawTrackerGrid.run();
                }
            } else if (isDragging[0]) {
                double trackH = trackerCanvas.getHeight() / CHANNELS.length;
                int targetTrack = (int) (e.getY() / trackH);
                if (targetTrack >= 0 && targetTrack < CHANNELS.length) {
                    String newChannel = CHANNELS[targetTrack];
                    double newX = e.getX() - dragOffsetX[0];
                    int newStartMs = (int) Math.max(0, (newX - 90.0) / pxPerMs);
                    newStartMs = ((newStartMs + 25) / 50) * 50;

                    melodyNotesList.set(activeNoteIdx[0], new GameBoyAudioPlayer.NoteStep(newChannel, current.getNoteName(), current.getFrequency(), current.getDurationMs(), newStartMs, current.getVolume(), current.getDutyCycle()));
                    drawTrackerGrid.run();
                }
            }
        });

        final long[] startTime = {0};
        final boolean[] isPlaying = {false};
        AnimationTimer playheadTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isPlaying[0]) return;
                long elapsedNs = now - startTime[0];
                double elapsedMs = elapsedNs / 1_000_000.0;
                double w = trackerCanvas.getWidth();
                double x = 90.0 + (elapsedMs / maxDuration) * (w - 90.0);

                drawTrackerGrid.run();

                gc.setStroke(Color.web("#ef4444"));
                gc.setLineWidth(2);
                gc.strokeLine(x, 0, x, trackerCanvas.getHeight());

                if (elapsedMs >= maxDuration) {
                    isPlaying[0] = false;
                    stop();
                    drawTrackerGrid.run();
                }
            }
        };

        Button btnAddNote = new Button("Añadir Nota");
        Button btnRemoveNote = new Button("Eliminar Nota");
        Button btnClearMelody = new Button("Limpiar Tracker");
        Button btnImportTracker = new Button("📥 Importar Tracker (.mod / .vgm)...");
        Button btnMoveUp = new Button("▲ Subir");
        Button btnMoveDown = new Button("▼ Bajar");

        btnAddNote.setOnAction(e -> {
            String ch = comboMelodyChannel.getValue();
            String noteName = comboNotes.getValue();
            int freq = NOTE_FREQS.getOrDefault(noteName, 262);
            int dur = spinMelodyDuration.getValue();
            int vol = spinMelodyVol.getValue();
            String duty = comboMelodyDuty.getValue();

            int defaultStart = 0;
            for (GameBoyAudioPlayer.NoteStep step : melodyNotesList) {
                if (step.getChannel().equals(ch)) {
                    int end = step.getStartTimeMs() + step.getDurationMs();
                    if (end > defaultStart) defaultStart = end;
                }
            }

            melodyNotesList.add(new GameBoyAudioPlayer.NoteStep(ch, noteName, freq, dur, defaultStart, vol, duty));
            drawTrackerGrid.run();
        });

        btnRemoveNote.setOnAction(e -> {
            int idx = listViewNotes.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                melodyNotesList.remove(idx);
                drawTrackerGrid.run();
            }
        });

        btnClearMelody.setOnAction(e -> {
            melodyNotesList.clear();
            drawTrackerGrid.run();
        });

        btnImportTracker.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Importar archivo Tracker (.mod / .vgm)");
            chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tracker Files (*.mod, *.vgm, *.vgz)", "*.mod", "*.vgm", "*.vgz"),
                new FileChooser.ExtensionFilter("Todos los archivos (*.*)", "*.*")
            );
            File initialDir = currentProjectDir;
            if (initialDir == null || !initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = new File(System.getProperty("user.home"));
            }
            try { chooser.setInitialDirectory(initialDir); } catch (Exception ignored) {}

            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                List<GameBoyAudioPlayer.NoteStep> imported = modVgmImportService.importFile(selected);
                if (imported != null && !imported.isEmpty()) {
                    melodyNotesList.setAll(imported);
                    drawTrackerGrid.run();
                    showAlert("Importación exitosa", "Se han importado " + imported.size() + " notas del archivo tracker.", Alert.AlertType.INFORMATION, stage);
                } else {
                    showAlert("Aviso", "No se pudieron extraer notas del archivo seleccionado.", Alert.AlertType.WARNING, stage);
                }
            }
        });

        btnMoveUp.setOnAction(e -> {
            int idx = listViewNotes.getSelectionModel().getSelectedIndex();
            if (idx > 0) {
                GameBoyAudioPlayer.NoteStep item = melodyNotesList.remove(idx);
                melodyNotesList.add(idx - 1, item);
                listViewNotes.getSelectionModel().select(idx - 1);
                drawTrackerGrid.run();
            }
        });

        btnMoveDown.setOnAction(e -> {
            int idx = listViewNotes.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < melodyNotesList.size() - 1) {
                GameBoyAudioPlayer.NoteStep item = melodyNotesList.remove(idx);
                melodyNotesList.add(idx + 1, item);
                listViewNotes.getSelectionModel().select(idx + 1);
                drawTrackerGrid.run();
            }
        });

        TextField txtMelodySymbol = new TextField("background_music");
        CheckBox chkMelodyConst = new CheckBox("Incluir const");
        chkMelodyConst.setSelected(true);

        TextArea outputAreaMelody = new TextArea();
        outputAreaMelody.setEditable(false);
        outputAreaMelody.setPrefRowCount(8);

        Runnable updateMelodyCode = () -> {
            String code = codeGenerator.generateMelodyCode(txtMelodySymbol.getText(), melodyNotesList, chkMelodyConst.isSelected());
            outputAreaMelody.setText(code);
        };

        melodyNotesList.addListener((javafx.collections.ListChangeListener.Change<? extends GameBoyAudioPlayer.NoteStep> c) -> updateMelodyCode.run());
        txtMelodySymbol.textProperty().addListener((obs, oldV, newV) -> updateMelodyCode.run());
        chkMelodyConst.selectedProperty().addListener((obs, oldV, newV) -> updateMelodyCode.run());

        Button btnPlayMelody = new Button("▶ Reproducir Tracker (Timeline)");
        btnPlayMelody.setOnAction(e -> {
            audioPlayer.playMelody(melodyNotesList);
            startTime[0] = System.nanoTime();
            isPlaying[0] = true;
            playheadTimer.start();
        });

        Button btnCopyMelody = new Button("Copiar código");
        btnCopyMelody.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(outputAreaMelody.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button btnExportMelody = new Button("Exportar melodía al proyecto");
        btnExportMelody.setOnAction(e -> exportCode(stage, currentProjectDir, txtMelodySymbol.getText(), outputAreaMelody.getText(), onExported));

        VBox formMelody = new VBox(10,
            new HBox(10, new Label("Canal:"), comboMelodyChannel, new Label("Nota:"), comboNotes),
            new HBox(10, new Label("Duración(ms):"), spinMelodyDuration, new Label("Volumen:"), spinMelodyVol, new Label("Duty:"), comboMelodyDuty, btnAddNote),
            new HBox(10, btnRemoveNote, btnImportTracker, btnMoveUp, btnMoveDown, btnClearMelody),
            listViewNotes,
            trackerCanvas,
            new HBox(10, new Label("Símbolo C:"), txtMelodySymbol, chkMelodyConst),
            new HBox(10, btnPlayMelody, btnCopyMelody, btnExportMelody)
        );
        formMelody.setPadding(new Insets(12));
        tabMelody.setContent(new VBox(10, formMelody, outputAreaMelody));
        updateMelodyCode.run();
        drawTrackerGrid.run();

        tabPane.getTabs().addAll(tabSfx, tabMelody);

        stage.setScene(new Scene(new BorderPane(tabPane), 720, 740));
        stage.show();
    }

    private void exportCode(Stage stage, File currentProjectDir, String symbol, String code, Consumer<File> onExported) {
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
        chooser.setInitialFileName((symbol == null || symbol.isBlank() ? "sound" : symbol) + ".c");

        File selected = chooser.showSaveDialog(stage);
        if (selected == null) return;

        try {
            Files.writeString(selected.toPath(), code != null ? code : "", StandardCharsets.UTF_8);
            lastUsedDirectory = selected.getParentFile();
            if (onExported != null) onExported.accept(selected);
        } catch (Exception ex) {
            showAlert("Error de exportación", "No se pudo exportar: " + ex.getMessage(), Alert.AlertType.ERROR, stage);
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type, Stage owner) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
