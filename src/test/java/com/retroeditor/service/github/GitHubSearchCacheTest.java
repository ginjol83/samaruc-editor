package com.retroeditor.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class GitHubSearchCacheTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private GitHubSearchCache cacheAt(Path file) {
        return new GitHubSearchCache(mapper, file);
    }

    private GitHubRepo repo(String name, int stars) {
        return new GitHubRepo(1L, name, "alguien", "alguien/" + name,
            "desc " + name, stars, "C", "https://github.com/alguien/" + name,
            null, "main", LocalDateTime.of(2026, 1, 1, 12, 0));
    }

    @Test
    void putThenGetReturnsSameRepos() throws Exception {
        Path file = tempDir.resolve("cache.json");
        GitHubSearchCache cache = cacheAt(file);

        List<GitHubRepo> repos = List.of(repo("proyecto-uno", 5), repo("proyecto-dos", 3));
        assertFalse(cache.get("gameboy").isPresent(), "antes de guardar no hay caché");

        cache.put("gameboy", repos);
        cache.put("z80", List.of(repo("otro", 1)));

        Optional<List<GitHubRepo>> hit = cache.get("gameboy");
        assertTrue(hit.isPresent(), "debe recuperarse la consulta guardada");
        assertEquals(2, hit.get().size());
        assertEquals("proyecto-uno", hit.get().get(0).name());
        assertEquals("proyecto-dos", hit.get().get(1).name());
        assertEquals(5, hit.get().get(0).stars());
        assertEquals("main", hit.get().get(0).defaultBranch());
        assertEquals(LocalDateTime.of(2026, 1, 1, 12, 0), hit.get().get(0).updatedAt());

        assertTrue(cache.get("z80").isPresent());
        assertFalse(cache.get("desconocida").isPresent(), "consultas sin guardar no devuelven caché");
    }

    @Test
    void putReplacesEntryForSameQuery() throws Exception {
        Path file = tempDir.resolve("cache.json");
        GitHubSearchCache cache = cacheAt(file);

        cache.put("gb", List.of(repo("a", 1)));
        cache.put("gb", List.of(repo("b", 2), repo("c", 3)));

        Optional<List<GitHubRepo>> hit = cache.get("gb");
        assertTrue(hit.isPresent());
        assertEquals(2, hit.get().size(), "la última escritura reemplaza a la anterior");
        assertEquals("b", hit.get().get(0).name());
    }

    @Test
    void expiredEntriesAreNotReturned() throws Exception {
        Path file = tempDir.resolve("cache.json");
        // Se simula una entrada caducada escribiendo un archivo con savedAt antiguo.
        GitHubSearchCache cache = cacheAt(file);
        cache.put("vieja", List.of(repo("x", 1)));

        // Reescribir el savedAt a un valor antiguo (1 ms = caducado frente a now).
        String json = Files.readString(file)
            .replaceFirst("\"savedAt\" : [0-9]+", "\"savedAt\" : 1");
        Files.writeString(file, json);

        assertFalse(cache.get("vieja").isPresent(), "una entrada caducada no debe devolverse");
    }

    @Test
    void clearRemovesCacheFile() throws Exception {
        Path file = tempDir.resolve("cache.json");
        GitHubSearchCache cache = cacheAt(file);

        cache.put("gb", List.of(repo("a", 1)));
        assertTrue(Files.isRegularFile(file));
        cache.clear();
        assertFalse(Files.exists(file));
        assertFalse(cache.get("gb").isPresent());
    }

    @Test
    void toJsonRoundTripsThroughParseRepoNode() {
        GitHubRepo original = repo("prueba", 42);
        GitHubRepo parsed = GitHubApiService.parseRepoNode(GitHubSearchCache.toJson(original));
        assertEquals(original, parsed);
    }
}