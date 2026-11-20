package com.retroeditor.controller.help;

import java.net.URL;

import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.util.AppInfo;

public class HelpController {
    /**
     * Muestra una ventana emergente con los créditos de la aplicación.
     */
    public void onShowCredits() {
        UserActionMonitor.creditsShown();
        
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);

        alert.setTitle      ("Créditos");
        alert.setHeaderText ("Samaru C Retro Editor v" + AppInfo.getVersion());
        alert.setContentText("Desarrollado por Andres Gimenez © 2025\n\n¡Gracias por usar Samaru C Retro Editor!");
        alert.showAndWait   ();
    }
    
    /**
     * Abre el manual de usuario HTML en una nueva ventana.
     */
    public void onOpenManual() {
        UserActionMonitor.manualOpened();
        openHtmlWindow(
            "/manual.html",
            "Manual de usuario",
            "<html><body><h1>Manual no disponible</h1></body></html>",
            820,
            680
        );
    }

    /**
     * Abre el manual de usuario directamente en la sección de atajos de teclado.
     */
    public void onOpenKeyboardShortcuts() {
        UserActionMonitor.keyboardShortcutsOpened();
        openHtmlWindow(
            "/manual.html",
            "Atajos de teclado",
            "<html><body><h1>Atajos de teclado no disponibles</h1></body></html>",
            820,
            680,
            "atajos-teclado"
        );
    }

    /**
     * Abre el manual de BASIC (inclusiones y rangos de numeracion) en una ventana WebView.
     */
    public void onOpenBasicManual() {
        UserActionMonitor.basicManualOpened();
        openHtmlWindow(
            "/manual-basic.html",
            "Manual de BASIC",
            "<html><body><h1>Manual de BASIC no disponible</h1></body></html>",
            900,
            740
        );
    }

    /**
     * Abre una ventana con licencias de librerias de terceros usadas por la aplicacion.
     */
    public void onOpenThirdPartyLicenses() {
        UserActionMonitor.licensesOpened();
        openHtmlWindow(
            "/third-party-licenses.html",
            "Licencias de terceros",
            "<html><body><h1>Licencias no disponibles</h1></body></html>",
            900,
            700
        );
    }

    /**
     * Abre el manual de desarrollo de plugins en una ventana WebView.
     */
    public void onOpenPluginManual() {
        UserActionMonitor.pluginManualOpened();
        openHtmlWindow(
            "/manual-plugins.html",
            "Manual de plugins",
            "<html><body><h1>Manual de plugins no disponible</h1></body></html>",
            980,
            760
        );
    }

    /**
     * Abre la documentación oficial de GBDK-2020 en una ventana WebView.
     */
    public void onOpenGbdkDocs() {
        UserActionMonitor.gbdkDocsOpened();
        openUrlWindow(
            "https://gbdk.org/docs/api/index.html",
            "Documentación GBDK-2020",
            1100,
            800
        );
    }

    /**
     * Abre la documentación oficial de Z88DK en una ventana WebView.
     */
    public void onOpenZ88dkDocs() {
        // Podríamos añadir una acción específica en UserActionMonitor si fuera necesario
        openUrlWindow(
            "https://github.com/z88dk/z88dk/wiki",
            "Documentación Z88DK",
            1200,
            800
        );
    }

    /**
     * Abre la documentación oficial de CC65 (Atari / C64 / NES) en una ventana WebView.
     */
    public void onOpenCc65Docs() {
        openUrlWindow(
            "https://cc65.github.io/",
            "Documentación CC65",
            1200,
            800
        );
    }

    /**
     * Abre la documentación oficial de openMSX en una ventana WebView.
     */
    public void onOpenOpenMsxDocs() {
        openUrlWindow(
            "https://openmsx.org/manual/",
            "Documentación openMSX",
            1200,
            800
        );
    }

    /**
     * Abre la documentación oficial de Mesen en una ventana WebView.
     */
    public void onOpenMesenDocs() {
        openUrlWindow(
            "https://www.mesen.ca/",
            "Documentación Mesen",
            1200,
            800
        );
    }

    private void openHtmlWindow(String resourcePath, String title, String fallbackHtml, int width, int height) {
        openHtmlWindow(resourcePath, title, fallbackHtml, width, height, null);
    }

    private void openHtmlWindow(String resourcePath, String title, String fallbackHtml, int width, int height,
                                String anchor) {
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        URL url = getClass().getResource(resourcePath);

        if (url != null) {
            String location = url.toExternalForm();
            if (anchor != null && !anchor.isBlank()) {
                location += "#" + anchor;
            }
            webView.getEngine().load(location);
        } else {
            webView.getEngine().loadContent(fallbackHtml);
        }

        javafx.scene.Scene scene = new javafx.scene.Scene(webView, width, height);
        javafx.stage.Stage stage = new javafx.stage.Stage();

        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
    }

    private void openUrlWindow(String url, String title, int width, int height) {
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.getEngine().load(url);

        javafx.scene.Scene scene = new javafx.scene.Scene(webView, width, height);
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
    }

}