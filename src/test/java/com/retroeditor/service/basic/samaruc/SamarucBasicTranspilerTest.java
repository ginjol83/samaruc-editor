package com.retroeditor.service.basic.samaruc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.retroeditor.service.atari.basic.AtariBasicTokenizer;
import org.junit.jupiter.api.Test;

class SamarucBasicTranspilerTest {

    private static String transpile(String source) throws SamarucBasicException {
        return SamarucBasicTranspiler.transpile(source);
    }

    @Test
    void leavesSourceUntouchedWithoutPragma() throws SamarucBasicException {
        String source = "10 PRINT \"HOLA\"\n20 END\n";
        assertSame(source, SamarucBasicTranspiler.transpile(source));
    }

    @Test
    void acceptsLegacyModernPragmaAlias() throws SamarucBasicException {
        String source = "REM @modern on\n10 PRINT \"X\"\n20 END\n";
        String output = transpile(source);
        assertEquals("10 PRINT \"X\"\n20 END\n", output);
    }

    @Test
    void transpilesNumericFunctionWithParamsLocalsAndReturn() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 D=DIST(0,0,3,4)",
                "20 END",
                "1000 FUNCTION DIST(X1,Y1,X2,Y2)",
                "1010 LOCAL DX,DY",
                "1020 DX=X2-X1",
                "1030 DY=Y2-Y1",
                "1040 RETURN SQR(DX*DX+DY*DY)",
                "1050 END FUNCTION") + "\n";

        String expected = String.join("\n",
                "1 DIM P(4):DIM L(6)",
                "10 P(0)=0: P(1)=0: P(2)=3: P(3)=4: GOSUB 1000: D=R",
                "20 END",
                "1000 L(0)=P(0): L(1)=P(1): L(2)=P(2): L(3)=P(3)",
                "1010 REM",
                "1020 L(4)=L(2)-L(0)",
                "1030 L(5)=L(3)-L(1)",
                "1040 R=SQR(L(4)*L(4)+L(5)*L(5)): RETURN",
                "1050 RETURN") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void transpilesProcedureCall() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 BEEP(3)",
                "20 END",
                "100 PROCEDURE BEEP(N)",
                "110 POKE 53279,N",
                "120 END PROCEDURE") + "\n";

        String output = transpile(source);
        assertTrue(output.contains("10 P(0)=3: GOSUB 100\n"), output);
        assertTrue(output.contains("1 DIM P(1):DIM L(1)\n"), output);
        assertTrue(output.contains("100 L(0)=P(0)\n"), output);
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(output));
    }

    @Test
    void transpilesStringReturningFunction() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 N$=NAME$(5)",
                "20 END",
                "100 FUNCTION NAME$(N)",
                "110 RETURN \"HI\"",
                "120 END FUNCTION") + "\n";

        String output = transpile(source);
        assertTrue(output.contains("10 P(0)=5: GOSUB 100: N$=R$\n"), output);
        assertTrue(output.contains("110 R$=\"HI\": RETURN\n"), output);
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(output));
    }

    @Test
    void pragmaWithoutLineNumberIsRemoved() throws SamarucBasicException {
        String source = "REM @samaruc on\n10 PRINT \"X\"\n20 END\n";
        String output = transpile(source);
        assertEquals("10 PRINT \"X\"\n20 END\n", output);
    }

    @Test
    void rejectsArgumentCountMismatch() {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 D=DIST(1)",
                "20 END",
                "100 FUNCTION DIST(A,B)",
                "110 RETURN A+B",
                "120 END FUNCTION") + "\n";

        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("espera 2"), ex.getMessage());
        assertEquals(10, ex.getLine());
    }

    @Test
    void rejectsNestedCallInArgument() {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 D=DIST(1,2,3,DIST(0,0,0,0))",
                "20 END",
                "100 FUNCTION DIST(A,B,C,E)",
                "110 RETURN A+B+C+E",
                "120 END FUNCTION") + "\n";

        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("anidada"), ex.getMessage());
    }

    @Test
    void rejectsCallAsEmbeddedExpression() {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 PRINT DIST(1,2,3,4)",
                "20 END",
                "100 FUNCTION DIST(A,B,C,E)",
                "110 RETURN A+B+C+E",
                "120 END FUNCTION") + "\n";

        assertThrows(SamarucBasicException.class, () -> transpile(source));
    }

    @Test
    void rejectsAssignmentToProcedure() {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 X=BEEP(3)",
                "20 END",
                "100 PROCEDURE BEEP(N)",
                "110 POKE 53279,N",
                "120 END PROCEDURE") + "\n";

        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("PROCEDURE"), ex.getMessage());
    }

    @Test
    void rejectsMissingEnd() {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 D=FOO(1)",
                "20 END",
                "100 FUNCTION FOO(A)",
                "110 RETURN A") + "\n";

        assertThrows(SamarucBasicException.class, () -> transpile(source));
    }

    @Test
    void rejectsReturnWithValueOutsideFunction() {
        String source = "REM @samaruc on\n10 RETURN 5\n20 END\n";
        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("fuera de una funcion"), ex.getMessage());
    }

    @Test
    void rejectsStringParameters() {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 FUNCTION FOO(A$)",
                "110 RETURN 1",
                "120 END FUNCTION") + "\n";

        assertThrows(SamarucBasicException.class, () -> transpile(source));
    }

    @Test
    void rejectsDuplicateFunction() {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 FUNCTION FOO(A)",
                "110 RETURN A",
                "120 END FUNCTION",
                "200 FUNCTION FOO(B)",
                "210 RETURN B",
                "220 END FUNCTION") + "\n";

        assertThrows(SamarucBasicException.class, () -> transpile(source));
    }

    @Test
    void rejectsLocalOutsideFunction() {
        String source = "REM @samaruc on\n10 LOCAL X\n20 END\n";
        assertThrows(SamarucBasicException.class, () -> transpile(source));
    }

    @Test
    void transpilesIfElseEndIf() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 IF A>0 THEN",
                "110 PRINT \"POS\"",
                "120 ELSE",
                "130 PRINT \"NEG\"",
                "140 END IF",
                "150 END") + "\n";

        String expected = String.join("\n",
                "100 IF NOT (A>0) THEN 130",
                "110 PRINT \"POS\"",
                "120 GOTO 150",
                "130 PRINT \"NEG\"",
                "140 REM",
                "150 END") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void transpilesIfWithoutElse() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 IF A>0 THEN",
                "110 PRINT \"POS\"",
                "120 ENDIF",
                "130 END") + "\n";

        String expected = String.join("\n",
                "100 IF NOT (A>0) THEN 120",
                "110 PRINT \"POS\"",
                "120 REM",
                "130 END") + "\n";

        assertEquals(expected, transpile(source));
    }

    @Test
    void transpilesWhileEndWhile() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "200 WHILE I<10",
                "210 I=I+1",
                "220 ENDWHILE",
                "230 PRINT I") + "\n";

        String expected = String.join("\n",
                "200 IF NOT (I<10) THEN 230",
                "210 I=I+1",
                "220 GOTO 200",
                "230 PRINT I") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void acceptsWendAndEndWhileAliases() throws SamarucBasicException {
        String wend = String.join("\n",
                "REM @samaruc on",
                "10 WHILE A",
                "20 A=A-1",
                "30 WEND",
                "40 END") + "\n";
        String endWhile = String.join("\n",
                "REM @samaruc on",
                "10 WHILE A",
                "20 A=A-1",
                "30 END WHILE",
                "40 END") + "\n";

        assertEquals(transpile(wend), transpile(endWhile));
        assertTrue(transpile(wend).contains("30 GOTO 10\n"));
    }

    @Test
    void transpilesRepeatUntil() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "300 REPEAT",
                "310 I=I-1",
                "320 UNTIL I=0",
                "330 END") + "\n";

        String expected = String.join("\n",
                "300 REM",
                "310 I=I-1",
                "320 IF NOT (I=0) THEN 300",
                "330 END") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void transpilesDoLoopVariants() throws SamarucBasicException {
        String plain = String.join("\n",
                "REM @samaruc on",
                "400 DO",
                "410 I=I+1",
                "420 LOOP",
                "430 END") + "\n";
        String loopWhile = String.join("\n",
                "REM @samaruc on",
                "400 DO",
                "410 I=I+1",
                "420 LOOP WHILE I<10",
                "430 END") + "\n";
        String loopUntil = String.join("\n",
                "REM @samaruc on",
                "400 DO",
                "410 I=I+1",
                "420 LOOP UNTIL I>=10",
                "430 END") + "\n";

        assertTrue(transpile(plain).contains("420 GOTO 400\n"));
        assertTrue(transpile(loopWhile).contains("420 IF (I<10) THEN 400\n"));
        assertTrue(transpile(loopUntil).contains("420 IF NOT (I>=10) THEN 400\n"));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(loopWhile)));
    }

    @Test
    void transpilesNestedStructuredBlocks() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 WHILE I<10",
                "110 IF I/2=0 THEN",
                "120 PRINT \"PAR\"",
                "130 ELSE",
                "140 PRINT \"IMPAR\"",
                "150 ENDIF",
                "160 I=I+1",
                "170 ENDWHILE",
                "180 END") + "\n";

        String expected = String.join("\n",
                "100 IF NOT (I<10) THEN 180",
                "110 IF NOT (I/2=0) THEN 140",
                "120 PRINT \"PAR\"",
                "130 GOTO 160",
                "140 PRINT \"IMPAR\"",
                "150 REM",
                "160 I=I+1",
                "170 GOTO 100",
                "180 END") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void substitutesLocalsInsideStructuredConditions() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 X=SIGN(5)",
                "20 END",
                "100 FUNCTION SIGN(A)",
                "110 IF A>0 THEN",
                "120 RETURN 1",
                "130 ENDIF",
                "140 RETURN 0",
                "150 END FUNCTION") + "\n";

        String output = transpile(source);
        assertTrue(output.contains("110 IF NOT (L(0)>0) THEN 130\n"), output);
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(output));
    }

    @Test
    void rejectsUnclosedBlock() {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 WHILE A",
                "110 A=A-1",
                "120 END") + "\n";

        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("sin cerrar"), ex.getMessage());
    }

    @Test
    void rejectsEndIfWithoutIf() {
        String source = "REM @samaruc on\n100 PRINT \"X\"\n110 ENDIF\n120 END\n";
        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("ENDIF"), ex.getMessage());
        assertEquals(110, ex.getLine());
    }

    @Test
    void rejectsElseWithoutIf() {
        String source = "REM @samaruc on\n100 PRINT \"X\"\n110 ELSE\n120 END\n";
        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("ELSE"), ex.getMessage());
    }

    @Test
    void rejectsBlockCrossingFunctionBoundary() {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 WHILE A",
                "200 FUNCTION FOO(X)",
                "210 RETURN X",
                "220 END FUNCTION",
                "300 ENDWHILE") + "\n";

        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("cruzar"), ex.getMessage());
    }

    @Test
    void rejectsElseAfterElse() {
        String source = String.join("\n",
                "REM @samaruc on",
                "100 IF A THEN",
                "110 ELSE",
                "120 ELSE",
                "130 ENDIF",
                "140 END") + "\n";

        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("ELSE"), ex.getMessage());
    }

    @Test
    void singleLineIfIsLeftUntouched() throws SamarucBasicException {
        String source = "REM @samaruc on\n10 IF A>0 THEN PRINT \"POS\"\n20 END\n";
        String output = transpile(source);
        assertTrue(output.contains("10 IF A>0 THEN PRINT \"POS\"\n"), output);
    }

    @Test
    void transpilesAndTokenizesFullDemo() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "CONST MAXV = 10",
                "CONST ETIQUETA = \"VUELTA\"",
                "10 PRINT CHR$(125)",
                "20 PRINT \"BASIC EXTENDIDO - DEMO\"",
                "30 PRINT",
                "40 D = DIST(0, 0, 3, 4)",
                "50 PRINT \"DISTANCIA 0,0 A 3,4 = \"; D",
                "60 S = SUMA(5)",
                "70 PRINT \"SUMA 1..5 = \"; S",
                "80 DOBLE(7)",
                "90 PRINT",
                "100 I = 0",
                "110 WHILE I < MAXV",
                "120 PRINT ETIQUETA; I",
                "130 I += 1",
                "140 ENDWHILE",
                "150 IF D > 4 THEN",
                "160 PRINT \"DISTANCIA LARGA\"",
                "170 ELSE",
                "180 PRINT \"DISTANCIA CORTA\"",
                "190 END IF",
                "200 J = 3",
                "210 REPEAT",
                "220 PRINT \"CUENTA \"; J",
                "230 DEC J",
                "240 UNTIL J = 0",
                "250 PRINT",
                "260 PRINT \"FIN\"",
                "270 END",
                "1000 FUNCTION DIST(X1, Y1, X2, Y2)",
                "1010 LOCAL DX, DY",
                "1020 DX = X2 - X1",
                "1030 DY = Y2 - Y1",
                "1040 RETURN SQR(DX*DX + DY*DY)",
                "1050 END FUNCTION",
                "1100 FUNCTION SUMA(N)",
                "1110 LOCAL T, I",
                "1120 T = 0",
                "1130 I = 1",
                "1140 WHILE I <= N",
                "1150 T += I",
                "1160 INC I",
                "1170 ENDWHILE",
                "1180 RETURN T",
                "1190 END FUNCTION",
                "1200 PROCEDURE DOBLE(N)",
                "1210 LOCAL D",
                "1220 D = N * 2",
                "1230 PRINT \"EL DOBLE DE \"; N; \" ES \"; D",
                "1240 END PROCEDURE") + "\n";

        String output = transpile(source);
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(output));
        assertTrue(output.contains("110 IF NOT (I < 10) THEN 150\n"), output);
        assertTrue(output.contains("120 PRINT \"VUELTA\"; I\n"), output);
        assertTrue(output.contains("130 I = I + 1\n"), output);
        assertTrue(output.contains("140 GOTO 110\n"), output);
        assertTrue(output.contains("170 GOTO 200\n"), output);
        assertTrue(output.contains("230 J = J - 1\n"), output);
        assertTrue(output.contains("240 IF NOT (J = 0) THEN 210\n"), output);
        assertTrue(output.contains("1150 L(7) = L(7) + L(8)\n"), output);
        assertTrue(output.contains("1160 L(8) = L(8) + 1\n"), output);
        assertTrue(output.contains("1170 GOTO 1140\n"), output);
    }

    @Test
    void substitutesConstantsInBodiesAndConditions() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "CONST MAX = 10",
                "CONST SALUDO = \"HOLA\"",
                "10 IF N > MAX THEN",
                "20 N = 0",
                "30 ENDIF",
                "40 PRINT SALUDO",
                "50 END") + "\n";

        String expected = String.join("\n",
                "10 IF NOT (N > 10) THEN 30",
                "20 N = 0",
                "30 REM",
                "40 PRINT \"HOLA\"",
                "50 END") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void resolvesConstantsThatReferenceOtherConstants() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "CONST A = 3",
                "CONST B = A + 1",
                "10 X = B",
                "20 END") + "\n";

        assertTrue(transpile(source).contains("10 X = 3 + 1\n"), transpile(source));
    }

    @Test
    void localShadowsConstant() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "CONST N = 5",
                "10 X = F(1)",
                "20 END",
                "100 FUNCTION F(N)",
                "110 RETURN N",
                "120 END FUNCTION") + "\n";

        String output = transpile(source);
        assertTrue(output.contains("110 R=L(0): RETURN\n"), output);
    }

    @Test
    void transpilesCompoundAssignment() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 I = 0",
                "20 I += 5",
                "30 I -= 2",
                "40 I *= 3",
                "50 I /= 4",
                "60 A(0) += 1",
                "70 END") + "\n";

        String expected = String.join("\n",
                "10 I = 0",
                "20 I = I + 5",
                "30 I = I - 2",
                "40 I = I * 3",
                "50 I = I / 4",
                "60 A(0) = A(0) + 1",
                "70 END") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void transpilesStringCompoundAssignmentTextually() throws SamarucBasicException {
        String source = "REM @samaruc on\n10 S$ += \"X\"\n20 END\n";
        assertTrue(transpile(source).contains("10 S$ = S$ + \"X\"\n"), transpile(source));
    }

    @Test
    void parenthesizesCompoundRhsOnlyWhenNeeded() throws SamarucBasicException {
        String source = "REM @samaruc on\n10 I = 0\n20 I *= 2 + 3\n30 END\n";
        assertTrue(transpile(source).contains("20 I = I * (2 + 3)\n"), transpile(source));
    }

    @Test
    void transpilesIncDec() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "10 I = 0",
                "20 INC I",
                "30 DEC I",
                "40 DEC I, 3",
                "50 INC A(2), 4",
                "60 END") + "\n";

        String expected = String.join("\n",
                "10 I = 0",
                "20 I = I + 1",
                "30 I = I - 1",
                "40 I = I - 3",
                "50 A(2) = A(2) + 4",
                "60 END") + "\n";

        assertEquals(expected, transpile(source));
        assertDoesNotThrow(() -> AtariBasicTokenizer.tokenize(transpile(source)));
    }

    @Test
    void constantCombinedWithCompoundAssignment() throws SamarucBasicException {
        String source = String.join("\n",
                "REM @samaruc on",
                "CONST STEP = 2",
                "10 I = 0",
                "20 I += STEP",
                "30 END") + "\n";

        assertTrue(transpile(source).contains("20 I = I + 2\n"));
    }

    @Test
    void rejectsCyclicConstants() {
        String source = "REM @samaruc on\nCONST A = B\nCONST B = A\n10 X = A\n20 END\n";
        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("Ciclo"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateConstant() {
        String source = "REM @samaruc on\nCONST A = 1\nCONST A = 2\n10 END\n";
        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("duplicada"), ex.getMessage());
    }

    @Test
    void rejectsReservedConstantName() {
        String source = "REM @samaruc on\nCONST R = 1\n10 END\n";
        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("reservado"), ex.getMessage());
    }

    @Test
    void rejectsConstantCollidingWithFunction() {
        String source = String.join("\n",
                "REM @samaruc on",
                "CONST FOO = 5",
                "10 X = FOO(1)",
                "20 END",
                "100 FUNCTION FOO(A)",
                "110 RETURN A",
                "120 END FUNCTION") + "\n";

        SamarucBasicException ex = assertThrows(SamarucBasicException.class, () -> transpile(source));
        assertTrue(ex.getMessage().contains("colisiona"), ex.getMessage());
    }
}
