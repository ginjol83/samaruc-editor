package com.retroeditor.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retroeditor.service.github.GitHubRepoService.CreatedRepo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class GitHubRepoServiceTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

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

    private GitHubRepoService newService() {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        return new GitHubRepoService(client, apiBase(), new ObjectMapper());
    }

    private String apiBase() {
        return "http://127.0.0.1:" + port;
    }

    private void servePost(int statusCode, String body) {
        server.createContext("/user/repos", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
    }

    @Test
    void createRepositoryPostsJsonAndParsesCreatedRepo() throws Exception {
        servePost(201,
            "{\"name\":\"mi-juego\",\"full_name\":\"octocat/mi-juego\","
            + "\"html_url\":\"https://github.com/octocat/mi-juego\","
            + "\"clone_url\":\"https://github.com/octocat/mi-juego.git\"}");

        CreatedRepo repo = newService().createRepository("token123", "mi-juego", "Un juego retro", false);

        assertEquals("mi-juego", repo.name());
        assertEquals("octocat/mi-juego", repo.fullName());
        assertEquals("https://github.com/octocat/mi-juego", repo.htmlUrl());
        assertTrue(repo.cloneUrl().endsWith(".git"));

        String body = lastRequestBody.get();
        assertTrue(body.contains("\"name\":\"mi-juego\""), "El cuerpo debe incluir el nombre: " + body);
        assertTrue(body.contains("\"private\":false"), "El cuerpo debe incluir la visibilidad: " + body);
        assertTrue(body.contains("\"description\":\"Un juego retro\""), "El cuerpo debe incluir la descripción: " + body);
    }

    @Test
    void createRepositoryThrowsOn401() throws Exception {
        servePost(401, "{\"message\":\"Bad credentials\"}");

        GitHubRepoService service = newService();

        IOException ex = assertThrows(IOException.class, () -> service.createRepository("malo", "x", null, false));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void createRepositoryThrowsOn422WhenNameExists() throws Exception {
        servePost(422, "{\"message\":\"Repository creation failed.\"}");

        GitHubRepoService service = newService();

        IOException ex = assertThrows(IOException.class, () -> service.createRepository("token", "existing", null, false));
        assertTrue(ex.getMessage().contains("422"));
    }

    @Test
    void createRepositoryThrowsOn403WithoutRepoScope() throws Exception {
        servePost(403, "{\"message\":\"Resource not accessible by integration\"}");

        GitHubRepoService service = newService();

        IOException ex = assertThrows(IOException.class, () -> service.createRepository("token", "x", null, false));
        assertTrue(ex.getMessage().contains("403"));
        assertTrue(ex.getMessage().contains("repo"));
    }

    @Test
    void createRepositoryRejectsBlankTokenOrName() {
        GitHubRepoService service = newService();

        assertThrows(IOException.class, () -> service.createRepository("  ", "x", null, false));
        assertThrows(IOException.class, () -> service.createRepository("token", "  ", null, false));
    }
}