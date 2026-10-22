#!/usr/bin/env bash
# bench-env.sh — audit and minimize running services for benchmarking.
#
#   bench-env.sh check             List running services grouped: KEEP / STOP / DESKTOP / REVIEW
#   bench-env.sh smoke             Env-drift check: fixed SQL insert, >15% slower than baseline = warn
#   bench-env.sh on                Enter benchmark mode: stop the STOP list + lock CPU to 3.0GHz
#   bench-env.sh on --desktop      Also drop to runlevel 3 (isolate multi-user.target) — kills the
#                                  whole GUI session; SSH stays up. Console gets text login.
#   bench-env.sh off               Leave benchmark mode: graphical target + restart services +
#                                  restore CPU governor/frequencies
#   (aliases: quiet=on, normal=off)
#
# Unknown/new services are NEVER stopped — they show as REVIEW in `check`.

set -u
STATE=/var/tmp/bench-env.stopped

# --- groups -----------------------------------------------------------------
KEEP=(
  ssh.service NetworkManager.service networkd-dispatcher.service
  wpa_supplicant.service systemd-resolved.service dbus.service
  systemd-journald.service systemd-logind.service systemd-udevd.service
  systemd-oomd.service polkit.service cron.service rsyslog.service
  chrony.service thermald.service power-profiles-daemon.service
)
STOP=(
  accounts-daemon.service avahi-daemon.service avahi-daemon.socket
  bluetooth.service colord.service
  cups.service cups.socket cups.path cups-browsed.service
  fwupd.service ModemManager.service rtkit-daemon.service
  snapd.service snapd.socket switcheroo-control.service
  udisks2.service unattended-upgrades.service upower.service
  mssql-server.service
)
DESKTOP=(gdm.service gnome-remote-desktop.service)

in_list() { local x=$1; shift; for e in "$@"; do [[ $e == "$x" ]] && return 0; done; return 1; }

running_services() {
  systemctl list-units --type=service --state=running --no-legend --no-pager \
    | awk '{print $1}' | sort
}

mem_of() { systemctl show "$1" -p MemoryCurrent --value 2>/dev/null; }

fmt_mem() { # bytes -> human
  local b=${1:-0}
  [[ $b == "[not set]" || -z $b ]] && b=0
  awk -v b="$b" 'BEGIN{ if(b>1048576) printf "%.0fM", b/1048576;
                        else if(b>1024) printf "%.0fK", b/1024;
                        else printf "%dB", b }'
}

cmd_check() {
  local keep=() stop=() desk=() review=()
  while read -r s; do
    if   in_list "$s" "${KEEP[@]}";    then keep+=("$s")
    elif in_list "$s" "${STOP[@]}";    then stop+=("$s")
    elif in_list "$s" "${DESKTOP[@]}"; then desk+=("$s")
    elif [[ $s == user@* ]];           then keep+=("$s")
    else review+=("$s"); fi
  done < <(running_services)

  local group arrname total=0 raw
  for group in KEEP STOP DESKTOP REVIEW; do
    case $group in KEEP) arrname=keep;; STOP) arrname=stop;; DESKTOP) arrname=desk;; *) arrname=review;; esac
    local -n arr=$arrname; ((${#arr[@]})) || continue
    echo "== $group =="
    for s in "${arr[@]}"; do
      raw=$(mem_of "$s"); [[ $raw =~ ^[0-9]+$ ]] || raw=0
      printf "  %-40s %8s\n" "$s" "$(fmt_mem "$raw")"
      [[ $group == STOP || $group == DESKTOP ]] && total=$((total + raw))
    done
  done
  echo "------------------------------------------------"
  echo "$(running_services | wc -l) services running; ~$(fmt_mem $total) reclaimable."
  echo "Run 'sudo $0 on' for benchmark mode; add --desktop for DESKTOP."
}

cmd_quiet() {
  local desktop=0
  [[ ${1:-} == "--desktop" ]] && desktop=1

  : > "$STATE"
  if ((desktop)); then
    echo "Isolating multi-user.target (runlevel 3) — GUI session ends, SSH stays up..."
    # Record GUI services first so `on` knows what to bring back
    for s in "${DESKTOP[@]}"; do
      systemctl is-active --quiet "$s" && echo "$s" >> "$STATE"
    done
    systemctl isolate multi-user.target
    echo "TARGET=graphical" >> "$STATE"   # restore point for `on`
  fi
  for s in "${STOP[@]}"; do
    if systemctl is-active --quiet "$s"; then
      systemctl stop "$s" && echo "stopped $s" && echo "$s" >> "$STATE" \
        || echo "FAILED  $s"
    fi
  done

  # CPU: record current settings, then lock all cores to 3.0 GHz (performance governor)
  local cf=/sys/devices/system/cpu/cpu0/cpufreq
  if [[ -d $cf ]]; then
    {
      echo "CPUGOV=$(cat $cf/scaling_governor)"
      echo "CPUMIN=$(cat $cf/scaling_min_freq)"
      echo "CPUMAX=$(cat $cf/scaling_max_freq)"
    } >> "$STATE"
    for c in /sys/devices/system/cpu/cpu*/cpufreq; do
      echo performance > "$c/scaling_governor"
      echo 3000000 > "$c/scaling_max_freq"
      echo 3000000 > "$c/scaling_min_freq"
    done
    echo "CPU locked: performance governor, min=max=3.0GHz"
  fi

  # VM: pin swappiness low for benchmark determinism
  echo "SWAPPINESS=$(cat /proc/sys/vm/swappiness)" >> "$STATE"
  echo 10 > /proc/sys/vm/swappiness
  echo "swappiness: 10"

  [[ -s $STATE ]] && chmod 644 "$STATE"
  echo "---"; echo "Restore with: sudo $0 off"
}

cmd_normal() {
  if [[ ! -s $STATE ]]; then echo "Nothing recorded as stopped."; return; fi
  if grep -q '^TARGET=graphical$' "$STATE"; then
    echo "Returning to graphical.target (GUI)..."
    systemctl isolate graphical.target
  fi
  # Restore CPU governor + freq bounds
  if grep -q '^CPUGOV=' "$STATE"; then
    local gov minf maxf
    gov=$(grep '^CPUGOV=' "$STATE" | cut -d= -f2)
    minf=$(grep '^CPUMIN=' "$STATE" | cut -d= -f2)
    maxf=$(grep '^CPUMAX=' "$STATE" | cut -d= -f2)
    for c in /sys/devices/system/cpu/cpu*/cpufreq; do
      echo "$minf" > "$c/scaling_min_freq"
      echo "$maxf" > "$c/scaling_max_freq"
      echo "$gov"  > "$c/scaling_governor"
    done
    echo "CPU restored: governor=$gov min=${minf}kHz max=${maxf}kHz"
  fi
  if grep -q '^SWAPPINESS=' "$STATE"; then
    sw=$(grep '^SWAPPINESS=' "$STATE" | cut -d= -f2)
    echo "$sw" > /proc/sys/vm/swappiness
    echo "swappiness restored: $sw"
  fi
  while read -r s; do
    [[ $s == TARGET=* || $s == CPU*=* || $s == SWAPPINESS=* ]] && continue
    systemctl start "$s" && echo "started $s"
  done < "$STATE"
  : > "$STATE"
}

cmd_smoke() { # quick env-drift check: fixed 200k-row insert, compare to baseline
  local base=/var/tmp/bench-env.baseline ms t0 t1
  t0=$(date +%s%3N)
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U weblogic -P weblogic -C -d testdb -b -Q "
    CREATE TABLE #sm (id INT IDENTITY PRIMARY KEY, v NVARCHAR(50));
    INSERT INTO #sm(v) SELECT TOP 200000 CAST(NEWID() AS NVARCHAR(50))
      FROM master..spt_values a CROSS JOIN master..spt_values b;" >/dev/null 2>&1 \
    || { echo "smoke failed — is SQL Server up? (mssql status)"; return 1; }
  t1=$(date +%s%3N); ms=$((t1 - t0))
  if [[ -f $base ]]; then
    local b; b=$(cat "$base"); local pct=$(( (ms - b) * 100 / b ))
    echo "smoke: ${ms}ms vs baseline ${b}ms ($((pct))% drift)"
    ((pct > 15)) && echo "WARNING: >15% slower — environment changed; don't trust run-to-run deltas"
  else
    echo "$ms" > "$base"; echo "baseline recorded: ${ms}ms — rerun 'smoke' before each session to check drift"
  fi
}

case "${1:-check}" in
  check)         cmd_check ;;
  smoke)         cmd_smoke ;;
  on|quiet)      [[ $EUID -ne 0 ]] && exec sudo "$0" "$@"; cmd_quiet "${2:-}" ;;
  off|normal)    [[ $EUID -ne 0 ]] && exec sudo "$0" "$@"; cmd_normal ;;
  *) echo "usage: $0 [check|smoke|on [--desktop]|off]"; exit 1 ;;
esac
