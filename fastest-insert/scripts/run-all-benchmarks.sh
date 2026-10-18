#!/usr/bin/env bash
#
# Runs ALL fastest-insert benchmark suites sequentially.
# Requires SQL Server running on localhost:1433 with testdb + weblogic user.
#
# Usage:
#   ./run-all-benchmarks.sh              # run all 3 suites
#   ./run-all-benchmarks.sh --core       # only core benchmarks (~56 min)
#   ./run-all-benchmarks.sh --advanced   # only thread/index sweep (~14 min)
#   ./run-all-benchmarks.sh --extended   # only 16-thread/1M/bulkCopy (~7 min)

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
    local name=$1 class=$2 est=$3
    echo "================================================================"
    echo "[Suite] $name (estimated: $est)"
    echo "================================================================"
    mvn test -pl benchmarks -Dtest="$class" -Dsurefire.useFile=false
    echo ""
}

case "${1:-all}" in
    --core)
        run_suite "Core Benchmarks (Parts 1-5, 8)" "FastestInsertApplicationTests" "~56 min"
        ;;
    --advanced)
        run_suite "Thread + Index Sweep (Parts 6-7)" "AdvancedBenchmarkTests" "~14 min"
        ;;
    --extended)
        run_suite "16-Thread, 1M Dataset, BulkCopy=true" "ExtendedBenchmarkTests" "~7 min"
        ;;
    all|*)
        run_suite "Core Benchmarks (Parts 1-5, 8)" "FastestInsertApplicationTests" "~56 min"
        run_suite "Thread + Index Sweep (Parts 6-7)" "AdvancedBenchmarkTests" "~14 min"
        run_suite "16-Thread, 1M Dataset, BulkCopy=true" "ExtendedBenchmarkTests" "~7 min"
        ;;
esac

echo "=== All benchmarks complete ==="
echo "Results stored in testdb.test_result table."
echo "Query: SELECT method_name, batch_size, AVG(duration_ms) FROM test_result GROUP BY method_name, batch_size ORDER BY AVG(duration_ms)"
