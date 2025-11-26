package com.retroeditor.controller.editor;

import org.fxmisc.richtext.CodeArea;

import com.retroeditor.util.FXUtils;

import javafx.event.ActionEvent;
import javafx.scene.control.TabPane;


public class EditOptionsController {

    private final FXUtils fxUtils = new FXUtils();

    /**
     * Deshace la última acción en el área de código actual.
     * @param event Evento de acción.
     * @param tabPane Pestaña del editor.
     */
    public void onUndo(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) codeArea.undo();
    }

    /**
     * Rehace la última acción deshecha en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onRedo(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) codeArea.redo();
    }

    /**
     * Corta el texto seleccionado en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onCut(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) codeArea.cut();
    }

    /**
     * Copia el texto seleccionado en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onCopy(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) codeArea.copy();
    }

    /**
     * Pega el texto del portapapeles en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onPaste(ActionEvent event, TabPane tabPane ) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) codeArea.paste();
    }
}