package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonDescriptors;
import dev.professional.blog.fastinsert.hibernate.ChunkEntityPersistRepository;
import dev.professional.blog.fastinsert.hibernate.PersonPersistService;
import dev.professional.blog.fastinsert.hibernate5.PersistablePerson;
import dev.professional.blog.fastinsert.hibernate5.PersistablePersonRepository;
import dev.professional.blog.fastinsert.hibernate5.StatelessSessionInsertRepository;
import dev.professional.blog.fastinsert.index.IndexBenchmarkHelper;
import dev.professional.blog.fastinsert.jdbc.BulkCopyRepository;
import dev.professional.blog.fastinsert.jdbc.BulkPersistCustomRepository;
import dev.professional.blog.fastinsert.jdbc.SpringTemplateBulkInsertRepository;
import dev.professional.blog.fastinsert.parallel.ParallelInsertExecutor;
import dev.professional.blog.fastinsert.tvp.PlainJdbcInsertRepository;
import dev.professional.blog.fastinsert.tvp.TvpInsertRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Facade over every insert strategy benchmarked in the
 * <a href="https://www.professionaldev.dev/series/fastest-way-to-insert-data-in-ms-sql/">Fastest MS SQL Inserts</a> series.
 *
 * <p>Each {@code insertXxx} method delegates to one specific repository/strategy.
 * The benchmark test classes call these methods, wrap them in a timed loop, and persist
 * the results via {@link TestResultService}.</p>
 *
 * <p>Strategy catalogue:</p>
 * <ul>
 *   <li><b>Hibernate</b>: {@code insertHibernate} (Part 1)</li>
 *   <li><b>SaveAll</b>: {@code insertSaveAll}, {@code insertPersistableSaveAll} (Parts 1, 5)</li>
 *   <li><b>StatelessSession</b>: {@code insertStatelessSession} (Part 5)</li>
 *   <li><b>JdbcTemplate</b>: {@code insertJdbcTemplate} (Part 2)</li>
 *   <li><b>JDBC PreparedStatement</b>: {@code insertJdbc} (Part 2)</li>
 *   <li><b>Bulk Copy API</b>: {@code insertBulkApi} (Part 2)</li>
 *   <li><b>Parallel Bulk API</b>: {@code insertParallel*} (Part 6)</li>
 *   <li><b>TVP</b>: {@code insertTvp} (Part 8)</li>
 *   <li><b>Plain JDBC</b>: {@code insertPlainJdbc} (Part 8)</li>
 * </ul>
 *
 * @see TestResultService for persisting benchmark timings
 * @see dev.professional.blog.fastinsert.common.PersonDescriptors for table/column metadata
 */

/**
 * Orchestrates all insert strategies for benchmarking.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FastInsertService {
    @PersistenceContext
    private EntityManager entityManager;

    private final ChunkEntityPersistRepository chunkEntityPersistRepository;
    private final PersonPersistService personPersistService;
    private final BulkPersistCustomRepository bulkPersistCustomRepository;
    private final SpringTemplateBulkInsertRepository springTemplateBulkInsertRepository;
    private final BulkCopyRepository bulkCopyRepository;
    // Part 5
    private final StatelessSessionInsertRepository statelessSessionInsertRepository;
    private final PersistablePersonRepository persistablePersonRepository;
    // Part 6
    private final ParallelInsertExecutor parallelInsertExecutor;
    // Part 7
    private final IndexBenchmarkHelper indexBenchmarkHelper;
    // Part 8
    private final TvpInsertRepository tvpInsertRepository;
    private final PlainJdbcInsertRepository plainJdbcInsertRepository;

    public void cleanUpPeopleTable() {
        entityManager.createNativeQuery("truncate table person").executeUpdate();
        entityManager.clear();
    }

    /** Part 1: Hibernate persist-and-flush in chunks */
    public void insertHibernateBatch(List<Person> people, int batchSize) {
        chunkEntityPersistRepository.persistAndFlushInChunks(people, batchSize);
    }

    /** Part 1: JPA saveAll (triggers SELECT per entity, then INSERT) */
    public void saveAll(List<Person> people) {
        personPersistService.saveAll(people);
    }

    /** Part 2: JDBC PreparedStatement with multi-value support */
    public void insertJdbcBatch(int batchSize, int objectsPerInsert, List<Person> people) {
        bulkPersistCustomRepository.insert(people, PersonDescriptors.PERSON_TABLE, batchSize, objectsPerInsert);
    }

    /** Part 2: Spring JdbcTemplate batch */
    public void insertJdbcTemplateBatch(int batchSize, int objectsPerInsert, List<Person> people) {
        springTemplateBulkInsertRepository.insertPeople(PersonDescriptors.PERSON_TABLE, people, batchSize, objectsPerInsert);
    }

    /** Part 2: MS SQL Bulk Copy API */
    public void insertBulkApi(List<Person> people, int batchSize) {
        bulkCopyRepository.performBulkInsert(people, PersonDescriptors.PERSON_TABLE, batchSize);
    }

    // ---- Part 5: Hibernate Tuning ----

    /** Part 5: Hibernate StatelessSession (no 1st-level cache, no dirty checking) */
    public void insertStatelessSession(List<Person> people, int batchSize) {
        statelessSessionInsertRepository.insertStateless(people, batchSize);
    }

    /** Part 5: saveAll with Persistable (avoids SELECT-before-INSERT) */
    public void saveAllPersistable(List<Person> people) {
        List<PersistablePerson> persistable = people.stream()
                .map(p -> new PersistablePerson(
                        p.getPersonId(), p.getUserName(), p.getFirstName(),
                        p.getLastName(), p.getYears(), true))
                .collect(Collectors.toList());
        persistablePersonRepository.saveAllAndFlush(persistable);
    }

    // ---- Part 6: Parallel Inserts ----

    /** Part 6: Parallel Bulk API across N threads */
    public void insertParallelBulkApi(List<Person> people, int batchSize, int threadCount) {
        parallelInsertExecutor.parallelBulkApiInsert(people, PersonDescriptors.PERSON_TABLE, batchSize, threadCount);
    }

    /** Part 6: Parallel JdbcTemplate across N threads */
    public void insertParallelJdbcTemplate(List<Person> people, int batchSize, int objectsPerInsert, int threadCount) {
        parallelInsertExecutor.parallelJdbcTemplateInsert(people, PersonDescriptors.PERSON_TABLE, batchSize, objectsPerInsert, threadCount);
    }

    // ---- Part 7: Index Impact ----

    /** Part 7: Set the number of non-clustered indexes on person table */
    public void setIndexCount(int count) {
        indexBenchmarkHelper.setIndexCount(count);
    }

    // ---- Part 8: TVP + Plain JDBC ----

    /** Part 8: Table-Valued Parameters */
    public void insertWithTvp(List<Person> people, int batchSize) {
        tvpInsertRepository.insertWithTvp(people, batchSize);
    }

    /** Part 8: Ensure TVP type exists in database */
    public void ensureTvpTypeExists() {
        tvpInsertRepository.ensureTvpTypeExists();
    }

    /** Part 8: Plain JDBC PreparedStatement (no Hibernate/JPA overhead) */
    public void insertPlainJdbc(List<Person> people, int batchSize) {
        plainJdbcInsertRepository.insertBatch(people, batchSize);
    }
}
