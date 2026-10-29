#!/usr/bin/env python3
"""Left/right on the remote never goes the wrong way on our screens. Reported on a Sony and a TCL: «if I hold right it goes left».
What the code shows (checked here from the source, since the focus walk needs Android):
 1. the surah grid: at its two ends the first press used to fall to Android's own search, which counts cells the other way
    (CellWalk, tests/cell_walk_cases.tsv);
 2. the screens built in code (no layoutDirection) took the TV's language as their direction, but their nextFocus ids assume one: the
    rows of «ترتيب المفضلة», «المهام» and «الرقابة الأبوية» assume left-to-right, the phone screen right-to-left, so on a TV set to the
    other language their ◀▶ went the opposite way. Each now pins its direction.
Not changed, and why: the home screen forces right-to-left and walks its rows by hand (MainActivity.moveFocusSideways); its explicit
nextFocus ids are written in row order, but no focus can reach them (every left/right on a row is consumed by the walk; the cards that are
not in a row point at themselves).   Run: python3 tests/dpad_rtl_test.py"""
import re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/urtv/home"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

for f, d in (("DockActivity", "LTR"), ("TasksActivity", "LTR"), ("ParentalActivity", "LTR"), ("PhoneActivity", "RTL")):
    s = (HOME / f"{f}.java").read_text(encoding="utf-8")
    m = re.search(rf"scroll\.setLayoutDirection\(View\.LAYOUT_DIRECTION_{d}\);", s)
    check(f"{f}: its direction is pinned to {d} before setContentView", bool(m) and m.start() < s.index("setContentView(scroll)"))
surah = (HOME / "tilawah/SurahActivity.java").read_text(encoding="utf-8")
check("SurahActivity: a first press at an end of the grid is consumed, not handed to Android's search", "CellWalk.consume(false, e.getRepeatCount(), onCell())" in surah)
check("SurahActivity: the walk uses CellWalk for the next cell", "CellWalk.next(position, grid.getCount(), right)" in surah)
main = (HOME / "MainActivity.java").read_text(encoding="utf-8")
check("the home screen still forces right-to-left and walks its rows by hand", "root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL)" in main and "moveFocusSideways(" in main)
if not shutil.which("javac"):
    print("SKIP cell walk: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/cell_walk_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(HOME / "tilawah/CellWalk.java"), str(ROOT / "tests/CellWalkRun.java")], check=True)
    out = subprocess.run(["java", "-cp", tmp, "sa.urtv.home.tilawah.CellWalkRun", str(ROOT / "tests/cell_walk_cases.tsv")],
                         check=True, capture_output=True).stdout.decode("utf-8").split("\n")
for c, g in zip(cases, out):
    want, note = (c[4], c[5]) if c[0] == "next" else (c[4], c[5])
    check(f"cell walk {c[0]} {c[1:4]}: {note}", g == want, f"{g!r}, wanted {want!r}")
check("an answer for every case", len(out) - 1 >= len(cases), (len(out), len(cases)))
sys.exit(1 if failed else 0)
