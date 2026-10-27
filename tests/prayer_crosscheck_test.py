#!/usr/bin/env python3
"""The prayer times against an independent calculation and against printed timetables.

tests/prayer_crosscheck.tsv holds the six times of every city in the app's list (Saudi Arabia, the Gulf, Egypt, Jordan, Syria, Lebanon,
Palestine, Morocco, Algeria, Tunisia) on 12 dates of 2026-2027, on the days the clocks change, on the Ramadan edges, with each country's method,
from tests/adhan_port.mjs (a port of the formulas of adhan-js 4.4.6, Meeus' full series, MIT) -- see tests/prayer_crosscheck_gen.mjs. Here:
  - the port reproduces the values adhan's own unit tests expect (tests/adhan_port_test.mjs);
  - the file is exactly what the generator writes, and holds every city of Cities.java;
  - the launcher's PrayerMath is within one minute of it for every time (tests/PrayerCrosscheckRun.java), and of the timetables four authorities
    printed (tests/prayer_published.tsv). The page's computePrayers is checked the same way by tests/prayer_crosscheck_page.mjs (run.sh).
No network. Needs node (the generator and the port) and javac (the launcher); a missing one is skipped with a note.   Run: python3 tests/prayer_crosscheck_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok


if shutil.which("node"):
    run = subprocess.run(["node", str(ROOT / "tests/adhan_port_test.mjs")], capture_output=True, text=True)
    print(run.stdout, end="")
    check("adhan port: self-test", run.returncode == 0, run.stderr)
    gen = subprocess.run(["node", str(ROOT / "tests/prayer_crosscheck_gen.mjs")], capture_output=True, text=True)
    stored = (ROOT / "tests/prayer_crosscheck.tsv").read_text(encoding="utf-8")
    check("prayer_crosscheck.tsv is exactly what tests/prayer_crosscheck_gen.mjs writes (a new city or a changed date needs it run again)",
          gen.returncode == 0 and gen.stdout == stored, gen.stderr[:300] or "differs")
else:
    print("SKIP port and generator: no node here")

rows = [l.split("\t") for l in (ROOT / "tests/prayer_crosscheck.tsv").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
cities = re.findall(r'\{"([^"]+)", "(-?[\d.]+)", "(-?[\d.]+)"(?:, "([^"]+)")?\}',
                    (ROOT / "android/app/src/main/java/sa/urtv/screensaver/Cities.java").read_text(encoding="utf-8"))
have = {}
for r in rows:
    if r[6] != "0":
        have.setdefault((r[1], r[0]), set()).add(r[4])
missing = [n for n, _, _, z in cities if len(have.get((n, z or "Asia/Riyadh"), ())) < 12]
check(f"every one of the {len(cities)} cities of Cities.java has at least 12 dates in the file", not missing and len(cities) > 300, missing[:5])
check("the dates include both Ramadans, the days the clocks change in Egypt, Lebanon, Palestine and Morocco, and 2026 and 2027",
      {"2026-02-20", "2027-02-09", "2026-04-24", "2026-03-29", "2026-03-28", "2026-02-15", "2026-10-29"} <= {r[4] for r in rows}
      and any(r[4].startswith("2027") for r in rows) and any(r[4].startswith("2026") for r in rows), "")

if shutil.which("javac"):
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / "android/launcher/src/main/java/sa/urtv/home/tilawah/PrayerMath.java"),
                        str(ROOT / "tests/PrayerCrosscheckRun.java"), str(ROOT / "tests/PrayerPublishedRun.java")], check=True)
        for cls, tsv in (("PrayerCrosscheckRun", "prayer_crosscheck.tsv"), ("PrayerPublishedRun", "prayer_published.tsv")):
            run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.tilawah." + cls, str(ROOT / "tests" / tsv)], capture_output=True)
            out = run.stdout.decode("utf-8")
            print(out, end="")
            check(cls + " exit code", run.returncode == 0 and "FAIL" not in out, run.stderr.decode("utf-8", "replace")[-300:])
else:
    print("SKIP launcher crosscheck: no javac here")
sys.exit(1 if failed else 0)
