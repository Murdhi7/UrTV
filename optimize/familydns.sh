#!/usr/bin/env bash
# «حماية العائلة (DNS)»: Android's Private DNS pointed at AdGuard Family. Off by default; nothing here runs unless it is asked.
#   optimize/dock.sh familydns on       (this script) check the TV can reach the service, record what it had, switch it on, check
#                                       the internet still works within 20 s, and put it all back at once if it does not
#   optimize/dock.sh familydns off      put back exactly what the TV had (a value that was not set is deleted)
#   optimize/dock.sh familydns status   on or off, and what was recorded
# What the TV had is recorded in optimize/run/family-dns-journal.txt before anything changes; every value is read back; a
# value that cannot be read is never recorded as «not set». The home screen's own row «حماية العائلة» does the same on the TV.
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
HOST=family.adguard-dns.com   # AdGuard DNS «Family protection» (DNS over TLS); the same host is probed, set and tested
PORT=853
CHECK_HOST=connectivitycheck.gstatic.com
J="${URTV_RUN_DIR:-optimize/run}/family-dns-journal.txt"
tv() { "$PY" -I optimize/tv.py "$@"; }
get() {   # setting -> its value; «null» only when the TV itself says so, «unknown» when it could not be read
  local v; v=$(tv "settings get global $1" 2> /dev/null | tr -d '\r' | tail -n 1) || v=""; echo "${v:-unknown}"
}
jget() { sed -n "s/^$1=//p" "$J" 2> /dev/null | head -n 1; }
put_back() {   # key value: exactly that; null is deleted
  if [ "$2" = null ]; then echo "settings delete global $1"; else echo "settings put global $1 $2"; fi
}
reach() {   # host port -> yes | no | unsure (the TV has no tool to test with)
  local o
  for t in "toybox nc" "nc"; do
    o=$(tv "$t -z -w 3 $1 $2 > /dev/null 2>&1 && echo REACH || echo NOREACH" 2> /dev/null | tr -d '\r' | tail -n 1) || o=""
    case "$o" in REACH) echo yes; return ;; NOREACH) ;; *) continue ;; esac
    # NOREACH from a tool that does not exist looks the same: only believe it when that tool is there
    if tv "$t --help 2>&1 | head -n 3" 2> /dev/null | grep -qi usage; then echo no; return; fi
  done
  echo unsure
}
revert() {   # put the recorded values back now
  local m s; m=$(jget private_dns_mode); s=$(jget private_dns_specifier)
  { put_back private_dns_mode "$m"; put_back private_dns_specifier "$s"; } | tv --batch > /dev/null
}
internet_ok() {   # within 20 s: the name resolves (through the private DNS) and the server answers
  local i
  for i in 1 2 3 4; do
    if [ "$(reach "$CHECK_HOST" 80)" = yes ]; then return 0; fi
    sleep 1
  done
  return 1
}
case "${1:-status}" in
  status)
    mode=$(get private_dns_mode); spec=$(get private_dns_specifier)
    if [ "$mode" = hostname ] && [ "$spec" = "$HOST" ]; then echo "حماية العائلة (DNS): on ($HOST)"; else echo "حماية العائلة (DNS): off (Private DNS: ${mode}${spec:+, }$([ "$spec" = null ] || echo "$spec"))"; fi
    [ ! -f "$J" ] || echo "recorded before it was switched on: private_dns_mode=$(jget private_dns_mode), private_dns_specifier=$(jget private_dns_specifier)" ;;
  on)
    mode=$(get private_dns_mode); spec=$(get private_dns_specifier)
    if [ "$mode" = hostname ] && [ "$spec" = "$HOST" ]; then echo "حماية العائلة (DNS) is already on."; exit 0; fi
    if [ "$mode" = unknown ] || [ "$spec" = unknown ]; then
      echo "!! familydns: could not read this TV's Private DNS setting, so nothing was changed"; exit 1
    fi
    case "$(reach "$HOST" "$PORT")" in
      yes) ;;
      no) echo "!! familydns: this TV cannot reach $HOST on port $PORT (DNS over TLS), so nothing was changed. The network may block it; Private DNS needs it"; exit 1 ;;
      *) echo "!! familydns: this TV has no tool to test whether $HOST:$PORT is reachable, so nothing was changed"; exit 1 ;;
    esac
    mkdir -p "$(dirname "$J")"; rm -f "${J%.txt}.done"
    if [ ! -f "$J" ]; then   # the first time records what the TV had; a later «on» keeps that record
      printf '# what the TV had before «familydns on» (null: it was not set); «familydns off» puts exactly this back\nprivate_dns_mode=%s\nprivate_dns_specifier=%s\n' "$mode" "$spec" > "$J.tmp" && mv "$J.tmp" "$J"
    fi
    printf '%s\n' "settings put global private_dns_mode hostname" "settings put global private_dns_specifier $HOST" | tv --batch > /dev/null
    if [ "$(get private_dns_mode)" != hostname ] || [ "$(get private_dns_specifier)" != "$HOST" ]; then
      revert
      echo "!! familydns: this TV did not take the Private DNS setting, so the old one was put back"; exit 1
    fi
    if ! internet_ok; then
      revert
      echo "!! familydns: the internet did not work through $HOST within 20 seconds, so the old Private DNS setting was put back"; exit 1
    fi
    echo "حماية العائلة (DNS) is on ($HOST); the internet works through it." ;;
  off)
    if [ ! -f "$J" ]; then
      if [ -f "${J%.txt}.done" ]; then echo "حماية العائلة (DNS) was already switched off (nothing to put back)."
      else echo "Nothing recorded: this tool did not change the TV's Private DNS."; fi
      exit 0
    fi
    m=$(jget private_dns_mode); s=$(jget private_dns_specifier)
    for v in "$m" "$s"; do
      [[ "$v" =~ ^(null|[A-Za-z0-9_.-]+)$ ]] && [ "$v" != unknown ] || { echo "!! familydns: what the TV had was not recorded as a value this script puts back, so it is left as it is"; exit 1; }
    done
    revert
    if [ "$(get private_dns_mode)" != "$m" ] || [ "$(get private_dns_specifier)" != "$s" ]; then
      echo "!! familydns: this TV did not take the old Private DNS setting back (the record is kept: run the same command again)"; exit 1
    fi
    mv -f "$J" "${J%.txt}.done"
    echo "حماية العائلة (DNS) is off: the TV's Private DNS is as it was." ;;
  *) echo "usage: optimize/dock.sh familydns on|off|status"; exit 1 ;;
esac
