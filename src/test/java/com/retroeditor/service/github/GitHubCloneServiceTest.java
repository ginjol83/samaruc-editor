package com.retroeditor.service.github;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.service.git.GitService;

class GitHubCloneServiceTest {

    private final GitHubCloneService cloneService = new GitHubCloneService();
    private final GitService git = new GitService();

    @TempDir
    Path tempDir;

    @Test
    void convertToGitInitsRepoWithOrigin() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        Path repoDir = tempDir.resolve("mi-proyecto");
        Files.createDirectories(repoDir);
        Files.write(repoDir.resolve("main.c"), "int main(void) { return 0; }\n".getBytes());

        boolean converted = cloneService.convertToGit(repoDir, "https://github.com/demo/mi-proyecto.git", git);

        assertTrue(converted, "debe poder convertirse");
        assertTrue(git.isRepository(repoDir), "debe existir .git tras la conversión");
        assertTrue(git.hasRemote(repoDir, "origin"), "debe quedar el remoto origin configurado");
        assertTrue(git.remoteUrl(repoDir, "origin").orElse("").contains("demo/mi-proyecto"),
            "la URL del remoto debe ser la indicada");
    }

    @Test
    void convertToGitIsIdempotentOnExistingGitRepo() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        Path repoDir = tempDir.resolve("ya-git");
        Files.createDirectories(repoDir);
        git.init(repoDir);

        assertTrue(cloneService.convertToGit(repoDir, null, git), "un repo ya git debe devolver true sin tocar nada");
        assertTrue(git.isRepository(repoDir));
    }

    @Test
    void convertToGitReturnsFalseForMissingDir() {
        Path missing = tempDir.resolve("no-existe");
        assertFalse(cloneService.convertToGit(missing, null, git));
    }

    @Test
    void convertToGitCommitsOnlyWhenIdentityExists() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        Path repoDir = tempDir.resolve("identidad-condicional");
        Files.createDirectories(repoDir);
        Files.write(repoDir.resolve("a.c"), "int a;\n".getBytes());

        // Con identidad global hay commit inicial; sin ella los archivos quedan sin commitear.
        boolean hasIdentity = git.userEmail(tempDir).isPresent() && git.userName(tempDir).isPresent();

        boolean converted = cloneService.convertToGit(repoDir, null, git);
        assertTrue(converted);
        assertTrue(git.isRepository(repoDir));

        boolean committed = git.status(repoDir).isEmpty();
        if (hasIdentity) {
            assertTrue(committed, "con identidad debe existir commit inicial");
        } else {
            assertFalse(committed, "sin identidad los archivos deben seguir sin commitear");
        }
    }
}