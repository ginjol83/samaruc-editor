package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.model.EditorTheme;

/**
 * Pruebas unitarias de {@link JsonThemeRepository}.
 */
class JsonThemeRepositoryTest {

    private final JsonThemeRepository repository = new JsonThemeRepository();

    @TempDir
    Path tempDir;

    @Test
    void loadReturnsEmptyWhenFileDoesNotExist() {
        List<EditorTheme> themes = repository.load(tempDir.resolve("missing.json").toFile());
        assertNotNull(themes);
        assertTrue(themes.isEmpty());
    }

    @Test
    void loadReturnsEmptyWhenFileIsCorrupt() throws Exception {
        Path file = tempDir.resolve("modos.json");
        Files.writeString(file, "esto-no-es-json{");

        List<EditorTheme> themes = repository.load(file.toFile());
        assertNotNull(themes);
        assertTrue(themes.isEmpty());
    }

    @Test
    void saveAndLoadRoundTrip() {
        Path file = tempDir.resolve("modos.json");

        EditorTheme themeA = new EditorTheme();
        themeA.setId("mi-modo");
        themeA.setName("Mi Modo");
        themeA.setBuiltIn(false);
        themeA.setDarkBase(true);
        themeA.setFontFamily("Fira Code");
        themeA.setFontSize(15.0);
        themeA.setLineNumberSize(14.0);
        themeA.setColor(EditorTheme.KEY_EDITOR_BACKGROUND, "#123456");
        themeA.setColor(EditorTheme.KEY_SYNTAX_KEYWORD, "#abcdef");

        repository.save(file.toFile(), List.of(themeA));

        List<EditorTheme> loaded = repository.load(file.toFile());
        assertEquals(1, loaded.size());
        EditorTheme theme = loaded.get(0);
        assertEquals("mi-modo", theme.getId());
        assertEquals("Mi Modo", theme.getName());
        assertEquals("#123456", theme.getColor(EditorTheme.KEY_EDITOR_BACKGROUND));
        assertEquals("#abcdef", theme.getColor(EditorTheme.KEY_SYNTAX_KEYWORD));
        assertEquals("Fira Code", theme.getFontFamily());
        assertEquals(15.0, theme.getFontSize());
        assertEquals(14.0, theme.getLineNumberSize());
        assertEquals(14.0, theme.getEffectiveLineNumberSize());
    }

    @Test
    void saveCreatesParentDirectories() {
        Path file = tempDir.resolve("nested").resolve("deep").resolve("modos.json");

        EditorTheme theme = new EditorTheme();
        theme.setId("directorio");
        theme.setName("Directorio");

        repository.save(file.toFile(), List.of(theme));

        List<EditorTheme> loaded = repository.load(file.toFile());
        assertEquals(1, loaded.size());
        assertEquals("directorio", loaded.get(0).getId());
    }
}