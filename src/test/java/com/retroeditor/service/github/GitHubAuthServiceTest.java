package com.retroeditor.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.retroeditor.service.github.GitHubApiService.RateLimitException;
import com.retroeditor.service.github.GitHubAuthService.AuthResult;
import com.retroeditor.service.github.GitHubAuthService.ResolvedToken;
import com.retroeditor.service.github.GitHubAuthService.TokenStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class GitHubAuthServiceTest {

    private HttpServer server;
    private int port;

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

    private GitHubAuthService newService(TokenStore store, Supplier<Optional<String>> ghProvider) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        return new GitHubAuthService(store, client, apiBase(), ghProvider);
    }

    private String apiBase() {
        return "http://127.0.0.1:" + port;
    }

    private void serve(int statusCode, String body, String oauthScopes) {
        server.createContext("/user", exchange -> {
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (oauthScopes != null) {
                exchange.getResponseHeaders().add("x-oauth-scopes", oauthScopes);
            }
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
    }

    @Test
    void resolveTokenPrefersGhCliOverSettings() {
        GitHubAuthService service = newService(
            () -> "settings-token",
            () -> Optional.of("gh-token"));

        ResolvedToken resolved = service.resolveToken();

        assertTrue(resolved.isPresent());
        assertEquals(GitHubAuthService.SOURCE_GH, resolved.source());
        assertEquals("gh-token", resolved.token());
    }

    @Test
    void resolveTokenFallsBackToSettingsWhenGhAbsent() {
        assumeTrue(System.getenv(GitHubAuthService.TOKEN_ENV) == null,
            "SAMARUC_GITHUB_TOKEN está definida en el entorno; no se puede probar el fallback");

        GitHubAuthService service = newService(
            () -> "settings-token",
            () -> Optional.empty());

        ResolvedToken resolved = service.resolveToken();

        assertTrue(resolved.isPresent());
        assertEquals(GitHubAuthService.SOURCE_CONFIG, resolved.source());
        assertEquals("settings-token", resolved.token());
    }

    @Test
    void resolveTokenReturnsEmptyWhenNothingConfigured() {
        assumeTrue(System.getenv(GitHubAuthService.TOKEN_ENV) == null,
            "SAMARUC_GITHUB_TOKEN está definida en el entorno; no se puede probar el caso vacío");

        GitHubAuthService service = newService(() -> "", () -> Optional.empty());

        ResolvedToken resolved = service.resolveToken();

        assertFalse(resolved.isPresent());
        assertEquals(null, resolved.source());
        assertEquals(null, resolved.token());
    }

    @Test
    void validateReturnsLoginAndScopesOnSuccess() throws Exception {
        serve(200, "{\"login\":\"octocat\"}", "repo, workflow");

        AuthResult result = newService(null, () -> Optional.empty()).validate("token123");

        assertEquals("octocat", result.login());
        assertTrue(result.scopes().contains("repo"));
        assertTrue(result.scopes().contains("workflow"));
    }

    @Test
    void validateThrowsOnInvalidToken() throws Exception {
        serve(401, "{\"message\":\"Bad credentials\"}", null);

        GitHubAuthService service = newService(null, () -> Optional.empty());

        IOException ex = assertThrows(IOException.class, () -> service.validate("malo"));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void validateThrowsRateLimitOn403() throws Exception {
        serve(403, "{\"message\":\"rate limit exceeded\"}", null);

        GitHubAuthService service = newService(null, () -> Optional.empty());

        assertThrows(RateLimitException.class, () -> service.validate("token"));
    }

    @Test
    void validateRejectsBlankToken() {
        GitHubAuthService service = newService(() -> "", () -> Optional.empty());

        assertThrows(IOException.class, () -> service.validate("  "));
    }
}