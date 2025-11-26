package com.retroeditor.controller.config;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.plugin.PluginInfo;
import com.retroeditor.plugin.PluginManager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ConfigController {
    @FXML private TextField        txtGbdkBin;
    @FXML private TextField        txtCompilador;
    @FXML private TextField        txtSpectrumBin;
    @FXML private TextField        txtJspeccyBin;
    @FXML private Tab              tabIdioma;
    @FXML private Tab              tabCompilador;
    @FXML private Label            lblError;
    @FXML private Label            lblIdioma;
    @FXML private Label            lblCompilador;
    @FXML private Label            lblTituloConfig;
    @FXML private Button           btnGuardarIdioma;
    @FXML private Button           btnCerrarConfig;
    @FXML private Button           btnGuardarCompilador;
    @FXML private ComboBox<String> comboIdioma;
    @FXML private ComboBox<String> comboCompilador;

    @FXML private javafx.scene.control.ListView<String> lstPlugins;
    @FXML private javafx.scene.control.Button btnInstallPlugin;
    @FXML private javafx.scene.control.Button btnEnable;
    @FXML private javafx.scene.control.Button btnDisable;
    @FXML private javafx.scene.control.Button btnOpenFolder;
    @FXML private javafx.scene.control.Button btnRefreshPlugins;
    @FXML private javafx.scene.control.Label lblPluginInfo;

    private Runnable onCloseCallback;
    private Runnable onLanguageChanged;

    private ConfigModel configModel = new ConfigModel();

    private final File        configFile  = new File(System.getProperty("user.home"), ".retroeditor.properties");
    private final File        pluginsDir  = new File(System.getProperty("user.dir"), "plugins");

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

        if (comboCompilador != null) {
            comboCompilador.getItems().clear();
            comboCompilador.getItems().addAll("GBDK", "Spectrum");
            String selected = configModel.getConfigProperty("compilador_seleccionado", "GBDK");
            comboCompilador.getSelectionModel().select(selected);
        }

        if (txtSpectrumBin != null) {
            txtSpectrumBin.setText(configModel.getConfigProperty("spectrum_bin", ""));
        }

        if (txtJspeccyBin != null) {
            txtJspeccyBin.setText(configModel.getConfigProperty("jspeccy_bin", ""));
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

        if (lblError != null) {
            lblError.setText("");
        }

        if (comboCompilador != null) {
            comboCompilador.getItems().addAll("GBDK", "Spectrum");
            String selected = configModel.getConfigProperty("compilador_seleccionado", "GBDK");
            comboCompilador.getSelectionModel().select(selected);
        }

        if (txtSpectrumBin != null) {
            txtSpectrumBin.setText(configModel.getConfigProperty("spectrum_bin", ""));
        }

        if (txtJspeccyBin != null) {
            txtJspeccyBin.setText(configModel.getConfigProperty("jspeccy_bin", ""));
        }

        comboIdioma.getItems().addAll("Español", "English");
        configModel.getDefaultLanguage(configFile);
        String lang = configModel.getConfigProperty("idioma", "es");
        comboIdioma.getSelectionModel().select(lang.equals("en") ? "English" : "Español");

        if (txtGbdkBin != null) {
            txtGbdkBin.setText(configModel.getConfigProperty("gbdk_bin", ""));
        }

        // Si no existe, crear carpeta de plugins
        try { if (!pluginsDir.exists()) pluginsDir.mkdirs(); } catch (Exception ignored) {}

        // Configurar lista de plugins
        if (lstPlugins != null) {
            lstPlugins.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onPluginSelected(newV));
        }

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

        PluginManager pm = getPluginManager();

        List<PluginInfo> infos = pm != null ? pm.discoverPlugins() : java.util.Collections.emptyList();

        List<String> display = infos.stream().map(i -> String.format("%s %s", i.jarName, i.enabled ? "(habilitado)" : "(deshabilitado)"))
            .collect(Collectors.toList());

        lstPlugins.getItems().setAll(display);

        lblPluginInfo.setText(display.isEmpty() ? "No hay plugins instalados." : "Selecciona un plugin para ver detalles.");
    }

    /**
     * Maneja la selección de un plugin en la lista.
     * @param display Texto mostrado del plugin seleccionado.
     */
    private void onPluginSelected(String display) {

        if (display == null) return;

        String jarName = display.split(" ")[0];
        PluginManager pm = getPluginManager();
        
        List<PluginInfo> infos = pm != null ? pm.discoverPlugins() : java.util.Collections.emptyList();

        for (PluginInfo pi : infos) {

            if (pi.jarName.equals(jarName)) {
                String infoText = "Jar: " 
                                + pi.jarName + "\nEstado: " 
                                + (pi.enabled ? "Habilitado" : "Deshabilitado") 
                                + "\nImplementaciones: " 
                                + String.join(", ", pi.implNames);

                lblPluginInfo.setText(infoText);
                return;
            }
        }

        lblPluginInfo.setText("Información no disponible");
    }

    /**
     * Refrescar la lista de plugins desde el disco.
     * @param event
     */
    @FXML
    public void onRefreshPlugins(ActionEvent event) {
        // recargar plugins
        PluginManager pm = getPluginManager();

        if (pm != null) pm.reloadPlugins();

        refreshPluginsList();
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

        fc.setTitle("Seleccionar JAR de plugin");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));

        File selected = fc.showOpenDialog(btnInstallPlugin.getScene().getWindow());

        if (selected != null) {
            try {
                if (!pluginsDir.exists()) pluginsDir.mkdirs();

                File dest = new File(pluginsDir, selected.getName());
                Files.copy(selected.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

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
        String sel = lstPlugins.getSelectionModel().getSelectedItem();

        if (sel == null) return;

        String        jarName = sel.split(" ")[0];
        PluginManager pm      = getPluginManager();

        if (pm != null) pm.setJarEnabled(jarName, true);

        // Persiste el estado habilitado y recarga los plugins para que el cambio surta efecto inmediatamente
        if (pm != null) pm.reloadPlugins();

        refreshPluginsList();
    }

    /**
     * Deshabilitar un plugin seleccionado.
     * @param event
     */
    @FXML
    public void onDisablePlugin(ActionEvent event) {
        String sel = lstPlugins.getSelectionModel().getSelectedItem();

        if (sel == null) return;

        String jarName = sel.split(" ")[0];
        PluginManager pm = getPluginManager();

        if (pm != null) pm.setJarEnabled(jarName, false);

        // Persiste el estado deshabilitado y recarga los plugins para que el cambio surta efecto inmediatamente
        if (pm != null) pm.reloadPlugins();

        refreshPluginsList();
    }

    /**
     * Guardar ruta de bin de GBDK
     * @param event
     */
    @FXML
    public void onSaveGbdkBin(ActionEvent event) {
        if (txtGbdkBin != null) {
            configModel.setConfigProperty("gbdk_bin", txtGbdkBin.getText());
            configModel.saveConfig(configFile);
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
        }
    }

    /**
     * Evento para guardar la configuración del compilador
     * @param event
     */
    @FXML
    private void onSaveCompilador(ActionEvent event) {
        // Validaciones
        String selectedCompiler = comboCompilador   != null ? comboCompilador.getSelectionModel().getSelectedItem() : null;
        String gbdkPath         = txtGbdkBin        != null ? txtGbdkBin.getText().trim()                           : "";
        String spectrumPath     = txtSpectrumBin    != null ? txtSpectrumBin.getText().trim()                       : "";

        if (selectedCompiler == null || selectedCompiler.isEmpty()) {
            showError("Debe seleccionar un compilador.");
            return;
        }

        if (selectedCompiler.equals("GBDK") && (gbdkPath.isEmpty() || !(new File(gbdkPath).exists()))) {
            showError("Debe especificar una ruta válida para el bin de GBDK.");
            return;
        }

        if (selectedCompiler.equals("Spectrum") && (spectrumPath.isEmpty() || !(new File(spectrumPath).exists()))) {
            showError("Debe especificar una ruta válida para el bin de Spectrum.");
            return;
        }

        // Guardar configuración si las validaciones pasan
        configModel.onSaveCompilador(configFile, txtCompilador.getText());

        if (comboCompilador != null) {
            configModel.setConfigProperty("compilador_seleccionado", selectedCompiler);
            configModel.saveConfig(configFile);
        }

        showError(""); // Limpiar error
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
            configModel.setConfigProperty("spectrum_bin", txtSpectrumBin.getText());
            configModel.saveConfig(configFile);
        }
    }

    /**
     * guardar evento del botón bin de jspeccy
     * @param event
     */
    @FXML
    public void onSaveJspeccyBin(ActionEvent event) {
        if (txtJspeccyBin != null) {
            configModel.setConfigProperty("jspeccy_bin", txtJspeccyBin.getText());
            configModel.saveConfig(configFile);
        }
    }

    /**
     * seleccionar archivo jspeccy.jar o ejecutable
     * @param event
     */
    @FXML
    public void onSeleccionarJspeccyBin(ActionEvent event) {
        FileChooser chooser = new FileChooser();

        chooser.setTitle("Seleccionar jspeccy.jar o ejecutable");

        File selected = chooser.showOpenDialog(txtJspeccyBin.getScene().getWindow());

        if (selected != null) {
            txtJspeccyBin.setText(selected.getAbsolutePath());
        }
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
        if (lblCompilador        != null) lblCompilador        .setText(bundle.getString("label.compiler"));
        if (btnCerrarConfig      != null) btnCerrarConfig      .setText(bundle.getString("button.close"));
        if (lblTituloConfig      != null) lblTituloConfig      .setText(bundle.getString("label.configTitle"));
        if (btnGuardarIdioma     != null) btnGuardarIdioma     .setText(bundle.getString("button.saveLanguage"));
        if (btnGuardarCompilador != null) btnGuardarCompilador .setText(bundle.getString("button.saveConfig"));
    }
}
