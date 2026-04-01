[CmdletBinding()]
param(
    [string]$AppName = 'Samaruc',
    [string]$InputDir = 'release-input',
    [string]$MainJar = 'samaruc-1.0-SNAPSHOT.jar',
    [string]$ReleaseDir = 'release',
    [string]$LibsDir = 'libs',
    [string]$SamplesDir = 'samples',
    [string]$PluginsDir = 'plugins',
    [string]$IconPath = 'jp-temp/icons/RetroEditor.ico',
    [ValidateSet('none', 'exe', 'msi', 'both')]
    [string]$InstallerType = 'none',
    [switch]$SkipMvn,
    [switch]$SkipJPackage
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-AbsolutePath {
    param([string]$BasePath, [string]$RelativePath)
    if ([System.IO.Path]::IsPathRooted($RelativePath)) {
        return [System.IO.Path]::GetFullPath($RelativePath)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $BasePath $RelativePath))
}

function Invoke-Robocopy {
    param(
        [Parameter(Mandatory=$true)][string]$Source,
        [Parameter(Mandatory=$true)][string]$Target
    )

    if (!(Test-Path -LiteralPath $Source)) {
        throw "La ruta de origen no existe: $Source"
    }

    New-Item -ItemType Directory -Force -Path $Target | Out-Null
    robocopy $Source $Target /E /R:2 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -gt 7) {
        throw "robocopy fallo al copiar '$Source' -> '$Target' (codigo $LASTEXITCODE)"
    }
}

function Remove-DirectoryIfExists {
    param([string]$PathToRemove)
    if (Test-Path -LiteralPath $PathToRemove) {
        Write-Host "[build-release] Limpiando: $PathToRemove"
        Remove-Item -LiteralPath $PathToRemove -Recurse -Force
    }
}

function New-AppImage {
    param(
        [Parameter(Mandatory=$true)][string]$JPackageExe,
        [Parameter(Mandatory=$true)][string]$ReleaseDir,
        [Parameter(Mandatory=$true)][string]$InputDir,
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string]$Jar,
        [Parameter(Mandatory=$true)][string]$Icon,
        [Parameter(Mandatory=$true)][string]$AppDir
    )

    Remove-DirectoryIfExists -PathToRemove $AppDir

    $jpackageArgs = @(
        '--type', 'app-image',
        '--dest', $ReleaseDir,
        '--input', $InputDir,
        '--name', $Name,
        '--main-jar', $Jar,
        '--icon', $Icon
    )

    & $JPackageExe @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage app-image termino con codigo $LASTEXITCODE"
    }
}

function New-WindowsInstaller {
    param(
        [Parameter(Mandatory=$true)][string]$JPackageExe,
        [Parameter(Mandatory=$true)][string]$ReleaseDir,
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string]$Icon,
        [Parameter(Mandatory=$true)][string]$Type,
        [Parameter(Mandatory=$true)][string]$AppDir
    )

    $jpackageArgs = @(
        '--type', $Type,
        '--dest', $ReleaseDir,
        '--name', $Name,
        '--icon', $Icon,
        '--app-image', $AppDir
    )

    & $JPackageExe @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage $Type termino con codigo $LASTEXITCODE"
    }
}

function Assert-WixInstalled {
    $lightCmd = Get-Command light.exe -ErrorAction SilentlyContinue
    $candleCmd = Get-Command candle.exe -ErrorAction SilentlyContinue

    if ($null -eq $lightCmd -or $null -eq $candleCmd) {
        throw 'Para generar instaladores EXE/MSI necesitas WiX Toolset en PATH (light.exe y candle.exe).'
    }
}

$scriptRoot    = Split-Path -Parent $MyInvocation.MyCommand.Path
$inputDirAbs   = Resolve-AbsolutePath -BasePath $scriptRoot -RelativePath $InputDir
$releaseDirAbs = Resolve-AbsolutePath -BasePath $scriptRoot -RelativePath $ReleaseDir
$libsDirAbs    = Resolve-AbsolutePath -BasePath $scriptRoot -RelativePath $LibsDir
$samplesDirAbs = Resolve-AbsolutePath -BasePath $scriptRoot -RelativePath $SamplesDir
$pluginsDirAbs = Resolve-AbsolutePath -BasePath $scriptRoot -RelativePath $PluginsDir
$iconPathAbs   = Resolve-AbsolutePath -BasePath $scriptRoot -RelativePath $IconPath
$mainJarAbs    = Resolve-AbsolutePath -BasePath $inputDirAbs -RelativePath $MainJar
$releaseAppDir = Join-Path $releaseDirAbs $AppName

Write-Host "[build-release] Root: $scriptRoot"
Write-Host "[build-release] App:  $AppName"
Write-Host "[build-release] Out:  $releaseAppDir"
Write-Host "[build-release] InstallerType: $InstallerType"

# ── PASO 1: compilar con Maven ────────────────────────────────────────────────
if (-not $SkipMvn) {
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -eq $mvnCmd) {
        throw 'No se encontro mvn en PATH.'
    }

    Write-Host '[build-release] Compilando con Maven (mvn -q -DskipTests package)...'
    Push-Location $scriptRoot
    try {
        & $mvnCmd.Source -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven termino con codigo $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }

    $generatedJar = Join-Path $scriptRoot "target\$MainJar"
    if (!(Test-Path -LiteralPath $generatedJar)) {
        throw "Maven no genero el JAR esperado: $generatedJar"
    }

    New-Item -ItemType Directory -Force -Path $inputDirAbs | Out-Null
    Copy-Item -LiteralPath $generatedJar -Destination $mainJarAbs -Force
    Write-Host "[build-release] Copiando JAR a release-input: $MainJar"
}

# ── PASO 2: validaciones ──────────────────────────────────────────────────────
if (!(Test-Path -LiteralPath $mainJarAbs))    { throw "No existe el jar principal: $mainJarAbs" }
if (!(Test-Path -LiteralPath $libsDirAbs))    { throw "No existe el directorio de librerias: $libsDirAbs" }
if (!(Test-Path -LiteralPath $samplesDirAbs)) { throw "No existe el directorio de samples: $samplesDirAbs" }
if (!(Test-Path -LiteralPath $pluginsDirAbs)) { throw "No existe el directorio de plugins: $pluginsDirAbs" }
if (!(Test-Path -LiteralPath $iconPathAbs))   { throw "No existe el icono configurado: $iconPathAbs" }
if ($SkipJPackage -and $InstallerType -ne 'none') {
    throw 'No se puede usar InstallerType cuando SkipJPackage esta activo.'
}

# ── PASO 3: jpackage ─────────────────────────────────────────────────────────
if (-not $SkipJPackage) {
    $jpackageCmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($null -eq $jpackageCmd) {
        throw 'No se encontro jpackage en PATH.'
    }

    Write-Host '[build-release] Ejecutando jpackage...'
    Write-Host "[build-release] Icono configurado: $iconPathAbs"

    New-Item -ItemType Directory -Force -Path $releaseDirAbs | Out-Null
    New-AppImage -JPackageExe $jpackageCmd.Source -ReleaseDir $releaseDirAbs -InputDir $inputDirAbs -Name $AppName -Jar $MainJar -Icon $iconPathAbs -AppDir $releaseAppDir
}

if (!(Test-Path -LiteralPath $releaseAppDir)) {
    throw "No existe la salida esperada de jpackage: $releaseAppDir"
}

# ── PASO 4: copiar dependencias ───────────────────────────────────────────────
Write-Host "[build-release] Copiando libs a $releaseAppDir"
Invoke-Robocopy -Source $libsDirAbs    -Target (Join-Path $releaseAppDir 'libs')

Write-Host "[build-release] Copiando samples a $releaseAppDir"
Invoke-Robocopy -Source $samplesDirAbs -Target (Join-Path $releaseAppDir 'samples')

Write-Host "[build-release] Copiando plugins a $releaseAppDir"
Invoke-Robocopy -Source $pluginsDirAbs -Target (Join-Path $releaseAppDir 'plugins')

# ── PASO 5: instalador opcional ───────────────────────────────────────────────
if (-not $SkipJPackage -and $InstallerType -ne 'none') {
    $jpackageCmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($null -eq $jpackageCmd) {
        throw 'No se encontro jpackage en PATH.'
    }
    Assert-WixInstalled

    if ($InstallerType -in @('exe', 'both')) {
        Write-Host '[build-release] Generando instalador EXE...'
        New-WindowsInstaller -JPackageExe $jpackageCmd.Source -ReleaseDir $releaseDirAbs -Name $AppName -Icon $iconPathAbs -Type 'exe' -AppDir $releaseAppDir
    }
    if ($InstallerType -in @('msi', 'both')) {
        Write-Host '[build-release] Generando instalador MSI...'
        New-WindowsInstaller -JPackageExe $jpackageCmd.Source -ReleaseDir $releaseDirAbs -Name $AppName -Icon $iconPathAbs -Type 'msi' -AppDir $releaseAppDir
    }
}

# ── PASO 6: limpieza legacy ───────────────────────────────────────────────────
Remove-DirectoryIfExists -PathToRemove (Join-Path $scriptRoot 'jp-out')
Remove-DirectoryIfExists -PathToRemove (Join-Path $scriptRoot 'release-fixed')
Remove-DirectoryIfExists -PathToRemove (Join-Path $releaseDirAbs 'RetroEditor')

if ($InstallerType -ne 'none') {
    $exeFound = @(Get-ChildItem -Path $releaseDirAbs -Filter "$AppName*.exe" -ErrorAction SilentlyContinue)
    $msiFound = @(Get-ChildItem -Path $releaseDirAbs -Filter "$AppName*.msi" -ErrorAction SilentlyContinue)

    if ($InstallerType -in @('exe', 'both') -and $exeFound.Count -eq 0) {
        throw "No se encontro instalador EXE en: $releaseDirAbs"
    }
    if ($InstallerType -in @('msi', 'both') -and $msiFound.Count -eq 0) {
        throw "No se encontro instalador MSI en: $releaseDirAbs"
    }
}

Write-Host '[build-release] OK: build generado en release y sin tocar RetroEditor.ico.'
