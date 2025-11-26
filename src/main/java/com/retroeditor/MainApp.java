package com.retroeditor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static com.retroeditor.plugin.PluginManager pluginManager;

    /**
     * Inicia la aplicación.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("Iniciando MainApp.start()");
        
        try {
            FXMLLoader  loader  = new FXMLLoader(getClass().getResource("/views/MainView.fxml"));
            Parent      root    = loader.load();
            Scene       scene   = new Scene(root);

            System.out.println("FXML cargado correctamente");

            primaryStage.setTitle("Samaru - C");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);

            // Inicializar el gestor de plugins (carpeta plugins/ junto al directorio de trabajo)
            try {
                // Intentar obtener el controlador para poder pasarle la MenuBar a los plugins
                Object controller = loader.getController();

                if (controller instanceof com.retroeditor.controller.MainController) {
                    com.retroeditor.controller.MainController mc = (com.retroeditor.controller.MainController) controller;
                    pluginManager = new com.retroeditor.plugin.PluginManager(primaryStage, scene, mc.getMenuBar());
                } else {
                    pluginManager = new com.retroeditor.plugin.PluginManager(primaryStage, scene, null);
                }

                pluginManager.loadPlugins();
                pluginManager.enablePlugins();

            } catch (Throwable t) {
                System.err.println("[Plugin] Failed to initialize PluginManager: " + t.getMessage());
                t.printStackTrace();
            }

            // Añadir favicon
            javafx.scene.image.Image favicon = new javafx.scene.image.Image(getClass().getResourceAsStream("/icons/favicon.png"));

            primaryStage.getIcons().clear();
            primaryStage.getIcons().add(favicon);
            primaryStage.show();

        } catch (Exception e) {
            System.out.println("Exception during FXML loading or stage setup:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Devuelve el gestor de plugins de la aplicación.
     */
    public static com.retroeditor.plugin.PluginManager getPluginManager() { return pluginManager; }
}