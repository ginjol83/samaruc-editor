package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.model.BuiltInThemes;
import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.EditorTheme;

/**
 * Pruebas unitarias de {@link ThemeService}.
 */
class ThemeServiceTest {

    @TempDir
    Path tempDir;

    private File themesFile;
    private ThemeService service;

    @BeforeEach
    void setUp() {
        themesFile = tempDir.resolve("modos.json").toFile();
        service = new ThemeService(new JsonThemeRepository(), themesFile);
    }

    @Test
    void builtInThemesAreAlwaysAvailable() {
        assertTrue(service.isBuiltIn(BuiltInThemes.MODERN_DARK_ID));
        assertTrue(service.isBuiltIn(BuiltInThemes.CLASSIC_ID));
        assertFalse(service.isCustom(BuiltInThemes.MODERN_DARK_ID));
        assertNotNull(service.getTheme(BuiltInThemes.MODERN_DARK_ID));
        assertNotNull(service.getTheme(BuiltInThemes.CLASSIC_ID));
    }

    @Test
    void modernDarkThemeReproducesCurrentDarkCssPalette() {
        EditorTheme dark = service.getTheme(BuiltInThemes.MODERN_DARK_ID);
        assertTrue(dark.isDarkBase());
        assertEquals("#0f172a", dark.getColor(EditorTheme.KEY_EDITOR_BACKGROUND));
        assertEquals("#c084fc", dark.getColor(EditorTheme.KEY_SYNTAX_KEYWORD));
        assertEquals("#60a5fa", dark.getColor(EditorTheme.KEY_SYNTAX_ZX_FUNCTION));
        assertEquals(13.0, dark.getFontSize());
        assertEquals(12.0, dark.getEffectiveLineNumberSize());
    }

    @Test
    void classicThemeHasLightBaseAndDarkColors() {
        EditorTheme classic = service.getTheme(BuiltInThemes.CLASSIC_ID);
        assertFalse(classic.isDarkBase());
        assertEquals("#f8fafc", classic.getColor(EditorTheme.KEY_EDITOR_BACKGROUND));
        assertEquals("#7c3aed", classic.getColor(EditorTheme.KEY_SYNTAX_KEYWORD));
    }

    @Test
    void duplicateCreatesCustomThemeWithSanitizedIdAndCopiesColors() {
        EditorTheme source = service.getTheme(BuiltInThemes.MODERN_DARK_ID);

        EditorTheme copy = service.duplicate(BuiltInThemes.MODERN_DARK_ID, "Mi Modo");

        assertNotNull(copy);
        assertEquals("mi-modo", copy.getId());
        assertEquals("Mi Modo", copy.getName());
        assertFalse(copy.isBuiltIn());
        assertTrue(service.isCustom("mi-modo"));
        assertEquals(source.getColor(EditorTheme.KEY_SYNTAX_KEYWORD), copy.getColor(EditorTheme.KEY_SYNTAX_KEYWORD));
        assertTrue(service.getAllThemes().contains(copy));
    }

    @Test
    void duplicateGeneratesUniqueIdOnNameCollision() {
        service.duplicate(BuiltInThemes.MODERN_DARK_ID, "Mi Modo");
        EditorTheme second = service.duplicate(BuiltInThemes.MODERN_DARK_ID, "Mi Modo");

        assertEquals("mi-modo-2", second.getId());
    }

    @Test
    void saveCustomUpsertsAndReloadsFromDisk() {
        EditorTheme theme = service.createCustom("Retro");
        theme.setColor(EditorTheme.KEY_SYNTAX_KEYWORD, "#ff0000");
        service.saveCustom(theme);

        ThemeService reloaded = new ThemeService(new JsonThemeRepository(), themesFile);
        EditorTheme loaded = reloaded.getTheme("retro");
        assertNotNull(loaded);
        assertEquals("#ff0000", loaded.getColor(EditorTheme.KEY_SYNTAX_KEYWORD));
        assertFalse(loaded.isBuiltIn());
    }

    @Test
    void saveCustomAssignsUniqueIdWhenIdIsBlank() {
        EditorTheme theme = new EditorTheme();
        theme.setName("Sin id");
        theme.setColor(EditorTheme.KEY_EDITOR_BACKGROUND, "#000000");

        EditorTheme saved = service.saveCustom(theme);

        assertNotNull(saved.getId());
        assertEquals("sin-id", saved.getId());
        assertTrue(service.isCustom("sin-id"));
    }

    @Test
    void deleteCustomRemovesAndPersists() {
        service.createCustom("Temporal");

        assertTrue(service.deleteCustom("temporal"));
        assertFalse(service.isCustom("temporal"));
        assertEquals(2, service.getAllThemes().size());

        ThemeService reloaded = new ThemeService(new JsonThemeRepository(), themesFile);
        assertNull(reloaded.getTheme("temporal"));
    }

    @Test
    void deleteBuiltInReturnsFalseAndDoesNothing() {
        assertFalse(service.deleteCustom(BuiltInThemes.CLASSIC_ID));
        assertTrue(service.isBuiltIn(BuiltInThemes.CLASSIC_ID));
        assertEquals(2, service.getAllThemes().size());
    }

    @Test
    void getThemeOrDefaultFallsBackToModernDarkForUnknownId() {
        EditorTheme fallback = service.getThemeOrDefault("no-existe");
        assertEquals(BuiltInThemes.MODERN_DARK_ID, fallback.getId());
    }

    @Test
    void resolveActiveThemeIdPrefersEditorTheme() {
        service.createCustom("Mi Modo");
        ConfigModel config = new ConfigModel();
        config.setConfigProperty("editor_theme", "mi-modo");

        assertEquals("mi-modo", service.resolveActiveThemeId(config));
    }

    @Test
    void resolveActiveThemeIdMigratesLegacyClassicAppearance() {
        ConfigModel config = new ConfigModel();
        config.setConfigProperty("editor_appearance", "classic");

        assertEquals(BuiltInThemes.CLASSIC_ID, service.resolveActiveThemeId(config));
    }

    @Test
    void resolveActiveThemeIdFallsBackToModernDarkWhenThemeMissing() {
        ConfigModel config = new ConfigModel();
        config.setConfigProperty("editor_theme", "modo-borrado");

        assertEquals(BuiltInThemes.MODERN_DARK_ID, service.resolveActiveThemeId(config));
    }

    @Test
    void newCustomIdSanitizesName() {
        assertEquals("mi-modo-especial", service.newCustomId("  Mi Modo Especial!!  "));
        assertEquals("modo-personalizado", service.newCustomId("   "));
    }
}