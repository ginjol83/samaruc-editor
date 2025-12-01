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
        try {
            WebView   web    = new WebView();
            WebEngine engine = web.getEngine();
            String    url    = getClass().getResource(resourcePath).toExternalForm();

            engine.load(url);

            Tab tab          = new Tab("Home", web);
            tab.setClosable(true);

            tabPane.getTabs().add(0, tab); // añade como primera pestaña
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

        uiElements.btnCut.setTooltip           (new Tooltip(bundle.getString("button.cut")));
        uiElements.btnOpen.setTooltip          (new Tooltip(bundle.getString("button.open")));
        uiElements.btnSave.setTooltip          (new Tooltip(bundle.getString("button.save")));
        uiElements.btnUndo.setTooltip          (new Tooltip(bundle.getString("button.undo")));
        uiElements.btnRedo.setTooltip          (new Tooltip(bundle.getString("button.redo")));
        uiElements.btnCopy.setTooltip          (new Tooltip(bundle.getString("button.copy")));
        uiElements.btnNuevo.setTooltip         (new Tooltip(bundle.getString("button.new")));
        uiElements.btnClose.setTooltip         (new Tooltip(bundle.getString("button.close")));
        uiElements.btnPaste.setTooltip         (new Tooltip(bundle.getString("button.paste")));
        uiElements.btnConfig.setTooltip        (new Tooltip(bundle.getString("button.config")));
        uiElements.btnCompilar.setTooltip      (new Tooltip(bundle.getString("button.compile")));
        uiElements.btnEjecutar.setTooltip      (new Tooltip(bundle.getString("button.run")));
        uiElements.btnNuevoProyecto.setTooltip (new Tooltip(bundle.getString("button.newProject")));
        uiElements.btnAbrirProyecto.setTooltip (new Tooltip(bundle.getString("button.openProject")));

        if (uiElements.menuArchivo         != null) uiElements.menuArchivo.setText          (bundle.getString("menu.file"));
        if (uiElements.menuEdicion         != null) uiElements.menuEdicion.setText          (bundle.getString("menu.edit"));
        if (uiElements.menuItemNuevo       != null) uiElements.menuItemNuevo.setText        (bundle.getString("button.new"));
        if (uiElements.menuItemAbrir       != null) uiElements.menuItemAbrir.setText        (bundle.getString("button.open"));
        if (uiElements.menuItemSalir       != null) uiElements.menuItemSalir.setText        (bundle.getString("menu.exit"));
        if (uiElements.menuItemConfig      != null) uiElements.menuItemConfig.setText       (bundle.getString("button.config"));
        if (uiElements.menuItemCerrar      != null) uiElements.menuItemCerrar.setText       (bundle.getString("button.close"));
        if (uiElements.menuCompilacion     != null) uiElements.menuCompilacion.setText      (bundle.getString("menu.project"));
        if (uiElements.menuItemGuardar     != null) uiElements.menuItemGuardar.setText      (bundle.getString("button.save"));
        if (uiElements.menuItemCompilar    != null) uiElements.menuItemCompilar.setText     (bundle.getString("button.compile"));
        if (uiElements.menuItemEjecutar    != null) uiElements.menuItemEjecutar.setText     (bundle.getString("button.run"));
        if (uiElements.menuItemGuardarComo != null) uiElements.menuItemGuardarComo.setText  (bundle.getString("button.saveAs"));
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
    }

    /**
     * Crea un nuevo proyecto en una carpeta seleccionada por el usuario.
     * @param parentStage La ventana principal (stage) de la aplicación.
     * @return La carpeta del nuevo proyecto, o null si se cancela la operación.
     */
    public File createNewProject(Stage parentStage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selecciona carpeta para el nuevo proyecto");
        File parent = chooser.showDialog(parentStage);

        if (parent != null) {
            // Pregunta al usuario por un nombre de proyecto
            while (true) {
                javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();

                dialog.setTitle      ("Nombre del proyecto");
                dialog.setHeaderText ("Introduce el nombre para el nuevo proyecto");
                dialog.setContentText("Nombre:");

                java.util.Optional<String> result = dialog.showAndWait();

                if (!result.isPresent()) return null; // Usuario canceló

                String nombre = result.get().trim();

                if (nombre.isEmpty()) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle      ("Nombre inválido");
                    alert.setHeaderText (null);
                    alert.setContentText("El nombre del proyecto no puede estar vacío.");
                    alert.showAndWait   ();

                    continue; 
                }

                // Valida que el nombre no contenga separadores de ruta
                if (nombre.contains(java.io.File.separator) || nombre.contains("/")) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle      ("Nombre inválido");
                    alert.setHeaderText (null);
                    alert.setContentText("El nombre no puede contener separadores de ruta.");
                    alert.showAndWait   ();
                    continue;
                }

                File nuevoProyecto = new File(parent, nombre);

                if (nuevoProyecto.exists()) {
                    ButtonType overwrite = new ButtonType("Sobrescribir", ButtonBar.ButtonData.YES);
                    ButtonType retry     = new ButtonType("Reintentar"  , ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel    = new ButtonType("Cancelar"    , ButtonBar.ButtonData.CANCEL_CLOSE);
                    Alert      alert     = new Alert( Alert.AlertType.CONFIRMATION,
                                                    "Ya existe una carpeta con ese nombre. Puedes sobrescribirla (se eliminará su contenido) o probar otro nombre.",
                                                    overwrite, 
                                                    retry, 
                                                    cancel);

                    alert.setTitle      ("Ya existe");
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

                        ButtonType retryDel  = new ButtonType("Reintentar", ButtonBar.ButtonData.OK_DONE);
                        ButtonType cancelDel = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);
                        Alert err            = new Alert(AlertType.CONFIRMATION,
                                                         "No se pudo eliminar la carpeta existente: " + ex.getMessage(), 
                                                         retryDel, 
                                                         cancelDel);

                        err.setTitle     ("Error al sobrescribir");
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
                    ButtonType retry  = new ButtonType("Reintentar", ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
                    Alert alert       = new Alert     (Alert.AlertType.CONFIRMATION, 
                                                                                                 "No se pudo crear la carpeta del proyecto. Comprueba permisos.", 
                                                                                                 retry, 
                                                                                                 cancel);
                    alert.setTitle("Error");
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

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Abrir proyecto existente");
        File carpeta             = chooser.showDialog(parentStage);

        if (carpeta != null && carpeta.isDirectory()) {
            return carpeta;
        }

        return null;
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
        addNewTab(null, "",  tabFileMap, syntaxHighlighter, tabPane, newFileCounter);
    }
}
