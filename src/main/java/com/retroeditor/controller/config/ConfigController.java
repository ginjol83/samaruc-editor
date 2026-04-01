package com.retroeditor.controller.config;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.plugin.PluginInfo;
import com.retroeditor.plugin.PluginManager;
import com.retroeditor.service.UserActionMonitor;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ConfigController {
    private static final String COMPILER_GBDK      = "GBDK";
    private static final String COMPILER_Z88DK     = "z88dk";
    private static final String LEGACY_SPECTRUM    = "Spectrum";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY   = "JSpeccy";

    @FXML private TabPane          tabPaneConfig;
    @FXML private TabPane          tabPaneCompilerOptions;
    @FXML private TextField        txtGbdkBin;
    @FXML private TextField        txtCompilador;
    @FXML private TextField        txtSpectrumBin;
    @FXML private TextField        txtGbdkDefines;
    @FXML private TextField        txtGbdkIncludes;
    @FXML private TextField        txtGbdkExtraArgs;
    @FXML private TextField        txtZ88dkTarget;
    @FXML private CheckBox         chkZ88dkCreateApp;
    @FXML private TextField        txtZ88dkDefines;
    @FXML private TextField        txtZ88dkIncludes;
    @FXML private TextField        txtZ88dkExtraArgs;
    @FXML private ComboBox<String> comboZ88dkClib;
    @FXML private CheckBox         chkZ88dkCrtOrgCode;
    @FXML private ComboBox<String> comboGbdkOptLevel;
    @FXML private CheckBox         chkGbdkOptSpeed;
    @FXML private CheckBox         chkGbdkOptSize;
    @FXML private Tab              tabIdioma;
    @FXML private Tab              tabCompilador;
    @FXML private Tab              tabCompilerGbdk;
    @FXML private Tab              tabCompilerZ88dk;
    @FXML private Tab              tabPlugins;
    @FXML private Label            lblError;
    @FXML private Label            lblIdioma;
    @FXML private Label            lblCompilador;
    @FXML private Label            lblGbdkBin;
    @FXML private Label            lblGbdkOpts;
    @FXML private Label            lblGbdkOptLevel;
    @FXML private Label            lblZ88dkBin;
    @FXML private Label            lblZ88dkOpts;
    @FXML private Label            lblZ88dkClib;
    @FXML private Label            lblGbdkDefines;
    @FXML private Label            lblGbdkIncludes;
    @FXML private Label            lblGbdkExtraArgs;
    @FXML private Label            lblZ88dkTarget;
    @FXML private Label            lblZ88dkDefines;
    @FXML private Label            lblZ88dkIncludes;
    @FXML private Label            lblZ88dkExtraArgs;
    @FXML private Label            lblTituloConfig;
    @FXML private Button           btnGuardarIdioma;
    @FXML private Button           btnCerrarConfig;
    @FXML private Button           btnGuardarCompilador;
    @FXML private ComboBox<String> comboIdioma;
    @FXML private ComboBox<String> comboCompilador;

    @FXML private Button           btnSeleccionarGbdkBin;
    @FXML private Button           btnGuardarGbdkBin;
    @FXML private Button           btnSeleccionarSpectrumBin;
    @FXML private Button           btnGuardarSpectrumBin;

    @FXML private javafx.scene.control.ListView<String> lstPlugins;
    @FXML private javafx.scene.control.Button btnInstallPlugin;
    @FXML private javafx.scene.control.Button btnEnable;
    @FXML private javafx.scene.control.Button btnDisable;
    @FXML private javafx.scene.control.Button btnOpenFolder;
    @FXML private javafx.scene.control.Button btnRefreshPlugins;
    @FXML private javafx.scene.control.Label lblPluginInfo;
    @FXML private Label lblPluginsInstalled;
    @FXML private ProgressIndicator progressPluginsReload;

    private Runnable onCloseCallback;
    private Runnable onLanguageChanged;
    private boolean syncingCompilerUi = false;

    private ConfigModel configModel = new ConfigModel();

    private final File        configFile  = new File(System.getProperty("user.home"), ".retroeditor.properties");
    private final File        pluginsDir  = new File(System.getProperty("user.dir"), "plugins");
    private List<PluginInfo>  discoveredPlugins = Collections.emptyList();
    private boolean pluginsReloading = false;

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

        if (comboIdioma != null) {
            comboIdioma.getItems().clear();
            comboIdioma.getItems().addAll("Español", "English");
            String lang = configModel.getConfigProperty("idioma", "es");
            comboIdioma.getSelectionModel().select(lang.equals("en") ? "English" : "Español");
        }

        if (txtGbdkBin != null) {
            txtGbdkBin.setText(configModel.getConfigProperty("gbdk_bin", ""));
        }

        loadZ88dkFlagsFromConfig();
        loadGbdkOptsFromConfig();

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
        configModel.getDefaultLanguage(configFile);

        if (lblError != null) {
            lblError.setText("");
        }

        configureCompilerCombo();
        applyCompilerSelectionFromModel();

        if (txtSpectrumBin != null) {
            txtSpectrumBin.setText(configModel.getConfigProperty("spectrum_bin", ""));
        }

        comboIdioma.getItems().addAll("Español", "English");
        String lang = configModel.getConfigProperty("idioma", "es");
        comboIdioma.getSelectionModel().select(lang.equals("en") ? "English" : "Español");

        if (txtGbdkBin != null) {
            txtGbdkBin.setText(configModel.getConfigProperty("gbdk_bin", ""));
        }

        loadZ88dkFlagsFromConfig();
        loadGbdkOptsFromConfig();

        // Si no existe, crear carpeta de plugins
        try { if (!pluginsDir.exists()) pluginsDir.mkdirs(); } catch (Exception ignored) {}

        // Configurar lista de plugins
        if (lstPlugins != null) {
            lstPlugins.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onPluginSelected(newV));
        }

        updatePluginButtons(null);

        refreshPluginsList();

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

        discoveredPlugins = pm != null ? pm.discoverPlugins() : Collections.emptyList();

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
                if (pm != null) pm.reloadPlugins();
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
            if (!pluginsDir.exists()) pluginsDir.mkdirs();

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
                if (!pluginsDir.exists()) pluginsDir.mkdirs();

                File dest = new File(pluginsDir, selected.getName());
                Files.copy(selected.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
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

        if (pm != null) pm.setJarEnabled(jarName, true);

        UserActionMonitor.pluginEnabled(jarName);

        // Persiste el estado habilitado y recarga los plugins para que el cambio surta efecto inmediatamente
        if (pm != null) pm.reloadPlugins();

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

        if (pm != null) pm.setJarEnabled(jarName, false);

        UserActionMonitor.pluginDisabled(jarName);

        // Persiste el estado deshabilitado y recarga los plugins para que el cambio surta efecto inmediatamente
        if (pm != null) pm.reloadPlugins();

        refreshPluginsList();
        selectJarInList(jarName);
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
            return;
        }

        if (info == null) {
            btnEnable.setDisable(true);
            btnDisable.setDisable(true);
            return;
        }

        btnEnable.setDisable(info.enabled);
        btnDisable.setDisable(!info.enabled);
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

    /**
     * Guardar ruta de bin de GBDK
     * @param event
     */
    @FXML
    public void onSaveGbdkBin(ActionEvent event) {
        if (txtGbdkBin != null) {
            String path = txtGbdkBin.getText();
            configModel.setConfigProperty("gbdk_bin", path);
            configModel.saveConfig(configFile);
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

    /**
     * Evento para guardar la configuración del compilador
     * @param event
     */
    @FXML
    private void onSaveCompilador(ActionEvent event) {
        // Validaciones
        String selectedCompiler = comboCompilador   != null ? normalizeCompiler(comboCompilador.getSelectionModel().getSelectedItem()) : null;
        String gbdkPath         = txtGbdkBin        != null ? txtGbdkBin.getText().trim()        : "";
        String spectrumPath     = txtSpectrumBin    != null ? txtSpectrumBin.getText().trim()    : "";

        if (selectedCompiler == null || selectedCompiler.isEmpty()) {
            showError("Debe seleccionar un compilador.");
            return;
        }

        if (COMPILER_GBDK.equals(selectedCompiler) && (gbdkPath.isEmpty() || !(new File(gbdkPath).exists()))) {
            showError("Debe especificar una ruta válida para el bin de GBDK.");
            return;
        }

        if (COMPILER_Z88DK.equals(selectedCompiler) && (spectrumPath.isEmpty() || !(new File(spectrumPath).exists()))) {
            showError("Debe especificar una ruta válida para el bin de z88dk.");
            return;
        }

        if (COMPILER_Z88DK.equals(selectedCompiler) && !validateZ88dkTarget()) {
            return;
        }

        // Guardar configuración si las validaciones pasan
        if (txtCompilador != null) {
            configModel.onSaveCompilador(configFile, txtCompilador.getText());
        }

        saveCompilerSelection(selectedCompiler, true);
        if (!persistZ88dkFlags(true)) {
            return;
        }
        persistGbdkOpts(true);
        UserActionMonitor.compilerChanged(selectedCompiler);

        showError(""); // Limpiar error
    }

    private void configureCompilerCombo() {
        if (comboCompilador != null) {
            comboCompilador.getItems().setAll(COMPILER_GBDK, COMPILER_Z88DK);
            comboCompilador.setOnAction(this::onSaveCompilador);
        }

        if (tabPaneCompilerOptions != null) {
            tabPaneCompilerOptions.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (syncingCompilerUi || newTab == null) return;
                String selectedCompiler = newTab == tabCompilerZ88dk ? COMPILER_Z88DK : COMPILER_GBDK;
                saveCompilerSelection(selectedCompiler, true);
            });
        }
    }

    private void applyCompilerSelectionFromModel() {
        String compiler = normalizeCompiler(configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK));
        saveCompilerSelection(compiler, true);
    }

    private void saveCompilerSelection(String compiler, boolean persist) {
        String normalizedCompiler = normalizeCompiler(compiler);
        String normalizedEmulator = getEmulatorForCompiler(normalizedCompiler);

        syncingCompilerUi = true;
        try {
            if (tabPaneCompilerOptions != null) {
                if (COMPILER_Z88DK.equalsIgnoreCase(normalizedCompiler) && tabCompilerZ88dk != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(tabCompilerZ88dk);
                } else if (tabCompilerGbdk != null) {
                    tabPaneCompilerOptions.getSelectionModel().select(tabCompilerGbdk);
                }
            }

            if (comboCompilador != null) {
                comboCompilador.getSelectionModel().select(normalizedCompiler);
            }
        } finally {
            syncingCompilerUi = false;
        }

        configModel.setConfigProperty("compilador_seleccionado", normalizedCompiler);
        configModel.setConfigProperty("emulador_seleccionado", normalizedEmulator);

        if (persist) {
            configModel.saveConfig(configFile);
        }
    }

    private String getEmulatorForCompiler(String compiler) {
        return COMPILER_Z88DK.equalsIgnoreCase(normalizeCompiler(compiler)) ? EMULATOR_JSPECCY : EMULATOR_EMULICIOUS;
    }

    private String normalizeCompiler(String compiler) {
        return compiler != null && (COMPILER_Z88DK.equalsIgnoreCase(compiler) || LEGACY_SPECTRUM.equalsIgnoreCase(compiler))
            ? COMPILER_Z88DK
            : COMPILER_GBDK;
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
            configModel.saveConfig(configFile);
            UserActionMonitor.compilerPathUpdated("z88dk", path);
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

        if (persist) configModel.saveConfig(configFile);
    }

    private void loadZ88dkFlagsFromConfig() {
        if (comboZ88dkClib != null) {
            comboZ88dkClib.getItems().setAll(ConfigModel.getSupportedZ88dkClibOptions());
            String selected = configModel.getConfigProperty("z88dk_clib_option", ConfigModel.Z88DK_CLIB_NEW);
            comboZ88dkClib.getSelectionModel().select(selected);
        }

        if (chkZ88dkCrtOrgCode != null) {
            chkZ88dkCrtOrgCode.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_use_pragma_crt_org_code_32768", "true")));
        }
        if (txtZ88dkTarget != null) {
            txtZ88dkTarget.setText(configModel.getConfigProperty("z88dk_target", "+zx"));
        }
        if (chkZ88dkCreateApp != null) {
            chkZ88dkCreateApp.setSelected(Boolean.parseBoolean(configModel.getConfigProperty("z88dk_create_app", "true")));
        }
        if (txtZ88dkDefines != null) {
            txtZ88dkDefines.setText(configModel.getConfigProperty("z88dk_defines", ""));
        }
        if (txtZ88dkIncludes != null) {
            txtZ88dkIncludes.setText(configModel.getConfigProperty("z88dk_includes", ""));
        }
        if (txtZ88dkExtraArgs != null) {
            txtZ88dkExtraArgs.setText(configModel.getConfigProperty("z88dk_extra_args", ""));
        }
    }

    private boolean persistZ88dkFlags(boolean persist) {
        if (!validateZ88dkTarget()) {
            return false;
        }

        String clibOption = comboZ88dkClib != null ? comboZ88dkClib.getValue() : ConfigModel.Z88DK_CLIB_NEW;
        String useCrtOrg = chkZ88dkCrtOrgCode != null ? Boolean.toString(chkZ88dkCrtOrgCode.isSelected()) : "true";

        configModel.setConfigProperty("z88dk_clib_option", clibOption != null ? clibOption : ConfigModel.Z88DK_CLIB_NEW);
        configModel.setConfigProperty("z88dk_use_pragma_crt_org_code_32768", useCrtOrg);
        configModel.setConfigProperty("z88dk_target", txtZ88dkTarget != null ? txtZ88dkTarget.getText().trim() : "+zx");
        configModel.setConfigProperty("z88dk_create_app", chkZ88dkCreateApp != null ? Boolean.toString(chkZ88dkCreateApp.isSelected()) : "true");
        configModel.setConfigProperty("z88dk_defines", txtZ88dkDefines != null ? txtZ88dkDefines.getText().trim() : "");
        configModel.setConfigProperty("z88dk_includes", txtZ88dkIncludes != null ? txtZ88dkIncludes.getText().trim() : "");
        configModel.setConfigProperty("z88dk_extra_args", txtZ88dkExtraArgs != null ? txtZ88dkExtraArgs.getText().trim() : "");

        if (persist) {
            configModel.saveConfig(configFile);
        }

        return true;
    }

    private boolean validateZ88dkTarget() {
        String target = txtZ88dkTarget != null ? txtZ88dkTarget.getText().trim() : "";

        if (target.isEmpty() || !target.startsWith("+")) {
            showError(tr("config.compiler.z88dk.target.invalid", "El target de z88dk debe empezar por '+' (ejemplo: +zx)."));
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

        chooser.setTitle("Seleccionar carpeta bin de Spectrum");

        File selectedDir = chooser.showDialog(txtSpectrumBin.getScene().getWindow());

        if (selectedDir != null) {
            txtSpectrumBin.setText(selectedDir.getAbsolutePath());
            UserActionMonitor.compilerPathUpdated("z88dk", selectedDir.getAbsolutePath());
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
        String selected = comboIdioma.getSelectionModel().getSelectedItem();

        // Actualizar modelo y persistir
        configModel.changeLanguage(selected);
        configModel.saveConfig(configFile);
        UserActionMonitor.languageChanged(selected);

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
        if (lblIdioma            != null) lblIdioma            .setText(bundle.getString("label.language"));
        if (tabCompilador        != null) tabCompilador        .setText(bundle.getString("label.compilerTab"));
        if (tabCompilerGbdk      != null) tabCompilerGbdk      .setText(bundle.getString("label.compiler.gbdkTab"));
        if (tabCompilerZ88dk     != null) tabCompilerZ88dk     .setText(bundle.getString("label.compiler.z88dkTab"));
        if (tabPlugins           != null) tabPlugins           .setText(bundle.getString("config.plugins.tab"));
        if (lblPluginsInstalled  != null) lblPluginsInstalled  .setText(bundle.getString("config.plugins.installed"));
        if (lblCompilador        != null) lblCompilador        .setText(bundle.getString("label.compiler"));
        if (lblGbdkBin           != null) lblGbdkBin           .setText(bundle.getString("label.compiler.gbdk.bin"));
        if (lblGbdkOpts          != null) lblGbdkOpts          .setText(bundle.getString("label.compiler.gbdk.opts"));
        if (lblGbdkOptLevel      != null) lblGbdkOptLevel      .setText(bundle.getString("label.compiler.gbdk.opt.level"));
        if (lblGbdkDefines       != null) lblGbdkDefines       .setText(bundle.getString("label.compiler.gbdk.defines"));
        if (lblGbdkIncludes      != null) lblGbdkIncludes      .setText(bundle.getString("label.compiler.gbdk.includes"));
        if (lblGbdkExtraArgs     != null) lblGbdkExtraArgs     .setText(bundle.getString("label.compiler.gbdk.extra"));
        if (lblZ88dkBin          != null) lblZ88dkBin          .setText(bundle.getString("label.compiler.z88dk.bin"));
        if (lblZ88dkOpts         != null) lblZ88dkOpts         .setText(bundle.getString("label.compiler.z88dk.opts"));
        if (lblZ88dkClib         != null) lblZ88dkClib         .setText(bundle.getString("label.compiler.z88dk.clib"));
        if (lblZ88dkTarget       != null) lblZ88dkTarget       .setText(bundle.getString("label.compiler.z88dk.target"));
        if (chkZ88dkCreateApp    != null) chkZ88dkCreateApp    .setText(bundle.getString("label.compiler.z88dk.createApp"));
        if (lblZ88dkDefines      != null) lblZ88dkDefines      .setText(bundle.getString("label.compiler.z88dk.defines"));
        if (lblZ88dkIncludes     != null) lblZ88dkIncludes     .setText(bundle.getString("label.compiler.z88dk.includes"));
        if (lblZ88dkExtraArgs    != null) lblZ88dkExtraArgs    .setText(bundle.getString("label.compiler.z88dk.extra"));
        if (btnCerrarConfig      != null) btnCerrarConfig      .setText(bundle.getString("button.close"));
        if (lblTituloConfig      != null) lblTituloConfig      .setText(bundle.getString("label.configTitle"));
        if (btnGuardarIdioma     != null) btnGuardarIdioma     .setText(bundle.getString("button.saveLanguage"));
        if (btnGuardarCompilador != null) btnGuardarCompilador .setText(bundle.getString("button.saveConfig"));
        if (btnGuardarGbdkBin    != null) btnGuardarGbdkBin    .setText(bundle.getString("button.saveGbdk"));
        if (btnGuardarSpectrumBin!= null) btnGuardarSpectrumBin.setText(bundle.getString("button.saveZ88dk"));
        if (btnInstallPlugin     != null) btnInstallPlugin     .setText(bundle.getString("config.plugins.install"));
        if (btnEnable            != null) btnEnable            .setText(bundle.getString("config.plugins.enable"));
        if (btnDisable           != null) btnDisable           .setText(bundle.getString("config.plugins.disable"));
        if (btnOpenFolder        != null) btnOpenFolder        .setText(bundle.getString("config.plugins.openFolder"));
        if (btnRefreshPlugins    != null) btnRefreshPlugins    .setText(bundle.getString("config.plugins.refresh"));
    }
}
