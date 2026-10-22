#!/usr/bin/env bash
#
# Runs ALL fastest-insert benchmark suites sequentially.
# Requires SQL Server running on localhost:1433 with testdb + weblogic user.
#
# Usage:
#   ./run-all-benchmarks.sh              # run all suites
#   ./run-all-benchmarks.sh --core       # per-part suites, all modules (~56 min)
#   ./run-all-benchmarks.sh --advanced   # Parts 6-7 only (~14 min)
#   ./run-all-benchmarks.sh --extended   # 16-thread/1M/bulkCopy (~7 min)

set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Fastest-Insert Benchmark Suite ==="
echo "Project: $(pwd)"
echo "Java:    $(java -version 2>&1 | head -1)"
echo ""

# Build first
echo "[Build] Compiling all modules..."
mvn clean install -DskipTests -q
echo "[Build] Done."
echo ""

run_suite() {
    local name=$1 shift
    local est=$1 shift
    echo "================================================================"
    echo "[Suite] $name (estimated: $est)"
    echo "================================================================"
    mvn test -Dsurefire.useFile=false "$@"
    echo ""
}

case "${1:-all}" in
    --core)
        # Per-part tests across all part modules (Parts 1-8)
        run_suite "Core Benchmarks — all parts" "~56 min" \
            -pl 'part1-hibernate-batching,part2-jdbc-strategies,part3-multivalue-insert,part4-bulk-copy-parameter,part5-hibernate-tuning,part6-parallel-inserts,part7-index-impact,part8-tvp-jdbc'
        ;;
    --advanced)
        # Thread sweep (part6) + index sweep (part7)
        run_suite "Thread + Index Sweep (Parts 6-7)" "~14 min" \
            -pl part6-parallel-inserts,part7-index-impact
        ;;
    --extended)
        run_suite "16-Thread, 1M Dataset, BulkCopy=true" "~7 min" \
            -pl benchmarks -Dtest="ExtendedBenchmarkTests"
        ;;
    all|*)
        run_suite "Core Benchmarks — all parts" "~56 min" \
            -pl 'part1-hibernate-batching,part2-jdbc-strategies,part3-multivalue-insert,part4-bulk-copy-parameter,part5-hibernate-tuning,part6-parallel-inserts,part7-index-impact,part8-tvp-jdbc'
        run_suite "16-Thread, 1M Dataset, BulkCopy=true" "~7 min" \
            -pl benchmarks -Dtest="ExtendedBenchmarkTests"
        ;;
esac

echo "=== All benchmarks complete ==="
echo "Results stored in testdb.test_result table."
echo "Query: SELECT method_name, batch_size, AVG(duration_ms) FROM test_result GROUP BY method_name, batch_size ORDER BY AVG(duration_ms)"
