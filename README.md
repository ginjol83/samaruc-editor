# Samaruc

Samaruc es un editor para C retro en JavaFX con compilacion integrada para GBDK y Z88DK.

## Información del proyecto

| Campo | Valor |
|---------|---------|
| Autor | Andrés Giménez |
| Versión | 1.0.0 |
| Año | 2026 |
| Licencia | MIT *(o la que corresponda)* |


## Requisitos

- Java 17+ (JDK)
- Maven en `PATH`
- Para empaquetar: `jpackage` en `PATH`
- Para instalador `exe`/`msi`: WiX Toolset v3 (`light.exe`, `candle.exe`)


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


```
