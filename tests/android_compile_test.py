#!/usr/bin/env python3
"""tests/android_compile.sh: skips (exit 0, a SKIP line) when it cannot get the jar, fails loudly on (and never uses) a jar with the wrong sha1, keeps the cache outside the
repo, and is part of tests/run.sh. The compile itself is the script's own PASS/FAIL lines in tests/run.sh."""
import os
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


src = (ROOT / "tests/android_compile.sh").read_text(encoding="utf-8")
check("the jar is pinned by sha1 and fetched from the Maven Central Google mirror", "SHA1=" in src and "maven-central.storage-download.googleapis.com" in src and "android-all-$VER.jar" in src)
check("the cache is outside the repo", '${URTV_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/urtv}' in src)
check("it is run by tests/run.sh", "bash tests/android_compile.sh" in (ROOT / "tests/run.sh").read_text(encoding="utf-8"))

tmp = tempfile.mkdtemp()
Path(tmp, "curl").write_text("#!/bin/sh\nexit 6\n")   # no network
os.chmod(os.path.join(tmp, "curl"), 0o755)
env = {**os.environ, "PATH": tmp + os.pathsep + os.environ["PATH"], "URTV_CACHE": os.path.join(tmp, "cache")}
env.pop("URTV_ANDROID_JAR", None)
r = subprocess.run(["bash", str(ROOT / "tests/android_compile.sh")], capture_output=True, text=True, env=env)
check("offline with nothing cached: a SKIP line and exit 0, not a failure", r.returncode == 0 and r.stdout.startswith("SKIP android compile") and "FAIL" not in r.stdout, (r.returncode, r.stdout, r.stderr))
check("nothing is left in the cache folder after a failed download", not [p for p in Path(tmp, "cache").glob("*")], list(Path(tmp, "cache").glob("*")))
wrong = Path(tmp, "wrong.jar")
wrong.write_bytes(b"not the jar")
r = subprocess.run(["bash", str(ROOT / "tests/android_compile.sh")], capture_output=True, text=True, env={**env, "URTV_ANDROID_JAR": str(wrong)})
check("a jar with another sha1 is not used and not skipped quietly: FAIL with an error line, exit 1", r.returncode == 1 and "sha1 differs" in r.stdout and r.stdout.startswith("::error::") and "FAIL android compile" in r.stdout, (r.returncode, r.stdout))
sys.exit(1 if failed else 0)
