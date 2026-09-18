package com.retroeditor.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalFileChangeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void fingerprintIsStableAndContentSensitive() {
        String a = "#include <gb/gb.h>\nvoid main() {}\n";
        Assertions.assertEquals(ExternalFileChangeService.fingerprint(a), ExternalFileChangeService.fingerprint(a));
        Assertions.assertNotEquals(ExternalFileChangeService.fingerprint(a), ExternalFileChangeService.fingerprint(a + "\n"));
        Assertions.assertFalse(ExternalFileChangeService.fingerprint((String) null).isEmpty());
    }

    @Test
    void unchangedFileHasNoExternalChange() throws IOException {
        Path file = tempDir.resolve("main.c");
        String content = "void main() { return; }\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        Assertions.assertFalse(ExternalFileChangeService.hasExternalChange(file.toFile(), ExternalFileChangeService.fingerprint(content)));
        Assertions.assertEquals(content, ExternalFileChangeService.readCurrentContent(file.toFile()));
    }

    @Test
    void externallyModifiedFileDetectsChange() throws IOException {
        Path file = tempDir.resolve("main.c");
        String original = "void main() { return 0; }\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);
        String baseline = ExternalFileChangeService.fingerprint(original);

        Files.writeString(file, "void main() { return 1; }\n", StandardCharsets.UTF_8);

        Assertions.assertTrue(ExternalFileChangeService.hasExternalChange(file.toFile(), baseline));
    }

    @Test
    void missingRegistrationOrFileDoesNotBlock() {
        Assertions.assertFalse(ExternalFileChangeService.hasExternalChange(null, "abc"));
        Assertions.assertFalse(ExternalFileChangeService.hasExternalChange(tempDir.toFile(), null));
        Assertions.assertFalse(ExternalFileChangeService.hasExternalChange(tempDir.resolve("no-existe.c").toFile(),
            ExternalFileChangeService.fingerprint("x")));
    }

    @Test
    void nonAsciiContentRoundTrips() throws IOException {
        Path file = tempDir.resolve("txt.c");
        String content = "// t\u00edtulo con acentos: \u00e1\u00e9\u00ed\u00f3\u00fa\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        Assertions.assertFalse(ExternalFileChangeService.hasExternalChange(file.toFile(), ExternalFileChangeService.fingerprint(content)));
    }
}