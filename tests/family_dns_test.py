#!/usr/bin/env python3
"""«حماية العائلة (DNS)» (FamilyDns.java, the launcher): nothing changes without the grant or a route, the originals are recorded first and
put back exactly (a value that was not set is deleted), a broken internet or a value that is not taken reverts at once, a second "on" keeps
the first record, an unreadable value is never recorded as "not set". Needs javac.
Run: python3 tests/family_dns_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


if not shutil.which("javac"):
    print("SKIP family dns: no javac here")
    sys.exit(0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / "android/launcher/src/main/java/sa/urtv/home/FamilyDns.java"),
                    str(ROOT / "tests/FamilyDnsRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.FamilyDnsRun"],
                         check=True, capture_output=True, text=True, encoding="utf-8").stdout.splitlines()
check("one place for the host and the port", out[0] == "family.adguard-dns.com 853", out[0])
check("no grant: NEEDS_GRANT, nothing written or recorded", out[1] == "NEEDS_GRANT 0 opportunistic/null true", out[1])
check("no route to the service: NO_ROUTE, nothing changed, nothing recorded", out[2] == "NO_ROUTE 0 opportunistic/null true", out[2])
check("on then off: the exact originals are back, the record is gone", out[3] == "ENABLED hostname/family.adguard-dns.com RESTORED hostname/dns.example true", out[3])
check("originals that were not set are deleted on off, not written as defaults", out[4] == "hostname/family.adguard-dns.com false false true", out[4])
check("internet breaks: REVERTED at once, a null original is deleted", out[5] == "REVERTED off/null false true", out[5])
check("a second on keeps the first record (not our own values); off puts the real originals back",
      out[6] == "ENABLED opportunistic x.example" and out[7] == "opportunistic/x.example", out[6] + " / " + out[7])
check("an unreadable value is not recorded as null: nothing written, no record", out[8] == "UNREADABLE_VALUE 0 true", out[8])
check("a value that is not taken: NOT_TAKEN, originals back, record cleared", out[9] == "NOT_TAKEN opportunistic/keep.example true", out[9])
check("off with no record: NOTHING_TO_RESTORE and nothing touched", out[10] == "NOTHING_TO_RESTORE 0 hostname/other.example", out[10])
check("status: off, on (by us), unknown when unreadable; on by someone else is left alone",
      out[11] == "off (none) | on (us) | unknown (none) | on (other) | ALREADY_ON_BY_OTHER 0 true", out[11])
check("a record that cannot be saved: nothing written", out[12] == "NOT_RECORDED 0", out[12])
check("Arabic copy for the row", out[13] == "ما قدرنا نوصل لخدمة الحماية، ما تغيّر شي | الإنترنت وقف فرجّعنا الإعداد القديم | يحتاج تفعيل من الكمبيوتر", out[13])
src = (ROOT / "android/launcher/src/main/java/sa/urtv/home/FamilyDns.java").read_text(encoding="utf-8")
check("FamilyDns has no Android imports", "import android" not in src)
check("manifest: the permission is declared and DockReceiver takes FAMILY_DNS",
      "android.permission.WRITE_SECURE_SETTINGS" in (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
      and 'sa.urtv.home.FAMILY_DNS' in (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8"))
sys.exit(1 if failed else 0)
