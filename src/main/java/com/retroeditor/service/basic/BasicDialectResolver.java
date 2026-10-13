package com.retroeditor.service.basic;

import com.retroeditor.service.ProjectPlatformDetectionService;
import java.io.File;
import java.util.Optional;

/**
 * Resuelve el dialecto BASIC de un fichero {@code .bas}.
 *
 * <p>Precedencia (de m\u00e1s a menos espec\u00edfica):</p>
 * <ol>
 *   <li>Sobrescritura persistida del proyecto (sidecar {@code .samaruc/basic-dialects.properties}).</li>
 *   <li>Dialecto de la plataforma del proyecto detectada (vease {@link #dialectForPlatform}).</li>
 *   <li>{@link BasicDialect#GENERIC} (texto sin resaltado ni toolchain); el flujo
 *       de compilar/ejecutar ofrecer\u00e1 preguntar al usuario.</li>
 * </ol>
 *
 * <p>Un {@code .bas} fuera de cualquier proyecto o en una plataforma sin BASIC
 * propio resuelve a {@code GENERIC}, manteniendo el editor abierto a cualquier
 * dialecto sin asumir que la extensi\u00f3n significa Atari.</p>
 */
public final class BasicDialectResolver {

    private final ProjectPlatformDetectionService platformDetection;

    public BasicDialectResolver() {
        this(new ProjectPlatformDetectionService());
    }

    BasicDialectResolver(ProjectPlatformDetectionService platformDetection) {
        this.platformDetection = platformDetection;
    }

    public BasicDialect resolve(File file, File projectRoot) {
        if (file == null || !isBasicFile(file)) return BasicDialect.GENERIC;

        if (projectRoot != null) {
            BasicDialectStore store = new BasicDialectStore(projectRoot.toPath());
            Optional<BasicDialect> override = store.dialectFor(
                    BasicDialectStore.relativePathOf(projectRoot.toPath(), file.toPath()));
            if (override.isPresent()) return override.get();

            BasicDialect byPlatform = dialectForPlatform(
                    platformDetection.detectProjectPlatform(projectRoot).getPlatform());
            if (byPlatform != null) return byPlatform;
        }
        return BasicDialect.GENERIC;
    }

    public static boolean isBasicFile(File file) {
        return file != null && isBasicName(file.getName());
    }

    public static boolean isBasicName(String name) {
        return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".bas");
    }

    /**
     * Dialecto BASIC asociado por defecto a una plataforma, o {@code null} si la
     * plataforma no tiene BASIC propio (GB, SMS, GG, NES, desconocida).
     */
    public static BasicDialect dialectForPlatform(ProjectPlatformDetectionService.Platform platform) {
        if (platform == null) return null;
        return switch (platform) {
            case ATARI -> BasicDialect.ATARI;
            case SPECTRUM -> BasicDialect.ZX;
            case CPC -> BasicDialect.CPC;
            case C64 -> BasicDialect.C64;
            case MSX -> BasicDialect.MSX;
            default -> null;
        };
    }
}