package com.retroeditor.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FXUtilsProjectCreationTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createProjectFromTemplateWritesWorkspaceJsonConfig() throws Exception {
        FXUtils utils = new FXUtils();

        Path projectDir = tempDir.resolve("gb-project");
        Files.createDirectories(projectDir);

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object gameBoyTemplate = Enum.valueOf(templateClass, "GAMEBOY");

        Class optionsClass = Class.forName("com.retroeditor.util.FXUtils$ProjectCreationOptions");
        Constructor ctor = optionsClass.getDeclaredConstructor(templateClass, boolean.class, boolean.class);
        ctor.setAccessible(true);
        Object options = ctor.newInstance(gameBoyTemplate, true, true);

        Method createProjectMethod = FXUtils.class.getDeclaredMethod("createProjectFromTemplate", File.class, optionsClass);
        createProjectMethod.setAccessible(true);
        createProjectMethod.invoke(utils, projectDir.toFile(), options);

        Path workspaceSettings = projectDir.resolve(".samarucws").resolve("settings.json");
        Assertions.assertTrue(Files.exists(workspaceSettings), "Debe generarse .samarucws/settings.json");

        String json = Files.readString(workspaceSettings, StandardCharsets.UTF_8);
        Assertions.assertTrue(json.contains("\"samaruc.workspaceVersion\": 1"));
        Assertions.assertTrue(json.contains("\"samaruc.target\": \"gameboy\""));
        Assertions.assertTrue(json.contains("\"**/out\": true"));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createProjectFromGccTemplateWritesCorrectTargetAndFiles() throws Exception {
        FXUtils utils = new FXUtils();

        Path projectDir = tempDir.resolve("gcc-project");
        Files.createDirectories(projectDir);

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object gccTemplate = Enum.valueOf(templateClass, "GCC");

        Class optionsClass = Class.forName("com.retroeditor.util.FXUtils$ProjectCreationOptions");
        Constructor ctor = optionsClass.getDeclaredConstructor(templateClass, boolean.class, boolean.class);
        ctor.setAccessible(true);
        Object options = ctor.newInstance(gccTemplate, true, true);

        Method createProjectMethod = FXUtils.class.getDeclaredMethod("createProjectFromTemplate", File.class, optionsClass);
        createProjectMethod.setAccessible(true);
        createProjectMethod.invoke(utils, projectDir.toFile(), options);

        Path workspaceSettings = projectDir.resolve(".samarucws").resolve("settings.json");
        String json = Files.readString(workspaceSettings, StandardCharsets.UTF_8);
        Assertions.assertTrue(json.contains("\"samaruc.target\": \"gcc\""));
        Assertions.assertTrue(Files.exists(projectDir.resolve("src/main.c")));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createProjectFromMakefileTemplateWritesMakefile() throws Exception {
        FXUtils utils = new FXUtils();

        Path projectDir = tempDir.resolve("make-project");
        Files.createDirectories(projectDir);

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object makeTemplate = Enum.valueOf(templateClass, "MAKEFILE");

        Class optionsClass = Class.forName("com.retroeditor.util.FXUtils$ProjectCreationOptions");
        Constructor ctor = optionsClass.getDeclaredConstructor(templateClass, boolean.class, boolean.class);
        ctor.setAccessible(true);
        Object options = ctor.newInstance(makeTemplate, true, true);

        Method createProjectMethod = FXUtils.class.getDeclaredMethod("createProjectFromTemplate", File.class, optionsClass);
        createProjectMethod.setAccessible(true);
        createProjectMethod.invoke(utils, projectDir.toFile(), options);

        Assertions.assertTrue(Files.exists(projectDir.resolve("Makefile")));
        Assertions.assertTrue(Files.exists(projectDir.resolve("src/main.c")));
    }
}
