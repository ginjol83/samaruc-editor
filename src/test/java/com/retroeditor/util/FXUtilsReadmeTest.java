package com.retroeditor.util;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FXUtilsReadmeTest {

    @Test
    void readmeIsPlatformSpecificForSpectrumTemplate() throws Exception {
        String readme = invokeReadme("SPECTRUM", "zx-proj");

        Assertions.assertTrue(readme.contains("ZX Spectrum project"));
        Assertions.assertTrue(readme.contains("z88dk"));
        Assertions.assertTrue(readme.contains("src/main.c"));
        Assertions.assertTrue(readme.contains("out/"));
        Assertions.assertFalse(readme.contains("Game Boy project"));
    }

    @Test
    void readmeIsPlatformSpecificForGameBoyTemplate() throws Exception {
        String readme = invokeReadme("GAMEBOY", "gb-proj");

        Assertions.assertTrue(readme.contains("Game Boy project"));
        Assertions.assertTrue(readme.contains("GBDK"));
        Assertions.assertTrue(readme.contains("src/main.c"));
        Assertions.assertTrue(readme.contains("out/"));
        Assertions.assertFalse(readme.contains("ZX Spectrum project"));
    }

    @Test
    void readmeIsGenericForEmptyTemplate() throws Exception {
        String readme = invokeReadme("EMPTY", "generic-proj");

        Assertions.assertTrue(readme.contains("C project created"));
        Assertions.assertTrue(readme.contains("ZX Spectrum"));
        Assertions.assertTrue(readme.contains("Game Boy"));
        Assertions.assertTrue(readme.contains("src/main.c"));
    }

    @Test
    void readmeCanBeGeneratedInSpanishWhenConfigured() throws Exception {
        String readme = invokeReadmeWithLanguage("SPECTRUM", "zx-proj", "es");

        Assertions.assertTrue(readme.contains("Proyecto ZX Spectrum"));
        Assertions.assertTrue(readme.contains("Toolchain recomendada"));
        Assertions.assertFalse(readme.contains("ZX Spectrum project created"));
    }

    @Test
    void markdownPreviewRendererProducesFormattedHtml() throws Exception {
        String html = invokeMarkdownRenderer("README.md", "# Title\n\n- item\n\n`code`\n");

        Assertions.assertTrue(html.contains("<h1>Title</h1>"));
        Assertions.assertTrue(html.contains("<ul>"));
        Assertions.assertTrue(html.contains("<li>item</li>"));
        Assertions.assertTrue(html.contains("<code>code</code>"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String invokeReadme(String templateName, String projectName) throws Exception {
        FXUtils utils = new FXUtils();

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object template = Enum.valueOf(templateClass, templateName);

        Method m = FXUtils.class.getDeclaredMethod("getReadmeContent", String.class, templateClass);
        m.setAccessible(true);

        return (String) m.invoke(utils, projectName, template);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String invokeReadmeWithLanguage(String templateName, String projectName, String language) throws Exception {
        FXUtils utils = new FXUtils();

        Field languageField = FXUtils.class.getDeclaredField("projectReadmeLanguage");
        languageField.setAccessible(true);
        languageField.set(utils, language);

        Class templateClass = Class.forName("com.retroeditor.util.FXUtils$ProjectTemplate");
        Object template = Enum.valueOf(templateClass, templateName);

        Method m = FXUtils.class.getDeclaredMethod("getReadmeContent", String.class, templateClass);
        m.setAccessible(true);

        return (String) m.invoke(utils, projectName, template);
    }

    private String invokeMarkdownRenderer(String title, String markdown) throws Exception {
        FXUtils utils = new FXUtils();
        Method m = FXUtils.class.getDeclaredMethod("renderMarkdownToHtml", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(utils, title, markdown);
    }
}
