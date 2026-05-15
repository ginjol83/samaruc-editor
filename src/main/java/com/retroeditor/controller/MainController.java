package com.retroeditor.controller;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.event.ActionEvent;
import org.kordamp.ikonli.javafx.FontIcon;

import com.retroeditor.controller.build.BuildController;
import com.retroeditor.controller.config.ConfigDialogCoordinator;
import com.retroeditor.controller.editor.EditOptionsController;
import com.retroeditor.controller.editor.FileOptionsController;
import com.retroeditor.controller.help.HelpController;
import com.retroeditor.controller.projectExplorer.ProjectExplorerController;
import com.retroeditor.controller.search.FileOpenPort;
import com.retroeditor.controller.search.ProjectContextPort;
import com.retroeditor.controller.search.SearchController;
import com.retroeditor.controller.terminal.TerminalController;
import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.EditorModel;
import com.retroeditor.service.AppLogger;
import com.retroeditor.service.CompilationDiagnosticParserService;
import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.PluginApplicationService;
import com.retroeditor.service.PropertiesConfigRepository;
import com.retroeditor.service.ProjectPlatformDetectionService;
import com.retroeditor.service.ProjectSearchService;
import com.retroeditor.service.TextSearchService;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.util.CompilationDiagnosticsUiHelper;
import com.retroeditor.util.FXUtils;
import com.retroeditor.view.UIElements;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.StyleClassedTextArea;
import javafx.stage.Stage;

public class MainController implements ProjectContextPort, FileOpenPort {
    private static final Set<String> NON_TEXT_EXTENSIONS = Set.of(
        "tap", "tzx", "z80", "sna", "gb", "gbc", "rom", "bin", "cdt", "wav", "dsk", "edsk", "class", "jar", "png", "jpg", "jpeg", "gif", "ico", "exe", "dll"
    );

    private final File userConfigFile = new File(System.getProperty("user.home"), ".retroeditor.properties");

    private FXUtils fxUtils = new FXUtils();
    private final ConfigModel configModel = new ConfigModel();

    private final HelpController helpController = new HelpController();
    private final EditOptionsController editOptionsController = new EditOptionsController();
    private final FileOptionsController fileOptionsController = new FileOptionsController();
    private final TerminalController terminalController = new TerminalController();
    private final BuildController buildController = new BuildController();
    private final ConfigDialogCoordinator configDialogCoordinator = new ConfigDialogCoordinator();
    private final CompilationDiagnosticParserService compilationDiagnosticParserService = new CompilationDiagnosticParserService();
    private final CompilationDiagnosticsUiHelper compilationDiagnosticsUiHelper = new CompilationDiagnosticsUiHelper();
    private final ConfigRepository configRepository = new PropertiesConfigRepository();
    private final PluginApplicationService pluginApplicationService = new PluginApplicationService();
    private final ProjectPlatformDetectionService projectPlatformDetectionService = new ProjectPlatformDetectionService();
    private final ProjectSearchService projectSearchService = new ProjectSearchService();
    private final TextSearchService textSearchService = new TextSearchService();

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
    @FXML private Label lblCompileStatus;
    @FXML private javafx.scene.control.ProgressIndicator progressCompile;
    @FXML private Menu menuArchivo;
    @FXML private Menu menuEdicion;
    @FXML private Menu menuCompilacion;
    @FXML private Menu menuAyuda;
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
    @FXML private Button btnCancelarCompilacion;
    @FXML private Button btnEjecutar;
    @FXML private Button btnNuevoProyecto;
    @FXML private Button btnAbrirProyecto;
    @FXML private Button btnManual;
    @FXML private MenuItem menuItemPegar;
    @FXML private MenuItem menuItemGoToLine;
    @FXML private MenuItem menuItemFind;
    @FXML private MenuItem menuItemFindProject;
    @FXML private MenuItem menuItemManual;
    @FXML private MenuItem menuItemPluginManual;
    @FXML private MenuItem menuItemLicenses;
    @FXML private MenuItem menuItemCreditos;
    @FXML private MenuItem menuItemNuevoProyecto;
    @FXML private MenuItem menuItemAbrirProyecto;
    @FXML private MenuItem menuItemNuevo;
    @FXML private MenuItem menuItemAbrir;
    @FXML private MenuItem menuItemSalir;
    @FXML private MenuItem menuItemCerrar;
    @FXML private MenuItem menuItemConfig;
    @FXML private MenuItem menuItemCortar;
    @FXML private MenuItem menuItemCopiar;
    @FXML private MenuItem menuItemGuardar;
    @FXML private MenuItem menuItemGuardarTodo;
    @FXML private MenuItem menuItemRehacer;
    @FXML private MenuItem menuItemCompilar;
    @FXML private MenuItem menuItemEjecutar;
    @FXML private MenuItem menuItemDeshacer;
    @FXML private MenuItem menuItemGuardarComo;
    @FXML private AnchorPane projectExplorerAnchor;
    @FXML private TabPane outputTabPane;
    @FXML private StyleClassedTextArea consoleOutputArea;
    @FXML private StyleClassedTextArea debuggerOutputArea;
    @FXML private Button btnClearConsole;
    @FXML private Button btnCopyConsole;
    @FXML private Button btnClearMonitor;
    @FXML private Label lblConsoleDotError;
    @FXML private Label lblConsoleDotWarning;
    @FXML private Label lblMonitorDotError;
    @FXML private Label lblMonitorDotWarning;
    @FXML private Label lblConsoleLegendError;
    @FXML private Label lblConsoleLegendWarning;
    @FXML private Label lblMonitorLegendError;
    @FXML private Label lblMonitorLegendWarning;

    private UIElements getUIElements() {
        UIElements elements = new UIElements();

        elements.btnCompilar = btnCompilar;
        elements.btnCancelarCompilacion = btnCancelarCompilacion;
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
        elements.menuItemNuevoProyecto = menuItemNuevoProyecto;
        elements.menuItemAbrirProyecto = menuItemAbrirProyecto;
        elements.menuItemGuardar = menuItemGuardar;
        elements.menuItemGuardarTodo = menuItemGuardarTodo;
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

    private static final String HOME_TAB_ID = "home-tab";
    private static final String HOME_LINK_HOOKED = "home-link-hooked";
    private final Map<File, CompileLineDiagnostics> compileDiagnosticsByFile = new HashMap<>();
    private volatile boolean lastCompilationHasErrors = false;
    private volatile boolean lastCompilationHasWarnings = false;

    private static final KeyCombination ACCEL_NEW          = new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_OPEN         = new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_NEW_PROJECT  = new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination ACCEL_OPEN_PROJECT = new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination ACCEL_SAVE         = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_SAVE_ALL     = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination ACCEL_CLOSE        = new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_UNDO         = new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_REDO         = new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_CUT          = new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_COPY         = new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_PASTE        = new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_SELECT_ALL   = new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_GOTO_LINE    = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_FIND_FILE    = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_FIND_PROJECT = new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_BUILD        = new KeyCodeCombination(KeyCode.F5);
    private static final KeyCombination ACCEL_RUN          = new KeyCodeCombination(KeyCode.F6);

    @FXML
    public void initialize() {
        // Cargar configuración del usuario (incluye gbdk_bin)
        configModel.applyProperties(configRepository.load(userConfigFile));

        // Leer idioma desde configModel (ya cargado por getDefaultLanguage)
        String lang     = configModel.getConfigProperty("idioma", "es");
        Locale locale   = java.util.Locale.forLanguageTag(lang);
        bundle          = ResourceBundle.getBundle("i18n.MessagesBundle", locale);

        AppLogger.setBundle(bundle);

        // Inicializar modelo, resaltador y vista
        editorModel         = new EditorModel();
        syntaxHighlighter   = new SyntaxHighlighter();
        fxUtils             = new FXUtils();

        // Configurar listener para cambios de idioma en la configuración
        configModel.idiomaProperty().addListener((obs, oldV, newV) -> {
            try {
                Locale newLocale = java.util.Locale.forLanguageTag(newV != null ? newV : "es");
                bundle           = ResourceBundle.getBundle("i18n.MessagesBundle", newLocale);
                AppLogger.setBundle(bundle);
                Platform.runLater(() -> {
                    fxUtils.refreshLanguage(getUIElements(), bundle);
                    applyBundleToUI();
                    updateCompileStatusUI();
                    refreshHomeTabLanguage();
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        buildController.compilationRunningProperty().addListener((obs, wasRunning, isRunning) -> updateCompileStatusUI());

        if (tabPane != null) {
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> applyDiagnosticsForTab(newTab));
        }

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
                registerGlobalShortcuts(scene);

                // Crear el SearchController con el Stage ya disponible como owner
                try {
                    Stage owner = (Stage) scene.getWindow();
                    searchController = new SearchController(owner, tabPane, tabFileMap, editorModel, this, this, projectSearchService, textSearchService, fxUtils);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        if (consoleOutputArea != null) {
            consoleOutputArea.setOnMouseClicked(event -> {
                if (event.getClickCount() >= 2) {
                    navigateToFileLineFromConsole();
                }
            });
        }

        if (lblCompileStatus != null) {
            lblCompileStatus.setOnMouseClicked(event -> onCompileStatusLabelClicked());
        }

        // Inicializar iconos y tooltips
        if (btnCompilar != null) {
            btnCompilar.setGraphic(new FontIcon("fas-hammer"));
            btnCompilar.setTooltip(new Tooltip(bundle.getString("button.compile")));
            btnCompilar.disableProperty().bind(buildController.compilationRunningProperty());
        }
        if (btnCancelarCompilacion != null) {
            btnCancelarCompilacion.setGraphic(new FontIcon("fas-stop-circle"));
            btnCancelarCompilacion.setTooltip(new Tooltip(bundle.getString("button.cancelCompile")));
            btnCancelarCompilacion.disableProperty().bind(buildController.compilationRunningProperty().not());
        }
        if (btnEjecutar != null) {
            btnEjecutar.setGraphic(new FontIcon("fas-play"));
            btnEjecutar.setTooltip(new Tooltip(bundle.getString("button.run")));
            btnEjecutar.disableProperty().bind(buildController.compilationRunningProperty());
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
            btnManual.setTooltip(new Tooltip(bundle.getString("tooltip.manual.open")));
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
            btnFind.setTooltip(new Tooltip(bundle.getString("tooltip.find.file")));
        }
        if (btnFindProject != null) {
            btnFindProject.setGraphic(new FontIcon("fas-search-plus"));
            btnFindProject.setTooltip(new Tooltip(bundle.getString("tooltip.find.project")));
        }
        if (btnPaste != null) {
            btnPaste.setGraphic(new FontIcon("fas-paste"));
            btnPaste.setTooltip(new Tooltip(bundle.getString("button.paste")));
        }

        // Improve toolbar visual: larger toolbar, bigger icons and nicer background
        try {
            if (toolBar != null) {
                toolBar.getStyleClass().add("tool-bar");
            }

            Button[] tbButtons = new Button[] { btnNuevoProyecto, btnAbrirProyecto, btnNuevo, btnOpen, btnSave, btnClose, btnUndo, btnRedo, btnFind, btnFindProject, btnCut, btnCopy, btnPaste, btnCompilar, btnCancelarCompilacion, btnEjecutar, btnConfig, btnManual };
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
        if (menuAyuda != null) menuAyuda.setText(bundle.getString("menu.help"));
        if (menuItemNuevoProyecto != null) menuItemNuevoProyecto.setText(bundle.getString("button.newProject"));
        if (menuItemAbrirProyecto != null) menuItemAbrirProyecto.setText(bundle.getString("button.openProject"));
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
        if (menuItemGoToLine != null) menuItemGoToLine.setText(bundle.getString("menu.goto.line"));
        if (menuItemFind != null) menuItemFind.setText(bundle.getString("menu.find.file"));
        if (menuItemFindProject != null) menuItemFindProject.setText(bundle.getString("menu.find.project"));
        if (menuItemManual != null) menuItemManual.setText(bundle.getString("menu.help.manual"));
        if (menuItemPluginManual != null) menuItemPluginManual.setText(bundle.getString("menu.help.pluginsManual"));
        if (menuItemLicenses != null) menuItemLicenses.setText(bundle.getString("menu.help.licenses"));
        if (menuItemCreditos != null) menuItemCreditos.setText(bundle.getString("menu.help.credits"));
        applyDiagnosticLegendI18n();

        applyMenuAccelerators();
        applyShortcutTooltips();

        // Instead of opening a new.c by default, show a homepage (local HTML) in the first tab
        try {
            Tab homeTab = fxUtils.addHomeTabContent(buildHomeHtml(), tabPane, getHomeTabTitle(), HOME_TAB_ID);
            hookHomeExternalLinks(homeTab);
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
                    if (!scene.getStylesheets().contains(cssLight)) {
                        scene.getStylesheets().add(cssLight);
                    }
                }
            } catch (Exception ignored) {}
        });

        if (menuItemCompilar != null) {
            menuItemCompilar.disableProperty().bind(buildController.compilationRunningProperty());
        }
        if (menuItemEjecutar != null) {
            menuItemEjecutar.disableProperty().bind(buildController.compilationRunningProperty());
        }
        updateCompileStatusUI();
    }

    @FXML
    private void onFindInFile(ActionEvent event) {
        if (searchController != null) searchController.showFindInFileDialog();
    }

    @FXML
    private void onGoToLine(ActionEvent event) {
        editOptionsController.onGoToLine(event, tabPane, bundle);
    }


    @FXML
    private void onFindInProject(ActionEvent event) {
        if (searchController != null) searchController.showFindInProjectDialog();
    }

    @FXML
    private void onCompilar(ActionEvent event) {
        lastCompilationHasErrors = false;
        lastCompilationHasWarnings = false;
        updateCompileStatusUI();
        clearCompileDiagnostics();
        buildController.onCompilar(
            event,
            consoleOutputArea,
            tabPane,
            tabFileMap,
            configModel,
            currentProjectDir,
            this::onCompilerOutputLine
        );
    }

    @FXML
    private void onCancelarCompilacion(ActionEvent event) {
        buildController.cancelActiveCompilation(consoleOutputArea);
    }

    @FXML
    private void onEjecutar(ActionEvent event) {
        buildController.onEjecutar(
            event,
            consoleOutputArea,
            tabPane,
            tabFileMap,
            configModel,
            currentProjectDir,
            getStage()
        );
    }

    @FXML
    private void onNuevoProyecto(ActionEvent event) {
        File creado = fxUtils.createNewProject(getStage(), bundle);
        if (creado != null) {
            if (fxUtils.wasLastCreatedProjectGameBoyTemplate()) {
                configModel.applyGameBoyProfile();
                configRepository.save(userConfigFile, configModel.toProperties());
            } else if (fxUtils.wasLastCreatedProjectSpectrumTemplate()) {
                configModel.applySpectrumProfile();
                configRepository.save(userConfigFile, configModel.toProperties());
            }

            UserActionMonitor.projectCreated(creado.getAbsolutePath());
            setProyectoActual(creado);

            File defaultMainFile = new File(creado, "src/main.c");
            if (defaultMainFile.isFile()) {
                openFileFromExplorer(defaultMainFile);
            }
        } else {
            if (fxUtils.wasLastProjectCreationCancelled()) {
                UserActionMonitor.errorOccurred("PROJECT_CREATE", "Dialogo de proyecto cancelado");
                return;
            }
            UserActionMonitor.errorOccurred("PROJECT_CREATE", "No se pudo crear la carpeta del proyecto");
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(bundle.getString("alert.project.create.title"));
            alert.setHeaderText(bundle.getString("alert.project.create.header"));
            alert.setContentText(bundle.getString("alert.project.create.content"));
            alert.showAndWait();
        }
    }

    @FXML
    private void onAbrirProyecto(ActionEvent event) {
        File carpeta = fxUtils.openExistingProject(getStage(), bundle);
        if (carpeta != null) {
            UserActionMonitor.projectOpened(carpeta.getAbsolutePath());
            setProyectoActual(carpeta);
            maybeApplyDetectedProfileForOpenedProject(carpeta);
        } else {
            UserActionMonitor.errorOccurred("PROJECT_OPEN", "Diálogo de proyecto cancelado");
        }
    }

    private void maybeApplyDetectedProfileForOpenedProject(File projectDir) {
        ProjectPlatformDetectionService.DetectionResult detected = projectPlatformDetectionService.detectProjectPlatform(projectDir);
        if (detected == null || detected.getPlatform() == ProjectPlatformDetectionService.Platform.UNKNOWN) return;

        boolean profileAlreadyMatches =
            (detected.getPlatform() == ProjectPlatformDetectionService.Platform.GAMEBOY && isCurrentProfileGameBoy())
                || (detected.getPlatform() == ProjectPlatformDetectionService.Platform.SPECTRUM && isCurrentProfileSpectrum());

        if (profileAlreadyMatches) return;

        String detectedProfileName = detected.getPlatform() == ProjectPlatformDetectionService.Platform.GAMEBOY
            ? msg("dialog.project.detected.profile.gameboy", "Game Boy (GBDK)")
            : msg("dialog.project.detected.profile.spectrum", "ZX Spectrum (z88dk)");

        String confidenceText = Integer.toString(detected.getConfidence());
        String evidence = detected.getEvidence() != null ? detected.getEvidence() : "";

        ButtonType applyButton = new ButtonType(msg("dialog.project.detected.action.apply", "Aplicar perfil"), javafx.scene.control.ButtonBar.ButtonData.YES);
        ButtonType keepButton = new ButtonType(msg("dialog.project.detected.action.keep", "Mantener actual"), javafx.scene.control.ButtonBar.ButtonData.NO);
        ButtonType cancelButton = new ButtonType(msg("dialog.project.detected.action.cancel", "Cancelar"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            msg("dialog.project.detected.content", "Se ha detectado una plataforma para este proyecto. ¿Quieres aplicar su perfil de compilación?")
                + "\n\n"
                + msg("dialog.project.detected.content.profile", "Perfil detectado:") + " " + detectedProfileName
                + "\n"
                + msg("dialog.project.detected.content.confidence", "Confianza:") + " " + confidenceText + "%"
                + (evidence.isBlank() ? "" : "\n" + msg("dialog.project.detected.content.evidence", "Evidencias:") + " " + evidence),
            applyButton,
            keepButton,
            cancelButton
        );

        alert.setTitle(msg("dialog.project.detected.title", "Perfil detectado"));
        alert.setHeaderText(msg("dialog.project.detected.header", "El proyecto abierto parece ser de otra plataforma"));

        Optional<ButtonType> selected = alert.showAndWait();
        if (!selected.isPresent() || selected.get() != applyButton) return;

        if (detected.getPlatform() == ProjectPlatformDetectionService.Platform.GAMEBOY) {
            configModel.applyGameBoyProfile();
        } else {
            configModel.applySpectrumProfile();
        }
        configRepository.save(userConfigFile, configModel.toProperties());
    }

    private boolean isCurrentProfileGameBoy() {
        return "GBDK".equalsIgnoreCase(configModel.getConfigProperty("compilador_seleccionado", "GBDK"));
    }

    private boolean isCurrentProfileSpectrum() {
        String compiler = configModel.getConfigProperty("compilador_seleccionado", "GBDK");
        return "z88dk".equalsIgnoreCase(compiler) || "Spectrum".equalsIgnoreCase(compiler);
    }

    private String msg(String key, String fallback) {
        if (bundle == null) return fallback;
        try {
            return bundle.getString(key);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public File getCurrentProjectDir() { return currentProjectDir; }

    // Expose MenuBar for plugins or other host integrations
    public MenuBar getMenuBar() { return menuBar; }

    public void setProyectoActual(File carpeta) {
        this.currentProjectDir = carpeta;
        UserActionMonitor.projectSet(carpeta != null ? carpeta.getName() : null);
        if (projectExplorerController != null) projectExplorerController.setRootDirectory(carpeta);
        try { if (carpeta != null) fileOptionsController.setDefaultSaveDirectory(carpeta); } catch (Exception ex) { ex.printStackTrace(); }
    }

    public boolean isSupportedRomFile(File file) {
        return buildController.isSupportedRomFile(file);
    }

    public void runRomFromProjectExplorer(File romFile) {
        buildController.runRomFile(romFile, consoleOutputArea, getStage());
    }

    public void openFileFromExplorer(File file) {
        if (file == null || !file.isFile()) {
            UserActionMonitor.errorOccurred("FILE_OPEN", "Intento de abrir archivo inválido o nulo");
            return;
        }
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            if (file.equals(entry.getValue())) { 
                UserActionMonitor.fileOpenedFromSearch(file.getName());
                tabPane.getSelectionModel().select(entry.getKey());
                applyDiagnosticsForTab(entry.getKey());
                return; 
            }
        }

        OpenChoice openChoice = shouldAskOpenMode(file) ? askHowToOpenFile(file) : OpenChoice.EDITOR_TEXT;
        if (openChoice == OpenChoice.CANCEL) return;
        if (openChoice == OpenChoice.SYSTEM_DEFAULT) {
            openFileWithSystem(file);
            return;
        }

        try {
            openFileInEditorTab(file, openChoice == OpenChoice.EDITOR_FORCE_TEXT);
        } catch (IOException e) {
            if (openChoice != OpenChoice.EDITOR_FORCE_TEXT && shouldOfferForceTextOpen(e)) {
                OpenChoice retryChoice = askHowToOpenFile(file);
                if (retryChoice == OpenChoice.SYSTEM_DEFAULT) {
                    openFileWithSystem(file);
                    return;
                }
                if (retryChoice == OpenChoice.EDITOR_FORCE_TEXT) {
                    try {
                        openFileInEditorTab(file, true);
                        return;
                    } catch (IOException retryError) {
                        showOpenFileError(file, retryError);
                        return;
                    }
                }
                return;
            }
            showOpenFileError(file, e);
        }
    }

    private void openFileInEditorTab(File file, boolean forceSingleByteText) throws IOException {
        if (forceSingleByteText) {
            editorModel.openFileAsSingleByteText(file);
        } else {
            editorModel.openFile(file);
        }

        String content = editorModel.getFileContent(file);
        Tab tab = fxUtils.addTab(file.getName(), content, syntaxHighlighter, tabPane);
        tabFileMap.put(tab, file);
        applyDiagnosticsForTab(tab);

        // Limpieza cuando el usuario cierra el tab con la X integrada
        tab.setOnClosed(e -> {
            tabFileMap.remove(tab);
            editorModel.closeFile(file);
        });
    }

    private boolean shouldAskOpenMode(File file) {
        String ext = getExtension(file.getName());
        return ext != null && NON_TEXT_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT));
    }

    private boolean shouldOfferForceTextOpen(IOException e) {
        if (e == null || e.getMessage() == null) return false;
        String message = e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("binario") || message.contains("decodificar") || message.contains("malformed");
    }

    private OpenChoice askHowToOpenFile(File file) {
        ButtonType openAsTextButton = new ButtonType("Abrir como texto");
        ButtonType openWithSystemButton = new ButtonType("Abrir con aplicación del sistema");
        ButtonType cancelButton = ButtonType.CANCEL;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Abrir archivo");
        alert.setHeaderText("Selecciona cómo abrir: " + file.getName());
        alert.setContentText("Este archivo puede no ser de texto. ¿Cómo quieres abrirlo?");
        alert.getButtonTypes().setAll(openAsTextButton, openWithSystemButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == openAsTextButton) {
            return OpenChoice.EDITOR_FORCE_TEXT;
        }
        if (result.isPresent() && result.get() == openWithSystemButton) {
            return OpenChoice.SYSTEM_DEFAULT;
        }
        return OpenChoice.CANCEL;
    }

    private void openFileWithSystem(File file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Desktop no soportado en este sistema.");
            }
            Desktop.getDesktop().open(file);
        } catch (IOException ex) {
            showOpenFileError(file, ex);
        }
    }

    private void showOpenFileError(File file, Exception e) {
        String message = e != null && e.getMessage() != null ? e.getMessage() : "Error desconocido al abrir el archivo.";
        UserActionMonitor.errorOccurred("FILE_OPEN_EXPLORER", message);
        if (e != null) e.printStackTrace();

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error al abrir archivo");
        alert.setHeaderText(file != null ? file.getName() : "Archivo no disponible");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String getExtension(String fileName) {
        if (fileName == null) return null;
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return null;
        return fileName.substring(idx + 1);
    }

    private enum OpenChoice {
        EDITOR_TEXT,
        EDITOR_FORCE_TEXT,
        SYSTEM_DEFAULT,
        CANCEL
    }

    /**
     * Cierra pestañas abiertas que apuntan a un archivo borrado o a archivos dentro
     * de una carpeta borrada.
     * @param deletedTarget Archivo/carpeta eliminado.
     * @param targetWasDirectory true si el elemento eliminado era una carpeta.
     */
    public void closeTabsForDeletedTarget(File deletedTarget, boolean targetWasDirectory) {
        if (deletedTarget == null || tabPane == null) return;

        Path targetPath;
        try {
            targetPath = deletedTarget.toPath().toAbsolutePath().normalize();
        } catch (Exception ex) {
            return;
        }

        List<Tab> tabsToClose = new ArrayList<>();

        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            File mappedFile = entry.getValue();
            if (mappedFile == null) continue;

            try {
                Path filePath = mappedFile.toPath().toAbsolutePath().normalize();
                boolean shouldClose = targetWasDirectory ? filePath.startsWith(targetPath) : filePath.equals(targetPath);
                if (shouldClose) tabsToClose.add(entry.getKey());
            } catch (Exception ignored) {
            }
        }

        for (Tab tab : tabsToClose) {
            File mappedFile = tabFileMap.remove(tab);
            if (mappedFile != null && editorModel != null) {
                editorModel.closeFile(mappedFile);
                compileDiagnosticsByFile.remove(mappedFile.getAbsoluteFile());
            }
            tabPane.getTabs().remove(tab);
        }
    }

    private void onCompilerOutputLine(File sourceFile, String outputLine) {
        if (outputLine == null || outputLine.isBlank()) return;

        Integer exitCode = compilationDiagnosticParserService.parseCompilationExitCode(outputLine);
        if (exitCode != null && exitCode.intValue() != 0) {
            Platform.runLater(() -> {
                lastCompilationHasErrors = true;
                updateCompileStatusUI();
            });
        }

        CompilationDiagnosticParserService.DiagnosticLocation location =
            compilationDiagnosticParserService.parseCompilerDiagnosticLocation(sourceFile, outputLine, this::resolveConsoleFileReference);
        if (location == null || location.getLine() <= 0) return;

        Platform.runLater(() -> {
            File key = toFileKey(location.getFile());
            CompileLineDiagnostics diagnostics = compileDiagnosticsByFile.computeIfAbsent(key, k -> new CompileLineDiagnostics());
            if (location.isWarning()) {
                diagnostics.warnings.add(location.getLine());
                lastCompilationHasWarnings = true;
            } else {
                diagnostics.errors.add(location.getLine());
                lastCompilationHasErrors = true;
            }

            updateCompileStatusUI();

            for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
                File mapped = entry.getValue();
                if (mapped == null) continue;
                if (toFileKey(mapped).equals(key)) {
                    applyDiagnosticsForTab(entry.getKey());
                }
            }
        });
    }

    private void clearCompileDiagnostics() {
        compileDiagnosticsByFile.clear();
        for (Tab tab : tabPane.getTabs()) {
            clearDiagnosticsForTab(tab);
        }
    }

    private void clearDiagnosticsForTab(Tab tab) {
        CodeArea codeArea = getCodeAreaFromTab(tab);
        if (codeArea == null) return;

        compilationDiagnosticsUiHelper.clear(codeArea);
    }

    private void applyDiagnosticsForTab(Tab tab) {
        CodeArea codeArea = getCodeAreaFromTab(tab);
        if (codeArea == null) return;

        clearDiagnosticsForTab(tab);

        File mappedFile = tabFileMap.get(tab);
        if (mappedFile == null) return;

        CompileLineDiagnostics diagnostics = compileDiagnosticsByFile.get(toFileKey(mappedFile));
        if (diagnostics == null) {
            compilationDiagnosticsUiHelper.apply(codeArea, java.util.Collections.emptySet(), java.util.Collections.emptySet());
            return;
        }

        Set<Integer> errorLines = diagnostics.errors;
        Set<Integer> warningLines = diagnostics.warnings;

        compilationDiagnosticsUiHelper.apply(codeArea, errorLines, warningLines);
    }

    private CodeArea getCodeAreaFromTab(Tab tab) {
        if (tab == null) return null;
        if (!(tab.getContent() instanceof CodeArea)) return null;
        return (CodeArea) tab.getContent();
    }

    private File toFileKey(File file) {
        return file == null ? null : file.getAbsoluteFile();
    }

    @FXML
    public void onOpenConfig(ActionEvent event) {
        if (event != null) event.consume();
        if (isRefreshingLanguage) return;

        configDialogCoordinator.open(
            getStage(),
            configModel,
            configRepository,
            pluginApplicationService,
            bundle,
            () -> {
                String langCode = configModel.getConfigProperty("idioma", "es");
                Locale newLocale = java.util.Locale.forLanguageTag(langCode);
                bundle = ResourceBundle.getBundle("i18n.MessagesBundle", newLocale);
                AppLogger.setBundle(bundle);
                Platform.runLater(() -> {
                    fxUtils.refreshLanguage(getUIElements(), bundle);
                    applyBundleToUI();
                });
            },
            UserActionMonitor::configDialogOpened,
            UserActionMonitor::configDialogClosed,
            e -> {
                UserActionMonitor.errorOccurred("CONFIG_OPEN", e.getMessage());
                System.err.println("[ERROR] No se pudo abrir la configuración:");
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(bundle.getString("alert.config.open.title"));
                alert.setHeaderText(bundle.getString("alert.config.open.header"));
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        );
    }

    /**
     * Metodo para generar un nuevo tab de editor
     */
    @FXML
    public void onNewTab(ActionEvent event) {
        // Asegurarse de que hay un proyecto abierto antes de crear un nuevo archivo
        if (this.currentProjectDir == null) {
            UserActionMonitor.errorOccurred("TAB_NEW", "Sin proyecto abierto");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(bundle.getString("alert.project.notOpen.title"));
            alert.setHeaderText(null);
            alert.setContentText(bundle.getString("alert.project.notOpen.content"));
            alert.showAndWait();
            return;
        }
        fxUtils.onNewTab(event, newFileCounter, tabPane, tabFileMap, syntaxHighlighter);
    }

    @FXML
    private void onExit(ActionEvent event) { 
        shutdown();
        UserActionMonitor.tabClosed("Aplicación cerrada por el usuario");
        Platform.exit(); 
    }

    public void shutdown() {
        try {
            buildController.stopActiveCompilation();
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onClearConsole(ActionEvent event) {
        if (consoleOutputArea != null) {
            consoleOutputArea.clear();
            AppLogger.logMonitor("CONSOLA", bundle.getString("log.monitor.console.cleared"));
        }
    }

    @FXML
    private void onClearMonitor(ActionEvent event) {
        if (debuggerOutputArea != null) {
            debuggerOutputArea.clear();
            AppLogger.logConsole("MONITOR", bundle.getString("log.monitor.monitor.cleared"));
        }
    }

    @FXML
    private void onCopyConsole(ActionEvent event) {
        if (consoleOutputArea == null) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(consoleOutputArea.getText() != null ? consoleOutputArea.getText() : "");
        Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void onOpenFile(ActionEvent event)   { fileOptionsController.onOpenFile(event, tabPane, tabFileMap, editorModel, syntaxHighlighter); }

    @FXML
    private void onSaveFile(ActionEvent event) {
        boolean saved = fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel);
        if (saved && projectExplorerController != null) {
            projectExplorerController.refreshTree();
        }
    }

    @FXML
    private void onSaveAllFiles(ActionEvent event) {
        if (tabPane == null || tabPane.getTabs().isEmpty()) {
            return;
        }

        Tab originalTab = tabPane.getSelectionModel().getSelectedItem();
        boolean savedAny = false;

        try {
            List<Tab> tabsToSave = new ArrayList<>();
            for (Tab tab : tabPane.getTabs()) {
                if (tabFileMap.containsKey(tab)) {
                    tabsToSave.add(tab);
                }
            }

            for (Tab tab : tabsToSave) {
                tabPane.getSelectionModel().select(tab);
                boolean saved = fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel);
                if (!saved) {
                    break;
                }
                savedAny = true;
            }
        } finally {
            if (originalTab != null && tabPane.getTabs().contains(originalTab)) {
                tabPane.getSelectionModel().select(originalTab);
            }
        }

        if (savedAny && projectExplorerController != null) {
            projectExplorerController.refreshTree();
        }
    }

    @FXML
    private void onOpenCpcWebTest(ActionEvent event) {
        File cpcIndex = new File(System.getProperty("user.dir"), "libs/cpcbox-main/cpcbox-main/index.html");

        if (!cpcIndex.isFile()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("CPC Web");
            alert.setHeaderText("No se encontró el emulador CPC web");
            alert.setContentText("Ruta esperada: " + cpcIndex.getAbsolutePath());
            alert.showAndWait();
            return;
        }

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.getEngine().load(cpcIndex.toURI().toString());

        Stage stage = new Stage();
        stage.setTitle("CPC Web Test");
        stage.initOwner(getStage());
        stage.setScene(new Scene(webView, 1280, 900));
        stage.show();
    }

    @FXML
    private void onSaveAsFile(ActionEvent event) {
        boolean saved = fileOptionsController.onSaveAsFile(event, tabPane, tabFileMap, editorModel);
        if (saved && projectExplorerController != null) {
            projectExplorerController.refreshTree();
        }
    }

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
    private void onSelectAll(ActionEvent event)  { editOptionsController.onSelectAll(event, tabPane); }

    @FXML
    private void onOpenManual()                               { helpController.onOpenManual(); }

    @FXML
    private void onOpenPluginManual()                         { helpController.onOpenPluginManual(); }

    @FXML
    private void onOpenLicenses()                             { helpController.onOpenThirdPartyLicenses(); }

    @FXML
    private void onShowCredits()                              { helpController.onShowCredits(); }

    private void navigateToFileLineFromConsole() {
        if (consoleOutputArea == null) return;

        String clickedLine = getCurrentConsoleLine(consoleOutputArea);
        FileLineLocation target = parseConsoleFileLine(clickedLine);
        if (target == null) return;

        openFileFromExplorer(target.file);
        Platform.runLater(() -> {
            if (editOptionsController.goToLine(tabPane, target.line)) {
                UserActionMonitor.goToLineExecuted(target.line);
            }
        });
    }

    private String getCurrentConsoleLine(StyleClassedTextArea area) {
        String text = area.getText();
        if (text == null || text.isEmpty()) return null;

        int caret = Math.max(0, Math.min(area.getCaretPosition(), text.length()));
        int start = (caret <= 0) ? 0 : text.lastIndexOf('\n', caret - 1) + 1;
        int end = text.indexOf('\n', caret);
        if (end < 0) end = text.length();
        return text.substring(start, end).trim();
    }

    private FileLineLocation parseConsoleFileLine(String lineText) {
        if (lineText == null || lineText.isEmpty()) return null;

        CompilationDiagnosticParserService.DiagnosticLocation location =
            compilationDiagnosticParserService.parseConsoleFileLine(lineText, this::resolveConsoleFileReference);
        if (location == null) return null;
        return new FileLineLocation(location.getFile(), location.getLine());
    }

    private File resolveConsoleFileReference(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return null;

        File direct = new File(rawPath);
        if (direct.isAbsolute()) {
            return direct;
        }

        if (currentProjectDir != null) {
            File fromProject = new File(currentProjectDir, rawPath);
            if (fromProject.exists()) return fromProject;
        }

        Tab selectedTab = fxUtils.getSelectedTab(tabPane);
        File selectedFile = selectedTab != null ? tabFileMap.get(selectedTab) : null;
        if (selectedFile != null && selectedFile.getParentFile() != null) {
            File fromActiveDir = new File(selectedFile.getParentFile(), rawPath);
            if (fromActiveDir.exists()) return fromActiveDir;
        }

        return direct;
    }

    private static final class FileLineLocation {
        private final File file;
        private final int line;
        private final boolean warning;

        private FileLineLocation(File file, int line) {
            this(file, line, false);
        }

        private FileLineLocation(File file, int line, boolean warning) {
            this.file = file;
            this.line = line;
            this.warning = warning;
        }
    }

    private static final class CompileLineDiagnostics {
        private final Set<Integer> errors = new HashSet<>();
        private final Set<Integer> warnings = new HashSet<>();
    }

    /**
     * Aplica el ResourceBundle actual a los elementos de la UI
     */
    private void applyBundleToUI() {
        if (bundle == null) return;

        if (btnCompilar         != null) btnCompilar.setTooltip(new Tooltip(bundle.getString("button.compile")));
        if (btnCancelarCompilacion != null) btnCancelarCompilacion.setTooltip(new Tooltip(bundle.getString("button.cancelCompile")));
        if (btnEjecutar         != null) btnEjecutar.setTooltip(new Tooltip(bundle.getString("button.run")));
        if (btnNuevo            != null) btnNuevo.setTooltip(new Tooltip(bundle.getString("button.new")));
        if (btnOpen             != null) btnOpen.setTooltip(new Tooltip(bundle.getString("button.open")));
        if (btnSave             != null) btnSave.setTooltip(new Tooltip(bundle.getString("button.save")));
        if (btnClose            != null) btnClose.setTooltip(new Tooltip(bundle.getString("button.close")));
        if (btnConfig           != null) btnConfig.setTooltip(new Tooltip(bundle.getString("button.config")));
        if (btnNuevoProyecto    != null) btnNuevoProyecto.setTooltip(new Tooltip(bundle.getString("button.newProject")));
        if (btnAbrirProyecto    != null) btnAbrirProyecto.setTooltip(new Tooltip(bundle.getString("button.openProject")));
        if (btnFind             != null) btnFind.setTooltip(new Tooltip(bundle.getString("tooltip.find.file")));
        if (btnFindProject      != null) btnFindProject.setTooltip(new Tooltip(bundle.getString("tooltip.find.project")));
        if (btnManual           != null) btnManual.setTooltip(new Tooltip(bundle.getString("tooltip.manual.open")));
        if (btnUndo             != null) btnUndo.setTooltip(new Tooltip(bundle.getString("button.undo")));
        if (btnRedo             != null) btnRedo.setTooltip(new Tooltip(bundle.getString("button.redo")));
        if (btnCut              != null) btnCut.setTooltip(new Tooltip(bundle.getString("button.cut")));
        if (btnCopy             != null) btnCopy.setTooltip(new Tooltip(bundle.getString("button.copy")));
        if (btnPaste            != null) btnPaste.setTooltip(new Tooltip(bundle.getString("button.paste")));
        updateCompileStatusUI();

        if (menuArchivo         != null) menuArchivo.setText(bundle.getString("menu.file"));
        if (menuCompilacion     != null) menuCompilacion.setText(bundle.getString("menu.project"));
        if (menuEdicion         != null) menuEdicion.setText(bundle.getString("menu.edit"));
        if (menuAyuda           != null) menuAyuda.setText(bundle.getString("menu.help"));
        if (menuItemNuevoProyecto != null) menuItemNuevoProyecto.setText(bundle.getString("button.newProject"));
        if (menuItemAbrirProyecto != null) menuItemAbrirProyecto.setText(bundle.getString("button.openProject"));
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
        if (menuItemGoToLine    != null) menuItemGoToLine.setText(bundle.getString("menu.goto.line"));
        if (menuItemFind        != null) menuItemFind.setText(bundle.getString("menu.find.file"));
        if (menuItemFindProject != null) menuItemFindProject.setText(bundle.getString("menu.find.project"));
        if (menuItemManual      != null) menuItemManual.setText(bundle.getString("menu.help.manual"));
        if (menuItemPluginManual != null) menuItemPluginManual.setText(bundle.getString("menu.help.pluginsManual"));
        if (menuItemLicenses    != null) menuItemLicenses.setText(bundle.getString("menu.help.licenses"));
        if (menuItemCreditos    != null) menuItemCreditos.setText(bundle.getString("menu.help.credits"));
        applyDiagnosticLegendI18n();
        applyMenuAccelerators();
        applyShortcutTooltips();
        refreshHomeTabLanguage();
    }

    private void applyDiagnosticLegendI18n() {
        if (bundle == null) return;

        Tooltip errorTip   = new Tooltip(bundle.getString("legend.tooltip.error"));
        Tooltip warningTip = new Tooltip(bundle.getString("legend.tooltip.warning"));

        final String errorColor   = "-fx-text-fill: #ff3b30; -fx-font-size: 13px; -fx-padding: 0 2 0 4;";
        final String warningColor = "-fx-text-fill: #e6ac00; -fx-font-size: 13px; -fx-padding: 0 2 0 4;";

        if (lblConsoleDotError != null) {
            lblConsoleDotError.setStyle(errorColor);
            lblConsoleDotError.setTooltip(errorTip);
        }
        if (lblConsoleDotWarning != null) {
            lblConsoleDotWarning.setStyle(warningColor);
            lblConsoleDotWarning.setTooltip(warningTip);
        }
        if (lblMonitorDotError != null) {
            lblMonitorDotError.setStyle(errorColor);
            lblMonitorDotError.setTooltip(errorTip);
        }
        if (lblMonitorDotWarning != null) {
            lblMonitorDotWarning.setStyle(warningColor);
            lblMonitorDotWarning.setTooltip(warningTip);
        }

        if (lblConsoleLegendError != null) {
            lblConsoleLegendError.setText(bundle.getString("legend.error"));
            lblConsoleLegendError.setTooltip(errorTip);
        }
        if (lblConsoleLegendWarning != null) {
            lblConsoleLegendWarning.setText(bundle.getString("legend.warning"));
            lblConsoleLegendWarning.setTooltip(warningTip);
        }
        if (lblMonitorLegendError != null) {
            lblMonitorLegendError.setText(bundle.getString("legend.error"));
            lblMonitorLegendError.setTooltip(errorTip);
        }
        if (lblMonitorLegendWarning != null) {
            lblMonitorLegendWarning.setText(bundle.getString("legend.warning"));
            lblMonitorLegendWarning.setTooltip(warningTip);
        }
    }

    private String buildHomeHtml() {
        String template;

        try (InputStream in = getClass().getResourceAsStream("/views/home/home.html")) {
            if (in == null) return "";
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
            return "";
        }

        if (bundle == null) return template;

        String html = template;
        String[] keys = new String[] {
            "home.lang",
            "home.pageTitle",
            "home.welcomeTitle",
            "home.welcomeText",
            "home.shortcutsTitle",
            "home.shortcut.newProject",
            "home.shortcut.openProject",
            "home.shortcut.findFile",
            "home.shortcut.findProject",
            "home.pluginsTitle",
            "home.pluginsText",
            "home.toolchainsTitle",
            "home.toolchainsText",
            "home.toolchain.z88dk",
            "home.toolchain.gbdk",
            "home.footerText"
        };

        for (String key : keys) {
            String value;
            try {
                value = bundle.getString(key);
            } catch (Exception ex) {
                value = "";
            }
            html = html.replace("{{" + key + "}}", value);
        }

        return html;
    }

    private String getHomeTabTitle() {
        try {
            return bundle != null ? bundle.getString("tab.home") : "Home";
        } catch (Exception ex) {
            return "Home";
        }
    }

    private void refreshHomeTabLanguage() {
        if (tabPane == null) return;

        for (Tab tab : tabPane.getTabs()) {
            if (!HOME_TAB_ID.equals(tab.getId())) continue;
            if (!(tab.getContent() instanceof javafx.scene.web.WebView)) continue;

            javafx.scene.web.WebView homeWeb = (javafx.scene.web.WebView) tab.getContent();
            try {
                homeWeb.getEngine().loadContent(buildHomeHtml(), "text/html");
                tab.setText(getHomeTabTitle());
                hookHomeExternalLinks(tab);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return;
        }
    }

    private void hookHomeExternalLinks(Tab homeTab) {
        if (homeTab == null) return;
        if (!(homeTab.getContent() instanceof javafx.scene.web.WebView)) return;

        javafx.scene.web.WebView webView = (javafx.scene.web.WebView) homeTab.getContent();
        Object alreadyHooked = webView.getProperties().get(HOME_LINK_HOOKED);
        if (Boolean.TRUE.equals(alreadyHooked)) return;

        webView.getProperties().put(HOME_LINK_HOOKED, Boolean.TRUE);
        webView.getEngine().locationProperty().addListener((obs, oldLocation, newLocation) -> {
            if (newLocation == null) return;
            String lower = newLocation.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return;

            openInSystemBrowser(newLocation);
            Platform.runLater(() -> webView.getEngine().loadContent(buildHomeHtml(), "text/html"));
        });
    }

    private void openInSystemBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            AppLogger.logConsole("HOME", "No se puede abrir navegador por defecto para: " + url);
        } catch (Exception ex) {
            AppLogger.logConsole("HOME", "Error abriendo URL externa: " + url + " | " + ex.getMessage());
        }
    }

    private void updateCompileStatusUI() {
        if (lblCompileStatus == null || bundle == null) return;

        boolean running = buildController.isCompilationRunning();
        String textKey;
        String cssClass;

        if (running) {
            textKey = "status.compile.running";
            cssClass = "compile-status-running";
        } else if (lastCompilationHasErrors) {
            textKey = "status.compile.error";
            cssClass = "compile-status-error";
        } else if (lastCompilationHasWarnings) {
            textKey = "status.compile.warning";
            cssClass = "compile-status-warning";
        } else {
            textKey = "status.compile.idle";
            cssClass = "compile-status-idle";
        }

        lblCompileStatus.setText(bundle.getString(textKey));
        lblCompileStatus.getStyleClass().removeAll(
            "compile-status-idle",
            "compile-status-running",
            "compile-status-error",
            "compile-status-warning"
        );
        lblCompileStatus.getStyleClass().add(cssClass);

        if (progressCompile != null) {
            progressCompile.setVisible(running);
            progressCompile.setManaged(running);
        }
    }

    private void registerGlobalShortcuts(Scene scene) {
        if (scene == null) return;

        scene.getAccelerators().put(ACCEL_NEW, () -> onNewTab(null));
        scene.getAccelerators().put(ACCEL_OPEN, () -> onOpenFile(null));
        scene.getAccelerators().put(ACCEL_NEW_PROJECT, () -> onNuevoProyecto(null));
        scene.getAccelerators().put(ACCEL_OPEN_PROJECT, () -> onAbrirProyecto(null));
        scene.getAccelerators().put(ACCEL_SAVE, () -> onSaveFile(null));
        scene.getAccelerators().put(ACCEL_SAVE_ALL, () -> onSaveAllFiles(null));
        scene.getAccelerators().put(ACCEL_CLOSE, () -> onCloseFile(null));

        scene.getAccelerators().put(ACCEL_UNDO, () -> onUndo(null));
        scene.getAccelerators().put(ACCEL_REDO, () -> onRedo(null));
        scene.getAccelerators().put(ACCEL_CUT, () -> onCut(null));
        scene.getAccelerators().put(ACCEL_COPY, () -> onCopy(null));
        scene.getAccelerators().put(ACCEL_PASTE, () -> onPaste(null));
        scene.getAccelerators().put(ACCEL_SELECT_ALL, () -> onSelectAll(null));
        scene.getAccelerators().put(ACCEL_GOTO_LINE, () -> onGoToLine(null));

        scene.getAccelerators().put(ACCEL_FIND_FILE, () -> onFindInFile(null));
        scene.getAccelerators().put(ACCEL_FIND_PROJECT, () -> onFindInProject(null));

        scene.getAccelerators().put(ACCEL_BUILD, () -> onCompilar(null));
        scene.getAccelerators().put(ACCEL_RUN, () -> onEjecutar(null));
    }

    private void applyMenuAccelerators() {
        if (menuItemNuevoProyecto != null) menuItemNuevoProyecto.setAccelerator(ACCEL_NEW_PROJECT);
        if (menuItemAbrirProyecto != null) menuItemAbrirProyecto.setAccelerator(ACCEL_OPEN_PROJECT);
        if (menuItemNuevo       != null) menuItemNuevo.setAccelerator(ACCEL_NEW);
        if (menuItemAbrir       != null) menuItemAbrir.setAccelerator(ACCEL_OPEN);
        if (menuItemGuardar     != null) menuItemGuardar.setAccelerator(ACCEL_SAVE);
        if (menuItemGuardarTodo != null) menuItemGuardarTodo.setAccelerator(ACCEL_SAVE_ALL);
        if (menuItemCerrar      != null) menuItemCerrar.setAccelerator(ACCEL_CLOSE);
        if (menuItemDeshacer    != null) menuItemDeshacer.setAccelerator(ACCEL_UNDO);
        if (menuItemRehacer     != null) menuItemRehacer.setAccelerator(ACCEL_REDO);
        if (menuItemCortar      != null) menuItemCortar.setAccelerator(ACCEL_CUT);
        if (menuItemCopiar      != null) menuItemCopiar.setAccelerator(ACCEL_COPY);
        if (menuItemPegar       != null) menuItemPegar.setAccelerator(ACCEL_PASTE);
        if (menuItemGoToLine    != null) menuItemGoToLine.setAccelerator(ACCEL_GOTO_LINE);
        if (menuItemFind        != null) menuItemFind.setAccelerator(ACCEL_FIND_FILE);
        if (menuItemFindProject != null) menuItemFindProject.setAccelerator(ACCEL_FIND_PROJECT);
        if (menuItemCompilar    != null) menuItemCompilar.setAccelerator(ACCEL_BUILD);
        if (menuItemEjecutar    != null) menuItemEjecutar.setAccelerator(ACCEL_RUN);
    }

    private void applyShortcutTooltips() {
        if (bundle == null) return;

        if (btnNuevoProyecto != null) btnNuevoProyecto.setTooltip(new Tooltip(bundle.getString("button.newProject") + " (Ctrl+Shift+N)"));
        if (btnAbrirProyecto != null) btnAbrirProyecto.setTooltip(new Tooltip(bundle.getString("button.openProject") + " (Ctrl+Shift+O)"));
        if (btnNuevo       != null) btnNuevo.setTooltip(new Tooltip(bundle.getString("button.new") + " (Ctrl+N)"));
        if (btnSave        != null) btnSave.setTooltip(new Tooltip(bundle.getString("button.save") + " (Ctrl+S)"));
        if (btnClose       != null) btnClose.setTooltip(new Tooltip(bundle.getString("button.close") + " (Ctrl+W)"));
        if (btnUndo        != null) btnUndo.setTooltip(new Tooltip(bundle.getString("button.undo") + " (Ctrl+Z)"));
        if (btnRedo        != null) btnRedo.setTooltip(new Tooltip(bundle.getString("button.redo") + " (Ctrl+Y)"));
        if (btnCut         != null) btnCut.setTooltip(new Tooltip(bundle.getString("button.cut") + " (Ctrl+X)"));
        if (btnCopy        != null) btnCopy.setTooltip(new Tooltip(bundle.getString("button.copy") + " (Ctrl+C)"));
        if (btnPaste       != null) btnPaste.setTooltip(new Tooltip(bundle.getString("button.paste") + " (Ctrl+V)"));
        if (btnFind        != null) btnFind.setTooltip(new Tooltip(bundle.getString("tooltip.find.file") + " (Ctrl+F)"));
        if (btnFindProject != null) btnFindProject.setTooltip(new Tooltip(bundle.getString("tooltip.find.project") + " (Ctrl+H)"));
        if (btnCompilar    != null) btnCompilar.setTooltip(new Tooltip(bundle.getString("button.compile") + " (F5)"));
        if (btnEjecutar    != null) btnEjecutar.setTooltip(new Tooltip(bundle.getString("button.run") + " (F6)"));
    }

    /**
     * Maneja el clic en el label de estado de compilación para navegar al primer error
     */
    private void onCompileStatusLabelClicked() {
        // Solo navegar si hay errores
        if (!lastCompilationHasErrors) return;
        
        // Obtener archivo actual
        Tab selectedTab = fxUtils.getSelectedTab(tabPane);
        if (selectedTab == null) return;
        
        File currentFile = tabFileMap.get(selectedTab);
        if (currentFile == null) return;
        
        // Buscar diagnostics para el archivo actual
        CompileLineDiagnostics diags = compileDiagnosticsByFile.get(toFileKey(currentFile));
        if (diags == null || diags.errors.isEmpty()) return;
        
        // Encontrar la primera línea con error
        int firstErrorLine = diags.errors.stream()
            .mapToInt(Integer::intValue)
            .min()
            .orElse(-1);
        
        if (firstErrorLine > 0) {
            editOptionsController.goToLine(tabPane, firstErrorLine);
        }
    }

    /**
     * Obtiene el Stage principal de la aplicación
     * @return Stage principal
     */
    private Stage getStage() { return (Stage) tabPane.getScene().getWindow(); }
}

