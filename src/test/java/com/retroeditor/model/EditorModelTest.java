package com.retroeditor.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link EditorModel} text loading behavior.
 */
class EditorModelTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies regular UTF-8 files are loaded without fallback.
     */
    @Test
    void openFileReadsUtf8Text() throws IOException {
        EditorModel model = new EditorModel();
        Path filePath = tempDir.resolve("utf8.c");
        String content = "int main() { return 0; }\n";
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        model.openFile(filePath.toFile());

        Assertions.assertEquals(content, model.getFileContent(filePath.toFile()));
    }

    /**
     * Verifies fallback decoding supports legacy single-byte encodings.
     */
    @Test
    void openFileFallsBackToWindows1252() throws IOException {
        EditorModel model = new EditorModel();
        Path filePath = tempDir.resolve("legacy.txt");
        String expected = "cami\u00F3n";
        Files.write(filePath, expected.getBytes(Charset.forName("windows-1252")));

        model.openFile(filePath.toFile());

        Assertions.assertEquals(expected, model.getFileContent(filePath.toFile()));
    }

    /**
     * Verifies binary-looking files are rejected in normal text mode.
     */
    @Test
    void openFileRejectsBinaryContent() throws IOException {
        EditorModel model = new EditorModel();
        Path filePath = tempDir.resolve("snapshot.tap");
        Files.write(filePath, new byte[] {0x00, 0x01, 0x02, 0x03, 0x04});

        IOException ex = Assertions.assertThrows(IOException.class, () -> model.openFile(filePath.toFile()));
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("binario"));
    }

    /**
     * Verifies force mode allows opening arbitrary bytes as single-byte text.
     */
    @Test
    void openFileAsSingleByteTextAlwaysLoadsContent() throws IOException {
        EditorModel model = new EditorModel();
        Path filePath = tempDir.resolve("raw.bin");
        Files.write(filePath, new byte[] {(byte) 0xE1, (byte) 0xE9, (byte) 0xED});

        File file = filePath.toFile();
        model.openFileAsSingleByteText(file);

        Assertions.assertFalse(model.getFileContent(file).isEmpty());
    }
}

