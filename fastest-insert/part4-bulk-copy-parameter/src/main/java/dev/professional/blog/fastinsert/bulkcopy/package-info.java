/**
 * Part 4 — useBulkCopyForBatchInsert.
 *
 * <p>This module does not introduce new strategy classes. It demonstrates
 * the performance impact of the MS SQL JDBC driver parameter
 * {@code useBulkCopyForBatchInsert=true}, which automatically routes
 * batched JDBC inserts through the Bulk Copy protocol at the driver level.</p>
 *
 * <p>The key change is in the JDBC URL:
 * <pre>{@code
 * jdbc:sqlserver://localhost:1433;databaseName=testdb;useBulkCopyForBatchInsert=true
 * }</pre></p>
 *
 * <p>With this parameter enabled, both Hibernate persist and JdbcTemplate
 * batch inserts gain Bulk API-level performance without code changes.</p>
 */
package dev.professional.blog.fastinsert.bulkcopy;
