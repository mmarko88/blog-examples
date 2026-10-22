package dev.professional.blog.fastinsert.app;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs a benchmark job multiple times and records each result.
 * Prints per-repetition timings, a per-scenario summary, and a full
 * median/mean/min/max table when the suite finishes.
 */
@Service
@Slf4j
public class MultipleRunner {
    private static final int REPETITIONS = 10;

    @Autowired
    private CleanupService cleanupService;
    @Autowired
    private TestResultService testResultService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** scenario key -> all repetition durations, in run order */
    private final Map<String, List<Long>> results = new LinkedHashMap<>();

    public void execJob(String testName, TestParam param, Consumer<Void> job) {
        String scenario = scenarioKey(testName, param);
        List<Long> durations = new ArrayList<>(REPETITIONS);

        for (int i = 0; i < REPETITIONS; i++) {
            cleanupService.cleanUpPeopleTable();
            jdbcTemplate.execute("DBCC SHRINKFILE ('testdb_log', 1024)");

            long startTime = System.currentTimeMillis();
            job.accept(null);
            long duration = System.currentTimeMillis() - startTime;
            durations.add(duration);

            log.info("{} rep {}/{}: {} ms", testName, i + 1, REPETITIONS, duration);

            testResultService.save(new TestResult(
                    null, testName, new Date(), duration,
                    param.getPeople().size(), param.getBatchSize(),
                    param.getObjectsPerInsert(), i, param.getTestStartDate()));
        }
        results.merge(scenario, durations, (a, b) -> { a.addAll(b); return a; });
    }

    private static String scenarioKey(String testName, TestParam param) {
        return String.format("%s (n=%d, batch=%d, opi=%d)", testName,
                param.getPeople().size(), param.getBatchSize(), param.getObjectsPerInsert());
    }

    private static String stats(List<Long> d) {
        List<Long> s = d.stream().sorted().toList();
        int n = s.size();
        double median = n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
        double mean = s.stream().mapToLong(Long::longValue).average().orElse(0);
        return String.format("n=%d median=%.0f ms mean=%.0f ms min=%d ms max=%d ms",
                n, median, mean, s.get(0), s.get(n - 1));
    }

    @PreDestroy
    void printSummary() {
        if (results.isEmpty()) return;
        StringBuilder sb = new StringBuilder("\n========== BENCHMARK SUMMARY ==========\n");
        results.forEach((scenario, d) -> sb.append(String.format("%-55s %s%n", scenario, stats(d))));
        log.info(sb.toString());
    }
}
