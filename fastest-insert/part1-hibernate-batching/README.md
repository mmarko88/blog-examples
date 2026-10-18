# Part 1 — Hibernate Batching

Blog post: [Fastest Way to Insert Data in MS SQL — Part 1: Hibernate Batching](https://www.professionaldev.dev/post/java/mssqlserver/persist_vs_saveall/)

## What this module covers

Hibernate's default insert behavior sends one `INSERT` statement per entity. By enabling
batching (`hibernate.jdbc.batch_size`) and flushing in chunks, you avoid memory pressure
and let the JDBC driver batch statements together.

### Key classes

| Class | Purpose |
|-------|---------|
| `ChunkEntityPersistRepository` | Interface for chunk-based persist-and-flush |
| `ChunkEntityPersistRepositoryImpl` | Partitions entities, sets COMMIT flush mode, flushes per chunk |

### Key configuration

```properties
spring.jpa.properties.hibernate.jdbc.batch_size=10000
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```
