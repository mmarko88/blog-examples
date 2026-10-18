# Fastest Way to Insert Data in MS SQL — Code Examples

Multi-module Maven project with code examples for the 4-part blog series on [professionaldev.dev](https://www.professionaldev.dev/).

## Blog Series

| Part | Post | Module |
|------|------|--------|
| 1 | [Hibernate Batching](https://www.professionaldev.dev/post/java/mssqlserver/persist_vs_saveall/) | `part1-hibernate-batching/` |
| 2 | [Hibernate vs PreparedStatement vs JdbcTemplate vs Bulk API](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part2/) | `part2-jdbc-strategies/` |
| 3 | [Multi-Value Insert vs Bulk API](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part3/) | `part3-multivalue-insert/` |
| 4 | [useBulkCopyForBatchInsert Parameter](https://www.professionaldev.dev/post/java/mssqlserver/fast_insert_part4/) | `part4-bulk-copy-parameter/` |

## Project Structure

```
fastest-insert/
  common/                       Shared entities, descriptors, person generator
  part1-hibernate-batching/     Hibernate persist-and-flush strategy
  part2-jdbc-strategies/        JDBC PreparedStatement, JdbcTemplate, Bulk Copy API
  part3-multivalue-insert/      Multi-value INSERT optimization (config, no new code)
  part4-bulk-copy-parameter/    useBulkCopyForBatchInsert driver flag (config, no new code)
  benchmarks/                   Spring Boot app + benchmark test suite
```

## Prerequisites

- **Java 21+**
- **MS SQL Server** with a `testdb` database
- **Maven 3.9+**

## Running

```bash
# Build all modules (no DB required)
mvn clean install -DskipTests

# Run benchmarks (requires MS SQL Server)
# Edit benchmarks/src/main/resources/application.properties with your DB credentials first
mvn test -pl benchmarks
```

## Package

All classes use the `dev.professional.blog.fastinsert` package namespace.
