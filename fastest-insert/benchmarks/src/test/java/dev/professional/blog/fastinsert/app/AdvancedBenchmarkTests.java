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
 * Advanced benchmarks:
 * - Part 6: Thread count sweep (1, 2, 4, 8 threads)
 * - Part 7: Index impact (0, 1, 3, 5 non-clustered indexes)
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class AdvancedBenchmarkTests {
    private static final String THREAD_PARAMS = "threadParams";
    private static final String INDEX_PARAMS = "indexParams";

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;

    @BeforeAll
    void setUp() {
        fastInsertService.ensureTvpTypeExists();
    }

    // ========== Part 6: Thread count sweep ==========

    @ParameterizedTest
    @MethodSource(THREAD_PARAMS)
    void parallelBulkApi_threads(ThreadParam param) {
        multipleRunner.execJob("parallelBulkApi_" + param.threadCount + "t", param.toTestParam(),
                v -> fastInsertService.insertParallelBulkApi(
                        param.people, param.batchSize, param.threadCount));
        assertCount(param.people.size());
    }

    @ParameterizedTest
    @MethodSource(THREAD_PARAMS)
    void parallelJdbcTemplate_threads(ThreadParam param) {
        multipleRunner.execJob("parallelJdbcTemplate_" + param.threadCount + "t", param.toTestParam(),
                v -> fastInsertService.insertParallelJdbcTemplate(
                        param.people, param.batchSize, 1, param.threadCount));
        assertCount(param.people.size());
    }

    // ========== Part 7: Index impact ==========

    @ParameterizedTest
    @MethodSource(INDEX_PARAMS)
    void bulkApiInsert_withIndexes(IndexParam param) {
        fastInsertService.setIndexCount(param.indexCount);
        try {
            multipleRunner.execJob("bulkApi_idx" + param.indexCount, param.toTestParam(),
                    v -> fastInsertService.insertBulkApi(param.people, param.batchSize));
            assertCount(param.people.size());
        } finally {
            fastInsertService.setIndexCount(0);
        }
    }

    @ParameterizedTest
    @MethodSource(INDEX_PARAMS)
    void jdbcTemplateInsert_withIndexes(IndexParam param) {
        fastInsertService.setIndexCount(param.indexCount);
        try {
            multipleRunner.execJob("jdbcTemplate_idx" + param.indexCount, param.toTestParam(),
                    v -> fastInsertService.insertJdbcTemplateBatch(
                            param.batchSize, 1, param.people));
            assertCount(param.people.size());
        } finally {
            fastInsertService.setIndexCount(0);
        }
    }

    @ParameterizedTest
    @MethodSource(INDEX_PARAMS)
    void hibernatePersist_withIndexes(IndexParam param) {
        fastInsertService.setIndexCount(param.indexCount);
        try {
            multipleRunner.execJob("hibernate_idx" + param.indexCount, param.toTestParam(),
                    v -> fastInsertService.insertHibernateBatch(param.people, param.batchSize));
            assertCount(param.people.size());
        } finally {
            fastInsertService.setIndexCount(0);
        }
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }

    // ========== Parameter providers ==========

    /** Thread count sweep: 1, 2, 4, 8 threads × batch 1000 */
    public static Stream<ThreadParam> threadParams() {
        List<ThreadParam> params = new ArrayList<>();
        Date start = new Date();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        int[] threadCounts = {1, 2, 4, 8};
        for (int tc : threadCounts) {
            params.add(new ThreadParam(people, 1000, start, tc));
        }
        return params.stream();
    }

    /** Index impact: 0, 1, 3, 5 indexes × batch 1000 */
    public static Stream<IndexParam> indexParams() {
        List<IndexParam> params = new ArrayList<>();
        Date start = new Date();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        int[] indexCounts = {0, 1, 3, 5};
        for (int ic : indexCounts) {
            params.add(new IndexParam(people, 1000, start, ic));
        }
        return params.stream();
    }

    // ========== Parameter classes ==========

    static class ThreadParam {
        final List<Person> people;
        final int batchSize;
        final Date startDate;
        final int threadCount;

        ThreadParam(List<Person> people, int batchSize, Date startDate, int threadCount) {
            this.people = people;
            this.batchSize = batchSize;
            this.startDate = startDate;
            this.threadCount = threadCount;
        }

        TestParam toTestParam() {
            return new TestParam(people, batchSize, startDate, 1);
        }

        @Override
        public String toString() {
            return threadCount + " threads, batch=" + batchSize;
        }
    }

    static class IndexParam {
        final List<Person> people;
        final int batchSize;
        final Date startDate;
        final int indexCount;

        IndexParam(List<Person> people, int batchSize, Date startDate, int indexCount) {
            this.people = people;
            this.batchSize = batchSize;
            this.startDate = startDate;
            this.indexCount = indexCount;
        }

        TestParam toTestParam() {
            return new TestParam(people, batchSize, startDate, 1);
        }

        @Override
        public String toString() {
            return indexCount + " indexes, batch=" + batchSize;
        }
    }
}
