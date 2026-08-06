# Test script for WinRT IBuffer in PowerShell
Add-Type -AssemblyName System.Runtime.WindowsRuntime

[Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher, Windows.Devices.Bluetooth, ContentType = WindowsRuntime] | Out-Null
[Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData, Windows.Devices.Bluetooth, ContentType = WindowsRuntime] | Out-Null

$pub = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher
$mData = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData
$mData.CompanyId = 0x0006

$bytes = [byte[]]@(0x03, 0x00, 0x80, 0x53, 0x75, 0x72, 0x66, 0x61, 0x63, 0x65)
$buf = [System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions]::AsBuffer($bytes, 0, $bytes.Length)

$mData.Data = $buf
$pub.Advertisement.ManufacturerData.Add($mData)

Write-Host "Success! Manufacturer data count:" $pub.Advertisement.ManufacturerData.Count
$pub.Start()
Write-Host "Publisher Started successfully!"
Start-Sleep -Seconds 2
$pub.Stop()
Write-Host "Publisher Stopped successfully!"
