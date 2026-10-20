#!/usr/bin/env python3
"""Choose an installed iOS runtime matching the selected Xcode SDK release."""
import json
from pathlib import Path
import sys


def version(value):
    parts = str(value).split(".")
    if len(parts) not in (2, 3) or not all(part.isdecimal() for part in parts):
        raise ValueError("Expected a numeric iOS SDK/runtime version")
    return tuple(map(int, parts)) + (0,) * (3 - len(parts))


def select_simulator(inventory, sdk_version):
    family = version(sdk_version)[:2]
    runtimes = [runtime for runtime in inventory["runtimes"]
                if runtime.get("isAvailable") and ".iOS-" in runtime["identifier"]
                and version(runtime["version"])[:2] == family]
    if not runtimes:
        raise RuntimeError(f"No available iOS {family[0]}.{family[1]} Simulator runtime "
                           "matches the selected SDK. Install it in Xcode.")
    runtime = max(runtimes, key=lambda item: version(item["version"]))
    major, minor, patch = version(runtime["version"])
    encoded = (major << 16) | (minor << 8) | patch
    types = [device for device in inventory["devicetypes"]
             if device["name"].startswith("iPhone")
             and device.get("minRuntimeVersion", 0) <= encoded
             <= device.get("maxRuntimeVersion", 0xFFFFFFFF)]
    if not types:
        raise RuntimeError("No compatible iPhone device type for the selected SDK runtime")
    return types[-1], runtime


def main():
    if len(sys.argv) != 3:
        sys.exit("Usage: ios_simulator.py INVENTORY_JSON SDK_VERSION_FILE")
    try:
        inventory = json.loads(Path(sys.argv[1]).read_text())
        sdk = Path(sys.argv[2]).read_text().strip()
        device, runtime = select_simulator(inventory, sdk)
    except (RuntimeError, ValueError, KeyError, TypeError) as error:
        sys.exit(str(error))
    print(device["identifier"], runtime["identifier"])


if __name__ == "__main__":
    main()
