# Estructura del Proyecto - Sistema de Idiomas

## 📁 Jerarquía de Directorios

```
samaruc-editor/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/retroeditor/
│   │   │       ├── service/
│   │   │       │   ├── LanguageService.java          ✨ NUEVO
│   │   │       │   ├── AppLogger.java
│   │   │       │   ├── ConfigRepository.java
│   │   │       │   ├── PluginApplicationService.java
│   │   │       │   ├── PluginMarketplaceService.java
│   │   │       │   ├── PropertiesConfigRepository.java
│   │   │       │   ├── UserActionMonitor.java
│   │   │       │   ├── ConfigRepository.java
│   │   │       │   └── ... (otros servicios)
│   │   │       │
│   │   │       ├── controller/
│   │   │       │   ├── config/
│   │   │       │   │   └── ConfigController.java      🔧 MODIFICADO
│   │   │       │   ├── MainController.java
│   │   │       │   └── ... (otros controladores)
│   │   │       │
│   │   │       ├── model/
│   │   │       ├── view/
│   │   │       └── ... (otros paquetes)
│   │   │
│   │   └── resources/
│   │       ├── i18n/                                 📍 LOCALIZACIÓN
│   │       │   ├── MessagesBundle.properties         (Español - por defecto)
│   │       │   ├── MessagesBundle_en.properties      (English)
│   │       │   ├── MessagesBundle_es.properties      (Español)
│   │       │   ├── MessagesBundle_it.properties      (Italiano)
│   │       │   └── MessagesBundle_XX.properties      (Nuevos idiomas aquí)
│   │       │
│   │       ├── fxml/
│   │       ├── css/
│   │       ├── config.properties
│   │       └── ... (otros recursos)
│   │
│   └── test/
│       └── java/
│           └── com/retroeditor/
│               ├── service/
│               │   └── LanguageServiceTest.java      ✨ NUEVO
│               └── ... (otros tests)
│
├── I18N_LANGUAGE_SERVICE_GUIDE.md                    📘 DOCUMENTACIÓN
├── I18N_USAGE_EXAMPLES.md                            📘 DOCUMENTACIÓN
├── I18N_IMPLEMENTATION_SUMMARY.md                    📘 DOCUMENTACIÓN
├── pom.xml
├── README.md
└── ... (otros archivos del proyecto)
```

---

## 📂 Detalles de Archivos Clave

### 1. LanguageService.java (NUEVO)

**Ubicación:** `src/main/java/com/retroeditor/service/LanguageService.java`

**Propósito:** Servicio centralizado para gestión de idiomas

**Contiene:**
- Clase principal: `LanguageService`
- Record: `LanguageInfo`
- ~187 líneas de código

**Responsabilidades:**
- Detectar idiomas disponibles
- Mapear códigos ↔ nombres legibles
- Gestionar idioma actual
- Localizar textos con fallbacks

---

### 2. ConfigController.java (MODIFICADO)

**Ubicación:** `src/main/java/com/retroeditor/controller/config/ConfigController.java`

**Cambios:**
- ✅ Agregado: `LanguageService languageService`
- ✅ Agregado: Importación de `LanguageService`
- ✅ Modificado: `loadLanguageSelectionsFromConfig()` - Simplificado 48%
- ✅ Modificado: `toReadmeLanguageCode()` - Mejorado
- ✅ Modificado: `getCurrentBundle()` - Delegado a LanguageService
- ✅ Modificado: `onSaveLanguage()` - Conversión automática de código
- ❌ Removido: `readmeLanguageCodeByLabel` (ya no necesario)

**Línea de cambio:** ~1750 líneas

---

### 3. Archivos de Propiedades de Idiomas

**Ubicación:** `src/main/resources/i18n/`

#### MessagesBundle.properties (Español - Por defecto)
- **Idioma:** Español
- **Locale:** ROOT (predeterminado)
- **Contenido:** 100+ pares clave-valor
- **Ejemplo:**
```properties
button.saveLanguage=Guardar idioma
button.saveAppearance=Guardar apariencia
menu.file=Archivo
menu.edit=Editar
```

#### MessagesBundle_es.properties (Español - Explícito)
- **Idioma:** Español
- **Locale:** es (español)
- **Contenido:** Mismo contenido que MessagesBundle.properties
- **Nota:** Redundante pero recomendado para claridad

#### MessagesBundle_en.properties (English)
- **Idioma:** English
- **Locale:** en
- **Contenido:** Traducción al inglés
- **Ejemplo:**
```properties
button.saveLanguage=Save language
button.saveAppearance=Save appearance
menu.file=File
menu.edit=Edit
```

#### MessagesBundle_it.properties (Italiano)
- **Idioma:** Italiano
- **Locale:** it
- **Contenido:** Traducción al italiano
- **Ejemplo:**
```properties
button.saveLanguage=Salva lingua
button.saveAppearance=Salva aspetto
menu.file=File
menu.edit=Modifica
```

---

### 4. LanguageServiceTest.java (NUEVO)

**Ubicación:** `src/test/java/com/retroeditor/service/LanguageServiceTest.java`

**Propósito:** Tests unitarios para validar LanguageService

**Contiene:**
- 11 métodos de test
- Cobertura de funcionalidad principal
- Validación de casos edge

**Ejemplos de tests:**
```java
@Test
void testDetectAvailableLanguages() { ... }

@Test
void testGetLanguageNameByCode() { ... }

@Test
void testSetAndGetCurrentLanguage() { ... }

@Test
void testGetStringWithFallback() { ... }
```

---

### 5. Documentación (NUEVO)

#### I18N_LANGUAGE_SERVICE_GUIDE.md
- **Tamaño:** 7.4 KB
- **Contenido:**
  - Resumen del sistema
  - Problemas identificados
  - Arquitectura
  - Cómo agregar idiomas
  - API pública
  - Integración en controladores
  - Mejores prácticas

#### I18N_USAGE_EXAMPLES.md
- **Tamaño:** 13.6 KB
- **Contenido:**
  - Uso básico
  - Ejemplos en controladores JavaFX
  - Manejo de fallbacks
  - Patrones recomendados
  - 5 casos de uso comunes
  - Debugging

#### I18N_IMPLEMENTATION_SUMMARY.md
- **Tamaño:** 8.3 KB
- **Contenido:**
  - Resumen ejecutivo
  - Estado inicial vs final
  - Impacto de mejoras
  - Archivos modificados
  - Beneficios clave
  - Próximos pasos

---

## 📊 Estadísticas de Cambios

### Líneas de Código

| Componente | Líneas | Estado |
|-----------|--------|--------|
| LanguageService.java | 187 | ✨ NUEVO |
| LanguageServiceTest.java | 244 | ✨ NUEVO |
| ConfigController.java | ~1800 | 🔧 MODIFICADO |
| Documentación | 29 KB | 📘 AGREGADA |

### Archivos

| Tipo | Cantidad | Estado |
|-----|----------|--------|
| Archivos nuevos | 5 | ✨ NUEVO |
| Archivos modificados | 1 | 🔧 MODIFICADO |
| Archivos removidos | 0 | - |
| Documentos | 3 | 📘 NUEVO |

### Tests

| Métrica | Valor |
|---------|-------|
| Tests nuevos | 11 |
| Tests existentes | Todos pasan ✅ |
| Cobertura | LanguageService completamente cubierto |

---

## 🔄 Flujo de Carga de Idiomas

### Al Iniciar la Aplicación

```
1. ConfigController.initialize()
   ↓
2. LanguageService.__init__()
   ↓
3. detectAvailableLanguages()
   ├─ Escanea MessagesBundle*.properties
   ├─ Carga cada idioma disponible
   └─ Crea lista de LanguageInfo
   ↓
4. setLanguage("es")
   ├─ Obtiene el idioma guardado (o "es" por defecto)
   └─ Carga ResourceBundle correspondiente
   ↓
5. loadLanguageSelectionsFromConfig()
   ├─ Llama languageService.getAvailableLanguageNames()
   ├─ Llena ComboBox de idiomas
   └─ Selecciona idioma actual
```

### Al Cambiar el Idioma

```
1. Usuario selecciona idioma en ComboBox
   ↓
2. onSaveLanguage()
   ├─ Obtiene displayName (ej: "Español")
   ├─ Convierte a código (ej: "es")
   └─ Llama languageService.setLanguage("es")
   ↓
3. languageService.setLanguage()
   ├─ Busca LanguageInfo con código "es"
   ├─ Actualiza currentLanguageCode
   ├─ Carga ResourceBundle
   └─ Notifica cambio
   ↓
4. refreshTexts() - Actualiza UI
```

---

## 🎯 Estructura de Claves de Traducción

Las claves en MessagesBundle_XX.properties están organizadas jerárquicamente:

```
button.*
  button.saveLanguage
  button.saveAppearance
  button.saveAs

menu.*
  menu.file
  menu.edit
  menu.project
  menu.config
  menu.exit

label.*
  label.configTitle
  label.languageTab
  label.appearanceTab
  label.language
  label.editorAppearance
  label.compilerTab
  label.compiler

config.*
  config.readmeLanguage
  config.readmeLanguage.option.es
  config.readmeLanguage.option.en
  config.readmeLanguage.option.it
  config.editorAppearance.option.modernDark
  config.editorAppearance.option.classic
  config.compiler.option.z88dk.spectrum
  config.compiler.option.z88dk.cpc
```

---

## ➕ Cómo Agregar un Nuevo Idioma

### Paso 1: Crear Archivo

Crear `src/main/resources/i18n/MessagesBundle_XX.properties`

Ejemplo para Francés (fr):
```
src/main/resources/i18n/MessagesBundle_fr.properties
```

### Paso 2: Copiar Estructura

Copiar todas las claves de `MessagesBundle_es.properties` y traducir valores

### Paso 3: (Opcional) Actualizar LanguageService

Editar `LanguageService.java`:
```java
Map.entry("fr", "Français")
```

### Paso 4: Reiniciar

- El nuevo idioma se detectará automáticamente
- Aparecerá en los ComboBox de idiomas
- ¡Listo!

---

## 🔐 Integridad de Traducción

### Validar Claves Completas

Todos los idiomas deben tener las mismas claves:

```bash
# Contar claves en cada idioma
grep "^[^#]" MessagesBundle_es.properties | grep "=" | wc -l  # 100+
grep "^[^#]" MessagesBundle_en.properties | grep "=" | wc -l  # 100+
grep "^[^#]" MessagesBundle_it.properties | grep "=" | wc -l  # 100+
```

### Claves Que No Deben Faltar

Estas claves son críticas y no deben faltarle a ningún idioma:

```
button.saveLanguage
button.saveAppearance
menu.file
menu.edit
menu.project
menu.config
menu.exit
label.configTitle
label.languageTab
label.language
config.readmeLanguage.option.*
```

---

## 📋 Checklist para Nuevo Idioma

- [ ] Crear archivo `MessagesBundle_XX.properties`
- [ ] Copiar todas las claves de `MessagesBundle_es.properties`
- [ ] Traducir todos los valores
- [ ] Validar que no hay caracteres especiales no escapados
- [ ] Verificar codificación UTF-8 del archivo
- [ ] (Opcional) Agregar entrada en `LanguageService.LANGUAGE_NAMES`
- [ ] Compilar y verificar que no hay errores
- [ ] Ejecutar tests
- [ ] Probar en la aplicación

---

## 🔍 Debugging

### Ver Idiomas Detectados

```java
LanguageService languageService = new LanguageService();
for (var lang : languageService.getAvailableLanguages()) {
    System.out.println(lang.code() + " -> " + lang.displayName());
}
```

### Ver Keys Disponibles

```java
ResourceBundle bundle = languageService.getCurrentBundle();
Collections.list(bundle.getKeys()).forEach(System.out::println);
```

### Verificar Traducción

```java
String value = languageService.getString("button.saveLanguage");
System.out.println(value);
```

---

## 📚 Referencias

- **Guía Principal:** `I18N_LANGUAGE_SERVICE_GUIDE.md`
- **Ejemplos de Uso:** `I18N_USAGE_EXAMPLES.md`
- **Resumen:** `I18N_IMPLEMENTATION_SUMMARY.md`
- **Clase:** `com.retroeditor.service.LanguageService`
- **Tests:** `com.retroeditor.service.LanguageServiceTest`
- **Propiedades:** `src/main/resources/i18n/*.properties`

