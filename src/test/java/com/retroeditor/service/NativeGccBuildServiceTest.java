package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeGccBuildServiceTest {

    private final NativeGccBuildService service = new NativeGccBuildService();

    @TempDir
    Path tempDir;

    @Test
    void buildsCommandWithAllPartsInOrder() {
        File output = new File(tempDir.toFile(), "out/main.exe");
        List<File> sources = List.of(
            new File(tempDir.toFile(), "Main.c"),
            new File(tempDir.toFile(), "rooms.c")
        );

        List<String> cmd = service.buildCommand("gcc", "-O2", "DEBUG,FOO=1", "inc1;inc2", "-Wall", output, sources);

        assertEquals(List.of(
            "gcc",
            "-O2",
            "-DDEBUG",
            "-DFOO=1",
            "-Iinc1",
            "-Iinc2",
            "-Wall",
            "-o",
            output.getAbsolutePath(),
            new File(tempDir.toFile(), "Main.c").getAbsolutePath(),
            new File(tempDir.toFile(), "rooms.c").getAbsolutePath()
        ), cmd);
    }

    @Test
    void omitsOptLevelWhenNoneOrBlank() {
        File output = new File(tempDir.toFile(), "out/main.exe");
        List<File> sources = List.of(new File(tempDir.toFile(), "Main.c"));

        List<String> cmdNone = service.buildCommand("gcc", "none", "", "", "", output, sources);
        assertFalse(cmdNone.stream().anyMatch(arg -> arg.startsWith("-O") && !"-o".equals(arg)));

        List<String> cmdBlank = service.buildCommand("gcc", "", "", "", "", output, sources);
        assertFalse(cmdBlank.stream().anyMatch(arg -> arg.startsWith("-O") && !"-o".equals(arg)));
        assertTrue(cmdBlank.contains("-o"));
    }

    @Test
    void addsDefaultWarningAndDebugFlagsWhenNoExtraArgsConfigured() {
        File output = new File(tempDir.toFile(), "out/main.exe");
        List<File> sources = List.of(new File(tempDir.toFile(), "Main.c"));

        List<String> cmd = service.buildCommand("gcc", "-O2", "", "", "", output, sources);

        assertTrue(cmd.contains("-Wall"));
        assertTrue(cmd.contains("-Wextra"));
        assertTrue(cmd.contains("-g"));
        assertTrue(cmd.indexOf("-Wall") < cmd.indexOf("-Wextra"));
        assertTrue(cmd.indexOf("-Wextra") < cmd.indexOf("-g"));
        assertTrue(cmd.indexOf("-g") < cmd.indexOf("-o"));
    }

    @Test
    void doesNotAddDefaultFlagsWhenExtraArgsConfigured() {
        File output = new File(tempDir.toFile(), "out/main.exe");
        List<File> sources = List.of(new File(tempDir.toFile(), "Main.c"));

        List<String> cmd = service.buildCommand("gcc", "-O2", "", "", "-Wall", output, sources);

        assertTrue(cmd.contains("-Wall"));
        assertFalse(cmd.contains("-Wextra"));
        assertFalse(cmd.contains("-g"));
    }

    @Test
    void preservesQuotedSectionsInExtraArgs() {
        File output = new File(tempDir.toFile(), "out/main.exe");
        List<File> sources = List.of(new File(tempDir.toFile(), "Main.c"));

        List<String> cmd = service.buildCommand("gcc", "none", "", "", "-Wall \"-DVALUE=hola mundo\"", output, sources);

        assertTrue(cmd.contains("-Wall"));
        assertTrue(cmd.contains("-DVALUE=hola mundo"));
    }

    @Test
    void collectsAllSourcesRecursivelyExcludingBuildDirs() throws Exception {
        Path project = tempDir.resolve("proyecto");
        Files.createDirectories(project.resolve("src"));
        Files.createDirectories(project.resolve("out"));
        Files.createDirectories(project.resolve("cmake-build-debug"));
        Files.createDirectories(project.resolve("build"));
        Files.createDirectories(project.resolve("target"));
        Files.writeString(project.resolve("Main.c"), "");
        Files.writeString(project.resolve("rooms.c"), "");
        Files.writeString(project.resolve("inventario.c"), "");
        Files.writeString(project.resolve("src/extra.c"), "");
        Files.writeString(project.resolve("out/ignored.c"), "");
        Files.writeString(project.resolve("cmake-build-debug/ignored.c"), "");
        Files.writeString(project.resolve("build/ignored.c"), "");
        Files.writeString(project.resolve("target/ignored.c"), "");

        List<File> sources = service.collectNativeSources(
            project.toFile(),
            project.resolve("out").toFile(),
            project.resolve("Main.c").toFile()
        );

        Set<String> names = sources.stream().map(File::getName).collect(Collectors.toSet());
        assertEquals(Set.of("Main.c", "rooms.c", "inventario.c", "extra.c"), names);
        assertEquals(4, sources.size());

        List<File> sorted = new ArrayList<>(sources);
        sorted.sort(Comparator.comparing(File::getAbsolutePath));
        assertEquals(sorted, sources);
    }

    @Test
    void fallsBackToActiveSourceWithoutProject() {
        File sourceFile = new File(tempDir.toFile(), "Main.c");
        List<File> sources = service.collectNativeSources(null, null, sourceFile);
        assertEquals(List.of(sourceFile), sources);
    }

    @Test
    void buildsPlanWithOnlyChangedSourcesAndLinkStep() throws Exception {
        Path project = tempDir.resolve("incr");
        Files.createDirectories(project.resolve("out"));
        Path main = project.resolve("main.c");
        Path rooms = project.resolve("rooms.c");
        Files.writeString(main, "int main(void) { return 0; }\n");
        Files.writeString(rooms, "int rooms(void) { return 1; }\n");

        File outputFile = new File(project.resolve("out").toFile(), "main.exe");
        List<File> sources = List.of(main.toFile(), rooms.toFile());

        NativeGccBuildService.GccBuildPlan plan = service.buildPlan(
            "gcc", "-O2", "", "", "", outputFile, sources
        );

        assertEquals(2, plan.compileSteps().size());
        assertEquals("main.o", plan.compileSteps().get(0).objectFile().getName());
        assertEquals("rooms.o", plan.compileSteps().get(1).objectFile().getName());
        assertTrue(plan.compileSteps().get(0).command().contains("-c"));
        assertEquals(outputFile, plan.linkStep().outputFile());
        assertTrue(plan.linkStep().command().contains(outputFile.getAbsolutePath()));
    }

    @Test
    void skipsUpToDateSourcesInPlan() throws Exception {
        Path project = tempDir.resolve("incr2");
        Path outDir = project.resolve("out");
        Files.createDirectories(outDir);
        Path main = project.resolve("main.c");
        Path rooms = project.resolve("rooms.c");
        Files.writeString(main, "int main(void) { return 0; }\n");
        Files.writeString(rooms, "int rooms(void) { return 1; }\n");

        File outputFile = new File(outDir.toFile(), "main.exe");
        List<File> sources = List.of(main.toFile(), rooms.toFile());

        File mainObj = new File(outDir.toFile(), "main.o");
        File roomsObj = new File(outDir.toFile(), "rooms.o");
        Files.writeString(mainObj.toPath(), "simulated compiled main\n");
        Files.writeString(roomsObj.toPath(), "simulated compiled rooms\n");

        Thread.sleep(1100);
        Files.writeString(main, "int main(void) { return 2; }\n");

        NativeGccBuildService.GccBuildPlan plan = service.buildPlan(
            "gcc", "-O2", "", "", "", outputFile, sources
        );

        assertEquals(1, plan.compileSteps().size());
        assertEquals("main.o", plan.compileSteps().get(0).objectFile().getName());
        assertTrue(plan.linkStep().command().contains(mainObj.getAbsolutePath()));
        assertTrue(plan.linkStep().command().contains(roomsObj.getAbsolutePath()));
    }

    @Test
    void linkStepAlwaysIncludedEvenWhenNothingChanged() throws Exception {
        Path project = tempDir.resolve("incr3");
        Path outDir = project.resolve("out");
        Files.createDirectories(outDir);
        Path main = project.resolve("main.c");
        Files.writeString(main, "int main(void) { return 0; }\n");

        File outputFile = new File(outDir.toFile(), "main.exe");
        List<File> sources = List.of(main.toFile());

        File mainObj = new File(outDir.toFile(), "main.o");
        Files.writeString(mainObj.toPath(), "simulated compiled main\n");

        NativeGccBuildService.GccBuildPlan plan = service.buildPlan(
            "gcc", "-O2", "", "", "", outputFile, sources
        );

        assertEquals(0, plan.compileSteps().size());
        assertTrue(plan.linkStep().command().contains("-o"));
    }
}
