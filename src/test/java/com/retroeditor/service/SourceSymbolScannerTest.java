package com.retroeditor.service;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SourceSymbolScannerTest {

    @Test
    void functionRangeSpansSignatureToClosingBrace() {
        String code = "int a;\nvoid foo() {\n    x();\n}\n\nvoid bar(void);\n";
        List<SourceSymbolScanner.SourceSymbol> symbols = SourceSymbolScanner.scan(code);

        SourceSymbolScanner.SourceSymbol foo = symbols.stream()
                .filter(s -> "foo".equals(s.getName())).findFirst().orElseThrow();
        Assertions.assertEquals(2, foo.getStartLine());
        Assertions.assertEquals(4, foo.getEndLine());
        Assertions.assertEquals("void foo() {", foo.getSignature());

        SourceSymbolScanner.SourceSymbol bar = symbols.stream()
                .filter(s -> "bar".equals(s.getName())).findFirst().orElseThrow();
        Assertions.assertEquals(6, bar.getStartLine());
        Assertions.assertEquals(6, bar.getEndLine());
    }

    @Test
    void labelInsideFunctionIsDiscardedButAsmLabelsAreKept() {
        String cCode = "void f() {\n    switch (x) {\n    default:\n        break;\n    }\n}\n";
        List<SourceSymbolScanner.SourceSymbol> cSymbols = SourceSymbolScanner.scan(cCode);
        Assertions.assertTrue(cSymbols.stream().noneMatch(s -> "default".equals(s.getName())));

        String asmCode = "start:\n    nop\nplayer:\n    ld a, 1";
        SourceSymbolScanner.SourceSymbol player = SourceSymbolScanner.scan(asmCode).stream()
                .filter(s -> "player".equals(s.getName())).findFirst().orElseThrow();
        Assertions.assertEquals(3, player.getStartLine());
        Assertions.assertEquals(4, player.getEndLine());
    }

    @Test
    void unclosedRegionReachesEndOfFile() {
        String code = "#region physics\n    nop";
        SourceSymbolScanner.SourceSymbol region = SourceSymbolScanner.scan(code).stream()
                .filter(s -> "region".equals(s.getType())).findFirst().orElseThrow();
        Assertions.assertEquals(1, region.getStartLine());
        Assertions.assertEquals(2, region.getEndLine());
    }

    @Test
    void structForwardDeclarationStillProducesStructSymbol() {
        String code = "struct Player;\n";
        List<SourceSymbolScanner.SourceSymbol> symbols = SourceSymbolScanner.scan(code);
        Assertions.assertEquals(1, symbols.size());
        Assertions.assertEquals("struct", symbols.get(0).getType());
        Assertions.assertEquals("Player", symbols.get(0).getName());
    }
}
