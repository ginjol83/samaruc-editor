[CmdletBinding()]
param(
    [string]$AppName = 'Samaruc',
    [string]$InputDir = 'release/input',
    [string]$MainJar = 'samaruc-1.0-SNAPSHOT.jar',
    [string]$ReleaseDir = 'release',
    [string]$LibsDir = 'libs',
    [string]$SamplesDir = 'samples',
    [string]$PluginsDir = 'plugins',
    [string]$IconPath = 'src/main/resources/icons/Samaruc.ico',
    [ValidateSet('none', 'exe', 'msi', 'both')]
    [string]$InstallerType = 'none',
    [switch]$SkipMvn,
    [switch]$SkipJPackage,
    [switch]$KeepTarget
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

    $installerPattern = "$Name*.$Type"
    $existingInstallers = @(
        Get-ChildItem -Path $ReleaseDir -Filter $installerPattern -File -ErrorAction SilentlyContinue |
            Where-Object { $_.BaseName -notlike "$Name-Setup*" }
    )
    foreach ($existingInstaller in $existingInstallers) {
        Remove-Item -LiteralPath $existingInstaller.FullName -Force -ErrorAction SilentlyContinue
    }

    $jpackageArgs = @(
        '--type', $Type,
        '--dest', $ReleaseDir,
        '--name', $Name,
        '--icon', $Icon,
        '--app-image', $AppDir,
        '--win-menu',
        '--win-shortcut',
        '--win-menu-group', $Name
    )

    & $JPackageExe @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage $Type termino con codigo $LASTEXITCODE"
    }

    $generatedInstallers = @(
        Get-ChildItem -Path $ReleaseDir -Filter $installerPattern -File -ErrorAction SilentlyContinue |
            Where-Object { $_.BaseName -notlike "$Name-Setup*" }
    )
    if ($generatedInstallers.Count -ne 1) {
        throw "No se pudo identificar de forma unica el instalador $Type generado en: $ReleaseDir"
    }

    $generatedInstaller = $generatedInstallers[0]
    $suffix = $generatedInstaller.BaseName.Substring($Name.Length)
    $renamedInstallerPath = Join-Path $ReleaseDir ("$Name-Setup$suffix.$Type")

    if ($generatedInstaller.FullName -ne $renamedInstallerPath) {
        if (Test-Path -LiteralPath $renamedInstallerPath) {
            Remove-Item -LiteralPath $renamedInstallerPath -Force
        }
        Move-Item -LiteralPath $generatedInstaller.FullName -Destination $renamedInstallerPath -Force
    }

    return $renamedInstallerPath
}

function Test-Wix3BinDirectory {
    param([string]$PathToCheck)

    if ([string]::IsNullOrWhiteSpace($PathToCheck)) {
        return $false
    }

    $normalizedPath = [System.IO.Path]::GetFullPath($PathToCheck)
    $lightExe = Join-Path $normalizedPath 'light.exe'
    $candleExe = Join-Path $normalizedPath 'candle.exe'

    return (Test-Path -LiteralPath $lightExe) -and (Test-Path -LiteralPath $candleExe)
}

function Find-WixToolset {
    param([Parameter(Mandatory=$true)][string]$ScriptRoot)

    $candidates = New-Object System.Collections.ArrayList
    $seenCandidates = @{}
    $wix4Hints = New-Object System.Collections.ArrayList
    $seenWix4Hints = @{}

    function Add-WixCandidate {
        param([string]$CandidatePath, [string]$Source)

        if ([string]::IsNullOrWhiteSpace($CandidatePath)) {
            return
        }

        $normalizedPath = [System.IO.Path]::GetFullPath($CandidatePath)
        $key = $normalizedPath.ToLowerInvariant()
        if (-not $seenCandidates.ContainsKey($key)) {
            [void]$candidates.Add([pscustomobject]@{
                Path   = $normalizedPath
                Source = $Source
            })
            $seenCandidates[$key] = $true
        }
    }

    function Add-Wix4Hint {
        param([string]$HintPath)

        if ([string]::IsNullOrWhiteSpace($HintPath)) {
            return
        }

        $normalizedPath = [System.IO.Path]::GetFullPath($HintPath)
        $key = $normalizedPath.ToLowerInvariant()
        if (-not $seenWix4Hints.ContainsKey($key)) {
            [void]$wix4Hints.Add($normalizedPath)
            $seenWix4Hints[$key] = $true
        }
    }

    foreach ($relativePath in @(
        'tools\wix\bin',
        'tools\wix3\bin',
        'tools\wix311\bin',
        'third_party\wix\bin',
        'third_party\wix3\bin',
        'third_party\wix311\bin',
        'vendor\wix\bin',
        'vendor\wix3\bin',
        'vendor\wix311\bin'
    )) {
        Add-WixCandidate -CandidatePath (Join-Path $ScriptRoot $relativePath) -Source "repo:$relativePath"
    }

    foreach ($containerRelative in @('tools', 'third_party', 'vendor')) {
        $containerPath = Join-Path $ScriptRoot $containerRelative
        if (Test-Path -LiteralPath $containerPath) {
            $matchingDirs = @(Get-ChildItem -LiteralPath $containerPath -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'wix*' })
            foreach ($dir in $matchingDirs) {
                Add-WixCandidate -CandidatePath (Join-Path $dir.FullName 'bin') -Source "repo:$containerRelative\$($dir.Name)\bin"
                Add-WixCandidate -CandidatePath $dir.FullName -Source "repo:$containerRelative\$($dir.Name)"
                Add-Wix4Hint -HintPath (Join-Path $dir.FullName 'wix.exe')
                Add-Wix4Hint -HintPath (Join-Path $dir.FullName 'bin\wix.exe')
            }
        }
    }

    foreach ($envVarName in @('WIX_BIN', 'WIX', 'WIX_HOME')) {
        $envVarValue = [Environment]::GetEnvironmentVariable($envVarName)
        if (-not [string]::IsNullOrWhiteSpace($envVarValue)) {
            Add-WixCandidate -CandidatePath $envVarValue -Source "env:$envVarName"
            Add-WixCandidate -CandidatePath (Join-Path $envVarValue 'bin') -Source "env:$envVarName\\bin"
            Add-Wix4Hint -HintPath (Join-Path $envVarValue 'wix.exe')
            Add-Wix4Hint -HintPath (Join-Path $envVarValue 'bin\wix.exe')
        }
    }

    $lightCmd = Get-Command light.exe -ErrorAction SilentlyContinue
    $candleCmd = Get-Command candle.exe -ErrorAction SilentlyContinue
    if ($null -ne $lightCmd) {
        Add-WixCandidate -CandidatePath (Split-Path -Parent $lightCmd.Source) -Source 'PATH:light.exe'
    }
    if ($null -ne $candleCmd) {
        Add-WixCandidate -CandidatePath (Split-Path -Parent $candleCmd.Source) -Source 'PATH:candle.exe'
    }

    $wixCmd = Get-Command wix.exe -ErrorAction SilentlyContinue
    if ($null -ne $wixCmd) {
        Add-Wix4Hint -HintPath $wixCmd.Source
    }

    foreach ($programFilesDir in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if (-not [string]::IsNullOrWhiteSpace($programFilesDir) -and (Test-Path -LiteralPath $programFilesDir)) {
            $wixDirs = @(Get-ChildItem -LiteralPath $programFilesDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^WiX Toolset v3' })
            foreach ($dir in $wixDirs) {
                Add-WixCandidate -CandidatePath (Join-Path $dir.FullName 'bin') -Source "programfiles:$($dir.Name)\bin"
                Add-WixCandidate -CandidatePath $dir.FullName -Source "programfiles:$($dir.Name)"
            }

            $wix4Dirs = @(Get-ChildItem -LiteralPath $programFilesDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^WiX Toolset v4' })
            foreach ($dir in $wix4Dirs) {
                Add-Wix4Hint -HintPath (Join-Path $dir.FullName 'bin\wix.exe')
                Add-Wix4Hint -HintPath (Join-Path $dir.FullName 'wix.exe')
            }
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-Wix3BinDirectory -PathToCheck $candidate.Path) {
            return [pscustomobject]@{
                BinPath   = $candidate.Path
                RootPath  = Split-Path -Parent $candidate.Path
                Source    = $candidate.Source
                Wix4Hints = @($wix4Hints)
            }
        }
    }

    return [pscustomobject]@{
        BinPath   = $null
        RootPath  = $null
        Source    = $null
        Wix4Hints = @($wix4Hints)
    }
}

function Resolve-WixToolset {
    param([Parameter(Mandatory=$true)][string]$ScriptRoot)

    $wixInfo = Find-WixToolset -ScriptRoot $ScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($wixInfo.BinPath)) {
        return $wixInfo
    }

    $message = @(
        'Para generar instaladores EXE/MSI, jpackage necesita WiX Toolset v3 (light.exe y candle.exe).',
        'No se encontro una instalacion compatible de WiX 3.',
        'Rutas buscadas por prioridad: repo portable (tools/ o third_party/), variables WIX/WIX_HOME/WIX_BIN, PATH y Program Files.',
        "Sugerencia: extrae WiX 3 portable en '$ScriptRoot\tools\wix\bin' o '$ScriptRoot\third_party\wix\bin'."
    )

    if ($wixInfo.Wix4Hints.Count -gt 0) {
        $message += 'Se detecto WiX 4, pero jpackage en Windows sigue requiriendo WiX 3 para EXE/MSI:'
        $message += ($wixInfo.Wix4Hints | ForEach-Object { " - $_" })
    }

    throw ($message -join [Environment]::NewLine)
}

function Get-ExecutableProductVersion {
    param([Parameter(Mandatory=$true)][string]$ExecutablePath)

    if (!(Test-Path -LiteralPath $ExecutablePath)) {
        throw "No existe el ejecutable: $ExecutablePath"
    }

    $versionInfo = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($ExecutablePath)
    foreach ($candidate in @($versionInfo.ProductVersion, $versionInfo.FileVersion)) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            return $candidate.Trim()
        }
    }

    return 'desconocida'
}

function Get-WixToolsetDetails {
    param([Parameter(Mandatory=$true)]$WixInfo)

    $lightPath = Join-Path $WixInfo.BinPath 'light.exe'
    $candlePath = Join-Path $WixInfo.BinPath 'candle.exe'
    $lightVersion = Get-ExecutableProductVersion -ExecutablePath $lightPath
    $candleVersion = Get-ExecutableProductVersion -ExecutablePath $candlePath
    $displayVersion = if ($lightVersion -eq $candleVersion) {
        $lightVersion
    } else {
        "light.exe=$lightVersion; candle.exe=$candleVersion"
    }

    return [pscustomobject]@{
        LightPath       = $lightPath
        CandlePath      = $candlePath
        LightVersion    = $lightVersion
        CandleVersion   = $candleVersion
        DisplayVersion  = $displayVersion
        VersionsMatch   = ($lightVersion -eq $candleVersion)
    }
}

function Get-InstallerTargetsDescription {
    param([Parameter(Mandatory=$true)][string]$InstallerType)

    switch ($InstallerType) {
        'exe'  { return 'EXE' }
        'msi'  { return 'MSI' }
        'both' { return 'EXE + MSI (una sola ejecucion)' }
        default { return 'ninguno' }
    }
}

function Invoke-WithWixEnvironment {
    param(
        [Parameter(Mandatory=$true)]$WixInfo,
        [Parameter(Mandatory=$true)][scriptblock]$ScriptBlock
    )

    $originalPath = $env:PATH
    $hadOriginalWix = Test-Path Env:WIX
    $originalWix = $env:WIX

    try {
        $pathEntries = @($env:PATH -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        if ($pathEntries -notcontains $WixInfo.BinPath) {
            if ([string]::IsNullOrWhiteSpace($originalPath)) {
                $env:PATH = $WixInfo.BinPath
            } else {
                $env:PATH = "$($WixInfo.BinPath);$originalPath"
            }
        }
        $env:WIX = $WixInfo.RootPath

        & $ScriptBlock
    } finally {
        $env:PATH = $originalPath
        if ($hadOriginalWix) {
            $env:WIX = $originalWix
        } else {
            Remove-Item Env:WIX -ErrorAction SilentlyContinue
        }
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
$jpackageCmd   = $null
$wixInfo       = $null
$wixDetails    = $null
$generatedInstallerPaths = @()

Write-Host "[build-release] Root: $scriptRoot"
Write-Host "[build-release] App:  $AppName"
Write-Host "[build-release] Out:  $releaseAppDir"
Write-Host "[build-release] InstallerType: $InstallerType"
Write-Host "[build-release] Targets instalador: $(Get-InstallerTargetsDescription -InstallerType $InstallerType)"

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
    Write-Host "[build-release] Copiando JAR a ${InputDir}: $MainJar"
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

if (-not $SkipJPackage) {
    $jpackageCmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($null -eq $jpackageCmd) {
        throw 'No se encontro jpackage en PATH.'
    }
}

if (-not $SkipJPackage -and $InstallerType -ne 'none') {
    $wixInfo = Resolve-WixToolset -ScriptRoot $scriptRoot
    $wixDetails = Get-WixToolsetDetails -WixInfo $wixInfo

    Write-Host "[build-release] WiX detectado: $($wixInfo.BinPath) [$($wixInfo.Source)]"
    Write-Host "[build-release] WiX version: $($wixDetails.DisplayVersion)"
    if (-not $wixDetails.VersionsMatch) {
        Write-Warning "WiX presenta versiones distintas entre light.exe y candle.exe. Se continuara usando la copia detectada en: $($wixInfo.BinPath)"
    }
}

# ── PASO 3: jpackage ─────────────────────────────────────────────────────────
if (-not $SkipJPackage) {
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
    if ($InstallerType -eq 'both') {
        Write-Host '[build-release] Generando instaladores EXE y MSI en una sola ejecucion...'
    }

    Invoke-WithWixEnvironment -WixInfo $wixInfo -ScriptBlock {
        if ($InstallerType -in @('exe', 'both')) {
            Write-Host '[build-release] Generando instalador EXE...'
            $generatedExePath = New-WindowsInstaller -JPackageExe $jpackageCmd.Source -ReleaseDir $releaseDirAbs -Name $AppName -Icon $iconPathAbs -Type 'exe' -AppDir $releaseAppDir
            $script:generatedInstallerPaths += $generatedExePath
            Write-Host "[build-release] Instalador EXE final: $generatedExePath"
        }
        if ($InstallerType -in @('msi', 'both')) {
            Write-Host '[build-release] Generando instalador MSI...'
            $generatedMsiPath = New-WindowsInstaller -JPackageExe $jpackageCmd.Source -ReleaseDir $releaseDirAbs -Name $AppName -Icon $iconPathAbs -Type 'msi' -AppDir $releaseAppDir
            $script:generatedInstallerPaths += $generatedMsiPath
            Write-Host "[build-release] Instalador MSI final: $generatedMsiPath"
        }
    }
}

# ── PASO 6: limpieza legacy ───────────────────────────────────────────────────
Remove-DirectoryIfExists -PathToRemove (Join-Path $scriptRoot 'jp-out')
Remove-DirectoryIfExists -PathToRemove (Join-Path $scriptRoot 'release-fixed')
Remove-DirectoryIfExists -PathToRemove (Join-Path $scriptRoot 'release-input')
Remove-DirectoryIfExists -PathToRemove (Join-Path $releaseDirAbs 'RetroEditor')

if (-not $KeepTarget) {
    Remove-DirectoryIfExists -PathToRemove (Join-Path $scriptRoot 'target')
}

if ($InstallerType -ne 'none') {
    foreach ($installerPath in $generatedInstallerPaths) {
        if (!(Test-Path -LiteralPath $installerPath)) {
            throw "No se encontro el instalador esperado: $installerPath"
        }
    }

    if ($InstallerType -in @('exe', 'both') -and -not ($generatedInstallerPaths | Where-Object { $_ -like '*.exe' })) {
        throw "No se registro el instalador EXE esperado en: $releaseDirAbs"
    }
    if ($InstallerType -in @('msi', 'both') -and -not ($generatedInstallerPaths | Where-Object { $_ -like '*.msi' })) {
        throw "No se registro el instalador MSI esperado en: $releaseDirAbs"
    }
}

Write-Host "[build-release] OK: build generado en release usando el icono permanente: $iconPathAbs"
