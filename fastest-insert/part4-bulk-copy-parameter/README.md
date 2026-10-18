# Part 4 — `useBulkCopyForBatchInsert` Parameter

Blog post: [Fastest Way to Insert Data in MS SQL — Part 4: Turning on useBulkCopyForBatchInsert](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part4/)

## What this module covers

The MS SQL JDBC driver parameter `useBulkCopyForBatchInsert=true` automatically
routes batched inserts through the Bulk Copy protocol at the driver level.

### Key configuration change

```properties
# Without the parameter (default):
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=testdb;useBulkCopyForBatchInsert=false

# With the parameter — Bulk Copy for ALL batched inserts:
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=testdb;useBulkCopyForBatchInsert=true
```

### Key insight

With `useBulkCopyForBatchInsert=true`:
- **Hibernate persist** gains Bulk API performance without any code changes
- **JdbcTemplate** batch inserts similarly benefit
- No need to manually use `SQLServerBulkCopy` — the driver does it transparently

### No new code

This module depends on both Part 1 (Hibernate) and Part 2 (JDBC) strategies.
The insight is purely a connection-string parameter change — see the `benchmarks/`
module for the test configuration with this parameter enabled.
