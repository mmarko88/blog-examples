# Fastest Way to Insert Data in MS SQL — Code Examples

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/mmarko88/blog-examples?quickstart=1)

Multi-module Maven project with code examples for the 8-part blog series on [professionaldev.dev](https://www.professionaldev.dev/).

## Blog Series

| Part | Topic | Module |
|------|-------|--------|
| 1 | [Hibernate Batching](https://www.professionaldev.dev/post/java/mssqlserver/persist_vs_saveall/) — persist vs saveAll | `part1-hibernate-batching/` |
| 2 | [JDBC Strategies](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part2/) — PreparedStatement, JdbcTemplate, Bulk API | `part2-jdbc-strategies/` |
| 3 | [Multi-Value Insert](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part3/) — N values per INSERT | `part3-multivalue-insert/` |
| 4 | [useBulkCopyForBatchInsert](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part4/) — one connection string param | `part4-bulk-copy-parameter/` |
| 5 | Hibernate Tuning — StatelessSession, Persistable, batch optimization | `part5-hibernate-tuning/` |
| 6 | Parallel Inserts — splitting data across N threads | `part6-parallel-inserts/` |
| 7 | Index Impact — how 0/1/3/5 indexes affect insert speed | `part7-index-impact/` |
| 8 | TVP + Plain JDBC — Table-Valued Parameters, bare-metal JDBC | `part8-tvp-jdbc/` |

## Project Structure

```
fastest-insert/
  common/                       Shared entities, descriptors, person generator
  part1-hibernate-batching/     Hibernate persist-and-flush, saveAll
  part2-jdbc-strategies/        JDBC PreparedStatement, JdbcTemplate, Bulk Copy API
  part3-multivalue-insert/      Multi-value INSERT (config, no new code)
  part4-bulk-copy-parameter/    useBulkCopyForBatchInsert (config, no new code)
  part5-hibernate-tuning/       StatelessSession, Persistable (avoid SELECT-before-INSERT)
  part6-parallel-inserts/       ParallelInsertExecutor (1-16 threads)
  part7-index-impact/           IndexBenchmarkHelper (create/drop 0-5 indexes)
  part8-tvp-jdbc/               TVP (SQLServerDataTable), plain JDBC batch insert
  test-support/                 Shared test infra: Spring Boot app, MultipleRunner,
                                AbstractBenchmarkTest base, DB config
  benchmarks/                   FastInsertService facade + cross-part/experimental suites
```

Each `partN` module owns its benchmark test (`src/test/.../PartN*Tests.java`), so
`mvn test -pl part1-hibernate-batching` runs just Part 1.

## Benchmark Results (100,000 persons, batch=1000)

| Rank | Strategy | Avg ms | Module |
|------|----------|--------|--------|
| 1 | Parallel Bulk API (4 threads) | 330 | part6 |
| 2 | Multi-value 400 + 8 threads | 368 | part3+6 |
| 3 | Multi-value 400 + 4 threads | 426 | part3+6 |
| 4 | Parallel Bulk API (8 threads) | 434 | part6 |
| 5 | Bulk API (single thread) | 492 | part2 |
| 6 | Parallel JdbcTemplate 16t | 608 | part6 |
| 7 | Parallel JdbcTemplate 4t + bulkCopy | 731 | part6+4 |
| 8 | TVP (Table-Valued Parameters) | 818 | part8 |
| 9 | Multi-value 400 (single thread) | 1058 | part3 |
| 10 | StatelessSession + bulkCopy=true | 1075 | part5+4 |
| 11 | JdbcTemplate + bulkCopy=true | 1373 | part4 |
| 12 | Plain JDBC + bulkCopy=true | 1516 | part8+4 |
| 13 | Hibernate + bulkCopy=true | 2144 | part4 |
| 14 | JDBC PreparedStatement | 2423 | part2 |
| 15 | StatelessSession | 2650 | part5 |
| 16 | JdbcTemplate | 2985 | part2 |
| 17 | saveAll + Persistable | 3426 | part5 |
| 18 | Plain JDBC (no Hibernate) | 3905 | part8 |
| 19 | Hibernate persist (chunked) | 4048 | part1 |
| 20 | saveAll (original) | 27794 | part1 |

### Key findings

- **Parallel Bulk API** is the absolute fastest (330ms for 100k rows)
- **Multi-value 400 + parallel** is a close second (368ms) and works with any JDBC driver
- **`useBulkCopyForBatchInsert=true`** gives 2-2.5x speedup to single-row batch strategies
- **Multi-value INSERT is incompatible with useBulkCopyForBatchInsert** -- choose one or the other
- **Persistable interface** makes saveAll 8x faster by eliminating SELECT-before-INSERT
- **StatelessSession** is 35% faster than regular Hibernate persist
- **TVP** is the best portable single-thread strategy (818ms)
- **Indexes** slow inserts 2-7x depending on strategy (Bulk API most sensitive)
- **Batch size sweet spot is 500-1000** -- below 200 overhead dominates, above 1000 diminishing returns

## Prerequisites

### Required software

| Tool | Minimum version | Tested with |
|------|----------------|-------------|
| **Java (JDK)** | 17 | Eclipse Adoptium 17.0.16 |
| **Maven** | 3.9+ | 3.9.9 |
| **MS SQL Server** | 2019+ (any edition) | SQL Server 2025 Express (RTM 17.0.1000.7) |

SQL Server Express is free and sufficient for all benchmarks. Developer Edition also works. The
key SQL features used (Bulk Copy, TVP, In-Memory OLTP, partitioning) are available in all
editions since SQL Server 2016 SP1.

> **No-root toolchain:** JDK and Maven don't need sudo. Extract a
> [Temurin](https://adoptium.net/) JDK tarball and the
> [Maven binary](https://maven.apache.org/download.cgi) to `~/opt/` and add their `bin/`
> dirs to `PATH` (plus `JAVA_HOME`). See `scripts/README.md` for the exact layout used
> during development (multiple JDKs under `~/opt/jdk-<ver>` with a `setjava` switcher).

### Database setup

Pick one of the two setup scripts — both create `testdb`, the `weblogic`/`weblogic`
login (db_owner), and the `PersonTableType` TVP:

```bash
./scripts/setup-linux.sh          # Docker container (any OS with Docker)
sudo ./scripts/setup-mssql-native.sh   # native SQL Server on Ubuntu/Debian, no Docker
```

Or manually:

```sql
-- Create the test database (one-time)
CREATE DATABASE testdb;
-- The application creates the person table automatically (Hibernate ddl-auto=update)
```

Default connection: `localhost:1433`, login `weblogic`/`weblogic` (edit
`test-support/src/main/resources/application.properties` to change — it's the
shared config every module's tests pick up).

> **sqlcmd 18+ note:** the ODBC driver defaults to mandatory encryption and rejects
> SQL Server's self-signed cert. Always pass `-C` (trust server certificate):
> `sqlcmd -S localhost -U weblogic -P weblogic -C -d testdb`.

### Environment recommendations for reliable benchmarks

These are the conditions under which the published numbers were measured. Your results will
differ; what matters is the *relative* ranking between strategies on *your* hardware.

**Hardware:**
- **CPU:** 4+ cores. Parallel benchmarks (Part 6) need real cores, not hyperthreads.
- **RAM:** 8 GB minimum. SQL Server Express uses up to 1.4 GB; the JVM needs 512 MB–4 GB
  depending on the GC test.
- **Disk:** SSD strongly preferred. Spinning disk dominates the numbers and hides the
  strategy-vs-strategy delta.
- **Network:** localhost (no network hop). Any latency between the JVM and SQL Server changes
  the relative ranking (see Part 8's simulated-latency experiment).

**Operating system:**
- Windows 10/11 or Windows Server 2019+ (SQL Server native).
- Linux works via Docker or SQL Server on Linux; expect minor differences in I/O scheduling.
- macOS: use Docker for SQL Server (`mcr.microsoft.com/mssql/server:2022-latest`).

**What to close before running:**
- Other database connections (SSMS query windows, monitoring tools).
- Background processes that write to disk (Windows Update, antivirus scans, IDE indexing).
- Docker Desktop if SQL Server is running natively (and vice versa).

**Linux quick-quiet:** `scripts/bench-env.sh` automates this — `check` audits running
services, `sudo scripts/bench-env.sh on` turns on benchmark mode (stops non-essential
services, locks the CPU at a fixed frequency), `on --desktop` additionally drops to
`multi-user.target` (runlevel 3, kills the GUI), and `off` restores everything.
See `scripts/README.md` for details.

**JVM flags (set automatically by Maven Surefire):**
- `-Xmx1g` for most tests; GC tests explicitly override to `-Xmx512m` / `-Xmx4g`.
- `-Duser.timezone=UTC -Dfile.encoding=UTF-8` for deterministic string handling.

**SQL Server configuration tips:**
- Set `tempdb` on a separate drive from `testdb` if possible (reduces I/O contention on
  spill-heavy tests like Part 8's tempdb experiment).
- Leave `AUTO_UPDATE_STATISTICS ON` (the default). Turning it off makes inserts **slower**
  (measured in Part 7).
- Leave `MAXDOP` at default unless you're testing parallelism specifically.

**Warm-up:** each test class runs a 1-run warm-up before the timed runs. The published numbers
are the **median of 10 timed runs** per strategy.

## Running

```bash
# Build all modules (no DB required) — run from the project ROOT so the
# reactor builds `common` before the modules that depend on it
mvn clean install -DskipTests

# Run benchmarks (requires MS SQL Server on localhost:1433)
# Edit test-support/src/main/resources/application.properties with your DB credentials first

# Run one part (~per-module feedback loop)
mvn test -pl part1-hibernate-batching
mvn test -pl part6-parallel-inserts

# Run core benchmarks — all parts (~56 min)
mvn test -pl 'part1-hibernate-batching,part2-jdbc-strategies,part3-multivalue-insert,part4-bulk-copy-parameter,part5-hibernate-tuning,part6-parallel-inserts,part7-index-impact,part8-tvp-jdbc'

# Run thread + index sweep (Parts 6-7 — ~14 min)
mvn test -pl part6-parallel-inserts,part7-index-impact

# Run 16-thread, 1M, useBulkCopy=true (Parts 4-8 — ~7 min)
mvn test -pl benchmarks -Dtest="ExtendedBenchmarkTests"
```

Or use the convenience runner — per-part modules, individual suites, or all at once:

```bash
./scripts/run-benchmark.sh list        # parts + all experimental suites
./scripts/run-benchmark.sh 1           # Part 1 only (or ./run-benchmark.sh part1)
./scripts/run-benchmark.sh gc          # single suite (fuzzy match → GcTuningTests)
./scripts/run-benchmark.sh all         # every module + suite, with pass/fail summary
```

### TVP setup

Part 8 (TVP) requires a user-defined table type in SQL Server. The test creates it automatically, or run manually:
```sql
CREATE TYPE dbo.PersonTableType AS TABLE (
    person_id INT, user_name NVARCHAR(30),
    first_name NVARCHAR(10), last_name NVARCHAR(15), years INT
);
```

## Test Class Guide

Per-part tests live inside their own module — run one with
`mvn test -pl <module>` (whole module) or `-Dtest="ClassName"` (single class):

| Test class | Module | Blog part | What it benchmarks |
|-----------|--------|-----------|-------------------|
| `Part1HibernateBatchingTests` | part1 | 1 | Hibernate persist-and-flush vs saveAll |
| `Part2JdbcStrategyTests` | part2 | 2 | PreparedStatement, JdbcTemplate, Bulk Copy API |
| `Part3MultiValueTests` | part3 | 3 | Multi-value INSERT (objectsPerInsert > 1) |
| `Part4BulkCopyParameterTests` | part4 | 4 | Part 1/2 inserts with useBulkCopyForBatchInsert=true |
| `Part5HibernateTuningTests` | part5 | 5 | StatelessSession, Persistable saveAll |
| `Part6ParallelInsertTests` | part6 | 6 | 4-thread benchmark + 1/2/4/8 thread sweep |
| `Part7IndexImpactTests` | part7 | 7 | 0/1/3/5 indexes × 3 strategies |
| `Part8TvpJdbcTests` | part8 | 8 | TVP + plain PreparedStatement JDBC |

Cross-part and experimental suites live in `benchmarks/src/test/java/`. Run any
single class with `mvn test -pl benchmarks -Dtest="ClassName"`.

| Test class | Blog part | What it benchmarks |
|-----------|-----------|-------------------|
| `ExtendedBenchmarkTests` | 4–8 | 1M rows, 16 threads, useBulkCopyForBatchInsert=true across all strategies |
| `ExperimentalBenchmarkTests` | 5 | Batch-size sweep (100–10,000), multi-value objectsPerInsert sweep |
| `MultiValueParallelTests` | 3, 6 | Multi-value INSERT combined with parallel threads |
| `CombinedWinnerTests` | 8 | Combined top strategies head-to-head |
| `OptimizationSweepTests` | 8 | Strategy matrix sweep across batch sizes and thread counts |
| `FillFactorAndPartitionTests` | 7 | Fill factor (50/70/90/100), partition + SWITCH pattern |
| `AdvancedExperimentTests` | 5, 7 | Transaction patterns, isolation levels, GC tuning |
| `AdvancedPatternTests` | 8 | RCSI, MERGE vs INSERT, triggers, async pipeline |
| `StoragePatternTests` | 7 | Change tracking, temporal tables, GUID keys |
| `ColumnTypeAndStatsTests` | 7 | XML/JSON columns, sparse, AUTO_UPDATE_STATISTICS, encryption |
| `IndexAndLoggingTests` | 7 | Index varieties (filtered, INCLUDE, composite), computed columns, trace flag 610, delayed durability |
| `IdentityAndFkTests` | 7 | ID generation strategies (IDENTITY, SEQUENCE, app-generated), foreign keys, batch retry |
| `PlanAndBulkOptionsTests` | 8 | OPTION(RECOMPILE), simulated latency, INSERT…SELECT, SQLServerBulkCopy options |
| `InMemoryAndBulkTests` | 8 | In-Memory OLTP, BULK INSERT from file, stored procedures, check constraints |
| `SnapshotAndSwitchTests` | 8 | SNAPSHOT isolation + concurrent readers, SWITCH with indexes |
| `XmlJsonAndSpillTests` | 8 | XML/JSON index overhead, tempdb spill under memory pressure |
| `PoolAndTuningTests` | 5 | Connection pool tuning, sendStringParametersAsUnicode |
| `GcTuningTests` | 5 | GC comparison: G1 vs Parallel vs ZGC, heap sizes |

## Package

All classes use the `dev.professional.blog.fastinsert` package namespace.
