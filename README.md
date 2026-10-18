# Blog Post Code Examples

Runnable code examples referenced by posts on [professionaldev.dev](https://www.professionaldev.dev/).

| Directory | Post(s) | Stack |
| --- | --- | --- |
| `data_to_functions/` | [Moving Data to Functions](https://www.professionaldev.dev/post/java/data_to_functions/) (FP vs OOP car wash + order processing, JMH benchmarks) | Java 21, JMH |
| `fastest-insert/` | [Fastest Way to Insert Data in MS SQL](https://www.professionaldev.dev/post/java/mssqlserver/persist_vs_saveall/) — 4-part series (Hibernate batching, JDBC strategies, multi-value insert, useBulkCopyForBatchInsert) | Java 21, Spring Boot 3.3, MS SQL Server |

Each directory is a standalone Maven project — `cd <dir> && mvn clean install -DskipTests` to build.
