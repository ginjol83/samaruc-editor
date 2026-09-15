# Plan: integración de GitHub completa en Samaruc

> Estado: **en progreso** — Fase 0 ✅ | Fase 1 ✅ | Fase 2 ✅ | Fase 3 ✅ | Fase 4 casi completa.
> Última actualización: 2026-09-15 (Fase 3 completada; Fase 4 en curso)

---

## 1. Contexto y estado actual

### Lo que ya existe (implementado)

| Componente | Estado | Archivos |
|---|---|---|
| **Git CLI wrapper** | ✅ completo | `service/git/GitCli.java` (ProcessBuilder, timeout, UTF-8) |
| **Git models** | ✅ completo | `service/git/GitModels.java` (GitStatusEntry, GitCommitInfo, GitBranchInfo) |
| **GitService orquestador** | ✅ completo | `service/git/GitService.java` — init, status, branch, add, commit, log, diffStat, push, pull, clone, remoteAdd, hasRemote, remoteUrl, userEmail, setLocalUser, listBranches, createBranch, switchBranch, stash, stashPop, restoreFile |
| **GitPanelController** | ✅ completo | `controller/GitPanelController.java` — panel acoplable: branch, cambios, commit, historial, init, dirty paths callback, status callback |
| **Integración en MainController** | ✅ completo | pestaña Git en outputPane, dirty markers (naranja) en tabs, lblGitBranch en status bar, menú Git, btnGit toolbar |
| **Share to GitHub** | ✅ completo | `GitHubShareService.shareProject()` — crea repo, init, addRemote, commit, push con token efímero |
| **Autenticación GitHub** | ✅ completo | `GitHubAuthService` — cascade gh → env → settings token, validación GET /user |
| **GitHubRepoService** | ✅ completo | `POST /user/repos` para crear repos public/private |
| **GitHubApiService** | ✅ completo | Búsqueda de repos, getUserRepos (con token), getRepository |
| **GitHubCloneService** | ✅ fallback ZIP | Descarga ZIP + extract; `GitService.clone()` (real git) se usa primero desde el controller |
| **i18n** | ✅ completo | Todas las claves git.panel.* y git.share.* en los 6 bundles |
| **CSS** | ✅ completo | `.git-dirty-dot` en ambos themes (app.css, app-dark.css) |
| **Tests** | ✅ fase 1+2 | `GitServiceTest` completo (init/status/add/commit/log + round-trip remoto file://) |

### Lo que falta

| Componente | Estado | Fase |
|---|---|---|
| GitHubReleaseService | ✅ completo (tests incluidos) | 3 ✅ |
| GitHubIssueService | ✅ completo (tests incluidos) | 3 ✅ |
| Navegador: "Mis repos" con auth | ✅ completo | 3 ✅ |
| Navegador: botón de starring | ✅ completo | 3 ✅ |
| Plantillas desde repos de ejemplo en el asistente | ✅ completo | 3 ✅ |
| Navegador: filtros por topic | ❌ | 4 |
| Navegador: vista previa README | ❌ | 4 |
| Diff visual inline | ❌ | 4 |
| Caché offline | ❌ | 4 |
| Tests Fase 3 (release/share/clone) | ✅ completados | 3 ✅ |

---

## 2. Objetivos

1. **Git dentro del IDE** → ✅ logrado (Fase 0+1+2)
2. **Sincronización bidireccional** con GitHub: pull/push → ✅ logrado (Fase 2)
3. **Autenticación** sin fricción → ✅ logrado (Fase 2 servicios)
4. **Share to GitHub** → ✅ logrado
5. **Publicar releases** de los builds → ✅ logrado (Fase 3)
6. **Enriquecer el navegador de repos** → ⏳ parcial (starring ✅, filtros/README pendiente)

---

## 3. Decisiones de diseño (mantenidas del plan original)

- **Motor git**: CLI del sistema (`ProcessBuilder`) con fallback ZIP — ✅ implementado
- **Autenticación**: cascade gh → env → settings con token efímero en pushes — ✅ implementado
- **Arquitectura**: `service/git/*` + `service/github/*` + `controller/GitPanelController` — ✅ implementada

---

## 4. Fases de implementación

### Fase 0 — Prerrequisito ✅ COMPLETADA

- [x] Migrar las tools a pestañas (diálogo pestaña/ventana) en `MainController` + `FXUtils.addToolTab`
- [x] Refactor de `GitHubRepoBrowserController` al patrón `createNode(...)` / `setOnCloseRequest(...)`
- [x] El navegador de GitHub se abre como pestaña

### Fase 1 — Git local ✅ COMPLETADA

- [x] `GitService` + `GitCli`: `init`, `status --porcelain`, `branch --show-current`, `add`, `commit -m`, `log --oneline -n N`, `diff --stat`
- [x] `GitPanelController` acoplable (commit + historial básico) y puntos sucios en pestañas
- [x] Integrar `onOpenGitPanel` en menú y barra
- [x] Tests: `GitServiceTest` con repo temporal real, ronda add/commit/status

### Fase 2 — Remoto y autenticación ✅ COMPLETADA

**Completado:**
- [x] `GitHubAuthService` (cascade gh → env → settings) + verificación `GET /user`
- [x] `GitService.pull/push/clone/remoteAdd` con credencial efímera
- [x] Clone real preferente (fallback ZIP)
- [x] **Share to GitHub**: `GitHubRepoService.create()` + push del proyecto actual
- [x] Indicador de rama en la barra de estado (`lblGitBranch`)
- [x] **Botones Pull/Push en GitPanelController**: `btnPull`/`btnPush` con credenciales resueltas vía `resolveCredentials()` (`githubTokenSupplier` + fallback `SAMARUC_GITHUB_TOKEN`)
- [x] **Selector de ramas**: `cmbBranch` en el panel (con flag `updatingBranches`), crear rama nueva (`btnNewBranch`) y switching
- [x] **Operación de stash**: `git stash` / `git stash pop` desde el panel (`btnStash`, `btnStashPop`)
- [x] **Descartar cambios individuales**: `git restore` sobre archivos del status list (`btnRestore` con hub de ventana)
- [x] **Menú contextual del Explorador de Proyectos**: en la raíz del repo → submenú `Git` (Abrir panel, Commit…, Pull, Push, Nueva rama…, Stash, Recuperar stash, Publicar en GitHub…)
- [x] **Métodos públicos del panel** para reutilizar desde MainController: `commitWithMessage`, `pull`, `push`, `newBranch`, `stash`, `popStash`
- [x] **Tests**: `GitHubAuthServiceTest` (cascade + validación con HttpServer), `GitHubRepoServiceTest` (POST /user/repos con HttpServer), `GitServiceTest` ampliado (createAndSwitchBranchRoundTrip, stashAndPopRoundTrip, restoreFile)*

*Nota: el round-trip remoto `file://` (`remoteAddPushClonePullRoundTrip`) se omite automáticamente en entornos git msys/Windows sin soporte de URLs `file://` (asumido con `isFileUrlSupported()`). El resto de tests git/github quedan en verde.

### Fase 3 — GitHub avanzado (releases / repos propios / issues) ✅ COMPLETADA

**Completado:**
- [x] **`GitHubReleaseService`**: crear tag + release (`POST /repos/{owner}/{repo}/releases`) y subir artefactos (`POST {upload_url}?name=...`; Content-Type por extensión). Constructores package-private para tests.
- [x] **Tests `GitHubReleaseServiceTest`**: 9 tests con `HttpServer` (POST + parseo uploadUrl, errores 422/401/404, upload con nombre+content-type, `stripUploadTemplate`, `parseOwnerRepo` con https/scp/ssh://).
- [x] **`GitHubIssueService`**: `listOpenIssues`, `listIssues` (descarta pull_requests), `createIssue`. Constructores package-private para tests.
- [x] **Tests `GitHubIssueServiceTest`**: 4 tests con `HttpServer` (list+skip PR, create POST+parse, errores 404/401).
- [x] **Publicar release desde UI**: menú `Publicar release...` (MainView + MainController + ProjectExplorerController), diálogo con tag/nombre/descripción/draft/prerelease + selección de artefactos del build, `publishReleaseAsync` (resuelve token, crea release, sube assets, abre `html_url`).
- [x] **Starring**: `GitHubApiService.starRepo()`, `unstarRepo()`, `isStarred()`, helper privado `enc()`. `GitHubRepoBrowserController.createStarButton()` integrado en `RepoCell` y `CuraredCell`.
- [x] **Issues desde UI**: menú `Issues...` (MainView + MainController + ProjectExplorerController), `showIssuesDialog` (ListView de issues + formulario crear issue + botón Crear), `loadIssues` async.
- [x] **Plantillas**: `GITHUB` añadido a `ProjectTemplate` en `FXUtils`; en `createNewProject`, si el usuario elige "Desde repositorio GitHub..." se abre el navegador en vez de crear archivos (`wasLastCreatedProjectGitHubTemplate()`). Claves i18n en los 6 bundles.
- [x] **Tests**: 387 tests, 1 fallo preexistente (translator.smt), 1 skip (file:// remoto), 0 errores.

**Pendiente (opcional / Fase 4):**
- [ ] Navegador: filtros por topic (`topic:gbdev`, `topic:z80`) y lenguaje
- [x] Navegador: vista previa README del repo seleccionado (raw.githubusercontent) — pestañas con TextArea propio por sección, botón "Ver README" (`003d18a`)

### Fase 4 — Pulido ⏳ CASI COMPLETADA

- [x] Caché offline de resultados de búsqueda (`GitHubSearchCache` en `~/.samaruc-editor/github-search-cache.json`, 10 consultas, 24 h; fallback en `doSearch`) — `46eaedf`
- [x] Notificaciones de avances (pull/push/release) en la barra de estado (`showOperationStatus` transitorio vía Timeline) — `8346a5e`
- [x] Migrar `~/samaruc-editor/github-repos` a clones git cuando exista `git` (`convertToGit` best-effort, commit solo con identidad) — `20fb11e`
- [x] Diff visual inline en el panel Git (`git diff --color=never [--cached]` parseado en ADD/REMOVE/HUNK/CONTEXT/HEADER) — `5cdda6e`
- [x] Doc: guía de uso en `docs/`; actualizar README
- [x] Fix `.gitignore`: eliminado `/docs/` para que la documentación del proyecto entre en los commits (alineado con AGENTS.md)

---

## 5. Archivos afectados por fase

### Fase 2 — completado

| Área | Archivos | Estado |
|---|---|---|
| Panel UI | `GitPanelController.java` | ✅ btnPull, btnPush, cmbBranch, btnNewBranch, stash, restore |
| Servicio git | `GitService.java` | ✅ `listBranches()`, `createBranch()`, `switchBranch()`, `stash()`, `stashPop()`, `restoreFile()` |
| MainController | `MainController.java` | ✅ token supplier, métodos públicos git y `shareToGithub()`, menú contextual del explorador |
| Explorador | `ProjectExplorerController.java` | ✅ submenú Git en la raíz del repo |
| Tests | `GitServiceTest.java` | ✅ branch switching, stash, restore, round-trip remoto (skip en entornos sin file://) |
| Tests | `GitHubAuthServiceTest.java` (nuevo) | ✅ HttpServer simulado, cascade, validación |
| Tests | `GitHubRepoServiceTest.java` (nuevo) | ✅ HttpServer simulado, creación de repo |
| i18n | `MessagesBundle_*.properties` | ✅ 20 claves `git.panel.*` en los 6 bundles |

### Fase 3 — archivos afectados

| Área | Archivos | Estado |
|---|---|---|
| Release | `GitHubReleaseService.java` (nuevo), `GitHubReleaseServiceTest.java` (nuevo) | ✅ |
| Issue | `GitHubIssueService.java` (nuevo), `GitHubIssueServiceTest.java` (nuevo) | ✅ |
| Star | `GitHubApiService.java` (+starRepo, +unstarRepo, +isStarred) | ✅ |
| Navegador | `GitHubRepoBrowserController.java` (+createStarButton) | ✅ |
| Plantillas | `FXUtils.java` (+GITHUB enum, +wasLastCreatedProjectGitHubTemplate) | ✅ |
| MainController | `MainController.java` (+publishRelease, +showIssues, +dialogs) | ✅ |
| Explorador | `ProjectExplorerController.java` (+gitReleaseItem, +gitIssuesItem) | ✅ |
| Vista | `MainView.fxml` (+menuItemPublishRelease, +menuItemIssues) | ✅ |
| i18n | `MessagesBundle*.properties` (6 bundles: menu.git.release/issues, git.release.draft/prerelease, template.option/description.github) | ✅ |

---

## 6. Riesgos y limitaciones

- **`git` ausente**: las operaciones push/pull/stash/branch se ocultan si `GitService.isGitAvailable()` es false; Share to GitHub falla con mensaje claro
- **Token en texto plano**: mitigado con cascade gh/env + aviso + acción borrar
- **Límites de API** sin token (60/h): manejo de `RateLimitException` existente
- **GitHubEnterprise / GitLab**: fuera de alcance
- **Commits vacíos en pull**: si hay cambios locales sin commit, pull falla → ofrecer stash automático o mensaje de aviso
- **Test ajeno a git en rojo**: `FXUtilsProjectCreationTest.createProjectFromTranslatorTemplateWritesSmTFileAndNoMain` falla (preexistente, incluido en fa1daa7 sin la lógica de `translation.smt` en `FXUtils.createProjectFromTemplate`). Fuera del alcance de la integración Git/GitHub; no afecta a los tests de git/github.

---

## 7. Métricas de éxito

- [x] Un proyecto recién creado se puede llevar a GitHub con 2 clics (Share to GitHub)
- [x] Pull/push del proyecto abierto desde el panel Git (con credenciales resueltas)
- [x] Cambio de rama y creación de rama desde el panel
- [x] Publicar artefactos del build como GitHub Release desde el menú Git (`Publicar release...`, con artefactos de `resolveBuildArtifacts`)
- [x] 100 % de claves nuevas traducidas en los 6 bundles; tests verdes (`mvn test`: 387 tests, 1 fallo preexistente translator, 1 skip file://)
