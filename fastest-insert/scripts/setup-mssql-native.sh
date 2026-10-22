#!/usr/bin/env bash
#
# Sets up MS SQL Server for the fastest-insert benchmark suite — NATIVE install,
# no Docker. Ubuntu/Debian only (apt). Installs SQL Server 2025 Developer Edition
# from the Microsoft repo, then creates testdb + weblogic user + TVP type.
#
# Use this when you want SQL Server on bare metal for benchmark accuracy.
# For a quick containerized setup use ./setup-linux.sh instead.
#
# Usage:
#   sudo ./setup-mssql-native.sh          # install + configure
#   sudo ./setup-mssql-native.sh --db-only # skip install, just create db/login/type
#   ./setup-mssql-native.sh --status      # check service + connectivity
#
# Prompts for the SA password (not stored).

set -euo pipefail
cd "$(dirname "$0")"

MSSQL_VER=2025
SQLCMD=/opt/mssql-tools18/bin/sqlcmd

# --- status (no root needed) -------------------------------------------------
if [[ "${1:-}" == "--status" ]]; then
    systemctl is-active mssql-server 2>/dev/null | sed 's/^/service: /'
    ss -tln | grep -q ':1433 ' && echo "port 1433: listening" || echo "port 1433: closed"
    [[ -x $SQLCMD ]] && $SQLCMD -S localhost -U weblogic -P weblogic -C -d testdb \
        -Q "SELECT 'weblogic OK' AS status" -h -1 -W 2>&1 | head -3
    exit 0
fi

[[ $EUID -eq 0 ]] || { echo "Run with sudo."; exit 1; }
command -v apt-get >/dev/null || { echo "Ubuntu/Debian only (apt-get not found). Use ./setup-linux.sh for Docker."; exit 1; }

if [[ "${1:-}" != "--db-only" ]]; then
    # --- 1. Microsoft repo ---------------------------------------------------
    # No repo exists for every Ubuntu release; fall back to the newest LTS repo.
    echo "[1/6] Adding Microsoft GPG key + repo"
    install -m 0755 -d /usr/share/keyrings
    curl -fsSL https://packages.microsoft.com/keys/microsoft.asc \
        | gpg --dearmor -o /usr/share/keyrings/microsoft-prod.gpg
    chmod 644 /usr/share/keyrings/microsoft-prod.gpg

    CODENAME=$(. /etc/os-release && echo "${VERSION_CODENAME:-noble}")
    UBUNTU_VER=$(. /etc/os-release && echo "$VERSION_ID")
    for v in "$UBUNTU_VER" 24.04 22.04; do
        if curl -fs -o /dev/null "https://packages.microsoft.com/config/ubuntu/$v/mssql-server-$MSSQL_VER.list"; then
            break
        fi
    done
    LIST=$(curl -fsSL "https://packages.microsoft.com/config/ubuntu/$v/mssql-server-$MSSQL_VER.list")
    echo "  Using repo for ubuntu/$v (this system: $UBUNTU_VER/$CODENAME)"
    echo "$LIST" > /etc/apt/sources.list.d/mssql-server-$MSSQL_VER.list
    curl -fsSL "https://packages.microsoft.com/config/ubuntu/$v/prod.list" \
        > /etc/apt/sources.list.d/microsoft-prod.list

    # --- 2. Install ----------------------------------------------------------
    echo "[2/6] Installing mssql-server + mssql-tools18"
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive ACCEPT_EULA=Y apt-get install -y \
        mssql-server mssql-tools18 unixodbc

    # --- 3. SA password + edition --------------------------------------------
    echo "[3/6] SA password (>=8 chars, upper+lower+digit/symbol)"
    while true; do
        read -rsp "  SA password: " pw1; echo
        read -rsp "  Confirm:      " pw2; echo
        [[ $pw1 == "$pw2" ]] || { echo "  Mismatch."; continue; }
        [[ ${#pw1} -ge 8 ]] || { echo "  Too short."; continue; }
        break
    done
    ACCEPT_EULA=Y MSSQL_PID=Developer MSSQL_SA_PASSWORD="$pw1" \
        /opt/mssql/bin/mssql-conf -n setup
    unset pw1 pw2

    systemctl enable --now mssql-server
else
    echo "Skipping install (--db-only)."
fi

# --- 4. Wait for readiness ---------------------------------------------------
echo "[4/6] Waiting for SQL Server..."
for i in $(seq 1 30); do
    systemctl is-active --quiet mssql-server && break
    sleep 2
    [[ $i -eq 30 ]] && { echo "Service did not start." >&2; exit 1; }
done
sleep 3  # engine init after service-active

# --- 5. DB objects (idempotent) ----------------------------------------------
read -rsp "[5/6] SA password again (for DB setup): " SQLCMDPASSWORD; echo
export SQLCMDPASSWORD; trap 'unset SQLCMDPASSWORD' EXIT

$SQLCMD -S localhost -U sa -C -b -Q "
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name='weblogic')
    CREATE LOGIN weblogic WITH PASSWORD='weblogic', CHECK_POLICY=OFF;
IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE name='testdb')
    CREATE DATABASE testdb;"

$SQLCMD -S localhost -U sa -C -d testdb -b -Q "
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name='weblogic')
    CREATE USER weblogic FOR LOGIN weblogic;
IF IS_ROLEMEMBER('db_owner','weblogic') <> 1
    ALTER ROLE db_owner ADD MEMBER weblogic;
IF NOT EXISTS (SELECT 1 FROM sys.types WHERE name='PersonTableType')
    CREATE TYPE dbo.PersonTableType AS TABLE (
        person_id INT, user_name NVARCHAR(30),
        first_name NVARCHAR(10), last_name NVARCHAR(15), years INT);"

# --- 6. Verify ---------------------------------------------------------------
echo "[6/6] Verifying weblogic connection..."
$SQLCMD -S localhost -U weblogic -P weblogic -C -d testdb \
    -Q "SELECT DB_NAME() AS db, USER_NAME() AS usr" -h -1 -W

echo ""
echo "=== Done ==="
echo "Service control:  scripts/mssql on|off|status   (or systemctl)"
echo "Connect:          sqlcmd -S localhost -U weblogic -P weblogic -C -d testdb"
echo "Note: sqlcmd 18+ requires -C (trust server certificate) for self-signed certs."
echo "Run benchmarks:   mvn test -pl benchmarks -Dtest=FastestInsertApplicationTests"
