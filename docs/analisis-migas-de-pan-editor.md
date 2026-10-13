# Análisis: barra de migas de pan (breadcrumbs) del editor

Fecha: 2026-10-07
Estado: análisis, sin implementar.
Archivos clave: `service/SymbolBreadcrumbService.java`, `util/FXUtils.java` (L2161-2198), `service/StructureViewService.java`, `service/CodeFoldingService.java`, `css/app.css` L211-234.

## 1. Qué se observa (y por qué es correcto)

La barra muestra `archivo › símbolo1 › símbolo2 › …` donde los símbolos son **todos los que aparecen
antes de la línea del cursor**, y al pulsar cualquier chip el cursor salta a la línea de ese símbolo.
Es decir: hoy funciona como un **índice/atajo de "volver a líneas anteriores"**, no como un breadcrumb
de verdad. La percepción del usuario es exacta.

### Cómo se hace hoy

1. `SymbolBreadcrumbService.getBreadcrumbs(texto, fichero, líneaCursor)` (`SymbolBreadcrumbService.java:36`):
   - chip 1 = fichero (línea 1);
   - escanea **todo el documento** línea a línea con 3 regex (region / función C / etiqueta ASM);
   - se queda con **todos** los símbolos con `sym.line <= caretLine` (L64-68) y los devuelve en plano.
2. `FXUtils.updateBreadcrumbBar` (`FXUtils.java:2171`) repinta los chips y les pone un click que hace
   `moveTo(línea del símbolo)` (L2183-2188).
3. Se recalcula **solo al mover el cursor de línea** (L407-413), con un escaneo completo del texto cada vez.

## 2. Por qué no debería funcionar así

Un breadcrumb estándar (VS Code, IntelliJ, Qt Creator) responde a **"¿en qué ámbito estoy?"**,
no a **"¿qué se ha declarado antes?"**:

- Debe mostrar la **ruta de ámbitos que contienen al cursor**: `archivo › región › función › etiqueta interna`.
- Al pulsar un chip se salta al **inicio del ámbito que te contiene** (o despliega los hermanos para
  navegar entre funciones) — el click hacia atrás es legítimo, pero cada chip debe ser *significativo*.

### Defectos concretos del enfoque actual

| # | Defecto | Evidencia |
|---|---|---|
| 1 | **Acumula símbolos ya cerrados**: al bajar por el fichero la barra crece sin límite (`archivo › f1 › lbl › region › f2 › f3 › …`), incluyendo funciones a las que ya no perteneces. | filtro plano `sym.line <= caretLine`, `SymbolBreadcrumbService.java:64` |
| 2 | **Sin jerarquía**: regiones, funciones y etiquetas van en la misma lista plana aunque aniden. | ibidem |
| 3 | **Desbordamiento de la barra**: es un `HBox` sin scroll, sin truncado y sin clip; con ficheros grandes los chips se salen del ancho del editor. | `FXUtils.java:2164-2168`, `app.css:212` |
| 4 | **Falsos positivos**: `REGION_PATTERN` no está anclada → cualquier línea que contenga `region X` crea chip (p. ej. un comentario `// region buffer` → chip "buffer"); y la región tiene prioridad sobre función/etiqueta en la cadena if-else. El patrón de etiqueta ASM también casa `default:` de un `switch` en C o `foo:` dentro de comentarios `/* … */`. | `SymbolBreadcrumbService.java:33-34,48-61` |
| 5 | **Falsos negativos graves para este IDE**: `C_FUNC_PATTERN` solo admite `int\|void\|char\|long\|float\|double\|unsigned\|static\|inline\|extern\|_Bool\|bool\|struct` → **se come casi todas las funciones reales de GBDK/SDCC** (`uint8_t`, `UBYTE`, `UWORD`, `uint16_t`, firmas multilínea). Las etiquetas numéricas locales del ensamblador (`1:`) tampoco se detectan. | `SymbolBreadcrumbService.java:30-32` |
| 6 | **Rendimiento**: escaneo regex de todo el documento en *cada* cambio de línea del cursor, instanciando un servicio nuevo; el índice podría calcularse al cambiar el texto (que ya hay un debounce de 250 ms) y en el cursor solo resolver contención. | `FXUtils.java:407-413,2175` |
| 7 | **Barra obsoleta**: si editas sin cambiar de línea (pegar arriba, borrar una función), no se repinta hasta que el cursor cambie de línea. | `FXUtils.java:417` solo marca dirty |
| 8 | **Errores silenciados**: `catch (Exception ignored)` deja la barra vacía sin diagnóstico. | `FXUtils.java:2196` |
| 9 | **Triplicado de regex**: los mismos patrones (region/func/label) viven en `StructureViewService.java:33-41`, `SymbolBreadcrumbService.java:30-34` y `CodeFoldingService.java:29-30`; si se arregla el patrón de `uint8_t` hay que tocar tres sitios. | — |
| 10 | Los tests solo cubren 2 casos felices (un fichero C y un ASM); no hay test de "función ya cerrada", "varias funciones", "falsos positivos". | `SymbolBreadcrumbServiceTest.java` |

## 3. Propuesta de rediseño

### 3.1 Semántica: ruta de ámbitos, no historial

```
Hoy:   main.c › physics_region › move_player › update › player_t › main        (todo lo anterior)
Ideal: main.c › update › player_t.something                                  (solo lo que contiene al cursor)
```

Algoritmo: cada símbolo necesita **rango `[startLine, endLine]`**; con él, la barra es
`fichero + todos los símbolos cuyo rango contiene a caretLine`, ordenados por inicio
(anidamiento natural → la ruta ya sale jerárquica).

### 3.2 Obtener los rangos (sin parser nuevo)

- **Funciones C `{...}`**: contar llaves desde la firma hasta cerrar — `CodeFoldingService.java:37-52`
  ya hace exactamente ese cálculo; lo que falta es **vincular el bloque `{...}` con la firma de la
  línea de apertura** (hoy el fold se titula "{...}").
- **Regiones**: `#region`/`#endregion` con pila — ya implementado en `CodeFoldingService.java:54-68`.
- **Etiquetas ASM**: desde la etiqueta hasta la siguiente etiqueta con sangrado menor o igual, o EOF
  (aproximación barata y suficiente para ensamblador plano).

### 3.3 Unificar el escáner de símbolos

Crear un único servicio (p. ej. `SourceSymbolScanner`) que devuelva `record SourceSymbol(name, startLine, endLine, type, signature)`
y que consuman a la vez:

- **Breadcrumbs** (contención del cursor),
- **Structure View** (`StructureViewService` — de paso gana `endLine` y podría plegar/colapsar),
- **Code folding** (`CodeFoldingService` — deja de tener rangos anónimos y recibe los de las firmas).

Corrige de una vez los falsos positivos/negativos (#4, #5): anclar la región, ampliar los tipos
(`uint8_t`, `UBYTE`, `UWORD`, `uint16_t`, `size_t`, punteros a función), descartar comentarios,
etiquetas numéricas `^\d+:`.

### 3.4 Rendimiento y refresco

- Recalcular el índice **al cambiar el texto** (reutilizar el pipeline `multiPlainChanges().successionEnds(250 ms)`
  de `FXUtils.java:403-405`), cacheado por tab.
- En el listener de cursor solo: resolver contención (O(símbolos)) y repintar si la ruta cambió
  (guardar la firma de la última ruta para evitar repintados inútiles).
- Quitar el `catch (Exception ignored)` por log mínimo o dejar propagar.

### 3.5 UX de la barra

- Mantener click → salto al inicio del ámbito (equivalente a VS Code).
- Mejora opcional (fase posterior): click en un chip con hijos despliega un combo de símbolos hermanos
  para saltar de función a función, como VS Code.
- Aunque con contención la ruta será corta, añadir truncado con `…` + tooltip como red de seguridad (#3).

### 3.6 Tests a añadir

- Cursor dentro de función cerrada por otra → solo `archivo › la-que-contiene`.
- Varias funciones seguidas → nunca aparece la anterior.
- `uint8_t foo(void)` detectado; `default:` y `// region de memoria` **no** generan chips.
- Región con función dentro → `archivo › región › función`.
- Firmas multilínea / comentarios con llaves dentro no descuadran el rango (como mínimo: no rompen).

## 4. Esfuerzo estimado

| Fase | Contenido | Esfuerzo |
|---|---|---|
| 1 | Rangos de ámbito (firma↔llaves, regiones) + breadcrumb por contención en `SymbolBreadcrumbService` | bajo-medio |
| 2 | Reparto de recálculo texto/cursor y eliminación del catch silencioso en `FXUtils` | bajo |
| 3 | Unificar escáner con `StructureViewService`/`CodeFoldingService` y arreglar regex (`uint8_t`, región anclada, `default:`) | medio |
| 4 | UX: truncado, y opcional combo de hermanos | bajo (truncado) / medio (combo) |

La **fase 1 sola ya corrige la percepción del usuario** (deja de "enumerar líneas anteriores");
las fases 3-4 eliminan duplicación y falsos positivos.

## 5. Referencias internas

- `src/main/java/com/retroeditor/service/SymbolBreadcrumbService.java`
- `src/main/java/com/retroeditor/util/FXUtils.java` (construcción L328-330, listener L407-415, repintado L2164-2198)
- `src/main/java/com/retroeditor/service/StructureViewService.java`
- `src/main/java/com/retroeditor/service/CodeFoldingService.java`
- `src/test/java/com/retroeditor/service/SymbolBreadcrumbServiceTest.java`
- `src/main/resources/css/app.css:211` / `app-dark.css:191`

## 6. Estado de implementación

Fases 1-3 completadas, más una parte de la 4:

- **Fase 1**: `SymbolBreadcrumbService` reescrito con índices de rango `[inicio, fin]` y
  `pathAt(línea)` por contención. Etiquetas hasta la siguiente, etiquetas dentro de funciones
  descartadas, firmas multilínea, tipos de 8 bits.
- **Fase 2**: en `FXUtils` el índice se recalcula solo al cambiar el texto (junto al resaltado ya
  existente); el cursor solo resuelve la ruta y repinta si la clave cambió. Eliminado el
  `catch (Exception ignored)` y protegido el salto de chip ante líneas fuera de rango.
- **Fase 3**: nuevo `service/SourceSymbolScanner` como escáner único (comentarios/cadenas
  eliminados de base, precedencia región > macro > función > struct > etiqueta > variable);
  `SymbolBreadcrumbService`, `StructureViewService` y `CodeFoldingService` delegan en él.
  El plegado ahora titula los bloques con el símbolo de apertura (`foo()`, `struct Player`)
  y cuenta llaves sobre código sin comentarios (no plega llaves de comentarios).
- **Fase 4 completa**:
  - Tooltip con la ruta completa en cada chip.
  - Truncado con `…`: si la ruta no cabe, cada chip recibe un ancho proporcional a su
    pref (`OverrunStyle.ELLIPSIS` + escucha del ancho de la barra).
  - Combo de hermanos: `BreadcrumbIndex.siblingsAt(línea)` devuelve, por nivel de la ruta,
    los símbolos del mismo padre (el nivel del archivo son sus símbolos de primer nivel).
    Cada chip con 2+ hermanos muestra un chevron `▾` que abre un `ContextMenu` con los
    hermanos (el clic en el chip sigue saltando al ámbito).

Tests: `SymbolBreadcrumbServiceTest` (16 casos), `SourceSymbolScannerTest` (4 casos) y las suites
de `StructureViewService`/`CodeFoldingService` existentes, todas en verde (`mvn -q test`).
