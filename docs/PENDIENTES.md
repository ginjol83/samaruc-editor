# Pendientes del proyecto

Actualizado: 2026-10-15
Commit consolidado: 46630111

## 1. Organizativo / Release

- [ ] **Release 1.3.3**. Actualizar: pom.xml (version), VERSION.txt, uild-release.ps1 ( y --app-version). Actualizar CHANGELOG.md con cambios desde 1.3.2. Crear tag X.Y.Z, push y generar release con artefactos (.exe, .msi, .jar) vía gh release create.
- [ ] **Limpiar docs versionados**. Los .md de docs/ están en .gitignore. Decidir si se quieren versionar o eliminar. Si se versionan, usar git add -f docs/*.md; si no, mantener ignorados.

## 2. P3.4 - Cierre transversal (paridad)

- [ ] **Actualizar AGENTS.md**. En el apartado de estructura de libs/ dice que mesen y openmsx están empaquetados "sin ninguna referencia desde el código". Desde 29/09 tienen referencias (o al menos están presentes en la distribución). Revisar y corregir ese párrafo.
- [ ] **Resumir informe integrado**. Completar los puntos de P3.4 marcados en estado-integrado-del-proyecto.md (resumen del informe, pasada final de i18n).
- [ ] **Revisar dobleces de backslash i18n** (ya corregidos hoy en de/fr). Asegurar que todos los bundles usan el mismo formato (\uXXXX o bytes crudos) y que MessagesBundleParityTest sigue pasando.
- [ ] **Inventario de claves i18n**. Revisar si falta alguna clave usada (p.ej. config.compiler.option.gbdk mencionada) y añadirla a los 6 bundles.

## 3. IA - Verificación y UI

- [ ] **UI de ajustes IA**. AiSettings existe y se persiste en propiedades, pero no está expuesto en ConfigView.fxml. Añadir pestaña/sección de IA en la configuración (modelo, timeout, rutas opencode, etc.) con bindings a ConfigController.
- [ ] **Verificar ciclo real de IA**. Probar en la app JavaFX el ciclo completo opencode serve + streaming en el panel IA (no solo tests unitarios). Comprobar reconexión, timeouts y eventos session.idle/message.part.updated.

## 4. Editor de sprites - Animaciones

- [ ] **Animaciones nombradas**. Añadir panel para crear secuencias de animación nombradas (caminar, correr, atacar...) que referencien frames ya existentes (reutilizables).
- [ ] **Offsets por paso**. Permitir desplazamiento X/Y por frame de animación (offsets por paso) para compensar movimiento del sprite entre frames.
- [ ] **Marcadores de bucle**. Añadir marcador de inicio/fin de bucle por animación.
- [ ] **Onion skinning**. Mostrar frames anterior/siguiente semitransparentes en el preview de animación.

## 5. Tests de regresión (paridad)

- [ ] **Perfil Atari detectado**. Añadir test: proyecto Atari detectado compila con -t atari (verificar esolveCompilerTarget/perfil).
- [ ] **Artefacto Game Gear**. Corregir/comprobar esolveSmsArtifact: cuando target es Game Gear, preferir .gg antes que .sms (la lógica actual parece preferir .sms). Añadir test.
- [ ] **Snapshot CPC**. Al generar snapshot/artefacto .cpc, Caprice32 requiere -subtype=dsk (o cpr). Añadir/subsanar en generación de artefactos CPC con test de verificación.

## 6. Roadmap medio/largo plazo (sin compromiso)

- Jerarquía de llamadas (Call Hierarchy)
- Quick Fixes / Bombilla (code actions)
- Depurador source-level (integrado con emuladores)
- Profiler de T-states
- Optimizador de tilesets
- Analizador de attribute clash (ZX Spectrum)
- Tracker multi-canal: export a drivers (Arkos/AY)
- MSX/NES: cubrir detalles menores si surgen
- SDK de plugins documentado
- IA Fase 3/4: selector de modelo, permisos, límites, MCP, skills de dominio