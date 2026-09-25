package com.retroeditor.util;

import javafx.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.service.ExternalFileChangeService;
import com.retroeditor.service.SymbolBreadcrumbService;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.view.UIElements;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class FXUtils {

    public static final String TAB_PROP_BASE_TITLE = "editor.baseTitle";
    public static final String TAB_PROP_FILE_ICON = "editor.fileIcon";
    private static final String TAB_PROP_BASE_TEXT = "editor.baseText";
    private static final String TAB_PROP_CODE_AREA = "editor.codeArea";
    private static final String TAB_PROP_MARKDOWN_WEBVIEW = "editor.markdownWebView";
    private static final String TAB_PROP_MARKDOWN_PREVIEW = "editor.markdownPreviewVisible";
    private static final String TAB_PROP_DISK_FINGERPRINT = "editor.diskFingerprint";
    private static final String EDITOR_STYLESHEET_MODERN = "/css/c-syntax.css";
    private static final String EDITOR_STYLESHEET_CLASSIC = "/css/c-syntax-classic.css";

    private enum ProjectTemplate {
        EMPTY,
        SPECTRUM,
        SPECTRUM_SCREEN,
        GAMEBOY,
        GAMEBOY_SPRITE,
        TRANSLATOR,
        GITHUB,
        CPC,
        GCC,
        GCC_CALC,
        CC65,
        CC65_C64,
        GENERIC,
        ASM,
        MAKEFILE
    }

    private static class ProjectCreationOptions {
        private final ProjectTemplate template;
        private final boolean createReadme;
        private final boolean createGitignore;

        private ProjectCreationOptions(ProjectTemplate template, boolean createReadme, boolean createGitignore) {
            this.template = template;
            this.createReadme = createReadme;
            this.createGitignore = createGitignore;
        }
    }

    private boolean lastProjectCreationCancelled = false;
    private ProjectTemplate lastCreatedProjectTemplate = null;
    private boolean lastCreateReadmeSelected = true;
    private boolean lastCreateGitignoreSelected = true;
    private String projectReadmeLanguage = "en";
    private String editorAppearance = ConfigModel.EDITOR_APPEARANCE_MODERN_DARK;
    private java.util.function.BiConsumer<String, CodeArea> doubleClickHandler;
    private java.util.function.Consumer<CodeArea> editorThemeApplier;
    private Label cursorStatusLabel;

    public void setCursorStatusLabel(Label label) {
        this.cursorStatusLabel = label;
    }

    /** Refresca la posición del cursor en la barra de estado a partir de la pestaña activa. */
    public void updateCursorStatus(TabPane tabPane) {
        if (cursorStatusLabel == null || tabPane == null) return;
        CodeArea area = getCurrentCodeArea(tabPane);
        if (area == null) {
            cursorStatusLabel.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder()
                .append("Ln ").append(area.getCurrentParagraph() + 1)
                .append(", Col ").append(area.getCaretColumn() + 1);
        if (area.getSelection().getLength() > 0) {
            sb.append(", Sel ").append(area.getSelection().getLength());
        }
        cursorStatusLabel.setText(sb.toString());
    }

    public void setDoubleClickHandler(java.util.function.BiConsumer<String, CodeArea> handler) {
        this.doubleClickHandler = handler;
    }

    /**
     * Permite a MainController proporcionar el CSS del modo activo sin que
     * FXUtils dependa de ThemeService/ThemeCssGenerator: al crear cada
     * {@code CodeArea} se invoca al applier, que resuelve el modo activo y
     * aplica la hoja generada. Si no hay applier se usa la hoja estática.
     */
    public void setEditorThemeApplier(java.util.function.Consumer<CodeArea> applier) {
        this.editorThemeApplier = applier;
    }

    private void applyEditorThemeToNewCodeArea(CodeArea codeArea) {
        if (codeArea == null) return;
        if (editorThemeApplier != null) {
            editorThemeApplier.accept(codeArea);
        } else {
            applyEditorAppearance(codeArea);
        }
    }

    private void attachCodeAreaDoubleClickHandler(CodeArea codeArea) {
        codeArea.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && doubleClickHandler != null) {
                int caret = codeArea.getCaretPosition();
                String text = codeArea.getText();
                int end = caret;
                while (end < text.length() && isWordCharStatic(text.charAt(end))) {
                    end++;
                }
                int idx = end;
                boolean isFuncCall = false;
                while (idx < text.length()) {
                    char c = text.charAt(idx);
                    if (c == '(') {
                        isFuncCall = true;
                        break;
                    }
                    if (!Character.isWhitespace(c)) break;
                    idx++;
                }
                if (isFuncCall) {
                    String symbol = extractWordAtCaretStatic(text, caret);
                    if (symbol != null && !symbol.isBlank()) {
                        doubleClickHandler.accept(symbol, codeArea);
                        event.consume();
                    }
                }
            }
        });
    }

    private static boolean isWordCharStatic(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static String extractWordAtCaretStatic(String text, int caret) {
        if (text == null || text.isEmpty() || caret < 0 || caret > text.length()) return null;
        int start = caret;
        while (start > 0 && isWordCharStatic(text.charAt(start - 1))) {
            start--;
        }
        int end = caret;
        while (end < text.length() && isWordCharStatic(text.charAt(end))) {
            end++;
        }
        if (start >= end) return null;
        return text.substring(start, end);
    }

    public boolean wasLastProjectCreationCancelled() {
        return lastProjectCreationCancelled;
    }

    public boolean wasLastCreatedProjectGameBoyTemplate() {
        return lastCreatedProjectTemplate == ProjectTemplate.GAMEBOY
            || lastCreatedProjectTemplate == ProjectTemplate.GAMEBOY_SPRITE;
    }

    public boolean wasLastCreatedProjectSpectrumTemplate() {
        return lastCreatedProjectTemplate == ProjectTemplate.SPECTRUM
            || lastCreatedProjectTemplate == ProjectTemplate.SPECTRUM_SCREEN;
    }

    public boolean wasLastCreatedProjectCpcTemplate() {
        return lastCreatedProjectTemplate == ProjectTemplate.CPC;
    }

    public boolean wasLastCreatedProjectCc65Template() {
        return lastCreatedProjectTemplate == ProjectTemplate.CC65;
    }

    public boolean wasLastCreatedProjectCc65C64Template() {
        return lastCreatedProjectTemplate == ProjectTemplate.CC65_C64;
    }

    public boolean wasLastCreatedProjectTranslatorTemplate() {
        return lastCreatedProjectTemplate == ProjectTemplate.TRANSLATOR;
    }

    /** True si el usuario eligió "Desde repositorio GitHub..." en el asistente. */
    public boolean wasLastCreatedProjectGitHubTemplate() {
        return lastCreatedProjectTemplate == ProjectTemplate.GITHUB;
    }

    /**
     * Obtiene un nombre de archivo predeterminado para guardar en funciÃ³n del tÃ­tulo de la pestaÃ±a.
     * @param tab PestaÃ±a actual.
     * @return Nombre de archivo sugerido.
     */
    public String getDefaultFileName(Tab tab) {
        if (tab == null) return "";

        Object baseTitle = tab.getProperties().get(TAB_PROP_BASE_TITLE);
        if (baseTitle instanceof String && !((String) baseTitle).isBlank()) {
            return (String) baseTitle;
        }

        String title = tab.getText();
        return title != null && title.endsWith("*") ? title.substring(0, title.length() - 1) : title;
    }

    /**
     * Cierra una pestaÃ±a especÃ­fica en el TabPane.
     * @param tab PestaÃ±a a cerrar.
     * @param tabPane TabPane que contiene la pestaÃ±a.
     */
    public void closeTab(Tab tab, TabPane tabPane)   { tabPane.getTabs().remove(tab); }
    
    /**
     * Obtiene la pestaÃ±a actualmente seleccionada en el TabPane.
     * @param tabPane TabPane que contiene las pestaÃ±as.
     * @return PestaÃ±a actualmente seleccionada.
     */
    public Tab getSelectedTab(TabPane tabPane)       { return tabPane.getSelectionModel().getSelectedItem(); }

    /**
     * AÃ±ade una nueva pestaÃ±a con un CodeArea y resaltado de sintaxis.
     * @param title TÃ­tulo de la pestaÃ±a.
     * @param content Contenido inicial del CodeArea.
     * @param syntaxHighlighter Resaltador de sintaxis a utilizar.
     * @param tabPane TabPane donde se aÃ±adirÃ¡ la nueva pestaÃ±a.
     * @return La nueva pestaÃ±a creada.
     */
    public Tab addTab(String title, String content, SyntaxHighlighter syntaxHighlighter, TabPane tabPane) {
        CodeArea codeArea = new FoldingCodeArea();
        VirtualizedScrollPane<CodeArea> editorScrollPane = new VirtualizedScrollPane<>(codeArea);
        CodeMinimapPane minimap = new CodeMinimapPane(codeArea);
        BorderPane editorLayout = new BorderPane();
        editorLayout.setCenter(editorScrollPane);
        editorLayout.setRight(minimap);
        String initialText = content != null ? content : "";

        // Habilita la funciÃ³n de numeraciÃ³n de lÃ­neas para los pÃ¡rrafos. 
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.replaceText(initialText);

        // Aplica el resaltado inicial de sintaxis
        StyleSpans<Collection<String>> initialSpans = syntaxHighlighter.computeHighlighting(initialText, title);
        codeArea.setStyleSpans(0, initialSpans);

        final Tab tab = new Tab(title, editorLayout);
        tab.getProperties().put(TAB_PROP_BASE_TITLE, title != null ? title : "");
        tab.getProperties().put(TAB_PROP_BASE_TEXT, initialText);
        tab.getProperties().put(TAB_PROP_CODE_AREA, codeArea);
        applyFileTypeIcon(tab, title);
        recordDiskFingerprint(tab, initialText);

        // Barra de migas de pan (breadcrumbs) sobre el editor
        HBox breadcrumbBar = buildBreadcrumbBar();
        editorLayout.setTop(breadcrumbBar);

        final CodeArea area = codeArea;
        final AtomicReference<StyleSpans<Collection<String>>> baseSpans = new AtomicReference<>(initialSpans);
        final AtomicInteger baseLength = new AtomicInteger(initialText.length());
        final String[] lastHighlightWord = {null};
        final int[] lastHighlightPair = {-1, -1};
        final int[] lastBreadcrumbLine = {-1};

        Runnable refreshExtras = () -> {
            try {
                if (area.getText().length() != baseLength.get()) return;
                int cp = area.getCaretPosition();
                String text = area.getText();
                int[] pair = findMatchingBracket(text, cp);
                String word = extractWordAtCaretStatic(text, cp);
                boolean sameWord = word == null ? lastHighlightWord[0] == null : word.equals(lastHighlightWord[0]);
                boolean samePair = pair == null
                        ? (lastHighlightPair[0] == -1 && lastHighlightPair[1] == -1)
                        : (pair[0] == lastHighlightPair[0] && pair[1] == lastHighlightPair[1]);
                if (sameWord && samePair) return;
                lastHighlightWord[0] = word;
                lastHighlightPair[0] = pair != null ? pair[0] : -1;
                lastHighlightPair[1] = pair != null ? pair[1] : -1;

                List<TextStyleRange> extras = new ArrayList<>();
                if (pair != null) {
                    extras.add(new TextStyleRange(pair[0], pair[1], List.of("bracket-match")));
                }
                if (word != null && word.length() >= 2) {
                    int wi = text.indexOf(word);
                    while (wi >= 0) {
                        extras.add(new TextStyleRange(wi, wi + word.length(), List.of("word-occurrence")));
                        wi = text.indexOf(word, wi + word.length());
                    }
                }
                StyleSpans<Collection<String>> combined = applyExtraStyles(baseSpans.get(), extras);
                area.setStyleSpans(0, combined);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };

        Runnable refreshBaseAndExtras = () -> {
            try {
                StyleSpans<Collection<String>> base = syntaxHighlighter.computeHighlighting(area.getText(), title);
                baseSpans.set(base);
                baseLength.set(area.getText().length());
                lastHighlightWord[0] = null;
                lastHighlightPair[0] = -1;
                lastHighlightPair[1] = -1;

                int maxP = area.getParagraphs().size();
                for (int p = 0; p < maxP; p++) {
                    area.setParagraphStyle(p, java.util.Collections.emptyList());
                }

                var diags = com.retroeditor.service.StaticAnalysisService.analyzeCode(area.getText());
                for (var d : diags) {
                    if (d.isError()) {
                        int pIdx = d.line() - 1;
                        if (pIdx >= 0 && pIdx < maxP) {
                            area.setParagraphStyle(pIdx, java.util.Collections.singleton("compile-error-line"));
                        }
                    }
                }
                refreshExtras.run();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };

        // Reduce el tiempo de ejecuciÃ³n de las ediciones posteriores para evitar actualizaciones de estilo demasiado frecuentes
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(250))
                .subscribe(ignore -> refreshBaseAndExtras.run());

        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            refreshExtras.run();
            int line = area.getCurrentParagraph() + 1;
            if (line != lastBreadcrumbLine[0]) {
                lastBreadcrumbLine[0] = line;
                updateBreadcrumbBar(breadcrumbBar, area, baseTitleOf(tab));
            }
            updateCursorStatus(tabPane, tab);
        });

        codeArea.textProperty().addListener((obs, oldText, newText) -> updateTabDirtyIndicator(tab, codeArea));

        applyEditorThemeToNewCodeArea(codeArea);
        attachCodeAreaDoubleClickHandler(codeArea);
        MultiCursorManager.attach(codeArea);
        attachProfessionalEditingKeys(codeArea);

        updateBreadcrumbBar(breadcrumbBar, area, baseTitleOf(tab));
        updateCursorStatus(tabPane, tab);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        return tab;
    }

    /**
     * Aplica el icono de tipo de archivo a una pestaña de editor, guardándolo
     * como propiedad para que el indicador git pueda combinarlo sin borrarlo.
     * El graphic de la pestaña es un contenedor {@code HBox} que el marcador git
     * reutiliza para insertar el punto de cambios sin destruir el icono.
     */
    public void applyFileTypeIcon(Tab tab, String fileName) {
        if (tab == null) return;
        org.kordamp.ikonli.javafx.FontIcon icon = FileTypeIcons.iconForFileName(fileName);
        javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(3, icon);
        container.getStyleClass().add("file-icon-container");
        tab.getProperties().put(TAB_PROP_FILE_ICON, container);
        tab.setGraphic(container);
    }

    /** Icono de tipo de archivo de una pestaña (contenedor HBox) si lo tiene. */
    public javafx.scene.layout.HBox getFileIconContainer(Tab tab) {
        if (tab == null) return null;
        Object stored = tab.getProperties().get(TAB_PROP_FILE_ICON);
        return stored instanceof javafx.scene.layout.HBox box ? box : null;
    }

    public Tab addMarkdownTab(String title, String content, SyntaxHighlighter syntaxHighlighter, TabPane tabPane) {
        CodeArea codeArea = new FoldingCodeArea();
        VirtualizedScrollPane<CodeArea> editorScrollPane = new VirtualizedScrollPane<>(codeArea);
        WebView previewView = new WebView();
        String initialText = content != null ? content : "";

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.replaceText(initialText);
        applyEditorThemeToNewCodeArea(codeArea);
        attachCodeAreaDoubleClickHandler(codeArea);
        MultiCursorManager.attach(codeArea);
        codeArea.setStyleSpans(0, syntaxHighlighter.computeHighlighting(initialText, title));
        codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(250))
            .subscribe(ignore -> {
                try {
                    codeArea.setStyleSpans(0, syntaxHighlighter.computeHighlighting(codeArea.getText(), title));
                    updateMarkdownPreview(previewView, codeArea.getText(), title);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

        updateMarkdownPreview(previewView, initialText, title);

        StackPane center = new StackPane(editorScrollPane, previewView);
        previewView.setVisible(false);
        previewView.setManaged(false);

        ToggleButton codeToggle = new ToggleButton("CÃ³digo");
        ToggleButton previewToggle = new ToggleButton("Vista previa");
        ToggleGroup group = new ToggleGroup();
        codeToggle.setToggleGroup(group);
        previewToggle.setToggleGroup(group);
        codeToggle.setSelected(true);

        BorderPane root = new BorderPane();
        Tab tab = new Tab(title);
        codeToggle.setOnAction(e -> setMarkdownPreviewVisible(tab, editorScrollPane, previewView, false));
        previewToggle.setOnAction(e -> setMarkdownPreviewVisible(tab, editorScrollPane, previewView, true));

        root.setTop(new javafx.scene.control.ToolBar(codeToggle, previewToggle));
        root.setCenter(center);
        tab.setContent(root);
        tab.getProperties().put(TAB_PROP_BASE_TITLE, title != null ? title : "");
        tab.getProperties().put(TAB_PROP_BASE_TEXT, initialText);
        tab.getProperties().put(TAB_PROP_CODE_AREA, codeArea);
        tab.getProperties().put(TAB_PROP_MARKDOWN_WEBVIEW, previewView);
        tab.getProperties().put(TAB_PROP_MARKDOWN_PREVIEW, Boolean.FALSE);
        applyFileTypeIcon(tab, title);
        recordDiskFingerprint(tab, initialText);

        codeArea.textProperty().addListener((obs, oldText, newText) -> updateTabDirtyIndicator(tab, codeArea));
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (cursorStatusLabel != null && tabPane.getSelectionModel().getSelectedItem() == tab) {
                updateCursorStatus(tabPane);
            }
        });

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        return tab;
    }

    public Tab addToolTab(String title, Node node, TabPane tabPane) {
        Tab tab = new Tab(title != null ? title : "");
        tab.setContent(node);
        tab.setClosable(true);
        tab.getProperties().put(TAB_PROP_BASE_TITLE, title != null ? title : "");
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        return tab;
    }

    public Tab addTranslatorTab(String title, Node node, TabPane tabPane) {
        Tab tab = new Tab(title != null ? title : "");
        tab.setContent(node);
        tab.setClosable(true);
        tab.getProperties().put(TAB_PROP_BASE_TITLE, title != null ? title : "");
        applyFileTypeIcon(tab, title);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        return tab;
    }

    public void setEditorAppearance(String appearance) {
        if (ConfigModel.EDITOR_APPEARANCE_CLASSIC.equalsIgnoreCase(appearance)) {
            editorAppearance = ConfigModel.EDITOR_APPEARANCE_CLASSIC;
        } else {
            editorAppearance = ConfigModel.EDITOR_APPEARANCE_MODERN_DARK;
        }
    }

    public void applyEditorAppearance(CodeArea codeArea) {
        if (codeArea == null) return;
        String modernCss = resolveStylesheet(EDITOR_STYLESHEET_MODERN);
        String classicCss = resolveStylesheet(EDITOR_STYLESHEET_CLASSIC);

        if (modernCss != null) {
            codeArea.getStylesheets().remove(modernCss);
        }
        if (classicCss != null) {
            codeArea.getStylesheets().remove(classicCss);
        }

        String activeCssPath = ConfigModel.EDITOR_APPEARANCE_CLASSIC.equals(editorAppearance)
            ? EDITOR_STYLESHEET_CLASSIC
            : EDITOR_STYLESHEET_MODERN;
        String activeCss = resolveStylesheet(activeCssPath);
        if (activeCss != null && !codeArea.getStylesheets().contains(activeCss)) {
            codeArea.getStylesheets().add(activeCss);
        }
    }

    private String resolveStylesheet(String resourcePath) {
        try {
            java.net.URL resource = getClass().getResource(resourcePath);
            return resource != null ? resource.toExternalForm() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static final String TAB_PROP_GENERATED_CSS = "editor.generatedCss";

    public void applyGeneratedEditorCss(CodeArea codeArea, String cssUrl) {
        if (codeArea == null) return;
        Object previous = codeArea.getProperties().get(TAB_PROP_GENERATED_CSS);
        if (previous instanceof String) {
            codeArea.getStylesheets().remove((String) previous);
        }
        if (cssUrl != null && !codeArea.getStylesheets().contains(cssUrl)) {
            codeArea.getStylesheets().add(cssUrl);
            codeArea.getProperties().put(TAB_PROP_GENERATED_CSS, cssUrl);
        }
    }

    public void applyGeneratedEditorTheme(CodeArea codeArea, String cssSource) {
        if (codeArea == null) return;
        applyGeneratedEditorCss(codeArea, dataCssUrl(cssSource));
    }

    /**
     * Convierte texto CSS en una URL {@code data:text/css}. Los espacios se
     * codifican como {@code %20} (nunca {@code +}): el decodificador de
     * {@code DataURI} de JavaFX trata {@code +} como un signo más literal, por
     * lo que {@code URLEncoder} por sí solo produce hojas que el parser CSS
     * rechaza y el tema no llega a aplicarse.
     *
     * @return la URL data, o {@code null} si la entrada es nula/vacía
     */
    public static String dataCssUrl(String cssSource) {
        if (cssSource == null || cssSource.isBlank()) return null;
        try {
            String encoded = java.net.URLEncoder.encode(cssSource, StandardCharsets.UTF_8).replace("+", "%20");
            return "data:text/css;charset=utf-8," + encoded;
        } catch (Exception ex) {
            return null;
        }
    }

    public void markTabSaved(Tab tab, String baseTitle, String savedText) {
        if (tab == null) return;

        String normalizedTitle = baseTitle != null ? baseTitle : "";
        String normalizedText = savedText != null ? savedText : "";

        tab.getProperties().put(TAB_PROP_BASE_TITLE, normalizedTitle);
        tab.getProperties().put(TAB_PROP_BASE_TEXT, normalizedText);
        recordDiskFingerprint(tab, normalizedText);
        tab.setText(normalizedTitle);
        Object preview = tab.getProperties().get(TAB_PROP_MARKDOWN_WEBVIEW);
        Object codeArea = tab.getProperties().get(TAB_PROP_CODE_AREA);
        if (preview instanceof WebView && codeArea instanceof CodeArea) {
            updateMarkdownPreview((WebView) preview, normalizedText, normalizedTitle);
        }
    }

    /**
     * Registra en la pestaña la huella del contenido tal como se leyó/guardó por
     * última vez. Sobre esa huella se detectan cambios externos en disco.
     */
    public void recordDiskFingerprint(Tab tab, String content) {
        if (tab == null) return;
        tab.getProperties().put(TAB_PROP_DISK_FINGERPRINT, ExternalFileChangeService.fingerprint(content));
    }

    /**
     * Huella del último contenido sincronizado de la pestaña, o {@code null} si
     * la pestaña no es de editor (no se puede detectar cambios externos).
     */
    public String getDiskFingerprint(Tab tab) {
        if (tab == null) return null;
        Object value = tab.getProperties().get(TAB_PROP_DISK_FINGERPRINT);
        return value instanceof String ? (String) value : null;
    }

    private void updateTabDirtyIndicator(Tab tab, CodeArea codeArea) {
        if (tab == null || codeArea == null) return;

        Object baseTitleObj = tab.getProperties().get(TAB_PROP_BASE_TITLE);
        String baseTitle = baseTitleObj instanceof String ? (String) baseTitleObj : (tab.getText() != null ? tab.getText() : "");
        if (baseTitle.endsWith("*")) {
            baseTitle = baseTitle.substring(0, baseTitle.length() - 1);
            tab.getProperties().put(TAB_PROP_BASE_TITLE, baseTitle);
        }

        Object baseTextObj = tab.getProperties().get(TAB_PROP_BASE_TEXT);
        String baseText = baseTextObj instanceof String ? (String) baseTextObj : "";
        String currentText = codeArea.getText() != null ? codeArea.getText() : "";

        boolean dirty = !currentText.equals(baseText);
        tab.setText(dirty ? baseTitle + "*" : baseTitle);
    }

    /**
     * AÃ±ade una pestaÃ±a "Home" que carga un recurso HTML en un WebView.
     * @param resourcePath classpath del recurso (ej. "/views/home/home.html")
     */
    public Tab addHomeTab(String resourcePath, TabPane tabPane) {
        return addHomeTab(resourcePath, tabPane, "Home", null);
    }

    /**
     * AÃ±ade una pestaÃ±a Home con tÃ­tulo e id personalizados.
     * @param resourcePath classpath del recurso HTML
     * @param tabPane contenedor de pestaÃ±as
     * @param tabTitle texto de la pestaÃ±a
     * @param tabId id opcional de la pestaÃ±a para localizarla luego
     */
    public Tab addHomeTab(String resourcePath, TabPane tabPane, String tabTitle, String tabId) {
        try {
            WebView   web    = new WebView();
            WebEngine engine = web.getEngine();
            String    url    = getClass().getResource(resourcePath).toExternalForm();

            engine.load(url);

            Tab tab          = new Tab(tabTitle != null ? tabTitle : "Home", web);
            tab.setClosable(true);
            if (tabId != null && !tabId.isEmpty()) tab.setId(tabId);

            tabPane.getTabs().add(0, tab); // aÃ±ade como primera pestaÃ±a
            tabPane.getSelectionModel().select(tab);

            return tab;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * AÃ±ade una pestaÃ±a Home con contenido HTML ya renderizado.
     * @param htmlContent html completo
     * @param tabPane contenedor de pestaÃ±as
     * @param tabTitle texto de la pestaÃ±a
     * @param tabId id opcional de la pestaÃ±a para localizarla luego
     */
    public Tab addHomeTabContent(String htmlContent, TabPane tabPane, String tabTitle, String tabId) {
        try {
            WebView   web    = new WebView();
            WebEngine engine = web.getEngine();

            engine.loadContent(htmlContent != null ? htmlContent : "", "text/html");

            Tab tab          = new Tab(tabTitle != null ? tabTitle : "Home", web);
            tab.setClosable(true);
            if (tabId != null && !tabId.isEmpty()) tab.setId(tabId);

            tabPane.getTabs().add(0, tab);
            tabPane.getSelectionModel().select(tab);

            return tab;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Obtiene la ventana (Stage) asociada a un TabPane.
     * @param tabPane
     * @return
     */
    public Stage getStage(TabPane tabPane) { return (Stage) tabPane.getScene().getWindow(); }

    /**
     * Obtiene el CodeArea del tab actualmente seleccionado en el TabPane.
     * @param tabPane TabPane que contiene las pestaÃ±as.
     * @return CodeArea del tab seleccionado, o null si no hay tab seleccionado o no es un CodeArea.
     */
    public CodeArea getCurrentCodeArea(TabPane tabPane) {
        Tab tab = getSelectedTab(tabPane);

        if (tab != null) {
            return getCodeArea(tab);
        }

        return null;
    }

    /**
     * Actualiza los textos de la interfaz segÃºn el ResourceBundle proporcionado.
     * @param uiElements
     * @param bundle
     */
    public void refreshLanguage(UIElements uiElements, ResourceBundle bundle) {
    
        if (bundle == null) {
            bundle = ResourceBundle.getBundle("i18n.MessagesBundle", java.util.Locale.forLanguageTag("es"));
        }

        // Null-checks en todos los botones para evitar NPE cuando alguno no existe en el FXML
        if (uiElements.btnCut          != null) uiElements.btnCut.setTooltip           (new Tooltip(bundle.getString("button.cut")));
        if (uiElements.btnOpen         != null) uiElements.btnOpen.setTooltip          (new Tooltip(bundle.getString("button.open")));
        if (uiElements.btnSave         != null) uiElements.btnSave.setTooltip          (new Tooltip(bundle.getString("button.save")));
        if (uiElements.btnUndo         != null) uiElements.btnUndo.setTooltip          (new Tooltip(bundle.getString("button.undo")));
        if (uiElements.btnRedo         != null) uiElements.btnRedo.setTooltip          (new Tooltip(bundle.getString("button.redo")));
        if (uiElements.btnCopy         != null) uiElements.btnCopy.setTooltip          (new Tooltip(bundle.getString("button.copy")));
        if (uiElements.btnNuevo        != null) uiElements.btnNuevo.setTooltip         (new Tooltip(bundle.getString("button.new")));
        if (uiElements.btnClose        != null) uiElements.btnClose.setTooltip         (new Tooltip(bundle.getString("button.close")));
        if (uiElements.btnPaste        != null) uiElements.btnPaste.setTooltip         (new Tooltip(bundle.getString("button.paste")));
        if (uiElements.btnConfig       != null) uiElements.btnConfig.setTooltip        (new Tooltip(bundle.getString("button.config")));
        if (uiElements.btnCompilar     != null) uiElements.btnCompilar.setTooltip      (new Tooltip(bundle.getString("button.compile")));
        if (uiElements.btnCancelarCompilacion != null) uiElements.btnCancelarCompilacion.setTooltip(new Tooltip(bundle.getString("button.cancelCompile")));
        if (uiElements.btnEjecutar     != null) uiElements.btnEjecutar.setTooltip      (new Tooltip(bundle.getString("button.run")));
        if (uiElements.btnNuevoProyecto!= null) uiElements.btnNuevoProyecto.setTooltip (new Tooltip(bundle.getString("button.newProject")));
        if (uiElements.btnAbrirProyecto!= null) uiElements.btnAbrirProyecto.setTooltip (new Tooltip(bundle.getString("button.openProject")));

        if (uiElements.menuArchivo         != null) uiElements.menuArchivo.setText          (bundle.getString("menu.file"));
        if (uiElements.menuEdicion         != null) uiElements.menuEdicion.setText          (bundle.getString("menu.edit"));
        if (uiElements.menuCompilacion     != null) uiElements.menuCompilacion.setText      (bundle.getString("menu.project"));
        if (uiElements.menuItemNuevoProyecto != null) uiElements.menuItemNuevoProyecto.setText(bundle.getString("button.newProject"));
        if (uiElements.menuItemAbrirProyecto != null) uiElements.menuItemAbrirProyecto.setText(bundle.getString("button.openProject"));
        if (uiElements.menuItemNuevo       != null) uiElements.menuItemNuevo.setText        (bundle.getString("button.new"));
        if (uiElements.menuItemAbrir       != null) uiElements.menuItemAbrir.setText        (bundle.getString("button.open"));
        if (uiElements.menuItemSalir       != null) uiElements.menuItemSalir.setText        (bundle.getString("menu.exit"));
        if (uiElements.menuItemConfig      != null) uiElements.menuItemConfig.setText       (bundle.getString("button.config"));
        if (uiElements.menuItemCerrar      != null) uiElements.menuItemCerrar.setText       (bundle.getString("button.close"));
        if (uiElements.menuItemGuardar     != null) uiElements.menuItemGuardar.setText      (bundle.getString("button.save"));
        if (uiElements.menuItemGuardarTodo != null) uiElements.menuItemGuardarTodo.setText  (bundle.getString("button.saveAll"));
        if (uiElements.menuItemCompilar    != null) uiElements.menuItemCompilar.setText     (bundle.getString("button.compile"));
        if (uiElements.menuItemEjecutar    != null) uiElements.menuItemEjecutar.setText     (bundle.getString("button.run"));
        if (uiElements.menuItemGuardarComo != null) uiElements.menuItemGuardarComo.setText  (bundle.getString("button.saveAs"));
        if (uiElements.menuItemFind        != null) uiElements.menuItemFind.setText         (bundle.getString("menu.find.file"));
        if (uiElements.menuItemFindProject != null) uiElements.menuItemFindProject.setText  (bundle.getString("menu.find.project"));
    }

    /**
     * Obtiene el CodeArea contenido en una pestaÃ±a especÃ­fica.
     * @param tab PestaÃ±a de la cual obtener el CodeArea.
     * @return CodeArea contenido en la pestaÃ±a, o null si no es un CodeArea.
     */
    public CodeArea getCodeArea(Tab tab) {
        if (tab == null) return null;

        Object stored = tab.getProperties().get(TAB_PROP_CODE_AREA);
        if (stored instanceof CodeArea) {
            return (CodeArea) stored;
        }

        if (tab.getContent() instanceof CodeArea) { return (CodeArea) tab.getContent(); }

        if (tab.getContent() instanceof VirtualizedScrollPane<?> scrollPane && scrollPane.getContent() instanceof CodeArea) {
            return (CodeArea) scrollPane.getContent();
        }

        if (tab.getContent() instanceof BorderPane) {
            BorderPane borderPane = (BorderPane) tab.getContent();
            if (borderPane.getCenter() instanceof StackPane) {
                StackPane stackPane = (StackPane) borderPane.getCenter();
                for (javafx.scene.Node node : stackPane.getChildren()) {
                    if (node instanceof CodeArea) return (CodeArea) node;
                    if (node instanceof VirtualizedScrollPane<?> scrollPane && scrollPane.getContent() instanceof CodeArea) {
                        return (CodeArea) scrollPane.getContent();
                    }
                }
            }
        }

        return null;
    }

    /**
     * AÃ±ade una nueva pestaÃ±a (tab) al TabPane y la asocia a un archivo en el mapa.
     * @param file Archivo asociado a la pestaÃ±a (puede ser null para nuevo archivo).
     * @param content Contenido inicial del CodeArea.
     * @param tabFileMap Mapa que asocia pestaÃ±as a archivos.
     * @param syntaxHighlighter Resaltador de sintaxis a utilizar.
     * @param tabPane TabPane donde se aÃ±adirÃ¡ la nueva pestaÃ±a.
     * @param newFileCounter Contador para nombrar nuevos archivos sin guardar.
     */
    public void addNewTab(File file, String content, Map<Tab, File> tabFileMap, SyntaxHighlighter syntaxHighlighter, TabPane tabPane, int newFileCounter) {
        String tabName = (file != null) ? file.getName() : ("new" + newFileCounter + ".c");
        addNewTabNamed(file, content, tabName, tabFileMap, syntaxHighlighter, tabPane);
    }

    /**
     * Crea una nueva pestaña sin guardar con un nombre arbitrario (p. ej.
     * "nuevo1.txt" o "nuevo1.tile").
     * @param tabName Nombre base mostrado en la pestaña.
     */
    public void addNewTabNamed(File file, String content, String tabName, Map<Tab, File> tabFileMap, SyntaxHighlighter syntaxHighlighter, TabPane tabPane) {
        String name = (file != null) ? file.getName() : tabName;
        Tab    tab     = isMarkdownFileName(name)
            ? addMarkdownTab(name, content, syntaxHighlighter, tabPane)
            : addTab(name, content, syntaxHighlighter, tabPane);

        tabFileMap.put(tab, file);

        // Limpieza cuando el usuario cierra el tab con la X integrada
        tab.setOnClosed(e -> tabFileMap.remove(tab));
    }

    /**
     * Crea un nuevo proyecto en una carpeta seleccionada por el usuario.
     * @param parentStage La ventana principal (stage) de la aplicaciÃ³n.
     * @return La carpeta del nuevo proyecto, o null si se cancela la operaciÃ³n.
     */
    public File createNewProject(Stage parentStage) {
        return createNewProject(parentStage, null);
    }

    public File createNewProject(Stage parentStage, ResourceBundle bundle, String readmeLanguage) {
        projectReadmeLanguage = normalizeReadmeLanguage(readmeLanguage);
        return createNewProject(parentStage, bundle);
    }

    /**
     * Crea un nuevo proyecto en una carpeta seleccionada por el usuario.
     * @param parentStage La ventana principal (stage) de la aplicaciÃ³n.
     * @param bundle ResourceBundle opcional para i18n.
     * @return La carpeta del nuevo proyecto, o null si se cancela la operaciÃ³n.
     */
    public File createNewProject(Stage parentStage, ResourceBundle bundle) {
        return createNewProject(parentStage, bundle, (File) null);
    }

    public File createNewProject(Stage parentStage, ResourceBundle bundle, File initialDirectory) {
        projectReadmeLanguage = normalizeReadmeLanguage(projectReadmeLanguage);
        lastProjectCreationCancelled = false;
        lastCreatedProjectTemplate = null;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(msg(bundle, "dialog.project.new.selectFolder.title", "Selecciona carpeta para el nuevo proyecto"));
        setInitialDirectoryIfValid(chooser, initialDirectory);
        File parent = chooser.showDialog(parentStage);

        if (parent != null) {
            // Pregunta al usuario por un nombre de proyecto
            while (true) {
                javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();

                dialog.setTitle      (msg(bundle, "dialog.project.new.name.title", "Nombre del proyecto"));
                dialog.setHeaderText (msg(bundle, "dialog.project.new.name.header", "Introduce el nombre para el nuevo proyecto"));
                dialog.setContentText(msg(bundle, "dialog.project.new.name.label", "Nombre:"));

                java.util.Optional<String> result = dialog.showAndWait();

                if (!result.isPresent()) {
                    lastProjectCreationCancelled = true;
                    return null; // Usuario cancelÃ³
                }

                String nombre = result.get().trim();

                if (nombre.isEmpty()) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle      (msg(bundle, "dialog.project.new.invalid.title", "Nombre invalido"));
                    alert.setHeaderText (null);
                    alert.setContentText(msg(bundle, "dialog.project.new.invalid.empty", "El nombre del proyecto no puede estar vacio."));
                    alert.showAndWait   ();

                    continue; 
                }

                // Valida que el nombre no contenga separadores de ruta
                if (nombre.contains(java.io.File.separator) || nombre.contains("/")) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle      (msg(bundle, "dialog.project.new.invalid.title", "Nombre invalido"));
                    alert.setHeaderText (null);
                    alert.setContentText(msg(bundle, "dialog.project.new.invalid.separator", "El nombre no puede contener separadores de ruta."));
                    alert.showAndWait   ();
                    continue;
                }

                File nuevoProyecto = new File(parent, nombre);
                ProjectCreationOptions creationOptions = askProjectTemplate(bundle);
                if (creationOptions == null || creationOptions.template == null) {
                    lastProjectCreationCancelled = true;
                    return null;
                }

                // La plantilla GITHUB abre el explorador en vez de crear archivos
                if (creationOptions.template == ProjectTemplate.GITHUB) {
                    lastCreatedProjectTemplate = ProjectTemplate.GITHUB;
                    return null;
                }

                if (nuevoProyecto.exists()) {
                    ButtonType overwrite = new ButtonType(msg(bundle, "dialog.project.new.action.overwrite", "Sobrescribir"), ButtonBar.ButtonData.YES);
                    ButtonType retry     = new ButtonType(msg(bundle, "dialog.project.new.action.retry", "Reintentar")  , ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel    = new ButtonType(msg(bundle, "dialog.project.new.action.cancel", "Cancelar")    , ButtonBar.ButtonData.CANCEL_CLOSE);
                    Alert      alert     = new Alert( Alert.AlertType.CONFIRMATION,
                                                    msg(bundle, "dialog.project.new.exists.content", "Ya existe una carpeta con ese nombre. Puedes sobrescribirla (se eliminara su contenido) o probar otro nombre."),
                                                    overwrite, 
                                                    retry, 
                                                    cancel);

                    alert.setTitle      (msg(bundle, "dialog.project.new.exists.title", "Ya existe"));
                    alert.setHeaderText (null);

                    Optional<ButtonType> choice = alert.showAndWait();

                    if (!choice.isPresent() || choice.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                        lastProjectCreationCancelled = true;
                        return null; // Cancelado
                    }

                    if (choice.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) continue; // Reintentar
                    // Overwrite selected: attempt recursive delete
                    try {
                        Path dir = nuevoProyecto.toPath();

                        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                if (exc != null) throw exc;
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }

                        });
                    } catch (IOException ex) {

                        ButtonType retryDel  = new ButtonType(msg(bundle, "dialog.project.new.action.retry", "Reintentar"), ButtonBar.ButtonData.OK_DONE);
                        ButtonType cancelDel = new ButtonType(msg(bundle, "dialog.project.new.action.cancel", "Cancelar"),   ButtonBar.ButtonData.CANCEL_CLOSE);
                        Alert err            = new Alert(AlertType.CONFIRMATION,
                                                         msg(bundle, "dialog.project.new.overwriteError.content", "No se pudo eliminar la carpeta existente") + ": " + ex.getMessage(), 
                                                         retryDel, 
                                                         cancelDel);

                        err.setTitle     (msg(bundle, "dialog.project.new.overwriteError.title", "Error al sobrescribir"));
                        err.setHeaderText(null);

                        Optional<ButtonType> choice2 = err.showAndWait();

                        if (!choice2.isPresent() || choice2.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                            lastProjectCreationCancelled = true;
                            return null;
                        }

                        continue; // Reintentar eliminaciÃ³n
                    }
                    
                    // En este punto, la carpeta antigua fue eliminada; intentar crear una nueva a continuaciÃ³n en el flujo normal
                }

                try {
                    createProjectFromTemplate(nuevoProyecto, creationOptions);
                    lastCreatedProjectTemplate = creationOptions.template;
                    lastCreateReadmeSelected = creationOptions.createReadme;
                    lastCreateGitignoreSelected = creationOptions.createGitignore;
                    return nuevoProyecto;
                } catch (IOException ex) {
                    ButtonType retry  = new ButtonType(msg(bundle, "dialog.project.new.action.retry", "Reintentar"), ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel = new ButtonType(msg(bundle, "dialog.project.new.action.cancel", "Cancelar"), ButtonBar.ButtonData.CANCEL_CLOSE);
                    Alert alert       = new Alert(Alert.AlertType.CONFIRMATION,
                        msg(bundle, "dialog.project.new.createError.content", "No se pudo crear la carpeta del proyecto. Comprueba permisos.") + ": " + ex.getMessage(),
                        retry,
                        cancel);
                    alert.setTitle(msg(bundle, "dialog.project.new.createError.title", "Error"));
                    alert.setHeaderText(null);
                    Optional<ButtonType> choice = alert.showAndWait();
                    if (!choice.isPresent() || choice.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                        lastProjectCreationCancelled = true;
                        return null;
                    }
                    // otro intento
                    continue;
                }
            }
        }
        lastProjectCreationCancelled = true;
        lastCreatedProjectTemplate = null;
        return null;
    }

    private ProjectCreationOptions askProjectTemplate(ResourceBundle bundle) {
        java.util.LinkedHashMap<ProjectTemplate, String> labels = new java.util.LinkedHashMap<>();
        labels.put(ProjectTemplate.EMPTY, msg(bundle, "dialog.project.new.template.option.empty", "Proyecto vacio"));
        labels.put(ProjectTemplate.SPECTRUM, msg(bundle, "dialog.project.new.template.option.spectrum", "Spectrum - Basico"));
        labels.put(ProjectTemplate.SPECTRUM_SCREEN, msg(bundle, "dialog.project.new.template.option.spectrumScreen", "Spectrum - Pantalla inicial"));
        labels.put(ProjectTemplate.GAMEBOY, msg(bundle, "dialog.project.new.template.option.gameboy", "Game Boy - Basico"));
        labels.put(ProjectTemplate.GAMEBOY_SPRITE, msg(bundle, "dialog.project.new.template.option.gameboySprite", "Game Boy - Sprite demo"));
        labels.put(ProjectTemplate.CPC, msg(bundle, "dialog.project.new.template.option.cpc", "Amstrad CPC - Basico"));
        labels.put(ProjectTemplate.GCC, msg(bundle, "dialog.project.new.template.option.gcc", "C normal (gcc) - Basico"));
        labels.put(ProjectTemplate.GCC_CALC, msg(bundle, "dialog.project.new.template.option.gccCalc", "C normal (gcc) - Calculadora"));
        labels.put(ProjectTemplate.CC65, msg(bundle, "dialog.project.new.template.option.cc65", "CC65 (Atari XE/XL) - Basico"));
        labels.put(ProjectTemplate.CC65_C64, msg(bundle, "dialog.project.new.template.option.cc65C64", "CC65 (C64) - Basico"));
        labels.put(ProjectTemplate.GENERIC, msg(bundle, "dialog.project.new.template.option.generic", "C generico"));
        labels.put(ProjectTemplate.ASM, msg(bundle, "dialog.project.new.template.option.asm", "Ensamblador (z80 / Game Boy)"));
        labels.put(ProjectTemplate.MAKEFILE, msg(bundle, "dialog.project.new.template.option.makefile", "Makefile / Custom"));

        labels.put(ProjectTemplate.GITHUB, msg(bundle, "dialog.project.new.template.option.github", "Desde repositorio GitHub..."));

        java.util.Map<ProjectTemplate, String> descriptions = new java.util.HashMap<>();
        descriptions.put(ProjectTemplate.EMPTY, msg(bundle, "dialog.project.new.template.description.empty", "Estructura minima en C con src/main.c sin dependencias."));
        descriptions.put(ProjectTemplate.SPECTRUM, msg(bundle, "dialog.project.new.template.description.spectrum", "Plantilla base para Spectrum con conio.h, pantalla limpia y espera de tecla."));
        descriptions.put(ProjectTemplate.SPECTRUM_SCREEN, msg(bundle, "dialog.project.new.template.description.spectrumScreen", "Ejemplo para Spectrum con mensaje de bienvenida y pausa por teclado."));
        descriptions.put(ProjectTemplate.GAMEBOY, msg(bundle, "dialog.project.new.template.description.gameboy", "Plantilla base de Game Boy con bucle principal y sincronizacion VBlank."));
        descriptions.put(ProjectTemplate.GAMEBOY_SPRITE, msg(bundle, "dialog.project.new.template.description.gameboySprite", "Demo de Game Boy con sprite cargado y mostrado en pantalla."));
        descriptions.put(ProjectTemplate.CPC, msg(bundle, "dialog.project.new.template.description.cpc", "Plantilla basica para Amstrad CPC con Z88DK."));
        descriptions.put(ProjectTemplate.GCC, msg(bundle, "dialog.project.new.template.description.gcc", "Plantilla basica de C normal para compilar y ejecutar de forma nativa con GCC."));
        descriptions.put(ProjectTemplate.GCC_CALC, msg(bundle, "dialog.project.new.template.description.gccCalc", "Ejemplo de consola en C normal con bucles y entrada por teclado."));
        descriptions.put(ProjectTemplate.CC65, msg(bundle, "dialog.project.new.template.description.cc65", "Plantilla basica para Atari XE/XL con CC65."));
        descriptions.put(ProjectTemplate.CC65_C64, msg(bundle, "dialog.project.new.template.description.cc65C64", "Plantilla basica para Commodore 64 con CC65."));
        descriptions.put(ProjectTemplate.GENERIC, msg(bundle, "dialog.project.new.template.description.generic", "Plantilla generica en C para cualquier plataforma."));
        descriptions.put(ProjectTemplate.ASM, msg(bundle, "dialog.project.new.template.description.asm", "Plantilla de ensamblador z80 para Game Boy / ZX."));
        descriptions.put(ProjectTemplate.MAKEFILE, msg(bundle, "dialog.project.new.template.description.makefile", "Proyecto basado en Makefile con estructura base."));

        descriptions.put(ProjectTemplate.GITHUB, msg(bundle, "dialog.project.new.template.description.github", "Importa un repositorio de ejemplo desde el catálogo de GitHub."));

        javafx.scene.control.Dialog<ProjectCreationOptions> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(msg(bundle, "dialog.project.new.template.title", "Plantilla del proyecto"));
        dialog.setHeaderText(msg(bundle, "dialog.project.new.template.header", "Selecciona una plantilla para el proyecto"));

        ButtonType accept = new ButtonType(msg(bundle, "dialog.project.new.template.action.accept", "Aceptar"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(accept, ButtonType.CANCEL);

        javafx.scene.control.ComboBox<ProjectTemplate> combo = new javafx.scene.control.ComboBox<>();
        combo.getItems().addAll(labels.keySet());
        combo.getSelectionModel().select(ProjectTemplate.SPECTRUM);

        combo.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ProjectTemplate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labels.get(item));
            }
        });
        combo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ProjectTemplate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labels.get(item));
            }
        });

        javafx.scene.control.Label descLabel = new javafx.scene.control.Label();
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(420);
        descLabel.setText(descriptions.get(ProjectTemplate.SPECTRUM));

        combo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            ProjectTemplate selectedTemplate = newV != null ? newV : ProjectTemplate.SPECTRUM;
            descLabel.setText(descriptions.getOrDefault(selectedTemplate, ""));
        });

        javafx.scene.control.Label selectorLabel = new javafx.scene.control.Label(msg(bundle, "dialog.project.new.template.label", "Plantilla:"));
        javafx.scene.control.CheckBox readmeCheck = new javafx.scene.control.CheckBox(
            msg(bundle, "dialog.project.new.template.option.readme", "Crear README.md")
        );
        readmeCheck.setSelected(lastCreateReadmeSelected);

        javafx.scene.control.CheckBox gitignoreCheck = new javafx.scene.control.CheckBox(
            msg(bundle, "dialog.project.new.template.option.gitignore", "Crear .gitignore")
        );
        gitignoreCheck.setSelected(lastCreateGitignoreSelected);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8, selectorLabel, combo, descLabel, readmeCheck, gitignoreCheck);
        content.setPrefWidth(430);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button != accept) return null;
            return new ProjectCreationOptions(
                combo.getValue(),
                readmeCheck.isSelected(),
                gitignoreCheck.isSelected()
            );
        });

        Optional<ProjectCreationOptions> selected = dialog.showAndWait();
        return selected.orElse(null);
    }

    private void createProjectFromTemplate(File projectDir, ProjectCreationOptions options) throws IOException {
        ProjectTemplate template = options != null ? options.template : ProjectTemplate.EMPTY;
        Path projectPath = projectDir.toPath();
        Path srcPath = projectPath.resolve("src");

        if (template == ProjectTemplate.TRANSLATOR) {
            Files.createDirectories(projectPath);
            Files.writeString(projectPath.resolve("translation.smt"), getTranslatorSmtContent());
        } else {
            Files.createDirectories(srcPath);
            if (template == ProjectTemplate.ASM) {
                Files.writeString(srcPath.resolve("main.asm"), getTemplateMainContent(template));
            } else if (template == ProjectTemplate.MAKEFILE) {
                Files.writeString(srcPath.resolve("main.c"), getTemplateMainContent(template));
                Files.writeString(projectPath.resolve("Makefile"), getMakefileContent());
            } else {
                Files.writeString(srcPath.resolve("main.c"), getTemplateMainContent(template));
            }
        }

        if (options != null && options.createReadme) {
            Files.writeString(projectPath.resolve("README.md"), getReadmeContent(projectDir.getName(), template));
        }

        if (options != null && options.createGitignore) {
            Files.writeString(projectPath.resolve(".gitignore"), getGitignoreContent(template));
        }

        writeWorkspaceConfig(projectPath, template);
    }

    private String getMakefileContent() {
        return String.join("\n",
            "CC = gcc",
            "CFLAGS = -Wall -std=c11",
            "",
            "all: out/main",
            "",
            "out/main: src/main.c",
            "\t@mkdir -p out",
            "\t$(CC) $(CFLAGS) -o out/main src/main.c",
            "",
            "clean:",
            "\trm -rf out",
            ""
        );
    }

    private String getTranslatorSmtContent() {
        return String.join("\n",
            "{",
            "  \"version\": 1,",
            "  \"romPath\": \"\",",
            "  \"fontTable\": \"pokemon\",",
            "  \"encoding\": \"SENTINEL_0x50\",",
            "  \"entries\": []",
            "}",
            ""
        );
    }

    private void writeWorkspaceConfig(Path projectPath, ProjectTemplate template) throws IOException {
        if (projectPath == null) return;

        Path workspaceDir = projectPath.resolve(".samarucws");
        Files.createDirectories(workspaceDir);

        String target = resolveWorkspaceTarget(template);
        String json = String.join("\n",
            "{",
            "  \"samaruc.workspaceVersion\": 1,",
            "  \"samaruc.target\": \"" + target + "\",",
            "  \"files.exclude\": {",
            "    \"**/out\": true",
            "  },",
            "  \"files.associations\": {",
            "    \"*.inc\": \"c\",",
            "    \"*.z80\": \"asm\",",
            "    \"*.s\": \"asm\",",
            "    \"*.json\": \"json\"",
            "  },",
            "  \"editor.tabSize\": 4,",
            "  \"editor.insertSpaces\": true",
            "}",
            ""
        );

        Files.writeString(workspaceDir.resolve("settings.json"), json, StandardCharsets.UTF_8);
    }

    private String resolveWorkspaceTarget(ProjectTemplate template) {
        if (template == ProjectTemplate.GAMEBOY || template == ProjectTemplate.GAMEBOY_SPRITE
                || template == ProjectTemplate.TRANSLATOR) {
            return "gameboy";
        }
        if (template == ProjectTemplate.SPECTRUM || template == ProjectTemplate.SPECTRUM_SCREEN) {
            return "spectrum";
        }
        if (template == ProjectTemplate.CPC) {
            return "cpc";
        }
        if (template == ProjectTemplate.GCC || template == ProjectTemplate.GCC_CALC) {
            return "gcc";
        }
        if (template == ProjectTemplate.CC65) {
            return "cc65";
        }
        if (template == ProjectTemplate.CC65_C64) {
            return "c64";
        }
        if (template == ProjectTemplate.MAKEFILE) {
            return "make";
        }
        return "generic";
    }

    private String getReadmeContent(String projectName, ProjectTemplate template) {
        String normalizedName = projectName != null && !projectName.isBlank() ? projectName : "Nuevo Proyecto";

        if (template == ProjectTemplate.GAMEBOY || template == ProjectTemplate.GAMEBOY_SPRITE) {
            return getGameBoyReadmeContent(normalizedName, template);
        }

        if (template == ProjectTemplate.SPECTRUM || template == ProjectTemplate.SPECTRUM_SCREEN) {
            return getSpectrumReadmeContent(normalizedName, template);
        }

        if (template == ProjectTemplate.CC65 || template == ProjectTemplate.CC65_C64) {
            return getCc65ReadmeContent(normalizedName, template);
        }

        return getGenericReadmeContent(normalizedName);
    }

    private String getSpectrumReadmeContent(String projectName, ProjectTemplate template) {
        if (isSpanishReadme()) {
            return getSpectrumReadmeContentEs(projectName, template);
        }

        String demoNote = template == ProjectTemplate.SPECTRUM_SCREEN
            ? "\nThis project uses the ZX Spectrum splash-screen template."
            : "";

        return String.join("\n",
            "# " + projectName,
            "",
            "ZX Spectrum project created with Samaruc.",
            demoNote,
            "",
            "## Structure",
            "- Main source file: `src/main.c`",
            "- Build output: `out/`",
            "",
            "## Recommended Toolchain",
            "- z88dk (`zcc`)",
            "",
            "## Notes",
            "- If you use a Makefile, try to write artifacts to `out/`.",
            ""
        );
    }

    private String getGameBoyReadmeContent(String projectName, ProjectTemplate template) {
        if (isSpanishReadme()) {
            return getGameBoyReadmeContentEs(projectName, template);
        }

        String demoNote = template == ProjectTemplate.GAMEBOY_SPRITE
            ? "\nThis project uses the Game Boy sprite demo template."
            : "";

        return String.join("\n",
            "# " + projectName,
            "",
            "Game Boy project created with Samaruc.",
            demoNote,
            "",
            "## Structure",
            "- Main source file: `src/main.c`",
            "- Build output: `out/`",
            "",
            "## Recommended Toolchain",
            "- GBDK (`lcc`)",
            "",
            "## Notes",
            "- If you use a Makefile, try to write artifacts to `out/`.",
            ""
        );
    }

    private String getCc65ReadmeContent(String projectName, ProjectTemplate template) {
        boolean isC64 = template == ProjectTemplate.CC65_C64;
        if (isSpanishReadme()) {
            return isC64 ? getC64ReadmeContentEs(projectName) : getCc65ReadmeContentEs(projectName);
        }
        return isC64 ? getC64ReadmeContent(projectName) : getCc65ReadmeContent(projectName);
    }

    private String getCc65ReadmeContent(String projectName) {
        return String.join("\n",
            "# " + projectName,
            "",
            "Atari XE/XL project created with Samaruc.",
            "",
            "## Structure",
            "- Main source file: `src/main.c`",
            "- Build output: `out/`",
            "",
            "## Recommended Toolchain",
            "- CC65 (`cl65 -t atari`)",
            "- Emulator: Altirra",
            "",
            "## Notes",
            "- If you use a Makefile, try to write artifacts to `out/`.",
            ""
        );
    }

    private String getCc65ReadmeContentEs(String projectName) {
        return String.join("\n",
            "# " + projectName,
            "",
            "Proyecto Atari XE/XL creado con Samaruc.",
            "",
            "## Estructura",
            "- Archivo principal: `src/main.c`",
            "- Salida de compilaci\u00f3n: `out/`",
            "",
            "## Toolchain recomendada",
            "- CC65 (`cl65 -t atari`)",
            "- Emulador: Altirra",
            "",
            "## Notas",
            "- Si usas Makefile, intenta generar artefactos en `out/`.",
            ""
        );
    }

    private String getC64ReadmeContent(String projectName) {
        return String.join("\n",
            "# " + projectName,
            "",
            "Commodore 64 project created with Samaruc.",
            "",
            "## Structure",
            "- Main source file: `src/main.c`",
            "- Build output: `out/`",
            "",
            "## Recommended Toolchain",
            "- CC65 (`cl65 -t c64`)",
            "- Emulator: VICE (`x64sc`)",
            "",
            "## Notes",
            "- The compiled artifact is a `.prg` that VICE autostarts.",
            "- If you use a Makefile, try to write artifacts to `out/`.",
            ""
        );
    }

    private String getC64ReadmeContentEs(String projectName) {
        return String.join("\n",
            "# " + projectName,
            "",
            "Proyecto Commodore 64 creado con Samaruc.",
            "",
            "## Estructura",
            "- Archivo principal: `src/main.c`",
            "- Salida de compilaci\u00f3n: `out/`",
            "",
            "## Toolchain recomendada",
            "- CC65 (`cl65 -t c64`)",
            "- Emulador: VICE (`x64sc`)",
            "",
            "## Notas",
            "- El artefacto compilado es un `.prg` que VICE autoejecuta.",
            "- Si usas Makefile, intenta generar artefactos en `out/`.",
            ""
        );
    }

    private String getGenericReadmeContent(String projectName) {
        if (isSpanishReadme()) {
            return getGenericReadmeContentEs(projectName);
        }

        return String.join("\n",
            "# " + projectName,
            "",
            "C project created with Samaruc.",
            "",
            "## Structure",
            "- Main source file: `src/main.c`",
            "- Recommended build output: `out/`",
            "",
            "## Supported Toolchains",
            "- ZX Spectrum (z88dk)",
            "- Game Boy (GBDK)",
            "- Atari XE/XL and Commodore 64 (CC65)",
            "",
            "## Notes",
            "- You can switch platform from the compiler configuration.",
            ""
        );
    }

    private String getSpectrumReadmeContentEs(String projectName, ProjectTemplate template) {
        String demoNote = template == ProjectTemplate.SPECTRUM_SCREEN
            ? "\nEste proyecto usa la plantilla de pantalla inicial para ZX Spectrum."
            : "";

        return String.join("\n",
            "# " + projectName,
            "",
            "Proyecto ZX Spectrum creado con Samaruc.",
            demoNote,
            "",
            "## Estructura",
            "- Archivo principal: `src/main.c`",
            "- Salida de compilaciÃ³n: `out/`",
            "",
            "## Toolchain recomendada",
            "- z88dk (`zcc`)",
            "",
            "## Notas",
            "- Si usas Makefile, intenta generar artefactos en `out/`.",
            ""
        );
    }

    private String getGameBoyReadmeContentEs(String projectName, ProjectTemplate template) {
        String demoNote = template == ProjectTemplate.GAMEBOY_SPRITE
            ? "\nEste proyecto usa la plantilla de demo con sprite para Game Boy."
            : "";

        return String.join("\n",
            "# " + projectName,
            "",
            "Proyecto Game Boy creado con Samaruc.",
            demoNote,
            "",
            "## Estructura",
            "- Archivo principal: `src/main.c`",
            "- Salida de compilaciÃ³n: `out/`",
            "",
            "## Toolchain recomendada",
            "- GBDK (`lcc`)",
            "",
            "## Notas",
            "- Si usas Makefile, intenta generar artefactos en `out/`.",
            ""
        );
    }

    private String getGenericReadmeContentEs(String projectName) {
        return String.join("\n",
            "# " + projectName,
            "",
            "Proyecto C creado con Samaruc.",
            "",
            "## Estructura",
            "- Archivo principal: `src/main.c`",
            "- Salida recomendada: `out/`",
            "",
            "## Toolchains soportadas",
            "- ZX Spectrum (z88dk)",
            "- Game Boy (GBDK)",
            "- Atari XE/XL y Commodore 64 (CC65)",
            "",
            "## Notas",
            "- Puedes cambiar la plataforma desde la configuraciÃ³n del compilador.",
            ""
        );
    }

    private boolean isSpanishReadme() {
        return "es".equalsIgnoreCase(normalizeReadmeLanguage(projectReadmeLanguage));
    }

    private String normalizeReadmeLanguage(String language) {
        if (language == null) return "en";
        String normalized = language.trim().toLowerCase();
        return "es".equals(normalized) ? "es" : "en";
    }

    private String getGitignoreContent(ProjectTemplate template) {
        StringBuilder sb = new StringBuilder();
        appendGitignoreBase(sb);

        if (template == ProjectTemplate.GAMEBOY || template == ProjectTemplate.GAMEBOY_SPRITE) {
            appendGameBoyGitignore(sb);
        } else if (template == ProjectTemplate.SPECTRUM || template == ProjectTemplate.SPECTRUM_SCREEN) {
            appendSpectrumGitignore(sb);
        } else if (template == ProjectTemplate.CC65) {
            appendCc65Gitignore(sb);
        } else if (template == ProjectTemplate.CC65_C64) {
            appendC64Gitignore(sb);
        } else {
            appendGenericPlatformGitignore(sb);
        }

        return sb.toString();
    }

    private void appendGitignoreBase(StringBuilder sb) {
        sb.append("# Build output\n");
        sb.append("out/\n");
        sb.append("build/\n\n");
        sb.append("# Generic binary artifacts\n");
        sb.append("*.o\n");
        sb.append("*.obj\n");
        sb.append("*.bin\n");
        sb.append("*.rom\n");
        sb.append("*.ihx\n");
        sb.append("*.map\n");
        sb.append("*.sym\n");
        sb.append("*.noi\n");
        sb.append("*.lst\n");
        sb.append("*.asm\n\n");
    }

    private void appendSpectrumGitignore(StringBuilder sb) {
        sb.append("# ZX Spectrum outputs\n");
        sb.append("*.tap\n");
        sb.append("*.tzx\n");
        sb.append("*.sna\n");
        sb.append("*.z80\n");
        sb.append("\n");
    }

    private void appendGameBoyGitignore(StringBuilder sb) {
        sb.append("# Game Boy outputs\n");
        sb.append("*.gb\n");
        sb.append("*.gbc\n");
        sb.append("\n");
    }

    private void appendCc65Gitignore(StringBuilder sb) {
        sb.append("# CC65 / Atari 8-bit outputs\n");
        sb.append("*.xex\n");
        sb.append("*.atr\n");
        sb.append("*.com\n");
        sb.append("*.exe\n");
        sb.append("\n");
    }

    private void appendC64Gitignore(StringBuilder sb) {
        sb.append("# Commodore 64 / CC65 outputs\n");
        sb.append("*.prg\n");
        sb.append("*.d64\n");
        sb.append("*.t64\n");
        sb.append("*.tap\n");
        sb.append("*.crt\n");
        sb.append("\n");
    }

    private void appendGenericPlatformGitignore(StringBuilder sb) {
        sb.append("# Platform-specific outputs (generic template)\n");
        sb.append("*.tap\n");
        sb.append("*.tzx\n");
        sb.append("*.sna\n");
        sb.append("*.z80\n");
        sb.append("*.gb\n");
        sb.append("*.gbc\n");
        sb.append("\n");
    }

    private String loadResourceTemplate(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return "int main() { return 0; }\n";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "int main() { return 0; }\n";
        }
    }

    private String getTemplateMainContent(ProjectTemplate template) {
        if (template == ProjectTemplate.EMPTY) {
            return String.join("\n",
                "/* Proyecto C vacio */",
                "",
                "int main(void)",
                "{",
                "    return 0;",
                "}",
                ""
            );
        }

        if (template == ProjectTemplate.GAMEBOY) {
            return String.join("\n",
                "#include <gb/gb.h>",
                "",
                "void main(void)",
                "{",
                "    DISPLAY_ON;",
                "",
                "    while (1) {",
                "        wait_vbl_done();",
                "    }",
                "}",
                ""
            );
        }

        if (template == ProjectTemplate.GAMEBOY_SPRITE) {
            return String.join("\n",
                "#include <gb/gb.h>",
                "",
                "static const unsigned char sprite_data[] = {",
                "    0x3C, 0x3C, 0x42, 0x42, 0xA5, 0xA5, 0x81, 0x81,",
                "    0xA5, 0xA5, 0x99, 0x99, 0x42, 0x42, 0x3C, 0x3C",
                "};",
                "",
                "void main(void)",
                "{",
                "    SPRITES_8x8;",
                "    set_sprite_data(0, 1, sprite_data);",
                "    set_sprite_tile(0, 0);",
                "    move_sprite(0, 84, 72);",
                "    SHOW_SPRITES;",
                "    DISPLAY_ON;",
                "",
                "    while (1) {",
                "        wait_vbl_done();",
                "    }",
                "}",
                ""
            );
        }

        if (template == ProjectTemplate.SPECTRUM_SCREEN) {
            return String.join("\n",
                "#include <conio.h>",
                "",
                "void main()",
                "{",
                "    unsigned char key;",
                "    unsigned char border_color = 1;",
                "",
                "    textcolor(7);",
                "    textbackground(0);",
                "    bordercolor(1);",
                "    clrscr();",
                "",
                "    gotoxy(5, 5);",
                "    cputs(\"*** ZX SPECTRUM ***\");",
                "",
                "    gotoxy(6, 7);",
                "    cputs(\"SAMARUC TEMPLATE\");",
                "",
                "    gotoxy(2, 10);",
                "    cputs(\"CONTROLS:\");",
                "",
                "    gotoxy(2, 11);",
                "    cputs(\"SPACE - Change border\");",
                "",
                "    gotoxy(2, 12);",
                "    cputs(\"ENTER - Exit\");",
                "",
                "    while (1) {",
                "        if (kbhit()) {",
                "            key = getch();",
                "",
                "            if (key == ' ') {",
                "                border_color = (border_color + 1) % 8;",
                "                bordercolor(border_color);",
                "            }",
                "",
                "            if (key == 13) {",
                "                break;",
                "            }",
                "        }",
                "    }",
                "",
                "    clrscr();",
                "    textcolor(7);",
                "    textbackground(2);",
                "    gotoxy(10, 10);",
                "    cputs(\"BYE!\");",
                "    getch();",
                "}",
                ""
            );
        }

        if (template == ProjectTemplate.CPC) {
            return loadResourceTemplate("/templates/cpc_main.c");
        }
        if (template == ProjectTemplate.GCC) {
            return loadResourceTemplate("/templates/native_main.c");
        }
        if (template == ProjectTemplate.GCC_CALC) {
            return loadResourceTemplate("/templates/native_calc.c");
        }
        if (template == ProjectTemplate.CC65) {
            return loadResourceTemplate("/templates/cc65_main.c");
        }
        if (template == ProjectTemplate.CC65_C64) {
            return loadResourceTemplate("/templates/c64_main.c");
        }
        if (template == ProjectTemplate.GENERIC || template == ProjectTemplate.MAKEFILE) {
            return loadResourceTemplate("/templates/generic_main.c");
        }
        if (template == ProjectTemplate.ASM) {
            return loadResourceTemplate("/templates/asm_main.asm");
        }

        return loadResourceTemplate("/templates/zx_main.c");
    }

    /**
     * Abre un proyecto existente seleccionando una carpeta.
     * @param parentStage La ventana principal (stage) de la aplicaciÃ³n.
     * @return La carpeta del proyecto seleccionado, o null si se cancela la operaciÃ³n.
     */
    public File openExistingProject(Stage parentStage) {
        return openExistingProject(parentStage, null);
    }

    /**
     * Abre un proyecto existente seleccionando una carpeta.
     * @param parentStage La ventana principal (stage) de la aplicaciÃ³n.
     * @param bundle ResourceBundle opcional para i18n.
     * @return La carpeta del proyecto seleccionado, o null si se cancela la operaciÃ³n.
     */
    public File openExistingProject(Stage parentStage, ResourceBundle bundle) {
        return openExistingProject(parentStage, bundle, (File) null);
    }

    public File openExistingProject(Stage parentStage, ResourceBundle bundle, File initialDirectory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(msg(bundle, "dialog.project.open.selectFolder.title", "Abrir proyecto existente"));
        setInitialDirectoryIfValid(chooser, initialDirectory);
        File carpeta             = chooser.showDialog(parentStage);

        if (carpeta != null && carpeta.isDirectory()) {
            return carpeta;
        }

        return null;
    }

    private String msg(ResourceBundle bundle, String key, String fallback) {
        if (bundle == null) return fallback;
        try {
            return bundle.getString(key);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Maneja la acciÃ³n de crear una nueva pestaÃ±a.
     * @param event Evento de acciÃ³n.
     * @param newFileCounter Contador para nombrar nuevos archivos sin guardar.
     * @param tabPane TabPane donde se aÃ±adirÃ¡ la nueva pestaÃ±a.
     * @param tabFileMap Mapa que asocia pestaÃ±as a archivos.
     * @param syntaxHighlighter Resaltador de sintaxis a utilizar.
     */
    public void onNewTab(ActionEvent event, int newFileCounter, TabPane tabPane, Map<Tab, File> tabFileMap, SyntaxHighlighter syntaxHighlighter) {
        String tabName = "nuevo" + newFileCounter + ".c";
        UserActionMonitor.newTabCreated(tabName);
        addNewTab(null, "",  tabFileMap, syntaxHighlighter, tabPane, newFileCounter);
    }

    private void setInitialDirectoryIfValid(DirectoryChooser chooser, File initialDirectory) {
        if (chooser == null || initialDirectory == null || !initialDirectory.exists() || !initialDirectory.isDirectory()) {
            return;
        }

        try {
            chooser.setInitialDirectory(initialDirectory);
        } catch (Exception ignored) {
        }
    }

    public boolean isMarkdownFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        String normalized = fileName.toLowerCase();
        return normalized.endsWith(".md") || normalized.endsWith(".markdown");
    }

    private void setMarkdownPreviewVisible(Tab tab, Node editorNode, WebView previewView, boolean previewVisible) {
        if (tab != null) {
            tab.getProperties().put(TAB_PROP_MARKDOWN_PREVIEW, Boolean.valueOf(previewVisible));
        }

        if (editorNode != null) {
            editorNode.setVisible(!previewVisible);
            editorNode.setManaged(!previewVisible);
        }

        if (previewView != null) {
            previewView.setVisible(previewVisible);
            previewView.setManaged(previewVisible);
        }
    }

    private void updateMarkdownPreview(WebView previewView, String markdownText, String title) {
        if (previewView == null) return;

        WebEngine engine = previewView.getEngine();
        engine.loadContent(renderMarkdownToHtml(title, markdownText), "text/html");
    }

    private String renderMarkdownToHtml(String title, String markdownText) {
        String body = markdownToHtml(markdownText != null ? markdownText : "");
        String safeTitle = escapeHtml(title != null && !title.isBlank() ? title : "Markdown");
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
            + "<style>"
            + "body{font-family:Segoe UI,Arial,sans-serif;margin:24px;line-height:1.5;color:#1f2937;background:#fff;}"
            + "h1,h2,h3,h4,h5,h6{margin:1em 0 0.5em;}"
            + "pre{background:#f6f8fa;padding:12px;border-radius:8px;overflow:auto;}"
            + "code{font-family:Consolas,monospace;background:#f6f8fa;padding:0 4px;border-radius:4px;}"
            + "blockquote{border-left:4px solid #d0d7de;margin:0;padding:0 0 0 12px;color:#57606a;}"
            + "ul,ol{margin:0 0 1em 1.5em;}"
            + "a{color:#0969da;text-decoration:none;}"
            + "table{border-collapse:collapse;margin:1em 0;}"
            + "th,td{border:1px solid #d0d7de;padding:6px 10px;}"
            + "hr{border:0;border-top:1px solid #d0d7de;margin:1em 0;}"
            + ".meta{color:#6b7280;font-size:12px;margin-bottom:12px;}"
            + "</style></head><body>"
            + "<div class=\"meta\">" + safeTitle + "</div>"
            + body
            + "</body></html>";
    }

    private String markdownToHtml(String markdownText) {
        String[] lines = (markdownText != null ? markdownText : "").replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder html = new StringBuilder();
        boolean inUl = false;
        boolean inOl = false;
        boolean inCode = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inCode) {
                    html.append("</code></pre>");
                    inCode = false;
                } else {
                    closeLists(html, inUl, inOl);
                    inUl = false;
                    inOl = false;
                    html.append("<pre><code>");
                    inCode = true;
                }
                continue;
            }

            if (inCode) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }

            if (trimmed.isEmpty()) {
                closeLists(html, inUl, inOl);
                inUl = false;
                inOl = false;
                continue;
            }

            if (trimmed.startsWith("#")) {
                closeLists(html, inUl, inOl);
                inUl = false;
                inOl = false;
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                level = Math.min(6, Math.max(1, level));
                String text = trimmed.substring(level).trim();
                html.append("<h").append(level).append(">")
                    .append(renderInlineMarkdown(text))
                    .append("</h").append(level).append(">");
                continue;
            }

            if (trimmed.startsWith(">")) {
                closeLists(html, inUl, inOl);
                inUl = false;
                inOl = false;
                html.append("<blockquote>").append(renderInlineMarkdown(trimmed.substring(1).trim())).append("</blockquote>");
                continue;
            }

            if (trimmed.matches("[-*+]\\s+.+")) {
                if (!inUl) {
                    closeLists(html, inUl, inOl);
                    inOl = false;
                    html.append("<ul>");
                    inUl = true;
                }
                html.append("<li>").append(renderInlineMarkdown(trimmed.substring(2).trim())).append("</li>");
                continue;
            }

            if (trimmed.matches("\\d+\\.\\s+.+")) {
                if (!inOl) {
                    closeLists(html, inUl, inOl);
                    inUl = false;
                    html.append("<ol>");
                    inOl = true;
                }
                int dot = trimmed.indexOf('.');
                html.append("<li>").append(renderInlineMarkdown(trimmed.substring(dot + 1).trim())).append("</li>");
                continue;
            }

            closeLists(html, inUl, inOl);
            inUl = false;
            inOl = false;
            html.append("<p>").append(renderInlineMarkdown(trimmed)).append("</p>");
        }

        if (inCode) {
            html.append("</code></pre>");
        }
        closeLists(html, inUl, inOl);
        return html.toString();
    }

    private void closeLists(StringBuilder html, boolean inUl, boolean inOl) {
        if (inUl) html.append("</ul>");
        if (inOl) html.append("</ol>");
    }

    private String renderInlineMarkdown(String text) {
        if (text == null || text.isEmpty()) return "";

        String escaped = escapeHtml(text);
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        escaped = escaped.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        escaped = escaped.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        escaped = escaped.replaceAll("(?<!_)_([^_]+)_(?!_)", "<em>$1</em>");
        escaped = escaped.replaceAll("\\[([^\\]]+)\\]\\(([^\\)]+)\\)", "<a href=\"$2\">$1</a>");
        return escaped;
    }

    private String escapeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    // ---------------------------------------------------------------------
    // Edición profesional: breadcrumbs, Ln/Col, auto-cierre, atajos de línea
    // ---------------------------------------------------------------------

    private HBox buildBreadcrumbBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("breadcrumb-bar");
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return bar;
    }

    private void updateBreadcrumbBar(HBox bar, CodeArea area, String baseTitle) {
        if (bar == null || area == null) return;
        try {
            List<SymbolBreadcrumbService.BreadcrumbItem> items =
                new SymbolBreadcrumbService().getBreadcrumbs(area.getText(), baseTitle, area.getCurrentParagraph() + 1);
            bar.getChildren().clear();
            for (int i = 0; i < items.size(); i++) {
                SymbolBreadcrumbService.BreadcrumbItem item = items.get(i);
                Label chip = new Label(item.getName());
                chip.getStyleClass().add(i == 0 ? "breadcrumb-file" : "breadcrumb-item");
                chip.setCursor(Cursor.HAND);
                final int line = item.getLine();
                chip.setOnMouseClicked(e -> {
                    int off = area.position(line - 1, 0).toOffset();
                    area.moveTo(Math.min(off, area.getLength()));
                    area.requestFollowCaret();
                    area.requestFocus();
                });
                bar.getChildren().add(chip);
                if (i < items.size() - 1) {
                    Label sep = new Label("\u203a");
                    sep.getStyleClass().add("breadcrumb-sep");
                    bar.getChildren().add(sep);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String baseTitleOf(Tab tab) {
        Object t = tab.getProperties().get(TAB_PROP_BASE_TITLE);
        return t instanceof String ? (String) t : (tab.getText() != null ? tab.getText() : "");
    }

    private void updateCursorStatus(TabPane tabPane, Tab tab) {
        if (cursorStatusLabel == null || tabPane == null) return;
        if (tabPane.getSelectionModel().getSelectedItem() != tab) return;
        updateCursorStatus(tabPane);
    }

    /** Aplica los atajos estándar de edición de línea y el auto-cierre de pares. */
    private void attachProfessionalEditingKeys(CodeArea codeArea) {
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            boolean ctrlLike = event.isControlDown() || event.isMetaDown();
            KeyCode code = event.getCode();

            if (ctrlLike && event.isShiftDown() && !event.isAltDown() && code == KeyCode.K) {
                deleteSelectedLines(codeArea);
                event.consume();
                return;
            }
            if (event.isAltDown() && !ctrlLike && (code == KeyCode.UP || code == KeyCode.DOWN)) {
                boolean up = code == KeyCode.UP;
                if (event.isShiftDown()) {
                    duplicateSelectedLines(codeArea);
                } else {
                    moveSelectedLines(codeArea, up);
                }
                event.consume();
                return;
            }
            if (!ctrlLike && !event.isAltDown() && isPlainTypingChar(event)) {
                handleAutoCloseAndSkip(codeArea, event);
            }
        });
    }

    private static boolean isPlainTypingChar(KeyEvent event) {
        String t = event.getText();
        return t != null && t.length() == 1
            && event.getCode() != KeyCode.UNDEFINED
            && !Character.isISOControl(t.charAt(0));
    }

    private void handleAutoCloseAndSkip(CodeArea area, KeyEvent event) {
        char c = event.getText().charAt(0);
        int cp = area.getCaretPosition();
        String text = area.getText();
        if (area.getSelection().getLength() > 0) return;
        if (isOpenBracket(c)) {
            area.insertText(cp, String.valueOf(c) + closingOf(c));
            area.moveTo(cp + 1);
            area.requestFollowCaret();
            event.consume();
        } else if (c == '"') {
            area.insertText(cp, "\"\"");
            area.moveTo(cp + 1);
            area.requestFollowCaret();
            event.consume();
        } else if (c == '\'') {
            char after = cp < text.length() ? text.charAt(cp) : '\0';
            if (!Character.isLetterOrDigit(after)) {
                area.insertText(cp, "''");
                area.moveTo(cp + 1);
                area.requestFollowCaret();
                event.consume();
            }
        } else if (isCloseBracket(c)) {
            if (cp < text.length() && text.charAt(cp) == c) {
                area.moveTo(cp + 1);
                area.requestFollowCaret();
                event.consume();
            }
        }
    }

    private static int[] lineBlockBounds(CodeArea area) {
        int n = area.getParagraphs().size();
        int ss = area.getSelection().getStart();
        int se = area.getSelection().getEnd();
        var sp = area.offsetToPosition(ss, TwoDimensional.Bias.Forward);
        var ep = area.offsetToPosition(se, TwoDimensional.Bias.Forward);
        int firstP = sp.getMajor();
        int lastP = ep.getMajor();
        int start = lineStartOffset(area, firstP);
        int end = lastP + 1 < n ? lineStartOffset(area, lastP + 1) : area.getLength();
        return new int[] { start, end, firstP, lastP };
    }

    private static int lineStartOffset(CodeArea area, int paragraph) {
        int n = area.getParagraphs().size();
        if (n == 0) return 0;
        if (paragraph >= n) return area.getLength();
        if (paragraph < 0) return 0;
        return area.position(paragraph, 0).toOffset();
    }

    private void moveSelectedLines(CodeArea area, boolean up) {
        int[] b = lineBlockBounds(area);
        int start = b[0], end = b[1], firstP = b[2], lastP = b[3];
        int n = area.getParagraphs().size();
        String text = area.getText();
        if (up && firstP > 0) {
            int targetStart = lineStartOffset(area, firstP - 1);
            String targetLine = text.substring(targetStart, start);
            String blockLine = text.substring(start, end);
            area.replaceText(targetStart, end, blockLine + targetLine);
            area.selectRange(targetStart, targetStart + blockLine.length());
            area.moveTo(targetStart);
            area.requestFollowCaret();
        } else if (!up && lastP < n - 1) {
            int targetEnd = lastP + 2 < n ? lineStartOffset(area, lastP + 2) : area.getLength();
            String targetLine = text.substring(end, targetEnd);
            String blockLine = text.substring(start, end);
            area.replaceText(start, targetEnd, targetLine + blockLine);
            int newStart = start + targetLine.length();
            area.selectRange(newStart, newStart + blockLine.length());
            area.moveTo(newStart);
            area.requestFollowCaret();
        }
    }

    private void duplicateSelectedLines(CodeArea area) {
        int[] b = lineBlockBounds(area);
        int start = b[0], end = b[1];
        String blockLine = area.getText().substring(start, end);
        String insertion = end >= area.getLength() ? blockLine + "\n" : blockLine;
        area.insertText(start, insertion);
        int copyEnd = start + insertion.length();
        area.selectRange(start, Math.min(copyEnd, area.getLength()));
        area.moveTo(start);
        area.requestFollowCaret();
    }

    private void deleteSelectedLines(CodeArea area) {
        int[] b = lineBlockBounds(area);
        int start = b[0], end = b[1], firstP = b[2];
        area.deleteText(start, end);
        int n = area.getParagraphs().size();
        int target = lineStartOffset(area, Math.min(firstP, Math.max(0, n - 1)));
        area.moveTo(Math.min(target, area.getLength()));
        area.requestFollowCaret();
    }

    private StyleSpans<Collection<String>> applyExtraStyles(StyleSpans<Collection<String>> base, List<TextStyleRange> extras) {
        if (extras == null || extras.isEmpty()) return base;
        List<TextStyleRange> sorted = new ArrayList<>(extras);
        sorted.sort((a1, b1) -> Integer.compare(a1.start, b1.start));
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;
        for (StyleSpan<Collection<String>> span : base) {
            int s = pos;
            int e = pos + span.getLength();
            TreeSet<Integer> cuts = new TreeSet<>();
            cuts.add(s);
            cuts.add(e);
            for (TextStyleRange r : sorted) {
                if (r.end <= s || r.start >= e) continue;
                cuts.add(Math.max(r.start, s));
                cuts.add(Math.min(r.end, e));
            }
            java.util.Iterator<Integer> it = cuts.iterator();
            it.next();
            int prev = s;
            while (it.hasNext()) {
                int cut = it.next();
                if (cut <= prev) continue;
                Set<String> cls = new HashSet<>(span.getStyle());
                for (TextStyleRange r : sorted) {
                    if (r.start <= prev && r.end >= cut) {
                        cls.addAll(r.classes);
                    }
                }
                builder.add(new StyleSpan<>(cls, cut - prev));
                prev = cut;
            }
            pos = e;
        }
        return builder.create();
    }

    private static int[] findMatchingBracket(String text, int caret) {
        if (text == null || text.isEmpty()) return null;
        int n = text.length();
        int idx = -1;
        char at = caret >= 0 && caret < n ? text.charAt(caret) : '\0';
        char prev = caret > 0 && caret - 1 < n ? text.charAt(caret - 1) : '\0';
        if (isOpenBracket(prev) || isCloseBracket(prev)) {
            idx = caret - 1;
        } else if (isOpenBracket(at) || isCloseBracket(at)) {
            idx = caret;
        }
        if (idx < 0) return null;
        char ch = text.charAt(idx);
        if (isOpenBracket(ch)) {
            char close = closingOf(ch);
            int depth = 1;
            for (int i = idx + 1; i < n; i++) {
                char c = text.charAt(i);
                if (c == ch) depth++;
                else if (c == close && --depth == 0) return new int[] { idx, i };
            }
            return null;
        }
        char open = openingOf(ch);
        int depth = 1;
        for (int i = idx - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ch) depth++;
            else if (c == open && --depth == 0) return new int[] { i, idx };
        }
        return null;
    }

    private static boolean isOpenBracket(char c) {
        return c == '(' || c == '[' || c == '{';
    }

    private static boolean isCloseBracket(char c) {
        return c == ')' || c == ']' || c == '}';
    }

    private static char closingOf(char c) {
        switch (c) {
            case '(': return ')';
            case '[': return ']';
            case '{': return '}';
            default: return c;
        }
    }

    private static char openingOf(char c) {
        switch (c) {
            case ')': return '(';
            case ']': return '[';
            case '}': return '{';
            default: return c;
        }
    }

    private static final class TextStyleRange {
        final int start;
        final int end;
        final List<String> classes;

        TextStyleRange(int start, int end, List<String> classes) {
            this.start = start;
            this.end = end;
            this.classes = classes;
        }
    }

    public static class CodeMinimapPane extends javafx.scene.layout.Pane {
        private final CodeArea codeArea;
        private final javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(90, 600);

        public CodeMinimapPane(CodeArea codeArea) {
            this.codeArea = codeArea;
            getChildren().add(canvas);
            setMinWidth(90);
            setMaxWidth(90);
            prefWidthProperty().bind(widthProperty());
            canvas.widthProperty().bind(widthProperty());
            canvas.heightProperty().bind(heightProperty());

            codeArea.textProperty().addListener((obs, oldV, newV) -> drawMinimap());
            heightProperty().addListener((obs, oldV, newV) -> drawMinimap());
            widthProperty().addListener((obs, oldV, newV) -> drawMinimap());

            setOnMouseClicked(e -> {
                double y = e.getY();
                double totalLines = Math.max(1, codeArea.getParagraphs().size());
                double ratio = y / Math.max(1, getHeight());
                int targetParagraph = (int) Math.round(ratio * totalLines);
                int p = Math.max(0, Math.min(targetParagraph, codeArea.getParagraphs().size() - 1));
                codeArea.showParagraphAtTop(p);
                codeArea.moveTo(p, 0);
            });

            setOnMouseDragged(e -> {
                double y = e.getY();
                double totalLines = Math.max(1, codeArea.getParagraphs().size());
                double ratio = Math.max(0, Math.min(1.0, y / Math.max(1, getHeight())));
                int targetParagraph = (int) Math.round(ratio * totalLines);
                int p = Math.max(0, Math.min(targetParagraph, codeArea.getParagraphs().size() - 1));
                codeArea.showParagraphAtTop(p);
                codeArea.moveTo(p, 0);
            });

            drawMinimap();
        }

        public void drawMinimap() {
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0) return;

            gc.setFill(javafx.scene.paint.Color.web("#1e1e1e"));
            gc.fillRect(0, 0, w, h);

            var paragraphs = codeArea.getParagraphs();
            int total = paragraphs.size();
            if (total == 0) return;

            double lineHeight = Math.max(2.0, h / Math.max(1, total));
            gc.setFill(javafx.scene.paint.Color.web("#858585", 0.6));

            for (int i = 0; i < total; i++) {
                String text = paragraphs.get(i).getText();
                if (text == null || text.isBlank()) continue;
                double y = i * lineHeight;
                double lineW = Math.min(w - 10, text.length() * 1.5);
                gc.fillRect(5, y, Math.max(4, lineW), Math.max(1.5, lineHeight - 0.5));
            }
        }
    }
}
