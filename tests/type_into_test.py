#!/usr/bin/env python3
"""Typing from the phone into other apps (the accessibility service): the pure choice (TypeInto.java on a fake screen), then source checks that
it stays inside its limits: off until the owner turns the service on, the family lock and kids mode apply, no password fields, nothing of the
screen is logged, stored or sent back (the phone gets a fixed word), and it is written down plainly in the docs and the service's description.
Needs javac for the Java cases. Run: python3 tests/type_into_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok


def text(p):
    return (ROOT / p).read_text(encoding="utf-8")


HOME = "android/launcher/src/main/java/sa/urtv/home/"
if shutil.which("javac") and shutil.which("java"):
    tmp = tempfile.mkdtemp()
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / HOME / "TypeInto.java"), str(ROOT / "tests/TypeIntoRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.TypeIntoRun"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        if line.startswith(("PASS ", "FAIL ")):
            print(line)
            failed += line.startswith("FAIL ")
    check("every Java case ran", "ALL PASS" in out or "FAILED" in out, out[-200:])
else:
    print("SKIP the Java cases: no javac")

svc = text(HOME + "SleepService.java")
ti = text(HOME + "TypeInto.java")
act = text(HOME + "phone/PhoneActions.java")
xml = text("android/launcher/src/main/res/xml/sleep_service.xml")
body = svc.split("static String typeText(")[1].split("    /** Presses Back")[0]
check("the service listens to NO events (no accessibilityEventTypes) and reads window content only for this", "android:accessibilityEventTypes" not in xml and 'canRetrieveWindowContent="true"' in xml
      and "onAccessibilityEvent(AccessibilityEvent e) { }" in svc)
check("it only types when the service is on, and the family lock and kids mode are asked first (the same policy as opening the app)",
      "TypeInto.allowed(s != null, pkg != null && Parental.needsPin(c, pkg), pkg != null && !ParentalPolicy.visible(st, pkg))" in body and body.index("TypeInto.allowed(") < body.index("TypeInto.pick("))
check("a password field is never typed into", "TypeInto.fieldProblem(picked)" in body and "PASSWORD" in body)
check("nothing of the screen is logged or stored (no Log, no preferences, no file, no broadcast in the typing code)", not re.search(r"\bLog\.|SharedPreferences|getSharedPreferences|FileOutputStream|sendBroadcast|setPrimaryClip|toString\(\)\.getBytes", body + ti))
check("what the field holds is used only to build the new text, in one expression, and never returned", body.count("field.getText()") == 1 and "return now" not in body and "return out" not in body)
check("the answer is one of a few fixed words", re.search(r'TYPED = "typed_other", TYPED_NO_SEARCH = "typed_other_nosearch", OFF = "service_off", LOCKED = "locked",\s+NO_FIELD = "no_field", PASSWORD = "password_field", FAILED = "failed";', svc) is not None
      and not re.search(r"return [a-z]+\.get(Text|ContentDescription|Package)", body))
check("the phone path: the service off is a failure the page explains (nothing is copied), locked and password stay failures, only «no field» falls back to the clipboard",
      'default: return Result.fail(r);' in act and 'case "no_field": break;' in act and 'if (Foreground.current() == null)' in act)
check("the node walk is bounded (depth and count)", "depth >= 40" in svc and "new int[] {3000}" in svc)
check("search presses the keyboard's action only on Android 11+ and only when asked", "SDK_INT < 30" in body and "ACTION_IME_ENTER" in body and body.index("if (!search) return TYPED;") < body.index("ACTION_IME_ENTER"))
for d in ("docs/FEATURES.md", "docs/SETTINGS.md"):
    t = text(d)
    check(f"{d} says plainly what the service sees and keeps", "الكتابة في التطبيقات الأخرى" in t or "typing into other apps" in t.lower())
check("the service's own description (shown in the TV's accessibility settings) says it too",
      "لا يقرأ الشاشة إلا لحظة وصول نص" in text("android/launcher/src/main/res/values/strings.xml") and "لا يحفظ ولا يسجّل ولا يرسل" in text("android/launcher/src/main/res/values/strings.xml"))
sys.exit(1 if failed else 0)
