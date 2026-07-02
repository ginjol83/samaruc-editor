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
}
