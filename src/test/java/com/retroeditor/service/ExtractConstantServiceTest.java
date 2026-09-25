package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtractConstantServiceTest {

    private final ExtractConstantService service = new ExtractConstantService();

    @Test
    void insertsDefineAtTopAndReplacesSelection() {
        String code = "int main(void) {\n    return 2048 * 4;\n}\n";
        int start = code.indexOf("2048 * 4");
        var result = service.extract(code, "FRAME_SIZE", start, start + "2048 * 4".length());

        assertEquals("", result.diagnostic());
        assertTrue(result.newText().startsWith("#define FRAME_SIZE 2048 * 4\n"));
        assertTrue(result.newText().contains("return FRAME_SIZE;"));
        int defineLen = "#define FRAME_SIZE 2048 * 4\n".length();
        assertEquals(defineLen + code.indexOf("return ") + "return ".length() + "FRAME_SIZE".length(), result.caretPosition());
    }

    @Test
    void trimsSurroundingWhitespaceOfExpression() {
        String code = "void f() { x =  42  ; }\n";
        int start = code.indexOf(" 42 ");
        var result = service.extract(code, "ANS", start, start + 4);

        assertEquals("", result.diagnostic());
        assertTrue(result.newText().startsWith("#define ANS 42\n"));
    }

    @Test
    void rejectsInvalidIdentifier() {
        String code = "int main(void) { return 1; }\n";
        int start = code.indexOf("return 1");
        var result = service.extract(code, "9BAD", start, start + 1);

        assertTrue(!result.diagnostic().isEmpty());
        assertEquals(code, result.newText());
    }

    @Test
    void rejectsEmptySelection() {
        String code = "int main(void) { return 1; }\n";
        var result = service.extract(code, "X", 10, 10);

        assertTrue(!result.diagnostic().isEmpty());
        assertEquals(code, result.newText());
    }

    @Test
    void rejectsMultilineExpression() {
        String code = "int a = 1 +\n        2;\n";
        int start = code.indexOf("1 +\n");
        var result = service.extract(code, "SUM", start, start + "1 +\n        2".length());

        assertTrue(!result.diagnostic().isEmpty());
    }
}