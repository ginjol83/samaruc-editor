package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.retroeditor.service.SnippetService.Insertion;
import com.retroeditor.service.SnippetService.Parsed;
import com.retroeditor.service.SnippetService.TabStop;

class SnippetServiceTest {

    private final SnippetService service = new SnippetService();

    @Test
    void parseExpandsFragmentAndListsTabStopsInOrderOfAppearance() {
        Parsed parsed = service.parse("for ($2;j<$1;j++) {$0}");
        assertEquals("for (;j<;j++) {}", parsed.fragment());
        List<TabStop> stops = parsed.tabStops();
        assertEquals(2, stops.size());
        assertEquals(2, stops.get(0).number());
        assertEquals(1, stops.get(1).number());
        assertEquals(List.of(), stops.stream().map(s -> s.defaultValue()).filter(s -> !s.isEmpty()).toList());
        assertEquals(parsed.fragment().length() - 1, parsed.finalCaret());
    }

    @Test
    void parseHonorsDefaultValuesAndDollarLiteral() {
        Parsed parsed = service.parse("${1:n} $$ ${2:valor}");
        assertEquals("n $ valor", parsed.fragment());
        List<TabStop> stops = parsed.tabStops();
        assertEquals(2, stops.size());
        assertEquals("n", stops.get(0).defaultValue());
        assertEquals(0, stops.get(0).start());
        assertEquals(1, stops.get(0).end());
        assertEquals("valor", stops.get(1).defaultValue());
    }

    @Test
    void parseUsesFinalCaretFromDollarZero() {
        Parsed parsed = service.parse("${1:a}b$0c");
        assertEquals("abc", parsed.fragment());
        assertTrue(parsed.tabStops().size() == 1);
        assertEquals("ab".length(), parsed.finalCaret());
    }

    @Test
    void parseDefaultsFinalCaretToFragmentEnd() {
        Parsed parsed = service.parse("hola $1 mundo");
        assertEquals("hola  mundo", parsed.fragment());
        assertEquals("hola  mundo".length(), parsed.finalCaret());
    }

    @Test
    void projectComputesAbsoluteOffsetsWithinDocument() {
        Insertion ins = service.project("ABCDEFGH", 2, 5, "x$1y$0z");
        assertEquals("ABxyzFGH", ins.fullText());
        assertEquals(1, ins.tabStops().size());
        assertEquals(3, ins.tabStops().get(0).start());
        assertEquals(3, ins.tabStops().get(0).end());
        assertEquals(4, ins.finalCaret());
    }

    @Test
    void projectClampsOutOfBoundsSelection() {
        Insertion ins = service.project("ab", 10, 5, "$1");
        assertEquals("ab", ins.fullText());
        assertEquals(1, ins.tabStops().size());
        assertEquals(2, ins.tabStops().get(0).start());
    }
}