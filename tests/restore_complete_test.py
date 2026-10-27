"""optimize/restore.sh puts back every package ANY of our disable runs turned off (a second run, a list that changed or is gone), not
only the lists it is given; a package that was never installed is not in it. Fake TV; no TV needed.

    python3 tests/restore_complete_test.py
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STUB = r'''#!/usr/bin/env bash
shift 2
case "$1" in
  --batch) cat >> "$T/batch" ;;
  "pm list packages; echo END_OF_LIST"|"pm list packages")
    echo package:android; echo package:com.a.one; echo package:com.a.two; echo package:com.c.four; echo package:com.b.three
    echo package:com.google.android.tvlauncher; [[ "$1" == *END* ]] && echo END_OF_LIST ;;
  "pm list packages -d; echo END_OF_LIST")   # 1st read of a run: nothing was off; 2nd read: what the batch turned off
    n=$(cat "$T/n" 2>/dev/null || echo 0); echo $(( (n+1) % 2 )) > "$T/n"
    [ "$n" = 0 ] || printf 'package:%s\\n' com.a.one com.a.two com.c.four
    echo END_OF_LIST ;;
esac
exit 0
'''
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

t = Path(tempfile.mkdtemp()); tree = t / "tree"; lists = tree / "optimize" / "lists"; lists.mkdir(parents=True)
for f in ("disable.sh", "restore.sh", "home-journal.sh", "settle.sh"):
    shutil.copy(ROOT / "optimize" / f, tree / "optimize")
(lists / "keep.txt").write_text("com.keep.me\n"); (lists / "core.txt").write_text("com.core.pkg\n"); (lists / "home-replaced.txt").write_text("com.tcl.helper\n")
(tree / "optimize" / "offlist.py").write_text("")
(t / "py").write_text(STUB); (t / "py").chmod(0o755)
(lists / "first.txt").write_text("# a list\ncom.a.one\ncom.a.two  # note\n")
(lists / "second.txt").write_text("com.b.three\ncom.keep.me\n")
env = dict(os.environ, T=str(t), PY=str(t / "py"), URTV_RUN_DIR=str(tree / "optimize/run"), PACKAGE_SETTLE_S="0")
def sh(script, *args):
    return subprocess.run(["bash", str(tree / "optimize" / script), *args], env=env, capture_output=True, text=True, cwd=tree)

r1 = sh("disable.sh", "optimize/lists/first.txt")
r2 = sh("disable.sh", "optimize/lists/second.txt")   # refuses (keep.txt): nothing disabled, nothing remembered from it
ledger = (tree / "optimize/run/disabled-by-us.txt").read_text().split()
check("a disable run remembers what it turned off", r1.returncode == 0 and ledger == ["com.a.one", "com.a.two"], f"{r1.stderr} {ledger}")
check("a refused run (a package in keep.txt) remembers nothing", r2.returncode == 1 and "com.b.three" not in ledger and "com.keep.me" not in ledger, r2.stdout)
(lists / "third.txt").write_text("com.c.four\ncom.a.one\n")
sh("disable.sh", "optimize/lists/third.txt")   # a second run, another list; one package twice
ledger = (tree / "optimize/run/disabled-by-us.txt").read_text().split()
check("a second run adds to it, once each", ledger == ["com.a.one", "com.a.two", "com.c.four"], str(ledger))
check("the disable time is marked", (tree / "optimize/run/.package-change").exists())

# the lists change or are gone: restore with no list still re-enables everything that was turned off
(lists / "first.txt").unlink(); (lists / "third.txt").unlink()
(t / "batch").unlink(missing_ok=True)
r = sh("restore.sh")
b = (t / "batch").read_text().splitlines()
check("restore with no list: every package any run turned off is turned on (we never uninstall, so nothing is installed again)",
      all(f"pm enable {p}" in b for p in ("com.a.one", "com.a.two", "com.c.four")) and not any("install-existing" in x for x in b), str(b))
check("and the stock home's helpers and the home are still put back", "pm enable com.tcl.helper" in b and "input keyevent KEYCODE_HOME" in b, str(b))
check("restore is not harmed by a missing ledger (nothing disabled by us)", subprocess.run(["bash", str(tree / "optimize/restore.sh")], env=dict(env, URTV_RUN_DIR=str(t / "norun")), capture_output=True, text=True, cwd=tree).returncode == 0)
sys.exit(1 if failed else 0)
