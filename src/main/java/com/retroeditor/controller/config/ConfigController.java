package com.retroeditor.controller.config;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.LanguageMarketplaceItem;
import com.retroeditor.model.WorkspaceModel;
import com.retroeditor.model.PluginMarketplaceItem;
import com.retroeditor.model.EditorTheme;
import com.retroeditor.model.BuiltInThemes;
import com.retroeditor.plugin.PluginInfo;
import com.retroeditor.plugin.PluginManager;
import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.AppLogger;
import com.retroeditor.service.LanguageMarketplaceService;
import com.retroeditor.service.LanguageService;
import com.retroeditor.service.PluginApplicationService;
import com.retroeditor.service.PluginMarketplaceService;
import com.retroeditor.service.PropertiesConfigRepository;
import com.retroeditor.service.ThemeCssGenerator;
import com.retroeditor.service.ThemeService;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.WorkspaceService;
import com.retroeditor.service.github.GitHubApiService;
import com.retroeditor.service.github.GitHubAuthService;
import com.retroeditor.service.syntax.SyntaxHighlighter;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

public class ConfigController {
    private static final String COMPILER_GBDK      = "GBDK";
    private static final String COMPILER_Z88DK     = "z88dk";
    private static final String COMPILER_GCC       = "gcc";
    private static final String COMPILER_MAKEFILE  = "Makefile";
    private static final String LEGACY_DISPLAY_Z88DK = "Z88DK";
    private static final String LEGACY_SPECTRUM    = "Spectrum";
    private static final String Z88DK_PROFILE_SPECTRUM = "spectrum";
    private static final String Z88DK_PROFILE_CPC = "cpc";
    private static final String Z88DK_PROFILE_SMS = "sms";
    private static final String Z88DK_PROFILE_GAME_GEAR = "gg";
    private static final String Z88DK_PROFILE_MSX = "msx";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY   = "JSpeccy";
    private static final String EMULATOR_CAP32 = "Cap32";
    private static final String EMULATOR_OPENMSX = ConfigModel.EMULATOR_OPENMSX;
    private static final String EMULATOR_MESEN = ConfigModel.EMULATOR_MESEN;
    private static final String EMULATOR_ALTIRRA   = ConfigModel.EMULATOR_ALTIRRA;
    private static final String EMULATOR_VICE      = ConfigModel.EMULATOR_VICE;
    private static final String CC65_TARGET_ATARI  = ConfigModel.CC65_TARGET_ATARI;
    private static final String CC65_TARGET_C64    = ConfigModel.CC65_TARGET_C64;
    private static final String CC65_TARGET_NES    = ConfigModel.CC65_TARGET_NES;

    @FXML private TabPane          tabPaneConfig;
    @FXML private TabPane          tabPaneCompilerOptions;
    @FXML private TextField        txtCompilador;
    @FXML private TextField        txtSpectrumBin;
    @FXML private TextField        txtCpcBin;
    @FXML private TextField        txtGbdkDefines;
    @FXML private TextField        txtGbdkIncludes;
    @FXML private TextField        txtGbdkExtraArgs;
    @FXML private TextField        txtZ88dkSpectrumTarget;
    @FXML private CheckBox         chkZ88dkSpectrumCreateApp;
    @FXML private TextField        txtZ88dkSpectrumDefines;
    @FXML private TextField        txtZ88dkSpectrumIncludes;
    @FXML private TextField        txtZ88dkSpectrumExtraArgs;
    @FXML private ComboBox<String> comboZ88dkSpectrumClib;
    @FXML private CheckBox         chkZ88dkSpectrumCrtOrgCode;
    @FXML private TextField        txtZ88dkCpcTarget;
    @FXML private CheckBox         chkZ88dkCpcCreateApp;
    @FXML private TextField        txtZ88dkCpcDefines;
    @FXML private TextField        txtZ88dkCpcIncludes;
    @FXML private TextField        txtZ88dkCpcExtraArgs;
    @FXML private ComboBox<String> comboZ88dkCpcClib;
    @FXML private CheckBox         chkZ88dkCpcCrtOrgCode;
    @FXML private TextField        txtSmsBin;
    @FXML private TextField        txtZ88dkSmsTarget;
    @FXML private CheckBox         chkZ88dkSmsCreateApp;
    @FXML private TextField        txtZ88dkSmsDefines;
    @FXML private TextField        txtZ88dkSmsIncludes;
    @FXML private TextField        txtZ88dkSmsExtraArgs;
    @FXML private ComboBox<String> comboZ88dkSmsClib;
    @FXML private TextField        txtGgBin;
    @FXML private TextField        txtZ88dkGgTarget;
    @FXML private CheckBox         chkZ88dkGgCreateApp;
    @FXML private TextField        txtZ88dkGgDefines;
    @FXML private TextField        txtZ88dkGgIncludes;
    @FXML private TextField        txtZ88dkGgExtraArgs;
    @FXML private ComboBox<String> comboZ88dkGgClib;
    @FXML private TextField        txtMsxBin;
    @FXML private TextField        txtZ88dkMsxTarget;
    @FXML private CheckBox         chkZ88dkMsxCreateApp;
    @FXML private TextField        txtZ88dkMsxDefines;
    @FXML private TextField        txtZ88dkMsxIncludes;
    @FXML private TextField        txtZ88dkMsxExtraArgs;
    @FXML private ComboBox<String> comboZ88dkMsxClib;
    @FXML private ComboBox<String> comboGbdkOptLevel;
    @FXML private CheckBox         chkGbdkOptSpeed;
    @FXML private CheckBox         chkGbdkOptSize;
    @FXML private CheckBox         chkProjectDetectAutoApply;
    @FXML private CheckBox         chkEnableLogs;
    @FXML private CheckBox         chkShowHomeOnStartup;
    @FXML private CheckBox         chkSyntaxCheckOnSave;
    @FXML private TextField        txtProjectDetectThreshold;
    @FXML private TextField        txtMakeExecutable;
    @FXML private TextField        txtMakeBuildTarget;
    @FXML private TextField        txtMakeRunTarget;
    @FXML private TextField        txtMakeExtraArgs;
    @FXML private TextField        txtGccBin;
    @FXML private TextField        txtGccDefines;
    @FXML private TextField        txtGccIncludes;
    @FXML private TextField        txtGccExtraArgs;
    @FXML private ComboBox<String> comboGccOptLevel;
    @FXML private Tab              tabCompilerGcc;
    @FXML private Label            lblGccBin;
    @FXML private Label            lblGccOpts;
    @FXML private Label            lblGccOptLevel;
    @FXML private Label            lblGccDefines;
    @FXML private Label            lblGccIncludes;
    @FXML private Label            lblGccExtraArgs;
    @FXML private Button           btnSeleccionarGccBin;
    @FXML private Button           btnSaveGccOpts;
    @FXML private TextField        txtCc65Bin;
    @FXML private TextField        txtAltirraBin;
    @FXML private TextField        txtViceBin;
    @FXML private TextField        txtCc65Defines;
    @FXML private TextField        txtCc65Includes;
    @FXML private TextField        txtCc65ExtraArgs;
    @FXML private ComboBox<String> comboCc65OptLevel;
    @FXML private Tab              tabCompilerCc65;
    @FXML private Label            lblCc65Bin;
    @FXML private Label            lblAltirraBin;
    @FXML private Label            lblViceBin;
    @FXML private Label            lblCc65Opts;
    @FXML private Label            lblCc65OptLevel;
    @FXML private Label            lblCc65Defines;
    @FXML private Label            lblCc65Includes;
    @FXML private Label            lblCc65ExtraArgs;
    @FXML private Button           btnSeleccionarCc65Bin;
    @FXML private Button           btnSeleccionarAltirraBin;
    @FXML private Button           btnSeleccionarViceBin;
    @FXML private Button           btnSaveCc65Opts;
    @FXML private Tab              tabIdioma;
    @FXML private Tab              tabAppearance;
    @FXML private Tab              tabCompilador;
    @FXML private Tab              tabCompilerGbdk;
    @FXML private Tab              tabCompilerZ88dkSpectrum;
    @FXML private Tab              tabCompilerZ88dkCpc;
    @FXML private Tab              tabCompilerZ88dkSms;
    @FXML private Tab              tabCompilerZ88dkGameGear;
    @FXML private Tab              tabCompilerZ88dkMsx;
    @FXML private Tab              tabCompilerMakefile;
    @FXML private Tab              tabPlugins;
    @FXML private Tab              tabGitHub;
    @FXML private PasswordField    txtGithubToken;
    @FXML private Label            lblGithubToken;
    @FXML private Label            lblGithubTokenWarning;
    @FXML private Label            lblGithubStatus;
    @FXML private ProgressIndicator progressGithubVerify;
    @FXML private Button           btnVerifyGithub;
    @FXML private Button           btnSaveGithub;
    @FXML private Button           btnClearGithubToken;
    @FXML private Label            lblError;
    @FXML private Label            lblIdioma;
    @FXML private Label            lblEditorAppearance;
    @FXML private Label            lblThemeMode;
    @FXML private Label            lblThemeName;
    @FXML private Label            lblThemeFont;
    @FXML private Label            lblThemeFontSize;
    @FXML private Label            lblThemePreview;
    @FXML private Label            lblReadmeLanguage;
    @FXML private Label            lblCompilador;
    @FXML private Label            lblGbdkBundled;
    @FXML private Label            lblZ88dkSpectrumBundled;
    @FXML private Label            lblZ88dkCpcBundled;
    @FXML private Label            lblGbdkOpts;
    @FXML private Label            lblGbdkOptLevel;
    @FXML private Label            lblZ88dkSpectrumBin;
    @FXML private Label            lblZ88dkSpectrumOpts;
    @FXML private Label            lblZ88dkSpectrumClib;
    @FXML private Label            lblGbdkDefines;
    @FXML private Label            lblGbdkIncludes;
    @FXML private Label            lblGbdkExtraArgs;
    @FXML private Label            lblZ88dkSpectrumTarget;
    @FXML private Label            lblZ88dkSpectrumDefines;
    @FXML private Label            lblZ88dkSpectrumIncludes;
    @FXML private Label            lblZ88dkSpectrumExtraArgs;
    @FXML private Label            lblZ88dkCpcBin;
    @FXML private Label            lblZ88dkCpcOpts;
    @FXML private Label            lblZ88dkCpcClib;
    @FXML private Label            lblZ88dkCpcTarget;
    @FXML private Label            lblZ88dkCpcDefines;
    @FXML private Label            lblZ88dkCpcIncludes;
    @FXML private Label            lblZ88dkCpcExtraArgs;
    @FXML private Label            lblProjectDetectThreshold;
    @FXML private Label            lblMakeExecutable;
    @FXML private Label            lblMakeBuildTarget;
    @FXML private Label            lblMakeRunTarget;
    @FXML private Label            lblMakeExtraArgs;
    @FXML private Label            lblTituloConfig;
    @FXML private Button           btnGuardarIdioma;
    @FXML private Button           btnGuardarAppearance;
    @FXML private Button           btnCerrarConfig;
    @FXML private Button           btnGuardarCompilador;
    @FXML private Button           btnGuardarWorkspace;
    @FXML private Button           btnSaveMakeOpts;
    @FXML private ComboBox<String> comboIdioma;
    @FXML private ComboBox<EditorTheme> comboThemeMode;
    @FXML private TextField        txtThemeName;
    @FXML private ComboBox<String> comboFontFamily;
    @FXML private Spinner<Double>  spFontSize;
    @FXML private CheckBox         chkLineNumberAuto;
    @FXML private Spinner<Double>  spLineNumberSize;
    @FXML private Button           btnDuplicateTheme;
    @FXML private Button           btnDeleteTheme;
    @FXML private Button           btnResetTheme;
    @FXML private VBox             vboxThemeColors;
    @FXML private VBox             vboxThemePreview;
    @FXML private ComboBox<String> comboTerminalShell;
    @FXML private ComboBox<String> comboReadmeLanguage;
    @FXML private ComboBox<String> comboCompilador;

    @FXML private Button           btnSeleccionarSpectrumBin;
    @FXML private Button           btnGuardarSpectrumBin;
    @FXML private Button           btnSeleccionarCpcBin;
    @FXML private Button           btnGuardarCpcBin;

    @FXML private javafx.scene.control.ListView<String> lstPlugins;
    @FXML private javafx.scene.control.Button btnInstallPlugin;
    @FXML private javafx.scene.control.Button btnEnable;
    @FXML private javafx.scene.control.Button btnDisable;
    @FXML private javafx.scene.control.Button btnRemovePlugin;
    @FXML private javafx.scene.control.Button btnOpenFolder;
    @FXML private javafx.scene.control.Button btnRefreshPlugins;
    @FXML private javafx.scene.control.Label lblPluginInfo;
    @FXML private Label lblPluginsInstalled;
    @FXML private ProgressIndicator progressPluginsReload;
    @FXML private Label lblMarketplace;
    @FXML private Label lblMarketplaceUrl;
    @FXML private TextField txtMarketplaceUrl;
    @FXML private Button btnSaveMarketplaceUrl;
    @FXML private javafx.scene.control.ListView<String> lstMarketplacePlugins;
    @FXML private javafx.scene.control.Button btnMarketplaceRefresh;
    @FXML private javafx.scene.control.Button btnMarketplaceInstall;
    @FXML private javafx.scene.control.Label lblMarketplaceInfo;
    @FXML private ProgressIndicator progressMarketplace;

    // Marketplace Idiomas
    @FXML private ListView<String> lstMarketplaceIdiomas;
    @FXML private Label lblMarketplaceIdiomasInfo;
    @FXML private Button btnInstallIdioma;
    @FXML private Button btnRefreshIdiomas;
    @FXML private ProgressIndicator progressIdiomas;
    @FXML private Label lblMarketplaceIdiomas;

    private Runnable onCloseCallback;
    private Runnable onLanguageChanged;
    private Runnable onAppearanceApplied;
    private boolean syncingCompilerUi = false;
    private final Map<String, String> terminalShellCodeByLabel = new HashMap<>();

    private ThemeService themeService = new ThemeService();
    private EditorTheme editingTheme;
    private boolean syncingThemeUi = false;
    private final Map<String, ColorPicker> themeColorPickers = new HashMap<>();
    private final Map<String, TextField> themeColorFields = new HashMap<>();
    private final List<CodeArea> previewAreas = new ArrayList<>();
    private final SyntaxHighlighter previewHighlighter = new SyntaxHighlighter();

    private ConfigModel configModel = new ConfigModel();
    private ConfigRepository configRepository = new PropertiesConfigRepository();
    private WorkspaceService workspaceService = new WorkspaceService();
    private File projectRoot;
    private PluginApplicationService pluginApplicationService = new PluginApplicationService();
    private PluginMarketplaceService pluginMarketplaceService = new PluginMarketplaceService();
    private LanguageMarketplaceService languageMarketplaceService = new LanguageMarketplaceService();
    private LanguageService languageService = new LanguageService();

    private final File        configFile  = new File(System.getProperty("user.home"), ".retroeditor.properties");
    private final File        pluginsDir  = new File(System.getProperty("user.dir"), "plugins");
    private final File        i18nDir     = new File(System.getProperty("user.home"), ".samaruc-editor/i18n");
    private List<PluginInfo>  discoveredPlugins = Collections.emptyList();
    private List<PluginMarketplaceItem> marketplacePlugins = Collections.emptyList();
    private List<LanguageMarketplaceItem> marketplaceLanguages = Collections.emptyList();
    private boolean pluginsReloading = false;
    private boolean marketplaceLoading = false;

    /**
     * Obtener el gestor de plugins de la aplicación.
     * @return
     */
    private PluginManager getPluginManager() {
        PluginManager pm = null;
        try { pm = com.retroeditor.MainApp.getPluginManager(); } catch (Throwable ignored) {}
        return pm;
    }

    /**
     * Establecer el modelo de configuración a utilizar.
     * @param configModel Modelo de configuración
     */
    public void setConfigModel(ConfigModel configModel) {
        if (configModel != null) this.configModel = configModel;
    }

    public void setProjectRoot(File root) {
        this.projectRoot = root;
    }

    public void setConfigRepository(ConfigRepository configRepository) {
        if (configRepository != null) this.configRepository = configRepository;
    }

    public void setPluginApplicationService(PluginApplicationService pluginApplicationService) {
        if (pluginApplicationService != null) this.pluginApplicationService = pluginApplicationService;
    }

    public void setPluginMarketplaceService(PluginMarketplaceService pluginMarketplaceService) {
        if (pluginMarketplaceService != null) this.pluginMarketplaceService = pluginMarketplaceService;
    }

    public void setThemeService(ThemeService themeService) {
        if (themeService != null) this.themeService = themeService;
    }

    private static record ThemeToken(String colorKey, String labelKey) {}
    private static record ThemeSection(String titleKey, List<ThemeToken> tokens) {
        static ThemeSection of(String titleKey, ThemeToken... tokens) {
            return new ThemeSection(titleKey, List.of(tokens));
        }
    }

    private static final List<ThemeSection> THEME_SECTIONS = List.of(
        ThemeSection.of("config.theme.section.editor",
            new ThemeToken(EditorTheme.KEY_EDITOR_BACKGROUND,     "config.theme.token.editor_background"),
            new ThemeToken(EditorTheme.KEY_EDITOR_TEXT,           "config.theme.token.editor_text"),
            new ThemeToken(EditorTheme.KEY_HIGHLIGHT_FILL,        "config.theme.token.highlight_fill"),
            new ThemeToken(EditorTheme.KEY_HIGHLIGHT_TEXT_FILL,   "config.theme.token.highlight_text_fill"),
            new ThemeToken(EditorTheme.KEY_CARET,                 "config.theme.token.caret"),
            new ThemeToken(EditorTheme.KEY_LINENO,                "config.theme.token.lineno"),
            new ThemeToken(EditorTheme.KEY_LINENO_BACKGROUND,     "config.theme.token.lineno_background"),
            new ThemeToken(EditorTheme.KEY_SELECTION,             "config.theme.token.selection"),
            new ThemeToken(EditorTheme.KEY_CURRENT_LINE,          "config.theme.token.current_line")),
        ThemeSection.of("config.theme.section.c",
            new ThemeToken(EditorTheme.KEY_SYNTAX_KEYWORD,        "config.theme.token.keyword"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_PREPROCESSOR,   "config.theme.token.preprocessor"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_COMMENT,        "config.theme.token.comment"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_STRING,         "config.theme.token.string"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_CHARACTER,      "config.theme.token.character"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_OPERATOR,       "config.theme.token.operator"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_ZX_FUNCTION,    "config.theme.token.zx_function"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_GBDK_FUNCTION,  "config.theme.token.gbdk_function")),
        ThemeSection.of("config.theme.section.asm",
            new ThemeToken(EditorTheme.KEY_SYNTAX_ASM_LABEL,      "config.theme.token.asm_label"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_ASM_DIRECTIVE,  "config.theme.token.asm_directive"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_ASM_INSTRUCTION,"config.theme.token.asm_instruction"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_ASM_REGISTER,   "config.theme.token.asm_register"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_ASM_NUMBER,     "config.theme.token.asm_number")),
        ThemeSection.of("config.theme.section.md",
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_HEADING,     "config.theme.token.md_heading"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_BLOCKQUOTE,  "config.theme.token.md_blockquote"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_LIST,        "config.theme.token.md_list"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_CHECKLIST,   "config.theme.token.md_checklist"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_PENDING, "config.theme.token.md_checklist_pending"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_DONE, "config.theme.token.md_checklist_done"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_TABLE,       "config.theme.token.md_table"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_LINK,        "config.theme.token.md_link"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_BOLD,        "config.theme.token.md_bold"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_ITALIC,      "config.theme.token.md_italic"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_CODE,        "config.theme.token.md_code"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_MD_CODE_BLOCK,  "config.theme.token.md_code_block")),
        ThemeSection.of("config.theme.section.json",
            new ThemeToken(EditorTheme.KEY_SYNTAX_JSON_KEY,       "config.theme.token.json_key"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_JSON_NUMBER,    "config.theme.token.json_number"),
            new ThemeToken(EditorTheme.KEY_SYNTAX_JSON_LITERAL,   "config.theme.token.json_literal")),
        ThemeSection.of("config.theme.section.diagnostico",
            new ThemeToken(EditorTheme.KEY_COMPILE_ERROR_LINE,    "config.theme.token.compile_error_line"),
            new ThemeToken(EditorTheme.KEY_COMPILE_WARNING_LINE,  "config.theme.token.compile_warning_line"),
            new ThemeToken(EditorTheme.KEY_GUTTER_ERROR,          "config.theme.token.gutter_error"),
            new ThemeToken(EditorTheme.KEY_GUTTER_WARNING,        "config.theme.token.gutter_warning"))
    );

    private void loadConfigFromDisk() {
        java.util.Properties loaded = configRepository.load(configFile);
        configModel.applyProperties(loaded);
        persistNormalizedLegacyConfigIfNeeded(loaded);
    }

    private void persistNormalizedLegacyConfigIfNeeded(java.util.Properties loadedProps) {
        if (loadedProps == null || loadedProps.isEmpty()) return;

        String compilerRaw = loadedProps.getProperty("compilador_seleccionado", "").trim();
        String profileRaw = loadedProps.getProperty("z88dk_profile", "").trim();
        String emulatorRaw = loadedProps.getProperty("emulador_seleccionado", "").trim();

        boolean legacyCompiler = "Spectrum".equalsIgnoreCase(compilerRaw)
            || "Z88DK".equalsIgnoreCase(compilerRaw)
            || compilerRaw.toLowerCase(Locale.ROOT).contains("z88dk (");

        boolean invalidProfile = !profileRaw.isEmpty()
            && !Z88DK_PROFILE_SPECTRUM.equalsIgnoreCase(profileRaw)
            && !Z88DK_PROFILE_CPC.equalsIgnoreCase(profileRaw);

        String normalizedCompiler = configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK);
        String normalizedProfile = configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM);
        String cc65Target = resolveCurrentCc65Target();
        String expectedEmulator = getEmulatorForCompiler(normalizedCompiler, normalizedProfile, cc65Target);
        boolean emulatorMismatch = !emulatorRaw.isEmpty() && !expectedEmulator.equalsIgnoreCase(emulatorRaw);

        List<String> migrationCauses = new ArrayList<>();
        if (legacyCompiler) {
            migrationCauses.add(tr("config.migration.legacy.cause.compiler", "compilador legacy detectado"));
        }
        if (invalidProfile) {
            migrationCauses.add(tr("config.migration.legacy.cause.profile", "perfil z88dk invalido"));
        }
        if (emulatorMismatch) {
            migrationCauses.add(tr("config.migration.legacy.cause.emulator", "emulador no alineado con el perfil"));
        }

        if (legacyCompiler || invalidProfile || emulatorMismatch) {
            persistConfig();
            String detail = String.join(", ", migrationCauses);
            AppLogger.logMonitor(
                "CONFIG",
                tr("config.migration.legacy.normalized.detail", "Se normalizo automaticamente una configuracion legacy de compilador. Causas:")
                    + " " + detail
            );
        }
    }

    private void persistConfig() {
        configRepository.save(configFile, configModel.toProperties());
    }

    /**
     * Recargar los campos de la interfaz de usuario desde el modelo de configuración actual.
     * Puede ser llamado después de setConfigModel(...) para refrescar el diálogo.
     */
    public void reloadFromModel() {
        configureCompilerCombo();
        applyCompilerSelectionFromModel();

        if (txtSpectrumBin != null) {
            txtSpectrumBin.setText(configModel.getConfigProperty("spectrum_bin", ""));
        }
        if (txtCpcBin != null) {
            txtCpcBin.setText(configModel.getConfigProperty("cpc_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }
        if (txtSmsBin != null) {
            txtSmsBin.setText(configModel.getConfigProperty("sms_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }
        if (txtGgBin != null) {
            txtGgBin.setText(configModel.getConfigProperty("gg_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }
        if (txtMsxBin != null) {
            txtMsxBin.setText(configModel.getConfigProperty("msx_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }

        loadLanguageSelectionsFromConfig();
        loadThemeUiFromConfig();
        loadTerminalShellFromConfig();

        if (chkEnableLogs != null) {
            chkEnableLogs.setSelected(configModel.isEnableLogs());
        }
        if (chkShowHomeOnStartup != null) {
            chkShowHomeOnStartup.setSelected(configModel.isShowHomeOnStartup());
        }
        if (chkSyntaxCheckOnSave != null) {
            chkSyntaxCheckOnSave.setSelected(configModel.isSyntaxCheckOnSave());
        }

        loadZ88dkFlagsFromConfig();
        loadGbdkOptsFromConfig();
        loadMakeOptsFromConfig();
        loadGccOptsFromConfig();
        loadCc65OptsFromConfig();
        loadProjectDetectionPrefsFromConfig();
        if (txtMarketplaceUrl != null) {
            txtMarketplaceUrl.setText(configModel.getConfigProperty("marketplace_catalog_url", PluginMarketplaceService.DEFAULT_MARKETPLACE_URL));
        }

        loadGithubFromConfig();

        if (btnGuardarWorkspace != null) {
            boolean hasActiveProject = (projectRoot != null && projectRoot.isDirectory());
            btnGuardarWorkspace.setVisible(hasActiveProject);
            btnGuardarWorkspace.setManaged(hasActiveProject);
        }

        refreshTexts(
            tabIdioma,
            tabCompilador,
            lblIdioma,
            lblCompilador,
            lblTituloConfig,
            btnCerrarConfig,
            btnGuardarIdioma,
            btnGuardarCompilador
        );
    }

    /**
     * Configura el callback que se ejecuta al cambiar el idioma.
     * @param callback
     */
    public void setOnLanguageChanged(Runnable callback) { this.onLanguageChanged = callback; }

    /**
     * Configura el callback que re-aplica el tema y la apariencia en el
     * editor principal tras guardar la pestaña Apariencia. Se invoca siempre,
     * incluso cuando el id de tema/apariencia no cambia y por tanto los
     * listeners de propiedad no se disparan.
     * @param callback
     */
    public void setOnAppearanceApplied(Runnable callback) { this.onAppearanceApplied = callback; }

    /**
     * Configura el callback que se ejecuta al cerrar la ventana de configuración.
     * @param callback
     */
    public void setOnCloseCallback(Runnable callback) { this.onCloseCallback = callback; }

    /**
     * Inicializa el controlador de configuración.
     */
    @FXML
    public void initialize() {
        loadConfigFromDisk();

        if (lblError != null) {
            lblError.setText("");
        }

        configureCompilerCombo();
        applyCompilerSelectionFromModel();

        if (txtSpectrumBin != null) {
            txtSpectrumBin.setText(configModel.getConfigProperty("spectrum_bin", ""));
        }
        if (txtCpcBin != null) {
            txtCpcBin.setText(configModel.getConfigProperty("cpc_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }
        if (txtSmsBin != null) {
            txtSmsBin.setText(configModel.getConfigProperty("sms_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }
        if (txtGgBin != null) {
            txtGgBin.setText(configModel.getConfigProperty("gg_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }
        if (txtMsxBin != null) {
            txtMsxBin.setText(configModel.getConfigProperty("msx_bin", configModel.getConfigProperty("spectrum_bin", "")));
        }

        loadLanguageSelectionsFromConfig();
        loadThemeUiFromConfig();

        loadZ88dkFlagsFromConfig();
        loadGbdkOptsFromConfig();
        loadMakeOptsFromConfig();
        loadGccOptsFromConfig();
        loadCc65OptsFromConfig();
        loadProjectDetectionPrefsFromConfig();
        if (txtMarketplaceUrl != null) {
            txtMarketplaceUrl.setText(configModel.getConfigProperty("marketplace_catalog_url", PluginMarketplaceService.DEFAULT_MARKETPLACE_URL));
        }

        loadGithubFromConfig();

        // Si no existe, crear carpeta de plugins
        try { pluginApplicationService.ensurePluginsDir(pluginsDir); } catch (Exception ignored) {}

        // Configurar lista de plugins
        if (lstPlugins != null) {
            lstPlugins.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onPluginSelected(newV));
        }

        if (lstMarketplacePlugins != null) {
            lstMarketplacePlugins.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onMarketplaceSelected());
        }

        updatePluginButtons(null);
        updateMarketplaceButtons(null);

        refreshPluginsList();
        refreshMarketplaceList();

        refreshTexts(
            tabIdioma,
            tabCompilador,
            lblIdioma,
            lblCompilador,
            lblTituloConfig,
            btnCerrarConfig,
            btnGuardarIdioma,
            btnGuardarCompilador
        );
    }

    /**
     * Refrescar la lista de plugins en la interfaz de usuario.
     */
    private void refreshPluginsList() {
        if (lstPlugins == null) return;

        String selectedJar = null;
        PluginInfo selectedInfo = getSelectedPluginInfo();
        if (selectedInfo != null) selectedJar = selectedInfo.jarName;

        PluginManager pm = getPluginManager();

        discoveredPlugins = pluginApplicationService.discoverPlugins(pm);

        List<String> display = discoveredPlugins.stream().map(i -> String.format("%s %s", i.jarName, i.enabled ? "(habilitado)" : "(deshabilitado)"))
            .collect(Collectors.toList());

        lstPlugins.getItems().setAll(display);

        lblPluginInfo.setText(display.isEmpty()
            ? tr("config.plugins.noneInstalled", "No hay plugins instalados.")
            : tr("config.plugins.selectForDetails", "Selecciona un plugin para ver detalles."));

        if (selectedJar != null) {
            selectJarInList(selectedJar);
        } else {
            updatePluginButtons(null);
        }
    }

    /**
     * Maneja la selección de un plugin en la lista.
     * @param display Texto mostrado del plugin seleccionado.
     */
    private void onPluginSelected(String display) {
        PluginInfo pi = getSelectedPluginInfo();

        if (pi == null) {
            lblPluginInfo.setText(tr("config.plugins.infoUnavailable", "Informacion no disponible"));
            updatePluginButtons(null);
            return;
        }

        String infoText = tr("config.plugins.info.jar", "Jar") + ": "
                        + pi.jarName + "\n" + tr("config.plugins.info.state", "Estado") + ": "
                        + (pi.enabled
                            ? tr("config.plugins.state.enabled", "Habilitado")
                            : tr("config.plugins.state.disabled", "Deshabilitado"))
                        + "\n" + tr("config.plugins.info.implementations", "Implementaciones") + ": "
                        + String.join(", ", pi.implNames);

        lblPluginInfo.setText(infoText);
        updatePluginButtons(pi);
    }

    /**
     * Refrescar la lista de plugins desde el disco.
     * @param event
     */
    @FXML
    public void onRefreshPlugins(ActionEvent event) {
        if (pluginsReloading) return;

        setPluginsReloading(true);
        // Ejecutar en el siguiente ciclo para que el spinner llegue a renderizarse.
        Platform.runLater(() -> {
            PluginManager pm = getPluginManager();

            try {
                pluginApplicationService.reloadPlugins(pm);
                refreshPluginsList();
            } finally {
                setPluginsReloading(false);
            }
        });
    }

    private void setPluginsReloading(boolean reloading) {
        pluginsReloading = reloading;

        if (btnRefreshPlugins != null) btnRefreshPlugins.setDisable(reloading);
        PluginInfo selected = getSelectedPluginInfo();
        if (btnEnable != null) btnEnable.setDisable(reloading || selected == null || selected.enabled);
        if (btnDisable != null) btnDisable.setDisable(reloading || selected == null || !selected.enabled);
        if (btnRemovePlugin != null) btnRemovePlugin.setDisable(reloading || selected == null);
        if (btnInstallPlugin != null) btnInstallPlugin.setDisable(reloading);
        if (progressPluginsReload != null) {
            progressPluginsReload.setVisible(reloading);
            progressPluginsReload.setManaged(reloading);
        }

        if (lblPluginInfo != null) {
            if (reloading) {
                String lang = configModel.getConfigProperty("idioma", "es");
                ResourceBundle bundle = ResourceBundle.getBundle("i18n.MessagesBundle", Locale.forLanguageTag(lang));
                lblPluginInfo.setText(bundle.containsKey("config.plugins.reloading")
                    ? bundle.getString("config.plugins.reloading")
                    : "Recargando plugins...");
            } else {
                PluginInfo info = getSelectedPluginInfo();
                if (info == null) {
                    if (lstPlugins != null && !lstPlugins.getItems().isEmpty()) {
                        lblPluginInfo.setText(tr("config.plugins.selectForDetails", "Selecciona un plugin para ver detalles."));
                    }
                } else {
                    onPluginSelected(lstPlugins != null ? lstPlugins.getSelectionModel().getSelectedItem() : null);
                }
            }
        }
    }

    private String tr(String key, String fallback) {
        String lang = configModel.getConfigProperty("idioma", "es");
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.MessagesBundle", Locale.forLanguageTag(lang));
        return bundle.containsKey(key) ? bundle.getString(key) : fallback;
    }

    /**
     * Abrir la carpeta de plugins en el explorador de archivos.
     * @param event
     */
    @FXML
    public void onOpenPluginsFolder(ActionEvent event) {

        try {
            pluginApplicationService.ensurePluginsDir(pluginsDir);

            Desktop.getDesktop().open(pluginsDir);

        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Instalar un nuevo plugin desde un archivo JAR.
     * @param event
     */
    @FXML
    public void onInstallPlugin(ActionEvent event) {

        FileChooser fc = new FileChooser();

        fc.setTitle(tr("config.plugins.install.dialogTitle", "Seleccionar JAR de plugin"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));

        File selected = fc.showOpenDialog(btnInstallPlugin.getScene().getWindow());

        if (selected != null) {
            try {
                pluginApplicationService.installPluginJar(selected, pluginsDir);
                UserActionMonitor.pluginInstalled(selected.getName());

                refreshPluginsList();

            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    /**
     * Habilitar un plugin seleccionado.
     * @param event
     */
    @FXML
    public void onEnablePlugin(ActionEvent event) {
        PluginInfo pi = getSelectedPluginInfo();
        if (pi == null) return;

        // Evita recargas innecesarias que provocan duplicado de UI en plugins no idempotentes.
        if (pi.enabled) {
            updatePluginButtons(pi);
            return;
        }

        PluginManager pm = getPluginManager();
        String jarName = pi.jarName;

        pluginApplicationService.setPluginEnabledAndReload(pm, jarName, true);

        UserActionMonitor.pluginEnabled(jarName);

        refreshPluginsList();
        selectJarInList(jarName);
    }

    /**
     * Deshabilitar un plugin seleccionado.
     * @param event
     */
    @FXML
    public void onDisablePlugin(ActionEvent event) {
        PluginInfo pi = getSelectedPluginInfo();
        if (pi == null) return;

        if (!pi.enabled) {
            updatePluginButtons(pi);
            return;
        }

        String jarName = pi.jarName;
        PluginManager pm = getPluginManager();

        pluginApplicationService.setPluginEnabledAndReload(pm, jarName, false);

        UserActionMonitor.pluginDisabled(jarName);


        refreshPluginsList();
        selectJarInList(jarName);
    }

    @FXML
    public void onRemovePlugin(ActionEvent event) {
        PluginInfo pi = getSelectedPluginInfo();
        if (pi == null || pluginsReloading) return;

        if (!confirmPluginRemoval(pi.jarName)) {
            return;
        }

        PluginManager pm = getPluginManager();
        if (pm != null) {
            pm.setJarEnabled(pi.jarName, false);
        }

        boolean deleted = pluginApplicationService.deletePluginJar(pluginsDir, pi.jarName);
        if (!deleted) {
            if (lblPluginInfo != null) {
                lblPluginInfo.setText(tr("config.plugins.remove.error", "No se pudo eliminar el plugin seleccionado."));
            }
            return;
        }

        pluginApplicationService.reloadPlugins(pm);
        UserActionMonitor.pluginDisabled(pi.jarName);

        refreshPluginsList();
        if (lblPluginInfo != null) {
            lblPluginInfo.setText(tr("config.plugins.remove.ok", "Plugin eliminado correctamente."));
        }
    }

    private PluginInfo getSelectedPluginInfo() {
        if (lstPlugins == null) return null;

        int idx = lstPlugins.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= discoveredPlugins.size()) return null;

        return discoveredPlugins.get(idx);
    }

    private void updatePluginButtons(PluginInfo info) {
        if (btnEnable == null || btnDisable == null) return;

        if (pluginsReloading) {
            btnEnable.setDisable(true);
            btnDisable.setDisable(true);
            if (btnRemovePlugin != null) btnRemovePlugin.setDisable(true);
            return;
        }

        if (info == null) {
            btnEnable.setDisable(true);
            btnDisable.setDisable(true);
            if (btnRemovePlugin != null) btnRemovePlugin.setDisable(true);
            return;
        }

        btnEnable.setDisable(info.enabled);
        btnDisable.setDisable(!info.enabled);
        if (btnRemovePlugin != null) btnRemovePlugin.setDisable(false);
    }

    private boolean confirmPluginRemoval(String jarName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(tr("config.plugins.remove.confirm.title", "Confirmar eliminacion"));
        alert.setHeaderText(tr("config.plugins.remove.confirm.header", "Vas a eliminar un plugin instalado."));
        alert.setContentText(
            tr("config.plugins.remove.confirm.body", "Plugin") + ": " + safe(jarName, "-") + "\n"
                + tr("config.plugins.remove.confirm.warning", "Esta accion no se puede deshacer.")
        );

        if (btnRemovePlugin != null && btnRemovePlugin.getScene() != null) {
            alert.initOwner(btnRemovePlugin.getScene().getWindow());
        }

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void selectJarInList(String jarName) {
        if (jarName == null || lstPlugins == null) return;

        for (int i = 0; i < discoveredPlugins.size(); i++) {
            PluginInfo info = discoveredPlugins.get(i);
            if (!jarName.equals(info.jarName)) continue;
            lstPlugins.getSelectionModel().select(i);
            onPluginSelected(lstPlugins.getSelectionModel().getSelectedItem());
            return;
        }
    }

    @FXML
    public void onRefreshMarketplace(ActionEvent event) {
        refreshMarketplaceList();
    }

    @FXML
    public void onSaveMarketplaceUrl(ActionEvent event) {
        String current = txtMarketplaceUrl != null ? txtMarketplaceUrl.getText() : "";
        String normalized = current != null && !current.isBlank()
            ? current.trim()
            : PluginMarketplaceService.DEFAULT_MARKETPLACE_URL;

        configModel.setConfigProperty("marketplace_catalog_url", normalized);
        persistConfig();

        if (txtMarketplaceUrl != null) {
            txtMarketplaceUrl.setText(normalized);
        }

        if (lblMarketplaceInfo != null) {
            lblMarketplaceInfo.setText(tr("config.plugins.marketplace.url.saved", "URL del marketplace guardada."));
        }
    }

    private void refreshMarketplaceList() {
        if (marketplaceLoading) return;

        setMarketplaceLoading(true);

        new Thread(() -> {
            try {
                String marketplaceUrl = configModel.getConfigProperty("marketplace_catalog_url", PluginMarketplaceService.DEFAULT_MARKETPLACE_URL);
                List<PluginMarketplaceItem> items = pluginMarketplaceService.fetchCatalog(marketplaceUrl);
                Platform.runLater(() -> applyMarketplaceItems(items));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    marketplacePlugins = Collections.emptyList();
                    if (lstMarketplacePlugins != null) lstMarketplacePlugins.getItems().clear();
                    if (lblMarketplaceInfo != null) {
                        lblMarketplaceInfo.setText(tr("config.plugins.marketplace.error", "No se pudo cargar el marketplace.") + "\n" + ex.getMessage());
                    }
                    updateMarketplaceButtons(null);
                    setMarketplaceLoading(false);
                });
            }
        }, "marketplace-refresh-thread").start();
    }

    private void applyMarketplaceItems(List<PluginMarketplaceItem> items) {
        marketplacePlugins = items != null ? new ArrayList<>(items) : Collections.emptyList();

        if (lstMarketplacePlugins != null) {
            List<String> display = marketplacePlugins.stream().map(PluginMarketplaceItem::displayLine).collect(Collectors.toList());
            lstMarketplacePlugins.getItems().setAll(display);
        }

        if (lblMarketplaceInfo != null) {
            lblMarketplaceInfo.setText(marketplacePlugins.isEmpty()
                ? tr("config.plugins.marketplace.empty", "No hay plugins publicados en el marketplace.")
                : tr("config.plugins.marketplace.select", "Selecciona un plugin del marketplace para ver detalles."));
        }

        if (lstMarketplacePlugins != null && !lstMarketplacePlugins.getItems().isEmpty()) {
            lstMarketplacePlugins.getSelectionModel().selectFirst();
            onMarketplaceSelected();
        } else {
            updateMarketplaceButtons(null);
        }

        setMarketplaceLoading(false);
    }

    private void onMarketplaceSelected() {
        PluginMarketplaceItem item = getSelectedMarketplaceItem();
        updateMarketplaceButtons(item);

        if (lblMarketplaceInfo == null) return;
        if (item == null) {
            lblMarketplaceInfo.setText(tr("config.plugins.marketplace.select", "Selecciona un plugin del marketplace para ver detalles."));
            return;
        }

        String verified = item.verified()
            ? tr("config.plugins.marketplace.verifiedYes", "Si")
            : tr("config.plugins.marketplace.verifiedNo", "No");

        lblMarketplaceInfo.setText(
            tr("config.plugins.marketplace.info.name", "Nombre") + ": " + safe(item.name(), item.id()) + "\n"
                + tr("config.plugins.marketplace.info.version", "Version") + ": " + safe(item.version(), "-") + "\n"
                + tr("config.plugins.marketplace.info.author", "Autor") + ": " + safe(item.author(), "-") + "\n"
                + tr("config.plugins.marketplace.info.category", "Categoria") + ": " + safe(item.category(), "-") + "\n"
                + tr("config.plugins.marketplace.info.size", "Tamano") + ": " + safe(item.fileSize(), "-") + "\n"
                + tr("config.plugins.marketplace.info.verified", "Verificado") + ": " + verified + "\n"
                + tr("config.plugins.marketplace.info.description", "Descripcion") + ": " + safe(item.description(), "-")
        );
    }

    @FXML
    private void onRefreshMarketplaceIdiomas(ActionEvent event) {
        if (progressIdiomas != null) {
            progressIdiomas.setVisible(true);
            progressIdiomas.setManaged(true);
        }
        btnRefreshIdiomas.setDisable(true);

        new Thread(() -> {
            try {
                marketplaceLanguages = languageMarketplaceService.fetchCatalog(null);
                Platform.runLater(() -> {
                    lstMarketplaceIdiomas.getItems().clear();
                    for (LanguageMarketplaceItem item : marketplaceLanguages) {
                        lstMarketplaceIdiomas.getItems().add(item.displayLine());
                    }
                    if (progressIdiomas != null) {
                        progressIdiomas.setVisible(false);
                        progressIdiomas.setManaged(false);
                    }
                    btnRefreshIdiomas.setDisable(false);
                });
            } catch (Exception e) {
                System.err.println("Error al obtener catálogo de idiomas: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (progressIdiomas != null) {
                        progressIdiomas.setVisible(false);
                        progressIdiomas.setManaged(false);
                    }
                    btnRefreshIdiomas.setDisable(false);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error Marketplace");
                    alert.setHeaderText("No se pudo descargar el catálogo de idiomas");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    @FXML
    private void onInstallMarketplaceIdioma(ActionEvent event) {
        int idx = lstMarketplaceIdiomas.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= marketplaceLanguages.size()) {
            return;
        }

        LanguageMarketplaceItem item = marketplaceLanguages.get(idx);
        
        if (progressIdiomas != null) {
            progressIdiomas.setVisible(true);
            progressIdiomas.setManaged(true);
        }
        btnInstallIdioma.setDisable(true);

        new Thread(() -> {
            try {
                LanguageMarketplaceService.DownloadedLanguage downloaded = languageMarketplaceService.downloadLanguage(item);
                
                if (!i18nDir.exists()) i18nDir.mkdirs();
                File destFile = new File(i18nDir, downloaded.suggestedFileName());
                java.nio.file.Files.copy(downloaded.tempFile(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.nio.file.Files.deleteIfExists(downloaded.tempFile());

                Platform.runLater(() -> {
                    if (progressIdiomas != null) {
                        progressIdiomas.setVisible(false);
                        progressIdiomas.setManaged(false);
                    }
                    btnInstallIdioma.setDisable(false);
                    
                    // Recargar idiomas en el service
                    languageService.setExternalI18nDir(i18nDir);
                    
                    // Actualizar combo box de idiomas en la UI
                    loadLanguageSelectionsFromConfig();

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Idioma instalado");
                    alert.setHeaderText(null);
                    alert.setContentText("El idioma " + item.name() + " ha sido instalado correctamente. Ya puedes seleccionarlo en el menú desplegable.");
                    alert.showAndWait();
                });
            } catch (Exception e) {
                System.err.println("Error al instalar idioma: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (progressIdiomas != null) {
                        progressIdiomas.setVisible(false);
                        progressIdiomas.setManaged(false);
                    }
                    btnInstallIdioma.setDisable(false);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error de instalación");
                    alert.setHeaderText("No se pudo instalar el idioma");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    @FXML
    public void onInstallMarketplacePlugin(ActionEvent event) {
        PluginMarketplaceItem item = getSelectedMarketplaceItem();
        if (item == null || marketplaceLoading) return;

        if (!confirmMarketplaceInstall(item)) {
            if (lblMarketplaceInfo != null) {
                lblMarketplaceInfo.setText(tr("config.plugins.marketplace.install.cancelled", "Instalacion cancelada por el usuario."));
            }
            return;
        }

        setMarketplaceLoading(true);
        if (lblMarketplaceInfo != null) {
            lblMarketplaceInfo.setText(tr("config.plugins.marketplace.installing", "Descargando e instalando plugin...") + "\n" + safe(item.name(), item.id()));
        }

        new Thread(() -> {
            try {
                PluginMarketplaceService.DownloadedPlugin downloaded = pluginMarketplaceService.downloadPlugin(item);
                File installed = pluginApplicationService.installPluginJar(downloaded.tempFile(), pluginsDir, downloaded.suggestedFileName());
                pluginApplicationService.reloadPlugins(getPluginManager());
                UserActionMonitor.pluginInstalled(installed.getName());

                Platform.runLater(() -> {
                    refreshPluginsList();
                    if (lblMarketplaceInfo != null) {
                        lblMarketplaceInfo.setText(tr("config.plugins.marketplace.install.ok", "Plugin instalado correctamente.") + "\n" + installed.getName());
                    }
                    setMarketplaceLoading(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (lblMarketplaceInfo != null) {
                        lblMarketplaceInfo.setText(tr("config.plugins.marketplace.install.error", "No se pudo instalar el plugin.") + "\n" + ex.getMessage());
                    }
                    setMarketplaceLoading(false);
                });
            }
        }, "marketplace-install-thread").start();
    }


    private PluginMarketplaceItem getSelectedMarketplaceItem() {
        if (lstMarketplacePlugins == null) return null;

        int idx = lstMarketplacePlugins.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= marketplacePlugins.size()) return null;

        return marketplacePlugins.get(idx);
    }

    private void updateMarketplaceButtons(PluginMarketplaceItem item) {
        boolean disableInstall = marketplaceLoading || item == null;
        if (btnMarketplaceInstall != null) btnMarketplaceInstall.setDisable(disableInstall);
        if (btnMarketplaceRefresh != null) btnMarketplaceRefresh.setDisable(marketplaceLoading);
    }

    private void setMarketplaceLoading(boolean loading) {
        marketplaceLoading = loading;
        updateMarketplaceButtons(getSelectedMarketplaceItem());
        if (progressMarketplace != null) {
            progressMarketplace.setVisible(loading);
            progressMarketplace.setManaged(loading);
        }
    }

    private String safe(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private boolean confirmMarketplaceInstall(PluginMarketplaceItem item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(tr("config.plugins.marketplace.confirm.title", "Confirmar instalacion"));
        alert.setHeaderText(tr("config.plugins.marketplace.confirm.header", "Vas a instalar un plugin desde el marketplace."));
        alert.setContentText(
            tr("config.plugins.marketplace.confirm.body", "Plugin") + ": " + safe(item.name(), item.id()) + "\n"
            + tr("config.plugins.marketplace.confirm.version", "Version") + ": " + safe(item.version(), "-") + "\n"
            + tr("config.plugins.marketplace.confirm.url", "URL") + ": " + safe(item.downloadUrl(), "-")
        );

        if (btnMarketplaceInstall != null && btnMarketplaceInstall.getScene() != null) {
            alert.initOwner(btnMarketplaceInstall.getScene().getWindow());
        }

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    @FXML
    public void onSaveWorkspace(ActionEvent event) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            showError("No hay un proyecto activo para guardar el workspace.");
            return;
        }

        WorkspaceModel workspace = configModel.getActiveWorkspace();
        if (workspace == null) {
            workspace = new WorkspaceModel();
            workspace.setProjectName(projectRoot.getName());
            configModel.setActiveWorkspace(workspace);
        }

        // Capturar estado actual de la UI de compilación
        captureCompilerSettingsToWorkspace(workspace);

        workspaceService.saveWorkspace(projectRoot, workspace);
        showError("");
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Workspace Guardado");
        alert.setHeaderText(null);
        alert.setContentText("La configuración se ha guardado en workspace.json del proyecto.");
        alert.showAndWait();
    }

    private void captureCompilerSettingsToWorkspace(WorkspaceModel workspace) {
        String selectedCompiler = comboCompilador != null ? comboCompilador.getSelectionModel().getSelectedItem() : "GBDK";
        workspace.setSetting("compilador_seleccionado", normalizeCompiler(selectedCompiler));

        // GBDK
        if (comboGbdkOptLevel != null) workspace.setSetting("gbdk_opt_level", comboGbdkOptLevel.getSelectionModel().getSelectedItem());
        if (chkGbdkOptSpeed != null) workspace.setSetting("gbdk_opt_speed", String.valueOf(chkGbdkOptSpeed.isSelected()));
        if (chkGbdkOptSize != null) workspace.setSetting("gbdk_opt_size", String.valueOf(chkGbdkOptSize.isSelected()));
        if (txtGbdkDefines != null) workspace.setSetting("gbdk_defines", txtGbdkDefines.getText());
        if (txtGbdkIncludes != null) workspace.setSetting("gbdk_includes", txtGbdkIncludes.getText());
        if (txtGbdkExtraArgs != null) workspace.setSetting("gbdk_extra_args", txtGbdkExtraArgs.getText());

        // Z88DK Spectrum
        if (txtSpectrumBin != null) workspace.setSetting("spectrum_bin", txtSpectrumBin.getText());
        if (comboZ88dkSpectrumClib != null) workspace.setSetting("z88dk_clib_option", comboZ88dkSpectrumClib.getSelectionModel().getSelectedItem());
        if (chkZ88dkSpectrumCrtOrgCode != null) workspace.setSetting("z88dk_use_pragma_crt_org_code_32768", String.valueOf(chkZ88dkSpectrumCrtOrgCode.isSelected()));
        if (txtZ88dkSpectrumTarget != null) workspace.setSetting("z88dk_target", txtZ88dkSpectrumTarget.getText());
        if (chkZ88dkSpectrumCreateApp != null) workspace.setSetting("z88dk_create_app", String.valueOf(chkZ88dkSpectrumCreateApp.isSelected()));
        if (txtZ88dkSpectrumDefines != null) workspace.setSetting("z88dk_defines", txtZ88dkSpectrumDefines.getText());
        if (txtZ88dkSpectrumIncludes != null) workspace.setSetting("z88dk_includes", txtZ88dkSpectrumIncludes.getText());
        if (txtZ88dkSpectrumExtraArgs != null) workspace.setSetting("z88dk_extra_args", txtZ88dkSpectrumExtraArgs.getText());

        // Z88DK CPC
        if (txtCpcBin != null) workspace.setSetting("cpc_bin", txtCpcBin.getText());
        if (comboZ88dkCpcClib != null) workspace.setSetting("z88dk_cpc_clib_option", comboZ88dkCpcClib.getSelectionModel().getSelectedItem());
        if (chkZ88dkCpcCrtOrgCode != null) workspace.setSetting("z88dk_cpc_use_pragma_crt_org_code_32768", String.valueOf(chkZ88dkCpcCrtOrgCode.isSelected()));
        if (txtZ88dkCpcTarget != null) workspace.setSetting("z88dk_cpc_target", txtZ88dkCpcTarget.getText());
        if (chkZ88dkCpcCreateApp != null) workspace.setSetting("z88dk_cpc_create_app", String.valueOf(chkZ88dkCpcCreateApp.isSelected()));
        if (txtZ88dkCpcDefines != null) workspace.setSetting("z88dk_cpc_defines", txtZ88dkCpcDefines.getText());
        if (txtZ88dkCpcIncludes != null) workspace.setSetting("z88dk_cpc_includes", txtZ88dkCpcIncludes.getText());
        if (txtZ88dkCpcExtraArgs != null) workspace.setSetting("z88dk_cpc_extra_args", txtZ88dkCpcExtraArgs.getText());

        // Z88DK SMS
        if (txtSmsBin != null) workspace.setSetting("sms_bin", txtSmsBin.getText());
        if (comboZ88dkSmsClib != null) workspace.setSetting("z88dk_sms_clib_option", comboZ88dkSmsClib.getSelectionModel().getSelectedItem());
        if (txtZ88dkSmsTarget != null) workspace.setSetting("z88dk_sms_target", txtZ88dkSmsTarget.getText());
        if (chkZ88dkSmsCreateApp != null) workspace.setSetting("z88dk_sms_create_app", String.valueOf(chkZ88dkSmsCreateApp.isSelected()));
        if (txtZ88dkSmsDefines != null) workspace.setSetting("z88dk_sms_defines", txtZ88dkSmsDefines.getText());
        if (txtZ88dkSmsIncludes != null) workspace.setSetting("z88dk_sms_includes", txtZ88dkSmsIncludes.getText());
        if (txtZ88dkSmsExtraArgs != null) workspace.setSetting("z88dk_sms_extra_args", txtZ88dkSmsExtraArgs.getText());

        // Z88DK Game Gear
        if (txtGgBin != null) workspace.setSetting("gg_bin", txtGgBin.getText());
        if (comboZ88dkGgClib != null) workspace.setSetting("z88dk_gg_clib_option", comboZ88dkGgClib.getSelectionModel().getSelectedItem());
        if (txtZ88dkGgTarget != null) workspace.setSetting("z88dk_gg_target", txtZ88dkGgTarget.getText());
        if (chkZ88dkGgCreateApp != null) workspace.setSetting("z88dk_gg_create_app", String.valueOf(chkZ88dkGgCreateApp.isSelected()));
        if (txtZ88dkGgDefines != null) workspace.setSetting("z88dk_gg_defines", txtZ88dkGgDefines.getText());
        if (txtZ88dkGgIncludes != null) workspace.setSetting("z88dk_gg_includes", txtZ88dkGgIncludes.getText());
        if (txtZ88dkGgExtraArgs != null) workspace.setSetting("z88dk_gg_extra_args", txtZ88dkGgExtraArgs.getText());

        // Z88DK MSX
        if (txtMsxBin != null) workspace.setSetting("msx_bin", txtMsxBin.getText());
        if (comboZ88dkMsxClib != null) workspace.setSetting("z88dk_msx_clib_option", comboZ88dkMsxClib.getSelectionModel().getSelectedItem());
        if (txtZ88dkMsxTarget != null) workspace.setSetting("z88dk_msx_target", txtZ88dkMsxTarget.getText());
        if (chkZ88dkMsxCreateApp != null) workspace.setSetting("z88dk_msx_create_app", String.valueOf(chkZ88dkMsxCreateApp.isSelected()));
        if (txtZ88dkMsxDefines != null) workspace.setSetting("z88dk_msx_defines", txtZ88dkMsxDefines.getText());
        if (txtZ88dkMsxIncludes != null) workspace.setSetting("z88dk_msx_includes", txtZ88dkMsxIncludes.getText());
        if (txtZ88dkMsxExtraArgs != null) workspace.setSetting("z88dk_msx_extra_args", txtZ88dkMsxExtraArgs.getText());

        // Makefile
        if (txtMakeExecutable != null) workspace.setSetting("make_executable", txtMakeExecutable.getText());
        if (txtMakeBuildTarget != null) workspace.setSetting("make_build_target", txtMakeBuildTarget.getText());
        if (txtMakeRunTarget != null) workspace.setSetting("make_run_target", txtMakeRunTarget.getText());
        if (txtMakeExtraArgs != null) workspace.setSetting("make_extra_args", txtMakeExtraArgs.getText());

        // GCC / C nativo
        if (txtGccBin != null) workspace.setSetting("gcc_bin", txtGccBin.getText());
        if (comboGccOptLevel != null) workspace.setSetting("gcc_opt_level", comboGccOptLevel.getSelectionModel().getSelectedItem());
        if (txtGccDefines != null) workspace.setSetting("gcc_defines", txtGccDefines.getText());
        if (txtGccIncludes != null) workspace.setSetting("gcc_includes", txtGccIncludes.getText());
        if (txtGccExtraArgs != null) workspace.setSetting("gcc_extra_args", txtGccExtraArgs.getText());

        // CC65 (Atari / C64)
        String cc65ComboLabel = comboCompilador != null ? comboCompilador.getSelectionModel().getSelectedItem() : null;
        String cc65TargetForWorkspace = comboCompilador != null
            ? resolveCc65TargetFromSelection(cc65ComboLabel)
            : resolveCurrentCc65Target();
        workspace.setSetting("cc65_target", cc65TargetForWorkspace);
        if (txtCc65Bin != null) workspace.setSetting("cc65_bin", txtCc65Bin.getText());
        if (txtAltirraBin != null) workspace.setSetting("altirra_bin", txtAltirraBin.getText());
        if (txtViceBin != null) workspace.setSetting("vice_bin", txtViceBin.getText());
        if (comboCc65OptLevel != null) workspace.setSetting("cc65_opt_level", comboCc65OptLevel.getSelectionModel().getSelectedItem());
        if (txtCc65Defines != null) workspace.setSetting("cc65_defines", txtCc65Defines.getText());
        if (txtCc65Includes != null) workspace.setSetting("cc65_includes", txtCc65Includes.getText());
        if (txtCc65ExtraArgs != null) workspace.setSetting("cc65_extra_args", txtCc65ExtraArgs.getText());
    }

    /**
     * Cuando hay un proyecto activo, guarda la configuración de compilación capturada
     * en el workspace del proyecto (workspace.json) para que sea específica del proyecto
     * en lugar de quedar solo en la configuración global.
     */
    private void persistCompilerSettingsToActiveWorkspace() {
        if (projectRoot == null || !projectRoot.isDirectory()) return;

        WorkspaceModel workspace = configModel.getActiveWorkspace();
        if (workspace == null) {
            workspace = new WorkspaceModel();
            workspace.setProjectName(projectRoot.getName());
            configModel.setActiveWorkspace(workspace);
        }

        captureCompilerSettingsToWorkspace(workspace);
        workspaceService.saveWorkspace(projectRoot, workspace);
    }

    /**
     * Evento para guardar la configuración del compilador
     * @param event
     */
    @FXML
    private void onSaveCompilador(ActionEvent event) {
        String selectedCompilerLabel = comboCompilador != null ? comboCompilador.getSelectionModel().getSelectedItem() : null;
        String selectedCompiler = normalizeCompiler(selectedCompilerLabel);
        String spectrumPath     = txtSpectrumBin    != null ? txtSpectrumBin.getText().trim()    : "";
        String cpcPath          = txtCpcBin         != null ? txtCpcBin.getText().trim()         : "";
        String z88dkProfile     = resolveZ88dkProfileFromSelection(selectedCompilerLabel, getSelectedZ88dkProfile());

        if (selectedCompiler == null || selectedCompiler.isEmpty()) {
            showError("Debe seleccionar un compilador.");
            return;
        }

        if (COMPILER_Z88DK.equals(selectedCompiler)) {
            String requiredPath = Z88DK_PROFILE_CPC.equals(z88dkProfile) ? cpcPath : spectrumPath;
            if (!requiredPath.isEmpty() && !(new File(requiredPath).exists())) {
                showError("Debe especificar una ruta valida para el bin de Z88DK del perfil activo, o dejarla vacia para usar la copia integrada en libs/devkits/z88dk.");
                return;
            }
        }

        if (COMPILER_Z88DK.equals(selectedCompiler) && !validateZ88dkTargets()) {
            return;
        }

        if (!persistMakeOpts(false)) {
            return;
        }

        if (!persistZ88dkFlags(false)) {
            return;
        }
        persistGbdkOpts(false);
        persistGccOpts(false);
        persistCc65Opts(false);
        if (!persistProjectDetectionPrefs(false)) {
            return;
        }

        configModel.setConfigProperty("spectrum_bin", spectrumPath);
        configModel.setConfigProperty("cpc_bin", cpcPath);

        if (txtCompilador != null) {
            configModel.setCompilador(txtCompilador.getText());
        }

        saveCompilerSelection(selectedCompiler, z88dkProfile, false);
        persistConfig();
        persistCompilerSettingsToActiveWorkspace();
        UserActionMonitor.compilerChanged(selectedCompiler);

        showError(""); // Limpiar error
    }

    private void configureCompilerCombo() {
        if (comboCompilador != null) {
            comboCompilador.getItems().setAll(
                COMPILER_GBDK,
                getZ88dkSpectrumDisplayLabel(),
                getZ88dkCpcDisplayLabel(),
                getZ88dkSmsDisplayLabel(),
                getZ88dkGameGearDisplayLabel(),
                getZ88dkMsxDisplayLabel(),
                getCc65DisplayLabel(),
                getCc65C64DisplayLabel(),
                getCc65NesDisplayLabel(),
                getGccDisplayLabel(),
                COMPILER_MAKEFILE
            );
            comboCompilador.setOnAction(this::onCompilerSelectionChanged);
        }

        if (tabPaneCompilerOptions != null) {
            tabPaneCompilerOptions.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                onCompilerOptionsTabChanged(newTab);
            });
        }
    }

    private void applyCompilerSelectionFromModel() {
        String compiler = normalizeCompiler(configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK));
        String profile = normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
        saveCompilerSelection(compiler, profile, false);
    }

    private Tab resolveZ88dkTabForProfile(String profile) {
        String z = normalizeZ88dkProfile(profile);
        if (Z88DK_PROFILE_CPC.equals(z)) return tabCompilerZ88dkCpc;
        if (Z88DK_PROFILE_SMS.equals(z)) return tabCompilerZ88dkSms;
        if (Z88DK_PROFILE_GAME_GEAR.equals(z)) return tabCompilerZ88dkGameGear;
        if (Z88DK_PROFILE_MSX.equals(z)) return tabCompilerZ88dkMsx;
        return tabCompilerZ88dkSpectrum;
    }

    @FXML
    private void onCompilerSelectionChanged(ActionEvent event) {
        if (syncingCompilerUi) return;
        String selectedCompilerLabel = comboCompilador != null ? comboCompilador.getSelectionModel().getSelectedItem() : null;
        String selectedCompiler = normalizeCompiler(selectedCompilerLabel);
        String profile = resolveZ88dkProfileFromSelection(selectedCompilerLabel, getSelectedZ88dkProfile());
        saveCompilerSelection(selectedCompiler, profile, false);
        showError("");
    }

    private void onCompilerOptionsTabChanged(Tab newTab) {
        if (syncingCompilerUi || newTab == null) return;
        String selectedCompiler;
        String profile = getSelectedZ88dkProfile();

        if (newTab == tabCompilerZ88dkSpectrum) {
            selectedCompiler = COMPILER_Z88DK;
            profile = Z88DK_PROFILE_SPECTRUM;
        } else if (newTab == tabCompilerZ88dkCpc) {
            selectedCompiler = COMPILER_Z88DK;
            profile = Z88DK_PROFILE_CPC;
        } else if (newTab == tabCompilerZ88dkSms) {
            selectedCompiler = COMPILER_Z88DK;
            profile = Z88DK_PROFILE_SMS;
        } else if (newTab == tabCompilerZ88dkGameGear) {
            selectedCompiler = COMPILER_Z88DK;
            profile = Z88DK_PROFILE_GAME_GEAR;
        } else if (newTab == tabCompilerZ88dkMsx) {
            selectedCompiler = COMPILER_Z88DK;
            profile = Z88DK_PROFILE_MSX;
        } else if (newTab == tabCompilerGcc) {
            selectedCompiler = COMPILER_GCC;
        } else if (newTab == tabCompilerCc65) {
            selectedCompiler = ConfigModel.COMPILER_CC65;
        } else if (newTab == tabCompilerMakefile) {
            selectedCompiler = COMPILER_MAKEFILE;
        } else {
            selectedCompiler = COMPILER_GBDK;
        }

        saveCompilerSelection(selectedCompiler, profile, false);
        showError("");
    }

    private void saveCompilerSelection(String compiler, String z88dkProfile, boolean persist) {
        String normalizedCompiler = normalizeCompiler(compiler);
        String normalizedProfile = normalizeZ88dkProfile(z88dkProfile);

        String cc65Target = resolveCurrentCc65Target();
        if (ConfigModel.COMPILER_CC65.equalsIgnoreCase(normalizedCompiler) && comboCompilador != null) {
            String comboLabel = comboCompilador.getSelectionModel().getSelectedItem();
            if (comboLabel != null && (comboLabel.equalsIgnoreCase(getCc65DisplayLabel())
                    || comboLabel.equalsIgnoreCase(getCc65C64DisplayLabel())
                    || comboLabel.equalsIgnoreCase(getCc65NesDisplayLabel()))) {
                cc65Target = resolveCc65TargetFromSelection(comboLabel);
            }
        }
        String normalizedEmulator = getEmulatorForCompiler(normalizedCompiler, normalizedProfile, cc65Target);

        syncingCompilerUi = true;
        try {
            if (tabPaneCompilerOptions != null) {
                if (COMPILER_Z88DK.equalsIgnoreCase(normalizedCompiler) && tabCompilerZ88dkSpectrum != null && tabCompilerZ88dkCpc != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(resolveZ88dkTabForProfile(normalizedProfile));
                } else if (COMPILER_MAKEFILE.equalsIgnoreCase(normalizedCompiler) && tabCompilerMakefile != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(tabCompilerMakefile);
                } else if (ConfigModel.COMPILER_CC65.equalsIgnoreCase(normalizedCompiler) && tabCompilerCc65 != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(tabCompilerCc65);
                } else if (COMPILER_GCC.equalsIgnoreCase(normalizedCompiler) && tabCompilerGcc != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(tabCompilerGcc);
                } else if (tabCompilerGbdk != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(tabCompilerGbdk);
                }
            }

            if (comboCompilador != null) {
                comboCompilador.getSelectionModel().select(toCompilerDisplayLabel(normalizedCompiler, normalizedProfile));
            }
        } finally {
            syncingCompilerUi = false;
        }

        configModel.setConfigProperty("compilador_seleccionado", normalizedCompiler);
        configModel.setConfigProperty("z88dk_profile", normalizedProfile);
        configModel.setConfigProperty("cc65_target", cc65Target);
        configModel.setConfigProperty("emulador_seleccionado", normalizedEmulator);

        if (persist) {
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
        }
    }

    private String getEmulatorForCompiler(String compiler, String z88dkProfile, String cc65Target) {
        String norm = normalizeCompiler(compiler);
        if (COMPILER_Z88DK.equalsIgnoreCase(norm)) {
            String z = normalizeZ88dkProfile(z88dkProfile);
            if (Z88DK_PROFILE_CPC.equals(z)) return EMULATOR_CAP32;
            if (Z88DK_PROFILE_MSX.equals(z)) return EMULATOR_OPENMSX;
            if (Z88DK_PROFILE_SMS.equals(z) || Z88DK_PROFILE_GAME_GEAR.equals(z)) return EMULATOR_EMULICIOUS;
            return EMULATOR_JSPECCY;
        }
        if (ConfigModel.COMPILER_CC65.equalsIgnoreCase(norm)) {
            if (CC65_TARGET_NES.equalsIgnoreCase(cc65Target)) return EMULATOR_MESEN;
            return CC65_TARGET_C64.equalsIgnoreCase(cc65Target) ? EMULATOR_VICE : EMULATOR_ALTIRRA;
        }
        return EMULATOR_EMULICIOUS;
    }

    private String getSelectedZ88dkProfile() {
        if (tabPaneCompilerOptions == null) {
            return normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
        }

        Tab selected = tabPaneCompilerOptions.getSelectionModel().getSelectedItem();
        if (selected == tabCompilerZ88dkCpc) return Z88DK_PROFILE_CPC;
        if (selected == tabCompilerZ88dkSms) return Z88DK_PROFILE_SMS;
        if (selected == tabCompilerZ88dkGameGear) return Z88DK_PROFILE_GAME_GEAR;
        if (selected == tabCompilerZ88dkMsx) return Z88DK_PROFILE_MSX;
        if (selected == tabCompilerZ88dkSpectrum) return Z88DK_PROFILE_SPECTRUM;
        return normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
    }

    private String normalizeZ88dkProfile(String profile) {
        if (Z88DK_PROFILE_CPC.equalsIgnoreCase(profile)) return Z88DK_PROFILE_CPC;
        if (Z88DK_PROFILE_SMS.equalsIgnoreCase(profile)) return Z88DK_PROFILE_SMS;
        if (Z88DK_PROFILE_GAME_GEAR.equalsIgnoreCase(profile)) return Z88DK_PROFILE_GAME_GEAR;
        if (Z88DK_PROFILE_MSX.equalsIgnoreCase(profile)) return Z88DK_PROFILE_MSX;
        return Z88DK_PROFILE_SPECTRUM;
    }

    private String normalizeCompiler(String compiler) {
        if (compiler == null) return COMPILER_GBDK;

        String normalized = compiler.trim();

        if (COMPILER_MAKEFILE.equalsIgnoreCase(normalized)) {
            return COMPILER_MAKEFILE;
        }

        if (COMPILER_GBDK.equalsIgnoreCase(normalized)
            || normalized.equalsIgnoreCase(getGbdkDisplayLabel())) {
            return COMPILER_GBDK;
        }

        if (COMPILER_Z88DK.equalsIgnoreCase(normalized)
            || LEGACY_DISPLAY_Z88DK.equalsIgnoreCase(normalized)
            || LEGACY_SPECTRUM.equalsIgnoreCase(normalized)
            || normalized.equalsIgnoreCase(getZ88dkSpectrumDisplayLabel())
            || normalized.equalsIgnoreCase(getZ88dkCpcDisplayLabel())
            || normalized.toLowerCase(Locale.ROOT).contains("z88dk")) {
            return COMPILER_Z88DK;
        }

        if (COMPILER_GCC.equalsIgnoreCase(normalized)
            || normalized.equalsIgnoreCase(getGccDisplayLabel())
            || normalized.toLowerCase(Locale.ROOT).contains("gcc")) {
            return COMPILER_GCC;
        }

        if (ConfigModel.COMPILER_CC65.equalsIgnoreCase(normalized)
            || normalized.equalsIgnoreCase(getCc65DisplayLabel())
            || normalized.equalsIgnoreCase(getCc65C64DisplayLabel())
            || normalized.equalsIgnoreCase(getCc65NesDisplayLabel())
            || normalized.toLowerCase(Locale.ROOT).contains("cc65")
            || normalized.toLowerCase(Locale.ROOT).contains("atari")
            || normalized.toLowerCase(Locale.ROOT).contains("c64")
            || normalized.toLowerCase(Locale.ROOT).contains("nes")) {
            return ConfigModel.COMPILER_CC65;
        }

        return COMPILER_GBDK;
    }

    private String toCompilerDisplayLabel(String normalizedCompiler, String z88dkProfile) {
        if (COMPILER_Z88DK.equalsIgnoreCase(normalizedCompiler)) {
            String z = normalizeZ88dkProfile(z88dkProfile);
            if (Z88DK_PROFILE_CPC.equals(z)) return getZ88dkCpcDisplayLabel();
            if (Z88DK_PROFILE_SMS.equals(z)) return getZ88dkSmsDisplayLabel();
            if (Z88DK_PROFILE_GAME_GEAR.equals(z)) return getZ88dkGameGearDisplayLabel();
            if (Z88DK_PROFILE_MSX.equals(z)) return getZ88dkMsxDisplayLabel();
            return getZ88dkSpectrumDisplayLabel();
        }
        if (COMPILER_GCC.equalsIgnoreCase(normalizedCompiler)) return getGccDisplayLabel();
        if (ConfigModel.COMPILER_CC65.equalsIgnoreCase(normalizedCompiler)) {
            String target = resolveCurrentCc65Target();
            if (CC65_TARGET_NES.equals(target)) return getCc65NesDisplayLabel();
            if (CC65_TARGET_C64.equals(target)) return getCc65C64DisplayLabel();
            return getCc65DisplayLabel();
        }
        if (COMPILER_MAKEFILE.equalsIgnoreCase(normalizedCompiler)) return COMPILER_MAKEFILE;
        return getGbdkDisplayLabel();
    }

    private String getGbdkDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.gbdk")
            ? bundle.getString("config.compiler.option.gbdk")
            : "GBDK (Game Boy)";
    }

    private String getCc65DisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.cc65")
            ? bundle.getString("config.compiler.option.cc65")
            : "CC65 (Atari XE/XL)";
    }

    private String getCc65C64DisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.cc65.c64")
            ? bundle.getString("config.compiler.option.cc65.c64")
            : "CC65 (C64)";
    }

    private String getCc65NesDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.cc65.nes")
            ? bundle.getString("config.compiler.option.cc65.nes")
            : "CC65 (NES)";
    }

    private String resolveCurrentCc65Target() {
        String selected = configModel.getConfigProperty("cc65_target", CC65_TARGET_ATARI);
        if (CC65_TARGET_NES.equalsIgnoreCase(selected)) return CC65_TARGET_NES;
        if (CC65_TARGET_C64.equalsIgnoreCase(selected)) return CC65_TARGET_C64;
        return CC65_TARGET_ATARI;
    }

    private String resolveCc65TargetFromSelection(String compilerLabel) {
        if (compilerLabel != null && compilerLabel.equalsIgnoreCase(getCc65NesDisplayLabel())) {
            return CC65_TARGET_NES;
        }
        if (compilerLabel != null && compilerLabel.equalsIgnoreCase(getCc65C64DisplayLabel())) {
            return CC65_TARGET_C64;
        }
        return CC65_TARGET_ATARI;
    }

    private String resolveZ88dkProfileFromSelection(String compilerLabel, String fallbackProfile) {
        if (compilerLabel == null) return normalizeZ88dkProfile(fallbackProfile);

        if (compilerLabel.equalsIgnoreCase(getZ88dkCpcDisplayLabel())) return Z88DK_PROFILE_CPC;
        if (compilerLabel.equalsIgnoreCase(getZ88dkSpectrumDisplayLabel())) return Z88DK_PROFILE_SPECTRUM;
        if (compilerLabel.equalsIgnoreCase(getZ88dkSmsDisplayLabel())) return Z88DK_PROFILE_SMS;
        if (compilerLabel.equalsIgnoreCase(getZ88dkGameGearDisplayLabel())) return Z88DK_PROFILE_GAME_GEAR;
        if (compilerLabel.equalsIgnoreCase(getZ88dkMsxDisplayLabel())) return Z88DK_PROFILE_MSX;

        return normalizeZ88dkProfile(fallbackProfile);
    }

    private String getZ88dkSpectrumDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.z88dk.spectrum")
            ? bundle.getString("config.compiler.option.z88dk.spectrum")
            : "Z88DK (Spectrum)";
    }

    private String getZ88dkCpcDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.z88dk.cpc")
            ? bundle.getString("config.compiler.option.z88dk.cpc")
            : "Z88DK (CPC)";
    }

    private String getZ88dkSmsDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.z88dk.sms")
            ? bundle.getString("config.compiler.option.z88dk.sms")
            : "Z88DK (Master System)";
    }

    private String getZ88dkGameGearDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.z88dk.gamegear")
            ? bundle.getString("config.compiler.option.z88dk.gamegear")
            : "Z88DK (Game Gear)";
    }

    private String getZ88dkMsxDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.z88dk.msx")
            ? bundle.getString("config.compiler.option.z88dk.msx")
            : "Z88DK (MSX)";
    }

    private String getGccDisplayLabel() {
        ResourceBundle bundle = getCurrentBundle();
        return bundle.containsKey("config.compiler.option.gcc")
            ? bundle.getString("config.compiler.option.gcc")
            : "C normal (gcc)";
    }

    private void refreshCompilerComboItems() {
        if (comboCompilador == null) return;

        String compiler = normalizeCompiler(configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK));
        String profile = normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));

        comboCompilador.getItems().setAll(
            getGbdkDisplayLabel(),
            getZ88dkSpectrumDisplayLabel(),
            getZ88dkCpcDisplayLabel(),
            getZ88dkSmsDisplayLabel(),
            getZ88dkGameGearDisplayLabel(),
            getZ88dkMsxDisplayLabel(),
            getCc65DisplayLabel(),
            getCc65C64DisplayLabel(),
            getCc65NesDisplayLabel(),
            getGccDisplayLabel(),
            COMPILER_MAKEFILE
        );
        comboCompilador.getSelectionModel().select(toCompilerDisplayLabel(compiler, profile));
    }

    /**
     * Mostrar mensaje de error en el diálogo de configuración.
     * @param message Mensaje de error
     */
    private void showError(String message) {
        if (lblError != null) {
            lblError.setText(message);
        }
    }

    /**
     * guardar evento del botón bin de spectrum
     * @param event
     */
    @FXML
    public void onSaveSpectrumBin(ActionEvent event) {
        if (txtSpectrumBin != null) {
            String path = txtSpectrumBin.getText();
            configModel.setConfigProperty("spectrum_bin", path);
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
            UserActionMonitor.compilerPathUpdated("Z88DK-Spectrum", path);
        }
    }

    @FXML
    public void onSaveCpcBin(ActionEvent event) {
        if (txtCpcBin != null) {
            String path = txtCpcBin.getText();
            configModel.setConfigProperty("cpc_bin", path);
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
            UserActionMonitor.compilerPathUpdated("Z88DK-CPC", path);
        }
    }

    @FXML
    public void onSaveSmsBin(ActionEvent event) {
        if (txtSmsBin != null) {
            String path = txtSmsBin.getText();
            configModel.setConfigProperty("sms_bin", path);
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
            UserActionMonitor.compilerPathUpdated("Z88DK-SMS", path);
        }
    }

    @FXML
    public void onSaveGgBin(ActionEvent event) {
        if (txtGgBin != null) {
            String path = txtGgBin.getText();
            configModel.setConfigProperty("gg_bin", path);
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
            UserActionMonitor.compilerPathUpdated("Z88DK-GameGear", path);
        }
    }

    @FXML
    public void onSaveMsxBin(ActionEvent event) {
        if (txtMsxBin != null) {
            String path = txtMsxBin.getText();
            configModel.setConfigProperty("msx_bin", path);
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
            UserActionMonitor.compilerPathUpdated("Z88DK-MSX", path);
        }
    }

    @FXML
    public void onSaveZ88dkFlags(ActionEvent event) {
        if (persistZ88dkFlags(true)) {
            showError("");
        }
    }

    @FXML
    public void onSaveGbdkOpts(ActionEvent event) {
        persistGbdkOpts(true);
    }

    @FXML
    public void onSaveMakeOpts(ActionEvent event) {
        if (persistMakeOpts(true)) {
            showError("");
        }
    }

    @FXML
    public void onSaveProjectDetectionPrefs(ActionEvent event) {
        if (persistProjectDetectionPrefs(true)) {
            showError("");
        }
    }

    @FXML
    public void onProjectDetectionAutoApplyChanged(ActionEvent event) {
        updateProjectDetectionControlsState();
        showError("");
    }

    private void loadGbdkOptsFromConfig() {
        if (comboGbdkOptLevel != null) {
            comboGbdkOptLevel.getItems().setAll(ConfigModel.GBDK_OPT_LEVELS);
            String selected = configModel.getConfigProperty("gbdk_opt_level", ConfigModel.GBDK_OPT_NONE);
            comboGbdkOptLevel.getSelectionModel().select(selected);
        }
        if (chkGbdkOptSpeed != null) {
            chkGbdkOptSpeed.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("gbdk_opt_speed", "false")));
        }
        if (chkGbdkOptSize != null) {
            chkGbdkOptSize.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("gbdk_opt_size", "false")));
        }
        if (txtGbdkDefines != null) {
            txtGbdkDefines.setText(configModel.getConfigProperty("gbdk_defines", ""));
        }
        if (txtGbdkIncludes != null) {
            txtGbdkIncludes.setText(configModel.getConfigProperty("gbdk_includes", ""));
        }
        if (txtGbdkExtraArgs != null) {
            txtGbdkExtraArgs.setText(configModel.getConfigProperty("gbdk_extra_args", ""));
        }
    }

    private void persistGbdkOpts(boolean persist) {
        String optLevel  = comboGbdkOptLevel != null && comboGbdkOptLevel.getValue() != null
                           ? comboGbdkOptLevel.getValue() : ConfigModel.GBDK_OPT_NONE;
        boolean optSpeed = chkGbdkOptSpeed != null && chkGbdkOptSpeed.isSelected();
        boolean optSize  = chkGbdkOptSize  != null && chkGbdkOptSize.isSelected();

        configModel.setConfigProperty("gbdk_opt_level", optLevel);
        configModel.setConfigProperty("gbdk_opt_speed", Boolean.toString(optSpeed));
        configModel.setConfigProperty("gbdk_opt_size",  Boolean.toString(optSize));
        configModel.setConfigProperty("gbdk_defines", txtGbdkDefines != null ? txtGbdkDefines.getText().trim() : "");
        configModel.setConfigProperty("gbdk_includes", txtGbdkIncludes != null ? txtGbdkIncludes.getText().trim() : "");
        configModel.setConfigProperty("gbdk_extra_args", txtGbdkExtraArgs != null ? txtGbdkExtraArgs.getText().trim() : "");

        if (persist) {
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
        }
    }

    private void loadGccOptsFromConfig() {
        if (txtGccBin != null) {
            txtGccBin.setText(configModel.getConfigProperty("gcc_bin", ""));
        }
        if (comboGccOptLevel != null) {
            comboGccOptLevel.getItems().setAll(ConfigModel.GCC_OPT_LEVELS);
            String selected = configModel.getConfigProperty("gcc_opt_level", ConfigModel.GCC_OPT_NONE);
            comboGccOptLevel.getSelectionModel().select(selected);
        }
        if (txtGccDefines != null) {
            txtGccDefines.setText(configModel.getConfigProperty("gcc_defines", ""));
        }
        if (txtGccIncludes != null) {
            txtGccIncludes.setText(configModel.getConfigProperty("gcc_includes", ""));
        }
        if (txtGccExtraArgs != null) {
            txtGccExtraArgs.setText(configModel.getConfigProperty("gcc_extra_args", ""));
        }
    }

    private boolean persistGccOpts(boolean persist) {
        String gccBin = txtGccBin != null ? txtGccBin.getText().trim() : "";
        String optLevel = comboGccOptLevel != null && comboGccOptLevel.getValue() != null
            ? comboGccOptLevel.getValue() : ConfigModel.GCC_OPT_NONE;

        configModel.setConfigProperty("gcc_bin", gccBin);
        configModel.setConfigProperty("gcc_opt_level", optLevel);
        configModel.setConfigProperty("gcc_defines", txtGccDefines != null ? txtGccDefines.getText().trim() : "");
        configModel.setConfigProperty("gcc_includes", txtGccIncludes != null ? txtGccIncludes.getText().trim() : "");
        configModel.setConfigProperty("gcc_extra_args", txtGccExtraArgs != null ? txtGccExtraArgs.getText().trim() : "");

        if (persist) {
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
        }
        return true;
    }

    @FXML
    public void onSaveGccOpts(ActionEvent event) {
        persistGccOpts(true);
        showError("");
    }

    @FXML
    public void onSeleccionarGccBin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();

        chooser.setTitle("Seleccionar carpeta bin de GCC");

        File selectedDir = chooser.showDialog(txtGccBin.getScene().getWindow());

        if (selectedDir != null) {
            txtGccBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("GCC", selectedDir.getAbsolutePath());
        }
    }

    private void loadCc65OptsFromConfig() {
        if (txtCc65Bin != null) {
            txtCc65Bin.setText(configModel.getConfigProperty("cc65_bin", ""));
        }
        if (txtAltirraBin != null) {
            txtAltirraBin.setText(configModel.getConfigProperty("altirra_bin", ""));
        }
        if (txtViceBin != null) {
            txtViceBin.setText(configModel.getConfigProperty("vice_bin", ""));
        }
        if (comboCc65OptLevel != null) {
            comboCc65OptLevel.getItems().setAll(ConfigModel.CC65_OPT_LEVELS);
            String selected = configModel.getConfigProperty("cc65_opt_level", ConfigModel.CC65_OPT_NONE);
            comboCc65OptLevel.getSelectionModel().select(selected);
        }
        if (txtCc65Defines != null) {
            txtCc65Defines.setText(configModel.getConfigProperty("cc65_defines", ""));
        }
        if (txtCc65Includes != null) {
            txtCc65Includes.setText(configModel.getConfigProperty("cc65_includes", ""));
        }
        if (txtCc65ExtraArgs != null) {
            txtCc65ExtraArgs.setText(configModel.getConfigProperty("cc65_extra_args", ""));
        }
    }

    private boolean persistCc65Opts(boolean persist) {
        String cc65Bin = txtCc65Bin != null ? txtCc65Bin.getText().trim() : "";
        String altirraBin = txtAltirraBin != null ? txtAltirraBin.getText().trim() : "";
        String viceBin = txtViceBin != null ? txtViceBin.getText().trim() : "";
        String optLevel = comboCc65OptLevel != null && comboCc65OptLevel.getValue() != null
            ? comboCc65OptLevel.getValue() : ConfigModel.CC65_OPT_NONE;

        configModel.setConfigProperty("cc65_bin", cc65Bin);
        configModel.setConfigProperty("altirra_bin", altirraBin);
        configModel.setConfigProperty("vice_bin", viceBin);
        configModel.setConfigProperty("cc65_opt_level", optLevel);
        configModel.setConfigProperty("cc65_defines", txtCc65Defines != null ? txtCc65Defines.getText().trim() : "");
        configModel.setConfigProperty("cc65_includes", txtCc65Includes != null ? txtCc65Includes.getText().trim() : "");
        configModel.setConfigProperty("cc65_extra_args", txtCc65ExtraArgs != null ? txtCc65ExtraArgs.getText().trim() : "");

        if (persist) {
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
        }
        return true;
    }

    @FXML
    public void onSaveCc65Opts(ActionEvent event) {
        persistCc65Opts(true);
        showError("");
    }

    @FXML
    public void onSeleccionarCc65Bin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Seleccionar carpeta bin de CC65");
        File selectedDir = chooser.showDialog(txtCc65Bin.getScene().getWindow());
        if (selectedDir != null) {
            txtCc65Bin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("CC65", selectedDir.getAbsolutePath());
        }
    }

    @FXML
    public void onSeleccionarAltirraBin(ActionEvent event) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Seleccionar ejecutable de Altirra");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Altirra Executable", "altirra64.exe", "altirra.exe", "*.exe"));
        File selectedFile = chooser.showOpenDialog(txtAltirraBin.getScene().getWindow());
        if (selectedFile != null) {
            txtAltirraBin.setText(selectedFile.getAbsolutePath());
            UserActionMonitor.emulatorPathUpdated("Altirra", selectedFile.getAbsolutePath());
        }
    }

    @FXML
    public void onSeleccionarViceBin(ActionEvent event) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Seleccionar ejecutable de VICE");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("VICE Executable", "x64sc.exe", "x64.exe", "x64sc", "x64", "*.exe"));
        File selectedFile = chooser.showOpenDialog(txtViceBin.getScene().getWindow());
        if (selectedFile != null) {
            txtViceBin.setText(selectedFile.getAbsolutePath());
            UserActionMonitor.emulatorPathUpdated("VICE", selectedFile.getAbsolutePath());
        }
    }

    private void loadZ88dkFlagsFromConfig() {
        if (comboZ88dkSpectrumClib != null) {
            comboZ88dkSpectrumClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_clib_option", ConfigModel.Z88DK_CLIB_NEW);
            comboZ88dkSpectrumClib.getSelectionModel().select(selected);
        }

        if (comboZ88dkCpcClib != null) {
            comboZ88dkCpcClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_cpc_clib_option", ConfigModel.Z88DK_CLIB_DEFAULT);
            comboZ88dkCpcClib.getSelectionModel().select(selected);
        }

        if (chkZ88dkSpectrumCrtOrgCode != null) {
            chkZ88dkSpectrumCrtOrgCode.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_use_pragma_crt_org_code_32768", "true")));
        }
        if (txtZ88dkSpectrumTarget != null) {
            txtZ88dkSpectrumTarget.setText(configModel.getConfigProperty("z88dk_target", "+zx"));
        }
        if (chkZ88dkSpectrumCreateApp != null) {
            chkZ88dkSpectrumCreateApp.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_create_app", "true")));
        }
        if (txtZ88dkSpectrumDefines != null) {
            txtZ88dkSpectrumDefines.setText(configModel.getConfigProperty("z88dk_defines", ""));
        }
        if (txtZ88dkSpectrumIncludes != null) {
            txtZ88dkSpectrumIncludes.setText(configModel.getConfigProperty("z88dk_includes", ""));
        }
        if (txtZ88dkSpectrumExtraArgs != null) {
            txtZ88dkSpectrumExtraArgs.setText(configModel.getConfigProperty("z88dk_extra_args", ""));
        }

        if (chkZ88dkCpcCrtOrgCode != null) {
            chkZ88dkCpcCrtOrgCode.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_cpc_use_pragma_crt_org_code_32768", "false")));
        }
        if (txtZ88dkCpcTarget != null) {
            txtZ88dkCpcTarget.setText(configModel.getConfigProperty("z88dk_cpc_target", "+cpc"));
        }
        if (chkZ88dkCpcCreateApp != null) {
            chkZ88dkCpcCreateApp.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_cpc_create_app", "true")));
        }
        if (txtZ88dkCpcDefines != null) {
            txtZ88dkCpcDefines.setText(configModel.getConfigProperty("z88dk_cpc_defines", ""));
        }
        if (txtZ88dkCpcIncludes != null) {
            txtZ88dkCpcIncludes.setText(configModel.getConfigProperty("z88dk_cpc_includes", ""));
        }
        if (txtZ88dkCpcExtraArgs != null) {
            txtZ88dkCpcExtraArgs.setText(configModel.getConfigProperty("z88dk_cpc_extra_args", ""));
        }

        if (comboZ88dkSmsClib != null) {
            comboZ88dkSmsClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_sms_clib_option", ConfigModel.Z88DK_CLIB_DEFAULT);
            comboZ88dkSmsClib.getSelectionModel().select(selected);
        }
        if (txtZ88dkSmsTarget != null) {
            txtZ88dkSmsTarget.setText(configModel.getConfigProperty("z88dk_sms_target", "+sms"));
        }
        if (chkZ88dkSmsCreateApp != null) {
            chkZ88dkSmsCreateApp.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_sms_create_app", "true")));
        }
        if (txtZ88dkSmsDefines != null) {
            txtZ88dkSmsDefines.setText(configModel.getConfigProperty("z88dk_sms_defines", ""));
        }
        if (txtZ88dkSmsIncludes != null) {
            txtZ88dkSmsIncludes.setText(configModel.getConfigProperty("z88dk_sms_includes", ""));
        }
        if (txtZ88dkSmsExtraArgs != null) {
            txtZ88dkSmsExtraArgs.setText(configModel.getConfigProperty("z88dk_sms_extra_args", ""));
        }

        if (comboZ88dkGgClib != null) {
            comboZ88dkGgClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_gg_clib_option", ConfigModel.Z88DK_CLIB_DEFAULT);
            comboZ88dkGgClib.getSelectionModel().select(selected);
        }
        if (txtZ88dkGgTarget != null) {
            txtZ88dkGgTarget.setText(configModel.getConfigProperty("z88dk_gg_target", ConfigModel.Z88DK_TARGET_GAME_GEAR_DEFAULT));
        }
        if (chkZ88dkGgCreateApp != null) {
            chkZ88dkGgCreateApp.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_gg_create_app", "true")));
        }
        if (txtZ88dkGgDefines != null) {
            txtZ88dkGgDefines.setText(configModel.getConfigProperty("z88dk_gg_defines", ""));
        }
        if (txtZ88dkGgIncludes != null) {
            txtZ88dkGgIncludes.setText(configModel.getConfigProperty("z88dk_gg_includes", ""));
        }
        if (txtZ88dkGgExtraArgs != null) {
            txtZ88dkGgExtraArgs.setText(configModel.getConfigProperty("z88dk_gg_extra_args", ""));
        }

        if (comboZ88dkMsxClib != null) {
            comboZ88dkMsxClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_msx_clib_option", ConfigModel.Z88DK_CLIB_DEFAULT);
            comboZ88dkMsxClib.getSelectionModel().select(selected);
        }
        if (txtZ88dkMsxTarget != null) {
            txtZ88dkMsxTarget.setText(configModel.getConfigProperty("z88dk_msx_target", "+msx"));
        }
        if (chkZ88dkMsxCreateApp != null) {
            chkZ88dkMsxCreateApp.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_msx_create_app", "true")));
        }
        if (txtZ88dkMsxDefines != null) {
            txtZ88dkMsxDefines.setText(configModel.getConfigProperty("z88dk_msx_defines", ""));
        }
        if (txtZ88dkMsxIncludes != null) {
            txtZ88dkMsxIncludes.setText(configModel.getConfigProperty("z88dk_msx_includes", ""));
        }
        if (txtZ88dkMsxExtraArgs != null) {
            txtZ88dkMsxExtraArgs.setText(configModel.getConfigProperty("z88dk_msx_extra_args", ""));
        }
    }

    private void loadMakeOptsFromConfig() {
        if (txtMakeExecutable != null) {
            txtMakeExecutable.setText(configModel.getConfigProperty("make_executable", ""));
        }
        if (txtMakeBuildTarget != null) {
            txtMakeBuildTarget.setText(configModel.getConfigProperty("make_build_target", ""));
        }
        if (txtMakeRunTarget != null) {
            txtMakeRunTarget.setText(configModel.getConfigProperty("make_run_target", "run"));
        }
        if (txtMakeExtraArgs != null) {
            txtMakeExtraArgs.setText(configModel.getConfigProperty("make_extra_args", ""));
        }
    }

    private boolean persistMakeOpts(boolean persist) {
        String makeExecutable = sanitizeMakeField(txtMakeExecutable != null ? txtMakeExecutable.getText() : "", true);
        String makeBuildTarget = sanitizeMakeField(txtMakeBuildTarget != null ? txtMakeBuildTarget.getText() : "", true);
        String makeRunTarget = sanitizeMakeField(txtMakeRunTarget != null ? txtMakeRunTarget.getText() : "run", false);
        String makeExtraArgs = sanitizeMakeField(txtMakeExtraArgs != null ? txtMakeExtraArgs.getText() : "", true);

        if (makeExecutable == null || makeBuildTarget == null || makeRunTarget == null || makeExtraArgs == null) {
            showError(tr("config.compiler.make.invalid", "Las opciones de Makefile no pueden contener saltos de linea."));
            return false;
        }

        configModel.setConfigProperty("make_executable", makeExecutable);
        configModel.setConfigProperty("make_build_target", makeBuildTarget);
        configModel.setConfigProperty("make_run_target", makeRunTarget);
        configModel.setConfigProperty("make_extra_args", makeExtraArgs);

        if (txtMakeExecutable != null) txtMakeExecutable.setText(makeExecutable);
        if (txtMakeBuildTarget != null) txtMakeBuildTarget.setText(makeBuildTarget);
        if (txtMakeRunTarget != null) txtMakeRunTarget.setText(makeRunTarget);
        if (txtMakeExtraArgs != null) txtMakeExtraArgs.setText(makeExtraArgs);

        if (persist) {
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
        }

        return true;
    }

    private String sanitizeMakeField(String value, boolean allowEmpty) {
        String normalized = value != null ? value.trim() : "";

        if (normalized.contains("\n") || normalized.contains("\r")) {
            return null;
        }

        if (!allowEmpty && normalized.isEmpty()) {
            return "run";
        }

        return normalized;
    }

    private void loadProjectDetectionPrefsFromConfig() {
        if (chkProjectDetectAutoApply != null) {
            chkProjectDetectAutoApply.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("project_detect_auto_apply", "true")));
        }
        if (txtProjectDetectThreshold != null) {
            String threshold = configModel.getConfigProperty("project_detect_auto_apply_threshold", "75");
            txtProjectDetectThreshold.setText(threshold);
        }
        updateProjectDetectionControlsState();
    }

    private boolean persistProjectDetectionPrefs(boolean persist) {
        boolean autoApply = chkProjectDetectAutoApply != null && chkProjectDetectAutoApply.isSelected();
        String thresholdRaw = txtProjectDetectThreshold != null ? txtProjectDetectThreshold.getText().trim() : "75";

        int threshold;
        try {
            threshold = Integer.parseInt(thresholdRaw);
        } catch (Exception ex) {
            showError(tr("config.projectDetection.threshold.invalid", "El umbral debe ser un numero entre 0 y 100."));
            return false;
        }

        if (threshold < 0 || threshold > 100) {
            showError(tr("config.projectDetection.threshold.invalid", "El umbral debe ser un numero entre 0 y 100."));
            return false;
        }

        configModel.setConfigProperty("project_detect_auto_apply", Boolean.toString(autoApply));
        configModel.setConfigProperty("project_detect_auto_apply_threshold", Integer.toString(threshold));

        if (txtProjectDetectThreshold != null) {
            txtProjectDetectThreshold.setText(Integer.toString(threshold));
        }

        updateProjectDetectionControlsState();

        if (persist) {
            persistConfig();
        }

        return true;
    }

    private void updateProjectDetectionControlsState() {
        boolean enabled = chkProjectDetectAutoApply == null || chkProjectDetectAutoApply.isSelected();
        if (txtProjectDetectThreshold != null) {
            txtProjectDetectThreshold.setDisable(!enabled);
        }
        if (lblProjectDetectThreshold != null) {
            lblProjectDetectThreshold.setDisable(!enabled);
        }
    }

    private boolean persistZ88dkFlags(boolean persist) {
        if (!validateZ88dkTargets()) {
            return false;
        }

        String spectrumClibOption = comboZ88dkSpectrumClib != null ? comboZ88dkSpectrumClib.getValue() : ConfigModel.Z88DK_CLIB_NEW;
        String spectrumUseCrtOrg = chkZ88dkSpectrumCrtOrgCode != null ? Boolean.toString(chkZ88dkSpectrumCrtOrgCode.isSelected()) : "true";
        String cpcClibOption = comboZ88dkCpcClib != null ? comboZ88dkCpcClib.getValue() : ConfigModel.Z88DK_CLIB_DEFAULT;
        String cpcUseCrtOrg = chkZ88dkCpcCrtOrgCode != null ? Boolean.toString(chkZ88dkCpcCrtOrgCode.isSelected()) : "false";

        configModel.setConfigProperty("z88dk_clib_option", spectrumClibOption != null ? spectrumClibOption : ConfigModel.Z88DK_CLIB_NEW);
        configModel.setConfigProperty("z88dk_use_pragma_crt_org_code_32768", spectrumUseCrtOrg);
        configModel.setConfigProperty("z88dk_target", txtZ88dkSpectrumTarget != null ? txtZ88dkSpectrumTarget.getText().trim() : "+zx");
        configModel.setConfigProperty("z88dk_create_app", chkZ88dkSpectrumCreateApp != null ? Boolean.toString(chkZ88dkSpectrumCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_defines", txtZ88dkSpectrumDefines != null ? txtZ88dkSpectrumDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_includes", txtZ88dkSpectrumIncludes != null ? txtZ88dkSpectrumIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_extra_args", txtZ88dkSpectrumExtraArgs != null ? txtZ88dkSpectrumExtraArgs.getText().trim() : "");

        configModel.setConfigProperty("z88dk_cpc_clib_option", cpcClibOption != null ? cpcClibOption : ConfigModel.Z88DK_CLIB_DEFAULT);
        configModel.setConfigProperty("z88dk_cpc_use_pragma_crt_org_code_32768", cpcUseCrtOrg);
        configModel.setConfigProperty("z88dk_cpc_target", txtZ88dkCpcTarget != null ? txtZ88dkCpcTarget.getText().trim() : "+cpc");
        configModel.setConfigProperty("z88dk_cpc_create_app", chkZ88dkCpcCreateApp != null ? Boolean.toString(chkZ88dkCpcCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_cpc_defines", txtZ88dkCpcDefines != null ? txtZ88dkCpcDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_cpc_includes", txtZ88dkCpcIncludes != null ? txtZ88dkCpcIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_cpc_extra_args", txtZ88dkCpcExtraArgs != null ? txtZ88dkCpcExtraArgs.getText().trim() : "");

        String smsClibOption = comboZ88dkSmsClib != null ? comboZ88dkSmsClib.getValue() : ConfigModel.Z88DK_CLIB_DEFAULT;
        configModel.setConfigProperty("z88dk_sms_clib_option", smsClibOption != null ? smsClibOption : ConfigModel.Z88DK_CLIB_DEFAULT);
        configModel.setConfigProperty("z88dk_sms_target", txtZ88dkSmsTarget != null ? txtZ88dkSmsTarget.getText().trim() : "+sms");
        configModel.setConfigProperty("z88dk_sms_create_app", chkZ88dkSmsCreateApp != null ? Boolean.toString(chkZ88dkSmsCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_sms_defines", txtZ88dkSmsDefines != null ? txtZ88dkSmsDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_sms_includes", txtZ88dkSmsIncludes != null ? txtZ88dkSmsIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_sms_extra_args", txtZ88dkSmsExtraArgs != null ? txtZ88dkSmsExtraArgs.getText().trim() : "");

        String ggClibOption = comboZ88dkGgClib != null ? comboZ88dkGgClib.getValue() : ConfigModel.Z88DK_CLIB_DEFAULT;
        configModel.setConfigProperty("z88dk_gg_clib_option", ggClibOption != null ? ggClibOption : ConfigModel.Z88DK_CLIB_DEFAULT);
        configModel.setConfigProperty("z88dk_gg_target", txtZ88dkGgTarget != null ? txtZ88dkGgTarget.getText().trim() : ConfigModel.Z88DK_TARGET_GAME_GEAR_DEFAULT);
        configModel.setConfigProperty("z88dk_gg_create_app", chkZ88dkGgCreateApp != null ? Boolean.toString(chkZ88dkGgCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_gg_defines", txtZ88dkGgDefines != null ? txtZ88dkGgDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_gg_includes", txtZ88dkGgIncludes != null ? txtZ88dkGgIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_gg_extra_args", txtZ88dkGgExtraArgs != null ? txtZ88dkGgExtraArgs.getText().trim() : "");

        String msxClibOption = comboZ88dkMsxClib != null ? comboZ88dkMsxClib.getValue() : ConfigModel.Z88DK_CLIB_DEFAULT;
        configModel.setConfigProperty("z88dk_msx_clib_option", msxClibOption != null ? msxClibOption : ConfigModel.Z88DK_CLIB_DEFAULT);
        configModel.setConfigProperty("z88dk_msx_target", txtZ88dkMsxTarget != null ? txtZ88dkMsxTarget.getText().trim() : "+msx");
        configModel.setConfigProperty("z88dk_msx_create_app", chkZ88dkMsxCreateApp != null ? Boolean.toString(chkZ88dkMsxCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_msx_defines", txtZ88dkMsxDefines != null ? txtZ88dkMsxDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_msx_includes", txtZ88dkMsxIncludes != null ? txtZ88dkMsxIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_msx_extra_args", txtZ88dkMsxExtraArgs != null ? txtZ88dkMsxExtraArgs.getText().trim() : "");

        if (persist) {
            persistConfig();
            persistCompilerSettingsToActiveWorkspace();
        }

        return true;
    }

    private boolean validateZ88dkTargets() {
        String spectrumTarget = txtZ88dkSpectrumTarget != null ? txtZ88dkSpectrumTarget.getText().trim() : "";
        if (spectrumTarget.isEmpty() || !spectrumTarget.startsWith("+")) {
            showError(tr("config.compiler.z88dk.target.invalid", "El target de Z88DK debe empezar por '+' (ejemplo: +zx)."));
            return false;
        }

        String cpcTarget = txtZ88dkCpcTarget != null ? txtZ88dkCpcTarget.getText().trim() : "";
        if (cpcTarget.isEmpty() || !cpcTarget.startsWith("+")) {
            showError(tr("config.compiler.z88dk.target.invalid", "El target de Z88DK debe empezar por '+' (ejemplo: +zx)."));
            return false;
        }

        return true;
    }

    /**
     * seleccionar carpeta para bin de spectrum
     * @param event
     */
    @FXML
    public void onSeleccionarSpectrumBin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();

        chooser.setTitle("Seleccionar carpeta bin de Z88DK");

        File selectedDir = chooser.showDialog(txtSpectrumBin.getScene().getWindow());

        if (selectedDir != null) {
            txtSpectrumBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("Z88DK-Spectrum", selectedDir.getAbsolutePath());
        }
    }

    @FXML
    public void onSeleccionarCpcBin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();

        chooser.setTitle("Seleccionar carpeta bin de Z88DK (CPC)");

        File selectedDir = chooser.showDialog(txtCpcBin.getScene().getWindow());

        if (selectedDir != null) {
            txtCpcBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("Z88DK-CPC", selectedDir.getAbsolutePath());
        }
    }

    @FXML
    public void onSeleccionarSmsBin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();

        chooser.setTitle("Seleccionar carpeta bin de Z88DK (SMS)");

        File selectedDir = chooser.showDialog(txtSmsBin.getScene().getWindow());

        if (selectedDir != null) {
            txtSmsBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("Z88DK-SMS", selectedDir.getAbsolutePath());
        }
    }

    @FXML
    public void onSeleccionarGgBin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();

        chooser.setTitle("Seleccionar carpeta bin de Z88DK (Game Gear)");

        File selectedDir = chooser.showDialog(txtGgBin.getScene().getWindow());

        if (selectedDir != null) {
            txtGgBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("Z88DK-GameGear", selectedDir.getAbsolutePath());
        }
    }

    @FXML
    public void onSeleccionarMsxBin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();

        chooser.setTitle("Seleccionar carpeta bin de Z88DK (MSX)");

        File selectedDir = chooser.showDialog(txtMsxBin.getScene().getWindow());

        if (selectedDir != null) {
            txtMsxBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("Z88DK-MSX", selectedDir.getAbsolutePath());
        }
    }

    /**
     * Cerrar ventana de configuración
     * @param event
     */
    @FXML
    private void onCloseConfig(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();

        if (onCloseCallback != null) onCloseCallback.run();
    }

    /**
     * guardar evento del botón de opción de idioma
     * @param event
     */
    @FXML
    public void onSaveLanguage(ActionEvent event) {
        String selectedDisplayName = comboIdioma.getSelectionModel().getSelectedItem();
        String selectedLanguageCode = languageService.getLanguageCodeByName(selectedDisplayName);
        String selectedReadmeLanguage = comboReadmeLanguage != null
            ? comboReadmeLanguage.getSelectionModel().getSelectedItem()
            : "English";

        // Actualizar modelo y persistir - Usar código de idioma directamente
        configModel.setConfigProperty("idioma", selectedLanguageCode);
        configModel.setConfigProperty("project_readme_language", toReadmeLanguageCode(selectedReadmeLanguage));
        languageService.setLanguage(selectedLanguageCode);
        persistConfig();
        UserActionMonitor.languageChanged(selectedLanguageCode);

        // Refrescar textos locales en el diálogo
        refreshTexts(
            tabIdioma,
            tabCompilador,
            lblIdioma,
            lblCompilador,
            lblTituloConfig,
            btnCerrarConfig,
            btnGuardarIdioma,
            btnGuardarCompilador
        );

        // Notificar mediante el listener de propiedad en MainController (también mantener el callback opcional)
        if (onLanguageChanged != null) onLanguageChanged.run();
    }

    @FXML
    public void onSaveEditorAppearance(ActionEvent event) {
        String appearance = ConfigModel.EDITOR_APPEARANCE_MODERN_DARK;
        if (editingTheme != null) {
            syncThemeControlsToModel();
            if (editingTheme.isBuiltIn()) {
                configModel.setEditorTheme(editingTheme.getId());
            } else {
                String name = txtThemeName != null ? txtThemeName.getText() : null;
                if (name != null && !name.isBlank()) {
                    editingTheme.setName(name.trim());
                }
                themeService.saveCustom(editingTheme);
                configModel.setEditorTheme(editingTheme.getId());
            }
            appearance = editingTheme.isDarkBase()
                ? ConfigModel.EDITOR_APPEARANCE_MODERN_DARK
                : ConfigModel.EDITOR_APPEARANCE_CLASSIC;
            refreshThemeComboItems();
            selectThemeInUi(editingTheme.getId());
        }
        configModel.setConfigProperty("editor_appearance", appearance);
        if (comboTerminalShell != null) {
            String selectedShell = comboTerminalShell.getSelectionModel().getSelectedItem();
            configModel.setConfigProperty("terminal_shell", toTerminalShellCode(selectedShell));
        }
        if (chkEnableLogs != null) {
            configModel.setEnableLogs(chkEnableLogs.isSelected());
        }
        if (chkShowHomeOnStartup != null) {
            configModel.setShowHomeOnStartup(chkShowHomeOnStartup.isSelected());
        }
        if (chkSyntaxCheckOnSave != null) {
            configModel.setSyntaxCheckOnSave(chkSyntaxCheckOnSave.isSelected());
        }
        persistConfig();
        applyApplicationAppearanceToCurrentScene(appearance);
        if (onAppearanceApplied != null) {
            onAppearanceApplied.run();
        }
        showError("");
    }

    /**
     * Cargar los campos de la sección GitHub desde la configuración.
     */
    private void loadGithubFromConfig() {
        if (txtGithubToken != null) {
            txtGithubToken.setText(configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));
        }
        if (lblGithubStatus != null) {
            lblGithubStatus.setText("");
        }
    }

    /**
     * Guardar el token de GitHub en la configuración ({@code github.token}).
     */
    @FXML
    public void onSaveGithub(ActionEvent event) {
        if (txtGithubToken == null) return;
        String token = txtGithubToken.getText() == null ? "" : txtGithubToken.getText().trim();
        if (token.isBlank()) {
            showError(tr("config.github.token.empty", "Introduce un token o usa 'Borrar token'."));
            return;
        }
        configModel.setConfigProperty(GitHubAuthService.CONFIG_KEY, token);
        persistConfig();
        showError("");
        if (lblGithubStatus != null) {
            lblGithubStatus.setText(tr("config.github.saved", "Token guardado."));
        }
    }

    /**
     * Borrar el token de GitHub de la configuración.
     */
    @FXML
    public void onClearGithubToken(ActionEvent event) {
        configModel.removeConfigProperty(GitHubAuthService.CONFIG_KEY);
        persistConfig();
        if (txtGithubToken != null) {
            txtGithubToken.clear();
        }
        if (lblGithubStatus != null) {
            lblGithubStatus.setText(tr("config.github.cleared", "Token eliminado."));
        }
        if (lblError != null) {
            lblError.setText("");
        }
    }

    /**
     * Verificar la autenticación de GitHub (cascada gh → env → Ajustes) contra
     * {@code GET /user}. La operación de red se ejecuta en hilo de fondo.
     */
    @FXML
    public void onVerifyGithub(ActionEvent event) {
        if (btnVerifyGithub == null || lblGithubStatus == null) return;

        String typed = txtGithubToken != null ? txtGithubToken.getText() : null;
        if (typed != null && !typed.isBlank()) {
            configModel.setConfigProperty(GitHubAuthService.CONFIG_KEY, typed.trim());
            persistConfig();
        }

        btnVerifyGithub.setDisable(true);
        progressGithubVerify.setVisible(true);
        progressGithubVerify.setManaged(true);
        lblGithubStatus.setText(tr("config.github.verifying", "Comprobando autenticación con GitHub..."));

        CompletableFuture.runAsync(() -> {
            try {
                GitHubAuthService auth = new GitHubAuthService(
                    () -> configModel.getConfigProperty(GitHubAuthService.CONFIG_KEY, ""));
                GitHubAuthService.ResolvedToken resolved = auth.resolveToken();
                if (!resolved.isPresent()) {
                    Platform.runLater(() -> renderGithubStatus(
                        tr("config.github.status.noToken", "No hay token configurado (gh, SAMARUC_GITHUB_TOKEN o Ajustes).")));
                    return;
                }
                GitHubAuthService.AuthResult result = auth.validate(resolved.token());
                String sourceLabel = switch (resolved.source()) {
                    case GitHubAuthService.SOURCE_GH -> tr("config.github.status.source.gh", "GitHub CLI (gh)");
                    case GitHubAuthService.SOURCE_ENV -> tr("config.github.status.source.env", "variable de entorno SAMARUC_GITHUB_TOKEN");
                    default -> tr("config.github.status.source.settings", "Ajustes");
                };
                String scopes = result.scopes().isEmpty() ? "-" : String.join(", ", result.scopes());
                String text = tr("config.github.status.ok", "Conectado como {0} (via {1}). Scopes: {2}")
                    .replace("{0}", result.login() != null ? result.login() : "?")
                    .replace("{1}", sourceLabel)
                    .replace("{2}", scopes);
                Platform.runLater(() -> renderGithubStatus(text));
            } catch (GitHubApiService.RateLimitException e) {
                Platform.runLater(() -> renderGithubStatus(e.getMessage()));
            } catch (Exception e) {
                Platform.runLater(() -> renderGithubStatus(
                    tr("config.github.status.error", "Error de autenticación") + ": " + e.getMessage()));
            }
        });
    }

    private void renderGithubStatus(String text) {
        if (btnVerifyGithub != null) btnVerifyGithub.setDisable(false);
        if (progressGithubVerify != null) {
            progressGithubVerify.setVisible(false);
            progressGithubVerify.setManaged(false);
        }
        lblGithubStatus.setText(text);
    }

    /**
     * Refrescar textos de la aplicación según el idioma seleccionado.
     * @param tabIdioma
     * @param tabCompilador
     * @param lblIdioma
     * @param lblCompilador
     * @param lblTituloConfig
     * @param btnCerrarConfig
     * @param btnGuardarIdioma
     * @param btnGuardarCompilador
     */
    public void refreshTexts(
        Tab    tabIdioma,
        Tab    tabCompilador,
        Label  lblIdioma,
        Label  lblCompilador,
        Label  lblTituloConfig,
        Button btnCerrarConfig,
        Button btnGuardarIdioma,
        Button btnGuardarCompilador
    ) {
        String lang           = configModel.getConfigProperty("idioma", "es");
        Locale locale         = Locale.forLanguageTag(lang);
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.MessagesBundle", locale);

        if (tabIdioma            != null) tabIdioma            .setText(bundle.getString("label.languageTab"));
        if (tabAppearance        != null) tabAppearance        .setText(bundle.getString("label.appearanceTab"));
        if (lblIdioma            != null) lblIdioma            .setText(bundle.getString("label.language"));
        if (lblEditorAppearance  != null) lblEditorAppearance  .setText(bundle.getString("label.editorAppearance"));
        if (lblThemeMode != null) lblThemeMode.setText(bundle.containsKey("config.theme.mode") ? bundle.getString("config.theme.mode") : "Modo:");
        if (lblThemeName != null) lblThemeName.setText(bundle.containsKey("config.theme.name") ? bundle.getString("config.theme.name") : "Nombre del modo:");
        if (lblThemeFont != null) lblThemeFont.setText(bundle.containsKey("config.theme.font.family") ? bundle.getString("config.theme.font.family") : "Fuente del editor:");
        if (lblThemeFontSize != null) lblThemeFontSize.setText(bundle.containsKey("config.theme.font.size") ? bundle.getString("config.theme.font.size") : "Tamaño:");
        if (lblThemePreview != null) lblThemePreview.setText(bundle.containsKey("config.theme.preview") ? bundle.getString("config.theme.preview") : "Vista previa:");
        if (btnDuplicateTheme != null) btnDuplicateTheme.setText(bundle.containsKey("config.theme.duplicate.button") ? bundle.getString("config.theme.duplicate.button") : "Duplicar");
        if (btnDeleteTheme   != null) btnDeleteTheme  .setText(bundle.containsKey("config.theme.delete.button") ? bundle.getString("config.theme.delete.button") : "Eliminar");
        if (btnResetTheme    != null) btnResetTheme   .setText(bundle.containsKey("config.theme.reset.button") ? bundle.getString("config.theme.reset.button") : "Restablecer");
        if (chkLineNumberAuto != null) chkLineNumberAuto.setText(bundle.containsKey("config.theme.lineno.auto") ? bundle.getString("config.theme.lineno.auto") : "Automatico (tamaño - 1)");
        if (lblReadmeLanguage    != null) lblReadmeLanguage    .setText(bundle.getString("config.readmeLanguage"));
        if (tabCompilador        != null) tabCompilador        .setText(bundle.getString("label.compilerTab"));
        if (tabCompilerGbdk      != null) tabCompilerGbdk      .setText(bundle.getString("label.compiler.gbdkTab"));
        if (tabCompilerZ88dkSpectrum != null) tabCompilerZ88dkSpectrum.setText(bundle.getString("label.compiler.z88dkSpectrumTab"));
        if (tabCompilerZ88dkCpc  != null) tabCompilerZ88dkCpc  .setText(bundle.getString("label.compiler.z88dkCpcTab"));
        if (tabCompilerMakefile  != null) tabCompilerMakefile  .setText(bundle.getString("label.compiler.make.tab"));
        if (tabPlugins           != null) tabPlugins           .setText(bundle.getString("config.plugins.tab"));
        if (tabGitHub != null) tabGitHub.setText(bundle.containsKey("config.github.tab") ? bundle.getString("config.github.tab") : "GitHub");
        if (lblGithubToken != null) lblGithubToken.setText(bundle.containsKey("config.github.token.label") ? bundle.getString("config.github.token.label") : "Token de GitHub (PAT):");
        if (lblGithubTokenWarning != null) lblGithubTokenWarning.setText(bundle.containsKey("config.github.token.warning") ? bundle.getString("config.github.token.warning") : "El token se guarda en el fichero de configuración local de Samaruc. Solo se envía a api.github.com; nunca se incluye en URLs remotas de git.");
        if (btnVerifyGithub != null) btnVerifyGithub.setText(bundle.containsKey("config.github.verify") ? bundle.getString("config.github.verify") : "Verificar");
        if (btnSaveGithub != null) btnSaveGithub.setText(bundle.containsKey("config.github.save") ? bundle.getString("config.github.save") : "Guardar");
        if (btnClearGithubToken != null) btnClearGithubToken.setText(bundle.containsKey("config.github.clear") ? bundle.getString("config.github.clear") : "Borrar token");
        if (lblPluginsInstalled  != null) lblPluginsInstalled  .setText(bundle.getString("config.plugins.installed"));
        if (lblCompilador        != null) lblCompilador        .setText(bundle.getString("label.compiler"));
        if (lblGbdkBundled       != null) lblGbdkBundled       .setText(bundle.getString("label.compiler.gbdk.bundled"));
        if (lblZ88dkSpectrumBundled != null) lblZ88dkSpectrumBundled.setText(bundle.getString("label.compiler.z88dk.bundled"));
        if (lblZ88dkCpcBundled != null) lblZ88dkCpcBundled.setText(bundle.getString("label.compiler.z88dk.bundled"));
        if (lblGbdkOpts          != null) lblGbdkOpts          .setText(bundle.getString("label.compiler.gbdk.opts"));
        if (lblGbdkOptLevel      != null) lblGbdkOptLevel      .setText(bundle.getString("label.compiler.gbdk.opt.level"));
        if (lblGbdkDefines       != null) lblGbdkDefines       .setText(bundle.getString("label.compiler.gbdk.defines"));
        if (lblGbdkIncludes      != null) lblGbdkIncludes      .setText(bundle.getString("label.compiler.gbdk.includes"));
        if (lblGbdkExtraArgs     != null) lblGbdkExtraArgs     .setText(bundle.getString("label.compiler.gbdk.extra"));
        if (lblZ88dkSpectrumBin      != null) lblZ88dkSpectrumBin      .setText(bundle.getString("label.compiler.z88dk.spectrum.bin"));
        if (lblZ88dkSpectrumOpts     != null) lblZ88dkSpectrumOpts     .setText(bundle.getString("label.compiler.z88dk.spectrum.opts"));
        if (lblZ88dkSpectrumClib     != null) lblZ88dkSpectrumClib     .setText(bundle.getString("label.compiler.z88dk.clib"));
        if (lblZ88dkSpectrumTarget   != null) lblZ88dkSpectrumTarget   .setText(bundle.getString("label.compiler.z88dk.target"));
        if (chkZ88dkSpectrumCreateApp!= null) chkZ88dkSpectrumCreateApp.setText(bundle.getString("label.compiler.z88dk.createApp"));
        if (lblZ88dkSpectrumDefines  != null) lblZ88dkSpectrumDefines  .setText(bundle.getString("label.compiler.z88dk.defines"));
        if (lblZ88dkSpectrumIncludes != null) lblZ88dkSpectrumIncludes .setText(bundle.getString("label.compiler.z88dk.includes"));
        if (lblZ88dkSpectrumExtraArgs!= null) lblZ88dkSpectrumExtraArgs.setText(bundle.getString("label.compiler.z88dk.extra"));
        if (lblZ88dkCpcBin           != null) lblZ88dkCpcBin           .setText(bundle.getString("label.compiler.z88dk.cpc.bin"));
        if (lblZ88dkCpcOpts          != null) lblZ88dkCpcOpts          .setText(bundle.getString("label.compiler.z88dk.cpc.opts"));
        if (lblZ88dkCpcClib          != null) lblZ88dkCpcClib          .setText(bundle.getString("label.compiler.z88dk.clib"));
        if (lblZ88dkCpcTarget        != null) lblZ88dkCpcTarget        .setText(bundle.getString("label.compiler.z88dk.target"));
        if (chkZ88dkCpcCreateApp     != null) chkZ88dkCpcCreateApp     .setText(bundle.getString("label.compiler.z88dk.createApp"));
        if (lblZ88dkCpcDefines       != null) lblZ88dkCpcDefines       .setText(bundle.getString("label.compiler.z88dk.defines"));
        if (lblZ88dkCpcIncludes      != null) lblZ88dkCpcIncludes      .setText(bundle.getString("label.compiler.z88dk.includes"));
        if (lblZ88dkCpcExtraArgs     != null) lblZ88dkCpcExtraArgs     .setText(bundle.getString("label.compiler.z88dk.extra"));
        if (lblMakeExecutable    != null) lblMakeExecutable    .setText(bundle.getString("label.compiler.make.executable"));
        if (lblMakeBuildTarget   != null) lblMakeBuildTarget   .setText(bundle.getString("label.compiler.make.buildTarget"));
        if (lblMakeRunTarget     != null) lblMakeRunTarget     .setText(bundle.getString("label.compiler.make.runTarget"));
        if (lblMakeExtraArgs     != null) lblMakeExtraArgs     .setText(bundle.getString("label.compiler.make.extra"));
        if (tabCompilerGcc       != null) tabCompilerGcc       .setText(bundle.getString("label.compiler.gccTab"));
        if (lblGccBin            != null) lblGccBin            .setText(bundle.getString("label.compiler.gcc.bin"));
        if (lblGccOpts           != null) lblGccOpts           .setText(bundle.getString("label.compiler.gcc.opts"));
        if (lblGccOptLevel       != null) lblGccOptLevel       .setText(bundle.getString("label.compiler.gcc.opt.level"));
        if (lblGccDefines        != null) lblGccDefines        .setText(bundle.getString("label.compiler.gcc.defines"));
        if (lblGccIncludes       != null) lblGccIncludes       .setText(bundle.getString("label.compiler.gcc.includes"));
        if (lblGccExtraArgs      != null) lblGccExtraArgs      .setText(bundle.getString("label.compiler.gcc.extra"));
        if (btnSaveGccOpts       != null) btnSaveGccOpts       .setText(bundle.getString("button.saveGcc"));
        if (tabCompilerCc65      != null) tabCompilerCc65      .setText(bundle.getString("label.compiler.cc65Tab"));
        if (lblCc65Bin           != null) lblCc65Bin           .setText(bundle.getString("label.compiler.cc65.bin"));
        if (lblAltirraBin        != null) lblAltirraBin        .setText(bundle.getString("label.compiler.altirra.bin"));
        if (lblViceBin           != null) lblViceBin           .setText(bundle.getString("label.compiler.vice.bin"));
        if (lblCc65Opts          != null) lblCc65Opts          .setText(bundle.getString("label.compiler.cc65.opts"));
        if (lblCc65OptLevel      != null) lblCc65OptLevel      .setText(bundle.getString("label.compiler.cc65.opt.level"));
        if (lblCc65Defines       != null) lblCc65Defines       .setText(bundle.getString("label.compiler.cc65.defines"));
        if (lblCc65Includes      != null) lblCc65Includes      .setText(bundle.getString("label.compiler.cc65.includes"));
        if (lblCc65ExtraArgs     != null) lblCc65ExtraArgs     .setText(bundle.getString("label.compiler.cc65.extra"));
        if (btnSaveCc65Opts      != null) btnSaveCc65Opts      .setText(bundle.getString("button.saveCc65"));
        if (chkProjectDetectAutoApply != null) chkProjectDetectAutoApply.setText(bundle.getString("config.projectDetection.autoApply"));
        if (lblProjectDetectThreshold != null) lblProjectDetectThreshold.setText(bundle.getString("config.projectDetection.threshold"));
        if (btnCerrarConfig      != null) btnCerrarConfig      .setText(bundle.getString("button.close"));
        if (lblTituloConfig      != null) lblTituloConfig      .setText(bundle.getString("label.configTitle"));
        if (btnGuardarIdioma     != null) btnGuardarIdioma     .setText(bundle.getString("button.saveLanguage"));
        if (btnGuardarAppearance != null) btnGuardarAppearance .setText(bundle.getString("button.saveAppearance"));
        if (chkEnableLogs != null) chkEnableLogs.setText(bundle.containsKey("config.appearance.enable_logs") ? bundle.getString("config.appearance.enable_logs") : "Habilitar guardado de logs en archivo (samaruc.log)");
        if (chkShowHomeOnStartup != null) chkShowHomeOnStartup.setText(bundle.containsKey("config.appearance.show_home_on_startup") ? bundle.getString("config.appearance.show_home_on_startup") : "Mostrar página de inicio al arrancar");
        if (chkSyntaxCheckOnSave != null) chkSyntaxCheckOnSave.setText(bundle.containsKey("config.appearance.syntax_check_on_save") ? bundle.getString("config.appearance.syntax_check_on_save") : "Comprobar errores de sintaxis al guardar (GCC)");
        if (btnGuardarCompilador != null) btnGuardarCompilador .setText(bundle.getString("button.saveConfig"));
        if (btnSaveMakeOpts      != null) btnSaveMakeOpts      .setText(bundle.getString("button.saveMake"));
        if (btnGuardarSpectrumBin!= null) btnGuardarSpectrumBin.setText(bundle.getString("button.saveZ88dk"));
        if (btnGuardarCpcBin     != null) btnGuardarCpcBin     .setText(bundle.getString("button.saveZ88dk"));
        if (btnInstallPlugin     != null) btnInstallPlugin     .setText(bundle.getString("config.plugins.install"));
        if (btnEnable            != null) btnEnable            .setText(bundle.getString("config.plugins.enable"));
        if (btnDisable           != null) btnDisable           .setText(bundle.getString("config.plugins.disable"));
        if (btnRemovePlugin      != null) btnRemovePlugin      .setText(bundle.getString("config.plugins.remove"));
        if (btnOpenFolder        != null) btnOpenFolder        .setText(bundle.getString("config.plugins.openFolder"));
        if (btnRefreshPlugins    != null) btnRefreshPlugins    .setText(bundle.getString("config.plugins.refresh"));
        if (lblMarketplace       != null) lblMarketplace       .setText(bundle.getString("config.plugins.marketplace.title"));
        if (lblMarketplaceUrl    != null) lblMarketplaceUrl    .setText(bundle.getString("config.plugins.marketplace.url"));
        if (btnSaveMarketplaceUrl!= null) btnSaveMarketplaceUrl.setText(bundle.getString("config.plugins.marketplace.url.save"));
        if (btnMarketplaceRefresh!= null) btnMarketplaceRefresh.setText(bundle.getString("config.plugins.marketplace.refresh"));
        if (btnMarketplaceInstall!= null) btnMarketplaceInstall.setText(bundle.getString("config.plugins.marketplace.install"));

        if (lblMarketplaceInfo != null && !marketplaceLoading) {
            if (getSelectedMarketplaceItem() != null) {
                onMarketplaceSelected();
            } else {
                lblMarketplaceInfo.setText(bundle.getString("config.plugins.marketplace.select"));
            }
        }

        // Setup Marketplace Idiomas selection
        if (lstMarketplaceIdiomas != null) {
            lstMarketplaceIdiomas.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                int idx = lstMarketplaceIdiomas.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < marketplaceLanguages.size()) {
                    LanguageMarketplaceItem item = marketplaceLanguages.get(idx);
                    lblMarketplaceIdiomasInfo.setText(
                        item.name() + " (" + item.languageCode() + ") v" + item.version() + "\n" +
                        "Autor: " + item.author() + "\n" +
                        "Descripción: " + item.description()
                    );
                } else {
                    lblMarketplaceIdiomasInfo.setText(bundle.containsKey("marketplace.idiomas.select.info") 
                        ? bundle.getString("marketplace.idiomas.select.info") 
                        : "Selecciona un idioma para ver detalles.");
                }
            });
        }

        // Asegurar que i18nDir existe
        if (!i18nDir.exists()) {
            i18nDir.mkdirs();
        }
        languageService.setExternalI18nDir(i18nDir);

        loadLanguageSelectionsFromConfig();
        loadThemeUiFromConfig();
        refreshCompilerComboItems();
    }

    private void loadLanguageSelectionsFromConfig() {
        if (comboIdioma != null) {
            comboIdioma.getItems().clear();
            comboIdioma.getItems().addAll(languageService.getAvailableLanguageNames());
            
            String lang = configModel.getConfigProperty("idioma", "es");
            String displayName = languageService.getLanguageNameByCode(lang);
            comboIdioma.getSelectionModel().select(displayName);
        }

        if (comboReadmeLanguage != null) {
            ResourceBundle bundle = languageService.getCurrentBundle();
            Map<String, String> readmeLanguageCodeByLabel = new HashMap<>();
            
            for (LanguageService.LanguageInfo langInfo : languageService.getAvailableLanguages()) {
                String code = langInfo.code();
                String key = "config.readmeLanguage.option." + code;
                String label = bundle.containsKey(key) 
                    ? bundle.getString(key)
                    : langInfo.displayName();
                readmeLanguageCodeByLabel.put(label, code);
            }
            
            comboReadmeLanguage.getItems().clear();
            comboReadmeLanguage.getItems().addAll(readmeLanguageCodeByLabel.keySet());
            
            String readmeLang = configModel.getConfigProperty("project_readme_language", "en");
            String selectedLabel = readmeLanguageCodeByLabel.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(readmeLang))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(readmeLanguageCodeByLabel.entrySet().stream()
                    .filter(e -> e.getValue().equalsIgnoreCase("en"))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null));
            
            if (selectedLabel != null) {
                comboReadmeLanguage.getSelectionModel().select(selectedLabel);
            }
            
            // Almacenar el mapeo en el atributo del combo para acceso posterior
            comboReadmeLanguage.setUserData(readmeLanguageCodeByLabel);
        }
    }

    private String toReadmeLanguageCode(String displayValue) {
        if (displayValue == null || displayValue.isBlank()) return "en";
        
        // Intentar obtener el mapeo del combo
        if (comboReadmeLanguage != null && comboReadmeLanguage.getUserData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> mapping = (Map<String, String>) comboReadmeLanguage.getUserData();
            String code = mapping.get(displayValue);
            if (code != null) return code;
        }
        
        // Fallback: buscar por código directo
        if (languageService.isLanguageAvailable(displayValue)) return displayValue;
        
        // Fallback final: mapear por nombre legible
        return languageService.getLanguageCodeByName(displayValue);
    }


    private void loadThemeUiFromConfig() {
        if (comboThemeMode == null) return;
        buildThemeControls();
        buildThemePreview();
        wireThemeControls();
        refreshThemeComboItems();
        selectThemeInUi(themeService.resolveActiveThemeId(configModel));
    }

    private void refreshThemeComboItems() {
        if (comboThemeMode == null) return;
        String selectedId = currentThemeId();
        comboThemeMode.getItems().clear();
        comboThemeMode.getItems().addAll(themeService.getAllThemes());
        if (selectedId != null && themeService.getTheme(selectedId) != null) {
            comboThemeMode.getSelectionModel().select(themeService.getTheme(selectedId));
        }
    }

    private String currentThemeId() {
        return editingTheme != null && editingTheme.getId() != null
            ? editingTheme.getId()
            : BuiltInThemes.MODERN_DARK_ID;
    }

    private String themeItemLabel(EditorTheme theme) {
        if (theme == null) return "";
        return theme.getName() != null ? theme.getName() : theme.getId();
    }

    /**
     * Construye una vez las secciones de colores y los selectores asociados.
     */
    private void buildThemeControls() {
        if (vboxThemeColors == null || !vboxThemeColors.getChildren().isEmpty()) return;

        if (comboThemeMode != null) {
            comboThemeMode.setCellFactory(list -> new javafx.scene.control.ListCell<EditorTheme>() {
                @Override
                protected void updateItem(EditorTheme item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : themeItemLabel(item));
                }
            });
            comboThemeMode.setButtonCell(new javafx.scene.control.ListCell<EditorTheme>() {
                @Override
                protected void updateItem(EditorTheme item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : themeItemLabel(item));
                }
            });
        }

        if (comboFontFamily != null) {
            comboFontFamily.setEditable(true);
            List<String> families = new ArrayList<>();
            families.add("JetBrains Mono");
            families.add("Cascadia Mono");
            families.add("Source Code Pro");
            families.add("Consolas");
            families.add("Fira Code");
            families.add("DejaVu Sans Mono");
            for (String family : javafx.scene.text.Font.getFamilies()) {
                if (!families.contains(family)) {
                    families.add(family);
                }
            }
            comboFontFamily.getItems().setAll(families);
        }

        if (spFontSize != null && spFontSize.getValueFactory() == null) {
            spFontSize.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(8, 32, 13, 0.5));
        }
        if (spLineNumberSize != null && spLineNumberSize.getValueFactory() == null) {
            spLineNumberSize.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(6, 40, 12, 0.5));
        }

        ResourceBundle bundle = getCurrentBundle();
        for (ThemeSection section : THEME_SECTIONS) {
            String title = tr(section.titleKey(), "Colores");
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(6);
            int row = 0;
            for (ThemeToken token : section.tokens()) {
                String labelText = tr(token.labelKey(), token.colorKey());
                Label label = new Label(labelText);
                ColorPicker picker = new ColorPicker();
                picker.setPrefWidth(90);
                picker.setOnAction(e -> {
                    if (syncingThemeUi || editingTheme == null) return;
                    String formatted = colorToString(picker.getValue());
                    editingTheme.setColor(token.colorKey(), formatted);
                    TextField field = themeColorFields.get(token.colorKey());
                    if (field != null) {
                        field.setText(formatted);
                    }
                    refreshThemePreviews();
                });
                TextField hexField = new TextField();
                hexField.setPrefWidth(100);
                hexField.setPromptText("#RRGGBB");
                java.util.function.Consumer<String> commitHex = text -> {
                    if (syncingThemeUi || editingTheme == null) return;
                    Color color = parseColor(text);
                    if (color == null) {
                        // Entrada invalida: restaurar el ultimo color valido
                        String current = editingTheme.getColor(token.colorKey());
                        hexField.setText(current != null && !current.isBlank() ? current : "");
                        return;
                    }
                    String formatted = colorToString(color);
                    editingTheme.setColor(token.colorKey(), formatted);
                    picker.setValue(color);
                    hexField.setText(formatted);
                    refreshThemePreviews();
                };
                hexField.setOnAction(e -> commitHex.accept(hexField.getText()));
                hexField.focusedProperty().addListener((obs, wasFocused, focused) -> {
                    if (!focused) {
                        commitHex.accept(hexField.getText());
                    }
                });
                themeColorPickers.put(token.colorKey(), picker);
                themeColorFields.put(token.colorKey(), hexField);
                HBox pickerRow = new HBox(6, picker, hexField);
                pickerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                grid.add(label, 0, row);
                grid.add(pickerRow, 1, row);
                row++;
            }
            TitledPane pane = new TitledPane(title, grid);
            pane.setAnimated(false);
            pane.setCollapsible(true);
            vboxThemeColors.getChildren().add(pane);
        }
    }

    /**
     * Construye una vez la vista previa en vivo (uno ventral por lenguaje).
     */
    private void buildThemePreview() {
        if (vboxThemePreview == null || !vboxThemePreview.getChildren().isEmpty()) return;

        for (Map.Entry<String, String> entry : ThemePreviewBuilder.buildSampleTexts().entrySet()) {
            String extension = entry.getKey();
            String sample = entry.getValue();

            CodeArea area = new CodeArea();
            area.setEditable(false);
            area.setParagraphGraphicFactory(LineNumberFactory.get(area));
            area.replaceText(sample);
            area.getStyleClass().add("code-area");
            area.setPrefHeight(110);
            area.setMaxHeight(130);

            try {
                area.setStyleSpans(0, previewHighlighter.computeHighlighting(sample, "preview." + extension));
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            Label caption = new Label(extension.toUpperCase(Locale.ROOT));
            caption.setStyle("-fx-font-weight: bold;");
            VBox box = new VBox(4, caption, area);
            previewAreas.add(area);
            vboxThemePreview.getChildren().add(box);
        }
    }

    private void wireThemeControls() {
        if (tabAppearance != null && Boolean.TRUE.equals(tabAppearance.getProperties().get("themeControlsWired"))) {
            return;
        }
        if (tabAppearance != null) {
            tabAppearance.getProperties().put("themeControlsWired", Boolean.TRUE);
        }

        if (comboThemeMode != null) {
            comboThemeMode.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (syncingThemeUi) return;
                syncThemeControlsToModel();
                if (newV != null) {
                    selectThemeInUi(newV.getId());
                }
            });
        }

        if (txtThemeName != null) {
            txtThemeName.textProperty().addListener((obs, oldV, newV) -> {
                if (syncingThemeUi || editingTheme == null || editingTheme.isBuiltIn()) return;
                editingTheme.setName(newV);
            });
        }

        if (comboFontFamily != null) {
            comboFontFamily.getEditor().textProperty().addListener((obs, oldV, newV) -> {
                if (syncingThemeUi || editingTheme == null) return;
                editingTheme.setFontFamily(trimFontFamily(newV));
                refreshThemePreviews();
            });
        }

        if (spFontSize != null) {
            spFontSize.valueProperty().addListener((obs, oldV, newV) -> {
                if (syncingThemeUi || editingTheme == null || newV == null) return;
                editingTheme.setFontSize(newV);
                if (chkLineNumberAuto != null && chkLineNumberAuto.isSelected() && spLineNumberSize != null) {
                    spLineNumberSize.getValueFactory().setValue(Math.max(1, newV - 1));
                }
                refreshThemePreviews();
            });
        }

        if (chkLineNumberAuto != null) {
            chkLineNumberAuto.selectedProperty().addListener((obs, oldV, newV) -> {
                if (syncingThemeUi || editingTheme == null) return;
                if (Boolean.TRUE.equals(newV)) {
                    editingTheme.setLineNumberSize(-1);
                    if (spLineNumberSize != null) {
                        spLineNumberSize.setDisable(true);
                    }
                } else {
                    if (spLineNumberSize != null) {
                        spLineNumberSize.setDisable(false);
                        editingTheme.setLineNumberSize(spLineNumberSize.getValue());
                    }
                }
                refreshThemePreviews();
            });
        }

        if (spLineNumberSize != null) {
            spLineNumberSize.valueProperty().addListener((obs, oldV, newV) -> {
                if (syncingThemeUi || editingTheme == null || newV == null) return;
                if (chkLineNumberAuto == null || !chkLineNumberAuto.isSelected()) {
                    editingTheme.setLineNumberSize(newV);
                    refreshThemePreviews();
                }
            });
        }
    }

    private void selectThemeInUi(String themeId) {
        EditorTheme theme = themeService.getTheme(themeId);
        if (theme == null) {
            theme = BuiltInThemes.modernDark();
        }
        editingTheme = theme.copy();

        syncingThemeUi = true;
        try {
            final EditorTheme target = theme;
            if (comboThemeMode != null && comboThemeMode.getSelectionModel().getSelectedItem() == null
                || comboThemeMode != null && comboThemeMode.getSelectionModel().getSelectedItem() != null
                    && !target.getId().equals(comboThemeMode.getSelectionModel().getSelectedItem().getId())) {
                comboThemeMode.getItems().stream()
                    .filter(t -> target.getId().equals(t.getId()))
                    .findFirst()
                    .ifPresent(t -> comboThemeMode.getSelectionModel().select(t));
            }
            if (txtThemeName != null) {
                txtThemeName.setText(theme.getName() != null ? theme.getName() : "");
            }
            if (comboFontFamily != null) {
                comboFontFamily.setValue(displayFont(theme.getFontFamily()));
            }
            boolean autoLineNumbers = theme.getLineNumberSize() <= 0;
            if (chkLineNumberAuto != null) {
                chkLineNumberAuto.setSelected(autoLineNumbers);
            }
            if (spFontSize != null) {
                spFontSize.getValueFactory().setValue(theme.getFontSize());
            }
            if (spLineNumberSize != null) {
                double ln = autoLineNumbers ? Math.max(1, theme.getFontSize() - 1) : theme.getLineNumberSize();
                spLineNumberSize.getValueFactory().setValue(ln);
                spLineNumberSize.setDisable(autoLineNumbers);
            }
            for (Map.Entry<String, ColorPicker> entry : themeColorPickers.entrySet()) {
                String raw = theme.getColor(entry.getKey());
                Color color = parseColor(raw);
                if (color != null) {
                    entry.getValue().setValue(color);
                }
                TextField field = themeColorFields.get(entry.getKey());
                if (field != null) {
                    field.setText(color != null ? colorToString(color) : (raw != null ? raw : ""));
                }
            }
            setThemeEditEnabled(!theme.isBuiltIn());
        } finally {
            syncingThemeUi = false;
        }
        refreshThemePreviews();
    }

    private void setThemeEditEnabled(boolean enabled) {
        if (txtThemeName != null) txtThemeName.setEditable(enabled);
        if (comboFontFamily != null) comboFontFamily.setDisable(!enabled);
        if (spFontSize != null) spFontSize.setDisable(!enabled);
        if (chkLineNumberAuto != null) chkLineNumberAuto.setDisable(!enabled);
        if (spLineNumberSize != null) {
            boolean auto = editingTheme != null && editingTheme.getLineNumberSize() <= 0;
            spLineNumberSize.setDisable(!enabled || auto);
        }
        for (ColorPicker picker : themeColorPickers.values()) {
            picker.setDisable(!enabled);
        }
        for (TextField field : themeColorFields.values()) {
            field.setDisable(!enabled);
        }
    }

    private void refreshThemePreviews() {
        if (editingTheme == null || previewAreas.isEmpty()) return;
        String css = ThemeCssGenerator.generateCss(editingTheme);
        String url = com.retroeditor.util.FXUtils.dataCssUrl(css);
        for (CodeArea area : previewAreas) {
            area.getStylesheets().clear();
            if (url != null) {
                area.getStylesheets().add(url);
            }
        }
    }

    private void syncThemeControlsToModel() {
        if (editingTheme == null || syncingThemeUi) return;

        if (txtThemeName != null && !editingTheme.isBuiltIn()) {
            String name = txtThemeName.getText();
            if (name != null && !name.isBlank()) {
                editingTheme.setName(name.trim());
            }
        }
        if (comboFontFamily != null) {
            editingTheme.setFontFamily(trimFontFamily(comboFontFamily.getEditor().getText()));
        }
        if (spFontSize != null && spFontSize.getValue() != null) {
            editingTheme.setFontSize(spFontSize.getValue());
        }
        boolean autoLineNumbers = chkLineNumberAuto != null && chkLineNumberAuto.isSelected();
        if (autoLineNumbers) {
            editingTheme.setLineNumberSize(-1);
        } else if (spLineNumberSize != null && spLineNumberSize.getValue() != null) {
            editingTheme.setLineNumberSize(spLineNumberSize.getValue());
        }
    }

    @FXML
    public void onDuplicateTheme(ActionEvent event) {
        if (event != null) event.consume();
        try {
            AppLogger.logMonitor("CONFIG",
                "Boton Duplicar pulsado (tema actual: " + currentThemeId() + ", combo items: "
                    + (comboThemeMode == null ? "null" : comboThemeMode.getItems().size()) + ")");
            syncThemeControlsToModel();

            TextInputDialog dialog = new TextInputDialog(tr("config.theme.duplicate.defaultName", "Modo personalizado"));
            dialog.setTitle(tr("config.theme.duplicate.title", "Duplicar modo"));
            dialog.setHeaderText(tr("config.theme.duplicate.header", "Nombre del nuevo modo personalizado:"));
            initDialogOwner(dialog, event);
            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty() || result.get().isBlank()) return;
            AppLogger.logMonitor("CONFIG", "Nombre aceptado: " + result.get());

            EditorTheme created = duplicateCurrentTheme(result.get());
            AppLogger.logMonitor("CONFIG", "Modo duplicado: " + created.getId());
        } catch (Exception ex) {
            AppLogger.logMonitor("CONFIG", "Error al duplicar modo: " + ex.getMessage());
            ex.printStackTrace();
            showError("Error al duplicar modo: " + ex.getMessage());
        }
    }

    /**
     * Asigna la ventana de la configuracion como propietaria de un dialogo para
     * que no quede detras del editor en Windows.
     */
    private void initDialogOwner(javafx.scene.control.Dialog<?> dialog, ActionEvent event) {
        try {
            javafx.scene.Node source = event != null ? (javafx.scene.Node) event.getSource() : null;
            javafx.stage.Window owner = source != null ? source.getScene().getWindow() : null;
            if (owner != null) {
                dialog.initOwner(owner);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Crea un modo personalizado a partir del modo actualmente seleccionado y
     * lo selecciona en la interfaz. Separado de {@link #onDuplicateTheme(ActionEvent)}
     * para poder probar el flujo sin abrir el dialogo.
     *
     * @param themeName nombre visible del nuevo modo
     * @return el modo personalizado creado
     */
    EditorTheme duplicateCurrentTheme(String themeName) {
        EditorTheme created = themeService.duplicate(currentThemeId(), themeName);
        selectThemeInUi(created.getId());
        refreshThemeComboItems();
        showError("");
        return created;
    }

    @FXML
    public void onDeleteTheme(ActionEvent event) {
        if (event != null) event.consume();
        if (editingTheme == null || editingTheme.isBuiltIn()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(tr("config.theme.delete.title", "Eliminar modo"));
        confirm.setHeaderText(tr("config.theme.delete.header", "Eliminar el modo personalizado")
            + ": " + editingTheme.getName());
        confirm.setContentText(tr("config.theme.delete.body", "Esta accion no se puede deshacer."));
        initDialogOwner(confirm, event);
        try {
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            themeService.deleteCustom(editingTheme.getId());
            if (BuiltInThemes.MODERN_DARK_ID.equals(currentThemeId())) {
                configModel.setEditorTheme(BuiltInThemes.MODERN_DARK_ID);
            } else if (themeService.getTheme(currentThemeId()) == null) {
                configModel.setEditorTheme(BuiltInThemes.MODERN_DARK_ID);
            }
            selectThemeInUi(BuiltInThemes.MODERN_DARK_ID);
            refreshThemeComboItems();
            showError("");
        } catch (Exception ex) {
            AppLogger.logMonitor("CONFIG", "Error al eliminar modo: " + ex.getMessage());
            ex.printStackTrace();
            showError("Error al eliminar modo: " + ex.getMessage());
        }
    }

    @FXML
    public void onResetTheme(ActionEvent event) {
        if (event != null) event.consume();
        if (editingTheme == null || editingTheme.isBuiltIn()) return;

        EditorTheme base = editingTheme.isDarkBase() ? BuiltInThemes.modernDark() : BuiltInThemes.classic();
        for (Map.Entry<String, String> colors : base.getColors().entrySet()) {
            editingTheme.setColor(colors.getKey(), colors.getValue());
        }
        editingTheme.setFontFamily(base.getFontFamily());
        editingTheme.setFontSize(base.getFontSize());
        editingTheme.setLineNumberSize(base.getLineNumberSize());
        selectThemeInUi(editingTheme.getId());
        showError("");
    }

    private static String displayFont(String fullFamily) {
        if (fullFamily == null || fullFamily.isBlank()) return "";
        return fullFamily.split(",")[0].trim();
    }

    private static String trimFontFamily(String value) {
        return value != null ? value.trim() : "";
    }

    private static String colorToString(Color color) {
        if (color == null) return "";
        if (color.getOpacity() >= 0.999) {
            return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
        }
        return String.format("rgba(%d, %d, %d, %s)",
            Math.round(color.getRed() * 255),
            Math.round(color.getGreen() * 255),
            Math.round(color.getBlue() * 255),
            String.format(Locale.ROOT, "%.2f", color.getOpacity()));
    }

    private static Color parseColor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Color.web(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private void loadTerminalShellFromConfig() {
        if (comboTerminalShell == null) return;

        comboTerminalShell.getItems().clear();
        terminalShellCodeByLabel.clear();
        for (String code : ConfigModel.getSupportedTerminalShells()) {
            String label = terminalShellLabel(code);
            comboTerminalShell.getItems().add(label);
            terminalShellCodeByLabel.put(label, code);
        }

        String shell = configModel.getConfigProperty("terminal_shell", ConfigModel.TERMINAL_SHELL_AUTO);
        comboTerminalShell.getSelectionModel().select(terminalShellLabel(shell));
    }

    private String terminalShellLabel(String code) {
        if (ConfigModel.TERMINAL_SHELL_CMD.equals(code)) {
            return tr("config.terminal.shell.option.cmd", "cmd.exe (Windows)");
        }
        if (ConfigModel.TERMINAL_SHELL_POWERSHELL.equals(code)) {
            return tr("config.terminal.shell.option.powershell", "PowerShell (Windows)");
        }
        if (ConfigModel.TERMINAL_SHELL_PWSH.equals(code)) {
            return tr("config.terminal.shell.option.pwsh", "PowerShell 7 (pwsh)");
        }
        if (ConfigModel.TERMINAL_SHELL_BASH.equals(code)) {
            return tr("config.terminal.shell.option.bash", "Bash (Linux/macOS)");
        }
        return tr("config.terminal.shell.option.auto", "Automatico (recomendado)");
    }

    private String toTerminalShellCode(String displayLabel) {
        if (displayLabel == null || displayLabel.isBlank()) {
            return ConfigModel.TERMINAL_SHELL_AUTO;
        }
        String mapped = terminalShellCodeByLabel.get(displayLabel);
        return mapped != null ? mapped : ConfigModel.TERMINAL_SHELL_AUTO;
    }

    private void applyApplicationAppearanceToCurrentScene(String appearance) {
        if (tabPaneConfig == null || tabPaneConfig.getScene() == null) return;
        String classicCss = resolveStylesheet("/css/app.css");
        String darkCss = resolveStylesheet("/css/app-dark.css");
        Scene scene = tabPaneConfig.getScene();
        if (classicCss != null) scene.getStylesheets().remove(classicCss);
        if (darkCss != null) scene.getStylesheets().remove(darkCss);

        String activeCssPath = ConfigModel.EDITOR_APPEARANCE_CLASSIC.equalsIgnoreCase(appearance) ? "/css/app.css" : "/css/app-dark.css";
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

    private ResourceBundle getCurrentBundle() {
        String lang = configModel.getConfigProperty("idioma", "es");
        languageService.setLanguage(lang);
        return languageService.getCurrentBundle();
    }
}
