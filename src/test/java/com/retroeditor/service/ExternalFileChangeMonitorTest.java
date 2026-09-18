package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalFileChangeMonitorTest {

    @TempDir
    Path tempDir;

    private static Map<File, String> registered(File file, String content) {
        Map<File, String> m = new HashMap<>();
        m.put(file, ExternalFileChangeService.fingerprint(content));
        return m;
    }

    @Test
    void unchangedFileProducesNoNotification() throws IOException {
        Path p = tempDir.resolve("main.c");
        Files.writeString(p, "void main() {}\n", StandardCharsets.UTF_8);
        ExternalFileChangeMonitor monitor = new ExternalFileChangeMonitor();

        List<File> detected = monitor.refresh(registered(p.toFile(), "void main() {}\n"));

        Assertions.assertTrue(detected.isEmpty());
        Assertions.assertTrue(monitor.currentExternalChanges().isEmpty());
    }

    @Test
    void externalModificationIsReportedOnceUntilResolved() throws IOException {
        Path p = tempDir.resolve("main.c");
        Files.writeString(p, "int main() { return 0; }\n", StandardCharsets.UTF_8);
        ExternalFileChangeMonitor monitor = new ExternalFileChangeMonitor();
        String baseline = ExternalFileChangeService.fingerprint("int main() { return 0; }\n");

        Files.writeString(p, "int main() { return 1; }\n", StandardCharsets.UTF_8);

        List<File> first = monitor.refresh(registered(p.toFile(), baseline));
        Assertions.assertEquals(List.of(p.toFile()), first);
        Assertions.assertTrue(monitor.currentExternalChanges().contains(p.toFile()));

        List<File> second = monitor.refresh(registered(p.toFile(), baseline));
        Assertions.assertTrue(second.isEmpty(), "el mismo contenido de disco no debe re-notificar");
        Assertions.assertTrue(monitor.currentExternalChanges().contains(p.toFile()));

        List<File> afterSync = monitor.refresh(registered(p.toFile(), "int main() { return 1; }\n"));
        Assertions.assertTrue(afterSync.isEmpty());
        Assertions.assertTrue(monitor.currentExternalChanges().isEmpty());
    }

    @Test
    void furtherExternalModificationNotifiesAgain() throws IOException {
        Path p = tempDir.resolve("main.c");
        Files.writeString(p, "v1\n", StandardCharsets.UTF_8);
        ExternalFileChangeMonitor monitor = new ExternalFileChangeMonitor();
        String baseline = ExternalFileChangeService.fingerprint("v1\n");

        Files.writeString(p, "v2\n", StandardCharsets.UTF_8);
        monitor.refresh(registered(p.toFile(), baseline));

        Files.writeString(p, "v3\n", StandardCharsets.UTF_8);
        List<File> again = monitor.refresh(registered(p.toFile(), baseline));
        Assertions.assertEquals(List.of(p.toFile()), again);
    }

    @Test
    void closedTabIsForgotten() throws IOException {
        Path p = tempDir.resolve("main.c");
        Files.writeString(p, "content\n", StandardCharsets.UTF_8);
        ExternalFileChangeMonitor monitor = new ExternalFileChangeMonitor();
        String baseline = ExternalFileChangeService.fingerprint("content\n");

        Files.writeString(p, "changed\n", StandardCharsets.UTF_8);
        monitor.refresh(registered(p.toFile(), baseline));
        Assertions.assertFalse(monitor.currentExternalChanges().isEmpty());

        List<File> detected = monitor.refresh(Map.of());
        Assertions.assertTrue(detected.isEmpty());
        Assertions.assertTrue(monitor.currentExternalChanges().isEmpty());
    }

    @Test
    void emptyOrOversizedFilesAreNotWatched() throws IOException {
        Path empty = tempDir.resolve("empty.c");
        Files.write(empty, new byte[0]);
        Path big = tempDir.resolve("big.c");
        byte[] bigBytes = new byte[(int) ExternalFileChangeMonitor.MAX_WATCHED_FILE_SIZE + 1];
        Files.write(big, bigBytes);

        ExternalFileChangeMonitor monitor = new ExternalFileChangeMonitor();
        Map<File, String> regs = new HashMap<>();
        regs.put(empty.toFile(), ExternalFileChangeService.fingerprint(String.valueOf("")));
        regs.put(big.toFile(), ExternalFileChangeService.fingerprint(new byte[0]));

        List<File> detected = monitor.refresh(regs);

        Assertions.assertTrue(detected.isEmpty());
        Assertions.assertTrue(monitor.currentExternalChanges().isEmpty());
    }
}