# Plan de commits pendientes — `develop`

Fecha: **2026-10-15** · Rama: `develop` · HEAD: `676f3b34` ("Add BASIC dialect resolution")
Estado verificado: suite **113 clases / 1063 tests / 0 fallos / 0 errores / 1 skip** (`mvn -o -q test`, limpiando antes `target/surefire-reports`).

> `docs/` está en `.gitignore`: para que este documento entre en un commit hay que forzarlo con `git add -f docs/<archivo>.md`.

## 1. Situación

`676f3b34` sólo commiteó `service/basic/BasicDialect*` (+2 tests), 3 análisis de `docs/` y `templates/atari_basic_main.bas`. **Todo lo demás está sin commitear** por encima, mezclando bloques del 01/10 al 14/10.

El árbol está muy acoplado por ficheros "hub" tocados por **muchos** bloques a la vez, así que una historia limpia por feature exige `git add -p` (staging por hunks) en esos ficheros. Ficheros hub:

- `controller/MainController.java` (menús/handlers de todos los bloques)
- `controller/build/BuildController.java` (Atari + ZX + dialecto)
- `util/FXUtils.java`
- `views/MainView.fxml`, `views/ConfigView.fxml`
- los 6 `i18n/MessagesBundle*.properties`
- `controller/config/ConfigController.java` (ajustes IA **y** tokens de tema BASIC)
- `controller/config/ConfigDialogCoordinator.java`
- `model/ConfigModel.java`, `model/EditorTheme.java`, `model/BuiltInThemes.java`, `service/ThemeCssGenerator.java` (+test)
- `service/syntax/SyntaxHighlighter.java` (+test)
- `pom.xml`

## 2. Inventario por fecha (origen de cada fichero)

| Fecha | Bloque | Ficheros |
|---|---|---|
| 01-02/10 | Autocompletado (P3.2) | `service/syntax/CompletionDictionaryService.java` (+test) |
| 02/10 | Editor pantalla ZX (P3.3), núcleo | `service/zxspectrum/ZxScreenAttribute.java`, `ZxScreenEditorModel.java` (+test) |
| 05/10 | Editor pantalla ZX (UI/persistencia) | `model/zxscreen/ZxScreenDocument.java`, `controller/tools/ZxScreenEditorController.java`, `service/zxspectrum/ZxScreenCodeGenerator.java`, `ZxScreenPersistenceService.java` (+2 tests), `EditOptionsController.java` |
| 05-07/10 | Asistente IA | `controller/ai/**`, `service/ai/**`, `views/AiChatPane.fxml` (+8 tests) |
| 06/10 | PNG→C unificado + ajustes IA | `controller/tools/UnifiedPngToCController.java`, `service/converter/PngConversionTarget.java` (+test), `PngToRetroConverterController.java`, `ConfigView.fxml`, `ConfigDialogCoordinator.java`, `pom.xml` (pin JavaFX) |
| 06-07/10 | Menú Ver + editor UX | `model/ConfigModel.java` (view.*), `service/SourceSymbolScanner.java` (+test), `StructureViewService.java`, `CodeFoldingService.java`, `SymbolBreadcrumbService.java` (+test), `app.css`, `app-dark.css` |
| 08/10 | Atari BASIC (Etapa 1-2) | `service/atari/basic/{AtariBasicTokens,AtariBcd,AtariBasicTokenizer,AtariBasicDetokenizer,AtariBasicSyntaxException}.java` (+3 tests), `FileTemplateService.java` (+test), `ProjectPlatformDetectionService.java` (+test) |
| 09/10 | Atari BASIC (ATR/export/highlight) | `service/atari/atr/**`, `service/atari/basic/AtariBasicVariant.java`, `service/atari/AtariBasicExportService.java` (+test), `EditorTheme/BuiltInThemes/ThemeCssGenerator` (+test), `SyntaxHighlighter` (+test), `c-syntax*.css`, `FileTypeIcons.java`, `FileOptionsController.java` |
| 13-14/10 | Preprocesador + modern + ZX BASIC | `service/basic/preprocessor/**`, `service/basic/modern/**`, `service/zxspectrum/basic/**` (+tests), `manual-basic.html`, `HelpController.java`, `UserActionMonitor.java`, `BasicDialect.java`, `BuildController.java`, ficheros hub de UI/i18n |

## 3. Secuencia propuesta (9 commits)

Orden pensado para que cada commit compile: los servicios nuevos van antes que los hubs que los referencian. Aun así, **los hubs exigen `git add -p`**; se indica el grupo con el que va cada trozo.

### C1 — Editor: símbolos, breadcrumbs, folding y menú Ver (`view.*`)
- **Nuevos**: `service/SourceSymbolScanner.java` + `test/.../SourceSymbolScannerTest.java`
- **Modificados completos**: `SymbolBreadcrumbService.java` (+test), `CodeFoldingService.java`, `StructureViewService.java`, `model/ConfigModel.java`, `css/app.css`, `css/app-dark.css`
- **Hunks**: `MainController` (menú Ver + persistencia `view.*`), `MainView.fxml` (`menuVer`), i18n (`view.*` / etiquetas de menús)
- Mensaje: `Editor UX: source symbol scanner, breadcrumbs, folding and a configurable View menu`

### C2 — Autocompletado por plataforma
- **Nuevos**: `service/syntax/CompletionDictionaryService.java` + test
- **Hunks**: `EditOptionsController.java` (se elimina `buildSuggestions` hardcodeado), `FXUtils.java` (`TAB_PROP_BASE_TITLE`), `MainController` (pasa el nombre de fichero de la pestaña), i18n si aplica
- Mensaje: `Platform-aware code completion dictionary (gbdk/z88dk/cc65 profiles)`

### C3 — Editor de pantalla ZX
- **Nuevos**: `model/zxscreen/ZxScreenDocument.java`, `service/zxspectrum/ZxScreenAttribute.java`, `ZxScreenEditorModel.java`, `ZxScreenCodeGenerator.java`, `ZxScreenPersistenceService.java`, `controller/tools/ZxScreenEditorController.java` + 3 tests
- **Hunks**: `MainController` (`onOpenZxScreenEditor`, apertura de `.zxscreen`), `MainView.fxml` (`menuItemZxScreenEditor`), `FXUtils` (filtros), i18n (`menu.tools.zxScreenEditor`)
- Mensaje: `ZX Spectrum screen editor (attributes, 6912-byte codegen, .zxscreen persistence)`

### C4 — Conversor PNG→C unificado
- **Nuevos**: `controller/tools/UnifiedPngToCController.java`, `service/converter/PngConversionTarget.java` + test
- **Modificados completos**: `PngToRetroConverterController.java`
- **Hunks**: `MainController` (`onOpenPngToCConverter`, quita los 3 menús sueltos), `MainView.fxml` (3 items → `menuItemPngToC`), i18n (`menu.tools.pngToC`, `tool.pngToC.*`)
- Mensaje: `Unify PNG-to-C tools into one converter with platform auto-detection`

### C5 — Asistente IA (opencode) + ajustes en Configuración
- **Nuevos**: `controller/ai/**`, `service/ai/**`, `views/AiChatPane.fxml` (+8 tests), estilos `.ai-chat-*` en `app.css`/`app-dark.css`
- **Hunks**: `MainController` (menú IA, tab IA, handlers, `initAiFromSettings`), `MainView.fxml` (`menuAi` + `aiTab`), `ConfigView.fxml` (tab IA), `ConfigController` (bloque IA), `ConfigDialogCoordinator` (`onAiSettingsChanged`), i18n (`menu.ai.*`, `config.ai.*`)
- Mensaje: `OpenCode AI assistant: chat panel, streaming provider and settings UI`

### C6 — Infra: pin de JavaFX 17.0.19 y arreglo de arranque
- **Modificado completo**: `pom.xml` (dependencyManagement) + (si aplica) icono `fas-rotate`→`fas-redo` en el hub de UI
- Mensaje: `Pin the whole JavaFX platform to 17.0.19 and fix the startup icon`
- *Si se prefiere, puede plegarse en C1.*

### C7 — Resaltado/tema BASIC
- **Modificados completos**: `service/syntax/SyntaxHighlighter.java` (+test), `model/EditorTheme.java`, `model/BuiltInThemes.java`, `service/ThemeCssGenerator.java` (+test), `css/c-syntax.css`, `css/c-syntax-classic.css`
- **Modificados (completos si sólo hay tema)**: `controller/config/ConfigController.java` (sección `config.theme.section.basic` + italic)
- **Hunks**: `MainController`/`FXUtils`/`FileOptionsController`/`FileTypeIcons` (filtro `*.bas`, icono BASIC), i18n (`config.theme.token.basic_*`, `dialog.newFile.*`)
- Mensaje: `BASIC syntax highlighting, theme tokens and italic support`

### C8 — Atari BASIC: tokenizador, ATR y export
- **Nuevos**: `service/atari/**` (basic/, atr/) + tests
- **Modificados completos**: `FileTemplateService.java` (+test), `ProjectPlatformDetectionService.java` (+test)
- **Hunks**: `BuildController` (`runAtariBasic`/`buildAtariBasic*`, `atariVariantFor`, `reportAtari*`), `MainController` (`onExportAtr`, variante Atari), `MainView.fxml` (`menuItemExportAtr`), `ConfigView.fxml`/`FXUtils` (selector variante), i18n (`atari.*`, `dialog.project.new.atari.*`, `basic.dialect.notAtari`)
- Mensaje: `Atari BASIC toolchain: tokenizer, detokenizer, BCD, ATR image and export`

### C9 — ZX Spectrum BASIC + preprocesador + Samaruc BASIC (`@samaruc`)
- **Nuevos**: `service/zxspectrum/basic/**`, `service/basic/preprocessor/**`, `service/basic/samaruc/**` (+tests), `resources/manual-basic.html`
- **Hunks**: `BuildController` (`runZxBasic`/`buildZxTap` y dispatch por dialecto), `BasicDialect.java` (`hasNativeToolchain` ZX), `MainController` (`onChangeBasicDialect`, `applyBasicDialectToTab`, `persistBasicDialectChoice`, `onOpenBasicManual`), `MainView.fxml` (`menuItemBasicDialect`, `menuItemBasicManual`), `HelpController.java`, `UserActionMonitor.java`, i18n (`zx.*`, `basic.dialect.*`, `log.monitor.help.basic.manual`)
- Mensaje: `ZX Spectrum BASIC toolchain (.tap), BASIC include preprocessor and Samaruc BASIC transpiler`

### Transversal
- **Eliminado**: `docs/plan-paridad-maquinas-8bits.md` fue **rastreado** en `790bee55`; su borrado debe stagedarse (`git add docs/plan-paridad-maquinas-8bits.md`). Encaja en C7 o en un commit `docs`.
- **Docs nuevos**: si se quieren versionar los análisis (`docs/*.md` están gitignored), forzar con `git add -f`.

## 4. Alternativas

- **Coarse (4 commits)**: (1) Editor+autocompletado+menú Ver, (2) IA+ajustes, (3) PNG→C+editor ZX, (4) BASIC (Atari+ZX+preprocesador+modern). Menos `git add -p`, PR más legible de un tirón.
- **Uno solo**: `Integrate editor UX, AI assistant, unified PNG-to-C, ZX screen editor and Atari/ZX BASIC toolchains`. Seguro y rápido, pero pierde granularidad.

## 5. Verificación
- Tras cada commit (o al menos tras los que toquen `main`/`test`): `mvn -o -q test`. Referencia: 113 / 1063 / 0 / 0 / 1.
- No tocar `pom.xml <version>`, `VERSION.txt` ni `build-release.ps1` (eso es la release 1.3.3, fuera de este plan).
- No commitear sin petición explícita del usuario.
