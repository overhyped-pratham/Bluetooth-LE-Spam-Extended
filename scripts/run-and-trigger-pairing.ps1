param(
    [string]$JavaHome,
    [string]$DeviceSerial = "",
    [ValidateSet('apple','android','both','all')][string]$Target = 'all',
    [int]$IntervalMs = 3000,
    [switch]$SkipBuild
)

function Write-Err([string]$m){ Write-Host "ERROR: $m" -ForegroundColor Red }
function Write-Ok([string]$m){ Write-Host "$m" -ForegroundColor Green }

if ($JavaHome) {
    if (Test-Path $JavaHome) {
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
        Write-Ok "Using JAVA_HOME=$JavaHome"
    } else {
        Write-Err "Provided JavaHome does not exist: $JavaHome"
        exit 1
    }
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Err "adb not found on PATH. Install Android platform-tools and add to PATH."
    exit 1
}

# Build APK
if (-not $SkipBuild) {
    if (-not (Test-Path .\gradlew.bat)) { Write-Err "gradlew.bat not found. Run from project root."; exit 1 }
    Write-Host "Building debug APK..."
    & .\gradlew.bat assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { Write-Err "Gradle build failed"; exit $LASTEXITCODE }
}

# Verify APK
$apk = Get-Item -Path .\app\build\outputs\apk\debug\app-debug.apk -ErrorAction SilentlyContinue
if (-not $apk) { Write-Err "APK not found. Build failed or path changed."; exit 1 }

# Check connected devices
Write-Host "Checking connected adb devices..."
$devicesRaw = adb devices
if ($devicesRaw -notmatch "device") {
    Write-Err "No adb device detected. Connect a device or start an emulator."
    adb devices
    exit 1
}

if ($DeviceSerial) {
    $installTarget = "$DeviceSerial"
} else {
    # Use first non-empty device
    $first = adb devices | Select-String -Pattern "\tdevice" | Select-Object -First 1
    if (-not $first) { Write-Err "No device ready"; exit 1 }
    $installTarget = ($first.ToString().Split("`t"))[0]
}

Write-Host "Installing APK to $installTarget..."
adb -s $installTarget install -r .\app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { Write-Err "adb install failed"; exit $LASTEXITCODE }

# Package name for debug build
$PKG = 'de.simon.dankelmann.bluetoothlespam.debug'

# Grant permissions
Write-Host "Granting runtime permissions (debug build)..."
adb -s $installTarget shell pm grant $PKG android.permission.BLUETOOTH_SCAN
adb -s $installTarget shell pm grant $PKG android.permission.BLUETOOTH_CONNECT
adb -s $installTarget shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb -s $installTarget shell pm grant $PKG android.permission.POST_NOTIFICATIONS

# Start app main activity to ensure scan service can run
Write-Host "Starting main activity..."
adb -s $installTarget shell am start -n ${PKG}/de.simon.dankelmann.bluetoothlespam.MainActivity

Start-Sleep -Seconds 2

# Optionally start the scan foreground service
Write-Host "Starting BLE scan foreground service (if available)..."
$svcOutput = adb -s $installTarget shell am start-foreground-service -n ${PKG}/de.simon.dankelmann.bluetoothlespam.Services.BluetoothLeScanForegroundService
if ($LASTEXITCODE -ne 0) {
    Write-Host "Service start may not be exported or supported; opening app should start scanning."
    Write-Host $svcOutput
}

Start-Sleep -Seconds 1

# Trigger continuous pairing broadcast
Write-Host "Triggering continuous pairing (target=$Target, interval_ms=$IntervalMs)..."
adb -s $installTarget shell am broadcast -a de.simon.dankelmann.bluetoothlespam.ACTION_PAIR_ALL -n ${PKG}/.Services.PairAllReceiver --ei interval_ms $IntervalMs --es target $Target

Write-Ok "Triggered continuous pairing. Watch device for pairing dialogs and logs." 
