package com.retroeditor.service.atari.xex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.retroeditor.service.atari.basic.AtariBasicSyntaxException;
import com.retroeditor.service.atari.basic.AtariBasicTokenizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtariBasicXexExportServiceTest {

    private static final String PROGRAM = "10 PRINT \"HOLA MUNDO\"\n20 END\n";

    private final AtariBasicXexExportService service = new AtariBasicXexExportService();

    @Test
    void exportsTokenizedProgramAsXex() throws Exception {
        byte[] tokenized = AtariBasicTokenizer.tokenize(PROGRAM);

        byte[] xex = service.exportToXexBytes(PROGRAM, null);

        AtariBasicXexPackager packager = new AtariBasicXexPackager();
        assertArrayEquals(packager.pack(tokenized), xex);
    }

    @Test
    void writesXexToDisk(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("programa.bas");
        Files.writeString(source, PROGRAM, StandardCharsets.UTF_8);
        Path xex = tmp.resolve("salida.xex");

        Path result = service.exportToXex(source, xex, null);

        assertEquals(xex, result);
        assertTrue(Files.exists(xex));
        assertTrue(Files.size(xex) > AtariBasicXexPackager.BASIC_HEADER_SIZE);
    }

    @Test
    void expandsIncludesBeforePackaging(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("lib.bas"), "20 PRINT \"LIB\"\n", StandardCharsets.UTF_8);
        Path source = tmp.resolve("main.bas");
        Files.writeString(source, "10 REM INCLUDE \"lib.bas\"\n30 END\n", StandardCharsets.UTF_8);
        Path output = tmp.resolve("main.xex");

        service.exportToXex(source, output, null);

        AtariBasicXexPackager packager = new AtariBasicXexPackager();
        byte[] expected = packager.pack(AtariBasicTokenizer.tokenize("20 PRINT \"LIB\"\n30 END\n"));
        assertArrayEquals(expected, Files.readAllBytes(output));
    }

    @Test
    void rejectsInvalidSource() {
        assertThrows(AtariBasicSyntaxException.class, () -> service.exportToXexBytes("PRINT 1\n", null));
    }

    @Test
    void rejectsNullPaths(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () -> service.exportToXex(null, tmp.resolve("x.xex"), null));
        assertThrows(IllegalArgumentException.class, () -> service.exportToXex(tmp.resolve("x.bas"), null, null));
    }
}
