package com.retroeditor.service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio centralizado para gestionar la localización (i18n) de la aplicación.
 * 
 * Detecta automáticamente los idiomas disponibles escaneando los archivos 
 * MessagesBundle_*.properties en los recursos, permitiendo agregar nuevos idiomas 
 * sin cambios en el código Java.
 */
public class LanguageService {
    private static final String BUNDLE_BASE_NAME = "i18n.MessagesBundle";
    private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("MessagesBundle(?:_([a-z]{2}))?(?:\\.properties)?$");
    
    private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
        Map.entry("es", "Español"),
        Map.entry("en", "English"),
        Map.entry("it", "Italiano"),
        Map.entry("fr", "Français"),
        Map.entry("de", "Deutsch")
       /* Map.entry("pt", "Português"),
        Map.entry("ja", "日本語"),
        Map.entry("zh", "中文"),
        Map.entry("ru", "Русский"),
        Map.entry("pl", "Polski")
        */
    );
    
    private final List<LanguageInfo> availableLanguages = new ArrayList<>();
    private String currentLanguageCode = "es";
    private ResourceBundle currentBundle;
    private File externalI18nDir;

    public LanguageService() {
        this(null);
    }

    public LanguageService(File externalI18nDir) {
        this.externalI18nDir = externalI18nDir;
        detectAvailableLanguages();
        setLanguage("es");
    }

    public void setExternalI18nDir(File externalI18nDir) {
        this.externalI18nDir = externalI18nDir;
        detectAvailableLanguages();
    }
    
    /**
     * Detecta automáticamente los idiomas disponibles basándose en los archivos 
     * MessagesBundle_*.properties presentes en los recursos.
     */
    private void detectAvailableLanguages() {
        availableLanguages.clear();
        
        // Cargar idioma por defecto (MessagesBundle.properties = español)
        try {
            ResourceBundle bundle = getBundle(BUNDLE_BASE_NAME, Locale.ROOT);
            availableLanguages.add(new LanguageInfo("es", "Español", bundle));
        } catch (MissingResourceException e) {
            // Fallback if default not found
        }
        
        // Detectar idiomas adicionales conocidos
        for (String code : LANGUAGE_NAMES.keySet()) {
            if ("es".equals(code)) continue; // Ya agregado
            
            try {
                Locale locale = Locale.forLanguageTag(code);
                ResourceBundle bundle = getBundle(BUNDLE_BASE_NAME, locale);
                String displayName = LANGUAGE_NAMES.get(code);
                availableLanguages.add(new LanguageInfo(code, displayName, bundle));
            } catch (MissingResourceException e) {
                // Idioma no disponible, continuar
            }
        }

        // Detectar idiomas externos en la carpeta externalI18nDir
        detectExternalLanguages();
        
        // Asegurar que siempre hay al menos español
        if (availableLanguages.isEmpty()) {
            try {
                ResourceBundle bundle = getBundle(BUNDLE_BASE_NAME, Locale.getDefault());
                availableLanguages.add(new LanguageInfo("es", "Español", bundle));
            } catch (MissingResourceException e) {
                // Idioma por defecto no encontrado
            }
        }
    }

    private void detectExternalLanguages() {
        if (externalI18nDir == null || !externalI18nDir.exists() || !externalI18nDir.isDirectory()) {
            return;
        }

        File[] files = externalI18nDir.listFiles((dir, name) -> name.startsWith("MessagesBundle_") && name.endsWith(".properties"));
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            Matcher matcher = LANGUAGE_CODE_PATTERN.matcher(fileName);
            if (matcher.find()) {
                String code = matcher.group(1);
                if (code != null && !isLanguageAvailable(code)) {
                    try {
                        Locale locale = Locale.forLanguageTag(code);
                        ResourceBundle bundle = getBundle(BUNDLE_BASE_NAME, locale);
                        String displayName = LANGUAGE_NAMES.getOrDefault(code, "Extra (" + code.toUpperCase() + ")");
                        availableLanguages.add(new LanguageInfo(code, displayName, bundle));
                    } catch (MissingResourceException e) {
                        // Ignorar si no se puede cargar
                    }
                }
            }
        }
    }

    private ResourceBundle getBundle(String baseName, Locale locale) {
        if (externalI18nDir != null && externalI18nDir.exists()) {
            try {
                URL[] urls = {externalI18nDir.toURI().toURL()};
                ClassLoader loader = new URLClassLoader(urls);
                return ResourceBundle.getBundle(baseName, locale, loader);
            } catch (MalformedURLException e) {
                // Fallback a classpath
            } catch (MissingResourceException e) {
                // Fallback a classpath
            }
        }
        return ResourceBundle.getBundle(baseName, locale);
    }
    
    /**
     * Obtiene la lista de idiomas disponibles.
     * @return Lista de LanguageInfo con los idiomas detectados
     */
    public List<LanguageInfo> getAvailableLanguages() {
        return new ArrayList<>(availableLanguages);
    }
    
    /**
     * Obtiene solo los nombres legibles de los idiomas disponibles.
     * @return Lista de nombres de idiomas (ej: ["Español", "English", "Italiano"])
     */
    public List<String> getAvailableLanguageNames() {
        return availableLanguages.stream()
            .map(LanguageInfo::displayName)
            .toList();
    }
    
    /**
     * Obtiene el código de idioma desde el nombre legible.
     * @param displayName Nombre legible (ej: "Español")
     * @return Código del idioma (ej: "es")
     */
    public String getLanguageCodeByName(String displayName) {
        return availableLanguages.stream()
            .filter(l -> l.displayName().equals(displayName))
            .map(LanguageInfo::code)
            .findFirst()
            .orElse("es");
    }
    
    /**
     * Obtiene el nombre legible desde el código de idioma.
     * @param code Código del idioma (ej: "es")
     * @return Nombre legible (ej: "Español")
     */
    public String getLanguageNameByCode(String code) {
        return availableLanguages.stream()
            .filter(l -> l.code().equals(code))
            .map(LanguageInfo::displayName)
            .findFirst()
            .orElse("Español");
    }
    
    /**
     * Cambia el idioma actual de la aplicación.
     * @param languageCode Código del idioma (ej: "es", "en", "it")
     */
    public void setLanguage(String languageCode) {
        String code = languageCode == null || languageCode.isBlank() ? "es" : languageCode;
        
        LanguageInfo langInfo = availableLanguages.stream()
            .filter(l -> l.code().equals(code))
            .findFirst()
            .orElse(null);
        
        if (langInfo != null) {
            currentLanguageCode = code;
            currentBundle = langInfo.bundle();
        }
    }
    
    /**
     * Obtiene el código del idioma actual.
     * @return Código del idioma (ej: "es")
     */
    public String getCurrentLanguageCode() {
        return currentLanguageCode;
    }
    
    /**
     * Obtiene el nombre legible del idioma actual.
     * @return Nombre del idioma (ej: "Español")
     */
    public String getCurrentLanguageName() {
        return getLanguageNameByCode(currentLanguageCode);
    }
    
    /**
     * Obtiene el ResourceBundle del idioma actual.
     * @return ResourceBundle para localizar textos
     */
    public ResourceBundle getCurrentBundle() {
        if (currentBundle == null) {
            try {
                currentBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag(currentLanguageCode));
            } catch (MissingResourceException e) {
                currentBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT);
            }
        }
        return currentBundle;
    }
    
    /**
     * Obtiene una cadena de texto localizada.
     * @param key Clave de la cadena
     * @param fallback Valor por defecto si la clave no existe
     * @return Texto localizado o fallback
     */
    public String getString(String key, String fallback) {
        try {
            ResourceBundle bundle = getCurrentBundle();
            return bundle.containsKey(key) ? bundle.getString(key) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
    
    /**
     * Obtiene una cadena de texto localizada.
     * @param key Clave de la cadena
     * @return Texto localizado o la misma clave si no existe
     */
    public String getString(String key) {
        return getString(key, key);
    }
    
    /**
     * Verifica si un idioma está disponible.
     * @param languageCode Código del idioma
     * @return true si el idioma está disponible
     */
    public boolean isLanguageAvailable(String languageCode) {
        return availableLanguages.stream()
            .anyMatch(l -> l.code().equals(languageCode));
    }
    
    /**
     * Información sobre un idioma disponible.
     */
    public record LanguageInfo(String code, String displayName, ResourceBundle bundle) {}
    public ResourceBundle getBundleForCode(String code) {
        for (LanguageInfo info : availableLanguages) {
            if (info.code().equals(code)) {
                return info.bundle();
            }
        }
        // Fallback
        Locale locale = Locale.forLanguageTag(code);
        return getBundle(BUNDLE_BASE_NAME, locale);
    }
}
