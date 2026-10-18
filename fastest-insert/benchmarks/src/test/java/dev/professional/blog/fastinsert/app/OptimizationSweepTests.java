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
 * Optimization sweep tests:
 * 1. Batch size sweep (50-2000 in steps of 50) for Bulk API
 * 2. provider_disables_autocommit with Hibernate
 * 3. Combined winner: multi-value 400 + parallel + bulkCopy
 */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.properties",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=true",
        "spring.datasource.hikari.auto-commit=false"
})
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class OptimizationSweepTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;

    @BeforeAll
    void setUp() {
        fastInsertService.ensureTvpTypeExists();
    }

    // ========== Batch size sweep ==========

    @ParameterizedTest
    @MethodSource("batchSweepParams")
    void bulkApi_batchSweep(BatchSweepParam param) {
        multipleRunner.execJob("bulkApi_bs" + param.batchSize, param.toTestParam(),
                v -> fastInsertService.insertBulkApi(param.people, param.batchSize));
        assertCount(param.people.size());
    }

    @ParameterizedTest
    @MethodSource("batchSweepParams")
    void jdbcTemplate_batchSweep(BatchSweepParam param) {
        multipleRunner.execJob("jdbcTemplate_bs" + param.batchSize, param.toTestParam(),
                v -> fastInsertService.insertJdbcTemplateBatch(param.batchSize, 1, param.people));
        assertCount(param.people.size());
    }

    // ========== provider_disables_autocommit ==========

    @Test
    void hibernatePersist_providerDisablesAutocommit() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("hibernate_noAutoCommit", param,
                v -> fastInsertService.insertHibernateBatch(people, 1000));
        assertCount(people.size());
    }

    @Test
    void statelessSession_providerDisablesAutocommit() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("stateless_noAutoCommit", param,
                v -> fastInsertService.insertStatelessSession(people, 1000));
        assertCount(people.size());
    }

    @Test
    void saveAllPersistable_providerDisablesAutocommit() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("persistable_noAutoCommit", param,
                v -> fastInsertService.saveAllPersistable(people));
        assertCount(people.size());
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }

    // ========== Parameter providers ==========

    static Stream<BatchSweepParam> batchSweepParams() {
        List<BatchSweepParam> params = new ArrayList<>();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        Date start = new Date();
        for (int bs = 50; bs <= 2000; bs += 50) {
            params.add(new BatchSweepParam(people, bs, start));
        }
        return params.stream();
    }

    static class BatchSweepParam {
        final List<Person> people;
        final int batchSize;
        final Date startDate;

        BatchSweepParam(List<Person> people, int batchSize, Date startDate) {
            this.people = people;
            this.batchSize = batchSize;
            this.startDate = startDate;
        }

        TestParam toTestParam() {
            return new TestParam(people, batchSize, startDate, 1);
        }

        @Override
        public String toString() {
            return "batch=" + batchSize;
        }
    }
}
