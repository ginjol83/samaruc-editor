package com.retroeditor.plugin;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.module.Configuration;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import com.retroeditor.service.AppLogger;

/**
 * Gestor de plugins sencillo que carga implementaciones de plugins mediante ServiceLoader desde JARs ubicados en
 * la carpeta `plugins/` junto al directorio de trabajo.
 */
public class PluginManager {
    private final File pluginsDir;
    private final File propsFile;

    private final Properties   props  = new Properties();
    private final List<Plugin> loaded = new ArrayList<>();

    // mantener referencias para poder crear contextos por plugin
    private final Stage   stage;
    private final Scene   scene;
    private final MenuBar menuBar;

    // seguimiento para una limpieza correcta al descargar
    private final Map<Plugin, URLClassLoader> pluginLoaders  = new HashMap<>();
    private final Map<Plugin, PluginContext>  pluginContexts = new HashMap<>();

    /**
     * Metodo para crear un nuevo PluginManager.
     *
     * @param stage   instancia de {@link javafx.stage.Stage} asociada a la aplicación (puede ser no nula)
     * @param scene   instancia de {@link javafx.scene.Scene} principal (puede ser {@code null} si la UI aún no está inicializada)
     * @param menuBar instancia de {@link javafx.scene.control.MenuBar} usada por la aplicación (puede ser {@code null} si no está disponible)
     */
    public PluginManager(Stage stage, Scene scene, MenuBar menuBar) {
        this.pluginsDir = new File(System.getProperty("user.dir"),  "plugins");
        this.propsFile  = new File(System.getProperty("user.home"), ".retroeditor.plugins.properties");

        // cargar propiedades si están presentes
        if (propsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("Failed to load plugins properties: " + e.getMessage());
            }
        }

        this.stage   = stage;
        this.scene   = scene;
        this.menuBar = menuBar;
    }

    /**
     * Método encargado de cargar plugins desde la carpeta de plugins.
     */
    public void loadPlugins() {
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            log("Directorio de plugins no encontrado: " + pluginsDir.getAbsolutePath());
            return;
        }

        File[] jars = pluginsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jar");
            }
        });

        if (jars == null || jars.length == 0) {
            log("No plugin jars found in " + pluginsDir.getAbsolutePath());
            return;
        }

        for (File jar : jars) {
            // comprobar si está habilitado/deshabilitado el JAR del plugin
            String key     = jar.getName();
            String enabled = props.getProperty(key, "true");

            if (!Boolean.parseBoolean(enabled)) {
                log("Skipping disabled plugin jar: " + jar.getName());
                continue;
            }

            try {
                URL url = jar.toURI().toURL();
                
                // Usar un classloader child-first para que los plugins puedan sobrescribir clases
                URLClassLoader loader    = new ChildFirstClassLoader(new URL[] { url }, this.getClass().getClassLoader());
                ServiceLoader<Plugin> sl = ServiceLoader.load(Plugin.class, loader);
                Iterator<Plugin> it      = sl.iterator();

                while (it.hasNext()) {
                    try {
                        Plugin plugin = it.next();
                        // crear un contexto por plugin para poder rastrear la UI registrada por el plugin
                        PluginContextImpl ctx = new PluginContextImpl(stage, scene, menuBar);
                        plugin.onLoad(ctx);
                        loaded.add(plugin);
                        pluginLoaders.put(plugin, loader);
                        pluginContexts.put(plugin, ctx);
                        log("Loaded plugin: " + plugin.getName());
                    } catch (Throwable t) {
                        log("Failed to initialize plugin from " + jar.getName() + " -> " + t.getMessage());
                        t.printStackTrace();
                    }
                }
            } catch (MalformedURLException e) {
                log("Invalid plugin jar URL: " + jar.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    /**
     * Definimos una clase interna ChildFirstClassLoader.
     * Un URLClassLoader que intenta cargar clases desde sus URLs primero antes de delegar al parent. 
     * Esto permite que los plugins incluyan copias de clases del host y que se usen esas versiones en lugar de las del host.
     */
    private static class ChildFirstClassLoader extends URLClassLoader {
        private final ClassLoader system;

        /**
         * Constructor del ChildFirstClassLoader.
         */
        ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.system = getSystemClassLoader();
        }

        /**
         * Cargar una clase con preferencia por las definiciones en este classloader.
         *
         * @param  name: nombre de la clase a cargar
         * @param  resolve: si es {@code true}, resolver la clase después de cargarla
         * @return la clase cargada
         * @throws ClassNotFoundException si la clase no se encuentra en este classloader ni en los padres
         */
        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            String[] parentFirst = new String[]{"java.", "javax.", "javafx.", "com.sun.", "org.w3c.", "org.xml.", "org.openjfx.", "org.slf4j.", "org.kordamp."};
            
            for (String p : parentFirst) {
                if (name.startsWith(p)) {
                    return getParent().loadClass(name);
                }
            }

            Class<?> c = findLoadedClass(name);

            if (c == null) {
                try {
                    // Intentar cargar desde este classloader (child) primero
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    // Si no se encuentra en el child, delegar al parent
                    try {
                        c = getParent().loadClass(name);
                    } catch (ClassNotFoundException ex) {
                        // Como último recurso, intentar el system classloader
                        c = system.loadClass(name);
                    }
                }
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }

    /**
     * Descubrir plugins en el directorio de plugins.
     * @return lista de PluginInfo con información sobre los plugins encontrados
     */
    public List<PluginInfo> discoverPlugins() {
        List<PluginInfo> out = new ArrayList<>();

        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) return out;

        File[] jars = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        if (jars == null) return out;

        for (File jar : jars) {
            PluginInfo info = new PluginInfo();
            info.jarName    = jar.getName();
            info.enabled    = Boolean.parseBoolean(props.getProperty(info.jarName, "true"));

            // intentar leer entradas de servicio
            try (URLClassLoader loader = new URLClassLoader(new URL[] { jar.toURI().toURL() }, this.getClass().getClassLoader())) {
                ServiceLoader<Plugin> sl = ServiceLoader.load(Plugin.class, loader);

                for (Plugin p : sl) {
                    try { info.implNames.add(p.getName()); } catch (Throwable ignored) {}
                }

            } catch (Exception e) {
                // ignorar errores de descubrimiento
            }

            out.add(info);
        }
        return out;
    }

    /**
     * Habilitar o deshabilitar un JAR de plugin.
     * @param jarName nombre del archivo JAR del plugin
     * @param enabled {@code true} para habilitar, {@code false} para deshabilitar
     */
    public void setJarEnabled(String jarName, boolean enabled) {
        props.setProperty(jarName, Boolean.toString(enabled));
        
        try (FileOutputStream fos = new FileOutputStream(propsFile)) {
            props.store(fos, "Retro Editor plugins enabled/disabled");
            log("Saved plugins properties to " + propsFile.getAbsolutePath());
        } catch (IOException e) {
            log("Failed to save plugins properties: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Habilitar todos los plugins cargados.
     */
    public void enablePlugins() {
        for (Plugin p : loaded) {
            try { p.onEnable(); } catch (Throwable t) { log("Error enabling " + p.getName() + ": " + t.getMessage()); }
        }
    }

    /**
     * Deshabilitar todos los plugins cargados.
     */
    public void disablePlugins() {

        for (Plugin p : new ArrayList<>(loaded)) {

            try { p.onDisable(); } catch (Throwable t) { log("Error disabling " + p.getName() + ": " + t.getMessage()); }
            PluginContext ctx = pluginContexts.get(p);

            if (ctx != null) {
                try { ctx.unregisterAll(); } catch (Throwable ignored) {}
            }

            URLClassLoader loader = pluginLoaders.get(p);

            if (loader != null) {
                try { loader.close(); } catch (IOException e) { log("Error closing classloader for " + p.getName() + ": " + e.getMessage()); }
            }

            pluginContexts.remove(p);
            pluginLoaders.remove(p);
        }
        loaded.clear();
    }

    /**
     * Recargar plugins: deshabilitar los actuales, limpiar la lista y cargar/habilitar de nuevo.
     */
    public void reloadPlugins() {
        log("Recargando plugins...");

        try {
            disablePlugins();
        } catch (Throwable ignored) {}

        loaded.clear();
        loadPlugins();
        enablePlugins();
    }

    /**
     * Clase interna.
     * Implementación simple de PluginContext para uso interno.
     */
    private static class SimplePluginContext implements PluginContext {
        private final Stage   stage;
        private final Scene   scene;
        private final MenuBar menuBar;

        private final List<Menu> registeredMenus = new ArrayList<>();

        /**
         * Constructor de SimplePluginContext.
         */
        SimplePluginContext(Stage stage, Scene scene, MenuBar menuBar) {
            this.stage   = stage;
            this.scene   = scene;
            this.menuBar = menuBar;
        }

        /**
         * @return el escenario primario
         */
        @Override public Stage getPrimaryStage() { return stage; }

        /**
         * @return la escena principal
         */
        @Override public Scene getScene() { return scene; }

        /**
         * @return la barra de menú
         */
        @Override public MenuBar getMenuBar() { return menuBar; }

        /**
         * Registrar un mensaje en el logger del host.
         *
         * @param message texto a registrar; se espera una cadena no nula (si se pasa {@code null} se registrará la cadena "null").
         */
        @Override public void log(String message) { AppLogger.logMonitor("plugin", message); }

        /**
         * Registrar un {@link javafx.scene.control.Menu} en la MenuBar del host.
         *
         * @param menu menú a registrar; si es {@code null} la llamada no tiene efecto.
         */
        @Override
        public void registerMenu(Menu menu) {
            if (menu == null) return;
            registeredMenus.add(menu);
            if (menuBar != null) menuBar.getMenus().add(menu);
        }

        /**
         * Anular el registro de un Menu previamente registrado.
         *
         * @param menu menú a anular; si es {@code null} la llamada no tiene efecto.
         */
        @Override
        public void unregisterMenu(Menu menu) {
            if (menu == null) return;
            registeredMenus.remove(menu);
            if (menuBar != null) menuBar.getMenus().remove(menu);
        }

        /**
         * Eliminar todos los recursos registrados previamente por el plugin (menús, listeners, etc.).
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

    /**
     * Registra un mensaje en el logger del plugin manager.
     */
    private void log(String message) { AppLogger.logMonitor("pluginmanager", message); }
}
