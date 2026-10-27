#!/usr/bin/env python3
"""optimize/install.sh extras on a flaky Wi-Fi: an extra an earlier try installed is not fetched or uploaded again, and an APK
that was fully downloaded is not downloaded again. A stub tv.py and curl; no TV needed.

    python3 tests/install_extras_test.py
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TVBRO, STUBE = "tvbro-1.2.3-generic-geckoExcluded.apk", "SmartTube_stable_1.0_arm64-v8a.apk"
STUB_PY = r'''#!/usr/bin/env bash
[ "$2" = -c ] && exec python3 "$@"   # the asset picker is real python
shift 2
[ "$1" = --bg ] && { shift 2; set -- "$*"; }
case "$1" in
  --push-b64) echo "push $(basename "$2")" >> "$T/log"; [ "$FAIL_PUSH" = "$(basename "$2")" ] && { echo "!! TV not answering" >&2; exit 1; }; exit 0 ;;
  "getprop ro.product.cpu.abilist") echo "arm64-v8a,armeabi-v7a" ;;
  "pm list packages "*) pkg=${1##* }; grep -qx "$pkg" "$T/installed" 2>/dev/null && echo "package:$pkg" ;;
  "pm install -r /data/local/tmp/tvbro"*) echo com.phlox.tvwebbrowser >> "$T/installed"; echo "install tvbro" >> "$T/log"; echo Success ;;
  "pm install -r /data/local/tmp/SmartTube"*) [ "$SILENT" = 1 ] && exit 1; echo org.smarttube.stable >> "$T/installed"; echo "install smarttube" >> "$T/log"; echo Success ;;
esac
exit 0
'''
STUB_CURL = r'''#!/bin/sh
case "$*" in
  *api.github.com/repos/truefedex/tv-bro*) printf '{"assets":[{"name":"%s","browser_download_url":"https://x/%s","size":10}]}\n200' "$TVBRO" "$TVBRO" ;;
  *api.github.com/repos/yuliskov/SmartTube*) printf '{"assets":[{"name":"%s","browser_download_url":"https://x/%s","size":20}]}\n200' "$STUBE" "$STUBE" ;;
  *)
    out=; url=; while [ $# -gt 0 ]; do [ "$1" = -o ] && out=$2; url=$1; shift; done
    echo "download $(basename "$url")" >> "$T/log"
    case "$url" in *tvbro*) head -c 10 /dev/zero > "$out" ;; *) head -c 20 /dev/zero > "$out" ;; esac ;;
esac
'''
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

def run(installed=(), have=None, fail_push="", tree=None, silent=""):
    t = Path(tempfile.mkdtemp())
    tree = tree or t / "tree"
    bin_ = t / "bin"; bin_.mkdir(); (tree / "optimize" / "lists").mkdir(parents=True, exist_ok=True)
    shutil.copy(ROOT / "optimize" / "install.sh", tree / "optimize")
    shutil.copy(ROOT / "optimize" / "home-journal.sh", tree / "optimize")
    for f, body in (("py", STUB_PY), ("curl", STUB_CURL), ("sleep", "#!/bin/sh\nexit 0\n")):
        (bin_ / f).write_text(body); (bin_ / f).chmod(0o755)
    (t / "installed").write_text("".join(p + "\n" for p in installed))
    (t / "log").write_text("")
    for name, size in (have or {}).items():
        (tree / "optimize" / name).write_bytes(b"x" * size)
    env = dict(os.environ, T=str(t), PATH=f"{bin_}:{os.environ['PATH']}", PY=str(bin_ / "py"), TVBRO=TVBRO, STUBE=STUBE, FAIL_PUSH=fail_push, SILENT=silent)
    r = subprocess.run(["bash", str(tree / "optimize" / "install.sh"), "extras"], env=env, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr, (t / "log").read_text().splitlines(), (t / "installed").read_text().split(), tree

rc, out, log, inst, _ = run()
check("nothing there: both downloaded, uploaded and installed", rc == 0 and log.count("install tvbro") == 1 and log.count("install smarttube") == 1
      and [l for l in log if l.startswith("download")] == [f"download {TVBRO}", f"download {STUBE}"], f"{rc} {log} {out}")
rc, out, log, inst, _ = run(installed=["com.phlox.tvwebbrowser", "org.smarttube.stable"])
check("both already on the TV: nothing downloaded or uploaded", rc == 0 and log == [] and out.count("already on the TV") == 2, f"{log} {out}")
rc, out, log, inst, tree = run(installed=["com.phlox.tvwebbrowser"], fail_push=STUBE)
check("the first is on the TV, the second's upload fails: only the second is tried, the step fails", rc == 1 and not any(TVBRO in l for l in log)
      and f"push {STUBE}" in log, f"{rc} {log} {out}")
check("the half-done APK stays on this computer for the next try", (tree / "optimize" / STUBE).exists())
rc, out, log, inst, _ = run(installed=["com.phlox.tvwebbrowser"], have={STUBE: 20}, tree=tree)
check("the next try does not download a finished APK again, uploads and installs it", rc == 0 and "download" not in " ".join(log)
      and f"push {STUBE}" in log and "install smarttube" in log and "already downloaded" in out, f"{rc} {log} {out}")
rc, out, log, inst, _ = run(installed=["com.phlox.tvwebbrowser"], have={STUBE: 7})
check("a cut-off download (wrong size) is downloaded again", rc == 0 and f"download {STUBE}" in log, f"{rc} {log} {out}")
rc, out, log, inst, _ = run(silent="1")
check("SmartTube: the TV goes silent during its install: skipped with a clear line, the step still ends well, TV Bro stays",
      rc == 0 and "org.smarttube.stable was skipped" in out and "com.phlox.tvwebbrowser" in inst, f"{rc} {out}")
print("\nall install-extras checks passed" if not failed else f"\n{failed} failed")
sys.exit(1 if failed else 0)
