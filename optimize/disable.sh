#!/usr/bin/env bash
# Disable every package in a debloat list (one pm call per shell command; chained pm calls can stop early).
# Packages are disabled for user 0, never uninstalled. Undo with optimize/restore.sh <same list>.
# Refuses anything in optimize/lists/keep.txt, and Android's own core (optimize/lists/core.txt), with no override.
# Usage: optimize/disable.sh optimize/lists/tcl-safe.txt
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: optimize/disable.sh <debloat list>}"
PY="${PY:-optimize/venv/bin/python}"
KEEP=optimize/lists/keep.txt
pkgs=$(grep -vE '^\s*(#|$)' "$LIST" | awk '{print $1}')
blocked=$(printf '%s\n' "$pkgs" | grep -Fxf <(grep -vE '^\s*(#|$)' "$KEEP" | awk '{print $1}') || true)
if [ -n "$blocked" ]; then
  echo "Refusing: these are in $KEEP (features people use, or the TV needs them):"
  printf '%s\n' "$blocked" | sed 's/^/  /'
  exit 1
fi
core=$(printf '%s\n' "$pkgs" | grep -Ef <(grep -vE '^\s*(#|$)' optimize/lists/core.txt | sed 's/.*/^(&)$/') || true)
if [ -n "$core" ]; then
  echo "Refusing: these are Android's own core, and a TV may not start again without them (optimize/lists/core.txt):"
  printf '%s\n' "$core" | sed 's/^/  /'
  exit 1
fi
# what the person asked to keep on (optimize/keep.sh restore) stays on, also on a later `run.sh again`
keptpkgs=$("$PY" -I optimize/offlist.py kept) || exit 1   # an unreadable kept.txt is not «nothing kept»: it stops here, with its own !! line
if [ -n "$keptpkgs" ]; then
  skipped=$(printf '%s\n' "$pkgs" | grep -Fxf <(printf '%s\n' "$keptpkgs") || true)
  [ -z "$skipped" ] || echo "Left on, as the person asked: $(paste -sd' ' - <<< "$skipped")" >&2
  pkgs=$(printf '%s\n' "$pkgs" | grep -Fxvf <(printf '%s\n' "$keptpkgs") || true)
fi
[ -n "$pkgs" ] || exit 0
was_off=""; was_off_ok=
if was_off_raw=$("$PY" -I optimize/tv.py "pm list packages -d" 2> /dev/null); then
  was_off=$(printf '%s\n' "$was_off_raw" | tr -d '\r' | sed -n 's/^package://p'); was_off_ok=1
fi
printf '%s\n' "$pkgs" | awk '{print "pm disable-user --user 0 " $1}' | "$PY" -I optimize/tv.py --batch
# what this run disabled is remembered (also when a list changes later, or a second run disables another list): optimize/restore.sh
# re-enables every package in it. And the time, so nothing restarts the TV before Android has written the state (optimize/settle.sh).
RUN="${URTV_RUN_DIR:-optimize/run}"
mkdir -p "$RUN"
# only what the TV did not already have off is ours (a maker's own «disabled» package must not be turned ON by a restore). The list of
# what was off BEFORE this run was read first; if it could not be read, nothing is remembered and a !! line says so.
if [ -n "${was_off_ok:-}" ]; then
  mine=$(printf '%s\n' "$pkgs" | grep -vxFf <(printf '%s\n' "$was_off") || true)
else
  mine=""; echo "!! disable.sh: the TV did not say which packages were already off, so nothing from this run is remembered for the restore (optimize/restore.sh with the same list still turns them on)." >&2
fi
{ [ ! -f "$RUN/disabled-by-us.txt" ] || cat "$RUN/disabled-by-us.txt"; printf '%s\n' "$mine"; } | grep -E '^[A-Za-z0-9_.]+$' | sort -u > "$RUN/disabled-by-us.txt.tmp" \
  && mv "$RUN/disabled-by-us.txt.tmp" "$RUN/disabled-by-us.txt"
optimize/settle.sh mark 2> /dev/null || true
