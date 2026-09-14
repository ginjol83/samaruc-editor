package com.retroeditor.controller.config;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.retroeditor.model.EditorTheme;
import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.JsonThemeRepository;
import com.retroeditor.service.ThemeService;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Prueba de integracion del boton "Duplicar" de la pestana Apariencia usando
 * el FXML real de ConfigView y el flujo del {@code ConfigDialogCoordinator}.
 */
class ConfigControllerFullFxmlThemeTest {

    private static final List<String> platformErrors = new ArrayList<>();

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> platformErrors.add(t + " -> " + e));
    }

    @AfterAll
    static void tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        System.out.println("Platform errors captured: " + platformErrors);
    }

    @BeforeEach
    void clearErrors() {
        platformErrors.clear();
    }

    @Test
    void duplicateButtonCreatesEditableCustomThemeInRealFxml() throws Exception {
        Platform.runLater(() -> {
            try {
                File tmp = Files.createTempFile("retroeditor-themes", ".json").toFile();
                ThemeService themeService = new ThemeService(new JsonThemeRepository(), tmp);

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ConfigView.fxml"));
                loader.setControllerFactory(clazz -> {
                    try {
                        ConfigController controller = (ConfigController) clazz.getDeclaredConstructor().newInstance();
                        setField(controller, "configRepository", new ConfigRepository() {
                            @Override
                            public Properties load(File configFile) {
                                return new Properties();
                            }

                            @Override
                            public void save(File configFile, Properties properties) {
                            }
                        });
                        return controller;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                Parent root = loader.load();
                ConfigController controller = loader.getController();
                controller.setConfigModel(new com.retroeditor.model.ConfigModel());
                controller.setThemeService(themeService);
                controller.reloadFromModel();

                ComboBox<?> combo = (ComboBox<?>) readField(controller, "comboThemeMode");
                assertTrue(combo.getItems().size() == 2,
                    "combo must list the 2 built-in themes, got " + combo.getItems().size());
                assertTrue(combo.getSelectionModel().getSelectedItem() instanceof EditorTheme,
                    "combo must have an initial selection");

                TabPane tabPane = findFirst(root, TabPane.class);
                assertTrue(tabPane != null, "TabPane not found");
                Tab appearance = findTab(tabPane, "Apariencia");
                assertTrue(appearance != null, "Apariencia tab not found");
                tabPane.getSelectionModel().select(appearance);

                Button duplicate = findButton(appearance, "Duplicar");
                assertTrue(duplicate != null, "Duplicar button must exist");

                Platform.runLater(duplicate::fire);
                Platform.runLater(() -> {
                    DialogPane pane = findOpenInputDialogPane();
                    if (pane == null) {
                        platformErrors.add("duplicate dialog never opened");
                        return;
                    }
                    Object content = pane.getContent();
                    if (content instanceof TextField input) {
                        input.setText("Mi Modo");
                    }
                    Button ok = (Button) pane.lookupButton(javafx.scene.control.ButtonType.OK);
                    if (ok != null) {
                        ok.fire();
                    } else {
                        platformErrors.add("OK button not found in dialog");
                    }
                });
                Platform.runLater(() -> closeOpenInputDialogPanes());

                waitForFxThreadSettle();
                waitForFxThreadSettle();

                List<EditorTheme> customs = themeService.getCustomThemes();
                assertTrue(customs.size() == 1,
                    "one custom theme must be created, got " + customs.size());
                EditorTheme created = customs.get(0);
                assertTrue(!created.isBuiltIn(), "created theme must be custom");

                Object selected = combo.getSelectionModel().getSelectedItem();
                assertTrue(selected instanceof EditorTheme && created.getId().equals(((EditorTheme) selected).getId()),
                    "combo must select the created theme, selected=" + selected);

                TextField nameField = (TextField) readField(controller, "txtThemeName");
                assertTrue(nameField.isEditable(), "name field must be editable after duplicate");
            } catch (Throwable t) {
                platformErrors.add(t + " -> " + t.getCause());
                t.printStackTrace();
            }
        });
        waitForFxThreadSettle();
        assertTrue(platformErrors.isEmpty(), "no exceptions on FX thread, got: " + platformErrors);
    }

    private static Tab findTab(TabPane pane, String text) {
        for (Tab t : pane.getTabs()) {
            if (text.equals(t.getText())) {
                return t;
            }
        }
        return null;
    }

    private static <T> T findFirst(javafx.scene.Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof javafx.scene.control.ScrollPane scrollPane) {
            T found = findFirst(scrollPane.getContent(), type);
            if (found != null) {
                return found;
            }
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                T found = findFirst(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Button findButton(Tab tab, String text) {
        return findButtonInNode(tab.getContent(), text);
    }

    private static Button findButtonInNode(javafx.scene.Node node, String text) {
        if (node instanceof Button b && text.equals(b.getText())) {
            return b;
        }
        if (node instanceof javafx.scene.control.ScrollPane scrollPane) {
            Button found = findButtonInNode(scrollPane.getContent(), text);
            if (found != null) {
                return found;
            }
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Button found = findButtonInNode(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static DialogPane findOpenInputDialogPane() {
        for (Window w : Window.getWindows()) {
            if (!(w instanceof Stage stage) || !stage.isShowing()) continue;
            for (Object node : stage.getScene().getRoot().lookupAll(".dialog-pane")) {
                DialogPane pane = (DialogPane) node;
                if (pane.getContent() instanceof TextField) {
                    return pane;
                }
            }
        }
        return null;
    }

    private static void closeOpenInputDialogPanes() {
        for (Window w : Window.getWindows()) {
            if (!(w instanceof Stage stage) || !stage.isShowing()) continue;
            for (Object node : stage.getScene().getRoot().lookupAll(".dialog-pane")) {
                DialogPane pane = (DialogPane) node;
                if (pane.getContent() instanceof TextField) {
                    stage.close();
                }
            }
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void waitForFxThreadSettle() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        latch.await(10, TimeUnit.SECONDS);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}