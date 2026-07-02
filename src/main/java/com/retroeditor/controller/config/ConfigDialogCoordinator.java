package com.retroeditor.controller.config;

import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.PluginApplicationService;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Coordina la apertura/cierre del dialogo de configuracion para aligerar MainController.
 */
public class ConfigDialogCoordinator {

    private Stage configStage;
    private boolean open;

    public void open(
        Stage owner,
        ConfigModel configModel,
        ConfigRepository configRepository,
        PluginApplicationService pluginApplicationService,
        ResourceBundle bundle,
        Runnable onLanguageChanged,
        Runnable onOpened,
        Runnable onClosed,
        Consumer<Exception> onError
    ) {
        if (open) {
            if (configStage != null && configStage.isShowing()) {
                configStage.toFront();
            }
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ConfigView.fxml"));
            Parent root = loader.load();

            ConfigController configController = loader.getController();
            configController.setConfigModel(configModel);
            configController.setConfigRepository(configRepository);
            configController.setPluginApplicationService(pluginApplicationService);
            configController.reloadFromModel();
            configController.setOnLanguageChanged(onLanguageChanged);
            configController.setOnCloseCallback(() -> {
                closeInternals();
                if (onClosed != null) onClosed.run();
            });

            configStage = new Stage();
            String title = (bundle != null && bundle.containsKey("label.configTitle"))
                ? bundle.getString("label.configTitle")
                : "Configuracion";
            configStage.setTitle(title);
            configStage.setScene(new Scene(root));
            if (owner != null) {
                configStage.initOwner(owner);
            }
            configStage.setOnHidden(e -> {
                closeInternals();
                if (onClosed != null) onClosed.run();
            });

            open = true;
            configStage.show();
            Platform.runLater(this::fitToVisibleScreen);

            if (onOpened != null) onOpened.run();
        } catch (Exception ex) {
            closeInternals();
            if (onError != null) onError.accept(ex);
        }
    }

    private void closeInternals() {
        configStage = null;
        open = false;
    }

    private void fitToVisibleScreen() {
        if (configStage == null || !configStage.isShowing()) return;

        final double margin = 24.0;
        Screen targetScreen = Screen.getPrimary();

        Stage owner = (Stage) configStage.getOwner();
        if (owner != null) {
            List<Screen> ownerScreens = Screen.getScreensForRectangle(
                owner.getX(), owner.getY(), Math.max(owner.getWidth(), 1), Math.max(owner.getHeight(), 1));
            if (!ownerScreens.isEmpty()) {
                targetScreen = ownerScreens.get(0);
            }
        }

        Rectangle2D bounds = targetScreen.getVisualBounds();
        double maxWidth = Math.max(520, bounds.getWidth() - margin);
        double maxHeight = Math.max(420, bounds.getHeight() - margin);

        configStage.setWidth(Math.min(configStage.getWidth(), maxWidth));
        configStage.setHeight(Math.min(configStage.getHeight(), maxHeight));
        configStage.setX(bounds.getMinX() + (bounds.getWidth() - configStage.getWidth()) / 2.0);
        configStage.setY(bounds.getMinY() + (bounds.getHeight() - configStage.getHeight()) / 2.0);
    }
}

