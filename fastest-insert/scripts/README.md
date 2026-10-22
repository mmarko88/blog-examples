# Helper Scripts

## Database setup

| Script | What it does |
|---|---|
| `setup-linux.sh` | SQL Server 2022 in Docker (`blog-sqlserver` container): creates `testdb`, `weblogic`/`weblogic` login, `PersonTableType` TVP. `--stop` removes the container, `--status` checks it. Works on Linux/macOS (incl. Apple Silicon via Rosetta). |
| `setup-mssql-native.sh` | **Native** SQL Server 2025 Developer Edition on Ubuntu/Debian — no Docker. Adds the Microsoft apt repo (falls back to the newest LTS repo if your release isn't published yet), installs `mssql-server` + `mssql-tools18`, configures the instance as Developer, creates `testdb`/`weblogic`/TVP. Idempotent; `--db-only` re-runs just the DB objects, `--status` verifies connectivity. Requires sudo; prompts for the SA password (never stored). |

Pick **native** when benchmark fidelity matters (no Docker I/O layer); pick **Docker** for convenience or macOS.

## Running benchmarks

| Script | What it does |
|---|---|
| `run-all-benchmarks.sh` | Builds all modules then runs the suites sequentially. `--core` (per-part tests, all 8 modules, ~56 min), `--advanced` (part6+part7 thread/index sweep, ~14 min), `--extended` (16-thread/1M/bulkCopy, ~7 min). |
| `run-benchmark.sh` | Per-part / per-suite runs for debugging and fast feedback. `list` shows the 8 part modules plus every experimental suite; `1`–`8` or `partN` runs one part module's whole suite (`mvn test -pl partN-...`); a class name or substring runs one suite in whichever module owns it (e.g. `./run-benchmark.sh gc` → GcTuningTests); `all` runs every module with a pass/fail summary. Warns if nothing is listening on :1433. |

## Benchmark environment (Linux)

| Script | What it does |
|---|---|
| `bench-env.sh` | Minimize the OS before a run. `check` lists running services grouped KEEP/STOP/DESKTOP/REVIEW with memory use (unknown services are never stopped). `sudo ./bench-env.sh on` enters benchmark mode — stops the STOP list and locks all cores to a fixed 3.0 GHz (`performance` governor); `on --desktop` also `isolate`s `multi-user.target` (runlevel 3 — kills the GUI, SSH stays up). `sudo ./bench-env.sh off` restores services, graphical target, and previous CPU governor/freq bounds. (`quiet`/`normal` work as aliases.) State is recorded in `/var/tmp/bench-env.stopped`. |
| `mssql` | Start/stop SQL Server on demand. `mssql on` starts for this session; `mssql off` stops **and disables boot autostart** (stopped-by-default, keeps the box quiet for other runs); `mssql status` shows active/enabled/port 1433. |
| `cpu-shield` | Isolate a core for pinned benchmark runs (cgroup v2 — systemd `AllowedCPUs`, no extra packages). `cpu-shield on [core]` (default 3) restricts system/user slices off the core, `run <cmd>` executes pinned to it, `off` releases, `status` shows state. `run-benchmark.sh --cpu N` does shield+run in one step; shell alias `smvn` = `cpu-shield run mvn`. |

### CPU pinning

For extra determinism you can pin the JVM to a single isolated core:

```bash
sudo cpu-shield on                      # shield core 3 (or: cpu-shield on <n>)
./scripts/run-benchmark.sh --cpu 3 1    # shield + run Part 1 pinned to core 3
smvn test -pl part1-hibernate-batching  # = cpu-shield run mvn ...
sudo cpu-shield off                     # release the core
```

How it works: `cpu-shield` sets systemd `AllowedCPUs` on `system.slice`,
`user.slice` and `init.scope` (cgroup v2 — no cpuset package needed), then runs
your command in a `systemd-run --scope` pinned to the free core. SQL Server
keeps cores 0–2, the JVM gets a quiet core 3.

**Caveats:** pinning changes absolute numbers (fine for run-to-run comparisons,
don't compare pinned vs unpinned runs). Don't pin for Part 6 — parallel inserts
need real cores; a single-core pin makes the thread sweep meaningless.

## Toolchain without sudo (used during development)

JDKs and Maven can live entirely in `$HOME` — no `apt`, no root:

```
~/opt/jdk-8  ~/opt/jdk-11  ~/opt/jdk-17  ~/opt/jdk-21  ~/opt/jdk-25   # Temurin tarballs
~/opt/apache-maven-3.x/bin/mvn
~/opt/hugo/bin/hugo
```

Get them from Adoptium's binary API (any LTS version):

```bash
mkdir -p ~/opt && cd ~/opt
curl -sL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" | tar xz
mv jdk-21* jdk-21
```

Shell setup (`~/.bashrc` or `~/.zshrc`):

```bash
export PATH="$HOME/opt/apache-maven-3.x/bin:$PATH"

# Java version switcher: `setjava 8|11|17|21|25`
setjava() {
  export JAVA_HOME="$HOME/opt/jdk-$1"
  PATH="$(echo "$PATH" | tr ':' '\n' | grep -v "$HOME/opt/jdk" | paste -sd:)"
  export PATH="$JAVA_HOME/bin:$PATH"
  java -version
}
setjava 21 >/dev/null 2>&1
```

Only JDK 17+ is required for this project; multiple versions are handy for
checking compatibility.

Typical benchmark session:

```bash
sudo ./scripts/bench-env.sh on --desktop   # benchmark mode: quiet system, CPU pinned
./scripts/mssql on                         # if SQL Server is native + off
./scripts/run-all-benchmarks.sh --core
./scripts/mssql off
sudo ./scripts/bench-env.sh off            # benchmark mode off: normal desktop again
```

## Query/inspection

`query-*.sql` — result-inspection queries used by the blog series (per-part
timing tables, index stats, XE events). Run with
`sqlcmd -S localhost -U weblogic -P weblogic -C -d testdb -i <file>`.

`check-*.sql` — server feature checks (In-Memory OLTP, partitioning support).

`generate-charts.py` — renders benchmark CSVs into the charts used in the posts.
