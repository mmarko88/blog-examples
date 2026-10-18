/**
 * Part 3 — Multi-Value Insert.
 *
 * <p>This module does not introduce new strategy classes. Instead, it demonstrates
 * the performance effect of increasing {@code objectsPerInsert} in the Part 2
 * {@link dev.professional.blog.fastinsert.jdbc.BulkPersistCustomRepositoryImpl}.</p>
 *
 * <p>MS SQL Server supports multi-value inserts:
 * {@code INSERT INTO t (c1,c2) VALUES (v1,v2),(v3,v4),...}
 * up to 2,100 parameter bindings per statement.</p>
 *
 * @see dev.professional.blog.fastinsert.jdbc.BulkPersistCustomRepositoryImpl#getInsertQuery
 */
package dev.professional.blog.fastinsert.multivalue;
