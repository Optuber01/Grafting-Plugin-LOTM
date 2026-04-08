[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-JavaTool {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ToolName,
        [string]$JavaHomePath
    )

    if ($JavaHomePath) {
        $candidate = Join-Path $JavaHomePath "bin/$ToolName.exe"
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
        throw "JAVA_HOME is set but $candidate does not exist."
    }

    $command = Get-Command "$ToolName.exe" -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "JAVA_HOME is not set and $ToolName.exe was not found on PATH."
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Executable,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed with exit code ${LASTEXITCODE}: $Executable $($Arguments -join ' ')"
        }
    } finally {
        Pop-Location
    }
}

function Write-TextNoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Write-LinesNoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string[]]$Lines
    )

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $Lines, $utf8NoBom)
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildFile = Join-Path $repoRoot 'build.gradle'
$buildText = Get-Content $buildFile -Raw
$versionMatch = [regex]::Match($buildText, "(?m)^\s*version\s*=\s*'([^']+)'")
if (-not $versionMatch.Success) {
    throw 'Could not parse project version from build.gradle.'
}

$projectVersion = $versionMatch.Groups[1].Value
$javaExe = Resolve-JavaTool -ToolName 'java' -JavaHomePath $JavaHome
$javacExe = Resolve-JavaTool -ToolName 'javac' -JavaHomePath $JavaHome
$jarExe = Resolve-JavaTool -ToolName 'jar' -JavaHomePath $JavaHome

$javaVersionLines = & $javaExe -version 2>&1
$javaVersionLine = ($javaVersionLines | Select-Object -First 1)
if ($javaVersionLine -notmatch 'version "21(\.|")') {
    throw "Java 21 is required. Found: $javaVersionLine"
}

$paperApiDir = Join-Path $repoRoot '.tools/paper-api'
$paperApiJar = Get-ChildItem $paperApiDir -Filter 'paper-api-*.jar' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -First 1
if (-not $paperApiJar) {
    throw "No Paper API jar was found under $paperApiDir."
}

$dependencyDir = Join-Path $repoRoot '.tools/deps'
$dependencyJars = Get-ChildItem $dependencyDir -Filter '*.jar' -ErrorAction SilentlyContinue | Sort-Object Name
if ($dependencyJars.Count -eq 0) {
    throw "No dependency jars were found under $dependencyDir."
}

$buildRoot = Join-Path $repoRoot 'build/offline-verify'
$mainClasses = Join-Path $buildRoot 'classes/main'
$testClasses = Join-Path $buildRoot 'classes/test'
$mainResources = Join-Path $buildRoot 'resources/main'
$testResources = Join-Path $buildRoot 'resources/test'
$libsDir = Join-Path $buildRoot 'libs'
$tmpDir = Join-Path $buildRoot 'tmp'

if (Test-Path $buildRoot) {
    Remove-Item $buildRoot -Recurse -Force
}

New-Item -ItemType Directory -Force $mainClasses | Out-Null
New-Item -ItemType Directory -Force $testClasses | Out-Null
New-Item -ItemType Directory -Force $mainResources | Out-Null
New-Item -ItemType Directory -Force $testResources | Out-Null
New-Item -ItemType Directory -Force $libsDir | Out-Null
New-Item -ItemType Directory -Force $tmpDir | Out-Null

$mainSourceDir = Join-Path $repoRoot 'src/main/java'
$testSourceDir = Join-Path $repoRoot 'src/test/java'
$mainSourceFiles = Get-ChildItem $mainSourceDir -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
$testSourceFiles = Get-ChildItem $testSourceDir -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }

if ($mainSourceFiles.Count -eq 0) {
    throw 'No main Java sources were found.'
}
if ($testSourceFiles.Count -eq 0) {
    throw 'No test Java sources were found.'
}

$mainArgsFile = Join-Path $tmpDir 'main-sources.txt'
$testArgsFile = Join-Path $tmpDir 'test-sources.txt'
Write-LinesNoBom -Path $mainArgsFile -Lines $mainSourceFiles
Write-LinesNoBom -Path $testArgsFile -Lines $testSourceFiles

$mainResourceSource = Join-Path $repoRoot 'src/main/resources'
if (Test-Path $mainResourceSource) {
    Copy-Item (Join-Path $mainResourceSource '*') $mainResources -Recurse -Force
}

$pluginYml = Join-Path $mainResources 'plugin.yml'
if (Test-Path $pluginYml) {
    $pluginYmlText = (Get-Content $pluginYml -Raw).Replace('${version}', $projectVersion)
    Write-TextNoBom -Path $pluginYml -Content $pluginYmlText
}

$testResourceSource = Join-Path $repoRoot 'src/test/resources'
if (Test-Path $testResourceSource) {
    Copy-Item (Join-Path $testResourceSource '*') $testResources -Recurse -Force
}

$compileClasspath = (@($paperApiJar.FullName) + $dependencyJars.FullName) -join ';'
Invoke-Checked -Executable $javacExe -Arguments @('--release', '21', '-encoding', 'UTF-8', '-cp', $compileClasspath, '-d', $mainClasses, "@$mainArgsFile") -WorkingDirectory $repoRoot

$testCompileClasspath = (@($mainClasses, $mainResources, $paperApiJar.FullName) + $dependencyJars.FullName) -join ';'
Invoke-Checked -Executable $javacExe -Arguments @('--release', '21', '-encoding', 'UTF-8', '-cp', $testCompileClasspath, '-d', $testClasses, "@$testArgsFile") -WorkingDirectory $repoRoot

$pluginJar = Join-Path $libsDir "GraftingPlugin-$projectVersion.jar"
Invoke-Checked -Executable $jarExe -Arguments @('--create', '--file', $pluginJar, '-C', $mainClasses, '.', '-C', $mainResources, '.') -WorkingDirectory $repoRoot

$runtimeClasspathParts = @($testClasses, $mainClasses, $mainResources)
if (Test-Path $testResourceSource) {
    $runtimeClasspathParts += $testResources
}
$runtimeClasspathParts += $paperApiJar.FullName
$runtimeClasspathParts += $dependencyJars.FullName
$runtimeClasspath = $runtimeClasspathParts -join ';'
Invoke-Checked -Executable $javaExe -Arguments @('-ea', '-cp', $runtimeClasspath, 'com.graftingplugin.tests.TestHarness') -WorkingDirectory $repoRoot

Write-Host "Offline verification passed."
Write-Host "Java runtime: $javaVersionLine"
Write-Host "Paper API jar: $($paperApiJar.Name)"
Write-Host "Plugin jar: $pluginJar"
