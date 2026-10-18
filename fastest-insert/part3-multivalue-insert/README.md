# Part 3 — Multi-Value Insert

Blog post: [Fastest Way to Insert Data in MS SQL — Part 3: Multi-Value Insert vs Bulk API](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part3/)

## What this module covers

MS SQL Server supports multi-value inserts:

```sql
INSERT INTO person (person_id, first_name, last_name, user_name, years)
VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?), ...
```

This module reuses the `BulkPersistCustomRepositoryImpl` from Part 2 with `objectsPerInsert > 1`
to pack multiple rows into a single INSERT statement (up to MS SQL's 2,100 parameter binding limit).

### Key insight

Increasing `objectsPerInsert` from 1 to 400 (the max with 5 columns and 2,100 bindings)
closes most of the gap between JDBC Template and the Bulk Copy API. The benchmark
in the blog post shows the multi-value approach competing with Bulk API at large batch sizes.

### No new code

This module depends on `part2-jdbc-strategies` and demonstrates the effect via benchmark
configuration — the `objectsPerInsert` parameter in the `benchmarks/` test suite.
