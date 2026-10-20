# Part 5 — Hibernate Tuning

Blog post: [Fastest MS SQL Inserts — Part 5: Hibernate Tuning](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part5/)

## What this module covers

Three Hibernate optimizations that close 80% of the gap between Hibernate and the raw Bulk API:

1. **`Persistable<ID>` interface** — makes Spring Data use `persist()` instead of `merge()`, eliminating the SELECT-before-INSERT on every entity.
2. **`StatelessSession`** — bypasses Hibernate's first-level cache entirely, removing GC pressure from large batch inserts.
3. **`useBulkCopyForBatchInsert=true`** (combined with Part 4) — rewrites `addBatch()` calls into the TDS Bulk Copy protocol.

### Key classes

| Class | Purpose |
|-------|---------|
| `PersistablePerson` | Entity implementing `Persistable<Long>` with `isNew()` returning `true` — forces `persist()` over `merge()`. |
| `PersistablePersonRepository` | Spring Data `JpaRepository<PersistablePerson>` for the `saveAll()` path. |
| `StatelessSessionInsertRepository` | Opens a Hibernate `StatelessSession` and inserts in batches, flushing per chunk without the first-level cache. |

### Key finding

| Strategy | Avg ms (100k rows) |
|----------|------|
| Hibernate persist (default) | 4,048 |
| Persistable saveAll | 3,426 |
| StatelessSession | 2,650 |
| StatelessSession + bulkCopy=true | **1,075** |
| Bulk API (baseline) | 492 |
