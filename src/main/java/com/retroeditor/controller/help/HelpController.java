package com.retroeditor.controller.help;

import java.net.URL;
import java.util.ResourceBundle;

import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.util.AppInfo;

public class HelpController {

    private ResourceBundle bundle;

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    private String msg(String key, String fallback) {
        if (bundle == null || !bundle.containsKey(key)) return fallback;
        return bundle.getString(key);
    }
    /**
     * Muestra una ventana emergente con los créditos de la aplicación.
     */
    public void onShowCredits() {
        UserActionMonitor.creditsShown();
        
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);

        alert.setTitle      (msg("menu.help.credits", "Créditos"));
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
            msg("menu.help.manual", "Manual de usuario"),
            fallbackHtml("help.manual.fallback", "Manual no disponible"),
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
            msg("menu.help.keyboardShortcuts", "Atajos de teclado"),
            fallbackHtml("help.shortcuts.fallback", "Atajos de teclado no disponibles"),
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
            msg("menu.help.basicManual", "Manual de BASIC"),
            fallbackHtml("help.basicManual.fallback", "Manual de BASIC no disponible"),
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
            msg("menu.help.licenses", "Licencias de terceros"),
            fallbackHtml("help.licenses.fallback", "Licencias no disponibles"),
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
            msg("menu.help.pluginsManual", "Manual de plugins"),
            fallbackHtml("help.pluginsManual.fallback", "Manual de plugins no disponible"),
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
            msg("menu.help.gbdkDocs", "Documentación GBDK-2020"),
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
            msg("menu.help.z88dkDocs", "Documentación Z88DK"),
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
            msg("menu.help.cc65Docs", "Documentación CC65"),
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
            msg("menu.help.openMsxDocs", "Documentación openMSX"),
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
            msg("menu.help.mesenDocs", "Documentación Mesen"),
            1200,
            800
        );
    }

    private String fallbackHtml(String key, String fallback) {
        return "<html><body><h1>" + msg(key, fallback) + "</h1></body></html>";
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