package com.retroeditor.service.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.service.git.GitModels.GitCommitInfo;
import com.retroeditor.service.git.GitModels.GitStatusEntry;

class GitServiceTest {

    private final GitService service = new GitService();

    @TempDir
    Path tempDir;

    @Test
    void parseStatusHandlesPorcelainV1Formats() {
        List<GitStatusEntry> entries = GitService.parseStatus(List.of(
            "?? nuevo.c",
            " M modificado.c",
            "M  indexado.c",
            "MM ambos.c",
            " D borrado.c",
            "R  viejo.c -> nuevo.c"
        ));

        assertEquals(6, entries.size());
        assertTrue(entries.get(0).isUntracked());
        assertEquals("nuevo.c", entries.get(0).path());
        assertEquals("??", entries.get(0).shortStatus());
        assertEquals(' ', entries.get(1).indexStatus());
        assertEquals('M', entries.get(1).workTreeStatus());
        assertEquals("M ", entries.get(2).shortStatus());
        assertEquals("modificado.c", entries.get(1).path());
        assertEquals("viejo.c -> nuevo.c", entries.get(5).path());
    }

    @Test
    void isRepositoryDetectsGitDirectory() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        Path dir = tempDir.resolve("proyecto");
        Files.createDirectory(dir);
        assertFalse(service.isRepository(dir));

        service.init(dir);
        assertTrue(service.isRepository(dir));
    }

    @Test
    void addCommitStatusLogRoundTrip() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        Path dir = tempDir.resolve("proyecto");
        Files.createDirectory(dir);
        service.init(dir);

        // Identidad local para que el commit no dependa de la config global del equipo
        GitCli cli = new GitCli();
        cli.execute(dir, "config", "user.email", "test@samaruc.local");
        cli.execute(dir, "config", "user.name", "Samaruc Test");

        Files.write(dir.resolve("src.c"), "int main(void) { return 0; }\n".getBytes());
        Files.createDirectory(dir.resolve("sub"));
        Files.write(dir.resolve("sub").resolve("aux.c"), "int aux = 1;\n".getBytes());

        // Status con cambios sin trackear (untracked)
        Set<String> dirtyBefore = service.dirtyPaths(dir);
        assertTrue(dirtyBefore.contains("src.c"));
        assertTrue(dirtyBefore.contains("sub/aux.c"));

        service.addAll(dir);
        service.commit(dir, "Primer commit");

        // Tras el commit, el status queda limpio
        assertTrue(service.status(dir).isEmpty());
        assertTrue(service.dirtyPaths(dir).isEmpty());

        // El historial contiene un commit con hash corto y asunto
        List<GitCommitInfo> log = service.log(dir, 10);
        assertEquals(1, log.size());
        assertEquals("Primer commit", log.get(0).subject());
        assertEquals(7, log.get(0).shortHash().length());

        // Una modificación posterior vuelve a aparecer en status
        Files.write(dir.resolve("src.c"), "int main(void) { return 1; }\n".getBytes());
        List<GitStatusEntry> status = service.status(dir);
        assertEquals(1, status.size());
        assertEquals("src.c", status.get(0).path());
        assertEquals(' ', status.get(0).indexStatus());
        assertEquals('M', status.get(0).workTreeStatus());
    }

    @Test
    void commitThrowsWithoutMessage() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        Path dir = tempDir.resolve("proyecto");
        Files.createDirectory(dir);
        service.init(dir);

        GitOperationsException ex = org.junit.jupiter.api.Assertions.assertThrows(
            GitOperationsException.class,
            () -> service.commit(dir, "   ")
        );
        assertTrue(ex.getMessage().contains("vacío"));
    }

    @Test
    void remoteAddPushClonePullRoundTrip() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        // Repo de origen con un commit
        Path work = tempDir.resolve("work");
        Files.createDirectory(work);
        service.init(work);
        GitCli cli = new GitCli();
        cli.execute(work, "config", "user.email", "test@samaruc.local");
        cli.execute(work, "config", "user.name", "Samaruc Test");

        Files.write(work.resolve("a.txt"), "hola\n".getBytes());
        service.addAll(work);
        service.commit(work, "primer commit");
        String branch = service.currentBranch(work).current().orElse("master");

        // Repo desnudo como remoto local (file://, sin red)
        Path bare = tempDir.resolve("bare.git");
        cli.execute(tempDir, "init", "--bare", bare.getFileName().toString());
        assertTrue(Files.isDirectory(bare));
        String fileUrl = bare.toUri().toString();

        // remote add (+ idempotencia con set-url)
        assertFalse(service.hasRemote(work, "origin"));
        service.addRemote(work, "origin", fileUrl);
        assertTrue(service.hasRemote(work, "origin"));
        assertEquals(fileUrl, service.remoteUrl(work, "origin").orElse(""));
        service.addRemote(work, "origin", fileUrl);

        // push + clone desde el remoto
        service.push(work, "origin", branch, null);
        Path cloned = service.clone(tempDir, fileUrl, "clone", null);
        assertTrue(Files.isDirectory(cloned.resolve(".git")));
        assertTrue(Files.exists(cloned.resolve("a.txt")));
        assertEquals("primer commit", service.log(cloned, 1).get(0).subject());

        // un commit más en el clon y push
        cli.execute(cloned, "config", "user.email", "test@samaruc.local");
        cli.execute(cloned, "config", "user.name", "Samaruc Test");
        Files.write(cloned.resolve("a.txt"), "hola desde clon\n".getBytes());
        service.addAll(cloned);
        service.commit(cloned, "commit desde clon");
        service.push(cloned, "origin", branch, null);

        // pull (fast-forward) en el origen
        service.pull(work, "origin", branch, null);
        assertTrue(Files.readString(work.resolve("a.txt")).contains("hola desde clon"));

        // un commit más en work y pull en el clon
        Files.write(work.resolve("a.txt"), "hola de vuelta\n".getBytes());
        service.addAll(work);
        service.commit(work, "commit local");
        service.push(work, "origin", branch, null);
        service.pull(cloned, "origin", branch, null);
        assertTrue(Files.readString(cloned.resolve("a.txt")).contains("hola de vuelta"));
    }

    @Test
    void cloneThrowsWhenTargetExists() throws Exception {
        assumeTrue(GitService.isGitAvailable(), "git no está instalado en este equipo, se omite el test");

        Path existing = tempDir.resolve("ocupado");
        Files.createDirectory(existing);

        GitOperationsException ex = org.junit.jupiter.api.Assertions.assertThrows(
            GitOperationsException.class,
            () -> service.clone(tempDir, "file:///inexistente", "ocupado", null)
        );
        assertTrue(ex.getMessage().contains("ya existe"));
    }
}