package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprueba que todas las traducciones del conversor PNG a C existen en los seis
 * bundles. Una clave que se olvida en un idioma sale en pantalla como
 * {@code !clave!} en lugar de con el texto traducido.
 */
@DisplayName("RetroConverterMessages Tests")
class RetroConverterMessagesTest {
    private static final Path BUNDLES = Path.of("src", "main", "resources", "i18n");
    private static final List<String> LANGUAGES = List.of("", "_es", "_en", "_de", "_fr", "_it");
    private static final String PREFIX = "tool.pngToRetro.";

    @Test
    @DisplayName("Los seis bundles tienen las mismas claves del conversor")
    void allBundlesShareTheSameKeys() throws IOException {
        Properties base = load("");
        for (String language : LANGUAGES) {
            Properties bundle = load(language);
            for (String key : base.stringPropertyNames()) {
                if (key.startsWith(PREFIX)) {
                    assertTrue(bundle.containsKey(key),
                            "Falta la clave " + key + " en MessagesBundle" + language + ".properties");
                }
            }
        }
    }

    @Test
    @DisplayName("Cada modo de video tiene su etiqueta en todos los idiomas")
    void everyModeHasItsOwnLabel() throws IOException {
        List<String> modeKeys = List.of("cpc0", "cpc1", "cpc2", "atariPlayfield", "atariMode7",
                "c64Charset", "c64Bitmap", "masterSystem", "gameGear");
        assertEquals(RetroImageMode.values().length, modeKeys.size(),
                "Hay que traducir tambien los modos nuevos");
        for (String language : LANGUAGES) {
            Properties bundle = load(language);
            for (String key : modeKeys) {
                String fullKey = PREFIX + "mode." + key;
                assertTrue(bundle.containsKey(fullKey),
                        "Falta la clave " + fullKey + " en MessagesBundle" + language + ".properties");
            }
        }
    }

    @Test
    @DisplayName("Las etiquetas de los modos no estan vacias")
    void everyLabelHasText() throws IOException {
        for (String language : LANGUAGES) {
            Properties bundle = load(language);
            for (String key : bundle.stringPropertyNames()) {
                if (key.startsWith(PREFIX)) {
                    assertFalse(bundle.getProperty(key).isBlank(),
                            "La clave " + key + " de MessagesBundle" + language + ".properties esta vacia");
                }
            }
        }
    }

    @Test
    @DisplayName("El bundle base define la interfaz completa del conversor")
    void baseBundleIsComplete() throws IOException {
        Properties base = load("");
        assertEquals(new TreeSet<>(base.stringPropertyNames()).size(), base.stringPropertyNames().size(),
                "No hay claves repetidas en el bundle base");
        for (String key : List.of("title", "selectFile", "mode", "arrayName", "width", "height", "defaultSize",
                "includeConst", "includeDefines", "preview", "noImage", "converting", "output", "buttonExport",
                "buttonClipboard", "buttonClose")) {
            assertTrue(base.containsKey(PREFIX + key), "Falta la clave " + PREFIX + key + " en el bundle base");
        }
    }

    private Properties load(String language) throws IOException {
        Path path = BUNDLES.resolve("MessagesBundle" + language + ".properties");
        assertTrue(Files.exists(path), "No se encuentra el bundle " + path);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
