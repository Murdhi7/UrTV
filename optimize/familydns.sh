#!/usr/bin/env bash
# «حماية العائلة (DNS)»: Android's Private DNS pointed at AdGuard Family. Off by default; nothing here runs unless it is asked.
#   optimize/dock.sh familydns on       check the TV can reach the service, record what it had, switch it on, check it works, and put
#                                       it all back if it does not
#   optimize/dock.sh familydns off      put back exactly what the TV had (a value that was not set is deleted)
#   optimize/dock.sh familydns status   on or off, who switched it, and what was recorded
#   optimize/dock.sh familydns grant    give the home app the permission its «حماية العائلة» row needs (recorded; reset.sh takes it back)
# With the home app on the TV, «on» and «off» are done BY the app (it runs entirely on the TV, so a dropped connection or a closed
# window here cannot leave the TV half switched; there is one record, the app's). Without the app the same steps run from here, with a
# watchdog on the TV that puts the old values back after 45 s unless this script confirms. What the TV had is recorded before anything
# changes (optimize/run/family-dns-journal.txt for the script, the app's own record for the app); every value is read back; a value
# that cannot be read is never recorded as «not set».
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
HOST=family.adguard-dns.com   # AdGuard DNS «Family protection» (DNS over TLS); the same host is probed, set and tested
PORT=853
CHECK_HOSTS="connectivitycheck.gstatic.com www.gstatic.com"
RUN="${URTV_RUN_DIR:-optimize/run}"
J="$RUN/family-dns-journal.txt"
MARK_DIR=/data/local/tmp
APP=sa.urtv.home
tv() { "$PY" -I optimize/tv.py "$@"; }
get() {   # setting -> its value; «null» only when the TV itself says so, «unknown» when it could not be read
  local v; v=$(tv "settings get global $1" 2> /dev/null | tr -d '\r' | tail -n 1) || v=""; echo "${v:-unknown}"
}
jget() { sed -n "s/^$1=//p" "$J" 2> /dev/null | head -n 1; }
put_back() {   # key value: exactly that; null is deleted
  if [ "$2" = null ]; then echo "settings delete global $1"; else echo "settings put global $1 $2"; fi
}
sdk() { tv "getprop ro.build.version.sdk" 2> /dev/null | tr -d '\r' | tail -n 1 | tr -dc '0-9'; }
app_here() { tv "pm list packages $APP" 2> /dev/null | tr -d '\r' | grep -qx "package:$APP"; }
app() {   # mode -> the app's answer ("family-dns: …"), empty when it did not answer
  tv "am broadcast -n $APP/.DockReceiver -a $APP.FAMILY_DNS --es mode $1" 2> /dev/null | tr -d '\r' | sed -n 's/.*data="\(.*\)"$/\1/p' | head -n 1 || true
}
app_idle() {   # wait (up to 70 s) for a running check of the app to end; the last status line
  local i s=""
  for i in $(seq 35); do
    s=$(app status)
    case "$s" in *"; working"*) sleep 2 ;; *) break ;; esac
  done
  echo "$s"
}
reach() {   # host port -> yes | no | unsure (the TV has no tool to test with). Exit 0 of «nc -w 3 host port» is a connection.
  local o t
  for t in "toybox nc" "nc"; do
    o=$(tv "$t -w 3 $1 $2 < /dev/null > /dev/null 2>&1 && echo REACH || echo NOREACH" 2> /dev/null | tr -d '\r' | tail -n 1) || o=""
    case "$o" in REACH) echo yes; return ;; NOREACH) ;; *) continue ;; esac
    # NOREACH from a tool that does not exist looks the same: only believe it when that tool is there
    if tv "$t --help 2>&1 | head -n 3" 2> /dev/null | grep -qi usage; then echo no; return; fi
  done
  echo unsure
}
validated() {   # within 15 s the system shows the private DNS server as ours and not failed (dumpsys connectivity)
  local i o
  for i in 1 2 3 4 5; do
    o=$(tv "dumpsys connectivity 2> /dev/null | grep -i privatedns" 2> /dev/null | tr -d '\r') || o=""
    if grep -q "$HOST" <<< "$o" && ! grep -qiE 'fail|error|timed? ?out' <<< "$o"; then return 0; fi
    sleep 3
  done
  return 1
}
internet_ok() {   # the system took the server and answers through it, within about 20 s; the DNS cache is reset when the setting changes
  local h
  validated || return 1
  for h in $CHECK_HOSTS; do [ "$(reach "$h" 80)" = yes ] || return 1; done
}
explain() {   # a result name of the app -> a !! line
  case "$1" in
    ENABLED) ;;
    NO_ROUTE) echo "!! familydns: this TV cannot reach $HOST on port $PORT (DNS over TLS), so nothing was changed. The network may block it" ;;
    REVERTED) echo "!! familydns: the internet did not work through $HOST, so the old Private DNS setting was put back" ;;
    NOT_TAKEN) echo "!! familydns: this TV did not take the Private DNS setting, so the old one was put back" ;;
    NEEDS_GRANT) echo "!! familydns: the home app has no permission to change it: run optimize/dock.sh familydns grant" ;;
    UNSUPPORTED) echo "!! familydns: this TV's Android is older than 9, which has no Private DNS; nothing was changed" ;;
    UNREADABLE_VALUE) echo "!! familydns: could not read this TV's Private DNS setting, so nothing was changed" ;;
    NOT_RECORDED) echo "!! familydns: the old setting could not be recorded, so nothing was changed" ;;
    ALREADY_ON_BY_OTHER) echo "!! familydns: the Private DNS is already pointed at $HOST by someone else; left as it is (switch it off where it was switched on)" ;;
    *) echo "!! familydns: $1" ;;
  esac
}
grant() {
  mkdir -p "$RUN"
  tv "pm grant $APP android.permission.WRITE_SECURE_SETTINGS" > /dev/null 2>&1 \
    || { echo "!! familydns: this TV did not allow the permission (some operator builds refuse it): the on/off row in the TV's settings cannot work; the script's own on/off still can"; return 1; }
  grep -qx 'android.permission.WRITE_SECURE_SETTINGS' "$RUN/grants-journal.txt" 2> /dev/null || echo 'android.permission.WRITE_SECURE_SETTINGS' >> "$RUN/grants-journal.txt"
}

# ---- the script's own steps (no app on the TV) -------------------------------------------------------------------------
PHASE=idle   # switching: values were put and not yet confirmed
revert() {   # put the recorded values back now; 0 when the TV did
  local m s; m=$(jget private_dns_mode); s=$(jget private_dns_specifier)
  { put_back private_dns_mode "$m"; put_back private_dns_specifier "$s"; } | tv --batch > /dev/null 2>&1 || return 1
  [ "$(get private_dns_mode)" = "$m" ] && [ "$(get private_dns_specifier)" = "$s" ]
}
abort_switch() {   # on exit, interrupt or a dropped connection in the middle of «on»
  local rc=$?
  trap - EXIT INT TERM HUP
  if [ "$PHASE" = switching ]; then
    PHASE=idle
    if revert; then echo "!! familydns: interrupted: the old Private DNS setting was put back"
    else echo "!! familydns: interrupted and the old Private DNS setting could not be put back from here: the TV puts it back by itself within 45 seconds; check with optimize/dock.sh familydns status"; fi
  fi
  exit "$rc"
}
shell_on() {
  local mode spec nonce marker
  mode=$(get private_dns_mode); spec=$(get private_dns_specifier)
  if [ "$mode" = unknown ] || [ "$spec" = unknown ]; then explain UNREADABLE_VALUE; exit 1; fi
  if [ "$mode" = hostname ] && [ "$spec" = "$HOST" ] && [ ! -f "$J" ]; then explain ALREADY_ON_BY_OTHER; exit 1; fi
  case "$(reach "$HOST" "$PORT")" in
    yes) ;;
    no) explain NO_ROUTE; exit 1 ;;
    *) echo "!! familydns: this TV has no tool to test whether $HOST:$PORT is reachable, so nothing was changed"; exit 1 ;;
  esac
  for v in "$mode" "$spec"; do [[ "$v" =~ ^(null|[A-Za-z0-9_.-]+)$ ]] || { echo "!! familydns: the current Private DNS setting has a value this script does not put back, so nothing was changed"; exit 1; }; done
  mkdir -p "$RUN"; rm -f "${J%.txt}.done"
  if [ ! -f "$J" ]; then   # the first time records what the TV had; a later «on» keeps that record
    printf '# what the TV had before «familydns on» (null: it was not set); «familydns off» puts exactly this back\nprivate_dns_mode=%s\nprivate_dns_specifier=%s\n' "$mode" "$spec" > "$J.tmp" && mv "$J.tmp" "$J"
  fi
  # the TV puts the old values back by itself after 45 s unless this script confirms (the computer may drop, or the window close)
  nonce="$(date +%s)-$$"; marker="$MARK_DIR/urtv_dns_ok.$nonce"
  local m s; m=$(jget private_dns_mode); s=$(jget private_dns_specifier)
  printf '%s\n' "rm -f $marker" \
    "nohup sh -c 'sleep 45; [ -f $marker ] || { $(put_back private_dns_mode "$m"); $(put_back private_dns_specifier "$s"); }; rm -f $marker' > /dev/null 2>&1 &" \
    | tv --batch > /dev/null
  PHASE=switching
  trap abort_switch EXIT INT TERM HUP
  printf '%s\n' "settings put global private_dns_mode hostname" "settings put global private_dns_specifier $HOST" | tv --batch > /dev/null
  if [ "$(get private_dns_mode)" != hostname ] || [ "$(get private_dns_specifier)" != "$HOST" ]; then
    revert || echo "!! familydns: the old setting could not be put back from here: the TV puts it back within 45 seconds"
    PHASE=idle; explain NOT_TAKEN; exit 1
  fi
  if ! internet_ok; then
    revert || echo "!! familydns: the old setting could not be put back from here: the TV puts it back within 45 seconds"
    PHASE=idle; explain REVERTED; exit 1
  fi
  tv "touch $marker" > /dev/null   # confirmed: the watchdog on the TV does nothing
  PHASE=idle
  if [ "$(get private_dns_mode)" != hostname ]; then echo "!! familydns: the TV took too long and put the old setting back by itself; run the same command again"; exit 1; fi
  echo "حماية العائلة (DNS) is on ($HOST); the internet works through it."
}
shell_off() {   # only while the value is still ours: someone else's change is not overwritten
  local m s
  [ -f "$J" ] || return 0
  m=$(jget private_dns_mode); s=$(jget private_dns_specifier)
  for v in "$m" "$s"; do
    [[ "$v" =~ ^(null|[A-Za-z0-9_.-]+)$ ]] && [ "$v" != unknown ] || { echo "!! familydns: what the TV had was not recorded as a value this script puts back, so it is left as it is"; return 1; }
  done
  if [ "$(get private_dns_mode)" != hostname ] || [ "$(get private_dns_specifier)" != "$HOST" ]; then
    mv -f "$J" "${J%.txt}.done"
    echo "The Private DNS was changed by someone else since it was switched on: left as it is, and the record dropped."
    return 0
  fi
  { put_back private_dns_mode "$m"; put_back private_dns_specifier "$s"; } | tv --batch > /dev/null
  if [ "$(get private_dns_mode)" != "$m" ] || [ "$(get private_dns_specifier)" != "$s" ]; then
    echo "!! familydns: this TV did not take the old Private DNS setting back (the record is kept: run the same command again)"; return 1
  fi
  mv -f "$J" "${J%.txt}.done"
  echo "حماية العائلة (DNS) is off: the TV's Private DNS is as it was."
}

case "${1:-status}" in
  grant)
    app_here || { echo "!! familydns: the home app is not on this TV: optimize/install.sh launcher first"; exit 1; }
    grant && echo "The home app may now switch the Private DNS from the TV's settings (حماية العائلة). optimize/reset.sh takes the permission back." ;;
  status)
    mode=$(get private_dns_mode); spec=$(get private_dns_specifier)
    if [ "$mode" = hostname ] && [ "$spec" = "$HOST" ]; then echo "حماية العائلة (DNS): on ($HOST)"
    else echo "حماية العائلة (DNS): off (Private DNS: $mode$([ "$spec" = null ] || echo ", $spec"))"; fi
    [ ! -f "$J" ] || echo "recorded by the script before it was switched on: private_dns_mode=$(jget private_dns_mode), private_dns_specifier=$(jget private_dns_specifier)"
    if app_here; then s=$(app status); [ -z "$s" ] || echo "the home app says: $s"; fi ;;
  on)
    sd=$(sdk)
    if [ -n "$sd" ] && [ "$sd" -lt 28 ]; then explain UNSUPPORTED; exit 1; fi
    if app_here; then
      s=$(app_idle)
      case "$s" in *"grant=no"*) grant || true ;; esac
      a=$(app on)
      case "$a" in
        *started*) ;;
        *) r=${a#family-dns: }; explain "${r:-NO_ANSWER}"; exit 1 ;;
      esac
      s=$(app_idle)
      r=$(sed -n 's/.*last=\([A-Z_]*\).*/\1/p' <<< "$s")
      if [ "$r" = ENABLED ]; then echo "حماية العائلة (DNS) is on ($HOST); the internet works through it (switched by the home app, which holds the record)."
      else explain "${r:-NO_ANSWER}"; exit 1; fi
    else
      shell_on
    fi ;;
  off)
    fail=0; had=0
    [ ! -f "$J" ] || had=1
    shell_off || fail=1
    if app_here; then
      app_idle > /dev/null
      a=$(app off); r=${a#family-dns: }
      case "$r" in
        RESTORED) echo "حماية العائلة (DNS) is off: the TV's Private DNS is as it was (put back by the home app)." ;;
        NOTHING_TO_RESTORE) ;;
        CHANGED_BY_SOMEONE_ELSE) echo "The Private DNS was changed by someone else since it was switched on: left as it is." ;;
        *) echo "!! familydns: the home app did not put the old Private DNS back (${r:-no answer}): run optimize/dock.sh familydns off again"; fail=1 ;;
      esac
    fi
    if [ "$had" = 0 ] && ! app_here; then
      if [ -f "${J%.txt}.done" ]; then echo "حماية العائلة (DNS) was already switched off (nothing to put back)."
      else echo "Nothing recorded: this tool did not change the TV's Private DNS."; fi
    fi
    exit "$fail" ;;
  *) echo "usage: optimize/dock.sh familydns on|off|status|grant"; exit 1 ;;
esac
