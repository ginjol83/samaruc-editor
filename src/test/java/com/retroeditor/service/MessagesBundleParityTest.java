package com.retroeditor.service;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


/**
 * Comprueba que los bundles de i18n esten sincronizados entre si. Una clave que
 * solo existe en el bundle base (o que se olvida en una traduccion) no da error de
 * compilacion ni de arranque: la interfaz cae al bundle base o al texto de reserva
 * y el fallo solo se ve en pantalla. Por eso se compara el conjunto de claves.
 */
class MessagesBundleParityTest {

    private static final String BASE_RESOURCE = "i18n/MessagesBundle.properties";
    private static final String[] EXPECTED_LOCALES = { "es", "en", "de", "fr", "it" };

    private static final List<String> resourceNames = new ArrayList<>();
    private static final Properties base = new Properties();

    @BeforeAll
    static void loadBundles() throws Exception {
        resourceNames.addAll(listResourceNames());
        base.putAll(readBundle(BASE_RESOURCE));
    }

    @Test
    void everyExpectedLocaleBundleIsPresent() {
        for (String locale : EXPECTED_LOCALES) {
            String resource = "i18n/MessagesBundle_" + locale + ".properties";
            Assertions.assertTrue(resourceNames.contains(resource),
                "falta el bundle " + resource + "; los que hay son " + resourceNames);
        }
    }

    @Test
    void noStrayFilesInTheI18nFolder() {
        List<String> strays = new ArrayList<>();
        for (String name : resourceNames) {
            if (!name.equals(BASE_RESOURCE) && !name.startsWith("i18n/MessagesBundle_")) {
                strays.add(name);
            }
        }
        for (String locale : EXPECTED_LOCALES) {
            String resource = "i18n/MessagesBundle_" + locale + ".properties";
            if (!resourceNames.contains(resource)) {
                strays.add("falta " + resource);
            }
        }
        Assertions.assertEquals(List.of(), strays,
            "la carpeta i18n solo admite MessagesBundle.properties y un fichero por idioma; "
                + "hay archivos o idiomas inesperados");
    }

    @Test
    void everyLocaleDefinesAllTheKeysOfTheBaseBundle() throws IOException {
        for (String locale : EXPECTED_LOCALES) {
            String resource = "i18n/MessagesBundle_" + locale + ".properties";
            Assertions.assertTrue(resourceNames.contains(resource), "falta el bundle " + resource);
            Set<String> missing = new TreeSet<>(base.stringPropertyNames());
            missing.removeAll(readBundle(resource).stringPropertyNames());
            Assertions.assertEquals(Set.of(), missing,
                "claves del bundle base ausentes en " + resource);
        }
    }

    @Test
    void noLocaleDefinesKeysThatTheBaseBundleLacks() throws IOException {
        for (String locale : EXPECTED_LOCALES) {
            String resource = "i18n/MessagesBundle_" + locale + ".properties";
            if (!resourceNames.contains(resource)) continue;
            Set<String> extra = new TreeSet<>(readBundle(resource).stringPropertyNames());
            extra.removeAll(base.stringPropertyNames());
            Assertions.assertEquals(Set.of(), extra,
                resource + " define claves que el bundle base no tiene; la clave deberia existir tambien en MessagesBundle.properties");
        }
    }

    @Test
    void noBundleHasBlankValues() throws IOException {
        for (String resource : resourceNames) {
            Properties properties = readBundle(resource);
            List<String> blanks = new ArrayList<>();
            for (String key : new TreeSet<>(properties.stringPropertyNames())) {
                if (properties.getProperty(key).isBlank()) {
                    blanks.add(key);
                }
            }
            Assertions.assertEquals(List.of(), blanks, "valores vacios en " + resource);
        }
    }

    @Test
    void languageServiceResolvesEveryKeyInEveryDeclaredLocale() {
        LanguageService languageService = new LanguageService();
        for (String locale : EXPECTED_LOCALES) {
            languageService.setLanguage(locale);
            Assertions.assertEquals(locale, languageService.getCurrentLanguageCode(),
                "LanguageService no reconoce el locale " + locale);
            List<String> unresolved = new ArrayList<>();
            for (String key : base.stringPropertyNames()) {
                String value = languageService.getBundleForCode(locale).getString(key);
                if (value == null || value.isBlank() || key.equals(value)) {
                    unresolved.add(key);
                }
            }
            Assertions.assertEquals(List.of(), unresolved,
                "LanguageService no resuelve estas claves para el locale " + locale);
        }
    }

    private static Properties readBundle(String resource) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(i18nDir().resolve(Path.of(resource.substring(resource.indexOf('/') + 1))), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static List<String> listResourceNames() throws Exception {
        Path dir = i18nDir();
        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                .filter(Files::isRegularFile)
                .map(p -> "i18n/" + p.getFileName())
                .sorted()
                .toList();
        }
    }

    /**
     * Los bundles se leen del arbol de fuentes, no del classpath: target/classes
     * conserva copias de recursos borrados y haria fallar el test hasta el
     * siguiente mvn clean.
     */
    private static Path i18nDir() {
        Path dir = Path.of("src", "main", "resources", "i18n");
        Assertions.assertTrue(Files.isDirectory(dir), "no se encuentra la carpeta de fuentes " + dir.toAbsolutePath());
        return dir;
    }
}
