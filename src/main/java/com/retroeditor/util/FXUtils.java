package com.retroeditor.util;

import javafx.event.ActionEvent;
import java.io.File;
import java.io.IOException;
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

import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.view.UIElements;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class FXUtils {

    /**
     * Obtiene un nombre de archivo predeterminado para guardar en función del título de la pestaña.
     * @param tab Pestaña actual.
     * @return Nombre de archivo sugerido.
     */
    public String getDefaultFileName(Tab tab)        { return tab.getText(); }

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

        // Habilita la función de numeración de líneas para los párrafos. 
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.replaceText(content);

        // Aplica el resaltado inicial de sintaxis
        codeArea.setStyleSpans(0, syntaxHighlighter.computeHighlighting(content));

        // Reduce el tiempo de ejecución de las ediciones posteriores para evitar actualizaciones de estilo demasiado frecuentes
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(250))
                .subscribe(ignore -> {
                    try {
                        // Aplica el resaltado de sintaxis al texto modificado
                        codeArea.setStyleSpans(0, syntaxHighlighter.computeHighlighting(codeArea.getText()));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

        codeArea.getStylesheets().add(getClass().getResource("/css/c-syntax.css").toExternalForm());
        Tab tab = new Tab(title, codeArea);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        return tab;
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
        if (uiElements.menuItemNuevo       != null) uiElements.menuItemNuevo.setText        (bundle.getString("button.new"));
        if (uiElements.menuItemAbrir       != null) uiElements.menuItemAbrir.setText        (bundle.getString("button.open"));
        if (uiElements.menuItemSalir       != null) uiElements.menuItemSalir.setText        (bundle.getString("menu.exit"));
        if (uiElements.menuItemConfig      != null) uiElements.menuItemConfig.setText       (bundle.getString("button.config"));
        if (uiElements.menuItemCerrar      != null) uiElements.menuItemCerrar.setText       (bundle.getString("button.close"));
        if (uiElements.menuItemGuardar     != null) uiElements.menuItemGuardar.setText      (bundle.getString("button.save"));
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
    private CodeArea getCodeArea(Tab tab) {
        if (tab == null) return null;

        if (tab.getContent() instanceof CodeArea) { return (CodeArea) tab.getContent(); }

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
        Tab    tab     = addTab(tabName, content, syntaxHighlighter, tabPane);

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

    /**
     * Crea un nuevo proyecto en una carpeta seleccionada por el usuario.
     * @param parentStage La ventana principal (stage) de la aplicación.
     * @param bundle ResourceBundle opcional para i18n.
     * @return La carpeta del nuevo proyecto, o null si se cancela la operación.
     */
    public File createNewProject(Stage parentStage, ResourceBundle bundle) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(msg(bundle, "dialog.project.new.selectFolder.title", "Selecciona carpeta para el nuevo proyecto"));
        File parent = chooser.showDialog(parentStage);

        if (parent != null) {
            // Pregunta al usuario por un nombre de proyecto
            while (true) {
                javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();

                dialog.setTitle      (msg(bundle, "dialog.project.new.name.title", "Nombre del proyecto"));
                dialog.setHeaderText (msg(bundle, "dialog.project.new.name.header", "Introduce el nombre para el nuevo proyecto"));
                dialog.setContentText(msg(bundle, "dialog.project.new.name.label", "Nombre:"));

                java.util.Optional<String> result = dialog.showAndWait();

                if (!result.isPresent()) return null; // Usuario canceló

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

                    if (!choice.isPresent() || choice.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) return null; // Cancelado

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
                            return null;
                        }

                        continue; // Reintentar eliminación
                    }
                    
                    // En este punto, la carpeta antigua fue eliminada; intentar crear una nueva a continuación en el flujo normal
                }

                if (nuevoProyecto.mkdir()) {
                    return nuevoProyecto;
                } else {
                    ButtonType retry  = new ButtonType(msg(bundle, "dialog.project.new.action.retry", "Reintentar"), ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel = new ButtonType(msg(bundle, "dialog.project.new.action.cancel", "Cancelar"), ButtonBar.ButtonData.CANCEL_CLOSE);
                    Alert alert       = new Alert     (Alert.AlertType.CONFIRMATION, 
                                                                                                 msg(bundle, "dialog.project.new.createError.content", "No se pudo crear la carpeta del proyecto. Comprueba permisos."), 
                                                                                                 retry, 
                                                                                                 cancel);
                    alert.setTitle(msg(bundle, "dialog.project.new.createError.title", "Error"));
                    alert.setHeaderText(null);
                    Optional<ButtonType> choice = alert.showAndWait();
                    if (!choice.isPresent() || choice.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                        return null;
                    }
                    // otro intento
                    continue;
                }
            }
        }
        return null;
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

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(msg(bundle, "dialog.project.open.selectFolder.title", "Abrir proyecto existente"));
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
}
