package com.retroeditor.controller.editor;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.fxmisc.richtext.CodeArea;

import com.retroeditor.model.EditorModel;
import com.retroeditor.service.LocalHistoryService;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.util.FXUtils;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

public class FileOptionsController {

    private final FXUtils fxUtils = new FXUtils();

    private File defaultOpenDirectory = null;
    private File defaultSaveDirectory = null;

    public void setDefaultOpenDirectory(File dir) {
        this.defaultOpenDirectory = dir;
    }

    /**
     * Establece el directorio por defecto para guardar archivos.
     * @param dir Directorio por defecto.
     */
    public void setDefaultSaveDirectory(File dir) {
        this.defaultSaveDirectory = dir;
    }

   
    /**
     * Abre un archivo y lo carga en una nueva pestaña del editor.
     * @param event
     * @param tabPane
     * @param tabFileMap
     * @param editorModel
     * @param syntaxHighlighter
     */
    public File onOpenFile(ActionEvent event, TabPane tabPane, Map<Tab, File> tabFileMap, EditorModel editorModel, SyntaxHighlighter syntaxHighlighter) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C / Assembly / Markdown", "*.c", "*.h", "*.asm", "*.s", "*.md", "*.markdown"));
        if (defaultOpenDirectory != null && defaultOpenDirectory.exists() && defaultOpenDirectory.isDirectory()) {
            try {
                fileChooser.setInitialDirectory(defaultOpenDirectory);
            } catch (Exception ex) {
                // ignore invalid directory
            }
        }
        File file = fileChooser.showOpenDialog(fxUtils.getStage(tabPane));

        if (file != null) {
            UserActionMonitor.fileOpenDialogShown(file.getName());
            
            for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
                if (file.equals(entry.getValue())) {
                    tabPane.getSelectionModel().select(entry.getKey());
                    return file;
                }
            }

            try {
                editorModel.openFile(file);
            } catch (IOException e) {
                UserActionMonitor.errorOccurred("FILE_OPEN", e.getMessage());
                e.printStackTrace();
                return null;
            }

            String content = editorModel.getFileContent(file);
            Tab    tab     = fxUtils.isMarkdownFileName(file.getName())
                ? fxUtils.addMarkdownTab(file.getName(), content, syntaxHighlighter, tabPane)
                : fxUtils.addTab(file.getName(), content, syntaxHighlighter, tabPane);

            tabFileMap.put(tab, file);
            if (file.getParentFile() != null) {
                defaultOpenDirectory = file.getParentFile();
                defaultSaveDirectory = file.getParentFile();
            }

            // Limpieza cuando el usuario cierra el tab con la X integrada
            tab.setOnClosed(e -> {
                tabFileMap.remove(tab);
                editorModel.closeFile(file);
                UserActionMonitor.fileClosed(file.getName());
                // No podemos llamar directamente a persistSessionState de MainController aquí sin una referencia.
                // Sin embargo, MainController ya añade su propio listener en openFileInEditorTab.
            });
        }

        return file;
    }

    /**
     * Guarda el archivo actual.
     * @param event
     * @param tabPane
     * @param tabFileMap
     * @param editorModel
     */
    public boolean onSaveFile(ActionEvent event, TabPane tabPane, Map<Tab, File> tabFileMap, EditorModel editorModel) {
        Tab tab = fxUtils.getSelectedTab(tabPane);

        if (tab == null) return false;

        File file = tabFileMap.get(tab);
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (file != null && codeArea != null) {
            try {
                editorModel.saveFile(file, codeArea.getText());
                LocalHistoryService.recordSnapshot(file, codeArea.getText());
                fxUtils.markTabSaved(tab, file.getName(), codeArea.getText());
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return onSaveAsFile(event, tabPane, tabFileMap, editorModel);
        }
    }

    /**
     * Guarda el archivo actual con un nuevo nombre.
     * @param event
     * @param tabPane
     * @param tabFileMap
     * @param editorModel
     */
    public boolean onSaveAsFile(ActionEvent event, TabPane tabPane, Map<Tab, File> tabFileMap, EditorModel editorModel) {
        Tab tab = fxUtils.getSelectedTab(tabPane);

        if (tab == null) return false;

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C / Assembly / Markdown", "*.c", "*.h", "*.asm", "*.s", "*.md", "*.markdown"));
        String defaultName = fxUtils.getDefaultFileName(tab);
        fileChooser.setInitialFileName(defaultName);

        // Establecer el directorio inicial si es válido
        File initialDirectory = defaultSaveDirectory != null ? defaultSaveDirectory : defaultOpenDirectory;
        if (initialDirectory != null && initialDirectory.exists() && initialDirectory.isDirectory()) {
            try {
                fileChooser.setInitialDirectory(initialDirectory);
            } catch (Exception ex) {
                // Ignorar excepciones relacionadas con permisos o accesos no válidos
            }
        }

        File file = fileChooser.showSaveDialog(fxUtils.getStage(tabPane));
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (file != null && codeArea != null) {
            UserActionMonitor.fileSaveAsDialogShown(file.getName());
            try {
                editorModel.saveFile(file, codeArea.getText());
                LocalHistoryService.recordSnapshot(file, codeArea.getText());
                fxUtils.markTabSaved(tab, file.getName(), codeArea.getText());
                tabFileMap.put(tab, file);
                if (file.getParentFile() != null) {
                    defaultSaveDirectory = file.getParentFile();
                    if (defaultOpenDirectory == null) {
                        defaultOpenDirectory = file.getParentFile();
                    }
                }
                return true;
            } catch (IOException e) {
                UserActionMonitor.errorOccurred("FILE_SAVE_AS", e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        return false;
    }

    /**
     * Cierra el archivo actual y la pestaña asociada.
     * @param event
     * @param tabPane
     * @param tabFileMap
     * @param editorModel
     */
    public void onCloseFile(ActionEvent event, TabPane tabPane, Map<Tab, File> tabFileMap, EditorModel editorModel) {
        Tab tab = fxUtils.getSelectedTab(tabPane);

        if (tab != null) {
            fxUtils.closeTab(tab, tabPane);
            File file = tabFileMap.remove(tab);

            if (file != null) {
                editorModel.closeFile(file);
            }
        }
    }

    /**
     * Muestra el historial local de snapshots para el archivo actual y permite restaurar una versión anterior.
     * @param event Evento de acción.
     * @param tabPane Pestañas del editor.
     * @param tabFileMap Mapa de pestañas a archivos.
     */
    public void onShowLocalHistory(ActionEvent event, TabPane tabPane, Map<Tab, File> tabFileMap) {
        Tab tab = fxUtils.getSelectedTab(tabPane);
        if (tab == null) return;
        File file = tabFileMap.get(tab);
        if (file == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(fxUtils.getStage(tabPane));
            alert.setTitle("Historial Local");
            alert.setHeaderText(null);
            alert.setContentText("El archivo actual no está guardado en disco.");
            alert.showAndWait();
            return;
        }

        var snapshots = LocalHistoryService.getSnapshots(file);
        if (snapshots.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(fxUtils.getStage(tabPane));
            alert.setTitle("Historial Local");
            alert.setHeaderText(null);
            alert.setContentText("No hay snapshots previos guardados para este archivo.");
            alert.showAndWait();
            return;
        }

        Dialog<LocalHistoryService.HistoryEntry> dialog = new Dialog<>();
        dialog.initOwner(fxUtils.getStage(tabPane));
        dialog.setTitle("Historial Local — " + file.getName());
        dialog.setHeaderText("Selecciona un snapshot anterior para restaurar:");

        ButtonType restoreButtonType = new ButtonType("Restaurar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(restoreButtonType, ButtonType.CANCEL);

        ListView<LocalHistoryService.HistoryEntry> listView = new ListView<>();
        listView.getItems().setAll(snapshots);
        listView.setPrefSize(450, 250);

        TextArea previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefSize(450, 250);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                previewArea.setText(LocalHistoryService.loadSnapshotContent(newV.snapshotFile()));
            }
        });
        if (!snapshots.isEmpty()) {
            listView.getSelectionModel().select(0);
        }

        HBox content = new HBox(10, listView, previewArea);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == restoreButtonType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        java.util.Optional<LocalHistoryService.HistoryEntry> result = dialog.showAndWait();
        result.ifPresent(entry -> {
            String contentStr = LocalHistoryService.loadSnapshotContent(entry.snapshotFile());
            CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
            if (codeArea != null) {
                codeArea.replaceText(contentStr);
            }
        });
    }

}