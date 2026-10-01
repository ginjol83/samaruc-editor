# Changelog

## No publicado

### Añadido
- **Master System, Game Gear, MSX y NES como plataformas de primera clase**: plantilla de proyecto y de "nuevo archivo" (`gg-c`, `msx-c`, `nes-c`), pestaña propia de compilación Z88DK para cada una, perfil de compilador seleccionable, `cc65 -t nes` como tercer target de CC65, README y `.gitignore` específicos, y emuladores empaquetados (`openMSX`, `Mesen`) con entradas en el menú Ayuda (`CC65`, `openMSX`, `Mesen`).
- **Detección automática** de MSX y NES con pesos propios (`#include <msx.h>`, `+msx`, `-subtype=rom`, `samaruc.target`; `#include <nes.h>`, `-t nes`, `nes.cfg`, `ppu_`).
- **`ProjectProfileService`**: al abrir un proyecto ofrece aplicar el perfil de la plataforma detectada, y devuelve vacío para las plataformas sin toolchain integrada en lugar de proponer el perfil de otra máquina.
- **README y `.gitignore` específicos de CPC** (`*.dsk`, `*.edsk`, `*.cdt`, `*.cpr`, `*.voc`, `*.wav`, `*.sna`, `*.cpc`).
- **Conversor PNG a C multiformato para MSX y NES**: `MSX_TILES` (TMS9918 SCREEN 1: patrones de 1bpp por carácter de 8x8, tabla de nombres y tabla de color de un byte por cada 8 caracteres) y `NES_TILES` (PPU 2bpp con planos entrelazados, tabla de atributos por cuadrante de 16x16 y paleta RAM de 4 subpaletas), con sus paletas de hardware, generación de código `msx.h`/`msx/gfx.h` y `nes.h` y claves en los 6 bundles.
- **Test de paridad de bundles de i18n** (`MessagesBundleParityTest`): falla si una clave existe en un bundle y falta en otro.
- **MSX en el editor de tiles y sprites** (`PlatformMode.MSX` y `SpriteAnimationCodeGenerator.Platform.MSX`): paleta de los 16 colores fijos del TMS9918, patrón 1bpp con color por bloque 8x8 (marcando en rojo los bloques con tonos mezclados), manual de uso con las direcciones de la VRAM y los datos de sprites 8x8/16x16, y `generateMsx` para el exportado.
- **Sprites de MSX en TMS9918**: los tiles 8x8 de los frames se generan en 1bpp y el color por tile es un único índice de 4 bits (el byte de atributo, donde el 0 es transparente), no un par de nibbles. El ejemplo generado usa `set_mode(1)`, `set_sprite_8` y `put_sprite_8`, con el aviso de los 4 sprites por línea.
- **NES en el editor de tiles y sprites** (`PlatformMode.NES` y `SpriteAnimationCodeGenerator.Platform.NES`): paleta de la subpaleta 0 del PPU (`$0F`, `$01`, `$21`, `$31`), tiles 2bpp de 16 bytes (8 del plano 0 y 8 del plano 1) y `generateNes` para el exportado. El manual recuerda que la copia de CC65 empaquetada no trae `ppu_*` ni `pal_*` y que hay que escribir la VRAM por `$2006`/`$2007` y el OAM por `$2003`/`$2004`, con la subpaleta en los bits 0-2 del byte de atributos.

- **MSX en el editor de audio** (`SoundCodeGenerator.Platform.MSX` y entrada `MSX (AY-3-8910)`): reutiliza el generador AY y el motor de preview `AY_3_8912` de CPC/ZX. El MSX comparte reloj con el ZX (1,7734 MHz, la mitad de la CPU) y no con el CPC de 1 MHz, así que los tonos se calculan con 110838 en vez de 62500. El driver escribe directo a los puertos `$98` (registro) y `$99` (dato) y enmascara el registro 7 con `%00111111` fijando el bit 7, porque en el MSX esos dos bits controlan el bus del PSG y no el mezclador; la escritura va con las interrupciones desactivadas, igual que `set_psg_callee.asm` del propio Z88DK. Las etiquetas del ensamblador llevan el símbolo (`msx_write_data_<sym>`) para que dos efectos en el mismo `.c` no choquen.
- **NES en el editor de audio** (`SoundCodeGenerator.Platform.NES`, `SoundEngine.APU_RP2A03` y entrada `NES (APU RP2A03)`): el preview usa el motor propio de la APU (2× pulso con duty seleccionable, triangular y ruido con el LFSR de 15 bits del RP2A03). El generador emite SFX y melodías escribiendo la struct `APU` de `<nes.h>` en lugar de punteros crudos a `$4000`. Los timers salen del reloj NTSC de `1789773` Hz: pulso `f = reloj / (16·(t+1))` y triangular `f = reloj / (32·(t+1))`, con el barrido del pulso 1 desactivado para que el tono no se desvíe de la afinación pedida. El triangular se controla por `$4008` porque su DAC es fija y no tiene registro de volumen. Los tiempos se cuentan en frames de vblank (`waitvsync`, 60,098 Hz) en vez de milisegundos reales. DPCM queda fuera a propósito: reproduce muestras y no es síntesis. Verificado compilando y enlazando los cinco casos con `cl65 -t nes`.

### Cambiado
- **Funciones de plataforma resaltadas en el editor**: el resaltador de C reconoce ya las cinco plataformas que faltaban, por prefijo: `SMS_` (SMS/GG), `msx_`/`vdp_`/`set_vdp_` (MSX), `ppu_`/`pal_`/`set_vram_`/`oam_` (NES), `cpc_` (CPC) y `vic_`/`sid_` (C64). Cada grupo tiene su color en los dos temas, y el clásico se completa con el de Atari, que le faltaba. Como en ZX/GBDK/Atari, solo se resalta cuando hay paréntesis de llamada y nunca dentro de un identificador más largo (`my_ppu_wait_nmi_helper`).
- **La casilla de pares 8x16 también sirve para NES**: `SpriteAnimationCodeGenerator.Options.gameboy8x16Pairs` pasa a llamarse `tallSpritePairs` y se habilita en el editor para las dos plataformas, porque el PPU empareja igual los dos tiles de un sprite 8x16 (el índice apunta al par top/bottom desde un índice par).
- **Reestructuración de `libs/`**: la carpeta pasó a organizarse por tipo de herramienta en dos subcarpetas:
  - `libs/devkits/` — toolchains: `cc65`, `devkitSMS`, `gbdk`, `z88dk`.
  - `libs/emulators/` — emuladores: `Altirra`, `cap32`, `emulicious`, `jspeccy`, `mesen`, `openmsx`, `SDL2VICE`.
  Se actualizaron todas las referencias del código, la configuración, los bundles de i18n, el manual, las vistas FXML y la documentación. `build-release.ps1` no cambia: sigue copiando `libs/` completo de forma recursiva.

### Corregido
- **El segundo pulso de NES escribía sobre el primero**: el generador traducía el nombre del canal a un índice buscando solo en inglés (`square 2`, `pulse 2`), pero la UI lo llama `Pulso 2`, así que "Pulso 2" caía en el canal 1 y dos efectos distintos pisaban `$4000-$4003`. La búsqueda ahora acepta los nombres en español.
- **La melodía NES no compilaba con cc65**: el jugador emitted las declaraciones de cada nota dentro del bucle (`unsigned int f = ...`), y cc65 no admite declaraciones mezcladas con código (`Mixed declarations and code are not supported in cc65`). Todas las variables se declaran ahora al principio de la función.
- **El código de audio AY de CPC y ZX no compilaba**: dos fallos que solo se ven al pasar el código generado por el Z88DK de verdad. Uno es la espera: los efectos y las melodías llamaban a `delay()` de `<time.h>`, que Z88DK no define para los targets `+cpc`, `+zx` ni `+msx` (solo está `t_delay()`, en tstates), así que el enlazado fallaba con `undefined symbol: _delay`. Ahora la salida AY emite su propio `delay()` en tstates, troceado porque el argumento de `t_delay` es un `unsigned int` de 16 bits y `300 * 3579` desbordaría: 4000 tstates por ms en el Z80 de 4 MHz del CPC, 3500 en el de 3,5 MHz del ZX y 3580 en el de 3,579545 MHz del MSX. El otro es la notación de los inmediatos: `ld bc, &fffd` no la acepta sjasmplus (falla con `syntax error`), así que los puertos pasan a `0xFFFD`, `0xBFFD`, `0xF400` y `0xF600`. Verificado compilando y enlazando con `zcc +cpc`, `zcc +zx` y `zcc +msx`.
- **El formato de tiles de MSX del conversor no existía en el hardware**: `MSX_TILES` generaba 4bpp en dos planos por columna, copiado del Master System, pero el TMS9918 no tiene ningún modo de tiles a 4bpp (sus dos planos solo aparecen en SCREEN 3, donde cada carácter son 8 bytes de color por bloques de 4x4). Ahora exporta las tres tablas reales de SCREEN 1: los patrones de 1bpp en `0x0000` (8 bytes por carácter, máximo 256, deduplicados), el mapa de nombres en `0x1800` (un byte por celda) y la tabla de color en `0x2000` (un byte por cada 8 caracteres, con el nibble alto = primer plano y el bajo = fondo, y un nibble a 0 = transparente), verificado contra la documentación del TMS9918A. Como la tabla de color es por grupos de 8, los números de carácter se reordenan para que los del mismo color compartan grupo.
- **Paleta de MSX equivocada**: `RetroHardwarePalette.msx()` reutilizaba la tabla de 3 bits por canal del Master System. Se sustituyó por los 16 colores fijos del TMS9918 (datasheet del TI) y se eliminó el codificador de CRAM `msxCramByte`, que copiaba además el orden de canales `%00BBGGRR` del SMS y no el del VDP del MSX1.
- **Master System y Game Gear no compilaban**: las plantillas usaban la API de SMSLib (`SMS_displayOff`, `RGB15`), que no viene con Z88DK, y el perfil de Game Gear usaba el target `+gg`, que Z88DK no define. Ahora ambas usan la API de `<sms.h>` del propio Z88DK (`clear_vram`, `load_palette`, `set_sprite`, `wait_vblank_noint`) y el Game Gear se compila como `+sms -subtype=gamegear` (el target guardado en configuraciones antiguas se migra al leerlo).
- **CPC no enlazaba con `printf`**: la copia integrada de `cpc_clib.lib` no incluye el módulo `writebyte` que usa `stdio/fputc_callee.c`. El build de CPC compila ahora el fuente del propio toolchain (`libsrc/target/cpc/fcntl/writebyte.c`) junto al proyecto.
- **NES no compilaba**: la plantilla llamaba a `ppu_*`/`pal_*`, que no existen en la copia de CC65 empaquetada. Ahora escribe el PPU directamente por `$2006`/`$2007`, que es el método del propio port.
- **Detección de Game Gear**: `-subtype=gamegear` puntúa más que el `#include <sms.h>` de la propia plantilla y anula la pista `+sms` del mismo archivo.

### Eliminado
- `libs/cpcbox-main/`: emulador CPC en WebView, sustituido por el emulador nativo Caprice32 (`libs/emulators/cap32`).
- `libs/lib/` (DLLs de JInput): sin uso por el código.
- `src/main/resources/i18n/fr`: bundle francés duplicado que se había colado con nombre de archivo en vez de `MessagesBundle_fr.properties`.

## 1.3.2 - 2026-09-18

### Añadido
- **Emuladores empaquetados**: Altirra 4.21 (Atari 8-bit) y openMSX 21.0 (MSX) incluidos en `libs/`.
- **Detección de cambios externos en vivo**: un watcher avisa en la barra inferior cuando un archivo abierto se modifica fuera del editor (p. ej. por opencode) y permite recargarlo con un clic. Además, antes de compilar, ejecutar o guardar se confirma cómo proceder (recargar desde disco / conservar la versión del editor / usar la versión de disco / cancelar).

### Mejorado
- **Editor**: resaltado de la palabra bajo el cursor, emparejamiento visual de paréntesis/corchetes/llaves y indicador de línea:columna en la barra de estado.
- **Editor de audio Game Boy**: scroll y rejilla del tracker mejorados.
- **Configuración**: opciones para habilitar logs y para mostrar la Home al iniciar.
- **Sincronización de versión** resuelta: `pom.xml`, `VERSION.txt` y `build-release.ps1` apuntan a la misma versión.

## 1.3.1 - 2026-09-17

### Añadido
- **Z88DK integrado con Samaruc**: distribución de Z88DK (~308 MB) incluida en `libs/z88dk` que permite compilar proyectos de ZX Spectrum y Amstrad CPC sin toolchain externa. Si se configura una ruta externa (`spectrum_bin`/`cpc_bin`) tiene prioridad; si el campo queda vacío se usa la copia integrada.

### Mejorado
- **Ruta de Z88DK opcional** en la configuración: vacía = usar la copia integrada en `libs/z88dk/bin`.
- **`-clib` de CPC**: el valor `new` se reconduce a `default` (el target `+cpc` de Z88DK no define la clib `new`).


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