# Part 8 — TVP + Plain JDBC

Blog post: [Fastest MS SQL Inserts — Part 8: TVP, JDBC, Final Ranking](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part8/)

## What this module covers

Two strategies that bypass Hibernate entirely:

1. **Table-Valued Parameters (TVP)** — send an entire batch of rows as a single SQL Server data table parameter. One network round-trip per batch instead of N.
2. **Plain JDBC batch insert** — raw `PreparedStatement.addBatch()` without Spring's `JdbcTemplate` wrapper.

### Key classes

| Class | Purpose |
|-------|---------|
| `TvpInsertRepository` | Creates a `SQLServerDataTable`, populates it with persons, and calls a stored procedure that accepts the TVP. Auto-creates the `PersonTableType` if missing. |
| `PlainJdbcInsertRepository` | Raw JDBC: opens a connection, prepares a statement, calls `addBatch()` in a loop, and executes. No Spring, no Hibernate. |

### TVP setup

The `TvpInsertRepository` creates the required user-defined table type automatically on first run. To create it manually:

```sql
CREATE TYPE dbo.PersonTableType AS TABLE (
    person_id   INT,
    user_name   NVARCHAR(30),
    first_name  NVARCHAR(30),
    last_name   NVARCHAR(30),
    years       INT
);
```

### Key finding

| Strategy | Avg ms (100k rows) |
|----------|------|
| TVP (batch 5,000) | 818 |
| Plain JDBC + bulkCopy=true | 1,516 |
| Plain JDBC (no bulkCopy) | 3,905 |

TVP beats plain JDBC handily and is the best portable option (works with any JDBC driver that supports `CallableStatement`).

### Other experiments in Part 8

Part 8 is the series finale and covers many additional topics beyond TVP/JDBC: RCSI, MERGE, triggers, async pipeline, In-Memory OLTP, BULK INSERT, stored procedures, OPTION(RECOMPILE), latency simulation, INSERT…SELECT, SQLServerBulkCopy options, and more. See the test-class guide in the root README.
