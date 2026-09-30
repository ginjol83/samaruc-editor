package com.retroeditor.controller.config;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.retroeditor.model.ConfigModel;
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
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Prueba de integracion del boton "Duplicar" de la pestana Apariencia usando
 * el FXML real de ConfigView y el flujo del {@code ConfigDialogCoordinator}.
 */
class ConfigControllerFullFxmlThemeTest {

    private static final List<String> platformErrors = new ArrayList<>();

    private static final long FX_TIMEOUT_SECONDS = 30;

    @BeforeAll
    static void initJfx() throws InterruptedException {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException alreadyStarted) {
        }
        disableImplicitExit();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> platformErrors.add(t + " -> " + e));
    }

    /**
     * Por defecto, al cerrarse la ultima ventana JavaFX termina el hilo de la
     * aplicacion y las tareas posteriores en runLater se descartan en silencio. Estas
     * pruebas abren y cierran escenarios y dialogos, por lo que el toolkit debe
     * sobrevivir de un caso de prueba al siguiente.
     *
     * <p>No basta con llamar a {@code setImplicitExit} dentro del arranque: si otra
     * clase de prueba ya inicializo JavaFX, {@code Platform.startup} no llega a
     * ejecutarse y la opcion se quedaria activa, que es exactamente lo que hacia
     * que esta clase colgara al cerrarse el dialogo de duplicar.
     */
    private static void disableImplicitExit() throws InterruptedException {
        CountDownLatch applied = new CountDownLatch(1);
        Platform.runLater(() -> {
            Platform.setImplicitExit(false);
            applied.countDown();
        });
        if (!applied.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            fail("el hilo de JavaFX no atendio para desactivar la salida implicita");
        }
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

    /**
     * Los errores del hilo de JavaFX se recogian en una lista que solo se imprimia al
     * terminar la clase, de modo que un fallo real no suspendia ninguna prueba: el
     * AssertionError se guardaba y se mostraba dentro del caso de prueba
     * siguiente. Aqui se comprueba siempre, y de paso se cierra cualquier dialogo
     * que una prueba dejara abierto (un showAndWait sin cerrar bloquea el hilo de
     * JavaFX y arrastra a las pruebas siguientes).
     */
    @AfterEach
    void assertNoPendingFxThreadErrors() throws InterruptedException {
        try {
            runOnFxThread(ConfigControllerFullFxmlThemeTest::closeOpenInputDialogPanes);
        } finally {
            if (!platformErrors.isEmpty()) {
                List<String> captured = new ArrayList<>(platformErrors);
                platformErrors.clear();
                throw new AssertionError("errores en el hilo de JavaFX: " + captured);
            }
        }
    }

    @Test
    void duplicateButtonCreatesEditableCustomThemeInRealFxml() throws Exception {
        CountDownLatch taskFinished = new CountDownLatch(1);

        // Esta tarea se queda bloqueada dentro del TextInputDialog.showAndWait() de
        // onDuplicateTheme hasta que el dialogo se cierre, asi que no se espera aqui:
        // el hilo de prueba conduce el dialogo y luego.await(taskFinished).
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

        // onDuplicateTheme abra el dialogo. Este fire() no vuelve hasta que el dialogo se cierra.
        duplicate.fire();

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
            } finally {
                taskFinished.countDown();
            }
        });

        // El .dialog-pane solo existe tras el pulso de CSS, asi que se espera a que
        // aparezca en vez de asumirlo en la siguiente tarea.
        waitForFxCondition(() -> findOpenInputDialogPane() != null, "que aparezca el dialogo de duplicar");

        DialogPane pane = findOpenInputDialogPane();
        runOnFxThread(() -> {
            TextField input = findInputField(pane);
            assertTrue(input != null, "input field not found in dialog");
            input.setText("Mi Modo");
            Button ok = (Button) pane.lookupButton(javafx.scene.control.ButtonType.OK);
            assertTrue(ok != null, "OK button not found in dialog");
            ok.fire();
        });

        if (!taskFinished.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("la tarea de JavaFX no termino en " + FX_TIMEOUT_SECONDS + "s");
        }
        waitForFxThreadSettle();
        assertTrue(platformErrors.isEmpty(), "no exceptions on FX thread, got: " + platformErrors);
    }

    @Test
    void savingEditedActiveCustomThemeReappliesWithoutRestart() throws Exception {
        List<String> localErrors = new ArrayList<>();
        Platform.runLater(() -> {
            try {
                File tmp = Files.createTempFile("retroeditor-themes-save", ".json").toFile();
                ThemeService themeService = new ThemeService(new JsonThemeRepository(), tmp);
                ConfigModel model = new ConfigModel();

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
                loader.load();
                ConfigController controller = loader.getController();
                controller.setConfigModel(model);
                controller.setThemeService(themeService);

                int[] applyCount = {0};
                controller.setOnAppearanceApplied(() -> applyCount[0]++);

                controller.reloadFromModel();
                assertTrue(applyCount[0] == 0, "no reapply callback before saving");

                // Crear un tema personalizado y simular que YA es el activo
                // (editor_theme apunta a su id) antes de guardar.
                EditorTheme created = controller.duplicateCurrentTheme("Mi Modo Guardado");
                String createdId = created.getId();
                model.setEditorTheme(createdId);

                // Editar un color del tema activo (escenario del bug original).
                EditorTheme editing = (EditorTheme) readField(controller, "editingTheme");
                editing.setColor(EditorTheme.KEY_EDITOR_BACKGROUND, "#123456");

                Button save = (Button) readField(controller, "btnGuardarAppearance");
                assertTrue(save != null, "save appearance button must exist");
                save.fire();

                assertTrue(applyCount[0] == 1,
                    "reapply callback must run even when editor_theme id does not change, got " + applyCount[0]);
                assertTrue(createdId.equals(model.getEditorTheme()),
                    "editor_theme must keep pointing at the edited custom theme");
                EditorTheme persisted = themeService.getTheme(createdId);
                assertTrue(persisted != null, "custom theme must be persisted");
                assertTrue("#123456".equals(persisted.getColor(EditorTheme.KEY_EDITOR_BACKGROUND)),
                    "persisted theme must contain the edited color, got "
                        + persisted.getColor(EditorTheme.KEY_EDITOR_BACKGROUND));
            } catch (Throwable t) {
                localErrors.add(t + " -> " + t.getCause());
                t.printStackTrace();
            }
        });
        waitForFxThreadSettle();
        assertTrue(localErrors.isEmpty(), "no exceptions on FX thread, got: " + localErrors);
    }

    @Test
    void hexColorFieldAppliesTypedValue() throws Exception {
        List<String> localErrors = new ArrayList<>();
        Platform.runLater(() -> {
            try {
                File tmp = Files.createTempFile("retroeditor-themes-hex", ".json").toFile();
                ThemeService themeService = new ThemeService(new JsonThemeRepository(), tmp);
                ConfigModel model = new ConfigModel();

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
                loader.load();
                ConfigController controller = loader.getController();
                controller.setConfigModel(model);
                controller.setThemeService(themeService);
                controller.reloadFromModel();

                EditorTheme created = controller.duplicateCurrentTheme("Hex Mi Modo");
                String createdId = created.getId();

                @SuppressWarnings("unchecked")
                Map<String, TextField> fields =
                    (Map<String, TextField>) readField(controller, "themeColorFields");
                @SuppressWarnings("unchecked")
                Map<String, javafx.scene.control.ColorPicker> pickers =
                    (Map<String, javafx.scene.control.ColorPicker>) readField(controller, "themeColorPickers");

                TextField bgField = fields.get(EditorTheme.KEY_EDITOR_BACKGROUND);
                assertTrue(bgField != null, "hex field for editor_background must exist");
                bgField.setText("#191A1C");
                bgField.fireEvent(new javafx.event.ActionEvent());

                EditorTheme editing = (EditorTheme) readField(controller, "editingTheme");
                String stored = editing.getColor(EditorTheme.KEY_EDITOR_BACKGROUND);
                assertTrue("#191a1c".equals(stored), "typed hex must be stored in the theme, got " + stored);
                Color pickerColor = pickers.get(EditorTheme.KEY_EDITOR_BACKGROUND).getValue();
                assertTrue(pickerColor != null && "#191a1c".equals(colorToStringPublic(pickerColor)),
                    "picker must reflect the typed color");

                int[] applyCount = {0};
                controller.setOnAppearanceApplied(() -> applyCount[0]++);
                Button saveButton = (Button) readField(controller, "btnGuardarAppearance");
                assertTrue(saveButton != null, "save button must exist");
                saveButton.fire();
                assertTrue(applyCount[0] == 1, "reapply callback must run after saving the hex color");

                EditorTheme persisted = themeService.getTheme(createdId);
                assertTrue(persisted != null && "#191a1c".equals(persisted.getColor(EditorTheme.KEY_EDITOR_BACKGROUND)),
                    "typed hex must be persisted for the custom theme");
            } catch (Throwable t) {
                localErrors.add(t + " -> " + t.getCause());
                t.printStackTrace();
            }
        });
        waitForFxThreadSettle();
        assertTrue(localErrors.isEmpty(), "no exceptions on FX thread, got: " + localErrors);
    }

    private static String colorToStringPublic(javafx.scene.paint.Color color) {
        if (color.getOpacity() >= 0.999) {
            return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
        }
        return String.format("rgba(%d, %d, %d, %s)",
            Math.round(color.getRed() * 255),
            Math.round(color.getGreen() * 255),
            Math.round(color.getBlue() * 255),
            String.format(java.util.Locale.ROOT, "%.2f", color.getOpacity()));
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

    /**
     * Localiza el {@code TextInputDialog} que abre "Duplicar". El dialogo se busca por
     * su campo de texto, no por el tipo de contenido: desde JavaFX 19 el contenido de
     * {@code TextInputDialog} es un {@code VBox} que envuelve al {@code TextField}, y
     * exigir {@code content instanceof TextField} hacia que el dialogo nunca se
     * encontrara (y por tanto la prueba nunca duplicara nada).
     */
    private static DialogPane findOpenInputDialogPane() {
        for (Window w : Window.getWindows()) {
            if (!(w instanceof Stage stage) || !stage.isShowing()) continue;
            for (Object node : stage.getScene().getRoot().lookupAll(".dialog-pane")) {
                DialogPane pane = (DialogPane) node;
                if (findInputField(pane) != null) {
                    return pane;
                }
            }
        }
        return null;
    }

    private static TextField findInputField(DialogPane pane) {
        if (pane.getContent() instanceof TextField direct) {
            return direct;
        }
        for (Object child : pane.lookupAll("TextField")) {
            if (child instanceof TextField field) {
                return field;
            }
        }
        return null;
    }

    private static void closeOpenInputDialogPanes() {
        for (Window w : Window.getWindows()) {
            if (!(w instanceof Stage stage) || !stage.isShowing()) continue;
            for (Object node : stage.getScene().getRoot().lookupAll(".dialog-pane")) {
                DialogPane pane = (DialogPane) node;
                if (findInputField(pane) != null) {
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
        if (Platform.isFxApplicationThread()) return;
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("el hilo de JavaFX no se estabilizo en " + FX_TIMEOUT_SECONDS + "s");
        }
    }

    /**
     * Ejecuta una accion en el hilo de JavaFX y espera a que termine. Si el hilo no
     * atiende en el plazo la prueba falla, en vez de continuar sobre un estado sin
     * verificar: antes el tiempo de espera se ignoraba en silencio y los errores
     * acababan apareciendo en la prueba siguiente, que seguia en verde.
     */
    private static void runOnFxThread(Runnable action) throws InterruptedException {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("el hilo de JavaFX no atendio en " + FX_TIMEOUT_SECONDS + "s");
        }
        Throwable t = failure.get();
        if (t != null) throw new AssertionError("fallo en el hilo de JavaFX: " + t, t);
    }

    /**
     * Sondea una condicion desde el hilo de JavaFX hasta que se cumpla. El sondeo se
     * hace desde este hilo y no desde JavaFX a proposito: cuando hay un
     * {@code showAndWait()} abierto, el hilo de JavaFX esta dentro de un bucle de
     * eventos anidado y dormir ahi impediria el pulso de CSS que crea el
     * {@code .dialog-pane} que se esta buscando.
     */
    private static void waitForFxCondition(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FX_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (evaluateOnFxThread(condition)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("se agoto el plazo esperando: " + description);
    }

    private static boolean evaluateOnFxThread(BooleanSupplier condition) throws InterruptedException {
        if (Platform.isFxApplicationThread()) return condition.getAsBoolean();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean();
        Platform.runLater(() -> {
            try {
                result.set(condition.getAsBoolean());
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("el hilo de JavaFX no atendio en " + FX_TIMEOUT_SECONDS + "s");
        }
        return result.get();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}