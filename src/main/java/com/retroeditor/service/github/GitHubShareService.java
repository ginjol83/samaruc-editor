package com.retroeditor.service.github;

import java.io.IOException;
import java.nio.file.Path;

import com.retroeditor.service.git.GitService;
import com.retroeditor.service.git.GitOperationsException;

/**
 * Orquesta el flujo "Share to GitHub": crear repositorio remoto, inicializar
 * git si hace falta, configurar remote, hacer commit y push.
 */
public class GitHubShareService {

    public record GitHubShareParams(
        String repoName,
        String description,
        boolean isPrivate,
        String commitMessage,
        String gitUserName,
        String gitUserEmail
    ) {}

    public record ShareResult(String fullName, String htmlUrl, String cloneUrl) {}

    private final GitHubRepoService repoService;
    private final GitService gitService;

    public GitHubShareService() {
        this(new GitHubRepoService(), new GitService());
    }

    GitHubShareService(GitHubRepoService repoService, GitService gitService) {
        this.repoService = repoService;
        this.gitService = gitService;
    }

    /**
     * Crea un repositorio en GitHub y publica el proyecto local.
     *
     * @param dir      directorio del proyecto
     * @param params   parámetros del diálogo de publicación
     * @param token    token de GitHub (scope {@code repo})
     * @param creds    credenciales efímeras para git push HTTPS
     * @return datos del repositorio creado
     * @throws IOException           si la API de GitHub falla o el token no es válido
     * @throws GitOperationsException si alguna operación git falla
     */
    public ShareResult shareProject(Path dir, GitHubShareParams params,
                                    String token, GitService.GitCredentials creds)
            throws IOException, InterruptedException, GitOperationsException {

        if (token == null || token.isBlank()) {
            throw new IOException("No hay token de GitHub configurado. Ve a Ajustes > GitHub.");
        }
        if (params == null || params.repoName() == null || params.repoName().isBlank()) {
            throw new IOException("El nombre del repositorio no puede estar vacío.");
        }

        // 1. Crear repositorio en GitHub
        GitHubRepoService.CreatedRepo created = repoService.createRepository(
            token,
            params.repoName().trim(),
            params.description(),
            params.isPrivate());

        // 2. Inicializar git si no es repositorio
        if (!gitService.isRepository(dir)) {
            gitService.init(dir);
        }

        // 3. Configurar identidad del usuario (local al repo)
        if (params.gitUserName() != null && !params.gitUserName().isBlank()) {
            String email = params.gitUserEmail() != null ? params.gitUserEmail().trim() : "";
            gitService.setLocalUser(dir, params.gitUserName().trim(), email);
        }

        // 4. Añadir remote origin
        gitService.addRemote(dir, "origin", created.cloneUrl());

        // 5. Añadir todos los archivos y commitear
        gitService.addAll(dir);
        String msg = (params.commitMessage() != null && !params.commitMessage().isBlank())
            ? params.commitMessage().trim()
            : "Initial commit from Samaruc Editor";
        gitService.commit(dir, msg);

        // 6. Push a origin
        String branch = gitService.currentBranch(dir).current().orElse("main");
        gitService.push(dir, "origin", branch, creds);

        return new ShareResult(created.fullName(), created.htmlUrl(), created.cloneUrl());
    }
}
