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
 * Combination benchmarks: multi-value INSERT (objectsPerInsert=400) + parallel threads.
 *
 * <p>Note: useBulkCopyForBatchInsert must be FALSE for multi-value inserts.
 * The JDBC driver's bulk copy path is incompatible with multi-value INSERT SQL
 * because it expects exactly (columns) parameters per batch row, but multi-value
 * INSERT has (columns * objectsPerInsert) parameters.</p>
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class MultiValueParallelTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;

    /** Baseline: single-thread, multi-value 400, batch 1000 */
    @Test
    void multiValue400_singleThread() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 400);
        multipleRunner.execJob("multiValue400_1t", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 400, people));
        assertCount(people.size());
    }

    /** Multi-value 400 + parallel 4 threads */
    @Test
    void multiValue400_parallel4t() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 400);
        multipleRunner.execJob("multiValue400_4t", param,
                v -> fastInsertService.insertParallelJdbcTemplate(people, 1000, 400, 4));
        assertCount(people.size());
    }

    /** Multi-value 400 + parallel 8 threads */
    @Test
    void multiValue400_parallel8t() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 400);
        multipleRunner.execJob("multiValue400_8t", param,
                v -> fastInsertService.insertParallelJdbcTemplate(people, 1000, 400, 8));
        assertCount(people.size());
    }

    /** Multi-value 400 + parallel JDBC PreparedStatement (4 threads) */
    @Test
    void multiValue400_parallelJdbc4t() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 400);
        multipleRunner.execJob("multiValue400_jdbc_4t", param,
                v -> fastInsertService.insertParallelJdbcTemplate(people, 5000, 400, 4));
        assertCount(people.size());
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
