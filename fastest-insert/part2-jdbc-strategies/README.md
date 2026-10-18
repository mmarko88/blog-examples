# Part 2 — JDBC Strategies

Blog post: [Fastest Way to Insert Data in MS SQL — Part 2: Hibernate vs PreparedStatement vs JdbcTemplate vs Bulk API](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part2/)

## What this module covers

Compares four insert strategies against MS SQL Server:

| Strategy | Class | Approach |
|----------|-------|----------|
| **JDBC PreparedStatement** | `BulkPersistCustomRepositoryImpl` | Multi-value `INSERT INTO ... VALUES (?,?),(?,?)` with batching |
| **Spring JdbcTemplate** | `SpringTemplateBulkInsertRepository` | Same SQL, delegated through Spring's `batchUpdate` |
| **MS SQL Bulk Copy API** | `BulkCopyRepository` | TDS bulk-insert protocol via `SQLServerBulkCopy` |

All strategies use the shared `TableDescriptor`/`ColumnDescriptor` framework from `common/`.
