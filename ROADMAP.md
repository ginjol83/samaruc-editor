# Samaruc Code - Roadmap

## 🚀 Estado Actual: Completado e Implementado
- [x] **Navegación y Diagnósticos**:
  - [x] Doble clic en un error de la consola para saltar a la línea en el editor (`CompilationDiagnosticParserService`).
  - [x] Breadcrumb de símbolos (`SymbolBreadcrumbService`).
  - [x] "Go to definition" básico para funciones C y prototipos.
- [x] **Edición y Productividad (Estilo IDE Profesional)**:
  - [x] Plegado de bloques (`{ }` y regiones ASM) (`CodeFoldingService`).
  - [x] Autocompletado inteligente (`Ctrl+Space`) con Intellisense Contextual por SDK (GBDK, Conio, MS-DOS, C estándar).
  - [x] Minimapa interactivo (Minimap) lateral con navegación rápida por clic y arrastre.
  - [x] Historial Local (Local History) automático por archivo con snapshots temporales y visor/restaurador de versiones (`LocalHistoryService`).
  - [x] Atajo de comentar/descomentar (`Ctrl+/`) adaptado por lenguaje (`//` para C, `;` para ASM).
  - [x] Multi-cursor y Selección de Columnas (`MultiCursorManager`) para edición simultánea en bloque (`Ctrl+Alt+Up/Down`).
  - [x] Formateador Automático de Código (`CodeFormatterService`) para indentación y embellecimiento (`Ctrl+Alt+L`).
  - [x] Estructura de Archivo / Outline View (`StructureViewService`) para análisis jerárquico de funciones, variables, macros y estructuras.
  - [x] Plantillas de código y proyectos nuevos (`FileTemplateService` para Game Boy, ZX Spectrum, Amstrad CPC, Atari, MS-DOS, Sega Master System, C genérico y ASM).
  - [x] Vista dividida para comparar archivos (Split View).
  - [x] Búsqueda y reemplazo avanzado en archivos y proyectos enteros.
- [x] **Plataformas y Emulación Retro**:
  - [x] Game Boy (GBDK-2020): editores de tiles, sprites multi-tamaño, generador de audio SFX, reproductor y tracker musical (`GameBoyAudioEditorController`, `GameBoyAudioPlayer`, `ModVgmImportService`).
  - [x] ZX Spectrum y Amstrad CPC (Z88DK), convertidores gráficos PNG a bitmap.
  - [x] Atari XE/XL (CC65).
  - [x] MS-DOS: plantillas, detección de plataforma y ejecución integrada con DOSBox.
  - [x] Sega Master System / Game Gear: plantillas, detección y emulación integrada con Emulicious.
  - [x] Opción independiente "Compilar y ejecutar" (Build & Run).
- [x] **Editores Gráficos y de Assets**:
  - [x] Editor de Mapas de Tiles (`TilemapEditorController`) con scroll, capas de colisión y generación de arreglos C.
- [x] **Análisis y Refactorización**:
  - [x] Análisis Estático en Tiempo Real (`StaticAnalysisService`) para detección de errores y advertencias.
  - [x] Renombrado Seguro de Símbolos en Proyecto (`RenameRefactoringService`) con propagación automática por límites de palabra (`\b`).
  - [x] Estadísticas del proyecto (`ProjectStatisticsService`).
  - [x] Marketplace dinámico de idiomas y plugins.
  - [x] Terminal integrada con soporte PTY (`SamarucPtyTtyConnector`).
  - [x] Gestión de sesión y preferencias de apariencia (Modo oscuro modernizado y clásico).

---

## 🎯 Próximos Pasos y Nuevas Funcionalidades Propuestas

### 1. Experiencia de Edición Avanzada (Estilo IntelliJ / VS Code)
- [ ] **Paleta de Comandos (`Ctrl+Shift+P`)**: Buscador flotante rápido para ejecutar cualquier comando, configuración, SDK o herramienta del IDE sin usar el menú superior.
- [ ] **Acciones Rápidas y Quick Fixes (Bombilla / Bulb Actions)**: Correcciones automáticas y sugerencias contextuales ante errores de compilación (ej. añadir includes faltantes, declarar prototipos).

### 2. Refactorización Avanzada
- [ ] **Jerarquía de Llamadas (Call Hierarchy)**: Visualización de qué funciones llaman a una función seleccionada y a qué funciones llama esta.

### 3. Control de Versiones y Colaboración (Git)
- [ ] **Panel Git Integrado (Source Control View)**: Vista lateral de archivos modificados, diff visual en línea y staging de cambios.
- [ ] **Gestión de Ramas (Branch Manager)**: Selector y creador rápido de ramas Git directamente desde la barra de estado.

### 4. Emulación y Depuración Avanzada (Retro Debugging)
- [ ] **Depurador Fuente a Fuente Integrado (Source-level Debugger)**:
  - Puntos de interrupción (Breakpoints) en código C mapeados a instrucciones Z80 / LR35902 / 6502.
  - Inspección en vivo de registros de CPU (`A`, `B`, `C`, `D`, `E`, `H`, `L`, `IX`, `IY`, `PC`, `SP`).
  - Visor y volcado de memoria RAM, VRAM y paletas de colores en tiempo real.
- [ ] **Profiler de Ciclos de Reloj (T-States & Scanlines Analyzer)**: Herramienta de rendimiento para medir ciclos de CPU consumidos por rutinas críticas, optimizando código para pantallas de 50/60 Hz.

### 5. Herramientas Gráficas y de Audio Expandidas
- [ ] **Optimizador y Compresor de Tilesets**: Herramientas integradas para compresión RLE, Bitpacking y Detección de Tiles Duplicados/Volteados (Flip X/Y).
- [ ] **Visor de Atributos de ZX Spectrum (Attribute Clash Analyzer)**: Herramienta visual para detectar conflictos de color de tinta/papel en bloques de 8x8 píxeles antes de compilar.
- [ ] **Tracker de Audio Multi-Canal Mejorado**: Exportación directa a formatos de driver populares (Arkos Tracker, AY/YM, Beep/PSG drivers).

### 6. Nuevas Plataformas y SDKs Retro
- [ ] **MSX y MSX2 (SDCC / C-BIOS)**: Soporte completo de plantillas, compilación y emulación (openMSX).
- [ ] **Commodore 64 (CC65 / VICE)**: Soporte de desarrollo en C/ASM con emulador VICE integrado.
- [ ] **NES / Famicom (CC65 / Mesen)**: Plantillas y compilación para desarrollo de juegos de 8 bits en NES.

### 7. Ecosistema, Plugins e Inteligencia Artificial
- [ ] **SDK de Plugins Oficial y Documentado**: Facilidad para que la comunidad desarrolle extensiones empaquetadas (`.samaruc-plugin`) con acceso seguro al modelo de eventos del IDE.
- [ ] **Asistente IA Local / LLM Assistant (Ollama / Local API)**: Integración opcional con modelos locales para sugerir optimizaciones de código ensamblador, explicar rutinas retro complejas o generar esqueletos de juego.
