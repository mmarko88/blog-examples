#!/usr/bin/env bash
#
# Sets up MS SQL Server for the fastest-insert benchmark suite on Linux/macOS.
# Uses Docker to run SQL Server 2022 and creates testdb + weblogic user + TVP type.
#
# Prerequisites:
#   - Docker installed and running
#   - Ports: 1433 available
#
# Usage:
#   ./setup-linux.sh              # start fresh container
#   ./setup-linux.sh --stop       # stop and remove container
#   ./setup-linux.sh --status     # check if container is running

set -euo pipefail

CONTAINER_NAME="blog-sqlserver"
SA_PASSWORD="weblogic"
DB_PORT=1433
IMAGE="mcr.microsoft.com/mssql/server:2022-latest"

# Detect architecture for Apple Silicon
PLATFORM=""
if [[ "$(uname -m)" == "arm64" ]] && [[ "$(uname -s)" == "Darwin" ]]; then
    PLATFORM="--platform linux/amd64"
    echo "Note: Running x86_64 SQL Server via Rosetta on Apple Silicon"
fi

case "${1:-}" in
    --stop)
        echo "Stopping $CONTAINER_NAME..."
        docker stop "$CONTAINER_NAME" 2>/dev/null || true
        docker rm "$CONTAINER_NAME" 2>/dev/null || true
        echo "Done."
        exit 0
        ;;
    --status)
        if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            echo "$CONTAINER_NAME is running"
            docker exec "$CONTAINER_NAME" /opt/mssql-tools18/bin/sqlcmd \
                -S localhost -U sa -P "$SA_PASSWORD" -C \
                -Q "SELECT 'Connected' AS status, DB_NAME() AS db" 2>/dev/null || echo "SQL Server not ready yet"
        else
            echo "$CONTAINER_NAME is not running"
        fi
        exit 0
        ;;
esac

echo "=== Fastest-Insert DB Setup (Linux/macOS + Docker) ==="

# 1. Start SQL Server container
echo -e "\n[1/5] Starting SQL Server container..."
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "  Container already running."
else
    docker rm "$CONTAINER_NAME" 2>/dev/null || true
    docker run -d $PLATFORM \
        --name "$CONTAINER_NAME" \
        -e "ACCEPT_EULA=Y" \
        -e "MSSQL_SA_PASSWORD=$SA_PASSWORD" \
        -p "$DB_PORT:1433" \
        "$IMAGE"
    echo "  Container started. Waiting for SQL Server to be ready..."
fi

# 2. Wait for SQL Server to start
echo -e "\n[2/5] Waiting for SQL Server to accept connections..."
SQLCMD="docker exec $CONTAINER_NAME /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $SA_PASSWORD -C"
for i in $(seq 1 30); do
    if $SQLCMD -Q "SELECT 1" &>/dev/null; then
        echo "  SQL Server is ready."
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "  ERROR: SQL Server did not start within 30 seconds." >&2
        docker logs "$CONTAINER_NAME" --tail 20
        exit 1
    fi
    sleep 2
done

# 3. Create testdb
echo -e "\n[3/5] Creating testdb database..."
$SQLCMD -Q "
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'testdb')
    CREATE DATABASE testdb;
"
echo "  testdb ready."

# 4. Create weblogic login + user
echo -e "\n[4/5] Creating weblogic login and user..."
$SQLCMD -Q "
IF NOT EXISTS (SELECT name FROM sys.server_principals WHERE name = 'weblogic')
    CREATE LOGIN weblogic WITH PASSWORD = 'weblogic', CHECK_POLICY = OFF;
"
$SQLCMD -d testdb -Q "
IF NOT EXISTS (SELECT name FROM sys.database_principals WHERE name = 'weblogic')
BEGIN
    CREATE USER weblogic FOR LOGIN weblogic;
    ALTER ROLE db_owner ADD MEMBER weblogic;
END
"
echo "  weblogic login/user ready."

# 5. Create PersonTableType TVP (Part 8)
echo -e "\n[5/5] Creating PersonTableType TVP..."
$SQLCMD -d testdb -Q "
IF NOT EXISTS (SELECT * FROM sys.types WHERE name = 'PersonTableType')
    CREATE TYPE dbo.PersonTableType AS TABLE (
        person_id INT,
        user_name NVARCHAR(30),
        first_name NVARCHAR(10),
        last_name NVARCHAR(15),
        years INT
    );
"
echo "  PersonTableType TVP ready."

# Verify
echo -e "\n=== Setup complete! ==="
echo "SQL Server: localhost:$DB_PORT"
echo "Database:   testdb"
echo "Login:      weblogic / weblogic"
echo ""
echo "Run benchmarks:"
echo "  mvn test -pl benchmarks"
echo ""
echo "Stop container:"
echo "  ./setup-linux.sh --stop"
