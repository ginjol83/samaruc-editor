package com.retroeditor.controller.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.text.MessageFormat;
import java.io.File;
import java.nio.charset.StandardCharsets;

import org.fxmisc.richtext.CodeArea;

import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.util.FXUtils;

import javafx.beans.InvalidationListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.stage.Window;


public class EditOptionsController {

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    private static final int MAX_SUGGESTIONS = 50;

    private static final List<String> GBDK_KEYWORDS = List.of(
        "set_sprite_data", "set_sprite_tile", "move_sprite", "wait_vbl_done",
        "display_on", "display_off", "set_bkg_data", "set_bkg_tiles",
        "joypad", "JOY_UP", "JOY_DOWN", "JOY_LEFT", "JOY_RIGHT", "JOY_A", "JOY_B",
        "OBP0", "BGP", "NR50", "NR51", "NR52", "UINT8", "INT8", "UINT16", "INT16"
    );

    private static final List<String> CONIO_KEYWORDS = List.of(
        "clrscr", "cputs", "cprintf", "gotoxy", "textattr", "bordercolor",
        "kbhit", "getch", "inkey", "zx_border", "plot", "draw", "delay"
    );

    private static final List<String> MSDOS_KEYWORDS = List.of(
        "int86", "union REGS", "struct SREGS", "segread", "bdos", "sound", "nosound"
    );

    private static final List<String> C_KEYWORDS = List.of(
        "printf", "scanf", "malloc", "free", "strlen", "strcpy", "strcmp",
        "memcpy", "memset", "fopen", "fclose", "fread", "fwrite", "abs", "rand", "srand"
    );

    private final FXUtils fxUtils = new FXUtils();

    private final Popup autoCompletePopup = new Popup();
    private final ListView<String> autoCompleteList = new ListView<>();
    private final EventHandler<KeyEvent> autoCompleteKeyFilter = this::handleAutoCompleteKey;
    private CodeArea autoCompleteArea;
    private int autoCompletePrefixStart;
    private boolean autoCompleteAccepting;

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
     * Comenta o descomenta las líneas actuales (o la selección) según el tipo de archivo.
     * Si todas las líneas seleccionadas ya están comentadas, las descomenta; si no, las comenta.
     * @param tabPane Pestañas del editor.
     */
    public void toggleLineComments(TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;

        Tab tab = fxUtils.getSelectedTab(tabPane);
        String fileName = tab != null ? stripDirtyMarker(tab.getText()) : null;
        String prefix = commentPrefixFor(fileName);
        if (prefix == null || prefix.isEmpty()) return;

        int startParagraph = codeArea.offsetToPosition(codeArea.getSelection().getStart(), CodeArea.Bias.Forward).getMajor();
        int endParagraph = codeArea.offsetToPosition(codeArea.getSelection().getEnd(), CodeArea.Bias.Backward).getMajor();

        // Si hay selección y termina al principio de una línea, ajustamos el párrafo final
        if (codeArea.getSelection().getEnd() > codeArea.getSelection().getStart()) {
            var endPos = codeArea.offsetToPosition(codeArea.getSelection().getEnd(), CodeArea.Bias.Backward);
            if (endPos.getMinor() == 0 && endParagraph > startParagraph) {
                endParagraph--;
            }
        }

        List<String> lines = new ArrayList<>();
        for (int i = startParagraph; i <= endParagraph; i++) {
            lines.add(codeArea.getParagraph(i).getText());
        }

        boolean allCommented = true;
        for (String line : lines) {
            String core = line.substring(leadingWhitespaceLength(line));
            if (!core.isEmpty() && !core.startsWith(prefix)) {
                allCommented = false;
                break;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int leading = leadingWhitespaceLength(line);
            String core = line.substring(leading);

            if (allCommented) {
                // Descomentar
                if (core.startsWith(prefix)) {
                    result.append(line, 0, leading).append(core.substring(prefix.length()));
                } else {
                    result.append(line);
                }
            } else {
                // Comentar (sin duplicar prefijos)
                if (core.startsWith(prefix)) {
                    result.append(line);
                } else {
                    result.append(line, 0, leading).append(prefix).append(core);
                }
            }

            if (i < lines.size() - 1) {
                result.append("\n");
            }
        }

        int startOffset = codeArea.position(startParagraph, 0).toOffset();
        int endOffset = (endParagraph == codeArea.getParagraphs().size() - 1)
            ? codeArea.getLength()
            : codeArea.position(endParagraph + 1, 0).toOffset();

        codeArea.replaceText(startOffset, endOffset, result.toString());

        // Re-seleccionar las líneas afectadas
        int newStart = codeArea.position(startParagraph, 0).toOffset();
        int newEnd = codeArea.position(endParagraph, codeArea.getParagraph(endParagraph).length()).toOffset();
        codeArea.selectRange(newStart, newEnd);
        codeArea.requestFollowCaret();
    }

    private String stripDirtyMarker(String title) {
        if (title != null && title.endsWith("*")) {
            return title.substring(0, title.length() - 1);
        }
        return title;
    }

    private String commentPrefixFor(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".asm") || name.endsWith(".s") || name.endsWith(".inc") || name.endsWith(".z80")) {
            return ";";
        }
        if (name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".cpp") || name.endsWith(".hpp")
            || name.endsWith(".cc") || name.endsWith(".cxx")) {
            return "//";
        }
        return null;
    }

    private int leadingWhitespaceLength(String line) {
        int count = 0;
        while (count < line.length()) {
            char c = line.charAt(count);
            if (c == ' ' || c == '\t') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Muestra el autocompletado básico basado en las palabras del archivo actual.
     * Se abre en la posición del cursor; con Ctrl+Space se reabre en el cursor actual.
     * @param tabPane Pestañas del editor.
     */
    public void showAutoComplete(TabPane tabPane) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;

        hideAutoComplete();

        int caret = codeArea.getCaretPosition();
        String text = codeArea.getText();
        int prefixStart = caret;
        while (prefixStart > 0 && isWordChar(text.charAt(prefixStart - 1))) {
            prefixStart--;
        }
        String prefix = text.substring(prefixStart, caret);

        List<String> suggestions = buildSuggestions(text, prefix);
        if (suggestions.isEmpty()) return;

        autoCompleteArea = codeArea;
        autoCompletePrefixStart = prefixStart;
        autoCompleteAccepting = false;

        autoCompleteList.getItems().setAll(suggestions);
        autoCompleteList.getSelectionModel().select(0);
        autoCompleteList.setPrefSize(260, Math.min(220, suggestions.size() * 24 + 10));
        autoCompleteList.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                String selected = autoCompleteList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    acceptAutoComplete(selected);
                }
            }
        });

        autoCompletePopup.getContent().setAll(autoCompleteList);
        autoCompletePopup.setAutoHide(true);
        autoCompletePopup.setAutoFix(true);
        autoCompletePopup.setOnAutoHide(e -> hideAutoComplete());

        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, autoCompleteKeyFilter);
        codeArea.textProperty().addListener(autoCompleteTextListener);

        showPopupNearCaret(codeArea);
    }

    /**
     * Oculta el popup de autocompletado si está visible y limpia los listeners asociados.
     */
    public void hideAutoComplete() {
        if (autoCompletePopup.isShowing()) {
            autoCompletePopup.hide();
        }
        if (autoCompleteArea != null) {
            autoCompleteArea.removeEventFilter(KeyEvent.KEY_PRESSED, autoCompleteKeyFilter);
            autoCompleteArea.textProperty().removeListener(autoCompleteTextListener);
            autoCompleteArea = null;
        }
    }

    private final InvalidationListener autoCompleteTextListener = obs -> {
        if (autoCompleteArea == null || autoCompleteAccepting) return;

        int caret = autoCompleteArea.getCaretPosition();
        String text = autoCompleteArea.getText();
        if (caret < autoCompletePrefixStart || caret > text.length()
            || autoCompleteArea.getSelection().getLength() > 0) {
            hideAutoComplete();
            return;
        }

        int from = Math.min(autoCompletePrefixStart, text.length());
        int to = Math.min(caret, text.length());
        String prefix = text.substring(from, to);
        if (!prefix.matches("[A-Za-z0-9_]*")) {
            hideAutoComplete();
            return;
        }

        List<String> suggestions = buildSuggestions(text, prefix);
        if (suggestions.isEmpty()) {
            hideAutoComplete();
            return;
        }

        autoCompleteList.getItems().setAll(suggestions);
        autoCompleteList.getSelectionModel().select(0);
        showPopupNearCaret(autoCompleteArea);
    };

    private void handleAutoCompleteKey(KeyEvent event) {
        if (autoCompleteArea == null) return;

        int size = autoCompleteList.getItems().size();
        if (size == 0) {
            hideAutoComplete();
            return;
        }

        if (event.getCode() == KeyCode.DOWN) {
            int selected = autoCompleteList.getSelectionModel().getSelectedIndex();
            autoCompleteList.getSelectionModel().select((selected + 1) % size);
            autoCompleteList.scrollTo(autoCompleteList.getSelectionModel().getSelectedIndex());
            event.consume();
        } else if (event.getCode() == KeyCode.UP) {
            int selected = autoCompleteList.getSelectionModel().getSelectedIndex();
            autoCompleteList.getSelectionModel().select(selected <= 0 ? size - 1 : selected - 1);
            autoCompleteList.scrollTo(autoCompleteList.getSelectionModel().getSelectedIndex());
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
            String selected = autoCompleteList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                acceptAutoComplete(selected);
                event.consume();
            }
        } else if (event.getCode() == KeyCode.ESCAPE) {
            hideAutoComplete();
            event.consume();
        }
    }

    private void acceptAutoComplete(String word) {
        if (autoCompleteArea == null) return;

        CodeArea area = autoCompleteArea;
        int startOffset = autoCompletePrefixStart;
        autoCompleteAccepting = true;
        hideAutoComplete();
        int endOffset = Math.max(startOffset, area.getCaretPosition());
        area.replaceText(startOffset, endOffset, word);
        area.moveTo(startOffset + word.length());
        area.requestFollowCaret();
        autoCompleteAccepting = false;
    }

    private List<String> buildSuggestions(String text, String prefix) {
        Set<String> pool = new HashSet<>();
        pool.addAll(GBDK_KEYWORDS);
        pool.addAll(CONIO_KEYWORDS);
        pool.addAll(MSDOS_KEYWORDS);
        pool.addAll(C_KEYWORDS);

        Map<String, Integer> frequencies = new HashMap<>();
        Matcher matcher = WORD_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 2) {
                frequencies.merge(word, 1, Integer::sum);
                pool.add(word);
            }
        }

        List<String> candidates = new ArrayList<>();
        for (String word : pool) {
            if (prefix.isEmpty()) {
                candidates.add(word);
            } else if (word.length() >= prefix.length()
                && word.regionMatches(true, 0, prefix, 0, prefix.length())) {
                candidates.add(word);
            }
        }

        candidates.sort((a, b) -> {
            int freqA = frequencies.getOrDefault(a, 0);
            int freqB = frequencies.getOrDefault(b, 0);
            int byFrequency = Integer.compare(freqB, freqA);
            if (byFrequency != 0) return byFrequency;
            int byLength = Integer.compare(a.length(), b.length());
            if (byLength != 0) return byLength;
            return a.compareTo(b);
        });

        return candidates.stream().limit(MAX_SUGGESTIONS).collect(Collectors.toList());
    }

    private void showPopupNearCaret(CodeArea codeArea) {
        Bounds caretBounds = codeArea.getCaretBounds().orElse(null);
        double x = caretBounds != null ? caretBounds.getMinX() : 24;
        double y = caretBounds != null ? caretBounds.getMaxY() + 2 : 24;
        Point2D screen = codeArea.localToScreen(x, y);
        Window window = codeArea.getScene() != null ? codeArea.getScene().getWindow() : null;
        if (screen != null && window != null) {
            autoCompletePopup.show(window, screen.getX(), screen.getY());
        } else if (window != null) {
            autoCompletePopup.show(window);
        }
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static class DefinitionResult {
        private final File file;
        private final int line;

        public DefinitionResult(File file, int line) {
            this.file = file;
            this.line = line;
        }

        public File getFile() { return file; }
        public int getLine() { return line; }
    }

    public DefinitionResult findDefinition(String symbol, File currentFile, File projectDir, Map<Tab, File> tabFileMap, com.retroeditor.model.EditorModel editorModel) {
        if (symbol == null || symbol.isBlank()) return null;

        // 1. Current file
        if (currentFile != null) {
            String content = editorModel != null ? editorModel.getFileContent(currentFile) : null;
            if (content != null) {
                int line = findDefinitionLineInText(content, symbol);
                if (line > 0) return new DefinitionResult(currentFile, line);
            }
        }

        // 2. Open tabs
        if (tabFileMap != null) {
            for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
                File f = entry.getValue();
                if (f == null || (currentFile != null && f.equals(currentFile))) continue;
                String content = editorModel != null ? editorModel.getFileContent(f) : null;
                if (content != null) {
                    int line = findDefinitionLineInText(content, symbol);
                    if (line > 0) return new DefinitionResult(f, line);
                }
            }
        }

        // 3. Project directory
        if (projectDir != null && projectDir.isDirectory()) {
            try {
                List<java.nio.file.Path> paths = java.nio.file.Files.walk(projectDir.toPath())
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".asm") || name.endsWith(".s");
                    })
                    .collect(Collectors.toList());

                for (java.nio.file.Path p : paths) {
                    File f = p.toFile();
                    if (currentFile != null && f.equals(currentFile)) continue;
                    try {
                        String content = java.nio.file.Files.readString(p, StandardCharsets.UTF_8);
                        int line = findDefinitionLineInText(content, symbol);
                        if (line > 0) return new DefinitionResult(f, line);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    public boolean isFunctionCallAtCaret(String text, int caret) {
        if (text == null || caret < 0 || caret >= text.length()) return false;
        int end = caret;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        int idx = end;
        while (idx < text.length()) {
            char c = text.charAt(idx);
            if (c == '(') return true;
            if (!Character.isWhitespace(c)) break;
            idx++;
        }
        return false;
    }

    public boolean goToDefinition(TabPane tabPane, File projectDir) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return false;

        String text = codeArea.getText();
        if (text == null || text.isBlank()) return false;

        int caret = codeArea.getCaretPosition();
        String symbol = extractWordAtCaret(text, caret);
        if (symbol == null || symbol.isBlank()) return false;

        Tab selectedTab = fxUtils.getSelectedTab(tabPane);
        // We need tabFileMap if we want full search, but here we can search current text
        int lineNum = findDefinitionLineInText(text, symbol);
        if (lineNum > 0) {
            return goToLine(tabPane, lineNum);
        }

        return false;
    }

    public String extractWordAtCaret(String text, int caret) {
        if (text == null || text.isEmpty() || caret < 0 || caret > text.length()) return null;
        int start = caret;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        int end = caret;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        if (start >= end) return null;
        return text.substring(start, end);
    }

    private int findDefinitionLineInText(String text, String symbol) {
        if (text == null || symbol == null) return -1;
        String[] lines = text.split("\\R");
        Pattern cFuncPattern1 = Pattern.compile("\\b(?:int|void|char|long|float|double|unsigned|static|inline|extern|_Bool|bool|struct\\s+\\w+)\\s+\\**" + Pattern.quote(symbol) + "\\s*\\(");
        Pattern cFuncPattern2 = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\s*\\([^;]*\\)\\s*(?:\\{|;|$)");
        Pattern cProtoPattern = Pattern.compile("\\b(?:int|void|char|long|float|double|unsigned|static|inline|extern|_Bool|bool|struct\\s+\\w+)\\s+\\**" + Pattern.quote(symbol) + "\\s*\\([^;]*\\)\\s*;");
        Pattern asmLabelPattern = Pattern.compile("^\\s*" + Pattern.quote(symbol) + "\\s*:");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (cFuncPattern1.matcher(line).find() || cFuncPattern2.matcher(line).find() || cProtoPattern.matcher(line).find() || asmLabelPattern.matcher(line).find()) {
                return i + 1;
            }
        }
        return -1;
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