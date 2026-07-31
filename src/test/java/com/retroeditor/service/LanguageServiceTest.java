package com.retroeditor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LanguageService Tests")
class LanguageServiceTest {

    private LanguageService languageService;

    @BeforeEach
    void setUp() {
        languageService = new LanguageService();
    }

    @Test
    @DisplayName("Should detect available languages")
    void testDetectAvailableLanguages() {
        List<LanguageService.LanguageInfo> languages = languageService.getAvailableLanguages();
        
        assertNotNull(languages, "Available languages should not be null");
        assertFalse(languages.isEmpty(), "Should detect at least Spanish as default");
        assertTrue(languages.stream().anyMatch(l -> "es".equals(l.code())), 
            "Spanish should be available");
    }

    @Test
    @DisplayName("Should get all language names")
    void testGetAvailableLanguageNames() {
        List<String> names = languageService.getAvailableLanguageNames();
        
        assertNotNull(names, "Language names should not be null");
        assertFalse(names.isEmpty(), "Should have at least one language name");
        assertTrue(names.contains("Español"), "Should contain Spanish");
    }

    @Test
    @DisplayName("Should map language code to display name")
    void testGetLanguageNameByCode() {
        String name = languageService.getLanguageNameByCode("es");
        assertEquals("Español", name, "Code 'es' should map to 'Español'");
        
        name = languageService.getLanguageNameByCode("en");
        assertEquals("English", name, "Code 'en' should map to 'English'");
    }

    @Test
    @DisplayName("Should map display name to language code")
    void testGetLanguageCodeByName() {
        String code = languageService.getLanguageCodeByName("Español");
        assertEquals("es", code, "Display name 'Español' should map to 'es'");
        
        code = languageService.getLanguageCodeByName("English");
        assertEquals("en", code, "Display name 'English' should map to 'en'");
    }

    @Test
    @DisplayName("Should set and get current language")
    void testSetAndGetCurrentLanguage() {
        languageService.setLanguage("es");
        assertEquals("es", languageService.getCurrentLanguageCode(), 
            "Current language code should be 'es'");
        assertEquals("Español", languageService.getCurrentLanguageName(), 
            "Current language name should be 'Español'");
        
        languageService.setLanguage("en");
        assertEquals("en", languageService.getCurrentLanguageCode(), 
            "Current language code should be 'en'");
        assertEquals("English", languageService.getCurrentLanguageName(), 
            "Current language name should be 'English'");
    }

    @Test
    @DisplayName("Should default to Spanish for null language")
    void testDefaultLanguageForNull() {
        languageService.setLanguage(null);
        assertEquals("es", languageService.getCurrentLanguageCode(), 
            "Should default to Spanish when null is provided");
    }

    @Test
    @DisplayName("Should get ResourceBundle for current language")
    void testGetCurrentBundle() {
        languageService.setLanguage("es");
        ResourceBundle bundle = languageService.getCurrentBundle();
        
        assertNotNull(bundle, "ResourceBundle should not be null");
        assertTrue(bundle.containsKey("button.saveLanguage"), 
            "Bundle should contain 'button.saveLanguage' key");
    }

    @Test
    @DisplayName("Should get string with fallback")
    void testGetStringWithFallback() {
        languageService.setLanguage("es");
        
        String result = languageService.getString("button.saveLanguage", "Fallback");
        assertNotNull(result, "Result should not be null");
        assertNotEquals("Fallback", result, "Should return actual string, not fallback");
        
        String fallbackResult = languageService.getString("non.existent.key", "Fallback");
        assertEquals("Fallback", fallbackResult, "Should return fallback for non-existent key");
    }

    @Test
    @DisplayName("Should get string without fallback")
    void testGetStringWithoutFallback() {
        languageService.setLanguage("es");
        
        String result = languageService.getString("button.saveLanguage");
        assertNotNull(result, "Result should not be null");
        assertNotEquals("button.saveLanguage", result, 
            "Should return translated string, not the key itself");
    }

    @Test
    @DisplayName("Should verify language availability")
    void testIsLanguageAvailable() {
        assertTrue(languageService.isLanguageAvailable("es"), 
            "Spanish should be available");
        
        if (languageService.getAvailableLanguages().stream().anyMatch(l -> "en".equals(l.code()))) {
            assertTrue(languageService.isLanguageAvailable("en"), 
                "English should be available if detected");
        }
        
        assertFalse(languageService.isLanguageAvailable("xy"), 
            "Unknown language code should not be available");
    }

    @Test
    @DisplayName("Should handle multiple language switches")
    void testMultipleLanguageSwitches() {
        languageService.setLanguage("es");
        assertEquals("es", languageService.getCurrentLanguageCode());
        
        if (languageService.isLanguageAvailable("en")) {
            languageService.setLanguage("en");
            assertEquals("en", languageService.getCurrentLanguageCode());
        }
        
        languageService.setLanguage("es");
        assertEquals("es", languageService.getCurrentLanguageCode(), 
            "Should be able to switch back to Spanish");
    }

    @Test
    @DisplayName("Should handle invalid language codes gracefully")
    void testInvalidLanguageCode() {
        languageService.setLanguage("invalid");
        // Should not throw exception, but may fallback to default
        assertNotNull(languageService.getCurrentLanguageCode(), 
            "Should have a current language even after invalid code");
    }

    @Test
    @DisplayName("LanguageInfo record should be created correctly")
    void testLanguageInfoRecord() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.MessagesBundle", java.util.Locale.forLanguageTag("es"));
        LanguageService.LanguageInfo langInfo = new LanguageService.LanguageInfo("es", "Español", bundle);
        
        assertEquals("es", langInfo.code());
        assertEquals("Español", langInfo.displayName());
        assertNotNull(langInfo.bundle());
    }

    @Test
    @DisplayName("Should support transitioning between multiple available languages")
    void testLanguageTransitions() {
        List<String> availableLanguages = languageService.getAvailableLanguageNames();
        
        if (availableLanguages.size() >= 2) {
            for (String langName : availableLanguages) {
                String code = languageService.getLanguageCodeByName(langName);
                languageService.setLanguage(code);
                assertEquals(code, languageService.getCurrentLanguageCode(), 
                    "Should successfully switch to " + langName);
            }
        }
    }
}
