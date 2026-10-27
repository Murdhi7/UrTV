#!/usr/bin/env python3
"""«مركز التحكم» (the control center): the text and the rules on a plain JVM (tests/ControlTextRun.java: what «حرّر الذاكرة» says and who it may stop,
the speed test's answer, the network and the sound output tiles), the speed test against a server of its own on this machine
(tests/SpeedTestRun.java: one host constant, a bounded read, cancel at once), and what can only be read from the source: the pill and the panel are
behind the family lock's «@center», every tile that opens something keeps its own lock, no network is touched before the person presses the speed
test, the panel closes with the home (and stops the test), and the manifest asks only for the normal permission.
Run: python3 tests/control_center_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/urtv/home"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

def src(n): return (HOME / n).read_text(encoding="utf-8")
main, center, text, speed, policy = src("MainActivity.java"), src("ControlCenter.java"), src("ControlText.java"), src("SpeedTest.java"), src("ParentalPolicy.java")
manifest = (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

# ---- the wiring
check("the pill opens the panel only through the family lock, with the @center path", "guarded(ParentalPolicy.PILL_CENTER, null, ControlText.NAME, () -> control.open(v))" in main)
check("@center is a lock target: a pill, a path, a valid name, and it is on the lock screen's list",
      'CENTER = "@center"' in policy and "SLEEP, CENTER}" in policy and "PILL_CENTER" in policy and "case ParentalPolicy.CENTER" in src("ParentalActivity.java"))
check("the sleep timer, the Wi-Fi, the screensaver and the settings keep their own lock paths",
      all(p in main + center for p in ("ParentalPolicy.PILL_SLEEP", "ParentalPolicy.PILL_WIFI", "ParentalPolicy.PILL_SAVER", "ParentalPolicy.PILL_SAVER_SETTINGS", "ParentalPolicy.PILL_TV_SETTINGS")))
check("the panel starts nothing by itself: every launch goes through the host's launchGuarded/guarded paths (no startActivity in it)",
      "startActivity(" not in center and "host.launchGuarded(" in center)
check("the sound output opens an intent that resolves, falling back to the TV's settings",
      "resolveActivity(sound, 0) != null ? sound : new Intent(Settings.ACTION_SETTINGS)" in center)
check("the sleep timer's tile and «أطفئ الآن» end the same way (SleepTimer.turnOff: the service, else sound off and the screensaver)",
      "SleepTimer.turnOff(act)" in center and "static String turnOff(Context c)" in src("SleepTimer.java") and "String done = turnOff(c);" in src("SleepTimer.java")
      and "SleepService.sleepNow()" in src("SleepTimer.java").split("static String turnOff")[1])
check("«أطفئ الآن» says how to turn the service on when it is off, and a long OK opens that screen behind the sleep lock",
      "SleepService.enabled()" in center and "ParentalPolicy.SLEEP_ACCESSIBILITY" in center)
check("kids mode: no pill and no panel (the row's own check, and the panel closes when it goes)", "private boolean centerWanted() { return !Parental.state(this).kids; }" in main
      and "control.dismiss();" in main.split("private void applyCenterPill")[1].split("wireFocus();")[0])
check("the pill follows the row's walk: it is in `pills` and in the row's children together, at the same place",
      "pills.add(at, centerPill);" in main and "pillRow.addView(centerPill, at + 1," in main)
check("the home closing or coming back closes the panel (its test stops with it)", main.count("control.dismiss()") >= 3 and "speed.cancel()" in center.split("setOnDismissListener")[1])
check("the panel is for the remote: no touch handling beyond the outside dismiss", "setOnTouchListener" not in center and "setOutsideTouchable(true)" in center and "new PopupWindow(panel, -2, -2, true)" in center)
check("RTL: the panel's own direction is set, and the tiles walk right to left with explicit focus",
      "LAYOUT_DIRECTION_RTL" in center and "setNextFocusRightId(col == 0" in center and "setNextFocusLeftId(" in center)
check("focus goes back to the pill when it closes, and the first tile has it when it opens", "anchor.requestFocus()" in center and "tiles.get(0).requestFocus()" in center)

# ---- no traffic before OK
check("the only network code is the speed test's, and it is started by the tile's OK on a worker thread",
      "new SpeedTest()" in center and "HttpURLConnection" not in center and "new Thread(" in center.split("private void speedTest")[1]
      and "test.downloadMbps()" in center.split("new Thread(")[2])
check("nothing in the panel's opening reads the network: no SpeedTest, no thread before a press",
      "new SpeedTest()" not in center.split("void open(View anchor)")[1].split("// ----")[0] and "new Thread(" not in center.split("void open(View anchor)")[1].split("// ----")[0])
check("the speed test's host is named once, in SpeedTest, and the panel does not name a URL", speed.count("speed.cloudflare.com") == 1 and "cloudflare" not in center + main + text and "://" not in center.split("*/", 1)[0].split("//")[-1] + center.split("*/", 1)[1])
check("the speed test is bounded: a few MB, a measuring time, connect and read timeouts, no cache",
      "BYTES = 6_000_000" in speed and "MEASURE_NANOS" in speed and "setConnectTimeout" in speed and "setReadTimeout" in speed and "setUseCaches(false)" in speed)
check("nothing is uploaded: no output stream, no POST", "getOutputStream" not in speed and "setDoOutput" not in speed and "POST" not in speed)
check("the talkies credit headers are in the files that adapt it", all("rajupp/talkies" in s.split("*/")[0] for s in (speed, center)))
check("«حرّر الذاكرة»: only killBackgroundProcesses, only for ControlText.killable, never ours", "killBackgroundProcesses(pkg)" in center and "ControlText.killable(pkg, system, keyboards.contains(pkg))" in center
      and "forceStopPackage" not in center and 'startsWith("sa.urtv.")' in text)
check("the manifest asks for the normal KILL_BACKGROUND_PROCESSES, and no QUERY_ALL_PACKAGES, no new accessibility capability",
      "android.permission.KILL_BACKGROUND_PROCESSES" in manifest and "QUERY_ALL_PACKAGES" not in manifest and manifest.count("android.accessibilityservice.AccessibilityService") == 2)   # the sleep service and the phone remote's typing service; the Control Center adds none
check("every number goes through Digits.of (no String.valueOf of a number into a label), every format has Locale.ROOT",
      "String.valueOf" not in center and all("Locale.ROOT" in l for l in (text + speed).split("\n") if "String.format" in l))
check("no new receiver, service or alarm for it", not re.search(r"registerReceiver|startService|AlarmManager|JobScheduler|postDelayed", center))
check("the phone remote and the accessibility service are untouched by it", "PhoneApi" not in center and "sleep_service" not in center)

# ---- the logic
if not shutil.which("javac"):
    print("SKIP control center logic: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/control_center_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(HOME / "ControlText.java"), str(HOME / "PillText.java"), str(HOME / "DigitsText.java"),
                    str(HOME / "SpeedTest.java"), str(ROOT / "tests/ControlTextRun.java"), str(ROOT / "tests/SpeedTestRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.ControlTextRun", str(ROOT / "tests/control_center_cases.tsv")],
                         check=True, capture_output=True).stdout
    got = out.decode("utf-8").split("\n")
    for c, g in zip(cases, got):
        want = c[5]
        # the digits of the answers in the table are written hindi for reading; the class writes western ones
        g_hindi = g.translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))
        check(f"control center {c[0]}: {c[6] if len(c) > 6 else ''}", g == want or (c[0] in ("freed", "mbps") and g_hindi.replace(".", "٫") == want) or (c[0] == "freed" and g_hindi == want), f"{g!r}, wanted {want!r}")
    check("every case answered", len(got) > len(cases), (len(got), len(cases)))
    r = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.SpeedTestRun"], capture_output=True, text=True)
    for l in r.stdout.splitlines():
        check("speed test: " + l.split(" ", 1)[1], l.startswith("ok "))
    check("speed test run ended well", r.returncode == 0, r.stderr[-300:])
sys.exit(1 if failed else 0)
