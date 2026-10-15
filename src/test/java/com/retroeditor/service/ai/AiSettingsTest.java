package com.retroeditor.service.ai;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.PropertiesConfigRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsAreSafe() {
        AiSettings settings = AiSettings.defaults();

        assertNull(settings.cliPath());
        assertEquals(0, settings.port());
        assertEquals(AiSettings.Mode.SERVE, settings.mode());
        assertNull(settings.workingDirectory());
        assertEquals(AiSettings.DEFAULT_MAX_CONTEXT_KB, settings.maxContextKb());
        assertEquals(AiSettings.DEFAULT_MAX_FILES, settings.maxFilesPerPrompt());
        assertEquals(AiSettings.DEFAULT_MAX_LOG_LINES, settings.maxLogLines());
    }

    @Test
    void roundTripsThroughProperties() {
        Path workspace = tempDir.resolve("proyecto");
        AiSettings original = new AiSettings(
                "C:/tools/opencode.exe",
                41777,
                AiSettings.Mode.RUN,
                workspace,
                128,
                8,
                120);

        AiSettings restored = AiSettings.fromProperties(original.toProperties());

        assertEquals(original.cliPath(), restored.cliPath());
        assertEquals(original.port(), restored.port());
        assertEquals(original.mode(), restored.mode());
        assertEquals(original.workingDirectory(), restored.workingDirectory());
        assertEquals(original.maxContextKb(), restored.maxContextKb());
        assertEquals(original.maxFilesPerPrompt(), restored.maxFilesPerPrompt());
        assertEquals(original.maxLogLines(), restored.maxLogLines());
    }

    @Test
    void nullPropertiesFallsBackToDefaults() {
        assertEquals(AiSettings.defaults(), AiSettings.fromProperties(null));
    }

    @Test
    void corruptValuesFallBackInsteadOfFailing() {
        Properties props = new Properties();
        props.setProperty(AiSettings.KEY_PORT, "no-es-un-numero");
        props.setProperty(AiSettings.KEY_MAX_CONTEXT_KB, "999999999");
        props.setProperty(AiSettings.KEY_MAX_FILES, "-5");
        props.setProperty(AiSettings.KEY_MAX_LOG_LINES, "");
        props.setProperty(AiSettings.KEY_MODE, "TELEPATIA");

        AiSettings settings = AiSettings.fromProperties(props);

        assertEquals(0, settings.port());
        assertEquals(AiSettings.DEFAULT_MAX_CONTEXT_KB, settings.maxContextKb());
        assertEquals(AiSettings.DEFAULT_MAX_FILES, settings.maxFilesPerPrompt());
        assertEquals(AiSettings.DEFAULT_MAX_LOG_LINES, settings.maxLogLines());
        assertEquals(AiSettings.Mode.SERVE, settings.mode());
    }

    @Test
    void outOfRangePortFallsBackToRandom() {
        AiSettings settings = new AiSettings(null, 70000, AiSettings.Mode.SERVE, null, 64, 5, 40);

        assertEquals(0, settings.port());
    }

    @Test
    void blankCliPathBecomesAutodetect() {
        AiSettings settings = new AiSettings("   ", 0, AiSettings.Mode.SERVE, null, 64, 5, 40);

        assertNull(settings.cliPath());
    }

    @Test
    void workingDirectoryIsNormalised() {
        Path workspace = tempDir.resolve("sub").resolve("..").resolve("proyecto");

        AiSettings settings = AiSettings.defaults().withWorkingDirectory(workspace);

        assertTrue(settings.workingDirectory().isAbsolute());
        assertFalse(settings.workingDirectory().toString().contains(".."));
    }

    @Test
    void repositoryWritesToDedicatedFile() {
        File file = tempDir.resolve("ai.properties").toFile();
        AiSettingsRepository repository = new AiSettingsRepository(new PropertiesConfigRepository());
        AiSettings settings = new AiSettings(null, 1234, AiSettings.Mode.SERVE, null, 32, 3, 20);

        repository.save(file, settings);
        AiSettings restored = repository.load(file);

        assertTrue(Files.exists(file.toPath()));
        assertEquals(1234, restored.port());
        assertEquals(32, restored.maxContextKb());
        assertEquals(3, restored.maxFilesPerPrompt());
        assertEquals(20, restored.maxLogLines());
    }

    @Test
    void repositoryReturnsDefaultsWhenFileMissing() {
        File missing = tempDir.resolve("no-existe").resolve("ai.properties").toFile();
        AiSettingsRepository repository = new AiSettingsRepository(new PropertiesConfigRepository());

        assertEquals(AiSettings.defaults(), repository.load(missing));
    }

    @Test
    void repositoryToleratesNullSettingsOnSave() {
        File file = tempDir.resolve("null.properties").toFile();
        AiSettingsRepository repository = new AiSettingsRepository(new PropertiesConfigRepository());

        repository.save(file, null);

        assertEquals(AiSettings.defaults(), repository.load(file));
    }

    @Test
    void defaultSettingsFileLivesInUserHome() {
        File file = AiSettingsRepository.defaultSettingsFile();

        assertEquals("ai.properties", file.getName());
        assertEquals(".samaruc-editor", file.getParentFile().getName());
        assertEquals(System.getProperty("user.home"), file.getParentFile().getParent());
    }

    @Test
    void nullRepositoryFallsBackToProperties() {
        AiSettingsRepository repository = new AiSettingsRepository((ConfigRepository) null);

        assertEquals(AiSettings.defaults(), repository.load(tempDir.resolve("x.properties").toFile()));
    }
}