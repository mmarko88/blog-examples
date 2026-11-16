# Part 6 — Parallel Inserts

Blog post: [Fastest MS SQL Inserts — Part 6: Parallel Threads](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part6/)

## What this module covers

Splitting the data across N threads, each with its own JDBC connection and Bulk Copy stream. The single class in this module handles the thread pool, data partitioning, and result aggregation.

### Key classes

| Class | Purpose |
|-------|---------|
| `ParallelInsertExecutor` | Partitions the person list into N chunks, submits each to a fixed thread pool, and collects per-thread timings. Works with any `BiConsumer<List<Person>, Integer>` insert strategy. |

### Key finding

| Threads | Avg ms (100k rows, Bulk API) |
|---------|------|
| 1 | 1,155 |
| 2 | 998 |
| 4 | 537 |
| 8 | 434 |
| 16 | 442 (diminishing returns) |

4–8 threads is the sweet spot on a single SQL Server instance. Beyond that, lock contention and context-switch overhead flatten the curve.
