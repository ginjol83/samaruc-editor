## Plan de implementación: Soporte de Atari BASIC (XL/XE) en Samaruc

**Modo**: Solo lectura (Plan). No se proponen modificaciones ahora, solo etapas verificables.

### Resumen de requisitos (según respuestas)
- Soportar **Atari BASIC estándar y BASIC XL/XE** si es posible (distinguible por modo/proyecto).
- Generar un **`.ATR` ejecutable** (no abrir vista tokenizada, pasos intermedios no visibles).
- Flujo: código BASIC (.bas) → tokenizar → escribir en ATR → lanzar Altirra (siempre Altirra).
- Plantilla inicial: "Hola Mundo".
- Ejecutar siempre con Altirra.
- Aplicar validaciones/limitaciones (formato estándar, líneas válidas).

## 1. Referencia del PDF (análisis para tokenización)

El PDF `docs/Atari_BASIC_Source_Book_2006.pdf` contiene la referencia de Atari BASIC (Source Book). Para tokenizar correctamente:

- **Atari BASIC** (OS ROM): tabla de tokens propia (keywords, functions, operators). 
- **BASIC XL / BASIC XE**: añaden tokens/keywords extendidos. Dado que se pide soportar ambas variantes si es posible, conviene diseñar el tokenizer con un **modo de variante** (`ATARI_BASIC`, `ATARI_BASIC_XL`, `ATARI_BASIC_XE`).
- La especificación de tokenizado Atari: cada línea BASIC se convierte a formato tokenizado (número de línea, puntero siguiente, tokens + terminador `$00`). Esto es necesario para generar un `.BAS` válido dentro del ATR (que el BASIC de Atari pueda `LOAD/RUN` directamente).

**Conclusión**: Implementar `AtariBasicTokenizer` con soporte por variante (flag). Empezar con estándar y añadir XL/XE reutilizando tablas.

## 2. Arquitectura a seguir (alineada con proyecto)

AGENTS.md indica: modelos en `model`, controladores en `controller`, lógica de negocio en `service`, utilitarios en `util`, vistas/FXML/CSS en `resources`.

Se crearán nuevos paquetes:

- `com.retroeditor.service.atari.basic` → `AtariBasicTokenizer`, `AtariBasicDetokenizer`, `AtariBasicToken`, `AtariBasicTokenType`, `AtariBasicVariant`
- `com.retroeditor.service.atari.atr` → `AtrImageWriter`, `AtrFileEntry` (escritor mínimo ATR)
- `com.retroeditor.service.atari` → `AtariBasicExportService` (orquesta tokenizar + ATR + nombres)
- `com.retroeditor.model.project` → ajustes de modo Atari BASIC (si necesario)
- `resources/templates/atari/basic/` → plantilla Hola Mundo
- Tests: `src/test/java/com/retroeditor/service/atari/basic/*`, `service/atari/atr/*`, `service/atari/*`

## 3. Etapas de implementación

### Etapa 1: Modelo, detección y creación de proyecto (fundamentos)
**Objetivo**: Reconocer proyecto Atari BASIC (.bas) y permitir crearlo.

- **Extensiones**: Añadir `.bas`, `.BAS` a asociación de ficheros (editor). Revisar registro de tipos de fichero en proyecto.
- **Modos/proyecto**: Definir modo para Atari BASIC. Opciones: añadir variantes `ATARI_BASIC`, `ATARI_BASIC_XL`, `ATARI_BASIC_XE` (enum). Vincular a `Platform.ATARI`.
- **Detección de plataforma**: `ProjectPlatformDetectionService` ya puntúa Atari (incluye `.h`, `atari`, `-t atari`). Añadir heurística para ficheros `.bas`: si proyecto contiene `.bas` → reforzar puntuación ATARI (sin romper CPC/otros). No invasivo.
- **Plantillas**: Crear `resources/templates/atari/basic/hello.bas`
  ```basic
  10 PRINT "HOLA MUNDO"
  20 END
  ```
  Seguir estructura de plantillas existente.
- **UI/i18n**: Añadir claves (6 bundles: base/es/en/de/fr/it): `project.type.atariBasic`, `project.type.atariBasicXl`, `project.type.atariBasicXe`, `project.new.atariBasic`, `menu.export.atr`, `action.run.atrGenerated`, etc. No añadir comentarios.
- **Criterio**: Proyecto Atari BASIC creado/abierto con `.bas`. Compila.

### Etapa 2: Tokenizador Atari BASIC (núcleo crítico)
**Objetivo**: Texto → tokens fiel al formato Atari (estándar + XL/XE).

**Clases a crear**:
- `AtariBasicTokenType` (enum): KEYWORD, FUNCTION, OPERATOR, NUMBER, STRING, VARIABLE, LABEL/REM, EOL, EOF, LITERAL?
- `AtariBasicToken` (record): tipo, byteValue (token), text, line, column.
- `AtariBasicVariant` (enum): `STANDARD`, `XL`, `XE`.
- `AtariBasicTokenizer`: parser línea por línea (BASIC Atari es estructurado por números de línea). Reglas:
  - Reconocimiento **longest match** de keywords/functions (respetar tabla del PDF).
  - Números: enteros/reales (notación Atari). Cadenas entre comillas `"` (no anidadas).
  - `REM`/comentarios: resto de línea como comentario (no tokeniza interior).
  - Variables: A–Z, A0–Z9 (convención Atari). Arrays si aparecen.
  - Operadores y símbolos.
  - Mantener fidelidad mayúsculas/minúsculas según tabla (normalización controlada).
- `AtariBasicDetokenizer`: inverso (útil para validación roundtrip). No expuesto en UI.

**Tablas**: Definir mapas estáticos por variante (evitar I/O). Separar estándar vs XL/XE para cumplir “ambas si posible”.

**Validaciones (Etapa 6)**: números de línea válidos, longitud de línea, tokens soportados. Limitar a formato estándar.

**Tests**: casos básicos: `10 PRINT "HOLA MUNDO"`, `GOTO`, `IF THEN`, `DATA`, `REM`, números, variables. Roundtrip texto→tokens→texto donde sea estable.

**Criterio**: Tokenizer probado con ejemplos del PDF. Compila + tests unitarios verdes.

### Etapa 3: Sintaxis (resaltado) — pulido
**Objetivo**: Resaltado para `.bas` Atari.

- `AtariBasicSyntaxService` (o extensión de `SyntaxService`): categorías: keywords, functions, strings, numbers, comments (REM), operators, variables.
- Registrar modo por extensión `.bas` + plataforma ATARI/BASIC.
- Estilos coherentes con temas claro/oscuro (sin tocar existentes).

**Criterio**: Resaltado correcto en editor. Sin regresiones.

### Etapa 4: Escritura de imagen ATR (formato disco)
**Objetivo**: Generar `.ATR` válido para Atari 8-bit.

**Especificación ATR (mínima, suficiente MVP)**: formato estándar de imagen de disco simple (90KB SD, 130KB DD común). Implementación **propia ligera** en `util/atr` o `service/atari/atr` (sin nuevas dependencias). 

**Clases**:
- `AtrHeader` / escritura de cabecera ATR
- `AtrSector` / mapeo sectores (simple)
- `AtrImageWriter`: crea imagen vacía (formato estándar), escribe fichero(s) en estructura sencilla (DOS 2.0S amigable). **Ojo**: un ATR sin imagen de arranque no es bootable y, sin un DOS cargado, BASIC no puede `LOAD`/`RUN` desde disco (no hay gestor de ficheros `D:`). Por eso el ATR sirve para **distribución**, no para el flujo de ejecución directa.

**Nombre de fichero en ATR**: por requisito 3 se usa **`AUTORUN.BAS`** (o nombre del proyecto truncado a 8.3).

**Criterio**: Genera `.ATR` válido con el programa tokenizado bajo `AUTORUN.BAS`.

### Etapa 5: Export y ejecución (flujo invisible + Altirra siempre)
**Objetivo**: Flujo completo BASIC → ejecutar, sin pasos visibles.

**Servicio**:
- `AtariBasicExportService` (service): 
  - `Path exportToBasic(Path sourceBas, Path outputBasic, AtariBasicVariant variant)`: lee `.bas` texto, tokeniza (con variante elegida: estándar/XL/XE) y escribe la **imagen BASIC** tokenizada (`<base>.bas`). **Este** formato es el que Altirra carga y ejecuta directamente (activa BASIC por sí solo).
  - `Path exportToAtr(Path sourceBas, Path outputAtr, String entryName, AtariBasicVariant variant)`: empaqueta el tokenizado en un ATR con `AUTORUN.BAS` (para distribución / `Exportar ATR...`).
  - Validaciones (líneas, formato). Limitar según requisito 6.

**Integración UI/flujo Ejecución**:
- Cuando proyecto es **Atari BASIC** (cualquier variante), acción `Ejecutar` (`Run`): 
  1. Guardar fichero(s) actuales (si sucio).
  2. Generar la imagen BASIC tokenizada (`<base>.bas`) en la carpeta de salida. Pasos intermedios NO visibles.
  3. Lanzar **Altirra siempre** con ese `.bas` vía `EmulatorLauncher.launchAltirra`. Altirra detecta la imagen BASIC, activa BASIC y **carga y ejecuta** el programa.
- `EmulatorLauncher`: ya existe `launchAltirra(File altirraExe, File romFile, ...)`. `.bas` es “romFile/programa” válido. No cambiar API existente.
- Nota de diagnóstico (2026-10): pasar un ATR no bootable a Altirra hacía que arrancara **sin BASIC** → pantalla en blanco y aviso de disco no bootable. Se comprobó empíricamente que un `.bas` tokenizado se ejecuta (pantalla `HOLA MUNDO` + `Ready`).
- Detección ruta Altirra: usar `libs/emulators/Altirra/` (altirra.exe/altirra64.exe). Seguir patrón otros emuladores (resolución desde libs). Si no existe, mostrar error con mensaje existente (sin romper flujo).

**Comportamiento**: “ejecutable en máquina final” → imagen BASIC generada y ejecutada en Altirra. Interfaz no muestra tokenizado.

**Criterio**: Ejecutar proyecto `.bas` abre Altirra y el programa se carga/ejecuta directamente.

### Etapa 6: Validaciones, límites y pruebas
**Objetivo**: Cumplir requisito 6 (limitar).

- Límites Atari BASIC: números de línea 0–32767, longitud líneas razonable, nombres variables válidos, tokens soportados.
- Errores claros (mensaje en UI/console) sin exponer detalles internos.
- Tests integración: export → ATR existe → contiene fichero tokenizado (smoke). Tests unitarios tokenizer/detokenizer + ATR writer.
- Verificación con ejemplos del PDF. Casos XL/XE cuando aplique.

## 4. Orden de ejecución recomendado

Según respuestas y criticidad:

1. **Etapa 2 (Tokenizer)** – bloque crítico (PDF). Tests primero.
2. **Etapa 1 (Modelo/UI/i18n)** – habilita proyecto.
3. **Etapa 4 (ATR Writer)** – necesario para export.
4. **Etapa 5 (Export+Ejecutar)** – flujo completo invisible.
5. **Etapa 3 (Sintaxis)** – pulido.
6. **Etapa 6 (Validaciones+tests)** – endurecer.

## 5. Archivos a tocar/crear (resumen)

**Crear**: servicios Atari (basic, atr), export, enums/records, plantillas, tests.

**Modificar (mínimo necesario)**:
- `ProjectPlatformDetectionService` (refuerzo heurística `.bas` → ATARI)
- `EmulatorLauncher` (sin cambios funcionales necesarios; usar método existente)
- Registro tipos/creación proyecto + i18n 6 bundles
- Integración acción Ejecutar para modo ATARI_BASIC* (en controlador adecuado, siguiendo patrón MVC)

Todo sin romper abstracciones existentes. No asumir `Compiler/Emulator` genéricos (seguir patrón por plataforma).

## 6. Notas de diseño

- **Dos variantes**: implementar `AtariBasicVariant` (STANDARD/XL/XE). Selector en proyecto (nuevo proyecto) o inferencia no obligatoria. Recomendado permitir elegir en creación (clarifica qué tabla usar).
- **Pasos invisibles**: ATR se genera en ruta temporal o `target/atari-export/` (no ensucia proyecto). Nunca mostrar vista intermedia.
- **Siempre Altirra**: hardcodeado para ejecución Atari BASIC (requisito 5). Respeta `libs/emulators/Altirra/` existente.
- **Sin dependencias**: tokenizer + ATR propios. Alineado con filosofía proyecto.
- **Limitado (req 6)**: validar antes de tokenizar/exportar. Fallos con mensajes user-friendly.

## 7. Criterio de aceptación global

1. Crear proyecto “Atari BASIC” (estándar/XL/XE opcional) con plantilla Hola Mundo.
2. Editar `.bas` → Ejecutar genera `.ATR` (invisible) y abre **Altirra** cargando el programa.
3. En Altirra: `LIST` muestra código, `RUN` imprime “HOLA MUNDO” (o equivalente). Ejecutable en máquina final.
4. Compila (`mvn compile`), tests verdes (`mvn test`).
5. Sin regresiones existentes.
6. i18n completo en 6 bundles.

**Plan listo para validación.** No se realizan cambios en este momento (modo Plan).
