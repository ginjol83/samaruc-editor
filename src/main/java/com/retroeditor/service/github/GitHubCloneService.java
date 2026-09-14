package com.retroeditor.service.github;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Descarga repositorios de GitHub como ZIP y los extrae en el workspace.
 */
public class GitHubCloneService {

    private static final String GITHUB_REPOS_DIR = ".samaruc-editor/github-repos";
    private static final int BUFFER_SIZE = 8192;

    private final HttpClient httpClient;

    public GitHubCloneService() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
    }

    GitHubCloneService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Descarga y extrae un repositorio de GitHub.
     *
     * @param repo           Repositorio a clonar
     * @param baseDir        Directorio base donde crear la carpeta del proyecto
     * @param progressCallback (bytesDescargados, bytesTotales) - puede ser null
     * @return Path del directorio extraído
     */
    public Path cloneRepo(GitHubRepo repo, Path baseDir,
                          BiConsumer<Long, Long> progressCallback) throws IOException, InterruptedException {

        Path repoDir = baseDir.resolve(repo.name());
        if (Files.exists(repoDir)) {
            throw new IOException("El directorio ya existe: " + repoDir);
        }

        Files.createDirectories(baseDir);

        String zipUrl = repo.getZipUrl();
        HttpRequest request = HttpRequest.newBuilder(URI.create(zipUrl))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "Samaruc-Editor")
            .GET()
            .build();

        HttpResponse<InputStream> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Error descargando repo: HTTP " + response.statusCode());
        }

        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);

        Path tempZip = Files.createTempFile(baseDir, "samaruc-clone-", ".zip");
        try {
            // Descargar ZIP
            long downloaded = 0;
            try (InputStream in = response.body();
                 FileOutputStream fos = new FileOutputStream(tempZip.toFile(), true)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;
                    if (progressCallback != null) {
                        progressCallback.accept(downloaded, totalBytes);
                    }
                }
            }

            // Extraer ZIP
            extractZip(tempZip, baseDir, repo.name());
        } finally {
            Files.deleteIfExists(tempZip);
        }

        // Verificar que se creó el directorio
        if (!Files.isDirectory(repoDir)) {
            throw new IOException("No se pudo extraer el repositorio a: " + repoDir);
        }

        return repoDir;
    }

    /**
     * Extrae un archivo ZIP en un directorio destino.
     * Maneja el prefijo de directorio raíz que GitHub añade (repo-name-main/).
     */
    private void extractZip(Path zipPath, Path destDir, String repoName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            String rootPrefix = null;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Detectar el prefijo del directorio raíz (ej: "repo-name-main/")
                if (rootPrefix == null && entryName.contains("/")) {
                    rootPrefix = entryName.substring(0, entryName.indexOf('/') + 1);
                }

                // Quitar el prefijo del directorio raíz
                if (rootPrefix != null && entryName.startsWith(rootPrefix)) {
                    entryName = entryName.substring(rootPrefix.length());
                }

                if (entryName.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                Path targetPath = destDir.resolve(entryName);

                // Prevenir path traversal
                if (!targetPath.normalize().startsWith(destDir.normalize())) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    /**
     * Devuelve el directorio base para repos clonados.
     */
    public Path getCloneBaseDirectory() {
        return Path.of(System.getProperty("user.home"), GITHUB_REPOS_DIR);
    }

    /**
     * Lista los repos ya clonados en el directorio base.
     */
    public List<Path> listClonedRepos() {
        Path base = getCloneBaseDirectory();
        List<Path> repos = new ArrayList<>();
        if (!Files.isDirectory(base)) return repos;

        try (var stream = Files.list(base)) {
            stream.filter(Files::isDirectory).sorted().forEach(repos::add);
        } catch (IOException ignored) { }

        return repos;
    }

    /**
     * Elimina un repositorio clonado.
     */
    public void deleteClonedRepo(Path repoDir) throws IOException {
        if (repoDir != null && Files.isDirectory(repoDir)) {
            deleteRecursively(repoDir);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }
}
