<#
==============================================================================
 SwiftPair Laptop BLE Advertiser Module (Native C# / WinRT)
 Transmits Microsoft Swift Pair BLE advertisements directly from laptop Bluetooth.
 No ESP32, Flipper Zero, or Android device required.
==============================================================================
#>

param(
    [string]$DeviceName = "Surface Ergonomic Keyboard",
    [int]$DurationSeconds = 10
)

$cSharpCode = @"
using System;
using System.Text;
using System.Threading;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Security.Cryptography;

public class SwiftPairLaptopBroadcaster
{
    public static void StartBroadcasting(string deviceName, int durationSeconds)
    {
        try
        {
            var publisher = new BluetoothLEAdvertisementPublisher();
            var mData = new BluetoothLEManufacturerData();
            mData.CompanyId = 0x0006; // Microsoft Corporation ID

            // Swift Pair payload header: 0x03 (Swift Pair), 0x00 (Subscenario), 0x80 (Flags)
            byte[] header = new byte[] { 0x03, 0x00, 0x80 };
            byte[] nameBytes = Encoding.UTF8.GetBytes(deviceName);
            byte[] payload = new byte[header.Length + nameBytes.Length];

            Buffer.BlockCopy(header, 0, payload, 0, header.Length);
            Buffer.BlockCopy(nameBytes, 0, payload, header.Length, nameBytes.Length);

            mData.Data = CryptographicBuffer.CreateFromByteArray(payload);
            publisher.Advertisement.ManufacturerData.Add(mData);

            Console.WriteLine("[+] SwiftPair Laptop Advertiser Module active.");
            Console.WriteLine("    Broadcasting target: " + deviceName);
            Console.WriteLine("    Company ID         : 0x0006 (Microsoft)");
            Console.WriteLine("    Payload Hex        : " + BitConverter.ToString(payload).Replace("-", ""));

            publisher.Start();
            Console.WriteLine("[+] BLE Publisher Started! Transmitting packets...");

            Thread.Sleep(durationSeconds * 1000);

            publisher.Stop();
            Console.WriteLine("[+] Broadcast stopped successfully.");
        }
        catch (Exception ex)
        {
            Console.WriteLine("[!] Error during BLE broadcasting: " + ex.Message);
        }
    }
}
"@

# Add assembly reference and compile type
Add-Type -TypeDefinition $cSharpCode -Language CSharp -IgnoreWarnings

# Invoke Swift Pair Broadcaster directly on laptop's Bluetooth adapter
[SwiftPairLaptopBroadcaster]::StartBroadcasting($DeviceName, $DurationSeconds)
