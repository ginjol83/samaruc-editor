# Guía de uso: integración git y GitHub

> Cobertura: integración Git local, remoto y GitHub (releases, issues, navegador de repos).
> Feature completa a partir de la rama `develop` (Fases 1-4 del plan de integración).

---

## 1. Requisitos

- **Java 17+** y **Maven** para compilar.
- **`git` CLI** en el `PATH` para las operaciones de control de versiones (init, commit, branch, stash, pull, push, diff). Si `git` no está disponible, el IDE lo detecta y oculta esas operaciones; la descarga ZIP sigue funcionando como respaldo.
- **Token de GitHub** (opcional) para: subir a repos propios, clonar repos privados, publicar releases, gestionar issues y marcar con estrellas.

### Resolución de credenciales (cascade)

1. CLI `gh` configurada (más cómoda: `gh auth login`).
2. Variable de entorno `SAMARUC_GITHUB_TOKEN`.
3. Token guardado en Ajustes del IDE (diálogo *GitHub*).

Los pushes y las llamadas autenticadas usan el token resuelto; nunca se inventa identidad de git (si no hay `user.name`/`user.email`, no se crean commits automáticos).

---

## 2. Git local (panel Git)

- **Abrir**: menú `Git` → *Abrir panel* (o botón de la barra). Se acopla a la pestaña que esté activa.
- El panel muestra: rama actual, estado/WIP, historial (`git log --oneline -n 30`).
- **Commit**: los cambios listados; `git add` + `git commit -m` en un clic. Se requieren `user.name` y `user.email` locales (el panel ofrece configurarlos con "Usuario local" si faltan).
- **Diff inline**: al seleccionar un archivo del estado, se muestra el diff parseado (líneas añadidas en verde, borradas en rojo, cabeceras de hunk en azul). Los archivos sin seguimiento muestran un aviso.
- **Ramas**: selector para cambiar de rama y botón *Nueva rama...*.
- **Trabajo en curso**: `git stash` / `git stash pop` desde el panel.
- **Descartar cambios**: botón *Revertir* sobre un archivo concreto (`git restore`).
- **Puntos sucios**: las pestañas con proyecto modificado muestran un punto naranja junto al nombre del archivo.

## 3. Remoto y GitHub

- **Pull / Push**: botones del panel Git. Si el repo no tiene `origin`, se ofrece asociarlo (usa el origen del repo cuando existe).
- **Share to GitHub**: menú `Git` → *Publicar en GitHub...*. Crea el repo remoto (público/privado), hace `git init` + primer commit + push. Requiere token.
- **Clonar**: menú `Git` → *Clonar repositorio GitHub...* abre el navegador de repos:
  - **Catálogo**: repos curados (GBDK, z88dk, csound, etc.) embebidos en la app — funciona **sin conexión**.
  - **Mis repos**: repos propios (requiere autenticación).
  - **Búsqueda**: GitHub Search API con filtros (lenguaje, `topic:...`). Los resultados se **guardan en caché** (`~/.samaruc-editor/github-search-cache.json`, 10 consultas, 24 h): sin conexión se reutilizan los últimos resultados de cada búsqueda.
  - **README**: cada repo tiene botón *Ver README* (se obtiene de raw.githubusercontent.com).
  - **Estrellas**: los repos tienen botón de estrella (requiere token).
  - La descarga intenta **`git clone`** primero; si falla, usa ZIP (el ZIP extraído se **convierte a repo git** automáticamente cuando hay anomalía, añadiendo `origin` y opcionalmente un commit inicial).
- **Publicar release**: menú `Git` → *Publicar release...*. Crea tag + release en GitHub y sube los artefactos del build (`.exe`, `.msi`, `.jar`). Abre la URL del release al terminar.
- **Issues**: menú `Git` → *Issues...*. Lista las issues abiertas del repo del proyecto y permite crear nuevas.

## 4. Barra de estado

- Muestra la **rama actual** a la izquierda del footer.
- Muestra **notificaciones transitorias** (~6 s) del avance de pull/push/commit/release en el estado correspondiente.

## 5. Asistente de nuevo proyecto

- En el diálogo de plantillas aparece **"Desde repositorio GitHub..."**: en lugar de crear carpetas localmente, abre el navegador de repos para clonar un proyecto de ejemplo.

## 6. Datos locales

| Dato | Ubicación |
|---|---|
| Repos clonados | `~/samaruc-editor/github-repos` |
| Caché de búsqueda | `~/.samaruc-editor/github-search-cache.json` |

## 7. Solución de problemas

- **"Límite de peticiones alcanzado (60/h)"**: sin token, la API pública limita a 60 peticiones/hora; usa `gh auth login` o configura un token.
- **Pull falla con cambios locales sin commitear**: haz commit o stash primero desde el panel.
- **Los tests de la suite**: `mvn test` (los de git/github, en verde; `GitServiceTest` omite automáticamente el round-trip `file://` en sistemas donde git MSYS no soporta esa URL; `FXUtilsProjectCreationTest` de translator está en rojo de forma preexistente y ajena a git).

---

Centro de control del feature: `docs/plan-integracion-github-completa.md` (trazabilidad por fases y commits).