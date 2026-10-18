package dev.professional.blog.fastinsert.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.function.Consumer;

/**
 * Runs a benchmark job multiple times and records each result.
 */
@Service
@Slf4j
public class MultipleRunner {
    private static final int REPETITIONS = 10;

    @Autowired
    private FastInsertService fastInsertService;
    @Autowired
    private TestResultService testResultService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void execJob(String testName, TestParam param, Consumer<Void> job) {
        for (int i = 0; i < REPETITIONS; i++) {
            fastInsertService.cleanUpPeopleTable();
            jdbcTemplate.execute("DBCC SHRINKFILE ('testdb_log', 1024)");

            long startTime = System.currentTimeMillis();
            job.accept(null);
            long duration = System.currentTimeMillis() - startTime;

            testResultService.save(new TestResult(
                    null, testName, new Date(), duration,
                    param.getPeople().size(), param.getBatchSize(),
                    param.getObjectsPerInsert(), i, param.getTestStartDate()));
        }
    }
}
