package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Date;
import java.util.List;

/**
 * Tests combining the best optimizations to find the absolute fastest:
 * - useBulkCopyForBatchInsert=true (from Part 4)
 * - Parallel threads (from Part 6)
 *
 * Note: multi-value INSERT (objectsPerInsert > 1) is NOT compatible with
 * useBulkCopyForBatchInsert=true — the driver rewrites the SQL to bulk copy
 * which only works with single-row INSERT statements.
 */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.properties",
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true;useBulkCopyForBatchInsert=true"
})
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class CombinedWinnerTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;

    /** bulkCopy=true + single thread, batch=1000 (baseline for this context) */
    @Test
    void bulkApi_singleThread() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("combined_bulkApi_1t", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        assertCount(people.size());
    }

    /** bulkCopy=true + parallel 4t */
    @Test
    void bulkApi_parallel4t() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("combined_bulkApi_4t", param,
                v -> fastInsertService.insertParallelBulkApi(people, 1000, 4));
        assertCount(people.size());
    }

    /** StatelessSession + bulkCopy=true */
    @Test
    void statelessSession_bulkCopy() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("combined_stateless_bulkCopy", param,
                v -> fastInsertService.insertStatelessSession(people, 1000));
        assertCount(people.size());
    }

    /** JdbcTemplate + bulkCopy=true */
    @Test
    void jdbcTemplate_bulkCopy() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("combined_jdbcTemplate_bulkCopy", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        assertCount(people.size());
    }

    /** Parallel JdbcTemplate 4t + bulkCopy=true */
    @Test
    void parallelJdbcTemplate4t_bulkCopy() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("combined_parallelJdbc4t_bulkCopy", param,
                v -> fastInsertService.insertParallelJdbcTemplate(people, 1000, 1, 4));
        assertCount(people.size());
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
