package com.retroeditor.service;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CodeFoldingServiceTest {

    private final CodeFoldingService service = new CodeFoldingService();

    @Test
    void detectsBraceBlocks() {
        String code = "void foo() {\n    int x = 0;\n}\n";
        List<CodeFoldingService.FoldRange> ranges = service.detectFoldRanges(code);

        Assertions.assertFalse(ranges.isEmpty());
        CodeFoldingService.FoldRange range = ranges.get(0);
        Assertions.assertEquals(1, range.getStartLine());
        Assertions.assertEquals(3, range.getEndLine());
    }

    @Test
    void detectsAsmRegions() {
        String code = "#region Init\n  ld a, 0\n#endregion\n";
        List<CodeFoldingService.FoldRange> ranges = service.detectFoldRanges(code);

        Assertions.assertFalse(ranges.isEmpty());
        CodeFoldingService.FoldRange range = ranges.get(0);
        Assertions.assertEquals(1, range.getStartLine());
        Assertions.assertEquals(3, range.getEndLine());
        Assertions.assertTrue(range.getTitle().contains("Init"));
    }
}
