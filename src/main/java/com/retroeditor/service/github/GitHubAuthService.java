package com.retroeditor.service.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.retroeditor.service.github.GitHubApiService.RateLimitException;

/**
 * Autenticación de GitHub con cascade de detección del token:
 * <ol>
 *   <li>CLI {@code gh}: {@code gh auth token} (si el usuario ha hecho login con GitHub CLI).</li>
 *   <li>Variable de entorno {@code SAMARUC_GITHUB_TOKEN}.</li>
 *   <li>Token guardado en Ajustes (clave {@code github.token} de la configuración).</li>
 * </ol>
 *
 * <p>Nunca almacena ni devuelve más token del necesario: lo resuelve bajo demanda y lo
 * pasa a las operaciones git/API como credencial efímera.</p>
 */
public class GitHubAuthService {

    public static final String TOKEN_ENV = "SAMARUC_GITHUB_TOKEN";
    public static final String CONFIG_KEY = "github.token";
    public static final String SOURCE_GH = "gh";
    public static final String SOURCE_ENV = "env";
    public static final String SOURCE_CONFIG = "settings";

    private static final String API_BASE = "https://api.github.com";
    private static final String SCOPES_HEADER = "x-oauth-scopes";

    /** Token resuelto y el origen de donde salió. */
    public record ResolvedToken(String token, String source) {

        public boolean isPresent() {
            return token != null && !token.isBlank();
        }
    }

    /** Resultado de validar un token contra {@code GET /user}. */
    public record AuthResult(String login, List<String> scopes) {}

    /** Lee el token guardado en Ajustes. Devuelve {@code null} si no hay ninguno. */
    @FunctionalInterface
    public interface TokenStore {
        String getToken();
    }

    private final HttpClient httpClient;
    private final String apiBase;
    private final TokenStore tokenStore;
    private final Supplier<Optional<String>> ghTokenProvider;

    public GitHubAuthService(TokenStore tokenStore) {
        this(
            tokenStore,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            API_BASE,
            GitHubAuthService::readGhAuthToken);
    }

    GitHubAuthService(TokenStore tokenStore, HttpClient httpClient,
                      String apiBase, Supplier<Optional<String>> ghTokenProvider) {
        this.tokenStore = tokenStore;
        this.httpClient = httpClient;
        this.apiBase = apiBase;
        this.ghTokenProvider = ghTokenProvider;
    }

    /**
     * Resuelve el primer token disponible siguiendo la cascade gh → env → Ajustes.
     *
     * @return token y su origen, o {@code ResolvedToken} vacío si no hay autenticación.
     */
    public ResolvedToken resolveToken() {
        Optional<String> ghToken = ghTokenProvider != null ? safeGet(ghTokenProvider) : Optional.empty();
        if (ghToken.isPresent()) {
            return new ResolvedToken(ghToken.get(), SOURCE_GH);
        }

        String envToken = System.getenv(TOKEN_ENV);
        if (envToken != null && !envToken.isBlank()) {
            return new ResolvedToken(envToken.trim(), SOURCE_ENV);
        }

        String stored = tokenStore != null ? tokenStore.getToken() : null;
        if (stored != null && !stored.isBlank()) {
            return new ResolvedToken(stored.trim(), SOURCE_CONFIG);
        }

        return new ResolvedToken(null, null);
    }

    /**
     * Valida un token contra {@code GET /user}: devuelve el login y los scopes
     * anunciados por GitHub. Lanza {@link RateLimitException} si se agota el rate
     * limit y {@link IOException} (con mensaje claro) si el token no es válido.
     */
    public AuthResult validate(String token) throws IOException, InterruptedException {
        if (token == null || token.isBlank()) {
            throw new IOException("No hay token de GitHub configurado");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/user"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new IOException("Token de GitHub inválido o revocado (HTTP 401)");
        }
        if (response.statusCode() == 403) {
            throw new RateLimitException("Rate limit de GitHub alcanzado (HTTP 403)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        String login;
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            login = root.path("login").asText("");
        } catch (IOException e) {
            throw new IOException("Respuesta no válida de GitHub: " + e.getMessage(), e);
        }

        List<String> scopes = new ArrayList<>();
        for (String scope : response.headers().allValues(SCOPES_HEADER)) {
            for (String part : scope.split("\\s*,\\s*")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    scopes.add(trimmed);
                }
            }
        }

        return new AuthResult(login, scopes);
    }

    /** Devuelve el token que proporciona la CLI {@code gh auth token} si está disponible. */
    private static Optional<String> readGhAuthToken() {
        try {
            Process process = new ProcessBuilder("gh", "auth", "token").redirectErrorStream(false).start();
            byte[] out = new byte[0];
            try (var in = process.getInputStream()) {
                out = in.readAllBytes();
            } catch (IOException ignored) {
                return Optional.empty();
            }
            drain(process.getErrorStream());
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            String line = new String(out, java.nio.charset.StandardCharsets.UTF_8).trim();
            return line.isEmpty() ? Optional.empty() : Optional.of(line);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static void drain(java.io.InputStream stream) {
        try (stream) {
            stream.readAllBytes();
        } catch (IOException ignored) {
            // el proceso puede cerrar el flujo antes de tiempo
        }
    }

    private static Optional<String> safeGet(Supplier<Optional<String>> provider) {
        try {
            return provider.get();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}