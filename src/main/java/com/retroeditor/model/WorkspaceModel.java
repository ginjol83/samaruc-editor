package com.retroeditor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa la configuración específica de un espacio de trabajo (proyecto).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkspaceModel {
    private String projectName;
    private Map<String, String> settings = new HashMap<>();

    public WorkspaceModel() {
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    /**
     * Obtiene un valor de configuración del workspace.
     * @param key Clave de la configuración.
     * @return El valor si existe, null en caso contrario.
     */
    public String getSetting(String key) {
        return settings != null ? settings.get(key) : null;
    }

    /**
     * Establece un valor de configuración en el workspace.
     * @param key Clave.
     * @param value Valor.
     */
    public void setSetting(String key, String value) {
        if (settings == null) {
            settings = new HashMap<>();
        }
        if (value == null) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
    }
}
