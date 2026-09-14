package com.retroeditor.controller.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import com.retroeditor.model.TranslatorModel;
import com.retroeditor.service.TranslatorPersistenceService;
import com.retroeditor.util.FXUtils;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * Gestor de pestañas de traductor (.smt).
 * Orquesta la creación, carga, guardado y dirty tracking de pestañas de traducción.
 */
public class TranslatorTabManager {

    private static final String TAB_PROP_TRANSLATOR_CONTROLLER = "translator.controller";
    private static final String TAB_PROP_DIRTY = "translator.dirty";
    private static final String TAB_PROP_FILE = "translator.file";

    private final TranslatorPersistenceService persistenceService;
    private final Map<Tab, RomTextTranslatorController> tabControllerMap;
    private Runnable onDirtyStateChanged;

    public TranslatorTabManager() {
        this.persistenceService = new TranslatorPersistenceService();
        this.tabControllerMap = new HashMap<>();
    }

    public void setOnDirtyStateChanged(Runnable handler) {
        this.onDirtyStateChanged = handler;
    }

    /**
     * Crea un nuevo archivo .smt vacío y lo abre como pestaña.
     *
     * @param projectDir Directorio del proyecto actual.
     * @param fxUtils Utilidades de FX.
     * @param tabPane TabPane principal del editor.
     * @param bundle ResourceBundle para i18n.
     * @return La pestaña creada, o null si hubo error.
     */
    public Tab createNewTranslator(File projectDir, FXUtils fxUtils, TabPane tabPane, ResourceBundle bundle) {
        if (projectDir == null) {
            showAlert("Aviso", "No hay proyecto abierto", Alert.AlertType.WARNING);
            return null;
        }

        try {
            Path smtPath = uniqueSmtFile(projectDir.toPath(), "translation");
            Files.writeString(smtPath, "{}", java.nio.charset.StandardCharsets.UTF_8);
            return openTranslatorTab(smtPath.toFile(), fxUtils, tabPane, bundle);
        } catch (IOException ex) {
            showAlert("Error", "No se pudo crear el archivo .smt: " + ex.getMessage(), Alert.AlertType.ERROR);
            return null;
        }
    }

    /**
     * Abre un archivo .smt existente como pestaña.
     *
     * @param smtFile Archivo .smt a abrir.
     * @param fxUtils Utilidades de FX.
     * @param tabPane TabPane principal del editor.
     * @param bundle ResourceBundle para i18n.
     * @return La pestaña creada, o null si hubo error.
     */
    public Tab openTranslatorTab(File smtFile, FXUtils fxUtils, TabPane tabPane, ResourceBundle bundle) {
        if (smtFile == null || !smtFile.exists()) return null;

        TranslatorModel model = persistenceService.load(smtFile);
        RomTextTranslatorController controller = new RomTextTranslatorController();
        Node translatorNode = controller.createTranslatorNode(bundle);

        Path smtDir = smtFile.toPath().getParent();
        controller.loadFromModel(model, smtDir);

        Tab tab = fxUtils.addTranslatorTab(smtFile.getName(), translatorNode, tabPane);
        tabControllerMap.put(tab, controller);
        tab.getProperties().put(TAB_PROP_TRANSLATOR_CONTROLLER, controller);
        tab.getProperties().put(TAB_PROP_FILE, smtFile.getAbsoluteFile());

        controller.setOnStateChanged(() -> markDirty(tab));

        tab.setOnClosed(e -> {
            tabControllerMap.remove(tab);
            tab.getProperties().remove(TAB_PROP_TRANSLATOR_CONTROLLER);
            tab.getProperties().remove(TAB_PROP_FILE);
        });

        return tab;
    }

    /**
     * Guarda el estado de una pestaña de traductor en su archivo .smt.
     *
     * @param tab Pestaña a guardar.
     * @param smtFile Archivo .smt destino.
     * @return true si se guardó correctamente.
     */
    public boolean saveTranslatorTab(Tab tab, File smtFile) {
        if (tab == null) return false;

        if (smtFile == null) {
            smtFile = getFileForTab(tab);
        }
        if (smtFile == null) return false;

        RomTextTranslatorController controller = tabControllerMap.get(tab);
        if (controller == null) {
            Object stored = tab.getProperties().get(TAB_PROP_TRANSLATOR_CONTROLLER);
            if (stored instanceof RomTextTranslatorController ctrl) {
                controller = ctrl;
            }
        }
        if (controller == null) return false;

        try {
            tab.getProperties().put(TAB_PROP_FILE, smtFile.getAbsoluteFile());
            Path smtDir = smtFile.toPath().getParent();
            TranslatorModel model = controller.exportToModel(smtDir);
            persistenceService.save(smtFile, model);
            unmarkDirty(tab);
            return true;
        } catch (IOException ex) {
            showAlert("Error", "No se pudo guardar: " + ex.getMessage(), Alert.AlertType.ERROR);
            return false;
        }
    }

    /**
     * Comprueba si una pestaña es de tipo traductor.
     */
    public boolean isTranslatorTab(Tab tab) {
        return tab != null && tabControllerMap.containsKey(tab);
    }

    /**
     * Obtiene el controller de traductor asociado a una pestaña.
     */
    public RomTextTranslatorController getController(Tab tab) {
        return tabControllerMap.get(tab);
    }

    /**
     * Obtiene el archivo .smt asociado a una pestaña de traductor.
     */
    public File getFileForTab(Tab tab) {
        if (tab == null) return null;
        Object stored = tab.getProperties().get(TAB_PROP_FILE);
        return stored instanceof File file ? file : null;
    }

    /**
     * Marca una pestaña como dirty.
     */
    public void markDirty(Tab tab) {
        if (tab == null) return;
        boolean wasDirty = isDirty(tab);
        tab.getProperties().put(TAB_PROP_DIRTY, Boolean.TRUE);
        if (!wasDirty && tab.getText() != null && !tab.getText().endsWith("*")) {
            tab.setText(tab.getText() + "*");
        }
        if (onDirtyStateChanged != null) {
            onDirtyStateChanged.run();
        }
    }

    /**
     * Desmarca una pestaña como dirty.
     */
    public void unmarkDirty(Tab tab) {
        if (tab == null) return;
        tab.getProperties().put(TAB_PROP_DIRTY, Boolean.FALSE);
        if (tab.getText() != null && tab.getText().endsWith("*")) {
            tab.setText(tab.getText().substring(0, tab.getText().length() - 1));
        }
        if (onDirtyStateChanged != null) {
            onDirtyStateChanged.run();
        }
    }

    /**
     * Comprueba si una pestaña tiene cambios sin guardar.
     */
    public boolean isDirty(Tab tab) {
        if (tab == null) return false;
        Object dirty = tab.getProperties().get(TAB_PROP_DIRTY);
        return Boolean.TRUE.equals(dirty);
    }

    /**
     * Obtiene la lista de todos los archivos .smt abiertos.
     */
    public Map<Tab, File> getOpenTranslatorFiles(Map<Tab, File> tabFileMap) {
        Map<Tab, File> result = new HashMap<>();
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            if (isTranslatorTab(entry.getKey()) && entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private Path uniqueSmtFile(Path directory, String baseName) throws IOException {
        Path candidate = directory.resolve(baseName + ".smt");
        if (!Files.exists(candidate)) return candidate;

        for (int i = 2; i < 1000; i++) {
            Path alternative = directory.resolve(baseName + "-" + i + ".smt");
            if (!Files.exists(alternative)) return alternative;
        }
        throw new IOException("No se pudo generar un nombre único para " + baseName + ".smt");
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
