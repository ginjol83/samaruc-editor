# Plan de paridad de máquinas 8 bits — pasos 2 y 3

Fecha: 2026-09-29 · Actualizado: 2026-10-01 · Rama de trabajo: `develop`.

Este documento desglosa en **pasos ejecutables** los puntos 2 y 3 del análisis de paridad (`docs/analisis-sistemas-8-bits-soportados.md`): llevar MSX y NES al nivel de arte del resto y cerrar sintaxis/autocompletado + editor de pantalla ZX. El **punto 1 ya está implementado y verificado** (631 tests, BUILD SUCCESS):

- Plantillas `Nuevo archivo` `gg-c`, `msx-c`, `nes-c` (`FileTemplateService`) + claves `template.*` (6 bundles).
- Menú Ayuda: `CC65`, `openMSX`, `Mesen` (`HelpController` + `MainView.fxml` + `MainController` + claves `menu.help.*` en 6 bundles + command palette).
- README/gitignore específicos de **CPC** (`FXUtils.getCpcReadmeContent`, `appendCpcGitignore`).

Estado de ejecución: **P2.1–P2.9 HECHAS**, ver secciones. Suite actual: 676 tests, 0 fallos, 1 skipped. Antes de P2.6 se corrigieron los builds rotos de SMS/GG/CPC/NES (auditoría en `docs/analisis-sistemas-8-bits-soportados.md` §1.11).

Cada paso indica **qué añadir**, **dónde**, **cómo verificarlo** y **esfuerzo**. Orden de ejecución recomendado: de menor a mayor dependencia. No hay ninguna dependencia entre puntos 2 y 3, se pueden intercalar.

---

## Punto 2 — Pipeline de arte para MSX y NES

### P2.1 Base del camino: plataformas y paletas en el conversor Retro

- **Qué**: añadir `MSX` y `NES` a `RetroImagePlatform` y las paletas de hardware en `RetroHardwarePalette`:
  - `msx()`: 16 colores TMS9918 (6 bits por color en CRAM: 2 bits por canal) — reutiliza la tabla de 16 del Master System para el preview, pero el byte de CRAM MSX1 es de 6 bits planos.
  - `nes()`: paleta PPU de 64 entradas + las 4 subpaletas de 4 colores (incl. transparencia).
- **Dónde**: `service/converter/RetroImagePlatform.java`, `service/converter/RetroHardwarePalette.java`.
- **Verificar**: `mvn -o -Dtest=RetroHardwarePaletteTest test`. Esfuerzo **S**.

> **✓ HECHO (2026-09-29)**. `RetroImagePlatform` incluye `MSX("MSX","MSX")` y `NES("Nintendo NES","NES")`; `RetroHardwarePalette` añade `msx()` (reutiliza la tabla TMS9918 de 16 del SMS), `msxCramByte()` (layout `%00BBGGRR`, mismo VDP) y `nes()` (paleta NTSC de 64 entradas del PPU 2C02, con los 0x0D-0x0F/0x1D-0x1F/0x2E-0x2F/0x3E-0x3F en negro). Tests en `RetroHardwarePaletteTest` (3 nuevos).

### P2.2 Modo MSX (TMS9918)

- **Qué**: `RetroImageMode.MSX_TILES` (256×192, 4bpp planar por tile 8×8 → **reutiliza `Layout.TILE_PLANAR` de SMS**, sin packer nuevo); CRAM 6 bits; clases de código `RetroImageMode.modeSupportsCram()` estilo SMS.
- **Dónde**: `RetroImageMode.java`, `RetroImagePackable`/`RetroImageMode` (validación de dimensiones), `RetroImagePacker` (solo rama TILE_PLANAR ya cubre), `RetroImageConversion`.
- **Verificar**: casos en `RetroImagePackerTest`, `RetroPackingRoundTripTest`, `PngToRetroImageConverterTest`. Esfuerzo **S**.

> **✓ HECHO (2026-09-29)**: `RetroImageMode.MSX_TILES` (256×192, 4bpp, `Layout.TILE_PLANAR` reutilizado, paleta `msx()`, CRAM 6 bits vía `msxCramByte`, color 0 transparente como SMS). `modeKey` del combo en `PngToRetroConverterController` + clave `tool.pngToRetro.mode.msx` en 6 bundles + tests (round-trip, CRAM, dimensiones). Suite: 635 tests.

### P2.3 Modo NES (PPU) — layout nuevo

- **Qué**: `RetroImageMode.NES_TILES` con **`Layout.NES_2BPP`**: por tile 8×8, 16 bytes (interleaving 2bpp: planos 1|0, 3|2 por byte), tiles 8×8 y 8×16; asignar subpaleta (2 bits de atributo) + paleta del tile; dimensiones 256×240.
- **Dónde**: nuevo `packNES2Bpp(...)` en `RetroImagePacker`, capa extra de "atributos/paleta" en `RetroImageConversion`, `RetroCGenerator` (arrays `[]NES`).
- **Verificar**: tests de packing propios (bytes planos correctos por par de planos) y round-trip. Esfuerzo **M** (es el único empaquetado realmente nuevo de este punto).

> **✓ HECHO (2026-09-29)**: `RetroImageMode.NES_TILES` (256×240, 2bpp, `Layout.NES_2BPP`, paleta PPU de 64). `RetroImagePacker.packNES` genera 3 capas: patrones 16 B/tile (plano 0 + plano 1), atributos 2 bits por cuadrante 8×8 (por celda de 16×16) y paleta RAM de 16 bytes (4 subpaletas × 4; `0xFF` = hueco). Asignación de subpaletas: reutiliza la exacta, si no la más compacta, si no la de menor error de color (fallback). Suite: 639 tests.

> **✓ HECHO (2026-09-29)**: el controller ya soportaba las claves de modo y multi-capa (`modeKey` "msx"/"nes" de P2.2/P2.3, `layerNames` en `describe`). `RetroCGenerator` añade las ramas: MSX → `#include <msx.h>` + comentario CRAM MSX1 (`%00BBGGRR` por el VDP); NES → `#include <nes.h>` + comentario que mapea la capa palette a `$3F00`-`$3F0F` (fondo universal + subpaletas 0-3). Suite: 641 tests.

### P2.4 Generación de código C y mensajes

- **Qué**: en `RetroCGenerator`, ramas `MSX` (`#include <msx.h>`, `msx_set_border`/`vdp`…) y `NES` (`#include <nes.h>`, `ppu_*`, arrays de paletas); en `PngToRetroConverterController`, casos del combo `switch` (`"msx"`, `"nes"`) + claves i18n nuevas en 6 bundles (padre `tool.retroConverter.*`, rowing del menú).
- **Dónde**: `RetroCGenerator.java`, `PngToRetroConverterController.java`, `resources/i18n/MessagesBundle*.properties`.
- **Verificar**: `RetroCGeneratorTest`, `RetroConverterMessagesTest`. Esfuerzo **S**.

### P2.5 Exposición en la UI del conversor Retro

- **Qué**: añadir MSX y NES al combo de `PngToRetroConverterController`; actualizar el texto del menú "Convertir PNG a C" en `MainView.fxml` y su clave i18n (`menu.tools.pngToRetro`), que hoy lista "CPC, C64, Master System, Game Gear, Atari".
- **Verificar**: test de controller del combo + `mvn -o -Dtest="com.retroeditor.service.converter.*Test" test`. Esfuerzo **S**.

> **HECHO (2026-09-30)**: los destinos MSX y NES ya estaban en el combo y en el controller (`modeKey` "msx"/"nes" de P2.2/P2.3). Lo que faltaba era la rotación del `MenuItem` desde el menú y el texto: `MainController` ata `menuItemPngToRetro` a `PngToRetroConverterController.showConverter(...)` en los dos bloques de traducción + fallback, y `menu.tools.pngToRetro` / `tool.pngToRetro.title` se actualizan en los **6 bundles** ("CPC, C64, Master System, Game Gear, MSX, NES, Atari"). Test de paridad de bundles: `MessagesBundleParityTest` (falla si una clave falta en algún bundle).
>
> Antes de seguir con P2.6 se decidió **auditar los builds de verdad**: fallaban SMS, GG, CPC y NES (ver `docs/analisis-sistemas-8-bits-soportados.md` §1.11). Corregidos y verificados los 9 con los toolchains de `libs/devkits`.

> Tras P2.1–P2.5, MSX y NES ya tienen **PNG→C** igual que el resto.

### P2.6 Editor de tiles MSX

- **Qué**: `PlatformMode.MSX` en `TileEditorController` (1bpp + tabla de color por bloque 8x8, paleta TMS9918, datos de sprites 1bpp del VDP con el reparto de columnas del formato 16×16); export `+msx` en `SpriteAnimationCodeGenerator.Platform.MSX`.
- **Dónde**: `TileEditorController.java` (enum/paletas/`currentPalette`/`blockColorMode`/aviso de choque/help), `SpriteAnimationCodeGenerator.java` (`generateMsx`), bundles de i18n (`tool.pngToRetro.mode.msx` en los 6).
- **Verificar**: build + test manual de generación (patrón `SpriteAnimationCodeGeneratorTest` si existe). Esfuerzo **M**.

> **HECHO (2026-09-30)**: `PlatformMode.MSX` + paleta de los 16 colores fijos del TMS9918 y `SpriteAnimationCodeGenerator.Platform.MSX` con `generateMsx` (patrones 1bpp `_frame_N` + colores de sprite `_colors_N`, más un `main` de ejemplo con `set_mode(1)`/`set_sprite_8`/`put_sprite_8`). El aviso de choque de color se extiende a MSX. 3 tests nuevos en `SpriteAnimationCodeGeneratorTest`.
>
> Al implementarlo se vio que **la premisa del plan era falsa**: el TMS9918 no tiene tiles a 4bpp, así que el "4bpp planar" de P2.1 era un formato inventado (copia del Master System). Con la documentación del TMS9918A (Sean Young, vía bifi.msxnet.org) se corrigió también el conversor: `MSX_TILES` pasa a las tres tablas reales de SCREEN 1 — patrones de 1bpp en `0x0000` (8 bytes por carácter, máximo 256, deduplicados), mapa de nombres en `0x1800` y tabla de color en `0x2000` con **un byte por cada 8 caracteres** (nibble alto = primer plano, bajo = fondo, 0 = transparente), reordenando los números de carácter para que los del mismo color compartan grupo—, se eliminó la CRAM de MSX (el registro 7 solo define texto y borde) y se sustituyó `RetroHardwarePalette.msx()` por la tabla real del datasheet del TI en vez de la del Master System. Cosas útiles que salieron de la misma fuente: los sprites son 1bpp (8 bytes a 8x8, 32 bytes a 16x16 **por columnas**: izquierda y luego derecha, dirección `(patron AND 252) * 8`) y hay 4 sprites por línea; el byte de atributo de un sprite es **un único índice de 4 bits** (0 = transparente), no un par de nibbles; en z88dk `set_mode(1)` es SCREEN 1 y `set_mode(0)` es SCREEN 0 de 40 columnas; `set_mode`/`vwrite`/`set_sprite_8`/`put_sprite_8` están en `<msx/gfx.h>`, no en `<msx.h>`.
>
> Verificado compilando de verdad: el `.c` del generador y el del conversor juntos con `zcc +msx` (`out.cim` OK, 2337 bytes) usando el toolchain de `libs/devkits/z88dk`. Suite: 654 tests, 0 fallos, 1 skipped.

### P2.7 Editor de tiles NES

- **Qué**: `PlatformMode.NES` (2bpp con subpaleta por tile o sprite, 8×8/8×16, export `ppu_*`/`nes_*`).
- **Dónde**: igual que P2.6. Esfuerzo **M**.

> **HECHO (2026-10-01)**: `PlatformMode.NES` (subpaleta 0 del PPU: `$0F`, `$01`, `$21`, `$31`, la de la plantilla) y `SpriteAnimationCodeGenerator.Platform.NES` con `generateNes`: tiles 2bpp de 16 bytes (8 del plano 0 y 8 del plano 1, pixel izquierdo en el bit alto), tablas `_frames` y `_durations`.
>
> La premisa del plan ("export `ppu_*`/`nes_*`") era falsa por el mismo motivo que en la plantilla NES: la copia de CC65 empaquetada no trae esas funciones, solo `waitvsync()` y la estructura `__ppu` de `<nes.h>`. El ejemplo generado escribe la VRAM por `$2006`/`$2007` y el OAM por `$2003`/`$2004`, y avisa de que `$3F10`/`$3F14`/`$3F18`/`$3F1C` son espejos del fondo universal y no los usan los sprites.
>
> Un detalle del modelo que conviene tener claro: el valor del píxel (0-3) es el **slot dentro de la subpaleta**, no un índice de la paleta del PPU, y un sprite usa **una** subpaleta entera (no una por tile como el fondo, que es por celda de 16×16 con 2 bits por cuadrante). El slot 0 es el transparente del sprite.
>
> `Options.gameboy8x16Pairs` se renombró a `tallSpritePairs` porque el checkbox de pares por columna sirve igual para el NES: el PPU empareja los dos tiles de un sprite 8×16 igual que la Game Boy (índice par, top y bottom contiguos).
>
> Verificado compilando de verdad: el `.c` generado (16×16, pares 8×16) más el ejemplo de uso con `cl65 -t nes` produce un `out.nes` de 40976 bytes (cabecera + 32K PRG + 8K CHR). Suite: 657 tests, 0 fallos, 1 skipped.

### P2.8 Editor de audio MSX

- **Qué**: entrada `MSX (AY-3-8910)` en `GameBoyAudioEditorController` — **reutiliza el motor `SoundEngine.AY_3_8912`** (mismo PSG que CPC/ZX) y el generador de código AY de `SoundCodeGenerator`.
- **Dónde**: `GameBoyAudioEditorController.java` (constantes `PLATFORM_MSX`, combo, canales), `SoundCodeGenerator.java`, `GameBoyAudioPlayer` (sin cambios de motor), i18n.
- **Decisiones al implementarlo**:
  - El MSX comparte reloj con el ZX (1,7734 MHz = 3,579545 / 2), **no** el de 1 MHz del CPC: `ayClockDiv` devuelve 110838 para MSX. Con 62500 los tonos salían un 43 % graves.
  - El driver va directo a los puertos `$98` (registro) y `$99` (dato). El registro 7 se enmascara con `%00111111` y se fija el bit 7 porque en el MSX esos dos bits son control del bus del PSG, no mezclador; la escritura va con `di`/`ei` porque el PSG comparte líneas con el VDP. Es el mismo criterio que `libsrc/target/msx/psg/set_psg_callee.asm`.
  - Las etiquetas del asm se prefijan con el símbolo (`msx_write_data_<sym>`) para que dos efectos en el mismo `.c` no colisionen.
  - **Bug preexistente corregido**: la salida AY usaba `delay()` de `<time.h>`, que z88dk no trae para CPC/ZX/MSX (solo `t_delay()` en tstates), y usaba notación `&FFFD`, que sjasmplus rechaza. Ahora se emite un `delay()` propio en tstates troceados a 16 bits (4000/ms CPC, 3500/ms ZX, 3580/ms MSX) y los inmediatos llevan `0x`.
- **Verificar**: generado con `zcc +msx` (SFX tono, SFX ruido y melodía) y también `+cpc`/`+zx` para no romper lo anterior; tests del generador AY. Esfuerzo **M**.

### P2.9 Editor de audio NES

- **Qué**: nuevo `SoundEngine.APU_RP2A03` en `GameBoyAudioPlayer` (2× pulse con duty, triangle y noise; DPCM se omite porque es reproducción de muestras, no síntesis) + `SoundCodeGenerator.Platform.NES` (registros `$4000–$4017` vía la struct `APU` de `<nes.h>` y `waitvsync` como base de tiempos) + entrada en la UI.
- **Dónde**: `GameBoyAudioEditorController.java`, `GameBoyAudioPlayer.java`, `SoundCodeGenerator.java`, i18n.
- **Verificar**: preview auditivo test con swing de frecuencias + build de código. Esfuerzo **M** (motor nuevo, parecido al del GB).
- **Decisiones**: reloj NTSC `1789773` Hz, pulso `f = reloj / (16·(t+1))` y triangular `f = reloj / (32·(t+1))`; el barrido del pulso 1 se deja desactivado para que el tono no se desvíe de la afinación pedida; el triangular se controla vía `$4008` porque su DAC es fija (no hay volumen); los tiempos usan frames de vblank (`60,098 Hz`) en lugar de milisegundos reales.

---

## Punto 3 — Sintaxis, autocompletado y editor de pantalla ZX

### P3.1 Funciones resaltadas por plataforma

- **Qué**: en `service/syntax/SyntaxHighlighter.java` se resaltan funciones de GB/ZX/Atari; añadir sets:
  - **SMS/GG**: prefijo `SMS_` (SMSlib).
  - **MSX**: `msx_*` + registros VDP (`vdp_*`, `set_vdp_*`).
  - **NES**: `ppu_*`, `pal_*`, `set_vram_*`, `oam_*` (cc65 nes).
  - **CPC**: `cpc_*` (z88dk).
  - **C64**: registros `vic_*`/`sid_*`.
- **Dónde**: `service/syntax/SyntaxHighlighter.java` (+ su test si existe).
- **Verificar**: `mvn -o -Dtest="com.retroeditor.service.syntax.*Test" test`. Esfuerzo **S**.

### P3.2 Autocompletado por plataforma

- **Qué**: hoy `EditOptionsController` autocompleta GBDK/conio/C. Añadir diccionarios por perfil detectado (`z88dk_profile`: sms/gg/msx/cpc; `cc65_target`: `nes`) y por contexto del archivo.
- **Dónde**: `EditOptionsController.java` (+ service de completado existente), i18n.
- **Verificar**: tests del servicio de autocompletado. Esfuerzo **M**.

### P3.3 Editor de pantalla ZX (add-on)

- **Qué**: herramienta nueva (tab) tipo `TilemapEditorController` para la **pantalla 32×24** con los atributos INK/PAPER/bright/flash por celda y **preview en vivo del attribute clash** (reutiliza paleta y lógica de `TileEditorController`); export a `zx_wr_p/n` o arrays.
- **Dónde**: nuevo `service/zxspectrum/ZxScreenEditor...` (modelo de atributos + layout `ZX_SCREEN_LAYOUT` ya existente en `ZxBitmapLayout`) + nuevo `controller/tools/ZxScreenEditorController` + tab en `MainController` + menú en `MainView.fxml` + i18n.
- **Verificar**: tests del modelo de atributos + creación de fichero de ejemplo. Esfuerzo **L** (es la pieza más grande; conviene reutilizar canvas/render de `TileEditorController`).

### P3.4 Cierre transversal (opcional pero recomendado)

- **Qué**: actualizar `AGENTS.md` (la nota "mesen y openmsx sin referencia desde el código" ya es falsa), revisar que el **resumen rápido** del informe esté al día, y un pase de i18n para que ninguna clave nueva caiga en un solo bundle.
- **Verificar**: `mvn -o test` completo. Esfuerzo **S**.

---

## Estado

- [x] **Punto 1** — plantillas GG/MSX/NES, menú Ayuda CC65/openMSX/Mesen, README/gitignore CPC (2026-09-29).
- [x] **P2.1** paletas MSX/NES (2026-09-29)
- [x] **P2.2** modo MSX (reusa TILE_PLANAR) (2026-09-29)
- [x] **P2.3** modo NES (`NES_2BPP`) (2026-09-29)
- [x] **P2.4** generación C + i18n
- [x] **P2.5** UI conversor Retro (2026-09-30)
- [x] **Auditoría de builds** — SMS/GG con la API de `<sms.h>`, GG con `-subtype=gamegear` en vez del inexistente `+gg`, CPC compilando el `writebyte.c` que falta en `cpc_clib.lib`, NES con acceso directo al PPU (2026-09-30).
- [x] **P2.6** editor de tiles MSX (2026-09-30) — `PlatformMode.MSX` + `generateMsx`; de paso se corrigió el formato de MSX del conversor (las tres tablas de SCREEN 1, no 4bpp) y la paleta (la real del TMS9918).
- [x] **P2.7** editor de tiles NES (2026-10-01) — `PlatformMode.NES` + `generateNes` con los dos planos del PPU; de paso la casilla de pares 8×16 dejó de ser exclusiva del Game Boy.
- [x] **P2.8** audio MSX (2026-10-01) — `SoundCodeGenerator.Platform.MSX` reutilizando el código AY: reloj del ZX (110838), puertos `$98`/`$99` y enmascarado del registro 7. De paso el `delay()` AY roto en las tres plataformas (no existía en z88dk y usaba `&FFFD`).
- [x] **P2.9** audio NES (2026-10-01) - `SoundCodeGenerator.Platform.NES` y `SoundEngine.APU_RP2A03` con el LFSR de 15 bits de la APU. El generador escribe la struct `APU` de `<nes.h>` en vez de punteros crudos, y emite SFX y melodía con `waitvsync` como base de tiempos. Compilado y enlazado con `cl65 -t nes`. Dos bugs encontrados al compilar de verdad: las declaraciones de la melodía iban mezcladas con código (cc65 no lo permite) y los nombres de canal en español ("Pulso 2") no casaban con la búsqueda en inglés, así que el segundo pulso escribía sobre el primero.
- [ ] **P3.1** funciones de sintaxis por plataforma
- [ ] **P3.2** autocompletado por plataforma
- [ ] **P3.3** editor de pantalla ZX
- [ ] **P3.4** cierre transversal (AGENTS.md, informe, i18n)

Verificación rápida tras cada paso: `mvn -o -q test` (657 tests hoy; la suite debe crecer y seguir en verde, sin fallos). Cuando se toque una plantilla o un toolchain, comprobar además el artefacto real compilando la plantilla con el binario de `libs/devkits` (no basta con que el código compile: cuatro builds estaban rotos y nadie lo había ejecutado). Esto vale también para el código que genera el editor de sprites: el `.c` exportado, junto con el ejemplo de su comentario de uso, se puede compilar con el toolchain real.