package com.retroeditor.service.basic.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasicPreprocessorTest {

    @TempDir
    Path tempDir;

    private String process(String source, Path sourcePath) throws Exception {
        return new BasicPreprocessor(tempDir).processSource(source, sourcePath);
    }

    @Test
    void keepsSourceWithoutDirectivesUntouched() throws Exception {
        String source = "10 PRINT \"HOLA\"\n20 END\n";
        assertEquals(source, process(source, tempDir.resolve("main.bas")));
    }

    @Test
    void expandsQuotedRemInclude() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\n");
        String out = process("10 REM INCLUDE \"lib.bas\"\n30 END\n", tempDir.resolve("main.bas"));
        assertEquals("20 PRINT \"LIB\"\n30 END\n", out);
    }

    @Test
    void expandsHashInclude() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\n");
        String out = process("10 #INCLUDE \"lib.bas\"\n", tempDir.resolve("main.bas"));
        assertEquals("20 PRINT \"LIB\"\n", out);
    }

    @Test
    void expandsBareIncludeWithoutRem() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\n");
        String out = process("10 INCLUDE \"lib.bas\"\n", tempDir.resolve("main.bas"));
        assertEquals("20 PRINT \"LIB\"\n", out);
    }

    @Test
    void expandsApostropheCommentInclude() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\n");
        String out = process("10 ' INCLUDE \"lib.bas\"\n", tempDir.resolve("main.bas"));
        assertEquals("20 PRINT \"LIB\"\n", out);
    }

    @Test
    void directiveLineIsNotKeptInOutput() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\n");
        String out = process("10 REM INCLUDE \"lib.bas\"\n", tempDir.resolve("main.bas"));
        assertFalse(out.contains("INCLUDE"), "La directiva no debe aparecer en la salida");
    }

    @Test
    void expandsNestedIncludesInOrder() throws Exception {
        write("nivel2.bas", "30 PRINT \"LEAF\"\n");
        write("nivel1.bas", "20 REM INCLUDE \"nivel2.bas\"\n");
        String out = process("10 REM INCLUDE \"nivel1.bas\"\n40 END\n", tempDir.resolve("main.bas"));
        assertEquals("30 PRINT \"LEAF\"\n40 END\n", out);
    }

    @Test
    void resolvesRelativePathsFromIncludingFile() throws Exception {
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        write("sub/leaf.bas", "50 PRINT \"LEAF\"\n");
        write("sub/mid.bas", "40 REM INCLUDE \"leaf.bas\"\n");
        String out = process("10 REM INCLUDE \"sub/mid.bas\"\n60 END\n", tempDir.resolve("main.bas"));
        assertEquals("50 PRINT \"LEAF\"\n60 END\n", out);
    }

    @Test
    void expandsWithoutSourcePathUsingBaseDir() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\n");
        String out = new BasicPreprocessor(tempDir).processSource(
                "10 REM INCLUDE \"lib.bas\"\n30 END\n", null);
        assertEquals("20 PRINT \"LIB\"\n30 END\n", out);
    }

    @Test
    void normalizesWindowsLineEndings() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\r\n");
        String out = process("10 REM INCLUDE \"lib.bas\"\r\n30 END\r\n", tempDir.resolve("main.bas"));
        assertEquals("20 PRINT \"LIB\"\n30 END\n", out);
    }

    @Test
    void detectsMissingFileWithDirectiveLineNumber() throws Exception {
        BasicIncludeException ex = assertThrows(BasicIncludeException.class,
                () -> process("10 PRINT \"A\"\n20 REM INCLUDE \"nope.bas\"\n30 END\n", tempDir.resolve("main.bas")));
        assertEquals(2, ex.getLine());
        assertTrue(ex.getMessage().contains("nope.bas"));
    }

    @Test
    void detectsCycleBetweenFiles() throws Exception {
        write("a.bas", "10 REM INCLUDE \"b.bas\"\n");
        write("b.bas", "20 REM INCLUDE \"a.bas\"\n");
        BasicIncludeException ex = assertThrows(BasicIncludeException.class,
                () -> process("10 REM INCLUDE \"a.bas\"\n", tempDir.resolve("main.bas")));
        assertTrue(ex.getMessage().toLowerCase().contains("ciclo"));
    }

    @Test
    void selfIncludeIsDetectedAsCycle() throws Exception {
        write("a.bas", "10 REM INCLUDE \"a.bas\"\n");
        assertThrows(BasicIncludeException.class,
                () -> process("10 REM INCLUDE \"a.bas\"\n", tempDir.resolve("main.bas")));
    }

    @Test
    void includeMentionInsideCodeIsLeftUntouched() throws Exception {
        String source = "10 PRINT \"INCLUDE\"\n20 A=5\n";
        assertEquals(source, process(source, tempDir.resolve("main.bas")));
    }

    @Test
    void sameFileIncludedTwiceNotReportedAsCycle() throws Exception {
        write("lib.bas", "20 PRINT \"LIB\"\n");
        String source = "10 REM INCLUDE \"lib.bas\"\n30 REM INCLUDE \"lib.bas\"\n40 END\n";
        assertEquals("20 PRINT \"LIB\"\n20 PRINT \"LIB\"\n40 END\n",
                process(source, tempDir.resolve("main.bas")));
    }

    @Test
    void reservedRangeAcceptsLinesInsideRange() throws Exception {
        write("lib.bas", "150 PRINT \"A\"\n250 PRINT \"B\"\n");
        String out = process("10 REM INCLUDE \"lib.bas\" 100-300\n", tempDir.resolve("main.bas"));
        assertEquals("150 PRINT \"A\"\n250 PRINT \"B\"\n", out);
    }

    @Test
    void reservedRangeRejectsLineOutsideRange() throws Exception {
        write("lib.bas", "500 PRINT \"A\"\n");
        BasicIncludeException ex = assertThrows(BasicIncludeException.class,
                () -> process("10 REM INCLUDE \"lib.bas\" 100-300\n", tempDir.resolve("main.bas")));
        assertTrue(ex.getMessage().contains("fuera del rango reservado"));
        assertEquals(1, ex.getLine());
    }

    @Test
    void overlappingReservedRangesAreRejected() throws Exception {
        write("a.bas", "100 PRINT \"A\"\n");
        write("b.bas", "200 PRINT \"B\"\n");
        BasicIncludeException ex = assertThrows(BasicIncludeException.class,
                () -> process("10 REM INCLUDE \"a.bas\" 100-300\n20 REM INCLUDE \"b.bas\" 200-400\n",
                        tempDir.resolve("main.bas")));
        assertTrue(ex.getMessage().contains("solapados"));
    }

    @Test
    void invalidReservedRangeIsRejected() throws Exception {
        write("lib.bas", "10 PRINT \"A\"\n");
        assertThrows(BasicIncludeException.class,
                () -> process("10 REM INCLUDE \"lib.bas\" 500-100\n", tempDir.resolve("main.bas")));
    }

    @Test
    void duplicateLineNumberAcrossFilesIsRejected() throws Exception {
        write("a.bas", "100 PRINT \"A\"\n");
        write("b.bas", "100 PRINT \"B\"\n");
        BasicIncludeException ex = assertThrows(BasicIncludeException.class,
                () -> process("10 REM INCLUDE \"a.bas\"\n20 REM INCLUDE \"b.bas\"\n", tempDir.resolve("main.bas")));
        assertTrue(ex.getMessage().contains("duplicado"));
    }

    @Test
    void bareIncludeAcceptsTrailingRange() throws Exception {
        write("lib.bas", "150 PRINT \"A\"\n");
        String out = process("10 INCLUDE lib.bas 100-200\n", tempDir.resolve("main.bas"));
        assertEquals("150 PRINT \"A\"\n", out);
    }

    @Test
    void quotedFileNameWithHyphenKeepsRange() throws Exception {
        write("my-lib.bas", "150 PRINT \"A\"\n");
        String out = process("10 REM INCLUDE \"my-lib.bas\" 100-200\n", tempDir.resolve("main.bas"));
        assertEquals("150 PRINT \"A\"\n", out);
    }

    @Test
    void nestedIncludeValidatesItsOwnRange() throws Exception {
        write("leaf.bas", "150 PRINT \"L\"\n");
        write("mid.bas", "10 REM INCLUDE \"leaf.bas\" 100-200\n");
        String out = process("10 REM INCLUDE \"mid.bas\" 5000-5999\n", tempDir.resolve("main.bas"));
        assertEquals("150 PRINT \"L\"\n", out);
    }

    @Test
    void outerRangeDoesNotApplyToNestedIncludes() throws Exception {
        write("leaf.bas", "50 PRINT \"L\"\n");
        write("mid.bas", "10 REM INCLUDE \"leaf.bas\"\n");
        String out = process("10 REM INCLUDE \"mid.bas\" 5000-5999\n", tempDir.resolve("main.bas"));
        assertEquals("50 PRINT \"L\"\n", out);
    }

    private void write(String name, String content) throws Exception {
        Path target = tempDir.resolve(name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}