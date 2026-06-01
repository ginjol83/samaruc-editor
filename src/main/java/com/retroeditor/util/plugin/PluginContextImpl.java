package com.retroeditor.plugin;

import java.util.ArrayList;
import java.util.List;

import com.retroeditor.service.AppLogger;

import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.stage.Stage;

/**
 * Implementación concreta de {@link PluginContext} usada por el host.
 *
 * <p>Proporciona un pequeño "sandbox" con accesos controlados a recursos del host que los plugins pueden necesitar (Stage, Scene, MenuBar) y
 * mantiene una lista de menús registrados por el plugin para poder limpiarlos cuando éste se desactive.</p>
 *
 * <p>Notas sobre hilos: cualquier manipulación de la UI (añadir/eliminar menús, actualizar componentes) debe realizarse en la JavaFX Application
 * Thread. Los métodos de este contexto no fuerzan el hilo; el plugin debe usar {@code Platform.runLater(...)} cuando corresponda.</p>
 */
public class PluginContextImpl implements PluginContext {
    private final Stage   stage;
    private final Scene   scene;
    private final MenuBar menuBar;
    
    private final List<Menu> registeredMenus = new ArrayList<>();

    /**
     * Crear un nuevo PluginContextImpl.
     *
     * @param stage instancia de {@link javafx.stage.Stage} asociada a la aplicación (puede ser no nula)
     * @param scene instancia de {@link javafx.scene.Scene} principal (puede ser {@code null} si la UI aún no está inicializada)
     * @param menuBar instancia de {@link javafx.scene.control.MenuBar} usada por la aplicación (puede ser {@code null} si no está disponible)
     */
    public PluginContextImpl(Stage stage, Scene scene, MenuBar menuBar) {
        this.stage   = stage;
        this.scene   = scene;
        this.menuBar = menuBar;
    }

    /**
     * Devuelve el {@link javafx.stage.Stage} principal de la aplicación.
     *
     * @return el Stage principal; normalmente no es {@code null} cuando la UI está lista.
     */
    @Override
    public Stage getPrimaryStage() { return stage; }

    /**
     * Devuelve la {@link javafx.scene.Scene} principal de la aplicación.
     *
     * @return la Scene principal o {@code null} si la interfaz gráfica aún no se ha inicializado.
     */
    @Override
    public Scene getScene() { return scene; }

    /**
     * Proporciona acceso a la {@link javafx.scene.control.MenuBar} de la aplicación.
     *
     * @return la MenuBar del host o {@code null} si no está disponible.
     */
    @Override
    public MenuBar getMenuBar() { return menuBar; }

    /**
     * Registrar un mensaje en el logger del host.
     *
     * @param message texto a registrar; se espera una cadena no nula (si se pasa {@code null} se registrará la cadena "null").
     */
    @Override
    public void log(String message) { AppLogger.logMonitor("plugin", message); }

    /**
     * Registrar un {@link javafx.scene.control.Menu} en la MenuBar del host.
     * El host mantendrá una referencia para poder eliminarlo automáticamente cuando el plugin se desactive.
     *
     * @param menu menú a registrar; si es {@code null} la llamada no tiene efecto.
     */
    @Override
    public void registerMenu(Menu menu) {
        if (menu == null) return;

        registeredMenus.add(menu);

        if (menuBar != null) {
            menuBar.getMenus().add(menu);
        }
    }

    /**
     * Anular el registro de un menú previamente registrado por este plugin.
     *
     * @param menu menú a eliminar; si es {@code null} o no estaba registrado no tiene efecto.
     */
    @Override
    public void unregisterMenu(Menu menu) {
        if (menu == null) return;

        registeredMenus.remove(menu);

        if (menuBar != null) {
            menuBar.getMenus().remove(menu);
        }
    }

    /**
     * Eliminar todos los recursos registrados por el plugin (actualmente solo menus). Este método intenta dejar la 
     * UI del host en el mismo estado que antes de que el plugin añadiera sus elementos.
     */
    @Override
    public void unregisterAll() {
        if (menuBar != null) {
            for (Menu m : new ArrayList<>(registeredMenus)) {
                try { menuBar.getMenus().remove(m); } catch (Throwable ignored) {}
            }
        }

        registeredMenus.clear();
    }
}
