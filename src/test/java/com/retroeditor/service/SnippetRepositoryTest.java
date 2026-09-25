package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.service.SnippetService.NamedSnippet;

class SnippetRepositoryTest {

    @TempDir
    Path tmp;

    private SnippetRepository repo(File file) {
        return new SnippetRepository(new ConfigRepository() {
            @Override
            public java.util.Properties load(File f) {
                java.util.Properties props = new java.util.Properties();
                if (f.exists()) {
                    try (var in = Files.newInputStream(f.toPath())) {
                        props.load(in);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return props;
            }

            @Override
            public void save(File f, java.util.Properties props) {
                try {
                    Files.createDirectories(f.getParentFile().toPath());
                    try (var out = Files.newOutputStream(f.toPath())) {
                        props.store(out, null);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }, file);
    }

    @Test
    void saveAndLoadRoundTripPreservesOrderAndValues() {
        File file = new File(tmp.toFile(), "snippets.properties");
        SnippetRepository repo = repo(file);
        repo.save(List.of(
            new NamedSnippet("for", "for (int i = 0; i < ${1:n}; i++) {${0}}"),
            new NamedSnippet("fn", "uint8_t f(void) { return ${1:0}; }")));

        List<NamedSnippet> loaded = repo.load();
        assertEquals(2, loaded.size());
        assertEquals("for", loaded.get(0).name());
        assertEquals("for (int i = 0; i < ${1:n}; i++) {${0}}", loaded.get(0).template());
        assertEquals("fn", loaded.get(1).name());
        assertTrue(loaded.get(1).template().contains("${1:0}"));
    }

    @Test
    void loadReturnsEmptyForMissingFile() {
        File file = new File(tmp.toFile(), "no-existe.properties");
        assertTrue(repo(file).load().isEmpty());
    }

    @Test
    void saveSkipsBlankNames() {
        File file = new File(tmp.toFile(), "snippets.properties");
        SnippetRepository repo = repo(file);
        repo.save(List.of(
            new NamedSnippet("  ", "x"),
            new NamedSnippet("ok", "y")));

        List<NamedSnippet> loaded = repo.load();
        assertEquals(1, loaded.size());
        assertEquals("ok", loaded.get(0).name());
    }

    @Test
    void loadAcrossSeparateInstancesIsStable() {
        File file = new File(tmp.toFile(), "snippets.properties");
        repo(file).save(List.of(new NamedSnippet("a", "1"), new NamedSnippet("b", "2")));
        // Recargado con otra instancia del repositorio en el mismo archivo:
        assertEquals(2, repo(file).load().size());
    }

    @Test
    void overwriteReplacesPreviousSnippets() {
        File file = new File(tmp.toFile(), "snippets.properties");
        SnippetRepository repo = repo(file);
        repo.save(List.of(new NamedSnippet("a", "1"), new NamedSnippet("b", "2")));
        repo.save(List.of(new NamedSnippet("c", "3")));

        List<NamedSnippet> loaded = repo.load();
        assertEquals(1, loaded.size());
        assertEquals("c", loaded.get(0).name());
        assertNotEquals("a", loaded.get(0).name());
    }
}