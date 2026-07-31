package com.retroeditor.util;

import javafx.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.flowless.VirtualizedScrollPane;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.view.UIElements;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class FXUtils {

    private static final String TAB_PROP_BASE_TITLE = "editor.baseTitle";
    private static final String TAB_PROP_BASE_TEXT = "editor.baseText";
    private static final String TAB_PROP_CODE_AREA = "editor.codeArea";
    private static final String TAB_PROP_MARKDOWN_WEBVIEW = "editor.markdownWebView";
    private static final String TAB_PROP_MARKDOWN_PREVIEW = "editor.markdownPreviewVisible";
    private static final String EDITOR_STYLESHEET_MODERN = "/css/c-syntax.css";
    private static final String EDITOR_STYLESHEET_CLASSIC = "/css/c-syntax-classic.css";

    private enum ProjectTemplate {
        EMPTY,
        SPECTRUM,
        SPECTRUM_SCREEN,
        GAMEBOY,
        GAMEBOY_SPRITE
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

    /**
     * Obtiene un nombre de archivo predeterminado para guardar en función del título de la pestaña.
     * @param tab Pestaña actual.
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
     * Cierra una pestaña específica en el TabPane.
     * @param tab Pestaña a cerrar.
     * @param tabPane TabPane que contiene la pestaña.
     */
    public void closeTab(Tab tab, TabPane tabPane)   { tabPane.getTabs().remove(tab); }
    
    /**
     * Obtiene la pestaña actualmente seleccionada en el TabPane.
     * @param tabPane TabPane que contiene las pestañas.
     * @return Pestaña actualmente seleccionada.
     */
    public Tab getSelectedTab(TabPane tabPane)       { return tabPane.getSelectionModel().getSelectedItem(); }

    /**
     * Añade una nueva pestaña con un CodeArea y resaltado de sintaxis.
     * @param title Título de la pestaña.
     * @param content Contenido inicial del CodeArea.
     * @param syntaxHighlighter Resaltador de sintaxis a utilizar.
     * @param tabPane TabPane donde se añadirá la nueva pestaña.
     * @return La nueva pestaña creada.
     */
    public Tab addTab(String title, String content, SyntaxHighlighter syntaxHighlighter, TabPane tabPane) {
        CodeArea codeArea = new CodeArea();
        VirtualizedScrollPane<CodeArea> editorScrollPane = new VirtualizedScrollPane<>(codeArea);
        String initialText = content != null ? content : "";

        // Habilita la función de numeración de líneas para los párrafos. 
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.replaceText(initialText);

        // Aplica el resaltado inicial de sintaxis
        codeArea.setStyleSpans(0, syntaxHighlighter.computeHighlighting(initialText, title));

        // Reduce el tiempo de ejecución de las ediciones posteriores para evitar actualizaciones de estilo demasiado frecuentes
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(250))
                .subscribe(ignore -> {
                    try {
                        // Aplica el resaltado de sintaxis al texto modificado
                        codeArea.setStyleSpans(0, syntaxHighlighter.computeHighlighting(codeArea.getText(), title));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

        applyEditorAppearance(codeArea);
        Tab tab = new Tab(title, editorScrollPane);
        tab.getProperties().put(TAB_PROP_BASE_TITLE, title != null ? title : "");
        tab.getProperties().put(TAB_PROP_BASE_TEXT, initialText);
        tab.getProperties().put(TAB_PROP_CODE_AREA, codeArea);

        codeArea.textProperty().addListener((obs, oldText, newText) -> updateTabDirtyIndicator(tab, codeArea));

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        return tab;
    }

    public Tab addMarkdownTab(String title, String content, SyntaxHighlighter syntaxHighlighter, TabPane tabPane) {
        CodeArea codeArea = new CodeArea();
        VirtualizedScrollPane<CodeArea> editorScrollPane = new VirtualizedScrollPane<>(codeArea);
        WebView previewView = new WebView();
        String initialText = content != null ? content : "";

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.replaceText(initialText);
        applyEditorAppearance(codeArea);
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

        ToggleButton codeToggle = new ToggleButton("Código");
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

        codeArea.textProperty().addListener((obs, oldText, newText) -> updateTabDirtyIndicator(tab, codeArea));

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

    public void markTabSaved(Tab tab, String baseTitle, String savedText) {
        if (tab == null) return;

        String normalizedTitle = baseTitle != null ? baseTitle : "";
        String normalizedText = savedText != null ? savedText : "";

        tab.getProperties().put(TAB_PROP_BASE_TITLE, normalizedTitle);
        tab.getProperties().put(TAB_PROP_BASE_TEXT, normalizedText);
        tab.setText(normalizedTitle);
        Object preview = tab.getProperties().get(TAB_PROP_MARKDOWN_WEBVIEW);
        Object codeArea = tab.getProperties().get(TAB_PROP_CODE_AREA);
        if (preview instanceof WebView && codeArea instanceof CodeArea) {
            updateMarkdownPreview((WebView) preview, normalizedText, normalizedTitle);
        }
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
     * Añade una pestaña "Home" que carga un recurso HTML en un WebView.
     * @param resourcePath classpath del recurso (ej. "/views/home/home.html")
     */
    public Tab addHomeTab(String resourcePath, TabPane tabPane) {
        return addHomeTab(resourcePath, tabPane, "Home", null);
    }

    /**
     * Añade una pestaña Home con título e id personalizados.
     * @param resourcePath classpath del recurso HTML
     * @param tabPane contenedor de pestañas
     * @param tabTitle texto de la pestaña
     * @param tabId id opcional de la pestaña para localizarla luego
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

            tabPane.getTabs().add(0, tab); // añade como primera pestaña
            tabPane.getSelectionModel().select(tab);

            return tab;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Añade una pestaña Home con contenido HTML ya renderizado.
     * @param htmlContent html completo
     * @param tabPane contenedor de pestañas
     * @param tabTitle texto de la pestaña
     * @param tabId id opcional de la pestaña para localizarla luego
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
     * @param tabPane TabPane que contiene las pestañas.
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
     * Actualiza los textos de la interfaz según el ResourceBundle proporcionado.
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
     * Obtiene el CodeArea contenido en una pestaña específica.
     * @param tab Pestaña de la cual obtener el CodeArea.
     * @return CodeArea contenido en la pestaña, o null si no es un CodeArea.
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
     * Añade una nueva pestaña (tab) al TabPane y la asocia a un archivo en el mapa.
     * @param file Archivo asociado a la pestaña (puede ser null para nuevo archivo).
     * @param content Contenido inicial del CodeArea.
     * @param tabFileMap Mapa que asocia pestañas a archivos.
     * @param syntaxHighlighter Resaltador de sintaxis a utilizar.
     * @param tabPane TabPane donde se añadirá la nueva pestaña.
     * @param newFileCounter Contador para nombrar nuevos archivos sin guardar.
     */
    public void addNewTab(File file, String content, Map<Tab, File> tabFileMap, SyntaxHighlighter syntaxHighlighter, TabPane tabPane, int newFileCounter) {
        String tabName = (file != null) ? file.getName() : ("new" + newFileCounter + ".c");
        Tab    tab     = isMarkdownFileName(tabName)
            ? addMarkdownTab(tabName, content, syntaxHighlighter, tabPane)
            : addTab(tabName, content, syntaxHighlighter, tabPane);

        if (file == null) newFileCounter++;

        tabFileMap.put(tab, file);

        // Limpieza cuando el usuario cierra el tab con la X integrada
        tab.setOnClosed(e -> tabFileMap.remove(tab));
    }

    /**
     * Crea un nuevo proyecto en una carpeta seleccionada por el usuario.
     * @param parentStage La ventana principal (stage) de la aplicación.
     * @return La carpeta del nuevo proyecto, o null si se cancela la operación.
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
     * @param parentStage La ventana principal (stage) de la aplicación.
     * @param bundle ResourceBundle opcional para i18n.
     * @return La carpeta del nuevo proyecto, o null si se cancela la operación.
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
                    return null; // Usuario canceló
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

                        continue; // Reintentar eliminación
                    }
                    
                    // En este punto, la carpeta antigua fue eliminada; intentar crear una nueva a continuación en el flujo normal
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

        java.util.Map<ProjectTemplate, String> descriptions = new java.util.HashMap<>();
        descriptions.put(ProjectTemplate.EMPTY, msg(bundle, "dialog.project.new.template.description.empty", "Estructura minima en C con src/main.c sin dependencias."));
        descriptions.put(ProjectTemplate.SPECTRUM, msg(bundle, "dialog.project.new.template.description.spectrum", "Plantilla base para Spectrum con un main en C y salida por consola."));
        descriptions.put(ProjectTemplate.SPECTRUM_SCREEN, msg(bundle, "dialog.project.new.template.description.spectrumScreen", "Ejemplo para Spectrum con mensaje de bienvenida y pausa por teclado."));
        descriptions.put(ProjectTemplate.GAMEBOY, msg(bundle, "dialog.project.new.template.description.gameboy", "Plantilla base de Game Boy con bucle principal y sincronizacion VBlank."));
        descriptions.put(ProjectTemplate.GAMEBOY_SPRITE, msg(bundle, "dialog.project.new.template.description.gameboySprite", "Demo de Game Boy con sprite cargado y mostrado en pantalla."));

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
        Path mainFile = srcPath.resolve("main.c");

        Files.createDirectories(srcPath);
        Files.writeString(mainFile, getTemplateMainContent(template));

        if (options != null && options.createReadme) {
            Files.writeString(projectPath.resolve("README.md"), getReadmeContent(projectDir.getName(), template));
        }

        if (options != null && options.createGitignore) {
            Files.writeString(projectPath.resolve(".gitignore"), getGitignoreContent(template));
        }

        writeWorkspaceConfig(projectPath, template);
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
        if (template == ProjectTemplate.GAMEBOY || template == ProjectTemplate.GAMEBOY_SPRITE) {
            return "gameboy";
        }
        if (template == ProjectTemplate.SPECTRUM || template == ProjectTemplate.SPECTRUM_SCREEN) {
            return "spectrum";
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
            "- Salida de compilación: `out/`",
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
            "- Salida de compilación: `out/`",
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
            "",
            "## Notas",
            "- Puedes cambiar la plataforma desde la configuración del compilador.",
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

        return String.join("\n",
            "#include <conio.h>",
            "",
            "void main()",
            "{",
            "    clrscr();",
            "    textcolor(7);",
            "    textbackground(0);",
            "    bordercolor(1);",
            "",
            "    gotoxy(6, 10);",
            "    cputs(\"ZX SPECTRUM - BASIC\");",
            "",
            "    gotoxy(5, 12);",
            "    cputs(\"Press any key...\");",
            "",
            "    getch();",
            "}",
            ""
        );
    }

    /**
     * Abre un proyecto existente seleccionando una carpeta.
     * @param parentStage La ventana principal (stage) de la aplicación.
     * @return La carpeta del proyecto seleccionado, o null si se cancela la operación.
     */
    public File openExistingProject(Stage parentStage) {
        return openExistingProject(parentStage, null);
    }

    /**
     * Abre un proyecto existente seleccionando una carpeta.
     * @param parentStage La ventana principal (stage) de la aplicación.
     * @param bundle ResourceBundle opcional para i18n.
     * @return La carpeta del proyecto seleccionado, o null si se cancela la operación.
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
     * Maneja la acción de crear una nueva pestaña.
     * @param event Evento de acción.
     * @param newFileCounter Contador para nombrar nuevos archivos sin guardar.
     * @param tabPane TabPane donde se añadirá la nueva pestaña.
     * @param tabFileMap Mapa que asocia pestañas a archivos.
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
}
