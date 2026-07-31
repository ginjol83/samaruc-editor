# LanguageService - Guía de Uso y Ejemplos

## 📌 Tabla de Contenidos

1. [Uso Básico](#uso-básico)
2. [En Controladores JavaFX](#en-controladores-javafx)
3. [Manejo de Fallbacks](#manejo-de-fallbacks)
4. [Patrones Recomendados](#patrones-recomendados)
5. [Casos de Uso Comunes](#casos-de-uso-comunes)

---

## Uso Básico

### Inicializar el Servicio

```java
import com.retroeditor.service.LanguageService;

public class MiClase {
    private LanguageService languageService = new LanguageService();
    
    public void ejemplo() {
        // El servicio se inicializa automáticamente con español (es)
        String idiomaPorDefecto = languageService.getCurrentLanguageCode(); // "es"
    }
}
```

### Cambiar el Idioma

```java
// Cambiar por código de idioma
languageService.setLanguage("en");
System.out.println(languageService.getCurrentLanguageName()); // "English"

// Cambiar por nombre legible (convertir primero)
String displayName = "Italiano";
String code = languageService.getLanguageCodeByName(displayName);
languageService.setLanguage(code);
```

### Obtener Idiomas Disponibles

```java
// Obtener lista de objetos LanguageInfo (con detalles)
List<LanguageService.LanguageInfo> idiomas = languageService.getAvailableLanguages();
for (var langInfo : idiomas) {
    System.out.println(langInfo.code() + " -> " + langInfo.displayName());
}
// Salida:
// es -> Español
// en -> English
// it -> Italiano

// Obtener solo los nombres (más común para UI)
List<String> nombres = languageService.getAvailableLanguageNames();
// ["Español", "English", "Italiano"]
```

---

## En Controladores JavaFX

### Ejemplo Completo: Actualizar ComboBox de Idiomas

```java
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import com.retroeditor.service.LanguageService;

public class SettingsController {
    
    @FXML private ComboBox<String> comboLanguage;
    private LanguageService languageService = new LanguageService();
    
    @FXML
    public void initialize() {
        // Cargar idiomas disponibles
        comboLanguage.getItems().addAll(
            languageService.getAvailableLanguageNames()
        );
        
        // Seleccionar el idioma actual
        String currentLang = languageService.getCurrentLanguageName();
        comboLanguage.getSelectionModel().select(currentLang);
        
        // Listener para cambios
        comboLanguage.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> onLanguageSelected(newVal)
        );
    }
    
    private void onLanguageSelected(String displayName) {
        String code = languageService.getLanguageCodeByName(displayName);
        languageService.setLanguage(code);
        refreshUITexts();
    }
    
    private void refreshUITexts() {
        // Actualizar todos los textos de la interfaz
        // Los labels se actualizarán automáticamente gracias a los observables
    }
}
```

### Ejemplo: Localizar Labels

```java
public class MainController {
    
    @FXML private Label lblTitle;
    @FXML private Button btnSave;
    @FXML private MenuItem menuFile;
    
    private LanguageService languageService = new LanguageService();
    
    public void refreshTexts() {
        // Usar getBundle() para acceso directo
        ResourceBundle bundle = languageService.getCurrentBundle();
        
        lblTitle.setText(bundle.getString("label.configTitle"));
        btnSave.setText(bundle.getString("button.saveLanguage"));
        menuFile.setText(bundle.getString("menu.file"));
    }
    
    // O usar el método getString() con fallback
    public void updateUI() {
        lblTitle.setText(
            languageService.getString("label.configTitle", "Configuration")
        );
        
        btnSave.setText(
            languageService.getString("button.saveLanguage", "Save")
        );
    }
}
```

---

## Manejo de Fallbacks

### Patrón Seguro con Fallback

```java
public class ReportGenerator {
    private LanguageService languageService;
    
    public void generateReport() {
        // Manera segura: siempre tener fallback
        String title = languageService.getString(
            "report.title",
            "Untranslated Report"  // ← Fallback predeterminado
        );
        
        String date = languageService.getString(
            "report.dateLabel",
            "Date"
        );
        
        System.out.println(title + "\n" + date);
    }
}
```

### Detectar Keys Faltantes en Desarrollo

```java
public class I18nValidator {
    private LanguageService languageService;
    
    public boolean isKeyTranslated(String key) {
        ResourceBundle bundle = languageService.getCurrentBundle();
        return bundle.containsKey(key);
    }
    
    public void validateAllKeys() {
        List<String> requiredKeys = List.of(
            "button.save",
            "button.cancel",
            "menu.file",
            "menu.edit",
            "label.title"
        );
        
        for (String key : requiredKeys) {
            if (!isKeyTranslated(key)) {
                System.err.println("⚠️ Missing translation: " + key);
            }
        }
    }
}
```

---

## Patrones Recomendados

### ✅ Patrón 1: Inyección de Dependencia

```java
public class ConfigDialog {
    private LanguageService languageService;
    
    // Constructor injection (preferido)
    public ConfigDialog(LanguageService languageService) {
        this.languageService = languageService;
    }
    
    public void show() {
        // Usar el servicio inyectado
        String title = languageService.getString("dialog.config.title");
    }
}
```

### ✅ Patrón 2: Singleton Global

```java
public class AppContext {
    private static LanguageService languageService;
    
    public static LanguageService getLanguageService() {
        if (languageService == null) {
            languageService = new LanguageService();
        }
        return languageService;
    }
}

// Uso:
public class SomeController {
    public void init() {
        String msg = AppContext.getLanguageService()
            .getString("some.key");
    }
}
```

### ✅ Patrón 3: Método Helper Corto

```java
public abstract class BaseController {
    protected LanguageService i18n = new LanguageService();
    
    protected String t(String key) {
        return i18n.getString(key);
    }
    
    protected String t(String key, String fallback) {
        return i18n.getString(key, fallback);
    }
}

// Uso en subclases:
public class MyController extends BaseController {
    public void setupUI() {
        button.setText(t("button.save"));
        label.setText(t("label.unknown", "N/A"));
    }
}
```

---

## Casos de Uso Comunes

### 1. Cambiar Idioma de la Aplicación Completa

```java
public class LanguageChangeService {
    private LanguageService languageService;
    private List<LanguageObserver> observers = new ArrayList<>();
    
    public void changeLanguage(String languageCode) {
        // Validar
        if (!languageService.isLanguageAvailable(languageCode)) {
            throw new IllegalArgumentException("Language not available: " + languageCode);
        }
        
        // Cambiar
        languageService.setLanguage(languageCode);
        
        // Notificar a todos los observadores
        notifyAllObservers();
    }
    
    private void notifyAllObservers() {
        for (LanguageObserver observer : observers) {
            observer.onLanguageChanged(languageService.getCurrentLanguageCode());
        }
    }
    
    @FunctionalInterface
    public interface LanguageObserver {
        void onLanguageChanged(String newLanguageCode);
    }
}
```

### 2. Combo Box de Idiomas de README

```java
public class ProjectTemplateDialog {
    @FXML private ComboBox<String> comboReadmeLanguage;
    private LanguageService languageService;
    
    @FXML
    public void initialize() {
        // Obtener bundle actual para las etiquetas
        ResourceBundle bundle = languageService.getCurrentBundle();
        
        // Cargar opciones de idioma para README
        for (LanguageService.LanguageInfo langInfo : languageService.getAvailableLanguages()) {
            String code = langInfo.code();
            String key = "config.readmeLanguage.option." + code;
            
            String label = bundle.containsKey(key) 
                ? bundle.getString(key)
                : langInfo.displayName();
            
            comboReadmeLanguage.getItems().add(label);
        }
    }
}
```

### 3. Exportar Traducciones a CSV

```java
import java.io.FileWriter;
import java.io.IOException;

public class TranslationExporter {
    private LanguageService languageService;
    
    public void exportToCsv(String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            // Encabezado
            writer.write("Key");
            for (var langInfo : languageService.getAvailableLanguages()) {
                writer.write("," + langInfo.code());
            }
            writer.write("\n");
            
            // Contenido
            ResourceBundle esBundle = ResourceBundle.getBundle(
                "i18n.MessagesBundle", 
                java.util.Locale.forLanguageTag("es")
            );
            
            for (String key : esBundle.keySet()) {
                writer.write(key);
                
                for (var langInfo : languageService.getAvailableLanguages()) {
                    languageService.setLanguage(langInfo.code());
                    String value = languageService.getString(key, "");
                    writer.write("," + escapeCSV(value));
                }
                writer.write("\n");
            }
        }
    }
    
    private String escapeCSV(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
```

### 4. Validar Consistencia de Traducciones

```java
public class TranslationConsistencyValidator {
    private LanguageService languageService;
    
    public Map<String, List<String>> findMissingTranslations() {
        Map<String, List<String>> missingByLanguage = new HashMap<>();
        
        ResourceBundle esBundle = ResourceBundle.getBundle(
            "i18n.MessagesBundle",
            java.util.Locale.forLanguageTag("es")
        );
        
        for (var langInfo : languageService.getAvailableLanguages()) {
            List<String> missing = new ArrayList<>();
            languageService.setLanguage(langInfo.code());
            
            for (String key : esBundle.keySet()) {
                ResourceBundle langBundle = languageService.getCurrentBundle();
                if (!langBundle.containsKey(key)) {
                    missing.add(key);
                }
            }
            
            if (!missing.isEmpty()) {
                missingByLanguage.put(langInfo.displayName(), missing);
            }
        }
        
        return missingByLanguage;
    }
}
```

---

## ⚠️ Cosas a Evitar

### ❌ NO hacer esto

```java
// MAL: Hardcodear idiomas
if ("Español".equals(selectedLanguage)) {
    configModel.setLanguage("es");
} else if ("English".equals(selectedLanguage)) {
    configModel.setLanguage("en");
}

// MEJOR: Usar LanguageService
String code = languageService.getLanguageCodeByName(selectedLanguage);
languageService.setLanguage(code);
```

### ❌ NO hacer esto

```java
// MAL: ResourceBundle directo cada vez
ResourceBundle bundle = ResourceBundle.getBundle("i18n.MessagesBundle", Locale.forLanguageTag("es"));
String text = bundle.getString("some.key");

// MEJOR: Usar LanguageService
String text = languageService.getString("some.key");
```

### ❌ NO hacer esto

```java
// MAL: Sin fallback
String label = languageService.getString("potentially.missing.key"); // ¡Puede fallar!

// MEJOR: Siempre tener fallback
String label = languageService.getString("potentially.missing.key", "Default Label");
```

---

## 🔧 Debugging

### Ver Idiomas Disponibles en Consola

```java
public class DebugLanguageService {
    public static void printAvailableLanguages(LanguageService languageService) {
        System.out.println("=== Available Languages ===");
        for (var langInfo : languageService.getAvailableLanguages()) {
            System.out.println(
                String.format("  %s: %s", langInfo.code(), langInfo.displayName())
            );
        }
    }
}

// Uso:
DebugLanguageService.printAvailableLanguages(languageService);
// Salida:
// === Available Languages ===
//   es: Español
//   en: English
//   it: Italiano
```

### Ver Keys de Traducción

```java
ResourceBundle bundle = languageService.getCurrentBundle();
System.out.println("Keys in current bundle (" + languageService.getCurrentLanguageCode() + "):");
Collections.list(bundle.getKeys()).forEach(System.out::println);
```

---

## 📚 Referencias

- **Clase:** `com.retroeditor.service.LanguageService`
- **Archivos de Propiedades:** `src/main/resources/i18n/MessagesBundle_XX.properties`
- **Tests:** `src/test/java/com/retroeditor/service/LanguageServiceTest.java`
- **Guía Principal:** `I18N_LANGUAGE_SERVICE_GUIDE.md`

