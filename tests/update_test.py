#!/usr/bin/env python3
"""«تحديث تلفزيوني» (the TV's own update): the decision, the order, the «update in progress» record and every sentence on a plain JVM
(tests/UpdateRun.java), what can only be read from the source (the screens, the order of the steps in the engine, the manifest, that nothing is
installed silently), and the appops lines of optimize/install.sh and optimize/reset.sh (cut out of the scripts and run against stand-ins, no TV).
Needs javac for the first part (skipped with a note without).   Run: python3 tests/update_test.py"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android/launcher/src/main/java/sa/urtv/home"
PHONE = JAVA / "phone"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok


def text(p):
    return Path(p).read_text(encoding="utf-8")


# ---- the logic
if not shutil.which("javac"):
    print("SKIP update logic: no javac here")
else:
    pure = ["UpdateFlow", "UpdateOrder", "UpdateRecords", "UpdateText", "UpdateRules"]
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp] + [str(PHONE / f"{n}.java") for n in pure] + [str(ROOT / "tests/UpdateRun.java")], check=True)
        run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.phone.UpdateRun"], capture_output=True)
    out = run.stdout.decode("utf-8")
    print(out, end="")
    if run.returncode != 0 or "FAIL" in out:
        failed += 1
    for n in pure:
        src = text(PHONE / f"{n}.java")
        check(f"{n}.java has no Android in it", not re.search(r"^import android\.", src, re.M), "")

# ---- the engine, read from the source
upd = text(PHONE / "PhoneUpdater.java")
manifest = text(ROOT / "android/launcher/src/main/AndroidManifest.xml")
install_next = upd.split("private static void installNext")[1].split("private static void fail")[0]
check("the engine asks the decision (UpdateFlow) and the order (UpdateOrder); it has no rules of its own",
      "UpdateFlow.first(" in upd and "UpdateFlow.room(" in upd and "UpdateFlow.files(" in upd and "UpdateOrder.order(" in upd, "")
check("the permission is read with canRequestPackageInstalls() on Android 8+, and the system screen is ACTION_MANAGE_UNKNOWN_APP_SOURCES for our package",
      "canRequestPackageInstalls()" in upd and "SDK_INT < 26" in upd and "ACTION_MANAGE_UNKNOWN_APP_SOURCES" in upd and '"package:" + c.getPackageName()' in upd, "")
check("free space, Wi-Fi or Ethernet, and the signer are all checked before «available»",
      "getUsableSpace()" in upd and "TRANSPORT_WIFI" in upd and "TRANSPORT_ETHERNET" in upd and "UpdateRules.verify(" in upd, "")
check("install does nothing unless the checks ran and passed (state «available» with files ready)",
      re.search(r'if \(!state\.equals\("available"\) \|\| ready\.isEmpty\(\)\) return status\(\);', upd) is not None, "")
check("the file is checked again right before its own turn", "verify(c, pkg, file)" in install_next, "")
begin = install_next.find("UpdateRecords.begin(")
commit = install_next.find("session.commit(")
check("the «update in progress» record is written before the commit, and only for the launcher's step",
      0 < begin < commit and "UpdateOrder.replacesUs(pkg)" in install_next[:begin] and "fail(c" in install_next[begin:begin + 200], "")
check("a record that can't be written stops the update before the installer is touched", 'fail(c, "failed", pkg, "record")' in install_next, "")
check("PackageInstaller sessions with a status callback, and the owner confirms on Android's screen (no silent install)",
      "PackageInstaller.SessionParams" in upd and "session.commit(" in upd and "STATUS_PENDING_USER_ACTION" in upd and "STATUS_SUCCESS" in upd
      and "pm install" not in upd and "INSTALL_PACKAGES" not in manifest.replace("REQUEST_INSTALL_PACKAGES", ""), "")
check("the next app is handed over only after the answer of the one before (STATUS_SUCCESS calls installNext)",
      re.search(r"STATUS_SUCCESS\)\s*\{\s*installNext\(c\);", upd) is not None, "")
check("a failure empties the queue and clears the record, so nothing more is installed", "queue.clear()" in upd.split("private static void fail")[1] and "new UpdateStore(c).clear()" in upd.split("private static void fail")[1], "")
check("the failure words come from UpdateFlow.failure", "UpdateFlow.failure(status)" in upd, "")
check("the files are downloaded only from the release address", "startsWith(UpdateRules.BASE)" in upd, "")
check("nothing is uninstalled and the apps' auto-update is not touched", not re.search(r"pm uninstall|\.uninstall\(|deletePackage|setAutoUpdate|auto_update", upd), "")
check("the phone page and the TV's screen call the same engine", "PhoneUpdater.install(app, true)" in text(PHONE / "PhoneActions.java") and "PhoneUpdater.check(" in text(PHONE / "PhoneActions.java")
      and "PhoneUpdater.install(UpdateActivity.this, false)" in text(JAVA / "UpdateActivity.java"), "")
check("only the update's own downloaded files are deleted, inside its own folder", len(re.findall(r"\.delete\(\)", upd)) == 1 and "phone-update" in upd, "")

# ---- the screens
act = text(JAVA / "UpdateActivity.java")
check("the screen is in the manifest, exported like the other settings screens", re.search(r'android:name="\.UpdateActivity"\s+android:exported="true"', manifest) is not None, "")
check("the screen shows the installed version and the button «تحقق من التحديث»", "UpdateText.installed(" in act and "UpdateText.CHECK" in act, "")
check("every branch has its sentence: up to date, offline, available, permission, blocked, failed, done",
      all(w in act for w in ["UpdateText.upToDate(", "UpdateText.OFFLINE", "UpdateText.available(", "UpdateText.NEED_PERMISSION", "UpdateText.AI_MESSAGE", "UpdateText.NOT_DONE", "UpdateText.done("]), "")
check("the permission button opens the system screen, and if it can't be opened the message about the AI assistant follows",
      "openPermissionScreen(" in act and "permissionScreenFailed()" in act, "")
check("the repository address is small, under the message", re.search(r'UpdateText\.REPO,? ?[^;]*12,', act.replace('"⁦" + ', "").replace(' + "⁩"', "")) is not None, "")
check("the screen reads the record once at its start (take) and an update running in this process is not mistaken for a leftover",
      "UpdateRecords.take(" in act and "isBusy()" in text(JAVA / "MainActivity.java"), "")
main = text(JAVA / "MainActivity.java")
check("the launcher opens the screen on start only when a record exists (peek), and has the row in its menu",
      "showUpdateResult();" in main and "UpdateRecords.peek(" in main and 'labels.add("تحديث تلفزيوني…")' in main and "UpdateActivity.class" in main, "")
saver = text(ROOT / "android/app/src/main/java/sa/urtv/screensaver/SettingsActivity.java")
check("the screensaver app's settings have a row that opens it, the way «ترتيب التطبيقات المفضلة» opens the launcher",
      'row("تحديث تلفزيوني"' in saver and 'setClassName("sa.urtv.home", "sa.urtv.home.UpdateActivity")' in saver and "openUpdate();" in saver, "")
check("the phone page knows the new reasons", "cannot:" in text(ROOT / "android/launcher/src/main/assets/phone/index.html") and "failed:" in text(ROOT / "android/launcher/src/main/assets/phone/index.html"), "")


# ---- optimize/install.sh and optimize/reset.sh: the appops line
def block(path, marker_begin="APPOP-BEGIN", marker_end="APPOP-END"):
    lines = text(path).splitlines()
    a = next(i for i, l in enumerate(lines) if marker_begin in l)
    b = next(i for i, l in enumerate(lines) if marker_end in l)
    return "\n".join(lines[a:b + 1])


inst_src = text(ROOT / "optimize/install.sh")
inst_lines = inst_src.splitlines()
a = next(i for i, l in enumerate(inst_lines) if "APPOP-BEGIN" in l)
inst = "\n".join(inst_lines[a:next(i for i, l in enumerate(inst_lines) if "APPOP-END" in l) + 1])
res = block(ROOT / "optimize/reset.sh")
check("install.sh grants the permission ahead of time, with appops", "appops set sa.urtv.home REQUEST_INSTALL_PACKAGES allow" in inst_src, "")
check("reset.sh puts back what was recorded, not a fixed word", 'REQUEST_INSTALL_PACKAGES $appop_mode' in res and "REQUEST_INSTALL_PACKAGES default" not in res, "")


def fake_py(tmp, get_out, fail_get=False):
    """A stand-in for `$PY -I optimize/tv.py "<command>"`: logs the command, answers `appops get`."""
    p = Path(tmp) / "fakepy"
    p.write_text('#!/bin/bash\necho "$3" >> "%s/sent.txt"\nif [[ "$3" == "appops get"* ]]; then\n  %s\n  cat "%s/get.txt"\nfi\n' % (tmp, "exit 1" if fail_get else "true", tmp))
    p.chmod(0o755)
    (Path(tmp) / "get.txt").write_text(get_out)
    return p


def run_install(tmp, get_out, fail_get=False):
    py = fake_py(tmp, get_out, fail_get)
    (Path(tmp) / "sent.txt").write_text("")
    env = dict(os.environ, PY=str(py), URTV_RUN_DIR=str(Path(tmp) / "run"))
    r = subprocess.run(["bash", "-c", "set -euo pipefail\ncd '%s'\n%s" % (tmp, inst)], capture_output=True, text=True, env=env)
    return r.returncode, (Path(tmp) / "sent.txt").read_text().splitlines(), r.stderr


def record(tmp):
    f = Path(tmp) / "run" / "install-appop.txt"
    return f.read_text().strip() if f.exists() else None


ALLOW = "appops set sa.urtv.home REQUEST_INSTALL_PACKAGES allow"
for shown, want in [("REQUEST_INSTALL_PACKAGES: default; rejectTime=+1d\n", "default"), ("REQUEST_INSTALL_PACKAGES: ignore\n", "ignore"),
                    ("REQUEST_INSTALL_PACKAGES: deny; time=+3d\n", "deny"), ("REQUEST_INSTALL_PACKAGES: allow\n", "allow"), ("REQUEST_INSTALL_PACKAGES: foreground\r\n", "foreground"),
                    ("No operations.\n", "default")]:
    with tempfile.TemporaryDirectory() as tmp:
        rc, sent, err = run_install(tmp, shown)
        given = (ALLOW in sent) == (want == "default")   # only Android's own default is replaced: an explicit deny/ignore/foreground (or an allow) stays
        check(f"install.sh: «{shown.strip()[:40]}» is recorded as «{want}»" + (" and the permission is given" if want == "default" else " and left as it is"), rc == 0 and record(tmp) == want and given, f"{rc} {record(tmp)} {sent} {err}")
with tempfile.TemporaryDirectory() as tmp:
    rc, sent, err = run_install(tmp, "REQUEST_INSTALL_PACKAGES: ignore\n")
    rc, sent, err = run_install(tmp, "REQUEST_INSTALL_PACKAGES: allow\n")   # a second run: «allow» is ours now
    check("install.sh: a second run keeps the mode from before our change, it does not record our own «allow»", record(tmp) == "default" and ALLOW in sent
          and not any(l.startswith("appops get") for l in sent), f"{record(tmp)} {sent}")
with tempfile.TemporaryDirectory() as tmp:
    run_install(tmp, "REQUEST_INSTALL_PACKAGES: deny\n")
    rc, sent, err = run_install(tmp, "REQUEST_INSTALL_PACKAGES: deny\n")
    check("install.sh: a second run over an explicit «deny» still leaves it", record(tmp) == "deny" and ALLOW not in sent, f"{record(tmp)} {sent}")
check("install.sh reads the record with sed -nE and (a|b): «\\|» is GNU-only and macOS would record nothing",
      "sed -nE 's/^REQUEST_INSTALL_PACKAGES:" in inst and "\\|" not in inst.split("appop_mode=$(")[1].split("\n")[0], inst[:600])
with tempfile.TemporaryDirectory() as tmp:
    rc, sent, err = run_install(tmp, "", fail_get=True)
    check("install.sh: when the TV doesn't answer, nothing is recorded and nothing is changed (the TV's screen asks for it when needed)", rc == 0 and record(tmp) is None and ALLOW not in sent, f"{rc} {sent} {err}")
with tempfile.TemporaryDirectory() as tmp:
    rc, sent, err = run_install(tmp, "something unexpected\n")
    check("install.sh: an answer that isn't a mode changes nothing", rc == 0 and record(tmp) is None and ALLOW not in sent, f"{rc} {sent}")


def run_reset(tmp, recorded, fail=False, run_dir=None):
    run = Path(run_dir) if run_dir else Path(tmp) / "optimize" / "run"
    run.mkdir(parents=True, exist_ok=True)
    if recorded is not None:
        (run / "install-appop.txt").write_text(recorded)
    out = Path(tmp) / "sent.txt"
    out.write_text("")
    script = 'tv() { echo "$1" >> "%s"; %s; }\nset -euo pipefail\ncd "%s"\n%s\n' % (out, "return 1" if fail else "true", tmp, res)
    env = dict(os.environ, URTV_RUN_DIR=str(run_dir)) if run_dir else {k: v for k, v in os.environ.items() if k != "URTV_RUN_DIR"}
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env=env)
    return r.returncode, out.read_text().splitlines(), (run / "install-appop.txt").exists(), r.stderr


for mode in ["default", "ignore", "deny", "allow", "foreground"]:
    with tempfile.TemporaryDirectory() as tmp:
        rc, sent, still, err = run_reset(tmp, mode + "\n")
        check(f"reset.sh puts back exactly «{mode}» and forgets the record", rc == 0 and sent == [f"appops set sa.urtv.home REQUEST_INSTALL_PACKAGES {mode}"] and not still, f"{rc} {sent} {still} {err}")
with tempfile.TemporaryDirectory() as tmp:
    other = Path(tmp) / "elsewhere"
    rc, sent, still, err = run_reset(tmp, "deny\n", run_dir=other)
    check("reset.sh reads the record where install.sh keeps it: URTV_RUN_DIR, not a fixed optimize/run",
          rc == 0 and sent == ["appops set sa.urtv.home REQUEST_INSTALL_PACKAGES deny"] and not still, f"{rc} {sent} {still} {err}")
with tempfile.TemporaryDirectory() as tmp:
    rc, sent, still, err = run_reset(tmp, None)
    check("reset.sh with no record (a TV set up before this) changes nothing", rc == 0 and sent == [], f"{sent}")
with tempfile.TemporaryDirectory() as tmp:
    rc, sent, still, err = run_reset(tmp, "allow; reboot\n")
    check("reset.sh sends nothing for a record that isn't one of the five modes", rc == 0 and sent == [], f"{sent}")
with tempfile.TemporaryDirectory() as tmp:
    rc, sent, still, err = run_reset(tmp, "ignore\n", fail=True)
    check("reset.sh keeps the record when the TV refused, and carries on", rc == 0 and still, f"{rc} {still} {err}")

sys.exit(1 if failed else 0)
