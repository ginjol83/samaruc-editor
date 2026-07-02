package com.retroeditor.plugin;

/**
 * Esta interfaz define el contrato mínimo que usarán los plugins de SamaruC Editor.
 */
public interface Plugin {
    /**
     * Es llamado una vez cuando el plugin se carga. 
     */
    void onLoad(PluginContext context);

    /**
     * Es llamado cuando el plugin debe iniciarse (la interfaz de usuario está disponible).
     */
    void onEnable();

    /**
     * Es llamado cuando el plugin se está deshabilitando/descargando.
     */
    void onDisable();

    /**
     * Nombre legible por usuarios del plugin.
     */
    String getName();
}
