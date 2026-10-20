# Fastest Way to Insert Data in MS SQL — Code Examples

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
  benchmarks/                   Spring Boot app + 3 benchmark test suites
```

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

- **Java 17+**
- **MS SQL Server** with a `testdb` database
- **Maven 3.9+**

## Running

```bash
# Build all modules (no DB required)
mvn clean install -DskipTests

# Run benchmarks (requires MS SQL Server on localhost:1433)
# Edit benchmarks/src/main/resources/application.properties with your DB credentials first

# Run core benchmarks (Parts 1-5, 8 — ~56 min)
mvn test -pl benchmarks -Dtest="FastestInsertApplicationTests"

# Run thread + index sweep (Part 6-7 — ~14 min)
mvn test -pl benchmarks -Dtest="AdvancedBenchmarkTests"

# Run 16-thread, 1M, useBulkCopy=true (Parts 4-8 — ~7 min)
mvn test -pl benchmarks -Dtest="ExtendedBenchmarkTests"
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

Each test class in `benchmarks/src/test/java/` maps to one or more blog parts. Run any single class with `mvn test -pl benchmarks -Dtest="ClassName"`.

| Test class | Blog part | What it benchmarks |
|-----------|-----------|-------------------|
| `FastestInsertApplicationTests` | 1–5, 8 | Core strategies: Hibernate, JdbcTemplate, Bulk API, TVP, StatelessSession, Persistable, multi-value |
| `AdvancedBenchmarkTests` | 6–7 | Thread sweep (1–16), index count sweep (0–5) |
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
