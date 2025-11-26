

package com.retroeditor.controller.help;


public class HelpController {
    /**
     * Muestra una ventana emergente con los créditos de la aplicación.
     */
    public void onShowCredits() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);

        alert.setTitle      ("Créditos");
        alert.setHeaderText ("Créditos de Samaru C Retro Editor");
        alert.setContentText("Desarrollado por Andres Gimenez © 2025\n\n¡Gracias por usar Samaru C Retro Editor!");
        alert.showAndWait   ();
    }
    
    /**
     * Abre el manual de usuario en una nueva ventana.
     */
    public void onOpenManual() {
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        String url                       = getClass().getResource("/manual.html").toExternalForm();
        
        webView.getEngine().load(url);

        javafx.scene.Scene scene = new javafx.scene.Scene(webView, 700, 600);
        javafx.stage.Stage stage = new javafx.stage.Stage();

        stage.setTitle("Manual de usuario");
        stage.setScene(scene);
        stage.show    ();
    }

}