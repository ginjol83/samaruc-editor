package com.retroeditor.service.atari.basic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AtariBasicTokenizerTest {

    private static byte[] tokenize(String source) throws AtariBasicSyntaxException {
        return AtariBasicTokenizer.tokenize(source);
    }

    private static byte[] hex(String text) {
        String clean = text.replaceAll("\\s", "");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static void assertGolden(String source, String expected) throws AtariBasicSyntaxException {
        assertArrayEquals(hex(expected), tokenize(source), "source: " + source);
    }

    @Test
    void emptyProgramProducesHeaderAndImmediateLineOnly() throws Exception {
        assertGolden("", "00 00 00 01 00 01 01 01 01 01 01 01 07 01 00 00 80 06 06 34 16");
    }

    @Test
    void tokenizesLetAndPrintWithOracleGolden() throws Exception {
        assertGolden("10 LET X=1 : PRINT X",
                "00 00 00 01 01 01 02 01 0A 01 1D 01 23 01 D8 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 13 0F 06 80 2D 0E 40 01 00 00 00 00 14 13 20 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesPrintStringWithOracleGolden() throws Exception {
        assertGolden("10 PRINT \"HOLA MUNDO\"",
                "00 00 00 01 00 01 01 01 01 01 13 01 19 01 00"
                        + " 0A 00 12 12 20 0F 0A 48 4F 4C 41 20 4D 55 4E 44 4F 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesEmptyLineNumberAsRem() throws Exception {
        assertGolden("10", "00 00 00 01 00 01 01 01 01 01 07 01 0D 01 00 0A 00 06 06 00 9B 00 80 06 06 34 16");
    }

    @Test
    void tokenizesRemCommentWithPayload() throws Exception {
        assertGolden("10 REM THIS IS A COMMENT : WITH COLON",
                "00 00 00 01 00 01 01 01 01 01 25 01 2B 01 00"
                        + " 0A 00 24 24 00 54 48 49 53 20 49 53 20 41 20 43 4F 4D 4D 45 4E 54 20 3A 20 57 49 54 48 20 43 4F 4C 4F 4E 9B"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesDataWithRawPayload() throws Exception {
        assertGolden("10 DATA 1,2,3 : MORE DATA",
                "00 00 00 01 00 01 01 01 01 01 18 01 1E 01 00"
                        + " 0A 00 17 17 01 31 2C 32 2C 33 20 3A 20 4D 4F 52 45 20 44 41 54 41 9B"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesAbbreviationsLikeAtariBasic() throws Exception {
        assertGolden("10 PR. \"X\"\n20 POK. 100,1\n30 C. 1\n40 G. 40\n50 GO TO 50\n60 ? \"HI\"",
                "00 00 00 01 00 01 01 01 01 01 50 01 56 01 00"
                        + " 0A 00 09 09 20 0F 01 58 16"
                        + " 14 00 15 15 1F 0E 41 01 00 00 00 00 12 0E 40 01 00 00 00 00 16"
                        + " 1E 00 0D 0D 03 0E 40 01 00 00 00 00 16"
                        + " 28 00 0D 0D 0A 0E 40 40 00 00 00 00 16"
                        + " 32 00 0D 0D 0B 0E 40 50 00 00 00 00 16"
                        + " 3C 00 0A 0A 28 0F 02 48 49 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesIfThenStatementForm() throws Exception {
        assertGolden("10 IF X=1 THEN PRINT \"YES\"",
                "00 00 00 01 01 01 02 01 0A 01 21 01 27 01 D8 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 17 0F 07 80 22 0E 40 01 00 00 00 00 1B 17 20 0F 03 59 45 53 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesIfThenLineNumberForm() throws Exception {
        assertGolden("10 IF X=1 THEN 100",
                "00 00 00 01 01 01 02 01 0A 01 21 01 27 01 D8 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 17 17 07 80 22 0E 40 01 00 00 00 00 1B 0E 41 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesIfThenWithMultipleStatements() throws Exception {
        assertGolden("10 IF X=1 THEN PRINT \"A\" : PRINT \"B\"",
                "00 00 00 01 01 01 02 01 0A 01 25 01 2B 01 D8 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 1B 0F 07 80 22 0E 40 01 00 00 00 00 1B 15 20 0F 01 41 14 1B 20 0F 01 42 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesNestedIfThen() throws Exception {
        assertGolden("10 IF X THEN IF Y THEN PRINT 1",
                "00 00 00 01 02 01 03 01 13 01 28 01 2E 01 D8 D9 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00"
                        + " 0A 00 15 07 07 80 1B 0B 07 81 1B 15 20 0E 40 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesStringVariable() throws Exception {
        assertGolden("10 A$=\"HI\" : PRINT A$",
                "00 00 00 01 02 01 03 01 0B 01 1B 01 21 01 41 A4 00 80 00 00 00 00 00 00 00"
                        + " 0A 00 10 0C 36 80 2E 0F 02 48 49 14 10 20 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesArraysAndSubstrings() throws Exception {
        assertGolden("10 DIM A(10) : A(1)=5 : PRINT A(1)",
                "00 00 00 01 02 01 03 01 0B 01 3D 01 43 01 41 A8 00 40 00 00 00 00 00 00 00"
                        + " 0A 00 32 10 14 80 39 0E 40 10 00 00 00 00 2C 14 25 36 80 38 0E 40 01 00 00 00 00 2C 2D 0E 40 05 00 00 00 00 14 32 20 80 38 0E 40 01 00 00 00 00 2C 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesDimWithStringAndTwoDimensions() throws Exception {
        assertGolden("10 DIM A$(10),B(5,5)",
                "00 00 00 01 04 01 05 01 15 01 38 01 3E 01 41 A4 42 A8 00 80 00 00 00 00 00 00 00 40 01 00 00 00 00 00 00"
                        + " 0A 00 23 23 14 80 3B 0E 40 10 00 00 00 00 2C 12 81 39 0E 40 05 00 00 00 00 3C 0E 40 05 00 00 00 00 2C 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesSubstringAssignment() throws Exception {
        assertGolden("10 A$(2,4)=\"XY\"",
                "00 00 00 01 02 01 03 01 0B 01 28 01 2E 01 41 A4 00 80 00 00 00 00 00 00 00"
                        + " 0A 00 1D 1D 36 80 37 0E 40 02 00 00 00 00 3C 0E 40 04 00 00 00 00 2C 2E 0F 02 58 59 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesSubstringAccessWithExpressions() throws Exception {
        assertGolden("10 A$(I+1,J*2)=\"X\"",
                "00 00 00 01 04 01 05 01 1D 01 3D 01 43 01 41 A4 C9 CA 00 80 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02 00 00 00 00 00 00"
                        + " 0A 00 20 20 36 80 37 81 25 0E 40 01 00 00 00 00 3C 82 24 0E 40 02 00 00 00 00 2C 2E 0F 01 58 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesFunctions() throws Exception {
        assertGolden("10 PRINT ABS(-5)+SQR(4)",
                "00 00 00 01 00 01 01 01 01 01 1D 01 23 01 00"
                        + " 0A 00 1C 1C 20 4F 3A 36 0E 40 05 00 00 00 00 2C 25 4D 3A 0E 40 04 00 00 00 00 2C 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesNestedFunctionCalls() throws Exception {
        assertGolden("10 ? USR(ADR(\"A\"),1,2)",
                "00 00 00 01 00 01 01 01 01 01 20 01 26 01 00"
                        + " 0A 00 1F 1F 28 3F 3A 43 3A 0F 01 41 2C 3C 0E 40 01 00 00 00 00 3C 0E 40 02 00 00 00 00 2C 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesStringFunctions() throws Exception {
        assertGolden("10 ? LEN(\"ABC\")+ASC(\"A\")+VAL(\"5\")",
                "00 00 00 01 00 01 01 01 01 01 1D 01 23 01 00"
                        + " 0A 00 1C 1C 28 42 3A 0F 03 41 42 43 2C 25 40 3A 0F 01 41 2C 25 41 3A 0F 01 35 2C 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesParenthesizedExpressions() throws Exception {
        assertGolden("10 PRINT (1+2)*3\n20 PRINT 1+2*3",
                "00 00 00 01 00 01 01 01 01 01 3D 01 43 01 00"
                        + " 0A 00 1F 1F 20 2B 0E 40 01 00 00 00 00 25 0E 40 02 00 00 00 00 2C 24 0E 40 03 00 00 00 00 16"
                        + " 14 00 1D 1D 20 0E 40 01 00 00 00 00 25 0E 40 02 00 00 00 00 24 0E 40 03 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesNestedParenthesesWithoutExtraParens() throws Exception {
        assertGolden("10 PRINT ((A+B)*C)^2",
                "00 00 00 01 03 01 04 01 1C 01 33 01 39 01 C1 C2 C3 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02 00 00 00 00 00 00"
                        + " 0A 00 17 17 20 2B 2B 80 25 81 2C 24 82 2C 23 0E 40 02 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesVariableListInFirstUseOrder() throws Exception {
        assertGolden("10 C=1 : B=2 : A=C+B : PRINT A+B+C",
                "00 00 00 01 03 01 04 01 1C 01 47 01 4D 01 C3 C2 C1 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02 00 00 00 00 00 00"
                        + " 0A 00 2B 0F 36 80 2D 0E 40 01 00 00 00 00 14 1B 36 81 2D 0E 40 02 00 00 00 00 14 23 36 82 2D 80 25 81 14 2B 20 82 25 81 25 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesNumberFormats() throws Exception {
        assertGolden("10 PRINT 3.14159 : ? 1E9 : ? -0.5 : ? 100",
                "00 00 00 01 00 01 01 01 01 01 2D 01 33 01 00"
                        + " 0A 00 2C 0D 20 0E 40 03 14 15 90 00 14 17 28 0E 44 10 00 00 00 00 14 22 28 36 0E 3F 50 00 00 00 00 14 2C 28 0E 41 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesForNextLoop() throws Exception {
        assertGolden("10 FOR I=1 TO 10 STEP 2 : NEXT I",
                "00 00 00 01 01 01 02 01 0A 01 2D 01 33 01 C9 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 23 1F 08 80 2D 0E 40 01 00 00 00 00 19 0E 40 10 00 00 00 00 1A 0E 40 02 00 00 00 00 14 23 09 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesForWithoutStep() throws Exception {
        assertGolden("10 FOR I=1 TO 3",
                "00 00 00 01 01 01 02 01 0A 01 21 01 27 01 C9 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 17 17 08 80 2D 0E 40 01 00 00 00 00 19 0E 40 03 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesPutAndGetWithChannels() throws Exception {
        assertGolden("10 PUT #1,65 : GET #1,A",
                "00 00 00 01 01 01 02 01 0A 01 2D 01 33 01 C1 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 23 16 2A 1C 0E 40 01 00 00 00 00 12 0E 40 65 00 00 00 00 14 23 29 1C 0E 40 01 00 00 00 00 12 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesPutWithoutChannelAsSharpSixteen() throws Exception {
        assertGolden("10 PUT 65",
                "00 00 00 01 00 01 01 01 01 01 17 01 1D 01 00"
                        + " 0A 00 16 16 2A 1C 0E 40 16 00 00 00 00 12 0E 40 65 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesLowerCaseSourcePreservingVariableCase() throws Exception {
        assertGolden("10 print \"x\" : a=1 : print a",
                "00 00 00 01 01 01 02 01 0A 01 23 01 29 01 E1 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 19 09 20 0F 01 78 14 15 36 80 2D 0E 40 01 00 00 00 00 14 19 20 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesOpenWithFileNameString() throws Exception {
        assertGolden("10 OPEN #1,4,0,\"D:FILE\"",
                "00 00 00 01 00 01 01 01 01 01 28 01 2E 01 00"
                        + " 0A 00 27 27 17 1C 0E 40 01 00 00 00 00 12 0E 40 04 00 00 00 00 12 0E 00 00 00 00 00 00 12 0F 06 44 3A 46 49 4C 45 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesPrintSeparators() throws Exception {
        assertGolden("10 PRINT \"A\";\"B\",X",
                "00 00 00 01 01 01 02 01 0A 01 19 01 1F 01 D8 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 0F 0F 20 0F 01 41 15 0F 01 42 12 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesPrintWithoutArguments() throws Exception {
        assertGolden("10 PRINT",
                "00 00 00 01 00 01 01 01 01 01 07 01 0D 01 00 0A 00 06 06 20 16 00 80 06 06 34 16");
    }

    @Test
    void tokenizesQuestionMarkPrint() throws Exception {
        assertGolden("10 ? \"A\";",
                "00 00 00 01 00 01 01 01 01 01 0B 01 11 01 00 0A 00 0A 0A 28 0F 01 41 15 16 00 80 06 06 34 16");
    }

    @Test
    void tokenizesGosubReturn() throws Exception {
        assertGolden("10 GOSUB 100\n100 RETURN",
                "00 00 00 01 00 01 01 01 01 01 14 01 1A 01 00"
                        + " 0A 00 0D 0D 0C 0E 41 01 00 00 00 00 16"
                        + " 64 00 06 06 24 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesStringComparisonInIf() throws Exception {
        assertGolden("10 IF A$=\"X\" THEN ? \"Y\"",
                "00 00 00 01 02 01 03 01 0B 01 1C 01 22 01 41 A4 00 80 00 00 00 00 00 00 00"
                        + " 0A 00 11 0B 07 80 34 0F 01 58 1B 11 28 0F 01 59 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesStringRelationalOperators() throws Exception {
        assertGolden("10 IF A$<=\"Y\" THEN ? 1",
                "00 00 00 01 02 01 03 01 0B 01 20 01 26 01 41 A4 00 80 00 00 00 00 00 00 00"
                        + " 0A 00 15 0B 07 80 2F 0F 01 59 1B 15 28 0E 40 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesPowerAsLeftAssociative() throws Exception {
        assertGolden("10 PRINT 2^3^2",
                "00 00 00 01 00 01 01 01 01 01 1E 01 24 01 00"
                        + " 0A 00 1D 1D 20 0E 40 02 00 00 00 00 23 0E 40 03 00 00 00 00 23 0E 40 02 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesLogicalOperatorsWithoutParens() throws Exception {
        assertGolden("10 IF NOT A AND B OR C THEN ? 1",
                "00 00 00 01 03 01 04 01 1C 01 32 01 38 01 C1 C2 C3 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02 00 00 00 00 00 00"
                        + " 0A 00 16 0C 07 28 80 2A 81 29 82 1B 16 28 0E 40 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesUnaryMinusBeforePower() throws Exception {
        assertGolden("10 PRINT -X^2",
                "00 00 00 01 01 01 02 01 0A 01 1A 01 20 01 D8 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 10 10 20 36 80 23 0E 40 02 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesGraphicsStatements() throws Exception {
        assertGolden("10 GRAPHICS 2 : COLOR 1 : PLOT 0,0 : DRAWTO 10,10",
                "00 00 00 01 00 01 01 01 01 01 3C 01 42 01 00"
                        + " 0A 00 3B 0D 2B 0E 40 02 00 00 00 00 14 17 03 0E 40 01 00 00 00 00 14 29 2C 0E 00 00 00 00 00 00 12 0E 00 00 00 00 00 00 14 3B 2F 0E 40 10 00 00 00 00 12 0E 40 10 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesOnGoto() throws Exception {
        assertGolden("10 ON X GOTO 100,200,300",
                "00 00 00 01 01 01 02 01 0A 01 29 01 2F 01 D8 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 1F 1F 1E 80 17 0E 41 01 00 00 00 00 12 0E 41 02 00 00 00 00 12 0E 41 03 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesSetcolorAndSound() throws Exception {
        assertGolden("10 SETCOLOR 2,0,14 : SOUND 0,100,10,8",
                "00 00 00 01 00 01 01 01 01 01 40 01 46 01 00"
                        + " 0A 00 3F 1D 30 0E 40 02 00 00 00 00 12 0E 00 00 00 00 00 00 12 0E 40 14 00 00 00 00 14 3F 32 0E 00 00 00 00 00 00 12 0E 41 01 00 00 00 00 12 0E 40 10 00 00 00 00 12 0E 40 08 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesReadAndRestore() throws Exception {
        assertGolden("10 READ A : RESTORE",
                "00 00 00 01 01 01 02 01 0A 01 14 01 1A 01 C1 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 0A 07 22 80 14 0A 23 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesReadWithRestoreArgument() throws Exception {
        assertGolden("10 READ A : RESTORE 5",
                "00 00 00 01 01 01 02 01 0A 01 1B 01 21 01 C1 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 11 07 22 80 14 11 23 0E 40 05 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesReadWithStringVariables() throws Exception {
        assertGolden("10 READ A$,B$",
                "00 00 00 01 04 01 05 01 15 01 1E 01 24 01 41 A4 42 A4 00 80 00 00 00 00 00 00 00 80 01 00 00 00 00 00 00"
                        + " 0A 00 09 09 22 80 12 81 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesIoStatements() throws Exception {
        assertGolden("10 XIO 18,#6,0,0,\"S:\"",
                "00 00 00 01 00 01 01 01 01 01 2C 01 32 01 00"
                        + " 0A 00 2B 2B 1D 0E 40 18 00 00 00 00 12 1C 0E 40 06 00 00 00 00 12 0E 00 00 00 00 00 00 12 0E 00 00 00 00 00 00 12 0F 02 53 3A 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesLocateStatusNoteClose() throws Exception {
        assertGolden("10 LOCATE 10,10,X\n20 STATUS #1,A\n30 NOTE #1,A,B\n40 CLOSE #1",
                "00 00 00 01 03 01 04 01 1C 01 63 01 69 01 D8 C1 C2 00"
                        + " 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02 00 00 00 00 00 00"
                        + " 0A 00 17 17 31 0E 40 10 00 00 00 00 12 0E 40 10 00 00 00 00 12 80 16"
                        + " 14 00 10 10 1A 1C 0E 40 01 00 00 00 00 12 81 16"
                        + " 1E 00 12 12 1B 1C 0E 40 01 00 00 00 00 12 81 12 82 16"
                        + " 28 00 0E 0E 11 1C 0E 40 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesComLikeDim() throws Exception {
        assertGolden("10 COM A(10),B$(20)",
                "00 00 00 01 04 01 05 01 15 01 30 01 36 01 41 A8 42 A4 00 40 00 00 00 00 00 00 00 80 01 00 00 00 00 00 00"
                        + " 0A 00 1B 1B 10 80 39 0E 40 10 00 00 00 00 2C 12 81 3B 0E 40 20 00 00 00 00 2C 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesLoadSaveEnter() throws Exception {
        assertGolden("10 ENTER \"D:PROG\"\n20 LOAD \"D:FILE\"\n30 SAVE \"D:FILE\"",
                "00 00 00 01 00 01 01 01 01 01 2B 01 31 01 00"
                        + " 0A 00 0E 0E 05 0F 06 44 3A 50 52 4F 47 16"
                        + " 14 00 0E 0E 18 0F 06 44 3A 46 49 4C 45 16"
                        + " 1E 00 0E 0E 19 0F 06 44 3A 46 49 4C 45 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesNoArgumentStatements() throws Exception {
        assertGolden("10 CONT : DEG : NEW : STOP : POP : BYE : CLR",
                "00 00 00 01 00 01 01 01 01 01 19 01 1F 01 00"
                        + " 0A 00 18 06 0F 14 09 13 14 0C 16 14 0F 26 14 12 27 14 15 0E 14 18 12 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesChainedAssignments() throws Exception {
        assertGolden("10 A=B=C",
                "00 00 00 01 03 01 04 01 1C 01 27 01 2D 01 C1 C2 C3 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02 00 00 00 00 00 00"
                        + " 0A 00 0B 0B 36 80 2D 81 22 82 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesRelationalPrintExpression() throws Exception {
        assertGolden("10 ? 1=1",
                "00 00 00 01 00 01 01 01 01 01 16 01 1C 01 00"
                        + " 0A 00 15 15 28 0E 40 01 00 00 00 00 22 0E 40 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesPrintKeywordPrefixedVariableAsPrint() throws Exception {
        assertGolden("10 PRINTER",
                "00 00 00 01 02 01 03 01 0B 01 12 01 18 01 45 D2 00"
                        + " 00 00 00 00 00 00 00 00"
                        + " 0A 00 07 07 20 80 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesLetWithSpacesAroundEquals() throws Exception {
        assertGolden("10 LET A = 1",
                "00 00 00 01 01 01 02 01 0A 01 19 01 1F 01 C1 00 00 00 00 00 00 00 00 00"
                        + " 0A 00 0F 0F 06 80 2D 0E 40 01 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void tokenizesImpliedLetForPlainVariables() throws Exception {
        assertGolden("10 C=1\n20 TOP=5",
                "00 00 00 01 04 01 05 01 15 01 33 01 39 01 C3 54 4F D0 00"
                        + " 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00"
                        + " 0A 00 0F 0F 36 80 2D 0E 40 01 00 00 00 00 16"
                        + " 14 00 0F 0F 36 81 2D 0E 40 05 00 00 00 00 16"
                        + " 00 80 06 06 34 16");
    }

    @Test
    void acceptsCrLfAndBom() throws Exception {
        String crlf = "10 LET X=1 : PRINT X\r\n20 PRINT 2\r\n";
        String withBom = "﻿10 LET X=1 : PRINT X\n20 PRINT 2\n";
        byte[] expected = tokenize("10 LET X=1 : PRINT X\n20 PRINT 2\n");
        assertArrayEquals(expected, tokenize(crlf));
        assertArrayEquals(expected, tokenize(withBom));
    }

    @Test
    void rejectsTrailingColon() {
        AtariBasicSyntaxException ex = assertThrows(AtariBasicSyntaxException.class,
                () -> tokenize("10 PRINT 1:"));
        assertEquals(1, ex.getLine());
    }

    @Test
    void rejectsColonAtLineStart() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 : PRINT 1"));
    }

    @Test
    void rejectsIncompleteKeywords() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 GO 40"));
    }

    @Test
    void rejectsRunWithLineNumber() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 RUN 100"));
    }

    @Test
    void rejectsGetWithoutChannel() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 GET A"));
    }

    @Test
    void rejectsGetWithStringVariable() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 GET #1,A$"));
    }

    @Test
    void rejectsInputPrompt() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 INPUT \"prompt\";A"));
    }

    @Test
    void rejectsDoubleColon() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 X=1: :Y=2"));
    }

    @Test
    void rejectsDimWithoutClosingParen() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 DIM A(10"));
    }

    @Test
    void rejectsBareLet() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 LET"));
    }

    @Test
    void rejectsIfWithoutThen() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 IF X"));
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 IF X THEN"));
    }

    @Test
    void rejectsIfThenNumberWithTrailingStatement() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 IF X=1 THEN 100 : PRINT \"X\""));
    }

    @Test
    void rejectsUnnumberedStatementLines() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 X=1\nPRINT 2"));
    }

    @Test
    void rejectsStatementsWithoutLineNumber() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("PRINT 1"));
    }

    @Test
    void rejectsParserDirectives() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("$options mode=default"));
    }

    @Test
    void rejectsPointWithoutChannel() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 POINT 5,10,10"));
    }

    @Test
    void rejectsStringConcatenation() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 A$=\"X\"+A$(1)"));
    }

    @Test
    void rejectsStringVariableInNumericAssignment() {
        assertThrows(AtariBasicSyntaxException.class, () -> tokenize("10 X=A$(I)"));
    }

    @Test
    void rejectsNonAscendingLineNumbers() {
        AtariBasicSyntaxException ex = assertThrows(AtariBasicSyntaxException.class,
                () -> tokenize("10 X=1\n10 Y=2"));
        assertEquals(2, ex.getLine());
    }

    @Test
    void reportsErrorLineAndColumn() {
        AtariBasicSyntaxException ex = assertThrows(AtariBasicSyntaxException.class,
                () -> tokenize("10 X=1\n20 PRINT \"A\"\n30 GET A"));
        assertEquals(3, ex.getLine());
        assertTrue(ex.getMessage().contains("I/O channel"));
    }

    @Test
    void roundTripsThroughDetokenizer() throws Exception {
        String[] sources = {
                "10 LET X=1 : PRINT X",
                "10 PRINT \"HOLA MUNDO\"",
                "10 REM THIS IS A COMMENT : WITH COLON",
                "10 DATA 1,2,3 : MORE DATA",
                "10 PR. \"X\"\n20 POK. 100,1\n30 C. 1\n40 G. 40\n50 GO TO 50\n60 ? \"HI\"",
                "10 IF X=1 THEN PRINT \"A\" : PRINT \"B\"",
                "10 IF X=1 THEN 100",
                "10 A$=\"HI\" : PRINT A$",
                "10 DIM A(10) : A(1)=5 : PRINT A(1)",
                "10 DIM A$(10),B(5,5)",
                "10 A$(2,4)=\"XY\"",
                "10 PRINT ABS(-5)+SQR(4)",
                "10 ? USR(ADR(\"A\"),1,2)",
                "10 PRINT (1+2)*3\n20 PRINT 1+2*3",
                "10 PRINT ((A+B)*C)^2",
                "10 C=1 : B=2 : A=C+B : PRINT A+B+C",
                "10 FOR I=1 TO 10 STEP 2 : NEXT I",
                "10 FOR I=1 TO 3",
                "10 PUT #1,65 : GET #1,A",
                "10 print \"x\" : a=1 : print a",
                "10 OPEN #1,4,0,\"D:FILE\"",
                "10 PRINT \"A\";\"B\",X",
                "10 GOSUB 100\n100 RETURN",
                "10 IF A$<=\"Y\" THEN ? 1",
                "10 PRINT 2^3^2",
                "10 IF NOT A AND B OR C THEN ? 1",
                "10 PRINT -X^2",
                "10 GRAPHICS 2 : COLOR 1 : PLOT 0,0 : DRAWTO 10,10",
                "10 ON X GOTO 100,200,300",
                "10 SETCOLOR 2,0,14 : SOUND 0,100,10,8",
                "10 READ A : RESTORE",
                "10 XIO 18,#6,0,0,\"S:\"",
                "10 LOCATE 10,10,X\n20 STATUS #1,A\n30 NOTE #1,A,B\n40 CLOSE #1",
                "10 COM A(10),B$(20)",
                "10 CONT : DEG : NEW : STOP : POP : BYE : CLR",
                "10 A=B=C",
                "10 ? 1=1",
                "10 A$ (3)=\"X\"",
                "10 A$(I+1,J*2)=\"X\"",
        };
        for (String source : sources) {
            byte[] first = tokenize(source);
            String text = AtariBasicDetokenizer.detokenize(first);
            byte[] second = tokenize(text);
            assertArrayEquals(first, second, "roundtrip failed for: " + source + " -> " + text);
        }
    }
}
