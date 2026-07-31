package com.retroeditor.controller.editor;

import java.util.Optional;
import java.util.ResourceBundle;
import java.text.MessageFormat;

import org.fxmisc.richtext.CodeArea;

import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.util.FXUtils;

import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
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

        if (codeArea != null) {
            UserActionMonitor.undoExecuted();
            codeArea.undo();
        }
    }

    /**
     * Rehace la última acción deshecha en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onRedo(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) {
            UserActionMonitor.redoExecuted();
            codeArea.redo();
        }
    }

    /**
     * Corta el texto seleccionado en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onCut(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) {
            UserActionMonitor.cutExecuted();
            codeArea.cut();
        }
    }

    /**
     * Copia el texto seleccionado en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onCopy(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) {
            UserActionMonitor.copyExecuted();
            codeArea.copy();
        }
    }

    /**
     * Pega el texto del portapapeles en el área de código actual.
     * @param event
     * @param tabPane
     */
    public void onPaste(ActionEvent event, TabPane tabPane ) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) {
            UserActionMonitor.pasteExecuted();
            codeArea.paste();
        }
    }

    /**
     * Selecciona todo el contenido en el area de codigo actual.
     * @param event
     * @param tabPane
     */
    public void onSelectAll(ActionEvent event, TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);

        if (codeArea != null) {
            UserActionMonitor.selectAllExecuted();
            codeArea.selectAll();
        }
    }

    public void onGoToLine(ActionEvent event, TabPane tabPane, ResourceBundle bundle) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;

        int maxLines = Math.max(1, codeArea.getParagraphs().size());
        int currentLine = codeArea.getCurrentParagraph() + 1;

        TextInputDialog dialog = new TextInputDialog(String.valueOf(currentLine));
        dialog.setTitle(msg(bundle, "dialog.goto.line.title", "Ir a la linea"));
        dialog.setHeaderText(msgFmt(bundle, "dialog.goto.line.header", "Introduce el numero de linea (1-{0})", maxLines));
        dialog.setContentText(msg(bundle, "dialog.goto.line.label", "Linea:"));

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) return;

        String value = result.get() != null ? result.get().trim() : "";
        if (value.isEmpty()) {
            showInvalidLineAlert(bundle, maxLines);
            return;
        }

        final int requestedLine;
        try {
            requestedLine = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            showInvalidLineAlert(bundle, maxLines);
            return;
        }

        if (requestedLine < 1 || requestedLine > maxLines) {
            showInvalidLineAlert(bundle, maxLines);
            return;
        }

        if (goToLine(tabPane, requestedLine)) {
            UserActionMonitor.goToLineExecuted(requestedLine);
        }
    }

    public boolean goToLine(TabPane tabPane, int lineNumber) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return false;

        int maxLines = Math.max(1, codeArea.getParagraphs().size());
        if (lineNumber < 1 || lineNumber > maxLines) return false;

        int targetOffset = codeArea.position(lineNumber - 1, 0).toOffset();
        codeArea.requestFocus();
        codeArea.moveTo(targetOffset);
        codeArea.requestFollowCaret();
        return true;
    }

    private void showInvalidLineAlert(ResourceBundle bundle, int maxLines) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(msg(bundle, "dialog.goto.line.invalid.title", "Linea no valida"));
        alert.setHeaderText(null);
        alert.setContentText(msgFmt(bundle, "dialog.goto.line.invalid.content", "Introduce un numero entre 1 y {0}.", maxLines));
        alert.showAndWait();
    }

    private String msg(ResourceBundle bundle, String key, String fallback) {
        try {
            if (bundle != null && bundle.containsKey(key)) return bundle.getString(key);
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private String msgFmt(ResourceBundle bundle, String key, String fallback, Object... args) {
        return MessageFormat.format(msg(bundle, key, fallback), args);
    }

    /**
     * Mueve la línea actual (o la selección) hacia arriba.
     * @param tabPane
     */
    public void moveLinesUp(TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;

        int startParagraph = codeArea.offsetToPosition(codeArea.getSelection().getStart(), CodeArea.Bias.Forward).getMajor();
        int endParagraph = codeArea.offsetToPosition(codeArea.getSelection().getEnd(), CodeArea.Bias.Backward).getMajor();

        if (startParagraph <= 0) return;

        // Si hay selección y termina al principio de una línea, ajustamos el párrafo final
        if (codeArea.getSelection().getEnd() > codeArea.getSelection().getStart()) {
            var endPos = codeArea.offsetToPosition(codeArea.getSelection().getEnd(), CodeArea.Bias.Backward);
            if (endPos.getMinor() == 0 && endParagraph > startParagraph) {
                endParagraph--;
            }
        }

        StringBuilder contentToMove = new StringBuilder();
        for (int i = startParagraph; i <= endParagraph; i++) {
            contentToMove.append(codeArea.getParagraph(i).getText()).append("\n");
        }

        String targetParagraphText = codeArea.getParagraph(startParagraph - 1).getText();
        
        // Operación atómica de reemplazo
        int startOffset = codeArea.position(startParagraph - 1, 0).toOffset();
        int endOffset = (endParagraph == codeArea.getParagraphs().size() - 1) 
            ? codeArea.getLength() 
            : codeArea.position(endParagraph + 1, 0).toOffset();
        
        codeArea.replaceText(startOffset, endOffset, contentToMove.toString() + targetParagraphText + "\n");
        
        // Re-seleccionar las líneas movidas
        int newStart = codeArea.position(startParagraph - 1, 0).toOffset();
        int newEnd = (endParagraph - 1 >= 0) 
            ? codeArea.position(endParagraph - 1, codeArea.getParagraph(endParagraph - 1).length()).toOffset()
            : codeArea.position(startParagraph - 1, codeArea.getParagraph(startParagraph - 1).length()).toOffset();
        codeArea.selectRange(newStart, newEnd);
        codeArea.requestFollowCaret();
    }

    /**
     * Mueve la línea actual (o la selección) hacia abajo.
     * @param tabPane
     */
    public void moveLinesDown(TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;

        int startParagraph = codeArea.offsetToPosition(codeArea.getSelection().getStart(), CodeArea.Bias.Forward).getMajor();
        int endParagraph = codeArea.offsetToPosition(codeArea.getSelection().getEnd(), CodeArea.Bias.Backward).getMajor();

        // Ajuste si la selección termina al inicio de la siguiente línea
        if (codeArea.getSelection().getEnd() > codeArea.getSelection().getStart()) {
            var endPos = codeArea.offsetToPosition(codeArea.getSelection().getEnd(), CodeArea.Bias.Backward);
            if (endPos.getMinor() == 0 && endParagraph > startParagraph) {
                endParagraph--;
            }
        }

        if (endParagraph >= codeArea.getParagraphs().size() - 1) return;

        StringBuilder contentToMove = new StringBuilder();
        for (int i = startParagraph; i <= endParagraph; i++) {
            contentToMove.append(codeArea.getParagraph(i).getText()).append("\n");
        }

        String targetParagraphText = codeArea.getParagraph(endParagraph + 1).getText();

        // Reemplazo
        int startOffset = codeArea.position(startParagraph, 0).toOffset();
        int endOffset = (endParagraph + 1 == codeArea.getParagraphs().size() - 1)
            ? codeArea.getLength()
            : codeArea.position(endParagraph + 2, 0).toOffset();

        codeArea.replaceText(startOffset, endOffset, targetParagraphText + "\n" + contentToMove.toString());

        // Re-seleccionar
        int newStart = codeArea.position(startParagraph + 1, 0).toOffset();
        int newEnd = codeArea.position(endParagraph + 1, codeArea.getParagraph(endParagraph + 1).length()).toOffset();
        codeArea.selectRange(newStart, newEnd);
        codeArea.requestFollowCaret();
    }
}