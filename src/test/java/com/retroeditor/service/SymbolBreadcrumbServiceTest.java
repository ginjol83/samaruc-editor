package com.retroeditor.service;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SymbolBreadcrumbServiceTest {

    private final SymbolBreadcrumbService service = new SymbolBreadcrumbService();

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
}
