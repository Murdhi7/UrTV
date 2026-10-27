"""Fail closed (idea from Gathin23/cleanyourtv 66e83196): an empty, missing or unreadable answer is "unknown", never "safe". Fake TVs; no TV needed.

    python3 tests/failclosed_test.py

Covers disable.sh (an unreadable kept.txt; what the ledger remembers), keep.sh unkeep (keep.txt and core.txt refusals), reset.sh (the
TV Bro / SmartTube uninstall needs a real saved package list; a failed "is the home app installed?" question stops it), photos.sh
(no photo mode), precheck (an empty feature list is "the TV did not say").
"""
import os, re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

# ---- a tree with disable.sh and keep.sh and a fake tv.py that logs the batches and answers `pm list packages -d` from $OFF
def tree():
    t = Path(tempfile.mkdtemp()); tr = t / "tree"; (tr / "optimize" / "lists").mkdir(parents=True)
    for f in ("disable.sh", "keep.sh", "offlist.py", "settle.sh", "restore.sh", "home-journal.sh"):
        shutil.copy(ROOT / "optimize" / f, tr / "optimize")
    (tr / "optimize/lists/keep.txt").write_text("com.android.vending\n"); (tr / "optimize/lists/core.txt").write_text("com.core.pkg\n")
    (tr / "optimize/lists/names-ar.txt").write_text("")
    (t / "py").write_text('''#!/usr/bin/env bash
case "$2" in *offlist.py) exec python3 "$@" ;; esac
shift 2
echo "$1" >> "$T/calls"
case "$1" in
  --batch) cat >> "$T/batch" ;;
  "pm list packages -d") [ "$OFF_FAILS" = yes ] && exit 1; printf 'package:%s\\n' $OFF ;;
  "pm list packages "*) echo "package:${1##* }" ;;
esac
exit 0
''')
    (t / "py").chmod(0o755)
    return t, tr

def sh(t, tr, script, *args, **env):
    e = dict(os.environ, T=str(t), PY=str(t / "py"), URTV_HOME=str(t / "home"), URTV_RUN_DIR=str(tr / "optimize/run"), PACKAGE_SETTLE_S="0", OFF="", OFF_FAILS="")
    e.update({k.upper(): v for k, v in env.items()})
    r = subprocess.run(["bash", str(tr / "optimize" / script), *args], env=e, capture_output=True, text=True, cwd=tr)
    batch = (t / "batch").read_text() if (t / "batch").exists() else ""
    return r.returncode, r.stdout + r.stderr, batch

# disable.sh: an unreadable kept.txt is not «nothing kept»
t, tr = tree(); (tr / "list.txt").write_text("com.a.one\ncom.a.two\n")
(t / "home" / "kept.txt").mkdir(parents=True)   # a directory where the file should be: cannot be read
rc, out, batch = sh(t, tr, "disable.sh", "list.txt")
check("disable.sh: kept.txt cannot be read -> it stops with a !! line and turns nothing off", rc != 0 and "!! kept.txt could not be read" in out and "pm disable-user" not in batch, f"{rc} {out} {batch}")

# disable.sh: the ledger remembers only what was not already off
t, tr = tree(); (tr / "list.txt").write_text("com.a.already\ncom.a.new\n")
rc, out, batch = sh(t, tr, "disable.sh", "list.txt", off="com.a.already")
ledger = (tr / "optimize/run/disabled-by-us.txt").read_text().split()
check("disable.sh: a package the TV already had off is not remembered as ours (a restore would turn it ON), but is still sent", rc == 0 and ledger == ["com.a.new"] and "pm disable-user --user 0 com.a.already" in batch, f"{ledger} {batch}")
t, tr = tree(); (tr / "list.txt").write_text("com.a.one\n")
rc, out, batch = sh(t, tr, "disable.sh", "list.txt", off_fails="yes")
led = tr / "optimize/run/disabled-by-us.txt"
check("disable.sh: the TV did not say what was already off -> nothing is remembered, and a !! line says so", rc == 0 and "!! disable.sh: the TV did not say which packages were already off" in out
      and (not led.exists() or led.read_text().split() == []), f"{out} {led.read_text() if led.exists() else None}")

# keep.sh unkeep: the same refusals as disable.sh
t, tr = tree(); (t / "home").mkdir()
rc, out, batch = sh(t, tr, "keep.sh", "unkeep", "com.android.vending", "com.core.pkg", "com.ok.app")
check("keep.sh unkeep: a package in keep.txt and one of Android's own core are refused, another is turned off",
      "!! com.android.vending is in optimize/lists/keep.txt" in out and "!! com.core.pkg is Android's own core" in out
      and "pm disable-user --user 0 com.ok.app" in "".join(open(t / "calls").read()) and "disable-user --user 0 com.android.vending" not in open(t / "calls").read()
      and "disable-user --user 0 com.core.pkg" not in open(t / "calls").read(), f"{out} {open(t / 'calls').read()}")

# reset.sh: the TV Bro / SmartTube uninstall needs a real saved package list
src = (ROOT / "optimize/reset.sh").read_text(encoding="utf-8")
uninstall = src[src.index("# TV Bro and SmartTube"):src.index('echo "4/4')]
def reset_uninstall(packages):
    t = Path(tempfile.mkdtemp()); run = t / "optimize/run"; run.mkdir(parents=True)
    (run / "backup.txt").write_text("== all packages\n" + "".join(f"package:{p}\n" for p in packages))
    log = t / "log"
    script = f'tv() {{ echo "$*" >> "{log}"; echo Success; }}\ncd "{t}"\n' + uninstall
    subprocess.run(["bash", "-c", script], capture_output=True, text=True)
    return log.read_text() if log.exists() else ""
log = reset_uninstall([])
check("reset: a saved backup with NO package list does not make every app look installed by the setup: TV Bro and SmartTube are not uninstalled", "pm uninstall" not in log, log)
log = reset_uninstall([f"com.x{i}" for i in range(25)])
check("reset: with a real saved package list, an app that was not in it (the setup installed it) is uninstalled", "pm uninstall com.phlox.tvwebbrowser" in log and "pm uninstall org.smarttube.stable" in log, log)
log = reset_uninstall([f"com.x{i}" for i in range(25)] + ["com.phlox.tvwebbrowser"])
check("reset: and one that was already on the TV is left alone", "pm uninstall com.phlox.tvwebbrowser" not in log and "pm uninstall org.smarttube.stable" in log, log)

# reset.sh: a failed «is the home app installed?» stops before anything is removed
block = src[src.index("# a failed question is not"):src.index("if [ -f \"${URTV_RUN_DIR:-optimize/run}/family-dns-journal.txt\" ]")]
t = Path(tempfile.mkdtemp())
r = subprocess.run(["bash", "-c", f'tv() {{ return 1; }}\ncd "{t}"\n' + block + '\necho CONTINUED\n'], capture_output=True, text=True)
check("reset: if the TV cannot be asked whether the home app is installed, reset stops (the app holds the original Private DNS)", r.returncode == 1 and "CONTINUED" not in r.stdout and "!! familydns: could not ask the TV" in r.stdout, r.stdout + r.stderr)

# photos.sh: no photo mode answered
photos = (ROOT / "optimize/photos.sh").read_text(encoding="utf-8")
check("photos.sh show: no photo mode in the answer -> nothing is changed", photos.index('[ -n "$was" ] ||') < photos.index('send "--es op mode --es mode only"'))

# precheck: an empty feature list is «the TV did not say», exit 5 (tests/precheck_test.py has the case); here the source rule
check("precheck: only a feature list WITH lines but no leanback is «not an Android TV»", "feature list came back empty" in (ROOT / "optimize/precheck.py").read_text(encoding="utf-8"))
sys.exit(1 if failed else 0)
