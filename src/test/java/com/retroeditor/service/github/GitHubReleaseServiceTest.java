package com.retroeditor.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retroeditor.service.github.GitHubReleaseService.NewRelease;
import com.retroeditor.service.github.GitHubReleaseService.OwnerRepo;
import com.retroeditor.service.github.GitHubReleaseService.ReleaseCreated;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class GitHubReleaseServiceTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastAssetName = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicReference<byte[]> lastAssetBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String apiBase() {
        return "http://127.0.0.1:" + port;
    }

    private GitHubReleaseService newService() {
        return new GitHubReleaseService(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            apiBase(),
            new ObjectMapper());
    }

    private void serveRelease(int statusCode, String body, String uploadUrl) {
        server.createContext("/repos/octocat/mi-juego/releases", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String json = body;
            if (statusCode >= 200 && statusCode < 300 && uploadUrl != null) {
                String cleanUp = json.replace("%UPLOAD_URL%", uploadUrl);
                json = cleanUp.replace("\\{", "{").replace("\\}", "}");
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
    }

    @Test
    void createReleasePostsJsonAndParsesUploadUrl() throws Exception {
        serveRelease(201,
            "{\"tag_name\":\"v1.0.0\",\"html_url\":\"https://github.com/octocat/mi-juego/releases/tag/v1.0.0\","
            + "\"upload_url\":\"https://api.example.com/upload%UPLOAD_URL%\"}",
            "{?name,label}");

        ReleaseCreated created = newService().createRelease("token123", "octocat", "mi-juego",
            new NewRelease("v1.0.0", "Mi Juego 1.0", "Primera versión", false, false));

        assertEquals("v1.0.0", created.tagName());
        assertTrue(created.htmlUrl().endsWith("/releases/tag/v1.0.0"));
        assertTrue(created.uploadUrl().contains("upload"));

        String body = lastRequestBody.get();
        assertTrue(body.contains("\"tag_name\":\"v1.0.0\""), "El cuerpo debe incluir el tag: " + body);
        assertTrue(body.contains("\"name\":\"Mi Juego 1.0\""), "El cuerpo debe incluir el nombre: " + body);
        assertTrue(body.contains("\"draft\":false"), "El cuerpo debe incluir draft: " + body);
    }

    @Test
    void createReleaseThrowsOn422WhenTagExists() throws Exception {
        serveRelease(422, "{\"message\":\"Validation Failed\"}", null);

        GitHubReleaseService service = newService();

        IOException ex = assertThrows(IOException.class,
            () -> service.createRelease("token", "octocat", "mi-juego",
                new NewRelease("v1.0.0", null, null, false, false)));
        assertTrue(ex.getMessage().contains("422"));
        assertTrue(ex.getMessage().contains("v1.0.0"));
    }

    @Test
    void createReleaseThrowsOn401InvalidToken() throws Exception {
        serveRelease(401, "{\"message\":\"Bad credentials\"}", null);

        GitHubReleaseService service = newService();

        IOException ex = assertThrows(IOException.class,
            () -> service.createRelease("malo", "octocat", "mi-juego",
                new NewRelease("v9.9.9", null, null, false, false)));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void createReleaseRejectsBlankTokenOrTag() {
        GitHubReleaseService service = newService();

        assertThrows(IOException.class, () -> service.createRelease("  ", "o", "r",
            new NewRelease("v1", null, null, false, false)));
        assertThrows(IOException.class, () -> service.createRelease("token", "o", "r",
            new NewRelease("  ", null, null, false, false)));
        assertThrows(IOException.class, () -> service.createRelease("token", "  ", "r",
            new NewRelease("v1", null, null, false, false)));
    }

    @Test
    void uploadAssetPostsFileWithNameAndContentType() throws Exception {
        server.createContext("/upload", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            lastAssetName.set(exchange.getRequestURI().getQuery());
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            lastAssetBody.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        Path temp = Files.createTempFile("rom", ".gb");
        Files.write(temp, new byte[] { 1, 2, 3, 4 });

        newService().uploadAsset("token123", "http://127.0.0.1:" + port + "/upload{?name,label}", temp);

        assertTrue(lastAssetName.get().contains("name="), "Debe incluir ?name=: " + lastAssetName.get());
        assertTrue(lastAssetName.get().contains("rom"), "Debe incluir el nombre del asset");
        assertEquals("application/octet-stream", lastContentType.get());
        assertEquals(4, lastAssetBody.get().length);

        Files.deleteIfExists(temp);
    }

    @Test
    void uploadAssetZipUsesZipContentType() throws Exception {
        server.createContext("/upload", exchange -> {
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        Path temp = Files.createTempFile("assets", ".zip");
        Files.write(temp, new byte[] { 0x50, 0x4b });

        newService().uploadAsset("token", "http://127.0.0.1:" + port + "/upload?name=x.zip", temp);
        assertEquals("application/zip", lastContentType.get());

        Files.deleteIfExists(temp);
    }

    @Test
    void uploadAssetThrowsOn422DuplicateName() throws Exception {
        server.createContext("/upload", exchange -> {
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
        });

        Path temp = Files.createTempFile("dup", ".gb");

        IOException ex = assertThrows(IOException.class,
            () -> newService().uploadAsset("token", "http://127.0.0.1:" + port + "/upload", temp));
        assertTrue(ex.getMessage().contains("422"));

        Files.deleteIfExists(temp);
    }

    @Test
    void stripUploadTemplateRemovesTrailingBraces() {
        assertEquals("https://api.example.com/repos/o/r/releases/1/assets",
            GitHubReleaseService.stripUploadTemplate(
                "https://api.example.com/repos/o/r/releases/1/assets{?name,label}"));
        assertEquals("https://api.example.com/assets",
            GitHubReleaseService.stripUploadTemplate("https://api.example.com/assets"));
        assertEquals("", GitHubReleaseService.stripUploadTemplate(null));
    }

    @Test
    void parseOwnerRepoSupportsHttpsAndSshAndScp() {
        assertFalse(GitHubReleaseService.parseOwnerRepo(null).isPresent());
        assertFalse(GitHubReleaseService.parseOwnerRepo("https://example.com/sin-repo").isPresent());

        OwnerRepo https = GitHubReleaseService.parseOwnerRepo("https://github.com/octocat/Hello-World.git").orElseThrow();
        assertEquals("octocat", https.owner());
        assertEquals("Hello-World", https.repo());

        OwnerRepo ssh = GitHubReleaseService.parseOwnerRepo("git@github.com:octocat/hello.git").orElseThrow();
        assertEquals("octocat", ssh.owner());
        assertEquals("hello", ssh.repo());

        OwnerRepo scp = GitHubReleaseService.parseOwnerRepo("ssh://git@github.com/org/project").orElseThrow();
        assertEquals("org", scp.owner());
        assertEquals("project", scp.repo());
    }
}