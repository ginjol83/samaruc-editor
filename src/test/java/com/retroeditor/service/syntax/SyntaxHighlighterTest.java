package com.retroeditor.service.syntax;

import java.util.Collection;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SyntaxHighlighterTest {

    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();

    @Test
    void highlightsZxSnippetWithAccentsAndKeepsSpanLength() {
        String text = "#include <conio.h>\r\n"
            + "// Funcion para dibujar la mansion con graficos reales de ZX Spectrum\r\n"
            + "void dibujar_mansion() {\r\n"
            + "    textcolor(15); textbackground(0);\r\n"
            + "    gotoxy(26, 1); putch(143);\r\n"
            + "}\r\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertEquals(text.length(), totalLength(spans));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("#include"), "preprocessor"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("// Funcion"), "comment"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("void"), "keyword"));
    }

    @Test
    void singleLineCommentDoesNotBleedWhenTextUsesCarriageReturnOnly() {
        String text = "#include <conio.h>\r"
            + "// Comentario con acento o\r"
            + "void main() { return; }\r";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        int voidIndex = text.indexOf("void");
        Assertions.assertTrue(hasStyleAt(spans, voidIndex, "keyword"));
        Assertions.assertFalse(hasStyleAt(spans, voidIndex, "comment"));
    }

    @Test
    void highlightsCommonZxFunctions() {
        String text = "void main() {\n"
            + "    textcolor(7);\n"
            + "    gotoxy(5, 5);\n"
            + "    cputs(\"ZX\");\n"
            + "    if (kbhit()) { getch(); }\n"
            + "}\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("textcolor"), "zx-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("gotoxy"), "zx-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("cputs"), "zx-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("kbhit"), "zx-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("getch"), "zx-function"));
    }

    @Test
    void doesNotHighlightZxFunctionsInsideCommentsStringsOrSubIdentifiers() {
        String text = "// gotoxy en comentario\n"
            + "char* s = \"cputs en string\";\n"
            + "int my_gotoxy_helper = 0;\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("gotoxy"), "zx-function"));
        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("cputs"), "zx-function"));
        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("gotoxy", text.indexOf("my_gotoxy_helper")), "zx-function"));
    }

    @Test
    void doesNotHighlightZxFunctionNameWhenUsedAsVariable() {
        String text = "void main(void) {\n"
            + "    int textcolor = 7;\n"
            + "    int gotoxy = 3;\n"
            + "    gotoxy++;\n"
            + "}\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("textcolor"), "zx-function"));
        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("gotoxy"), "zx-function"));
    }

    @Test
    void highlightsCommonGbdkFunctions() {
        String text = "void main(void) {\n"
            + "    wait_vbl_done();\n"
            + "    set_sprite_data(0, 1, 0);\n"
            + "    set_sprite_tile(0, 0);\n"
            + "    move_sprite(0, 32, 64);\n"
            + "    joypad();\n"
            + "    set_sprite_prop(0, 0);\n"
            + "    move_bkg(1, 0);\n"
            + "    scroll_win(0, 1);\n"
            + "    waitpad(0x10);\n"
            + "}\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("wait_vbl_done"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("set_sprite_data"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("set_sprite_tile"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("move_sprite"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("joypad"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("set_sprite_prop"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("move_bkg"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("scroll_win"), "gbdk-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("waitpad"), "gbdk-function"));
    }

    @Test
    void doesNotHighlightGbdkFunctionsInsideCommentsStringsOrSubIdentifiers() {
        String text = "// wait_vbl_done en comentario\n"
            + "char* s = \"joypad en string\";\n"
            + "int my_wait_vbl_done_helper = 0;\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("wait_vbl_done"), "gbdk-function"));
        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("joypad"), "gbdk-function"));
        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("wait_vbl_done", text.indexOf("my_wait_vbl_done_helper")), "gbdk-function"));
    }

    @Test
    void doesNotHighlightGbdkFunctionNameWhenUsedAsVariable() {
        String text = "void main(void) {\n"
            + "    int wait_vbl_done = 1;\n"
            + "    int move_bkg = 2;\n"
            + "    wait_vbl_done++;\n"
            + "}\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("wait_vbl_done"), "gbdk-function"));
        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("move_bkg"), "gbdk-function"));
    }

    @Test
    void highlightsZxAndGbdkFunctionsInSameBuffer() {
        String text = "void main() {\n"
            + "    gotoxy(1,1);\n"
            + "    wait_vbl_done();\n"
            + "}\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("gotoxy"), "zx-function"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("wait_vbl_done"), "gbdk-function"));
    }

    @Test
    void tildeOperatorDoesNotBreakHighlighting() {
        String text = "int flags = 3;\n"
            + "int mask = ~flags;\n"
            + "return ~mask;\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text);

        Assertions.assertEquals(text.length(), totalLength(spans));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("~flags"), "operator"));
        Assertions.assertTrue(hasStyleAt(spans, text.lastIndexOf('~'), "operator"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("int"), "keyword"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("return"), "keyword"));
    }

    @Test
    void highlightsMarkdownWhenFileExtensionIsMd() {
        String text = "# Title\n"
            + "- item\n"
            + "Use `code` and [link](https://example.com)\n"
            + "```c\nint x = 1;\n```\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text, "README.md");

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("# Title"), "md-heading"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("- item"), "md-list"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("`code`"), "md-code"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("[link]"), "md-link"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("```c"), "md-code-block"));
    }

    @Test
    void keepsCHighlightingForNonMarkdownFiles() {
        String text = "int main(void) { return 0; }\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text, "main.c");

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("int"), "keyword"));
        Assertions.assertFalse(hasStyleAt(spans, text.indexOf("int"), "md-heading"));
    }

    @Test
    void highlightsAssemblySyntaxForAsmFiles() {
        String text = "start:\n"
            + "    ld a, $10 ; load value\n"
            + "    jp loop\n"
            + "loop:\n"
            + "    db \"A\", 0\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text, "main.asm");

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("start:"), "asm-label"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("ld"), "asm-instruction"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("a, $10"), "asm-register"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("$10"), "asm-number"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("db"), "asm-directive"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("; load"), "comment"));
    }

    @Test
    void highlightsAssemblySyntaxForSFiles() {
        String text = "org $4000\n"
            + "main:\n"
            + "    call init\n"
            + "    ret\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text, "boot.s");

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("org"), "asm-directive"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("main:"), "asm-label"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("call"), "asm-instruction"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("ret"), "asm-instruction"));
    }

    @Test
    void highlightsMarkdownTablesAndChecklists() {
        String text = "| Name | Value |\n"
            + "| ---- | ----- |\n"
            + "| A    | 1     |\n"
            + "- [ ] pending item\n"
            + "- [x] done item\n";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlighting(text, "notes.md");

        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("| Name | Value |"), "md-table"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("| ---- | ----- |"), "md-table"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("| A    | 1     |"), "md-table"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("- [ ] pending item"), "md-checklist"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("- [ ] pending item"), "md-checklist-pending"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("- [x] done item"), "md-checklist"));
        Assertions.assertTrue(hasStyleAt(spans, text.indexOf("- [x] done item"), "md-checklist-done"));

        String upperDone = "- [X] done uppercase\n";
        StyleSpans<Collection<String>> upperDoneSpans = highlighter.computeHighlighting(upperDone, "notes.md");
        Assertions.assertTrue(hasStyleAt(upperDoneSpans, upperDone.indexOf("- [X]"), "md-checklist-done"));
    }

    private boolean hasStyleAt(StyleSpans<Collection<String>> spans, int index, String styleClass) {
        if (index < 0) return false;

        int offset = 0;
        for (StyleSpan<Collection<String>> span : spans) {
            int next = offset + span.getLength();
            if (index >= offset && index < next) {
                return span.getStyle().contains(styleClass);
            }
            offset = next;
        }

        return false;
    }

    private int totalLength(StyleSpans<Collection<String>> spans) {
        int total = 0;
        for (StyleSpan<Collection<String>> span : spans) {
            total += span.getLength();
        }
        return total;
    }
}
