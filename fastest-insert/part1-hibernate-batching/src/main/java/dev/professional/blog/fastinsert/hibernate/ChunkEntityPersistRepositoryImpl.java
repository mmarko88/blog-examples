package dev.professional.blog.fastinsert.hibernate;

import com.google.common.collect.Lists;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Hibernate batch insert: sets COMMIT flush mode, partitions entities
 * into chunks, then persist + flush + clear per chunk.
 *
 * <p>Blog post: <em>Fastest Way to Insert Data in MS SQL — Part 1: Hibernate Batching</em></p>
 */
@Repository
@Slf4j
@Transactional
public class ChunkEntityPersistRepositoryImpl implements ChunkEntityPersistRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public <T> void persistAndFlushInChunks(List<T> objects, int bulkInsertBatchSize) {
        if (CollectionUtils.isEmpty(objects)) {
            return;
        }
        entityManager.setFlushMode(FlushModeType.COMMIT);
        flushAndClear();
        for (List<T> chunk : Lists.partition(objects, bulkInsertBatchSize)) {
            persistAndFlush(chunk);
        }
        entityManager.setFlushMode(FlushModeType.AUTO);
    }

    private <T> void persistAndFlush(Iterable<T> objects) {
        for (T object : objects) {
            entityManager.persist(object);
        }
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
