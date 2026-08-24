package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenameRefactoringServiceTest {

    @Test
    void testRenameSymbolAcrossFiles(@TempDir Path tempDir) throws Exception {
        File root = tempDir.toFile();
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);

        Path cFile = srcDir.resolve("main.c");
        Files.writeString(cFile, "void oldFunc() {\n    oldFunc();\n    int someOtherVar = 1;\n}\n");

        Path hFile = srcDir.resolve("main.h");
        Files.writeString(hFile, "void oldFunc();\n");

        RenameRefactoringService service = new RenameRefactoringService();
        RenameRefactoringService.RenameResult result = service.renameSymbolInProject(root, "oldFunc", "newFunc");

        assertEquals(2, result.getFilesModified());
        assertTrue(result.getTotalOccurrences() >= 3);

        String cContent = Files.readString(cFile);
        assertTrue(cContent.contains("void newFunc()"));
        assertTrue(cContent.contains("newFunc();"));
        assertFalse(cContent.contains("oldFunc"));

        String hContent = Files.readString(hFile);
        assertTrue(hContent.contains("void newFunc();"));
    }

    @Test
    void testInvalidIdentifier(@TempDir Path tempDir) {
        RenameRefactoringService service = new RenameRefactoringService();
        RenameRefactoringService.RenameResult result = service.renameSymbolInProject(tempDir.toFile(), "123invalid", "new");
        assertEquals(0, result.getFilesModified());
    }
}
