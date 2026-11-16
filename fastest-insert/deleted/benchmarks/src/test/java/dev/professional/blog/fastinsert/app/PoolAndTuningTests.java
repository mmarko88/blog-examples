package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Date;
import java.util.List;

/**
 * Tests connection pool sizing and connection string tuning.
 *
 * Default HikariCP pool: maximum-pool-size=10.
 * We test parallel 4t with different pool sizes: 2, 5, 10, 20.
 *
 * Connection string tuning: sendStringParametersAsUnicode=false
 * (avoids implicit NVARCHAR→VARCHAR conversion on the server).
 */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.properties",
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=false"
})
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class PoolAndTuningTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;

    // ========== Connection string tuning: sendStringParametersAsUnicode=false ==========

    @Test
    void unicodeFalse_bulkApi() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("noUnicode_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        assertCount(people.size());
    }

    @Test
    void unicodeFalse_jdbcTemplate() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("noUnicode_jdbc", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        assertCount(people.size());
    }

    @Test
    void unicodeFalse_parallel4t_bulkApi() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("noUnicode_par4_bulkApi", param,
                v -> fastInsertService.insertParallelBulkApi(people, 1000, 4));
        assertCount(people.size());
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }
}
