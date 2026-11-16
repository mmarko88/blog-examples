package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.List;

/**
 * GC and memory tuning tests. Reads active GC and heap settings from JVM
 * runtime, so the SAME test file produces different-labeled results when
 * invoked with different -D args:
 *
 *   mvn test -Dtest=GcTuningTests -DargLine="-Xmx512m -XX:+UseG1GC"
 *   mvn test -Dtest=GcTuningTests -DargLine="-Xmx4g -XX:+UseZGC"
 *
 * Label format: gc_{gcName}_{heapMb}
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class GcTuningTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;

    private String gcLabel;

    @BeforeAll
    void detectGc() {
        long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String gcName = "unknown";
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String n = gc.getName().toLowerCase();
            if (n.contains("g1")) { gcName = "G1"; break; }
            if (n.contains("zgc") || n.contains("z ")) { gcName = "ZGC"; break; }
            if (n.contains("parallel") || n.contains("ps ")) { gcName = "Parallel"; break; }
            if (n.contains("copy") || n.contains("marksweep")) { gcName = "Serial"; break; }
            if (n.contains("shenandoah")) { gcName = "Shenandoah"; break; }
        }
        gcLabel = "gc_" + gcName + "_" + roundHeap(maxMb) + "m";
        log.info("GC configuration: {} (max heap: {} MB)", gcName, maxMb);
    }

    private long roundHeap(long mb) {
        if (mb <= 600) return 512;
        if (mb <= 1200) return 1024;
        if (mb <= 2500) return 2048;
        if (mb <= 4500) return 4096;
        return 8192;
    }

    @Test
    void bulkApi_underCurrentGc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        long gcBefore = totalGcPauseMs();
        multipleRunner.execJob(gcLabel + "_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        long gcAfter = totalGcPauseMs();
        log.info("{} bulkApi: GC pause total {}ms", gcLabel, gcAfter - gcBefore);
        assertCount(people.size());
    }

    @Test
    void jdbcTemplate_underCurrentGc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        long gcBefore = totalGcPauseMs();
        multipleRunner.execJob(gcLabel + "_jdbc", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        long gcAfter = totalGcPauseMs();
        log.info("{} jdbc: GC pause total {}ms", gcLabel, gcAfter - gcBefore);
        assertCount(people.size());
    }

    private long totalGcPauseMs() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += gc.getCollectionTime();
        }
        return total;
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
