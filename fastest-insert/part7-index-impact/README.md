# Part 7 — Index Impact

Blog post: [Fastest MS SQL Inserts — Part 7: Index Impact](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part7/)

## What this module covers

Measures the cost of maintaining non-clustered indexes during bulk inserts. The helper class creates and drops 0–5 indexes on the `person` table so benchmark tests can sweep across index counts.

### Key classes

| Class | Purpose |
|-------|---------|
| `IndexBenchmarkHelper` | Creates N non-clustered indexes on the person table (`ix_person_1` through `ix_person_5`), drops them, and counts the current index set. Uses raw JDBC for DDL. |

### Key finding

| Non-clustered indexes | Bulk API avg ms | JdbcTemplate avg ms | Slowdown (Bulk API) |
|-----------------------|----------------|---------------------|---------------------|
| 0 | 784 | 4,572 | baseline |
| 1 | 1,829 | 5,790 | +133% |
| 3 | 3,632 | 8,010 | +363% |
| 5 | 5,290 | 10,330 | +575% |

### Other experiments in this blog part

Part 7 also covers compression, columnstore, fill factor, GUID keys, change tracking, temporal tables, computed columns, delayed durability, and more. These experiments use the `benchmarks/` module's test classes directly (see the test-class guide in the root README).
