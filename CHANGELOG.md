# Changelog

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