package dev.professional.blog.fastinsert.parallel;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonDescriptors;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Part 6: Parallel inserts — 4-thread core benchmark plus a thread-count sweep.
 */
class Part6ParallelInsertTests extends AbstractBenchmarkTest {

    private static final String THREAD_PARAMS = "threadParams";

    @Autowired
    private ParallelInsertExecutor parallelInsertExecutor;

    /** Part 6: Parallel Bulk API (4 threads) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void parallelBulkApiInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("parallelBulkApi_4t", param,
                v -> parallelInsertExecutor.parallelBulkApiInsert(
                        param.getPeople(), PersonDescriptors.PERSON_TABLE,
                        param.getBatchSize(), 4));
        assertInsertedCount(param);
    }

    /** Part 6: Parallel JdbcTemplate (4 threads) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void parallelJdbcTemplateInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("parallelJdbcTemplate_4t", param,
                v -> parallelInsertExecutor.parallelJdbcTemplateInsert(
                        param.getPeople(), PersonDescriptors.PERSON_TABLE,
                        param.getBatchSize(), param.getObjectsPerInsert(), 4));
        assertInsertedCount(param);
    }

    // ========== Thread count sweep ==========

    @ParameterizedTest
    @MethodSource(THREAD_PARAMS)
    void parallelBulkApi_threads(ThreadParam param) {
        multipleRunner.execJob("parallelBulkApi_" + param.threadCount + "t", param.toTestParam(),
                v -> parallelInsertExecutor.parallelBulkApiInsert(
                        param.people, PersonDescriptors.PERSON_TABLE,
                        param.batchSize, param.threadCount));
        assertCount(param.people.size());
    }

    @ParameterizedTest
    @MethodSource(THREAD_PARAMS)
    void parallelJdbcTemplate_threads(ThreadParam param) {
        multipleRunner.execJob("parallelJdbcTemplate_" + param.threadCount + "t", param.toTestParam(),
                v -> parallelInsertExecutor.parallelJdbcTemplateInsert(
                        param.people, PersonDescriptors.PERSON_TABLE,
                        param.batchSize, 1, param.threadCount));
        assertCount(param.people.size());
    }

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
}
