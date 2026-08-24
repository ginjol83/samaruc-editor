package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class StructureViewServiceTest {

    @Test
    void testExtractStructure() {
        String code = """
            #define SCREEN_WIDTH 160
            #define SCREEN_HEIGHT 144

            struct Player {
                int x;
                int y;
            };

            int globalScore = 0;

            void initGame() {
                globalScore = 100;
            }

            void updatePlayer(struct Player *p) {
                p->x += 1;
            }
            """;

        StructureViewService service = new StructureViewService();
        List<StructureViewService.StructureSymbol> symbols = service.getStructure(code);

        assertFalse(symbols.isEmpty());

        boolean foundMacro = symbols.stream().anyMatch(s -> "SCREEN_WIDTH".equals(s.getName()) && "macro".equals(s.getType()));
        boolean foundStruct = symbols.stream().anyMatch(s -> "Player".equals(s.getName()) && "struct".equals(s.getType()));
        boolean foundVar = symbols.stream().anyMatch(s -> "globalScore".equals(s.getName()) && "variable".equals(s.getType()));
        boolean foundFunc = symbols.stream().anyMatch(s -> "initGame".equals(s.getName()) && "function".equals(s.getType()));

        assertTrue(foundMacro, "Should find macro SCREEN_WIDTH");
        assertTrue(foundStruct, "Should find struct Player");
        assertTrue(foundVar, "Should find global variable globalScore");
        assertTrue(foundFunc, "Should find function initGame");
    }

    @Test
    void testEmptyCode() {
        StructureViewService service = new StructureViewService();
        List<StructureViewService.StructureSymbol> symbols = service.getStructure("");
        assertTrue(symbols.isEmpty());
    }
}
