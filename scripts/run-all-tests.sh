#!/usr/bin/env bash
#
# Runs tests for ALL blog-examples projects.
#
# Usage:
#   ./run-all-tests.sh                  # run all projects
#   ./run-all-tests.sh --skip-db        # skip fastest-insert (needs SQL Server)
#   ./run-all-tests.sh <project-name>   # run one project (e.g. extract-zip-stream)

set -euo pipefail
cd "$(dirname "$0")/.."

SKIP_DB=false
PROJECT=""

for arg in "$@"; do
    case "$arg" in
        --skip-db) SKIP_DB=true ;;
        *) PROJECT="$arg" ;;
    esac
done

PASS=0
FAIL=0
SKIP=0

run_project() {
    local dir=$1 name=$2 needs_db=${3:-false}
    if [ -n "$PROJECT" ] && [ "$PROJECT" != "$name" ]; then return; fi
    if [ "$needs_db" = true ] && [ "$SKIP_DB" = true ]; then
        echo "[SKIP] $name (requires SQL Server, --skip-db set)"
        SKIP=$((SKIP + 1))
        return
    fi

    echo "================================================================"
    echo "[TEST] $name"
    echo "================================================================"
    if (cd "$dir" && mvn clean test -q); then
        echo "[PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $name"
        FAIL=$((FAIL + 1))
    fi
    echo ""
}

echo "=== Blog Examples — Test Suite ==="
echo ""

run_project "extract-zip-stream"     "extract-zip-stream"
run_project "junit5-parallel-tests"  "junit5-parallel-tests"
run_project "data_to_functions"      "data_to_functions"
run_project "fastest-insert"         "fastest-insert" true

echo "=== Results ==="
echo "Pass: $PASS  Fail: $FAIL  Skip: $SKIP"

if [ "$FAIL" -gt 0 ]; then exit 1; fi
