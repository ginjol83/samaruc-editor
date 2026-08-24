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
    void loadsNativeGccCompilerSelection() {
        ConfigModel model = new ConfigModel();
        Properties props = new Properties();
        props.setProperty("compilador_seleccionado", "gcc");

        model.applyProperties(props);

        Assertions.assertEquals("gcc", model.getConfigProperty("compilador_seleccionado", "GBDK"));
    }

    @Test
    void normalizesDisplayLabelsAndAliasesToGcc() {
        for (String label : new String[] {"Native C (gcc)", "C normal (gcc)", "desktop", "C Natif (gcc)"}) {
            ConfigModel model = new ConfigModel();
            Properties props = new Properties();
            props.setProperty("compilador_seleccionado", label);

            model.applyProperties(props);

            Assertions.assertEquals(
                "gcc",
                model.getConfigProperty("compilador_seleccionado", "GBDK"),
                "Expected '" + label + "' to normalize to gcc"
            );
        }
    }

    @Test
    void roundTripsGccCompilerSettings() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("compilador_seleccionado", "gcc");
        model.setConfigProperty("gcc_opt_level", "-O2");
        model.setConfigProperty("gcc_defines", "DEBUG,FOO=1");
        model.setConfigProperty("gcc_includes", "C:\\proj\\include");
        model.setConfigProperty("gcc_extra_args", "-Wall");

        Properties serialized = model.toProperties();

        ConfigModel reloaded = new ConfigModel();
        reloaded.applyProperties(serialized);
        Assertions.assertEquals("gcc", reloaded.getConfigProperty("compilador_seleccionado", "GBDK"));
        Assertions.assertEquals("-O2", reloaded.getConfigProperty("gcc_opt_level", ""));
        Assertions.assertEquals("DEBUG,FOO=1", reloaded.getConfigProperty("gcc_defines", ""));
        Assertions.assertEquals("C:\\proj\\include", reloaded.getConfigProperty("gcc_includes", ""));
        Assertions.assertEquals("-Wall", reloaded.getConfigProperty("gcc_extra_args", ""));
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

    @Test
    void workspaceGccSettingsTakePrecedenceOverGlobal() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("compilador_seleccionado", "gcc");
        model.setConfigProperty("gcc_opt_level", "-O3");
        model.setConfigProperty("gcc_extra_args", "-Wall -g");

        WorkspaceModel workspace = new WorkspaceModel();
        workspace.setProjectName("demo");
        workspace.setSetting("gcc_opt_level", "-O2");
        workspace.setSetting("gcc_extra_args", "-Wall -Wextra");

        model.setActiveWorkspace(workspace);

        Assertions.assertEquals("-O2", model.getConfigProperty("gcc_opt_level", "none"));
        Assertions.assertEquals("-Wall -Wextra", model.getConfigProperty("gcc_extra_args", ""));
        Assertions.assertEquals("gcc", model.getConfigProperty("compilador_seleccionado", "GBDK"));
    }

    @Test
    void workspaceFallsBackToGlobalForMissingKeys() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("gcc_includes", "C:\\global\\include");

        WorkspaceModel workspace = new WorkspaceModel();
        workspace.setSetting("gcc_opt_level", "-O1");

        model.setActiveWorkspace(workspace);

        Assertions.assertEquals("-O1", model.getConfigProperty("gcc_opt_level", "none"));
        Assertions.assertEquals("C:\\global\\include", model.getConfigProperty("gcc_includes", ""));

        model.setActiveWorkspace(null);
        Assertions.assertEquals("none", model.getConfigProperty("gcc_opt_level", "none"));
        Assertions.assertEquals("C:\\global\\include", model.getConfigProperty("gcc_includes", ""));
    }

    @Test
    void workspaceRemovalReturnsToGlobalValue() {
        ConfigModel model = new ConfigModel();
        model.setConfigProperty("gcc_extra_args", "-Wall");

        WorkspaceModel workspace = new WorkspaceModel();
        workspace.setSetting("gcc_extra_args", "-O2");
        model.setActiveWorkspace(workspace);
        Assertions.assertEquals("-O2", model.getConfigProperty("gcc_extra_args", ""));

        workspace.setSetting("gcc_extra_args", null);
        Assertions.assertEquals("-Wall", model.getConfigProperty("gcc_extra_args", ""));
    }
}
