package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

class GccSyntaxCheckServiceTest {

    private final GccSyntaxCheckService service = new GccSyntaxCheckService();

    @Test
    void isApplicableOnlyForGcc() {
        assertTrue(service.isApplicable("gcc"));
        assertTrue(service.isApplicable(" GCC "));
        assertFalse(service.isApplicable("GBDK"));
        assertFalse(service.isApplicable("z88dk"));
        assertFalse(service.isApplicable("cc65"));
        assertFalse(service.isApplicable(null));
        assertFalse(service.isApplicable(""));
    }

    @Test
    void commandContainsFsyntaxOnlyAndSource() {
        List<String> cmd = service.buildSyntaxCheckCommand("", "none", "", "", "",
            new File("C:\\proyecto\\main.c"));
        assertEquals("gcc", cmd.get(0));
        assertTrue(cmd.contains("-fsyntax-only"));
        assertTrue(cmd.get(cmd.size() - 1).endsWith("main.c"));
    }

    @Test
    void commandUsesConfiguredCompilerAndOptLevel() {
        List<String> cmd = service.buildSyntaxCheckCommand("C:\\mingw\\bin\\gcc.exe", "-O2", "", "", "",
            new File("/tmp/main.c"));
        assertEquals("C:\\mingw\\bin\\gcc.exe", cmd.get(0));
        assertTrue(cmd.contains("-O2"));
    }

    @Test
    void commandAddsDefinesAndIncludes() {
        List<String> cmd = service.buildSyntaxCheckCommand("gcc", "none", "DEBUG,FOO=1", "C:\\inc;C:\\otros", "",
            new File("/tmp/main.c"));
        assertTrue(cmd.contains("-DDEBUG"));
        assertTrue(cmd.contains("-DFOO=1"));
        assertTrue(cmd.contains("-IC:\\inc"));
        assertTrue(cmd.contains("-IC:\\otros"));
    }

    @Test
    void commandAddsExtraArgsPreservingQuotes() {
        List<String> cmd = service.buildSyntaxCheckCommand("gcc", "none", "", "", "-fno-common -I\"C:\\path with spaces\\inc\"",
            new File("/tmp/main.c"));
        assertTrue(cmd.contains("-fno-common"));
        assertTrue(cmd.contains("-IC:\\path with spaces\\inc"));
    }
}