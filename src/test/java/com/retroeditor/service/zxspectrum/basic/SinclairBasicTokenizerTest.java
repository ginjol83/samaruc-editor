package com.retroeditor.service.zxspectrum.basic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SinclairBasicTokenizerTest {

    private static byte[] hex(String text) {
        String compact = text.replaceAll("\\s+", "");
        byte[] result = new byte[compact.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    @Test
    void tokenizesPrintAndStringLiteral() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 PRINT \"HELLO\"");
        assertArrayEquals(hex("00 0A 09 00 F5 22 48 45 4C 4C 4F 22 0D"), program);
    }

    @Test
    void collectsGoToTokenAndNumber() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("20 GOTO 10");
        assertArrayEquals(hex("00 14 0A 00 EC 31 30 0E 00 00 0A 00 00 0D"), program);
    }

    @Test
    void collapsesSpacedGoToForm() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("20 GO TO 10");
        assertArrayEquals(hex("00 14 0A 00 EC 31 30 0E 00 00 0A 00 00 0D"), program);
    }

    @Test
    void keepsRemainderOfRemLiteral() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 REM Hello World!");
        assertArrayEquals(hex("00 0A 0E 00 EA 48 65 6C 6C 6F 20 57 6F 72 6C 64 21 0D"), program);
    }

    @Test
    void doesNotTokenizeInsideStringsOrRem() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 PRINT \"10 PRINT\": REM 20 PRINT");
        assertArrayEquals(
                hex("00 0A 16 00 F5 22 31 30 20 50 52 49 4E 54 22 3A EA 32 30 20 50 52 49 4E 54 0D"),
                program);
    }

    @Test
    void tokenizesOperatorsAndFunctions() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 IF A<=B AND A>=C OR A<>D THEN PRINT");
        assertArrayEquals(
                hex("00 0A 0F 00 FA 41 C7 42 C6 41 C8 43 C5 41 C9 44 CB F5 0D"), program);
    }

    @Test
    void encodesLetAssignment() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 LET A=5");
        assertArrayEquals(hex("00 0A 0B 00 F1 41 3D 35 0E 00 00 05 00 00 0D"), program);
    }

    @Test
    void skipsBlankAndCommentLines() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("\n# not a program line\n10 CLS\n");
        assertArrayEquals(hex("00 0A 02 00 FB 0D"), program);
    }

    @Test
    void rejectsMissingLineNumber() {
        assertThrows(SinclairBasicSyntaxException.class,
                () -> SinclairBasicTokenizer.tokenize("PRINT \"HI\""));
    }

    @Test
    void rejectsNonIncreasingLineNumbers() {
        assertThrows(SinclairBasicSyntaxException.class,
                () -> SinclairBasicTokenizer.tokenize("20 CLS\n10 CLS"));
    }

    @Test
    void encodesShortInteger() throws Exception {
        assertArrayEquals(hex("00 00 0A 00 00"), SinclairBasicTokenizer.zxFloat(10, 1));
        assertArrayEquals(hex("00 00 01 00 00"), SinclairBasicTokenizer.zxFloat(1, 1));
        assertArrayEquals(hex("00 00 00 00 00"), SinclairBasicTokenizer.zxFloat(0, 1));
    }

    @Test
    void encodesFloatingPoint() throws Exception {
        assertArrayEquals(hex("80 00 00 00 00"), SinclairBasicTokenizer.zxFloat(0.5, 1));
        assertArrayEquals(hex("81 40 00 00 00"), SinclairBasicTokenizer.zxFloat(1.5, 1));
        assertArrayEquals(hex("87 16 DC 28 F6"), SinclairBasicTokenizer.zxFloat(75.43, 1));
    }

    @Test
    void encodesNegativeNumberKeepingSignInText() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 LET A=-1.5");
        assertArrayEquals(
                hex("00 0A 0E 00 F1 41 3D 2D 31 2E 35 0E 81 40 00 00 00 0D"), program);
    }

    @Test
    void mapsFullKeywordTableValues() {
        assertEquals(0xF5, SinclairBasicTokens.orderedKeywords().stream()
                .filter(k -> k.text().equals("print")).findFirst().orElseThrow().value());
        assertEquals(0xA5, SinclairBasicTokens.orderedKeywords().stream()
                .filter(k -> k.text().equals("rnd")).findFirst().orElseThrow().value());
        assertEquals(0xEA, SinclairBasicTokens.orderedKeywords().stream()
                .filter(k -> k.text().equals("rem")).findFirst().orElseThrow().value());
        assertEquals(0xA3, SinclairBasicTokens.orderedKeywords().stream()
                .filter(k -> k.text().equals("spectrum")).findFirst().orElseThrow().value());
        assertEquals(0xA4, SinclairBasicTokens.orderedKeywords().stream()
                .filter(k -> k.text().equals("play")).findFirst().orElseThrow().value());
    }

    @Test
    void supportsBinNumbers() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 LET A=BIN 1001");
        assertArrayEquals(
                hex("00 0A 0F 00 F1 41 3D C4 31 30 30 31 0E 00 00 09 00 00 0D"), program);
    }

    @Test
    void tapHasProgramHeaderAndDataBlocks() throws Exception {
        byte[] program = SinclairBasicTokenizer.tokenize("10 PRINT \"HI\"");
        byte[] tap = SinclairTapWriter.toTap(program, "GAME", 10);

        assertEquals(program.length + 25, tap.length);
        assertEquals(19, tap[0] & 0xFF);
        assertEquals(0, tap[1] & 0xFF);
        assertEquals(0x00, tap[2] & 0xFF);
        assertEquals(0x00, tap[3] & 0xFF);
        byte[] name = new byte[10];
        System.arraycopy(tap, 4, name, 0, 10);
        assertEquals("GAME      ", new String(name, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(program.length, (tap[14] & 0xFF) | (tap[15] & 0xFF) << 8);
        assertEquals(10, (tap[16] & 0xFF) | (tap[17] & 0xFF) << 8);
        assertEquals(program.length, (tap[18] & 0xFF) | (tap[19] & 0xFF) << 8);

        int headerChecksum = 0;
        for (int i = 2; i < 20; i++) {
            headerChecksum ^= tap[i] & 0xFF;
        }
        assertEquals(headerChecksum & 0xFF, tap[20] & 0xFF);

        int dataStart = 21;
        int expectedBlockLength = program.length + 2;
        assertEquals(expectedBlockLength & 0xFF, tap[dataStart] & 0xFF);
        assertEquals((expectedBlockLength >> 8) & 0xFF, tap[dataStart + 1] & 0xFF);
        assertEquals(0xFF, tap[dataStart + 2] & 0xFF);
        for (int i = 0; i < program.length; i++) {
            assertEquals(program[i], tap[dataStart + 3 + i]);
        }
    }

    @Test
    void exportServiceSetsAutostartToFirstLine() throws Exception {
        byte[] tap = new SinclairBasicExportService().exportToTapBytes("100 CLS\n200 STOP", "TEST");
        assertEquals(19, tap[0] & 0xFF);
        assertEquals(100, (tap[16] & 0xFF) | (tap[17] & 0xFF) << 8);
    }
}
