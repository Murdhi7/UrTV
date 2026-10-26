"""optimize/dock.sh familydns on|off|status (optimize/familydns.sh): the probe, the record, the read-back, the internet check and the
exact restore, against a fake TV whose settings are files. No TV needed.

    python3 tests/familydns_test.py
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STUB = r'''#!/usr/bin/env bash
shift 2
S="$T/tv"; mkdir -p "$S"
refused() { case " $REFUSE " in *" $1 "*) return 0 ;; esac; return 1; }
apply_line() { set -- $1
  case "$1 $2" in
    "settings put") refused "$4" || echo "$5" > "$S/$4" ;;
    "settings delete") refused "$4" || rm -f "$S/$4" ;;
  esac; }
case "$1" in
  --batch) while read -r l; do echo "$l" >> "$T/batch"; apply_line "$l"; done ;;
  "settings get global "*) k=${1##* }; [ "$UNREADABLE" = yes ] && exit 1; f="$S/$k"; [ -f "$f" ] && cat "$f" || echo null ;;
  *"nc --help"*) [ "$NONC" = yes ] && echo "toybox: Unknown command nc" || echo "usage: netcat [-46ELlntUu] [-pPORT] [-sADDR] [-wSECS] ..." ;;
  *"nc -z"*) host=$(echo "$1" | awk '{for (i = 1; i <= NF; i++) if ($i == "-z") { print $(i + 3); exit }}')
       [ "$NONC" = yes ] && { echo "toybox: Unknown command nc"; exit 0; }
       case "$host" in
         family.adguard-dns.com) [ "$NOFAMILY" = yes ] && echo NOREACH || echo REACH ;;
         connectivitycheck.gstatic.com) echo ping >> "$T/pings"; [ "$BREAK" = yes ] && [ "$(cat "$S/private_dns_mode" 2>/dev/null)" = hostname ] && echo NOREACH || echo REACH ;;
         *) echo NOREACH ;;
       esac ;;
esac
exit 0
'''
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

class Tv:
    def __init__(self, **settings):
        self.t = Path(tempfile.mkdtemp()); self.tree = self.t / "tree"; (self.tree / "optimize").mkdir(parents=True)
        for f in ("familydns.sh",): shutil.copy(ROOT / "optimize" / f, self.tree / "optimize")
        (self.t / "py").write_text(STUB); (self.t / "py").chmod(0o755)
        (self.t / "sleep").write_text("#!/bin/sh\nexit 0\n"); (self.t / "sleep").chmod(0o755)
        (self.t / "tv").mkdir()
        for k, v in settings.items(): (self.t / "tv" / k).write_text(v + "\n")
    def run(self, *args, **env):
        e = dict(os.environ, T=str(self.t), PY=str(self.t / "py"), PATH=f"{self.t}:{os.environ['PATH']}", REFUSE="", UNREADABLE="", NONC="", NOFAMILY="", BREAK="")
        e.update({k.upper(): v for k, v in env.items()})
        r = subprocess.run(["bash", str(self.tree / "optimize" / "familydns.sh"), *args], env=e, capture_output=True, text=True, cwd=self.tree)
        return r.returncode, r.stdout + r.stderr
    def get(self, k):
        f = self.t / "tv" / k
        return f.read_text().strip() if f.exists() else "null"
    def batch(self):
        f = self.t / "batch"
        return f.read_text() if f.exists() else ""
    def journal(self):
        f = self.tree / "optimize" / "run" / "family-dns-journal.txt"
        return f.read_text() if f.exists() else None

H = "family.adguard-dns.com"
# on, then off: exact restore of values the TV had
tv = Tv(private_dns_mode="opportunistic", private_dns_specifier="dns.example")
rc, out = tv.run("on")
check("on: probed, recorded, set, internet checked", rc == 0 and tv.get("private_dns_mode") == "hostname" and tv.get("private_dns_specifier") == H
      and "private_dns_mode=opportunistic" in tv.journal() and "private_dns_specifier=dns.example" in tv.journal() and "!!" not in out, f"{out} {tv.journal()}")
rc, out = tv.run("status")
check("status: on, and what was recorded", "on (" + H in out and "opportunistic" in out, out)
rc, out = tv.run("off")
check("off: exactly what the TV had", rc == 0 and tv.get("private_dns_mode") == "opportunistic" and tv.get("private_dns_specifier") == "dns.example" and tv.journal() is None, f"{out}")
rc, out = tv.run("off")
check("a second off changes nothing", rc == 0 and "already switched off" in out)

# values that were not set are deleted, not written as defaults
tv = Tv()
tv.run("on"); rc, out = tv.run("off")
check("off of values that were not set: deleted (null), never a default", tv.get("private_dns_mode") == "null" and tv.get("private_dns_specifier") == "null"
      and "settings delete global private_dns_mode" in tv.batch(), tv.batch())

# the probe refuses: nothing changes
tv = Tv(private_dns_mode="off")
rc, out = tv.run("on", nofamily="yes")
check("the service cannot be reached: a !! line, nothing changed, nothing recorded", rc == 1 and "!! familydns: this TV cannot reach family.adguard-dns.com on port 853" in out
      and tv.get("private_dns_mode") == "off" and tv.journal() is None and tv.batch() == "", out)
rc, out = tv.run("on", nonc="yes")
check("no tool to test with: refused, nothing changed", rc == 1 and "no tool to test" in out and tv.get("private_dns_mode") == "off" and tv.journal() is None, out)
tv2 = Tv(private_dns_mode="off")
rc, out = tv2.run("on", unreadable="yes")
check("a setting that cannot be read: refused, never recorded as null", rc == 1 and "could not read" in out and tv2.journal() is None, out)

# the internet breaks: put back at once
tv = Tv(private_dns_mode="opportunistic")
rc, out = tv.run("on", **{"break": "yes"})
check("the internet breaks under it: the recorded values are back at once, with a !! line", rc == 1 and "!! familydns: the internet did not work" in out
      and tv.get("private_dns_mode") == "opportunistic" and tv.get("private_dns_specifier") == "null", f"{out} {tv.get('private_dns_mode')}")

# the TV refuses the setting
tv = Tv(private_dns_mode="off")
rc, out = tv.run("on", refuse="private_dns_specifier")
check("the TV does not take the setting: put back, with a !! line", rc == 1 and "did not take" in out and tv.get("private_dns_mode") == "off", out)

# a second on does not overwrite the record with our own values
tv = Tv(private_dns_mode="opportunistic")
tv.run("on"); first = tv.journal(); tv.run("on")
check("on twice: the first record stays", tv.journal() == first and "opportunistic" in first)
sys.exit(1 if failed else 0)
