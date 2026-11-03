import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ios_simulator import select_simulator


def runtime(value, available=True, platform="iOS"):
    return {"version": value, "isAvailable": available,
            "identifier": f"com.apple.CoreSimulator.SimRuntime.{platform}-{value.replace('.', '-')}"}


def inventory(*runtimes):
    return {"runtimes": list(runtimes), "devicetypes": [
        {"name": "iPhone Fixture", "identifier": "fixture-iphone"}]}


class SimulatorSelectionTest(unittest.TestCase):
    def test_sdk_runtime_wins_over_newest_installed_major(self):
        device, selected = select_simulator(inventory(runtime("26.2"), runtime("18.5")), "18.5")
        self.assertEqual("18.5", selected["version"])
        self.assertEqual("fixture-iphone", device["identifier"])

    def test_highest_patch_in_sdk_release_is_selected(self):
        _, selected = select_simulator(inventory(runtime("18.5"), runtime("18.5.1"), runtime("18.6")), "18.5.0")
        self.assertEqual("18.5.1", selected["version"])

    def test_unavailable_match_never_falls_back_to_newer_runtime(self):
        with self.assertRaisesRegex(RuntimeError, "No available iOS 18.5"):
            select_simulator(inventory(runtime("18.5", False), runtime("26.2")), "18.5")

    def test_device_type_must_support_selected_runtime(self):
        data = inventory(runtime("18.5"))
        data["devicetypes"] += [
            {"name": "iPhone Too New", "identifier": "new", "minRuntimeVersion": 26 << 16},
            {"name": "iPhone Too Old", "identifier": "old", "maxRuntimeVersion": (18 << 16) | (4 << 8)},
        ]
        device, _ = select_simulator(data, "18.5")
        self.assertEqual("fixture-iphone", device["identifier"])

    def test_no_compatible_iphone_is_refused(self):
        data = inventory(runtime("18.5"))
        data["devicetypes"] = [{"name": "iPad Fixture", "identifier": "ipad"}]
        with self.assertRaisesRegex(RuntimeError, "No compatible iPhone"):
            select_simulator(data, "18.5")

    def test_other_platform_is_not_an_ios_match(self):
        with self.assertRaisesRegex(RuntimeError, "No available iOS 18.5"):
            select_simulator(inventory(runtime("18.5", platform="tvOS")), "18.5")

    def test_invalid_sdk_version_is_refused(self):
        with self.assertRaises(ValueError):
            select_simulator(inventory(runtime("18.5")), "not-a-version")

    def run_cli(self, data):
        with tempfile.TemporaryDirectory(prefix="bliss simulator ") as directory:
            path = Path(directory)
            (path / "inventory.json").write_text(json.dumps(data))
            (path / "sdk.txt").write_text("18.5\n")
            return subprocess.run([sys.executable, "-B", str(Path(__file__).resolve().parents[1] / "ios_simulator.py"),
                                   str(path / "inventory.json"), str(path / "sdk.txt")],
                                  capture_output=True, text=True, check=False)

    def test_cli_outputs_only_selected_identifiers(self):
        result = self.run_cli(inventory(runtime("26.2"), runtime("18.5")))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("fixture-iphone com.apple.CoreSimulator.SimRuntime.iOS-18-5\n", result.stdout)
        self.assertEqual("", result.stderr)

    def test_cli_fails_clearly_when_matching_runtime_is_missing(self):
        result = self.run_cli(inventory(runtime("26.2")))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("No available iOS 18.5", result.stderr)
        self.assertEqual("", result.stdout)


if __name__ == "__main__":
    unittest.main()
