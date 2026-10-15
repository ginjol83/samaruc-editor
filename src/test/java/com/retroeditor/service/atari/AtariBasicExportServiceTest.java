package com.retroeditor.service.atari;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.retroeditor.service.atari.atr.AtrFileEntry;
import com.retroeditor.service.atari.atr.AtrImageReader;
import com.retroeditor.service.atari.basic.AtariBasicDetokenizer;
import com.retroeditor.service.atari.basic.AtariBasicSyntaxException;
import com.retroeditor.service.atari.basic.AtariBasicTokenizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtariBasicExportServiceTest {

    private static final String PROGRAM = "10 PRINT \"HOLA MUNDO\"\n20 END\n";

    private final AtariBasicExportService service = new AtariBasicExportService();

    @Test
    void embedsTokenizedProgramUnderDefaultName() throws AtariBasicSyntaxException {
        byte[] image = service.exportToAtrBytes(PROGRAM, null, null);
        AtrImageReader reader = AtrImageReader.parse(image);
        AtrFileEntry entry = reader.findFile(AtariBasicExportService.DEFAULT_ENTRY_NAME);
        assertNotNull(entry);
        assertArrayEquals(AtariBasicTokenizer.tokenize(PROGRAM), reader.readFile(entry));
    }

    @Test
    void usesCustomEntryNameWhenProvided() throws AtariBasicSyntaxException {
        byte[] image = service.exportToAtrBytes(PROGRAM, "SALUDO.BAS", null);
        AtrImageReader reader = AtrImageReader.parse(image);
        assertNotNull(reader.findFile("SALUDO.BAS"));
    }

    @Test
    void normalizesWindowsLineEndings() throws AtariBasicSyntaxException {
        byte[] image = service.exportToAtrBytes("10 PRINT \"HOLA\"\r\n20 END\r\n", null, null);
        AtrImageReader reader = AtrImageReader.parse(image);
        AtrFileEntry entry = reader.findFile(AtariBasicExportService.DEFAULT_ENTRY_NAME);
        assertArrayEquals(AtariBasicTokenizer.tokenize("10 PRINT \"HOLA\"\n20 END\n"), reader.readFile(entry));
    }

    @Test
    void writesImageToDisk(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("programa.bas");
        Files.writeString(source, PROGRAM, StandardCharsets.UTF_8);
        Path atr = tmp.resolve("salida.atr");

        Path result = service.exportToAtr(source, atr, AtariBasicExportService.DEFAULT_ENTRY_NAME, null);

        assertEquals(atr, result);
        assertTrue(Files.exists(atr));
        byte[] image = Files.readAllBytes(atr);
        assertEquals(0x96, image[0] & 0xFF);
        assertEquals(0x02, image[1] & 0xFF);
        assertEquals(16 + 720 * 128, image.length);
    }

    @Test
    void rejectsInvalidSource() {
        assertThrows(AtariBasicSyntaxException.class,
                () -> service.exportToAtrBytes("PRINT 1\n", null, null));
    }

    @Test
    void rejectsNullSourcePath() {
        assertThrows(IllegalArgumentException.class,
                () -> service.exportToAtr(null, Path.of("x.atr"), null, null));
    }

    @Test
    void rejectsLineNumberOutOfRange() {
        assertThrows(AtariBasicSyntaxException.class,
                () -> service.exportToAtrBytes("40000 PRINT 1\n", null, null));
    }

    @Test
    void rejectsOverlongLine() {
        String program = "10 PRINT \"" + "A".repeat(300) + "\"\n";
        assertThrows(AtariBasicSyntaxException.class,
                () -> service.exportToAtrBytes(program, null, null));
    }

    @Test
    void roundTripsLargeProgramThroughAtr() throws AtariBasicSyntaxException {
        String program = "10 FOR I=1 TO 3\n20 PRINT \"LINE \";I\n30 NEXT I\n40 END\n";
        AtrImageReader reader = AtrImageReader.parse(service.exportToAtrBytes(program, null, null));
        AtrFileEntry entry = reader.findFile(AtariBasicExportService.DEFAULT_ENTRY_NAME);
        assertNotNull(entry);
        String detokenized = AtariBasicDetokenizer.detokenize(reader.readFile(entry));
        assertArrayEquals(AtariBasicTokenizer.tokenize(program), AtariBasicTokenizer.tokenize(detokenized));
    }

    @Test
    void rejectsNullOutputPath(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("p.bas");
        Files.writeString(source, PROGRAM, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> service.exportToAtr(source, null, null, null));
    }

    @Test
    void basicBytesMatchTokenizedProgram() throws AtariBasicSyntaxException {
        assertArrayEquals(AtariBasicTokenizer.tokenize(PROGRAM), service.exportToBasicBytes(PROGRAM, null));
    }

    @Test
    void writesBasicProgramToDisk(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("programa.bas");
        Files.writeString(source, PROGRAM, StandardCharsets.UTF_8);
        Path out = tmp.resolve("programa.tokenized.bas");

        Path result = service.exportToBasic(source, out, null);

        assertEquals(out, result);
        assertTrue(Files.exists(out));
        assertArrayEquals(AtariBasicTokenizer.tokenize(PROGRAM), Files.readAllBytes(out));
    }

    @Test
    void basicRejectsInvalidSource() {
        assertThrows(AtariBasicSyntaxException.class,
                () -> service.exportToBasicBytes("PRINT 1\n", null));
    }

    @Test
    void basicRejectsNullPaths(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("p.bas");
        Files.writeString(source, PROGRAM, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> service.exportToBasic(null, tmp.resolve("o.bas"), null));
        assertThrows(IllegalArgumentException.class, () -> service.exportToBasic(source, null, null));
    }

    @Test
    void groupsIncludedFilesWhenExportingToBasic(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("lib.bas"), "20 PRINT \"LIB\"\n", StandardCharsets.UTF_8);
        Path source = tmp.resolve("main.bas");
        Files.writeString(source, "10 REM INCLUDE \"lib.bas\"\n30 END\n", StandardCharsets.UTF_8);

        byte[] output = Files.readAllBytes(service.exportToBasic(source, tmp.resolve("out.bas"), null));

        assertArrayEquals(AtariBasicTokenizer.tokenize("20 PRINT \"LIB\"\n30 END\n"), output);
    }

    @Test
    void reportsMissingIncludeAsSyntaxError(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("main.bas");
        Files.writeString(source, "10 REM INCLUDE \"nope.bas\"\n", StandardCharsets.UTF_8);

        assertThrows(AtariBasicSyntaxException.class,
                () -> service.exportToBasic(source, tmp.resolve("out.bas"), null));
    }

    @Test
    void transpilesSamarucBasicWhenExportingToBasic(@TempDir Path tmp) throws Exception {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 D=DIST(0,0,3,4)",
                "20 END",
                "100 FUNCTION DIST(X1,Y1,X2,Y2)",
                "110 RETURN X1+Y1+X2+Y2",
                "120 END FUNCTION") + "\n";
        Path sourceBas = tmp.resolve("samaruc.bas");
        Files.writeString(sourceBas, source, StandardCharsets.UTF_8);

        byte[] output = Files.readAllBytes(service.exportToBasic(sourceBas, tmp.resolve("out.bas"), null));

        String expected = com.retroeditor.service.basic.samaruc.SamarucBasicTranspiler.transpile(source);
        assertArrayEquals(AtariBasicTokenizer.tokenize(expected), output);
    }
}
