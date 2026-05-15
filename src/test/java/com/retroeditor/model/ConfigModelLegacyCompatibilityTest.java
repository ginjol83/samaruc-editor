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
}

