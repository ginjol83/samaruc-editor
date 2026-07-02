package com.retroeditor.plugin;

import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.stage.Stage;

/**
 * Interfaz java que define un contexto ligero pasado a los plugins para que puedan interactuar con SamaruC.
 */
public interface PluginContext {
    /**
     * Devuelve el Stage principal de la aplicación.
     */
    Stage getPrimaryStage();

    /**
     * Devuelve el objeto Scene principal (puede ser null hasta que la UI esté lista).
     */
    Scene getScene();

    /**
     * Proporciona acceso a la MenuBar de la aplicación para que los plugins puedan añadir elementos de menú.
     * Puede devolver null si la MenuBar no está disponible.
     */
    MenuBar getMenuBar();

    /**
     * Solicitar a SamaruC que escriba un mensaje informativo en la consola/registro.
     */
    void log(String message);

    /**
     * Registrar un Menu que el plugin añadió a la UI del host. SamaruC eliminará automáticamente los menús 
     * registrados cuando el plugin se descargue.
     */
    void registerMenu(Menu menu);

    /**
     * Anular el registro de un Menu previamente registrado.
     */
    void unregisterMenu(Menu menu);

    /**
     * Eliminar todos los recursos registrados previamente por el plugin (menús, listeners, etc.). Las 
     * implementaciones deben limpiar la UI y demás registros del host para que el plugin pueda ser 
     * descargado de forma segura y su classloader cerrado.
     */
    void unregisterAll();
}
