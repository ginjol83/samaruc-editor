package com.retroeditor.controller.config;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.retroeditor.model.EditorTheme;
import com.retroeditor.service.JsonThemeRepository;
import com.retroeditor.service.ThemeService;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

class ConfigControllerDuplicateThemeTest {

    private static List<String> platformErrors = new ArrayList<>();

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> platformErrors.add(e.toString()));
    }

    @AfterAll
    static void tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(null);
    }

    @Test
    void duplicateCurrentThemeCreatesCustomThemeAndEnablesControls() throws Exception {
        Platform.runLater(() -> {
            try {
                File tmp = Files.createTempFile("retroeditor-themes", ".json").toFile();
                ThemeService themeService = new ThemeService(new JsonThemeRepository(), tmp);

                ConfigController controller = new ConfigController();
                setField(controller, "themeService", themeService);

                ComboBox<EditorTheme> combo = new ComboBox<>();
                TextField txtName = new TextField();
                ComboBox<String> comboFont = new ComboBox<>();
                comboFont.setEditable(true);
                Spinner<Double> spFont = new Spinner<>();
                CheckBox chkAuto = new CheckBox();
                Spinner<Double> spLine = new Spinner<>();
                VBox colorBox = new VBox();
                VBox previewBox = new VBox();

                setField(controller, "comboThemeMode", combo);
                setField(controller, "txtThemeName", txtName);
                setField(controller, "comboFontFamily", comboFont);
                setField(controller, "spFontSize", spFont);
                setField(controller, "chkLineNumberAuto", chkAuto);
                setField(controller, "spLineNumberSize", spLine);
                setField(controller, "vboxThemeColors", colorBox);
                setField(controller, "vboxThemePreview", null);
                setField(controller, "previewAreas", new ArrayList<>());

                invoke(controller, "loadThemeUiFromConfig");

                assertTrue(combo.getSelectionModel().getSelectedItem() != null, "combo must select a theme");
                assertTrue(themeService.isBuiltIn(combo.getSelectionModel().getSelectedItem().getId()),
                    "initial selection must be a built-in theme");

                invoke(controller, "duplicateCurrentTheme", "Mi Modo");

                List<EditorTheme> customs = themeService.getCustomThemes();
                assertTrue(customs.size() == 1, "one custom theme must be created, got " + customs.size());
                EditorTheme created = customs.get(0);
                assertTrue("Mi Modo".equals(created.getName()), "custom theme name must be 'Mi Modo'");
                assertTrue(!created.isBuiltIn(), "custom theme must not be built-in");

                EditorTheme selected = combo.getSelectionModel().getSelectedItem();
                assertTrue(selected != null && created.getId().equals(selected.getId()),
                    "combo must select the created theme, selected=" + selected);

                assertTrue(txtName.isEditable(), "editing controls must be enabled after duplicate");
                assertTrue(!spFont.isDisable(), "font size spinner must be enabled after duplicate");
                assertTrue(!comboFont.isDisable(), "font combo must be enabled after duplicate");
                assertTrue(!chkAuto.isDisable(), "auto line numbers checkbox must be enabled after duplicate");
            } catch (Throwable t) {
                platformErrors.add(t.toString());
                t.printStackTrace();
            }
        });
        drainPlatform();
        assertTrue(platformErrors.isEmpty(), "no exceptions on FX thread, got: " + platformErrors);
    }

    @Test
    void duplicateKeepsBuiltInThemeReadOnly() throws Exception {
        Platform.runLater(() -> {
            try {
                File tmp = Files.createTempFile("retroeditor-themes", ".json").toFile();
                ThemeService themeService = new ThemeService(new JsonThemeRepository(), tmp);

                ConfigController controller = new ConfigController();
                setField(controller, "themeService", themeService);

                ComboBox<EditorTheme> combo = new ComboBox<>();
                TextField txtName = new TextField();
                ComboBox<String> comboFont = new ComboBox<>();
                comboFont.setEditable(true);
                Spinner<Double> spFont = new Spinner<>();
                CheckBox chkAuto = new CheckBox();
                Spinner<Double> spLine = new Spinner<>();
                VBox colorBox = new VBox();
                VBox previewBox = new VBox();

                setField(controller, "comboThemeMode", combo);
                setField(controller, "txtThemeName", txtName);
                setField(controller, "comboFontFamily", comboFont);
                setField(controller, "spFontSize", spFont);
                setField(controller, "chkLineNumberAuto", chkAuto);
                setField(controller, "spLineNumberSize", spLine);
                setField(controller, "vboxThemeColors", colorBox);
                setField(controller, "vboxThemePreview", previewBox);
                setField(controller, "previewAreas", new ArrayList<>());

                invoke(controller, "loadThemeUiFromConfig");

                EditorTheme selected = combo.getSelectionModel().getSelectedItem();
                assertTrue(selected != null && "modern_dark".equals(selected.getId()),
                    "default selection must be modern_dark");
                assertTrue(!txtName.isEditable(), "built-in theme controls must stay read-only");
                assertTrue(spFont.isDisable(), "built-in theme font spinner must stay disabled");
            } catch (Throwable t) {
                platformErrors.add(t.toString());
                t.printStackTrace();
            }
        });
        drainPlatform();
        assertTrue(platformErrors.isEmpty(), "no exceptions on FX thread, got: " + platformErrors);
    }

    private static void drainPlatform() throws InterruptedException {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(latch::countDown);
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}