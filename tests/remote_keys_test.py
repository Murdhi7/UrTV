#!/usr/bin/env python3
"""The shortcut keys of the remote on our home screen (RemoteKeys.java, tests/remote_keys_cases.tsv): YouTube/Netflix (BUTTON_3/4), the
settings key, the star key (scan code 578), acting on a first press only and consuming its repeats and release; an app that is not installed
or a key that is not mapped is left alone. Then the wiring, read from the source: MainActivity asks the adhan rule first and RemoteKeys
second, and opens through the family lock.   Run: python3 tests/remote_keys_test.py"""
import re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/urtv/home"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

main = (HOME / "MainActivity.java").read_text(encoding="utf-8")
keys = (HOME / "RemoteKeys.java").read_text(encoding="utf-8")
body = main.split("public boolean dispatchKeyEvent(KeyEvent e) {")[1].split("\n    }\n")[0] if "public boolean dispatchKeyEvent(KeyEvent e) {" in main else ""
check("dispatchKeyEvent asks the adhan rule first, then RemoteKeys, then the Menu key",
      "adhanTakes(e)" in body and "remoteKeys.decide(" in body and "KEYCODE_MENU" in body
      and body.index("adhanTakes(e)") < body.index("remoteKeys.decide(") < body.index("KEYCODE_MENU"), body[:300])
check("the key numbers are written out (no Android in RemoteKeys)", "import android" not in keys and "KeyEvent." not in re.sub(r"//.*|/\*.*?\*/", "", keys, flags=re.S))
check("the apps open through the family lock (REMOTE_APP / REMOTE_SETTINGS), the star through the Menu key's path",
      "ParentalPolicy.REMOTE_APP" in main and "ParentalPolicy.REMOTE_SETTINGS" in main and "ParentalPolicy.MENU_KEY" in body)
check("an app hidden by kids mode counts as not installed (the key then does nothing)", "ParentalPolicy.visible(" in main.split("Set<String> remoteInstalled()")[1][:600] if "Set<String> remoteInstalled()" in main else False)
check("no GLOBAL_BUTTON receiver (the system sends that broadcast to a named component only)",
      "GLOBAL_BUTTON" not in (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8"))
if not shutil.which("javac"):
    print("SKIP remote keys: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/remote_keys_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(HOME / "RemoteKeys.java"), str(ROOT / "tests/RemoteKeysRun.java")], check=True)
    out = subprocess.run(["java", "-cp", tmp, "sa.urtv.home.RemoteKeysRun", str(ROOT / "tests/remote_keys_cases.tsv")],
                         check=True, capture_output=True).stdout.decode("utf-8").split("\n")
for c, g in zip(cases, out):
    if c[0] == "reset": continue
    check(f"remote keys: {c[7]}", g == c[6], f"{g!r}, wanted {c[6]!r}")
check("an answer for every case", len(out) - 1 >= len(cases), (len(out), len(cases)))
sys.exit(1 if failed else 0)
