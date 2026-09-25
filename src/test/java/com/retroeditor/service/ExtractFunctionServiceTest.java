package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtractFunctionServiceTest {

    private final ExtractFunctionService service = new ExtractFunctionService();

    private static final String CODE =
        "void bar() {\n"
        + "    int v = compute();\n"
        + "}\n"
        + "\n"
        + "void foo() {\n"
        + "    int x = 2;\n"
        + "    apply(x);\n"
        + "    finish(x);\n"
        + "}\n";

    @Test
    void extractsSelectionToNewFunctionBeforeEnclosingFunction() {
        String selected = "    apply(x);\n";
        int start = CODE.indexOf(selected);
        var result = service.extract(CODE, "apply", null, start, start + selected.length());

        assertEquals("", result.diagnostic());
        String newText = result.newText();
        int first = newText.indexOf("static void apply");
        assertTrue(first >= 0);
        int foo = newText.indexOf("void foo()");
        assertTrue(first < foo);
        assertTrue(newText.indexOf("apply()") > foo);
        // La llamada sustituye a la selección dentro de foo; el bloque queda en la nueva función.
        assertTrue(newText.substring(foo).contains("    apply();\n    finish(x);"));
        String fn = newText.substring(first, newText.indexOf("}", first));
        assertTrue(fn.contains("    apply(x);"));
        assertEquals(1, newText.split("void foo\\(\\)").length - 1);
        assertTrue(newText.split("void bar\\(\\)").length - 1 == 1);
    }

    @Test
    void caretLandsAtEndOfCall() {
        String code = "void f() {\n    setPixel(10, 20);\n    repaint();\n}\n";
        String selected = "setPixel(10, 20)";
        int start = code.indexOf(selected);
        var result = service.extract(code, "draw", "unsigned x, unsigned y", start, start + selected.length());

        String newText = result.newText();
        int callStart = newText.indexOf("draw(x, y)");
        assertTrue(callStart >= 0);
        assertEquals(callStart + "draw(x, y)".length(), result.caretPosition());
        assertTrue(result.caretPosition() > newText.indexOf("void f()"));
    }

    @Test
    void firstFunctionInFileInsertsAtBof() {
        String code = "void solo() {\n    init();\n    tick();\n}\n";
        String selected = "    tick();\n";
        int start = code.indexOf(selected);
        var result = service.extract(code, "tick", "", start, start + selected.length());

        assertEquals("", result.diagnostic());
        assertTrue(result.newText().startsWith("static void tick(void)"));
        assertTrue(result.newText().indexOf("static void tick") < result.newText().indexOf("void solo()"));
        assertTrue(result.newText().contains("void solo() {\n    init();\n    tick();\n}"));
    }

    @Test
    void usesProvidedParameterNamesInCall() {
        String code = "void f() {\n    int a = g2(3, 4);\n}\n";
        String selected = "g2(3, 4)";
        int start = code.indexOf(selected);
        var result = service.extract(code, "renamed", "int left, int right", start, start + selected.length());

        assertEquals("", result.diagnostic());
        String newText = result.newText();
        assertTrue(newText.contains("static void renamed(int left, int right)"));
        assertTrue(newText.contains("renamed(left, right)"));
    }

    @Test
    void voidParamsGenerateEmptyCall() {
        String code = "void f() {\n    ping();\n}\n";
        String selected = "ping()";
        int start = code.indexOf(selected);
        var result = service.extract(code, "p", null, start, start + selected.length());

        assertEquals("", result.diagnostic());
        String newText = result.newText();
        assertTrue(newText.contains("static void p(void)"));
        assertTrue(newText.contains("p()"));
    }

    @Test
    void rejectsInvalidFunctionName() {
        String selected = "    apply(x);\n";
        int start = CODE.indexOf(selected);
        var result = service.extract(CODE, "1bad", null, start, start + selected.length());

        assertTrue(!result.diagnostic().isEmpty());
        assertEquals(CODE, result.newText());
    }

    @Test
    void rejectsEmptySelection() {
        var result = service.extract(CODE, "x", null, 5, 5);

        assertTrue(!result.diagnostic().isEmpty());
        assertEquals(CODE, result.newText());
    }

    @Test
    void insertsAfterPreviousTopLevelTypeDeclaration() {
        String code =
            "typedef struct {\n"
            + "    int data;\n"
            + "} Info;\n"
            + "\n"
            + "void f() {\n"
            + "    alloc(&global);\n"
            + "}\n";
        String selected = "    alloc(&global);\n";
        int start = code.indexOf(selected);
        var result = service.extract(code, "fill", null, start, start + selected.length());

        assertEquals("", result.diagnostic());
        String newText = result.newText();
        // La nueva función no rompe la declaración del typedef y queda antes de void f().
        assertTrue(newText.contains("} Info;\nstatic void fill"));
        assertTrue(newText.indexOf("static void fill") < newText.indexOf("void f()"));
        assertTrue(newText.indexOf("} Info;") < newText.indexOf("static void fill"));
    }
}