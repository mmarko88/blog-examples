package dev.professional.blog.fastinsert.hibernate;

import java.util.List;

/**
 * Hibernate-based batch insert — persists entities in chunks,
 * flushing and clearing the persistence context after each chunk
 * to avoid memory pressure on large inserts.
 */
public interface ChunkEntityPersistRepository {
    <T> void persistAndFlushInChunks(List<T> objects, int bulkInsertBatchSize);
}
