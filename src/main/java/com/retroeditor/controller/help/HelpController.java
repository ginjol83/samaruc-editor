package com.retroeditor.controller.help;

import java.net.URL;

import com.retroeditor.service.UserActionMonitor;

public class HelpController {
    /**
     * Muestra una ventana emergente con los créditos de la aplicación.
     */
    public void onShowCredits() {
        UserActionMonitor.creditsShown();
        
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);

        alert.setTitle      ("Créditos");
        alert.setHeaderText ("Créditos de Samaru C Retro Editor");
        alert.setContentText("Desarrollado por Andres Gimenez © 2025\n\n¡Gracias por usar Samaru C Retro Editor!");
        alert.showAndWait   ();
    }
    
    /**
     * Abre una pagina temporal del manual en una nueva ventana.
     */
    public void onOpenManual() {
        UserActionMonitor.manualOpened();

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        URL url = getClass().getResource("/manual-under-construction.html");

        if (url != null) {
            webView.getEngine().load(url.toExternalForm());
        } else {
            // Fallback defensivo para no dejar la vista en blanco si faltara el recurso.
            webView.getEngine().loadContent("<html><body><h1>En construccion</h1></body></html>");
        }

        javafx.scene.Scene scene = new javafx.scene.Scene(webView, 700, 600);
        javafx.stage.Stage stage = new javafx.stage.Stage();

        stage.setTitle("Manual de usuario (En construccion)");
        stage.setScene(scene);
        stage.show();
    }

}