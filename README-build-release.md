# README `build-release.ps1`

Este documento explica como usar el script `build-release.ps1` para compilar, empaquetar y generar la distribución final de Samaruc con un solo comando.

## Objetivo

El script automatiza este flujo completo:

1. Compila el proyecto con Maven (`mvn -q -DskipTests package`) y copia el JAR resultante a `release-input/`.
2. Ejecuta `jpackage` para crear `release/Samaruc` con el icono personalizado.
3. Copia `libs`, `samples` y `plugins` a `release/Samaruc/`.
4. Opcionalmente genera instalador Windows (`.exe` y/o `.msi`) a partir de ese `app-image`.
5. Elimina salidas legacy (`jp-out`, `release-fixed`) para dejar un unico resultado final.

Asi se genera el ejecutable `Samaruc.exe` completo con un solo comando, sin pasos manuales intermedios.

## Requisitos

- Windows con PowerShell.
- **Maven** disponible en PATH (`mvn`).
- **JDK** con `jpackage` disponible en PATH.
- **WiX Toolset** en PATH (`light.exe` y `candle.exe`) si usas `-InstallerType exe|msi|both`.
- Estructura base esperada en el repo:
  - `jp-temp/icons/RetroEditor.ico` (icono obligatorio)
  - `libs/`
  - `samples/`
  - `plugins/`

> No es necesario tener el JAR en `release-input/` de antemano: el script lo genera y copia automaticamente.

## Parametros

- `AppName`: `Samaruc`
- `InputDir`: `release-input`
- `MainJar`: `samaruc-1.0-SNAPSHOT.jar`
- `ReleaseDir`: `release`
- `LibsDir`: `libs`
- `SamplesDir`: `samples`
- `PluginsDir`: `plugins`
- `IconPath`: `jp-temp/icons/RetroEditor.ico` (obligatorio, falla si no existe)
- `InstallerType`: `none | exe | msi | both` (por defecto: `none`)
- `SkipMvn` (switch): omite la compilacion Maven (usa el JAR ya existente en `release-input/`).
- `SkipJPackage` (switch): omite `jpackage` y solo recopia dependencias en `release/<AppName>`.

## Uso rapido

**Flujo completo** (compilar + empaquetar):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1
```

**Solo empaquetar** (sin recompilar, usa el JAR existente):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1 -SkipMvn
```

**Solo recopia dependencias** sobre un release ya generado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1 -SkipMvn -SkipJPackage
```

**Generar app-image + instalador EXE**:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1 -InstallerType exe
```

**Generar app-image + instaladores EXE y MSI**:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1 -InstallerType both
```

## Flujo interno

1. **Maven**: `mvn -q -DskipTests package` → copia `target/samaruc-1.0-SNAPSHOT.jar` a `release-input/`.
2. **Validaciones**: verifica JAR, icono, libs, samples y plugins.
3. **jpackage app-image**: genera `release/Samaruc` con el icono especificado.
4. **Copias**: libs, samples y plugins se copian a `release/Samaruc/`.
5. **Instalador opcional**: si `InstallerType` no es `none`, ejecuta `jpackage --type exe/msi --app-image release/Samaruc`.
6. **Limpieza**: elimina `jp-out`, `release-fixed` y cualquier carpeta legacy `release/RetroEditor`.

## Troubleshooting

- **"No se encontro mvn en PATH"**
  - Instala Maven y agrega `<maven>/bin` al PATH del sistema.

- **"Maven no genero el JAR esperado"**
  - Comprueba que `mvn package` funciona correctamente desde la raiz del proyecto.

- **"No existe el icono configurado"**
  - Verifica que existe `jp-temp/icons/RetroEditor.ico`.

- **"No se encontro jpackage en PATH"**
  - Instala/selecciona un JDK que incluya `jpackage` y agrega su `bin` al PATH.

- **"Para generar instaladores EXE/MSI necesitas WiX Toolset en PATH"**
  - Instala WiX Toolset y agrega su carpeta `bin` al PATH (debe resolver `light.exe` y `candle.exe`).

- **Error de `robocopy` con codigo > 7**
  - Revisa bloqueo de archivos (antivirus/procesos en uso) y permisos de escritura.

## Nota

El script usa `Set-StrictMode -Version Latest` y `ErrorActionPreference = 'Stop'`, por lo que falla rapido ante cualquier inconsistencia para evitar releases incompletos.
