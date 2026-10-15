package com.retroeditor.service;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SymbolBreadcrumbServiceTest {

    private final SymbolBreadcrumbService service = new SymbolBreadcrumbService();

    private List<String> namesAt(String code, String fileName, int caretLine) {
        return service.getBreadcrumbs(code, fileName, caretLine).stream()
                .map(SymbolBreadcrumbService.BreadcrumbItem::getName)
                .toList();
    }

    private static List<String> namesOf(List<SymbolBreadcrumbService.BreadcrumbItem> items) {
        return items.stream().map(SymbolBreadcrumbService.BreadcrumbItem::getName).toList();
    }

    @Test
    void detectsFileAndFunction() {
        String code = "int globalVar;\n\nvoid main() {\n    return;\n}\n";
        List<SymbolBreadcrumbService.BreadcrumbItem> items = service.getBreadcrumbs(code, "main.c", 3);

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals("main.c", items.get(0).getName());
        Assertions.assertEquals("main", items.get(1).getName());
        Assertions.assertEquals("function", items.get(1).getType());
        Assertions.assertEquals(3, items.get(1).getLine());
    }

    @Test
    void detectsAsmLabelAndRegion() {
        String code = "#region physics\nmove_player:\n    nop\n#endregion\n";
        List<SymbolBreadcrumbService.BreadcrumbItem> items = service.getBreadcrumbs(code, "game.asm", 1);

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals("physics", items.get(1).getName());
        Assertions.assertEquals("region", items.get(1).getType());
    }

    @Test
    void caretInsideFunctionShowsOnlyThatFunction() {
        String code = "void a() {\n}\n\nvoid b() {\n    int x;\n}\n\nvoid c() {\n}\n";

        Assertions.assertEquals(List.of("main.c", "b"), namesAt(code, "main.c", 5));
        Assertions.assertEquals(List.of("main.c", "c"), namesAt(code, "main.c", 8));
    }

    @Test
    void caretBetweenFunctionsShowsOnlyTheFile() {
        String code = "void a() {\n}\n\nvoid b() {\n    int x;\n}\n\nvoid c() {\n}\n";

        Assertions.assertEquals(List.of("main.c"), namesAt(code, "main.c", 3));
    }

    @Test
    void detectsEightBitTypesLikeUint8_t() {
        String code = "uint8_t readInput(void) {\n    return 1;\n}\n";

        Assertions.assertEquals(List.of("main.c", "readInput"), namesAt(code, "main.c", 2));
    }

    @Test
    void detectsMultilineSignatures() {
        String code = "uint8_t\nreadPad(void)\n{\n    return 0;\n}\n";

        Assertions.assertEquals(List.of("main.c", "readPad"), namesAt(code, "main.c", 3));
    }

    @Test
    void defaultLabelInsideFunctionIsNotAScope() {
        String code = "void f() {\n    switch (x) {\n    default:\n        break;\n    }\n}\n";

        List<String> names = namesAt(code, "main.c", 4);
        Assertions.assertEquals(List.of("main.c", "f"), names);
    }

    @Test
    void regionContainingFunctionNestsInOrder() {
        String code = "#region physics\nvoid update() {\n    move();\n}\n#endregion\n";

        Assertions.assertEquals(List.of("game.asm", "physics", "update"), namesAt(code, "game.asm", 3));
    }

    @Test
    void commentMentioningRegionMidSentenceIsNotAScope() {
        String code = "void main() {\n    // la region de memoria se usa aqui\n    return;\n}\n";

        Assertions.assertEquals(List.of("main.c", "main"), namesAt(code, "main.c", 3));
    }

    @Test
    void asmLabelsAreScopesUntilTheNextLabel() {
        String code = "start:\n    nop\nplayer:\n    ld a, 1\n";

        Assertions.assertEquals(List.of("game.asm", "start"), namesAt(code, "game.asm", 2));
        Assertions.assertEquals(List.of("game.asm", "player"), namesAt(code, "game.asm", 4));
    }

    @Test
    void numericAsmLabelsAreDetected() {
        String code = "    ld a, 5\n1:\n    nop\n";

        Assertions.assertEquals(List.of("game.asm", "1"), namesAt(code, "game.asm", 3));
    }

    @Test
    void prototypeDoesNotSwallowTheFollowingLines() {
        String code = "void foo(void);\nint x;\nvoid bar() {\n}\n";

        Assertions.assertEquals(List.of("main.c"), namesAt(code, "main.c", 2));
        Assertions.assertEquals(List.of("main.c", "bar"), namesAt(code, "main.c", 4));
    }

    @Test
    void bracesInsideCommentsDoNotBreakRanges() {
        String code = "void a() {\n    // }\n}\nvoid b() {\n}\n";

        Assertions.assertEquals(List.of("main.c", "a"), namesAt(code, "main.c", 3));
        Assertions.assertEquals(List.of("main.c", "b"), namesAt(code, "main.c", 5));
    }

    @Test
    void topLevelSiblingsGroupAllTopLevelScopes() {
        String code = "void a() {\n}\n\nvoid b() {\n}\n";
        SymbolBreadcrumbService.BreadcrumbIndex index = service.analyze(code, "main.c");

        java.util.List<List<SymbolBreadcrumbService.BreadcrumbItem>> groups = index.siblingsAt(1);

        Assertions.assertEquals(2, groups.size());
        Assertions.assertEquals(List.of("a", "b"), namesOf(groups.get(0)));
        Assertions.assertEquals(List.of("a", "b"), namesOf(groups.get(1)));
    }

    @Test
    void nestedScopeGroupsOnlySiblingsInsideParent() {
        String code = "#region r1\nvoid f() {\n}\n#endregion\nvoid g() {\n}\n";
        SymbolBreadcrumbService.BreadcrumbIndex index = service.analyze(code, "game.asm");

        java.util.List<List<SymbolBreadcrumbService.BreadcrumbItem>> groups = index.siblingsAt(2);

        Assertions.assertEquals(3, groups.size());
        Assertions.assertEquals(List.of("r1", "g"), namesOf(groups.get(0)));
        Assertions.assertEquals(List.of("r1", "g"), namesOf(groups.get(1)));
        Assertions.assertEquals(List.of("f"), namesOf(groups.get(2)));
    }

    @Test
    void fileWithSingleScopeHasNoChevronGroup() {
        String code = "void only() {\n}\n";
        SymbolBreadcrumbService.BreadcrumbIndex index = service.analyze(code, "main.c");

        java.util.List<List<SymbolBreadcrumbService.BreadcrumbItem>> groups = index.siblingsAt(2);

        Assertions.assertEquals(2, groups.size());
        Assertions.assertEquals(1, groups.get(0).size());
        Assertions.assertEquals(1, groups.get(1).size());
    }
}
