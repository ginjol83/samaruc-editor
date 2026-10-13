package com.retroeditor.service.basic;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Persistencia por proyecto de las asociaciones fichero {@code ->} dialecto BASIC.
 *
 * <p>El sidecar se guarda en {@code <.proyecto>/.samaruc/basic-dialects.properties}
 * (ruta relativa del fichero {@code ->} dialecto), de modo que la asociaci\u00f3n viaja
 * con el proyecto y puede committearse si se desea. Fuera de un proyecto no hay
 * sidecar; el flujo de "preguntar" gestiona la asociaci\u00f3n en memoria.</p>
 */
public final class BasicDialectStore {

    public static final String SIDE_CAR_DIR = ".samaruc";
    public static final String SIDE_CAR_FILE = "basic-dialects.properties";

    private final Path propertiesFile;
    private final Properties dialectProps = new Properties();

    public BasicDialectStore(Path projectRoot) {
        this(projectRoot, projectRoot != null
                ? projectRoot.resolve(SIDE_CAR_DIR).resolve(SIDE_CAR_FILE)
                : null);
    }

    BasicDialectStore(Path projectRoot, Path propertiesFile) {
        this.propertiesFile = propertiesFile;
        if (propertiesFile != null && Files.isRegularFile(propertiesFile)) {
            try (Reader reader = Files.newBufferedReader(propertiesFile)) {
                dialectProps.load(reader);
            } catch (IOException ex) {
                // Sidecar ilegible: se ignora y se comporta sin sobrescrituras.
            }
        }
    }

    /** Dialecto sobrescrito para una ruta relativa, si existe y es concreto. */
    public Optional<BasicDialect> dialectFor(String relativePath) {
        String value = dialectProps.getProperty(key(relativePath));
        if (value == null || value.isBlank()) return Optional.empty();
        BasicDialect dialect = BasicDialect.fromName(value);
        return dialect.isConcrete() ? Optional.of(dialect) : Optional.empty();
    }

    /** Fija la sobrescritura de una ruta relativa. Passa un no-concreto a {@link #clearDialect}. */
    public void setDialect(String relativePath, BasicDialect dialect) throws IOException {
        String k = key(relativePath);
        if (dialect == null || !dialect.isConcrete()) {
            if (dialectProps.remove(k) != null) write();
            return;
        }
        dialectProps.setProperty(k, dialect.configName());
        write();
    }

    public void clearDialect(String relativePath) throws IOException {
        if (dialectProps.remove(key(relativePath)) != null) {
            write();
        }
    }

    private void write() throws IOException {
        if (propertiesFile == null) return;
        Path dir = propertiesFile.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        try (Writer writer = Files.newBufferedWriter(propertiesFile)) {
            dialectProps.store(writer, "Samaruc: dialecto BASIC por fichero");
        }
    }

    /** Ruta relativa normalizada (separadores '/') usada como clave. */
    public static String relativePathOf(Path projectRoot, Path file) {
        if (projectRoot == null || file == null) return "";
        Path rel = projectRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize());
        return normalize(rel.toString());
    }

    private static String key(String relativePath) {
        return relativePath == null ? "" : normalize(relativePath);
    }

    private static String normalize(String path) {
        String p = path == null ? "" : path.replace('\\', '/');
        while (p.startsWith("./")) p = p.substring(2);
        return p;
    }
}