package com.retroeditor.controller;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import org.kordamp.ikonli.javafx.FontIcon;

import com.retroeditor.controller.build.BuildController;
import com.retroeditor.controller.config.ConfigController;
import com.retroeditor.controller.editor.EditOptionsController;
import com.retroeditor.controller.editor.FileOptionsController;
import com.retroeditor.controller.help.HelpController;
import com.retroeditor.controller.projectExplorer.ProjectExplorerController;
import com.retroeditor.controller.search.SearchController;
import com.retroeditor.controller.terminal.TerminalController;
import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.EditorModel;
import com.retroeditor.service.AppLogger;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.util.FXUtils;
import com.retroeditor.view.UIElements;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class MainController {
    private final File userConfigFile = new File(System.getProperty("user.home"), ".retroeditor.properties");

    private FXUtils fxUtils = new FXUtils();
    private final ConfigModel configModel = new ConfigModel();

    private final HelpController helpController = new HelpController();
    private final EditOptionsController editOptionsController = new EditOptionsController();
    private final FileOptionsController fileOptionsController = new FileOptionsController();
    private final TerminalController terminalController = new TerminalController();
    private final BuildController buildController = new BuildController();

    private int newFileCounter = 1;

    private ResourceBundle bundle;
    private EditorModel editorModel;
    private File currentProjectDir;

    private SyntaxHighlighter syntaxHighlighter;
    private ProjectExplorerController projectExplorerController;
    private SearchController searchController;
    private boolean isRefreshingLanguage = false;

    @FXML private MenuBar menuBar;
    @FXML private Label lblFooter;
    @FXML private Menu menuArchivo;
    @FXML private Menu menuEdicion;
    @FXML private Menu menuCompilacion;
    @FXML private ToolBar toolBar;
    @FXML private TabPane tabPane;
    @FXML private AnchorPane searchDock;
    @FXML private Button btnCut;
    @FXML private Button btnOpen;
    @FXML private Button btnSave;
    @FXML private Button btnUndo;
    @FXML private Button btnRedo;
    @FXML private Button btnCopy;
    @FXML private Button btnNuevo;
    @FXML private Button btnClose;
    @FXML private Button btnPaste;
    @FXML private Button btnConfig;
    @FXML private Button btnFind;
    @FXML private Button btnFindProject;
    @FXML private Button btnCompilar;
    @FXML private Button btnEjecutar;
    @FXML private Button btnNuevoProyecto;
    @FXML private Button btnAbrirProyecto;
    @FXML private ToggleButton btnThemeToggle;
    @FXML private Button btnManual;
    @FXML private MenuItem menuItemPegar;
    @FXML private MenuItem menuItemFind;
    @FXML private MenuItem menuItemFindProject;
    @FXML private MenuItem menuItemNuevo;
    @FXML private MenuItem menuItemAbrir;
    @FXML private MenuItem menuItemSalir;
    @FXML private MenuItem menuItemCerrar;
    @FXML private MenuItem menuItemConfig;
    @FXML private MenuItem menuItemCortar;
    @FXML private MenuItem menuItemCopiar;
    @FXML private MenuItem menuItemGuardar;
    @FXML private MenuItem menuItemRehacer;
    @FXML private MenuItem menuItemCompilar;
    @FXML private MenuItem menuItemEjecutar;
    @FXML private MenuItem menuItemDeshacer;
    @FXML private MenuItem menuItemGuardarComo;
    @FXML private AnchorPane projectExplorerAnchor;
    @FXML private TabPane outputTabPane;
    @FXML private TextArea consoleOutputArea;
    @FXML private TextArea debuggerOutputArea;

    private UIElements getUIElements() {
        UIElements elements = new UIElements();

        elements.btnCompilar = btnCompilar;
        elements.btnEjecutar = btnEjecutar;
        elements.btnNuevo = btnNuevo;
        elements.btnOpen = btnOpen;
        elements.btnSave = btnSave;
        elements.btnClose = btnClose;
        elements.btnConfig = btnConfig;
        elements.btnNuevoProyecto = btnNuevoProyecto;
        elements.btnAbrirProyecto = btnAbrirProyecto;
        elements.btnUndo = btnUndo;
        elements.btnRedo = btnRedo;
        elements.btnCut = btnCut;
        elements.btnCopy = btnCopy;
        elements.btnPaste = btnPaste;
        elements.menuArchivo = menuArchivo;
        elements.menuCompilacion = menuCompilacion;
        elements.menuEdicion = menuEdicion;
        elements.menuItemNuevo = menuItemNuevo;
        elements.menuItemAbrir = menuItemAbrir;
        elements.menuItemGuardar = menuItemGuardar;
        elements.menuItemGuardarComo = menuItemGuardarComo;
        elements.menuItemCerrar = menuItemCerrar;
        elements.menuItemConfig = menuItemConfig;
        elements.menuItemSalir = menuItemSalir;
        elements.menuItemCompilar = menuItemCompilar;
        elements.menuItemEjecutar = menuItemEjecutar;
        elements.menuItemFind = menuItemFind;
        elements.menuItemFindProject = menuItemFindProject;

        return elements;
    }

    private final Map<Tab, File> tabFileMap = new HashMap<>(); // Solo para mapear tabs a archivos

    @FXML
    public void initialize() {
        // Cargar configuración del usuario (incluye gbdk_bin)
        configModel.getDefaultLanguage(userConfigFile);
        // Leer idioma desde configModel (ya cargado por getDefaultLanguage)
        String lang = configModel.getConfigProperty("idioma", "es");
        Locale locale = java.util.Locale.forLanguageTag(lang);
        bundle = ResourceBundle.getBundle("i18n.MessagesBundle", locale);

        // Inicializar modelo, resaltador y vista
        editorModel = new EditorModel();
        syntaxHighlighter = new SyntaxHighlighter();
        fxUtils = new FXUtils();

        // Listen for language changes on the shared config model so UI updates whenever idioma changes
        configModel.idiomaProperty().addListener((obs, oldV, newV) -> {
            try {
                Locale newLocale = java.util.Locale.forLanguageTag(newV != null ? newV : "es");
                bundle = ResourceBundle.getBundle("i18n.MessagesBundle", newLocale);
                Platform.runLater(() -> {
                    fxUtils.refreshLanguage(getUIElements(), bundle);
                    applyBundleToUI();
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // Cargar ProjectExplorer solo una vez
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ProjectExplorer.fxml"));
            Parent explorer = loader.load();
            projectExplorerController = loader.getController();
            projectExplorerController.setMainController(this);
            projectExplorerAnchor.getChildren().setAll(explorer);
        } catch (Exception e) {
            e.printStackTrace();
        }

    // Deferir la inicialización del SearchController y el registro de atajos hasta que la Scene esté lista
    Platform.runLater(() -> {
            Scene scene = (tabPane != null) ? tabPane.getScene() : null;
            if (scene != null) {
        // Initialize app logger now that FXML TextAreas are available
        try { AppLogger.init(consoleOutputArea, debuggerOutputArea); } catch (Throwable ignored) {}
                // Registrar atajos de teclado en la escena (Ctrl+F = buscar en archivo, Ctrl+H = buscar en proyecto)
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), () -> onFindInFile(null));
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN), () -> onFindInProject(null));

                // Crear el SearchController con el Stage ya disponible como owner
                try {
                    Stage owner = (Stage) scene.getWindow();
                    searchController = new SearchController(owner, tabPane, tabFileMap, editorModel, this, fxUtils);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        // Inicializar iconos y tooltips
        if (btnCompilar != null) {
            btnCompilar.setGraphic(new FontIcon("fas-hammer"));
            btnCompilar.setTooltip(new Tooltip(bundle.getString("button.compile")));
        }
        if (btnEjecutar != null) {
            btnEjecutar.setGraphic(new FontIcon("fas-play"));
            btnEjecutar.setTooltip(new Tooltip(bundle.getString("button.run")));
        }
        if (btnNuevo != null) {
            btnNuevo.setGraphic(new FontIcon("fas-file-alt"));
            btnNuevo.setTooltip(new Tooltip(bundle.getString("button.new")));
        }
        if (btnOpen != null) {
            btnOpen.setGraphic(new FontIcon("fas-folder-open"));
            btnOpen.setTooltip(new Tooltip(bundle.getString("button.open")));
        }
        if (btnSave != null) {
            btnSave.setGraphic(new FontIcon("fas-save"));
            btnSave.setTooltip(new Tooltip(bundle.getString("button.save")));
        }
        if (btnClose != null) {
            btnClose.setGraphic(new FontIcon("fas-times"));
            btnClose.setTooltip(new Tooltip(bundle.getString("button.close")));
        }
        if (btnConfig != null) {
            btnConfig.setGraphic(new FontIcon("fas-cog"));
            btnConfig.setTooltip(new Tooltip(bundle.getString("button.config")));
        }
        if (btnNuevoProyecto != null) {
            btnNuevoProyecto.setGraphic(new FontIcon("fas-folder-plus"));
            btnNuevoProyecto.setTooltip(new Tooltip(bundle.getString("button.newProject")));
        }
        if (btnAbrirProyecto != null) {
            btnAbrirProyecto.setGraphic(new FontIcon("fas-folder-open"));
            btnAbrirProyecto.setTooltip(new Tooltip(bundle.getString("button.openProject")));
        }
        if (btnManual != null) {
            btnManual.setGraphic(new FontIcon("fas-question-circle"));
            btnManual.setTooltip(new Tooltip("Abrir manual de usuario"));
        }
        if (btnUndo != null) {
            btnUndo.setGraphic(new FontIcon("fas-undo"));
            btnUndo.setTooltip(new Tooltip(bundle.getString("button.undo")));
        }
        if (btnRedo != null) {
            btnRedo.setGraphic(new FontIcon("fas-redo"));
            btnRedo.setTooltip(new Tooltip(bundle.getString("button.redo")));
        }
        if (btnCut != null) {
            btnCut.setGraphic(new FontIcon("fas-cut"));
            btnCut.setTooltip(new Tooltip(bundle.getString("button.cut")));
        }
        if (btnCopy != null) {
            btnCopy.setGraphic(new FontIcon("fas-copy"));
            btnCopy.setTooltip(new Tooltip(bundle.getString("button.copy")));
        }
        if (btnFind != null) {
            btnFind.setGraphic(new FontIcon("fas-search"));
            btnFind.setTooltip(new Tooltip("Buscar en archivo (Ctrl+F)"));
        }
        if (btnFindProject != null) {
            btnFindProject.setGraphic(new FontIcon("fas-search-plus"));
            btnFindProject.setTooltip(new Tooltip("Buscar en proyecto (Ctrl+H)"));
        }
        if (btnPaste != null) {
            btnPaste.setGraphic(new FontIcon("fas-paste"));
            btnPaste.setTooltip(new Tooltip(bundle.getString("button.paste")));
        }

        // Theme toggle wiring
        if (btnThemeToggle != null) {
            btnThemeToggle.setTooltip(new Tooltip("Alternar tema claro/oscuro"));
            try { btnThemeToggle.setGraphic(new FontIcon("fas-adjust")); } catch (Exception ignored) {}
            btnThemeToggle.setOnAction(evt -> applyTheme(btnThemeToggle.isSelected()));
        }

        // Improve toolbar visual: larger toolbar, bigger icons and nicer background
        try {
            if (toolBar != null) {
                toolBar.getStyleClass().add("tool-bar");
            }

            Button[] tbButtons = new Button[] { btnNuevoProyecto, btnAbrirProyecto, btnNuevo, btnOpen, btnSave, btnClose, btnUndo, btnRedo, btnFind, btnFindProject, btnCut, btnCopy, btnPaste, btnCompilar, btnEjecutar, btnConfig, btnManual };
            for (Button b : tbButtons) {
                if (b == null) continue;
                b.getStyleClass().add("tb-button");
                if (b.getGraphic() instanceof FontIcon) {
                    FontIcon fi = (FontIcon) b.getGraphic();
                    try { fi.setIconSize(20); } catch (Exception ignore) {}
                }
            }
        } catch (Exception ignored) {}

        // Menús y textos
        if (menuArchivo != null) menuArchivo.setText(bundle.getString("menu.file"));
        if (menuCompilacion != null) menuCompilacion.setText(bundle.getString("menu.project"));
        if (menuEdicion != null) menuEdicion.setText(bundle.getString("menu.edit"));
        if (menuItemNuevo != null) menuItemNuevo.setText(bundle.getString("button.new"));
        if (menuItemAbrir != null) menuItemAbrir.setText(bundle.getString("button.open"));
        if (menuItemGuardar != null) menuItemGuardar.setText(bundle.getString("button.save"));
        if (menuItemGuardarComo != null) menuItemGuardarComo.setText(bundle.getString("button.saveAs"));
        if (menuItemCerrar != null) menuItemCerrar.setText(bundle.getString("button.close"));
        if (menuItemConfig != null) menuItemConfig.setText(bundle.getString("button.config"));
        if (menuItemSalir != null) menuItemSalir.setText(bundle.getString("menu.exit"));
        if (menuItemCompilar != null) menuItemCompilar.setText(bundle.getString("button.compile"));
        if (menuItemEjecutar != null) menuItemEjecutar.setText(bundle.getString("button.run"));
        if (menuItemDeshacer != null) menuItemDeshacer.setText(bundle.getString("button.undo"));
        if (menuItemRehacer != null) menuItemRehacer.setText(bundle.getString("button.redo"));
        if (menuItemCortar != null) menuItemCortar.setText(bundle.getString("button.cut"));
        if (menuItemCopiar != null) menuItemCopiar.setText(bundle.getString("button.copy"));
        if (menuItemPegar != null) menuItemPegar.setText(bundle.getString("button.paste"));

        // Instead of opening a new.c by default, show a homepage (local HTML) in the first tab
        try {
            fxUtils.addHomeTab("/views/home/home.html", tabPane);
        } catch (Throwable ignored) {
            // fallback to previous behavior
            fxUtils.addNewTab(null, "", tabFileMap, syntaxHighlighter, tabPane, newFileCounter);
        }

        // Attach default stylesheet to the Scene when available
        Platform.runLater(() -> {
            try {
                if (tabPane != null && tabPane.getScene() != null) {
                    Scene scene = tabPane.getScene();
                    String cssLight = getClass().getResource("/css/app.css").toExternalForm();
                    String cssDark = getClass().getResource("/css/app-dark.css").toExternalForm();
                    if (!scene.getStylesheets().contains(cssLight) && !scene.getStylesheets().contains(cssDark)) {
                        scene.getStylesheets().add(cssLight);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    @FXML
    private void onFindInFile(ActionEvent event) {
        if (searchController != null) searchController.showFindInFileDialog();
    }


    @FXML
    private void onFindInProject(ActionEvent event) {
        if (searchController != null) searchController.showFindInProjectDialog();
    }

    @FXML
    private void onCompilar(ActionEvent event) {
        buildController.onCompilar(event, consoleOutputArea, tabPane, tabFileMap, configModel);
    }

    @FXML
    private void onEjecutar(ActionEvent event) {
        buildController.onEjecutar(event, consoleOutputArea, tabPane, tabFileMap, configModel);
    }

    @FXML
    private void onNuevoProyecto(ActionEvent event) {
        File creado = fxUtils.createNewProject(getStage());
        if (creado != null) {
            setProyectoActual(creado);
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error al crear proyecto");
            alert.setHeaderText("No se pudo crear la carpeta del nuevo proyecto");
            alert.setContentText("Verifica permisos de escritura o elige otra ubicación.");
            alert.showAndWait();
        }
    }

    @FXML
    private void onAbrirProyecto(ActionEvent event) {
        File carpeta = fxUtils.openExistingProject(getStage());
        if (carpeta != null) setProyectoActual(carpeta);
    }

    public File getCurrentProjectDir() { return currentProjectDir; }

    // Expose MenuBar for plugins or other host integrations
    public MenuBar getMenuBar() { return menuBar; }

    public void setProyectoActual(File carpeta) {
        this.currentProjectDir = carpeta;
        if (projectExplorerController != null) projectExplorerController.setRootDirectory(carpeta);
        try { if (carpeta != null) fileOptionsController.setDefaultSaveDirectory(carpeta); } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void openFileFromExplorer(File file) {
        if (file == null || !file.isFile()) return;
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            if (file.equals(entry.getValue())) { tabPane.getSelectionModel().select(entry.getKey()); return; }
        }
        try {
            editorModel.openFile(file);
            String content = editorModel.getFileContent(file);
            Tab tab = fxUtils.addTab(file.getName(), content, syntaxHighlighter, tabPane);
            tabFileMap.put(tab, file);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML private Stage configStage = null;
    private boolean isConfigDialogOpen = false;

    @FXML
    public void onOpenConfig(ActionEvent event) {
        if (event != null) event.consume();
        if (isConfigDialogOpen || isRefreshingLanguage) {
            if (configStage != null && configStage.isShowing()) configStage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ConfigView.fxml"));
            Parent root = loader.load();
            ConfigController configController = loader.getController();
            configController.setConfigModel(configModel);
            configController.reloadFromModel();
            configModel.idiomaProperty().addListener((obs, oldV, newV) -> {
                try {
                    Locale newLocale = java.util.Locale.forLanguageTag(newV != null ? newV : "es");
                    bundle = ResourceBundle.getBundle("i18n.MessagesBundle", newLocale);
                    Platform.runLater(() -> fxUtils.refreshLanguage(getUIElements(), bundle));
                } catch (Exception ex) { ex.printStackTrace(); }
            });
            configController.setOnLanguageChanged(() -> {
                String langCode = configModel.getConfigProperty("idioma", "es");
                Locale newLocale = java.util.Locale.forLanguageTag(langCode);
                bundle = ResourceBundle.getBundle("i18n.MessagesBundle", newLocale);
                fxUtils.refreshLanguage(getUIElements(), bundle);
            });
            configController.setOnCloseCallback(() -> { configStage = null; isConfigDialogOpen = false; });
            configStage = new Stage();
            configStage.setTitle("Configuración");
            configStage.setScene(new javafx.scene.Scene(root));
            configStage.initOwner(getStage());
            configStage.setOnHidden(e -> { configStage = null; isConfigDialogOpen = false; });
            isConfigDialogOpen = true;
            configStage.show();
        } catch (Exception e) {
            System.err.println("[ERROR] No se pudo abrir la configuración:");
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error de configuración");
            alert.setHeaderText("No se pudo abrir la ventana de configuración");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Metodo para generar un nuevo tab de editor
     */
    @FXML
    public void onNewTab(ActionEvent event) {
        // Asegurarse de que hay un proyecto abierto antes de crear un nuevo archivo
        if (this.currentProjectDir == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Proyecto no abierto");
            alert.setHeaderText(null);
            alert.setContentText("No se puede crear un archivo nuevo sin un proyecto abierto.\nAbre o crea un proyecto primero.");
            alert.showAndWait();
            return;
        }
        fxUtils.onNewTab(event, newFileCounter, tabPane, tabFileMap, syntaxHighlighter);
    }

    @FXML
    private void onExit(ActionEvent event) { Platform.exit(); }

    @FXML
    private void onOpenFile(ActionEvent event)   { fileOptionsController.onOpenFile(event, tabPane, tabFileMap, editorModel, syntaxHighlighter); }

    @FXML
    private void onSaveFile(ActionEvent event)   { fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel); }

    @FXML
    private void onSaveAsFile(ActionEvent event) { fileOptionsController.onSaveAsFile(event, tabPane, tabFileMap, editorModel); }

    @FXML
    private void onCloseFile(ActionEvent event)  { fileOptionsController.onCloseFile(event, tabPane, tabFileMap, editorModel); }

    @FXML
    private void onUndo(ActionEvent event)       { editOptionsController.onUndo(event, tabPane); }

    @FXML
    private void onRedo(ActionEvent event)       { editOptionsController.onRedo(event, tabPane); }

    @FXML
    private void onCut(ActionEvent event)        { editOptionsController.onCut(event, tabPane); }

    @FXML
    private void onCopy(ActionEvent event)       { editOptionsController.onCopy(event, tabPane); }

    @FXML
    private void onPaste(ActionEvent event)      { editOptionsController.onPaste(event, tabPane); }

    @FXML
    private void onOpenManual()                               { helpController.onOpenManual(); }

    @FXML
    private void onShowCredits()                              { helpController.onShowCredits(); }

    /**
     * Aplica el ResourceBundle actual a los elementos de la UI
     */
    private void applyBundleToUI() {
        if (bundle == null) return;

        if (btnCompilar         != null) btnCompilar.setTooltip(new Tooltip(bundle.getString("button.compile")));
        if (btnEjecutar         != null) btnEjecutar.setTooltip(new Tooltip(bundle.getString("button.run")));
        if (btnNuevo            != null) btnNuevo.setTooltip(new Tooltip(bundle.getString("button.new")));
        if (btnOpen             != null) btnOpen.setTooltip(new Tooltip(bundle.getString("button.open")));
        if (btnSave             != null) btnSave.setTooltip(new Tooltip(bundle.getString("button.save")));
        if (btnClose            != null) btnClose.setTooltip(new Tooltip(bundle.getString("button.close")));
        if (btnConfig           != null) btnConfig.setTooltip(new Tooltip(bundle.getString("button.config")));
        if (btnNuevoProyecto    != null) btnNuevoProyecto.setTooltip(new Tooltip(bundle.getString("button.newProject")));
        if (btnAbrirProyecto    != null) btnAbrirProyecto.setTooltip(new Tooltip(bundle.getString("button.openProject")));
        if (btnManual           != null) btnManual.setTooltip(new Tooltip("Abrir manual de usuario"));
        if (btnUndo             != null) btnUndo.setTooltip(new Tooltip(bundle.getString("button.undo")));
        if (btnRedo             != null) btnRedo.setTooltip(new Tooltip(bundle.getString("button.redo")));
        if (btnCut              != null) btnCut.setTooltip(new Tooltip(bundle.getString("button.cut")));
        if (btnCopy             != null) btnCopy.setTooltip(new Tooltip(bundle.getString("button.copy")));
        if (btnPaste            != null) btnPaste.setTooltip(new Tooltip(bundle.getString("button.paste")));

        if (menuArchivo         != null) menuArchivo.setText(bundle.getString("menu.file"));
        if (menuCompilacion     != null) menuCompilacion.setText(bundle.getString("menu.project"));
        if (menuEdicion         != null) menuEdicion.setText(bundle.getString("menu.edit"));
        if (menuItemNuevo       != null) menuItemNuevo.setText(bundle.getString("button.new"));
        if (menuItemAbrir       != null) menuItemAbrir.setText(bundle.getString("button.open"));
        if (menuItemGuardar     != null) menuItemGuardar.setText(bundle.getString("button.save"));
        if (menuItemGuardarComo != null) menuItemGuardarComo.setText(bundle.getString("button.saveAs"));
        if (menuItemCerrar      != null) menuItemCerrar.setText(bundle.getString("button.close"));
        if (menuItemConfig      != null) menuItemConfig.setText(bundle.getString("button.config"));
        if (menuItemSalir       != null) menuItemSalir.setText(bundle.getString("menu.exit"));
        if (menuItemCompilar    != null) menuItemCompilar.setText(bundle.getString("button.compile"));
        if (menuItemEjecutar    != null) menuItemEjecutar.setText(bundle.getString("button.run"));
        if (menuItemDeshacer    != null) menuItemDeshacer.setText(bundle.getString("button.undo"));
        if (menuItemRehacer     != null) menuItemRehacer.setText(bundle.getString("button.redo"));
        if (menuItemCortar      != null) menuItemCortar.setText(bundle.getString("button.cut"));
        if (menuItemCopiar      != null) menuItemCopiar.setText(bundle.getString("button.copy"));
        if (menuItemPegar       != null) menuItemPegar.setText(bundle.getString("button.paste"));
    }

    /** 
     * Aplica el tema oscuro o claro a la interfaz
     * @param dark true para tema oscuro, false para tema claro
     */
    private void applyTheme(boolean dark) {
        try {
            if (tabPane == null || tabPane.getScene() == null) return;

            Scene scene = tabPane.getScene();
            String cssLight = getClass().getResource("/css/app.css").toExternalForm();
            String cssDark = getClass().getResource("/css/app-dark.css").toExternalForm();
            scene.getStylesheets().remove(cssLight);
            scene.getStylesheets().remove(cssDark);
            scene.getStylesheets().add(dark ? cssDark : cssLight);
        } catch (Exception ignored) {}
    }

    /**
     * Obtiene el Stage principal de la aplicación
     * @return Stage principal
     */
    private Stage getStage() { return (Stage) tabPane.getScene().getWindow(); }
}