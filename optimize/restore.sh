#!/usr/bin/env bash
# Undo everything: re-enable every package in the given debloat lists, bring back the stock launcher, Google
# TV's setup app and the stock home's helpers (lists/home-replaced.txt), and make the stock launcher home again.
# Usage: optimize/restore.sh [debloat list ...]        (no list: only the home screen)
#   STOCK_LAUNCHER=com.google.android.tvlauncher/.MainActivity optimize/restore.sh list.txt   (older Android TV)
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
. optimize/home-journal.sh
# The stock home that is really on this TV: the one asked for, else the one the home switch journal recorded, else a known launcher that is
# installed. A package that is not installed is never named (it would be a "restore" to nothing).
have_pkg() { "$PY" -I optimize/tv.py "pm list packages $1" 2> /dev/null | tr -d '\r' | grep -qx "package:$1"; }
STOCK_LAUNCHER="${STOCK_LAUNCHER:-$(jget stock)}"
if [ -z "$STOCK_LAUNCHER" ]; then
  if have_pkg com.google.android.apps.tv.launcherx; then STOCK_LAUNCHER=com.google.android.apps.tv.launcherx/.home.HomeActivity
  elif have_pkg com.google.android.tvlauncher; then STOCK_LAUNCHER=com.google.android.tvlauncher/.MainActivity; fi
fi
if [ -n "$STOCK_LAUNCHER" ] && ! have_pkg "${STOCK_LAUNCHER%%/*}"; then
  echo "!! restore: ${STOCK_LAUNCHER%%/*} is not installed on this TV, so it is not named as the home"; STOCK_LAUNCHER=
fi
nonce=$(jget nonce)
LEDGER="${URTV_RUN_DIR:-optimize/run}/disabled-by-us.txt"
{
  for LIST in "$@" optimize/lists/home-replaced.txt; do grep -vE '^\s*(#|$)' "$LIST" | awk '{print "pm enable " $1}'; done
  # every package any of our disable runs turned off (a second run, the community list, a list that has changed since), also one that
  # was removed for the user: installed again first, then turned on (idea from Gathin23/cleanyourtv eb29abe9)
  if [ -f "$LEDGER" ]; then grep -E '^[A-Za-z0-9_.]+$' "$LEDGER" | awk '{print "cmd package install-existing --user 0 " $1; print "pm enable " $1}'; fi
  echo "pm enable com.google.android.tungsten.setupwraith"
  echo "touch /data/local/tmp/home_ok"      # stop a running safety switch, we restore by hand
  [ -z "$nonce" ] || echo "touch /data/local/tmp/home_ok.$nonce"   # (the one of the last home switch)
  if [ -n "$STOCK_LAUNCHER" ]; then
    echo "pm enable ${STOCK_LAUNCHER%%/*}"
    # Google TV's home can come back without its notification access (seen on other TVs); give it back
    [ "${STOCK_LAUNCHER%%/*}" != com.google.android.apps.tv.launcherx ] || echo "cmd notification allow_listener com.google.android.apps.tv.launcherx/.coreservices.notificationlistener.TvNotificationListenerService"
    echo "cmd role add-role-holder --user 0 android.app.role.HOME ${STOCK_LAUNCHER%%/*}"
    echo "cmd package set-home-activity --user 0 $STOCK_LAUNCHER"
  fi
  echo "input keyevent KEYCODE_HOME"
} | "$PY" -I optimize/tv.py --batch
jstate restored
optimize/settle.sh mark 2> /dev/null || true
[ -n "$STOCK_LAUNCHER" ] || echo "!! restore: could not tell which app is this TV's own home, so no home was set: press Home on the remote; if nothing opens, tell the person to use the TV's own settings (Apps, Default apps, Home app)."
echo "Restored. Press Home on the remote."
