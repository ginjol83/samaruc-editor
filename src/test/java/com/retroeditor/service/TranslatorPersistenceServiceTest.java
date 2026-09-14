package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.model.TranslatorModel;
import com.retroeditor.service.gameboy.TextEncoding;

class TranslatorPersistenceServiceTest {

    private final TranslatorPersistenceService service = new TranslatorPersistenceService();

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadEmptyModel() throws IOException {
        File smtFile = tempDir.resolve("test.smt").toFile();
        TranslatorModel model = new TranslatorModel();

        service.save(smtFile, model);
        assertTrue(smtFile.exists());

        TranslatorModel loaded = service.load(smtFile);
        assertEquals(TranslatorModel.CURRENT_VERSION, loaded.getVersion());
        assertNull(loaded.getRomPath());
        assertEquals("pokemon", loaded.getFontTable());
    }

    @Test
    void saveAndLoadWithEntries() throws IOException {
        File smtFile = tempDir.resolve("test.smt").toFile();
        TranslatorModel model = new TranslatorModel();
        model.setRomPath("roms/pokemon.gbc");
        model.setFontTable("dragonquest");
        model.setEncoding(TextEncoding.SENTINEL_0xFF);
        model.addEntry(new TranslatorModel.TranslatorEntry(1, "4A2F", "C3 A1", "HELLO", "HOLA"));
        model.addEntry(new TranslatorModel.TranslatorEntry(2, "1000", "BB CC", "WORLD", "MUNDO"));

        service.save(smtFile, model);
        TranslatorModel loaded = service.load(smtFile);

        assertEquals("roms/pokemon.gbc", loaded.getRomPath());
        assertEquals("dragonquest", loaded.getFontTable());
        assertEquals(TextEncoding.SENTINEL_0xFF, loaded.getEncoding());
        assertEquals(2, loaded.getEntries().size());
        assertEquals("HELLO", loaded.getEntries().get(0).getDecodedText());
        assertEquals("HOLA", loaded.getEntries().get(0).getTranslatedText());
        assertEquals("MUNDO", loaded.getEntries().get(1).getTranslatedText());
    }

    @Test
    void saveAndLoadWithDetectedMappings() throws IOException {
        File smtFile = tempDir.resolve("test.smt").toFile();
        TranslatorModel model = new TranslatorModel();
        model.getDetectedMappings().put("41", "A");
        model.getDetectedMappings().put("42", "B");

        service.save(smtFile, model);
        TranslatorModel loaded = service.load(smtFile);

        assertEquals(2, loaded.getDetectedMappings().size());
        assertEquals("A", loaded.getDetectedMappings().get("41"));
        assertEquals("B", loaded.getDetectedMappings().get("42"));
    }

    @Test
    void loadNonExistentFileReturnsEmptyModel() {
        File smtFile = tempDir.resolve("nonexistent.smt").toFile();
        TranslatorModel loaded = service.load(smtFile);
        assertNotNull(loaded);
        assertEquals(TranslatorModel.CURRENT_VERSION, loaded.getVersion());
    }

    @Test
    void loadInvalidJsonReturnsEmptyModel() throws IOException {
        File smtFile = tempDir.resolve("invalid.smt").toFile();
        Files.writeString(smtFile.toPath(), "not valid json {{{");
        TranslatorModel loaded = service.load(smtFile);
        assertNotNull(loaded);
        assertEquals(TranslatorModel.CURRENT_VERSION, loaded.getVersion());
    }

    @Test
    void toRelativePath() {
        Path smtDir = Path.of("/project/translations");
        Path romPath = Path.of("/project/roms/pokemon.gbc");
        String relative = service.toRelativePath(romPath, smtDir);
        assertEquals("../roms/pokemon.gbc", relative);
    }

    @Test
    void toRelativePathSameDirectory() {
        Path smtDir = Path.of("/project");
        Path romPath = Path.of("/project/pokemon.gbc");
        String relative = service.toRelativePath(romPath, smtDir);
        assertEquals("pokemon.gbc", relative);
    }

    @Test
    void toAbsolutePath() {
        Path smtDir = tempDir;
        Path resolved = service.toAbsolutePath("roms/pokemon.gbc", smtDir);
        assertNotNull(resolved);
        assertTrue(resolved.toString().contains("roms"));
        assertTrue(resolved.toString().contains("pokemon.gbc"));
    }

    @Test
    void toAbsolutePathNullInput() {
        assertNull(service.toAbsolutePath(null, tempDir));
        assertNull(service.toAbsolutePath("", tempDir));
        assertNull(service.toAbsolutePath("roms/gb", null));
    }

    @Test
    void isSmtFile() {
        assertTrue(TranslatorPersistenceService.isSmtFile(new File("test.smt")));
        assertTrue(TranslatorPersistenceService.isSmtFile(new File("TEST.SMT")));
        assertFalse(TranslatorPersistenceService.isSmtFile(new File("test.txt")));
        assertFalse(TranslatorPersistenceService.isSmtFile(null));
    }

    @Test
    void isSmtFileName() {
        assertTrue(TranslatorPersistenceService.isSmtFileName("test.smt"));
        assertTrue(TranslatorPersistenceService.isSmtFileName("TEST.SMT"));
        assertFalse(TranslatorPersistenceService.isSmtFileName("test.txt"));
        assertFalse(TranslatorPersistenceService.isSmtFileName(null));
    }

    @Test
    void saveThrowsOnNullFile() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.save(null, new TranslatorModel());
        });
    }

    @Test
    void saveThrowsOnNullModel() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.save(tempDir.resolve("test.smt").toFile(), null);
        });
    }

    @Test
    void savedFileIsReadableJson() throws IOException {
        File smtFile = tempDir.resolve("readable.smt").toFile();
        TranslatorModel model = new TranslatorModel();
        model.setRomPath("test.gbc");
        service.save(smtFile, model);

        String content = Files.readString(smtFile.toPath());
        assertTrue(content.contains("romPath"));
        assertTrue(content.contains("test.gbc"));
        assertTrue(content.contains("pokemon"));
        assertTrue(content.contains("version"));
    }
}
