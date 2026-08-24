# Changelog

## 1.3.0 - 2026-08-24

### Añadido
- **Editores avanzados para Game Boy**: Editor de audio PSG/VGM y visor de estructura/tilemap.
- **Refactorización y Herramientas**: Soporte para refactorización de renombrado, formateo de código, plegado de código (code folding), breadcrumbs de símbolos y estadísticas de proyecto.

## 1.2.5 - 2026-08-01

### Añadido
- **Minimapa interactivo (Minimap)** en el editor de código con navegación rápida y salto de línea por clic/arrastre.
- **Intellisense Contextual por SDK** en el autocompletado (`Ctrl+Space`) con palabras clave para GBDK, Conio, MS-DOS y C estándar.
- **Soporte de Desarrollo y Emulación para MS-DOS**: Plantillas C y ASM (`.com`), detección de plataforma y lanzamiento vía DOSBox.
- **Soporte de Desarrollo y Emulación para Sega Master System / Game Gear**: Plantillas C y ASM, detección de plataforma y emulación integrada por defecto con Emulicious.

## 1.2.4 - 2026-08-01

### Añadido
- Iteración 1.2.4 de desarrollo y reubicación de documentación generada a `.trash/`.

## 1.2.3 - 2026-08-01

### Añadido
- **Plantillas para nuevos archivos**: nuevo ítem "Nuevo archivo desde plantilla..." en el menú Archivo. Incluye plantillas para Game Boy (C/GBDK), ZX Spectrum (C/Z88DK), Amstrad CPC (C/Z88DK), ensamblador z80 y C genérico. Si el nombre ya existe se genera un nombre alternativo.

## 1.2.2 - 2026-08-01

### Añadido
- **Autocompletado básico**: `Ctrl+Space` sugiere palabras del archivo actual ordenadas por frecuencia. Navegación con flechas, inserción con Enter/Tab o doble clic, cierre con Escape.

## 1.2.1 - 2026-08-01

### Añadido
- **Atajo para comentar/descomentar**: `Ctrl+/` comenta o descomenta la línea o selección actual. Usa `//` para C/C++ y `;` para Assembly, respetando la indentación.

## 1.2.0 - 2026-07-31

### Añadido
- **Marketplace de Idiomas**: Sistema dinámico para descargar e instalar nuevas traducciones desde la interfaz de configuración sin necesidad de reiniciar la aplicación.
- **Mejoras en Exportación de Tiles**: Soporte para incluir el modificador `const` y directivas `#define` (ancho, alto, cantidad) en el generador de código para Game Boy, facilitando la integración con GBDK.
- **Gestión de Sesión**: La aplicación ahora restaura automáticamente el estado de la última sesión (proyecto y archivos abiertos) al iniciarse.
- **Logs de Aplicación**: Opción para habilitar el guardado del output de la consola en un archivo de texto (`samaruc.log`) desde la configuración.
- **Estabilidad**: Corregidos fallos relacionados con claves de internacionalización faltantes (`menu.help.z88dkDocs`).

## 1.1.0 - 2026-07-05

### Añadido
- Soporte de Assembly: resaltado de sintaxis y compilación de código Assembly.
- Integración de emulador y compilación de proyectos para Amstrad CPC.
- Novedades de versión en la Home page.
- Visor de archivos `.md`.
- Resaltado de sintaxis para `.json`.
- Integración del editor de tiles de Game Boy.
- Generación automática de configuración de workspace en JSON al crear proyectos.
- Opción de renombrar archivos y carpetas.
- Reemplazo en búsquedas.
- Botón para ir al inicio de la consola de output.
- Botón para ir al final de la consola de output.
- Opción en Ayuda para consultar la documentación de GBDK-2020 dentro de la app.
- Apertura rápida de archivos y proyectos recientes.
- Soporte de más idiomas en interfaz: francés, alemán e italiano.
- Añadida una terminal de windows integrada en la app para ejecutar comandos de compilación y otros.

### Mejorado
- Home page más moderna y atractiva visualmente.
- Rediseño visual general del frontend con estilo moderno y dark mode, incluyendo mejoras sobre el editor RichEdit.
- Rediseño del menu de configuración de la aplicación.
- Rediseño de la zona de consola y se añade funcionalidades a esta