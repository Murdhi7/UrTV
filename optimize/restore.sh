#!/usr/bin/env bash
# Undo everything: re-enable every package in the given debloat lists, bring back the stock launcher, Google
# TV's setup app and the stock home's helpers (lists/home-replaced.txt), and make the stock launcher home again.
# Usage: optimize/restore.sh [debloat list ...]        (no list: only the home screen)
#   STOCK_LAUNCHER=com.google.android.tvlauncher/.MainActivity optimize/restore.sh list.txt   (older Android TV)
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
. optimize/home-journal.sh
# The stock home that is really on this TV: the one asked for, else the one the home switch journal recorded (found when the switch was made,
# from a good package list), else a known launcher found in the package list now. A package that is not installed is never named.
STOCK_LAUNCHER="${STOCK_LAUNCHER:-$(jget stock)}"
if [ -z "$STOCK_LAUNCHER" ]; then
  pkglist=$("$PY" -I optimize/tv.py "pm list packages" 2> /dev/null | tr -d '\r') || pkglist=""
  if ! grep -qx "package:android" <<< "$pkglist"; then   # the answer must hold the android package itself, else it is «the TV did not say»
    echo "!! restore: could not read the TV's package list, so its own home could not be found: the home was not touched and the safety switch (if any) was left armed. Run the same command again."
  elif grep -qx "package:com.google.android.apps.tv.launcherx" <<< "$pkglist"; then STOCK_LAUNCHER=com.google.android.apps.tv.launcherx/.home.HomeActivity
  elif grep -qx "package:com.google.android.tvlauncher" <<< "$pkglist"; then STOCK_LAUNCHER=com.google.android.tvlauncher/.MainActivity; fi
fi
nonce=$(jget nonce)
LEDGER="${URTV_RUN_DIR:-optimize/run}/disabled-by-us.txt"
{
  for LIST in "$@" optimize/lists/home-replaced.txt; do grep -vE '^[[:space:]]*(#|$)' "$LIST" | awk '{print "pm enable " $1}'; done
  # every package any of our disable runs turned off (a second run, the community list, a list that has changed since). The ledger holds
  # only packages that were installed and not already off (we never uninstall, so there is nothing to install again).
  if [ -f "$LEDGER" ]; then grep -E '^[A-Za-z0-9_.]+$' "$LEDGER" | awk '{print "pm enable " $1}'; fi
  echo "pm enable com.google.android.tungsten.setupwraith"
  if [ -n "$STOCK_LAUNCHER" ]; then   # the safety switch is stopped only when the home really is put back by hand
    echo "touch /data/local/tmp/home_ok"
    [ -z "$nonce" ] || echo "touch /data/local/tmp/home_ok.$nonce"   # (the one of the last home switch)
    echo "pm enable ${STOCK_LAUNCHER%%/*}"
    # Google TV's home can come back without its notification access (seen on other TVs); give it back
    [ "${STOCK_LAUNCHER%%/*}" != com.google.android.apps.tv.launcherx ] || echo "cmd notification allow_listener com.google.android.apps.tv.launcherx/.coreservices.notificationlistener.TvNotificationListenerService"
    echo "cmd role add-role-holder --user 0 android.app.role.HOME ${STOCK_LAUNCHER%%/*}"
    echo "cmd package set-home-activity --user 0 $STOCK_LAUNCHER"
  fi
  echo "input keyevent KEYCODE_HOME"
} | "$PY" -I optimize/tv.py --batch
[ -z "$STOCK_LAUNCHER" ] || jstate restored
optimize/settle.sh mark 2> /dev/null || true
[ -n "$STOCK_LAUNCHER" ] || echo "!! restore: could not tell which app is this TV's own home, so no home was set: press Home on the remote; if nothing opens, tell the person to use the TV's own settings (Apps, Default apps, Home app)."
echo "Restored. Press Home on the remote."
