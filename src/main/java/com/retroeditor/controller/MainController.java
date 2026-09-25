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
import java.util.function.Consumer;

import java.awt.Desktop;
import java.net.URI;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.kordamp.ikonli.javafx.FontIcon;
import javafx.event.ActionEvent;

import com.retroeditor.controller.projectExplorer.ProjectExplorerController;
import com.retroeditor.controller.config.ConfigDialogCoordinator;
import com.retroeditor.controller.editor.EditOptionsController;
import com.retroeditor.controller.editor.FileOptionsController;
import com.retroeditor.controller.editor.SnippetEditorDialog;
import com.retroeditor.controller.tools.TileEditorController;
import com.retroeditor.controller.tools.TilemapEditorController;
import com.retroeditor.controller.tools.StructureViewController;
import com.retroeditor.controller.tools.TodoViewController;
import com.retroeditor.controller.tools.PngToZxConverterController;
import com.retroeditor.controller.tools.PngToGameBoyConverterController;
import com.retroeditor.controller.tools.GameBoyAudioEditorController;
import com.retroeditor.controller.tools.TranslatorTabManager;
import com.retroeditor.controller.tools.GitHubRepoBrowserController;
import com.retroeditor.controller.terminal.TerminalController;
import com.retroeditor.controller.search.ProjectContextPort;
import com.retroeditor.controller.search.SearchController;
import com.retroeditor.controller.build.BuildController;
import com.retroeditor.controller.help.HelpController;
import com.retroeditor.controller.search.FileOpenPort;

import com.retroeditor.service.CompilationDiagnosticParserService;
import com.retroeditor.service.GccSyntaxCheckService;
import com.retroeditor.service.SnippetService;
import com.retroeditor.service.SnippetRepository;
import com.retroeditor.service.ExternalFileChangeMonitor;
import com.retroeditor.service.ExternalFileChangeService;
import com.retroeditor.service.LocalHistoryService;
import com.retroeditor.service.AutoSaveService;
import com.retroeditor.service.TodoScanService;
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
import com.retroeditor.service.TranslatorPersistenceService;
import com.retroeditor.service.CodeFormatterService;
import com.retroeditor.service.RenameRefactoringService;
import com.retroeditor.service.ExtractConstantService;
import com.retroeditor.service.ExtractFunctionService;
import com.retroeditor.model.WorkspaceModel;
import com.retroeditor.service.LanguageService;
import com.retroeditor.service.AppLogger;
import com.retroeditor.service.ThemeService;
import com.retroeditor.service.ThemeCssGenerator;
import com.retroeditor.model.EditorTheme;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.EditorModel;

import com.retroeditor.service.CodeFoldingService;
import com.retroeditor.service.git.GitService;
import com.retroeditor.service.git.GitModels;
import com.retroeditor.service.git.GitOperationsException;
import com.retroeditor.service.github.GitHubAuthService;
import com.retroeditor.service.github.GitHubReleaseService;
import com.retroeditor.service.github.GitHubIssueService;
import com.retroeditor.service.github.GitHubShareService;

import com.retroeditor.util.CompilationDiagnosticsUiHelper;
import com.retroeditor.util.FXUtils;
import com.retroeditor.util.FoldingCodeArea;
import com.retroeditor.util.TabPaneDragSupport;
import com.retroeditor.util.SnippetTabStopNavigator;
import com.retroeditor.util.AppInfo;
import com.retroeditor.util.CommandPalette;

import com.retroeditor.view.UIElements;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.Node;

import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

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
import javafx.scene.control.TextInputDialog;

import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.flowless.VirtualizedScrollPane;
import com.retroeditor.util.GitDiffViewer;

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
    private final ThemeService themeService  = new ThemeService();

    private final EditOptionsController   editOptionsController   = new EditOptionsController();
    private final FileOptionsController   fileOptionsController   = new FileOptionsController();
    private final TilemapEditorController tilemapEditorController = new TilemapEditorController();
    private final PngToZxConverterController pngToZxConverterController = new PngToZxConverterController();
    private final PngToGameBoyConverterController pngToGameBoyConverterController = new PngToGameBoyConverterController();
    private final GameBoyAudioEditorController    gameBoyAudioEditorController    = new GameBoyAudioEditorController();
    private final TranslatorTabManager            translatorTabManager            = new TranslatorTabManager();
    private final GitHubRepoBrowserController     gitHubRepoBrowserController     = new GitHubRepoBrowserController();
    private final GitPanelController              gitPanelController              = new GitPanelController();
    private final GitHubShareService              gitHubShareService              = new GitHubShareService();
    private final GitHubReleaseService            gitHubReleaseService            = new GitHubReleaseService();
    private final GitService                      gitService                       = new GitService();
    private final GitHubIssueService              gitHubIssueService                = new GitHubIssueService();
    private final TerminalController      terminalController      = new TerminalController();
    private final BuildController         buildController         = new BuildController();
    private final HelpController          helpController          = new HelpController();
    private final ConfigDialogCoordinator configDialogCoordinator = new ConfigDialogCoordinator();
    private final CompilationDiagnosticParserService compilationDiagnosticParserService = new CompilationDiagnosticParserService();
    private final CompilationDiagnosticsUiHelper compilationDiagnosticsUiHelper = new CompilationDiagnosticsUiHelper();
    private final GccSyntaxCheckService gccSyntaxCheckService = new GccSyntaxCheckService();
    private final ConfigRepository configRepository = new PropertiesConfigRepository();
    private final WorkspaceService workspaceService = new WorkspaceService();
    private final PluginApplicationService pluginApplicationService = new PluginApplicationService();
    private final ProjectPlatformDetectionService projectPlatformDetectionService = new ProjectPlatformDetectionService();
    private final ProjectSearchService projectSearchService = new ProjectSearchService();
    private final TextSearchService textSearchService = new TextSearchService();
    private final RenameRefactoringService renameRefactoringService = new RenameRefactoringService();
    private final ExtractConstantService extractConstantService = new ExtractConstantService();
    private final ExtractFunctionService extractFunctionService = new ExtractFunctionService();
    private final FileTemplateService fileTemplateService = new FileTemplateService();
    private final HomeReleaseNotesService homeReleaseNotesService = new HomeReleaseNotesService();
    private final SnippetService snippetService = new SnippetService();
    private final SnippetRepository snippetRepository = new SnippetRepository(configRepository, SnippetRepository.defaultFile());
    private final List<SnippetService.NamedSnippet> snippets = new java.util.ArrayList<>();

    private final ScheduledExecutorService autosaveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "samaruc-autosave");
        t.setDaemon(true);
        return t;
    });
    private static final long AUTOSAVE_INTERVAL_SECONDS = 30;

    private int newFileCounter = 1;

    private ResourceBundle bundle;
    private EditorModel editorModel;
    private File currentProjectDir;
    private Tab gitPanelTab;

    private SyntaxHighlighter syntaxHighlighter;
    private ProjectExplorerController projectExplorerController;
    private SearchController searchController;
    private boolean isRefreshingLanguage = false;

    private ExternalFileChangeMonitor externalFileChangeMonitor;
    private javafx.animation.Timeline externalFileWatcherTimer;
    private File pendingExternalChangeFile;

    @FXML private MenuBar menuBar;
    @FXML private Label lblFooter;
    @FXML private Label lblCompileStatus;
    @FXML private Label lblCursorPos;
    @FXML private Label lblGitBranch;
    @FXML private Label lblOpStatus;
    @FXML private Label lblExternalChange;
    @FXML private javafx.scene.control.ProgressIndicator progressCompile;
    @FXML private Menu menuArchivo;
    @FXML private Menu menuEdicion;
    @FXML private Menu menuRefactor;
    @FXML private Menu menuHerramientas;
    @FXML private Menu menuCompilacion;
    @FXML private Menu menuGit;
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
    @FXML private Button btnGit;
    @FXML private MenuItem menuItemPegar;
    @FXML private MenuItem menuItemRename;
    @FXML private MenuItem menuItemRenameSymbol;
    @FXML private MenuItem menuItemExtractConstant;
    @FXML private MenuItem menuItemExtractFunction;
    @FXML private MenuItem menuItemTileEditor;
    @FXML private MenuItem menuItemTilemapEditor;
    @FXML private MenuItem menuItemRomTextTranslator;
    @FXML private MenuItem menuItemGitHubRepos;
    @FXML private MenuItem menuItemGitPanel;
    @FXML private MenuItem menuItemViewDiff;
    @FXML private MenuItem menuItemShareToGithub;
    @FXML private MenuItem menuItemPublishRelease;
    @FXML private MenuItem menuItemIssues;
    @FXML private MenuItem menuItemPngToZx;
    @FXML private MenuItem menuItemGoToLine;
    @FXML private MenuItem menuItemFind;
    @FXML private MenuItem menuItemFindProject;
    @FXML private Menu menuSnippets;
    @FXML private MenuItem menuItemInsertSnippet;
    @FXML private MenuItem menuItemManageSnippets;
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
    @FXML private MenuItem menuItemNuevoTraduccion;
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
    @FXML private AnchorPane todoViewAnchor;
    private TodoViewController todoViewController;
    private final TodoScanService todoScanService = new TodoScanService();
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
    private static final String TAB_PROP_GIT_DIRTY       = "git.dirtyDot";
    private static final String TAB_PROP_TILE_CONTROLLER = "tileEditor.controller";
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
    private static final KeyCombination ACCEL_RENAME_SYMBOL = new KeyCodeCombination(KeyCode.F2);
    private static final KeyCombination ACCEL_FOLD          = new KeyCodeCombination(KeyCode.OPEN_BRACKET, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination ACCEL_UNFOLD        = new KeyCodeCombination(KeyCode.CLOSE_BRACKET, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination ACCEL_BUILD        = new KeyCodeCombination(KeyCode.F5);
    private static final KeyCombination ACCEL_RUN          = new KeyCodeCombination(KeyCode.F6);
    private static final KeyCombination ACCEL_COMMAND_PALETTE = new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination ACCEL_QUICK_OPEN   = new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN);

    @FXML
    public void initialize() {
        // Cargar configuración del usuario (incluye toolchains de usuario)
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
        fxUtils.setEditorThemeApplier(codeArea -> {
            String activeId = themeService.resolveActiveThemeId(configModel);
            String css = ThemeCssGenerator.generateCss(themeService.getThemeOrDefault(activeId));
            fxUtils.applyGeneratedEditorTheme(codeArea, css);
        });
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
                    gitPanelController.setBundle(bundle);
                    gitPanelController.refreshLanguage();
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
        configModel.editorThemeProperty().addListener((obs, oldV, newV) -> {
            Platform.runLater(() -> {
                applyEditorThemeToOpenTabs(newV);
                if (tabPane != null && tabPane.getScene() != null) {
                    applyApplicationAppearance(tabPane.getScene(), themeToShellAppearance(newV));
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
            TabPaneDragSupport.install(tabPane);
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                applyDiagnosticsForTab(newTab);
                persistSessionState();
                if (structureViewController != null) {
                    structureViewController.setActiveCodeArea(fxUtils.getCurrentCodeArea(tabPane));
                }
                fxUtils.updateCursorStatus(tabPane);
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

        // Cargar TodoView (TODO/FIXME del proyecto)
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/TodoView.fxml"));
            Parent todoView = loader.load();
            todoViewController = loader.getController();
            if (todoViewAnchor != null) {
                todoViewAnchor.getChildren().setAll(todoView);
            }
            if (todoViewController != null) {
                todoViewController.setOnTodoSelectedCallback(this::openTodoItem);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Restaurar el último proyecto/archivos al arrancar (la Home se abre
        // solo si no hay nada que restaurar y la preferencia está activada).
        Platform.runLater(this::restoreSessionState);

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

        // Panel Git: se abre bajo demanda como pestaña del editor principal
        gitPanelController.setBundle(bundle);
        gitPanelController.setOnDirtyPathsChanged(this::updateGitDirtyTabMarkers);
        gitPanelController.setOnStatusChanged(this::updateGitStatusLabel);
        gitPanelController.setOperationListener(this::showOperationStatus);
        gitPanelController.setGithubTokenSupplier(
            () -> configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));

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
        if (btnGit != null) {
            btnGit.setGraphic(new FontIcon("fas-code-branch"));
            btnGit.setTooltip(new Tooltip(msg("tooltip.git.openPanel", "Abrir panel Git")));
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

            Button[] tbButtons = new Button[] { btnNuevoProyecto, btnAbrirProyecto, btnNuevo, btnOpen, btnSave, btnClose, btnUndo, btnRedo, btnFind, btnFindProject, btnCut, btnCopy, btnPaste, btnCompilar, btnCancelarCompilacion, btnEjecutar, btnConfig, btnManual, btnGit };
            for (Button b : tbButtons) {
                if (b == null) continue;
                b.getStyleClass().add("tb-button");
                if (b.getGraphic() instanceof FontIcon) {
                    FontIcon fi = (FontIcon) b.getGraphic();
                    try { fi.setIconSize(16); } catch (Exception ignore) {}
                }
            }
        } catch (Exception ignored) {}

        // Menús y textos
        if (menuArchivo           != null) menuArchivo.setText(bundle.getString("menu.file"));
        if (menuCompilacion       != null) menuCompilacion.setText(bundle.getString("menu.project"));
        if (menuEdicion           != null) menuEdicion.setText(bundle.getString("menu.edit"));
        if (menuRefactor          != null) menuRefactor.setText(bundle.getString("menu.refactor"));
        if (menuHerramientas      != null) menuHerramientas.setText(bundle.getString("menu.tools"));
        if (menuGit               != null) menuGit.setText(bundle.getString("menu.git"));
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
        if (menuItemRenameSymbol != null) menuItemRenameSymbol.setText(bundle.getString("menu.rename.symbol"));
        if (menuItemExtractConstant != null) menuItemExtractConstant.setText(bundle.getString("menu.refactor.extractConstant"));
        if (menuItemExtractFunction != null) menuItemExtractFunction.setText(bundle.getString("menu.refactor.extractFunction"));
        if (menuItemTileEditor    != null) menuItemTileEditor.setText(bundle.getString("menu.tools.tileEditor"));
        if (menuItemTilemapEditor != null) menuItemTilemapEditor.setText(bundle.getString("menu.tools.tilemapEditor"));
        if (menuItemGoToLine      != null) menuItemGoToLine.setText(bundle.getString("menu.goto.line"));
        if (menuItemFind          != null) menuItemFind.setText(bundle.getString("menu.find.file"));
        if (menuItemFindProject   != null) menuItemFindProject.setText(bundle.getString("menu.find.project"));
        if (menuSnippets          != null) menuSnippets.setText(bundle.getString("menu.edit.snippets"));
        if (menuItemInsertSnippet != null) menuItemInsertSnippet.setText(bundle.getString("menu.edit.snippets.insert"));
        if (menuItemManageSnippets != null) menuItemManageSnippets.setText(bundle.getString("menu.edit.snippets.manage"));
        if (menuItemManual        != null) menuItemManual.setText(bundle.getString("menu.help.manual"));
        if (menuItemGbdkDocs      != null) menuItemGbdkDocs.setText(bundle.getString("menu.help.gbdkDocs"));
        if (menuItemZ88dkDocs     != null) menuItemZ88dkDocs.setText(bundle.getString("menu.help.z88dkDocs"));
        if (menuItemPluginManual  != null) menuItemPluginManual.setText(bundle.getString("menu.help.pluginsManual"));
        if (menuItemLicenses      != null) menuItemLicenses.setText(bundle.getString("menu.help.licenses"));
        if (menuItemCreditos      != null) menuItemCreditos.setText(bundle.getString("menu.help.credits"));
        
        if (lblFooter != null) {
            lblFooter.setText("Desarrollado por Andres Gimenez © 2025 - v" + AppInfo.getVersion());
        }
        if (lblCursorPos != null) {
            fxUtils.setCursorStatusLabel(lblCursorPos);
        }

        updateOutputTabsI18n();
        applyDiagnosticLegendI18n();

        applyMenuAccelerators();
        applyShortcutTooltips();

        // Attach stylesheet to the Scene according to the configured appearance
        Platform.runLater(() -> {
            try {
                if (tabPane != null && tabPane.getScene() != null) {
                    applyApplicationAppearance(tabPane.getScene(), themeToShellAppearance(null));
                }
            } catch (Exception ignored) {}
        });
        Platform.runLater(() -> applyEditorThemeToOpenTabs());

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

        startExternalFileWatcher();
        scheduleAutosave();
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
        FoldingCodeArea.expandAllFolds(codeArea);
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
            TabPaneDragSupport.install(secondaryTabPane);

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
            // Devolver todas las pestañas del panel secundario al principal
            // antes de descartarlo (evita que queden huérfanas).
            if (secondaryTabPane != null && tabPane != null) {
                for (Tab tab : new ArrayList<>(secondaryTabPane.getTabs())) {
                    secondaryTabPane.getTabs().remove(tab);
                    tabPane.getTabs().add(tab);
                }
            }
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

    /**
     * Decisión de sincronización antes de compilar/ejecutar.
     */
    private enum BuildSync { PROCEED_UNCHANGED, PROCEED_SYNCED, ABORT }

    /**
     * Acción elegida por el usuario ante un cambio externo detectado.
     */
    private enum ExternalChangeAction { RELOAD, OVERWRITE, DISK_ONLY, CANCEL }

    /**
     * Muestra el diálogo de confirmación ante un cambio externo en disco.
     *
     * @param fileName nombre del archivo afectado
     * @param forSave {@code true} si el diálogo se muestra antes de guardar
     *     (única opción «conservar» = sobrescribir); {@code false} si se muestra
     *     antes de compilar/ejecutar (añade la opción «usar la versión de disco»)
     * @return la acción elegida, o {@link Optional#empty()} si se cerró el diálogo
     */
    private Optional<ExternalChangeAction> askExternalChangeAction(String fileName, boolean forSave) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        Stage owner = getStage();
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(msg("external.change.title", "El archivo ha cambiado en disco"));
        alert.setHeaderText(msg("external.change.header", "Cambio externo detectado"));
        alert.setContentText(msgFmt(
            "external.change.content",
            "El archivo ''{0}'' fue modificado fuera del editor (p. ej. por opencode).\n¿Cómo quieres continuar?",
            fileName
        ));

        ButtonType reload = new ButtonType(msg("external.change.reload", "Recargar desde disco"), ButtonBar.ButtonData.OK_DONE);
        ButtonType overwrite = new ButtonType(msg("external.change.keep", "Conservar mi versión del editor"), ButtonBar.ButtonData.OTHER);
        ButtonType diskOnly = forSave ? null
            : new ButtonType(msg("external.change.diskOnly", "No tocar: usar la versión de disco"), ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType(msg("dialog.template.cancel", "Cancelar"), ButtonBar.ButtonData.CANCEL_CLOSE);

        if (forSave) {
            alert.getButtonTypes().setAll(reload, overwrite, cancel);
        } else {
            alert.getButtonTypes().setAll(reload, overwrite, diskOnly, cancel);
        }

        return alert.showAndWait().map(button -> {
            if (button == reload) return ExternalChangeAction.RELOAD;
            if (button == overwrite) return ExternalChangeAction.OVERWRITE;
            if (diskOnly != null && button == diskOnly) return ExternalChangeAction.DISK_ONLY;
            return ExternalChangeAction.CANCEL;
        });
    }

    /**
     * Recarga una pestaña desde disco y actualiza la huella registrada.
     *
     * @return {@code true} si la recarga tuvo éxito
     */
    private boolean reloadTabFromDisk(Tab tab, File file, CodeArea codeArea) {
        try {
            editorModel.openFile(file);
        } catch (IOException ex) {
            terminalController.appendConsoleOutput("No se pudo recargar el archivo desde disco: " + ex.getMessage(), consoleOutputArea);
            return false;
        }
        String fresh = editorModel.getFileContent(file);
        String reloaded = fresh != null ? fresh : "";
        codeArea.replaceText(reloaded);
        fxUtils.markTabSaved(tab, fxUtils.getDefaultFileName(tab), reloaded);
        terminalController.appendConsoleOutput("Archivo recargado desde disco: " + file.getName(), consoleOutputArea);
        return true;
    }

    /**
     * Detecta cambios externos en el archivo de la pestaña activa antes de
     * compilar o ejecutar. Si el archivo en disco se modificó fuera del editor
     * (p. ej. por opencode), pregunta al usuario cómo continuar.
     *
     * @return {@link BuildSync#PROCEED_UNCHANGED} si no hay conflicto y el flujo
     *     normal (guardar + compilar) puede continuar; {@link BuildSync#PROCEED_SYNCED}
     *     si el conflicto ya se resolvió (recargado, sobrescrito o «usar disco»)
     *     y debe omitirse el guardado automático; {@link BuildSync#ABORT} para
     *     cancelar la operación.
     */
    private BuildSync ensureFileSyncedBeforeBuild() {
        Tab tab = fxUtils.getSelectedTab(tabPane);
        if (tab == null) return BuildSync.PROCEED_UNCHANGED;
        File file = tabFileMap.get(tab);
        if (file == null) return BuildSync.PROCEED_UNCHANGED;
        CodeArea codeArea = fxUtils.getCodeArea(tab);
        if (codeArea == null) return BuildSync.PROCEED_UNCHANGED;

        String registered = fxUtils.getDiskFingerprint(tab);
        if (registered == null) return BuildSync.PROCEED_UNCHANGED;
        if (!ExternalFileChangeService.hasExternalChange(file, registered)) {
            return BuildSync.PROCEED_UNCHANGED;
        }

        Optional<ExternalChangeAction> decision = askExternalChangeAction(file.getName(), false);
        if (decision.isEmpty()) return BuildSync.ABORT;

        return switch (decision.get()) {
            case RELOAD -> reloadTabFromDisk(tab, file, codeArea) ? BuildSync.PROCEED_SYNCED : BuildSync.ABORT;
            case OVERWRITE -> {
                boolean saved = fileOptionsController.onSaveFile(null, tabPane, tabFileMap, editorModel);
                if (!saved) {
                    terminalController.appendConsoleOutput("No se pudo guardar la versión del editor sobre el disco.", consoleOutputArea);
                    yield BuildSync.ABORT;
                }
                terminalController.appendConsoleOutput("Versión del editor guardada sobre el disco: " + file.getName(), consoleOutputArea);
                yield BuildSync.PROCEED_SYNCED;
            }
            case DISK_ONLY -> {
                terminalController.appendConsoleOutput("Se compilará la versión del disco (el editor no se modifica): " + file.getName(), consoleOutputArea);
                yield BuildSync.PROCEED_SYNCED;
            }
            case CANCEL -> BuildSync.ABORT;
        };
    }

    @FXML
    private void onCompilar(ActionEvent event) {
        BuildSync sync = ensureFileSyncedBeforeBuild();
        if (sync == BuildSync.ABORT) return;
        if (sync == BuildSync.PROCEED_UNCHANGED) {
            boolean saved = fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel);
            if (!saved) {
                terminalController.appendConsoleOutput("No se puede compilar: el archivo no está guardado.", consoleOutputArea);
                return;
            }
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
        BuildSync sync = ensureFileSyncedBeforeBuild();
        if (sync == BuildSync.ABORT) return;
        if (sync == BuildSync.PROCEED_UNCHANGED) {
            boolean saved = fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel);
            if (!saved) {
                terminalController.appendConsoleOutput("No se puede compilar: el archivo no está guardado.", consoleOutputArea);
                return;
            }
        }
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
        FoldingCodeArea.expandAllFolds(codeArea);
        String formatted = CodeFormatterService.formatCode(codeArea.getText(), fileName);
        codeArea.replaceText(formatted);
    }

    @FXML
    private void onNuevoProyecto(ActionEvent event) {
        File creado = fxUtils.createNewProject(getStage(), bundle, resolveInitialProjectDirectory());
        if (creado != null) {
            if (fxUtils.wasLastCreatedProjectGameBoyTemplate() || fxUtils.wasLastCreatedProjectTranslatorTemplate()) {
                configModel.applyGameBoyProfile();
                configRepository.save(userConfigFile, configModel.toProperties());
            } else if (fxUtils.wasLastCreatedProjectSpectrumTemplate()) {
                configModel.applySpectrumProfile();
                configRepository.save(userConfigFile, configModel.toProperties());
            } else if (fxUtils.wasLastCreatedProjectCc65Template()) {
                configModel.applyAtariProfile();
                configRepository.save(userConfigFile, configModel.toProperties());
            } else if (fxUtils.wasLastCreatedProjectCc65C64Template()) {
                configModel.applyC64Profile();
                configRepository.save(userConfigFile, configModel.toProperties());
            }

            UserActionMonitor.projectCreated(creado.getAbsolutePath());
            setProyectoActual(creado);

            File defaultMainFile = new File(creado, "src/main.c");
            if (defaultMainFile.isFile()) {
                openFileFromExplorer(defaultMainFile);
            } else {
                File translatorFile = new File(creado, "translation.smt");
                if (translatorFile.isFile()) {
                    openFileFromExplorer(translatorFile);
                }
            }
        } else {
            if (fxUtils.wasLastCreatedProjectGitHubTemplate()) {
                onOpenGitHubRepoBrowser(null);
                return;
            }
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
                || (detected.getPlatform() == ProjectPlatformDetectionService.Platform.SPECTRUM && isCurrentProfileSpectrum())
                || (detected.getPlatform() == ProjectPlatformDetectionService.Platform.ATARI && isCurrentProfileAtari())
                || (detected.getPlatform() == ProjectPlatformDetectionService.Platform.C64 && isCurrentProfileC64());

        if (profileAlreadyMatches) return;

        String detectedProfileName;
        if (detected.getPlatform() == ProjectPlatformDetectionService.Platform.GAMEBOY) {
            detectedProfileName = msg("dialog.project.detected.profile.gameboy", "Game Boy (GBDK)");
        } else if (detected.getPlatform() == ProjectPlatformDetectionService.Platform.ATARI) {
            detectedProfileName = msg("dialog.project.detected.profile.atari", "Atari 8-bit (CC65)");
        } else if (detected.getPlatform() == ProjectPlatformDetectionService.Platform.C64) {
            detectedProfileName = msg("dialog.project.detected.profile.c64", "Commodore 64 (CC65)");
        } else {
            detectedProfileName = msg("dialog.project.detected.profile.spectrum", "ZX Spectrum (z88dk)");
        }

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
        } else if (detected.getPlatform() == ProjectPlatformDetectionService.Platform.ATARI) {
            configModel.applyAtariProfile();
        } else if (detected.getPlatform() == ProjectPlatformDetectionService.Platform.C64) {
            configModel.applyC64Profile();
        } else {
            configModel.applySpectrumProfile();
        }
        configRepository.save(userConfigFile, configModel.toProperties());
    }

    private boolean isCurrentProfileC64() {
        if (!"cc65".equalsIgnoreCase(configModel.getConfigProperty("compilador_seleccionado", "GBDK"))) return false;
        return "c64".equalsIgnoreCase(configModel.getConfigProperty("cc65_target", "atari"));
    }

    private boolean isCurrentProfileGameBoy() {
        return "GBDK".equalsIgnoreCase(configModel.getConfigProperty("compilador_seleccionado", "GBDK"));
    }

    private boolean isCurrentProfileSpectrum() {
        String compiler = configModel.getConfigProperty("compilador_seleccionado", "GBDK");
        return "z88dk".equalsIgnoreCase(compiler) || "Spectrum".equalsIgnoreCase(compiler);
    }

    private boolean isCurrentProfileAtari() {
        String compiler = configModel.getConfigProperty("compilador_seleccionado", "GBDK");
        return "cc65".equalsIgnoreCase(compiler);
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
        refreshTodoPanel();
        gitPanelController.setProject(carpeta);
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

    /**
     * Escanea el proyecto actual en busca de comentarios TODO/FIXME y actualiza
     * el panel de tareas. La búsqueda se hace en segundo plano para no
     * bloquear el hilo de JavaFX.
     */
    private void refreshTodoPanel() {
        if (todoViewController == null) return;
        File project = currentProjectDir;
        CompletableFuture.runAsync(() -> {
            List<TodoScanService.TodoItem> items = project != null
                    ? todoScanService.scanProject(project)
                    : java.util.List.of();
            Platform.runLater(() -> {
                if (todoViewController != null) {
                    todoViewController.setItems(items);
                }
            });
        });
    }

    /** Abre el archivo de un TODO en la línea indicada al hacer doble clic. */
    private void openTodoItem(File file, int line) {
        if (file == null) return;
        try {
            boolean alreadyOpen = findTabForFile(file) != null;
            if (!alreadyOpen) {
                openFileInEditorTab(file, false);
            }
            CodeArea area = fxUtils.getCurrentCodeArea(tabPane);
            if (area != null) {
                try {
                    int targetOffset = area.position(Math.max(0, line - 1), 0).toOffset();
                    area.moveTo(targetOffset);
                    area.requestFollowCaret();
                    area.requestFocus();
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ex) {
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
        if (TileEditorController.isTileFile(file)) {
            Tab existing = findTileTabForFile(file);
            if (existing != null) {
                tabPane.getSelectionModel().select(existing);
                return;
            }
            openTileEditorTab(file);
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

        if (TranslatorPersistenceService.isSmtFile(file)) {
            Tab translatorTab = translatorTabManager.openTranslatorTab(file, fxUtils, tabPane, bundle);
            if (translatorTab != null) {
                tabFileMap.put(translatorTab, file.getAbsoluteFile());
            }
            persistSessionState();
            return;
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
            persistSessionState();

            // Antes de cerrar con la X integrada, si la pestaña está sucia y el
            // archivo cambió externamente, pregunta (Recargar/Conservar/Cancelar)
            // en vez de descartar el buffer o sobrescribir la versión de disco.
            tab.setOnCloseRequest(e -> {
                if (!confirmCloseEditorTab(tab)) {
                    e.consume();
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openFileInEditorTab(File file, boolean forceSingleByteText) throws IOException {
        if (TranslatorPersistenceService.isSmtFile(file)) {
            Tab translatorTab = translatorTabManager.openTranslatorTab(file, fxUtils, tabPane, bundle);
            if (translatorTab != null) {
                tabFileMap.put(translatorTab, file.getAbsoluteFile());
            }
            persistSessionState();
            return;
        }

        if (TileEditorController.isTileFile(file)) {
            openTileEditorTab(file);
            return;
        }

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

        // Antes de cerrar con la X integrada, si la pestaña está sucia y el
        // archivo cambió externamente, pregunta (Recargar/Conservar/Cancelar)
        // en vez de descartar o sobrescribir en silencio.
        tab.setOnCloseRequest(e -> {
            if (!confirmCloseEditorTab(tab)) {
                e.consume();
            }
        });

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

    private void openHomeTab() {
        if (tabPane == null) return;
        try {
            Tab homeTab = fxUtils.addHomeTabContent(buildHomeHtml(), tabPane, getHomeTabTitle(), HOME_TAB_ID);
            hookHomeExternalLinks(homeTab);
        } catch (Throwable ignored) {
            fxUtils.addNewTab(null, "", tabFileMap, syntaxHighlighter, tabPane, newFileCounter);
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
        // Leer el estado persistido ANTES de tocar el proyecto o las pestañas:
        // setProyectoActual()/abrir ficheros llaman a persistSessionState(), que
        // de lo contrario sobrescribiría estos valores y rompería la restauración.
        File sessionProject = resolveStoredSessionProject();
        List<File> filesToRestore = loadSessionOpenFiles();
        String activeFilePath = configModel.getConfigProperty(LAST_SESSION_ACTIVE_FILE_PROPERTY, "");

        try {
            if (sessionProject != null) {
                setProyectoActual(sessionProject);
                maybeApplyDetectedProfileForOpenedProject(sessionProject);
            }

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

        // La pantalla de inicio solo se abre si no se ha restaurado ni proyecto
        // ni archivos y la preferencia del usuario está activada.
        boolean restoredSomething = sessionProject != null || !filesToRestore.isEmpty();
        if (tabPane.getTabs().isEmpty() && !restoredSomething && configModel.isShowHomeOnStartup()) {
            openHomeTab();
        }
        persistSessionState();

        promptAutosaveRecovery();
    }

    /**
     * Programa el volcado periódico de las pestañas modificadas a la carpeta
     * de autosave. Se ejecuta en FX thread para leer los CodeArea y escribe
     * los snapshot en un hilo de fondo.
     */
    private void scheduleAutosave() {
        autosaveScheduler.scheduleWithFixedDelay(() -> {
            try {
                Platform.runLater(this::autosaveDirtyTabs);
            } catch (Throwable ignored) {
            }
        }, AUTOSAVE_INTERVAL_SECONDS, AUTOSAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void autosaveDirtyTabs() {
        if (tabPane == null) return;
        expandAllFoldsInTabs();
        List<Map.Entry<File, String>> dirty = new ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            File file = tabFileMap.get(tab);
            if (file == null || !file.isFile()) continue;
            CodeArea area = fxUtils.getCodeArea(tab);
            if (area == null) continue;
            String text = area.getText();
            if (text == null) continue;
            try {
                String disk = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (!text.equals(disk)) {
                    dirty.add(new java.util.AbstractMap.SimpleEntry<>(file, text));
                }
            } catch (IOException ignored) {
            }
        }
        if (dirty.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            for (Map.Entry<File, String> entry : dirty) {
                AutoSaveService.saveSnapshot(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Expande todos los plegados activos de todas las pestañas de código antes de
     * que cualquier flujo externo lea el buffer (guardar, autosave, formato,
     * compilar, renombrar...), evitando persistir la forma plegada.
     */
    private void expandAllFoldsInTabs() {
        if (tabPane == null) return;
        for (Tab tab : tabPane.getTabs()) {
            CodeArea area = fxUtils.getCodeArea(tab);
            FoldingCodeArea.expandAllFolds(area);
        }
    }

    /**
     * Si hay snapshots de autosave pendientes de una sesión anterior, pregunta
     * al usuario si quiere recuperarlos o descartarlos.
     */
    private void promptAutosaveRecovery() {
        if (tabPane == null) return;
        List<AutoSaveService.RecoverableFile> recoverable = AutoSaveService.listRecoverable();
        if (recoverable.isEmpty()) return;

        String title   = msg("autosave.recovery.title", "Recuperación de archivos");
        String header  = msgFmt("autosave.recovery.header",
                "Se han recuperado {0} archivos con cambios sin guardar de una sesión anterior.",
                recoverable.size());
        String restoreBtn   = msg("autosave.recovery.restore", "Recuperar");
        String discardBtn   = msg("autosave.recovery.discard", "Descartar");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        StringBuilder content = new StringBuilder();
        for (AutoSaveService.RecoverableFile r : recoverable) {
            content.append("• ").append(r.getOriginalFile().getName()).append('\n');
        }
        alert.setContentText(content.toString().trim());
        alert.initOwner(tabPane.getScene() != null ? tabPane.getScene().getWindow() : null);

        ButtonType restoreType = new ButtonType(restoreBtn);
        ButtonType discardType = new ButtonType(discardBtn);
        alert.getButtonTypes().setAll(restoreType, discardType);

        if (alert.showAndWait().orElse(discardType) == restoreType) {
            for (AutoSaveService.RecoverableFile r : recoverable) {
                restoreAutosavedFile(r);
            }
        }
        AutoSaveService.clearAll();
    }

    private void restoreAutosavedFile(AutoSaveService.RecoverableFile recoverable) {
        File original = recoverable.getOriginalFile();
        String snapshot = AutoSaveService.loadSnapshotContent(recoverable.getSnapshotFile());
        if (snapshot.isEmpty() && original == null) return;

        Tab tab = findTabForFile(original);
        if (tab == null && original != null && original.exists() && original.isFile()) {
            try {
                openFileInEditorTab(original, false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            tab = findTabForFile(original);
        }
        if (tab == null) return;
        CodeArea area = fxUtils.getCodeArea(tab);
        if (area != null) {
            area.replaceText(snapshot);
        }
    }

    private Tab findTabForFile(File file) {
        if (file == null || tabFileMap.isEmpty()) return null;
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            if (sameFile(entry.getValue(), file)) {
                return entry.getKey();
            }
        }
        return null;
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

    /**
     * Diagnóstico al guardar (item 14): tras guardar un archivo de código con GCC
     * configurado, ejecuta {@code gcc -fsyntax-only} sobre ese único archivo en
     * background y subraya los errores/warnings en la pestaña, sin abrir la consola.
     */
    private void runSaveTimeSyntaxCheck(File file) {
        if (file == null || configModel == null || !configModel.isSyntaxCheckOnSave()) return;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".c") && !name.endsWith(".h")) return;

        String selectedCompiler = configModel.getConfigProperty("compilador_seleccionado", "");
        if (!gccSyntaxCheckService.isApplicable(selectedCompiler)) return;
        if (buildController != null && buildController.isCompilationRunning()) return;

        Thread worker = new Thread(() -> {
            List<String> command = gccSyntaxCheckService.buildSyntaxCheckCommand(
                configModel.getConfigProperty("gcc_bin", ""),
                configModel.getConfigProperty("gcc_opt_level", "none"),
                configModel.getConfigProperty("gcc_defines", ""),
                configModel.getConfigProperty("gcc_includes", ""),
                configModel.getConfigProperty("gcc_extra_args", ""),
                file
            );
            gccSyntaxCheckService.run(command, file.getParentFile(), line -> onSyntaxCheckOutputLine(file, line));
        }, "gcc-syntax-check");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Procesa una línea del parse de sintaxis al guardar: la inserta en
     * {@code compileDiagnosticsByFile} y subraya la pestaña correspondiente,
     * sin alterar el estado de compilación ni la consola.
     */
    private void onSyntaxCheckOutputLine(File sourceFile, String outputLine) {
        if (outputLine == null || outputLine.isBlank()) return;

        CompilationDiagnosticParserService.DiagnosticLocation location =
            compilationDiagnosticParserService.parseCompilerDiagnosticLocation(sourceFile, outputLine, this::resolveConsoleFileReference);
        if (location == null || location.getLine() <= 0) return;

        Platform.runLater(() -> {
            File key = toFileKey(location.getFile());
            CompileLineDiagnostics diagnostics = compileDiagnosticsByFile.computeIfAbsent(key, k -> new CompileLineDiagnostics());
            if (location.isWarning()) {
                diagnostics.warnings.add(location.getLine());
            } else {
                diagnostics.errors.add(location.getLine());
            }

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
            themeService,
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
            () -> {
                String activeId = themeService.resolveActiveThemeId(configModel);
                applyEditorThemeToOpenTabs(activeId);
                if (tabPane != null && tabPane.getScene() != null) {
                    applyApplicationAppearance(tabPane.getScene(), themeToShellAppearance(activeId));
                }
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

        NewFileChoice choice = askNewFileChoice();
        if (choice == null) return;

        String baseName = "nuevo" + newFileCounter;
        switch (choice) {
            case TEXT -> fxUtils.addNewTabNamed(null, "", baseName + ".txt", tabFileMap, syntaxHighlighter, tabPane);
            case C -> fxUtils.addNewTabNamed(null, "", baseName + ".c", tabFileMap, syntaxHighlighter, tabPane);
            case SPRITE -> openTileEditorTab(null, baseName + ".tile");
        }
        newFileCounter++;
    }

    private enum NewFileChoice { TEXT, C, SPRITE }

    /**
     * Pregunta qué tipo de archivo crear: texto, C o sprite (.tile).
     *
     * @return la opción elegida, o {@code null} si se cancela.
     */
    private NewFileChoice askNewFileChoice() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        Stage owner = getStage();
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(msg("dialog.newFile.title", "Nuevo archivo"));
        alert.setHeaderText(msg("dialog.newFile.header", "¿Qué tipo de archivo quieres crear?"));
        alert.setContentText(null);

        ButtonType text = new ButtonType(msg("dialog.newFile.text", "Archivo de texto (.txt)"), ButtonBar.ButtonData.OK_DONE);
        ButtonType c = new ButtonType(msg("dialog.newFile.c", "Archivo C (.c)"), ButtonBar.ButtonData.OK_DONE);
        ButtonType sprite = new ButtonType(msg("dialog.newFile.sprite", "Sprite (.tile)"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(msg("dialog.template.cancel", "Cancelar"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(text, c, sprite, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty()) return null;
        if (result.get() == text) return NewFileChoice.TEXT;
        if (result.get() == c) return NewFileChoice.C;
        if (result.get() == sprite) return NewFileChoice.SPRITE;
        return null;
    }

    @FXML
    private void onNewTranslatorFile(ActionEvent event) {
        if (this.currentProjectDir == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(bundle.getString("alert.project.notOpen.title"));
            alert.setHeaderText(null);
            alert.setContentText(bundle.getString("alert.project.notOpen.content"));
            alert.showAndWait();
            return;
        }
        Tab translatorTab = translatorTabManager.createNewTranslator(currentProjectDir, fxUtils, tabPane, bundle);
        if (translatorTab != null) {
            File smtFile = translatorTabManager.getFileForTab(translatorTab);
            if (smtFile != null) {
                tabFileMap.put(translatorTab, smtFile);
            }
        }
        persistSessionState();
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

    /**
     * Comprueba, antes de cerrar la aplicación, si alguna pestaña de editor
     * (tanto del panel principal como del panel secundario) tiene un archivo en
     * disco modificado externamente con un buffer sucio en el editor. Si es así,
     * pregunta con el diálogo de 3 botones (Recargar desde disco / Conservar mi
     * versión del editor / Cancelar) en lugar de descartar o sobrescribir en
     * silencio.
     *
     * @return {@code false} si el usuario decidió cancelar el cierre de alguna
     *     pestaña; {@code true} si todas las pestañas tienen el conflicto
     *     resuelto (recargado o conservado) o no presentan conflicto y se puede
     *     continuar con el apagado.
     */
    public boolean confirmAllEditorTabsBeforeApplicationExit() {
        if (tabPane != null) {
            for (Tab tab : new ArrayList<>(tabPane.getTabs())) {
                if (!confirmCloseEditorTab(tab)) {
                    return false;
                }
            }
        }
        if (secondaryTabPane != null) {
            for (Tab tab : new ArrayList<>(secondaryTabPane.getTabs())) {
                if (!confirmCloseEditorTab(tab)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void shutdown() {
        persistSessionState();
        try {
            if (externalFileWatcherTimer != null) {
                externalFileWatcherTimer.stop();
                externalFileWatcherTimer = null;
            }
        } catch (Exception ignored) {
        }
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
            if (TileEditorController.isTileFile(opened)) {
                rerouteOpenTileAsCodeToTool(opened);
                return;
            }
            rememberRecentFile(opened);
            persistSessionState();
        }
    }

    /**
     * Cuando un .tile se abrió como pestaña de código (p. ej. vía "Abrir..." de
     * Ctrl+O), se descarta esa pestaña y se reabre en la herramienta de tiles.
     */
    private void rerouteOpenTileAsCodeToTool(File tileFile) {
        for (Tab tab : new ArrayList<>(tabPane.getTabs())) {
            if (tileFile.equals(tabFileMap.get(tab))) {
                tabPane.getTabs().remove(tab);
                break;
            }
        }
        openTileEditorTab(tileFile);
        rememberRecentFile(tileFile);
        persistSessionState();
    }

    @FXML
    private void onSaveFile(ActionEvent event) {
        Tab selectedTab = tabPane != null ? tabPane.getSelectionModel().getSelectedItem() : null;
        if (selectedTab != null && isTileEditorTab(selectedTab)) {
            TileEditorController tile = getTileEditorController(selectedTab);
            if (tile != null && tile.saveCurrentProject()) {
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                persistSessionState();
            }
            return;
        }
        if (selectedTab != null && translatorTabManager.isTranslatorTab(selectedTab)) {
            File smtFile = tabFileMap.get(selectedTab);
            if (smtFile != null) {
                translatorTabManager.saveTranslatorTab(selectedTab, smtFile);
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                persistSessionState();
            }
            return;
        }
        if (!resolveExternalChangeBeforeSave(selectedTab)) {
            return;
        }
        boolean saved = fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel);
        if (saved && projectExplorerController != null) {
            projectExplorerController.refreshTree();
        }
        if (saved) {
            File savedFile = tabFileMap.get(selectedTab);
            if (savedFile != null) {
                AutoSaveService.clearSnapshot(savedFile);
                runSaveTimeSyntaxCheck(savedFile);
            }
        }
    }

    /**
     * Antes de guardar, si el archivo se modificó fuera del editor, pregunta al
     * usuario si quiere recargar desde disco o sobrescribir con su versión.
     *
     * @return {@code true} para continuar con el guardado, {@code false} para cancelarlo
     */
    private boolean resolveExternalChangeBeforeSave(Tab tab) {
        if (tab == null) return true;
        File file = tabFileMap.get(tab);
        CodeArea codeArea = fxUtils.getCodeArea(tab);
        String registered = fxUtils.getDiskFingerprint(tab);
        if (file == null || codeArea == null || registered == null) return true;
        if (!ExternalFileChangeService.hasExternalChange(file, registered)) return true;

        Optional<ExternalChangeAction> decision = askExternalChangeAction(file.getName(), true);
        if (decision.isEmpty()) return false;
        switch (decision.get()) {
            case RELOAD -> {
                if (!reloadTabFromDisk(tab, file, codeArea)) return false;
            }
            case OVERWRITE -> { /* se procede: el guardado sobrescribirá el disco */ }
            default -> { return false; }
        }
        return true;
    }

    /**
     * Decide si se puede cerrar la pestaña dada sin sobrescribir silenciosamente
     * un archivo que cambió en disco fuera del editor.
     * <p>
     * Si la pestaña está sucia y el archivo se modificó externamente (p. ej. por
     * opencode), no se cierra directamente: se pregunta al usuario con las mismas
     * opciones que al guardar (Recargar desde disco / Conservar mi versión en el
     * editor / Cancelar). Así se evita perder las ediciones sin guardar o pisar el
     * cambio externo sin confirmación.
     *
     * @param tab pestaña cuyo cierre se solicita
     * @return {@code true} si procede cerrar (sin conflicto, o ya resuelto
     *     recargando o conservando la versión del editor); {@code false} si el
     *     cierre debe cancelarse (el diálogo se cerró o el usuario eligió
     *     «Cancelar»)
     */
    private boolean confirmCloseEditorTab(Tab tab) {
        if (tab == null) return true;
        File file = tabFileMap.get(tab);
        CodeArea codeArea = fxUtils.getCodeArea(tab);
        String registered = fxUtils.getDiskFingerprint(tab);
        if (file == null || codeArea == null || registered == null) {
            return true;
        }
        if (!ExternalFileChangeService.hasExternalChange(file, registered)) {
            return true;
        }

        Optional<ExternalChangeAction> decision = askExternalChangeAction(file.getName(), true);
        if (decision.isEmpty()) return false;
        switch (decision.get()) {
            case RELOAD -> {
                if (!reloadTabFromDisk(tab, file, codeArea)) return false;
            }
            case OVERWRITE -> {
                if (!persistEditorVersionToDisk(tab, file, codeArea)) return false;
            }
            default -> { return false; }
        }
        return true;
    }

    /**
     * Consigna el buffer del editor de la pestaña sobre el archivo en disco,
     * marcando la pestaña como guardada y actualizando la huella registrada.
     * Se usa cuando el usuario elige «Conservar mi versión» ante un cambio externo.
     *
     * @return {@code true} si se escribió al disco correctamente
     */
    private boolean persistEditorVersionToDisk(Tab tab, File file, CodeArea codeArea) {
        try {
            FoldingCodeArea.expandAllFolds(codeArea);
            editorModel.saveFile(file, codeArea.getText());
            LocalHistoryService.recordSnapshot(file, codeArea.getText());
            fxUtils.markTabSaved(tab, fxUtils.getDefaultFileName(tab), codeArea.getText());
            return true;
        } catch (IOException ex) {
            terminalController.appendConsoleOutput("No se conservar la versión del editor: " + ex.getMessage(), consoleOutputArea);
            return false;
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
                TileEditorController tile = getTileEditorController(tab);
                if (tile != null) {
                    if (tile.getCurrentFile() != null) {
                        tabsToSave.add(tab);
                    }
                } else if (tabFileMap.containsKey(tab)) {
                    tabsToSave.add(tab);
                }
            }

            for (Tab tab : tabsToSave) {
                tabPane.getSelectionModel().select(tab);
                if (isTileEditorTab(tab)) {
                    TileEditorController tile = getTileEditorController(tab);
                    if (tile != null && tile.saveCurrentProject()) {
                        savedAny = true;
                    }
                } else if (translatorTabManager.isTranslatorTab(tab)) {
                    File smtFile = tabFileMap.get(tab);
                    if (smtFile != null) {
                        translatorTabManager.saveTranslatorTab(tab, smtFile);
                        savedAny = true;
                    }
                } else {
                    boolean saved = fileOptionsController.onSaveFile(event, tabPane, tabFileMap, editorModel);
                    if (!saved) {
                        break;
                    }
                    File savedFile = tabFileMap.get(tab);
                    if (savedFile != null) {
                        AutoSaveService.clearSnapshot(savedFile);
                    }
                    savedAny = true;
                }
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
        Tab selectedTab = tabPane != null ? tabPane.getSelectionModel().getSelectedItem() : null;
        if (selectedTab != null && isTileEditorTab(selectedTab)) {
            TileEditorController tile = getTileEditorController(selectedTab);
            if (tile != null && tile.saveProjectAs()) {
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                persistSessionState();
            }
            return;
        }
        if (selectedTab != null && translatorTabManager.isTranslatorTab(selectedTab)) {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Guardar como");
            fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivos de traducción", "*.smt"));
            String defaultName = fxUtils.getDefaultFileName(selectedTab);
            fileChooser.setInitialFileName(defaultName);
            File file = fileChooser.showSaveDialog(getStage());
            if (file != null) {
                tabFileMap.put(selectedTab, file);
                translatorTabManager.saveTranslatorTab(selectedTab, file);
                selectedTab.setText(file.getName());
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                persistSessionState();
            }
            return;
        }
        boolean saved = fileOptionsController.onSaveAsFile(event, tabPane, tabFileMap, editorModel);
        if (saved && projectExplorerController != null) {
            projectExplorerController.refreshTree();
            persistSessionState();
        }
        if (saved) {
            Tab savedTab = tabPane != null ? tabPane.getSelectionModel().getSelectedItem() : null;
            File savedFile = savedTab != null ? tabFileMap.get(savedTab) : null;
            if (savedFile != null) {
                runSaveTimeSyntaxCheck(savedFile);
            }
        }
    }

    @FXML
    private void onCloseFile(ActionEvent event)  {
        Tab tab = tabPane != null ? tabPane.getSelectionModel().getSelectedItem() : null;
        File closing = tab != null ? tabFileMap.get(tab) : null;
        fileOptionsController.onCloseFile(event, tabPane, tabFileMap, editorModel);
        if (closing != null) {
            AutoSaveService.clearSnapshot(closing);
        }
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
    private void onRenameSymbol(ActionEvent event) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;
        FoldingCodeArea.expandAllFolds(codeArea);
        String text = codeArea.getText();
        int caret = codeArea.getCaretPosition();
        String symbol = editOptionsController.extractWordAtCaret(text, caret);
        if (symbol == null || symbol.isBlank()) return;

        TextInputDialog dialog = new TextInputDialog(symbol);
        dialog.setTitle(msg("dialog.rename.title", "Renombrar símbolo"));
        dialog.setHeaderText(msgFmt("dialog.rename.header", "Introduce el nuevo nombre de ''{0}'' en todo el proyecto.", symbol));
        dialog.setContentText(msg("dialog.rename.label", "Nuevo nombre:"));
        dialog.initOwner(tabPane.getScene() != null ? tabPane.getScene().getWindow() : null);
        dialog.getDialogPane().getStylesheets().setAll(tabPane.getScene() != null
                ? tabPane.getScene().getStylesheets()
                : List.of());

        Optional<String> newNameOpt = dialog.showAndWait();
        if (newNameOpt.isEmpty()) return;
        String newName = newNameOpt.get().trim();
        if (newName.isEmpty() || newName.equals(symbol)) return;
        if (!newName.matches("[A-Za-z_]\\w*")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(msg("dialog.rename.title", "Renombrar símbolo"));
            alert.setHeaderText(null);
            alert.setContentText(msg("dialog.rename.invalid", "El nombre debe ser un identificador válido (letras, números o guion bajo)."));
            alert.showAndWait();
            return;
        }
        if (currentProjectDir == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(msg("dialog.rename.title", "Renombrar símbolo"));
            alert.setHeaderText(null);
            alert.setContentText(msg("alert.project.notOpen.content", "No hay ningún proyecto abierto."));
            alert.showAndWait();
            return;
        }

        File project = currentProjectDir;
        RenameRefactoringService.OccurrencePreview preview =
            renameRefactoringService.previewOccurrences(project, symbol);
        if (preview.getOccurrences() == 0) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(msg("dialog.rename.title", "Renombrar símbolo"));
            alert.setHeaderText(null);
            alert.setContentText(msgFmt("dialog.rename.none", "No se encontraron ocurrencias de ''{0}'' en el proyecto.", symbol));
            alert.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(msg("dialog.rename.title", "Renombrar símbolo"));
        confirm.setHeaderText(msgFmt("dialog.rename.confirm",
                "Se renombrarán {0} ocurrencias en {1} archivo(s). ¿Deseas continuar?",
                preview.getOccurrences(), preview.getFiles()));
        confirm.setContentText(msgFmt("dialog.rename.summary", "{0} → {1}", symbol, newName));
        confirm.initOwner(tabPane.getScene() != null ? tabPane.getScene().getWindow() : null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        final String oldSymbol = symbol;
        CompletableFuture.runAsync(() -> {
            RenameRefactoringService.RenameResult rename =
                renameRefactoringService.renameSymbolInProject(project, oldSymbol, newName);
            Platform.runLater(() -> {
                if (projectExplorerController != null) {
                    projectExplorerController.refreshTree();
                }
                reloadAffectedTabsAfterRename(project, oldSymbol);
                String summary = msgFmt("dialog.rename.done",
                        "Renombrado: {0} ocurrencias en {1} archivo(s).",
                        rename.getTotalOccurrences(), rename.getFilesModified());
                terminalController.appendConsoleOutput(summary, consoleOutputArea);
            });
        });
    }

    @FXML
    private void onExtractConstant(ActionEvent event) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;
        FoldingCodeArea.expandAllFolds(codeArea);
        String text = codeArea.getText();
        int lo = codeArea.getSelection().getStart();
        int hi = codeArea.getSelection().getEnd();
        if (lo >= hi) {
            showRefactorInfoAlert("dialog.refactor.extractConstant.title", "Extraer constante",
                "refactor.extract.selectionRequired", "Selecciona una expresión para extraerla como constante.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(msg("dialog.refactor.extractConstant.title", "Extraer constante"));
        dialog.setHeaderText(msg("dialog.refactor.extractConstant.header", "Introduce el nombre de la nueva constante."));
        dialog.setContentText(msg("dialog.refactor.extractConstant.label", "Nombre de la constante:"));
        Window owner = tabPane.getScene() != null ? tabPane.getScene().getWindow() : null;
        dialog.initOwner(owner);
        dialog.getDialogPane().getStylesheets().setAll(sceneStylesheets());

        Optional<String> nameOpt = dialog.showAndWait();
        if (nameOpt.isEmpty()) return;
        String name = nameOpt.get().trim();
        if (name.isEmpty()) return;

        ExtractConstantService.Result result = extractConstantService.extract(text, name, lo, hi);
        if (!result.diagnostic().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(msg("dialog.refactor.extractConstant.title", "Extraer constante"));
            alert.setHeaderText(null);
            alert.setContentText(result.diagnostic());
            alert.initOwner(owner);
            alert.showAndWait();
            return;
        }
        applyRefactorText(codeArea, result.newText(), result.caretPosition());
    }

    @FXML
    private void onExtractFunction(ActionEvent event) {
        CodeArea codeArea = fxUtils.getCurrentCodeArea(tabPane);
        if (codeArea == null) return;
        FoldingCodeArea.expandAllFolds(codeArea);
        String text = codeArea.getText();
        int lo = codeArea.getSelection().getStart();
        int hi = codeArea.getSelection().getEnd();
        if (lo >= hi) {
            showRefactorInfoAlert("dialog.refactor.extractFunction.title", "Extraer función",
                "refactor.extract.selectionRequired", "Selecciona el código a extraer a una función.");
            return;
        }

        Window owner = tabPane.getScene() != null ? tabPane.getScene().getWindow() : null;
        Dialog<ExtractFunctionNames> dialog = new Dialog<>();
        dialog.setTitle(msg("dialog.refactor.extractFunction.title", "Extraer función"));
        dialog.setHeaderText(msg("dialog.refactor.extractFunction.header",
            "Introduce el nombre y los parámetros de la nueva función."));
        ButtonType okType = new ButtonType(msg("dialog.project.new.template.action.accept", "Aceptar"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        TextField txtName = new TextField();
        txtName.setPromptText("mi_funcion");
        TextField txtParams = new TextField();
        txtParams.setPromptText(msg("dialog.refactor.extractFunction.paramsPlaceholder", "int x, int y (vacío = void)"));
        VBox vbox = new VBox(8,
            new Label(msg("dialog.refactor.extractFunction.nameLabel", "Nombre:")), txtName,
            new Label(msg("dialog.refactor.extractFunction.paramsLabel", "Parámetros (vacío = void):")), txtParams);
        vbox.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getStylesheets().setAll(sceneStylesheets());
        dialog.initOwner(owner);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(okType);
        okButton.setDisable(true);
        txtName.textProperty().addListener((obs, o, n) -> okButton.setDisable(n == null || n.isBlank()));

        dialog.setResultConverter(bt -> bt == okType
            ? new ExtractFunctionNames(txtName.getText().trim(), txtParams.getText().trim())
            : null);
        Optional<ExtractFunctionNames> picked = dialog.showAndWait();
        if (picked.isEmpty()) return;
        ExtractFunctionNames names = picked.get();

        ExtractFunctionService.Result result =
            extractFunctionService.extract(text, names.name(), names.params(), lo, hi);
        if (!result.diagnostic().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(msg("dialog.refactor.extractFunction.title", "Extraer función"));
            alert.setHeaderText(null);
            alert.setContentText(result.diagnostic());
            alert.initOwner(owner);
            alert.showAndWait();
            return;
        }
        applyRefactorText(codeArea, result.newText(), result.caretPosition());
    }

    private record ExtractFunctionNames(String name, String params) {}

    private void showRefactorInfoAlert(String titleKey, String titleFallback, String contentKey, String contentFallback) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(msg(titleKey, titleFallback));
        alert.setHeaderText(null);
        alert.setContentText(msg(contentKey, contentFallback));
        alert.initOwner(tabPane.getScene() != null ? tabPane.getScene().getWindow() : null);
        alert.showAndWait();
    }

    private void applyRefactorText(CodeArea codeArea, String newText, int caret) {
        codeArea.replaceText(0, codeArea.getLength(), newText);
        codeArea.moveTo(Math.min(caret, codeArea.getLength()));
        codeArea.requestFollowCaret();
        fxUtils.updateCursorStatus(tabPane);
    }

    @FXML
    private void onFoldBlock(ActionEvent event) {
        Tab tab = fxUtils.getSelectedTab(tabPane);
        CodeArea area = tab != null ? fxUtils.getCodeArea(tab) : null;
        if (!(area instanceof FoldingCodeArea foldingArea)) return;
        String text = area.getText();
        if (text == null || text.isBlank()) return;
        int caretLine = caretLineOf(area);
        CodeFoldingService.FoldRange best = null;
        for (CodeFoldingService.FoldRange r : new CodeFoldingService().detectFoldRanges(text)) {
            if (r.getStartLine() <= caretLine && r.getEndLine() >= caretLine) {
                boolean moreInner = best == null || r.getStartLine() > best.getStartLine()
                        || (r.getStartLine() == best.getStartLine() && r.getEndLine() < best.getEndLine());
                if (moreInner) best = r;
            }
        }
        if (best == null) {
            terminalController.appendConsoleOutput(msg("folding.none.underCaret", "No hay ningún bloque plegable bajo el cursor."), consoleOutputArea);
            return;
        }
        if (foldingArea.foldRange(best.getStartLine(), best.getEndLine(), best.getTitle())) {
            fxUtils.updateCursorStatus(tabPane);
        }
    }

    @FXML
    private void onUnfoldBlock(ActionEvent event) {
        Tab tab = fxUtils.getSelectedTab(tabPane);
        CodeArea area = tab != null ? fxUtils.getCodeArea(tab) : null;
        if (area instanceof FoldingCodeArea foldingArea && foldingArea.unfoldAtCaret()) {
            fxUtils.updateCursorStatus(tabPane);
            return;
        }
        terminalController.appendConsoleOutput(msg("folding.none.folded", "No hay ningún bloque plegado bajo el cursor."), consoleOutputArea);
    }

    @FXML
    private void onInsertSnippet(ActionEvent event) {
        if (snippets.isEmpty()) {
            snippets.addAll(snippetRepository.load());
        }
        Window owner = tabPane.getScene() != null ? tabPane.getScene().getWindow() : null;
        Optional<SnippetService.NamedSnippet> chosen = SnippetEditorDialog.show(owner,
            tabPane.getScene() != null ? tabPane.getScene().getStylesheets() : List.of(),
            bundle, snippets, true);
        chosen.ifPresent(snippet -> insertSnippetIntoActiveEditor(snippet));
        snippetRepository.save(snippets);
    }

    @FXML
    private void onManageSnippets(ActionEvent event) {
        Window owner = tabPane.getScene() != null ? tabPane.getScene().getWindow() : null;
        SnippetEditorDialog.show(owner,
            tabPane.getScene() != null ? tabPane.getScene().getStylesheets() : List.of(),
            bundle, snippets, false);
        snippetRepository.save(snippets);
    }

    private void insertSnippetIntoActiveEditor(SnippetService.NamedSnippet snippet) {
        Tab tab = fxUtils.getSelectedTab(tabPane);
        CodeArea area = tab != null ? fxUtils.getCodeArea(tab) : null;
        if (area == null) return;
        SnippetTabStopNavigator.insert(area, snippetService, snippet.template());
        fxUtils.updateCursorStatus(tabPane);
    }

    private int caretLineOf(CodeArea area) {
        int caret = Math.max(0, Math.min(area.getCaretPosition(), area.getLength()));
        String before = area.getText(0, caret);
        int line = 1;
        int idx = before.indexOf('\n');
        while (idx >= 0) {
            line++;
            idx = before.indexOf('\n', idx + 1);
        }
        return line;
    }

    /** Recarga desde disco las pestañas abiertas que pertenezcan al proyecto renombrado. */
    private void reloadAffectedTabsAfterRename(File project, String oldSymbol) {
        List<Tab> toReload = new ArrayList<>();
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            File file = entry.getValue();
            if (file != null && file.getAbsolutePath().startsWith(project.getAbsolutePath())) {
                toReload.add(entry.getKey());
            }
        }
        for (Tab tab : toReload) {
            try {
                reloadTabFromDisk(tab, tabFileMap.get(tab), fxUtils.getCodeArea(tab));
            } catch (Exception ignored) {
            }
        }
    }

    @FXML
    private void onOpenTileEditor(ActionEvent event) {
        openTileEditorTab(null);
    }

    private void openTileEditorTab(File fileToOpen) {
        openTileEditorTab(fileToOpen, null);
    }

    private void openTileEditorTab(File fileToOpen, String untitledName) {
        String title = untitledName != null
                ? untitledName
                : toolTitle("menu.tools.tileEditor", "Editor de Tiles (Game Boy)");
        Consumer<File> onExported = exportedFile -> {
            if (projectExplorerController != null) {
                projectExplorerController.refreshTree();
            }
            openFileFromExplorer(exportedFile);
            persistSessionState();
        };
        TileEditorController controller = new TileEditorController();
        Node node = controller.createNode(currentProjectDir, bundle, onExported);
        Tab tab = fxUtils.addToolTab(title, node, tabPane);
        tab.getProperties().put(TAB_PROP_TILE_CONTROLLER, controller);
        controller.setStateListener(() -> refreshTileTabTitle(tab));
        tab.setOnClosed(e -> tab.getProperties().remove(TAB_PROP_TILE_CONTROLLER));
        if (fileToOpen != null && controller.openProject(fileToOpen)) {
            rememberRecentFile(fileToOpen);
        }
        persistSessionState();
    }

    private Tab findTileTabForFile(File file) {
        if (file == null || tabPane == null) return null;
        for (Tab tab : tabPane.getTabs()) {
            TileEditorController tile = getTileEditorController(tab);
            File current = tile != null ? tile.getCurrentFile() : null;
            if (current != null && current.equals(file)) {
                return tab;
            }
        }
        return null;
    }

    private boolean isTileEditorTab(Tab tab) {
        return getTileEditorController(tab) != null;
    }

    private TileEditorController getTileEditorController(Tab tab) {
        if (tab == null) return null;
        Object stored = tab.getProperties().get(TAB_PROP_TILE_CONTROLLER);
        return stored instanceof TileEditorController controller ? controller : null;
    }

    /**
     * El título de la pestaña del editor de tiles refleja el archivo que se está
     * editando (con '*' si hay cambios sin guardar), igual que las pestañas de
     * código.
     */
    private void refreshTileTabTitle(Tab tab) {
        TileEditorController controller = getTileEditorController(tab);
        if (controller == null) return;
        String base;
        File current = controller.getCurrentFile();
        if (current != null) {
            base = current.getName();
        } else {
            Object stored = tab.getProperties().get(FXUtils.TAB_PROP_BASE_TITLE);
            base = stored instanceof String s && !s.isBlank()
                    ? s
                    : toolTitle("menu.tools.tileEditor", "Editor de Tiles (Game Boy)");
        }
        tab.setText(controller.hasUnsavedChanges() ? base + "*" : base);
    }

    @FXML
    private void onOpenTilemapEditor(ActionEvent event) {
        String title = toolTitle("menu.tools.tilemapEditor", "Editor de Mapas de Tiles (Game Boy)");
        Consumer<File> onExported = exportedFile -> {
            if (projectExplorerController != null) {
                projectExplorerController.refreshTree();
            }
            openFileFromExplorer(exportedFile);
            persistSessionState();
        };
        Node node = tilemapEditorController.createNode(currentProjectDir, bundle, onExported);
        fxUtils.addToolTab(title, node, tabPane);
    }

    private String toolTitle(String key, String fallback) {
        if (bundle == null) return fallback;
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return fallback;
        }
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
        String title = toolTitle("tool.pngToZx.title", "Convertir PNG a C (ZX Spectrum)");
        Consumer<File> onExported = exportedFile -> {
            if (projectExplorerController != null) {
                projectExplorerController.refreshTree();
            }
            openFileFromExplorer(exportedFile);
            persistSessionState();
        };
        Node node = pngToZxConverterController.createNode(currentProjectDir, bundle, onExported);
        Tab toolTab = fxUtils.addToolTab(title, node, tabPane);
        pngToZxConverterController.setOnCloseRequest(() -> tabPane.getTabs().remove(toolTab));
    }

    @FXML
    private void onOpenPngToGameBoyConverter(ActionEvent event) {
        String title = toolTitle("tool.pngToGameBoy.title", "Convertir PNG a C (Game Boy)");
        Consumer<File> onExported = exportedFile -> {
            if (projectExplorerController != null) {
                projectExplorerController.refreshTree();
            }
            openFileFromExplorer(exportedFile);
            persistSessionState();
        };
        Node node = pngToGameBoyConverterController.createNode(currentProjectDir, bundle, onExported);
        Tab toolTab = fxUtils.addToolTab(title, node, tabPane);
        pngToGameBoyConverterController.setOnCloseRequest(() -> tabPane.getTabs().remove(toolTab));
    }

    @FXML
    private void onOpenGameBoyAudioEditor(ActionEvent event) {
        String title = toolTitle("tool.gameBoyAudio.title", "Editor de Audio");
        Consumer<File> onExported = exportedFile -> {
            if (projectExplorerController != null) {
                projectExplorerController.refreshTree();
            }
            openFileFromExplorer(exportedFile);
            persistSessionState();
        };
        Node node = gameBoyAudioEditorController.createNode(currentProjectDir, bundle, onExported);
        Tab toolTab = fxUtils.addToolTab(title, node, tabPane);
        toolTab.setOnClosed(e -> gameBoyAudioEditorController.closeTool());
    }

    @FXML
    private void onOpenRomTextTranslator(ActionEvent event) {
        Tab translatorTab = translatorTabManager.createNewTranslator(currentProjectDir, fxUtils, tabPane, bundle);
        if (translatorTab != null) {
            File smtFile = translatorTabManager.getFileForTab(translatorTab);
            if (smtFile != null) {
                tabFileMap.put(translatorTab, smtFile);
            }
        }
        persistSessionState();
    }

    @FXML
    private void onOpenGitHubRepoBrowser(ActionEvent event) {
        String title = toolTitle("tool.github.title", "Repositorios de GitHub");
        Consumer<File> onCloned = clonedDir -> {
            if (clonedDir != null && clonedDir.isDirectory()) {
                Platform.runLater(() -> setProyectoActual(clonedDir));
            }
        };
        gitHubRepoBrowserController.setTokenStore(
            () -> configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));
        Node node = gitHubRepoBrowserController.createNode(currentProjectDir, bundle, onCloned);
        Tab toolTab = fxUtils.addToolTab(title, node, tabPane);
        gitHubRepoBrowserController.setOnCloseRequest(() -> tabPane.getTabs().remove(toolTab));
    }

    @FXML
    private void onOpenGitPanel(ActionEvent event) {
        openGitPanel();
    }

    @FXML
    private void onViewActiveFileDiff(ActionEvent event) {
        Tab selectedTab = fxUtils.getSelectedTab(tabPane);
        File selectedFile = selectedTab != null ? tabFileMap.get(selectedTab) : null;
        if (selectedTab == null || selectedFile == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(getStage());
            alert.setTitle(msg("git.diff.title", "Diff de archivo"));
            alert.setHeaderText(null);
            alert.setContentText(msg("git.diff.noFile", "Abre un archivo del proyecto para ver su diff."));
            alert.getDialogPane().getStylesheets().setAll(sceneStylesheets());
            alert.showAndWait();
            return;
        }
        if (currentProjectDir == null || !gitService.isRepository(currentProjectDir.toPath())) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(getStage());
            alert.setTitle(msg("git.diff.title", "Diff de archivo"));
            alert.setHeaderText(null);
            alert.setContentText(msg("git.diff.noRepo", "No hay un repositorio git en el proyecto abierto."));
            alert.getDialogPane().getStylesheets().setAll(sceneStylesheets());
            alert.showAndWait();
            return;
        }
        String relPath = toGitRelativePath(selectedFile);
        if (relPath == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(getStage());
            alert.setTitle(msg("git.diff.title", "Diff de archivo"));
            alert.setHeaderText(null);
            alert.setContentText(msg("git.diff.outsideRepo", "El archivo activo está fuera del repositorio git."));
            alert.getDialogPane().getStylesheets().setAll(sceneStylesheets());
            alert.showAndWait();
            return;
        }

        Path repoPath = currentProjectDir.toPath();
        GitService svc = gitService;
        CompletableFuture.runAsync(() -> {
            try {
                List<GitModels.GitDiffLine> lines = svc.diffVsHead(repoPath, relPath);
                List<GitModels.GitDiffEntry> entries = GitService.parseDiffWithLineNumbers(
                    lines.stream().map(GitModels.GitDiffLine::text).toList());
                Platform.runLater(() -> showDiffDialog(selectedFile.getName(), relPath, entries));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.initOwner(getStage());
                    alert.setTitle(msg("git.diff.title", "Diff de archivo"));
                    alert.setHeaderText(null);
                    alert.setContentText(msg("git.diff.error", "Error cargando el diff: ") + ex.getMessage());
                    alert.getDialogPane().getStylesheets().setAll(sceneStylesheets());
                    alert.showAndWait();
                });
            }
        });
    }

    private List<String> sceneStylesheets() {
        return tabPane.getScene() != null ? tabPane.getScene().getStylesheets() : List.of();
    }

    private void showDiffDialog(String fileName, String relPath, List<GitModels.GitDiffEntry> entries) {
        if (entries.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(getStage());
            alert.setTitle(msg("git.diff.title", "Diff de archivo"));
            alert.setHeaderText(null);
            alert.setContentText(msg("git.diff.noChanges", "Sin cambios frente a HEAD."));
            alert.getDialogPane().getStylesheets().setAll(sceneStylesheets());
            alert.showAndWait();
            return;
        }
        CodeArea codeArea = GitDiffViewer.create(entries, relPath);
        codeArea.setPrefSize(760, 460);
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
        scroll.setPrefSize(780, 480);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(msgFmt("git.diff.file", "Diff de {0}", fileName));
        dialog.initOwner(getStage());
        dialog.getDialogPane().getStylesheets().setAll(sceneStylesheets());
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(820);
        dialog.getDialogPane().setPrefHeight(540);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /** Abre o selecciona la pestaña del panel Git en el editor principal y fuerza un refresco. Invocable desde el explorador. */
    public void openGitPanel() {
        if (tabPane == null) return;
        if (gitPanelTab == null || !tabPane.getTabs().contains(gitPanelTab)) {
            gitPanelTab = gitPanelController.createTab();
            gitPanelTab.setClosable(true);
            tabPane.getTabs().add(gitPanelTab);
        }
        tabPane.getSelectionModel().select(gitPanelTab);
        gitPanelController.requestRefresh();
    }

    /** Despliega en el panel Git un commit (añade todo y commitea con el mensaje dado). */
    public void gitCommit(String message) {
        gitPanelController.setProject(currentProjectDir);
        gitPanelController.commitWithMessage(message);
    }

    /** Ejecuta un pull desde origin en el proyecto abierto. */
    public void gitPull() {
        openGitPanel();
        gitPanelController.pull();
    }

    /** Ejecuta un push a origin en el proyecto abierto. */
    public void gitPush() {
        openGitPanel();
        gitPanelController.push();
    }

    /** Abre el diálogo de nueva rama para el proyecto abierto. */
    public void gitNewBranch() {
        openGitPanel();
        gitPanelController.newBranch();
    }

    /** Guarda los cambios del proyecto abierto en el stash. */
    public void gitStash() {
        openGitPanel();
        gitPanelController.stash();
    }

    /** Recupera los cambios del último stash del proyecto abierto. */
    public void gitStashPop() {
        openGitPanel();
        gitPanelController.popStash();
    }

    /** Abre el diálogo de publicación en GitHub (invocable desde el explorador de proyectos). */
    public void shareToGithub() {
        onShareToGithub(null);
    }

    /**
     * Publica el proyecto actual en GitHub (Share to GitHub): crea el repositorio,
     * hace el commit inicial y sube el contenido con push.
     */
    @FXML
    private void onShareToGithub(ActionEvent event) {
        if (currentProjectDir == null || !currentProjectDir.isDirectory()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(msg("git.share.title", "Publicar en GitHub"));
            alert.setHeaderText(null);
            alert.setContentText(msg("git.share.noProject", "No hay proyecto abierto."));
            alert.showAndWait();
            return;
        }

        GitHubShareService.GitHubShareParams params = showShareToGithubDialog();
        if (params == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                GitHubAuthService auth = new GitHubAuthService(
                    () -> configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));
                GitHubAuthService.ResolvedToken resolved = auth.resolveToken();
                if (!resolved.isPresent()) {
                    throw new IOException(msg("git.share.noToken",
                        "No hay token de GitHub configurado. Configuralo en Configuracion > GitHub."));
                }
                GitService.GitCredentials creds = new GitService.GitCredentials(resolved.token());
                GitHubShareService.ShareResult result = gitHubShareService.shareProject(
                    currentProjectDir.toPath(), params, resolved.token(), creds);

                Platform.runLater(() -> {
                    String message = msg("git.share.success", "Proyecto publicado: {0}")
                        .replace("{0}", result.htmlUrl());
                    Alert success = new Alert(Alert.AlertType.INFORMATION);
                    success.setTitle(msg("git.share.title", "Publicar en GitHub"));
                    success.setHeaderText(null);
                    success.setContentText(message);
                    success.showAndWait();
                    openInSystemBrowser(result.htmlUrl());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle(msg("git.share.title", "Publicar en GitHub"));
                    error.setHeaderText(null);
                    error.setContentText(e.getMessage());
                    error.showAndWait();
                });
            }
        });
    }

    /** Diálogo con los datos del repositorio a crear. Devuelve {@code null} si se cancela. */
    private GitHubShareService.GitHubShareParams showShareToGithubDialog() {
        Dialog<GitHubShareService.GitHubShareParams> dialog = new Dialog<>();
        dialog.setTitle(msg("git.share.title", "Publicar en GitHub"));
        dialog.setHeaderText(msg("git.share.header",
            "Crea un repositorio en tu cuenta de GitHub y publica el proyecto actual."));

        ButtonType publishType = new ButtonType(msg("git.share.button", "Publicar"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(publishType, ButtonType.CANCEL);

        String defaultName = currentProjectDir != null ? currentProjectDir.getName() : "my-project";
        TextField txtRepoName = new TextField(defaultName);
        txtRepoName.setPromptText("my-project");
        TextField txtDescription = new TextField();
        txtDescription.setPromptText(msg("git.share.description.label", "Descripcion (opcional):"));
        CheckBox chkPrivate = new CheckBox(msg("git.share.private", "Repositorio privado"));
        TextField txtCommitMessage = new TextField("Initial commit from Samaruc Editor");
        TextField txtUserName = new TextField();
        txtUserName.setPromptText(msg("git.share.userName.placeholder", "Nombre de usuario git"));
        TextField txtUserEmail = new TextField();
        txtUserEmail.setPromptText(msg("git.share.userEmail.placeholder", "Email de usuario git"));

        VBox vbox = new VBox(8,
            new Label(msg("git.share.repoName.label", "Nombre del repositorio:")),
            txtRepoName,
            new Label(msg("git.share.description.label", "Descripcion (opcional):")),
            txtDescription,
            chkPrivate,
            new Label(msg("git.share.commitMessage.label", "Mensaje del commit inicial:")),
            txtCommitMessage,
            new Separator(),
            new Label(msg("git.share.gitIdentity.label", "Identidad git (opcional):")),
            txtUserName,
            txtUserEmail
        );
        vbox.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(vbox);

        var okButton = dialog.getDialogPane().lookupButton(publishType);
        txtRepoName.textProperty().addListener((obs, oldVal, newVal) ->
            okButton.setDisable(newVal == null || newVal.isBlank()));
        if (txtRepoName.getText() == null || txtRepoName.getText().isBlank()) {
            okButton.setDisable(true);
        }

        dialog.setResultConverter(buttonType -> {
            if (buttonType == publishType) {
                return new GitHubShareService.GitHubShareParams(
                    txtRepoName.getText().trim(),
                    txtDescription.getText().trim(),
                    chkPrivate.isSelected(),
                    txtCommitMessage.getText().trim(),
                    txtUserName.getText().trim(),
                    txtUserEmail.getText().trim());
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /** Abre el diálogo de publicación de release (invocable desde el explorador de proyectos). */
    public void publishRelease() {
        onPublishRelease(null);
    }

    /**
     * Publica un GitHub Release con los artefactos compilados del proyecto (ROMs en
     * out/). Reutiliza el remoto origin para localizar owner/repo.
     */
    @FXML
    private void onPublishRelease(ActionEvent event) {
        if (currentProjectDir == null || !currentProjectDir.isDirectory()) {
            showGitReleaseError(msg("git.release.noProject", "No hay ningún proyecto abierto para publicar."));
            return;
        }

        List<File> artifacts = buildController.resolveBuildArtifacts(currentProjectDir);
        if (artifacts.isEmpty()) {
            showGitReleaseError(msg("git.release.noArtifacts",
                "No se encontraron artefactos compilados en la carpeta out/. Compila primero el proyecto."));
            return;
        }

        String remoteUrl;
        try {
            remoteUrl = gitService.remoteUrl(currentProjectDir.toPath(), "origin").orElse("");
        } catch (GitOperationsException ex) {
            remoteUrl = "";
        }
        GitHubReleaseService.OwnerRepo ownerRepo = GitHubReleaseService.parseOwnerRepo(remoteUrl).orElse(null);
        if (ownerRepo == null) {
            showGitReleaseError(msg("git.release.hasRemote",
                "Publica tus cambios en GitHub primero (Compartir proyecto)."));
            return;
        }

        showPublishReleaseDialog(ownerRepo, artifacts);
    }

    /** Diálogo con los datos del release y los artefactos a subir. Devuelve {@code null} si se cancela. */
    private void showPublishReleaseDialog(GitHubReleaseService.OwnerRepo ownerRepo, List<File> artifacts) {
        Dialog<GitHubReleaseService.NewRelease> dialog = new Dialog<>();
        dialog.setTitle(msg("git.release.title", "Publicar Release"));
        dialog.setHeaderText(ownerRepo.owner() + "/" + ownerRepo.repo());

        ButtonType publishType = new ButtonType(msg("git.release.publish", "Publicar"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(publishType, ButtonType.CANCEL);

        TextField txtTag = new TextField("v1.0.0");
        TextField txtName = new TextField();
        txtName.setPromptText(msg("git.release.tag", "Tag (ej. v1.0.0)"));
        TextArea txtDescription = new TextArea();
        txtDescription.setPrefRowCount(3);
        CheckBox chkDraft = new CheckBox(msg("git.release.draft", "Borrador"));
        CheckBox chkPrerelease = new CheckBox(msg("git.release.prerelease", "Prerelease"));

        VBox assetBox = new VBox(4);
        assetBox.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 6;");
        List<javafx.scene.control.CheckBox> assetChecks = new ArrayList<>();
        for (File artifact : artifacts) {
            CheckBox chk = new CheckBox(artifact.getName());
            chk.setSelected(true);
            assetChecks.add(chk);
            assetBox.getChildren().add(chk);
        }

        VBox vbox = new VBox(8,
            new Label(msg("git.release.tag", "Tag (ej. v1.0.0):")), txtTag,
            new Label(msg("git.release.name", "Nombre del release:")), txtName,
            new Label(msg("git.release.description", "Descripción:")), txtDescription,
            new HBox(12, chkDraft, chkPrerelease),
            new Label(msg("git.release.assets", "Artefactos a subir:")), assetBox);
        vbox.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(vbox);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != publishType) {
                return null;
            }
            List<File> selected = new ArrayList<>();
            for (int i = 0; i < assetChecks.size(); i++) {
                if (assetChecks.get(i).isSelected()) {
                    selected.add(artifacts.get(i));
                }
            }
            if (selected.isEmpty()) {
                return null;
            }
            return new GitHubReleaseService.NewRelease(
                txtTag.getText().trim(),
                txtName.getText().trim(),
                txtDescription.getText(),
                chkDraft.isSelected(),
                chkPrerelease.isSelected());
        });

        dialog.showAndWait().ifPresent(release -> publishReleaseAsync(ownerRepo, release, artifacts));
    }

    /** Publica la release y sube los artefactos en segundo plano (sin bloquear el hilo de JavaFX). */
    private void publishReleaseAsync(GitHubReleaseService.OwnerRepo ownerRepo,
                                     GitHubReleaseService.NewRelease release, List<File> artifacts) {
        CompletableFuture.runAsync(() -> {
            try {
                GitHubAuthService auth = new GitHubAuthService(
                    () -> configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));
                GitHubAuthService.ResolvedToken resolved = auth.resolveToken();
                if (!resolved.isPresent()) {
                    throw new IOException(msg("git.release.noToken",
                        "No hay token de GitHub configurado. Configúralo en Configuración > GitHub."));
                }

                GitHubReleaseService.ReleaseCreated created =
                    gitHubReleaseService.createRelease(resolved.token(), ownerRepo.owner(), ownerRepo.repo(), release);

                for (File artifact : artifacts) {
                    gitHubReleaseService.uploadAsset(resolved.token(), created.uploadUrl(), artifact.toPath());
                }

                Platform.runLater(() -> {
                    String message = msg("git.release.success", "Release publicado: {0}")
                        .replace("{0}", created.htmlUrl());
                    showOperationStatus(message);
                    Alert success = new Alert(Alert.AlertType.INFORMATION);
                    success.setTitle(msg("git.release.title", "Publicar Release"));
                    success.setHeaderText(null);
                    success.setContentText(message);
                    success.showAndWait();
                    openInSystemBrowser(created.htmlUrl());
                });
            } catch (Exception e) {
                Platform.runLater(() -> showGitReleaseError(e.getMessage()));
            }
        });
    }

    private void showGitReleaseError(String message) {
        Alert error = new Alert(Alert.AlertType.ERROR);
        error.setTitle(msg("git.release.title", "Publicar Release"));
        error.setHeaderText(null);
        error.setContentText(message);
        error.showAndWait();
    }

    /** Abre el diálogo de issues del proyecto (invocable desde el explorador de proyectos). */
    public void showIssues() {
        onShowIssues(null);
    }

    /** Lista y crea issues del repositorio remoto del proyecto abierto. */
    @FXML
    private void onShowIssues(ActionEvent event) {
        if (currentProjectDir == null || !currentProjectDir.isDirectory()) {
            showGitReleaseError(msg("git.issue.empty", "No hay issues."));
            return;
        }
        String remoteUrl;
        try {
            remoteUrl = gitService.remoteUrl(currentProjectDir.toPath(), "origin").orElse("");
        } catch (GitOperationsException ex) {
            remoteUrl = "";
        }
        GitHubReleaseService.OwnerRepo ownerRepo = GitHubReleaseService.parseOwnerRepo(remoteUrl).orElse(null);
        if (ownerRepo == null) {
            showGitReleaseError(msg("git.release.hasRemote",
                "Publica tus cambios en GitHub primero (Compartir proyecto)."));
            return;
        }
        showIssuesDialog(ownerRepo);
    }

    /** Diálogo con la lista de issues y un formulario de creación. */
    private void showIssuesDialog(GitHubReleaseService.OwnerRepo ownerRepo) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(msg("git.issue.title", "Issues") + " — " + ownerRepo.owner() + "/" + ownerRepo.repo());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ListView<GitHubIssueService.Issue> listView = new ListView<>();
        listView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(GitHubIssueService.Issue item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText("#" + item.number() + "  " + item.title());
            }
        });
        listView.setPrefHeight(220);

        TextField txtTitle = new TextField();
        txtTitle.setPromptText(msg("git.issue.titleLabel", "Título"));
        TextArea txtBody = new TextArea();
        txtBody.setPromptText(msg("git.issue.bodyLabel", "Descripción"));
        txtBody.setPrefRowCount(2);
        Button btnCreate = new Button(msg("git.issue.createBtn", "Crear"));
        VBox form = new VBox(6, txtTitle, txtBody, btnCreate);

        VBox root = new VBox(10, listView, form);
        root.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(root);

        dialog.setOnShown(e -> loadIssues(ownerRepo, listView, btnCreate));

        btnCreate.setOnAction(e -> {
            String title = txtTitle.getText() == null ? "" : txtTitle.getText().trim();
            if (title.isEmpty()) {
                return;
            }
            btnCreate.setDisable(true);
            CompletableFuture.runAsync(() -> {
                try {
                    GitHubAuthService auth = new GitHubAuthService(
                        () -> configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));
                    GitHubAuthService.ResolvedToken resolved = auth.resolveToken();
                    if (!resolved.isPresent()) {
                        throw new IOException(msg("git.release.noToken",
                            "No hay token de GitHub configurado. Configúralo en Configuración > GitHub."));
                    }
                    gitHubIssueService.createIssue(resolved.token(), ownerRepo.owner(), ownerRepo.repo(),
                        title, txtBody.getText());
                    Platform.runLater(() -> {
                        txtTitle.clear();
                        txtBody.clear();
                        btnCreate.setDisable(false);
                        loadIssues(ownerRepo, listView, btnCreate);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        btnCreate.setDisable(false);
                        showGitReleaseError(msg("git.issue.error", "Error: ") + ex.getMessage());
                    });
                }
            });
        });

        dialog.showAndWait();
    }

    /** Recarga la lista de issues en segundo plano. */
    private void loadIssues(GitHubReleaseService.OwnerRepo ownerRepo,
                            ListView<GitHubIssueService.Issue> listView, Button btnCreate) {
        btnCreate.setDisable(true);
        CompletableFuture.runAsync(() -> {
            try {
                GitHubAuthService auth = new GitHubAuthService(
                    () -> configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));
                GitHubAuthService.ResolvedToken resolved = auth.resolveToken();
                if (!resolved.isPresent()) {
                    Platform.runLater(() -> {
                        listView.getItems().clear();
                        btnCreate.setDisable(false);
                    });
                    return;
                }
                List<GitHubIssueService.Issue> issues = gitHubIssueService.listOpenIssues(
                    resolved.token(), ownerRepo.owner(), ownerRepo.repo());
                Platform.runLater(() -> {
                    listView.getItems().setAll(issues);
                    btnCreate.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    listView.getItems().clear();
                    btnCreate.setDisable(false);
                    showGitReleaseError(msg("git.issue.listError", "Error cargando issues: ") + ex.getMessage());
                });
            }
        });
    }

    /**
     * Marca con un punto las pestañas de archivos que tienen cambios git sin commitear
     * (reutiliza tab.getGraphic, que los tabs de editor no usan para otra cosa).
     */
    private void updateGitDirtyTabMarkers(Set<String> dirtyPaths) {
        if (currentProjectDir == null) {
            return;
        }
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            Tab tab = entry.getKey();
            File file = entry.getValue();
            if (tab == null || file == null) continue;
            String relativePath = toGitRelativePath(file);
            setGitDirtyMarker(tab, relativePath != null && dirtyPaths.contains(relativePath));
        }
    }

    private String toGitRelativePath(File file) {
        if (currentProjectDir == null || file == null) return null;
        try {
            Path projectPath = currentProjectDir.getCanonicalFile().toPath();
            Path filePath = file.getCanonicalFile().toPath();
            if (!filePath.startsWith(projectPath)) return null;
            return projectPath.relativize(filePath).toString().replace('\\', '/');
        } catch (IOException e) {
            return null;
        }
    }

    private void setGitDirtyMarker(Tab tab, boolean dirty) {
        Node marker = (Node) tab.getProperties().get(TAB_PROP_GIT_DIRTY);
        HBox iconBox = fxUtils.getFileIconContainer(tab);
        if (dirty) {
            if (marker == null) {
                Label dot = new Label("\u25CF");
                dot.getStyleClass().add("git-dirty-dot");
                marker = dot;
                tab.getProperties().put(TAB_PROP_GIT_DIRTY, marker);
            }
            if (iconBox != null) {
                if (!iconBox.getChildren().contains(marker)) {
                    iconBox.getChildren().add(marker);
                }
            } else {
                tab.setGraphic(marker);
            }
        } else if (marker != null) {
            tab.getProperties().remove(TAB_PROP_GIT_DIRTY);
            if (iconBox != null) {
                iconBox.getChildren().remove(marker);
            } else {
                tab.setGraphic(null);
            }
        }
    }

    /** Actualiza el indicador de estado git en la barra inferior. */
    private void updateGitStatusLabel(String text) {
        if (lblGitBranch == null) return;
        boolean visible = text != null && !text.isBlank();
        lblGitBranch.setVisible(visible);
        lblGitBranch.setManaged(visible);
        lblGitBranch.setText(visible ? text : "");
    }

    /** Muestra un mensaje transitorio de avance (pull/push/commit/release) en la barra inferior. */
    public void showOperationStatus(String text) {
        if (lblOpStatus == null) return;
        boolean visible = text != null && !text.isBlank();
        lblOpStatus.setText(visible ? text : "");
        lblOpStatus.setVisible(visible);
        lblOpStatus.setManaged(visible);
        if (visible) {
            javafx.animation.Timeline timer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(6),
                    e -> {
                        lblOpStatus.setText("");
                        lblOpStatus.setVisible(false);
                        lblOpStatus.setManaged(false);
                    }));
            timer.setCycleCount(1);
            timer.play();
        }
    }

    /** Arranca el watcher en vivo de cambios externos en los archivos abiertos. */
    private void startExternalFileWatcher() {
        if (tabPane == null || externalFileChangeMonitor != null) return;
        externalFileChangeMonitor = new ExternalFileChangeMonitor();
        externalFileWatcherTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> pollExternalFileChanges()));
        externalFileWatcherTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        externalFileWatcherTimer.play();
    }

    /**
     * Compara el disco con las huellas registradas de las pestañas abiertas y
     * actualiza el aviso en la barra inferior cuando hay un cambio externo.
     * Se ejecuta en el hilo de JavaFX (Timeline), leyendo ficheros pequeños;
     * los archivos grandes quedan excluidos del aviso en vivo.
     */
    private void pollExternalFileChanges() {
        if (externalFileChangeMonitor == null || tabPane == null) return;
        Map<File, String> registrations = new HashMap<>();
        for (Tab tab : tabPane.getTabs()) {
            File file = tabFileMap.get(tab);
            String registered = fxUtils.getDiskFingerprint(tab);
            if (file != null && registered != null) {
                registrations.put(file, registered);
            }
        }
        externalFileChangeMonitor.refresh(registrations);
        updateExternalChangeBanner();
    }

    /** Sincroniza el banner de cambio externo con el estado actual del monitor. */
    private void updateExternalChangeBanner() {
        if (lblExternalChange == null || externalFileChangeMonitor == null) return;
        Set<File> changed = externalFileChangeMonitor.currentExternalChanges();
        if (changed.isEmpty()) {
            hideExternalChangeBanner();
            return;
        }
        File representative = externalFileChangeMonitor.lastNotifiedFile();
        if (representative == null || !changed.contains(representative)) {
            representative = changed.iterator().next();
        }
        if (representative.equals(pendingExternalChangeFile)) {
            return;
        }
        pendingExternalChangeFile = representative;
        String fileName = representative.getName();
        lblExternalChange.setText(msgFmt("external.change.banner",
            "''{0}'' cambió fuera del editor — clic para recargar", fileName));
        lblExternalChange.setTooltip(new Tooltip(msg("external.change.banner.tooltip",
            "El archivo se modificó fuera de Samaruc (p. ej. por opencode). Haz clic para recargar desde disco.")));
        lblExternalChange.setVisible(true);
        lblExternalChange.setManaged(true);
    }

    private void hideExternalChangeBanner() {
        pendingExternalChangeFile = null;
        if (lblExternalChange != null) {
            lblExternalChange.setVisible(false);
            lblExternalChange.setManaged(false);
        }
    }

    @FXML
    private void onExternalChangeBannerClicked() {
        File file = pendingExternalChangeFile;
        if (file == null) return;
        Tab tab = null;
        for (Tab t : tabPane.getTabs()) {
            if (file.equals(tabFileMap.get(t))) {
                tab = t;
                break;
            }
        }
        CodeArea codeArea = tab != null ? fxUtils.getCodeArea(tab) : null;
        if (tab != null && codeArea != null) {
            reloadTabFromDisk(tab, file, codeArea);
        }
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
        if (menuGit             != null) menuGit.setText(bundle.getString("menu.git"));
        if (menuAyuda           != null) menuAyuda.setText(bundle.getString("menu.help"));
        if (menuItemNuevoProyecto != null) menuItemNuevoProyecto.setText(bundle.getString("button.newProject"));
        if (menuItemAbrirProyecto != null) menuItemAbrirProyecto.setText(bundle.getString("button.openProject"));
        if (menuItemNuevo       != null) menuItemNuevo.setText(bundle.getString("button.new"));
        if (menuItemNuevoPlantilla != null) menuItemNuevoPlantilla.setText(bundle.getString("menu.file.newFromTemplate"));
        if (menuItemNuevoTraduccion != null) menuItemNuevoTraduccion.setText(bundle.getString("menu.file.newTranslator"));
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
        if (menuItemRenameSymbol != null) menuItemRenameSymbol.setText(bundle.getString("menu.rename.symbol"));
        if (menuItemExtractConstant != null) menuItemExtractConstant.setText(bundle.getString("menu.refactor.extractConstant"));
        if (menuItemExtractFunction != null) menuItemExtractFunction.setText(bundle.getString("menu.refactor.extractFunction"));
        if (menuItemTileEditor  != null) menuItemTileEditor.setText(bundle.getString("menu.tools.tileEditor"));
        if (menuItemRomTextTranslator != null) menuItemRomTextTranslator.setText(bundle.getString("menu.tools.romTextTranslator"));
        if (menuItemGitHubRepos != null) menuItemGitHubRepos.setText(bundle.getString("menu.tools.githubRepos"));
        if (menuItemGitPanel    != null) menuItemGitPanel.setText(bundle.getString("menu.git.panel"));
        if (menuItemViewDiff    != null) menuItemViewDiff.setText(bundle.getString("menu.git.viewDiff"));
        if (menuItemShareToGithub != null) menuItemShareToGithub.setText(bundle.getString("menu.git.share"));
        if (menuItemPublishRelease != null) menuItemPublishRelease.setText(bundle.containsKey("menu.git.release") ? bundle.getString("menu.git.release") : "Publicar release...");
        if (menuItemIssues != null) menuItemIssues.setText(bundle.containsKey("menu.git.issues") ? bundle.getString("menu.git.issues") : "Issues...");
        if (btnGit              != null) btnGit.setTooltip(new Tooltip(msg("tooltip.git.openPanel", "Abrir panel Git")));
        if (menuItemGoToLine    != null) menuItemGoToLine.setText(bundle.getString("menu.goto.line"));
        if (menuItemFind        != null) menuItemFind.setText(bundle.getString("menu.find.file"));
        if (menuItemFindProject != null) menuItemFindProject.setText(bundle.getString("menu.find.project"));
        if (menuSnippets          != null) menuSnippets.setText(bundle.getString("menu.edit.snippets"));
        if (menuItemInsertSnippet != null) menuItemInsertSnippet.setText(bundle.getString("menu.edit.snippets.insert"));
        if (menuItemManageSnippets != null) menuItemManageSnippets.setText(bundle.getString("menu.edit.snippets.manage"));
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
        applyEditorThemeToOpenTabs();
    }

    /**
     * Mapea un id de modo al valor de apariencia legacy usado por
     * {@code applyApplicationAppearance} para elegir la hoja del shell:
     * los modos de base clara usan {@code app.css} (classic) y el resto
     * {@code app-dark.css}.
     */
    private String themeToShellAppearance(String themeId) {
        EditorTheme theme = themeService.getThemeOrDefault(themeId);
        return theme != null && !theme.isDarkBase()
            ? ConfigModel.EDITOR_APPEARANCE_CLASSIC
            : ConfigModel.EDITOR_APPEARANCE_MODERN_DARK;
    }

    private void applyEditorThemeToOpenTabs(String themeId) {
        if (tabPane == null) return;
        EditorTheme theme = themeService.getThemeOrDefault(themeId);
        String generatedCss = ThemeCssGenerator.generateCss(theme);
        for (Tab tab : tabPane.getTabs()) {
            CodeArea codeArea = fxUtils.getCodeArea(tab);
            if (codeArea != null) {
                fxUtils.applyGeneratedEditorTheme(codeArea, generatedCss);
            }
        }
    }

    private void applyEditorThemeToOpenTabs() {
        if (tabPane == null) return;
        String activeThemeId = themeService.resolveActiveThemeId(configModel);
        applyEditorThemeToOpenTabs(activeThemeId);
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

    // ---------------------------------------------------------------------
    // Paleta de comandos (Ctrl+Shift+P) y apertura rápida (Ctrl+P)
    // ---------------------------------------------------------------------

    private void showCommandPalette() {
        Stage stage = getStage();
        if (stage == null || tabPane == null) return;

        List<CommandPalette.Entry> commands = new ArrayList<>();
        String h = msg("palette.hint", null);

        commands.add(new CommandPalette.Entry(msg("menu.file.newFromTemplate", "Nuevo archivo desde plantilla"), h, () -> onNuevoArchivoDesdePlantilla(null)));
        commands.add(new CommandPalette.Entry(msg("button.new", "Nuevo"), h, () -> onNewTab(null)));
        commands.add(new CommandPalette.Entry(msg("button.open", "Abrir"), h, () -> onOpenFile(null)));
        commands.add(new CommandPalette.Entry(msg("button.save", "Guardar"), h, () -> onSaveFile(null)));
        commands.add(new CommandPalette.Entry(msg("button.saveAll", "Guardar todo"), h, () -> onSaveAllFiles(null)));
        commands.add(new CommandPalette.Entry(msg("button.saveAs", "Guardar como"), h, () -> onSaveAsFile(null)));
        commands.add(new CommandPalette.Entry(msg("button.close", "Cerrar"), h, () -> onCloseFile(null)));
        commands.add(new CommandPalette.Entry(msg("button.newProject", "Nuevo Proyecto"), h, () -> onNuevoProyecto(null)));
        commands.add(new CommandPalette.Entry(msg("button.openProject", "Abrir Proyecto"), h, () -> onAbrirProyecto(null)));

        commands.add(new CommandPalette.Entry(msg("button.undo", "Deshacer"), h, () -> onUndo(null)));
        commands.add(new CommandPalette.Entry(msg("button.redo", "Rehacer"), h, () -> onRedo(null)));
        commands.add(new CommandPalette.Entry(msg("button.cut", "Cortar"), h, () -> onCut(null)));
        commands.add(new CommandPalette.Entry(msg("button.copy", "Copiar"), h, () -> onCopy(null)));
        commands.add(new CommandPalette.Entry(msg("button.paste", "Pegar"), h, () -> onPaste(null)));
        commands.add(new CommandPalette.Entry(msg("menu.goto.line", "Ir a la linea"), h, () -> onGoToLine(null)));
        commands.add(new CommandPalette.Entry(msg("menu.find.file", "Buscar en archivo"), h, () -> onFindInFile(null)));
        commands.add(new CommandPalette.Entry(msg("menu.find.project", "Buscar en proyecto"), h, () -> onFindInProject(null)));
        commands.add(new CommandPalette.Entry(msg("menu.goto.definition", "Ir a definición"), h, () -> onGoToDefinition(null)));
        commands.add(new CommandPalette.Entry(msg("button.compile", "Compilar"), h, () -> onCompilar(null)));
        commands.add(new CommandPalette.Entry(msg("button.compileAndRun", "Compilar y ejecutar"), h, () -> onCompilarYEjecutar(null)));
        commands.add(new CommandPalette.Entry(msg("button.run", "Ejecutar"), h, () -> onEjecutar(null)));
        commands.add(new CommandPalette.Entry(msg("menu.tools.tileEditor", "Editor de Sprites"), h, () -> onOpenTileEditor(null)));
        commands.add(new CommandPalette.Entry(msg("menu.tools.tilemapEditor", "Editor de Mapas de Tiles"), h, () -> onOpenTilemapEditor(null)));
        commands.add(new CommandPalette.Entry(msg("menu.tools.romTextTranslator", "Traductor de texto Game Boy"), h, () -> onOpenRomTextTranslator(null)));
        commands.add(new CommandPalette.Entry(msg("menu.tools.githubRepos", "Repositorios de GitHub"), h, () -> onOpenGitHubRepoBrowser(null)));
        commands.add(new CommandPalette.Entry(msg("menu.recent.files", "Archivos recientes"), h, this::showRecentFilesInPalette));
        commands.add(new CommandPalette.Entry(msg("menu.help.manual", "Manual"), h, () -> onOpenManual()));
        commands.add(new CommandPalette.Entry(msg("menu.help.gbdkDocs", "Documentación GBDK"), h, () -> onOpenGbdkDocs()));
        commands.add(new CommandPalette.Entry(msg("menu.help.z88dkDocs", "Documentación Z88DK"), h, () -> onOpenZ88dkDocs()));
        commands.add(new CommandPalette.Entry(msg("menu.config", "Configuración"), h, () -> onOpenConfig(null)));

        String prompt = msg("palette.commands.prompt", "Escriba un comando...");
        CommandPalette.show(stage, activePaletteStylesheets(), prompt, commands);
    }

    private void showQuickOpen() {
        Stage stage = getStage();
        if (stage == null) return;

        if (currentProjectDir == null || !currentProjectDir.isDirectory()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(msg("palette.noProject.title", "Sin proyecto"));
            alert.setHeaderText(null);
            alert.setContentText(msg("palette.noProject.content", "Abre o crea un proyecto antes de usar la apertura rápida."));
            alert.showAndWait();
            return;
        }

        List<CommandPalette.Entry> files = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(currentProjectDir.toPath())) {
            stream.filter(java.nio.file.Files::isRegularFile)
                .filter(p -> isQuickOpenCandidate(p))
                .sorted()
                .forEach(p -> {
                    String rel = currentProjectDir.toPath().relativize(p).toString();
                    File f = p.toFile();
                    files.add(new CommandPalette.Entry(rel, null, () -> openFileFromExplorer(f)));
                });
        } catch (java.io.IOException ignored) {
        }

        String prompt = msg("palette.open.prompt", "Abrir archivo...");
        CommandPalette.show(stage, activePaletteStylesheets(), prompt, files);
    }

    private void showRecentFilesInPalette() {
        Stage stage = getStage();
        if (stage == null) return;
        String emptyLabel = msg("menu.recent.empty", "Vacío");
        List<File> recents = loadRecentEntries(RECENT_FILES_PROPERTY, false);
        List<CommandPalette.Entry> entries = new ArrayList<>();
        if (recents == null || recents.isEmpty()) {
            entries.add(new CommandPalette.Entry(emptyLabel, null, () -> { }));
        } else {
            for (File f : recents) {
                File target = f;
                entries.add(new CommandPalette.Entry(formatRecentEntryLabel(target), target.getParent(), () -> openRecentFile(target)));
            }
        }
        String prompt = msg("palette.recent.prompt", "Archivos recientes");
        CommandPalette.show(stage, activePaletteStylesheets(), prompt, entries);
    }

    private static boolean isQuickOpenCandidate(java.nio.file.Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith(".")) return false;
        if (name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".asm")
            || name.endsWith(".s") || name.endsWith(".md") || name.endsWith(".markdown")
            || name.endsWith(".txt") || name.equals("makefile") || name.endsWith(".mk")
            || name.endsWith(".smt") || name.endsWith(".cfg")) {
            return !name.equals("dependency-reduced-pom.xml");
        }
        return false;
    }

    private java.util.List<String> activePaletteStylesheets() {
        java.util.List<String> sheets = new ArrayList<>();
        Scene scene = tabPane != null ? tabPane.getScene() : null;
        if (scene != null) {
            for (String s : scene.getStylesheets()) {
                if (s.endsWith("/app.css") || s.endsWith("/app-dark.css")) {
                    sheets.add(s);
                }
            }
        }
        if (sheets.isEmpty()) {
            String appearance = configModel != null ? configModel.getEditorAppearance() : null;
            String path = ConfigModel.EDITOR_APPEARANCE_CLASSIC.equalsIgnoreCase(appearance)
                ? APP_STYLESHEET_CLASSIC : APP_STYLESHEET_DARK;
            String resolved = resolveStylesheet(path);
            if (resolved != null) sheets.add(resolved);
        }
        return sheets;
    }

    private void registerGlobalShortcuts(Scene scene) {
        if (scene == null) return;

        scene.getAccelerators().put(ACCEL_COMMAND_PALETTE, this::showCommandPalette);
        scene.getAccelerators().put(ACCEL_QUICK_OPEN, this::showQuickOpen);

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
        scene.getAccelerators().put(ACCEL_RENAME_SYMBOL, () -> onRenameSymbol(null));
        scene.getAccelerators().put(ACCEL_FOLD, () -> onFoldBlock(null));
        scene.getAccelerators().put(ACCEL_UNFOLD, () -> onUnfoldBlock(null));

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
