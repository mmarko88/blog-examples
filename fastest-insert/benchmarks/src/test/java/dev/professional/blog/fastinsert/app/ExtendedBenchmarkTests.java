package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
 * Extended benchmarks:
 * - 16-thread parallel inserts
 * - 1M dataset for key strategies
 * - useBulkCopyForBatchInsert=true (via Spring property override)
 */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.properties",
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true;useBulkCopyForBatchInsert=true",
        "spring.jpa.properties.hibernate.jdbc.batch_size=10000"
})
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class ExtendedBenchmarkTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;

    @BeforeAll
    void setUp() {
        fastInsertService.ensureTvpTypeExists();
    }

    // ========== 16-thread parallel ==========

    @Test
    void parallelBulkApi_16threads() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("parallelBulkApi_16t", param,
                v -> fastInsertService.insertParallelBulkApi(people, 1000, 16));
        assertCount(people.size());
    }

    @Test
    void parallelJdbcTemplate_16threads() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("parallelJdbcTemplate_16t", param,
                v -> fastInsertService.insertParallelJdbcTemplate(people, 1000, 1, 16));
        assertCount(people.size());
    }

    // ========== 1M dataset ==========

    @Test
    void bulkApi_1M() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(1_000_000);
        TestParam param = new TestParam(people, 5000, new Date(), 1);
        multipleRunner.execJob("bulkApi_1M", param,
                v -> fastInsertService.insertBulkApi(people, 5000));
        assertCount(people.size());
    }

    @Test
    void parallelBulkApi_1M_4t() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(1_000_000);
        TestParam param = new TestParam(people, 5000, new Date(), 1);
        multipleRunner.execJob("parallelBulkApi_1M_4t", param,
                v -> fastInsertService.insertParallelBulkApi(people, 5000, 4));
        assertCount(people.size());
    }

    @Test
    void jdbcTemplate_1M() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(1_000_000);
        TestParam param = new TestParam(people, 5000, new Date(), 1);
        multipleRunner.execJob("jdbcTemplate_1M", param,
                v -> fastInsertService.insertJdbcTemplateBatch(5000, 1, people));
        assertCount(people.size());
    }

    @Test
    void tvp_1M() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(1_000_000);
        TestParam param = new TestParam(people, 5000, new Date(), 1);
        multipleRunner.execJob("tvp_1M", param,
                v -> fastInsertService.insertWithTvp(people, 5000));
        assertCount(people.size());
    }

    // ========== useBulkCopyForBatchInsert=true (connection string param) ==========
    // This test class already has useBulkCopyForBatchInsert=true in the datasource URL.
    // All Hibernate and JdbcTemplate inserts will automatically use bulk copy under the hood.

    @Test
    void hibernatePersist_withBulkCopy() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("hibernate_bulkCopy", param,
                v -> fastInsertService.insertHibernateBatch(people, 1000));
        assertCount(people.size());
    }

    @Test
    void jdbcTemplate_withBulkCopy() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("jdbcTemplate_bulkCopy", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        assertCount(people.size());
    }

    @Test
    void plainJdbc_withBulkCopy() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("plainJdbc_bulkCopy", param,
                v -> fastInsertService.insertPlainJdbc(people, 1000));
        assertCount(people.size());
    }

    @Test
    void statelessSession_withBulkCopy() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("stateless_bulkCopy", param,
                v -> fastInsertService.insertStatelessSession(people, 1000));
        assertCount(people.size());
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
