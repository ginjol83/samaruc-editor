package com.retroeditor.controller.tools;

import java.io.File;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.retroeditor.service.ProjectPlatformDetectionService;
import com.retroeditor.service.ProjectPlatformDetectionService.Platform;
import com.retroeditor.service.converter.PngConversionTarget;
import com.retroeditor.service.converter.RetroImageMode;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Herramienta unica "Convertir PNG a C", multiplataforma.
 *
 * Detecta la maquina del proyecto abierto con {@link ProjectPlatformDetectionService}
 * y la preselecciona; si no hay proyecto o la deteccion no se decide, el usuario
 * elige la maquina en el selector. Cada maquina incrusta el panel ya existente de
 * su conversor (Game Boy, ZX Spectrum o la familia Retro) para no duplicar la
 * logica de conversion.
 */
public class UnifiedPngToCController {

    /** Maquina con su etiqueta ya traducida. */
    private record TargetOption(PngConversionTarget target, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private ResourceBundle bundle;
    private Runnable closeHandler;

    /**
     * Crea la UI de la herramienta para mostrarla como pestana del editor.
     *
     * @param currentProjectDir proyecto abierto (puede ser {@code null}); se usa
     *        para detectar la maquina y como carpeta inicial de los conversores
     * @param bundle bundle de traduccion de la interfaz
     * @param onExported se invoca con el archivo exportado para refrescar el
     *        arbol de ficheros y abrirlo en el editor
     */
    public Node createNode(File currentProjectDir, ResourceBundle bundle, Consumer<File> onExported) {
        this.bundle = bundle;
        return buildUi(currentProjectDir, onExported);
    }

    /**
     * Registra la accion a ejecutar cuando la pestana de la herramienta se cierra.
     */
    public void setOnCloseRequest(Runnable onClose) {
        this.closeHandler = onClose;
    }

    private void requestClose() {
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    private Node buildUi(File currentProjectDir, Consumer<File> onExported) {
        PngConversionTarget detected = detectTarget(currentProjectDir);

        ComboBox<TargetOption> machineBox = new ComboBox<>();
        for (PngConversionTarget target : PngConversionTarget.values()) {
            machineBox.getItems().add(new TargetOption(target, targetLabel(target)));
        }
        machineBox.setPrefWidth(260);

        Label detectedLabel = new Label();
        detectedLabel.setWrapText(true);
        if (detected != null) {
            detectedLabel.setText(msg("tool.pngToC.detected", "Maquina detectada del proyecto: {0}")
                    .replace("{0}", targetLabel(detected)));
        } else if (currentProjectDir == null) {
            detectedLabel.setText(msg("tool.pngToC.noProject", "Abre un proyecto para detectar la maquina automaticamente."));
        }

        HBox header = new HBox(12,
                new Label(msg("tool.pngToC.platform", "Maquina:")),
                machineBox,
                detectedLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 10, 0, 10));

        StackPane contentPane = new StackPane();
        contentPane.setPadding(new Insets(0, 10, 10, 10));

        machineBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                contentPane.getChildren().setAll(buildPanel(newV.target(), currentProjectDir, onExported));
            }
        });

        if (detected != null) {
            machineBox.getItems().stream()
                    .filter(o -> o.target() == detected)
                    .findFirst()
                    .ifPresent(machineBox::setValue);
        } else {
            machineBox.setValue(machineBox.getItems().get(0));
        }

        VBox root = new VBox(10, header, new Separator(), contentPane);
        root.setPadding(new Insets(10));
        return root;
    }

    private Node buildPanel(PngConversionTarget target, File projectDir, Consumer<File> onExported) {
        switch (target) {
            case GAMEBOY:
                PngToGameBoyConverterController gameBoy = new PngToGameBoyConverterController();
                gameBoy.setOnCloseRequest(this::requestClose);
                return gameBoy.createNode(projectDir, bundle, onExported);
            case ZX_SPECTRUM:
                PngToZxConverterController zx = new PngToZxConverterController();
                zx.setOnCloseRequest(this::requestClose);
                return zx.createNode(projectDir, bundle, onExported);
            default:
                RetroImageMode mode = target.defaultRetroMode().orElse(RetroImageMode.CPC_MODE_1);
                PngToRetroConverterController retro = new PngToRetroConverterController();
                retro.setOnCloseRequest(this::requestClose);
                return retro.createNode(projectDir, bundle, mode, onExported);
        }
    }

    /** Detecta la maquina del proyecto si lo hay; {@code null} si no decide. */
    private PngConversionTarget detectTarget(File projectDir) {
        if (projectDir == null) {
            return null;
        }
        Platform platform = new ProjectPlatformDetectionService().detectProjectPlatform(projectDir).getPlatform();
        return PngConversionTarget.fromDetectedPlatform(platform).orElse(null);
    }

    private String targetLabel(PngConversionTarget target) {
        return msg("tool.pngToC.platform." + target.key(), target.name());
    }

    private String msg(String key, String fallback) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getString(key);
        }
        return fallback;
    }
}