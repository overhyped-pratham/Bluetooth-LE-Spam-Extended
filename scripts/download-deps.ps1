<#
Download Gradle dependencies for the workspace.

Usage:
  powershell -ExecutionPolicy Bypass -File .\scripts\download-deps.ps1
  # Or provide explicit Java home:
  powershell -ExecutionPolicy Bypass -File .\scripts\download-deps.ps1 -JavaHome "C:\Program Files\Java\jdk-17"

This script will:
  - Verify Java is available (or use provided -JavaHome).
  - Run Gradle with --refresh-dependencies to download all required artifacts.
  - Attempt to assemble the debug APK which triggers most Gradle downloads.
#>

param(
    [string]$JavaHome
)

function Write-Err([string]$msg){ Write-Host "ERROR: $msg" -ForegroundColor Red }
function Write-Ok([string]$msg){ Write-Host "$msg" -ForegroundColor Green }

if ($JavaHome) {
    if (Test-Path $JavaHome) {
        Write-Ok "Using Java home: $JavaHome"
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
    } else {
        Write-Err "Provided JavaHome path does not exist: $JavaHome"
        exit 1
    }
}

if (-not $env:JAVA_HOME) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $javaCmd) {
        Write-Err "JAVA_HOME not set and 'java' not found on PATH. Install a JDK (17+) and set JAVA_HOME."
        Write-Host "Example: setx JAVA_HOME \"C:\\Program Files\\Java\\jdk-17\""
        exit 1
    } else {
        Write-Ok "Found java on PATH: $($javaCmd.Path)"
    }
} else {
    Write-Ok "JAVA_HOME = $env:JAVA_HOME"
}

if (-not (Test-Path .\gradlew.bat)) {
    Write-Err "gradlew.bat not found in workspace root. Run this script from the project root."
    exit 1
}

Write-Host "Refreshing Gradle dependencies (this may take a while)..."
& .\gradlew.bat --no-daemon --refresh-dependencies
$rc = $LASTEXITCODE
if ($rc -ne 0) {
    Write-Err "Gradle failed (exit code $rc). Fix Java/Gradle errors and retry."
    exit $rc
}

Write-Host "Assembling debug APK to ensure all app dependencies are downloaded..."
& .\gradlew.bat assembleDebug --no-daemon --refresh-dependencies
$rc = $LASTEXITCODE
if ($rc -ne 0) {
    Write-Err "Gradle assemble failed (exit code $rc)."
    exit $rc
}

Write-Ok "Dependencies downloaded and debug APK assembled. See app\build\outputs\apk\debug\ for artifacts."
exit 0
