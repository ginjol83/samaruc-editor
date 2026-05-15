package com.retroeditor.service;

import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas unitarias de {@link PropertiesConfigRepository}.
 *
 * <p>Comprueba persistencia basica de configuracion en archivo .properties
 * y comportamiento seguro cuando el archivo aun no existe.
 */
class PropertiesConfigRepositoryTest {

    private final PropertiesConfigRepository repository = new PropertiesConfigRepository();

    @TempDir
    Path tempDir;

    /**
     * Escenario: se intenta cargar un archivo inexistente.
     *
     * <p>El contrato esperado es devolver una instancia valida de
     * {@link Properties} vacia para evitar null checks en capas consumidoras.
     */
    @Test
    void loadReturnsEmptyWhenFileDoesNotExist() {
        Path file = tempDir.resolve("missing.properties");
        Properties props = repository.load(file.toFile());
        Assertions.assertNotNull(props);
        Assertions.assertTrue(props.isEmpty());
    }

    /**
     * Escenario nominal: guardar y volver a cargar claves de configuracion.
     *
     * <p>Valida round-trip completo de IO: las claves persisten en disco y,
     * al recargar, mantienen exactamente sus valores originales.
     */
    @Test
    void saveAndLoadRoundTrip() {
        Path file = tempDir.resolve("config.properties");
        Properties toSave = new Properties();
        toSave.setProperty("idioma", "es");
        toSave.setProperty("compilador", "GBDK");

        repository.save(file.toFile(), toSave);
        Properties loaded = repository.load(file.toFile());

        Assertions.assertEquals("es", loaded.getProperty("idioma"));
        Assertions.assertEquals("GBDK", loaded.getProperty("compilador"));
    }
}

