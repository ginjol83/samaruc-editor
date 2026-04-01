# Instrucciones para GitHub Copilot: crear un plugin para SamaruC Editor

Este documento está escrito para ser usado como contexto de GitHub Copilot.
Cuando el usuario pida *"crea un plugin para SamaruC"*, sigue estos pasos en orden.
No omitas ningún paso. No preguntes al usuario lo que puedes inferir del enunciado.

---

## PASO 1 — Recopilar información del usuario

Antes de generar código, determina los siguientes datos. Si el usuario ya los mencionó en su petición, úsalos directamente sin preguntar:

| Dato | Descripción | Ejemplo |
|------|-------------|---------|
| `PLUGIN_NAME` | Nombre legible del plugin | `"Conversor de Paletas"` |
| `ARTIFACT_ID` | Identificador Maven (kebab-case) | `conversor-paletas` |
| `GROUP_ID` | Paquete Java base del plugin | `com.miplugin` |
| `MAIN_CLASS` | Nombre simple de la clase principal | `ConvertidorPaletasPlugin` |
| `MENU_LABEL` | Texto del menú que añadirá el plugin | `"Paletas"` |
| `DESCRIPTION` | Una frase describiendo qué hace el plugin | `"Convierte paletas de colores entre formatos"` |

Si algún dato no está claro y no puedes inferirlo, usa un valor por defecto razonable basándote en `PLUGIN_NAME`.

---

## PASO 2 — Crear la estructura de directorios

Crea los siguientes ficheros y carpetas en el directorio de trabajo actual (o en la raíz del workspace):

```
{ARTIFACT_ID}/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── {GROUP_ID como ruta}/
        │       └── {MAIN_CLASS}.java
        └── resources/
            └── META-INF/
                └── services/
                    └── com.retroeditor.plugin.Plugin
```

**Ejemplo** con `GROUP_ID=com.miplugin` y `MAIN_CLASS=ConvertidorPaletasPlugin`:

```
conversor-paletas/
└── src/main/java/com/miplugin/ConvertidorPaletasPlugin.java
```

---

## PASO 3 — Generar el `pom.xml`

Genera el fichero `{ARTIFACT_ID}/pom.xml` con exactamente este contenido (sustituyendo las variables):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>{GROUP_ID}</groupId>
    <artifactId>{ARTIFACT_ID}</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>{PLUGIN_NAME}</name>
    <description>{DESCRIPTION}</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!--
            Interfaces de SamaruC Editor.
            scope=provided: ya está en el classpath del host en runtime.
            Instálalo en tu repo local con:
              mvn install:install-file -Dfile=samaruc-1.0-SNAPSHOT.jar
                -DgroupId=com.retroeditor -DartifactId=samaruc
                -Dversion=1.0-SNAPSHOT -Dpackaging=jar
        -->
        <dependency>
            <groupId>com.retroeditor</groupId>
            <artifactId>samaruc</artifactId>
            <version>1.0-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>

        <!-- JavaFX: scope=provided, lo provee el host -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>17.0.10</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-fxml</artifactId>
            <version>17.0.10</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
            </plugin>
            <!--
                NO añadas maven-shade aquí.
                El JAR del plugin debe contener SOLO las clases propias del plugin.
                JavaFX y las interfaces de SamaruC las provee el host en runtime.
            -->
        </plugins>
    </build>
</project>
```

> **Regla crítica:** Nunca pongas `maven-shade-plugin` en el pom del plugin. Duplicaría clases de JavaFX y rompería el classloader del host.

---

## PASO 4 — Generar la clase principal del plugin

Genera `{ARTIFACT_ID}/src/main/java/{GROUP_ID_COMO_RUTA}/{MAIN_CLASS}.java`.

### Plantilla base obligatoria

```java
package {GROUP_ID};

import com.retroeditor.plugin.Plugin;
import com.retroeditor.plugin.PluginContext;
import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/**
 * {DESCRIPTION}
 */
public class {MAIN_CLASS} implements Plugin {

    private PluginContext context;

    // -------------------------------------------------------------------------
    // Ciclo de vida del plugin
    // -------------------------------------------------------------------------

    /**
     * Llamado cuando el JAR se carga. Guarda el contexto; NO toques la UI aquí.
     */
    @Override
    public void onLoad(PluginContext context) {
        this.context = context;
        context.log("{PLUGIN_NAME} cargado.");
    }

    /**
     * Llamado cuando la UI de SamaruC está lista.
     * SIEMPRE usa Platform.runLater() para modificar la UI.
     */
    @Override
    public void onEnable() {
        Platform.runLater(() -> {
            Menu menu = new Menu("{MENU_LABEL}");

            // --- añade aquí los MenuItem con la lógica del plugin ---
            MenuItem accion = new MenuItem("Acción principal");
            accion.setOnAction(e -> ejecutarAccionPrincipal());
            menu.getItems().add(accion);
            // ----------------------------------------------------------

            // registerMenu() añade el menú a la barra Y registra la referencia
            // para que SamaruC lo elimine automáticamente al desactivar el plugin.
            context.registerMenu(menu);
        });
    }

    /**
     * Llamado al desactivar o cerrar la app.
     * Los menús registrados con registerMenu() se limpian solos;
     * aquí cierra hilos, streams, conexiones, etc.
     */
    @Override
    public void onDisable() {
        context.log("{PLUGIN_NAME} desactivado.");
    }

    /**
     * Nombre visible en la pantalla de configuración → pestaña Plugins.
     */
    @Override
    public String getName() {
        return "{PLUGIN_NAME}";
    }

    // -------------------------------------------------------------------------
    // Lógica interna del plugin
    // -------------------------------------------------------------------------

    private void ejecutarAccionPrincipal() {
        // TODO: implementar la lógica específica del plugin
        context.log("{PLUGIN_NAME}: acción principal ejecutada.");
    }
}
```

### Reglas que debes respetar al rellenar la lógica

1. **Hilo de JavaFX**: toda manipulación de nodos, diálogos o escena debe estar dentro de `Platform.runLater(() -> { ... })`.
2. **Diálogos modales**: usa `dialogo.initOwner(context.getPrimaryStage())` para que el diálogo bloquee la ventana principal.
3. **Logging**: usa `context.log("mensaje")` para escribir en la pestaña Monitor de SamaruC. No uses `System.out` directamente.
4. **Limpieza**: si el plugin crea hilos (`Thread`, `ExecutorService`, timers, etc.), guárdalos como campos y ciérralos en `onDisable()`.
5. **No accedas a campos estáticos de la app host**: usa únicamente los métodos de `PluginContext`.

---

## PASO 5 — Generar el fichero de descriptor de ServiceLoader

Crea el fichero:

```
{ARTIFACT_ID}/src/main/resources/META-INF/services/com.retroeditor.plugin.Plugin
```

Con el contenido (una línea por clase implementadora):

```
{GROUP_ID}.{MAIN_CLASS}
```

**Ejemplo:**

```
com.miplugin.ConvertidorPaletasPlugin
```

> **Atención:** El nombre del fichero es exactamente `com.retroeditor.plugin.Plugin` (sin extensión). Cualquier error tipográfico hará que ServiceLoader no encuentre el plugin.

---

## PASO 6 — Verificar la estructura generada

Antes de terminar, comprueba mentalmente (o con herramientas de ficheros) que la estructura generada es:

```
{ARTIFACT_ID}/
├── pom.xml                                          ✅ sin maven-shade
└── src/main/
    ├── java/{GROUP_ID_RUTA}/{MAIN_CLASS}.java       ✅ implementa Plugin
    └── resources/META-INF/services/
        └── com.retroeditor.plugin.Plugin            ✅ contiene GROUP_ID.MAIN_CLASS
```

Si falta cualquiera de estos tres ficheros, créalo antes de responder al usuario.

---

## PASO 7 — Indicar al usuario cómo compilar e instalar

Al finalizar la generación de código, muestra al usuario estos comandos:

### Compilar

```bash
# Primero instala el JAR de SamaruC en tu repo Maven local (solo la primera vez)
mvn install:install-file -Dfile=samaruc-1.0-SNAPSHOT.jar ^
  -DgroupId=com.retroeditor -DartifactId=samaruc ^
  -Dversion=1.0-SNAPSHOT -Dpackaging=jar

# Compilar el plugin
cd {ARTIFACT_ID}
mvn package
```

### Instalar en SamaruC

```bash
# Windows
copy target\{ARTIFACT_ID}-1.0.0.jar <ruta-a-Samaruc>\plugins\

# Linux / macOS
cp target/{ARTIFACT_ID}-1.0.0.jar <ruta-a-Samaruc>/plugins/
```

Tras copiar el JAR, en SamaruC ve a **Configuración → Plugins → Recargar plugins**.

---

## Referencia rápida de la API `PluginContext`

| Método | Tipo retorno | Cuándo usarlo |
|--------|--------------|---------------|
| `getPrimaryStage()` | `Stage` | Para crear diálogos modales con `initOwner()` |
| `getScene()` | `Scene` | Para acceder al árbol de nodos del host |
| `getMenuBar()` | `MenuBar` | Acceso directo a la barra; preferible usar `registerMenu()` |
| `log(String)` | `void` | Escribir en la pestaña Monitor de SamaruC |
| `registerMenu(Menu)` | `void` | Añadir un menú con registro automático para limpieza |
| `unregisterMenu(Menu)` | `void` | Eliminar manualmente un menú registrado |
| `unregisterAll()` | `void` | Limpieza total (llamado automáticamente por el host) |

---

## Checklist final antes de entregar el código al usuario

- [ ] `pom.xml` tiene `<scope>provided</scope>` en las dependencias de SamaruC y JavaFX
- [ ] `pom.xml` **no** contiene `maven-shade-plugin`
- [ ] La clase principal implementa `com.retroeditor.plugin.Plugin`
- [ ] `onEnable()` usa `Platform.runLater()` para la UI
- [ ] `onDisable()` limpia todos los recursos propios del plugin
- [ ] El fichero `META-INF/services/com.retroeditor.plugin.Plugin` existe y contiene el FQCN de la clase
- [ ] Ningún import hace referencia a clases internas de SamaruC fuera del paquete `com.retroeditor.plugin`
- [ ] Se han mostrado los comandos de compilación e instalación al usuario

---

*Documento de instrucciones — SamaruC Editor v1.0*
