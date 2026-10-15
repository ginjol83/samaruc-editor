package com.retroeditor.service.atari.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AtariBasicDetokenizerTest {

    private static byte[] hex(String text) {
        String clean = text.replaceAll("\\s", "");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    @Test
    void detokenizesEmptyProgramAsEmptyText() throws Exception {
        byte[] data = AtariBasicTokenizer.tokenize("");
        assertEquals("", AtariBasicDetokenizer.detokenize(data));
    }

    @Test
    void detokenizesLetAndPrint() throws Exception {
        byte[] data = AtariBasicTokenizer.tokenize("10 LET X=1 : PRINT X");
        assertEquals("10 LET X=1 : PRINT X\n", AtariBasicDetokenizer.detokenize(data));
    }

    @Test
    void detokenizesEmptyLineAsRem() throws Exception {
        byte[] data = AtariBasicTokenizer.tokenize("10");
        assertEquals("10 REM\n", AtariBasicDetokenizer.detokenize(data));
    }

    @Test
    void detokenizesRemAndDataPayloads() throws Exception {
        assertEquals("10 REM THIS IS A COMMENT : WITH COLON\n",
                AtariBasicDetokenizer.detokenize(AtariBasicTokenizer.tokenize("10 REM THIS IS A COMMENT : WITH COLON")));
        assertEquals("10 DATA 1,2,3 : MORE DATA\n",
                AtariBasicDetokenizer.detokenize(AtariBasicTokenizer.tokenize("10 DATA 1,2,3 : MORE DATA")));
    }

    @Test
    void detokenizesIfBothForms() throws Exception {
        assertEquals("10 IF X=1 THEN PRINT \"A\" : PRINT \"B\"\n",
                AtariBasicDetokenizer.detokenize(AtariBasicTokenizer.tokenize("10 IF X=1 THEN PRINT \"A\" : PRINT \"B\"")));
        assertEquals("10 IF X=1 THEN 100\n",
                AtariBasicDetokenizer.detokenize(AtariBasicTokenizer.tokenize("10 IF X=1 THEN 100")));
    }

    @Test
    void detokenizesAbbreviationsAsLongKeywords() throws Exception {
        String source = "10 PR. \"X\"\n20 POK. 100,1\n30 C. 1\n40 G. 40\n50 GO TO 50\n60 ? \"HI\"";
        assertEquals("10 PRINT \"X\"\n20 POKE 100, 1\n30 COLOR 1\n40 GOTO 40\n50 GO TO 50\n60 ? \"HI\"\n",
                AtariBasicDetokenizer.detokenize(AtariBasicTokenizer.tokenize(source)));
    }

    @Test
    void detokenizesPreservingVariableCase() throws Exception {
        String text = AtariBasicDetokenizer.detokenize(AtariBasicTokenizer.tokenize("10 print \"x\" : a=1 : print a"));
        assertEquals("10 PRINT \"x\" : a=1 : PRINT a\n", text);
    }

    @Test
    void detokenizesExpressionsFromVerifiedGolden() throws Exception {
        String data = "00 00 00 01 00 01 01 01 01 01 20 01 26 01 00"
                + " 0A 00 1F 1F 28 3F 3A 43 3A 0F 01 41 2C 3C 0E 40 01 00 00 00 00 3C 0E 40 02 00 00 00 00 2C 16"
                + " 00 80 06 06 34 16";
        assertEquals("10 ? USR(ADR(\"A\"), 1, 2)\n", AtariBasicDetokenizer.detokenize(hex(data)));
    }

    @Test
    void rejectsTooShortProgram() {
        assertThrows(AtariBasicSyntaxException.class, () -> AtariBasicDetokenizer.detokenize(new byte[10]));
    }

    @Test
    void rejectsInvalidHeader() {
        assertThrows(AtariBasicSyntaxException.class,
                () -> AtariBasicDetokenizer.detokenize(hex("00 00 00 01 00 01 01 01 01 01 01 01 07 01 00")));
        assertThrows(AtariBasicSyntaxException.class,
                () -> AtariBasicDetokenizer.detokenize(hex("00 00 00 01 00 01 01 01 01 01 01 01 50 01 00")));
    }

    @Test
    void rejectsTruncatedLine() throws Exception {
        byte[] data = AtariBasicTokenizer.tokenize("10 PRINT \"X\"");
        byte[] truncated = new byte[data.length - 2];
        System.arraycopy(data, 0, truncated, 0, truncated.length);
        assertThrows(AtariBasicSyntaxException.class, () -> AtariBasicDetokenizer.detokenize(truncated));
    }

    @Test
    void rejectsQuoteInsideStringConstant() {
        String data = "00 00 00 01 00 01 01 01 01 01 0D 01 13 01 00"
                + " 0A 00 0C 0C 20 0F 03 41 22 42 16"
                + " 00 80 06 06 34 16";
        assertThrows(AtariBasicSyntaxException.class, () -> AtariBasicDetokenizer.detokenize(hex(data)));
    }

    @Test
    void rejectsTurboExpressionTokens() {
        String data = "00 00 00 01 00 01 01 01 01 01 08 01 0E 01 00"
                + " 0A 00 07 07 20 55 16"
                + " 00 80 06 06 34 16";
        assertThrows(AtariBasicSyntaxException.class, () -> AtariBasicDetokenizer.detokenize(hex(data)));
    }

    @Test
    void rejectsUnknownStatementToken() {
        String data = "00 00 00 01 00 01 01 01 01 01 07 01 0D 01 00"
                + " 0A 00 06 06 55 16"
                + " 00 80 06 06 34 16";
        assertThrows(AtariBasicSyntaxException.class, () -> AtariBasicDetokenizer.detokenize(hex(data)));
    }

    @Test
    void reportsProgramLineNumberInErrors() {
        String data = "00 00 00 01 00 01 01 01 01 01 08 01 0E 01 00"
                + " 1E 00 07 07 20 55 16"
                + " 00 80 06 06 34 16";
        AtariBasicSyntaxException ex = assertThrows(AtariBasicSyntaxException.class,
                () -> AtariBasicDetokenizer.detokenize(hex(data)));
        assertEquals(30, ex.getLine());
        assertTrue(ex.getMessage().contains("0x55"));
    }
}
