# Plan: Integración de Z88DK incluido con Samaruc

**Fecha:** 2026-09-17  
**Estado:** Implementado (opción B aprobada; falta commit + verificación de instalador)

## 1. Contexto y estado actual

| Aspecto | GBDK (referencia, ya integrado) | Z88DK (hoy, externo) |
|---|---|---|
| Ubicación binarios | Implícita: `user.dir/libs/gbdk/bin` | Explícita: `spectrum_bin` / `cpc_bin` (config) |
| Método en código | `BuildController.resolveBundledGbdkBin()` | `BuildController.resolveZ88dkBinForProfile()` |
| Si no está configurado | No aplica (incluido) | Diálogo de error duro, sin fallback |
| En git | Sí (135 ficheros, ~12 MB) | No (`libs/z88dk/` untracked) |
| En release | `libs/` se copia entero (build-release.ps1) | Igual, pero el código nunca lo usa |
| Env vars en compilación | PATH (dir gbdk) | PATH siempre; `ZCCCFG`+`Z88DK_PATH` solo en builds Makefile |

`libs/z88dk/` es un **checkout upstream completo** (binarios precompilados Windows + fuentes): ~503 MB. El runtime mínimo necesario es menor.

### Verificación hecha (validación técnica previa)

- `libs/z88dk/bin/zcc.exe` arranca standalone y resuelve `lib/config` por sí mismo (x64, machine 0x8664).
- Requiere `bin/` en el `PATH` para invocar herramientas auxiliares (`z88dk-z80asm`, etc.).
- `+zx -clib=new` compila y genera `.tap` con `-create-app` (exit 0). ✅
- `+cpc -subtype=dsk` compila mínimo (exit 0). ✅
- **`cpc.cfg` NO define `-clib=new`** (solo `default`/`ansi`) → el default "new" de Samaruc para CPC era un bug latente; se reconduce a `default`.
- `printf` con la **clib clásica** falla con `undefined symbol: writebyte` (las definiciones viven en `libsrc/target/*/fcntl`; las clibs clásicas precompiladas de este checkout no las contienen). Afecta por igual a una instalación externa del mismo checkout; no se corrige aquí.
- La clib `new` requiere los macro `src/m4/z88dk.m4` **y** `libsrc/` → confirma la **opción B**.

## 2. Distribución integrada (trimming) — hecho

**Conservado: ~308 MB, 24.127 ficheros**

| Carpeta | Tamaño | Notas |
|---|---|---|
| `bin/` | 113 MB | zcc.exe + compiladores + z80asm + appmake + DLLs (x64) |
| `include/` | 6 MB | `include/_DEVELOPMENT` incluido (new clib) |
| `lib/` | 90 MB | `config/`, `clibs/`, `crt/`, `target/` |
| `libsrc/` | 93 MB | **Opción B:** imprescindible para la clib `new` (referencias `DESTDIR\libsrc\_DEVELOPMENT\...` en zx.cfg) |
| `support/` + `ext/` | ~5 MB | Ayudantes/libertar externas (pequeño, se conserva por seguridad) |
| `src/m4/` | ~4 KB | `z88dk.m4` exigido por el CRT de la clib `new` — sin él, `+zx -clib=new` falla |
| `LICENSE` | — | GPL-3.0 (obligatorio para redistribuir) |

**Eliminado (~195 MB):** `bin.x86/` (91 MB, 32-bit), `src/` (98 MB, solo se restaura `src/m4/`), `examples/`, `doc/`, `test/`, `testsuite/`, `snap/`, `.github/`, `.devcontainer/`, `win32/` y scripts raíz (Makefile, build.sh, set_environment.sh, vsbuild.cmd, z88dk.Dockerfile, z88dk_prompt.bat).

> El tamaño final depende del checkout upstream; el plan original estimaba +300 MB y el resultado es ~308 MB.

## 3. Cambios de código — implementados

### 3.1 `BuildController.java`

1. **Nuevo** `resolveBundledZ88dkBin()` — espejo de `resolveBundledGbdkBin()`: `user.dir/libs/z88dk/bin`, valida `zcc.exe`/`zcc`.
2. **Nuevos** helpers `hasZccExecutable(String)` (acepta ruta a `bin/` o a la raíz) y `resolveEffectiveZ88dkBin()`: config válida → normalizada; si no, copia integrada; si no, valor configurado (para el mensaje de error).
3. Fallback aplicado en `onCompilar()` (rama Z88DK), en `prependToPath` (compilación directa y Makefile) y en `runMakeTarget()` (ZCCCFG/Z88DK_PATH).
4. Diálogo de error duro solo si no hay ruta válida ni copia integrada; se menciona `libs/z88dk` en el texto.
5. **CPC clib:** default pasa a `Z88DK_CLIB_DEFAULT` y se reconduce `new`→`default` al compilar para CPC (`cpc.cfg` no lo soporta).

### 3.2 `ConfigController.java`

- `onSaveCompilador()`: la ruta Z88DK del perfil activo deja de ser obligatoria. Vacía = usar copia integrada; si se rellena, debe existir.

### 3.3 ConfigView.fxml + i18n + manual — implementado

- Aviso "Z88DK se incluye con Samaruc (`libs/z88dk/bin`)" (`lblZ88dkSpectrumBundled`/`lblZ88dkCpcBundled`) en las pestañas Spectrum y CPC (modelo: `label.compiler.gbdk.bundled`).
- Clave i18n `label.compiler.z88dk.bundled` añadida a los **6** bundles.
- `resources/manual.html`: nota de distribución integrada, ruta opcional y aclaración de `-clib` en CPC.

### 3.4 Build / release

- `pom.xml`: sin cambios (igual que GBDK, no se empaqueta en el JAR).
- `build-release.ps1`: ya copia `libs/` entero → se verifica; el instalador crecerá ~300 MB sin comprimir (a asumir/cuenta de la opción B).

## 4. Estado de las tareas

| # | Tarea | Estado |
|---|---|---|
| 1 | Auditoría del runtime mínimo + validar clib clásico vs `-clib=new`; fijar opción | ✅ Hecho — opción **B** aprobada por el usuario |
| 2 | Trim `libs/z88dk/` + smoke ZX (`-clib=new`, `.tap`) y CPC (mínimo) | ✅ Hecho |
| 3 | `BuildController`: resolvers + fallback + ZCCCFG/Z88DK_PATH directo | ✅ Hecho |
| 4 | `ConfigController`: relajar validación | ✅ Hecho |
| 5 | UI + i18n + manual | ✅ Hecho |
| 6 | Verificación: `mvn test` (400, 1 skipped) BUILD SUCCESS | ✅ Hecho |
| 7 | Commit `libs/z88dk` + `docs/CHANGELOG.md` + verificar instalador | ⏳ Pendiente |

## 5. Criterios de aceptación

1. ✅ Compilar ZX Spectrum sin ruta configurada usa `libs/z88dk/bin` y genera `.tap`.
2. ✅ Compilar sin ruta en perfil CPC no falla por PATH (la generación de `.dsk` sigue el comportamiento del z88dk instalado externamente).
3. ✅ Con `spectrum_bin`/`cpc_bin` rellenos se usa la ruta externa (override).
4. ✅ Sin ruta y sin copia integrada → mensaje de error claro.
5. ✅ `mvn test` en verde (400 tests, BUILD SUCCESS).
6. ⏳ El instalador (jpackage) incluye `libs/z88dk` — pendiente de lanzar `build-release.ps1`.

## 6. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Repositorio +~300 MB (clon/commits) | Medio | Recorte aplicado; indicar en README/AGENTS |
| Instalador +~300 MB (sin comprimir) | Bajo | Aceptado como coste de integrar la toolchain (opción B) |
| Clib clásica `writebyte` rota en este checkout | Alto en ZX classic | ZX usa `-clib=new` (funciona); CPC pestaña `default` (válido en cpc.cfg). Documentado |
| `-clib=new` requiere `src/m4/` y `libsrc/` | Medio | Se conservan ambos; smoke test lo verifica |
| GPL-3.0 al redistribuir binarios | Bajo | `LICENSE` junto a `libs/z88dk`; `third-party-licenses.html` ya enlaza `licenses/Z88DK-NOTICE.txt` |
| zcc necesita `bin/` en PATH | Bajo | Ya se prepende en `onCompilar`/`prependToPath` |

## 7. Decisiones tomadas

- **Tamaño:** opción **B** (incluir `libsrc/`, requerido por la clib `new`).
- **Prioridad en `onCompilar`:** ruta configurada primero, fallback a copia integrada.
- **CPC:** la falta de `-clib=new` en `cpc.cfg` se resuelve con default `default`.