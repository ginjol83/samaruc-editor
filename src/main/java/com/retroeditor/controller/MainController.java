package com.retroeditor.controller;

import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.Optional;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import java.awt.Desktop;
import java.net.URI;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.text.MessageFormat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.kordamp.ikonli.javafx.FontIcon;
import javafx.event.ActionEvent;

import com.retroeditor.controller.projectExplorer.ProjectExplorerController;
import com.retroeditor.controller.config.ConfigDialogCoordinator;
import com.retroeditor.controller.editor.EditOptionsController;
import com.retroeditor.controller.editor.FileOptionsController;
import com.retroeditor.controller.tools.TileEditorController;
import com.retroeditor.controller.tools.StructureViewController;
import com.retroeditor.controller.tools.PngToZxConverterController;
import com.retroeditor.controller.tools.PngToGameBoyConverterController;
import com.retroeditor.controller.tools.GameBoyAudioEditorController;
import com.retroeditor.controller.terminal.TerminalController;
import com.retroeditor.controller.search.ProjectContextPort;
import com.retroeditor.controller.search.SearchController;
import com.retroeditor.controller.build.BuildController;
import com.retroeditor.controller.help.HelpController;
import com.retroeditor.controller.search.FileOpenPort;

import com.retroeditor.service.CompilationDiagnosticParserService;
import com.retroeditor.service.ProjectPlatformDetectionService;
import com.retroeditor.service.PropertiesConfigRepository;
import com.retroeditor.service.syntax.SyntaxHighlighter;
import com.retroeditor.service.HomeReleaseNotesService;
import com.retroeditor.service.PluginApplicationService;
import com.retroeditor.service.ProjectSearchService;
import com.retroeditor.service.TextSearchService;
import com.retroeditor.service.FileTemplateService;
import com.retroeditor.service.ProjectStatisticsService;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.WorkspaceService;
import com.retroeditor.service.CodeFormatterService;
import com.retroeditor.model.WorkspaceModel;
import com.retroeditor.service.LanguageService;
import com.retroeditor.service.AppLogger;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.EditorModel;

import com.retroeditor.util.CompilationDiagnosticsUiHelper;
import com.retroeditor.util.FXUtils;
import com.retroeditor.util.AppInfo;

import com.retroeditor.view.UIElements;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import javafx.scene.Scene;
import javafx.scene.Parent;

import javafx.scene.control.Tab;
import javafx.scene.control.Menu;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.CodeArea;

public class MainController implements ProjectContextPort, FileOpenPort {
    private static final Set<String> NON_TEXT_EXTENSIONS = Set.of(
        "tap", "tzx", "z80", "sna", "gb", "gbc", "rom", "bin", "cdt", "wav", "dsk", "edsk", "class", "jar", "png", "jpg", "jpeg", "gif", "ico", "exe", "dll"
    );
    private static final String RECENT_FILES_PROPERTY = "recent_files";
    private static final String RECENT_PROJECTS_PROPERTY = "recent_projects";
    private static final String LAST_OPEN_FILE_DIR_PROPERTY = "last_open_file_directory";
    private static final String LAST_OPEN_PROJECT_DIR_PROPERTY = "last_open_project_directory";
    private static final String LAST_SESSION_PROJECT_PROPERTY = "last_session_project";
    private static final String LAST_SESSION_OPEN_FILES_PROPERTY = "last_session_open_files";
    private static final String LAST_SESSION_ACTIVE_FILE_PROPERTY = "last_session_active_file";
    private static final String EDITOR_APPEARANCE_PROPERTY = "editor_appearance";
    private static final String APP_STYLESHEET_CLASSIC = "/css/app.css";
    private static final String APP_STYLESHEET_DARK = "/css/app-dark.css";
    private static final int MAX_RECENT_ITEMS = 8;

    private final File        userConfigFile = new File(System.getProperty("user.home"), ".retroeditor.properties");

    private       FXUtils     fxUtils        = new FXUtils();
    private final ConfigModel configModel    = new ConfigModel();

    private final EditOptionsController   editOptionsController   = new EditOptionsController();
    private final FileOptionsController   fileOptionsController   = new FileOptionsController();
    private final TileEditorController    tileEditorController    = new TileEditorController();
    private final PngToZxConverterController pngToZxConverterController = new PngToZxConverterController();
    private final PngToGameBoyConverterController pngToGameBoyConverterController = new PngToGameBoyConverterController();
    private final GameBoyAudioEditorController    gameBoyAudioEditorController    = new GameBoyAudioEditorController();
    private final TerminalController      terminalController      = new TerminalController();
    private final BuildController         buildController         = new BuildController();
    private final HelpController          helpController          = new HelpController();
    private final ConfigDialogCoordinator configDialogCoordinator = new ConfigDialogCoordinator();
    private final CompilationDiagnosticParserService compilationDiagnosticParserService = new CompilationDiagnosticParserService();
    private final CompilationDiagnosticsUiHelper compilationDiagnosticsUiHelper = new CompilationDiagnosticsUiHelper();
    private final ConfigRepository configRepository = new PropertiesConfigRepository();
    private final WorkspaceService workspaceService = new WorkspaceService();
    private final PluginApplicationService pluginApplicationService = new PluginApplicationService();
    private final ProjectPlatformDetectionService projectPlatformDetectionService = new ProjectPlatformDetectionService();
    private final ProjectSearchService projectSearchService = new ProjectSearchService();
    private final TextSearchService textSearchService = new TextSearchService();
    private final FileTemplateService fileTemplateService = new FileTemplateService();
    private final HomeReleaseNotesService homeReleaseNotesService = new HomeReleaseNotesService();

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
    @FXML private Menu menuRefactor;
    @FXML private Menu menuHerramientas;
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
    @FXML private MenuItem menuItemRename;
    @FXML private MenuItem menuItemTileEditor;
    @FXML private MenuItem menuItemPngToZx;
    @FXML private MenuItem menuItemGoToLine;
    @FXML private MenuItem menuItemFind;
    @FXML private MenuItem menuItemFindProject;
    @FXML private MenuItem menuItemManual;
    @FXML private MenuItem menuItemGbdkDocs;
    @FXML private MenuItem menuItemZ88dkDocs;
    @FXML private MenuItem menuItemPluginManual;
    @FXML private MenuItem menuItemLicenses;
    @FXML private MenuItem menuItemCreditos;
    @FXML private MenuItem menuItemNuevoProyecto;
    @FXML private MenuItem menuItemAbrirProyecto;
    @FXML private MenuItem menuItemNuevo;
    @FXML private MenuItem menuItemNuevoPlantilla;
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
    @FXML private MenuItem menuItemCompilarYEjecutar;
    @FXML private MenuItem menuItemEjecutar;
    @FXML private MenuItem menuItemDeshacer;
    @FXML private MenuItem menuItemGuardarComo;

    private Menu menuRecentes;
    private Menu menuRecentFiles;
    private Menu menuRecentProjects;
    private MenuItem menuClearRecentFiles;
    private MenuItem menuClearRecentProjects;
    @FXML private AnchorPane projectExplorerAnchor;
    @FXML private AnchorPane structureViewAnchor;
    private StructureViewController structureViewController;
    @FXML private TabPane outputTabPane;
    @FXML private Tab tabTerminal;
    @FXML private StyleClassedTextArea consoleOutputArea;
    @FXML private StyleClassedTextArea debuggerOutputArea;
    @FXML private StackPane terminalContainer;
    @FXML private Button btnConsoleTop;
    @FXML private Button btnConsoleBottom;
    @FXML private Button btnClearConsole;
    @FXML private Button btnCopyConsole;
    @FXML private Button btnClearMonitor;
    @FXML private Button btnTerminalRestart;
    @FXML private Button btnTerminalClear;
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
        elements.menuItemCompilarYEjecutar = menuItemCompilarYEjecutar;
        elements.menuItemEjecutar = menuItemEjecutar;
        elements.menuItemFind = menuItemFind;
        elements.menuItemFindProject = menuItemFindProject;

        return elements;
    }

    private final Map<Tab, File> tabFileMap = new HashMap<>(); // Solo para mapear tabs a archivos

    private static final String HOME_TAB_ID             = "home-tab";
    private static final String HOME_LINK_HOOKED        = "home-link-hooked";
    private volatile boolean lastCompilationHasErrors   = false;
    private volatile boolean lastCompilationHasWarnings = false;

    private final Map<File, CompileLineDiagnostics> compileDiagnosticsByFile = new HashMap<>();

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
    private static final KeyCombination ACCEL_MOVE_UP      = new KeyCodeCombination(KeyCode.UP, KeyCombination.ALT_DOWN);
    private static final KeyCombination ACCEL_MOVE_DOWN    = new KeyCodeCombination(KeyCode.DOWN, KeyCombination.ALT_DOWN);
    private static final KeyCombination ACCEL_TOGGLE_COMMENT = new KeyCodeCombination(KeyCode.SLASH, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_AUTO_COMPLETE = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
    private static final KeyCombination ACCEL_FORMAT_CODE   = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN);
    private static final KeyCombination ACCEL_BUILD        = new KeyCodeCombination(KeyCode.F5);
    private static final KeyCombination ACCEL_RUN          = new KeyCodeCombination(KeyCode.F6);

    @FXML
    public void initialize() {
        // Cargar configuración del usuario (incluye gbdk_bin)
        configModel.applyProperties(configRepository.load(userConfigFile));

        buildController.setEmbeddedTerminalController(terminalController);

        // Leer idioma desde configModel (ya cargado por getDefaultLanguage)
        String lang     = configModel.getConfigProperty("idioma", "es");
        File i18nDir    = new File(System.getProperty("user.home"), ".samaruc-editor/i18n");
        LanguageService langService = new LanguageService(i18nDir);
        bundle          = langService.getBundleForCode(lang);

        AppLogger.setBundle(bundle);

        // Inicializar modelo, resaltador y vista
        editorModel         = new EditorModel();
        syntaxHighlighter   = new SyntaxHighlighter();
        fxUtils             = new FXUtils();
        fxUtils.setEditorAppearance(configModel.getConfigProperty(EDITOR_APPEARANCE_PROPERTY, ConfigModel.EDITOR_APPEARANCE_MODERN_DARK));
        fxUtils.setDoubleClickHandler((symbol, area) -> navigateToDefinition(symbol));
        restoreDialogDirectories();
        setupRecentMenus();
        refreshRecentMenus();

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
        configModel.editorAppearanceProperty().addListener((obs, oldV, newV) -> {
            fxUtils.setEditorAppearance(newV);
            Platform.runLater(() -> {
                applyEditorAppearanceToOpenTabs();
                if (tabPane != null && tabPane.getScene() != null) {
                    applyApplicationAppearance(tabPane.getScene(), newV);
                }
            });
        });
        configModel.enableLogsProperty().addListener((obs, oldV, newV) -> {
            if (terminalController != null) {
                terminalController.setEnableLogging(newV);
            }
        });

        buildController.compilationRunningProperty().addListener((obs, wasRunning, isRunning) -> updateCompileStatusUI());

        if (tabPane != null) {
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                applyDiagnosticsForTab(newTab);
                persistSessionState();
                if (structureViewController != null) {
                    structureViewController.setActiveCodeArea(fxUtils.getCurrentCodeArea(tabPane));
                }
            });

            // Listener para cuando se cierran pestañas
            tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> {
                while (c.next()) {
                    if (c.wasRemoved() || c.wasAdded()) {
                        persistSessionState();
                    }
                }
            });
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

        // Cargar StructureView
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/StructureView.fxml"));
            Parent structure = loader.load();
            structureViewController = loader.getController();
            if (structureViewAnchor != null) {
                structureViewAnchor.getChildren().setAll(structure);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Restaurar estado de la última sesión
        Platform.runLater(() -> {
            restoreSessionState();
            
            // Si después de restaurar la sesión NO hay pestañas abiertas
            // y la opción de mostrar Home está activada, la abrimos.
            // Si está desactivada, el editor empezará vacío.
            if (tabPane.getTabs().isEmpty() && configModel.isShowHomeOnStartup()) {
                onNewTab(null); // Esto abre la Home Tab por defecto si no hay archivos
            }
        });

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

        if (terminalContainer != null) {
            terminalController.setEnableLogging(configModel.isEnableLogs());
            terminalController.setTerminalShell(configModel.getConfigProperty("terminal_shell", "auto"));
            terminalController.startTerminal(terminalContainer);
            if (tabTerminal != null) {
                tabTerminal.selectedProperty().addListener((obs, wasSelected, selected) -> {
                    if (selected) {
                        terminalController.requestTerminalFocus();
                    }
                });
            }
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
        if (btnConsoleTop != null) {
            btnConsoleTop.setTooltip(new Tooltip(bundle.getString("tooltip.console.top")));
        }
        if (btnConsoleBottom != null) {
            btnConsoleBottom.setTooltip(new Tooltip(bundle.getString("tooltip.console.bottom")));
        }
        if (btnTerminalRestart != null) {
            btnTerminalRestart.setTooltip(new Tooltip(msg("tooltip.terminal.restart", "Reiniciar terminal")));
        }
        if (btnTerminalClear != null) {
            btnTerminalClear.setTooltip(new Tooltip(msg("tooltip.terminal.clear", "Limpiar terminal")));
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
        if (menuArchivo           != null) menuArchivo.setText(bundle.getString("menu.file"));
        if (menuCompilacion       != null) menuCompilacion.setText(bundle.getString("menu.project"));
        if (menuEdicion           != null) menuEdicion.setText(bundle.getString("menu.edit"));
        if (menuRefactor          != null) menuRefactor.setText(bundle.getString("menu.refactor"));
        if (menuHerramientas      != null) menuHerramientas.setText(bundle.getString("menu.tools"));
        if (menuAyuda             != null) menuAyuda.setText(bundle.getString("menu.help"));
        if (menuItemNuevoProyecto != null) menuItemNuevoProyecto.setText(bundle.getString("button.newProject"));
        if (menuItemAbrirProyecto != null) menuItemAbrirProyecto.setText(bundle.getString("button.openProject"));
        if (menuItemNuevo         != null) menuItemNuevo.setText(bundle.getString("button.new"));
        if (menuItemNuevoPlantilla != null) menuItemNuevoPlantilla.setText(bundle.getString("menu.file.newFromTemplate"));
        if (menuItemAbrir         != null) menuItemAbrir.setText(bundle.getString("button.open"));
        if (menuItemGuardar       != null) menuItemGuardar.setText(bundle.getString("button.save"));
        if (menuItemGuardarComo   != null) menuItemGuardarComo.setText(bundle.getString("button.saveAs"));
        if (menuItemCerrar        != null) menuItemCerrar.setText(bundle.getString("button.close"));
        if (menuItemConfig        != null) menuItemConfig.setText(bundle.getString("button.config"));
        if (menuItemSalir         != null) menuItemSalir.setText(bundle.getString("menu.exit"));
        if (menuItemCompilar      != null) menuItemCompilar.setText(bundle.getString("button.compile"));
        if (menuItemEjecutar      != null) menuItemEjecutar.setText(bundle.getString("button.run"));
        if (menuItemDeshacer      != null) menuItemDeshacer.setText(bundle.getString("button.undo"));
        if (menuItemRehacer       != null) menuItemRehacer.setText(bundle.getString("button.redo"));
        if (menuItemCortar        != null) menuItemCortar.setText(bundle.getString("button.cut"));
        if (menuItemCopiar        != null) menuItemCopiar.setText(bundle.getString("button.copy"));
        if (menuItemPegar         != null) menuItemPegar.setText(bundle.getString("button.paste"));
        if (menuItemRename        != null) menuItemRename.setText(bundle.getString("menu.rename"));
        if (menuItemTileEditor    != null) menuItemTileEditor.setText(bundle.getString("menu.tools.tileEditor"));
        if (menuItemGoToLine      != null) menuItemGoToLine.setText(bundle.getString("menu.goto.line"));
        if (menuItemFind          != null) menuItemFind.setText(bundle.getString("menu.find.file"));
        if (menuItemFindProject   != null) menuItemFindProject.setText(bundle.getString("menu.find.project"));
        if (menuItemManual        != null) menuItemManual.setText(bundle.getString("menu.help.manual"));
        if (menuItemGbdkDocs      != null) menuItemGbdkDocs.setText(bundle.getString("menu.help.gbdkDocs"));
        if (menuItemZ88dkDocs     != null) menuItemZ88dkDocs.setText(bundle.getString("menu.help.z88dkDocs"));
        if (menuItemPluginManual  != null) menuItemPluginManual.setText(bundle.getString("menu.help.pluginsManual"));
        if (menuItemLicenses      != null) menuItemLicenses.setText(bundle.getString("menu.help.licenses"));
        if (menuItemCreditos      != null) menuItemCreditos.setText(bundle.getString("menu.help.credits"));
        
        if (lblFooter != null) {
            lblFooter.setText("Desarrollado por Andres Gimenez © 2025 - v" + AppInfo.getVersion());
        }

        updateOutputTabsI18n();
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

        // Attach stylesheet to the Scene according to the configured appearance
        Platform.runLater(() -> {
            try {
                if (tabPane != null && tabPane.getScene() != null) {
                    applyApplicationAppearance(tabPane.getScene(), configModel.getConfigProperty(EDITOR_APPEARANCE_PROPERTY, ConfigModel.EDITOR_APPEARANCE_MODERN_DARK));
                }
            } catch (Exception ignored) {}
        });
        Platform.runLater(this::restoreLastSessionState);

        if (menuItemCompilar != null) {
            menuItemCompilar.disableProperty().bind(buildController.compilationRunningProperty());
        }
        if (menuItemCompilarYEjecutar != null) {
            menuItemCompilarYEjecutar.disableProperty().bind(buildController.compilationRunningProperty());
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
    private void onGoToDefinition(ActionEvent event) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;
        String text = codeArea.getText();
        int caret = codeArea.getCaretPosition();
        String symbol = editOptionsController.extractWordAtCaret(text, caret);
        if (symbol == null || symbol.isBlank()) return;
        navigateToDefinition(symbol);
    }

    private void navigateToDefinition(String symbol) {
        Tab selectedTab = fxUtils.getSelectedTab(tabPane);
        File currentFile = selectedTab != null ? tabFileMap.get(selectedTab) : null;

        EditOptionsController.DefinitionResult result = editOptionsController.findDefinition(
            symbol, currentFile, currentProjectDir, tabFileMap, editorModel
        );

        if (result != null) {
            openFileFromExplorer(result.getFile());
            Platform.runLater(() -> {
                editOptionsController.goToLine(tabPane, result.getLine());
                UserActionMonitor.goToLineExecuted(result.getLine());
            });
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(msg("dialog.definition.notfound.title", "Ir a definición"));
            alert.setHeaderText(null);
            alert.setContentText(msgFmt("dialog.definition.notfound.symbol", "No se encontró la definición de ''{0}''.", symbol));
            alert.showAndWait();
        }
    }

    private String msgFmt(String key, String fallback, Object... args) {
        return MessageFormat.format(msg(key, fallback), args);
    }

    private TabPane secondaryTabPane = null;
    private javafx.scene.control.SplitPane editorSplitPane = null;

    @FXML
    private void onSplitEditor(ActionEvent event) {
        if (editorSplitPane == null) {
            secondaryTabPane = new TabPane();
            secondaryTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

            javafx.scene.Parent parent = tabPane.getParent();
            if (parent instanceof javafx.scene.layout.Pane pane) {
                int idx = pane.getChildren().indexOf(tabPane);
                if (idx >= 0) {
                    editorSplitPane = new javafx.scene.control.SplitPane();
                    editorSplitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
                    pane.getChildren().remove(tabPane);
                    editorSplitPane.getItems().addAll(tabPane, secondaryTabPane);
                    editorSplitPane.setDividerPositions(0.5);
                    pane.getChildren().add(idx, editorSplitPane);
                }
            }
        } else {
            javafx.scene.Parent parent = editorSplitPane.getParent();
            if (parent instanceof javafx.scene.layout.Pane pane) {
                int idx = pane.getChildren().indexOf(editorSplitPane);
                if (idx >= 0) {
                    editorSplitPane.getItems().remove(tabPane);
                    pane.getChildren().remove(editorSplitPane);
                    pane.getChildren().add(idx, tabPane);
                    editorSplitPane = null;
                    secondaryTabPane = null;
                }
            }
        }
    }


    @FXML
    private void onFindInProject(ActionEvent event) {
        if (searchController != null) searchController.showFindInProjectDialog();
    }

    @FXML
    private void onCompilar(ActionEvent event) {
        boolean saved = fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel);
        if (!saved) {
            terminalController.appendConsoleOutput("No se puede compilar: el archivo no está guardado.", consoleOutputArea);
            return;
        }
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
    private void onCompilarYEjecutar(ActionEvent event) {
        onEjecutar(event);
    }

    @FXML
    public void onFormatCode(ActionEvent event) {
        Tab tab = fxUtils.getSelectedTab(tabPane);
        if (tab == null) return;
        CodeArea codeArea = fxUtils.getCodeArea(tab);
        if (codeArea == null) return;
        String fileName = tab.getText();
        String formatted = CodeFormatterService.formatCode(codeArea.getText(), fileName);
        codeArea.replaceText(formatted);
    }

    @FXML
    private void onNuevoProyecto(ActionEvent event) {
        File creado = fxUtils.createNewProject(getStage(), bundle, resolveInitialProjectDirectory());
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
        File carpeta = fxUtils.openExistingProject(getStage(), bundle, resolveInitialProjectDirectory());
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

    /**
     * Resuelve una clave i18n para las plantillas; devuelve null si no se encuentra
     * para que el servicio use el texto por defecto de la plantilla.
     */
    private String msgFromBundle(String key) {
        return msg(key, null);
    }

    public File getCurrentProjectDir() { return currentProjectDir; }

    // Expose MenuBar for plugins or other host integrations
    public MenuBar getMenuBar() { return menuBar; }

    public void setProyectoActual(File carpeta) {
        this.currentProjectDir = carpeta;
        UserActionMonitor.projectSet(carpeta != null ? carpeta.getName() : null);

        if (terminalController != null) {
            terminalController.restartTerminalIfWorkingDirectoryChanged(terminalContainer, carpeta);
        }

        // Cargar workspace si existe
        if (carpeta != null) {
            WorkspaceModel workspace = workspaceService.loadWorkspace(carpeta);
            configModel.setActiveWorkspace(workspace);
        } else {
            configModel.setActiveWorkspace(null);
        }

        if (projectExplorerController != null) projectExplorerController.setRootDirectory(carpeta);
        try {
            if (carpeta != null) {
                fileOptionsController.setDefaultOpenDirectory(carpeta);
                fileOptionsController.setDefaultSaveDirectory(carpeta);
                configModel.setConfigProperty(LAST_OPEN_PROJECT_DIR_PROPERTY, carpeta.getAbsolutePath());
                configModel.setConfigProperty(LAST_OPEN_FILE_DIR_PROPERTY, carpeta.getAbsolutePath());
                configRepository.save(userConfigFile, configModel.toProperties());
                    rememberRecentProject(carpeta);
                    persistSessionState();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
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
        rememberRecentFile(file);
        if (file.getParentFile() != null) {
            fileOptionsController.setDefaultOpenDirectory(file.getParentFile());
            fileOptionsController.setDefaultSaveDirectory(file.getParentFile());
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

    public void openFileInSecondaryTab(File file) {
        if (file == null || !file.isFile()) return;
        if (editorSplitPane == null) {
            onSplitEditor(null);
        }
        if (secondaryTabPane == null) return;

        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            if (file.equals(entry.getValue()) && secondaryTabPane.getTabs().contains(entry.getKey())) {
                secondaryTabPane.getSelectionModel().select(entry.getKey());
                return;
            }
        }

        try {
            String content = editorModel != null ? editorModel.getFileContent(file) : null;
            if (content == null) {
                editorModel.openFile(file);
                content = editorModel.getFileContent(file);
            }
            boolean isMarkdown = file.getName().toLowerCase(Locale.ROOT).endsWith(".md");
            Tab tab = isMarkdown 
                ? fxUtils.addMarkdownTab(file.getName(), content, syntaxHighlighter, secondaryTabPane)
                : fxUtils.addTab(file.getName(), content, syntaxHighlighter, secondaryTabPane);
            
            tabFileMap.put(tab, file.getAbsoluteFile());
            secondaryTabPane.getSelectionModel().select(tab);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openFileInEditorTab(File file, boolean forceSingleByteText) throws IOException {
        if (forceSingleByteText) {
            editorModel.openFileAsSingleByteText(file);
        } else {
            editorModel.openFile(file);
        }

        String content = editorModel.getFileContent(file);
        Tab tab = fxUtils.isMarkdownFileName(file.getName())
            ? fxUtils.addMarkdownTab(file.getName(), content, syntaxHighlighter, tabPane)
            : fxUtils.addTab(file.getName(), content, syntaxHighlighter, tabPane);
        tabFileMap.put(tab, file);
        applyDiagnosticsForTab(tab);
        persistSessionState();

        // Limpieza cuando el usuario cierra el tab con la X integrada
        tab.setOnClosed(e -> {
            tabFileMap.remove(tab);
            editorModel.closeFile(file);
            UserActionMonitor.fileClosed(file.getName());
            persistSessionState();
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
        persistSessionState();
    }

    public void onProjectExplorerTargetRenamed(File previousTarget, File renamedTarget, boolean targetWasDirectory) {
        if (previousTarget == null || renamedTarget == null || tabPane == null) return;

        Path previousPath;
        Path renamedPath;
        try {
            previousPath = previousTarget.toPath().toAbsolutePath().normalize();
            renamedPath = renamedTarget.toPath().toAbsolutePath().normalize();
        } catch (Exception ex) {
            return;
        }

        List<Map.Entry<Tab, File>> entries = new ArrayList<>(tabFileMap.entrySet());
        for (Map.Entry<Tab, File> entry : entries) {
            Tab tab = entry.getKey();
            File mappedFile = entry.getValue();
            if (tab == null || mappedFile == null) continue;

            Path mappedPath;
            try {
                mappedPath = mappedFile.toPath().toAbsolutePath().normalize();
            } catch (Exception ex) {
                continue;
            }

            final File updatedFile;
            if (targetWasDirectory) {
                if (!mappedPath.startsWith(previousPath)) continue;
                Path relative = previousPath.relativize(mappedPath);
                updatedFile = renamedPath.resolve(relative).toFile();
            } else {
                if (!mappedPath.equals(previousPath)) continue;
                updatedFile = renamedPath.toFile();
            }

            tabFileMap.put(tab, updatedFile);
            if (editorModel != null) {
                editorModel.renameOpenFile(mappedFile, updatedFile);
            }
            updateTabTitleAfterRename(tab, updatedFile.getName());

            File oldDiagnosticsKey = mappedFile.getAbsoluteFile();
            if (compileDiagnosticsByFile.containsKey(oldDiagnosticsKey)) {
                CompileLineDiagnostics diagnostics = compileDiagnosticsByFile.remove(oldDiagnosticsKey);
                compileDiagnosticsByFile.put(updatedFile.getAbsoluteFile(), diagnostics);
            }
        }

        if (renamedTarget.isFile()) {
            removeRecentEntry(RECENT_FILES_PROPERTY, previousTarget, false);
            updateRecentEntries(RECENT_FILES_PROPERTY, renamedTarget, false);
        }

        persistSessionState();
    }

    private void updateTabTitleAfterRename(Tab tab, String newFileName) {
        if (tab == null || newFileName == null || newFileName.isBlank()) return;

        String currentTitle = tab.getText();
        boolean dirty = currentTitle != null && currentTitle.startsWith("*");
        tab.setText((dirty ? "*" : "") + newFileName);
    }

    private void restoreLastSessionState() {
        File sessionProject = resolveStoredSessionProject();
        if (sessionProject != null) {
            setProyectoActual(sessionProject);
        }

        List<File> filesToRestore = loadSessionOpenFiles();
        if (filesToRestore.isEmpty()) {
            return;
        }

        closeHomeTabIfPresent();

        for (File file : filesToRestore) {
            if (file == null || !file.isFile()) continue;
            if (currentProjectDir == null) {
                File parent = file.getParentFile();
                if (parent != null && parent.isDirectory()) {
                    setProyectoActual(parent);
                }
            }
            try {
                openFileInEditorTab(file, false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        String activeFilePath = configModel.getConfigProperty(LAST_SESSION_ACTIVE_FILE_PROPERTY, "");
        if (activeFilePath == null || activeFilePath.isBlank()) return;

        File activeFile = new File(activeFilePath).getAbsoluteFile();
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            if (sameFile(entry.getValue(), activeFile)) {
                tabPane.getSelectionModel().select(entry.getKey());
                break;
            }
        }
    }

    private void closeHomeTabIfPresent() {
        if (tabPane == null) return;
        Tab homeTab = null;
        for (Tab tab : tabPane.getTabs()) {
            if (HOME_TAB_ID.equals(tab.getId())) {
                homeTab = tab;
                break;
            }
        }
        if (homeTab != null) {
            tabPane.getTabs().remove(homeTab);
        }
    }

    private File resolveStoredSessionProject() {
        String raw = configModel.getConfigProperty(LAST_SESSION_PROJECT_PROPERTY, "");
        if (raw == null || raw.isBlank()) return null;
        File candidate = new File(raw).getAbsoluteFile();
        return candidate.exists() && candidate.isDirectory() ? candidate : null;
    }

    private List<File> loadSessionOpenFiles() {
        String raw = configModel.getConfigProperty(LAST_SESSION_OPEN_FILES_PROPERTY, "");
        if (raw == null || raw.isBlank()) return Collections.emptyList();

        List<File> files = new ArrayList<>();
        String[] lines = raw.split("\\R");
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            File file = new File(line.trim()).getAbsoluteFile();
            if (!file.exists() || !file.isFile()) continue;
            if (!containsFile(files, file)) {
                files.add(file);
            }
        }
        return files;
    }

    private void restoreSessionState() {
        try {
            // 1. Restaurar proyecto
            String lastProject = configModel.getConfigProperty(LAST_SESSION_PROJECT_PROPERTY, "");
            if (lastProject != null && !lastProject.isBlank()) {
                File projectDir = new File(lastProject);
                if (projectDir.exists() && projectDir.isDirectory()) {
                    setProyectoActual(projectDir);
                    maybeApplyDetectedProfileForOpenedProject(projectDir);
                }
            }

            // 2. Restaurar archivos abiertos
            String openFilesRaw = configModel.getConfigProperty(LAST_SESSION_OPEN_FILES_PROPERTY, "");
            if (openFilesRaw != null && !openFilesRaw.isBlank()) {
                List<File> filesToOpen = loadRecentEntries(LAST_SESSION_OPEN_FILES_PROPERTY, false);
                for (File file : filesToOpen) {
                    if (file.exists() && file.isFile()) {
                        try {
                            openFileInEditorTab(file, false);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }

            // 3. Restaurar archivo activo
            String activeFilePath = configModel.getConfigProperty(LAST_SESSION_ACTIVE_FILE_PROPERTY, "");
            if (activeFilePath != null && !activeFilePath.isBlank()) {
                File activeFile = new File(activeFilePath).getAbsoluteFile();
                for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
                    if (sameFile(entry.getValue(), activeFile)) {
                        tabPane.getSelectionModel().select(entry.getKey());
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void persistSessionState() {
        try {
            if (currentProjectDir != null && currentProjectDir.exists() && currentProjectDir.isDirectory()) {
                configModel.setConfigProperty(LAST_SESSION_PROJECT_PROPERTY, currentProjectDir.getAbsolutePath());
            } else {
                configModel.setConfigProperty(LAST_SESSION_PROJECT_PROPERTY, "");
            }

            List<File> openFiles = new ArrayList<>();
            if (tabPane != null) {
                for (Tab tab : tabPane.getTabs()) {
                    File mapped = tabFileMap.get(tab);
                    if (mapped == null || !mapped.exists() || !mapped.isFile()) continue;
                    if (!containsFile(openFiles, mapped)) {
                        openFiles.add(mapped.getAbsoluteFile());
                    }
                }
            }
            configModel.setConfigProperty(LAST_SESSION_OPEN_FILES_PROPERTY, joinRecentEntries(openFiles));

            String activeFilePath = "";
            if (tabPane != null) {
                Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
                File selectedFile = selectedTab != null ? tabFileMap.get(selectedTab) : null;
                if (selectedFile != null && selectedFile.exists() && selectedFile.isFile()) {
                    activeFilePath = selectedFile.getAbsolutePath();
                }
            }
            configModel.setConfigProperty(LAST_SESSION_ACTIVE_FILE_PROPERTY, activeFilePath);
            configRepository.save(userConfigFile, configModel.toProperties());
        } catch (Exception ex) {
            ex.printStackTrace();
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
        return fxUtils.getCodeArea(tab);
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
            currentProjectDir,
            configRepository,
            pluginApplicationService,
            bundle,
            () -> {
                String langCode = configModel.getConfigProperty("idioma", "es");
                Locale newLocale = java.util.Locale.forLanguageTag(langCode);
                
                // Cargar el bundle usando LanguageService para soportar idiomas externos
                File i18nDir = new File(System.getProperty("user.home"), ".samaruc-editor/i18n");
                LanguageService langService = new LanguageService(i18nDir);
                bundle = langService.getBundleForCode(langCode);
                
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
    private void onNuevoArchivoDesdePlantilla(ActionEvent event) {
        if (this.currentProjectDir == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(bundle.getString("alert.project.notOpen.title"));
            alert.setHeaderText(null);
            alert.setContentText(bundle.getString("alert.project.notOpen.content"));
            alert.showAndWait();
            return;
        }

        List<FileTemplateService.FileTemplate> templates = fileTemplateService.getTemplates(this::msgFromBundle);
        if (templates.isEmpty()) return;

        Dialog<FileTemplateService.FileTemplate> dialog = new Dialog<>();
        dialog.setTitle(bundle.getString("dialog.template.title"));
        dialog.setHeaderText(bundle.getString("dialog.template.header"));

        ButtonType createType = new ButtonType(bundle.getString("dialog.template.confirm"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(bundle.getString("dialog.template.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, cancelType);

        ListView<FileTemplateService.FileTemplate> listView = new ListView<>();
        listView.getItems().setAll(templates);
        listView.setPrefSize(500, 240);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FileTemplateService.FileTemplate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name() + "  (" + item.fileName() + ")" + System.lineSeparator() + item.description());
                }
            }
        });
        listView.getSelectionModel().select(0);

        dialog.getDialogPane().setContent(listView);
        dialog.setResultConverter(button -> button == createType ? listView.getSelectionModel().getSelectedItem() : null);

        Optional<FileTemplateService.FileTemplate> result = dialog.showAndWait();
        result.ifPresent(template -> createFileFromTemplate(template));
    }

    private void createFileFromTemplate(FileTemplateService.FileTemplate template) {
        try {
            Path target = fileTemplateService.createTemplateFile(template, currentProjectDir.toPath());
            File created = target.toFile();
            openFileFromExplorer(created);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(bundle.getString("alert.template.error.title"));
            alert.setHeaderText(null);
            alert.setContentText(bundle.getString("alert.template.error.content").replace("{0}", ex.getMessage()));
            alert.showAndWait();
        }
    }

    @FXML
    private void onExit(ActionEvent event) { 
        shutdown();
        UserActionMonitor.tabClosed("Aplicación cerrada por el usuario");
        Platform.exit(); 
    }

    public void shutdown() {
        persistSessionState();
        try {
            buildController.stopActiveCompilation();
        } catch (Exception ignored) {
        }
        try {
            terminalController.stopTerminal();
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
    private void onTerminalClear(ActionEvent event) {
        terminalController.clearTerminal();
    }

    @FXML
    private void onTerminalRestart(ActionEvent event) {
        terminalController.restartTerminal(terminalContainer);
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
    private void onConsoleGoTop(ActionEvent event) {
        moveConsoleCaret(0);
    }

    @FXML
    private void onConsoleGoBottom(ActionEvent event) {
        if (consoleOutputArea == null) return;
        moveConsoleCaret(consoleOutputArea.getLength());
    }

    private void moveConsoleCaret(int offset) {
        if (consoleOutputArea == null) return;
        int safeOffset = Math.max(0, Math.min(offset, consoleOutputArea.getLength()));
        consoleOutputArea.moveTo(safeOffset);
        consoleOutputArea.requestFollowCaret();
    }

    @FXML
    private void onOpenFile(ActionEvent event) {
        File opened = fileOptionsController.onOpenFile(event, tabPane, tabFileMap, editorModel, syntaxHighlighter);
        if (opened != null) {
            rememberRecentFile(opened);
            persistSessionState();
        }
    }

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
            persistSessionState();
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
            persistSessionState();
        }
    }

    @FXML
    private void onCloseFile(ActionEvent event)  {
        fileOptionsController.onCloseFile(event, tabPane, tabFileMap, editorModel);
        persistSessionState();
    }

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
    private void onRenameFromProjectExplorer(ActionEvent event) {
        if (projectExplorerController != null) {
            projectExplorerController.renameSelectedTreeItem();
        }
    }

    @FXML
    private void onOpenTileEditor(ActionEvent event) {
        tileEditorController.open(
            getStage(),
            currentProjectDir,
            bundle,
            exportedFile -> {
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                openFileFromExplorer(exportedFile);
                persistSessionState();
            }
        );
    }

    @FXML
    private void onOpenProjectStats(ActionEvent event) {
        if (currentProjectDir == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(bundle != null ? bundle.getString("alert.project.notOpen.title") : "Aviso");
            alert.setHeaderText(null);
            alert.setContentText(bundle != null ? bundle.getString("alert.project.notOpen.content") : "No hay ningún proyecto abierto.");
            alert.showAndWait();
            return;
        }

        ProjectStatisticsService statsService = new ProjectStatisticsService();
        ProjectStatisticsService.ProjectStats stats = statsService.calculateStats(currentProjectDir);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Estadísticas del proyecto");
        alert.setHeaderText("Estadísticas para: " + currentProjectDir.getName());
        alert.setContentText(String.format(
            "Archivos totales: %d\nLíneas de código (LOC): %d\nTamaño ROM / binarios: %d bytes\nArchivos de tiles/sprites: %d",
            stats.getTotalFiles(),
            stats.getTotalLinesOfCode(),
            stats.getRomSizeBytes(),
            stats.getTileFilesCount()
        ));
        alert.showAndWait();
    }

    @FXML
    private void onOpenPngToZxConverter(ActionEvent event) {
        pngToZxConverterController.open(
            getStage(),
            currentProjectDir,
            bundle,
            exportedFile -> {
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                openFileFromExplorer(exportedFile);
                persistSessionState();
            }
        );
    }

    @FXML
    private void onOpenPngToGameBoyConverter(ActionEvent event) {
        pngToGameBoyConverterController.open(
            getStage(),
            currentProjectDir,
            bundle,
            exportedFile -> {
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                openFileFromExplorer(exportedFile);
                persistSessionState();
            }
        );
    }

    @FXML
    private void onOpenGameBoyAudioEditor(ActionEvent event) {
        gameBoyAudioEditorController.open(
            getStage(),
            currentProjectDir,
            bundle,
            exportedFile -> {
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                openFileFromExplorer(exportedFile);
                persistSessionState();
            }
        );
    }

    @FXML
    private void onOpenManual()                               { helpController.onOpenManual(); }

    @FXML
    private void onOpenGbdkDocs()                             { helpController.onOpenGbdkDocs(); }
    
    @FXML
    private void onOpenZ88dkDocs()                            { helpController.onOpenZ88dkDocs(); }

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
        if (location == null) {
            location = compilationDiagnosticParserService.parseCompilerDiagnosticLocation(null, lineText, this::resolveConsoleFileReference);
        }
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
        if (btnConsoleTop       != null) btnConsoleTop.setTooltip(new Tooltip(bundle.getString("tooltip.console.top")));
        if (btnConsoleBottom    != null) btnConsoleBottom.setTooltip(new Tooltip(bundle.getString("tooltip.console.bottom")));
        if (btnTerminalRestart  != null) btnTerminalRestart.setTooltip(new Tooltip(msg("tooltip.terminal.restart", "Reiniciar terminal")));
        if (btnTerminalClear    != null) btnTerminalClear.setTooltip(new Tooltip(msg("tooltip.terminal.clear", "Limpiar terminal")));
        updateCompileStatusUI();

        if (menuArchivo         != null) menuArchivo.setText(bundle.getString("menu.file"));
        if (menuCompilacion     != null) menuCompilacion.setText(bundle.getString("menu.project"));
        if (menuEdicion         != null) menuEdicion.setText(bundle.getString("menu.edit"));
        if (menuRefactor        != null) menuRefactor.setText(bundle.getString("menu.refactor"));
        if (menuHerramientas    != null) menuHerramientas.setText(bundle.getString("menu.tools"));
        if (menuAyuda           != null) menuAyuda.setText(bundle.getString("menu.help"));
        if (menuItemNuevoProyecto != null) menuItemNuevoProyecto.setText(bundle.getString("button.newProject"));
        if (menuItemAbrirProyecto != null) menuItemAbrirProyecto.setText(bundle.getString("button.openProject"));
        if (menuItemNuevo       != null) menuItemNuevo.setText(bundle.getString("button.new"));
        if (menuItemNuevoPlantilla != null) menuItemNuevoPlantilla.setText(bundle.getString("menu.file.newFromTemplate"));
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
        if (menuItemRename      != null) menuItemRename.setText(bundle.getString("menu.rename"));
        if (menuItemTileEditor  != null) menuItemTileEditor.setText(bundle.getString("menu.tools.tileEditor"));
        if (menuItemGoToLine    != null) menuItemGoToLine.setText(bundle.getString("menu.goto.line"));
        if (menuItemFind        != null) menuItemFind.setText(bundle.getString("menu.find.file"));
        if (menuItemFindProject != null) menuItemFindProject.setText(bundle.getString("menu.find.project"));
        if (menuItemManual      != null) menuItemManual.setText(bundle.getString("menu.help.manual"));
        if (menuItemGbdkDocs    != null) menuItemGbdkDocs.setText(bundle.getString("menu.help.gbdkDocs"));
        if (menuItemZ88dkDocs   != null) menuItemZ88dkDocs.setText(bundle.getString("menu.help.z88dkDocs"));
        if (menuItemPluginManual != null) menuItemPluginManual.setText(bundle.getString("menu.help.pluginsManual"));
        if (menuItemLicenses    != null) menuItemLicenses.setText(bundle.getString("menu.help.licenses"));
        if (menuItemCreditos    != null) menuItemCreditos.setText(bundle.getString("menu.help.credits"));
        
        if (lblFooter != null) {
            lblFooter.setText("Desarrollado por Andres Gimenez © 2025 - v" + AppInfo.getVersion());
        }

        updateOutputTabsI18n();
        refreshRecentMenus();
        applyDiagnosticLegendI18n();
        applyMenuAccelerators();
        applyShortcutTooltips();
        refreshHomeTabLanguage();
    }

    private void updateOutputTabsI18n() {
        if (outputTabPane == null) return;
        if (outputTabPane.getTabs().size() > 0) {
            outputTabPane.getTabs().get(0).setText(msg("tab.console", "Consola"));
        }
        if (outputTabPane.getTabs().size() > 1) {
            outputTabPane.getTabs().get(1).setText(msg("tab.monitor", "Monitor"));
        }
        if (tabTerminal != null) {
            tabTerminal.setText(msg("tab.terminal", "Terminal"));
        } else if (outputTabPane.getTabs().size() > 2) {
            outputTabPane.getTabs().get(2).setText(msg("tab.terminal", "Terminal"));
        }
    }

    private void setupRecentMenus() {
        if (menuArchivo == null || menuRecentes != null) {
            return;
        }

        menuRecentes = new Menu();
        menuRecentFiles = new Menu();
        menuRecentProjects = new Menu();
        menuClearRecentFiles = new MenuItem();
        menuClearRecentProjects = new MenuItem();

        menuClearRecentFiles.setOnAction(e -> clearRecentFiles());
        menuClearRecentProjects.setOnAction(e -> clearRecentProjects());

        menuRecentes.getItems().add(menuRecentProjects);
        menuRecentes.getItems().add(menuRecentFiles);

        if (!menuArchivo.getItems().contains(menuRecentes)) {
            // Buscamos un separador para insertar antes o al final si no hay
            int insertIndex = -1;
            for (int i = 0; i < menuArchivo.getItems().size(); i++) {
                if (menuArchivo.getItems().get(i) instanceof javafx.scene.control.SeparatorMenuItem) {
                    insertIndex = i + 1; // Después del primer separador ("Abrir proyecto" / "Abrir")
                    break;
                }
            }
            if (insertIndex == -1) insertIndex = Math.min(2, menuArchivo.getItems().size());
            menuArchivo.getItems().add(insertIndex, menuRecentes);
            menuArchivo.getItems().add(insertIndex + 1, new javafx.scene.control.SeparatorMenuItem());
        }
    }

    private void refreshRecentMenus() {
        if (menuRecentes == null) {
            return;
        }

        String recentLabel = msg("menu.recent", "Recientes");
        String recentFilesLabel = msg("menu.recent.files", "Archivos recientes");
        String recentProjectsLabel = msg("menu.recent.projects", "Proyectos recientes");
        String emptyLabel = msg("menu.recent.empty", "Vacío");
        String clearFilesLabel = msg("menu.recent.clearFiles", "Limpiar archivos recientes");
        String clearProjectsLabel = msg("menu.recent.clearProjects", "Limpiar proyectos recientes");

        menuRecentes.setText(recentLabel);
        menuRecentProjects.setText(recentProjectsLabel);
        menuRecentFiles.setText(recentFilesLabel);
        menuClearRecentFiles.setText(clearFilesLabel);
        menuClearRecentProjects.setText(clearProjectsLabel);

        populateRecentMenu(menuRecentFiles, loadRecentEntries(RECENT_FILES_PROPERTY, false), emptyLabel, this::openRecentFile);
        populateRecentMenu(menuRecentProjects, loadRecentEntries(RECENT_PROJECTS_PROPERTY, true), emptyLabel, this::openRecentProject);
    }

    private void populateRecentMenu(Menu menu, List<File> entries, String emptyLabel, java.util.function.Consumer<File> action) {
        if (menu == null) return;

        menu.getItems().clear();
        if (entries == null || entries.isEmpty()) {
            MenuItem emptyItem = new MenuItem(emptyLabel);
            emptyItem.setDisable(true);
            menu.getItems().add(emptyItem);
            return;
        }

        for (File entry : entries) {
            if (entry == null) continue;
            MenuItem item = new MenuItem(formatRecentEntryLabel(entry));
            item.setOnAction(e -> action.accept(entry));
            menu.getItems().add(item);
        }

        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        MenuItem clearItem = menu == menuRecentFiles ? menuClearRecentFiles : menuClearRecentProjects;
        if (clearItem != null) {
            menu.getItems().add(clearItem);
        }
    }

    private String formatRecentEntryLabel(File entry) {
        if (entry == null) return "";
        String name = entry.getName();
        String parent = entry.getParent();
        if (parent == null || parent.isBlank()) {
            return name;
        }
        return name + " — " + parent;
    }

    private List<File> loadRecentEntries(String propertyKey, boolean directoryOnly) {
        String raw = configModel.getConfigProperty(propertyKey, "");
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }

        List<File> entries = new ArrayList<>();
        String[] tokens = raw.split("\\R");
        for (String token : tokens) {
            if (token == null) continue;
            String path = token.trim();
            if (path.isEmpty()) continue;

            File candidate = new File(path).getAbsoluteFile();
            if (directoryOnly) {
                if (!candidate.exists() || !candidate.isDirectory()) continue;
            } else {
                if (!candidate.exists() || !candidate.isFile()) continue;
            }

            if (!containsFile(entries, candidate)) {
                entries.add(candidate);
            }
        }

        return entries;
    }

    private void rememberRecentFile(File file) {
        updateRecentEntries(RECENT_FILES_PROPERTY, file, false);
        if (file != null && file.getParentFile() != null) {
            setLastDirectory(LAST_OPEN_FILE_DIR_PROPERTY, file.getParentFile());
        }
    }

    private void rememberRecentProject(File projectDir) {
        updateRecentEntries(RECENT_PROJECTS_PROPERTY, projectDir, true);
        if (projectDir != null) {
            setLastDirectory(LAST_OPEN_PROJECT_DIR_PROPERTY, projectDir);
        }
    }

    private void clearRecentFiles() {
        configModel.setConfigProperty(RECENT_FILES_PROPERTY, "");
        configRepository.save(userConfigFile, configModel.toProperties());
        refreshRecentMenus();
    }

    private void removeRecentEntry(String propertyKey, File entry, boolean directoryOnly) {
        if (entry == null) return;

        List<File> entries = new ArrayList<>(loadRecentEntries(propertyKey, directoryOnly));
        removeFile(entries, entry.getAbsoluteFile());
        configModel.setConfigProperty(propertyKey, joinRecentEntries(entries));
        configRepository.save(userConfigFile, configModel.toProperties());
        refreshRecentMenus();
    }

    private void clearRecentProjects() {
        configModel.setConfigProperty(RECENT_PROJECTS_PROPERTY, "");
        configRepository.save(userConfigFile, configModel.toProperties());
        refreshRecentMenus();
    }

    private void updateRecentEntries(String propertyKey, File entry, boolean directoryOnly) {
        if (entry == null) return;
        File normalizedEntry = entry.getAbsoluteFile();
        if (directoryOnly) {
            if (!normalizedEntry.exists() || !normalizedEntry.isDirectory()) return;
        } else {
            if (!normalizedEntry.exists() || !normalizedEntry.isFile()) return;
        }

        List<File> entries = new ArrayList<>(loadRecentEntries(propertyKey, directoryOnly));
        removeFile(entries, normalizedEntry);
        entries.add(0, normalizedEntry);
        while (entries.size() > MAX_RECENT_ITEMS) {
            entries.remove(entries.size() - 1);
        }

        configModel.setConfigProperty(propertyKey, joinRecentEntries(entries));
        configRepository.save(userConfigFile, configModel.toProperties());
        refreshRecentMenus();
    }

    private String joinRecentEntries(List<File> entries) {
        if (entries == null || entries.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (File entry : entries) {
            if (entry == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(entry.getAbsolutePath());
        }
        return sb.toString();
    }

    private void removeFile(List<File> entries, File target) {
        if (entries == null || entries.isEmpty() || target == null) return;
        entries.removeIf(entry -> sameFile(entry, target));
    }

    private boolean containsFile(List<File> entries, File target) {
        if (entries == null || entries.isEmpty() || target == null) return false;
        for (File entry : entries) {
            if (sameFile(entry, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameFile(File left, File right) {
        if (left == null || right == null) return false;
        try {
            return left.getAbsoluteFile().toPath().normalize().equals(right.getAbsoluteFile().toPath().normalize());
        } catch (Exception ex) {
            return left.getAbsolutePath().equalsIgnoreCase(right.getAbsolutePath());
        }
    }

    private void setLastDirectory(String propertyKey, File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
        configModel.setConfigProperty(propertyKey, directory.getAbsolutePath());
        configRepository.save(userConfigFile, configModel.toProperties());
    }

    private File resolveStoredDirectory(String propertyKey) {
        String path = configModel.getConfigProperty(propertyKey, "");
        if (path == null || path.isBlank()) {
            return null;
        }

        File dir = new File(path);
        return dir.exists() && dir.isDirectory() ? dir : null;
    }

    private File resolveInitialProjectDirectory() {
        File current = currentProjectDir;
        if (current != null && current.exists() && current.isDirectory()) {
            return current;
        }

        File storedProject = resolveStoredDirectory(LAST_OPEN_PROJECT_DIR_PROPERTY);
        if (storedProject != null) {
            return storedProject;
        }

        return resolveStoredDirectory(LAST_OPEN_FILE_DIR_PROPERTY);
    }

    private void restoreDialogDirectories() {
        File projectDir = resolveStoredDirectory(LAST_OPEN_PROJECT_DIR_PROPERTY);
        if (projectDir != null) {
            fileOptionsController.setDefaultOpenDirectory(projectDir);
            fileOptionsController.setDefaultSaveDirectory(projectDir);
            return;
        }

        File fileDir = resolveStoredDirectory(LAST_OPEN_FILE_DIR_PROPERTY);
        if (fileDir != null) {
            fileOptionsController.setDefaultOpenDirectory(fileDir);
            fileOptionsController.setDefaultSaveDirectory(fileDir);
        }
    }

    private void openRecentFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            showRecentItemMissing("FILE_OPEN", file, msg("menu.recent.files", "Archivos recientes"));
            removeRecentEntry(RECENT_FILES_PROPERTY, file, false);
            return;
        }

        openFileFromExplorer(file);
    }

    private void openRecentProject(File projectDir) {
        if (projectDir == null || !projectDir.exists() || !projectDir.isDirectory()) {
            showRecentItemMissing("PROJECT_OPEN", projectDir, msg("menu.recent.projects", "Proyectos recientes"));
            removeRecentEntry(RECENT_PROJECTS_PROPERTY, projectDir, true);
            return;
        }

        UserActionMonitor.projectOpened(projectDir.getAbsolutePath());
        setProyectoActual(projectDir);
        maybeApplyDetectedProfileForOpenedProject(projectDir);
    }

    private void showRecentItemMissing(String action, File file, String fallbackContext) {
        String path = file != null ? file.getAbsolutePath() : "(null)";
        UserActionMonitor.errorOccurred(action, "Recent item missing: " + path);

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(msg("menu.recent.missingTitle", "Elemento no encontrado"));
        alert.setHeaderText(fallbackContext);
        alert.setContentText(msg("menu.recent.missingMessage", "El siguiente elemento no se ha podido encontrar y será eliminado de la lista:") + "\n\n" + path);
        alert.showAndWait();
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
            "home.whatsNewTitle",
            "home.versionTitle",
            "home.pluginsTitle",
            "home.pluginsText",
            "home.toolchainsTitle",
            "home.toolchainsText",
            "home.toolchain.z88dk",
            "home.toolchain.gbdk",
            "home.footerText",
            "home.recentProjectsTitle",
            "home.recentFilesTitle",
            "home.noShowAgain"
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

        HomeReleaseNotesService.ReleaseNotes notes = homeReleaseNotesService.load(
            Path.of(System.getProperty("user.dir")),
            msg("home.whatsNew.fallback", "No hay changelog disponible para esta versión.")
        );

        String sourceText = notes.fromChangelog()
            ? msg("home.whatsNew.source.changelog", "Fuente: CHANGELOG.md")
                + (notes.changelogSection() != null && !notes.changelogSection().isBlank() ? " (" + notes.changelogSection() + ")" : "")
            : msg("home.whatsNew.source.fallback", "Fuente: contenido integrado");

        html = html.replace("{{home.versionValue}}", escapeHtml(notes.version()));
        html = html.replace("{{home.whatsNewSource}}", escapeHtml(sourceText));
        html = html.replace("{{home.whatsNewItemsHtml}}", renderHomeReleaseNotesItems(notes.items()));
        html = html.replace("{{home.recentProjectsHtml}}", renderRecentEntries(RECENT_PROJECTS_PROPERTY, true));
        html = html.replace("{{home.recentFilesHtml}}", renderRecentEntries(RECENT_FILES_PROPERTY, false));

        return html;
    }

    private String renderRecentEntries(String propertyKey, boolean directoryOnly) {
        List<File> entries = loadRecentEntries(propertyKey, directoryOnly);
        if (entries == null || entries.isEmpty()) {
            return "<li class='empty-recent'>" + escapeHtml(msg("menu.recent.empty", "Vacío")) + "</li>";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (File file : entries) {
            if (file == null || !file.exists()) continue;
            String name = file.getName();
            String path = file.getAbsolutePath();
            
            // Usamos un onclick para navegar desde el WebView (si lo soportamos) o simplemente mostramos
            // Por ahora solo visual.
            sb.append("<li title='").append(escapeHtml(path)).append("'>");
            sb.append("<span class='recent-name'>").append(escapeHtml(name)).append("</span>");
            sb.append("<span class='recent-path'>").append(escapeHtml(path)).append("</span>");
            sb.append("</li>");
            
            count++;
            if (count >= 5) break; // Máximo 5 elementos recientes
        }
        
        if (sb.length() == 0) {
            return "<li class='empty-recent'>" + escapeHtml(msg("menu.recent.empty", "Vacío")) + "</li>";
        }

        return sb.toString();
    }

    private String renderHomeReleaseNotesItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "<li>" + escapeHtml(msg("home.whatsNew.fallback", "No hay changelog disponible para esta versión.")) + "</li>";
        }

        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (item == null || item.isBlank()) continue;
            sb.append("<li>").append(escapeHtml(item)).append("</li>");
        }

        if (sb.length() == 0) {
            sb.append("<li>").append(escapeHtml(msg("home.whatsNew.fallback", "No hay changelog disponible para esta versión."))).append("</li>");
        }
        return sb.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
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

    private void applyEditorAppearanceToOpenTabs() {
        if (tabPane == null) return;
        for (Tab tab : tabPane.getTabs()) {
            CodeArea codeArea = fxUtils.getCodeArea(tab);
            if (codeArea != null) {
                fxUtils.applyEditorAppearance(codeArea);
            }
        }
    }

    private void applyApplicationAppearance(Scene scene, String appearance) {
        if (scene == null) return;
        String classicCss = resolveStylesheet(APP_STYLESHEET_CLASSIC);
        String darkCss = resolveStylesheet(APP_STYLESHEET_DARK);
        if (classicCss != null) scene.getStylesheets().remove(classicCss);
        if (darkCss != null) scene.getStylesheets().remove(darkCss);

        String activeCssPath = ConfigModel.EDITOR_APPEARANCE_CLASSIC.equalsIgnoreCase(appearance)
            ? APP_STYLESHEET_CLASSIC
            : APP_STYLESHEET_DARK;
        String activeCss = resolveStylesheet(activeCssPath);
        if (activeCss != null && !scene.getStylesheets().contains(activeCss)) {
            scene.getStylesheets().add(activeCss);
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

    private void hookHomeExternalLinks(Tab homeTab) {
        if (homeTab == null) return;
        if (!(homeTab.getContent() instanceof javafx.scene.web.WebView)) return;

        javafx.scene.web.WebView webView = (javafx.scene.web.WebView) homeTab.getContent();
        Object alreadyHooked = webView.getProperties().get(HOME_LINK_HOOKED);
        if (Boolean.TRUE.equals(alreadyHooked)) return;

        webView.getProperties().put(HOME_LINK_HOOKED, Boolean.TRUE);
        
        // Registrar el conector Java para JS
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine().executeScript("window");
                window.setMember("javaConnector", new HomeConnector());
            }
        });

        webView.getEngine().locationProperty().addListener((obs, oldLocation, newLocation) -> {
            if (newLocation == null) return;
            String lower = newLocation.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return;

            openInSystemBrowser(newLocation);
            Platform.runLater(() -> webView.getEngine().loadContent(buildHomeHtml(), "text/html"));
        });
    }

    /**
     * Clase interna para recibir llamadas desde el JS de la página Home.
     */
    public class HomeConnector {
        public void setShowHomeOnStartup(boolean show) {
            Platform.runLater(() -> {
                configModel.setShowHomeOnStartup(show);
                persistSessionState(); // Forzar guardado inmediato
                AppLogger.logConsole("HOME", "Preferencia de inicio actualizada: " + show);
            });
        }
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

        scene.getAccelerators().put(ACCEL_MOVE_UP, () -> editOptionsController.moveLinesUp(tabPane));
        scene.getAccelerators().put(ACCEL_MOVE_DOWN, () -> editOptionsController.moveLinesDown(tabPane));
        scene.getAccelerators().put(ACCEL_TOGGLE_COMMENT, () -> editOptionsController.toggleLineComments(tabPane));
        scene.getAccelerators().put(ACCEL_AUTO_COMPLETE, () -> editOptionsController.showAutoComplete(tabPane));
        scene.getAccelerators().put(ACCEL_FORMAT_CODE, () -> onFormatCode(null));

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
