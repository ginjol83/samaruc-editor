package com.retroeditor.service.git;

import static com.retroeditor.service.git.GitModels.GitBranchInfo;
import static com.retroeditor.service.git.GitModels.GitCommitInfo;
import static com.retroeditor.service.git.GitModels.GitStatusEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquestador de operaciones git locales sobre un directorio: init, status, rama, add,
 * commit y log. Todas las llamadas lanzan {@link GitOperationsException} en caso de error.
 */
public class GitService {

    private final GitCli cli;

    public GitService() {
        this(new GitCli());
    }

    GitService(GitCli cli) {
        this.cli = cli;
    }

    public static boolean isGitAvailable() {
        return GitCli.isGitAvailable();
    }

    /** Indica si el directorio (o padre) contiene un repositorio git (.git). */
    public boolean isRepository(Path dir) {
        return dir != null && Files.exists(dir.resolve(".git"));
    }

    /** Inicializa un repositorio en el directorio indicado. */
    public void init(Path dir) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "init");
        requireSuccess(result, "git init");
    }

    /** Nombre de la rama actual, o {@code Optional.empty()} si no hay branch (detached HEAD). */
    public GitBranchInfo currentBranch(Path dir) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "branch", "--show-current");
        requireSuccess(result, "git branch --show-current");
        String line = result.output().stream().findFirst().orElse("");
        return GitBranchInfo.of(line);
    }

    /** Lista de cambios según {@code git status --porcelain} (con -uall para no agregar directorios). */
    public List<GitStatusEntry> status(Path dir) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "status", "--porcelain", "-uall");
        requireSuccess(result, "git status --porcelain");
        return parseStatus(result.output());
    }

    /** Conjunto de rutas relativas con cambios (para marcadores de pestañas). */
    public Set<String> dirtyPaths(Path dir) throws GitOperationsException {
        return status(dir).stream()
            .map(GitStatusEntry::path)
            .collect(Collectors.toSet());
    }

    /** Añade todos los cambios del directorio de trabajo y del índice. */
    public void addAll(Path dir) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "add", "-A");
        requireSuccess(result, "git add -A");
    }

    /** Crea un commit con el mensaje indicado (el trabajo debe estar staged antes). */
    public void commit(Path dir, String message) throws GitOperationsException {
        if (message == null || message.isBlank()) {
            throw new GitOperationsException("El mensaje del commit no puede estar vacío");
        }
        GitCli.GitResult result = cli.execute(dir, "commit", "-m", message.trim());
        requireSuccess(result, "git commit");
    }

    /** Historial de commits ({@code git log --oneline}). */
    public List<GitCommitInfo> log(Path dir, int maxCount) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "log", "--oneline", "-n", Integer.toString(Math.max(1, maxCount)));
        requireSuccess(result, "git log");
        List<GitCommitInfo> commits = new ArrayList<>();
        for (String line : result.output()) {
            int space = line.indexOf(' ');
            String hash = space > 0 ? line.substring(0, space) : line;
            String subject = space > 0 ? line.substring(space + 1).trim() : "";
            commits.add(new GitCommitInfo(hash, subject));
        }
        return commits;
    }

    /** Resumen estadístico de un diff ({@code git diff --stat}). */
    public List<String> diffStat(Path dir, boolean staged) throws GitOperationsException {
        List<String> args = new ArrayList<>();
        args.add("diff");
        args.add("--stat");
        if (staged) {
            args.add("--cached");
        }
        GitCli.GitResult result = cli.execute(dir, args.toArray(new String[0]));
        requireSuccess(result, "git diff --stat");
        return result.output();
    }

    // === Operaciones remotas (Fase 2: push/pull/clone con credencial efímera) ===

    /** Credencial efímera para operaciones remotas HTTPS. {@code null} = sin autenticación. */
    public record GitCredentials(String token) {

        public boolean hasToken() {
            return token != null && !token.isBlank();
        }
    }

    /** Indica si existe un remoto con el nombre dado. */
    public boolean hasRemote(Path dir, String name) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "remote");
        requireSuccess(result, "git remote");
        return result.output().stream().anyMatch(name::equals);
    }

    /** URL del remoto indicado, o {@code Optional.empty()} si no existe. */
    public Optional<String> remoteUrl(Path dir, String name) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "remote", "get-url", name);
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        return result.output().stream().findFirst();
    }

    /** Añade un remoto; si ya existe, actualiza su URL. */
    public void addRemote(Path dir, String name, String url) throws GitOperationsException {
        boolean exists = hasRemote(dir, name);
        GitCli.GitResult result = exists
            ? cli.execute(dir, "remote", "set-url", name, url)
            : cli.execute(dir, "remote", "add", name, url);
        requireSuccess(result, "git remote " + (exists ? "set-url " : "add ") + name);
    }

    /**
     * Publica la rama local en el remoto ({@code git push -u remote branch}).
     * Si {@code credentials} incluye token HTTPS, se pasa como {@code http.extraHeader}
     * efímero (nunca se embebe en la URL ni en {@code .git/config}).
     */
    public void push(Path dir, String remote, String branch, GitCredentials credentials) throws GitOperationsException {
        List<String> args = remoteAuthPrefix(credentials);
        args.add("push");
        args.add("-u");
        args.add(remote);
        args.add(branch);
        GitCli.GitResult result = cli.execute(dir, args.toArray(new String[0]));
        requireSuccess(result, "git push");
    }

    /** Trae cambios del remoto y los integra en la rama actual ({@code git pull remote branch}). */
    public void pull(Path dir, String remote, String branch, GitCredentials credentials) throws GitOperationsException {
        List<String> args = remoteAuthPrefix(credentials);
        args.add("pull");
        args.add("--ff-only");
        args.add(remote);
        args.add(branch);
        GitCli.GitResult result = cli.execute(dir, args.toArray(new String[0]));
        requireSuccess(result, "git pull");
    }

    /** Clona el remoto en {@code parent/dirName} y devuelve la ruta creada. */
    public Path clone(Path parent, String url, String dirName, GitCredentials credentials) throws GitOperationsException {
        Path target = parent.resolve(dirName);
        if (Files.exists(target)) {
            throw new GitOperationsException("El directorio ya existe: " + target);
        }
        List<String> args = remoteAuthPrefix(credentials);
        args.add("clone");
        args.add(url);
        args.add(target.toString());
        GitCli.GitResult result = cli.execute(parent, args.toArray(new String[0]));
        requireSuccess(result, "git clone");
        return target;
    }

    /** Email de usuario configurado (local o global), o {@code Optional.empty()} si no lo hay. */
    public Optional<String> userEmail(Path dir) throws GitOperationsException {
        GitCli.GitResult result = cli.execute(dir, "config", "user.email");
        return result.isSuccess() ? result.output().stream().findFirst() : Optional.empty();
    }

    /** Configura la identidad del usuario a nivel local (repo). */
    public void setLocalUser(Path dir, String name, String email) throws GitOperationsException {
        requireSuccess(cli.execute(dir, "config", "user.name", name), "git config user.name");
        requireSuccess(cli.execute(dir, "config", "user.email", email), "git config user.email");
    }

    private List<String> remoteAuthPrefix(GitCredentials credentials) {
        List<String> args = new ArrayList<>();
        if (credentials != null && credentials.hasToken()) {
            args.add("-c");
            args.add("http.extraHeader=Authorization: token " + credentials.token());
        }
        return args;
    }

    static List<GitStatusEntry> parseStatus(List<String> lines) {
        List<GitStatusEntry> entries = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (line.startsWith("??")) {
                entries.add(new GitStatusEntry('?', '?', line.substring(2).trim()));
            } else if (line.length() >= 3) {
                entries.add(new GitStatusEntry(line.charAt(0), line.charAt(1), line.substring(3).trim()));
            }
        }
        return entries;
    }

    private void requireSuccess(GitCli.GitResult result, String operation) throws GitOperationsException {
        if (result == null || !result.isSuccess()) {
            String detail = result != null && !result.errorLines().isBlank()
                ? result.errorLines()
                : (result != null ? result.outputLines() : "");
            throw new GitOperationsException(
                "Falló '" + operation + "'" + (detail.isBlank() ? "" : ": " + detail));
        }
    }
}