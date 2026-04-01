# SamaruC Editor

Editor de código C básico en Java usando JavaFX y FXML.

## Características
- Abrir, guardar, cerrar archivos .c y .h
- Resaltado de sintaxis para C (RichTextFX)
- Menú y toolbar básicos
- Estructura modular (MVC)

## Requisitos
- Java 17+
- Maven

## Instalar y ejecutar

```bash
mvn compile
mvn install
mvn javafx:run
```

## Estructura
- `src/main/java/com/retroeditor/` - Código fuente
- `src/main/resources/fxml/` - Vistas FXML
- `src/main/resources/css/` - Estilos

## Notas
- El resaltado de sintaxis es básico y puede mejorarse.

## Build de release
- Consulta `README-build-release.md` para el flujo de `build-release.ps1` (jpackage + copia garantizada de `libs`).
