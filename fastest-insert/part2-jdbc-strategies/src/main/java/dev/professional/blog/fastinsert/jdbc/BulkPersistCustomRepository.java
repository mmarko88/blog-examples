package dev.professional.blog.fastinsert.jdbc;

import dev.professional.blog.fastinsert.common.TableDescriptor;

import java.util.List;

/**
 * JDBC-level multi-value batch insert using PreparedStatement.
 */
public interface BulkPersistCustomRepository {
    <T> void insert(List<T> objects, TableDescriptor<T> descriptor, int batchSize, int objectsPerInsert);
}
