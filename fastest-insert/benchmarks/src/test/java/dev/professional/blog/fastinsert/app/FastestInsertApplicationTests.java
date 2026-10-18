package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
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
@Slf4j
class FastestInsertApplicationTests {
    protected static final String TEST_PARAMS = "testParams";

    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private FastInsertService fastInsertService;
    @Autowired
    private MultipleRunner multipleRunner;

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
