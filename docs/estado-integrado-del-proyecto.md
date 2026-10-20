# Estado integrado del proyecto — análisis, evolutivos y pendientes

Fecha: **2026-10-06** · Rama: `develop` · Último commit: `790bee55` (2026-10-01).

Documento único de estado del proyecto. Reemplaza a los 10 análisis/planes/evaluaciones/roadmap que existían en `docs/` y que fueron **fusionados aquí y eliminados** el 2026-10-06 (resumen de lo que contenía cada uno en §2). Contesta a una sola pregunta: **¿en qué punto estamos?**

---

## 1. Resumen ejecutivo

| Área | Estado al 2026-10-06 |
|---|---|
| Rama | `develop`, **31 commits sin pushear** a `origin/develop` |
| Working tree | **Sucio**: 11 modificados + 21 nuevos, sin commitear (3 bloques, §8) |
| Versión | **1.3.2** sincronizada en los 3 ficheros; **16 commits sin versionar** desde `81c5384c` |
| Tests | ✅ **799 tests, 0 fallos, 0 errores, 1 skip** (`mvn -o test`, BUILD SUCCESS, 2026-10-06 08:14) — el bloqueante del 05/10 quedó resuelto (§8.4) |
| Plataformas | **9 de 8 bits con canalización completa** y casi paridad total de herramientas |
| Pendiente real grande | **Release 1.3.3 pendiente** (el traductor multiformato se **descartó** el 07/10: por decisión del usuario, el traductor es la única herramienta monoplataforma del sistema) |
| ROADMAP | Única fuente: el `ROADMAP.md` de la raíz (actualizado); el duplicado de `docs/` era desfasado y se eliminó (§7) |

Lo que queda no es "media feature grande" sino: (a) decidir y cerrar los commits de los tres bloques del 05/10 y verificar el streaming de IA en la app real, (b) cerrar la release 1.3.3, (c) cuatro huecos concretos de alcance medio (§6), y (d) un cierre transversal de documentación (§3-P3.4).

---

## 2. Qué contenían los documentos fusionados y eliminados

El 2026-10-06 se fusionaron y eliminaron los siguientes ficheros de `docs/`. Su contenido sustantivo queda recogido en este documento (§3–§8):

| Documento eliminado | Contenido que aportaba | Estado cuando se fusionó |
|---|---|---|
| `analisis-sistemas-8-bits-soportados.md` | Las 9 plataformas detalladas (plantillas, build, emulador, detección, perfil), auditoría de compilación de los 4 builds rotos (30/09), análisis de paridad | Casi al día; matriz y §1.7 quedaron desbancados por P2.6–P3.1 |
| `plan-paridad-maquinas-8bits.md` | Plan ejecutable en pasos (P1, P2.1–P2.9, P3.1–P3.4) para igualar MSX/NES e incluir sintaxis/autocompletado + editor de pantalla ZX | P1–P3.1 marcados; **P3.2 y P3.3 se terminaron el 05/10**; solo quedaba P3.4 |
| `analisis-cobertura-plataformas-herramientas.md` | Matriz de las 9 herramientas × 7 plataformas y los 7 huecos prioritarios | Huecos 1–5 y 7 resueltos; el 6 (traductor multiformato) quedó **descartado** (decisión 07/10) |
| `analisis-mejoras-editor-profesional.md` | 18 mejoras del editor (breadcrumbs, autosave, paleta de comandos, outline, folding, snippets, diff git, refactors…) | Cerrado 18/18 |
| `analisis-usabilidad-editor-tiles.md` | 20 mejoras del editor de tiles (undo/redo, i18n, filmstrip, herramientas de forma, paleta editable…) | Cerrado 20/20 + editor de paleta |
| `analisis-animaciones-sprites.md` | Propuesta de animación por frames en el editor de sprites | Núcleo hecho; quedaba animaciones nombradas + offsets |
| `analisis-integracion-traductor.md` | Convertir `.smt` en documento de primera clase (pestaña) | Cerrado (implementado) |
| `ai-integration-analysis.md` | Diseño del asistente IA solo con opencode (fases 0–4) | Fases 0–2 ya implementadas (§8.3); doc muy desfasado |
| `evaluacion-trabajo-pendiente.md` | Evaluación del pendiente (bugs resueltos 1.1–1.3, release, ROADMAP desfasado) | Bugs cerrados; release sigue pendiente y creció (8 → 16 commits) |
| `docs/ROADMAP.md` | Duplicado del roadmap de la raíz | Desfasado (paleta de comandos, panel Git, GitHub repos ya hechos) |

---

## 3. Paridad de máquinas 8 bits

*Resumen del análisis de sistemas de 8 bits + del plan de paridad en pasos P1–P3 (fusionados).*

**9 plataformas con canalización completa** (crear → compilar → ejecutar → detectar → perfil): Game Boy, ZX Spectrum, Amstrad CPC, Atari XE/XL, Commodore 64, Master System, Game Gear, MSX y NES. La auditoría del 2026-09-30 corrigió 4 builds que fallaban por suposiciones de API y quedaron verificados compilando con los toolchains de `libs/devkits/`:

| Caso | Causa | Arreglo |
|---|---|---|
| SMS | plantilla usaba SMSLib (`SMS_displayOff`, `RGB15`), que no viene con z88dk | reescrita con `<sms.h>` (`clear_vram`, `load_palette`, `set_sprite`, `wait_vblank_noint`) |
| Game Gear | target `+gg` no existe en z88dk | `zcc +sms -subtype=gamegear`; `normalizeGameGearTarget` migra `+gg` antiguo |
| CPC | `cpc_clib.lib` sin `writebyte` → `printf` no enlaza | `z88dkSupportSources` compila `libsrc/target/cpc/fcntl/writebyte.c` |
| NES | cc65 empaquetado no declara `ppu_*`/`pal_*` | `nes_main.c` con acceso directo al PPU por `$2006`/`$2007` + `waitvsync()` |

Matriz de paridad (estado real, al día):

| Feature | GB | ZX | CPC | Atari | C64 | SMS | GG | MSX | NES |
|---|---|---|---|---|---|---|---|---|---|
| Plantilla proyecto + archivo | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Build + emulador bundled | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Detección auto + perfil | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| README/gitignore | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Editor tiles/sprites | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ (P2.6) | ✔ (P2.7) |
| Editor de audio | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ (P2.8) | ✔ (P2.9) |
| PNG→C | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Sintaxis específica | ✔ | ✔ | ✔ (P3.1) | ✔ | ✔ (P3.1) | ✔ (P3.1) | ✔ (P3.1) | ✔ (P3.1) | ✔ (P3.1) |
| Editor pantalla / extra | traductor ROMs | ✔ pantalla ZX (P3.3) | — | — | — | — | — | — | — |

Notas de integración (MSX/NES) que conviene recordar: en MSX el TMS9918 no tiene tiles 4bpp (el conversor usa las tres tablas reales de SCREEN 1: patrones 1bpp en `0x0000`, nombres en `0x1800`, color en `0x2000`); en NES el valor de píxel es un slot de subpaleta (no un índice del PPU) y el sprite usa una subpaleta entera; el reloj del MSX es el del ZX (110838 ÷ 1773400/16), no el de 1 MHz del CPC.

**Conclusión de paridad:** el plan en pasos está **cerrado**. **P3.4 cerrado el 2026-10-19**: se corrigió el párrafo desfasado de `AGENTS.md` (decía que "mesen y openmsx están empaquetados sin ninguna referencia", falso desde el 29/09; ahora refleja `EmulatorLauncher.resolveBundled*` + lanzamiento en `BuildController` + enlaces de Ayuda), se revisó la consistencia de los bundles (0 dobles backslashes, 0 claves duras ausentes) y se completó el inventario añadiendo 39 claves usadas vía `msg(key, fallback)` que faltaban en los 6 bundles (§9). La única plataforma "esqueleto" es el ASM Z80 genérico (por diseño).

Pendientes de este bloque:
1. ~~**P3.4** — cierre transversal (S).~~ ✅ HECHO (2026-10-19).
2. Regresiones pendientes del plan: test que pince "Atari detectado compila `-t atari`" y "proyecto GG cuyo artefacto sale `.sms`" (la resolución `resolveSmsArtifact` prefiere `.sms` sobre `.gg`).
3. Detalle menor: el artefacto snapshot `.cpc` no se carga (habría que pedir `-subtype=dsk`).

---

## 4. Cobertura de herramientas por plataforma

*Resumen del análisis de cobertura (fusionado). Resumen por herramienta: tiles ✔ 7 plataformas; tilemaps ✔ 7; PNG→C ✔ 7 (conversor Retro, 9 modos en `service/converter`); audio/tracker ✔ 6 chips (APU GB, AY, POKEY, SN76489, SID, APU NES); compilación ✔ 8 (faltaba SMS/GG → ya cubierto con z88dk); emulación ✔ 8 (Caprice32 tomó el relevo de CPcBox).*

Estado de los huecos que listaba:

| # | Hueco | Estado real |
|---|---|---|
| 1 | Preview de audio multiplataforma | ✅ HECHO (24/09) — POKEY, SN76489, SID, AY, APU NES |
| 2 | Editor de tilemaps multiformato + `.tilemap` | ✅ HECHO (24/09–26/09) |
| 3 | Conversores PNG→C multiformato | ✅ HECHO (26/09) — 9 modos en `service/converter` |
| 4 | Compilador SMS/GG | ✅ HECHO (29–30/09) — `zcc +sms` / `-subtype=gamegear`, verificado en la auditoría de §3 |
| 5 | Plantillas de proyecto | ✅ HECHO — hoy hay 13 variantes (`gb-c`, `zx-c`, `cpc-c`, `cc65`, `cc65-c64`, `sms-c`, `sms-asm`, `gg-c`, `msx-c`, `nes-c`, `asm`, `c`, `native-c*`) |
| 6 | ~~Traductor de ROMs multiformato~~ | ⛔ **DESCARTADO (07/10)** — decisión del usuario: el traductor es la única herramienta monoplataforma; `RomTextTranslatorController`, `TextEncoding` y `.smt` siguen siendo 100% Game Boy y así se queda |
| 7 | `ProjectPlatformDetectionService` sin CPC | ✅ HECHO (24/09) — hoy el enum tiene 9 plataformas |

El traductor de ROMs es la **única** herramienta que es exclusiva de Game Boy, y así **se decide mantenerla** (07/10); el resto ya cubren las 9 máquinas.

---

## 5. Calidad del editor y de las herramientas

### 5.1 Editor profesional ✅ cerrado
18/18 mejoras implementadas: breadcrumbs, posición de cursor, auto-cierre de paréntesis, autosave + recuperación, iconos de tipo de archivo, panel TODO/FIXME, atajos estándar, resaltado de ocurrencias, paleta de comandos (`util/CommandPalette`), outline (`StructureView.fxml`), folding, drag & drop de pestañas, rename F2, diagnóstico al guardar (`GccSyntaxCheckService`, solo GCC), snippets (``TextMate, `SnippetService`), diff de git (`GitService.diffVsHead` + `GitDiffViewer`) y refactors (extraer constante/función).

### 5.2 Usabilidad del editor de tiles ✅ cerrado
20/20 + editor de paleta: undo/redo (50 estados), borrador con arrastre, cuenta-gotas, flood-fill, línea/rectángulo (Bresenham), pincel simétrico, filmstrip con reordenación, volteo/rotación, checkerboard, preview 1:1, edición por teclado, i18n completo, importación PNG con cuantización por plataforma, preferencias por plataforma (`java.util.prefs`), redibujado por región.

### 5.3 Animaciones de sprites 🟡 núcleo hecho
Implementado: `List<int[][]>` de frames con duración por frame, `SpriteAnimationCodeGenerator.generate(platform, frames, durationsVbls, ...)` para las **9 plataformas**, emisión de `#define *_FRAME_COUNT`, `*_frames[]`, `*_durations[]`, `*_attributes[]`/`*_colors[]` según máquina y ejemplo de bucle (`anim_ticks`, `wait_vbl_done`/`waitvsync`), filmstrip y preview con 50/60 Hz reales.
**Pendiente:** panel de **animaciones nombradas** (secuencias de referencias a frames reutilizables: caminar/correr/atacar), desplazamiento X/Y por paso, marcadores de bucle y "onion skinning" (futuro en el propio diseño).

### 5.4 Traductor como documento ✅ cerrado
`TranslatorModel`, `TranslatorPersistenceService` (JSON con rutas relativas) y `TranslatorTabManager` implementados; `MainController` detecta `.smt` en `openFileFromExplorer`/`onSaveFile`. El hueco de cobertura §4-6 (traductor multiformato) quedó **descartado** (decisión 07/10): el traductor sigue siendo monoplataforma (Game Boy).

---

## 6. Lista maestra de pendientes (priorizada)

Orden por valor/esfuerzo, no por documento:

| # | Pendiente | Origen | Esfuerzo | Estado |
|---|---|---|---|---|
| 1 | ~~Verificar la suite~~ (baneado por 799/0/0/1) + decidir los commits de los 3 bloques del 05/10 y commitear | §8 | S | 🟡 Falta solo decidir commit |
| 2 | **Release 1.3.3**: bump en `pom.xml` + `VERSION.txt` + `build-release.ps1` + `CHANGELOG.md`; push de los 31 commits; tag | evaluacion | S | ⛔ |
| 3 | ~~Traductor de ROMs multiformato~~ | cobertura hueco 6 | L | ⛔ **DESCARTADO** (07/10) — decisión: traductor solo Game Boy |
| 4 | ~~**P3.4** cierre transversal~~ (`AGENTS.md`, informe, i18n) | plan de paridad | S | ✅ HECHO (2026-10-19) |
| 5 | **UI de ajustes de IA** (`AiSettings` solo por fichero de propiedades, no está en `ConfigView.fxml`) | §8.6 | M | 🟡 |
| 6 | Verificar en la app real el ciclo completo `opencode serve` + streaming del panel | §8.4 | S | 🟡 Solo hay cobertura de tests unitarios |
| 7 | Panel de animaciones nombradas + offsets por paso | animaciones | M | 🟡 |
| 8 | Tests de regresión §3 (perfil Atari, artefacto GG `.sms`) + carga de `.cpc` | plan de paridad | S | 🟡 |
| 9 | ~~Actualizar el enunciado de AGENTS.md (mesen/openmsx ya tienen referencias) y clave `config.compiler.option.gbdk` inventariada~~ | P3.4 | S | ✅ HECHO (2026-10-19) |

Pendientes de medio/largo plazo sin compromiso (del `ROADMAP.md` de la raíz): jerarquía de llamadas, quick fixes/bulb, depurador source-level, profiler de T-states, optimizador de tilesets, analizador de attribute clash ZX, export del tracker a drivers (Arkos/AY), SDK de plugins documentado, y la Fase 3/4 de la IA (selector de modelo, permisos, MCP, skills de dominio).

---

## 7. Roadmap — qué está hecho y qué no

La única fuente es el **`ROADMAP.md` de la raíz** (actualizado): paleta de comandos, panel Git, explorador de repos GitHub y gestión de ramas figuran ya como `[x]`. El antiguo `docs/ROADMAP.md` era una copia desfasada que los listaba como pendientes y se eliminó en la fusión para que deje de generar trabajo fantasma.

Pendiente de roadmap en el mundo real (verificado en código): **jerarquía de llamadas, quick fixes (bombilla), depurador source-level, profiler de T-states, optimizador de tilesets, analizador de attribute clash ZX, tracker multi-canal export a drivers, MSX/NES ya cubiertos (§3), SDK de plugins documentado, y el asistente IA** (parcialmente hecho, §8).

---

## 8. Contexto de la última sesión (2026-10-05) — punto exacto donde se quedó

*Fuente: `.agents/diario-2026-10-05.md` y `.agents/contexto-conversacion.txt` (sesión cerrada). Todo el trabajo de abajo está **SIN COMMITEAR** en `develop` sobre la base `790bee55`.*

### 8.1 Bloque 1 (08:17–08:30): Editor de Pantalla ZX — terminado
- `service/zxspectrum/`: `ZxScreenAttribute` (record ink/paper/bright/flash, `toByte`/`fromByte`), `ZxScreenEditorModel` (matriz 32×24 de atributos = 256×192 ÷ 8), `ZxScreenCodeGenerator` (array C en el orden real de `0x5800`), `ZxScreenPersistenceService` (Jackson, escritura atómica).
- `model/zxscreen/ZxScreenDocument` (JSON `format=samaruc.zxscreen`, ext `.zxscreen`).
- UI: `controller/tools/ZxScreenEditorController` + `menuItemZxScreenEditor` en `MainView.fxml` + handler `onOpenZxScreenEditor` + clave i18n `menu.tools.zxScreenEditor` en los 6 bundles.
- Tests: `ZxScreenCodeGeneratorTest`, `ZxScreenPersistenceServiceTest`, `ZxScreenEditorModelTest`.
- **Equivalente al P3.3 del plan de paridad** (§3).

### 8.2 Bloque 2 (08:30): Autocompletado por plataforma — refactor
- Nuevo `service/syntax/CompletionDictionaryService` (+ test): indexa las tres listas que estaban hardcodeadas en `EditOptionsController` por **compilador** (gbdk/z88dk/cc65) y **perfil** (spectrum/cpc/sms/gg/msx/nes).
- Las palabras se comprobaron contra las cabeceras de `libs/devkits`, no contra documentación ajena.
- `EditOptionsController` pierde ~70 líneas de `buildSuggestions` y recibe el nombre del fichero de la pestaña activa (`FXUtils.TAB_PROP_BASE_TITLE`) para no sugerir API de C dentro de un ensamblador.
- **Equivalente al P3.2 del plan de paridad** (§3).

### 8.3 Bloque 3 (09:45–17:47): Asistente IA con opencode — EN CURSO
Arquitectura (todo en `service/ai`, sin JavaFX en los servicios; la UI solo conoce `AiService`):
- `AiProvider` (interfaz mínima), `AiStreamEvent` (sealed: `TextDelta`/`ToolStart`/`Status`/`Done`/`Error`), `AiSettings` + `AiSettingsRepository` **sin credenciales** (opencode autentica por su cuenta), `AiPromptBuilder`, `AiService` (pool propio + `UiDispatcher` inyectable), `AiConversation` (fusiona `TextDelta` contiguos).
- `OpenCodeRuntime` (rutas concretas en Windows: orden `.exe, .cmd, .bat, ""` — `ProcessBuilder("opencode")` falla con `CreateProcess error=2`), `OpenCodeServerBridge` (`opencode serve`, puerto propio, espera a `/global/health`, cwd = proyecto), `OpenCodeProvider` (única clase que conoce el protocolo; traduce `message.part.updated` y `session.idle`).
- UI: `AiChatController` + `AiChatMessage` + `AiChatPane.fxml` + CSS `.ai-chat-*`, `Menu menuAi` y `Tab aiTab` en `MainView.fxml`, **+428 líneas** en `MainController`, 24 claves nuevas en los 6 bundles.
- Tests: 8 clases en `service/ai` (`AiConversationTest`, `AiPromptBuilderTest`, `AiServiceTest`, `AiSettingsTest`, `OpenCodeRuntimeTest`, `OpenCodeServerBridgeTest`, `OpenCodeProviderTest`, `OpenCodeProviderStreamTest`).
- **Equivale a las fases 0–2 del diseño de IA** (§2): quedan la 3 (selector de modelo, permisos, límites) y la 4 (MCP, skills de dominio).

### 8.4 Verificación — ✅ RESUELTO el 2026-10-06
- La suite **no se había ejecutado desde las 13:37** del 05/10 y `OpenCodeProvider` se reescribió a las **17:47** sin testear: era el bloqueante real de la sesión.
- Verificado el 2026-10-06: `mvn -o test` → **799 tests, 0 fallos, 0 errores, 1 skip** (`GitServiceTest`), BUILD SUCCESS. Los 3 bloques del 05/10 compilan y pasan.
- **Sigue pendiente** verificar en la app real el ciclo completo `opencode serve` + streaming del panel (la parte de `OpenCodeProvider` es la que más se movió al final y solo está cubierta por tests unitarios).

### 8.5 Decisiones abiertas del 05/10
1. Commits: ¿tres separados (autocompletado / editor ZX / asistente IA) o uno solo?
2. Nada de versión ni `CHANGELOG.md` se ha tocado.

### 8.6 Pulido opcional de la IA
- `AiSettings` no tiene UI de configuración (todo va por el fichero de propiedades); no está en `ConfigView.fxml`.

---

## 9. Notas metodológicas


- Los documentos de `docs/` están en `.gitignore` (línea 101), pese a lo que dice `AGENTS.md`: **no entran en los commits** salvo `git add -f`.
- Verificaciones hechas al elaborar y mantener este informe (2026-10-06): presencia de `gg-c`/`msx-c`/`nes-c` en `FileTemplateService`, patrones de sintaxis CPC/C64/SMS/MSX/NES en `SyntaxHighlighter`, `CompletionDictionaryService` con los 3 compiladores y 6 perfiles, `TranslatorTabManager` conectado a `MainController`, y `TextEncoding` todavía en `service/gameboy/`.
- Número de tests: **799 tests, 0 fallos, 0 errores, 1 skip** (`mvn -o test`, ejecutado el 2026-10-06 08:14); el último dato previo era 683 tras P3.1 (01/10). *Nota: `target/surefire-reports` conserva dos `.txt` antiguos de septiembre (`ReproTest`, `DebugTmpTest`) que inflan el recuento si no se filtran por fecha.*
- Regla de mantenimiento: cuando una tarea de la lista §6 se cierre, marcarla con su fecha here mismo; los detalles de sesión se conservan en `.agents/`.

## 10. Cierre P3.4 (2026-10-19)

Pasada transversal de paridad; los tres frentes del P3.4 quedaron cerrados:

- **`AGENTS.md`** (§ Estructura de `libs/`): se sustituyó el párrafo "mesen y openmsx están empaquetados sin ninguna referencia desde el código" por el estado real. Verificado en código: `EmulatorLauncher.resolveBundledMesenExecutable` / `resolveBundledOpenMsxExecutable` (`util/emulator/EmulatorLauncher.java:365,377`), lanzamiento NES/MSX en `controller/build/BuildController.java:1579,1539` y enlaces de Ayuda `menu.help.mesenDocs` / `menu.help.openMsxDocs` (`controller/help/HelpController.java:118,130`).
- **Consistencia i18n**: 0 apariciones de doble backslash (`\\uXXXX`) en los 6 bundles; los únicos `\` restantes son `\n` de salto de línea en `*.notOpen.content` / `external.change.content`, válidos y consistentes. `MessagesBundleParityTest` en verde (6/6).
- **Inventario de claves**: 0 claves usadas con `getString`/`containsKey` ausentes (`config.compiler.option.gbdk` ya estaba). Sí faltaban **39 claves** invocadas vía `msg(key, fallback)` — el editor mostraba el literal en español en todos los idiomas (recuperación de autosave, refactor, rename, paleta de comandos, `git.diff.*`, folding, `menu.goto.definition`, `menu.tools.basicDialect`). Añadidas a los 6 bundles (823 → 862 claves, paridad idéntica). Las claves dinámicas `tool.github.category.<cat>`, `basic.dialect.<d>`, `tool.pngToC.platform.<k>` y `tool.pngToRetro.mode.<m>` tienen sus valores concretos salvo las categorías de GitHub (se muestran tal cual). `palette.hint` se deja fuera a propósito: se usa con fallback `null` y darle valor pintaría una pista idéntica junto a todos los comandos.