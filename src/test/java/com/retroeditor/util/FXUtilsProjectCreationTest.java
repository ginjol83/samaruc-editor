package com.retroeditor.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
    void createProjectFromCpcTemplateWritesCorrectTargetAndFiles() throws Exception {
        FXUtils utils = new FXUtils();

        Path projectDir = tempDir.resolve("cpc-project");
        Files.createDirectories(projectDir);

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object cpcTemplate = Enum.valueOf(templateClass, "CPC");

        Class optionsClass = Class.forName("com.retroeditor.util.FXUtils$ProjectCreationOptions");
        Constructor ctor = optionsClass.getDeclaredConstructor(templateClass, boolean.class, boolean.class);
        ctor.setAccessible(true);
        Object options = ctor.newInstance(cpcTemplate, true, true);

        Method createProjectMethod = FXUtils.class.getDeclaredMethod("createProjectFromTemplate", File.class, optionsClass);
        createProjectMethod.setAccessible(true);
        createProjectMethod.invoke(utils, projectDir.toFile(), options);

        String json = Files.readString(projectDir.resolve(".samarucws").resolve("settings.json"), StandardCharsets.UTF_8);
        Assertions.assertTrue(json.contains("\"samaruc.target\": \"cpc\""));
        Assertions.assertTrue(Files.exists(projectDir.resolve("src/main.c")));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void reportsLastCreatedProjectAsCpcTemplate() throws Exception {
        FXUtils utils = new FXUtils();
        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object cpcTemplate = Enum.valueOf(templateClass, "CPC");
        Field templateField = FXUtils.class.getDeclaredField("lastCreatedProjectTemplate");
        templateField.setAccessible(true);
        templateField.set(utils, cpcTemplate);

        Assertions.assertTrue(utils.wasLastCreatedProjectCpcTemplate());
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

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createProjectFromAtariBasicTemplateWritesBasAndAtariTarget() throws Exception {
        FXUtils utils = new FXUtils();

        Path projectDir = tempDir.resolve("atari-basic-project");
        Files.createDirectories(projectDir);

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object atariBasicTemplate = Enum.valueOf(templateClass, "ATARI_BASIC");

        Class optionsClass = Class.forName("com.retroeditor.util.FXUtils$ProjectCreationOptions");
        Constructor ctor = optionsClass.getDeclaredConstructor(templateClass, boolean.class, boolean.class);
        ctor.setAccessible(true);
        Object options = ctor.newInstance(atariBasicTemplate, true, true);

        Method createProjectMethod = FXUtils.class.getDeclaredMethod("createProjectFromTemplate", File.class, optionsClass);
        createProjectMethod.setAccessible(true);
        createProjectMethod.invoke(utils, projectDir.toFile(), options);

        Path basFile = projectDir.resolve("src/main.bas");
        Assertions.assertTrue(Files.exists(basFile), "Debe generarse src/main.bas");
        Assertions.assertFalse(Files.exists(projectDir.resolve("src/main.c")), "No debe generarse src/main.c");

        String bas = Files.readString(basFile, StandardCharsets.UTF_8);
        Assertions.assertTrue(bas.contains("HOLA MUNDO"));
        Assertions.assertTrue(bas.contains("PRINT"));

        String json = Files.readString(projectDir.resolve(".samarucws").resolve("settings.json"), StandardCharsets.UTF_8);
        Assertions.assertTrue(json.contains("\"samaruc.target\": \"atari\""));

        String gitignore = Files.readString(projectDir.resolve(".gitignore"), StandardCharsets.UTF_8);
        Assertions.assertTrue(gitignore.contains("*.atr"));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createProjectFromSpectrumBasicTemplateWritesBasAndSpectrumTarget() throws Exception {
        FXUtils utils = new FXUtils();

        Path projectDir = tempDir.resolve("zx-basic-project");
        Files.createDirectories(projectDir);

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object spectrumBasicTemplate = Enum.valueOf(templateClass, "SPECTRUM_BASIC");

        Class optionsClass = Class.forName("com.retroeditor.util.FXUtils$ProjectCreationOptions");
        Constructor ctor = optionsClass.getDeclaredConstructor(templateClass, boolean.class, boolean.class);
        ctor.setAccessible(true);
        Object options = ctor.newInstance(spectrumBasicTemplate, true, true);

        Method createProjectMethod = FXUtils.class.getDeclaredMethod("createProjectFromTemplate", File.class, optionsClass);
        createProjectMethod.setAccessible(true);
        createProjectMethod.invoke(utils, projectDir.toFile(), options);

        Path basFile = projectDir.resolve("src/main.bas");
        Assertions.assertTrue(Files.exists(basFile), "Debe generarse src/main.bas");
        Assertions.assertFalse(Files.exists(projectDir.resolve("src/main.c")), "No debe generarse src/main.c");

        String bas = Files.readString(basFile, StandardCharsets.UTF_8);
        Assertions.assertTrue(bas.contains("HOLA MUNDO"));
        Assertions.assertTrue(bas.contains("PRINT"));

        String json = Files.readString(projectDir.resolve(".samarucws").resolve("settings.json"), StandardCharsets.UTF_8);
        Assertions.assertTrue(json.contains("\"samaruc.target\": \"spectrum\""));

        String gitignore = Files.readString(projectDir.resolve(".gitignore"), StandardCharsets.UTF_8);
        Assertions.assertTrue(gitignore.contains("*.tap"));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void reportsLastCreatedProjectAsSpectrumBasicTemplate() throws Exception {
        FXUtils utils = new FXUtils();
        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object template = Enum.valueOf(templateClass, "SPECTRUM_BASIC");
        Field templateField = FXUtils.class.getDeclaredField("lastCreatedProjectTemplate");
        templateField.setAccessible(true);
        templateField.set(utils, template);

        Assertions.assertTrue(utils.wasLastCreatedProjectSpectrumBasicTemplate());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createProjectFromTranslatorTemplateWritesSmTFileAndNoMain() throws Exception {
        FXUtils utils = new FXUtils();

        Path projectDir = tempDir.resolve("translator-project");

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object translatorTemplate = Enum.valueOf(templateClass, "TRANSLATOR");

        Class optionsClass = Class.forName("com.retroeditor.util.FXUtils$ProjectCreationOptions");
        Constructor ctor = optionsClass.getDeclaredConstructor(templateClass, boolean.class, boolean.class);
        ctor.setAccessible(true);
        Object options = ctor.newInstance(translatorTemplate, true, true);

        Method createProjectMethod = FXUtils.class.getDeclaredMethod("createProjectFromTemplate", File.class, optionsClass);
        createProjectMethod.setAccessible(true);
        createProjectMethod.invoke(utils, projectDir.toFile(), options);

        Assertions.assertTrue(Files.exists(projectDir.resolve("translation.smt")), "Debe generarse translation.smt");

        String smt = Files.readString(projectDir.resolve("translation.smt"), StandardCharsets.UTF_8);
        Assertions.assertTrue(smt.contains("romPath"));
        Assertions.assertTrue(smt.contains("fontTable"));

        Assertions.assertFalse(Files.exists(projectDir.resolve("src/main.c")), "No debe generarse src/main.c");

        String json = Files.readString(projectDir.resolve(".samarucws").resolve("settings.json"), StandardCharsets.UTF_8);
        Assertions.assertTrue(json.contains("\"samaruc.target\": \"gameboy\""));
        Assertions.assertTrue(Files.exists(projectDir.resolve("README.md")));
        Assertions.assertTrue(Files.exists(projectDir.resolve(".gitignore")));
    }
}
