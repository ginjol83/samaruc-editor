package com.retroeditor.controller.editor;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.fxmisc.richtext.CodeArea;

import com.retroeditor.model.EditorModel;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.util.FXUtils;

import javafx.event.ActionEvent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;

public class FileOptionsController {

    private final FXUtils fxUtils = new FXUtils();

    private File defaultSaveDirectory = null;

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
    public void onOpenFile(ActionEvent event, TabPane tabPane, Map<Tab, File> tabFileMap, EditorModel editorModel, SyntaxHighlighter syntaxHighlighter) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C Files", "*.c", "*.h"));
        File file = fileChooser.showOpenDialog(fxUtils.getStage(tabPane));

        if (file != null) {
            UserActionMonitor.fileOpenDialogShown(file.getName());
            
            for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
                if (file.equals(entry.getValue())) {
                    tabPane.getSelectionModel().select(entry.getKey());
                    return;
                }
            }

            try {
                editorModel.openFile(file);
            } catch (IOException e) {
                UserActionMonitor.errorOccurred("FILE_OPEN", e.getMessage());
                e.printStackTrace();
                return;
            }

            String content = editorModel.getFileContent(file);
            Tab    tab     = fxUtils.addTab(file.getName(), content, syntaxHighlighter, tabPane);

            tabFileMap.put(tab, file);

            // Limpieza cuando el usuario cierra el tab con la X integrada
            tab.setOnClosed(e -> {
                tabFileMap.remove(tab);
                editorModel.closeFile(file);
                UserActionMonitor.fileClosed(file.getName());
            });
        }
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
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C Files", "*.c", "*.h"));
        String defaultName = fxUtils.getDefaultFileName(tab);
        fileChooser.setInitialFileName(defaultName);

        // Establecer el directorio inicial si es válido
        if (defaultSaveDirectory != null && defaultSaveDirectory.exists() && defaultSaveDirectory.isDirectory()) {
            try {
                fileChooser.setInitialDirectory(defaultSaveDirectory);
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
                fxUtils.markTabSaved(tab, file.getName(), codeArea.getText());
                tabFileMap.put(tab, file);
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

}