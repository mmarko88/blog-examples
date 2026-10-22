#!/usr/bin/env bash
# run-benchmark.sh — run one part/suite at a time for fast feedback,
# or all of them at once.
#
#   ./run-benchmark.sh list                show all parts + experimental suites
#   ./run-benchmark.sh 1|part1             run one part module (its whole test suite)
#   ./run-benchmark.sh <ClassName|substr>  run one test class (any module)
#   ./run-benchmark.sh all                 every part module + all benchmark suites
#   ./run-benchmark.sh --cpu N <target>    shield core N (cpu-shield on) then run pinned to it
#
# Output streams to console (no surefire file indirection).
set -uo pipefail
cd "$(dirname "$0")/.."
APPPKG=dev/professional/blog/fastinsert

declare -A PARTS=(
  [1]=part1-hibernate-batching [2]=part2-jdbc-strategies
  [3]=part3-multivalue-insert  [4]=part4-bulk-copy-parameter
  [5]=part5-hibernate-tuning   [6]=part6-parallel-inserts
  [7]=part7-index-impact       [8]=part8-tvp-jdbc
)

classes() { find part*/src/test benchmarks/src/test -name "*Tests.java" 2>/dev/null \
            | sed "s|.*/||;s|\.java$||" | sort; }

module_of() { # which module contains a test class
  find part*/src/test benchmarks/src/test -name "$1.java" 2>/dev/null \
    | head -1 | cut -d/ -f1
}

desc() { # first line of the class javadoc
  local f; f=$(find part*/src/test benchmarks/src/test -name "$1.java" | head -1)
  awk '/\/\*\*/{f=1;next} f{sub(/^[ \t]*\*+[ \t]?/,""); sub(/\*\/.*/,"");
     if(length($0)>2){print; exit}}' "$f"
}

warn_no_db() {
  ss -tln | grep -q ':1433 ' || \
    echo "WARNING: nothing listening on :1433 — start SQL Server first (mssql on / setup script)"
}

# optional CPU pinning: --cpu N activates the shield on core N, then runs mvn pinned to it
MVN=mvn
if [[ ${1:-} == "--cpu" ]]; then cpu-shield on "$2" || exit 1; shift 2; MVN="cpu-shield run mvn"; fi

run_module() { echo "=== $1 ==="; $MVN test -pl "$1" -Dsurefire.useFile=false; }
run_class()  { echo "=== $1 ($2) ==="; $MVN test -pl "$2" -Dtest="$1" -Dsurefire.useFile=false; }

case "${1:-list}" in
  list|"")
    echo "Per-part modules (run: $0 <n> or <module-name>):"
    echo
    for n in 1 2 3 4 5 6 7 8; do
      m=${PARTS[$n]}
      cls=$(find "$m/src/test" -name "*Tests.java" 2>/dev/null | head -1 | xargs -r basename | sed 's/\.java//')
      printf "  %-4s %-32s %s\n" "$n" "$m" "${cls:+— $cls}"
    done
    echo
    echo "Cross-part / experimental suites (benchmarks module):"
    echo
    for c in $(find benchmarks/src/test -name "*Tests.java" | xargs -n1 basename | sed 's/\.java//' | sort); do
      printf "  %-36s %s\n" "$c" "$(desc "$c")"
    done
    ;;
  all)
    warn_no_db
    pass=(); fail=()
    for m in "${PARTS[@]}" benchmarks; do
      if run_module "$m"; then pass+=("$m"); else fail+=("$m"); fi
    done
    echo "================ SUMMARY ================"
    echo "passed: ${#pass[@]}  failed: ${#fail[@]}"
    ((${#fail[@]})) && printf 'FAILED: %s\n' "${fail[@]}"
    ;;
  part[1-8]|[1-8])
    n=${1#part}
    warn_no_db; run_module "${PARTS[$n]}"
    ;;
  *)
    # module name or test-class (sub)string
    if [[ -d $1 && $1 == part* ]]; then warn_no_db; run_module "$1"; exit; fi
    hits=()
    while read -r c; do [[ ${c,,} == *"${1,,}"* ]] && hits+=("$c"); done < <(classes)
    case ${#hits[@]} in
      1) warn_no_db; run_class "${hits[0]}" "$(module_of "${hits[0]}")" ;;
      0) echo "no match: $1 (try: list, 1-8, partN, or a module/class name)" >&2; exit 1 ;;
      *) echo "ambiguous '$1':" >&2; printf '  %s\n' "${hits[@]}" >&2; exit 1 ;;
    esac
    ;;
esac
