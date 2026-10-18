package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Benchmark tests for all insert strategies.
 *
 * <p>Requires a running MS SQL Server with a {@code testdb} database.</p>
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class FastestInsertApplicationTests {
    protected static final String TEST_PARAMS = "testParams";

    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private FastInsertService fastInsertService;
    @Autowired
    private MultipleRunner multipleRunner;

    @BeforeAll
    void setUp() {
        // Ensure TVP type exists before any TVP tests run
        fastInsertService.ensureTvpTypeExists();
    }

    /** Part 2: JDBC PreparedStatement batch insert */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void regularBatchInsert(TestParam param) {
        multipleRunner.execJob("regularBatchInsert", param,
                v -> fastInsertService.insertJdbcBatch(
                        param.getBatchSize(), param.getObjectsPerInsert(), param.getPeople()));
        assertInsertedCount(param);
    }

    /** Part 2: Spring JdbcTemplate batch insert */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void jdbcTemplateInsert(TestParam param) {
        multipleRunner.execJob("jdbcTemplateInsert", param,
                v -> fastInsertService.insertJdbcTemplateBatch(
                        param.getBatchSize(), param.getObjectsPerInsert(), param.getPeople()));
        assertInsertedCount(param);
    }

    /** Part 2: MS SQL Bulk Copy API */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void bulkApiInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("bulkApiInsert", param,
                v -> fastInsertService.insertBulkApi(param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Part 1: Hibernate persist-and-flush */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void hibernatePersist(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("hibernatePersist", param,
                v -> fastInsertService.insertHibernateBatch(param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Part 1: JPA saveAll (SELECT per entity + INSERT) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void saveAllInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("saveAll", param,
                v -> fastInsertService.saveAll(param.getPeople()));
        assertInsertedCount(param);
    }

    // ---- Part 5: Hibernate Tuning ----

    /** Part 5: Hibernate StatelessSession */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void statelessSessionInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("statelessSession", param,
                v -> fastInsertService.insertStatelessSession(param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Part 5: saveAll with Persistable (no SELECT-before-INSERT) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void saveAllPersistableInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("saveAllPersistable", param,
                v -> fastInsertService.saveAllPersistable(param.getPeople()));
        assertInsertedCount(param);
    }

    // ---- Part 6: Parallel Inserts ----

    /** Part 6: Parallel Bulk API (4 threads) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void parallelBulkApiInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("parallelBulkApi_4t", param,
                v -> fastInsertService.insertParallelBulkApi(param.getPeople(), param.getBatchSize(), 4));
        assertInsertedCount(param);
    }

    /** Part 6: Parallel JdbcTemplate (4 threads) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void parallelJdbcTemplateInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("parallelJdbcTemplate_4t", param,
                v -> fastInsertService.insertParallelJdbcTemplate(
                        param.getPeople(), param.getBatchSize(), param.getObjectsPerInsert(), 4));
        assertInsertedCount(param);
    }

    // ---- Part 8: TVP + Plain JDBC ----

    /** Part 8: Table-Valued Parameters */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void tvpInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("tvpInsert", param,
                v -> fastInsertService.insertWithTvp(param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Part 8: Plain JDBC (no Hibernate/JPA overhead) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void plainJdbcInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("plainJdbc", param,
                v -> fastInsertService.insertPlainJdbc(param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    private void assertInsertedCount(TestParam param) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(param.getPeople().size());
    }

    public static Stream<TestParam> testParams() {
        List<TestParam> params = new ArrayList<>();
        Date startDate = new Date();
        int[] objectsPerInsert = {1, 10, 100, 400};
        int[] batchSizes = {10, 100, 1_000, 5_000};

        for (int opi : objectsPerInsert) {
            List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
            for (int bs : batchSizes) {
                params.add(new TestParam(people, bs, startDate, opi));
            }
        }
        return params.stream();
    }
}
