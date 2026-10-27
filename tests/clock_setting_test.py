#!/usr/bin/env python3
"""«ساعة ٢٤» on the screensaver's side (ClockSetting.java): what the receiver accepts, and the settings fragment, which stays as it
was while the setting is off. Needs javac; without it, skipped with a note.   Run: python3 tests/clock_setting_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if not shutil.which("javac"):
    print("SKIP clock setting: no javac here")
    sys.exit(0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / "android/app/src/main/java/sa/urtv/screensaver/ClockSetting.java"),
                    str(ROOT / "tests/ClockSettingRun.java")], check=True)
    run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.screensaver.ClockSettingRun"], capture_output=True)
out = run.stdout.decode("utf-8")
print(out, end="")
sys.exit(1 if run.returncode != 0 or "FAIL" in out else 0)
