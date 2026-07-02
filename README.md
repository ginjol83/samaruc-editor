# Samaruc

Samaruc es un editor para C retro en JavaFX con compilacion integrada para GBDK y Z88DK, incluyendo soporte para proyectos mixtos C + Assembly y visor Markdown.

Soporta resaltado de sintaxis para `.c`, `.h`, `.asm` y `.s`, y compila archivos Assembly con los toolchains de GBDK y z88dk.

## Requisitos

- Java 17+ (JDK)
- Maven en `PATH`
- Para empaquetar: `jpackage` en `PATH`
- Para instalador `exe`/`msi`: WiX Toolset v3 (`light.exe`, `candle.exe`)

`build-release.ps1` autodetecta WiX 3 en este orden:

- copia portable dentro del repo, por ejemplo `tools/wix/bin` o `third_party/wix/bin`
- variables `WIX_BIN`, `WIX`, `WIX_HOME`
- herramientas disponibles en `PATH`
- instalaciones tipicas en `Program Files`

> Nota: si solo tienes WiX 4 (`wix.exe`), `jpackage` en Windows no lo usa para generar `exe`/`msi`; necesitas WiX 3.

Antes de empaquetar instaladores, `build-release.ps1` muestra la ruta detectada y la version exacta de WiX que va a usar.

## Ejecutar en desarrollo

```powershell
mvn compile
mvn javafx:run
```

## Ejecutar tests

```powershell
mvn -q test
```

Para ver salida detallada de tests:

```powershell
mvn -Pverbose-tests test
```

Ejecutar una clase de test concreta:

```powershell
mvn -Dtest=EditorModelTest test
```

Ejecutar un metodo de test concreto:

```powershell
mvn -Dtest=EditorModelTest#openFileRejectsBinaryContent test
```

Ejecutar solo tests de servicios:

```powershell
mvn -q -Dtest="com.retroeditor.service.*Test" test
```

## Comandos de uso rapido

```powershell
# Compilar proyecto
mvn compile

# Ejecutar la app en desarrollo
mvn javafx:run

# Ejecutar todos los tests
mvn test

# Ejecutar tests con log detallado
mvn -Pverbose-tests test

# Empaquetar release
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1
```

## Generar release

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1
```

Instalador Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1 -InstallerType exe
```

El instalador se genera con un nombre distinguible, por ejemplo:

```text
release/Samaruc-Setup-1.0.exe
```

Al instalar en Windows, el instalador crea:

- entrada en el menu Inicio
- acceso directo en el escritorio
- grupo de menu `Samaruc`

La aplicacion portable queda aparte en:

```text
release/Samaruc/Samaruc.exe
```

Instaladores EXE + MSI en una sola ejecucion:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1 -InstallerType both
```

Salida previa esperada en consola:

```text
[build-release] Targets instalador: EXE + MSI (una sola ejecucion)
[build-release] WiX detectado: C:\ruta\a\wix\bin [origen]
[build-release] WiX version: 3.14.0.6526
[build-release] Instalador EXE final: D:\ruta\release\Samaruc-Setup-1.0.exe
[build-release] Instalador MSI final: D:\ruta\release\Samaruc-Setup-1.0.msi
```

Sin tocar `PATH`, puedes dejar WiX 3 portable con esta estructura:

```text
tools/
  wix/
	bin/
	  candle.exe
	  light.exe
```

La salida final se genera en `release/`.
