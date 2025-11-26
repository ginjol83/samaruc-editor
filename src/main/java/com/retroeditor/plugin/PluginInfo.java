package com.retroeditor.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO que SamaruC usa para mostrar información sobre los JARs de plugins descubiertos en la UI de configuración.
 */
public class PluginInfo {
    public String       jarName;
    public boolean      enabled     = true;
    public List<String> implNames   = new ArrayList<>();
}
