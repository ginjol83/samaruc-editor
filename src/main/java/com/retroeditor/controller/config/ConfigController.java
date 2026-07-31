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
import java.util.stream.Collectors;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.LanguageMarketplaceItem;
import com.retroeditor.model.WorkspaceModel;
import com.retroeditor.model.PluginMarketplaceItem;
import com.retroeditor.plugin.PluginInfo;
import com.retroeditor.plugin.PluginManager;
import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.AppLogger;
import com.retroeditor.service.LanguageMarketplaceService;
import com.retroeditor.service.LanguageService;
import com.retroeditor.service.PluginApplicationService;
import com.retroeditor.service.PluginMarketplaceService;
import com.retroeditor.service.PropertiesConfigRepository;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.service.WorkspaceService;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ConfigController {
    private static final String COMPILER_GBDK      = "GBDK";
    private static final String COMPILER_Z88DK     = "z88dk";
    private static final String COMPILER_MAKEFILE  = "Makefile";
    private static final String LEGACY_DISPLAY_Z88DK = "Z88DK";
    private static final String LEGACY_SPECTRUM    = "Spectrum";
    private static final String Z88DK_PROFILE_SPECTRUM = "spectrum";
    private static final String Z88DK_PROFILE_CPC = "cpc";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY   = "JSpeccy";
    private static final String EMULATOR_CPCBOX_WEB = "CPCBoxWeb";

    @FXML private TabPane          tabPaneConfig;
    @FXML private TabPane          tabPaneCompilerOptions;
    @FXML private TextField        txtGbdkBin;
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
    @FXML private ComboBox<String> comboGbdkOptLevel;
    @FXML private CheckBox         chkGbdkOptSpeed;
    @FXML private CheckBox         chkGbdkOptSize;
    @FXML private CheckBox         chkProjectDetectAutoApply;
    @FXML private CheckBox         chkEnableLogs;
    @FXML private CheckBox         chkShowHomeOnStartup;
    @FXML private TextField        txtProjectDetectThreshold;
    @FXML private TextField        txtMakeExecutable;
    @FXML private TextField        txtMakeBuildTarget;
    @FXML private TextField        txtMakeRunTarget;
    @FXML private TextField        txtMakeExtraArgs;
    @FXML private Tab              tabIdioma;
    @FXML private Tab              tabAppearance;
    @FXML private Tab              tabCompilador;
    @FXML private Tab              tabCompilerGbdk;
    @FXML private Tab              tabCompilerZ88dkSpectrum;
    @FXML private Tab              tabCompilerZ88dkCpc;
    @FXML private Tab              tabCompilerMakefile;
    @FXML private Tab              tabPlugins;
    @FXML private Label            lblError;
    @FXML private Label            lblIdioma;
    @FXML private Label            lblEditorAppearance;
    @FXML private Label            lblReadmeLanguage;
    @FXML private Label            lblCompilador;
    @FXML private Label            lblGbdkBin;
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
    @FXML private ComboBox<String> comboEditorAppearance;
    @FXML private ComboBox<String> comboReadmeLanguage;
    @FXML private ComboBox<String> comboCompilador;

    @FXML private Button           btnSeleccionarGbdkBin;
    @FXML private Button           btnGuardarGbdkBin;
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
    private boolean syncingCompilerUi = false;
    private final Map<String, String> editorAppearanceCodeByLabel = new HashMap<>();

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
        String expectedEmulator = getEmulatorForCompiler(normalizedCompiler, normalizedProfile);
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

        loadLanguageSelectionsFromConfig();
        loadEditorAppearanceFromConfig();

        if (chkEnableLogs != null) {
            chkEnableLogs.setSelected(configModel.isEnableLogs());
        }
        if (chkShowHomeOnStartup != null) {
            chkShowHomeOnStartup.setSelected(configModel.isShowHomeOnStartup());
        }

        if (txtGbdkBin != null) {
            txtGbdkBin.setText(configModel.getConfigProperty("gbdk_bin", ""));
        }

        loadZ88dkFlagsFromConfig();
        loadGbdkOptsFromConfig();
        loadMakeOptsFromConfig();
        loadProjectDetectionPrefsFromConfig();
        if (txtMarketplaceUrl != null) {
            txtMarketplaceUrl.setText(configModel.getConfigProperty("marketplace_catalog_url", PluginMarketplaceService.DEFAULT_MARKETPLACE_URL));
        }

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

        loadLanguageSelectionsFromConfig();
        loadEditorAppearanceFromConfig();

        if (txtGbdkBin != null) {
            txtGbdkBin.setText(configModel.getConfigProperty("gbdk_bin", ""));
        }

        loadZ88dkFlagsFromConfig();
        loadGbdkOptsFromConfig();
        loadMakeOptsFromConfig();
        loadProjectDetectionPrefsFromConfig();
        if (txtMarketplaceUrl != null) {
            txtMarketplaceUrl.setText(configModel.getConfigProperty("marketplace_catalog_url", PluginMarketplaceService.DEFAULT_MARKETPLACE_URL));
        }

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

    /**
     * Guardar ruta de bin de GBDK
     * @param event
     */
    @FXML
    public void onSaveGbdkBin(ActionEvent event) {
        if (txtGbdkBin != null) {
            String path = txtGbdkBin.getText();
            configModel.setConfigProperty("gbdk_bin", path);
            persistConfig();
            UserActionMonitor.compilerPathUpdated("GBDK", path);
        }
    }

    /**
     * Seleccionar carpeta para bin de GBDK
     * @param event
     */
    @FXML
    public void onSeleccionarGbdkBin(ActionEvent event) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();

        chooser.setTitle("Seleccionar carpeta bin de GBDK");

        File selectedDir = chooser.showDialog(txtGbdkBin.getScene().getWindow());

        if (selectedDir != null) {
            txtGbdkBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("GBDK", selectedDir.getAbsolutePath());
        }
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
        if (txtGbdkBin != null) workspace.setSetting("gbdk_bin", txtGbdkBin.getText());
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

        // Makefile
        if (txtMakeExecutable != null) workspace.setSetting("make_executable", txtMakeExecutable.getText());
        if (txtMakeBuildTarget != null) workspace.setSetting("make_build_target", txtMakeBuildTarget.getText());
        if (txtMakeRunTarget != null) workspace.setSetting("make_run_target", txtMakeRunTarget.getText());
        if (txtMakeExtraArgs != null) workspace.setSetting("make_extra_args", txtMakeExtraArgs.getText());
    }

    /**
     * Evento para guardar la configuración del compilador
     * @param event
     */
    @FXML
    private void onSaveCompilador(ActionEvent event) {
        String selectedCompilerLabel = comboCompilador != null ? comboCompilador.getSelectionModel().getSelectedItem() : null;
        String selectedCompiler = normalizeCompiler(selectedCompilerLabel);
        String gbdkPath         = txtGbdkBin        != null ? txtGbdkBin.getText().trim()        : "";
        String spectrumPath     = txtSpectrumBin    != null ? txtSpectrumBin.getText().trim()    : "";
        String cpcPath          = txtCpcBin         != null ? txtCpcBin.getText().trim()         : "";
        String z88dkProfile     = resolveZ88dkProfileFromSelection(selectedCompilerLabel, getSelectedZ88dkProfile());

        if (selectedCompiler == null || selectedCompiler.isEmpty()) {
            showError("Debe seleccionar un compilador.");
            return;
        }

        if (COMPILER_GBDK.equals(selectedCompiler) && (gbdkPath.isEmpty() || !(new File(gbdkPath).exists()))) {
            showError("Debe especificar una ruta válida para el bin de GBDK.");
            return;
        }

        if (COMPILER_Z88DK.equals(selectedCompiler)) {
            String requiredPath = Z88DK_PROFILE_CPC.equals(z88dkProfile) ? cpcPath : spectrumPath;
            if (requiredPath.isEmpty() || !(new File(requiredPath).exists())) {
                showError("Debe especificar una ruta valida para el bin de Z88DK del perfil activo.");
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
        if (!persistProjectDetectionPrefs(false)) {
            return;
        }

        configModel.setConfigProperty("gbdk_bin", gbdkPath);
        configModel.setConfigProperty("spectrum_bin", spectrumPath);
        configModel.setConfigProperty("cpc_bin", cpcPath);

        if (txtCompilador != null) {
            configModel.setCompilador(txtCompilador.getText());
        }

        saveCompilerSelection(selectedCompiler, z88dkProfile, false);
        persistConfig();
        UserActionMonitor.compilerChanged(selectedCompiler);

        showError(""); // Limpiar error
    }

    private void configureCompilerCombo() {
        if (comboCompilador != null) {
            comboCompilador.getItems().setAll(
                COMPILER_GBDK,
                getZ88dkSpectrumDisplayLabel(),
                getZ88dkCpcDisplayLabel(),
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
        String normalizedEmulator = getEmulatorForCompiler(normalizedCompiler, normalizedProfile);

        syncingCompilerUi = true;
        try {
            if (tabPaneCompilerOptions != null) {
                if (COMPILER_Z88DK.equalsIgnoreCase(normalizedCompiler) && tabCompilerZ88dkSpectrum != null && tabCompilerZ88dkCpc != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(
                        Z88DK_PROFILE_CPC.equals(normalizedProfile) ? tabCompilerZ88dkCpc : tabCompilerZ88dkSpectrum
                    );
                } else if (COMPILER_MAKEFILE.equalsIgnoreCase(normalizedCompiler) && tabCompilerMakefile != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(tabCompilerMakefile);
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
        configModel.setConfigProperty("emulador_seleccionado", normalizedEmulator);

        if (persist) {
            persistConfig();
        }
    }

    private String getEmulatorForCompiler(String compiler, String z88dkProfile) {
        if (COMPILER_Z88DK.equalsIgnoreCase(normalizeCompiler(compiler))) {
            return Z88DK_PROFILE_CPC.equals(normalizeZ88dkProfile(z88dkProfile)) ? EMULATOR_CPCBOX_WEB : EMULATOR_JSPECCY;
        }
        return EMULATOR_EMULICIOUS;
    }

    private String getSelectedZ88dkProfile() {
        if (tabPaneCompilerOptions == null) {
            return normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
        }

        Tab selected = tabPaneCompilerOptions.getSelectionModel().getSelectedItem();
        if (selected == tabCompilerZ88dkCpc) return Z88DK_PROFILE_CPC;
        if (selected == tabCompilerZ88dkSpectrum) return Z88DK_PROFILE_SPECTRUM;
        return normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
    }

    private String normalizeZ88dkProfile(String profile) {
        return Z88DK_PROFILE_CPC.equalsIgnoreCase(profile) ? Z88DK_PROFILE_CPC : Z88DK_PROFILE_SPECTRUM;
    }

    private String normalizeCompiler(String compiler) {
        if (compiler == null) return COMPILER_GBDK;

        String normalized = compiler.trim();

        if (COMPILER_MAKEFILE.equalsIgnoreCase(normalized)) {
            return COMPILER_MAKEFILE;
        }

        if (COMPILER_Z88DK.equalsIgnoreCase(normalized)
            || LEGACY_DISPLAY_Z88DK.equalsIgnoreCase(normalized)
            || LEGACY_SPECTRUM.equalsIgnoreCase(normalized)
            || normalized.equalsIgnoreCase(getZ88dkSpectrumDisplayLabel())
            || normalized.equalsIgnoreCase(getZ88dkCpcDisplayLabel())
            || normalized.toLowerCase(Locale.ROOT).contains("z88dk")) {
            return COMPILER_Z88DK;
        }

        return COMPILER_GBDK;
    }

    private String toCompilerDisplayLabel(String normalizedCompiler, String z88dkProfile) {
        if (COMPILER_Z88DK.equalsIgnoreCase(normalizedCompiler)) {
            return Z88DK_PROFILE_CPC.equals(normalizeZ88dkProfile(z88dkProfile))
                ? getZ88dkCpcDisplayLabel()
                : getZ88dkSpectrumDisplayLabel();
        }
        if (COMPILER_MAKEFILE.equalsIgnoreCase(normalizedCompiler)) return COMPILER_MAKEFILE;
        return COMPILER_GBDK;
    }

    private String resolveZ88dkProfileFromSelection(String compilerLabel, String fallbackProfile) {
        if (compilerLabel == null) return normalizeZ88dkProfile(fallbackProfile);

        if (compilerLabel.equalsIgnoreCase(getZ88dkCpcDisplayLabel())) return Z88DK_PROFILE_CPC;
        if (compilerLabel.equalsIgnoreCase(getZ88dkSpectrumDisplayLabel())) return Z88DK_PROFILE_SPECTRUM;

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

    private void refreshCompilerComboItems() {
        if (comboCompilador == null) return;

        String compiler = normalizeCompiler(configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK));
        String profile = normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));

        comboCompilador.getItems().setAll(
            COMPILER_GBDK,
            getZ88dkSpectrumDisplayLabel(),
            getZ88dkCpcDisplayLabel(),
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
            UserActionMonitor.compilerPathUpdated("Z88DK-Spectrum", path);
        }
    }

    @FXML
    public void onSaveCpcBin(ActionEvent event) {
        if (txtCpcBin != null) {
            String path = txtCpcBin.getText();
            configModel.setConfigProperty("cpc_bin", path);
            persistConfig();
            UserActionMonitor.compilerPathUpdated("Z88DK-CPC", path);
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

        if (persist) persistConfig();
    }

    private void loadZ88dkFlagsFromConfig() {
        if (comboZ88dkSpectrumClib != null) {
            comboZ88dkSpectrumClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_clib_option", ConfigModel.Z88DK_CLIB_NEW);
            comboZ88dkSpectrumClib.getSelectionModel().select(selected);
        }

        if (comboZ88dkCpcClib != null) {
            comboZ88dkCpcClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_cpc_clib_option", ConfigModel.Z88DK_CLIB_NEW);
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
        String cpcClibOption = comboZ88dkCpcClib != null ? comboZ88dkCpcClib.getValue() : ConfigModel.Z88DK_CLIB_NEW;
        String cpcUseCrtOrg = chkZ88dkCpcCrtOrgCode != null ? Boolean.toString(chkZ88dkCpcCrtOrgCode.isSelected()) : "false";

        configModel.setConfigProperty("z88dk_clib_option", spectrumClibOption != null ? spectrumClibOption : ConfigModel.Z88DK_CLIB_NEW);
        configModel.setConfigProperty("z88dk_use_pragma_crt_org_code_32768", spectrumUseCrtOrg);
        configModel.setConfigProperty("z88dk_target", txtZ88dkSpectrumTarget != null ? txtZ88dkSpectrumTarget.getText().trim() : "+zx");
        configModel.setConfigProperty("z88dk_create_app", chkZ88dkSpectrumCreateApp != null ? Boolean.toString(chkZ88dkSpectrumCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_defines", txtZ88dkSpectrumDefines != null ? txtZ88dkSpectrumDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_includes", txtZ88dkSpectrumIncludes != null ? txtZ88dkSpectrumIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_extra_args", txtZ88dkSpectrumExtraArgs != null ? txtZ88dkSpectrumExtraArgs.getText().trim() : "");

        configModel.setConfigProperty("z88dk_cpc_clib_option", cpcClibOption != null ? cpcClibOption : ConfigModel.Z88DK_CLIB_NEW);
        configModel.setConfigProperty("z88dk_cpc_use_pragma_crt_org_code_32768", cpcUseCrtOrg);
        configModel.setConfigProperty("z88dk_cpc_target", txtZ88dkCpcTarget != null ? txtZ88dkCpcTarget.getText().trim() : "+cpc");
        configModel.setConfigProperty("z88dk_cpc_create_app", chkZ88dkCpcCreateApp != null ? Boolean.toString(chkZ88dkCpcCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_cpc_defines", txtZ88dkCpcDefines != null ? txtZ88dkCpcDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_cpc_includes", txtZ88dkCpcIncludes != null ? txtZ88dkCpcIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_cpc_extra_args", txtZ88dkCpcExtraArgs != null ? txtZ88dkCpcExtraArgs.getText().trim() : "");

        if (persist) {
            persistConfig();
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
        String selectedAppearance = comboEditorAppearance != null
            ? comboEditorAppearance.getSelectionModel().getSelectedItem()
            : null;
        String appearance = toEditorAppearanceCode(selectedAppearance);
        configModel.setConfigProperty("editor_appearance", appearance);
        if (chkEnableLogs != null) {
            configModel.setEnableLogs(chkEnableLogs.isSelected());
        }
        if (chkShowHomeOnStartup != null) {
            configModel.setShowHomeOnStartup(chkShowHomeOnStartup.isSelected());
        }
        persistConfig();
        applyApplicationAppearanceToCurrentScene(appearance);
        showError("");
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
        if (lblReadmeLanguage    != null) lblReadmeLanguage    .setText(bundle.getString("config.readmeLanguage"));
        if (tabCompilador        != null) tabCompilador        .setText(bundle.getString("label.compilerTab"));
        if (tabCompilerGbdk      != null) tabCompilerGbdk      .setText(bundle.getString("label.compiler.gbdkTab"));
        if (tabCompilerZ88dkSpectrum != null) tabCompilerZ88dkSpectrum.setText(bundle.getString("label.compiler.z88dkSpectrumTab"));
        if (tabCompilerZ88dkCpc  != null) tabCompilerZ88dkCpc  .setText(bundle.getString("label.compiler.z88dkCpcTab"));
        if (tabCompilerMakefile  != null) tabCompilerMakefile  .setText(bundle.getString("label.compiler.make.tab"));
        if (tabPlugins           != null) tabPlugins           .setText(bundle.getString("config.plugins.tab"));
        if (lblPluginsInstalled  != null) lblPluginsInstalled  .setText(bundle.getString("config.plugins.installed"));
        if (lblCompilador        != null) lblCompilador        .setText(bundle.getString("label.compiler"));
        if (lblGbdkBin           != null) lblGbdkBin           .setText(bundle.getString("label.compiler.gbdk.bin"));
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
        if (chkProjectDetectAutoApply != null) chkProjectDetectAutoApply.setText(bundle.getString("config.projectDetection.autoApply"));
        if (lblProjectDetectThreshold != null) lblProjectDetectThreshold.setText(bundle.getString("config.projectDetection.threshold"));
        if (btnCerrarConfig      != null) btnCerrarConfig      .setText(bundle.getString("button.close"));
        if (lblTituloConfig      != null) lblTituloConfig      .setText(bundle.getString("label.configTitle"));
        if (btnGuardarIdioma     != null) btnGuardarIdioma     .setText(bundle.getString("button.saveLanguage"));
        if (btnGuardarAppearance != null) btnGuardarAppearance .setText(bundle.getString("button.saveAppearance"));
        if (chkEnableLogs != null) chkEnableLogs.setText(bundle.containsKey("config.appearance.enable_logs") ? bundle.getString("config.appearance.enable_logs") : "Habilitar guardado de logs en archivo (samaruc.log)");
        if (chkShowHomeOnStartup != null) chkShowHomeOnStartup.setText(bundle.containsKey("config.appearance.show_home_on_startup") ? bundle.getString("config.appearance.show_home_on_startup") : "Mostrar página de inicio al arrancar");
        if (btnGuardarCompilador != null) btnGuardarCompilador .setText(bundle.getString("button.saveConfig"));
        if (btnSaveMakeOpts      != null) btnSaveMakeOpts      .setText(bundle.getString("button.saveMake"));
        if (btnGuardarGbdkBin    != null) btnGuardarGbdkBin    .setText(bundle.getString("button.saveGbdk"));
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
        loadEditorAppearanceFromConfig();
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


    private void loadEditorAppearanceFromConfig() {
        if (comboEditorAppearance == null) return;

        ResourceBundle bundle = getCurrentBundle();
        String modernLabel = bundle.containsKey("config.editorAppearance.option.modernDark")
            ? bundle.getString("config.editorAppearance.option.modernDark")
            : "Oscuro";
        String classicLabel = bundle.containsKey("config.editorAppearance.option.classic")
            ? bundle.getString("config.editorAppearance.option.classic")
            : "Clasico";

        comboEditorAppearance.getItems().clear();
        comboEditorAppearance.getItems().addAll(modernLabel, classicLabel);
        editorAppearanceCodeByLabel.clear();
        editorAppearanceCodeByLabel.put(modernLabel, ConfigModel.EDITOR_APPEARANCE_MODERN_DARK);
        editorAppearanceCodeByLabel.put(classicLabel, ConfigModel.EDITOR_APPEARANCE_CLASSIC);

        String appearance = configModel.getConfigProperty("editor_appearance", ConfigModel.EDITOR_APPEARANCE_MODERN_DARK);
        if (ConfigModel.EDITOR_APPEARANCE_CLASSIC.equalsIgnoreCase(appearance)) {
            comboEditorAppearance.getSelectionModel().select(classicLabel);
        } else {
            comboEditorAppearance.getSelectionModel().select(modernLabel);
        }
    }

    private String toEditorAppearanceCode(String displayValue) {
        if (displayValue == null || displayValue.isBlank()) {
            return ConfigModel.EDITOR_APPEARANCE_MODERN_DARK;
        }
        String mapped = editorAppearanceCodeByLabel.get(displayValue);
        if (mapped != null) return mapped;
        return displayValue.toLowerCase(Locale.ROOT).contains("classic")
            ? ConfigModel.EDITOR_APPEARANCE_CLASSIC
            : ConfigModel.EDITOR_APPEARANCE_MODERN_DARK;
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
