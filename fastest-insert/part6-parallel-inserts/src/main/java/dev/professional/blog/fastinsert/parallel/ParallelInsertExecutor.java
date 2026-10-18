package dev.professional.blog.fastinsert.parallel;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.TableDescriptor;
import dev.professional.blog.fastinsert.jdbc.BulkCopyRepository;
import dev.professional.blog.fastinsert.jdbc.BulkPersistCustomRepository;
import dev.professional.blog.fastinsert.jdbc.SpringTemplateBulkInsertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Splits a dataset across multiple threads for parallel insertion.
 *
 * <p>Each thread gets its own chunk of data and performs the insert independently.
 * Since each insert strategy acquires its own JDBC connection from the pool,
 * N threads effectively means N concurrent connections inserting in parallel.</p>
 *
 * <p>Key considerations:</p>
 * <ul>
 *   <li>HikariCP pool size must be >= threadCount (otherwise threads block waiting for connections)</li>
 *   <li>SQL Server can handle concurrent inserts to the same table — no table-level locks for INSERT</li>
 *   <li>Auto-increment IDs or application-generated IDs must not collide across threads</li>
 *   <li>Transaction isolation: each thread has its own transaction</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParallelInsertExecutor {

    private final BulkCopyRepository bulkCopyRepository;
    private final BulkPersistCustomRepository bulkPersistCustomRepository;
    private final SpringTemplateBulkInsertRepository springTemplateBulkInsertRepository;

    /**
     * Insert using Bulk Copy API across multiple threads.
     */
    public void parallelBulkApiInsert(List<Person> people,
                                       TableDescriptor<Person> table,
                                       int batchSize,
                                       int threadCount) {
        executeInParallel(people, threadCount,
                chunk -> bulkCopyRepository.performBulkInsert(chunk, table, batchSize));
    }

    /**
     * Insert using JDBC PreparedStatement across multiple threads.
     */
    public void parallelJdbcInsert(List<Person> people,
                                    TableDescriptor<Person> table,
                                    int batchSize,
                                    int objectsPerInsert,
                                    int threadCount) {
        executeInParallel(people, threadCount,
                chunk -> bulkPersistCustomRepository.insert(chunk, table, batchSize, objectsPerInsert));
    }

    /**
     * Insert using JdbcTemplate across multiple threads.
     */
    public void parallelJdbcTemplateInsert(List<Person> people,
                                            TableDescriptor<Person> table,
                                            int batchSize,
                                            int objectsPerInsert,
                                            int threadCount) {
        executeInParallel(people, threadCount,
                chunk -> springTemplateBulkInsertRepository.insertPeople(table, chunk, batchSize, objectsPerInsert));
    }

    private <T> void executeInParallel(List<T> data, int threadCount,
                                        java.util.function.Consumer<List<T>> insertAction) {
        List<List<T>> chunks = partition(data, threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (List<T> chunk : chunks) {
                futures.add(CompletableFuture.runAsync(
                        () -> insertAction.accept(chunk), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int parts) {
        List<List<T>> partitions = new ArrayList<>();
        int chunkSize = (list.size() + parts - 1) / parts;
        for (int i = 0; i < list.size(); i += chunkSize) {
            partitions.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return partitions;
    }
}
