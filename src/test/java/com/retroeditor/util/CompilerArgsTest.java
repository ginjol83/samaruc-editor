package com.retroeditor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CompilerArgsTest {

    private final CompilerArgs compilerArgs = new CompilerArgs();

    @Test
    void addsMacroFlagsFromCsvOrWhitespace() {
        List<String> cmd = new ArrayList<>();
        compilerArgs.addMacroFlags(cmd, "DEBUG,FOO=1 BAR");
        assertEquals(List.of("-DDEBUG", "-DFOO=1", "-DBAR"), cmd);
    }

    @Test
    void addsIncludeFlagsSeparatedBySemicolonOrComma() {
        List<String> cmd = new ArrayList<>();
        compilerArgs.addIncludeFlags(cmd, "inc1;inc2, inc3 ");
        assertEquals(List.of("-Iinc1", "-Iinc2", "-Iinc3"), cmd);
    }

    @Test
    void addsExtraArgsPreservingQuotedSections() {
        List<String> cmd = new ArrayList<>();
        compilerArgs.addExtraArgs(cmd, "-Wall \"-DVALUE=a b\" -std=c17");
        assertEquals(List.of("-Wall", "-DVALUE=a b", "-std=c17"), cmd);
    }

    @Test
    void ignoresBlankInputs() {
        List<String> cmd = new ArrayList<>();
        compilerArgs.addMacroFlags(cmd, null);
        compilerArgs.addIncludeFlags(cmd, "   ");
        compilerArgs.addExtraArgs(cmd, "");
        assertEquals(List.of(), cmd);
    }
}
