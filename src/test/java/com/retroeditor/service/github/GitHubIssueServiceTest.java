package com.retroeditor.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retroeditor.service.github.GitHubIssueService.Issue;
import com.sun.net.httpserver.HttpServer;

class GitHubIssueServiceTest {

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

    private GitHubIssueService newService() {
        return new GitHubIssueService(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            "http://127.0.0.1:" + port,
            new ObjectMapper());
    }

    @Test
    void listOpenIssuesParsesJsonAndSkipsPullRequests() throws Exception {
        server.createContext("/repos/octocat/hello/issues", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            assertTrue(exchange.getRequestURI().getQuery().contains("state=open"),
                "Debe pedir estado open: " + exchange.getRequestURI().getQuery());
            String body = "["
                + "{\"number\":2,\"title\":\"Bug en sprites\",\"body\":\"se ve roto\","
                + "\"html_url\":\"https://github.com/octocat/hello/issues/2\",\"state\":\"open\","
                + "\"user\":{\"login\":\"octocat\"},\"created_at\":\"2026-01-01T10:00:00Z\"},"
                + "{\"number\":3,\"title\":\"PR no debe contarse\",\"body\":\"\","
                + "\"html_url\":\"https://github.com/octocat/hello/pull/3\",\"state\":\"open\","
                + "\"user\":{\"login\":\"octocat\"},\"created_at\":\"2026-01-02T10:00:00Z\","
                + "\"pull_request\":{\"url\":\"https://github.com/octocat/hello/pull/3\"}}"
                + "]";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });

        List<Issue> issues = newService().listOpenIssues("token123", "octocat", "hello");

        assertEquals(1, issues.size(), "Los PR no deben aparecer como issues");
        Issue issue = issues.get(0);
        assertEquals(2, issue.number());
        assertEquals("Bug en sprites", issue.title());
        assertEquals("octocat", issue.userLogin());
        assertEquals("open", issue.state());
    }

    @Test
    void createIssuePostsJsonAndParsesResult() throws Exception {
        server.createContext("/repos/octocat/hello/issues", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = "{\"number\":4,\"title\":\"Nuevo bug\",\"body\":\"detalle\","
                + "\"html_url\":\"https://github.com/octocat/hello/issues/4\",\"state\":\"open\","
                + "\"user\":{\"login\":\"octocat\"},\"created_at\":\"2026-09-15T10:00:00Z\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });

        Issue created = newService().createIssue("token", "octocat", "hello", "Nuevo bug", "detalle");

        assertEquals(4, created.number());
        assertEquals("Nuevo bug", created.title());

        String payload = lastRequestBody.get();
        assertTrue(payload.contains("\"title\":\"Nuevo bug\""), "Debe incluir el título: " + payload);
        assertTrue(payload.contains("\"body\":\"detalle\""), "Debe incluir el cuerpo: " + payload);
    }

    @Test
    void createIssueThrowsOnBlankTitleOr401() throws Exception {
        server.createContext("/repos/octocat/hello/issues", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });

        GitHubIssueService service = newService();

        IOException noAuth = assertThrows(IOException.class,
            () -> service.createIssue("  ", "o", "r", "t", null));
        assertTrue(noAuth.getMessage().contains("token"));

        IOException noTitle = assertThrows(IOException.class,
            () -> service.createIssue("token", "o", "r", "  ", null));
        assertTrue(noTitle.getMessage().contains("t\u00edtulo"));

        IOException api401 = assertThrows(IOException.class,
            () -> service.createIssue("token", "octocat", "hello", "t", null));
        assertTrue(api401.getMessage().contains("401"));
    }

    @Test
    void listIssuesThrowsOn404() throws Exception {
        server.createContext("/repos/nada/inexistente/issues", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        GitHubIssueService service = newService();

        IOException ex = assertThrows(IOException.class,
            () -> service.listOpenIssues("token", "nada", "inexistente"));
        assertTrue(ex.getMessage().contains("404"));
    }
}