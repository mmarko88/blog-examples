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
| 2 | Parallel Bulk API (8 threads) | 434 | part6 |
| 3 | Bulk API (single thread) | 492 | part2 |
| 4 | Parallel JdbcTemplate (16 threads) | 608 | part6 |
| 5 | TVP (Table-Valued Parameters) | 818 | part8 |
| 6 | StatelessSession + bulkCopy=true | 1075 | part5 |
| 7 | JdbcTemplate + bulkCopy=true | 1373 | part4 |
| 8 | Plain JDBC + bulkCopy=true | 1516 | part8 |
| 9 | Hibernate + bulkCopy=true | 2144 | part4 |
| 10 | JDBC PreparedStatement | 2423 | part2 |
| 11 | StatelessSession | 2650 | part5 |
| 12 | JdbcTemplate | 2985 | part2 |
| 13 | saveAll + Persistable | 3426 | part5 |
| 14 | Hibernate persist (chunked) | 4048 | part1 |
| 15 | Plain JDBC (no Hibernate) | 3905 | part8 |
| 16 | saveAll (original) | 27794 | part1 |

### Key findings

- **`useBulkCopyForBatchInsert=true`** gives 2-2.5x speedup to ALL batch strategies (one connection string param!)
- **Parallel Bulk API** is the absolute fastest (330ms for 100k rows)
- **Persistable interface** makes saveAll 8x faster by eliminating SELECT-before-INSERT
- **StatelessSession** is 35% faster than regular Hibernate persist
- **TVP** is 3rd fastest single-thread strategy (818ms)
- **Indexes** slow inserts 2-7x depending on strategy (Bulk API most sensitive)

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

## Package

All classes use the `dev.professional.blog.fastinsert` package namespace.
