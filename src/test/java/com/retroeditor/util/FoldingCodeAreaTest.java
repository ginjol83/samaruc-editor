package com.retroeditor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FoldingCodeAreaTest {

    @Test
    void foldReplacesRangeBySingleMarkerLine() {
        String src = "void loop() {\n    a();\n    b();\n}\n";
        String folded = FoldingCodeArea.foldToText(src, 2, 3, "▾ [2 líneas] @token-1");
        assertEquals("void loop() {\n    ▾ [2 líneas] @token-1\n}\n", folded);
    }

    @Test
    void foldPreservesIndentOfFirstHiddenLine() {
        String src = "if (ok) {\n\tx();\n\ty();\n}\n";
        String folded = FoldingCodeArea.foldToText(src, 2, 3, "▾ [2 líneas] @tk");
        assertTrue(folded.startsWith("if (ok) {\n\t▾ [2 líneas] @tk\n"));
    }

    @Test
    void expandRestoresOriginalTextExactly() {
        String src = "int f() {\n    return 1;\n    return 2;\n}\n// fin\n";
        String marker = "▾ [2 líneas] @tk-99";
        String folded = FoldingCodeArea.foldToText(src, 2, 3, marker);
        String expanded = FoldingCodeArea.expandToText(folded, "@tk-99", src.substring(
                FoldingCodeArea.lineStartOffset(src, 2),
                FoldingCodeArea.lineStartOffset(src, 4)));
        assertEquals(src, expanded);
    }

    @Test
    void foldKeepsLineAfterRangeIntact() {
        String src = "a();\nb();\nc();\nd();\n";
        String folded = FoldingCodeArea.foldToText(src, 2, 3, "▾ @t");
        assertEquals("a();\n▾ @t\nd();\n", folded);
    }

    @Test
    void foldOnLastLineWorksAndExpandsBack() {
        String src = "a();\nb();";
        String marker = "▾ @t";
        String folded = FoldingCodeArea.foldToText(src, 1, 2, marker);
        assertEquals("▾ @t\n", folded);
        String expanded = FoldingCodeArea.expandToText(folded, "@t", "a();\nb();");
        assertEquals(src, expanded);
    }

    @Test
    void computeFoldRejectsInvalidRange() {
        assertNull(FoldingCodeArea.computeFold("abc", 0, 3));
        assertNull(FoldingCodeArea.computeFold("abc", 3, 2));
        assertNull(FoldingCodeArea.computeFold("abc\nx\n", 2, 2));
    }

    @Test
    void expandReturnsNullWhenTokenMissing() {
        assertNull(FoldingCodeArea.expandToText("hola\nmundo\n", "@nada", "x"));
    }

    @Test
    void lineStartOffsetIsOneBased() {
        assertEquals(0, FoldingCodeArea.lineStartOffset("aa\nbb\ncc\n", 1));
        assertEquals(3, FoldingCodeArea.lineStartOffset("aa\nbb\ncc\n", 2));
        assertEquals(6, FoldingCodeArea.lineStartOffset("aa\nbb\ncc\n", 3));
    }

    @Test
    void displacementInEarlierLinesDoesNotAffectExpansion() {
        String src = "/* archivo */\nint x;\nint f() {\n    return 1;\n}\n";
        String folded = FoldingCodeArea.foldToText(src, 3, 4, "▾ @tk-1");
        String hidden = src.substring(FoldingCodeArea.lineStartOffset(src, 3),
                FoldingCodeArea.lineStartOffset(src, 5));
        String editedAbove = folded.replace("/* archivo */\n", "/* cabecera */\n// otra linea\n");
        String expanded = FoldingCodeArea.expandToText(editedAbove, "@tk-1", hidden);
        assertTrue(expanded.startsWith("/* cabecera */\n// otra linea\nint x;\nint f() {\n"));
        assertTrue(expanded.endsWith("}\n"));
        assertFalse(expanded.contains("@tk-1"));
    }
}