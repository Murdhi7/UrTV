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
pkgs=$(grep -vE '^[[:space:]]*(#|$)' "$LIST" | awk '{print $1}')
blocked=$(printf '%s\n' "$pkgs" | grep -Fxf <(grep -vE '^[[:space:]]*(#|$)' "$KEEP" | awk '{print $1}') || true)
if [ -n "$blocked" ]; then
  echo "Refusing: these are in $KEEP (features people use, or the TV needs them):"
  printf '%s\n' "$blocked" | sed 's/^/  /'
  exit 1
fi
core=$(printf '%s\n' "$pkgs" | grep -Ef <(grep -vE '^[[:space:]]*(#|$)' optimize/lists/core.txt | sed 's/.*/^(&)$/') || true)
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
# Which packages are ours to put back: installed on the TV, not already off before this run (a maker's own «disabled» package must not be turned
# ON by a restore, and a package that is not on the TV, or was removed for the user, is not ours at all). Each list is read with an end marker:
# an empty answer is «the TV did not say», not «none».
tvlist() {   # command -> package names, one per line; fails when the end marker is missing
  local raw; raw=$("$PY" -I optimize/tv.py "$1; echo END_OF_LIST" 2> /dev/null | tr -d '\r') || return 1
  [ "$(tail -n 1 <<< "$raw")" = END_OF_LIST ] || return 1
  sed -n 's/^package://p' <<< "$raw"
}
RUN="${URTV_RUN_DIR:-optimize/run}"
mkdir -p "$RUN"
LEDGER="$RUN/disabled-by-us.txt"
known=yes
installed=$(tvlist "pm list packages") || known=no
was_off=$(tvlist "pm list packages -d") || known=no
intended=""
if [ "$known" = yes ] && grep -qx android <<< "$installed"; then
  intended=$(printf '%s\n' "$pkgs" | grep -Fxf <(printf '%s\n' "$installed") | grep -vxFf <(printf '%s\n' "$was_off") || true)
else
  known=no
  echo "!! disable.sh: the TV did not say which packages it has or which were already off, so nothing from this run is remembered for the restore (optimize/restore.sh with the same list still turns them on)." >&2
fi
prev=""; [ ! -f "$LEDGER" ] || prev=$(cat "$LEDGER")
remember() { { printf '%s\n' "$prev"; printf '%s\n' "$1"; } | { grep -E '^[A-Za-z0-9_.]+$' || true; } | sort -u > "$LEDGER.tmp" && mv "$LEDGER.tmp" "$LEDGER"; }
[ "$known" = no ] || remember "$intended"   # written BEFORE the batch: a batch that dies half way is still undone by the restore
printf '%s\n' "$pkgs" | awk '{print "pm disable-user --user 0 " $1}' | "$PY" -I optimize/tv.py --batch
if [ "$known" = yes ]; then   # confirmed by what the TV says is off now (read from the package manager, which is up to date at once)
  if now_off=$(tvlist "pm list packages -d"); then
    remember "$(printf '%s\n' "$intended" | grep -Fxf <(printf '%s\n' "$now_off") || true)"
  fi
fi
optimize/settle.sh mark 2> /dev/null || true
