# Blog Post Code Examples

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/mmarko88/blog-examples?quickstart=1)

Runnable code examples referenced by posts on [professionaldev.dev](https://www.professionaldev.dev/).

| Directory | Post(s) | Stack |
| --- | --- | --- |
| `data_to_functions/` | [Moving Data to Functions](https://www.professionaldev.dev/post/java/data_to_functions/) (FP vs OOP car wash + order processing, JMH benchmarks) | Java 21, JMH |
| `fastest-insert/` | [Fastest Way to Insert Data in MS SQL](https://www.professionaldev.dev/post/java/mssqlserver/persist_vs_saveall/) — 8-part series (Hibernate batching, JDBC strategies, multi-value insert, useBulkCopyForBatchInsert, StatelessSession, parallel inserts, index impact, TVP) | Java 17, Spring Boot 3.3, MS SQL Server |
| `extract-zip-stream/` | [Best Practices When Extracting Zip Archive in Java](https://www.professionaldev.dev/post/java/extract_zip_stream_best_practices/) — size limits, entry count, zip bomb detection, sanitization | Java 17, Apache Commons Compress |
| `junit5-parallel-tests/` | [Setting up JUnit 5 Parallel Test Execution With Maven](https://www.professionaldev.dev/post/java/java_multithread_junit5/) — forkCount, Surefire plugin, parallel mode config | Java 17, JUnit 5, Maven Surefire |

Each directory is a standalone Maven project — `cd <dir> && mvn clean install -DskipTests` to build.
