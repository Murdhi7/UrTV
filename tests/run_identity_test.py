"""optimize/run.sh: which TV the .done-* marks and the backup belong to. A placeholder serial (unknown, 0123456789ABCDEF…) is not an id:
the address is used, so two such TVs do not keep each other's marks and backup. Cuts the block out of run.sh; no TV needed.

    python3 tests/run_identity_test.py
"""
import subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
src = (ROOT / "optimize/run.sh").read_text(encoding="utf-8")
block = src[src.index("is_placeholder() {"):src.index("# A home switch that a restart cut off")]
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

def run(serial, host, saved=None):
    t = Path(tempfile.mkdtemp()); run_dir = t / "run"; run_dir.mkdir()
    (t / "optimize").mkdir(); (t / "optimize/.tv_host").write_text("")
    (run_dir / "backup.txt").write_text("== all packages\n"); (run_dir / ".done-backup").write_text("")
    if saved is not None: (run_dir / ".tv").write_text(saved + "\n")
    script = f'tv() {{ printf "%s" "$SERIAL"; }}\nRUN="{run_dir}"\ncd "{t}"\n' + block
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env={"PATH": "/usr/bin:/bin", "SERIAL": serial, "TV_HOST": host})
    return r.stdout + r.stderr, (run_dir / "backup.txt").exists(), (run_dir / ".tv").read_text().strip()

out, kept, ident = run("ABC123", "h1")
check("a real serial is the id", ident == "ABC123", ident)
out, kept, ident = run("ABC123", "h2", saved="ABC123")
check("the same serial at another address: the same TV, nothing wiped", kept and ident == "ABC123", out)
for ph in ("unknown", "0123456789ABCDEF", "null", "1234567890", ""):
    out, kept, ident = run(ph, "h1")
    check(f"placeholder serial «{ph}»: the address is the id", ident == "h1", ident)
    out, kept, ident = run(ph, "h2", saved="h1")
    check(f"placeholder serial «{ph}»: another TV at another address starts from the top, its backup is not kept", not kept and "A different TV" in out and ident == "h2", f"{out} {kept} {ident}")
out, kept, ident = run("unknown", "h2", saved="unknown")
check("an id saved by an older run that was a placeholder is replaced, nothing wiped (it said nothing about which TV it was)", kept and ident == "h2", f"{out} {kept} {ident}")
sys.exit(1 if failed else 0)
