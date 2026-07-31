package com.retroeditor.model;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigModelLegacyCompatibilityTest {

    @Test
    void loadsLegacySpectrumCompilerAsZ88dkProfileSpectrum() {
        ConfigModel model = new ConfigModel();
        Properties props = new Properties();
        props.setProperty("compilador_seleccionado", "Spectrum");

        model.applyProperties(props);

        Assertions.assertEquals("z88dk", model.getConfigProperty("compilador_seleccionado", "GBDK"));
        Assertions.assertEquals("spectrum", model.getConfigProperty("z88dk_profile", "spectrum"));
        Assertions.assertEquals("JSpeccy", model.getConfigProperty("emulador_seleccionado", ""));
    }

    @Test
    void loadsExplicitZ88dkCpcLabelAsZ88dkProfileCpcWhenProfileProvided() {
        ConfigModel model = new ConfigModel();
        Properties props = new Properties();
        props.setProperty("compilador_seleccionado", "Z88DK (CPC)");
        props.setProperty("z88dk_profile", "cpc");

        model.applyProperties(props);

        Assertions.assertEquals("z88dk", model.getConfigProperty("compilador_seleccionado", "GBDK"));
        Assertions.assertEquals("cpc", model.getConfigProperty("z88dk_profile", "spectrum"));
        Assertions.assertEquals("CPCBoxWeb", model.getConfigProperty("emulador_seleccionado", ""));
    }

    @Test
    void writesCanonicalCompilerPropertiesAfterLegacyLoad() {
        ConfigModel model = new ConfigModel();
        Properties props = new Properties();
        props.setProperty("compilador_seleccionado", "Spectrum");
        props.setProperty("emulador_seleccionado", "LegacyEmu");

        model.applyProperties(props);

        Properties normalized = model.toProperties();
        Assertions.assertEquals("z88dk", normalized.getProperty("compilador_seleccionado"));
        Assertions.assertEquals("spectrum", normalized.getProperty("z88dk_profile"));
        Assertions.assertEquals("JSpeccy", normalized.getProperty("emulador_seleccionado"));
    }

    @Test
    void preservesCustomRecentPropertiesOnRoundTrip() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("recent_files", "C:\\proj\\main.c\nC:\\proj\\README.md");
        model.setConfigProperty("last_open_file_directory", "C:\\proj");

        Properties serialized = model.toProperties();
        Assertions.assertEquals("C:\\proj\\main.c\nC:\\proj\\README.md", serialized.getProperty("recent_files"));
        Assertions.assertEquals("C:\\proj", serialized.getProperty("last_open_file_directory"));

        ConfigModel reloaded = new ConfigModel();
        reloaded.applyProperties(serialized);
        Assertions.assertEquals("C:\\proj\\main.c\nC:\\proj\\README.md", reloaded.getConfigProperty("recent_files", ""));
        Assertions.assertEquals("C:\\proj", reloaded.getConfigProperty("last_open_file_directory", ""));
    }

    @Test
    void editorAppearanceDefaultsToModernDarkAndPersistsClassicChoice() {
        ConfigModel model = new ConfigModel();
        Assertions.assertEquals(
            ConfigModel.EDITOR_APPEARANCE_MODERN_DARK,
            model.getConfigProperty("editor_appearance", ConfigModel.EDITOR_APPEARANCE_CLASSIC)
        );

        model.setConfigProperty("editor_appearance", ConfigModel.EDITOR_APPEARANCE_CLASSIC);
        Properties serialized = model.toProperties();
        Assertions.assertEquals(ConfigModel.EDITOR_APPEARANCE_CLASSIC, serialized.getProperty("editor_appearance"));

        ConfigModel reloaded = new ConfigModel();
        reloaded.applyProperties(serialized);
        Assertions.assertEquals(
            ConfigModel.EDITOR_APPEARANCE_CLASSIC,
            reloaded.getConfigProperty("editor_appearance", ConfigModel.EDITOR_APPEARANCE_MODERN_DARK)
        );
    }

    @Test
    void preservesSessionRestorePropertiesOnRoundTrip() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("last_session_project", "C:\\retro\\project");
        model.setConfigProperty("last_session_open_files", "C:\\retro\\project\\src\\main.c\nC:\\retro\\project\\README.md");
        model.setConfigProperty("last_session_active_file", "C:\\retro\\project\\src\\main.c");

        Properties serialized = model.toProperties();
        Assertions.assertEquals("C:\\retro\\project", serialized.getProperty("last_session_project"));
        Assertions.assertEquals("C:\\retro\\project\\src\\main.c\nC:\\retro\\project\\README.md", serialized.getProperty("last_session_open_files"));
        Assertions.assertEquals("C:\\retro\\project\\src\\main.c", serialized.getProperty("last_session_active_file"));

        ConfigModel reloaded = new ConfigModel();
        reloaded.applyProperties(serialized);
        Assertions.assertEquals("C:\\retro\\project", reloaded.getConfigProperty("last_session_project", ""));
        Assertions.assertEquals("C:\\retro\\project\\src\\main.c\nC:\\retro\\project\\README.md", reloaded.getConfigProperty("last_session_open_files", ""));
        Assertions.assertEquals("C:\\retro\\project\\src\\main.c", reloaded.getConfigProperty("last_session_active_file", ""));
    }
}
