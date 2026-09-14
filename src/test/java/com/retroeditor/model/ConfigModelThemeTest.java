package com.retroeditor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Pruebas del soporte del modo de apariencia activo en {@link ConfigModel}.
 */
class ConfigModelThemeTest {

    @Test
    void editorThemeDefaultsToModernDark() {
        ConfigModel model = new ConfigModel();
        assertEquals(ConfigModel.EDITOR_THEME_DEFAULT, model.getConfigProperty("editor_theme", "otro"));
        assertEquals(ConfigModel.EDITOR_THEME_DEFAULT, model.getEditorTheme());
    }

    @Test
    void editorThemeRoundTripThroughProperties() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("editor_theme", "mi-modo");

        Properties serialized = model.toProperties();
        assertEquals("mi-modo", serialized.getProperty("editor_theme"));

        ConfigModel reloaded = new ConfigModel();
        reloaded.applyProperties(serialized);
        assertEquals("mi-modo", reloaded.getConfigProperty("editor_theme", ConfigModel.EDITOR_THEME_DEFAULT));
    }

    @Test
    void editorThemeFallsBackToEditorAppearanceOnLegacyConfig() {
        ConfigModel model = new ConfigModel();
        Properties props = new Properties();
        props.setProperty("editor_appearance", ConfigModel.EDITOR_APPEARANCE_CLASSIC);

        model.applyProperties(props);

        assertEquals(ConfigModel.EDITOR_APPEARANCE_CLASSIC, model.getConfigProperty("editor_theme", ""));
    }

    @Test
    void editorThemeIsGlobalAndIgnoresWorkspaceOverride() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("editor_theme", "mi-modo");

        WorkspaceModel workspace = new WorkspaceModel();
        workspace.setSetting("editor_theme", "classic");
        workspace.setSetting("editor_appearance", "classic");
        model.setActiveWorkspace(workspace);

        assertEquals("mi-modo", model.getConfigProperty("editor_theme", "classic"));
        assertEquals(ConfigModel.EDITOR_APPEARANCE_MODERN_DARK,
            model.getConfigProperty("editor_appearance", ConfigModel.EDITOR_APPEARANCE_MODERN_DARK));
    }

    @Test
    void blankEditorThemeNormalizesToModernDark() {
        ConfigModel model = new ConfigModel();
        model.setEditorTheme("   ");
        assertEquals(ConfigModel.EDITOR_THEME_DEFAULT, model.getEditorTheme());
    }
}